/**
 * 角色可见范围 —— "当前登录人能看/能维护哪些角色"。
 *
 * <h3>规则</h3>
 * <p><b>平台级角色不出现在租户管理界面里</b>：租户下不可能有平台管理员，把它列出来只会引导误配 ——
 * 租户管理员把自己（或别人）加进「平台管理员」，就等于越过了租户边界拿到跨租户权限，
 * 而这条路径不该在界面上存在。反过来，平台管理员本人登录时<b>不过滤</b>：那些角色正是他要维护的。
 *
 * <p>适用范围：角色定义（/org/roles）与角色成员（/org/admins）两个页面 ——
 * 两处必须同一口径，否则会一边藏一边露（例如成员页选得到、定义页看不到）。
 *
 * <p>纯函数（清单 + 查看人角色 → 清单），所以能脱离组件直接断言。
 */

/** 只有平台管理员能看/配的平台角色编码（以后再有同类角色，往这里加即可） */
export const PLATFORM_ONLY_ROLE_CODES = ['PLATFORM_ADMIN']

/** 当前登录人是否为平台管理员 */
export function isPlatformAdmin(roleCodes) {
  return (roleCodes || []).includes('PLATFORM_ADMIN')
}

/**
 * 过滤出当前登录人可见的角色。
 *
 * @param {Array<{code: string}>} roles 接口返回的角色清单
 * @param {string[]} viewerRoleCodes 当前登录人的角色编码（如 ['TENANT_ADMIN']）
 * @returns {Array} 平台管理员登录 → 原样返回；其余人 → 去掉平台专属角色
 */
export function visibleRoles(roles, viewerRoleCodes) {
  const list = roles || []
  if (isPlatformAdmin(viewerRoleCodes)) return list
  return list.filter(r => r && !PLATFORM_ONLY_ROLE_CODES.includes(r.code))
}
