/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.config;

import cn.ck.plm.resource.entity.ResourceContainer;
import cn.ck.plm.resource.mapper.ResourceLibraryMapper;
import cn.ck.plm.resource.mapper.ResourceContainerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 企业级资源库初始化（种子 + 数据自愈，幂等）：
 * <ol>
 *   <li>幂等预置「企业资源库」根节点与资源子库（平台级共享）；</li>
 *   <li>为「元器件库」子库准备归属阶段（Part 需要 stage_oid）；</li>
 *   <li>清理早期临时方案：借用在 ck_product_line 的 CORP_RESOURCE 行及其阶段。</li>
 * </ol>
 *
 * <p>容器表 {@code ck_resource_container} 的结构（含 code 唯一键与父节点索引）在
 * {@code src/main/resources/schema.sql} —— 本类原先自带的建表语句与
 * {@code ck_container → ck_resource_container} 的更名迁移已退场
 * （口径：结构只留一处来源；不做老库迁移）。
 */
@Component
public class ResourceContainerInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ResourceContainerInitializer.class);

    private final ResourceContainerMapper mapper;
    private final ResourceLibraryMapper resourceLibraryMapper;
    private final DataSource dataSource;

    public ResourceContainerInitializer(ResourceContainerMapper mapper,
                                        ResourceLibraryMapper resourceLibraryMapper,
                                        DataSource dataSource) {
        this.mapper = mapper;
        this.resourceLibraryMapper = resourceLibraryMapper;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try {
            ResourceContainer root = mapper.selectRoot();
            if (root == null) {
                root = newNode(ResourceContainer.ROOT_CODE, "企业资源库",
                        "企业级资源库（元器件、封装图符、标准件、通用件、技术文档、产品图册等统一归属）", null, 0);
                mapper.insert(root);
                log.info("已创建企业资源库根节点: CORP_RESOURCE");
            }
            int sort = 1;
            int created = 0;
            for (String[] def : ResourceContainer.defaultChildren()) {
                if (mapper.selectByCode(def[0]) == null) {
                    mapper.insert(newNode(def[0], def[1], def[1] + "（企业资源库子库）", root.getOid(), sort));
                    created++;
                }
                sort++;
            }
            log.info("企业资源库初始化完成（子库新增 {} 个）", created);

            // 先清理早期临时方案（其阶段挂在旧 product_line 行上），再补建归属阶段
            cleanLegacyHack();

            // 元器件库子库需要归属阶段（Part.stage_oid NOT NULL）
            ResourceContainer componentLib = mapper.selectByCode(ResourceContainer.COMPONENT);
            if (componentLib != null && resourceLibraryMapper.selectResourceStageOid(componentLib.getOid()) == null) {
                resourceLibraryMapper.insertResourceStage(UUID.randomUUID().toString(),
                        "COMPONENT", "元器件", componentLib.getOid(), LocalDateTime.now());
                log.info("已为元器件库创建归属阶段: COMPONENT");
            }
        } catch (Exception e) {
            log.error("企业资源库初始化失败: {}", e.getMessage(), e);
        }
    }

    /** 清理早期临时方案：资源库曾借用 ck_product_line 的 CORP_RESOURCE 行及其阶段 */
    private void cleanLegacyHack() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM ck_stage WHERE owner_oid IN " +
                    "(SELECT oid FROM ck_product_line WHERE code = 'CORP_RESOURCE')");
            int removed = stmt.executeUpdate("DELETE FROM ck_product_line WHERE code = 'CORP_RESOURCE'");
            if (removed > 0) {
                log.info("已清理临时方案数据：ck_product_line.CORP_RESOURCE（{} 行）", removed);
            }
        } catch (Exception e) {
            log.warn("清理临时资源库数据失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private ResourceContainer newNode(String code, String name, String description, String parentOid, int sortOrder) {
        ResourceContainer node = new ResourceContainer();
        node.setOid(UUID.randomUUID().toString());
        node.setCode(code);
        node.setName(name);
        node.setDescription(description);
        node.setContainerType(ResourceContainer.CONTAINER_TYPE);
        node.setParentOid(parentOid);
        node.setSortOrder(sortOrder);
        node.setDeleteMark(false);
        LocalDateTime now = LocalDateTime.now();
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        return node;
    }
}
