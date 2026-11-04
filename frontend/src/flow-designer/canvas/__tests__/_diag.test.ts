import { describe, it } from 'vitest'
import { DSL_VERSION, EdgeKind, EdgeRoute, NodeType, type FlowDsl } from '@flow-dsl-core'
import { branchLaneVertices, geometryOf, toGraphData } from '../render'
import { portSidesFor } from '../node-style'

const dsl: FlowDsl = {
  dslVersion: DSL_VERSION,
  meta: { key: 'doc-technical-review', name: '技术文档评审流程' },
  nodes: [
    { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: 'INITIATOR' } as never },
    { id: 'set_assignee_1', type: NodeType.SET_ASSIGNEE, name: '设置评审人', assignee: { strategy: 'INITIATOR' } as never },
    { id: 'service_approving', type: NodeType.SERVICE, name: '进入审核中', serviceRef: 'object.setLifecycleState', params: { state: 'APPROVING' } },
    { id: 'n_review', type: NodeType.COUNTERSIGN_APPROVAL, name: '技术评审（会签）', assignee: { strategy: 'ROLE', roleCodes: ['PM'] } as never },
    { id: 'service_published', type: NodeType.SERVICE, name: '评审通过：发布', serviceRef: 'object.setLifecycleState', params: { state: 'PUBLISHED' } },
    { id: 'service_draft', type: NodeType.SERVICE, name: '评审驳回：退回草稿', serviceRef: 'object.setLifecycleState', params: { state: 'DRAFT' } },
    { id: 'task_resubmit', type: NodeType.TASK, name: '重新提交', assignee: { strategy: 'INITIATOR' } as never },
    { id: 'n_end', type: NodeType.END, name: '结束' },
  ],
  edges: [
    { id: 'e0', source: 'start_1', target: 'set_assignee_1', kind: EdgeKind.NORMAL },
    { id: 'e1', source: 'set_assignee_1', target: 'service_approving', kind: EdgeKind.NORMAL },
    { id: 'e2', source: 'service_approving', target: 'n_review', kind: EdgeKind.NORMAL },
    { id: 'e3', source: 'n_review', target: 'service_published', kind: EdgeKind.NORMAL, route: EdgeRoute.PASS },
    { id: 'e4', source: 'n_review', target: 'service_draft', kind: EdgeKind.NORMAL, route: EdgeRoute.REJECT },
    { id: 'e5', source: 'service_published', target: 'n_end', kind: EdgeKind.NORMAL },
    { id: 'e6', source: 'service_draft', target: 'task_resubmit', kind: EdgeKind.NORMAL },
    { id: 'e7', source: 'task_resubmit', target: 'service_approving', kind: EdgeKind.NORMAL },
  ],
  layout: {
    nodes: {
      start_1: { x: 296, y: -8, width: 30, height: 30 },
      set_assignee_1: { x: 240, y: 66, width: 142, height: 44 },
      service_approving: { x: 240, y: 156, width: 142, height: 44 },
      n_review: { x: 240, y: 246, width: 180, height: 70 },
      service_published: { x: 240, y: 336, width: 142, height: 44 },
      n_end: { x: 296, y: 426, width: 30, height: 30 },
      service_draft: { x: 576, y: 246, width: 142, height: 44 },
      task_resubmit: { x: 540, y: 66, width: 142, height: 44 },
    },
  },
} as FlowDsl

describe('diag', () => {
  it('prints edge routing', () => {
    const data = toGraphData(dsl)
    for (const edge of data.edges) {
      console.log(
        edge.id,
        `${edge.source.cell}(${edge.source.port ?? '-'}) → ${edge.target.cell}(${edge.target.port ?? '-'})`,
        'vertices=' + JSON.stringify(edge.vertices ?? null),
        'router=' + edge.router.name,
      )
    }

    const nReview = dsl.nodes.find((n) => n.id === 'n_review')!
    const draft = dsl.nodes.find((n) => n.id === 'service_draft')!
    const nReviewRect = geometryOf(dsl, nReview)
    const draftRect = geometryOf(dsl, draft)
    const rects = dsl.nodes.map((n) => geometryOf(dsl, n))
    console.log('n_review rect=' + JSON.stringify(nReviewRect), 'sides=' + JSON.stringify(portSidesFor(NodeType.COUNTERSIGN_APPROVAL)))
    console.log('service_draft rect=' + JSON.stringify(draftRect), 'sides=' + JSON.stringify(portSidesFor(NodeType.SERVICE)))
    console.log('direct=' + JSON.stringify(branchLaneVertices(nReviewRect, draftRect, 'right', 'left', rects) ?? null))
  })
})
