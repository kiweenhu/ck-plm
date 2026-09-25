<template>
  <div class="flow-designer">
    <!-- 工具栏：全部动作都作用于 DSL（唯一事实源），画布只是投影 -->
    <div class="designer-toolbar">
      <a-space>
        <a-button size="small" @click="emit('back')">← 模板列表</a-button>
        <a-divider type="vertical" />
        <a-button size="small" :disabled="!canUndo" @click="undo">撤销</a-button>
        <a-button size="small" :disabled="!canRedo" @click="redo">重做</a-button>
        <a-button size="small" @click="runAutoLayout">自动排版</a-button>
        <a-button size="small" :disabled="!hasSelection" @click="removeSelection()">删除选中</a-button>
        <a-divider type="vertical" />
        <a-button size="small" type="primary" :loading="saving" :disabled="!templateOid" @click="openSave">
          保存
        </a-button>
        <a-button
          size="small"
          :loading="deploying"
          :disabled="!templateOid || dirty"
          :title="dirty ? '有未保存改动，请先保存' : '部署到流程引擎'"
          @click="doDeploy"
        >
          部署
        </a-button>
        <a-button size="small" :disabled="!templateOid" @click="historyVisible = true">版本历史</a-button>
        <a-divider type="vertical" />
        <a-button size="small" @click="xmlVisible = true">预览 BPMN</a-button>
        <!-- 「载入历史版本」的回头路：把画布换回最新版（不产生新版本） -->
        <a-button
          size="small"
          :disabled="viewingVersion === null"
          :title="viewingVersion === null ? '当前已在最新版' : `画布上是 v${viewingVersion} 的快照`"
          @click="goToLatest"
        >
          转至最新版
        </a-button>
      </a-space>
      <div class="designer-toolbar__status">
        <a-tag v-if="report.errorCount" color="error">{{ report.errorCount }} 个错误</a-tag>
        <a-tag v-else-if="report.warningCount" color="warning">{{ report.warningCount }} 个提醒</a-tag>
        <a-tag v-else color="success">校验通过</a-tag>
        <a-tag v-if="dirty" color="processing">未保存</a-tag>
        <span class="designer-toolbar__hint">{{ templateLabel }}</span>
      </div>
    </div>

    <div class="designer-body">
      <NodePalette />
      <div class="designer-canvas">
        <FlowCanvas
          :dsl="dsl"
          :report="report"
          @drop-node="onDropNode"
          @connected="onConnected"
          @edge-reconnected="onEdgeReconnected"
          @edge-waypoints-changed="onEdgeWaypointsChanged"
          @node-moved="(id, pos) => moveNode(id, pos)"
          @node-move-end="commitMove"
          @selection-changed="(s) => (selection = s)"
          @delete-selected="removeSelection"
          @undo="undo"
          @redo="redo"
          @reject="onReject"
        />
      </div>
      <div class="designer-inspector">
        <a-tabs v-model:activeKey="inspectorTab" size="small">
          <a-tab-pane key="props" tab="属性">
            <!-- 面板的写入走「整对象替换」：可见性变化会删字段，浅合并表达不了删除 -->
            <PropertyPanel
              :dsl="dsl"
              :report="report"
              :selection="selection"
              @patch-node="applyNode"
              @patch-edge="applyEdge"
              @patch-meta="applyMeta"
              @patch-variables="applyVariables"
            />
          </a-tab-pane>
          <a-tab-pane key="issues" :tab="`校验 (${report.issues.length})`">
            <a-empty v-if="!report.issues.length" description="暂无问题" :image-style="{ height: '40px' }" />
            <div
              v-for="(issue, index) in report.issues"
              :key="index"
              class="inspector-issue"
              :class="`is-${issue.level.toLowerCase()}`"
            >
              <span class="inspector-issue__level">{{ issue.level === 'ERROR' ? '错误' : '提醒' }}</span>
              <span>{{ issue.message }}</span>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </div>

    <!-- 保存：生成新版本，需填变更说明 -->
    <a-modal v-model:open="saveVisible" title="保存（生成新版本）" :confirm-loading="saving" @ok="doSave">
      <a-form layout="vertical">
        <a-form-item label="变更说明">
          <a-input v-model:value="changeNote" placeholder="如：调整技术评审的会签比例" />
        </a-form-item>
      </a-form>
      <div class="designer-hint">
        当前 v{{ template?.latestVersion ?? '-' }} → 保存后生成 v{{ (template?.latestVersion ?? 0) + 1 }}
      </div>
    </a-modal>

    <!-- 部署结果：成功后展示引擎标识与本层编译警告 -->
    <a-modal v-model:open="deployVisible" title="部署结果" width="640px" :footer="null">
      <a-result status="success" title="部署成功" :sub-title="`流程定义 ${deployResult?.outcome?.processDefinitionId ?? '-'}`">
        <template #extra>
          <a-space direction="vertical" style="text-align: left">
            <div>部署 id：{{ deployResult?.outcome?.deploymentId ?? '-' }}</div>
            <div>部署版本：v{{ deployResult?.outcome?.version ?? template?.latestVersion ?? '-' }}</div>
          </a-space>
        </template>
      </a-result>
      <div v-if="deployResult?.warnings?.length" class="designer-warnings">
        <div class="designer-warnings__title">编译降级提示（{{ deployResult.warnings.length }} 项）</div>
        <div v-for="(w, i) in deployResult.warnings" :key="i">· {{ w }}</div>
      </div>
    </a-modal>

    <VersionHistory
      :open="historyVisible"
      :template-oid="templateOid"
      :current-version="template?.latestVersion ?? null"
      @close="historyVisible = false"
      @load="onLoadVersion"
    />

    <a-modal v-model:open="xmlVisible" title="编译产物（BPMN 2.0 XML）" width="860px" :footer="null">
      <pre class="designer-xml">{{ compiledXml }}</pre>
      <div v-if="compileWarnings.length" class="designer-warnings">
        <div v-for="(w, i) in compileWarnings" :key="i">· {{ w }}</div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
/**
 * 流程设计器宿主（画布 + 节点库 + 属性面板 + 模板治理）。
 *
 * <p>数据流严格单向：<b>DSL → 画布</b>。画布手势只触发这里的动作，
 * 由动作调用 dsl-core 的 ops 产出新 DSL，再经 props 推回画布。
 * 因此不存在「图上改了但 DSL 没变」的状态。
 *
 * <p>模板治理（保存/部署/版本/部署开关/复制）全部经 `template-repo`：
 * 保存拦截与 key 不可变等规则只实现一次，本组件不重复实现，只呈现 `{ ok, error }`。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import FlowCanvas from '@flow-canvas/FlowCanvas.vue'
import { useFlowDesigner } from '@flow-canvas'
import NodePalette from './NodePalette.vue'
import PropertyPanel from './panel/PropertyPanel.vue'
import VersionHistory from './VersionHistory.vue'
import { templateRepository } from './template-service'
import { dslSignature } from './template-repo'
import { getNotificationChannels } from '@/api'
import { setSystemNotifyChannels, validateOptions } from './system-channels'
// 流程表单模板：企业自定义的在后端表里（业务配置 → 流程表单），启动时拉一次注入注册表，
// 节点的「节点表单」下拉才能同步列出它们
import { loadFormTemplates } from './form-templates'

const props = defineProps({
  /** 模板 oid；为空时退化为「离线草稿」（不可保存/部署） */
  templateOid: { type: String, default: null },
})

const emit = defineEmits(['back'])

const {
  dsl,
  report,
  selection,
  canUndo,
  canRedo,
  addNodeAt,
  connect,
  reconnectEdge,
  moveNode,
  commitMove,
  applyNode,
  applyEdge,
  applyEdgeWaypoints,
  applyMeta,
  applyVariables,
  removeSelection,
  runAutoLayout,
  undo,
  redo,
  replaceDsl,
} = useFlowDesigner(undefined, undefined, validateOptions)

const template = ref(null)
const saving = ref(false)
const deploying = ref(false)
const inspectorTab = ref('props')
const historyVisible = ref(false)
const xmlVisible = ref(false)
const saveVisible = ref(false)
const deployVisible = ref(false)
const changeNote = ref('')
const deployResult = ref(null)

/**
 * 已保存内容的指纹。
 *
 * <p>载入历史版本时会写入一个哨兵值（`__loaded_v{n}__`），
 * 它永远不等于任何真实 DSL 的 JSON 指纹 —— 于是「载入历史版本」必然标记为未保存改动，
 * 用户必须保存才会生效（保证「回退」是一次可审计的保存，而不是抹掉历史）。
 */
const baseline = ref(dslSignature(dsl.value))

/**
 * 画布上当前承载的是哪个历史版本的快照（null = 最新版）。
 *
 * <p>没有它就无法回答「我现在看的是哪个版本」：工具栏标签一直显示模板的
 * `latestVersion`，载入历史版本后用户会以为自己在改最新版（只有一个「未保存」tag，
 * 看不出为什么未保存）。
 */
const viewingVersion = ref(null)

const dirty = computed(() => dslSignature(dsl.value) !== baseline.value)
const hasSelection = computed(
  () => (selection.value?.nodeIds?.length ?? 0) > 0 || (selection.value?.edgeIds?.length ?? 0) > 0,
)

/**
 * 选中对象集合的稳定指纹（顺序无关、重复点同一对象不变）。
 *
 * <p>用它而不是 selection 对象本身判「选择变了」：画布**每次点击都会产出新的 selection 对象**
 * （哪怕点的是同一个节点），按对象身份判会把用户刚翻到的页签反复抢回来。
 */
function selectionKey(next) {
  const nodes = [...(next?.nodeIds ?? [])].sort()
  const edges = [...(next?.edgeIds ?? [])].sort()
  return nodes.length || edges.length ? `n:${nodes.join(',')}|e:${edges.join(',')}` : ''
}

/**
 * 换选中对象时把检查器切回「属性」。
 *
 * <p>「校验」是**全局问题清单**：用户在那里看完问题、回画布点节点准备动手修，
 * 页签若还停在「校验」，点开的新节点属性压根看不见（现象就是"属性面板停在校验"）。
 *
 * <p>两条边界：<b>失去选择不切</b>（点空白背景时不该把正在读的问题清单关掉）；
 * <b>选择没变不切</b>（重复点同一个节点是"继续看它"，不是"换了一个"）。
 * 切过去也不丢信息：属性面板里本就有「本项问题」，正好是当前对象的那几条。
 */
const lastSelectionKey = ref('')
watch(selection, (next) => {
  const key = selectionKey(next)
  if (key === lastSelectionKey.value) return
  lastSelectionKey.value = key
  if (key) inspectorTab.value = 'props'
})

const templateLabel = computed(() => {
  if (!props.templateOid) return '离线草稿（未绑定模板）'
  if (!template.value) return props.templateOid
  const base = `${template.value.displayName || template.value.name}（${template.value.key}） v${template.value.latestVersion ?? '-'}`
  // 载入历史版本后必须让用户看清「看的是哪一版」，否则会与 latestVersion 混淆
  return viewingVersion.value === null ? base : `${base} · 当前查看 v${viewingVersion.value}`
})

/** 编译产物：与部署走同一编译入口，避免两处产物不一致 */
const compiled = computed(() => templateRepository.compile(dsl.value))
const compiledXml = computed(() => (compiled.value.ok ? compiled.value.data.xml : ''))
const compileWarnings = computed(() => (compiled.value.ok ? compiled.value.data.warnings : []))

async function loadTemplate() {
  if (!props.templateOid) return
  const result = await templateRepository.load(props.templateOid)
  if (!result.ok) {
    message.error(result.error)
    return
  }
  template.value = result.data.template
  replaceDsl(result.data.dsl)
  baseline.value = dslSignature(result.data.dsl)
}

/** 只刷新模板主档（不动画布，避免重置撤销历史） */
async function refreshTemplate() {
  if (!props.templateOid) return
  const result = await templateRepository.load(props.templateOid)
  if (result.ok) {
    template.value = result.data.template
  }
}

onMounted(loadTemplate)
watch(() => props.templateOid, loadTemplate)
// 系统通知渠道取一次即可：校验"流程声明的通知方式系统有没有启用"要用它。
// 取不到就当没有（校验跳过该规则），绝不让设计器因为读不到系统配置而打不开
onMounted(loadSystemNotifyChannels)

// 表单模板要在用户选中节点之前就绪：面板的下拉是同步渲染的（见 form-templates.ts）
onMounted(loadFormTemplates)

async function loadSystemNotifyChannels() {
  try {
    const res = await getNotificationChannels()
    const usable = (res?.data || []).filter((channel) => channel.usable).map((channel) => channel.code)
    setSystemNotifyChannels(usable)
  } catch {
    setSystemNotifyChannels(undefined)
  }
}

function onDropNode({ presetId, position }) {
  addNodeAt(presetId, position)
}

function onConnected({ source, target, sourcePort, targetPort }) {
  // 非法连线必须给出原因：画布已移除 X6 的临时连线，若不提示就是「线莫名消失」。
  // 端点锚点一并传下去：用户点中哪个小圆点起手，这条线就固定在哪个点上
  const result = connect(source, target, { sourcePort, targetPort })
  if (!result.ok && result.error) {
    message.warning(result.error)
  }
}

/**
 * 拖动已有连线的端点（重连）：只改两端与锚点，分支名/类型/条件/路由全部保留。
 *
 * <p>失败原因由画布层先拦一道（`canConnect`），这里兜住 ops 的异常 ——
 * 两条路径都要给出可读原因，绝不能"拖了一下、线自己弹回去、没有任何提示"。
 */
function onEdgeReconnected(connection) {
  const result = reconnectEdge(connection)
  if (!result.ok && result.error) {
    message.warning(result.error)
  }
}

/**
 * 用户拖了折角：把折点写进 DSL。
 *
 * <p>折点与"手动锚点"同级，都是用户意图：写进去之后自动走线/自动排版都不再覆盖这条线的走线。
 * 想回到自动：在面板里把起点/终点锚点选一次（改锚点会一并清掉折点）。
 */
function onEdgeWaypointsChanged(edgeId, points) {
  applyEdgeWaypoints(edgeId, points)
}

function onReject(reason) {
  message.warning(reason)
}

function openSave() {
  changeNote.value = ''
  saveVisible.value = true
}

async function doSave() {
  saving.value = true
  const result = await templateRepository.save(
    props.templateOid,
    dsl.value,
    changeNote.value.trim() || undefined,
    template.value?.key,
  )
  saving.value = false
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  template.value = result.data
  baseline.value = dslSignature(dsl.value)
  saveVisible.value = false
  message.success(`已保存为 v${result.data?.latestVersion ?? ''}`)
}

async function doDeploy() {
  if (dirty.value) {
    message.warning('有未保存改动，请先保存再部署')
    return
  }
  deploying.value = true
  const result = await templateRepository.deploy(props.templateOid, dsl.value, template.value?.latestVersion)
  deploying.value = false
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  deployResult.value = result.data
  deployVisible.value = true
  await refreshTemplate()
}

function onLoadVersion({ dsl: versionDsl, version }) {
  replaceDsl(versionDsl)
  baseline.value = `__loaded_v${version}__`
  viewingVersion.value = version
  historyVisible.value = false
}

/**
 * 转至最新版 —— 与「载入历史版本」配对。
 *
 * <p>载入历史版本会把快照放进画布并标记未保存（回退必须是可审计的一次保存），
 * 但此前<b>没有回头的出口</b>：想回到最新版只能重新打开模板或刷新页面。
 *
 * <p>本操作<b>不产生新版本</b>：直接拉取模板最新版 DSL 覆盖画布，并把指纹基线重置为它
 * ——「未保存」随之消失、`viewingVersion` 归零。
 *
 * <p>会丢弃已载入的快照，且 `replaceDsl` 会重置撤销栈（不可撤销），故先确认。
 */
async function goToLatest() {
  if (!props.templateOid || viewingVersion.value === null) {
    return
  }
  if (dirty.value && !(await confirmDiscardLoadedVersion())) {
    return
  }
  const result = await templateRepository.load(props.templateOid)
  if (!result.ok) {
    message.error(result.error)
    return
  }
  template.value = result.data.template
  replaceDsl(result.data.dsl)
  baseline.value = dslSignature(result.data.dsl)
  viewingVersion.value = null
  message.success(`已转至最新版 v${result.data.template?.latestVersion ?? ''}`)
}

/** 确认「丢弃已载入的历史版本快照」（Modal.confirm 的 Promise 包装） */
function confirmDiscardLoadedVersion() {
  return new Promise((resolve) => {
    Modal.confirm({
      title: '转至最新版？',
      content: `画布上当前是 v${viewingVersion.value} 的快照（未保存改动）。转至最新版会丢弃它，且不能撤销。`,
      okText: '丢弃并转至最新版',
      cancelText: '再想想',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}
</script>

<style scoped>
.flow-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}
.designer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}
.designer-toolbar__status {
  display: flex;
  align-items: center;
  gap: 8px;
}
.designer-toolbar__hint {
  font-size: 12px;
  color: #8c8c8c;
}
.designer-body {
  display: flex;
  flex: 1;
  min-height: 0;
}
.designer-canvas {
  flex: 1;
  min-width: 0;
}
.designer-inspector {
  width: 320px;
  border-left: 1px solid #f0f0f0;
  padding: 4px 12px 12px;
  overflow-y: auto;
  flex-shrink: 0;
}
.designer-hint {
  font-size: 12px;
  color: #8c8c8c;
}
.inspector-issue {
  font-size: 12px;
  line-height: 1.6;
  padding: 6px 8px;
  border-radius: 4px;
  margin-bottom: 6px;
  background: #fafafa;
}
.inspector-issue.is-error {
  background: #fff1f0;
  color: #cf1322;
}
.inspector-issue.is-warning {
  background: #fffbe6;
  color: #d48806;
}
.inspector-issue__level {
  font-weight: 600;
  margin-right: 6px;
}
.designer-xml {
  max-height: 460px;
  overflow: auto;
  font-size: 12px;
  background: #fafafa;
  padding: 10px;
  border-radius: 6px;
}
.designer-warnings {
  margin-top: 10px;
  font-size: 12px;
  color: #d48806;
  line-height: 1.8;
}
.designer-warnings__title {
  font-weight: 600;
  margin-bottom: 4px;
}
</style>
