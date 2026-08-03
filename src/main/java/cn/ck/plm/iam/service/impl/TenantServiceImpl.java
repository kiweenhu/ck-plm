/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.impl;

import cn.ck.plm.iam.dto.TenantRegistrationRequest;
import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.entity.Tenant;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.RoleMapper;
import cn.ck.plm.iam.mapper.TenantMapper;
import cn.ck.plm.iam.service.api.NotificationService;
import cn.ck.plm.iam.service.api.TenantService;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.iam.util.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link TenantService} 的实现 —— 注册为 PENDING 状态，管理员审核通过后激活。
 */
@Service
public class TenantServiceImpl implements TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantServiceImpl.class);

    /** 租户管理员角色 code */
    private static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";

    private final TenantMapper tenantMapper;
    private final UserService userService;
    private final RoleMapper roleMapper;
    private final NotificationService notificationService;

    public TenantServiceImpl(TenantMapper tenantMapper, UserService userService,
                              RoleMapper roleMapper, NotificationService notificationService) {
        this.tenantMapper = tenantMapper;
        this.userService = userService;
        this.roleMapper = roleMapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public void register(TenantRegistrationRequest request) {
        validateRequest(request);
        String tenantId = request.getTenantId().trim();
        String username = request.getAdminUsername().trim();

        if (tenantMapper.existsByTenantId(tenantId)) {
            throw new IllegalArgumentException("租户标识 '" + tenantId + "' 已存在");
        }
        if (userService.existsByUsername(username)) {
            throw new IllegalArgumentException("管理员用户名 '" + username + "' 已存在");
        }

        // 1. 创建租户（状态为 PENDING）
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(request.getName() != null ? request.getName().trim() : tenantId);
        tenant.setContactName(request.getContactName());
        tenant.setContactEmail(request.getContactEmail());
        tenant.setAdminUsername(username);
        tenant.setAdminDisplayName(request.getAdminDisplayName() != null
                ? request.getAdminDisplayName().trim() : username);
        tenantMapper.insert(tenant);
        log.info("租户注册申请已提交: tenantId={}, name={}, status=PENDING", tenantId, tenant.getName());

        // 2. 创建租户管理员用户（enabled=false，locked=true，审核通过前不可登录）
        // 设置 TenantContext 确保拦截器正确填充 tenant_oid
        cn.ck.plm.base.util.TenantContext.set(tenant.getOid());
        try {
            User admin = new User(username, tenant.getAdminDisplayName());
            admin.setEmail(request.getContactEmail());
            admin.setTenantOid(tenant.getOid());
            admin.setEnabled(false);
            admin.setLocked(true);
            userService.createWithEncodedPassword(admin, PasswordEncoder.encode(request.getAdminPassword()));
            log.info("租户管理员用户已创建(待审核): username={}, tenantOid={}", username, tenant.getOid());

            // 3. 绑定 TENANT_ADMIN 角色
            Role tenantAdminRole = roleMapper.selectByCode(TENANT_ADMIN_ROLE_CODE);
            if (tenantAdminRole != null) {
                userService.assignRole(admin.getOid(), tenantAdminRole.getOid());
                log.info("已分配 TENANT_ADMIN 角色: user={}", username);
            }
        } finally {
            cn.ck.plm.base.util.TenantContext.clear();
        }

        // 4. 通知平台管理员审核
        notificationService.sendToAdmins(
                "新租户注册申请",
                "租户「" + tenant.getName() + "」(" + tenantId + ") 已提交注册申请，请尽快审核。",
                "TENANT_REGISTRATION",
                "TENANT",
                tenant.getOid()
        );
    }

    @Override
    public List<Tenant> listPending() {
        return tenantMapper.selectPending();
    }

    @Override
    public int countPending() {
        return tenantMapper.countPending();
    }

    @Override
    @Transactional
    public void approve(String tenantOid, String approvedBy) {
        Tenant tenant = tenantMapper.selectByOid(tenantOid);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在");
        }
        if (!tenant.isPending()) {
            throw new IllegalArgumentException("该租户不在待审核状态，当前状态: " + tenant.getStatus());
        }

        // 1. 激活租户状态
        tenant.setStatus(Tenant.STATUS_ACTIVE);
        tenant.setApprovedAt(LocalDateTime.now());
        tenant.setApprovedBy(approvedBy);
        tenantMapper.update(tenant);

        // 2. 激活租户管理员用户（设置 TenantContext 确保能查到该租户下的用户）
        cn.ck.plm.base.util.TenantContext.set(tenant.getOid());
        try {
            User admin = userService.findByUsername(tenant.getAdminUsername());
            if (admin != null) {
                admin.setEnabled(true);
                admin.setLocked(false);
                userService.update(admin);
                log.info("租户管理员已激活: username={}", tenant.getAdminUsername());
            } else {
                // 兼容存量数据：管理员用户未在注册时创建，此处补建
                log.warn("租户管理员用户不存在，补建: username={}", tenant.getAdminUsername());
                String password = tenant.getAdminPassword();
                if (password == null || password.isEmpty()) {
                    password = "123456";
                }
                admin = new User(tenant.getAdminUsername(), tenant.getAdminDisplayName());
                admin.setEmail(tenant.getContactEmail());
                admin.setTenantOid(tenant.getOid());
                admin.setEnabled(true);
                admin.setLocked(false);
                userService.createWithEncodedPassword(admin, password);
                Role tenantAdminRole = roleMapper.selectByCode(TENANT_ADMIN_ROLE_CODE);
                if (tenantAdminRole != null) {
                    userService.assignRole(admin.getOid(), tenantAdminRole.getOid());
                }
            }
        } finally {
            cn.ck.plm.base.util.TenantContext.clear();
        }

        log.info("租户审核通过: tenantId={}, oid={}, approvedBy={}", tenant.getTenantId(), tenant.getOid(), approvedBy);
    }

    @Override
    @Transactional
    public void reject(String tenantOid, String rejectedBy, String reason) {
        Tenant tenant = tenantMapper.selectByOid(tenantOid);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在");
        }
        if (!tenant.isPending()) {
            throw new IllegalArgumentException("该租户不在待审核状态，当前状态: " + tenant.getStatus());
        }

        // 1. 驳回租户
        tenant.setStatus(Tenant.STATUS_REJECTED);
        tenant.setRejectReason(reason);
        tenant.setApprovedBy(rejectedBy);
        tenant.setApprovedAt(LocalDateTime.now());
        tenantMapper.update(tenant);

        // 2. 禁用管理员用户（设置 TenantContext 确保能查到）
        cn.ck.plm.base.util.TenantContext.set(tenant.getOid());
        try {
            User admin = userService.findByUsername(tenant.getAdminUsername());
            if (admin != null) {
                admin.setEnabled(false);
                userService.update(admin);
            }
        } finally {
            cn.ck.plm.base.util.TenantContext.clear();
        }

        log.info("租户审核驳回: tenantId={}, reason={}, rejectedBy={}",
                tenant.getTenantId(), reason, rejectedBy);
    }

    @Override
    public Tenant getByOid(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return null;
        }
        return tenantMapper.selectByOid(oid);
    }

    @Override
    public Tenant getByTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return null;
        }
        return tenantMapper.selectByTenantId(tenantId);
    }

    @Override
    public List<Tenant> listActive() {
        return tenantMapper.selectActive();
    }

    private void validateRequest(TenantRegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("注册信息不能为空");
        }
        if (request.getTenantId() == null || request.getTenantId().trim().isEmpty()) {
            throw new IllegalArgumentException("租户标识不能为空");
        }
        if (!request.getTenantId().trim().matches("^[a-zA-Z0-9][a-zA-Z0-9_-]{1,48}$")) {
            throw new IllegalArgumentException("租户标识只允许字母/数字/下划线/连字符，长度 2-49，以字母或数字开头");
        }
        if (request.getAdminUsername() == null || request.getAdminUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("管理员用户名不能为空");
        }
        if (!request.getAdminUsername().trim().matches("^[a-zA-Z0-9][a-zA-Z0-9._@-]{2,49}$")) {
            throw new IllegalArgumentException("管理员用户名只允许字母/数字/点/下划线/@/连字符，长度 3-50，以字母或数字开头");
        }
        if (request.getAdminPassword() == null || request.getAdminPassword().length() < 4) {
            throw new IllegalArgumentException("管理员密码至少 4 位");
        }
    }
}
