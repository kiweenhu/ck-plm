<template>
  <div class="cqc">
    <!-- ① 只读：本流程的元器件引入申请信息（器件 + MPN + 申请要点） -->
    <div class="cqc-section">申请信息（只读）</div>
    <QualificationRequestSummary :task-id="taskId" class="cqc-summary" />

    <!-- ② 只读：会签规则 + 各参与人的办理情况 -->
    <div class="cqc-section">会签</div>
    <div class="cqc-rule">
      <span class="cqc-rule__label">会签规则</span>
      <span class="cqc-rule__value">{{ model.ruleText }}</span>
      <span class="cqc-rule__detail">{{ model.ruleDetail }}</span>
    </div>

    <div class="cqc-block">
      <div class="cqc-block__head">
        <span class="cqc-block__title">会签情况</span>
        <span class="cqc-block__progress">
          {{ model.progressText }}
          <template v-if="model.approvedCount || model.rejectedCount">
            （同意 {{ model.approvedCount }} · 驳回 {{ model.rejectedCount }}）
          </template>
        </span>
      </div>
      <a-table
        :columns="columns" :data-source="rows" size="small" row-key="rowKey"
        :pagination="false" :row-class-name="rowClassOf"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            {{ record.name }}
            <a-tag v-if="record.isMe" color="blue">我</a-tag>
          </template>
          <template v-else-if="column.key === 'state'">
            <a-tag :color="stateOf(record).color">{{ stateOf(record).text }}</a-tag>
          </template>
          <template v-else-if="column.key === 'comment'">
            <span v-if="record.comment" class="cqc-comment">{{ record.comment }}</span>
            <span v-else class="cqc-empty">—</span>
          </template>
          <template v-else>
            <span v-if="record.timeText">{{ record.timeText }}</span>
            <span v-else class="cqc-empty">—</span>
          </template>
        </template>
      </a-table>
      <div v-if="!model.total" class="cqc-empty-line">暂未取到参与者（流程进度里还没有这条活动的记录）</div>
    </div>

    <!-- ③ 我这一票：结论 + 意见（口径与内置「会签」一致） -->
    <a-form layout="vertical" class="cqc-mine">
      <a-form-item label="我的会签意见" :required="requiredNow">
        <div class="cqc-routes">
          <a-radio-group v-model:value="action">
            <a-radio value="approve">同意</a-radio>
            <a-radio v-if="model.opinion.allowReject" value="reject">驳回</a-radio>
          </a-radio-group>
          <span v-if="isReject && model.opinion.allowReject" class="cqc-target">
            驳回后回到：{{ model.opinion.rejectTargetText }}
          </span>
        </div>
        <a-textarea v-model:value="comment" :rows="4" :placeholder="placeholder" />
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { buildCountersignModel, validateCountersign } from '@/flow-designer/app/task/countersign'
import QualificationRequestSummary from './QualificationRequestSummary.vue'

/**
 * 「元器件认证会签」—— 本流程**会签节点专用**（用户要求，2026-10-13）。
 *
 * <h3>为什么不用内置「会签」</h3>
 * <p>内置那张回答"规则是什么 / 别人投了什么 / 我怎么投"，但会签人同样<b>看不到器件的申请信息</b>
 * ——认定评审（会签）恰恰是要集体判断"这颗器件该不该放行"。本表 = 内置会签的全部内容
 * （直接复用 {@code buildCountersignModel} / {@code validateCountersign}，规则、进度、
 * 逐人结论与意见、否决口径一处不改）<b>+ 一块只读申请信息</b>
 * （{@link QualificationRequestSummary}，与评审表、认证表共用）。
 *
 * <h3>提交口径</h3>
 * <p>与内置会签完全一致：{@code collect() → { ok, payload: { action, comment } }} ——
 * 不额外写流程变量，保证会签的通过规则 / 一票否决语义与内置表无差别。
 */
const props = defineProps({
  /** 任务 id（用于标出"我"那一行，也供只读摘要取申请信息） */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
  /** 流程进度（TaskContextVO.activities）：同一会签活动下一人一条 */
  activities: { type: Array, default: () => [] },
})

const action = ref('approve')
const comment = ref('')

const columns = [
  { key: 'name', title: '参与人', dataIndex: 'name', width: 150 },
  { key: 'state', title: '结论', dataIndex: 'state', width: 100 },
  { key: 'time', title: '时间', dataIndex: 'timeText', width: 150 },
  { key: 'comment', title: '意见', dataIndex: 'comment' },
]

/** 会签表单模型：规则 + 参与人明细 + 我这一票的口径（纯函数，与内置会签同源） */
const model = computed(() => {
  let dsl = null
  const raw = props.form?.dslJson
  if (raw) {
    try {
      dsl = JSON.parse(raw)
    } catch {
      dsl = null
    }
  }
  return buildCountersignModel({
    dsl,
    nodeId: props.form?.taskDefinitionKey,
    activities: props.activities,
    currentTaskId: props.taskId,
  })
})

/** 每行的稳定 key：有任务 id 用它，没有（存量数据）用活动下标兜底 */
const rows = computed(() =>
  model.value.participants.map((item, index) => ({ ...item, rowKey: item.taskId || `row-${index}` })),
)

const isReject = computed(() => action.value === 'reject')

/** 结论标签：未办理 / 已同意 / 已驳回 / 已办理（存量数据没有逐人结论时不猜） */
function stateOf(record) {
  if (!record.done) return { text: '未办理', color: 'default' }
  if (record.decision === 'APPROVE') return { text: '已同意', color: 'green' }
  if (record.decision === 'REJECT') return { text: '已驳回', color: 'red' }
  return { text: '已办理', color: 'default' }
}

/** 自己那一行淡蓝底：在一堆人里一眼找到自己 */
function rowClassOf(record) {
  return record?.isMe ? 'cqc-row-me' : ''
}

const requiredNow = computed(() =>
  (isReject.value
    ? model.value.opinion.commentRequiredOnReject
    : model.value.opinion.commentRequiredOnApprove))

const placeholder = computed(() => {
  if (isReject.value) {
    return model.value.opinion.commentRequiredOnReject ? '请说明驳回理由（必填）' : '请说明驳回理由...'
  }
  return model.value.opinion.commentRequiredOnApprove ? '请填写会签意见（必填）' : '请输入会签意见...'
})

const submitText = computed(() => (isReject.value ? '驳回并提交' : '同意并提交'))
const successText = computed(() =>
  (isReject.value ? '已驳回，流程按配置退回到指定位置' : '已同意，会签按配置规则继续'))
/** 只读摘要取不到也不挡表决（规则与进度不依赖它） */
const renderable = computed(() => true)

function collect() {
  const issue = validateCountersign(model.value, action.value, comment.value)
  if (issue) {
    return { ok: false, message: issue }
  }
  return { ok: true, payload: { action: action.value, comment: comment.value } }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.cqc-section {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.cqc-summary {
  margin-bottom: 18px;
}

.cqc-rule {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  padding: 8px 12px;
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  border-radius: 6px;
}

.cqc-rule__label {
  font-size: 12px;
  color: #2f54eb;
}

.cqc-rule__value {
  font-size: 13px;
  font-weight: 600;
  color: #1d39c4;
}

.cqc-rule__detail {
  font-size: 12px;
  color: #8c8c8c;
}

.cqc-block {
  margin: 16px 0 4px;
}

.cqc-block__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 6px;
}

.cqc-block__title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.cqc-block__progress {
  font-size: 12px;
  color: #8c8c8c;
}

.cqc-comment {
  white-space: pre-wrap;
}

.cqc-empty {
  color: #bfbfbf;
}

.cqc-empty-line {
  margin-top: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

.cqc-mine {
  margin-top: 12px;
}

/* 自己那一行：淡蓝底 + 左侧色条，一眼可辨 */
.cqc :deep(.cqc-row-me) > td {
  background: #f0f5ff;
}

.cqc :deep(.cqc-row-me) > td:first-child {
  border-left: 3px solid #1677ff;
}

.cqc-routes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-bottom: 8px;
}

.cqc-target {
  font-size: 12px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
  padding: 1px 8px;
}
</style>
