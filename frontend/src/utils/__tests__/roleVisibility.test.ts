/**
 * 角色可见范围：平台管理员角色对租户管理员不可见。
 *
 * <p><b>背景</b>：租户下不可能出现平台管理员。角色定义 / 角色成员两页若把「平台管理员」
 * 列出来，租户管理员就能把自己加进去 —— 越过租户边界拿到跨租户权限。
 * 本文件钉住这条规则，以及"平台管理员本人登录时不过滤"这个反面。
 */

import { describe, expect, it } from 'vitest'
import { PLATFORM_ONLY_ROLE_CODES, isPlatformAdmin, visibleRoles } from '@/utils/roleVisibility'

const roles = [
  { code: 'AUDIT_ADMIN', name: '审计管理员', roleType: 'PLATFORM' },
  { code: 'CATEGORY_ADMIN', name: '分类管理员', roleType: 'PLATFORM' },
  { code: 'PLATFORM_ADMIN', name: '平台管理员', roleType: 'PLATFORM' },
  { code: 'SECURITY_ADMIN', name: '安全管理员', roleType: 'PLATFORM' },
  { code: 'TENANT_ADMIN', name: '租户管理员', roleType: 'PLATFORM' },
  { code: 'DESIGNER', name: '设计师', roleType: 'BUSINESS' },
]

/** 故意喂脏入参（接口异常、登录态未水合）：这些分支就是给这种情况兜底的 */
const loose = (v: unknown) => v as never

describe('角色可见范围', () => {
  it('租户管理员看不到平台管理员', () => {
    const codes = visibleRoles(roles, ['TENANT_ADMIN']).map(r => r.code)
    expect(codes).not.toContain('PLATFORM_ADMIN')
  })

  it('租户管理员仍能看到其余角色（只挡平台专属的那几个，不是把平台级全挡掉）', () => {
    const codes = visibleRoles(roles, ['TENANT_ADMIN']).map(r => r.code)
    expect(codes).toEqual(['AUDIT_ADMIN', 'CATEGORY_ADMIN', 'SECURITY_ADMIN', 'TENANT_ADMIN', 'DESIGNER'])
  })

  it('平台管理员本人登录时不过滤（那些角色正是他要维护的）', () => {
    expect(visibleRoles(roles, ['PLATFORM_ADMIN'])).toHaveLength(roles.length)
  })

  it('同时具备平台管理员与其他角色时也按平台管理员看待', () => {
    expect(visibleRoles(roles, ['PLATFORM_ADMIN', 'TENANT_ADMIN'])).toHaveLength(roles.length)
  })

  it('没有角色 / 角色未恢复时不放行平台管理员（缺省即收紧）', () => {
    expect(visibleRoles(roles, []).map(r => r.code)).not.toContain('PLATFORM_ADMIN')
    expect(visibleRoles(roles, loose(undefined)).map(r => r.code)).not.toContain('PLATFORM_ADMIN')
  })

  it('空清单 / 空项不炸', () => {
    expect(visibleRoles([], ['TENANT_ADMIN'])).toEqual([])
    expect(visibleRoles(loose(undefined), ['TENANT_ADMIN'])).toEqual([])
    expect(visibleRoles(loose([null]), ['TENANT_ADMIN'])).toEqual([])
  })

  it('编码常量是页面唯一引用的口径', () => {
    expect(PLATFORM_ONLY_ROLE_CODES).toContain('PLATFORM_ADMIN')
    expect(isPlatformAdmin(['PLATFORM_ADMIN'])).toBe(true)
    expect(isPlatformAdmin(['TENANT_ADMIN'])).toBe(false)
  })
})
