import { describe, expect, it } from 'vitest'
import {
  ISSUE_STATUSES,
  availableActions,
  primaryAction,
  requiresComment,
  requiresOwner,
  requiresResolution,
  requiresRootCause,
  requiresVerifyResult,
  targetStatusOf
} from '@/utils/issueFlow'

describe('问题闭环状态机（前端镜像）', () => {
  it('完整闭环：提出 → 处理 → 提交验证 → 验证通过，逐跳可走', () => {
    expect(targetStatusOf('START', 'OPEN')).toBe('IN_PROGRESS')
    expect(targetStatusOf('SUBMIT_VERIFY', 'IN_PROGRESS')).toBe('VERIFYING')
    expect(targetStatusOf('VERIFY_PASS', 'VERIFYING')).toBe('CLOSED')
  })

  it('不许一步关闭：待处理 / 处理中都不给"验证通过"', () => {
    expect(targetStatusOf('VERIFY_PASS', 'OPEN')).toBeNull()
    expect(targetStatusOf('VERIFY_PASS', 'IN_PROGRESS')).toBeNull()
    expect(availableActions('OPEN')).not.toContain('VERIFY_PASS')
  })

  it('验证退回：待验证 → 处理中；处理中不可重复提交', () => {
    expect(targetStatusOf('VERIFY_FAIL', 'VERIFYING')).toBe('IN_PROGRESS')
    expect(targetStatusOf('SUBMIT_VERIFY', 'VERIFYING')).toBeNull()
  })

  it('取消：三种进行中状态都可；已取消是终态，不可再动、不可重开', () => {
    for (const from of ['OPEN', 'IN_PROGRESS', 'VERIFYING']) {
      expect(targetStatusOf('CANCEL', from)).toBe('CANCELLED')
    }
    expect(availableActions('CANCELLED')).toEqual([])
    expect(targetStatusOf('REOPEN', 'CANCELLED')).toBeNull()
  })

  it('重开：仅已关闭可重开', () => {
    expect(targetStatusOf('REOPEN', 'CLOSED')).toBe('OPEN')
    expect(targetStatusOf('REOPEN', 'IN_PROGRESS')).toBeNull()
  })

  it('必填约束：退回 / 取消 / 重开要说明，验证通过要结论', () => {
    expect(requiresComment('VERIFY_FAIL')).toBe(true)
    expect(requiresComment('CANCEL')).toBe(true)
    expect(requiresComment('REOPEN')).toBe(true)
    expect(requiresComment('START')).toBe(false)
    expect(requiresVerifyResult('VERIFY_PASS')).toBe(true)
    expect(requiresVerifyResult('VERIFY_FAIL')).toBe(false)
  })

  it('提交验证：根因分析与处理措施都必填', () => {
    expect(requiresRootCause('SUBMIT_VERIFY')).toBe(true)
    expect(requiresResolution('SUBMIT_VERIFY')).toBe(true)
    expect(requiresRootCause('START')).toBe(false)
    expect(requiresRootCause('VERIFY_PASS')).toBe(false)
    expect(requiresResolution('VERIFY_PASS')).toBe(false)
  })

  it('表格快捷推进：每态最多一个主入口', () => {
    expect(primaryAction('OPEN')).toBe('START')
    expect(primaryAction('IN_PROGRESS')).toBe('SUBMIT_VERIFY')
    expect(primaryAction('VERIFYING')).toBe('VERIFY_PASS')
    expect(primaryAction('CLOSED')).toBeNull()
    expect(primaryAction('CANCELLED')).toBeNull()
  })

  it('责任人约束：仅开始处理 / 提交验证限责任人，验证类与取消重开不限', () => {
    expect(requiresOwner('START')).toBe(true)
    expect(requiresOwner('SUBMIT_VERIFY')).toBe(true)
    expect(requiresOwner('VERIFY_PASS')).toBe(false)
    expect(requiresOwner('VERIFY_FAIL')).toBe(false)
    expect(requiresOwner('CANCEL')).toBe(false)
    expect(requiresOwner('REOPEN')).toBe(false)
  })

  it('状态集合与后端一致（五态）', () => {
    expect(ISSUE_STATUSES).toEqual(['OPEN', 'IN_PROGRESS', 'VERIFYING', 'CLOSED', 'CANCELLED'])
  })
})
