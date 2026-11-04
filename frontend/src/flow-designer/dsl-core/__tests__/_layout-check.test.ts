// 临时工具：跑优化后的自动排版，打印坐标（用完即删）
import { describe, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { autoLayout } from '../ops'
import type { FlowDsl } from '../types'

const RES = path.resolve(__dirname, '../../../../../src/main/resources/industry-packs/platform-base/v1.0.0/processes')

describe('layout check', () => {
  it('prints optimized layout', () => {
    const raw = JSON.parse(fs.readFileSync(path.join(RES, 'change-request.dsl.json'), 'utf8'))
    const laid = autoLayout(raw.dsl as FlowDsl)
    console.log('LAYOUT_BEGIN')
    Object.entries(laid.layout.nodes).forEach(([id, box]) => {
      const b = box as { x: number; y: number }
      console.log(`${id}|${Math.round(b.x)}|${Math.round(b.y)}`)
    })
    console.log('LAYOUT_END')
  })
})
