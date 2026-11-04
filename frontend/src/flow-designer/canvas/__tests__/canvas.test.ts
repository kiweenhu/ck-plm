import { describe, expect, it } from 'vitest'
import {
  ApprovalMode,
  AssigneeStrategy,
  DSL_VERSION,
  EdgeKind,
  EdgeRoute,
  NodeType,
  createEmptyDsl,
  parseFlowDsl,
  setEdgeWaypoints,
  updateEdge,
  defaultNodeSize,
  validateDsl,
  type FlowDsl,
} from '@flow-dsl-core'
import {
  BadgeState,
  CANVAS_STYLE,
  NODE_STYLE,
  PORT_SLOT_MIN_SPACING,
  facingPortSides,
  isPreciseAnchorHit,
  pickPortSides,
  portIdOf,
  portSidesFor,
  portSlotCount,
  portSlotFraction,
  rankPortSides,
  sideOfPortId,
  type Rect,
} from '../node-style'
import { BRANCH_BAND_STAGGER, geometryOf, needsReanchor, toGraphData } from '../render'
import { DslHistory } from '../undo-stack'

/** 极简 DSL 构造器 */
function baseDsl(partial: Partial<FlowDsl> = {}): FlowDsl {
  return {
    dslVersion: DSL_VERSION,
    meta: { key: 'canvas_flow', name: '画布测试' },
    nodes: [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    edges: [{ id: 'f1', source: 'start_1', target: 'end_1', kind: EdgeKind.NORMAL }],
    layout: {
      nodes: {
        start_1: { x: 240, y: 40 },
        end_1: { x: 240, y: 220 },
      },
    },
    ...partial,
  }
}

/**
 * 一个审批节点向下分出两条出边的场景：「通过」→ 办理、「驳回」→ 设置状态。
 *
 * <p>与用户反馈的那张图同形：审完既要往前走、也可能被驳回，两条线从同一个节点出去 ——
 * 锚点不够时它们就会叠在一起。
 */
function twoOutEdgesDsl(): FlowDsl {
  return baseDsl({
    nodes: [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      {
        id: 'a1',
        type: NodeType.APPROVAL,
        name: '审批',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
        approvalMode: ApprovalMode.SINGLE,
      },
      {
        id: 't1',
        type: NodeType.TASK,
        name: '办理',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      },
      { id: 's1', type: NodeType.SERVICE, name: '设置状态', serviceRef: 'object.setLifecycleState' },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    edges: [
      { id: 'f1', source: 'start_1', target: 'a1', kind: EdgeKind.NORMAL },
      { id: 'f2', source: 'a1', target: 't1', kind: EdgeKind.NORMAL, route: EdgeRoute.PASS },
      { id: 'f3', source: 'a1', target: 's1', kind: EdgeKind.NORMAL, route: EdgeRoute.REJECT },
      { id: 'f4', source: 't1', target: 'end_1', kind: EdgeKind.NORMAL },
      { id: 'f5', source: 's1', target: 'end_1', kind: EdgeKind.NORMAL },
    ],
    layout: {
      nodes: {
        start_1: { x: 240, y: 20 },
        a1: { x: 180, y: 120 },
        t1: { x: 80, y: 260 },
        s1: { x: 300, y: 260 },
        end_1: { x: 240, y: 400 },
      },
    },
  })
}

/** 给某条连线手动指定锚点（等价于"拖过端点"或"在面板里选过"） */
function withAnchor(dsl: FlowDsl, edgeId: string, anchor: { source?: string; target?: string }): FlowDsl {
  return { ...dsl, edges: dsl.edges.map((edge) => (edge.id === edgeId ? { ...edge, anchor } : edge)) }
}

describe('节点外观规格', () => {
  it('11 类节点都有视觉规格', () => {
    for (const type of Object.values(NodeType)) {
      expect(NODE_STYLE[type], `缺少 ${type} 的外观规格`).toBeDefined()
    }
  })

  it('尺寸只认 defaultNodeSize：layout 里的历史宽高不再生效', () => {
    // 存量模板的 layout 里残留着创建时的尺寸（180×64）。若渲染以它为准，
    // 调整基准尺寸对老流程永远不生效 —— 这正是「活动框改不小」的根因。
    const dsl = baseDsl({
      nodes: [
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '审批',
          assignee: { strategy: AssigneeStrategy.INITIATOR },
          approvalMode: ApprovalMode.SINGLE,
        },
      ],
      layout: { nodes: { a1: { x: 10, y: 20, width: 180, height: 64 } } },
    })
    expect(geometryOf(dsl, dsl.nodes[0])).toEqual({
      x: 10,
      y: 20,
      ...defaultNodeSize(NodeType.APPROVAL),
    })
    expect(geometryOf(dsl, dsl.nodes[0]).width).toBeLessThan(180)
  })

  it('横向相邻的节点走左右端口（而不是只能绕上下）', () => {
    const sizes = { width: 190, height: 68 }
    const sides = pickPortSides(
      { x: 0, y: 0, ...sizes },
      { x: 300, y: 0, ...sizes },
    )
    expect(sides).toEqual({ source: 'right', target: 'left' })
  })

  it('纵向相邻的节点走上下端口', () => {
    const sizes = { width: 190, height: 68 }
    expect(pickPortSides({ x: 0, y: 0, ...sizes }, { x: 0, y: 200, ...sizes })).toEqual({
      source: 'bottom',
      target: 'top',
    })
    expect(pickPortSides({ x: 0, y: 200, ...sizes }, { x: 0, y: 0, ...sizes })).toEqual({
      source: 'top',
      target: 'bottom',
    })
  })

  it('方位判定与节点尺寸无关：宽节点不会把纵向邻居误判成横向', () => {
    // 一个 190×68 的任务与正下方 100px 的邻居：横向位移 0，纵向明显
    const wide: Rect = { x: 0, y: 0, width: 190, height: 68 }
    const below: Rect = { x: 0, y: 168, width: 190, height: 68 }
    expect(pickPortSides(wide, below).source).toBe('bottom')
    // 窄高节点同理不会把左右邻居误判成纵向
    const narrow: Rect = { x: 0, y: 0, width: 40, height: 40 }
    const right: Rect = { x: 120, y: 0, width: 40, height: 40 }
    expect(pickPortSides(narrow, right).source).toBe('right')
  })

  it('起始节点不给 top、结束节点不给 bottom（那两个方位永远用不上）', () => {
    expect(portSidesFor(NodeType.START)).toEqual(['right', 'bottom', 'left'])
    expect(portSidesFor(NodeType.END)).toEqual(['top', 'right', 'left'])
    expect(portSidesFor(NodeType.APPROVAL)).toEqual(['top', 'right', 'bottom', 'left'])
  })

  it('排序只返回该节点真实开放的方位（起始节点永不出现 top）', () => {
    const start: Rect = { x: 0, y: 0, width: 40, height: 40 }
    const above: Rect = { x: 0, y: -200, width: 190, height: 68 }
    const ranked = rankPortSides(start, above, portSidesFor(NodeType.START))
    // 目标在右上方：起始节点没有 top，故最朝向它的是 right，其次 left，最后才是背对目标的 bottom
    expect(ranked).toEqual(['right', 'left', 'bottom'])
    expect(ranked).not.toContain('top')
  })

  it('多条分支会分别占用不同方位（网关扇出）', () => {
    // 分支朝向相同（都在下方）时，第一主方位是 bottom，其余依次退让
    const gw: Rect = { x: 0, y: 0, width: 46, height: 46 }
    const below: Rect = { x: -140, y: 120, width: 190, height: 68 }
    const ranked = rankPortSides(gw, below, portSidesFor(NodeType.EXCLUSIVE_GATEWAY))
    expect(ranked[0]).toBe('bottom')
    expect(new Set(ranked).size).toBe(4)
  })

  it('朝向目标的方位够用时，绝不挂到背对目标的那一侧', () => {
    // 终点明显在右方（略偏下）：left / top 都背对目标，不该出现在候选里
    const gw: Rect = { x: 0, y: 0, width: 46, height: 46 }
    const toTheRight: Rect = { x: 300, y: 60, width: 190, height: 68 }
    const facing = facingPortSides(gw, toTheRight, portSidesFor(NodeType.EXCLUSIVE_GATEWAY))
    expect(facing[0]).toBe('right')
    expect(facing).not.toContain('left')
    expect(facing).not.toContain('top')
  })
})

describe('DSL → 图元映射', () => {
  it('节点位置取 layout，尺寸取类型基准', () => {
    const data = toGraphData(baseDsl())
    const start = data.nodes.find((n) => n.id === 'start_1')!
    expect(start.x).toBe(240)
    expect(start.y).toBe(40)
    expect(start.width).toBe(defaultNodeSize(NodeType.START).width)
    expect(data.nodes).toHaveLength(2)
    expect(data.edges).toHaveLength(1)
  })

  it('圆形节点用显式 cx/cy/r，菱形用绝对坐标点串（不依赖 X6 特殊属性）', () => {
    const dsl = baseDsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '分支' },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      layout: { nodes: { start_1: { x: 0, y: 0 }, gw_1: { x: 0, y: 100 }, end_1: { x: 0, y: 200 } } },
    })
    const data = toGraphData(dsl)
    const start = data.nodes.find((n) => n.id === 'start_1')!
    const startHalf = defaultNodeSize(NodeType.START).width / 2
    expect(start.attrs.body.cx).toBe(startHalf)
    expect(start.attrs.body.r).toBe(startHalf - 1)
    expect(start.attrs.body.points).toBeUndefined()

    const gateway = data.nodes.find((n) => n.id === 'gw_1')!
    const side = defaultNodeSize(NodeType.EXCLUSIVE_GATEWAY).width
    expect(gateway.attrs.body.points).toBe(
      `0,${side / 2} ${side / 2},0 ${side},${side / 2} ${side / 2},${side}`,
    )
    expect(gateway.attrs.body.refPoints).toBeUndefined()
  })

  it('回边（驳回画回上游）给一条外车道：竖线绕开所有节点，不再穿框', () => {
    // 与真实流程同形：主线一路往下（审批 → 办理 → 设置状态），最后的活动被驳回回最上面的审批。
    // 没有车道时，这条线的竖段会笔直穿过中间的活动框 —— 用户看到的就是这个「线穿框」。
    const dsl = baseDsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '审批',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        {
          id: 't1',
          type: NodeType.TASK,
          name: '办理',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
        },
        { id: 's1', type: NodeType.SERVICE, name: '设置状态', serviceRef: 'object.setLifecycleState' },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'f1', source: 'start_1', target: 'a1', kind: EdgeKind.NORMAL },
        { id: 'f2', source: 'a1', target: 't1', kind: EdgeKind.NORMAL, route: EdgeRoute.PASS },
        { id: 'f3', source: 't1', target: 's1', kind: EdgeKind.NORMAL },
        { id: 'f4', source: 's1', target: 'end_1', kind: EdgeKind.NORMAL },
        // 回边：最下游的活动驳回到最上游的审批
        { id: 'f5', source: 's1', target: 'a1', kind: EdgeKind.NORMAL, route: EdgeRoute.REJECT },
      ],
      layout: {
        nodes: {
          start_1: { x: 360, y: 20 },
          a1: { x: 300, y: 120 },
          t1: { x: 300, y: 240 },
          s1: { x: 300, y: 360 },
          end_1: { x: 360, y: 520 },
        },
      },
    })
    const data = toGraphData(dsl)
    const back = data.edges.find((e) => e.id === 'f5')!
    const rects = dsl.nodes.map((node) => geometryOf(dsl, node))

    // 两段走线：出源 → 贴车道竖到目标那一层 → **横着进目标侧边**
    // （不再绕到目标上方再折下来：车道本来就贴着外缘，横进来的那一段压不到任何节点）
    expect(back.vertices).toHaveLength(2)
    const [out, turn] = back.vertices!
    // 竖段所在的那个 x 必须在所有节点之外 —— 否则这条竖线就会穿过谁（本用例的存在理由）
    for (const r of rects) {
      const outside = out.x < r.x || out.x > r.x + r.width
      expect(outside, `车道 x=${out.x} 落在节点 ${r.x}..${r.x + r.width} 之内`).toBe(true)
    }
    // 每个走线点都不落在任何节点框内
    for (const point of back.vertices!) {
      const inside = rects.some(
        (r) => point.x > r.x && point.x < r.x + r.width && point.y > r.y && point.y < r.y + r.height,
      )
      expect(inside, `走线点 ${point.x},${point.y} 落在节点框内`).toBe(false)
    }
    // 两段都轴对齐：任何路由算法都只会照走，不会再自己改道
    expect(out.x).toBe(turn.x)
    // 进出方位：源从外侧出、目标从同一侧进（车道那一侧的外缘是空的）
    expect(back.source.port).toBe('left')
    expect(back.target.port).toBe('left')

    // 顺流向下、不需要绕路的连线不该被塞走线点：路由仍有自由度
    expect(data.edges.find((e) => e.id === 'f2')!.vertices).toBeUndefined()
  })

  it('车道那一侧的入场横线被别的节点挡住时，退回从顶部进（不穿框）', () => {
    // 目标右侧还有别的节点：若仍从车道横着进目标右侧，这段横线会穿过中间那个节点 ——
    // 此时必须改回"绕到目标上方、从顶部落"（层间空带里没有节点）
    const dsl = baseDsl({
      nodes: [
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '上游审批',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        {
          id: 't1',
          type: NodeType.TASK,
          name: '中间节点',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
        },
        { id: 's1', type: NodeType.SERVICE, name: '设置状态', serviceRef: 'object.setLifecycleState' },
      ],
      edges: [
        { id: 'e1', source: 's1', target: 'a1', kind: EdgeKind.NORMAL, route: EdgeRoute.REJECT },
      ],
      layout: {
        nodes: {
          a1: { x: 800, y: 300 },
          t1: { x: 1000, y: 300 },
          s1: { x: 500, y: 620 },
        },
      },
    })
    const edge = toGraphData(dsl).edges[0]
    const rects = dsl.nodes.map((node) => geometryOf(dsl, node))
    // 车道在外侧（x 超过所有节点），从顶部进 = 三段折线
    expect(edge.vertices).toHaveLength(3)
    expect(edge.target.port).toMatch(/^top/)
    const [out] = edge.vertices!
    for (const r of rects) {
      expect(out.x < r.x || out.x > r.x + r.width).toBe(true)
    }
  })

  it('锚点背对源：先竖到目标外侧、再横进锚点，不贴着目标边沿绕一圈', () => {
    // 用户截图那处：需求与选型初审（左上）驳回 → 设置对象状态（右下），
    // 而目标的锚点钉在"下边沿" —— 线必须从下面进。
    // 原做法先横到目标左侧再竖下去，第一段几乎贴着目标的顶边走（看起来像"绕着节点转"）；
    // 正确走法是**离开源就竖下去**，在目标下方横移到锚点正下方，再抬进锚点。
    const dsl = baseDsl({
      nodes: [
        {
          id: 'd1',
          type: NodeType.APPROVAL,
          name: '需求与选型初审',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        { id: 's1', type: NodeType.SERVICE, name: '设置对象状态', serviceRef: 'object.setLifecycleState' },
      ],
      edges: [
        {
          id: 'e1',
          source: 'd1',
          target: 's1',
          kind: EdgeKind.NORMAL,
          route: EdgeRoute.REJECT,
          anchor: { source: 'right', target: 'bottom' },
        },
      ],
      layout: { nodes: { d1: { x: 240, y: 252 }, s1: { x: 576, y: 284 } } },
    })
    const edge = toGraphData(dsl).edges[0]
    // 出源（382,274）→ 竖到目标下方空带（402,348）→ 横到锚点正下方（647,348）→ 抬进下边沿
    expect(edge.vertices).toEqual([
      { x: 402, y: 274 },
      { x: 402, y: 348 },
      { x: 647, y: 348 },
    ])
    expect(edge.target.port).toBe('bottom')
  })

  it('两个节点挨得近时，侧出短横段不越进目标框（不再退回 manhattan 绕小台阶）', () => {
    // 源右侧 382、目标左侧 398：横缝只有 16px，比默认短横段 20px 还窄。
    // 旧走法先横到 402（已经进了目标框），自检不过 → 交回 manhattan → 在缝里绕出台阶；
    // 正确做法是把竖段放在两框之间（396），一步下去再横 2px 进锚点。
    const dsl = baseDsl({
      nodes: [
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '技术评审（会签）',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        { id: 's1', type: NodeType.SERVICE, name: '评审驳回：退回草稿', serviceRef: 'object.setLifecycleState' },
      ],
      edges: [
        {
          id: 'e1',
          source: 'a1',
          target: 's1',
          kind: EdgeKind.NORMAL,
          route: EdgeRoute.REJECT,
          anchor: { source: 'right', target: 'left' },
        },
      ],
      layout: { nodes: { a1: { x: 240, y: 250 }, s1: { x: 398, y: 336 } } },
    })
    const edge = toGraphData(dsl).edges[0]
    expect(edge.vertices).toEqual([
      { x: 396, y: 272 },
      { x: 396, y: 358 },
    ])
  })

  it('层间空带被压窄时，两条分支的水平段仍然错开（不会叠成一条线）', () => {
    // 层距被拖近到 30px：固定留白 10 + 错开 12 会同时被夹掉，两条分支曾经叠在同一 y 上，
    // 看上去像一条线穿过去、标签也挤在一起。留白/错开量按空带高度收，保证两者分得开。
    const dsl = baseDsl({
      nodes: [
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '技术评审（会签）',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        { id: 's1', type: NodeType.SERVICE, name: '评审通过：发布', serviceRef: 'object.setLifecycleState' },
        { id: 's2', type: NodeType.SERVICE, name: '评审驳回：退回草稿', serviceRef: 'object.setLifecycleState' },
      ],
      edges: [
        { id: 'f1', source: 'a1', target: 's1', kind: EdgeKind.NORMAL, name: '通过', route: EdgeRoute.PASS },
        { id: 'f2', source: 'a1', target: 's2', kind: EdgeKind.NORMAL, name: '驳回', route: EdgeRoute.REJECT },
      ],
      layout: {
        nodes: {
          a1: { x: 240, y: 250 },
          s1: { x: 100, y: 324 },
          s2: { x: 400, y: 324 },
        },
      },
    })
    const data = toGraphData(dsl)
    const bands = ['f1', 'f2'].map((id) => {
      const edge = data.edges.find((e) => e.id === id)!
      expect(edge.vertices?.length).toBeGreaterThan(0)
      return edge.vertices![0].y
    })
    expect(bands[0]).not.toBe(bands[1])
    expect(Math.abs(bands[0] - bands[1])).toBeGreaterThanOrEqual(4)
    // 两条水平段都还在层间空带里，谁也没压到节点
    for (const y of bands) {
      expect(y).toBeGreaterThan(294)
      expect(y).toBeLessThan(324)
    }
  })

  it('两端锚点差不到 2px 时按直线画：不在中间插一段亚像素横移', () => {
    // 同一列的两端：源用 top-2（四等分点 611.5），目标锚点在中点（611）——
    // 差 0.5px 属于取整噪声，照直画即可；交给路由算法会插一段 0.5px 的横移，放大后是个折
    const dsl = baseDsl({
      nodes: [
        { id: 's1', type: NodeType.SERVICE, name: '设置对象状态', serviceRef: 'object.setLifecycleState' },
        {
          id: 't1',
          type: NodeType.TASK,
          name: '重新提交申请',
          assignee: { strategy: AssigneeStrategy.INITIATOR },
        },
      ],
      edges: [
        {
          id: 'e1',
          source: 's1',
          target: 't1',
          kind: EdgeKind.NORMAL,
          anchor: { source: 'top-2' },
        },
      ],
      layout: { nodes: { s1: { x: 576, y: 284 }, t1: { x: 540, y: 66 } } },
    })
    const edge = toGraphData(dsl).edges[0]
    expect(edge.vertices).toBeUndefined()
    expect(edge.router).toEqual({ name: 'normal' })
  })

  it('分支线拐角落进层间空带、左右错开，且不穿任何节点（红框反馈的 1、2 处）', () => {
    const base = baseDsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'gw_split', type: NodeType.PARALLEL_GATEWAY, name: '并行' },
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '审核',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        {
          id: 'a2',
          type: NodeType.APPROVAL,
          name: '审定',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['QA'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        { id: 'gw_merge', type: NodeType.PARALLEL_GATEWAY, name: '汇聚' },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'f0', source: 'start_1', target: 'gw_split', kind: EdgeKind.NORMAL },
        { id: 'f1', source: 'gw_split', target: 'a1', kind: EdgeKind.NORMAL },
        { id: 'f2', source: 'gw_split', target: 'a2', kind: EdgeKind.NORMAL },
        { id: 'f3', source: 'a1', target: 'gw_merge', kind: EdgeKind.NORMAL },
        { id: 'f4', source: 'a2', target: 'gw_merge', kind: EdgeKind.NORMAL },
        { id: 'f5', source: 'gw_merge', target: 'end_1', kind: EdgeKind.NORMAL },
      ],
      layout: {
        nodes: {
          start_1: { x: 385, y: 20 },
          gw_split: { x: 381, y: 120 },
          a1: { x: 470, y: 240 },
          a2: { x: 230, y: 240 },
          gw_merge: { x: 381, y: 360 },
          end_1: { x: 385, y: 480 },
        },
      },
    })
    // 与用户流程同形：网关扇出的两端锚点被**手动钉在左右**（他们的流程正是这样钉的）
    const dsl = withAnchor(withAnchor(base, 'f1', { source: 'right' }), 'f2', { source: 'left' })
    const data = toGraphData(dsl)
    const rects = dsl.nodes.map((node) => geometryOf(dsl, node))
    const out1 = data.edges.find((e) => e.id === 'f1')!.vertices!
    const out2 = data.edges.find((e) => e.id === 'f2')!.vertices!
    expect(out1).toHaveLength(3)
    expect(out2).toHaveLength(3)

    // 1) 拐角离节点边有"贴边段"：竖段那个 x 在网关外侧至少 BRANCH_STUB 远
    // 2) 两条分支的水平段不在同一高度（错开，才不会被看成一条线）
    expect(out1[1].y).not.toBe(out2[1].y)
    expect(Math.abs(out1[1].y - out2[1].y)).toBe(BRANCH_BAND_STAGGER * 2)
    // 3) 入线对准目标锚点（顶边中点 → x 就是目标中心）
    const a1Rect = rects.find((r) => r.x === 470)!
    expect(out1[2].x).toBe(a1Rect.x + a1Rect.width / 2)

    // 4) 所有折线段都不穿过任何节点框
    for (const edge of data.edges) {
      const v = edge.vertices
      if (!v) continue
      const chain = [edge.source, ...v, edge.target]
      for (let i = 0; i < chain.length - 1; i += 1) {
        for (const r of rects) {
          const a = chain[i] as { x: number; y: number }
          const b = chain[i + 1] as { x: number; y: number }
          if (a.x === b.x && a.y === b.y) continue
          const x1 = Math.min(a.x, b.x)
          const x2 = Math.max(a.x, b.x)
          const y1 = Math.min(a.y, b.y)
          const y2 = Math.max(a.y, b.y)
          const crossed =
            x1 < r.x + r.width - 2 && x2 > r.x + 2 && y1 < r.y + r.height - 2 && y2 > r.y + 2
          expect(crossed, `${edge.id} 的线段 ${JSON.stringify(a)}→${JSON.stringify(b)} 压到了节点`).toBe(false)
        }
      }
    }
  })

  it('正上方竖线占住顶边时，侧向来边改走侧边（不与竖线交叉/并行）', () => {
    // 用户反馈的「画线打结」：与 doc-technical-review 同形 ——
    // t1 正上方直落进 t2（竖线，占 top 主锚点）；t3 在右上方、从侧面回到 t2。
    // 旧实现把 t3 也排在 top 的次锚点上：水平段在层间空带里横穿那条竖线，
    // 随后又贴着它落进同一条顶边，两条线看起来像一条。
    const dsl = baseDsl({
      nodes: [
        { id: 't1', type: NodeType.TASK, name: '设置评审人', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 't2', type: NodeType.TASK, name: '进入审核中', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 't3', type: NodeType.TASK, name: '重新提交', assignee: { strategy: AssigneeStrategy.INITIATOR } },
      ],
      edges: [
        { id: 'f1', source: 't1', target: 't2', kind: EdgeKind.NORMAL },
        { id: 'f2', source: 't3', target: 't2', kind: EdgeKind.NORMAL },
      ],
      layout: {
        nodes: {
          t1: { x: 240, y: 66 },
          t2: { x: 240, y: 156 },
          t3: { x: 540, y: 66 },
        },
      },
    })
    const data = toGraphData(dsl)
    const t2Rect = geometryOf(dsl, dsl.nodes.find((n) => n.id === 't2')!)
    const vertical = data.edges.find((e) => e.id === 'f1')!
    const lateral = data.edges.find((e) => e.id === 'f2')!

    // 1) 正上方那条保持顶边主锚点、直落（无折点）
    expect(vertical.target.port).toBe('top')
    expect(vertical.vertices).toBeUndefined()

    // 2) 侧向来边改走右侧锚点，而不是 top 的次锚点
    expect(lateral.target.port).toBe('right')

    // 3) 它的折线整段都在目标右侧之外 —— 不会横穿正上方那条竖线
    expect(lateral.vertices).toBeDefined()
    const targetRight = t2Rect.x + t2Rect.width
    for (const point of lateral.vertices!) {
      expect(point.x).toBeGreaterThanOrEqual(targetRight)
    }
    // 入线高度对准右侧锚点（竖直方向的中间）
    expect(lateral.vertices!.at(-1)!.y).toBe(t2Rect.y + t2Rect.height / 2)
  })

  it('网关的出边与入边不重叠：同一节点上的连线各占一个锚点', () => {
    // 用户反馈的原始问题：接上条件/并行/包容分支后，网关上的**出边与入边叠在一起**。
    // 成因：网关 38×38，每个方位只有 1 个锚点（38 / 4 < 锚点间距下限 34），而入边侧旧实现
    // 只看"最朝向的方位"、不看它是否已被占用 —— 第 2 条边抢不到就复用主锚点，两条线完全重合。
    const dsl = baseDsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'gw_split', type: NodeType.PARALLEL_GATEWAY, name: '并行' },
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '审核',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        {
          id: 'a2',
          type: NodeType.APPROVAL,
          name: '审定',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['QA'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        { id: 'gw_merge', type: NodeType.PARALLEL_GATEWAY, name: '汇聚' },
        { id: 't1', type: NodeType.TASK, name: '会签审批', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'f1', source: 'start_1', target: 'gw_split', kind: EdgeKind.NORMAL },
        { id: 'f2', source: 'gw_split', target: 'a1', kind: EdgeKind.NORMAL },
        { id: 'f3', source: 'gw_split', target: 'a2', kind: EdgeKind.NORMAL },
        { id: 'f4', source: 'a1', target: 'gw_merge', kind: EdgeKind.NORMAL },
        { id: 'f5', source: 'a2', target: 'gw_merge', kind: EdgeKind.NORMAL },
        { id: 'f6', source: 'gw_merge', target: 't1', kind: EdgeKind.NORMAL },
        { id: 'f7', source: 't1', target: 'end_1', kind: EdgeKind.NORMAL },
      ],
      layout: {
        nodes: {
          start_1: { x: 360, y: 20 },
          gw_split: { x: 381, y: 120 },
          a1: { x: 230, y: 240 },
          a2: { x: 470, y: 240 },
          gw_merge: { x: 381, y: 360 },
          t1: { x: 329, y: 480 },
          end_1: { x: 385, y: 600 },
        },
      },
    })
    const data = toGraphData(dsl)

    // 逐节点检查：连到同一节点的每一条线，落在它上面的锚点必须互不相同
    const attach = new Map<string, Map<string, string>>()
    for (const edge of data.edges) {
      for (const end of ['source', 'target'] as const) {
        const ref = edge[end]
        if (!ref.port) continue
        const byEdge = attach.get(ref.cell) ?? new Map<string, string>()
        byEdge.set(edge.id, ref.port)
        attach.set(ref.cell, byEdge)
      }
    }
    for (const [nodeId, byEdge] of attach) {
      const ports = [...byEdge.values()]
      expect(new Set(ports).size, `${nodeId} 上的锚点重复：${ports.join(' / ')}`).toBe(ports.length)
    }

    // 网关是最容易撞的一块：并行分流 = 2 出 + 1 入，汇聚 = 2 入 + 1 出，都必须是 3 个不同的点
    expect(new Set(attach.get('gw_split')!.values()).size).toBe(3)
    expect(new Set(attach.get('gw_merge')!.values()).size).toBe(3)
  })

  it('手动折点：写入 / 清空 / 改锚点自动清掉 / 保存往返不丢', () => {
    let dsl = createEmptyDsl({ key: 'wp_flow', name: '折点测试' })
    // 写入时取整：亚像素抖动不该进 DSL
    dsl = setEdgeWaypoints(dsl, 'edge_1', [{ x: 400.4, y: 120 }, { x: 400, y: 200.6 }])
    expect(dsl.edges[0].waypoints).toEqual([{ x: 400, y: 120 }, { x: 400, y: 201 }])
    // 清空 = 字段消失（不在 DSL 里留一个空壳）
    dsl = setEdgeWaypoints(dsl, 'edge_1', [])
    expect(dsl.edges[0].waypoints).toBeUndefined()
    // 改锚点 → 折点一并清掉：走线两端变了，旧折点已失效。这也是「还原自动走线」的入口
    dsl = setEdgeWaypoints(dsl, 'edge_1', [{ x: 400, y: 120 }])
    dsl = updateEdge(dsl, 'edge_1', { anchor: { source: 'bottom' } })
    expect(dsl.edges[0].waypoints).toBeUndefined()
    expect(dsl.edges[0].anchor).toEqual({ source: 'bottom' })
    // 保存/解析往返：zod 漏声明就会「保存一次折点就丢」
    dsl = setEdgeWaypoints(dsl, 'edge_1', [{ x: 10, y: 20 }])
    const roundTrip = parseFlowDsl(JSON.parse(JSON.stringify(dsl)))
    expect(roundTrip.ok).toBe(true)
    expect(roundTrip.ok ? roundTrip.dsl.edges[0].waypoints : undefined).toEqual([{ x: 10, y: 20 }])
  })

  it('带折点的边：渲染用折点、且不再交给路由算法（一拖不会多出折角）', () => {
    const dsl = baseDsl({
      edges: [
        {
          id: 'f1',
          source: 'start_1',
          target: 'end_1',
          kind: EdgeKind.NORMAL,
          waypoints: [{ x: 300, y: 120 }, { x: 300, y: 200 }],
        },
      ],
    })
    const edge = toGraphData(dsl).edges[0]
    expect(edge.vertices).toEqual([{ x: 300, y: 120 }, { x: 300, y: 200 }])
    // 'normal' = 直接用折点连直线，路由算法不再插手
    expect(edge.router).toEqual({ name: 'normal' })
  })

  it('同层并排且锚点对齐时走直线：不产生折点、也不交给 manhattan', () => {
    // 用户截图那处：技术评审 → 评审驳回，两节点同一层（y 相同、高度相同）
    // 锚点天然对齐 —— 直线就是最终走线，交给 manhattan 会在两端之间绕出一个多余的钩
    const dsl = baseDsl({
      nodes: [
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '技术评审',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        {
          id: 'a2',
          type: NodeType.APPROVAL,
          name: '评审驳回',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
      ],
      edges: [{ id: 'e4', source: 'a1', target: 'a2', kind: EdgeKind.NORMAL, route: EdgeRoute.REJECT }],
      layout: { nodes: { a1: { x: 100, y: 246 }, a2: { x: 400, y: 246 } } },
    })
    const edge = toGraphData(dsl).edges[0]
    expect(sideOfPortId(edge.source.port)).toBe('right')
    expect(sideOfPortId(edge.target.port)).toBe('left')
    // 直线不需要折点
    expect(edge.vertices).toBeUndefined()
    // 但必须用 normal —— 交回 manhattan 才会多出折角
    expect(edge.router).toEqual({ name: 'normal' })
  })

  it('锚点背对源时绕入：箭头从节点外侧进场，不会被压在节点底下', () => {
    // 用户截图那处：会签审批（源）在左上、「设置对象状态」（目标）在右下，
    // 而目标的锚点被手动钉在"下边沿"—— 线必须绕到目标下方、从外面正对着进，
    // 否则会横穿节点（边画在节点下层，箭头就看不见了）
    const dsl = baseDsl({
      nodes: [
        { id: 't1', type: NodeType.TASK, name: '会签审批', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 's1', type: NodeType.SERVICE, name: '设置对象状态' },
      ],
      edges: [
        {
          id: 'f1',
          source: 't1',
          target: 's1',
          kind: EdgeKind.NORMAL,
          route: EdgeRoute.REJECT,
          anchor: { source: 'right', target: 'bottom' },
        },
      ],
      layout: { nodes: { t1: { x: 200, y: 100 }, s1: { x: 420, y: 220 } } },
    })
    const edge = toGraphData(dsl).edges.find((e) => e.id === 'f1')!
    const target = dsl.nodes.find((n) => n.id === 's1')!
    const s1 = geometryOf(dsl, target)
    const vertices = edge.vertices
    expect(vertices && vertices.length).toBeGreaterThan(1)

    // 末点必须**在节点之外、正对底边中点** → 最后一段从下方竖着进，箭头露在外面
    const last = vertices![vertices!.length - 1]
    expect(last.x).toBe(s1.x + s1.width / 2)
    expect(last.y).toBeGreaterThan(s1.y + s1.height)

    // 整条折线不压任何节点
    const rects = dsl.nodes.map((node) => geometryOf(dsl, node))
    const chain = [edge.source, ...vertices!, edge.target]
    for (let i = 0; i < chain.length - 1; i += 1) {
      const a = chain[i] as { x: number; y: number }
      const b = chain[i + 1] as { x: number; y: number }
      if (a.x === b.x && a.y === b.y) continue
      const x1 = Math.min(a.x, b.x)
      const x2 = Math.max(a.x, b.x)
      const y1 = Math.min(a.y, b.y)
      const y2 = Math.max(a.y, b.y)
      for (const r of rects) {
        const crossed = x1 < r.x + r.width - 2 && x2 > r.x + 2 && y1 < r.y + r.height - 2 && y2 > r.y + 2
        expect(crossed, `线段 ${JSON.stringify(a)}→${JSON.stringify(b)} 压到了节点`).toBe(false)
      }
    }
  })

  it('校验错误反映为红色描边 + ! 角标', () => {
    // 会签未配通过规则 → ERROR
    const dsl = baseDsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '会签',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.COUNTERSIGN,
        },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'f1', source: 'start_1', target: 'a1', kind: EdgeKind.NORMAL },
        { id: 'f2', source: 'a1', target: 'end_1', kind: EdgeKind.NORMAL },
      ],
      layout: { nodes: { start_1: { x: 0, y: 0 }, a1: { x: 0, y: 100 }, end_1: { x: 0, y: 200 } } },
    })
    const report = validateDsl(dsl)
    expect(report.ok).toBe(false)
    const data = toGraphData(dsl, report)
    const approval = data.nodes.find((n) => n.id === 'a1')!
    expect(approval.data.badge).toBe(BadgeState.ERROR)
    expect(approval.attrs.body.stroke).toBe('#ff4d4f')
    expect(approval.attrs.badgeText.text).toBe('!')
    // 正常节点不受污染
    const end = data.nodes.find((n) => n.id === 'end_1')!
    expect(end.data.badge).toBe(BadgeState.OK)
    expect(end.attrs.badgeText.text).toBe('')
  })

  it('连线外观区分 普通/条件/默认，并带分支名标签', () => {
    const dsl = baseDsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '分支' },
        { id: 't1', type: NodeType.TASK, name: '办理', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'f0', source: 'start_1', target: 'gw_1', kind: EdgeKind.NORMAL },
        { id: 'f1', source: 'gw_1', target: 't1', kind: EdgeKind.CONDITION, name: '通过' },
        { id: 'f2', source: 'gw_1', target: 'end_1', kind: EdgeKind.DEFAULT },
        { id: 'f3', source: 't1', target: 'end_1', kind: EdgeKind.NORMAL },
      ],
      layout: {
        nodes: {
          start_1: { x: 240, y: 0 },
          gw_1: { x: 240, y: 100 },
          t1: { x: 80, y: 200 },
          end_1: { x: 400, y: 200 },
        },
      },
    })
    const data = toGraphData(dsl, validateDsl(dsl))
    const conditional = data.edges.find((e) => e.id === 'f1')!
    expect(conditional.attrs.line.stroke).toBe('#1677ff')
    expect(conditional.labels[0].attrs.label.text).toBe('通过')
    const defaultEdge = data.edges.find((e) => e.id === 'f2')!
    expect(defaultEdge.attrs.line.strokeDasharray).toBe('5 3')
    expect(defaultEdge.labels[0].attrs.label.text).toBe('默认')
  })

  it('网关多条出边分到不同出口端口', () => {
    const dsl = baseDsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '分支' },
        { id: 't1', type: NodeType.TASK, name: 'A', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 't2', type: NodeType.TASK, name: 'B', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'f0', source: 'start_1', target: 'gw_1', kind: EdgeKind.NORMAL },
        { id: 'f1', source: 'gw_1', target: 't1', kind: EdgeKind.CONDITION },
        { id: 'f2', source: 'gw_1', target: 't2', kind: EdgeKind.CONDITION },
        { id: 'f3', source: 'gw_1', target: 'end_1', kind: EdgeKind.DEFAULT },
      ],
      layout: {
        nodes: {
          start_1: { x: 240, y: 0 },
          gw_1: { x: 240, y: 100 },
          t1: { x: 60, y: 220 },
          t2: { x: 240, y: 220 },
          end_1: { x: 420, y: 220 },
        },
      },
    })
    const data = toGraphData(dsl)
    const ports = ['f1', 'f2', 'f3'].map((id) => data.edges.find((e) => e.id === id)!.source.port)
    // 三条分支的终点都在网关下方：谁都别接「上方」（那是入边方向，接了要绕一整圈）
    expect(ports.every((port) => port !== 'top')).toBe(true)
    // 也不会全挤在同一个附着点上（朝向目标的方位够用时必然分开）
    expect(new Set(ports).size).toBeGreaterThan(1)
  })

  it('连线标签与基础线样式作为「呈现快照」放进 data（显隐由画布决定，不再常显气泡）', () => {
    const dsl = baseDsl({
      edges: [
        { id: 'f1', source: 'start_1', target: 'end_1', kind: EdgeKind.CONDITION, name: '同意' },
      ],
    })
    const edge = toGraphData(dsl).edges.find((e) => e.id === 'f1')!
    // 快照必须与渲染层算出的标签/线样式一致，否则选中态会显示错误内容
    expect(edge.data.labelTexts).toEqual(edge.labels)
    expect(edge.data.line).toEqual(edge.attrs.line)
    expect(edge.data.labelTexts[0]?.attrs.label.text).toBe('同意')
  })

  it('分支连线标记为常显、顺序连线不常显（分支名是读懂条件分支的唯一信息）', () => {
    const dsl = baseDsl({
      edges: [
        { id: 'f1', source: 'start_1', target: 'end_1', kind: EdgeKind.NORMAL },
        { id: 'f2', source: 'start_1', target: 'end_1', kind: EdgeKind.CONDITION, name: '同意' },
        { id: 'f3', source: 'start_1', target: 'end_1', kind: EdgeKind.DEFAULT },
      ],
    })
    const data = toGraphData(dsl)
    const edge = (id: string) => data.edges.find((e) => e.id === id)!
    expect(edge('f1').data.branchEdge).toBe(false)
    expect(edge('f2').data.branchEdge).toBe(true)
    expect(edge('f3').data.branchEdge).toBe(true)
  })

  it('节点带着基础描边宽度进 data（选中态据此加粗，不改颜色以保留校验角标）', () => {
    const data = toGraphData(baseDsl())
    for (const node of data.nodes) {
      expect(typeof node.data.baseStrokeWidth, `${node.id} 缺少 baseStrokeWidth`).toBe('number')
      // 必须与真正画出来的描边宽度一致，否则选中态会把线宽改错
      expect(node.data.baseStrokeWidth).toBe(Number(node.attrs.body.strokeWidth))
    }
  })

  it('所有端口都必须可作连线端点（magnet: true），否则连线无处落下', () => {
    const data = toGraphData(baseDsl())
    for (const node of data.nodes) {
      for (const [groupId, group] of Object.entries(node.ports.groups)) {
        const magnet = (group as { attrs: { circle: { magnet?: boolean } } }).attrs.circle.magnet
        expect(magnet, `${node.id} 的端口 ${groupId} 不可作为连线端点`).toBe(true)
      }
    }
  })

  it('已连端口用淡灰实心、未连用白底（提示"方位用过了"，但依然可落线）', () => {
    // baseDsl：start_1 --(bottom)→ end_1(top)，故起点下方、终点上方是「已连」
    const data = toGraphData(baseDsl())
    const start = data.nodes.find((n) => n.id === 'start_1')!
    const end = data.nodes.find((n) => n.id === 'end_1')!
    const fillOf = (node: typeof start, side: string) =>
      (node.ports.groups[side] as { attrs: { circle: { fill: string } } }).attrs.circle.fill
    expect(fillOf(start, 'bottom')).toBe(CANVAS_STYLE.portConnectedFill)
    expect(fillOf(start, 'right')).toBe(CANVAS_STYLE.portFill)
    expect(fillOf(end, 'top')).toBe(CANVAS_STYLE.portConnectedFill)
    // data.portFills 必须与端口初值同源：悬停离开时按它复位，
    // 两份不一致的话「已连」提示会在悬停一次后就消失
    expect(start.data.portFills.bottom).toBe(fillOf(start, 'bottom'))
    expect(start.data.portFills.right).toBe(fillOf(start, 'right'))
    // 已连 ≠ 不可用：依然必须是可落下的端点
    const magnet = (start.ports.groups.bottom as { attrs: { circle: { magnet?: boolean } } }).attrs
      .circle.magnet
    expect(magnet).toBe(true)
  })

  it('端口默认不可见（opacity 0），但仍是可落下的端点', () => {
    // 隐藏是为了不把画布铺满小圆点；但隐藏绝不能靠 visibility/display ——
    // 那会让端口失去命中区，变成"看得见却落不下线"。本用例同时锁住这两条。
    const data = toGraphData(baseDsl())
    for (const node of data.nodes) {
      for (const [groupId, group] of Object.entries(node.ports.groups)) {
        const circle = (group as { attrs: { circle: { magnet?: boolean; opacity?: number } } }).attrs
          .circle
        expect(circle.opacity, `${node.id} 的端口 ${groupId} 应默认不可见`).toBe(0)
        expect(circle.magnet, `${node.id} 的端口 ${groupId} 隐藏后仍须可作端点`).toBe(true)
      }
    }
  })

  it('同一方位有多个锚点：同一节点的多条出边各有落点，不全挤在方位中点', () => {
    // 用户反馈的场景：审批节点同时有「通过」「驳回」两条出边（未来还会有企业自定义路由）。
    // 一个方位只有一个锚点时，这些线只能挤在同一个点上 —— 而"哪条是驳回"恰恰最该看清。
    const data = toGraphData(twoOutEdgesDsl())
    const approval = data.nodes.find((n) => n.id === 'a1')!
    const groupIds = Object.keys(approval.ports.groups)
    // 宽 150 → 四等分 37.5px ✓ 放满 3 个锚点
    expect(groupIds).toContain('bottom')
    expect(groupIds).toContain('bottom-2')
    expect(groupIds).toContain('bottom-3')
    expect(groupIds).toContain('top-2')
    // 高 52 → 四等分 13px ✗ 只放中点（太密会让吸附在两个锚点间摇摆）
    expect(groupIds).not.toContain('left-2')
    expect(groupIds).not.toContain('right-2')
    // 两条出边各占一个锚点，且锚点由路由决定（通过=主锚点、驳回=下一个）：
    // 固定下来之后，将来插入自定义路由或调换连线顺序都不会让已有连线"挪锚点"
    const portOf = (id: string) => data.edges.find((edge) => edge.id === id)!.source.port
    expect(portOf('f2')).toBe('bottom')
    expect(portOf('f3')).toBe('bottom-2')
    const outPorts = data.edges.filter((edge) => edge.source.cell === 'a1').map((edge) => edge.source.port)
    expect(new Set(outPorts).size).toBe(outPorts.length)
  })

  it('锚点间距不小于下限；0 号锚点沿用方位名（老模板的连线指向它）', () => {
    const size = defaultNodeSize(NodeType.APPROVAL)
    expect(portSlotCount('bottom', size)).toBe(3)
    expect(portSlotCount('left', size)).toBe(1)
    const gap = (portSlotFraction(2) - portSlotFraction(1)) * size.width
    expect(gap).toBeGreaterThanOrEqual(PORT_SLOT_MIN_SPACING)
    expect(portIdOf('bottom', 0)).toBe('bottom')
    expect(portIdOf('bottom', 1)).toBe('bottom-2')
  })

  it('手动指定的锚点优先，且自动分配会避开它（否则两条线仍会挤在同一个点）', () => {
    const dsl = twoOutEdgesDsl()
    // 把「通过」手动钉在主锚点上 → 自动分配的「驳回」必须让开，落到下一个锚点
    const data = toGraphData(withAnchor(dsl, 'f2', { source: 'bottom' }))
    const portOf = (id: string) => data.edges.find((edge) => edge.id === id)!.source.port
    expect(portOf('f2')).toBe('bottom')
    expect(portOf('f3')).toBe('bottom-2')
    // 也可以钉到靠边的锚点上（例如想让线分开得更开）
    const spread = toGraphData(withAnchor(dsl, 'f3', { source: 'bottom-3' }))
    expect(spread.edges.find((edge) => edge.id === 'f3')!.source.port).toBe('bottom-3')
  })

  it('手动锚点失效时回退自动分配（节点改小/改类型后，连线不能因此挂不上或消失）', () => {
    const dsl = twoOutEdgesDsl()
    // `bottom-9` 在任何节点上都不存在 —— 等价于"这个锚点已经没了"
    const data = toGraphData(withAnchor(dsl, 'f2', { source: 'bottom-9', target: 'top-9' }))
    expect(data.edges.find((edge) => edge.id === 'f2')!.source.port).toBe('bottom')
    expect(data.edges.find((edge) => edge.id === 'f2')!.target.port).toBe('top')
  })

  it('只有精确落在圆点上才算"选中了这个锚点"（吸附半径内的近似落点不算）', () => {
    const size = defaultNodeSize(NodeType.APPROVAL)
    const rect = { x: 0, y: 0, ...size }
    const hit = (x: number, y: number, port: string) =>
      isPreciseAnchorHit(NodeType.APPROVAL, rect, { x, y }, port)
    // 锚点坐标由尺寸推导，不写死数字：活动框尺寸会随视觉规格调整而变
    // （槽位是 [0.5, 0.25, 0.75] → 当前 142×44 时下边三点在 x = 71 / 35.5 / 106.5）
    const mid = size.width / 2
    const secondAnchor = size.width * 0.25 // 'bottom-2'
    expect(hit(secondAnchor, size.height, 'bottom-2')).toBe(true)
    expect(hit(mid, size.height, 'bottom')).toBe(true)
    // 落在两点之间 —— 只是"落在附近"，不该被当成选择
    const between = (secondAnchor + mid) / 2
    expect(hit(between, size.height, 'bottom-2')).toBe(false)
    expect(hit(between, size.height, 'bottom')).toBe(false)
    // 节点内部（离最近的圆点 20px+）同样不算
    expect(hit(mid, size.height / 2, 'top')).toBe(false)
    // 该锚点在这类节点上不存在
    expect(hit(75, 52, 'bottom-9')).toBe(false)
    expect(hit(0, 26, 'left-2')).toBe(false)
  })

  it('端点节点被程序挪过坐标的连线必须重挂（自动排版后线断掉的根因）', () => {
    const same = { cell: 'a1', port: 'right' }
    // 什么都没变：不必重挂，避免无谓的视图抖动
    expect(needsReanchor(same, { cell: 'a1', port: 'right' }, false)).toBe(false)
    // 端点换了
    expect(needsReanchor(same, { cell: 'a2', port: 'right' }, false)).toBe(true)
    // 附着方位换了（节点相对位置变了）
    expect(needsReanchor(same, { cell: 'a1', port: 'bottom' }, false)).toBe(true)
    // 端点节点刚被静默挪过坐标：即使端口没变也必须重挂，否则线留在旧坐标上
    expect(needsReanchor(same, { cell: 'a1', port: 'right' }, true)).toBe(true)
    expect(needsReanchor(undefined, { cell: 'a1' }, false)).toBe(true)
  })

  it('回边（驳回这类）的车道朝目标那一侧：目标在右上方时不再从左边绕一圈', () => {
    // 2026-10-13 优化：车道原先只看"源在画布中线哪侧"，目标明明在右上方也要先向左出去，
    // 复杂流程里就是一条绕半个画布的线。现在优先按"目标在源的哪一侧"定车道。
    const dsl = baseDsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        {
          id: 'up_1',
          type: NodeType.APPROVAL,
          name: '上游审批',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: 'SINGLE',
        },
        {
          id: 'down_1',
          type: NodeType.TASK,
          name: '下游办理',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
        },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'f1', source: 'start_1', target: 'up_1', kind: EdgeKind.NORMAL },
        { id: 'f2', source: 'up_1', target: 'down_1', kind: EdgeKind.NORMAL },
        { id: 'f3', source: 'down_1', target: 'end_1', kind: EdgeKind.NORMAL },
        // 回边：从下游回到上游 —— 目标在源的**右上方**，而源在画布中线左侧
        { id: 'f_back', source: 'down_1', target: 'up_1', kind: EdgeKind.NORMAL },
      ],
      layout: {
        nodes: {
          start_1: { x: 100, y: 40 },
          up_1: { x: 520, y: 40 },
          down_1: { x: 100, y: 320 },
          end_1: { x: 100, y: 520 },
        },
      },
    })
    const data = toGraphData(dsl)
    const back = data.edges.find((edge) => edge.id === 'f_back')!
    // 朝目标那一侧出去（右），而不是朝画布外缘那一侧（左）
    expect(sideOfPortId(back.source.port)).toBe('right')
  })

  it('同层（差不多在一条线上）时只用左右方位：不会退到上下绕一圈', () => {
    // 2026-10-13 用户口径：这种关系里就该横向直连，锚点落在左/右的中心锚点上
    const size = { width: 142, height: 44 }
    const left = { x: 100, y: 100, ...size }
    const right = { x: 400, y: 104, ...size } // 纵向几乎完全重叠 = 同层

    const facing = facingPortSides(left, right, ['top', 'right', 'bottom', 'left'])
    expect(facing[0]).toBe('right')
    expect(facing).not.toContain('bottom')
    expect(facing).not.toContain('top')

    // 反过来同理（目标在左边 → 从左侧出）
    const back = facingPortSides(right, left, ['top', 'right', 'bottom', 'left'])
    expect(back[0]).toBe('left')

    // 入边侧的排序同样把上下压到最后（只在左右都占满时才可能兜底用到）
    const ranked = rankPortSides(right, left, ['top', 'right', 'bottom', 'left'])
    expect(ranked[0]).toBe('left')
    // 同层时上下被压成 -1：排在朝向侧（left）之后，只在左右都被占满时才可能兜底用到
    // （背对目标的 right 是 -2，那才是最后一名 —— 别把它和"上下"混为一谈）
    expect(ranked.indexOf('top')).toBeGreaterThan(ranked.indexOf('left'))
    expect(ranked.indexOf('bottom')).toBeGreaterThan(ranked.indexOf('left'))
  })

  it('不同层（上下相邻）时上下方位照常参与 —— 别把纵向连接也掐掉', () => {
    const size = { width: 142, height: 44 }
    const up = { x: 100, y: 100, ...size }
    const down = { x: 110, y: 300, ...size }
    expect(facingPortSides(up, down, ['top', 'right', 'bottom', 'left'])[0]).toBe('bottom')
  })

  it('分支节点：类型名不重复画在画布上，改名后标签挂到图形下方', () => {
    const dsl = baseDsl({
      nodes: [
        { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '条件分支' },
        { id: 'gw_2', type: NodeType.EXCLUSIVE_GATEWAY, name: '金额判断' },
      ],
      edges: [],
    })
    const data = toGraphData(dsl)
    const node = (id: string) => data.nodes.find((n) => n.id === id)!
    // 名字与类型名相同 → 画布上不再重复一遍（形状与配色已经表达了类型）
    expect(node('gw_1').attrs.label.text).toBe('')
    // 用户改过名 → 显示，且贴在菱形下方（46×46 居中写必然溢出成"漂浮气泡"）
    expect(node('gw_2').attrs.label.text).toBe('金额判断')
    expect(node('gw_2').attrs.label.refY).toBe(1)
    expect(node('gw_2').attrs.label.textVerticalAnchor).toBe('top')
  })

  it('矩形节点仍显示名字并居中（未命名的审批节点必须写出「审批」）', () => {
    const dsl = baseDsl({
      nodes: [
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '审批',
          assignee: { strategy: AssigneeStrategy.INITIATOR },
          approvalMode: ApprovalMode.SINGLE,
        },
      ],
      edges: [],
    })
    const node = toGraphData(dsl).nodes.find((n) => n.id === 'a1')!
    expect(node.attrs.label.text).toBe('审批')
    expect(node.attrs.label.textVerticalAnchor).toBe('middle')
  })

  it('圆形节点：名字无名或与类型名相同，都回落类型业务名（开始 / 结束 必须写得出来）', () => {
    const data = toGraphData(baseDsl())
    expect(data.nodes.find((n) => n.id === 'start_1')!.attrs.label.text).toBe('开始')
    expect(data.nodes.find((n) => n.id === 'end_1')!.attrs.label.text).toBe('结束')
  })

  it('菱形节点：无名或只有类型名时都不显示（形状已表达类型，46×46 也放不下）', () => {
    const dsl = baseDsl({
      nodes: [
        { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '' },
        { id: 'gw_2', type: NodeType.EXCLUSIVE_GATEWAY, name: '条件分支' },
      ],
      edges: [],
      layout: { nodes: { gw_1: { x: 0, y: 0 }, gw_2: { x: 0, y: 100 } } },
    })
    const data = toGraphData(dsl)
    expect(data.nodes.find((n) => n.id === 'gw_1')!.attrs.label.text).toBe('')
    expect(data.nodes.find((n) => n.id === 'gw_2')!.attrs.label.text).toBe('')
  })
})

describe('撤销/重做（DSL 快照栈）', () => {
  it('push 后可撤销、撤销后可重做', () => {
    const history = new DslHistory()
    const v1 = baseDsl({ meta: { key: 'a', name: 'v1' } })
    const v2 = baseDsl({ meta: { key: 'a', name: 'v2' } })
    history.reset(v1)
    expect(history.canUndo).toBe(false)

    history.push(v2)
    expect(history.canUndo).toBe(true)
    expect(history.canRedo).toBe(false)

    expect(history.undo()?.meta.name).toBe('v1')
    expect(history.canRedo).toBe(true)
    expect(history.redo()?.meta.name).toBe('v2')
  })

  it('在撤销分支上继续编辑会丢弃重做尾部', () => {
    const history = new DslHistory()
    history.reset(baseDsl({ meta: { key: 'a', name: 'v1' } }))
    history.push(baseDsl({ meta: { key: 'a', name: 'v2' } }))
    history.push(baseDsl({ meta: { key: 'a', name: 'v3' } }))
    history.undo()
    expect(history.canRedo).toBe(true)
    history.push(baseDsl({ meta: { key: 'a', name: 'v2b' } }))
    expect(history.canRedo).toBe(false)
    expect(history.undo()?.meta.name).toBe('v2')
  })

  it('快照隔离：外部修改不影响已入栈状态', () => {
    const history = new DslHistory()
    const source = baseDsl({ meta: { key: 'a', name: '原始' } })
    history.push(source)
    source.meta.name = '被改坏'
    expect(history.current()?.meta.name).toBe('原始')
  })

  it('超出上限丢弃最老一步', () => {
    const history = new DslHistory(3)
    history.reset(baseDsl({ meta: { key: 'a', name: 'v1' } }))
    history.push(baseDsl({ meta: { key: 'a', name: 'v2' } }))
    history.push(baseDsl({ meta: { key: 'a', name: 'v3' } }))
    history.push(baseDsl({ meta: { key: 'a', name: 'v4' } }))
    expect(history.size).toBe(3)
    // 最老的 v1 已被丢弃：连续撤销只能回到 v2
    history.undo()
    history.undo()
    expect(history.canUndo).toBe(false)
    expect(history.current()?.meta.name).toBe('v2')
  })

  it('reset 清空历史（切换模板不应产生可撤销步骤）', () => {
    const history = new DslHistory()
    history.push(baseDsl())
    history.reset(baseDsl({ meta: { key: 'b', name: '另一模板' } }))
    expect(history.canUndo).toBe(false)
    expect(history.size).toBe(1)
  })
})
