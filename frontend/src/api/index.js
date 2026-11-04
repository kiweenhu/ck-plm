import axios from 'axios'
import { message } from 'ant-design-vue'

/** axios 实例 —— 统一配置 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

/* 请求拦截器 —— 自动附带 token */
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

/* 响应拦截器 —— 统一错误处理 */
request.interceptors.response.use(
  response => {
    const res = response.data
    /**
     * 只有"长得像统一包装"的响应才按 { code, message, data } 判成败。
     *
     * <p>判据是 <b>code 与 data 两个键同时存在</b>，缺一不可：
     * <ul>
     *   <li>有些接口返回的是<b>裸实体</b>（如 BomDiff），实体自己也带 code 字段
     *       （BomLinks.code 是编码）；只要写死 `res.code !== 200`，HTTP 200 的成功请求
     *       也会被弹成「请求失败」—— BOM 对比就踩过这个坑；</li>
     *   <li>真正的包装体一定有 data 键（值可能是 null），据此区分不会漏掉真实错误
     *       （`{code:500, message, data:null}` 照样会被提示）。</li>
     * </ul>
     */
    const enveloped = res && typeof res === 'object' && 'code' in res && 'data' in res
    if (enveloped && res.code !== 200) {
      message.error(res.message || '请求失败')
    }
    return res
  },
  error => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('tokenExpireAt')
        localStorage.removeItem('user')
        window.location.replace('/login')
        return Promise.reject(error)
      }
      message.error(data?.message || `服务器错误 (${status})`)
    } else if (error.code === 'ECONNABORTED') {
      message.error('请求超时，请稍后重试')
    } else {
      message.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

// ==================== 认证 API ====================

/**
 * 登录
 * @param {string} username
 * @param {string} password
 * @returns {Promise<{code, message, data: {token, username, displayName, roles}}>}
 */
export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

/**
 * 验证 token 有效性（路由守卫用）
 * @returns {Promise<{code, message, data: {token, username, displayName, roles}}>}
 */
export function verifyToken() {
  return request.get('/auth/verify')
}

/**
 * 登出
 * @returns {Promise}
 */
export function logout() {
  return request.post('/auth/logout')
}

/**
 * 注册租户（公开接口，无需登录）
 */
export function registerTenant(data) {
  return request.post('/tenants/register', data)
}

// ==================== 通知 API ====================

/** 获取未读通知数 */
export function getUnreadCount() {
  return request.get('/notifications/unread-count')
}

/**
 * 获取通知列表。
 *
 * @param {number} limit 条数
 * @param {boolean} unreadOnly 只看未读（在库里过滤，不是取一批再筛）
 * @param {string=} type 只看某一类（如 'ANNOUNCEMENT' → 企业公告页）
 */
export function getNotifications(limit = 20, unreadOnly = false, type = undefined) {
  return request.get('/notifications', { params: { limit, unreadOnly, type } })
}

// ==================== CK-PLM 助手 API ====================

/**
 * 助手状态：是否启用、是否配置完整、可选模型清单、认证方式。
 *
 * @returns {Promise<{code, data: {
 *   enabled: boolean, configured: boolean, state: 'ready'|'disabled'|'no-models',
 *   hint: string|null, defaultModel: string, model: string, authMode: string,
 *   models: Array<{ id, label, model, authMode }>
 * }}>}
 *   models  后端"实际可用"的清单（没填 base-url 或已停用的不会返回），前端下拉直接用；
 *   state   为什么不能用 —— disabled=总开关关着（改 plm.ai.enabled）/ no-models=没有可用模型；
 *   hint    直接可显示的一句说明（已包含"该改哪个配置键"），前端不要自己拼文案；
 *   configured=false 表示还没接上（提示一句，入口照常可用）。
 */
export function getAiStatus() {
  return request.get('/ai/status')
}

/**
 * 与助手聊一轮。工具查询以「当前登录用户」的身份执行，看到的数据与本人手动查的一致。
 *
 * @param {object} data { message, history: [{ role, content }], modelId? }
 *   modelId 可空 = 用后端的 default-model；传了没配置的 id，后端会明确回一句"没有这个模型"，
 *   而不是静默换一个模型回答
 * @returns {Promise<{code, data: {answer, tools: string[], configured: boolean}}>}
 */
export function askAi(data) {
  return request.post('/ai/chat', data)
}

/**
 * 发布公告（管理员）—— 发给发布人所在租户的全部启用用户。
 *
 * @param {object} data { title, content }
 * @returns {Promise<{code, data: number}>} data = 实际送达人数
 */
export function publishAnnouncement(data) {
  return request.post('/notifications/announcement', data)
}

/** 标记已读 */
export function markNotifRead(oid) {
  return request.put(`/notifications/${oid}/read`)
}

/** 全部已读 */
export function markAllNotifRead() {
  return request.put('/notifications/read-all')
}

// ==================== 租户审核 API ====================

/** 获取待审核租户列表 */
export function getPendingTenants() {
  return request.get('/tenants/pending')
}

/** 待审核数量 */
export function getPendingCount() {
  return request.get('/tenants/pending/count')
}

/** 审核通过 */
export function approveTenant(oid) {
  return request.put(`/tenants/${oid}/approve`)
}

/** 驳回 */
export function rejectTenant(oid, reason) {
  return request.put(`/tenants/${oid}/reject`, { reason })
}

/** 获取当前用户所属租户信息 */
/** 获取所有已激活租户列表（平台管理员专用） */
export function getActiveTenants() {
  return request.get('/tenants/active')
}

export function getCurrentTenant() {
  return request.get('/tenants/current')
}

export function updateTenant(oid, data) {
  return request.put(`/tenants/${oid}`, data)
}

// ==================== 租户管理 API ====================

/** 获取组织树 */
export function getOrgTree() {
  return request.get('/orgs', { params: { tree: true } })
}

/** 获取组织列表 */
export function getOrgList(params) {
  return request.get('/orgs', { params })
}

/** 创建组织 */
export function createOrg(data) {
  return request.post('/orgs', data)
}

/** 更新组织 */
export function updateOrg(oid, data) {
  return request.put(`/orgs/${oid}`, data)
}

/** 删除组织 */
export function deleteOrg(oid) {
  return request.delete(`/orgs/${oid}`)
}

// ==================== 用户管理 API ====================

/** 查询全部用户（支持 keyword、orgOid 可选过滤） */
export function getAllUsers(params = {}) {
  return request.get('/users', { params })
}

/** 按组织查询用户 */
export function getUsersByOrg(orgOid) {
  return request.get('/users', { params: { orgOid } })
}

/** 创建用户 */
export function createUser(data) {
  return request.post('/users', data)
}

/** 更新用户 */
export function updateUser(oid, data) {
  return request.put(`/users/${oid}`, data)
}

/** 删除用户 */
export function deleteUser(oid) {
  return request.delete(`/users/${oid}`)
}

/** 重置用户密码 */
export function resetPassword(oid, newPassword) {
  return request.put(`/users/${oid}/reset-password`, { newPassword })
}

// ==================== 个人信息 API ====================

/** 获取当前登录用户信息 */
export function getCurrentUser() {
  return request.get('/auth/me')
}

/** 更新个人资料（displayName / email / phone） */
export function updateProfile(data) {
  return request.put('/auth/profile', data)
}

/** 当前用户修改密码 */
export function changePassword(oldPassword, newPassword) {
  return request.put('/auth/password', { oldPassword, newPassword })
}

/** 退出其他设备：保留当前会话，其余会话立即失效（返回 { removed: 数量 }） */
export function logoutOtherDevices() {
  return request.post('/auth/logout-others')
}

/** 查询某用户的角色明细（含中文名，个人设置里展示角色用） */
export function getUserRoles(oid) {
  return request.get(`/users/${oid}/roles`)
}

// ==================== 类型定义 API（v2.0 统一） ====================

/** 获取类型树 */
export function getTypeDefinitionTree() {
  return request.get('/type-definitions/tree')
}

/** 获取 OOTB 根类型 | 全部 */
export function getTypeDefinitions(params = {}) {
  return request.get('/type-definitions', { params })
}

/** 获取类型详情 */
export function getTypeDefinition(oid) {
  return request.get(`/type-definitions/${oid}`)
}

/** 创建类型定义 */
export function createTypeDefinition(data) {
  return request.post('/type-definitions', data)
}

/** 更新类型定义 */
export function updateTypeDefinition(oid, data) {
  return request.put(`/type-definitions/${oid}`, data)
}

/** 删除类型定义 */
export function deleteTypeDefinition(oid) {
  return request.delete(`/type-definitions/${oid}`)
}

// --- IBA 属性 ---

/** 获取 IBA 列表 */
export function getIBAList(params = {}) {
  return request.get('/ibas', { params })
}

/** 创建 IBA */
export function createIBA(data) {
  return request.post('/ibas', data)
}

/** 更新 IBA */
export function updateIBA(oid, data) {
  return request.put(`/ibas/${oid}`, data)
}

/** 删除 IBA */
export function deleteIBA(oid) {
  return request.delete(`/ibas/${oid}`)
}

// --- 类型-IBA 映射 ---

/** 获取类型关联的 IBA 映射列表（支持 ModelClass / SoftType） */
export function getOwnerMappings(ownerOid, entityCode) {
  return request.get('/ibas/mappings', { params: { typeOid: ownerOid, entityCode } })
}

/** 获取类型关联的 IBA 映射列表 */
export function getTypeMappings(typeOid) {
  return request.get('/ibas/mappings', { params: { typeOid } })
}

/** 获取类型关联的 IBA 列表 */
export function getTypeIBAs(typeOid) {
  return request.get(`/ibas/types/${typeOid}`)
}

/** 获取未分配的 IBA（支持 ownerType） */
export function getUnassignedIBAs(typeOid, keyword) {
  return request.get('/ibas/unassigned', { params: { typeOid, keyword } })
}

/** 获取未分配的 IBA（带 entityCode） */
export function getUnassignedIBAsForOwner(ownerOid, entityCode, keyword) {
  return request.get('/ibas/unassigned', { params: { typeOid: ownerOid, entityCode, keyword } })
}

/** 分配单个 IBA */
export function assignIBA(data) {
  return request.post('/ibas/mappings', data)
}

/** 批量分配 IBA */
export function batchAssignIBAs(typeOid, ibaOids) {
  return request.post('/ibas/batch-assign', { typeOid, ibaOids })
}

/** 批量分配 IBA（带 entityCode） */
export function batchAssignIBAsForOwner(ownerOid, entityCode, ibaOids) {
  return request.post('/ibas/batch-assign', { typeOid: ownerOid, entityCode, ibaOids })
}

/** 更新 IBA 映射（覆写 required / defaultValue） */
export function updateIBAMapping(mappingOid, data) {
  return request.put(`/ibas/mappings/${mappingOid}`, data)
}

/** 移除 IBA 关联 */
export function removeIBAMapping(mappingOid) {
  return request.delete(`/ibas/mappings/${mappingOid}`)
}

/** 递归获取类型的继承属性映射（祖先类型的 IBA） */
export function getInheritedMappings(typeOid, entityCode) {
  return request.get('/ibas/inherited', { params: { typeOid, entityCode } })
}

/** 查询某实体的 IBA 属性值 */
export function getEntityIbaData(entityType, entityOid) {
  return request.get('/ibas/data', { params: { entityType, entityOid } })
}

// ==================== 类型-分类关联 API ====================

/** 获取某类型绑定的分类 */
export function getTypeClassificationLink(typeOid) {
  return request.get(`/type-definitions/${typeOid}/classification`)
}

/** 为类型绑定分类 */
export function bindTypeClassification(typeOid, classificationOid) {
  return request.post(`/type-definitions/${typeOid}/classification`, { classificationOid })
}

/** 解除类型的分类绑定 */
export function unbindTypeClassification(typeOid) {
  return request.delete(`/type-definitions/${typeOid}/classification`)
}

/** 获取某类型绑定的分类子树（仅包含绑定节点及其后代，用于限制选择范围） */
export function getTypeClassificationSubtree(typeOid) {
  return request.get(`/type-definitions/${typeOid}/classification-subtree`)
}

/** 获取以指定 oid 为根的子树 */
export function getClassificationSubtree(rootOid) {
  return request.get(`/classifications/subtree/${rootOid}`)
}

// ==================== 属性定义 API ====================

/** 获取实体的属性定义列表（系统 + IBA），可选传入 entityOid/entityType 以动态合并 type_iba 关联 */
export function getAttributeDefinitions(entityName, entityOid, entityType) {
  const params = { entityName }
  if (entityOid) params.entityOid = entityOid
  if (entityType) params.entityType = entityType
  return request.get('/attribute-definitions', { params })
}

/** 更新单个属性定义布局配置 */
export function updateAttributeDefinition(oid, data) {
  return request.put(`/attribute-definitions/${oid}`, data)
}

/** 批量更新属性定义布局配置 */
export function batchUpdateAttributeLayout(data) {
  return request.put('/attribute-definitions/batch-layout', data)
}

// ==================== 页面布局 API（低代码页面设计器） ====================

/** 获取实体的操作摘要列表（含系统预置 + 自定义） */
export function getOperationList(entityOid, entityCode) {
  return request.get('/page-layouts/operations', { params: { entityOid, entityCode } })
}

/** 查询实体的全部页面布局列表 */
export function getPageLayouts(entityOid, entityCode) {
  return request.get('/page-layouts/all', { params: { entityOid, entityCode } })
}

/** 查询某实体某操作的页面布局 */
export function getPageLayout(entityOid, operationCode) {
  return request.get('/page-layouts', { params: { entityOid, operationCode } })
}

/** 根据实体编码 + 操作码查询页面布局（自动匹配） */
export function getPageLayoutByCode(entityCode, operationCode) {
  return request.get('/page-layouts/by-code', { params: { entityCode, operationCode } })
}

/** 保存或更新页面布局 */
export function savePageLayout(data) {
  return request.post('/page-layouts', data)
}

/** 克隆「根类型布局」到当前类型（本租户内：当前类型的 root_type_code 对应类型的同操作布局） */
export function clonePageLayout(data) {
  return request.post('/page-layouts/clone', data)
}

/** 删除页面布局 */
export function deletePageLayout(entityOid, operationCode) {
  return request.delete('/page-layouts', { params: { entityOid, operationCode } })
}

// ==================== 流程表单模板 API（业务配置 → 流程表单） ====================

/**
 * 全部流程表单模板（平台内置 + 本租户自定义）。
 *
 * <p>内置模板由后端启动器自动登记（属平台租户），所以这个列表里天然包含它们在列 ——
 * 不需要前端再拼一份"内置清单"（那样两处必然漂移）。
 */
export function listFormTemplates() {
  return request.get('/form-templates')
}

/** 某节点类型在设计期可选的模板（仅启用项；设计器下拉用） */
export function listFormTemplatesForNode(nodeType) {
  return request.get('/form-templates/for-node', { params: { nodeType } })
}

/** 新建自定义流程表单模板 */
export function createFormTemplate(data) {
  return request.post('/form-templates', data)
}

/** 修改流程表单模板（内置模板只允许改名称/说明/启用/排序） */
export function updateFormTemplate(oid, data) {
  return request.put(`/form-templates/${oid}`, data)
}

/** 删除流程表单模板（内置模板不可删除） */
export function deleteFormTemplate(oid) {
  return request.delete(`/form-templates/${oid}`)
}

// ==================== 角色管理 API（admin 模块） ====================

/** 获取角色列表 */
export function getRoles(keyword) {
  return request.get('/roles', { params: keyword ? { keyword } : {} })
}

/** 获取角色详情 */
export function getRole(oid) {
  return request.get(`/roles/${oid}`)
}

/** 创建角色 */
export function createRole(data) {
  return request.post('/roles', data)
}

/** 更新角色 */
export function updateRole(oid, data) {
  return request.put(`/roles/${oid}`, data)
}

/** 删除角色 */
export function deleteRole(oid) {
  return request.delete(`/roles/${oid}`)
}

/** 获取平台级角色列表 */
export function getPlatformRoles() {
  return request.get('/roles/platform')
}

/** 获取角色的成员列表 */
export function getRoleMembers(roleOid) {
  return request.get(`/roles/${roleOid}/members`)
}

/** 为角色添加成员 */
export function addRoleMember(roleOid, userOid) {
  return request.post(`/roles/${roleOid}/members`, { userOid })
}

/** 从角色移除成员 */
export function removeRoleMember(roleOid, userOid) {
  return request.delete(`/roles/${roleOid}/members/${userOid}`)
}

// 注：原 getAdminMembers（GET /roles/admin-members，写死 TENANT_ADMIN）已随
// 「企业管理员」页改造成「角色成员」页退役 —— 角色成员改用通用的
// getRoles() + getRoleMembers(roleOid)（见上方角色管理 API），后端端点保留。

// ==================== 产品线管理 API ====================

/**
 * 批量查询「我能否维护这些系列/型号」（阶段 / 团队 / 编辑 / 删除）。
 *
 * <p>2026-10-17 用户要求：这四类操作只有产品经理（团队角色）能做，管理员豁免。
 * 列表页用它控制行按钮显隐；后端各写接口仍会独立校验（canManageUnder），这里只管显示。
 * 系列 oid 与型号 oid 都支持（型号判定继承父系列团队）。
 *
 * @param {string[]} oids 系列 / 型号 oid 清单
 * @returns {Promise<{code:number, data:Object<string,boolean>}>} oid → 是否可维护
 */
export function getMyManagePermissions(oids) {
  return request.get('/product-lines/my-manage-permissions', {
    params: { oids: (oids || []).join(',') },
  })
}

/** 获取产品线列表 */
export function getProductLines(keyword) {
  return request.get('/product-lines', { params: keyword ? { keyword } : {} })
}

/** 获取产品线详情 */
export function getProductLine(oid) {
  return request.get(`/product-lines/${oid}`)
}

/**
 * 根据实体编码 + OID 获取实体详情（统一框架入口）
 * 用于 DynamicForm 等通用组件的 edit 场景自动加载实体数据
 *
 * 走服务端统一入口：由后端按 type_definition.root_type_code 路由到宿主读取策略，
 * 与创建（POST /softtype-instances）完全对称。前端因此不再需要为「读取」维护
 * 「类型 → 端点」映射 —— 新增软类型（FOOTPRINT / SYMBOL 等）无需改前端即可读详情。
 *
 * @param {string} entityCode 实体编码，如 PRODUCT_LINE / FOOTPRINT
 * @param {string} oid 实体 ID
 * @param {object} [params] 额外查询参数（如 { iterationOid } 用于查看历史版本）
 */
export function getEntityByCode(entityCode, oid, params) {
  return request.get(`/softtype-instances/${oid}`, {
    params: { ...(params || {}), typeDefinitionCode: entityCode },
  })
}

/**
 * 「设置生命周期状态」的候选项 —— 依据该类型<b>当前配置</b>的生命周期模板。
 *
 * <p>候选一律来自业务配置的模板（设置状态时会先把历史对象切到新模板）；
 * 对象仍绑着旧模板 / 未绑定时，响应用 templateChanged / templateBindRequired
 * 标记"本次会先切换"，并用 migration* 说明切换后当前状态会变成什么。
 *
 * 返回 { templateCode, templateName, current, initial, states:[{code,name,reachable,reason}] }。
 * 与执行侧（后端 moveToState / moveToInitialState）用同一份模板，不会"界面让选、后端拒收"。
 */
export function getLifecycleStateOptions(oid, typeDefinitionCode) {
  return request.get(`/softtype-instances/${oid}/lifecycle-states`, {
    params: { typeDefinitionCode },
  })
}

/**
 * 设置生命周期状态。
 *
 * @param {object} data { typeDefinitionCode, mode, targetStateCode?, migrateTemplate? }
 *   mode=INITIAL：退回模板初始状态（沿模板回退规则逐跳，如 已发布 → 工作中 → 草稿）
 *   mode=SPECIFIED：设到 targetStateCode（要求当前状态能一步迁到它）
 *   migrateTemplate=true：先把对象从固化的旧模板迁到业务配置的新模板，再设置状态（同一事务）
 */
export function setLifecycleState(oid, data) {
  return request.post(`/softtype-instances/${oid}/lifecycle-state`, data)
}

/**
 * 解析实体编码 → API 路径（未登记时按 code 小写转连字符推断）。
 *
 * <p>供通用组件按「记录所属实体」分派接口。例如行操作组件可据
 * ENG_DOCUMENT / FOOTPRINT / SYMBOL → 'eng-documents' 判定走工程数据接口。
 *
 * <p><b>适用范围</b>：仅用于创建/读取之外的写操作（更新、删除、历史版本等）
 * 分派 —— 创建走 {@code createSoftTypeInstance}、读取（详情）走
 * {@code getEntityByCode}，二者均已统一到 {@code /softtype-instances}。
 *
 * @param {string} entityCode 实体编码，如 PART / ENG_DOCUMENT / FOOTPRINT
 * @returns {string} API 路径片段，如 'parts' / 'eng-documents'
 */
export function resolveEntityApiPath(entityCode) {
  if (!entityCode) return ''
  return ENTITY_API_PATH[entityCode] || entityCode.toLowerCase().replace(/_/g, '-')
}

/**
 * 实体编码 → API 路径映射。
 *
 * <p>仅服务于更新/删除/历史版本等尚未统一的写操作分派；
 * 创建与详情读取已由 {@code /softtype-instances} 统一路由，不再依赖本表。
 */
const ENTITY_API_PATH = {
  PRODUCT_LINE: 'product-lines',
  PRODUCT_MODEL: 'product-models',
  DOCUMENT: 'documents',
  PART: 'parts',
  FUNCTIONAL: 'functionals',
  // Part 子类型（SOFT_TYPE）共用 ck_part 表，按 typeDefinitionCode 区分，均走 parts 端点
  ELECTRONIC: 'parts',
  STRUCTURAL: 'parts',
  ELECTRICAL: 'parts',
  SOFTWARE: 'parts',
  PCBA: 'parts',
  // 文档子类型（SOFT_TYPE）共用 ck_document 表，按 typeDefinitionCode 区分，均走 documents 端点
  'SUMMARY-SOLUATION': 'documents',
  // 工程数据（OOTB）与其子类型（SOFT_TYPE）共用 ck_eng_document 表，均走 eng-documents 端点
  ENG_DOCUMENT: 'eng-documents',
  FOOTPRINT: 'eng-documents',
  SYMBOL: 'eng-documents',
}

/** 创建产品线 */
export function createProductLine(data) {
  return request.post('/product-lines', data)
}

/** 更新产品线 */
export function updateProductLine(oid, data) {
  return request.put(`/product-lines/${oid}`, data)
}

/**
 * 读取容器（产品系列 / 型号）的目标成本。
 *
 * <p>系列与型号共用这条路由 —— 后端按 oid 自己判是哪张表，
 * 返回 `{ ownerType: 'LINE' | 'MODEL', targetCost: number | null }`。
 * 目标成本是 IBA 属性（TARGET_COST），所以不在产品线的普通字段里。
 */
export function getTargetCost(oid) {
  return request.get(`/product-lines/${oid}/target-cost`)
}

/** 设置目标成本（单位：元）；targetCost 传 null 表示清除 */
export function setTargetCost(oid, targetCost) {
  return request.put(`/product-lines/${oid}/target-cost`, { targetCost })
}

/**
 * 读取容器（产品系列 / 型号）的目标上市日期。
 *
 * <p>返回 `{ ownerType, targetLaunchDate: 'YYYY-MM-DD' | null }`；
 * 指标「上市时间偏差」用它和 LDCP（上市决策评审）的通过日相减。
 */
export function getTargetLaunchDate(oid) {
  return request.get(`/product-lines/${oid}/target-launch-date`)
}

/** 设置目标上市日期（yyyy-MM-dd）；传 null 表示清除 */
export function setTargetLaunchDate(oid, targetLaunchDate) {
  return request.put(`/product-lines/${oid}/target-launch-date`, { targetLaunchDate })
}

/** 删除产品线 */
export function deleteProductLine(oid) {
  return request.delete(`/product-lines/${oid}`)
}

/** 查询回收站中的产品系列（逻辑删除） */
export function listDeletedProductLines() {
  return request.get('/product-lines/deleted')
}

/** 恢复产品系列（从回收站） */
export function restoreProductLine(oid) {
  return request.post(`/product-lines/${oid}/restore`)
}

/** 获取产品线关联的团队 */
export function getProductLineTeam(oid) {
  return request.get(`/product-lines/${oid}/team`)
}

/** 获取团队成员列表 */
export function getTeamMembers(productLineOid) {
  return request.get(`/product-lines/${productLineOid}/team/members`)
}

/** 添加团队成员 */
export function addTeamMember(productLineOid, userId, roleName) {
  return request.post(`/product-lines/${productLineOid}/team/members`, { userId, roleName })
}

/** 移除团队成员 */
export function removeTeamMember(productLineOid, userId) {
  return request.delete(`/product-lines/${productLineOid}/team/members/${userId}`)
}

/** 获取产品线树（嵌套 children 结构，含子系列 + 产品型号） */
export function getProductLineTree() {
  return request.get('/product-lines/tree')
}

/** 获取纯产品系列树（仅子系列，不含产品型号，用于 product-line-select 控件） */
export function getProductLineTreeLinesOnly() {
  return request.get('/product-lines/tree-lines-only')
}

/** 获取根节点产品线列表 */
export function getProductLineRoots() {
  return request.get('/product-lines/roots')
}

/** 获取指定节点的子产品线 */
export function getProductLineChildren(parentOid) {
  return request.get(`/product-lines/children/${parentOid}`)
}

/** 批量获取产品线统计（子系列 + 产品型号数量） */
export function getProductLineStats() {
  return request.get('/product-lines/stats')
}

// ==================== 研发阶段 API ====================

/**
 * 获取某个容器（产品系列/型号、企业资源库子库）的阶段列表。
 *
 * <p>后端按 `ck_stage.owner_oid` 过滤，并不限于"产品线"：资源库也有自己的阶段
 * （如「封装·图符库」的「封装图符」），它的文件夹就挂在该阶段下 ——
 * 所以入参的语义是"阶段的归属者"。实测 `/product-lines/{资源库 oid}/stages` 可正常返回。
 */
export function getStages(ownerOid) {
  return request.get(`/product-lines/${ownerOid}/stages`)
}

/** 为产品线初始化默认阶段 */
export function initDefaultStages(productLineOid) {
  return request.post(`/product-lines/${productLineOid}/stages/init`)
}

/** 更新阶段信息 */
export function updateStage(ownerOid, stageOid, data) {
  return request.put(`/product-lines/${ownerOid}/stages/${stageOid}`, data)
}

/** 切换阶段在仪表盘的显示状态 */
export function toggleStageShowOnDashboard(ownerOid, stageOid, showOnDashboard) {
  return request.put(`/product-lines/${ownerOid}/stages/${stageOid}/show-on-dashboard`, { showOnDashboard })
}

// ==================== 产品型号 API ====================

/** 获取产品型号列表（支持 productLineOid 过滤） */
export function getProductModels(params = {}) {
  return request.get('/product-models', { params })
}

/** 获取产品型号详情 */
export function getProductModel(oid) {
  return request.get(`/product-models/${oid}`)
}

/** 创建产品型号 */
export function createProductModel(data) {
  return request.post('/product-models', data)
}

/** 更新产品型号 */
export function updateProductModel(oid, data) {
  return request.put(`/product-models/${oid}`, data)
}

/** 删除产品型号 */
export function deleteProductModel(oid) {
  return request.delete(`/product-models/${oid}`)
}

/** 查询回收站中的产品型号（逻辑删除） */
export function listDeletedProductModels() {
  return request.get('/product-models/deleted')
}

/** 恢复产品型号（从回收站） */
export function restoreProductModel(oid) {
  return request.post(`/product-models/${oid}/restore`)
}

/** 获取产品型号关联的团队 */
export function getProductModelTeam(oid) {
  return request.get(`/product-models/${oid}/team`)
}

/** 获取产品型号团队成员列表 */
export function getProductModelTeamMembers(oid) {
  return request.get(`/product-models/${oid}/team/members`)
}

/** 添加产品型号团队成员 */
export function addProductModelTeamMember(oid, userId, roleName) {
  return request.post(`/product-models/${oid}/team/members`, { userId, roleName })
}

/** 移除产品型号团队成员 */
export function removeProductModelTeamMember(oid, userId) {
  return request.delete(`/product-models/${oid}/team/members/${userId}`)
}

// ==================== 文件存储 API（通用上传 → Media） ====================

/**
 * 通用文件上传（底层使用 MediaController）。
 *
 * 上传后返回 `{ oid, storagePath, fileSize, mimeType, ... }`。
 * <ul>
 *   <li>图片上传 / 缩略图 → 调用方使用返回的 `storagePath` 直接展示</li>
 *   <li>附件上传 → 调用方将 `{name, size, path}` 序列化为 JSON 存入业务字段</li>
 * </ul>
 *
 * @param {File} file - 浏览器 File 对象
 * @param {string} [description] - 可选描述
 * @returns {Promise<{code:number, data: {oid:string, storagePath:string, fileSize:number, mimeType:string, originalName:string}}>}
 */
function uploadFile(file, description) {
  const formData = new FormData()
  formData.append('file', file)
  if (description) formData.append('description', description)
  return request.post('/media/upload', formData, {
    timeout: 60000,
    transformRequest: [(data, headers) => {
      delete headers['Content-Type']
      return data
    }],
  })
}

/** 通用文件上传别名（保持向后兼容） */
export const uploadMedia = uploadFile

/** CKFile 主文档文件上传 */
export function uploadCKFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/ckfiles/upload', formData, {
    timeout: 60000,
    transformRequest: [(data, headers) => {
      delete headers['Content-Type']
      return data
    }],
  })
}

/** 查询 CKFile 文件信息 */
export function getCKFile(oid) {
  return request.get(`/ckfiles/${oid}`)
}

/** CKFile 从 URL 创建网络资源主文档 */
export function createCKFileFromUrl(url) {
  return request.post('/ckfiles/url', { url })
}

/** CKAttachment 附件上传 */
export function uploadAttachment(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/attachments/upload', formData, {
    timeout: 60000,
    transformRequest: [(data, headers) => {
      delete headers['Content-Type']
      return data
    }],
  })
}

/** 查询 CKAttachment 附件信息 */
export function getAttachment(oid) {
  return request.get(`/attachments/${oid}`)
}

/**
 * 附件的下载地址（浏览器直接打开即触发下载）。
 *
 * <p>与 getDocumentDownloadUrl 同一口径：不走 axios，交给 <a href> / window.open ——
 * 走 axios 只能拿到二进制，还得自己造 Blob 再起文件名，中文名容易丢。
 */
export function getAttachmentDownloadUrl(oid) {
  return `/api/attachments/${oid}/download`
}

// ==================== 图片空间 API（Media 实体） ====================

/**
 * 获取图片空间列表。
 * @param {string} [keyword] - 搜索关键词（匹配 originalName / description）
 * @returns {Promise<{code:number, data:Array<{oid:string, originalName:string, fileName:string, fileSize:number, storagePath:string, mimeType:string, description:string, createdAt:string}>}>}
 */
export function getMediaList(keyword) {
  return request.get('/media', { params: keyword ? { keyword } : {} })
}

/**
 * 获取单张图片详情。
 * @param {string} oid - 图片 oid
 */
export function getMedia(oid) {
  return request.get(`/media/${oid}`)
}

/**
 * 更新图片描述信息。
 * @param {string} oid - 图片 oid
 * @param {{description?:string}} data - 更新数据
 */
export function updateMedia(oid, data) {
  return request.put(`/media/${oid}`, data)
}

/**
 * 删除图片（同时删除磁盘文件 + 数据库记录）。
 * @param {string} oid - 图片 oid
 */
export function deleteMedia(oid) {
  return request.delete(`/media/${oid}`)
}

/**
 * 批量检查图片是否被业务对象引用（产品线缩略图等）。
 * @param {string[]} oids - 图片 oid 数组
 * @returns {Promise<{code:number, data:Object<string,boolean>}>} key=oid, value=是否被引用
 */
export function checkMediaUsage(oids) {
  return request.post('/media/check-usage', oids)
}

// ==================== 附件 API（CKAttachment 实体 -- 待后端完善） ====================

/**
 * @typedef {Object} CKAttachment
 * @property {string} oid - 附件唯一标识
 * @property {string} ownerOid - 所属业务对象 oid（可指向 DocumentIteration / PartIteration / CR 等）
 * @property {string} fileName - 原始文件名（含扩展名）
 * @property {number} fileSize - 文件大小（字节）
 * @property {string} storagePath - 文件存储路径（服务器相对路径）
 * @property {string} mimeType - MIME 类型（如 application/pdf）
 * @property {string} creator - 创建人
 * @property {string} createdAt - 创建时间
 */

// TODO: 后端 CKAttachmentController 就绪后启用
// export function getAttachmentsByOwner(ownerOid) {
//   return request.get(`/attachments`, { params: { ownerOid } })
// }
// export function uploadAttachment(ownerOid, file) { ... }
// export function deleteAttachment(oid) { ... }

// ==================== 主文档文件 API（CKFile 实体 -- 待后端完善） ====================

/**
 * @typedef {Object} CKFile
 * @property {string} oid - 文件唯一标识
 * @property {string} fileName - 原始文件名（含扩展名）
 * @property {number} fileSize - 文件大小（字节）
 * @property {string} storagePath - 文件存储路径（服务器相对路径）
 * @property {string} mimeType - MIME 类型
 * @property {string} creator - 创建人
 * @property {string} createdAt - 创建时间
 */

// TODO: 后端 CKFileController 就绪后启用
// export function uploadMainFile(file) { ... }
// export function getMainFile(oid) { ... }
// export function deleteMainFile(oid) { ... }

// ==================== 文件夹 API ====================

/** 获取文件夹树 */
export function getFolderTree(ownerOid, stageOid) {
  return request.get('/folders/tree', { params: { ownerOid, stageOid } })
}

/** 获取所有文件夹树（不限定业务对象和阶段） */
export function getAllFolderTree() {
  return request.get('/folders/all-tree')
}

/** 按 oid 查询文件夹详情（用于解析 Part/文档所属文件夹的完整路径） */
export function getFolderByOid(oid) {
  return request.get(`/folders/${oid}`)
}

/** 获取文件夹扁平列表 */
export function getFolders(ownerOid, stageOid) {
  return request.get('/folders', { params: { ownerOid, stageOid } })
}

/** 创建文件夹 */
export function createFolder(data) {
  return request.post('/folders', data)
}

/** 更新文件夹 */
export function updateFolder(oid, data) {
  return request.put(`/folders/${oid}`, data)
}

/** 删除文件夹 */
export function deleteFolder(oid) {
  return request.delete(`/folders/${oid}`)
}

// ==================== 软类型实例统一入口 API ====================

/**
 * 创建软类型实例（统一入口）。
 *
 * 后端依据 typeDefinitionCode 对应的 root_type_code 自动路由到能力宿主
 * （PART → ck_part / DOCUMENT → ck_document / ENG_DOCUMENT → ck_eng_document …），
 * 前端无需再硬编码「类型 → 端点」映射。
 *
 * @param {object} data 载荷，须含 typeDefinitionCode（如 FOOTPRINT / SYMBOL / ELECTRONIC）
 * @returns {Promise} 响应 data 含 hostCode / oid / number / name / iterationOid / displayVersion
 */
export function createSoftTypeInstance(data) {
  return request.post('/softtype-instances', data)
}

/** 解析某类型所属的能力宿主（诊断：可验证 root_type_code 路由是否符合预期） */
export function resolveSoftTypeHost(typeDefinitionCode) {
  return request.get('/softtype-instances/host', { params: { typeDefinitionCode } })
}

/** 已实现创建策略的能力宿主列表 */
export function getSoftTypeHosts() {
  return request.get('/softtype-instances/hosts')
}

// ==================== 工程数据（EngineeringDocument）API ====================
// 工程数据 = CAD 设计数据（3D 数模 / 2D 工程图 / 封装 FOOTPRINT / 图符 SYMBOL …），
// 与通用文档（/documents）并列，数据落 ck_eng_document + ck_eng_document_iteration。
// 注意：创建不在本组，统一走 createSoftTypeInstance（POST /softtype-instances）。

/** 更新工程数据（含 CAD 属性 / 分类 IBA / 实体 IBA） */
export function updateEngineeringDocument(oid, data) {
  return request.put(`/eng-documents/${oid}`, data)
}

/** 重命名工程数据 */
export function renameEngineeringDocument(oid, name) {
  return request.put(`/eng-documents/${oid}/rename`, { name })
}

/** 移动工程数据到新的容器/阶段/文件夹 */
export function moveEngineeringDocument(oid, data) {
  return request.put(`/eng-documents/${oid}/move`, data)
}

/** 删除工程数据（含全部子版本） */
export function deleteEngineeringDocument(oid) {
  return request.delete(`/eng-documents/${oid}`)
}

/** 删除工程数据的最新小版本（仅删除最新 iteration） */
export function deleteEngineeringDocumentLatestIteration(oid) {
  return request.delete(`/eng-documents/${oid}/latest-iteration`)
}

/** 查询工程数据详情（params = { iterationOid } 可选，用于查看指定历史版本） */
export function getEngineeringDocument(oid, params) {
  return request.get(`/eng-documents/${oid}`, { params })
}

/** 查询工程数据列表（params = { containerOid, stageOid, folderOid }） */
export function getEngineeringDocuments(params = {}) {
  return request.get('/eng-documents', { params })
}

/** 查询文件夹下的工程数据详情（含迭代、生命周期、类型名、CAD 属性），用于列表展示 */
export function getFolderEngineeringDocumentDetails(folderOid) {
  return request.get('/eng-documents/folder-details', { params: { folderOid } })
}

/** 查询工程数据历史版本（含主对象 clsOid、实体 IBA、迭代级分类 IBA） */
export function getEngineeringDocumentIterations(oid) {
  return request.get(`/eng-documents/${oid}/iterations`)
}

/** 检出工程数据（通用入口，entityType=ENG_DOCUMENT） */
export function checkoutEngineeringDocument(oid, comment) {
  return request.post('/checkout/checkout', { entityType: 'ENG_DOCUMENT', entityOid: oid, comment })
}

/** 检入工程数据（解除检出，将副本保存为新版本） */
export function checkinEngineeringDocument(oid) {
  return request.post('/checkout/checkin', { entityType: 'ENG_DOCUMENT', entityOid: oid })
}

/** 取消检出工程数据（撤销检出，不保留修改） */
export function undoCheckoutEngineeringDocument(oid) {
  return request.post('/checkout/undo-checkout', { entityType: 'ENG_DOCUMENT', entityOid: oid })
}

// ==================== 零组件 API ====================

/** 创建零组件 */
export function createPart(data) {
  return request.post('/parts', data)
}

// ==================== 功能系统 API ====================

/** 创建功能系统 */
export function createFunctional(data) {
  return request.post('/functionals', data)
}

/** 更新功能系统 */
export function updateFunctional(oid, data) {
  return request.put(`/functionals/${oid}`, data)
}

/** 删除功能系统 */
export function deleteFunctional(oid) {
  return request.delete(`/functionals/${oid}`)
}

/** 按文件夹查询功能系统 VO */
export function getFunctionalsByFolder(folderOid) {
  return request.get('/functionals/by-folder', { params: { folderOid } })
}

/** 更新零组件 */
export function updatePart(oid, data) {
  return request.put(`/parts/${oid}`, data)
}

/** 删除零组件 */
export function deletePart(oid) {
  return request.delete(`/parts/${oid}`)
}

/** 删除零组件的最新小版本 */
export function deletePartLatestIteration(oid) {
  return request.delete(`/parts/${oid}/latest-iteration`)
}

/** 重命名零组件 */
export function renamePart(oid, name) {
  return request.put(`/parts/${oid}/rename`, { name })
}

/** 另存为零组件（复制为新对象） */
export function saveAsPart(oid, name) {
  return request.post(`/parts/${oid}/save-as`, { name })
}

/** 移动零组件到新的容器/阶段/文件夹 */
export function movePart(oid, data) {
  return request.put(`/parts/${oid}/move`, data)
}

/** 按文件夹查询零组件 VO */
export function getPartsByFolder(folderOid) {
  return request.get('/parts/by-folder', { params: { folderOid } })
}

/** 按容器（产品系列/型号）查询零组件，支持名称/编码关键字过滤 */
export function getPartsByContainer(containerOid, keyword) {
  return request.get('/parts/by-container', { params: { containerOid, keyword } })
}

/** 查询零组件历史版本 */
export function getPartIterations(oid) {
  return request.get(`/parts/${oid}/iterations`)
}

// ==================== 文档 API ====================

/** 创建文档 */
export function createDocument(data) {
  return request.post('/documents', data)
}

/** 更新文档 */
export function updateDocument(oid, data) {
  return request.put(`/documents/${oid}`, data)
}

/** 删除文档 */
export function deleteDocument(oid) {
  return request.delete(`/documents/${oid}`)
}

/** 删除文档的最新小版本 */
export function deleteDocumentLatestIteration(oid) {
  return request.delete(`/documents/${oid}/latest-iteration`)
}

/** 重命名文档 */
export function renameDocument(oid, name) {
  return request.put(`/documents/${oid}/rename`, { name })
}

/** 移动文档到新的容器/阶段/文件夹 */
export function moveDocumentApi(oid, data) {
  return request.put(`/documents/${oid}/move`, data)
}

/** 检出文档（通用入口，传 entityType） */
export function checkoutDocument(oid, comment) {
  return request.post('/checkout/checkout', { entityType: 'DOCUMENT', entityOid: oid, comment })
}

/** 取消检出文档 */
export function undoCheckoutDocument(oid) {
  return request.post('/checkout/undo-checkout', { entityType: 'DOCUMENT', entityOid: oid })
}

/** 检出零组件（通用入口，entityType=PART） */
export function checkoutPart(oid, comment) {
  return request.post('/checkout/checkout', { entityType: 'PART', entityOid: oid, comment })
}

/** 取消检出零组件 */
export function undoCheckoutPart(oid) {
  return request.post('/checkout/undo-checkout', { entityType: 'PART', entityOid: oid })
}

/** 检入零组件（解除检出，将副本保存为新版本） */
export function checkinPart(oid) {
  return request.post('/checkout/checkin', { entityType: 'PART', entityOid: oid })
}

/** 检入文档（解除检出，将副本保存为新版本） */
export function checkinDocument(oid) {
  return request.post('/checkout/checkin', { entityType: 'DOCUMENT', entityOid: oid })
}

/** 获取文档下载链接 */
export function getDocumentDownloadUrl(ckfileOid) {
  return `/api/ckfiles/${ckfileOid}/download`
}

/** 获取文档详情 */
export function getDocument(oid) {
  return request.get(`/documents/${oid}`)
}

/**
 * 获取文档列表（支持多条件过滤）
 * @param {Object} params - { ownerOid, stageOid, folderOid }
 */
export function getDocuments(params = {}) {
  return request.get('/documents', { params })
}

/**
 * 获取文件夹下文档详情列表（含迭代、生命周期、类型中文名），用于阶段页面 DataTable
 */
export function getFolderDocumentDetails(folderOid) {
  return request.get('/documents/folder-details', { params: { folderOid } })
}

/** 查询文档历史版本 */
export function getDocumentIterations(oid) {
  return request.get(`/documents/${oid}/iterations`)
}

// ==================== 自动化服务清单 API ====================
// 流程设计器「函数调用」节点的候选清单以后端为准（后端实现了哪些服务/函数）。
// 前端白名单只保留显示名与参数声明 —— 两份清单会漂移，而漂移的表现是
// "函数明明部署了，下拉里选不到"。

export function getAutomationServices() {
  return request.get('/plm/automation-services')
}

// ==================== 目标系统注册表 API ====================
// 流程「REST 接口调用」节点的下拉清单，同时供「系统配置 → 目标系统」维护页使用。
// 凭据只写不读：接口不回传 secret，编辑时留空表示不修改（见后端 TargetSystemController）。

export function getTargetSystems() {
  return request.get('/plm/target-systems')
}
export function createTargetSystem(data) {
  return request.post('/plm/target-systems', data)
}
export function updateTargetSystem(oid, data) {
  return request.put(`/plm/target-systems/${oid}`, data)
}
export function deleteTargetSystem(oid) {
  return request.delete(`/plm/target-systems/${oid}`)
}

// ==================== 文件存储配置 API ====================

export function getFileStorageConfigs() {
  return request.get('/file-storage')
}
export function getFileStorageConfig(oid) {
  return request.get(`/file-storage/${oid}`)
}
export function createFileStorageConfig(data) {
  return request.post('/file-storage', data)
}
export function updateFileStorageConfig(oid, data) {
  return request.put(`/file-storage/${oid}`, data)
}
export function deleteFileStorageConfig(oid) {
  return request.delete(`/file-storage/${oid}`)
}
export function getFileStorageSummary() {
  return request.get('/file-storage/summary')
}

// ==================== 用户活动 API ====================

/** 记录用户活动 */
export function recordActivity(data) {
  return request.post('/activity', data)
}

/** 获取最近访问 */
export function getRecentAccess() {
  return request.get('/activity/recent-access')
}

/** 获取最近操作 */
export function getRecentOperations() {
  return request.get('/activity/recent-operations')
}

/** 分页查询操作日志（支持类型、时间筛选） */
export function getActivityLogs(params = {}) {
  return request.get('/activity/logs', { params })
}

// ==================== 检出 API ====================

/** 获取我的检出列表 */
export function getMyCheckouts() {
  return request.get('/checkout/mine')
}

// ==================== 修订 API（通用入口）====================

/**
 * 修订对象：基于当前最新版本创建新的大版本（revision+1，iteration=1）。
 *
 * <p>与检出/检入同一套架构：前端只调这一个入口，后端按 entityType 路由到对应
 * ReviseProvider（PART / DOCUMENT / ENG_DOCUMENT）。新增带版本的实体类型时，
 * 后端加一个 Provider 即可，所有调用处无需改动。
 *
 * @param {'PART'|'DOCUMENT'|'ENG_DOCUMENT'} entityType 实体类型
 * @param {string} oid 主对象 oid（不是迭代 oid）
 */
export function reviseObject(entityType, oid) {
  return request.post('/revise', { entityType, entityOid: oid })
}

/**
 * 实体编码 → 修订入口的 entityType（与后端 ReviseProvider#getEntityType 对齐）。
 *
 * <p>Part 子类型（ELECTRONIC / STRUCTURAL…）共用 parts 宿主 → PART；
 * 文档子类型 → DOCUMENT；工程数据子类型（FOOTPRINT / SYMBOL…）→ ENG_DOCUMENT。
 * 未登记宿主返回空串，调用方据此拒绝/隐藏修订。
 */
export function resolveReviseEntityType(entityCode) {
  const path = resolveEntityApiPath(entityCode)
  if (path === 'parts') return 'PART'
  if (path === 'documents') return 'DOCUMENT'
  if (path === 'eng-documents') return 'ENG_DOCUMENT'
  return ''
}

// ==================== 版本规则 API ====================

/** 获取所有版本规则 */
export function getVersionRules() {
  return request.get('/version-rules')
}

/** 获取版本规则详情 */
export function getVersionRule(oid) {
  return request.get(`/version-rules/${oid}`)
}

/** 获取版本规则详情 by code */
export function getVersionRuleByCode(code) {
  return request.get(`/version-rules/code/${code}`)
}

/** 创建版本规则 */
export function createVersionRule(data) {
  return request.post('/version-rules', data)
}

/** 更新版本规则 */
export function updateVersionRule(oid, data) {
  return request.put(`/version-rules/${oid}`, data)
}

/** 删除版本规则 */
export function deleteVersionRule(oid) {
  return request.delete(`/version-rules/${oid}`)
}

/** 生成下一个版本 */
export function generateNextVersion(code) {
  return request.post(`/version-rules/generate/${code}`)
}

/** 重置序号 */
export function resetSequence(code, value) {
  return request.post(`/version-rules/reset-sequence/${code}`, { value })
}

// ==================== 类型-版本规则关联 API ====================

/** 获取类型绑定的版本规则 */
export function getTypeVersionRuleLink(typeOid) {
  return request.get(`/type-version-rule-links/type/${typeOid}`)
}

/** 绑定类型版本规则 */
export function bindTypeVersionRule(typeOid, versionRuleCode) {
  return request.post('/type-version-rule-links', { typeOid, versionRuleCode })
}

/** 解绑类型版本规则 */
export function unbindTypeVersionRule(typeOid) {
  return request.delete(`/type-version-rule-links/type/${typeOid}`)
}

// ==================== 类型-生命周期模板关联 API ====================

/** 获取类型绑定的生命周期模板 */
export function getTypeLifecycleTemplateLink(typeOid) {
  return request.get(`/type-lifecycle-template-links/type/${typeOid}`)
}

/** 绑定类型生命周期模板 */
export function bindTypeLifecycleTemplate(typeOid, lifecycleTemplateCode) {
  return request.post('/type-lifecycle-template-links', { typeOid, lifecycleTemplateCode })
}

/** 解绑类型生命周期模板 */
export function unbindTypeLifecycleTemplate(typeOid) {
  return request.delete(`/type-lifecycle-template-links/type/${typeOid}`)
}

// ==================== 类型-生命周期状态-流程模板 关联 ====================
//
// 契约见 src/main/java/cn/ck/plm/softtype/controller/TypeLifecycleStateProcessLinkController.java
// 语义：**该类型所绑生命周期模板**里的某个状态「用哪个流程模板」（1:1）。
// 主键含类型：同一个生命周期模板（如 STANDARD）会被多个类型复用，各自可绑不同流程。
// 绑的是模板 code（配置层，不带版本），编辑生命周期模板不影响绑定。
// 该类型用的是哪个模板、归属哪个租户由后端自行解析，前端只需给类型 oid 与状态。
// 本期只做配置与展示（发起流程仍由人工手动发起，运行期自动触发未接入）。

/** 该类型（按其当前所绑生命周期模板）的「状态 → 流程模板」映射：{ statusCode: processTemplateOid } */
export function getTypeLifecycleStateProcesses(typeOid) {
  return request.get(`/type-lifecycle-state-process-links/type/${typeOid}`)
}

/** 绑定 / 改绑 / 解绑某状态的流程模板（processTemplateOid 传 null 或 '' 即解绑） */
export function bindTypeLifecycleStateProcess(typeOid, statusCode, processTemplateOid) {
  return request.put(`/type-lifecycle-state-process-links/type/${typeOid}/${statusCode}`, {
    processTemplateOid,
  })
}

// ==================== 生命周期状态 API ====================

/** 获取生命周期状态列表 */
export function getLifecycleStatuses(keyword) {
  return request.get('/lifecycle-statuses', { params: keyword ? { keyword } : {} })
}

/** 获取单个生命周期状态 */
export function getLifecycleStatus(code) {
  return request.get(`/lifecycle-statuses/${code}`)
}

/** 创建生命周期状态 */
export function createLifecycleStatus(data) {
  return request.post('/lifecycle-statuses', data)
}

/** 更新生命周期状态 */
export function updateLifecycleStatus(code, data) {
  return request.put(`/lifecycle-statuses/${code}`, data)
}

/** 删除生命周期状态 */
export function deleteLifecycleStatus(code) {
  return request.delete(`/lifecycle-statuses/${code}`)
}

// ==================== 生命周期模板 API ====================

/** 获取生命周期模板列表 */
export function getLifecycleTemplates(keyword) {
  return request.get('/lifecycle-templates', { params: keyword ? { keyword } : {} })
}

/** 获取单个生命周期模板 */
export function getLifecycleTemplate(code) {
  return request.get(`/lifecycle-templates/${code}`)
}

/** 创建生命周期模板 */
export function createLifecycleTemplate(data) {
  return request.post('/lifecycle-templates', data)
}

/** 更新生命周期模板 */
export function updateLifecycleTemplate(code, data) {
  return request.put(`/lifecycle-templates/${code}`, data)
}

/**
 * 设置某状态是否允许修订（租户管理员的权限开关）。
 *
 * <p>与模板结构编辑分开：不产生新的模板子版本，改动对该模板全部子版本生效，
 * 在途对象（固化了旧子版本）也立即按新规则判定。
 */
export function setLifecycleStateRevisable(code, statusCode, revisable) {
  return request.put(`/lifecycle-templates/${code}/states/${statusCode}/revisable`, { revisable })
}

/** 删除生命周期模板 */
export function deleteLifecycleTemplate(code) {
  return request.delete(`/lifecycle-templates/${code}`)
}

// ==================== 类型-编码规则关联 API ====================

/** 获取类型绑定的编码规则 */
export function getTypeNumberRuleLink(typeOid) {
  return request.get(`/type-number-rule-links/type/${typeOid}`)
}

/** 绑定类型编码规则 */
export function bindTypeNumberRule(typeOid, numberRuleCode) {
  return request.post('/type-number-rule-links', { typeOid, numberRuleCode })
}

/** 解绑类型编码规则 */
export function unbindTypeNumberRule(typeOid) {
  return request.delete(`/type-number-rule-links/type/${typeOid}`)
}

// ==================== 编码规则 API ====================

/** 获取编码规则列表（支持 keyword 模糊搜索） */
export function getNumberRules(keyword) {
  return request.get('/number-rules', { params: keyword ? { keyword } : {} })
}

/** 获取编码规则详情（含段定义） */
export function getNumberRule(code) {
  return request.get(`/number-rules/${encodeURIComponent(code)}`)
}

/** 创建编码规则 */
export function createNumberRule(data) {
  return request.post('/number-rules', data)
}

/** 更新编码规则 */
export function updateNumberRule(code, data) {
  return request.put(`/number-rules/${encodeURIComponent(code)}`, data)
}

/** 删除编码规则 */
export function deleteNumberRule(code) {
  return request.delete(`/number-rules/${encodeURIComponent(code)}`)
}

/** 生成下一个编码 */
export function generateNumber(code) {
  return request.post(`/number-rules/${encodeURIComponent(code)}/generate`)
}

/** 预览编码格式 */
export function previewNumber(code) {
  return request.post(`/number-rules/${encodeURIComponent(code)}/preview`)
}

/** 重置 SERIAL 段流水号 */
export function resetNumberSequence(code, segmentOid, value) {
  return request.post(`/number-rules/${encodeURIComponent(code)}/reset-sequence`, { segmentOid, value })
}

// ==================== 行业初始化引导（租户管理员首登：选行业与研发方法）API ====================

/** 引导状态：租户是否已引导 / 已选行业与方法论 / 当前用户是否租户管理员 / 本租户已有阶段数 */
export function getOnboardingState() {
  return request.get('/onboarding/state')
}

/** 可选的行业与研发方法（来自平台级阶段模板集的分组，不是前端硬编码清单） */
export function getOnboardingOptions() {
  return request.get('/onboarding/options')
}

/**
 * 行业解决方案（干跑预演，一行不写）—— 引导页"点「确认画像」后、确认安装前"看的那份方案清单。
 * 返回 { packId, packName, willInsert, willSkip, items[], errors[] }。
 */
export function planOnboarding(industryCode, devMethodCode) {
  return request.get('/onboarding/plan', {
    params: { industryCode, devMethodCode }
  })
}

/** 完成行业初始化：选定行业与研发方法，后端把该行业包装到本租户（幂等） */
export function completeOnboarding(payload) {
  return request.post('/onboarding/complete', payload)
}

// ==================== 行业包（Industry Pack）API ====================

/** 可用行业包列表（平台内置 + 外部目录覆盖） */
export function getIndustryPacks() {
  return request.get('/industry-packs/available')
}

/** 已安装的行业包（平台级 + 当前租户级） */
export function getInstalledIndustryPacks() {
  return request.get('/industry-packs/installed')
}

/** 校验全部行业包：平台版本区间 / 父包是否存在 / 资源文件是否齐 */
export function validateIndustryPacks() {
  return request.get('/industry-packs/validate')
}

/**
 * 行业包内容详情 —— 平台管理员"查看行业与内嵌方法论内容"：
 * 清单元数据 + 能力开关 + 企业画像题 + 可调参数（含按规模建议值）+ 内容亮点 + 待办 + 逐资源条目预览。
 */
export function getIndustryPackContent(code, version) {
  return request.get(`/industry-packs/${encodeURIComponent(code)}/${encodeURIComponent(version)}/content`)
}

/** 安装预演（dry-run）：回答"真装会得到什么"，不写库，可反复点 */
export function planIndustryPack(code, version) {
  return request.get(`/industry-packs/${encodeURIComponent(code)}/${encodeURIComponent(version)}/plan`)
}

/** 安装行业包（幂等）—— 平台级包通常由启动自动安装，这里留给实施/排障手动触发 */
export function installIndustryPack(code, version, params) {
  return request.post(`/industry-packs/${encodeURIComponent(code)}/${encodeURIComponent(version)}/install`,
    params ? { params } : {})
}

// ==================== 视图定义 API ====================

/** 获取视图列表（支持 keyword 模糊搜索） */
export function getViews(keyword) {
  return request.get('/views', { params: keyword ? { keyword } : {} })
}

/** 获取已启用的视图列表 */
export function getEnabledViews() {
  return request.get('/views/enabled')
}

/** 获取单个视图 */
export function getView(code) {
  return request.get(`/views/${encodeURIComponent(code)}`)
}

/** 创建视图 */
export function createView(data) {
  return request.post('/views', data)
}

/** 更新视图 */
export function updateView(code, data) {
  return request.put(`/views/${encodeURIComponent(code)}`, data)
}

/** 删除视图 */
export function deleteView(code) {
  return request.delete(`/views/${encodeURIComponent(code)}`)
}

// ==================== 视图切换规则 API ====================

/** 获取指定视图的切换规则列表 */
export function getViewTransitions(viewCode) {
  return request.get(`/views/${encodeURIComponent(viewCode)}/transitions`)
}

/** 创建视图切换规则 */
export function createViewTransition(data) {
  return request.post('/views/transitions', data)
}

/** 更新视图切换规则 */
export function updateViewTransition(oid, data) {
  return request.put(`/views/transitions/${oid}`, data)
}

/** 删除视图切换规则 */
export function deleteViewTransition(oid) {
  return request.delete(`/views/transitions/${oid}`)
}

// ==================== 分类管理 ====================
export function getClassificationTree() {
  return request.get('/classifications/tree')
}

export function getClassificationList() {
  return request.get('/classifications')
}
export function getClassification(oid) {
  return request.get(`/classifications/${oid}`)
}
export function createClassification(data) {
  return request.post('/classifications', data)
}
export function updateClassification(oid, data) {
  return request.put(`/classifications/${oid}`, data)
}
export function deleteClassification(oid) {
  return request.delete(`/classifications/${oid}`)
}
export function searchClassifications(keyword) {
  return request.get('/classifications/search', { params: { keyword } })
}

/** 全局搜索（产品系列/产品型号/零组件/文档）按 name/number(code) 模糊匹配 */
export function globalSearch(keyword, limit = 20) {
  return request.get('/global-search', { params: { q: keyword, limit } })
}

// ===== 分类-IBA 关联 =====
export function getClassificationIBAs(classificationOid) {
  return request.get(`/classifications/cls-iba/list/${classificationOid}`)
}
export function getUnassignedClsIBAs(classificationOid, keyword) {
  return request.get(`/classifications/cls-iba/unassigned/${classificationOid}`, { params: { keyword } })
}
export function assignClsIBA(classificationOid, data) {
  return request.post(`/classifications/cls-iba/assign/${classificationOid}`, data)
}
export function batchAssignClsIBAs(classificationOid, ibaOids) {
  return request.post(`/classifications/cls-iba/batch/${classificationOid}`, { ibaOids })
}
export function updateClsIBAMapping(mappingOid, data) {
  return request.put(`/classifications/cls-iba/${mappingOid}`, data)
}
export function removeClsIBAMapping(mappingOid) {
  return request.delete(`/classifications/cls-iba/${mappingOid}`)
}

// ===== 分类 IBA 数据存取 =====
export function getClassificationIBAValues(classificationOid) {
  return request.get(`/classifications/${classificationOid}/iba-values`)
}
export function saveClassificationIBAValues(classificationOid, values) {
  return request.put(`/classifications/${classificationOid}/iba-values`, values)
}

// ===== 分类 IBA 布局管理 =====
export function getClsIbaLayout(clsOid, operationCode) {
  return request.get('/cls-iba-layouts', { params: { clsOid, operationCode } })
}
export function saveClsIbaLayout(data) {
  return request.post('/cls-iba-layouts', data)
}
export function getClsIbaLayoutOperations(clsOid) {
  return request.get('/cls-iba-layouts/operations', { params: { clsOid } })
}
export function deleteClsIbaLayout(clsOid, operationCode) {
  return request.delete('/cls-iba-layouts', { params: { clsOid, operationCode } })
}

// ==================== 单位管理 API ====================

/** 获取所有量纲类型列表 */
export function getUnitQuantityTypes() {
  return request.get('/units/quantity-types')
}

/** 获取所有单位（按量纲分组） */
export function getUnitListGrouped() {
  return request.get('/units', { params: { grouped: true } })
}

/** 获取所有单位（平铺列表） */
export function getUnitListAll() {
  return request.get('/units', { params: { grouped: false } })
}

/** 按量纲查询单位 */
export function getUnitsByQuantityType(quantityType) {
  return request.get('/units/by-quantity-type', { params: { quantityType } })
}

/** 创建单位 */
export function createUnit(data) {
  return request.post('/units', data)
}

/** 更新单位 */
export function updateUnit(oid, data) {
  return request.put(`/units/${oid}`, data)
}

/** 删除单位 */
export function deleteUnit(oid) {
  return request.delete(`/units/${oid}`)
}

/** 单位换算 */
export function convertUnit(from, to) {
  return request.get('/units/convert', { params: { from, to } })
}

// ==================== 阶段模板 API ====================
// ==================== 流程模板 API（流程设计器，spec §2.3 / §4-H）====================
//
// 契约见 src/main/java/cn/ck/plm/process/controller/ProcessTemplateController.java
// 注意：baseURL 已含 /api，故此处路径不带 /api 前缀。

/** 模板列表（keyword / category / enabled 均可选） */
export function listProcessTemplates(params = {}) {
  return request.get('/plm/process-templates', { params })
}

/** 模板详情（含最新版 dslJson） */
export function getProcessTemplate(oid) {
  return request.get(`/plm/process-templates/${oid}`)
}

/** 新建模板（body: key,name,…,dslJson） */
export function createProcessTemplate(data) {
  return request.post('/plm/process-templates', data)
}

/** 保存（body: { dslJson, changeNote }）→ 后端生成新版本 */
export function saveProcessTemplate(oid, data) {
  return request.put(`/plm/process-templates/${oid}`, data)
}

/**
 * 按版本删除（**流程删除的唯一方式**）
 *
 * @param {string} oid 模板 oid
 * @param {number[]} versions 要删除的版本号，如 [2, 3]
 * @returns {Promise<{code, message, data: {templateOid, deleted, templateRemoved, latestVersion}}>}
 *
 * 只允许删未部署的版本（已部署的整批拒绝）；删完最后一个版本 → 该流程整体消失。
 * 刻意没有"删除模板"接口：删除必须按版本进行。
 */
export function deleteProcessTemplateVersions(oid, versions) {
  return request.post(`/plm/process-templates/${oid}/versions/delete`, { versions })
}

/** 复制/另存（body: { key, name }） */
export function copyProcessTemplate(oid, data) {
  return request.post(`/plm/process-templates/${oid}/copy`, data)
}

/**
 * 允许部署 / 停止部署（原「启用 / 禁用」）
 *
 * 语义是**发布闸门**：停止部署后不能发布新版本，但已部署的流程定义与在途实例不受影响，
 * 也不阻止按 key 直接发起新实例（那是运行期的事，不在本开关范围内）。
 */
export function enableProcessTemplate(oid) {
  return request.post(`/plm/process-templates/${oid}/enable`)
}

export function disableProcessTemplate(oid) {
  return request.post(`/plm/process-templates/${oid}/disable`)
}

/**
 * 预置流程模板市场：行业包里可取的流程清单（只读，不落库）。
 *
 * 数据源是包目录（随 jar 发布），不是模板表 —— 取用后才在本租户生成模板行。
 */
export function listProcessTemplateMarket() {
  return request.get('/plm/process-templates/market')
}

/** 取用一个预置流程（body: { packId, file, key?, name? }） */
export function adoptProcessTemplate(data) {
  return request.post('/plm/process-templates/market/adopt', data)
}

/** 版本历史 */
export function listProcessTemplateVersions(oid) {
  return request.get(`/plm/process-templates/${oid}/versions`)
}

/** 指定版本（含 DSL 与部署快照） */
export function getProcessTemplateVersion(oid, version) {
  return request.get(`/plm/process-templates/${oid}/versions/${version}`)
}

/** 部署（body: { version?, bpmnXml }；BPMN 的 process id 必须等于模板 key） */
export function deployProcessTemplate(oid, data) {
  return request.post(`/plm/process-templates/${oid}/deploy`, data)
}

// ==================== 发起流程（所有业务对象行操作共用）====================
//
// 契约见 src/main/java/cn/ck/plm/process/controller/ProcessInstanceController.java
// 用法：业务对象页面的「发起流程」弹框（components/StartProcessModal.vue）
//   1) 先 start-options 解析"该类型 + 该状态"绑定的流程模板及其最新版本信息；
//   2) 用户确认后调 start，由 Flowable 按 key 启动（用引擎里该 key 最新已部署的定义）。

/**
 * 发起前解析：该业务对象（类型 + 状态）该发起哪个流程，以及该流程的最新版本信息。
 *
 * @param {object} params { typeDefinitionCode, statusCode, iterationOid?, entityOid? }
 *   iterationOid 传业务对象迭代固化的生命周期模板子版本（可空）——
 *   传了就优先按"对象出生时那一版"的配置解析。
 *   entityOid 传业务对象主 oid（可空）—— 传了就一并判定"该对象是否已有流程在执行"，
 *   有在跑的则 startable=false（同一业务对象同时只允许一个流程）。
 * @returns {Promise<{code, message, data:{
 *   startable: boolean, reason: string|null,
 *   typeDefinitionCode, typeDefinitionName, typeOid, statusCode,
 *   processTemplate: { oid, key, name, displayName, description, enabled,
 *                      latestVersion, deployedVersion, deployedInCurrentTenant },
 *   processVersion: { version, changeNote, deployed, createdAt }
 * }}>}
 *
 * 未配置 / 模板已删 / 尚未部署 / 该对象已有流程在执行时同样返回 code=200，
 * 但 startable=false 且带 reason，弹框据此说明"为什么不能发起"，而不是让用户点了才报错。
 */
export function getProcessStartOption(params) {
  return request.get('/workflow/instance/start-options', { params })
}

/**
 * 按流程 key **直传**解析发起选项 —— 不经过"类型 + 状态 → 绑定"这条解析，
 * 用于"行上自带 processKey"的入口（产品系列页的**阶段门 / 评审**行：
 * TR1 → technical-review、CHARTER → gate-decision …）。
 *
 * <p>返回形状与 {@link getProcessStartOption} 完全一致（后端同一个 VO、同一份组装逻辑），
 * 因此发起弹框可直接复用：`startable / reason / processTemplate / processVersion`。
 *
 * @param {{ processKey: string, typeCode?: string, statusCode?: string, entityOid?: string }} params
 *        typeCode / statusCode / entityOid 是**发起上下文**（从哪个对象发起），
 *        与按类型解析时同义：entityOid 传了会一并判定"该对象该大版本下是否已有流程在执行"。
 */
export function getProcessStartOptionByKey(params) {
  return request.get('/workflow/instance/start-option-by-key', { params })
}

/**
 * 通过 Flowable 发起流程实例。
 *
 * @param {object} data {
 *   processKey,
 *   businessKey,             // 业务对象 oid —— 流程实例据此与业务对象关联
 *   entityOid?,              // 业务对象 oid；给了才写 ck_process_entity_set（实例 ↔ 实体 关联）
 *   entityVersion?,          // 业务对象的【大版本】（如 A）；不传则后端按对象当前大版本解析
 *   typeCode?,               // 类型编码（后端据此补 rootTypeCode）
 *   rootTypeCode?,           // 能力宿主（可省）
 *   variables?,
 * }
 * @returns {Promise<{code, data: string}>} data = 流程实例 id
 */
export function startProcessInstance(data) {
  return request.post('/workflow/instance/start', data)
}

/**
 * 某个业务对象关联的流程实例 —— 所有业务对象详情页「关联流程」两栏共用。
 *
 * @param {object} params { entityOid, entityVersion? }
 *   entityOid     业务对象主 oid（必填）
 *   entityVersion 业务对象大版本（可空 = 该对象全部大版本；大版本之间是两轮工作）
 * @returns {Promise<{code, data: Array}>} 执行中的在前、已执行的在后（各自按时间倒序）；
 *   每行含 status / entityVersion / startTime / endTime / currentActivityName / currentAssignees
 */
export function getEntityProcesses(params) {
  return request.get('/workflow/instance/by-entity', { params })
}

/** 流程实例详情（含流程变量） */
export function getProcessInstanceDetail(id) {
  return request.get(`/workflow/instance/${id}`)
}

/**
 * 我的待办任务（任务中心「待办任务」页签 + 工作台待办卡片共用）。
 *
 * @param {object} params { page, size }
 * @returns {Promise<{code, data: Array}>} 每项含 id / name / processDefinitionName /
 *   processDefinitionKey / processInstanceId / businessKey / formKey /
 *   taskDefinitionKey / assignee / createTime / dueDate / overdue
 */
export function getMyTodoTasks(params) {
  return request.get('/workflow/task/todo', { params })
}

/** 我的任务统计：{ todoCount, claimableCount, overdueCount, doneCount } */
export function getMyTaskStats() {
  return request.get('/workflow/task/stats')
}

/**
 * 单个在办任务 —— 办理页（新窗口冷启动）用它拿任务上下文。
 *
 * @param {string} id 任务 id
 * @returns {Promise<{code, data}>} 200 + 任务；任务已被办理或不存在时 code=404
 *   （两个人同时打开同一任务是正常的，后到的人应看到"已办理"，而不是报错）
 */
export function getTaskDetail(id) {
  return request.get(`/workflow/task/${id}`)
}

/**
 * 任务办理页的**渲染上下文** —— 「通用信息 + 任务表单 + 完整进度」一次给齐。
 *
 * <p>办理页是冷启动的独立窗口，只有任务 id；让前端自己串行拼 4~5 个请求会引入
 * 半加载状态与口径漂移，所以由后端聚合。
 *
 * @param {string} id 任务 id
 * @returns {Promise<{code, data: {
 *   task: object, process: object|null,
 *   entities: Array<{ entityOid, entityVersion, typeCode, rootTypeCode, businessKey }>,
 *   form: { formKey, taskDefinitionKey, taskName, dslJson },
 *   activities: Array
 * }}>} 任务已被办理或不存在时 code=404
 */
export function getTaskContext(id) {
  return request.get(`/workflow/task/${id}/context`)
}

/**
 * 任务办理表单上下文：{ formKey, taskDefinitionKey, dslJson, processDefinitionKey/version }
 * 前端按 formKey 派发表单（内置表单 / 通用同意-驳回）。
 */
export function getTaskForm(id) {
  return request.get(`/workflow/task/${id}/form`)
}

/**
 * 发起表单上下文：{ formKey, startEventId, dslJson, processDefinitionKey/version }
 * —— 开始节点配的表单（{@code startEvent} 的 flowable:formKey = DSL start 节点的 formRef）。
 *
 * <p>发起弹框在解析出「该发起哪个流程」后调它：formKey 非空则渲染申请表单，
 * 可填的字段随发起变量一并提交；为空则不渲染表单区（与老流程完全一致）。
 * 未部署时各字段为空，不是错误。
 */
export function getStartForm(processKey) {
  return request.get('/workflow/instance/start-form', { params: { processKey } })
}

/**
 * 办理任务。
 *
 * @param {string} id 任务 id
 * @param {object} data { action: 'approve'|'reject', comment, variables? }
 *   variables 是节点表单填的流程变量（如「设置流程参与者」选的人）
 */
export function completeTask(id, data) {
  return request.post(`/workflow/task/${id}/complete`, data)
}

/** 任务评论列表 */
export function getTaskComments(id) {
  return request.get(`/workflow/task/${id}/comment`)
}

/** 添加任务评论 */
export function addTaskComment(id, comment) {
  return request.post(`/workflow/task/${id}/comment`, { comment })
}

/**
 * 批量判定：这些业务对象里哪些正在流程中（发起弹窗过滤候选用）。
 *
 * <p>由后端判定而不是前端拿状态猜：依据是"关联表 + 引擎运行时"，且是**大版本粒度**
 * （同一对象的 A 版在跑，B 版照样能发起）—— 口径与发起闸门同一份实现。
 *
 * @param {string} typeCode 类型编码（解析对象当前大版本用）
 * @param {string[]} entityOids 业务对象 oid 列表
 * @returns {Promise<{code:number, data:string[]}>} data = 已在流程中的对象 oid
 */
export function getRunningEntityOids(typeCode, entityOids) {
  return request.get('/workflow/instance/running-entities', {
    params: { typeCode, entityOids: (entityOids || []).join(',') },
  })
}

/**
 * 我最近创建/修改过的业务对象（工作台）。
 *
 * @param {object} params { days = 5, limit = 20 }
 * @returns {Promise<{code, data: Array}>} 每项含 oid / name / code / entityType / entityTypeName /
 *   displayVersion / touchType（CREATED 创建 / UPDATED 修改 / CREATED_UPDATED 创建后又改过）/
 *   createdAt / updatedAt / touchedAt / linkPath
 */
export function getMyRecentObjects(params) {
  return request.get('/home/recent-objects', { params })
}

/**
 * 某流程实例的节点经路 —— "走到哪了"（关联流程里展开进度用）。
 *
 * @returns {Promise<{code, data: Array}>} 按时间顺序的节点，每项：
 *   status = completed（已办）/ running（当前在办）/ pending（尚未到达）
 *   name / type（审批活动、设置流程参与者…）/ assignees / assigneeNames
 *   startTime / endTime / comment（意见）/ outcome（实际走的分支名，如「同意」）
 */
export function getInstanceActivities(id) {
  return request.get(`/workflow/instance/${id}/activities`)
}

/**
 * 系统通知渠道 —— 「系统当前能怎么发通知」（服务端 plm.notification 配置）。
 *
 * 返回每个渠道的 { code, label, enabled, usable, missing }：设计器只应从 usable 的渠道里挑，
 * 管理员也能一眼看到"邮件为什么没发出去"（缺哪个配置项）。
 */
export function getNotificationChannels() {
  return request.get('/notifications/channels')
}

/**
 * 某流程实例的**节点执行日志** —— 「这个节点后台到底跑了什么、报了什么错」。
 *
 * 自动服务（设置状态 / 自动服务 / 通知）由后台执行，失败时用户只看到"流程卡住了"；
 * 这里把执行痕迹（含错误堆栈）读出来，详情页点开节点即可看。
 *
 * @param {string} id 流程实例 id
 * @param {{activityId?: string}} [params] 传 activityId 只看某个节点；不传返回整个实例的
 */
export function getInstanceNodeLogs(id, params) {
  return request.get(`/workflow/instance/${id}/node-logs`, { params })
}

/**
 * 某节点的「表单填写」—— 该节点办理时写入流程变量的表单类键值清单（key / label / value）。
 *
 * <p>与节点执行日志互补：日志回答"后台跑了什么"，这里回答"这一步填了什么"
 * （如「设置审批人」节点写入的各环节审批人 {@code ckplmSetupAssignees_<节点id>}）。
 * 平台内部变量（发起人 / 业务对象集 / 结论等）不返回 —— 那几项在节点头部已展示。
 *
 * @param {string} id 流程实例 id
 * @param {string} activityId 节点（活动）id
 */
export function getActivityForm(id, activityId) {
  return request.get(`/workflow/instance/${id}/activity-form`, { params: { activityId } })
}

/** 移动到指定分组（body: { categoryOid }；不产生新版本） */
export function moveProcessTemplateCategory(oid, categoryOid) {
  return request.post(`/plm/process-templates/${oid}/category`, { categoryOid })
}

// ==================== 流程分组 API（清单页左侧导航）====================
//
// 契约见 src/main/java/cn/ck/plm/process/controller/ProcessCategoryController.java
// 用法：先建分组 → 选中分组 → 在组内新建并设计流程；分组名在租户内唯一。

/** 分组列表（按 sort_order、name） */
export function listProcessCategories() {
  return request.get('/plm/process-categories')
}

/** 新建分组（body: { name, sortOrder?, description? }） */
export function createProcessCategory(data) {
  return request.post('/plm/process-categories', data)
}

/** 修改分组（改名会同步组内模板） */
export function updateProcessCategory(oid, data) {
  return request.put(`/plm/process-categories/${oid}`, data)
}

/** 删除分组（组内仍有流程时后端拒绝） */
export function deleteProcessCategory(oid) {
  return request.delete(`/plm/process-categories/${oid}`)
}

export function listStageTemplates() { return request.get('/stage-templates') }
export function createStageTemplate(data) { return request.post('/stage-templates', data) }
export function updateStageTemplate(oid, data) { return request.put(`/stage-templates/${oid}`, data) }
export function deleteStageTemplate(oid) { return request.delete(`/stage-templates/${oid}`) }
/** 查询平台提供的行业列表 */
export function listPlatformIndustries() { return request.get('/stage-templates/platform-industries') }
/** 查询平台模板（按 industry 过滤，空返回全部） */
export function listPlatformTemplates(industry) { return request.get('/stage-templates/platform', { params: { industry } }) }
/** 克隆平台模板（industry 空时克隆全部） */
export function cloneStageTemplatesFromPlatform(industry) {
  return industry
    ? request.post(`/stage-templates/clone-from-platform?industry=${encodeURIComponent(industry)}`)
    : request.post('/stage-templates/clone-from-platform')
}

// ==================== 交付件（交付物）模板 API ====================
// 与阶段模板同构：行业包随包装入企业一份，企业在此裁剪（含"停用"），
// 创建产品系列/型号时按它快照成实例（P2）。

/** 本租户交付件模板（可按 industry 过滤） */
export function listDeliverableTemplates(industry) {
  return request.get('/deliverable-templates', { params: { industry } })
}
export function createDeliverableTemplate(data) { return request.post('/deliverable-templates', data) }
export function updateDeliverableTemplate(oid, data) { return request.put(`/deliverable-templates/${oid}`, data) }
export function deleteDeliverableTemplate(oid) { return request.delete(`/deliverable-templates/${oid}`) }
/** 停用/启用（企业裁剪的正道：保留行与幂等键，重装行业包不会改回） */
export function setDeliverableTemplateDisabled(oid, value) {
  return request.put(`/deliverable-templates/${oid}/disabled`, null, { params: { value } })
}
/** 查询平台提供的方法论列表（克隆弹窗的选项来源） */
export function listPlatformDeliverableIndustries() { return request.get('/deliverable-templates/platform-industries') }
/** 查询平台交付件模板（按 industry 过滤） */
export function listPlatformDeliverableTemplates(industry) {
  return request.get('/deliverable-templates/platform', { params: { industry } })
}
/** 克隆平台交付件模板（幂等：已存在的 code 跳过，不覆盖企业裁剪） */
export function cloneDeliverableTemplatesFromPlatform(industry) {
  return industry
    ? request.post(`/deliverable-templates/clone-from-platform?industry=${encodeURIComponent(industry)}`)
    : request.post('/deliverable-templates/clone-from-platform')
}

// ==================== 阶段门（评审门）模板 API ====================
// 与阶段模板 / 交付件模板同构：行业包随包装入企业一份，企业在此裁剪（含"停用"）。

/** 本租户阶段门模板（可按 industry 过滤） */
export function listGateTemplates(industry) {
  return request.get('/gate-templates', { params: { industry } })
}
export function createGateTemplate(data) { return request.post('/gate-templates', data) }
export function updateGateTemplate(oid, data) { return request.put(`/gate-templates/${oid}`, data) }
export function deleteGateTemplate(oid) { return request.delete(`/gate-templates/${oid}`) }
/** 停用/启用（企业裁剪的正道：保留行与幂等键，重装行业包不会改回） */
export function setGateTemplateDisabled(oid, value) {
  return request.put(`/gate-templates/${oid}/disabled`, null, { params: { value } })
}
/** 查询平台提供的方法论列表（克隆弹窗的选项来源） */
export function listPlatformGateIndustries() { return request.get('/gate-templates/platform-industries') }
/** 查询平台阶段门模板（按 industry 过滤） */
export function listPlatformGateTemplates(industry) {
  return request.get('/gate-templates/platform', { params: { industry } })
}
/** 克隆平台阶段门模板（幂等：已存在的 code 跳过，不覆盖企业裁剪） */
export function cloneGateTemplatesFromPlatform(industry) {
  return industry
    ? request.post(`/gate-templates/clone-from-platform?industry=${encodeURIComponent(industry)}`)
    : request.post('/gate-templates/clone-from-platform')
}

// ==================== 评审模板 API ====================
// 与阶段 / 交付件 / 门模板同构：行业包随包装入企业一份，企业在此裁剪（含"停用"）。

/** 本租户评审模板（可按 industry 过滤） */
export function listReviewTemplates(industry) {
  return request.get('/review-templates', { params: { industry } })
}
export function createReviewTemplate(data) { return request.post('/review-templates', data) }
export function updateReviewTemplate(oid, data) { return request.put(`/review-templates/${oid}`, data) }
export function deleteReviewTemplate(oid) { return request.delete(`/review-templates/${oid}`) }
/** 停用/启用（企业裁剪的正道：保留行与幂等键，重装行业包不会改回） */
export function setReviewTemplateDisabled(oid, value) {
  return request.put(`/review-templates/${oid}/disabled`, null, { params: { value } })
}
/** 查询平台提供的方法论列表（克隆弹窗的选项来源） */
export function listPlatformReviewIndustries() { return request.get('/review-templates/platform-industries') }
/** 查询平台评审模板（按 industry 过滤） */
export function listPlatformReviewTemplates(industry) {
  return request.get('/review-templates/platform', { params: { industry } })
}
/** 克隆平台评审模板（幂等：已存在的 code 跳过，不覆盖企业裁剪） */
export function cloneReviewTemplatesFromPlatform(industry) {
  return industry
    ? request.post(`/review-templates/clone-from-platform?industry=${encodeURIComponent(industry)}`)
    : request.post('/review-templates/clone-from-platform')
}

// ==================== 检查规则模板 API ====================
// 与前面几类模板同构：行业包随包装入企业一份，企业在此裁剪（含"停用"）。

/** 本租户检查规则（可按 industry 过滤） */
export function listRuleTemplates(industry) {
  return request.get('/rule-templates', { params: { industry } })
}
export function createRuleTemplate(data) { return request.post('/rule-templates', data) }
export function updateRuleTemplate(oid, data) { return request.put(`/rule-templates/${oid}`, data) }
export function deleteRuleTemplate(oid) { return request.delete(`/rule-templates/${oid}`) }
/** 停用/启用（企业裁剪的正道：保留行与幂等键，重装行业包不会改回） */
export function setRuleTemplateDisabled(oid, value) {
  return request.put(`/rule-templates/${oid}/disabled`, null, { params: { value } })
}
/** 查询平台提供的方法论列表（克隆弹窗的选项来源） */
export function listPlatformRuleIndustries() { return request.get('/rule-templates/platform-industries') }
/** 查询平台检查规则（按 industry 过滤） */
export function listPlatformRuleTemplates(industry) {
  return request.get('/rule-templates/platform', { params: { industry } })
}
/** 克隆平台检查规则（幂等：已存在的 code 跳过，不覆盖企业裁剪） */
export function cloneRuleTemplatesFromPlatform(industry) {
  return industry
    ? request.post(`/rule-templates/clone-from-platform?industry=${encodeURIComponent(industry)}`)
    : request.post('/rule-templates/clone-from-platform')
}

// ==================== 问题类型模板 API ====================
// 与前面几类模板同构：行业包随包装入企业一份，企业在此裁剪（含"停用"）。

/** 本租户问题类型（可按 industry 过滤） */
export function listIssueTypeTemplates(industry) {
  return request.get('/issue-type-templates', { params: { industry } })
}
export function createIssueTypeTemplate(data) { return request.post('/issue-type-templates', data) }
export function updateIssueTypeTemplate(oid, data) { return request.put(`/issue-type-templates/${oid}`, data) }
export function deleteIssueTypeTemplate(oid) { return request.delete(`/issue-type-templates/${oid}`) }
/** 停用/启用（企业裁剪的正道：保留行与幂等键，重装行业包不会改回） */
export function setIssueTypeTemplateDisabled(oid, value) {
  return request.put(`/issue-type-templates/${oid}/disabled`, null, { params: { value } })
}
/** 查询平台提供的方法论列表（克隆弹窗的选项来源） */
export function listPlatformIssueTypeIndustries() { return request.get('/issue-type-templates/platform-industries') }
/** 查询平台问题类型（按 industry 过滤） */
export function listPlatformIssueTypeTemplates(industry) {
  return request.get('/issue-type-templates/platform', { params: { industry } })
}
/** 克隆平台问题类型（幂等：已存在的 code 跳过，不覆盖企业裁剪） */
export function cloneIssueTypeTemplatesFromPlatform(industry) {
  return industry
    ? request.post(`/issue-type-templates/clone-from-platform?industry=${encodeURIComponent(industry)}`)
    : request.post('/issue-type-templates/clone-from-platform')
}

// ==================== 指标（驾驶舱）模板 API ====================
// 指标与其它模板不同：它是"计算口径 + 数据就绪度"（level A/B/C；C 级第一版显示「暂未评估」，禁止编造数值）。

/** 本租户指标（可按 industry 过滤） */
export function listMetricTemplates(industry) {
  return request.get('/metric-templates', { params: { industry } })
}
export function createMetricTemplate(data) { return request.post('/metric-templates', data) }
export function updateMetricTemplate(oid, data) { return request.put(`/metric-templates/${oid}`, data) }
export function deleteMetricTemplate(oid) { return request.delete(`/metric-templates/${oid}`) }
/** 停用/启用（企业裁剪的正道：保留行与幂等键，重装行业包不会改回） */
export function setMetricTemplateDisabled(oid, value) {
  return request.put(`/metric-templates/${oid}/disabled`, null, { params: { value } })
}
/** 查询平台提供的方法论列表（克隆弹窗的选项来源） */
export function listPlatformMetricIndustries() { return request.get('/metric-templates/platform-industries') }
/** 查询平台指标（按 industry 过滤） */
export function listPlatformMetricTemplates(industry) {
  return request.get('/metric-templates/platform', { params: { industry } })
}
/** 克隆平台指标（幂等：已存在的 code 跳过，不覆盖企业裁剪） */
export function cloneMetricTemplatesFromPlatform(industry) {
  return industry
    ? request.post(`/metric-templates/clone-from-platform?industry=${encodeURIComponent(industry)}`)
    : request.post('/metric-templates/clone-from-platform')
}

// ==================== 驾驶舱（布局）模板 API ====================
// 与前面几类同构：行业包随包装入企业一份，企业在此裁剪（含"停用"）。
// 视角类型即 code：ENTERPRISE（经营战报）/ PRODUCT_LINE（系列战况）/ PRODUCT_MODEL（型号作战地图，主视角）。

/** 本租户驾驶舱布局（可按 industry 过滤） */
export function listDashboardTemplates(industry) {
  return request.get('/dashboard-templates', { params: { industry } })
}
export function createDashboardTemplate(data) { return request.post('/dashboard-templates', data) }
export function updateDashboardTemplate(oid, data) { return request.put(`/dashboard-templates/${oid}`, data) }
export function deleteDashboardTemplate(oid) { return request.delete(`/dashboard-templates/${oid}`) }
/** 停用/启用（企业裁剪的正道：保留行与幂等键，重装行业包不会改回） */
export function setDashboardTemplateDisabled(oid, value) {
  return request.put(`/dashboard-templates/${oid}/disabled`, null, { params: { value } })
}
/** 查询平台提供的方法论列表（克隆弹窗的选项来源） */
export function listPlatformDashboardIndustries() { return request.get('/dashboard-templates/platform-industries') }
/** 查询平台驾驶舱布局（按 industry 过滤） */
export function listPlatformDashboardTemplates(industry) {
  return request.get('/dashboard-templates/platform', { params: { industry } })
}
/** 克隆平台驾驶舱布局（幂等：已存在的 code 跳过，不覆盖企业裁剪） */
export function cloneDashboardTemplatesFromPlatform(industry) {
  return industry
    ? request.post(`/dashboard-templates/clone-from-platform?industry=${encodeURIComponent(industry)}`)
    : request.post('/dashboard-templates/clone-from-platform')
}

// ==================== MPN 管理 API（参考 Windchill 的 AML/AVL）====================
// 三层：内部件 ck_part → AML（制造商 + 制造商料号）→ AVL（供应商 + 供货条件）。
// MPN 厂商：制造商 / 供应商（导航页「MPN 厂商」维护；显示名 2026-10-11 由「MPN 数据」定名）。

/** 制造商列表（可按 keyword 过滤） */
export function listManufacturers(keyword) {
  return request.get('/mpn/manufacturers', { params: { keyword } })
}
export function createManufacturer(data) { return request.post('/mpn/manufacturers', data) }
export function updateManufacturer(oid, data) { return request.put(`/mpn/manufacturers/${oid}`, data) }
/** 删除制造商：已被 AML 引用时后端会拒绝（改用停用） */
export function deleteManufacturer(oid) { return request.delete(`/mpn/manufacturers/${oid}`) }

/** 供应商列表（可按 keyword 过滤） */
export function listSuppliers(keyword) {
  return request.get('/mpn/suppliers', { params: { keyword } })
}
export function createSupplier(data) { return request.post('/mpn/suppliers', data) }
export function updateSupplier(oid, data) { return request.put(`/mpn/suppliers/${oid}`, data) }
export function deleteSupplier(oid) { return request.delete(`/mpn/suppliers/${oid}`) }

/** 选件器：按关键字搜内部件（只返回 oid / code / name） */
export function searchMpnParts(keyword, limit) {
  return request.get('/mpn/parts', { params: { keyword, limit } })
}
/** 某内部件的 AML 树：AML 行内嵌 avls */
export function listPartAmlTree(partOid) {
  return request.get(`/mpn/parts/${partOid}/aml`)
}
/** ★ 汇总：AML 数 / 去重供应商数 / 是否单一来源（对应 R-SH-11） */
export function summarizePartMpn(partOid) {
  return request.get(`/mpn/parts/${partOid}/summary`)
}

export function createPartAml(data) { return request.post('/mpn/aml', data) }
export function updatePartAml(oid, data) { return request.put(`/mpn/aml/${oid}`, data) }
export function deletePartAml(oid) { return request.delete(`/mpn/aml/${oid}`) }

export function createPartAvl(data) { return request.post('/mpn/avl', data) }
export function updatePartAvl(oid, data) { return request.put(`/mpn/avl/${oid}`, data) }
export function deletePartAvl(oid) { return request.delete(`/mpn/avl/${oid}`) }

// ==================== 能力读侧 API ====================
// 本租户"能用哪些功能"的事实来源（ck_tenant_capability）。
// 只有包里声明了、且平台已实现的能力才会出现在这里 —— 界面据此决定入口是否显示。

/** 本租户已启用的能力（含名称与来源包） */
export function listCapabilities() {
  return request.get('/capabilities')
}

// ==================== 方法论实例 API（系列 / 型号页的面板数据源） ====================

/**
 * 取某归属单元（系列 / 型号）的六类实例，一次往返：
 * { deliverables, gates, reviews, rules, metrics, dashboards }
 * 阶段过滤在前端做（数据量本就很小）。
 */
export function listMethodologyInstances(ownerOid) {
  return request.get('/instances', { params: { ownerOid } })
}

/**
 * 裁剪一条方法论实例（**实例层**）：「本项目不适用」（停用 / 恢复），交付件还可改强制项。
 *
 * <p>与模板层「企业裁剪」（业务配置 → 模型定义）是两层 —— 这里只动本系列 / 型号的实例，
 * 模板与其它项目不受影响。权限限产品经理（PRODUCT_MANAGER）与租户管理员（TENANT_ADMIN），
 * 无权限时后端返回 403（前端按此提示，不静默）。
 *
 * @param {'deliverable'|'gate'|'review'|'rule'} kind
 * @param {string} oid 实例 oid
 * @param {{disabled?: boolean, mandatory?: boolean}} payload 至少给一个字段；
 *        mandatory 只对 deliverable 有效（其余三类给了会报 400）
 */
export function tailorInstance(kind, oid, payload) {
  return request.patch(`/instances/${kind}/${oid}`, payload)
}

// ==================== 交付件 ↔ 产出物 关联 API（2026-10-11） ====================
// 交付件的产出物**不都是文档**：原理图 / PCB（工程数据）、结构件 / 软件（零组件）都是业务对象，
// 三类同构（oid + 版本 + 生命周期）故统一为"产出物引用"。
// 硬约束：可关联的对象类别由模板 objectType 决定（服务端校验）；候选查询不按容器过滤
// —— 允许跨容器引用（用户要求），前端用 containerOid 标注"本型号 / 其它容器"。

/** 某交付件下的产出物关联列表（含产出物的编码 / 名称） */
export function getDeliverableArtifactLinks(deliverableOid) {
  return request.get('/deliverable-artifact-links', { params: { deliverableOid } })
}

/** 某系列 / 型号下每个交付件的已关联数：{ deliverableOid: cnt } */
export function getDeliverableArtifactSummary(ownerOid) {
  return request.get('/deliverable-artifact-links/summary', { params: { ownerOid } })
}

/**
 * 某交付件的产出物进展（「状态」列悬停浮层用，2026-10-25）：
 * 每条产出物的最新迭代状态（statusCode / statusName）与是否在流程中（inProcess / processName /
 * currentNode / currentAssignees）。悬停才拉，不随列表预加载。
 */
export function getDeliverableArtifactProgress(deliverableOid) {
  return request.get('/deliverable-artifact-links/progress', { params: { deliverableOid } })
}

/** 候选产出物（服务端按交付件的 objectType / bindMode 硬约束过滤；keyword 可空） */
export function getDeliverableArtifactCandidates(deliverableOid, keyword) {
  return request.get('/deliverable-artifact-links/candidates', { params: { deliverableOid, keyword } })
}

/** 关联产出物：{ deliverableOid, entityType: DOCUMENT / ENG_DOCUMENT / PART, entityOid } */
export function createDeliverableArtifactLink(data) {
  return request.post('/deliverable-artifact-links', data)
}

/** 解除关联（只删关系，不动产出物本身） */
export function deleteDeliverableArtifactLink(oid) {
  return request.delete(`/deliverable-artifact-links/${oid}`)
}

// ==================== BOM 链接 API ====================

/** 按父迭代查询 BOM 子件（BOM 结构页签） */
export function getBomLinksByParentIteration(parentIterationOid) {
  return request.get(`/bom-links/by-parent-iteration/${parentIterationOid}`)
}

/** 递归查询某父迭代下的完整多层 BOM 树（含子件展示信息） */
export function getBomTree(parentIterationOid) {
  return request.get(`/bom-links/tree/${parentIterationOid}`)
}

/**
 * 容器（产品系列 / 型号）的**物料清单**：其下所有零件最新迭代的 BOM 行里用到的物料，
 * 按物料去重聚合（用量合计 / 引用行数 / 使用它的父件编码）。
 *
 * <p>与 {@link getBomTree} 是两种视角：那个是"某个件的结构树"（可展开层级、逐版本），
 * 这个是"这个产品一共用了哪些料"（去重汇总，面向采购 / 成本 / 齐套）。
 *
 * @param {string} containerOid 产品系列或型号 oid
 * @returns {Promise<{code:number, data:Array}>} 每项含 childPartOid / number / name /
 *   typeCode / version / status / unit / totalQuantity / lineCount / parentNumbers
 */
export function getBomMaterials(containerOid) {
  return request.get(`/bom-links/materials/${containerOid}`)
}

/**
 * BOM 成本报告（卷积）：后端一次给出总成本与逐层明细（含每行的单位成本/本层金额/累计金额）。
 *
 * <p>入参是<b>父件迭代 oid</b>：成本是有版本的口径（BOM 行挂在迭代上）。
 */
export function getBomCostReport(parentIterationOid) {
  return request.get(`/bom-links/cost-report/${parentIterationOid}`)
}

/**
 * 容器（产品系列 / 型号）的成本趋势 —— 系列/型号页「成本趋势」分组件。
 *
 * 返回：当前卷积总成本 + 历史快照点（本层直接成本口径）+ 数据缺口提示。
 * 系列容器会自动并入旗下型号的零件（BOM 挂在型号的零件上）。
 */
export function getContainerCostTrend(containerOid) {
  return request.get(`/bom-links/cost-trend/${containerOid}`)
}

// ==================== BOM 快照 API ====================
// 快照 = 某一时刻父件迭代的顶层 BOM 行定格；只读，BOM 后续再改也不会回写它。

/**
 * 新增快照：把指定迭代当前的 BOM 行存成一份记录。
 * @param {{iterationOid:string, name:string, remark?:string}} data
 */
export function createBomSnapshot(data) {
  return request.post('/bom-snapshots', data)
}

/** 快照记录：按零件（跨版本）或按迭代过滤 */
export function getBomSnapshots(params) {
  return request.get('/bom-snapshots', { params })
}

/** 单份快照（含 BOM 明细） */
export function getBomSnapshot(oid) {
  return request.get(`/bom-snapshots/${oid}`)
}

/** 删除快照 */
export function deleteBomSnapshot(oid) {
  return request.delete(`/bom-snapshots/${oid}`)
}

/**
 * 快照对比：快照 ↔ 快照（toSnapshot）或 快照 ↔ 当前 BOM（toIteration）。
 * @param {{from:string, toSnapshot?:string, toIteration?:string}} params
 */
export function compareBomSnapshots(params) {
  return request.get('/bom-snapshots/compare', { params })
}

// ==================== 局部替代（BOM 行替代） ====================
// 与「全局替代」（part-alternates：物料主数据级）区分：这里的关系只在该 BOM 行上下文生效。

/** 某 BOM 行的局部替代件清单（带替代件编码/名称/类型/版本） */
export function getBomSubstitutesByLink(bomLinkOid) {
  return request.get(`/bom-substitutes/by-bom-link/${bomLinkOid}`)
}

/** 设置局部替代（同一行 + 同一替代件重复提交 = 更新其参数） */
export function setBomSubstitute(data) {
  return request.post('/bom-substitutes', data)
}

/** 删除一条局部替代关系 */
export function deleteBomSubstitute(oid) {
  return request.delete(`/bom-substitutes/${oid}`)
}

/** 取消替代：清空某 BOM 行的全部局部替代（返回删除条数） */
export function clearBomSubstitutes(bomLinkOid) {
  return request.delete(`/bom-substitutes/by-bom-link/${bomLinkOid}`)
}

// ==================== 成组替代（BOM 行成组替换） ====================
// 与局部替代的区别：局部替代换"一颗料"、挂在一条 BOM 行上；成组替代换"一组行"、
// 挂在父件迭代上，并带"整组替换"（atomicReplace）约束。

/** 某父件迭代下的全部成组替代组（含两侧成员） */
export function getBomSubstituteGroups(parentIterationOid) {
  return request.get(`/bom-substitute-groups/by-parent-iteration/${parentIterationOid}`)
}

/** 新建成组替代组（body：parentIterationOid / name / description / atomicReplace / enabled / sources[] / substitutes[]） */
export function createBomSubstituteGroup(data) {
  return request.post('/bom-substitute-groups', data)
}

/** 更新成组替代组（两侧成员整体替换；已批准的组被改动会退回 DRAFT） */
export function updateBomSubstituteGroup(oid, data) {
  return request.put(`/bom-substitute-groups/${oid}`, data)
}

/** 删除成组替代组（成员随组级联删除） */
export function deleteBomSubstituteGroup(oid) {
  return request.delete(`/bom-substitute-groups/${oid}`)
}

/** 成组替代组状态流转：DRAFT / APPROVED / OBSOLETE */
export function changeBomSubstituteGroupStatus(oid, status) {
  return request.put(`/bom-substitute-groups/${oid}/status`, { status })
}

// ==================== 通用件阈值配置（业务配置中心） ====================
// 口径：自研结构件被多少个型号、多少条 BOM 行引用达到阈值 → 通知管理员 → 走绑定的流程认定
// 为通用件（可跨产品系列/型号重用）。

/** 读取本租户的通用件阈值配置（未配置时后端返回默认口径） */
export function getGenPartThresholdConfig() {
  return request.get('/gen-part-threshold-config')
}

/** 保存通用件阈值配置 */
export function saveGenPartThresholdConfig(data) {
  return request.put('/gen-part-threshold-config', data)
}

/** 读取本租户的标准件入库流程配置（未配置时后端返回默认：不启用流程） */
export function getStdPartInboundConfig() {
  return request.get('/std-part-inbound-config')
}

/** 保存标准件入库流程配置 */
export function saveStdPartInboundConfig(data) {
  return request.put('/std-part-inbound-config', data)
}

/** 业务域列表（平台预置、共享表；用于类型/对象的「域」标签与「按域查看」视图） */
export function getBusinessDomains() {
  return request.get('/business-domains')
}

/**
 * 导出 BOM（csv / xls / xlsx / pdf）：当前这一版的完整多层结构 + 成本。
 *
 * <p>用 blob 而不是 window.open：这个接口要鉴权（Authorization 头），window.open 带不上 token。
 * 返回的是文件字节本身（响应拦截器只在"带 code+data 的包装体"上做判断，blob 会原样透传）。
 *
 * @param parentIterationOid 父件迭代 oid（哪一版）—— 与成本报告同口径
 * @param format csv | xls | xlsx | pdf
 */
export function exportBomFile(parentIterationOid, format = 'xlsx') {
  return request.get(`/bom-links/export/${parentIterationOid}`, {
    params: { format },
    responseType: 'blob',
    timeout: 60000,
  })
}

/** 创建 BOM 行（把指定 Part 添加为子件） */
export function createBomLinks(data) {
  return request.post('/bom-links', data)
}

/** 删除 BOM 行（移除子件） */
export function deleteBomLinks(oid) {
  return request.delete(`/bom-links/${oid}`)
}

/** 更新 BOM 行（行号/数量/单位/单位成本等） */
export function updateBomLinks(oid, data) {
  return request.put(`/bom-links/${oid}`, data)
}

/** 按子件 Part 查询反向 BOM（被使用情况页签） */
export function getBomLinksByChildPart(childPartOid) {
  return request.get(`/bom-links/by-child-part/${childPartOid}`)
}

/**
 * 被使用情况（反向 BOM）的可读口径：某零件被哪些父件引用。
 * 父件已解析成 编码 / 名称 / 版本，并带 parentPartOid —— 拿它再调一次本接口即「往上一层」。
 */
export function getPartUsedBy(partOid) {
  return request.get(`/bom-links/used-by/${partOid}`)
}

/** 查询两个 BOM 版本之间的差异（预计算数据，由检入时生成） */
export function getBomDiff(fromIterationOid, toIterationOid) {
  return request.get('/bom-diffs/between', { params: { fromIterationOid, toIterationOid } })
}

/** 实时对比两个 BOM 版本的顶层行差异（新增/移除/修改，不依赖预计算） */
export function compareBomVersions(fromIterationOid, toIterationOid) {
  return request.get('/bom-diffs/compare', { params: { fromIterationOid, toIterationOid } })
}

// ==================== 部件双向替代 API ====================

/** 创建双向替代关系（roleA/roleB 有序对，后端自动规范化） */
export function createPartAlternateLink(data) {
  return request.post('/part-alternate-links', data)
}

/** 更新双向替代关系 */
export function updatePartAlternateLink(oid, data) {
  return request.put(`/part-alternate-links/${oid}`, data)
}

/** 删除双向替代关系 */
export function deletePartAlternateLink(oid) {
  return request.delete(`/part-alternate-links/${oid}`)
}

/** 查询某部件作为 roleA（被替代方，roleB 为其替代件）的替代关系 */
export function getPartAlternateLinksByRoleA(partOid) {
  return request.get(`/part-alternate-links/by-role-a/${partOid}`)
}

/** 查询某部件作为 roleB（替代方，roleA 为被其替代的部件）的替代关系 */
export function getPartAlternateLinksByRoleB(partOid) {
  return request.get(`/part-alternate-links/by-role-b/${partOid}`)
}

/** 查询与某部件相关的全部替代关系（无论角色端） */
export function getPartAlternateLinksByPart(partOid) {
  return request.get(`/part-alternate-links/by-part/${partOid}`)
}

// ============ 零件-文档关联 API（Windchill 两分模型：DESCRIBES 定义 / REFERENCE 参考） ============
// 已废弃原 part-document-links（主数据级），统一到 part-doc-links（迭代级，支持版本锁定与晋升）

/** 查询关联：params = { partOid | partIterationOid, linkType? }（linkType 为空返回全部） */
export function getPartDocLinks(params = {}) {
  return request.get('/part-doc-links', { params })
}

/** 创建关联：{ partOid | partIterationOid, docMasterOid, docIterationOid?, linkType, category? } */
export function createPartDocLink(data) {
  return request.post('/part-doc-links', data)
}

/** 删除关联：linkType 指明类别（DESCRIBES / REFERENCE） */
export function deletePartDocLink(oid, linkType = 'REFERENCE') {
  return request.delete(`/part-doc-links/${oid}`, { params: { linkType } })
}

/** 晋升：REFERENCE → DESCRIBES（进入技术状态基线） */
export function promotePartDocLink(oid) {
  return request.post(`/part-doc-links/${oid}/promote`)
}

// ============ 零件-工程数据关联 API（Windchill EPMBuildRule：零件版本 ↔ 工程数据版本） ============

/**
 * 查询构建规则。
 * @param {{partOid?:string, partIterationOid?:string, engDocumentOid?:string}} params
 *   传零件侧参数 → 该零件版本关联的工程数据；传 engDocumentOid → 引用它的零件版本
 */
export function getEngDocPartLinks(params = {}) {
  return request.get('/eng-doc-part-links', { params })
}

/**
 * 建立关联：
 * { partOid | partIterationOid, engDocumentOid, engDocIterationOid?, assocType?,
 *   buildStructure?, buildAttribute?, buildRepresentation?, primary?, sortOrder? }
 */
export function createEngDocPartLink(data) {
  return request.post('/eng-doc-part-links', data)
}

/** 修改关联（关联类型 / 构建规则 / 锁定的工程数据版本） */
export function updateEngDocPartLink(oid, data) {
  return request.put(`/eng-doc-part-links/${oid}`, data)
}

/** 解除关联 */
export function deleteEngDocPartLink(oid) {
  return request.delete(`/eng-doc-part-links/${oid}`)
}

// ==================== 企业资源库-电子元器件库 API ====================
// 元器件为 ELECTRONIC 软类型的 Part（创建走 /parts），本模块仅负责分类绑定与清单查询

/** 查询企业资源库根节点下的全部资源子库（按 sort_order 排序） */
export function getResourceChildren() {
  return request.get('/resource-containers/children')
}

/** 查询本租户的企业资源库根节点（未初始化时 data 为 null） */
export function getResourceRoot() {
  return request.get('/resource-containers/root')
}

/**
 * 资源库分类树（来自分类管理，未绑定时 data 为 null）。
 * resourceCode: COMPONENT 元器件库 / STD_PART 标准件库 / GEN_PART 通用件库
 */
export function getLibraryCategoryTree(resourceCode) {
  return request.get('/resource-libraries/category-tree', { params: resourceCode ? { resourceCode } : {} })
}

/** 查询指定资源库绑定的根分类 oid（供业务配置回显） */
export function getLibraryCategoryBinding(resourceCode) {
  return request.get('/resource-libraries/category-binding', { params: resourceCode ? { resourceCode } : {} })
}

/**
 * 一次性查询所有资源库（COMPONENT / STD_PART / GEN_PART）的分类根节点绑定，
 * 含平台租户回退。返回 { COMPONENT: oid, STD_PART: oid, GEN_PART: oid }，
 * 缺失的 key 表示该资源库未绑定。
 * 用于「业务配置 → 资源库分类」页面加载（替代 N 次单条调用）。
 */
export function getAllCategoryBindings() {
  return request.get('/resource-libraries/category-bindings')
}

/** 绑定资源库的分类根节点（仅业务配置调用）：resourceCode = COMPONENT/STD_PART/GEN_PART */
export function bindLibraryCategory(resourceCode, rootClassificationOid) {
  return request.post('/resource-libraries/category-binding', { resourceCode, rootClassificationOid })
}

/** 企业资源容器上下文（containerOid/containerType/stageOid），元器件新建时使用 */
export function getElectronicComponentContext() {
  return request.get('/resource-libraries/context')
}

/** 按分类集合查询元器件清单（categoryOids 逗号分隔，可空 = 全部；返回 { items, context }） */
export function getElectronicComponents(params = {}) {
  return request.get('/resource-libraries', { params })
}

// ==================== 封装·图符库 API（PACKAGE_SYMBOL 资源子库） ====================

/** 封装·图符库归属上下文（containerOid / containerType / containerCode / containerName / stageOid） */
export function getPackageSymbolContext() {
  return request.get('/package-symbols/context')
}

/** 封装 / 图符清单：folderOid 可空=全部；typeCode 可选 FOOTPRINT / SYMBOL；keyword 名称或编码 */
export function getPackageSymbols(params = {}) {
  return request.get('/package-symbols', { params })
}

// ==================== 技术文档知识库 API（TECH_DOC 资源子库） ====================
// 业务对象是通用文档（Document / ck_document），与封装·图符库的 EPMDocument 不是一类，
// 所以接口也独立：/tech-docs 只列本资源子库容器下的文档。

/** 技术文档库归属上下文（containerOid / containerType / containerCode / containerName / stageOid） */
export function getTechDocContext() {
  return request.get('/tech-docs/context')
}

/** 技术文档清单：folderOid 可空=该库全部；keyword 匹配名称/编码/主文件名 */
export function getTechDocs(params = {}) {
  return request.get('/tech-docs', { params })
}

// ==================== 问题（持续改进 —— 跟踪与闭环） API ====================

/** 问题列表：status / severity / owner / keyword 可空；分页 { page, pageSize }（返回 { rows, total, page, pageSize }） */
export function listIssues(params = {}) {
  return request.get('/issues', { params })
}

/** 各状态计数 + 闭环率（闭环率 = CLOSED /（总数 - CANCELLED）） */
export function getIssueStats() {
  return request.get('/issues/stats')
}

/** 详情（问题本体 + 处理历程时间线 { issue, activities }） */
export function getIssueDetail(oid) {
  return request.get(`/issues/${oid}`)
}

/** 新建（编号自动生成，状态恒为 OPEN；提出人 = 当前用户） */
export function createIssue(data) {
  return request.post('/issues', data)
}

/** 编辑（patch 语义：缺席字段不改、空串清空；已关闭/已取消不可编辑） */
export function updateIssue(oid, data) {
  return request.put(`/issues/${oid}`, data)
}

/** 状态流转（闭环核心）：{ action, comment, verifyResult } —— action 见 utils/issueFlow.js */
export function transitionIssue(oid, payload) {
  return request.post(`/issues/${oid}/transition`, payload)
}

/** 追加跟踪备注（不改状态，只往时间线加一条） */
export function addIssueComment(oid, comment) {
  return request.post(`/issues/${oid}/comments`, { comment })
}

/**
 * 关联对象候选（表单里「关联对象编码」的模糊搜索，2026-10-25）。
 *
 * @param {{keyword?: string, type?: string, limit?: number}} params
 *   type = PART / DOCUMENT / ENG_DOCUMENT / PRODUCT_LINE / PRODUCT_MODEL（空 = 五类都查）
 * @returns {Promise<{code, data: Array<{oid, entityType, code, name}>}>}
 */
export function listIssueObjectCandidates(params = {}) {
  return request.get('/issues/object-candidates', { params })
}

/** 删除（连带时间线；一般问题应关闭而不是删除） */
export function deleteIssue(oid) {
  return request.delete(`/issues/${oid}`)
}

/**
 * 门的 ISSUE_OPEN 自动检查（"开门前还有几个未闭环问题"）：
 * 逐门判定只算挂在该门上（gate_oid 相同）的未闭环问题；openTotal / blockOpen / warnOpen
 * 是产品级汇总。返回 { ownerOid, openTotal, blockOpen, warnOpen, gates, items }。
 */
export function getIssueGateCheck(ownerOid) {
  return request.get('/issues/gate-check', { params: { ownerOid } })
}

// ==================== 变更管理（ECR / ECN / 变更任务）====================
// 状态与动作口径见 utils/changeFlow.js；真正的闸门在后端（非法流转返回 400/403）。

/**
 * 变更单挑问题的候选 → [{ oid, code, name, status }]。
 *
 * <p>只含<b>还能被关联</b>的问题（OPEN / IN_PROGRESS / VERIFYING），区别于
 * listIssues（问题台账，含已关闭）。下拉用这个，避免用户选到已闭环的问题才被后端退回。
 */
export function listChangeIssueOptions(params = {}) {
  return request.get('/plm/change-issues/options', { params })
}

/** 变更请求列表 { status, keyword, page, pageSize } → { rows, total, page, pageSize } */
export function listChangeRequests(params = {}) {
  return request.get('/plm/change-requests', { params })
}

/** 变更请求各状态计数 → { total, counts: { DRAFT: 0, ... } } */
export function getChangeRequestStats() {
  return request.get('/plm/change-requests/stats')
}

/** 变更请求详情 → { request, activities, issues, notices, affectedObjects } */
export function getChangeRequest(oid) {
  return request.get(`/plm/change-requests/${oid}`)
}

/**
 * 对象候选（变更单的「预估受影响对象」选择器）。
 *
 * <p>零件 / 文档 / 工程文档三类各取 limit 条：`[{ oid, entityType, code, name, containerOid }]`。
 *
 * <p>`containerOid` 传当前选中的产品系列/型号时，服务端只在它（含子系列与其下型号）里检索。
 */
export function listChangeObjectCandidates(keyword, limit = 20, containerOid) {
  return request.get('/plm/change-requests/object-candidates', {
    params: { keyword: keyword || undefined, limit, containerOid: containerOid || undefined }
  })
}

/**
 * 收集所选对象关联的其他对象（更改事项挑对象时的「收集关联对象」）。
 *
 * <p>当前口径：只对零件生效 —— 关联文档（DESCRIBES + REFERENCE）+ 替代件（双向），BOM 不收集。
 * 返回 `[{ oid, entityType, code, name, containerOid, relation, alternateType? }]`，
 * `relation` = DOCUMENT / ALTERNATE，服务端已按 oid 去重。
 */
export function listRelatedChangeObjects(entityOids) {
  return request.get('/plm/change-requests/related-objects', {
    params: { entityOids: (entityOids || []).join(',') }
  })
}

/** 预估受影响对象 → [{ oid, entityType, entityOid, entityCode, entityName, containerOid, containerName }] */
export function listChangeRequestAffectedObjects(oid) {
  return request.get(`/plm/change-requests/${oid}/affected-objects`)
}

/** 添加预估受影响对象：只需 { entityOid, note }，类型/编码/名称/容器由服务端回查 */
export function addChangeRequestAffectedObject(oid, data) {
  return request.post(`/plm/change-requests/${oid}/affected-objects`, data)
}

/** 移除预估受影响对象 */
export function removeChangeRequestAffectedObject(oid, affectedOid) {
  return request.delete(`/plm/change-requests/${oid}/affected-objects/${affectedOid}`)
}

/** 新建（编号自动生成 ECR-####，状态恒为 DRAFT，提出人 = 当前用户） */
export function createChangeRequest(data) {
  return request.post('/plm/change-requests', data)
}

/** 编辑（patch 语义：缺席字段不改） */
export function updateChangeRequest(oid, data) {
  return request.put(`/plm/change-requests/${oid}`, data)
}

/** 流转 { action, comment } —— action 见 CHANGE_REQUEST_ACTIONS */
export function transitionChangeRequest(oid, payload) {
  return request.post(`/plm/change-requests/${oid}/transition`, payload)
}

/** 删除（仅草稿） */
export function deleteChangeRequest(oid) {
  return request.delete(`/plm/change-requests/${oid}`)
}

/** 变更通告列表 { status, requestOid, keyword, page, pageSize } */
export function listChangeNotices(params = {}) {
  return request.get('/plm/change-notices', { params })
}

/** 各状态计数 → { total, counts: { DRAFT: 0, ... } } */
export function getChangeNoticeStats() {
  return request.get('/plm/change-notices/stats')
}

/** 详情聚合 → { notice, request, affectedObjects, tasks, activities } */
export function getChangeNotice(oid) {
  return request.get(`/plm/change-notices/${oid}`)
}

/**
 * 按对象反查变更通告 —— 对象详情页「变更情况」用。
 *
 * <p>口径是**变更通告的受影响对象**（ck_change_affected_object，主对象级），
 * 一行 = 某张通告上的这一条变更对象（被修订版本 / 修订产物 / 执行时间 + 通告状态）。
 *
 * @param {'PART'|'DOCUMENT'|'ENG_DOCUMENT'} entityType
 * @param {string} entityOid 主对象 oid（不是迭代 oid）
 */
export function listChangeNoticesByEntity(entityType, entityOid) {
  return request.get('/plm/change-notices/by-entity', { params: { entityType, entityOid } })
}

export function createChangeNotice(data) {
  return request.post('/plm/change-notices', data)
}

export function updateChangeNotice(oid, data) {
  return request.put(`/plm/change-notices/${oid}`, data)
}

/** 流转 { action, comment } —— 免审提交时 comment 即免审原因（必填） */
export function transitionChangeNotice(oid, payload) {
  return request.post(`/plm/change-notices/${oid}/transition`, payload)
}

/**
 * 管理员直接设置单据状态（运维纠错）：{ status, reason }。
 *
 * <p>与 {@link transitionChangeNotice} 分开：那条走状态机、责任人就能推；这条跳过规则与
 * 门槛，只有租户管理员能用，服务端另有 403 兜底。
 */
export function setChangeNoticeStatus(oid, payload) {
  return request.post(`/plm/change-notices/${oid}/status`, payload)
}

export function deleteChangeNotice(oid) {
  return request.delete(`/plm/change-notices/${oid}`)
}

/**
 * 按 oid 反查变更单据（请求 / 通告 / 任务）→ { kind, oid, code, name, status }。
 *
 * <p>流程侧只交出 businessKey，不认识业务表；任务中心 / 流程监控靠这条把
 * "办的是哪一张变更单"补上（见 {@code useEntityLabels}）。不是变更单据时后端返回 404，
 * 调用方按"没有业务对象"处理即可。
 */
export function getChangeDocument(oid) {
  return request.get(`/plm/change-documents/${oid}`)
}

export function listChangeAffectedObjects(oid) {
  return request.get(`/plm/change-notices/${oid}/affected-objects`)
}

/** 添加受影响对象（必须指定具体版本：baseIterationOid + baseVersion） */
export function addChangeAffectedObject(oid, data) {
  return request.post(`/plm/change-notices/${oid}/affected-objects`, data)
}

/**
 * 按对象 oid 添加受影响对象（受影响版本由服务端按对象当前迭代回查）。
 *
 * <p>更改事项上直接挑对象时用：前端只有对象 oid，不该先查一遍版本再 POST。
 * 幂等 —— 对象已在本通告里时返回既有那行。
 */
export function addChangeAffectedByEntity(oid, entityOid) {
  return request.post(`/plm/change-notices/${oid}/affected-objects/by-entity`, { entityOid })
}

export function removeChangeAffectedObject(oid, affectedOid) {
  return request.delete(`/plm/change-notices/${oid}/affected-objects/${affectedOid}`)
}

/** 把受影响对象改派到某个更改事项（变更任务）；taskOid 传空 = 取消归属 */
export function assignChangeAffectedTask(oid, affectedOid, taskOid) {
  return request.put(`/plm/change-notices/${oid}/affected-objects/${affectedOid}/task`, { taskOid: taskOid || '' })
}

/** 我的任务（assignee 缺省 = 当前用户） */
export function listMyChangeTasks(params = {}) {
  return request.get('/plm/change-tasks/my', { params })
}

/** 变更任务各状态计数（assignee 为空 = 全部）→ { total, counts: { OPEN: 0, ... } } */
export function getChangeTaskStats(params = {}) {
  return request.get('/plm/change-tasks/stats', { params })
}

export function listChangeTasksByNotice(noticeOid) {
  return request.get(`/plm/change-tasks/by-notice/${noticeOid}`)
}

export function createChangeTask(data) {
  return request.post('/plm/change-tasks', data)
}

export function updateChangeTask(oid, data) {
  return request.put(`/plm/change-tasks/${oid}`, data)
}

/** 流转 { action, comment }；START / SUBMIT 仅任务责任人可执行 */
export function transitionChangeTask(oid, payload) {
  return request.post(`/plm/change-tasks/${oid}/transition`, payload)
}

/**
 * 派发某通告下的全部草稿任务。
 *
 * <p>审批通过时后端会自动派发，界面自 2026-11-03 起不再提供入口（用户口径「不需要派发操作」）；
 * 保留这个包装是给运维/脚本兜底用的，新代码不要在前端调它。
 */
export function dispatchChangeTasks(noticeOid) {
  return request.post(`/plm/change-tasks/by-notice/${noticeOid}/dispatch`)
}

export function deleteChangeTask(oid) {
  return request.delete(`/plm/change-tasks/${oid}`)
}

/** 变更租户配置（免审开关）→ { approvalRequired, approvalProcessKey, bypassForReleased } */
export function getChangeConfig() {
  return request.get('/plm/change-config')
}

export function updateChangeConfig(data) {
  return request.put('/plm/change-config', data)
}

// ==================== CAD 轻量化可视化 ====================

/**
 * 转换器是否可用。
 * 未部署转换器时返回 false —— 前端据此隐藏 3D 入口，
 * 而不是给用户一个永远转不完的加载圈。
 */
export function getCadViewerAvailable() {
  return request.get('/cad-visualizations/available')
}

/**
 * 触发 CAD 转换（异步提交）。
 *
 * 立刻返回，status = QUEUED；真正的结果靠轮询 getLatestCadVisualization 拿。
 * 不要在调用处同步等待 —— 转换可能跑几十秒到几分钟。
 *
 * @param {{engDocIterationOid: string, sourceCkfileOid: string}} data
 */
export function convertCadModel(data) {
  return request.post('/cad-visualizations/convert', data)
}

/** 查询某迭代最近一次转换记录（前端轮询入口） */
export function getLatestCadVisualization(engDocIterationOid) {
  return request.get('/cad-visualizations/latest', { params: { engDocIterationOid } })
}

/**
 * 容器（产品系列 / 型号）下配套的三维数模清单 —— 「产品数模」页签。
 *
 * 一行 = 一个工程数据（ENG_DOCUMENT）最新迭代上的一个 CAD 主文件
 * （.step/.stp/.iges/.igs/.stl），加上它最近一次的轻量化转换状态。
 * 注意载体是 EPMDocument（ck_eng_document），不是通用文档（ck_document）。
 * 后端一次查全，不要逐行调 latest（N+1）。
 *
 * @param {string} containerOid 产品系列或型号 oid
 * @returns {Promise<{code:number, data:Array}>} 每项含 engDocNumber / engDocName / cadName /
 *   iterationOid / sourceCkfileOid / fileName / status / glbCkfileOid / triangleCount 等，
 *   status 为空表示未转换
 */
export function getContainerCadModels(containerOid) {
  return request.get('/cad-visualizations/models', { params: { containerOid } })
}

/**
 * 单个零件版本的数模与转换状态 —— BOM 右侧「轻量化」页签。
 *
 * 库里链条：零件迭代 →（EPMBuildRule）→ 工程数据迭代 → 主文件 → 最近一次转换记录。
 * data 为 null = 该零件版本没关联工程数据 / 主文件不是可轻量化的数模；
 * 有值时含 iterationOid + sourceCkfileOid（提交转换用）与 status/glbCkfileOid（渲染用）。
 *
 * @param {string} partIterationOid 零件迭代 oid（不是零件主对象 oid）
 */
export function getCadModelByPartIteration(partIterationOid) {
  return request.get(`/cad-visualizations/by-part-iteration/${partIterationOid}`)
}

/**
 * 批量提交轻量化转换。
 *
 * 为什么不前端循环调 convertCadModel：N 次 HTTP + N 条提示，中途失败没法汇总，
 * 用户也不知道到底哪几份进去了。后端逐条处理并返回分类结果。
 *
 * @param {Array<{iterationOid:string, ckfileOid:string}>} items
 * @returns {Promise<{code:number, data:{total:number, accepted:number, cached:number,
 *   skipped:number, failed:number, items:Array}}>}
 */
export function convertCadBatch(items) {
  return request.post('/cad-visualizations/convert-batch', items)
}

/**
 * 取消排队中 / 转换中的转换任务（批量误提交后的止损手段）。
 * 只有 QUEUED / RUNNING 可取消，其它状态后端返回 409。
 */
export function cancelCadVisualization(oid) {
  return request.post(`/cad-visualizations/${oid}/cancel`)
}

/**
 * 转换器（CK-CADForge）对接配置 —— 「业务配置 → 轻量化转换」。
 *
 * 这些值原先只在 application*.yml，2026-10-26 起下沉到 ck_cad_forge_config：
 * 改地址/超时不再需要改文件重启。令牌不回传明文，只回 callbackTokenSet。
 */
export function getCadForgeConfig() {
  return request.get('/cad-forge/config')
}

/** 保存并立即生效（后端刷内存 + 重建 HTTP 客户端，不需要重启） */
export function updateCadForgeConfig(config) {
  return request.put('/cad-forge/config', config)
}

/** 测试连接：用已生效的配置探一次转换器 /healthz，返回 engine / 并发 / 车道 */
export function testCadForgeConnection() {
  return request.post('/cad-forge/config/test')
}

/**
 * 「数模转换中心」：跨容器、跨人的全局转换队列。
 *
 * 与 getContainerCadModels 的分工：那个回答"这个产品有哪些数模"，
 * 这个回答"全公司现在有多少张图在排队 / 在转 / 哪些失败了"。
 * 批量提交之后，后者才是操作员常驻的页面。
 *
 * @param {{filter?:string, keyword?:string, page?:number, size?:number}} params
 *   filter: active（排队+转换中）/ success / failed（失败+超时）/ skipped；空=全部
 * @returns {Promise<{code:number, data:{list:Array, total:number, page:number, size:number, stats:Object}}>}
 */
export function getCadConvertJobs(params) {
  return request.get('/cad-visualizations/jobs', { params })
}

/**
 * 「下载装配数模」：把本容器下全部数模源文件打成一个 zip。
 *
 * 必须走 blob：接口要 Authorization，`window.open` / `<a href>` 带不上请求头会被 401 挡下。
 * 超时单独放宽到 2 分钟 —— 默认 15s 对几十 MB 的数模包不够。
 */
export function downloadContainerCadModels(containerOid) {
  return request.get('/cad-visualizations/models/package', {
    params: { containerOid },
    responseType: 'blob',
    timeout: 120000,
  })
}

/** 查询单条转换记录 */
export function getCadVisualization(oid) {
  return request.get(`/cad-visualizations/${oid}`)
}

/** 重新转换（失败后手动再试） */
export function retryCadVisualization(oid) {
  return request.post(`/cad-visualizations/${oid}/retry`)
}

/** 文件流地址（GLB / 结构树 JSON / 缩略图都走既有 ckfiles 通道） */
export function ckFileStreamUrl(oid) {
  return `/api/ckfiles/${oid}/stream`
}

export default request
