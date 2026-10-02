import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 用户状态管理。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref('')
  const displayName = ref('')
  const oid = ref('')
  const email = ref('')
  const phone = ref('')
  const orgOid = ref('')
  const roles = ref([])
  const tenantOid = ref('')
  const tenantName = ref('')

  /** token 过期时间戳（毫秒），用于前端快速判断 */
  const tokenExpireAt = ref(parseInt(localStorage.getItem('tokenExpireAt') || '0'))

  /** 是否已登录（含前端过期检查） */
  const isLoggedIn = computed(() => {
    if (!token.value) return false
    if (tokenExpireAt.value && Date.now() > tokenExpireAt.value) return false
    return true
  })

  /** 用户显示名称 */
  const userDisplayName = computed(() => displayName.value || username.value || '用户')

  /** 初始化 —— 仅在 store 未初始化时从 localStorage 恢复登录态 */
  function init() {
    // 如果 store 已有数据（如路由守卫已通过 verifyToken 设置了数据），不再覆盖
    if (username.value) return
    const stored = localStorage.getItem('user')
    if (stored) {
      try {
        const parsed = JSON.parse(stored)
        username.value = parsed.username || ''
        displayName.value = parsed.displayName || ''
        oid.value = parsed.oid || ''
        email.value = parsed.email || ''
        phone.value = parsed.phone || ''
        orgOid.value = parsed.orgOid || ''
        roles.value = parsed.roles || []
        tenantOid.value = parsed.tenantOid || ''
        tenantName.value = parsed.tenantName || ''
      } catch { /* ignore */ }
    }
  }

  /** 登录成功 */
  function setLogin(data) {
    token.value = data.token
    username.value = data.username || ''
    displayName.value = data.displayName || ''
    oid.value = data.oid || ''
    email.value = data.email || ''
    phone.value = data.phone || ''
    orgOid.value = data.orgOid || ''
    roles.value = data.roles || []
    // 注意：/auth/verify（路由守卫每次刷新都会调）与 /auth/login 的返回体里都没有 tenantName，
    // 租户名由 MainLayout 调 /tenants/current 补齐。早前这两行是无条件赋值 —— 于是每次刷新
    // 都被 verify 的返回值把已有的租户名清成空串，侧边栏"租户信息"变成空白。
    // 缺字段时保留原值（token 校验场景就是"补全登录态"，不是"重置"）。
    tenantOid.value = data.tenantOid || tenantOid.value || ''
    tenantName.value = data.tenantName || tenantName.value || ''

    // 设置过期时间为 3 天后
    const expireMs = Date.now() + 3 * 24 * 60 * 60 * 1000
    tokenExpireAt.value = expireMs

    localStorage.setItem('token', data.token)
    localStorage.setItem('tokenExpireAt', expireMs.toString())
    // 存"store 当前值"而不是 data.* 原样：verify 返回体缺 tenantName 时，
    // 写 data.tenantName（undefined）会被 JSON 直接丢掉 —— 刷新后 tenantName 又变空。
    localStorage.setItem('user', JSON.stringify({
      username: username.value,
      displayName: displayName.value,
      oid: oid.value,
      email: email.value,
      phone: phone.value,
      orgOid: orgOid.value,
      roles: roles.value,
      tenantOid: tenantOid.value,
      tenantName: tenantName.value
    }))
  }

  /** 更新用户信息（个人信息编辑后同步） */
  function setUserInfo(info) {
    if (info.displayName !== undefined) displayName.value = info.displayName
    if (info.email !== undefined) email.value = info.email
    if (info.phone !== undefined) phone.value = info.phone

    // 同步到 localStorage
    const stored = localStorage.getItem('user')
    if (stored) {
      try {
        const parsed = JSON.parse(stored)
        parsed.displayName = displayName.value
        parsed.email = email.value
        parsed.phone = phone.value
        localStorage.setItem('user', JSON.stringify(parsed))
      } catch { /* ignore */ }
    }
  }

  /** 登出 */
  function clearLogin() {
    token.value = ''
    username.value = ''
    displayName.value = ''
    oid.value = ''
    email.value = ''
    phone.value = ''
    orgOid.value = ''
    roles.value = []
    tenantOid.value = ''
    tenantName.value = ''
    tokenExpireAt.value = 0
    localStorage.removeItem('token')
    localStorage.removeItem('tokenExpireAt')
    localStorage.removeItem('user')
  }

  return {
    token, username, displayName, oid, email, phone, orgOid, roles,
    tenantOid, tenantName,
    tokenExpireAt, isLoggedIn, userDisplayName,
    init, setLogin, setUserInfo, clearLogin
  }
})
