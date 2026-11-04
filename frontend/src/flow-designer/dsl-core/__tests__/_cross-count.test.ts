// 临时工具：统计某份 layout 下的向前边交叉数，并比较几种汇聚点摆放（用完即删）
import { describe, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import type { FlowDsl } from '../types'

const RES = path.resolve(__dirname, '../../../../../src/main/resources/industry-packs/platform-base/v1.0.0/processes')

type Pt = { x: number; y: number }

function cross(a1: Pt, a2: Pt, b1: Pt, b2: Pt): boolean {
  const s = (p: Pt, q: Pt, r: Pt) => (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
  const d1 = s(b1, b2, a1)
  const d2 = s(b1, b2, a2)
  const d3 = s(a1, a2, b1)
  const d4 = s(a1, a2, b2)
  return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

function countCrossings(dsl: FlowDsl): { total: number; pairs: string[] } {
  const center = new Map<string, Pt>()
  Object.entries(dsl.layout.nodes).forEach(([id, box]) => {
    const b = box as { x: number; y: number; width?: number; height?: number }
    center.set(id, { x: b.x + (b.width ?? 140) / 2, y: b.y + (b.height ?? 44) / 2 })
  })
  const y = (id: string) => center.get(id)?.y ?? 0
  const forward = dsl.edges.filter((e) => y(e.target) > y(e.source))
  const pairs: string[] = []
  for (let i = 0; i < forward.length; i++) {
    for (let j = i + 1; j < forward.length; j++) {
      const a = forward[i]
      const b = forward[j]
      if (a.source === b.source || a.target === b.target || a.source === b.target || a.target === b.source) continue
      const a1 = center.get(a.source)
      const a2 = center.get(a.target)
      const b1 = center.get(b.source)
      const b2 = center.get(b.target)
      if (a1 && a2 && b1 && b2 && cross(a1, a2, b1, b2)) {
        pairs.push(`${a.source}->${a.target} × ${b.source}->${b.target}`)
      }
    }
  }
  return { total: pairs.length, pairs }
}

function horizontalTravel(dsl: FlowDsl): number {
  const center = new Map<string, Pt>()
  Object.entries(dsl.layout.nodes).forEach(([id, box]) => {
    const b = box as { x: number; y: number; width?: number; height?: number }
    center.set(id, { x: b.x + (b.width ?? 140) / 2, y: b.y + (b.height ?? 44) / 2 })
  })
  const y = (id: string) => center.get(id)?.y ?? 0
  return dsl.edges
    .filter((e) => y(e.target) > y(e.source))
    .reduce((sum, e) => sum + Math.abs((center.get(e.target)?.x ?? 0) - (center.get(e.source)?.x ?? 0)), 0)
}

describe('cross count', () => {
  it('compares arrangements', () => {
    const raw = JSON.parse(fs.readFileSync(path.join(RES, 'change-request.dsl.json'), 'utf8'))
    const dsl = JSON.parse(JSON.stringify(raw.dsl)) as FlowDsl

    const swap = (a: string, b: string) => {
      const na = dsl.layout.nodes[a] as { x: number }
      const nb = dsl.layout.nodes[b] as { x: number }
      const tx = na.x
      na.x = nb.x
      nb.x = tx
    }

    const report = (label: string) => {
      const c = countCrossings(dsl)
      console.log(`${label}|crossings=${c.total}|travel=${Math.round(horizontalTravel(dsl))}|${c.pairs.join(' ; ')}`)
    }

    report('A_current')

    // 汇聚点横向位置的网格搜索：交叉数优先，其次横向总位移
    const baseA = (dsl.layout.nodes['set_accepted'] as { x: number }).x
    const baseR = (dsl.layout.nodes['set_rejected'] as { x: number }).x
    for (const ax of [150, 209, 280, 350]) {
      for (const rx of [400, 449, 520, 620, 720]) {
        if (ax === rx) continue
        ;(dsl.layout.nodes['set_accepted'] as { x: number }).x = ax
        ;(dsl.layout.nodes['end_accepted'] as { x: number }).x = ax + 56
        ;(dsl.layout.nodes['set_rejected'] as { x: number }).x = rx
        ;(dsl.layout.nodes['end_rejected'] as { x: number }).x = rx + 56
        const c = countCrossings(dsl)
        console.log(`GRID|ax=${ax}|rx=${rx}|crossings=${c.total}|travel=${Math.round(horizontalTravel(dsl))}`)
      }
    }
    ;(dsl.layout.nodes['set_accepted'] as { x: number }).x = baseA
    ;(dsl.layout.nodes['end_accepted'] as { x: number }).x = baseA + 56
    ;(dsl.layout.nodes['set_rejected'] as { x: number }).x = baseR
    ;(dsl.layout.nodes['end_rejected'] as { x: number }).x = baseR + 56

    swap('set_accepted', 'set_rejected')
    swap('end_accepted', 'end_rejected')
    report('B_swap_sinks')

    swap('set_accepted', 'set_rejected')
    swap('end_accepted', 'end_rejected')
    report('A_again')
  })
})
