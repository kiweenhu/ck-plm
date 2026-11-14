<template>
  <EcnSummaryPanel ref="summaryRef" :form="form" hide-tasks />

  <div class="etp-card">
    <div class="etp-head">
      <span class="etp-title">更改事项（派发清单）</span>
      <span class="etp-muted">
        共 {{ tasks.length }} 项 · 提交时按这份清单为每项拉起一条变更任务流程
      </span>
      <a-button type="primary" size="small" :disabled="loading" @click="openCreate">
        + 新增事项
      </a-button>
    </div>

    <a-alert v-if="loadError" type="warning" show-icon :message="loadError" class="etp-alert" />

    <a-spin :spinning="loading">
      <a-table
        :columns="columns"
        :data-source="tasks"
        size="small"
        row-key="oid"
        :pagination="false"
        :locale="{ emptyText: '还没有更改事项：至少加一条，否则提交后不会派发任何任务' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'task'">
            <span class="etp-code">{{ record.code }}</span>
            {{ record.name || '—' }}
            <div v-if="record.description" class="etp-desc">{{ record.description }}</div>
          </template>
          <template v-else-if="column.key === 'type'">
            {{ TASK_TYPE_LABEL[record.taskType] || '未分类' }}
          </template>
          <template v-else-if="column.key === 'assignee'">
            <span v-if="record.assignee">{{ record.assignee }}</span>
            <span v-else class="etp-warn">未指派</span>
          </template>
          <template v-else-if="column.key === 'plan'">
            <span class="etp-plan">{{ record.planStart || '—' }} ~ {{ record.planEnd || '—' }}</span>
          </template>
          <template v-else-if="column.key === 'objects'">
            <span v-if="taskObjects(record).length" class="etp-objects">
              {{ taskObjects(record).map((o) => o.entityCode).join('、') }}
            </span>
            <span v-else class="etp-warn">未选对象</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="CHANGE_TASK_STATUS_COLOR[record.status] || 'default'">
              {{ CHANGE_TASK_STATUS_LABEL[record.status] || record.status }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'op'">
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm
              title="删除这条更改事项？它名下的变更对象也会一并从本通告移除"
              ok-text="删除"
              cancel-text="取消"
              @confirm="removeTask(record)"
            >
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-spin>

    <div class="etp-hint">
      清单改动立即写库；提交时会再同步一次流程变量 <code>changeTaskList</code> ——
      下游按它 1:M 派发，没同步的话派发的还是审批通过那一刻的旧清单。
    </div>
  </div>

  <div class="etp-card">
    <div class="etp-head">
      <span class="etp-title">办理意见</span>
    </div>
    <a-textarea v-model:value="comment" :rows="3" placeholder="可补充说明（如为什么拆成这几项）..." />
  </div>

  <a-modal
    v-model:open="modal.visible"
    :title="modal.editing ? '编辑更改事项' : '新增更改事项'"
    :confirm-loading="modal.saving"
    width="760px"
    ok-text="保存"
    cancel-text="取消"
    :body-style="{ maxHeight: '62vh', overflowY: 'auto', overflowX: 'hidden' }"
    @ok="saveTask"
  >
    <a-form layout="vertical">
      <a-form-item label="事项标题" required>
        <a-input v-model:value="modal.form.name" placeholder="如：外壳卡扣加厚" :maxlength="120" />
      </a-form-item>
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="类型">
            <a-select v-model:value="modal.form.taskType" placeholder="选择类型" allow-clear>
              <a-select-option v-for="(label, value) in TASK_TYPE_LABEL" :key="value" :value="value">
                {{ label }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="责任人">
            <ChangeUserSelect
              :value="modal.form.assignee"
              placeholder="选择责任人"
              @update:value="(val) => (modal.form.assignee = val || '')"
            />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="计划开始">
            <a-date-picker
              v-model:value="modal.form.planStart"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="计划完成">
            <a-date-picker
              v-model:value="modal.form.planEnd"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
      </a-row>
      <!-- 变更对象：与「创建变更通告 → 添加事项」同一口径 —— 事项的边界是"改哪些对象"，
           只填标题类型的话，派发出去的任务到了执行节点才发现没对象可改 -->
      <a-form-item label="包含的变更对象">
        <ChangeAffectedObjectSelect
          v-model:value="modal.form.affectedOids"
          :seed-options="modal.seedScope"
          :exclude-oids="excludeOids"
          placeholder="可选：输入编号或名称搜索零件 / 文档"
        />
      </a-form-item>
      <!-- 收集：选一个零件，把它关联的文档 / 工程数据 / 替代件一次带进来（BOM 结构不在范围内） -->
      <div class="etp-collect-bar">
        <a-button size="small" :loading="modal.collecting" @click="collectRelatedObjects">
          收集关联对象
        </a-button>
        <span class="etp-muted">把所选零件关联的文档、工程数据、替代件一并加入本任务（可多次收集）</span>
      </div>
      <div class="etp-hint">
        这里选的是这张任务要改的对象，保存时按对象当前版本加入本通告的受影响对象，并归到这张任务下。
      </div>
      <a-form-item label="说明" class="etp-desc-item">
        <a-textarea v-model:value="modal.form.description" :rows="3" :maxlength="500" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  ChangeAffectedObjectSelect,
  ChangeUserSelect
} from '@/components/change/changeFieldWidgets'
import {
  addChangeAffectedByEntity,
  assignChangeAffectedTask,
  createChangeTask,
  deleteChangeTask,
  getChangeNotice,
  listRelatedChangeObjects,
  removeChangeAffectedObject,
  syncChangeNoticeDispatchList,
  updateChangeTask
} from '@/api'
import {
  CHANGE_TASK_STATUS_COLOR,
  CHANGE_TASK_STATUS_LABEL,
  TASK_TYPE_LABEL
} from '@/utils/changeFlow'
import EcnSummaryPanel from './EcnSummaryPanel.vue'

/**
 * 「变更任务确认与派发」表单 —— 变更通告流程 `change-notice-task-plan` 节点。
 *
 * <p>这一节点是审批通过后、自动派发前的最后一站：变更管理员要能<b>调整清单本身</b>
 * （补一条漏掉的、合并重复的、改责任人和计划），而不是只能点"同意"。
 *
 * <p>关键点：下游「变更任务自动分发」子流程的集合表达式读流程变量 {@code changeTaskList}，
 * 那是审批通过那一刻写下的快照。页面里增删改只写库，提交前必须调
 * {@code syncChangeNoticeDispatchList} 把同一份清单写回变量 —— 否则改了照样按旧清单派发。
 * 这一步放在 {@code collect()} 里（提交前、completeTask 之前）执行，见 TaskFormRenderer 的 await。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：variables.noticeOid */
  form: { type: Object, default: null }
})

const noticeOid = computed(() => String(props.form?.variables?.noticeOid || ''))
const summaryRef = ref(null)
const tasks = ref([])
/** 本通告的受影响对象（对象 → 事项的归属就记在 taskOid 上） */
const affected = ref([])
const loading = ref(false)
const loadError = ref('')
const comment = ref('')

const columns = [
  { key: 'task', title: '事项', dataIndex: 'name' },
  { key: 'type', title: '类型', dataIndex: 'taskType', width: 90 },
  { key: 'assignee', title: '责任人', dataIndex: 'assignee', width: 110 },
  { key: 'plan', title: '计划', dataIndex: 'planEnd', width: 170 },
  { key: 'objects', title: '变更对象', width: 200 },
  { key: 'status', title: '状态', dataIndex: 'status', width: 90 },
  { key: 'op', title: '操作', width: 110 }
]

const modal = reactive({
  visible: false,
  saving: false,
  collecting: false,
  /** 非空 = 编辑态，保留原 oid */
  editing: null,
  /** 收集来的候选：控件只认自己搜过的对象，收集结果必须回灌，否则显示成裸 oid */
  seedScope: [],
  /** 编辑态下这张任务原有的对象 oid —— 保存时用它算增/减 */
  baselineOids: [],
  form: emptyForm()
})

function emptyForm() {
  return {
    name: '',
    taskType: undefined,
    assignee: '',
    planStart: null,
    planEnd: null,
    description: '',
    affectedOids: []
  }
}

/** 这张任务负责的对象（受影响对象里 taskOid 指向它的那几条） */
function taskObjects(record) {
  return affected.value.filter((item) => item.taskOid && item.taskOid === record?.oid)
}

/**
 * 可选对象里要置灰的：已被<b>别的</b>任务归属的对象。
 *
 * <p>与创建通告时的口径一致（一个对象只归一张任务），但放开"本通告里还没归属的对象"：
 * 派发前正是要把漏派的、或从别的任务摘下来的对象补进来。
 */
const excludeOids = computed(() => {
  const editingOid = modal.editing?.oid || ''
  return affected.value
    .filter((item) => item.entityOid && item.taskOid && item.taskOid !== editingOid)
    .map((item) => item.entityOid)
})

/**
 * 清单以库里为准：页面不做本地影子副本，改完立刻重新拉一次。
 * 本地改一份、库里留一份，提交时谁覆盖谁就成了谜。
 */
async function load() {
  const oid = noticeOid.value
  if (!oid) {
    loadError.value = '取不到变更通告标识（流程变量 noticeOid 为空），无法加载更改事项'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await getChangeNotice(oid)
    if (res?.code === 200 && res.data) {
      tasks.value = res.data.tasks || []
      affected.value = res.data.affectedObjects || []
    } else {
      loadError.value = res?.message || '更改事项加载失败'
    }
  } catch {
    // 具体原因由响应拦截器提示
    loadError.value = '更改事项加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  modal.editing = null
  modal.seedScope = []
  modal.baselineOids = []
  modal.form = emptyForm()
  modal.visible = true
}

function openEdit(record) {
  modal.editing = record
  const own = taskObjects(record)
  modal.baselineOids = own.map((item) => item.entityOid).filter(Boolean)
  // 候选回填：控件只认 seedOptions 里的对象，不回灌就只剩一堆裸 oid
  modal.seedScope = own.map((item) => ({
    value: item.entityOid,
    entityType: item.entityType,
    entityCode: item.entityCode,
    entityName: item.entityName
  }))
  modal.form = {
    name: record.name || '',
    taskType: record.taskType || undefined,
    assignee: record.assignee || '',
    planStart: record.planStart || null,
    planEnd: record.planEnd || null,
    description: record.description || '',
    affectedOids: [...modal.baselineOids]
  }
  modal.visible = true
}

/** 对象 oid → 本通告受影响对象行（改派要的是受影响对象自身的 oid） */
function affectedOidOf(entityOid) {
  return affected.value.find((item) => item.entityOid === entityOid)?.oid || ''
}

/**
 * 让"任务负责哪些对象"落库 —— 先加入受影响对象（服务端按 oid 回查当前版本、幂等），
 * 再改派到这张任务；摘下来的对象只取消归属，仍留在本通告的执行清单里。
 *
 * <p>单条失败不打断其余对象：对象没版本 / 已执行 / 通告已过阶段都会挡，少挂一个比整单回滚好补救。
 */
async function saveScope(taskOid, entityOids, removedEntityOids) {
  const oid = noticeOid.value
  let attached = 0
  let failed = 0
  for (const entityOid of entityOids) {
    try {
      const res = await addChangeAffectedByEntity(oid, entityOid)
      const affectedOid = res?.code === 200 ? res.data?.oid : ''
      if (!affectedOid) {
        failed += 1
        continue
      }
      const assign = await assignChangeAffectedTask(oid, affectedOid, taskOid)
      if (assign?.code === 200) attached += 1
      else failed += 1
    } catch {
      failed += 1
    }
  }
  let detached = 0
  for (const entityOid of removedEntityOids) {
    const affectedOid = affectedOidOf(entityOid)
    if (!affectedOid) continue
    try {
      const res = await assignChangeAffectedTask(oid, affectedOid, null)
      if (res?.code === 200) detached += 1
      else failed += 1
    } catch {
      failed += 1
    }
  }
  return { attached, detached, failed }
}

/**
 * 收集所选对象关联的其他对象（零件 → 关联文档 / 工程数据 / 替代件），并入本任务的范围。
 *
 * <p>口径见 {@code ChangeRequestService#relatedObjects}：BOM 结构不收集。
 * 与创建通告时同一套逻辑 —— 收集只改表单取值，落库仍走保存时的"加入受影响对象 + 改派"。
 */
async function collectRelatedObjects() {
  const selected = Array.isArray(modal.form.affectedOids) ? [...modal.form.affectedOids] : []
  if (!selected.length) {
    message.info('先选一个零件，再收集它关联的文档 / 工程数据 / 替代件')
    return
  }
  modal.collecting = true
  try {
    const res = await listRelatedChangeObjects(selected)
    if (res?.code !== 200) {
      message.error(res?.message || '收集失败')
      return
    }
    // 已选 + 已被别的任务占用的都不再收：收进来也只会显示成置灰项
    const existing = new Set([...selected, ...excludeOids.value])
    const fresh = (res.data || []).filter((row) => row.oid && !existing.has(row.oid))
    if (!fresh.length) {
      message.info('没有新的关联对象可收集')
      return
    }
    const RELATION_LABEL = { DOCUMENT: '关联文档', ENG_DOCUMENT: '关联工程数据', ALTERNATE: '替代件' }
    modal.seedScope = [
      ...modal.seedScope,
      ...fresh.map((row) => ({
        value: row.oid,
        entityType: row.entityType,
        entityCode: row.code,
        entityName: `${row.name || ''}（${RELATION_LABEL[row.relation] || '关联对象'}）`.trim()
      }))
    ]
    modal.form.affectedOids = [...selected, ...fresh.map((row) => row.oid)]
    const docs = fresh.filter((row) => row.relation === 'DOCUMENT').length
    const engDocs = fresh.filter((row) => row.relation === 'ENG_DOCUMENT').length
    const alternates = fresh.filter((row) => row.relation === 'ALTERNATE').length
    message.success(
      `收集到 ${fresh.length} 个关联对象（关联文档 ${docs}、工程数据 ${engDocs}、替代件 ${alternates}）`
    )
  } catch {
    // 失败原因由响应拦截器提示
  } finally {
    modal.collecting = false
  }
}

async function saveTask() {
  const data = modal.form
  if (!data.name || !data.name.trim()) {
    message.warning('请填写事项标题')
    return
  }
  if (data.planStart && data.planEnd && data.planEnd < data.planStart) {
    message.warning('计划完成不能早于计划开始')
    return
  }
  modal.saving = true
  try {
    const payload = {
      noticeOid: noticeOid.value,
      name: data.name.trim(),
      taskType: data.taskType || null,
      assignee: data.assignee || '',
      planStart: data.planStart || null,
      planEnd: data.planEnd || null,
      description: data.description || ''
    }
    const res = modal.editing
      ? await updateChangeTask(modal.editing.oid, payload)
      : await createChangeTask(payload)
    if (res?.code !== 200) {
      message.error(res?.message || '保存失败')
      return
    }
    // 事项的范围：新建时全部是新挂的，编辑时按 baseline 算增/减
    const taskOid = modal.editing?.oid || res.data?.oid
    const selectedOids = data.affectedOids || []
    const baseline = modal.editing ? modal.baselineOids : []
    const added = selectedOids.filter((oid) => !baseline.includes(oid))
    const removed = baseline.filter((oid) => !selectedOids.includes(oid))
    if (taskOid && (added.length || removed.length)) {
      const scope = await saveScope(taskOid, added, removed)
      if (scope.failed) {
        message.warning(
          `${scope.failed} 个对象未能挂到事项上（对象没有版本，或已被其它变更单锁定）`
        )
      }
    }
    message.success(modal.editing ? '已更新更改事项' : '已新增更改事项')
    modal.visible = false
    await load()
    // 对象清单也变了（新增对象会进本通告的受影响对象），摘要卡要跟着刷
    summaryRef.value?.reload?.()
  } catch {
    // 失败原因由响应拦截器提示
  } finally {
    modal.saving = false
  }
}

/**
 * 删除更改事项：连同它名下的变更对象一起移出本通告。
 *
 * <p>先删对象再删任务：{@code removeAffected} 会按对象的 taskOid 反查任务是否已进入流程
 * （已进入就不许移除），任务先没了这条校验就失去意义，等于悄悄放过。
 *
 * <p>对象删不掉（已执行 / 被别的变更单锁住）也照样把事项删掉，只提示条数 ——
 * 留在单上的对象会变成"未归属"，比整条删不掉更容易补救（详情里能再改派）。
 */
async function removeTask(record) {
  const own = taskObjects(record)
  let removed = 0
  let failed = 0
  try {
    for (const item of own) {
      try {
        const res = await removeChangeAffectedObject(noticeOid.value, item.oid)
        if (res?.code === 200) removed += 1
        else failed += 1
      } catch {
        failed += 1
      }
    }
    const res = await deleteChangeTask(record.oid)
    if (res?.code !== 200) {
      message.error(res?.message || '删除失败')
      return
    }
    if (failed) {
      message.warning(`事项已删除，但有 ${failed} 个变更对象未能移出（可能已执行或被其它变更单锁定）`)
    } else if (removed) {
      message.success(`已删除事项，并移出 ${removed} 个变更对象`)
    } else {
      message.success('已删除')
    }
    await load()
    summaryRef.value?.reload?.()
  } catch {
    // 失败原因由响应拦截器提示
  }
}

/**
 * 提交前先同步派发清单，再让 TaskFormRenderer 去 completeTask。
 *
 * <p>返回 Promise：模板端（TaskFormRenderer.submit）已改成 await，同步失败就不提交，
 * 免得流程走了、变量还是旧的。
 */
async function collect() {
  if (modal.visible) {
    return { ok: false, message: '请先保存或关闭正在编辑的更改事项' }
  }
  if (loading.value) {
    return { ok: false, message: '更改事项还在加载，请稍候' }
  }
  if (!tasks.value.length) {
    return { ok: false, message: '至少保留一条更改事项，否则提交后不会派发任何任务' }
  }
  const unassigned = tasks.value.filter((t) => !t.assignee)
  if (unassigned.length) {
    return {
      ok: false,
      message: `还有 ${unassigned.length} 条事项未指派责任人：${unassigned.map((t) => t.code).join('、')}`
    }
  }
  // 没有对象的事项派发出去就是一张空任务，执行人打开后无事可做
  const emptyScope = tasks.value.filter((t) => !taskObjects(t).length)
  if (emptyScope.length) {
    return {
      ok: false,
      message: `还有 ${emptyScope.length} 条事项没选变更对象：${emptyScope.map((t) => t.code).join('、')}`
    }
  }
  try {
    const res = await syncChangeNoticeDispatchList(noticeOid.value)
    if (res?.code !== 200) {
      return { ok: false, message: res?.message || '派发清单同步失败，未提交' }
    }
  } catch {
    return { ok: false, message: '派发清单同步失败，未提交' }
  }
  return { ok: true, payload: { action: 'approve', comment: comment.value } }
}

const renderable = computed(() => true)
const submitText = computed(() => '确认并派发')
const successText = computed(() => '已确认，流程将为每条更改事项拉起变更任务')

onMounted(load)

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.etp-card {
  padding: 12px 14px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.etp-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.etp-title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.etp-muted {
  flex: 1;
  font-size: 12px;
  color: #8c8c8c;
}

.etp-code {
  color: #8c8c8c;
}

.etp-desc {
  margin-top: 2px;
  font-size: 12px;
  color: #8c8c8c;
}

.etp-plan {
  font-size: 12px;
  color: #595959;
}

.etp-warn {
  color: #d4380d;
}

.etp-alert {
  margin-bottom: 10px;
}

.etp-objects {
  font-size: 12px;
  color: #595959;
}

.etp-collect-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: -4px 0 10px;
}

.etp-desc-item {
  margin-top: 10px;
}

.etp-hint {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #8c8c8c;
}
</style>
