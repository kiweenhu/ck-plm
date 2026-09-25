import { describe, expect, it } from 'vitest'
import {
  ASSIGNEE_BUILTIN_VARIABLES,
  BUILTIN_OBJECT_TYPE,
  BUILTIN_VARIABLES,
  BUILTIN_VARIABLE_NAMES,
  BuiltinVariableTiming,
  FIXED_BUILTIN_VARIABLES,
  ValueType,
  builtinVariableOf,
  builtinVariableTypeLabel,
  isBuiltinVariableName,
} from '../index'
import { RUNTIME_VARIABLES } from '../../bpmn-compiler/namespaces'

/**
 * 内置变量清单的"内容正确性"测试。
 *
 * <p>这份清单是给人看的说明书（属性面板的「内置变量」区）＋ 校验器的判据，
 * 所以两头都要钉住：<b>每一项都写全了</b>（漏了说明等于没登记），
 * 且<b>只登记运行期真的有人写的名字</b>（登记了没人写 = 引导用户配出取不到值的流程）。
 */
describe('内置变量清单', () => {
  it('每一项都有变量名、展示名、类型、写入时机与说明', () => {
    for (const v of BUILTIN_VARIABLES) {
      expect(v.name, v.name).toBeTruthy()
      expect(v.display, v.name).toBeTruthy()
      expect(v.help.length, v.name).toBeGreaterThan(10)
      // 面板按纯文本渲染说明：写了 HTML 标签会原样露出来
      expect(v.help, v.name).not.toMatch(/<[a-z/]/i)
      expect([...Object.values(ValueType), BUILTIN_OBJECT_TYPE], v.name).toContain(v.type)
      expect(Object.values(BuiltinVariableTiming)).toContain(v.timing)
    }
  })

  it('类型展示：OBJECT 说成「对象」，其余用原值', () => {
    expect(builtinVariableTypeLabel(BUILTIN_OBJECT_TYPE)).toBe('对象')
    expect(builtinVariableTypeLabel(ValueType.STRING)).toBe('STRING')
  })

  it('固定名不重复，也不含占位符（占位符只在展示名里）', () => {
    const names = BUILTIN_VARIABLES.map((v) => v.name)
    expect(new Set(names).size).toBe(names.length)
    for (const name of names) {
      expect(name).not.toMatch(/[<>\s]/)
    }
    // 按活动拼名的那类：名字是前缀、展示名带占位符（避免被照抄成固定名）
    const perActivity = BUILTIN_VARIABLES.filter((v) => v.perActivity)
    expect(perActivity.length).toBeGreaterThan(0)
    for (const v of perActivity) {
      expect(v.display).toContain('<活动id>')
    }
  })

  it('能认出内置变量：固定名、按活动拼名（前缀）、以及否定的情况', () => {
    expect(isBuiltinVariableName('initiator')).toBe(true)
    expect(isBuiltinVariableName('approved')).toBe(true)
    expect(isBuiltinVariableName('ckplmSetupAssignees_task_p7anuf')).toBe(true)
    expect(isBuiltinVariableName('ckplmSetupAssignees_')).toBe(true)
    expect(isBuiltinVariableName('ecrNo')).toBe(false)
    expect(isBuiltinVariableName('')).toBe(false)
    expect(isBuiltinVariableName(null)).toBe(false)
    expect(isBuiltinVariableName(undefined)).toBe(false)
  })

  it('按活动拼名的变量也能取到定义（面板/提示要显示它的说明）', () => {
    expect(builtinVariableOf('ckplmSetupAssignees_approval_mtkxvu')?.perActivity).toBe(true)
    expect(builtinVariableOf('initiator')?.timing).toBe(BuiltinVariableTiming.START)
    expect(builtinVariableOf('approved')?.timing).toBe(BuiltinVariableTiming.COMPLETE)
    expect(builtinVariableOf('ecrNo')).toBeUndefined()
  })

  it('可选列表里不含"按活动拼名"的那类（它不是可直接照抄的固定名）', () => {
    expect(FIXED_BUILTIN_VARIABLES.every((v) => !v.perActivity)).toBe(true)
    expect(BUILTIN_VARIABLE_NAMES).toEqual(FIXED_BUILTIN_VARIABLES.map((v) => v.name))
    expect(BUILTIN_VARIABLE_NAMES).not.toContain('ckplmSetupAssignees_')
  })

  it('业务对象用"一个集合对象"表达：businessObjectSet 在、退役的三件套不在', () => {
    expect(BUILTIN_VARIABLE_NAMES).toContain('businessObjectSet')
    expect(builtinVariableOf('businessObjectSet')?.type).toBe(BUILTIN_OBJECT_TYPE)
    // 流程与业务对象的关联是集合（一个实例可关联多个对象、各带大版本）：
    // 平铺的 businessObjectType / Oid / Code 表达不了它，已由服务端统一产出 businessObjectSet
    for (const retired of ['businessObjectType', 'businessObjectOid', 'businessObjectCode']) {
      expect(BUILTIN_VARIABLE_NAMES, retired).not.toContain(retired)
      expect(isBuiltinVariableName(retired), retired).toBe(false)
    }
  })

  it('可作审批人取值的只有"装人员标识"的那种（对象 / 布尔类不能选）', () => {
    expect(ASSIGNEE_BUILTIN_VARIABLES.map((v) => v.name)).toEqual(['initiator'])
    for (const wrong of ['businessObjectSet', 'approved', 'lifecycleStatus']) {
      expect(ASSIGNEE_BUILTIN_VARIABLES.map((v) => v.name), wrong).not.toContain(wrong)
    }
  })

  it('与编译层的运行期契约同源：发起人变量名一致', () => {
    // 编译层把「发起人」策略编译成 ${initiator}（RUNTIME_VARIABLES.INITIATOR）——
    // 两处名字不一致时，模板配了发起人却解析不出值
    expect(BUILTIN_VARIABLE_NAMES).toContain(RUNTIME_VARIABLES.INITIATOR)
  })

  it('只登记运行期真的有人写的变量：契约里那几个尚未实现的运行期变量不收', () => {
    // 这几个在 namespaces.ts 里是"待运行期实现"的契约，后端目前没有组件写入它们：
    // 收进清单会让用户配出永远取不到值的流程，所以必须"不在清单里"
    for (const name of [
      RUNTIME_VARIABLES.DEPT_LEADER,
      RUNTIME_VARIABLES.INITIATOR_LEADER,
      RUNTIME_VARIABLES.APPROVERS,
      RUNTIME_VARIABLES.APPROVER,
    ]) {
      expect(BUILTIN_VARIABLE_NAMES, name).not.toContain(name)
    }
  })
})
