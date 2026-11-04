/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.UserMapper;
import cn.ck.plm.product.dto.TeamMemberVO;
import cn.ck.plm.product.entity.ProductLine;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.entity.Stage;
import cn.ck.plm.product.entity.Team;
import cn.ck.plm.product.entity.TeamMember;
import cn.ck.plm.product.mapper.ProductLineMapper;
import cn.ck.plm.product.mapper.ProductModelMapper;
import cn.ck.plm.product.mapper.TeamMapper;
import cn.ck.plm.product.mapper.TeamMemberMapper;
import cn.ck.plm.product.service.api.FolderService;
import cn.ck.plm.product.service.api.ProductModelService;
import cn.ck.plm.product.service.api.MethodologyInstanceService;
import cn.ck.plm.product.service.api.StageService;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.service.api.IBADataService;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceCapability;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * {@link ProductModelService} 的数据库实现。
 *
 * <p>产品型号创建时会自动初始化关联团队、6 个默认研发阶段及系统文件夹，
 * 行为与产品系列一致。
 */
@Service
public class ProductModelServiceImpl implements ProductModelService, SoftTypeInstanceCapability {

    private static final Logger log = LoggerFactory.getLogger(ProductModelServiceImpl.class);

    /** 能力宿主 code（= type_definition.root_type_code） */
    private static final String HOST = "PRODUCT_MODEL";

    private final ProductModelMapper productModelMapper;
    private final ProductLineMapper productLineMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final FolderService folderService;
    private final StageService stageService;
    private final ObjectMapper objectMapper;
    private final MethodologyInstanceService methodologyInstanceService;
    /** 团队可见性过滤（2026-10-12）：不在系列/型号团队中的用户看不到对应记录 */
    private final ProductTeamVisibility teamVisibility;
    /** 建容器时顺带建同名 Part（BOM 父件） */
    private final ContainerPartProvisioner containerPartProvisioner;
    /** 型号上的 IBA（TARGET_LAUNCH_DATE / TARGET_COST …）：写要落 IBA，读要摊平回表单 */
    private final IbaDataSupport ibaDataSupport;
    private final IBADataService ibaDataService;

    public ProductModelServiceImpl(ProductModelMapper productModelMapper,
                                    ProductLineMapper productLineMapper,
                                    TeamMapper teamMapper,
                                    TeamMemberMapper teamMemberMapper,
                                    UserMapper userMapper,
                                    FolderService folderService,
                                    StageService stageService,
                                    ObjectMapper objectMapper,
                                    MethodologyInstanceService methodologyInstanceService,
                                    ProductTeamVisibility teamVisibility,
                                    ContainerPartProvisioner containerPartProvisioner,
                                    IbaDataSupport ibaDataSupport,
                                    IBADataService ibaDataService) {
        this.productModelMapper = productModelMapper;
        this.productLineMapper = productLineMapper;
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.userMapper = userMapper;
        this.folderService = folderService;
        this.stageService = stageService;
        this.objectMapper = objectMapper;
        this.methodologyInstanceService = methodologyInstanceService;
        this.teamVisibility = teamVisibility;
        this.containerPartProvisioner = containerPartProvisioner;
        this.ibaDataSupport = ibaDataSupport;
        this.ibaDataService = ibaDataService;
    }

    // ==================== 能力宿主策略（SoftTypeInstanceCapability）====================

    @Override
    public String hostCode() {
        return HOST;
    }

    @Override
    public Set<SoftTypeInstanceCapability.Operation> supportedOperations() {
        return EnumSet.of(
                SoftTypeInstanceCapability.Operation.CREATE,
                SoftTypeInstanceCapability.Operation.READ,
                SoftTypeInstanceCapability.Operation.UPDATE);
    }

    @Override
    public SoftTypeInstanceResult createInstance(TypeDefinition type, Map<String, Object> payload) {
        ProductModel model = objectMapper.convertValue(payload, ProductModel.class);
        ProductModel created = create(model);
        // 新建：全量保存 IBA（无旧数据，delete-all 无副作用）——与产品系列同一口径
        ibaDataSupport.saveIbaValues(HOST, created.getOid(), payload);

        SoftTypeInstanceResult result = new SoftTypeInstanceResult();
        result.setOid(created.getOid());
        result.setNumber(created.getCode());
        result.setName(created.getName());
        result.setEntity(created);
        return result;
    }

    @Override
    public Object getInstance(String oid, Map<String, Object> params) {
        return withIba(findByOid(oid), oid);
    }

    @Override
    public Object updateInstance(String oid, Map<String, Object> body) {
        ProductModel model = objectMapper.convertValue(body, ProductModel.class);
        model.setOid(oid);
        ProductModel updated = update(model);
        // 合并保存 IBA（保留已持久化但本次未提交的字段）——型号原先漏了这一步，
        // 布局里的 IBA 字段填了也进不了 ck_type_iba_data，指标永远读不到
        ibaDataSupport.mergeIbaValues(HOST, oid, body);
        // 返回实体（不摊平）：Controller 会强转成 ProductModel，Map 会 ClassCastException
        return updated;
    }

    /**
     * 详情读取时把 IBA 值摊平到返回的 JSON 上。
     *
     * <p>{@code ProductModel} 不是 {@code IBAExtensible}（没有 extAttrs 容器），没法靠
     * {@code restore} 注入；不摊平的话编辑表单里 IBA 字段永远是空的，一保存还会把库里已有的值冲掉。
     */
    private Object withIba(ProductModel model, String oid) {
        if (model == null) return null;
        Map<String, Object> flat = objectMapper.convertValue(model,
                new TypeReference<Map<String, Object>>() {
                });
        Map<String, Object> iba = ibaDataService.getValues(HOST, oid);
        if (iba != null && !iba.isEmpty()) flat.putAll(iba);
        return flat;
    }

    // ===== 产品型号 =====

    @Override
    @Transactional
    public ProductModel create(ProductModel model) {
        if (model == null || model.getCode() == null || model.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("产品型号编码不能为空");
        }
        if (model.getName() == null || model.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("产品型号名称不能为空");
        }
        if (model.getParentOid() == null || model.getParentOid().trim().isEmpty()) {
            throw new IllegalArgumentException("所属产品系列不能为空");
        }
        String code = model.getCode().trim();
        if (productModelMapper.existsByCode(code) > 0) {
            throw new IllegalArgumentException("产品型号编码 '" + code + "' 已存在");
        }

        // 校验所属产品系列存在
        ProductLine parentLine = productLineMapper.selectByOid(model.getParentOid().trim());
        if (parentLine == null) {
            throw new IllegalArgumentException("所属产品系列不存在");
        }
        model.setParentOid(parentLine.getOid());

        // 自动创建关联团队
        Team team = new Team();
        team.setCode("TEAM_" + code);
        team.setName(model.getName() + "研发团队");
        team.setDescription(model.getName() + "研发团队");
        teamMapper.insert(team);

        // 创建者自动成为「产品经理」—— 团队是刚建的、必无成员，故不必再用 existsByTeamAndUser 兜底。
        // 口径说明：ck_team_member.user_id 存 username（与前端手动添加成员、
        // buildMemberVOs 里的 userMapper.selectByUsername 一致）；role_name 是**自由文本**
        // （"产品经理"不是 ck_role 的角色码 —— 那 16 个是系统权限角色，两套东西别混）。
        // UserContext 用全限定名，免得为一行再动 import 块（本项目既有惯例，见 TypeDefinitionServiceImpl）。
        String creator = cn.ck.plm.base.util.UserContext.get();
        if (creator != null && !creator.trim().isEmpty()) {
            TeamMember productManager = new TeamMember();
            productManager.setTeamOid(team.getOid());
            productManager.setUserId(creator.trim());
            productManager.setRoleName("产品经理");
            teamMemberMapper.insert(productManager);
        }

        model.setTeamOid(team.getOid());
        if (model.getDeleteMark() == null) {
            model.setDeleteMark(false);
        }
        productModelMapper.insert(model);

        // 根据本租户的研发阶段模板初始化阶段到 ck_stage 表（先生成 stage OID）
        List<Stage> stages = stageService.initDefaultStages(model.getOid(), Stage.OWNER_TYPE_MODEL, null);

        // 六类方法论实例（交付件 / 阶段门 / 评审 / 检查规则 / 指标 / 驾驶舱）：
        // 与阶段同一口径 —— 从本租户模板「快照」而来、幂等（已有即跳过）。
        methodologyInstanceService.initFor(model.getOid(), Stage.OWNER_TYPE_MODEL);

        // 根据阶段自带的 defaultFolders 创建各研发阶段的系统默认文件夹
        for (Stage stage : stages) {
            List<String> folderNames = parseDefaultFolders(stage.getDefaultFolders());
            if (!folderNames.isEmpty()) {
                folderService.initSystemFolders(model.getOid(), stage.getOid(), folderNames);
            }
        }

        // 同名 Part：型号自己的 BOM 需要一个父件（不是复用父系列的那个 —— 型号的结构可能不同）
        containerPartProvisioner.provision(model.getName(), model.getOid(),
                ContainerPartProvisioner.CONTAINER_MODEL,
                stages.isEmpty() ? null : stages.get(0).getOid(), creator);

        return model;
    }

    @Override
    @Transactional
    public ProductModel update(ProductModel model) {
        if (model == null || model.getOid() == null) {
            throw new IllegalArgumentException("产品型号 oid 不能为空");
        }
        ProductModel existing = productModelMapper.selectByOid(model.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("产品型号不存在");
        }

        // 校验所属产品系列
        if (model.getParentOid() != null && !model.getParentOid().trim().isEmpty()) {
            ProductLine parentLine = productLineMapper.selectByOid(model.getParentOid().trim());
            if (parentLine == null) {
                throw new IllegalArgumentException("所属产品系列不存在");
            }
        }

        productModelMapper.update(model);

        // 同步团队名称
        if (existing.getTeamOid() != null) {
            Team team = teamMapper.selectByOid(existing.getTeamOid());
            if (team != null) {
                team.setName(model.getName() + "研发团队");
                team.setDescription(model.getName() + "研发团队");
                teamMapper.update(team);
            }
        }

        existing.setName(model.getName());
        existing.setDescription(model.getDescription());
        existing.setThumbnail(model.getThumbnail());
        existing.setParentOid(model.getParentOid());

        return existing;
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return false;
        }
        ProductModel existing = productModelMapper.selectByOid(oid);
        if (existing == null) {
            return false;
        }
        // 逻辑删除：进入回收站，不物理删除
        productModelMapper.softDeleteByOid(oid);
        return true;
    }

    @Override
    public List<ProductModel> findDeleted() {
        return productModelMapper.selectDeleted();
    }

    @Override
    public boolean restore(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return false;
        }
        return productModelMapper.restoreByOid(oid) > 0;
    }

    @Override
    public ProductModel findByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return null;
        }
        return productModelMapper.selectByOid(oid);
    }

    @Override
    public List<ProductModel> findAll() {
        // 团队可见性（2026-10-12）：不在型号团队、也不在父系列/祖先团队 → 不可见；管理员不过滤
        return teamVisibility.filterModels(productModelMapper.selectAll());
    }

    @Override
    public List<ProductModel> findByProductLineOid(String productLineOid) {
        return teamVisibility.filterModels(productModelMapper.selectByProductLineOid(productLineOid));
    }

    @Override
    public List<ProductModel> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        return teamVisibility.filterModels(productModelMapper.search(keyword.trim()));
    }

    // ===== 团队管理 =====

    private void ensureTeam(ProductModel model) {
        if (model.getTeamOid() != null) {
            if (teamMapper.selectByOid(model.getTeamOid()) != null) {
                return;
            }
        }
        String teamCode = "TEAM_" + model.getCode();
        Team existing = teamMapper.selectByCode(teamCode);
        if (existing != null) {
            model.setTeamOid(existing.getOid());
            productModelMapper.update(model);
            return;
        }
        Team team = new Team();
        team.setCode(teamCode);
        team.setName(model.getName() + "研发团队");
        team.setDescription(model.getName() + "研发团队");
        teamMapper.insert(team);
        model.setTeamOid(team.getOid());
        productModelMapper.update(model);
    }

    private void ensureTeam(ProductLine line) {
        if (line.getTeamOid() != null) {
            if (teamMapper.selectByOid(line.getTeamOid()) != null) {
                return;
            }
        }
        String teamCode = "TEAM_" + line.getCode();
        Team existing = teamMapper.selectByCode(teamCode);
        if (existing != null) {
            line.setTeamOid(existing.getOid());
            productLineMapper.update(line);
            return;
        }
        Team team = new Team();
        team.setCode(teamCode);
        team.setName(line.getName() + "研发团队");
        team.setDescription(line.getName() + "研发团队");
        teamMapper.insert(team);
        line.setTeamOid(team.getOid());
        productLineMapper.update(line);
    }

    @Override
    public Team getTeamByProductModelOid(String modelOid) {
        ProductModel model = productModelMapper.selectByOid(modelOid);
        if (model == null) {
            return null;
        }
        ensureTeam(model);
        return teamMapper.selectByOid(model.getTeamOid());
    }

    @Override
    public List<TeamMemberVO> getTeamMembers(String modelOid) {
        ProductModel model = productModelMapper.selectByOid(modelOid);
        if (model == null) {
            return new ArrayList<>();
        }
        ensureTeam(model);

        // 获取自身团队成员
        List<TeamMemberVO> vos = buildMemberVOs(model.getTeamOid(), false, null);

        // 用于去重的 userId 集合（自身成员优先）
        Set<String> seenUserIds = new HashSet<>();
        for (TeamMemberVO vo : vos) {
            seenUserIds.add(vo.getUsername());
        }

        // 获取关联产品线的团队成员（标记为继承）
        if (model.getParentOid() != null) {
            ProductLine pl = productLineMapper.selectByOid(model.getParentOid());
            if (pl != null && pl.getTeamOid() != null) {
                List<TeamMemberVO> lineMembers = buildMemberVOs(pl.getTeamOid(), true, pl.getName());
                for (TeamMemberVO vo : lineMembers) {
                    if (!seenUserIds.contains(vo.getUsername())) {
                        vos.add(vo);
                        seenUserIds.add(vo.getUsername());
                    }
                }

                // 获取产品线祖先的团队成员
                ProductLine parent = pl.getParentOid() != null ? productLineMapper.selectByOid(pl.getParentOid()) : null;
                while (parent != null) {
                    ensureTeam(parent);
                    if (parent.getTeamOid() != null) {
                        List<TeamMemberVO> ancestorVOs = buildMemberVOs(parent.getTeamOid(), true, parent.getName());
                        for (TeamMemberVO vo : ancestorVOs) {
                            if (!seenUserIds.contains(vo.getUsername())) {
                                vos.add(vo);
                                seenUserIds.add(vo.getUsername());
                            }
                        }
                    }
                    parent = parent.getParentOid() != null ? productLineMapper.selectByOid(parent.getParentOid()) : null;
                }
            }
        }

        return vos;
    }

    private List<TeamMemberVO> buildMemberVOs(String teamOid, boolean inherited, String sourceName) {
        List<TeamMember> members = teamMemberMapper.selectByTeamOid(teamOid);
        List<TeamMemberVO> vos = new ArrayList<>();
        for (TeamMember m : members) {
            User u = userMapper.selectByUsername(m.getUserId());
            if (u != null) {
                TeamMemberVO vo = new TeamMemberVO();
                vo.setUsername(u.getUsername());
                vo.setDisplayName(u.getDisplayName());
                vo.setEmail(u.getEmail());
                vo.setEnabled(u.isEnabled());
                vo.setLocked(u.isLocked());
                vo.setRoleName(m.getRoleName());
                vo.setInherited(inherited);
                vo.setSourceProductLineName(sourceName);
                vos.add(vo);
            }
        }
        return vos;
    }

    @Override
    @Transactional
    public void addTeamMember(String modelOid, String userId, String roleName) {
        ProductModel model = productModelMapper.selectByOid(modelOid);
        if (model == null) {
            throw new IllegalArgumentException("产品型号不存在");
        }
        ensureTeam(model);
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (teamMemberMapper.existsByTeamAndUser(model.getTeamOid(), userId.trim())) {
            throw new IllegalArgumentException("该用户已是团队成员");
        }
        TeamMember member = new TeamMember();
        member.setTeamOid(model.getTeamOid());
        member.setUserId(userId.trim());
        member.setRoleName(roleName != null ? roleName : "");
        teamMemberMapper.insert(member);
    }

    @Override
    @Transactional
    public void removeTeamMember(String modelOid, String userId) {
        ProductModel model = productModelMapper.selectByOid(modelOid);
        if (model == null) {
            throw new IllegalArgumentException("产品型号不存在");
        }
        ensureTeam(model);
        if (model.getTeamOid() == null) {
            return;
        }
        teamMemberMapper.deleteByTeamAndUser(model.getTeamOid(), userId);
    }

    /** 从 Stage.defaultFolders（JSON 数组字符串）解析文件夹名称列表 */
    private List<String> parseDefaultFolders(String defaultFoldersJson) {
        if (defaultFoldersJson == null || defaultFoldersJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return new ObjectMapper().readValue(defaultFoldersJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析 defaultFolders 失败: {}", defaultFoldersJson, e);
            return Collections.emptyList();
        }
    }
}
