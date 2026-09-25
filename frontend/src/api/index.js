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

/** 克隆平台级页面布局到当前租户 */
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
 * 「设置生命周期状态」的候选项 —— 依据该类型绑定的生命周期模板。
 *
 * 返回 { templateCode, templateName, current, initial, states:[{code,name,reachable,reason}] }：
 * 状态清单、初始状态、以及"从当前状态能否一步迁到它"（不可达的带原因，界面据此禁用）。
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
 * @param {object} data { typeDefinitionCode, mode, targetStateCode? }
 *   mode=INITIAL：退回模板初始状态（沿模板回退规则逐跳，如 已发布 → 工作中 → 草稿）
 *   mode=SPECIFIED：设到 targetStateCode（要求当前状态能一步迁到它）
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

/** 新建视图版本（revision+1，iteration=1） */
export function newViewVersionEngineeringDocument(oid) {
  return request.post(`/eng-documents/${oid}/new-view-version`)
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

/** 新建零组件视图版本（新的大版本） */
export function newViewVersionPart(oid) {
  return request.post(`/parts/${oid}/new-view-version`)
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

/** 新建文档视图版本（新的大版本） */
export function newViewVersionDocument(oid) {
  return request.post(`/documents/${oid}/new-view-version`)
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

/** 获取平台租户的分类树（用于克隆预览） */
export function getPlatformTree() {
  return request.get('/classifications/platform-tree')
}

/** 从平台克隆指定根分类到当前租户（支持多选） */
export function clonePlatform(rootOids) {
  return request.post('/classifications/clone-platform', { rootOids })
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
 * 我的待办任务（任务中心「待办任务」页签 + 个人中心待办卡片共用）。
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
 * 我最近创建/修改过的业务对象（个人中心）。
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
 * BOM 成本报告（卷积）：后端一次给出总成本与逐层明细（含每行的单位成本/本层金额/累计金额）。
 *
 * <p>入参是<b>父件迭代 oid</b>：成本是有版本的口径（BOM 行挂在迭代上）。
 */
export function getBomCostReport(parentIterationOid) {
  return request.get(`/bom-links/cost-report/${parentIterationOid}`)
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

// ==================== 企业资源库-电子元器件库 API ====================
// 元器件为 ELECTRONIC 软类型的 Part（创建走 /parts），本模块仅负责分类绑定与清单查询

/** 查询企业资源库根节点下的全部资源子库（按 sort_order 排序） */
export function getResourceChildren() {
  return request.get('/resource-containers/children')
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

export default request
