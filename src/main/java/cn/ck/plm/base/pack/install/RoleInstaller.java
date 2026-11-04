/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.mapper.RoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 角色安装器（{@code ROLE}）—— 读包内 {@code roles.json}。
 *
 * <p>落地的只是"岗位定义"（角色本身），<b>不含成员</b>：谁担任这个岗位是各企业自己的人事安排，
 * 装包不该替企业决定"谁是谁"。租户管理员在角色配置里补成员即可。
 *
 * <h3>为什么必须按平台级播一次（pack.json 里 reusable=true）</h3>
 * {@code ck_role.code} 是<b>全局唯一</b>（不是租户内唯一），而 {@code ck_role} 又是平台共享表
 * （查询注入 {@code tenant_oid IN (平台, 本租户)}）。若各租户各装一份：第一个租户建过之后，
 * 第二个租户的查重<b>看不到</b>那一行 → 会去 INSERT → 撞全局唯一键报错。
 * 所以这类资源只能平台级播一次、全租户共享可见；下文 catch 住唯一键冲突也只是兜底，
 * 真撞上了说明 reusable 被改错了。
 *
 * <h3>幂等</h3>
 * 幂等键 = code：已存在即跳过，<b>不覆盖</b>（管理员改过的名称与说明，不该被下一次装包冲掉）。
 */
@Component
public class RoleInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(RoleInstaller.class);

    private final RoleMapper roleMapper;

    public RoleInstaller(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public String resourceType() {
        return "ROLE";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取模板文件失败: " + e.getMessage()));
            return results;
        }

        Object roles = json.get("roles");
        if (!(roles instanceof List)) {
            return results;
        }
        for (Object element : (List<?>) roles) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String code = text(row.get("code"));
            if (code == null) {
                continue;
            }
            String name = text(row.get("name")) == null ? code : text(row.get("name"));
            try {
                if (roleMapper.selectByCode(code) != null) {
                    results.add(InstallResult.skipped(resourceType(), code, "角色已存在"));
                    continue;
                }
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增业务角色「" + name + "」"));
                    continue;
                }
                Role role = new Role(code, name);
                role.setDescription(text(row.get("description")));
                role.setRoleType("BUSINESS");
                roleMapper.insert(role);
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("业务角色：" + name)
                        .on("ck_role", role.getOid()));
            } catch (Exception e) {
                // code 全局唯一：撞键说明别的租户已建过同名角色（reusable 被改错时会走到这里）
                results.add(InstallResult.failed(resourceType(), code,
                        "角色 code 已存在（全局唯一，可能由其它租户装过）: " + e.getMessage()));
                log.warn("角色 {} 安装失败: {}", code, e.getMessage());
            }
        }
        log.debug("角色安装完成: {} 条明细", results.size());
        return results;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
