<template>
  <div class="csf">
    <!-- ① 规则：会签是"多人多票一起决定"，这一票在什么规则下才算数必须先说清 -->
    <div class="csf-rule">
      <span class="csf-rule__label">会签规则</span>
      <span class="csf-rule__value">{{ model.ruleText }}</span>
      <span class="csf-rule__detail">{{ model.ruleDetail }}</span>
    </div>

    <!-- ② 他人的会签情况：同一会签活动下每个人办到哪一步、投了什么、写了什么意见 -->
    <div class="csf-block">
      <div class="csf-block__head">
        <span class="csf-block__title">会签情况</span>
        <span class="csf-block__progress">
          {{ model.progressText }}
          <template v-if="model.approvedCount || model.rejectedCount">
            （同意 {{ model.approvedCount }} · 驳回 {{ model.rejectedCount }}）
          </template>
        </span>
      </div>
      <a-table
        :columns="columns"
        :data-source="rows"
        size="small"
        row-key="rowKey"
        :pagination="false"
        :row-class-name="rowClassOf"
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
            <span v-if="record.comment" class="csf-comment">{{ record.comment }}</span>
            <span v-else class="csf-empty">—</span>
          </template>
          <template v-else>
            <span v-if="record.timeText">{{ record.timeText }}</span>
            <span v-else class="csf-empty">—</span>
          </template>
        </template>
      </a-table>
      <div v-if="!model.total" class="csf-empty-line">暂未取到参与者（流程进度里还没有这条活动的记录）</div>
    </div>

    <!-- ③ 我这一票：结论 + 意见（口径与审批一致，同样可以配驳回） -->
    <a-form layout="vertical" class="csf-mine">
      <a-form-item label="我的会签意见" :required="requiredNow">
        <div class="csf-routes">
          <a-radio-group v-model:value="action">
            <a-radio value="approve">同意</a-radio>
            <!-- 活动没开驳回时不摆这个选项：让人选了却被后端/流程拒掉，不如不给 -->
            <a-radio v-if="model.opinion.allowReject" value="reject">驳回</a-radio>
          </a-radio-group>
          <span v-if="isReject && model.opinion.allowReject" class="csf-target">
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

/**
 * 「会签」—— 会签审批活动的固定表单模板。
 *
 * <p>它回答三件事：<b>规则是什么</b>（如「1 个通过即通过」）、<b>其他人办得怎么样</b>
 * （谁同意、谁驳回、各自意见）、<b>我这一票怎么投</b>。
 *
 * <p>前两件是只读的展示：规则来自设计期配置（`passRule`），办理情况来自流程进度
 * —— 让经办人在办理时改规则或替别人投票，都会把会签变成不可预期的东西。
 *
 * <p>规则解释集中在 {@link buildCountersignModel}（纯函数、可单测），本组件只渲染与收集；
 * 写在这里就会与设计器面板的配置各说各话。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
  /** 当前任务 id（用于标出"我"那一行） */
  taskId: { type: String, default: '' },
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

/** 会签表单模型：规则 + 参与人明细 + 我这一票的口径 */
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
  if (!record.done) {
    return { text: '未办理', color: 'default' }
  }
  if (record.decision === 'APPROVE') {
    return { text: '已同意', color: 'green' }
  }
  if (record.decision === 'REJECT') {
    return { text: '已驳回', color: 'red' }
  }
  return { text: '已办理', color: 'default' }
}

/** 自己那一行淡蓝底：在一堆人里一眼找到自己 */
function rowClassOf(record) {
  return record?.isMe ? 'csf-row-me' : ''
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

/** 按钮文案表明这次提交的结论 */
const submitText = computed(() => (isReject.value ? '驳回并提交' : '同意并提交'))

const successText = computed(() =>
  (isReject.value ? '已驳回，流程按配置退回到指定位置' : '已同意，会签按配置规则继续'))

/** 本表单不依赖 DSL 才能渲染（规则取不到时用缺省文案），故始终可渲染 */
const renderable = computed(() => true)

/** 收集提交载荷；不合规时返回原因（由渲染器统一提示） */
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
.csf-rule {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  padding: 8px 12px;
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  border-radius: 6px;
}

.csf-rule__label {
  font-size: 12px;
  color: #2f54eb;
}

.csf-rule__value {
  font-size: 13px;
  font-weight: 600;
  color: #1d39c4;
}

.csf-rule__detail {
  font-size: 12px;
  color: #8c8c8c;
}

.csf-block {
  margin: 16px 0 4px;
}

.csf-block__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 6px;
}

.csf-block__title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.csf-block__progress {
  font-size: 12px;
  color: #8c8c8c;
}

.csf-comment {
  white-space: pre-wrap;
}

.csf-empty {
  color: #bfbfbf;
}

.csf-empty-line {
  margin-top: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

.csf-mine {
  margin-top: 12px;
}

/* 自己那一行：淡蓝底 + 左侧色条，一眼可辨 */
.csf :deep(.csf-row-me) > td {
  background: #f0f5ff;
}

.csf :deep(.csf-row-me) > td:first-child {
  border-left: 3px solid #1677ff;
}

.csf-routes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-bottom: 8px;
}

.csf-target {
  font-size: 12px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
  padding: 1px 8px;
}
</style>
