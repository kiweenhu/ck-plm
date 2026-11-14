<template>
  <a-spin :spinning="loading">
    <a-alert v-if="loadError" type="warning" show-icon :message="loadError" class="etop-alert" />

    <!-- 通告与本任务：审批人要先知道这是哪张单、哪一条任务，再看对象版本 -->
    <div v-if="notice" class="etop-card">
      <div class="etop-head">
        <span class="etop-title">变更通告</span>
        <a-tag v-if="notice.code" color="blue">{{ notice.code }}</a-tag>
        <a-tag :color="CHANGE_NOTICE_STATUS_COLOR[notice.status] || 'default'">
          {{ CHANGE_NOTICE_STATUS_LABEL[notice.status] || notice.status }}
        </a-tag>
        <span class="etop-muted">{{ notice.name || '—' }} · 责任人 {{ notice.owner || '—' }}</span>
      </div>
      <div class="etop-line">变更原因：{{ htmlToText(notice.reason) || '—' }}</div>
      <div class="etop-line">执行方案：{{ htmlToText(notice.proposal) || '—' }}</div>
    </div>

    <div v-if="task" class="etop-card">
      <div class="etop-head">
        <span class="etop-title">本任务</span>
        <span class="etop-code">{{ task.code }}</span>
        <b>{{ task.name || '—' }}</b>
        <a-tag :color="CHANGE_TASK_STATUS_COLOR[task.status] || 'default'">
          {{ CHANGE_TASK_STATUS_LABEL[task.status] || task.status }}
        </a-tag>
        <span class="etop-muted">责任人 {{ task.assignee || '—' }}</span>
      </div>
      <div class="etop-line">任务说明：{{ task.description || '—' }}</div>
    </div>

    <!-- 审批判的是"这些对象出没出新版本、改到哪一步"，只读，不在这里改状态 -->
    <div class="etop-card">
      <div class="etop-head">
        <span class="etop-title">本任务修订的对象</span>
        <span class="etop-muted">共 {{ objects.length }} 项 · 已修订 {{ revisedCount }} 项</span>
      </div>
      <a-alert
        v-if="!loading && !objects.length"
        type="warning"
        show-icon
        class="etop-alert"
        message="本任务下还没有变更对象：请变更责任人把对象归入这条更改事项"
      />
      <a-alert
        v-else-if="!loading && !revisedCount"
        type="warning"
        show-icon
        class="etop-alert"
        message="这些对象还没有修订出新版本，审批前请先确认执行人已完成「获取变更权限」"
      />
      <a-table
        :columns="columns"
        :data-source="objects"
        size="small"
        row-key="oid"
        :pagination="false"
        :locale="{ emptyText: '本任务下没有变更对象' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'target'">
            <ChangeObjectLink :record="record" class="etop-code" />
            {{ record.entityName || '' }}
          </template>
          <template v-else-if="column.key === 'container'">
            {{ record.containerName || '—' }}
          </template>
          <template v-else-if="column.key === 'version'">
            <span class="etop-version">{{ record.baseVersion || '—' }} → {{ record.currentResultVersion || record.resultVersion || '—' }}</span>
          </template>
          <template v-else-if="column.key === 'state'">
            <a-tag v-if="record.executedAt" color="green">已修订</a-tag>
            <a-tag v-else color="orange">待修订</a-tag>
          </template>
          <template v-else-if="column.key === 'executor'">
            {{ record.executedBy || '—' }}
          </template>
        </template>
      </a-table>
    </div>
  </a-spin>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getChangeNotice, getChangeTask } from '@/api'
import {
  CHANGE_NOTICE_STATUS_COLOR,
  CHANGE_NOTICE_STATUS_LABEL,
  CHANGE_TASK_STATUS_COLOR,
  CHANGE_TASK_STATUS_LABEL
} from '@/utils/changeFlow'
import { htmlToText } from '@/utils/html'
import ChangeObjectLink from '@/components/change/ChangeObjectLink.vue'

/**
 * 「本任务修订了什么」只读面板 —— 变更任务子流程下游三个节点共用
 * （设置审批人 / 领导审批 / 会签审批）。
 *
 * <p>审批人判的是"这次改了哪些对象、有没有修出新版本"，只给一个意见框等于凭记忆签字。
 * 这块信息刻意与「执行变更任务」表单同源：子流程 businessKey = 变更任务 oid，
 * 回查任务 → 通告 → 受影响对象（按 taskOid 过滤），不另造一套口径。
 *
 * <p><b>只读、且从不阻止办理</b>：取不到详情只是少一块信息，不该让审批卡死
 * （renderable 由外层表单决定，本面板不参与）。
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
const objects = ref([])

const columns = [
  { key: 'target', title: '对象', dataIndex: 'entityName' },
  { key: 'container', title: '所属', dataIndex: 'containerName', width: 170 },
  { key: 'version', title: '变更前 → 变更后', width: 150 },
  { key: 'state', title: '修订状态', width: 100 },
  { key: 'executor', title: '执行人', dataIndex: 'executedBy', width: 110 }
]

const revisedCount = computed(() => objects.value.filter((item) => item.executedAt).length)

async function load() {
  const oid = taskOid.value
  if (!oid) {
    loadError.value = '取不到变更任务标识（流程 businessKey 为空），无法加载修订对象'
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
      loadError.value = '该变更任务没有所属通告，无法加载修订对象'
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
    loadError.value = '变更任务加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.etop-card {
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fcfcfc;
}

.etop-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-bottom: 8px;
}

.etop-title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.etop-muted {
  flex: 1;
  font-size: 12px;
  color: #8c8c8c;
}

.etop-line {
  font-size: 12px;
  line-height: 1.7;
  color: #595959;
  white-space: pre-wrap;
}

.etop-code {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #595959;
}

.etop-version {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  white-space: nowrap;
}

.etop-alert {
  margin-bottom: 10px;
}
</style>
