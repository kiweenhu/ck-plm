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
    // 后端 ApiResponse 统一包装 { code, message, data }
    if (res.code !== 200) {
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

/** 获取通知列表 */
export function getNotifications(limit = 20) {
  return request.get('/notifications', { params: { limit } })
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

/** 获取当前租户的 ADMIN 角色及成员 */
export function getAdminMembers() {
  return request.get('/roles/admin-members')
}

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
 * @param {string} entityCode 实体编码，如 PRODUCT_LINE
 * @param {string} oid 实体 ID
 */
export function getEntityByCode(entityCode, oid) {
  const path = ENTITY_API_PATH[entityCode] || entityCode.toLowerCase().replace(/_/g, '-')
  return request.get(`/${path}/${oid}`)
}

/** 实体编码 → API 路径映射，新增实体类型时在此添加即可 */
const ENTITY_API_PATH = {
  PRODUCT_LINE: 'product-lines',
  PRODUCT_MODEL: 'product-models',
  DOCUMENT: 'documents',
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

/** 获取产品线的阶段列表 */
export function getStages(productLineOid) {
  return request.get(`/product-lines/${productLineOid}/stages`)
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

/** 按文件夹查询零组件 VO */
export function getPartsByFolder(folderOid) {
  return request.get('/parts/by-folder', { params: { folderOid } })
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

/** 检出文档（通用入口，传 entityType） */
export function checkoutDocument(oid, comment) {
  return request.post('/checkout/checkout', { entityType: 'DOCUMENT', entityOid: oid, comment })
}

/** 取消检出文档 */
export function undoCheckoutDocument(oid) {
  return request.post('/checkout/undo-checkout', { entityType: 'DOCUMENT', entityOid: oid })
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

export default request
