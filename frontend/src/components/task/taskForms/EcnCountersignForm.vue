<template>
  <div class="ecs">
    <!-- ① 通告本体：会签人要先看清"改什么、影响哪些对象、拆成了哪些任务"才谈得上确认怎么改 -->
    <EcnSummaryPanel :form="form" />

    <!-- ② 会签进度 + 各专业已给出的口径 -->
    <div class="ecs-card">
      <div class="ecs-card__head">
        <span class="ecs-card__title">会签情况</span>
        <span class="ecs-progress">
          {{ model.progressText }}
          <template v-if="model.approvedCount || model.rejectedCount">
            （同意 {{ model.approvedCount }} · 驳回 {{ model.rejectedCount }}）
          </template>
        </span>
        <span class="ecs-rule">{{ model.ruleText }}</span>
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
          <template v-else-if="column.key === 'discipline'">
            <span v-if="record.plan">{{ record.plan.discipline }}</span>
            <span v-else class="ecs-empty">—</span>
          </template>
          <template v-else-if="column.key === 'state'">
            <a-tag :color="stateOf(record).color">{{ stateOf(record).text }}</a-tag>
          </template>
          <template v-else-if="column.key === 'comment'">
            <span v-if="record.comment" class="ecs-comment">{{ stripMarkup(record.comment) }}</span>
            <span v-else class="ecs-empty">—</span>
          </template>
          <template v-else>
            <span v-if="record.timeText">{{ record.timeText }}</span>
            <span v-else class="ecs-empty">—</span>
          </template>
        </template>
      </a-table>
      <div v-if="!model.total" class="ecs-empty-line">暂未取到参与者（流程进度里还没有这条活动的记录）</div>
    </div>

    <!-- ③ 我这一票：确认本专业这一段"怎么改" -->
    <div class="ecs-card ecs-mine">
      <div class="ecs-card__head">
        <span class="ecs-card__title">我的实施确认</span>
        <span class="ecs-muted">按你负责的专业说明这一段怎么改，会记入会签情况</span>
      </div>
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :xs="24" :sm="10">
            <a-form-item label="专业" required>
              <a-select v-model:value="discipline" placeholder="选择你负责的专业" :options="disciplineOptions" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="14">
            <a-form-item label="需协调的方面">
              <a-select
                v-model:value="concerns"
                mode="multiple"
                allow-clear
                placeholder="可多选"
                :options="concernOptions"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="实施说明" required>
          <a-textarea
            v-model:value="note"
            :rows="3"
            :maxlength="1000"
            show-count
            placeholder="本专业这一段具体怎么改：改动范围、切换与回退、需要的资源与周期（如：更换电机供应商，需重做 3 台样机验证，周期 3 周）"
          />
        </a-form-item>
        <a-form-item label="会签结论" :required="commentRequired">
          <a-radio-group v-model:value="action" class="ecs-routes">
            <a-radio value="approve">同意</a-radio>
            <a-radio v-if="model.opinion.allowReject" value="reject">驳回</a-radio>
          </a-radio-group>
          <a-textarea v-model:value="comment" :rows="2" :placeholder="commentPlaceholder" />
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { buildCountersignModel, validateCountersign } from '@/flow-designer/app/task/countersign'
import { useUserStore } from '@/stores/user'
import EcnSummaryPanel from './EcnSummaryPanel.vue'

/**
 * 「变更通告 · 会签」表单。
 *
 * <p>与「变更请求影响分析会签」的区别在<b>问的问题</b>：ECR 问"影响多大"，ECN 问"这一段怎么改" ——
 * 所以本表单不评影响程度，只收专业口径 + 实施说明。
 *
 * <p>结论同时写两处（与会签规则强相关，改动前先读 {@code EcrImpactCountersignForm} 的同类注释）：
 * <ul>
 *   <li>流程变量 {@code planOpinion__<taskId>}（一人一键，实例级）—— 供本表汇总他人结论；</li>
 *   <li>会签意见 {@code comment} —— 进 Flowable 历史评论，「流程进度」等其它视图也能看到，
 *       不依赖本表渲染。</li>
 * </ul>
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：variables / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
  /** 当前任务 id：既是"我"的标识，也是本人结论在流程变量里的键后缀 */
  taskId: { type: String, default: '' },
  /** 流程进度（TaskContextVO.activities）：同一会签活动下一人一条 */
  activities: { type: Array, default: () => [] }
})

const userStore = useUserStore()

const DISCIPLINES = ['硬件', '软件', '结构', '工艺', '质量', '采购', '生产', '其他']
const CONCERNS = ['认证/合规', '客户/合同', '在制库存', '工装夹具', '成本', '交期', '其它']

const VAR_PREFIX = 'planOpinion__'

const discipline = ref(undefined)
const concerns = ref([])
const note = ref('')
const action = ref('approve')
const comment = ref('')

const disciplineOptions = DISCIPLINES.map((item) => ({ value: item, label: item }))
const concernOptions = CONCERNS.map((item) => ({ value: item, label: item }))

const columns = [
  { key: 'name', title: '参与人', dataIndex: 'name', width: 140 },
  { key: 'discipline', title: '专业', dataIndex: 'discipline', width: 90 },
  { key: 'state', title: '结论', dataIndex: 'state', width: 90 },
  { key: 'time', title: '时间', dataIndex: 'timeText', width: 140 },
  { key: 'comment', title: '意见', dataIndex: 'comment' }
]

/** 会签模型：规则 + 参与人（复用内置会签的纯函数，规则解释不在这里重写） */
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
    currentTaskId: props.taskId
  })
})

/** 已提交的专业口径：流程变量里 {@code planOpinion__*} 的集合（按 taskId 索引，不会串行） */
const plans = computed(() => {
  const map = {}
  const variables = props.form?.variables || {}
  for (const [key, value] of Object.entries(variables)) {
    if (!key.startsWith(VAR_PREFIX)) continue
    try {
      const parsed = typeof value === 'string' ? JSON.parse(value) : value
      if (parsed && typeof parsed === 'object') {
        map[parsed.taskId || key.slice(VAR_PREFIX.length)] = parsed
      }
    } catch {
      /* 历史脏数据不该让整张表渲染不出来 */
    }
  }
  return map
})

const rows = computed(() =>
  model.value.participants.map((item, index) => {
    const key = item.taskId || `row-${index}`
    return { ...item, rowKey: key, plan: plans.value[item.taskId] || null }
  })
)

const commentRequired = computed(() =>
  action.value === 'reject'
    ? model.value.opinion.commentRequiredOnReject
    : model.value.opinion.commentRequiredOnApprove)

const commentPlaceholder = computed(() => {
  if (action.value === 'reject') {
    return model.value.opinion.commentRequiredOnReject ? '请说明驳回理由（必填）' : '请说明驳回理由...'
  }
  return model.value.opinion.commentRequiredOnApprove ? '请填写会签意见（必填）' : '可补充说明...'
})

const submitText = computed(() => (action.value === 'reject' ? '驳回并提交' : '同意并提交'))
const successText = computed(() =>
  action.value === 'reject' ? '已驳回，流程按配置退回到指定位置' : '已同意，会签按配置规则继续')

/** 取不到通告也要能办完这一票：本表单永远可渲染，只是少一块只读信息 */
const renderable = computed(() => true)

/** 会签情况里不重复展示我们自己加的标记（专业已在「专业」列体现） */
function stripMarkup(text) {
  return String(text || '').replace(/^【[^】]*】\s*/, '')
}

function stateOf(record) {
  if (!record.done) return { text: '未办理', color: 'default' }
  if (record.decision === 'APPROVE') return { text: '已同意', color: 'green' }
  if (record.decision === 'REJECT') return { text: '已驳回', color: 'red' }
  return { text: '已办理', color: 'default' }
}

function rowClassOf(record) {
  return record?.isMe ? 'ecs-row-me' : ''
}

/**
 * 收集提交载荷。
 *
 * <p>专业口径写进流程变量（键带 taskId，避免多人互相覆盖），同时拼进意见文本 ——
 * 让「流程进度 / 会签情况」在别的视图里也能看到结论，不必依赖本表。
 */
function collect() {
  if (!discipline.value) {
    return { ok: false, message: '请选择你负责的专业' }
  }
  if (!note.value.trim()) {
    return { ok: false, message: '请填写本专业的实施说明' }
  }
  const issue = validateCountersign(model.value, action.value, comment.value)
  if (issue) {
    return { ok: false, message: issue }
  }

  const concernText = concerns.value.length ? ` 协调：${concerns.value.join('、')}。` : ''
  const tail = comment.value.trim() ? ` 会签意见：${comment.value.trim()}` : ''
  const head = `【${discipline.value}】${note.value.trim()}`

  return {
    ok: true,
    payload: {
      action: action.value,
      comment: `${head}${concernText}${tail}`.trim(),
      variables: {
        [`${VAR_PREFIX}${props.taskId}`]: JSON.stringify({
          taskId: props.taskId,
          name: userStore.displayName || userStore.username || '',
          discipline: discipline.value,
          concerns: concerns.value,
          note: note.value.trim()
        })
      }
    }
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.ecs-card {
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.ecs-card__head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-bottom: 10px;
}

.ecs-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.ecs-progress {
  font-size: 12px;
  color: #595959;
}

.ecs-rule {
  font-size: 12px;
  color: #8c8c8c;
}

.ecs-muted {
  font-size: 12px;
  color: #8c8c8c;
}

.ecs-empty {
  color: #bfbfbf;
}

.ecs-empty-line {
  margin-top: 8px;
  font-size: 12px;
  color: #bfbfbf;
}

.ecs-comment {
  display: inline-block;
  max-width: 420px;
  white-space: pre-wrap;
}

.ecs-routes {
  margin-bottom: 8px;
}

.ecs-mine {
  background: #fcfcfc;
}

:deep(.ecs-row-me) {
  background: #e6f4ff;
}
</style>
