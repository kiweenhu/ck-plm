<template>
  <a-spin :spinning="loading">
    <a-alert v-if="loadError" type="warning" show-icon :message="loadError" class="ete-alert" />

    <!-- 变更通告摘要：执行人要先知道"这次改的是哪张单、为什么改"，才不会只盯自己那几行 -->
    <div v-if="notice" class="ete-card">
      <div class="ete-head">
        <span class="ete-title">变更通告</span>
        <a-tag v-if="notice.code" color="blue">{{ notice.code }}</a-tag>
        <a-tag v-if="notice.changeLevel" :color="CHANGE_LEVEL_COLOR[notice.changeLevel] || 'default'">
          {{ CHANGE_LEVEL_LABEL[notice.changeLevel] || notice.changeLevel }}变更
        </a-tag>
        <a-tag v-if="notice.status" :color="CHANGE_NOTICE_STATUS_COLOR[notice.status] || 'default'">
          {{ CHANGE_NOTICE_STATUS_LABEL[notice.status] || notice.status }}
        </a-tag>
        <span class="ete-muted">
          责任人 {{ notice.owner || '—' }} · 计划完成 {{ notice.planEnd || '—' }}
        </span>
      </div>
      <a-descriptions :column="2" size="small" bordered>
        <a-descriptions-item label="标题" :span="2">{{ notice.name || '—' }}</a-descriptions-item>
        <a-descriptions-item label="变更类型">{{ CHANGE_TYPE_LABEL[notice.changeType] || '—' }}</a-descriptions-item>
        <a-descriptions-item label="优先级">{{ PRIORITY_LABEL[notice.priority] || '—' }}</a-descriptions-item>
        <a-descriptions-item label="变更原因" :span="2">
          <div class="ete-pre">{{ htmlToText(notice.reason) || '—' }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="执行方案" :span="2">
          <div class="ete-pre">{{ htmlToText(notice.proposal) || '—' }}</div>
        </a-descriptions-item>
      </a-descriptions>
    </div>

    <!-- 本任务：任务是执行单元，对象范围与计划都以它为准 -->
    <div v-if="task" class="ete-card">
      <div class="ete-head">
        <span class="ete-title">本任务</span>
        <span class="ete-code">{{ task.code }}</span>
        <b>{{ task.name || '—' }}</b>
        <a-tag :color="CHANGE_TASK_STATUS_COLOR[task.status] || 'default'">
          {{ CHANGE_TASK_STATUS_LABEL[task.status] || task.status }}
        </a-tag>
      </div>
      <a-descriptions :column="2" size="small" bordered>
        <a-descriptions-item label="类型">{{ TASK_TYPE_LABEL[task.taskType] || '未分类' }}</a-descriptions-item>
        <a-descriptions-item label="责任人">{{ task.assignee || '—' }}</a-descriptions-item>
        <a-descriptions-item label="计划" :span="2">
          {{ task.planStart || '—' }} ~ {{ task.planEnd || '—' }}
        </a-descriptions-item>
        <a-descriptions-item label="任务说明" :span="2">
          <div class="ete-pre">{{ task.description || '—' }}</div>
        </a-descriptions-item>
      </a-descriptions>
    </div>

    <!-- 本任务的变更对象：执行范围在派发那一刻就定死了，这里只读 -->
    <div class="ete-card">
      <div class="ete-head">
        <span class="ete-title">本任务的变更对象</span>
        <span class="ete-muted">
          共 {{ objects.length }} 项 · 已获取权限 {{ permittedCount }} 项
        </span>
      </div>

      <!--
        权限先行：没修订出新版本就等于还没轮到这次变更，这一步不能被当成表格右上角的小按钮漏掉。
        做成整条横幅，文案直接点名"第 1 步 / 完成变更内容前必须先获取"。
      -->
      <div class="ete-perm" :class="{ 'is-ready': allPermitted }">
        <LockOutlined class="ete-perm-icon" />
        <div class="ete-perm-text">
          <div class="ete-perm-title">
            {{ allPermitted ? '变更权限已获取' : '第 1 步：先获取变更权限' }}
          </div>
          <div class="ete-perm-desc">
            <template v-if="allPermitted">
              对象已修订出新大版本，后续修改都在新版本上做；改完再点「完成变更内容」。
            </template>
            <template v-else>
              直接修订本任务的对象：每个对象新出大版本（变更前 → 变更后）。<b>没获取权限不能完成变更内容</b>；
              对象已被其它变更单占用时修订会被拦下。
            </template>
          </div>
        </div>
        <a-button
          type="primary"
          size="large"
          :loading="revising"
          :disabled="loading || revising || !objects.length || allPermitted"
          @click="acquirePermission"
        >
          {{ allPermitted ? '已获取变更权限' : '获取变更权限' }}
        </a-button>
      </div>

      <!--
        第 2 步：拿到权限（= 修订出新大版本）之后，真正要办的活是"改这些新版本对象"。
        这一步是人工在零件 / 文档 / 工程数据里完成的，表单上只把对象、顺序和落点说清楚，
        免得执行人把第 1 步当成全部工作，直接提交一个什么都没改的新版本。
      -->
      <div class="ete-step2" :class="{ 'is-pending': !allPermitted }">
        <EditOutlined class="ete-step2-icon" />
        <div class="ete-perm-text">
          <div class="ete-perm-title">第 2 步：针对变更后的对象进行修改</div>
          <div class="ete-perm-desc">
            打开「变更前 → 变更后」列里的<b>新版本</b>对象（如 {{ versionHint }}），
            按本次变更要求修改数据 / 数模：检出 → 修改 → 检入；
            本任务的对象都改完，再点「完成变更内容」。
            <span v-if="!allPermitted" class="ete-step2-wait">（先完成第 1 步）</span>
          </div>
        </div>
      </div>

      <a-table
        :columns="columns"
        :data-source="objects"
        size="small"
        row-key="oid"
        :pagination="false"
        :locale="{ emptyText: '这条任务下还没有变更对象：请变更责任人把对象归入本事项' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'target'">
            <ChangeObjectLink :record="record" class="ete-code" />
            {{ record.entityName || '' }}
          </template>
          <template v-else-if="column.key === 'container'">
            {{ record.containerName || '—' }}
          </template>
          <template v-else-if="column.key === 'version'">
            <span class="ete-version">{{ record.baseVersion || '—' }} → {{ record.currentResultVersion || record.resultVersion || '—' }}</span>
          </template>
          <template v-else-if="column.key === 'permit'">
            <a-tag v-if="hasPermission(record)" color="blue">已获取</a-tag>
            <a-tag v-else color="orange">待获取</a-tag>
          </template>
          <template v-else-if="column.key === 'executor'">
            {{ record.executedBy || '—' }}
          </template>
        </template>
      </a-table>
      <div class="ete-hint">
        第 1 步的修订与流程「执行变更修订」节点是同一服务、同一份结果：流程走到那里只会跳过，不会出第二个大版本。
      </div>
    </div>

    <div class="ete-card">
      <div class="ete-head">
        <span class="ete-title">修改说明</span>
        <span class="ete-muted">选填</span>
      </div>
      <a-textarea
        v-model:value="comment"
        :rows="3"
        :maxlength="2000"
        placeholder="请说明本次改了什么：改了哪些数据 / 数模、为什么改、有无遗留...（会记入任务处理记录）"
      />
    </div>
  </a-spin>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { EditOutlined, LockOutlined } from '@ant-design/icons-vue'
import { executeChangeTask, getChangeNotice, getChangeTask } from '@/api'
import {
  CHANGE_LEVEL_COLOR,
  CHANGE_LEVEL_LABEL,
  CHANGE_NOTICE_STATUS_COLOR,
  CHANGE_NOTICE_STATUS_LABEL,
  CHANGE_TASK_STATUS_COLOR,
  CHANGE_TASK_STATUS_LABEL,
  CHANGE_TYPE_LABEL,
  PRIORITY_LABEL,
  TASK_TYPE_LABEL
} from '@/utils/changeFlow'
import { htmlToText } from '@/utils/html'
import ChangeObjectLink from '@/components/change/ChangeObjectLink.vue'

/**
 * 「执行变更任务」表单 —— 变更任务子流程 `change-task-execute` 节点。
 *
 * <p>这一节点是子流程唯一的办理入口：任务责任人打开后要看到"这张通告在改什么、
 * 我这条任务改哪几个对象、改到哪一步了"，而不是只有一个同意/驳回。
 *
 * <p><b>不动任务状态</b>：真正的状态推进是流程本身（「执行变更修订」{@code change.executeTask} /
 * 「任务结项」{@code change.completeTask}）——这里只收集意见，不做"开始处理/提交验收"。
 *
 * <p><b>「获取变更权限」= 直接修订对象</b>（2026-11-11 用户口径）：调 {@code ChangeTaskService#execute}，
 * 为本任务下每个对象新出大版本，结果行就是"这个对象归这次变更改了"的凭证 —— 不是检出。
 * 修订是<b>完成变更内容的硬门槛</b>：没有新大版本说明对象还没进入本次变更，此时提交，
 * 下游看到的仍是旧版本，等于给没改的东西签了字。
 *
 * <p>数据来源：子流程的 businessKey 就是变更任务 oid（{@code ProcessTaskFormVO.businessKey}），
 * 回查任务拿 {@code noticeOid}，再回查通告取摘要与受影响对象 —— 子流程只传
 * {@code taskAssignee} 变量，没有 noticeOid 可读。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：businessKey = 变更任务 oid */
  form: { type: Object, default: null }
})

const taskOid = computed(() => String(props.form?.businessKey || ''))

const loading = ref(false)
const loadError = ref('')
const task = ref(null)
const notice = ref(null)
/** 只留归到本任务的对象：别的任务是别的执行人的范围 */
const objects = ref([])
const comment = ref('')
const revising = ref(false)

const columns = [
  { key: 'target', title: '对象', dataIndex: 'entityName' },
  { key: 'container', title: '所属', dataIndex: 'containerName', width: 160 },
  { key: 'version', title: '变更前 → 变更后', width: 150 },
  { key: 'permit', title: '变更权限', width: 100 },
  { key: 'executor', title: '执行人', dataIndex: 'executedBy', width: 110 }
]

/**
 * 「已获取变更权限」的判据 = 该对象已修订出新大版本（受影响行上有结果）。
 *
 * <p>不另记录"点没点过按钮"：权限这件事的事实只有一个 —— 对象有没有进入新大版本。
 * 这样刷新页面、换人打开、流程节点先跑过，判断都是同一个结果。
 */
const hasPermission = (record) => !!record?.executedAt
const permittedCount = computed(() => objects.value.filter((item) => hasPermission(item)).length)
const missingPermission = computed(() => objects.value.filter((item) => !hasPermission(item)))
const allPermitted = computed(() => objects.value.length > 0 && missingPermission.value.length === 0)

/** 第 2 步文案里的版本例子：用本任务的实际版本跃迁，别写死 A.1 → B.1（各对象起点不同） */
const versionHint = computed(() => {
  const row = objects.value.find((item) => item.baseVersion || item.resultVersion || item.currentResultVersion)
  if (!row) return '变更前版本 → 变更后版本'
  const result = row.currentResultVersion || row.resultVersion || '变更后'
  return `${row.baseVersion || '变更前'} → ${result}`
})

async function load() {
  const oid = taskOid.value
  if (!oid) {
    loadError.value = '取不到变更任务标识（流程 businessKey 为空），无法加载任务内容'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await getChangeTask(oid)
    if (res?.code !== 200 || !res.data) {
      loadError.value = res?.message || '变更任务加载失败'
      return
    }
    task.value = res.data.task || null
    const noticeOid = task.value?.noticeOid
    if (!noticeOid) {
      loadError.value = '该变更任务没有所属通告，无法加载变更内容'
      return
    }
    const detail = await getChangeNotice(noticeOid)
    if (detail?.code === 200 && detail.data) {
      notice.value = detail.data.notice || null
      objects.value = (detail.data.affectedObjects || []).filter((item) => item.taskOid === oid)
    } else {
      loadError.value = detail?.message || '变更通告加载失败'
    }
  } catch {
    // 具体原因由响应拦截器提示
    loadError.value = '变更任务加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 获取变更权限 = 直接修订本任务的对象（每个对象新出大版本）。
 *
 * <p>与流程「执行变更修订」节点同一个服务、同一段实现（{@code ChangeTaskService#execute}），
 * 已出过结果的对象由后端跳过 —— 这里先跑过，节点再跑一次也不会出第二个版本。
 *
 * <p>失败时后端是整批回滚并说明是哪个对象（"已被 X 检出，请先检入或取消检出后再修订"
 * 这类），原样提示即可：要处理的是那个对象，不是这段表单。
 */
async function acquirePermission() {
  if (allPermitted.value) {
    message.info('本任务的对象都已获取变更权限')
    return
  }
  revising.value = true
  try {
    const res = await executeChangeTask(taskOid.value)
    if (res?.code !== 200) {
      message.error(res?.message || '获取变更权限失败')
      return
    }
    const rows = res.data || []
    const done = rows.filter((row) => row.executedAt).length
    message.success(`已获取变更权限：${done} 个对象已修订出新版本`)
    await load()
  } catch {
    // 失败原因由响应拦截器提示
  } finally {
    revising.value = false
  }
}

/**
 * 完成变更内容前的闸门。
 *
 * <p>三条：任务有对象、且每个对象都「已获取变更权限」（= 已修订出新大版本）。
 * 权限这条是硬拦 —— 没有新版本说明对象还没进入本次变更，此时放行，
 * 下游审批的就是一张什么都没改的单。判据只看受影响行上的结果（executedAt），
 * 不依赖"用户点没点过按钮"，刷新、换人、流程先跑过都不影响结论。
 */
async function collect() {
  if (loading.value) {
    return { ok: false, message: '任务内容还在加载，请稍候' }
  }
  if (!task.value) {
    return { ok: false, message: '取不到变更任务，无法提交；请联系变更责任人检查这条任务' }
  }
  if (!objects.value.length) {
    return {
      ok: false,
      message: '这条任务下还没有变更对象：请让变更责任人把对象归入本条更改事项后再提交'
    }
  }
  const missing = objects.value.filter((item) => !hasPermission(item))
  if (missing.length) {
    const codes = missing.slice(0, 3).map((item) => item.entityCode || item.entityName).join('、')
    return {
      ok: false,
      message: `还有 ${missing.length} 个对象未获取变更权限（${codes}${missing.length > 3 ? ' 等' : ''}）：`
        + '请先点「获取变更权限」修订出新版本，再完成变更内容'
    }
  }
  return { ok: true, payload: { action: 'approve', comment: comment.value.trim() } }
}

const renderable = computed(() => true)
const submitText = computed(() => '完成变更内容')
const successText = computed(() => '变更内容已确认，变更任务流程继续往下走')

onMounted(load)

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.ete-card {
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fcfcfc;
}

.ete-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-bottom: 10px;
}

.ete-title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.ete-muted {
  flex: 1;
  font-size: 12px;
  color: #8c8c8c;
}

.ete-code {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #595959;
}

/* 版本跃迁用等宽：A.2 → B.1 这类值对齐了才扫得快 */
.ete-version {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  white-space: nowrap;
}

.ete-pre {
  white-space: pre-wrap;
  line-height: 1.6;
}

.ete-alert {
  margin-bottom: 12px;
}

.ete-hint {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #8c8c8c;
}

/* 权限横幅：未获取时用暖色提醒，获取完转绿，一眼能看出这一步过没过 */
.ete-perm {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid #ffd591;
  border-radius: 8px;
  background: #fffbe6;
}

.ete-perm.is-ready {
  border-color: #b7eb8f;
  background: #f6ffed;
}

.ete-perm-icon {
  font-size: 20px;
  color: #fa8c16;
}

.ete-perm.is-ready .ete-perm-icon {
  color: #52c41a;
}

.ete-perm-text {
  flex: 1;
  min-width: 0;
}

.ete-perm-title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.ete-perm-desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
  color: #8c8c8c;
}

/* 第 2 步：中性底、左侧蓝条，醒目但不抢「获取变更权限」主按钮 */
.ete-step2 {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px 14px;
  border: 1px solid #d9d9d9;
  border-left: 3px solid #1677ff;
  border-radius: 8px;
  background: #fafafa;
}

.ete-step2.is-pending {
  border-left-color: #d9d9d9;
}

.ete-step2-icon {
  font-size: 20px;
  color: #1677ff;
}

.ete-step2.is-pending .ete-step2-icon,
.ete-step2.is-pending .ete-perm-title {
  color: #8c8c8c;
}

.ete-step2-wait {
  color: #8c8c8c;
}
</style>
