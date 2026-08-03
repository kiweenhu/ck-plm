/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
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
import cn.ck.plm.product.service.api.ProductLineService;
import cn.ck.plm.product.service.api.StageService;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ProductLineService} 的数据库实现。
 */
@Service
public class ProductLineServiceImpl implements ProductLineService {

    private static final Logger log = LoggerFactory.getLogger(ProductLineServiceImpl.class);

    private final ProductLineMapper productLineMapper;
    private final ProductModelMapper productModelMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final FolderService folderService;
    private final StageService stageService;

    public ProductLineServiceImpl(ProductLineMapper productLineMapper,
                                   ProductModelMapper productModelMapper,
                                   TypeDefinitionMapper typeDefinitionMapper,
                                   TeamMapper teamMapper,
                                   TeamMemberMapper teamMemberMapper,
                                   UserMapper userMapper,
                                   FolderService folderService,
                                   StageService stageService) {
        this.productLineMapper = productLineMapper;
        this.productModelMapper = productModelMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.userMapper = userMapper;
        this.folderService = folderService;
        this.stageService = stageService;
    }

    // ===== 产品线 =====

    @Override
    @Transactional
    public ProductLine create(ProductLine productLine) {
        if (productLine == null || productLine.getCode() == null || productLine.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("产品线编码不能为空");
        }
        if (productLine.getName() == null || productLine.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("产品线名称不能为空");
        }
        String code = productLine.getCode().trim();
        if (productLineMapper.existsByCode(code) > 0) {
            throw new IllegalArgumentException("产品线编码 '" + code + "' 已存在");
        }

        // 校验父级产品线是否存在
        if (productLine.getParentOid() != null && !productLine.getParentOid().trim().isEmpty()) {
            String parentOid = productLine.getParentOid().trim();
            ProductLine parent = productLineMapper.selectByOid(parentOid);
            if (parent == null) {
                throw new IllegalArgumentException("父级产品线不存在");
            }
            // 防止循环引用
            if (parentOid.equals(productLine.getOid())) {
                throw new IllegalArgumentException("父级产品线不能为自己");
            }
        }

        // 自动创建关联团队
        Team team = new Team();
        team.setCode("TEAM_" + code);
        team.setName(productLine.getName() + "研发团队");
        team.setDescription(productLine.getName() + "研发团队");
        teamMapper.insert(team);

        productLine.setTeamOid(team.getOid());
        productLineMapper.insert(productLine);

        // 根据本租户的研发阶段模板初始化阶段到 ck_stage 表（先生成 stage OID）
        List<Stage> stages = stageService.initDefaultStages(productLine.getOid(), Stage.OWNER_TYPE_LINE);

        // 根据阶段自带的 defaultFolders 创建各研发阶段的系统默认文件夹
        for (Stage stage : stages) {
            List<String> folderNames = parseDefaultFolders(stage.getDefaultFolders());
            if (!folderNames.isEmpty()) {
                folderService.initSystemFolders(productLine.getOid(), stage.getOid(), folderNames);
            }
        }

        return productLine;
    }

    @Override
    @Transactional
    public ProductLine update(ProductLine productLine) {
        if (productLine == null || productLine.getOid() == null) {
            throw new IllegalArgumentException("产品线 oid 不能为空");
        }
        ProductLine existing = productLineMapper.selectByOid(productLine.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("产品线不存在");
        }

        // 校验父级：不允许设为自己或自己的子孙节点
        if (productLine.getParentOid() != null && !productLine.getParentOid().trim().isEmpty()) {
            String parentOid = productLine.getParentOid().trim();
            if (parentOid.equals(productLine.getOid())) {
                throw new IllegalArgumentException("父级产品线不能为自己");
            }
            ProductLine parent = productLineMapper.selectByOid(parentOid);
            if (parent == null) {
                throw new IllegalArgumentException("父级产品线不存在");
            }
            // 检查是否会造成循环引用：父级不能是自己的子孙
            if (isDescendant(parentOid, productLine.getOid())) {
                throw new IllegalArgumentException("不能将产品线移动到自己的子节点下");
            }
        }

        productLineMapper.update(productLine);

        // 同步团队名称
        Team team = teamMapper.selectByOid(existing.getTeamOid());
        if (team != null) {
            team.setName(productLine.getName() + "研发团队");
            team.setDescription(productLine.getName() + "研发团队");
            teamMapper.update(team);
        }

        existing.setName(productLine.getName());
        existing.setDescription(productLine.getDescription());
        existing.setThumbnail(productLine.getThumbnail());
        existing.setParentOid(productLine.getParentOid());

        return existing;
    }

    /** 检查 targetParentOid 是否为 givenOid 的子孙节点 */
    private boolean isDescendant(String targetParentOid, String givenOid) {
        String current = targetParentOid;
        int safety = 0;
        while (current != null && safety < 100) {
            if (current.equals(givenOid)) {
                return true;
            }
            ProductLine node = productLineMapper.selectByOid(current);
            if (node == null || node.getParentOid() == null) {
                return false;
            }
            current = node.getParentOid();
            safety++;
        }
        return false;
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return false;
        }
        ProductLine existing = productLineMapper.selectByOid(oid);
        if (existing == null) {
            return false;
        }
        // 删除关联团队成员
        if (existing.getTeamOid() != null) {
            teamMemberMapper.deleteByTeamOid(existing.getTeamOid());
            teamMapper.deleteByOid(existing.getTeamOid());
        }
        // 子节点的 parent_oid 由 FK ON DELETE SET NULL 自动处理
        productLineMapper.deleteByOid(oid);

        return true;
    }

    @Override
    public ProductLine findByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return null;
        }
        ProductLine pl = productLineMapper.selectByOid(oid);
        return pl;
    }

    @Override
    public List<ProductLine> findAll() {
        return productLineMapper.selectAll();
    }

    @Override
    public List<ProductLine> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        return productLineMapper.search(keyword.trim());
    }

    @Override
    public List<ProductLine> findTree() {
        // 获取所有产品系列的根节点
        List<ProductLine> roots = productLineMapper.selectRoots();
        // 设置产品系列的类型标识和图标
        for (ProductLine root : roots) {
            root.setNodeType("PRODUCT_LINE");
            root.setIcon("ApartmentOutlined");
            buildChildren(root);
        }
        return roots;
    }

    @Override
    public List<ProductLine> findLinesOnlyTree() {
        List<ProductLine> roots = productLineMapper.selectRoots();
        for (ProductLine root : roots) {
            root.setNodeType("PRODUCT_LINE");
            root.setIcon("ApartmentOutlined");
            buildLinesOnlyChildren(root);
        }
        return roots;
    }

    /** 递归构建子节点（仅子系列，不含产品型号） */
    private void buildLinesOnlyChildren(ProductLine parent) {
        List<ProductLine> children = new ArrayList<>();
        List<ProductLine> subLines = productLineMapper.selectByParentOid(parent.getOid());
        for (ProductLine sub : subLines) {
            sub.setNodeType("PRODUCT_LINE");
            sub.setIcon("ApartmentOutlined");
            buildLinesOnlyChildren(sub);
            children.add(sub);
        }
        parent.setChildren(children);
    }

    @Override
    public List<ProductLine> findRoots() {
        return productLineMapper.selectRoots();
    }

    @Override
    public List<ProductLine> findChildren(String parentOid) {
        return productLineMapper.selectByParentOid(parentOid);
    }

    @Override
    public Map<String, Map<String, Integer>> getStats() {
        Map<String, Map<String, Integer>> result = new HashMap<>();

        // 子系列数量：List<Map> 转为 Map<oid, count>
        Map<String, Integer> childrenMap = new HashMap<>();
        for (Map<String, Object> row : productLineMapper.countChildrenGroupByParentOid()) {
            String oid = String.valueOf(row.get("parent_oid"));
            Integer cnt = ((Number) row.get("cnt")).intValue();
            childrenMap.put(oid, cnt);
        }

        // 产品型号数量：List<Map> 转为 Map<oid, count>
        Map<String, Integer> modelsMap = new HashMap<>();
        for (Map<String, Object> row : productLineMapper.countModelsGroupByProductLineOid()) {
            String oid = String.valueOf(row.get("oid"));
            Integer cnt = ((Number) row.get("cnt")).intValue();
            modelsMap.put(oid, cnt);
        }

        List<ProductLine> all = productLineMapper.selectAll();
        for (ProductLine pl : all) {
            Map<String, Integer> stat = new HashMap<>();
            String oid = pl.getOid();
            stat.put("childrenCount", childrenMap.getOrDefault(oid, 0));
            stat.put("modelCount", modelsMap.getOrDefault(oid, 0));
            result.put(oid, stat);
        }
        return result;
    }

    /** 递归构建子节点（children 存储在 transient children 字段中） */
    private void buildChildren(ProductLine parent) {
        List<ProductLine> children = new ArrayList<>();

        // 1. 加载子系列
        List<ProductLine> subLines = productLineMapper.selectByParentOid(parent.getOid());
        for (ProductLine sub : subLines) {
            sub.setNodeType("PRODUCT_LINE");
            sub.setIcon("ApartmentOutlined");
            buildChildren(sub);
            children.add(sub);
        }

        // 2. 加载产品型号（作为子节点）
        List<ProductModel> models = productModelMapper.selectByProductLineOid(parent.getOid());
        for (ProductModel model : models) {
            ProductLine modelNode = convertToTreeNode(model);
            children.add(modelNode);
        }

        parent.setChildren(children);
    }

    /** 将 ProductModel 转换为树节点（用于合并到 ProductLine 树中） */
    private ProductLine convertToTreeNode(ProductModel model) {
        ProductLine node = new ProductLine();
        node.setOid(model.getOid());
        node.setCode(model.getCode());
        node.setName(model.getName());
        node.setDescription(model.getDescription());
        node.setTeamOid(model.getTeamOid());
        node.setThumbnail(model.getThumbnail());
        node.setNodeType("PRODUCT_MODEL");
        node.setIcon("TagOutlined");  // 来自 TypeDefinitionInitializer 中 ProductModel 的图标
        return node;
    }

    // ===== 团队管理 =====

    /**
     * 确保产品线关联的团队存在，若不存在则自动创建并更新产品线。
     * <p>创建规则：团队编码为 "TEAM_{产品线code}"，名称为 "{产品线名称}研发团队"。
     *
     * @param pl 产品线持久化对象
     */
    private void ensureTeam(ProductLine pl) {
        if (pl.getTeamOid() != null) {
            // 校验 team 是否真实存在（防止数据不一致）
            if (teamMapper.selectByOid(pl.getTeamOid()) != null) {
                return;
            }
            // team_oid 有值但对应 team 记录已被删除，置空，继续向下尝试修复
        }
        // 按 code 查找是否已有匹配的 team（处理 ck_product_line.team_oid 为 NULL 但 team 记录已存在的情况）
        String teamCode = "TEAM_" + pl.getCode();
        Team existing = teamMapper.selectByCode(teamCode);
        if (existing != null) {
            // 已有 team 记录，直接关联并更新 ck_product_line
            pl.setTeamOid(existing.getOid());
            productLineMapper.update(pl);
            return;
        }
        // 确实没有 team，新建
        Team team = new Team();
        team.setCode(teamCode);
        team.setName(pl.getName() + "研发团队");
        team.setDescription(pl.getName() + "研发团队");
        teamMapper.insert(team);
        pl.setTeamOid(team.getOid());
        productLineMapper.update(pl);
    }

    @Override
    public Team getTeamByProductLineOid(String productLineOid) {
        ProductLine pl = productLineMapper.selectByOid(productLineOid);
        if (pl == null) {
            return null;
        }
        ensureTeam(pl);
        return teamMapper.selectByOid(pl.getTeamOid());
    }

    @Override
    public List<TeamMemberVO> getTeamMembers(String productLineOid) {
        ProductLine pl = productLineMapper.selectByOid(productLineOid);
        if (pl == null) {
            return new ArrayList<>();
        }
        ensureTeam(pl);

        // 获取自身团队成员
        List<TeamMemberVO> vos = buildMemberVOs(pl.getTeamOid(), false, null);

        // 用于去重的 userId 集合（自身成员优先）
        Set<String> seenUserIds = new HashSet<>();
        for (TeamMemberVO vo : vos) {
            seenUserIds.add(vo.getUsername());
        }

        // 获取祖先团队成员（标记为继承，去重）
        ProductLine parent = pl.getParentOid() != null ? productLineMapper.selectByOid(pl.getParentOid()) : null;
        while (parent != null) {
            ensureTeam(parent);  // 确保父级团队存在并关联（修复数据不一致）
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
        return vos;
    }

    /** 根据 teamOid 构建 TeamMemberVO 列表 */
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

    /** 检查用户是否存在于祖先团队的成员中 */
    private ProductLine findAncestorWithMember(String productLineOid, String userId) {
        ProductLine pl = productLineMapper.selectByOid(productLineOid);
        if (pl == null || pl.getParentOid() == null) return null;
        String current = pl.getParentOid();
        int safety = 0;
        while (current != null && safety < 100) {
            ProductLine ancestor = productLineMapper.selectByOid(current);
            if (ancestor == null) break;
            ensureTeam(ancestor);
            if (ancestor.getTeamOid() != null
                    && teamMemberMapper.existsByTeamAndUser(ancestor.getTeamOid(), userId)) {
                return ancestor;
            }
            current = ancestor.getParentOid();
            safety++;
        }
        return null;
    }

    @Override
    @Transactional
    public void addTeamMember(String productLineOid, String userId, String roleName) {
        ProductLine pl = productLineMapper.selectByOid(productLineOid);
        if (pl == null) {
            throw new IllegalArgumentException("产品线不存在");
        }
        // 团队不存在时自动创建
        ensureTeam(pl);
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (teamMemberMapper.existsByTeamAndUser(pl.getTeamOid(), userId.trim())) {
            throw new IllegalArgumentException("该用户已是团队成员");
        }
        // 拦截：已在父级团队中继承的成员无需重复添加
        ProductLine ancestor = findAncestorWithMember(productLineOid, userId.trim());
        if (ancestor != null) {
            throw new IllegalArgumentException("该用户已从父级产品线「" + ancestor.getName() + "」继承，无需重复添加");
        }
        TeamMember member = new TeamMember();
        member.setTeamOid(pl.getTeamOid());
        member.setUserId(userId.trim());
        member.setRoleName(roleName != null ? roleName : "");
        teamMemberMapper.insert(member);
    }

    @Override
    @Transactional
    public void removeTeamMember(String productLineOid, String userId) {
        ProductLine pl = productLineMapper.selectByOid(productLineOid);
        if (pl == null) {
            throw new IllegalArgumentException("产品线不存在");
        }
        ensureTeam(pl);
        if (pl.getTeamOid() == null) {
            return;
        }
        // 拦截：继承成员不可在子级删除
        ProductLine ancestor = findAncestorWithMember(productLineOid, userId);
        if (ancestor != null) {
            throw new IllegalArgumentException("该成员从父级产品线「" + ancestor.getName()
                    + "」继承，不可在本级移除，请在父级产品线中管理");
        }
        // 仅允许删除自身团队的成员
        teamMemberMapper.deleteByTeamAndUser(pl.getTeamOid(), userId);
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
