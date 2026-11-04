/**
 * 节点防重叠：贴太近时自动让开 —— 让两节点之间的连线**看得见**。
 *
 * <p>背景（2026-10-13 用户实测反馈）：两个节点贴在一起时，它们之间的连线短到只剩一个箭头、
 * 甚至完全被节点框盖住，画布看起来"断了"（截图：设置审批人 ↔ 需求与选型初审 紧贴）。
 * 产品口径：与其让用户去猜线在哪，不如松手时把节点让开一点 —— 至少留一条能看出连线的缝。
 *
 * <p>本文件只钉住**几何**：给定自己与其它节点的矩形，算出应该挪到哪。
 * 调不调用、何时调用由画布层决定（见 graph.ts 的 node:moved）。
 */

import { describe, expect, it } from 'vitest'
import { pushApartFrom } from '../graph'

/** 与 defaultNodeSize 的实际取值无关：这里只测几何，尺寸自定 */
const SIZE = { width: 142, height: 44 }
const box = (x: number, y: number) => ({ x, y, ...SIZE })

describe('节点防重叠 · 让开规则', () => {
  it('上下间隙不足 → 沿垂直方向让开到最小间距（下方的往下让）', () => {
    const self = box(100, 80)
    const above = box(100, 20) // 底边 64；与 self 顶边 80 只差 16
    expect(pushApartFrom(self, [above], 24)).toEqual({ x: 100, y: 88 })
  })

  it('上下间隙足够 → 原地不动（别打扰用户已经摆好的布局）', () => {
    const self = box(100, 200)
    const above = box(100, 20) // 底边 64；间隙 136
    expect(pushApartFrom(self, [above], 24)).toEqual({ x: 100, y: 200 })
  })

  it('完全重叠 → 沿"出去所需位移最小"的方向让开（同等距离时向上）', () => {
    const self = box(100, 100)
    const same = box(100, 100)
    expect(pushApartFrom(self, [same], 24)).toEqual({ x: 100, y: 32 })
  })

  it('左右间隙不足 → 沿水平方向让开（左侧的往左让）', () => {
    const self = box(250, 100)
    const right = box(400, 100) // 左边界 400；与 self 右边界 392 只差 8
    expect(pushApartFrom(self, [right], 24)).toEqual({ x: 234, y: 100 })
  })

  it('斜对角（两个投影都不重叠）→ 不动：那是"错开摆放"，不是贴住', () => {
    const self = box(100, 300)
    const corner = box(300, 100)
    expect(pushApartFrom(self, [corner], 24)).toEqual({ x: 100, y: 300 })
  })

  it('让开的位置又撞上第三个节点时，继续让（迭代到收敛）', () => {
    const self = box(100, 100)
    const same = box(100, 100) // 先让开 → 上移到 y = 32
    const blocker = box(100, 32) // 正好在那儿 → 再往上让到 y = 32 - 44 - 24 = -36
    expect(pushApartFrom(self, [same, blocker], 24)).toEqual({ x: 100, y: -36 })
  })

  it('没有邻居 → 原样返回', () => {
    expect(pushApartFrom(box(10, 20), [], 24)).toEqual({ x: 10, y: 20 })
  })
})
