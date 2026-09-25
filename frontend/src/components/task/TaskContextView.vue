<template>
  <a-card size="small" class="tcv-card" title="通用信息">
    <!-- 1) 流程对应的业务实体（ck_process_entity_set：一个实例可能关联多个实体） -->
    <div class="tcv-block">
      <div class="tcv-block-title">
        业务实体
        <!-- 总数：一个流程带一批对象走审批时，"一共几个"是这栏最先要回答的问题
             （与页头「等 4 个实体」同一口径）。空关联时不给数字 —— 下面那句
             "该流程未关联业务实体"已经把 0 说清楚了，写成"业务实体（0）"是同一句话两处说 -->
        <span v-if="entities.length" class="tcv-block-count">（{{ entities.length }}）</span>
      </div>
      <a-empty v-if="!entities.length" :image-style="{ height: '32px' }"
        description="该流程未关联业务实体" />
      <!-- 一个实体一行看着清爽，但"一个流程带一批对象走审批"时 4 条就占掉半屏；
           多于一条即排成两列（`is-multi`），每条仍是同一套内容 -->
      <div class="tcv-entities" :class="{ 'is-multi': entities.length > 1 }">
        <div v-for="entity in entities" :key="entity.entityOid" class="tcv-entity">
          <a-tag color="geekblue">{{ entity.typeCode || entity.rootTypeCode || '未知类型' }}</a-tag>
          <span class="tcv-entity-name">{{ labelOf(entity) }}</span>
          <span v-if="codeOf(entity)" class="tcv-entity-code">{{ codeOf(entity) }}</span>
          <a-tag v-if="entity.entityVersion" size="small">版本 {{ entity.entityVersion }}</a-tag>
          <!-- 生命周期状态跟在版本后面：版本说"哪一版"、状态说"现在是什么状态"，
               两个都是"这条对象现在怎样"的判断依据，缺一个就得点进去看。
               与任务中心/流程监控同一份回查结果（entityInfos.statusName），颜色口径也一致 -->
          <a-tag v-if="statusOf(entity)" size="small" color="green">{{ statusOf(entity) }}</a-tag>
          <!-- 这里原先还显示 entityOid：一串 36 位 uuid 既没人读，又把这一行挤成两行；
               要认对象看编码/名称就够，真需要 oid 的地方是接口与日志，不是给人看的界面 -->
        </div>
      </div>
    </div>

    <!-- 2) 流程信息 -->
    <div class="tcv-block">
      <div class="tcv-block-title">流程信息</div>
      <!-- 两列而非三列：进度挪到右栏后左列变窄，三列会把"开始时间 2026/9/17 17:39:51"这类值挤成折行 -->
      <a-descriptions :column="2" size="small" :label-style="labelStyle">
        <a-descriptions-item label="流程">
          {{ process?.processDefinitionName || task?.processDefinitionName || '—' }}
        </a-descriptions-item>
        <a-descriptions-item label="流程 Key">
          {{ process?.processDefinitionKey || task?.processDefinitionKey || '—' }}
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="statusColor(process?.status)">{{ statusText(process?.status) }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="发起人">{{ process?.startUserId || '—' }}</a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ fmtTime(process?.startTime) }}</a-descriptions-item>
        <a-descriptions-item label="结束时间">{{ fmtTime(process?.endTime) }}</a-descriptions-item>
      </a-descriptions>
    </div>

    <!-- 3) 当前任务信息：办理前先确认"办的是哪一条"（新窗口开多了容易认错） -->
    <div class="tcv-block">
      <div class="tcv-block-title">当前任务</div>
      <a-descriptions :column="2" size="small" :label-style="labelStyle">
        <a-descriptions-item label="任务">{{ task?.name || '—' }}</a-descriptions-item>
        <!-- 负责人显示人名：assignee 可能是用户名也可能是人员 oid（按 oid 指派的下游任务） -->
        <a-descriptions-item label="负责人">{{ task?.assigneeName || task?.assignee || '—' }}</a-descriptions-item>
        <a-descriptions-item label="节点 Key">{{ task?.taskDefinitionKey || '—' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ fmtTime(task?.createTime) }}</a-descriptions-item>
        <a-descriptions-item label="截止时间">
          <span :style="{ color: task?.overdue ? '#f5222d' : '' }">
            {{ task?.dueDate ? fmtTime(task.dueDate) : '—' }}{{ task?.overdue ? '（已逾期）' : '' }}
          </span>
        </a-descriptions-item>
        <a-descriptions-item label="表单">
          {{ formCode ? (hasOwnTaskForm(formCode) ? taskFormLabel(formCode) : `${formCode}（未注册，按通用表单办理）`) : '未配置（按通用表单办理）' }}
        </a-descriptions-item>
      </a-descriptions>
    </div>
  </a-card>
</template>

<script setup>
import { computed } from 'vue'
import { effectiveFormCode, hasOwnTaskForm, taskFormLabel } from './taskForms'

/**
 * 「通用信息」渲染视图 —— 办理页的固定部分（与具体任务是哪个表单无关的部分）。
 *
 * <p>三个区块对应渲染上下文里的三份数据：
 * <ol>
 *   <li><b>业务实体</b>：流程关联的 ProcessEntity 引用（类型 / 大版本 / oid）；</li>
 *   <li><b>流程信息</b>：流程名与 key、状态、发起人、起止时间；</li>
 *   <li><b>当前任务</b>：任务名、负责人、节点 key、创建/截止时间、用的是哪个表单模板。</li>
 * </ol>
 *
 * <p><b>流程进度不在这里</b>：它是"当前卡在哪"的实时信息，办理时要一直看得见，
 * 所以渲染在办理页左栏（与页头同一行的位置），用同一份上下文的 {@code activities}。
 *
 * <p>实体<b>显示名</b>（编码/名称）不在这里回查：后端只给引用，回查由办理页统一做一次并下传
 * （{@code entityInfos}，见 composables/useEntityInfo）—— 页头也要显示同一个名字，
 * 各查一遍会出现同一实体两次请求，且两处可能一个查到一个没查到。查不到只影响显示，
 * 不影响办理，所以这里只做降级。
 */
const props = defineProps({
  /** 渲染上下文（GET /workflow/task/{id}/context 的 data） */
  context: { type: Object, default: () => ({}) },
  /** entityOid → { code, name }：办理页回查后下传（缺省时退化为只显示引用） */
  entityInfos: { type: Object, default: () => ({}) },
})

const labelStyle = { width: '84px', color: '#8c8c8c' }

const task = computed(() => props.context?.task || null)
const process = computed(() => props.context?.process || null)
/** 实际生效的表单 code：没写 formKey 的老实例按节点类型派生（否则这里会显示成"未配置"） */
const formCode = computed(() => effectiveFormCode(props.context?.form) || task.value?.formKey || '')
const entities = computed(() => props.context?.entities || [])

/** 实体显示名：名称优先，回查不到就显示编码，再不行给一句占位（不显示空白） */
function labelOf(entity) {
  const info = props.entityInfos?.[entity.entityOid]
  return info?.name || info?.code || '(对象)'
}

/** 实体编码（PART-2026… 这类）：与名称分列显示，便于对照/抄写 */
function codeOf(entity) {
  return props.entityInfos?.[entity.entityOid]?.code || ''
}

/**
 * 实体生命周期状态显示名（迭代状态，如「草稿」「已发布」）。
 *
 * <p>取自与页头、任务中心同一份回查结果（{@code statusName}，见 composables/useEntityInfo），
 * 回查不到就不显示 —— 状态是业务对象的事实，流程侧不知道，不编造。
 */
function statusOf(entity) {
  return props.entityInfos?.[entity.entityOid]?.statusName || ''
}

function fmtTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('zh-CN', { hour12: false })
}

/** 流程状态：与流程监控口径一致 */
function statusText(status) {
  return ({ running: '进行中', suspended: '已挂起', completed: '已完成', terminated: '已终止' })[status] || '—'
}

function statusColor(status) {
  return ({ running: 'blue', suspended: 'orange', completed: 'green', terminated: 'red' })[status] || 'default'
}
</script>

<style scoped>
.tcv-card {
  margin-bottom: 14px;
}

.tcv-block + .tcv-block {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #f0f0f0;
}

.tcv-block-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #595959;
}

/* 计数跟在标题后面、比标题轻：它是补充说明，不是第二个标题 */
.tcv-block-count {
  color: #8c8c8c;
  font-weight: 400;
}

/*
 * 多个实体时排成两列（固定两列，不用 auto-fit：卡片宽度变化时列数跟着变，
 * 同一条数据一会儿一行两个、一会儿一行三个，看的人得重新找位置）。
 * minmax(0, 1fr) 而非 1fr：避免长编码把列撑破溢出。
 */
.tcv-entities.is-multi {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
}

.tcv-entity {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 0;
}

/* 两列时内容更容易折行：改顶对齐，折行后标签与名称仍在一行起头处对齐 */
.tcv-entities.is-multi .tcv-entity {
  align-items: flex-start;
}

.tcv-entity-name {
  font-size: 14px;
  color: #262626;
  font-weight: 500;
}

/* 实体编码：常规色 + 等宽，和名称并列 —— 名称认人、编码用于对照与抄写 */
.tcv-entity-code {
  font-size: 12px;
  color: #595959;
  font-family: 'Consolas', 'Monaco', monospace;
}
</style>
