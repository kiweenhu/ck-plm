/**
 * 连线标签 —— 画布上那条「这条边是什么意思」。
 *
 * <p>真实反馈：画了「驳回」边，线上什么都不显示。原因有两个，本文件都钉住：
 * <ol>
 *   <li>标签只取 {@code name}（分支名）—— 没填就空；路由（通过/驳回）没被算进去；</li>
 *   <li>标签是否<b>常显</b>取决于 {@code branchEdge}（原先只认"带条件或默认分支"），
 *       路由边被判成普通连线 → 标签默认藏起来。</li>
 * </ol>
 */

import { describe, expect, it } from 'vitest'
import { EdgeKind, EdgeRoute, type FlowEdge } from '@flow-dsl-core'
import { edgeLabelOf } from '../render'

const edge = (extra: Partial<FlowEdge> = {}): FlowEdge => ({
  id: 'e1',
  source: 'appr_1',
  target: 'end_1',
  kind: EdgeKind.NORMAL,
  ...extra,
})

/** 取标签文字（无标签返回空串） */
function labelOf(candidate: FlowEdge): string {
  // 标签图元是「白底矩形 + 文字」，文字在 label 选择器上（底色解决"标签压线"）
  return (edgeLabelOf(candidate)[0]?.attrs?.label?.text as string | undefined) ?? ''
}

describe('连线标签', () => {
  it('填了分支名就显示分支名', () => {
    expect(labelOf(edge({ name: '同意' }))).toBe('同意')
  })

  it('没填分支名时，路由兜底显示「通过」/「驳回」', () => {
    expect(labelOf(edge({ route: EdgeRoute.PASS }))).toBe('通过')
    expect(labelOf(edge({ route: EdgeRoute.REJECT }))).toBe('驳回')
  })

  it('分支名优先于路由（想改叫法时不必改路由）', () => {
    expect(labelOf(edge({ name: '退回修改', route: EdgeRoute.REJECT }))).toBe('退回修改')
  })

  it('默认分支仍显示「默认」', () => {
    expect(labelOf(edge({ kind: EdgeKind.DEFAULT }))).toBe('默认')
  })

  it('顺序连线没有标签（不给画布铺满气泡）', () => {
    expect(labelOf(edge())).toBe('')
  })
})
