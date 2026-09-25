<template>
  <div>
    <a-alert v-if="loadError" type="warning" show-icon class="saf-alert" :message="loadError" />
    <a-alert v-else-if="model.warnings.length" type="info" show-icon class="saf-alert"
      :message="model.warnings.join('；')" />

    <!-- 每个人的事就选几个人：审批（单签）与办理是单选，会签与多签审批才给多选。
         一律多选会在"一个人的决定"上留出"可以选好几个"的错觉 —— 见 assignee-setup 的 allowMultiple -->
    <div v-for="slot in model.slots" :key="slot.nodeId" class="saf-slot">
      <div class="saf-slot-head">
        <b>{{ slot.nodeName }}</b>
        <a-tag size="small" color="blue" style="margin-left:6px">{{ slot.kind }}</a-tag>
        <a-tag v-if="slot.allowMultiple && slot.minPeople <= 1" size="small" color="orange">可多选</a-tag>
        <span class="saf-hint">
          {{ slot.roleCodes.length ? `候选：角色 ${slot.roleCodes.join('、')} 的成员` : '候选：全部用户（该活动未指定角色）' }}
          <template v-if="slot.minPeople > 1">；至少 {{ slot.minPeople }} 人</template>
        </span>
      </div>
      <a-select
        :mode="slot.allowMultiple ? 'multiple' : 'default'"
        :value="slot.allowMultiple ? (values[slot.nodeId] || []) : (values[slot.nodeId] || [])[0]"
        show-search allow-clear
        style="width:100%" :loading="slotLoading[slot.nodeId]" :options="slotOptions[slot.nodeId] || []"
        :filter-option="filterOption"
        :placeholder="slot.allowMultiple ? '请选择人员（可多人）' : `请选择${slot.kind}`"
        @change="(picked) => onPickSlot(slot, picked)" />
    </div>

    <a-form layout="vertical" style="margin-top:12px">
      <a-form-item label="说明" :required="commentRequired">
        <a-textarea v-model:value="comment" :rows="3" placeholder="可填写说明..." />
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import {
  buildAssigneeSetupModel, toSetupVariables, validateAssigneeSetup,
} from '@/flow-designer/app/task/assignee-setup'
import { loadOptions, loadRoleMembers } from '@/flow-designer/app/panel/options'

/**
 * 「设置流程参与者」内置表单模板 —— 「设置审批人」活动固定使用（见 dsl-core/forms.ts）。
 *
 * <p>它做的是"列出下游所有人工活动，由发起人逐项指定人"。槽位与覆盖规则**完全复用**
 * 设计器那份唯一实现（dsl-core 的覆盖判定 + assignee-setup 的模型/校验/载荷）——
 * 运行期不另写一套，否则会出现"校验说没人要指定、表单却列出了活动"这类两处真相。
 *
 * <p>它只负责"渲染 + 收集值"（{@link collect}）；提交由渲染器统一做，
 * 这样所有模板的提交/失败/并发处理只有一份。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
})

const model = reactive({ slots: [], warnings: [] })
/** 槽位（活动 id）→ 已选人员 oid 列表 */
const values = reactive({})
/** 槽位（活动 id）→ 候选人员选项 / 加载态 */
const slotOptions = reactive({})
const slotLoading = reactive({})
const comment = ref('')
const loadError = ref('')

/** 该活动是否要求必须填说明（来自 DSL 节点声明） */
const commentRequired = computed(() => {
  const raw = props.form?.dslJson
  if (!raw) return false
  try {
    const dsl = JSON.parse(raw)
    const node = (dsl?.nodes || []).find(n => n.id === props.form?.taskDefinitionKey)
    return node?.commentRequired === true
  } catch {
    return false
  }
})

/** 下拉搜索：按显示名过滤（选项的 value 是人员 oid，按 value 搜对用户无意义） */
function filterOption(input, option) {
  return String(option?.label ?? '').toLowerCase().includes(String(input).toLowerCase())
}

/**
 * 选中处理。
 *
 * <p>表单值统一是<b>人员 oid 列表</b>（模型、校验、拼流程变量三处都按列表工作），
 * 单选只是"这个活动只能有一个人"的 UI 约束 —— 所以这里把单选结果包成单元素数组，
 * 而不是让值类型分叉（分叉了那三处都得各判一次，正是这类代码出 bug 的地方）。
 */
function onPickSlot(slot, picked) {
  values[slot.nodeId] = slot.allowMultiple ? (picked || []) : (picked ? [picked] : [])
}

async function load() {
  loadError.value = ''
  const raw = props.form?.dslJson
  if (!raw || !props.form?.taskDefinitionKey) {
    // 拿不到流程内容就如实说明（不静默给一个空表单），由渲染器决定是否兜底到通用表单
    loadError.value = '未取到该流程模板内容，本节点的表单无法渲染'
    return
  }
  let dsl
  try {
    dsl = JSON.parse(raw)
  } catch {
    loadError.value = '流程模板内容解析失败，本节点的表单无法渲染'
    return
  }
  const built = buildAssigneeSetupModel(dsl, props.form.taskDefinitionKey)
  model.slots = built.slots
  model.warnings = built.warnings
  for (const slot of model.slots) {
    values[slot.nodeId] = []
  }
  await Promise.all(model.slots.map(async (slot) => {
    slotLoading[slot.nodeId] = true
    try {
      // 候选范围 = 该活动指定的角色成员；活动没指定角色则放宽为全体用户（与表单上的说明一致）
      slotOptions[slot.nodeId] = slot.roleCodes.length
        ? await loadRoleMembers(slot.roleCodes)
        : await loadOptions('USERS')
    } catch {
      slotOptions[slot.nodeId] = []
    } finally {
      slotLoading[slot.nodeId] = false
    }
  }))
}

const submitText = computed(() => '确定')

/** 提交成功后的回执文案（结果页用） */
const successText = computed(() => '已指定下游参与人，流程继续流转')

/**
 * 本模板能不能渲染自己：槽位来自"该实例在跑那一版"的 DSL，老数据可能没有
 * （部署时未关联模板版本 → dslJson 为 null）。
 *
 * <p>渲染不了时由渲染器<b>兜底到通用表单</b>，而不是弹个错让人办不了事 ——
 * 办理本身不依赖 DSL，只有这个表单依赖。
 */
const renderable = computed(() => !loadError.value)

/** 收集提交载荷；不合规时返回原因（由渲染器统一提示） */
function collect() {
  if (loadError.value) {
    return { ok: false, message: loadError.value }
  }
  const issues = validateAssigneeSetup(model, values)
  if (issues.length > 0) {
    return { ok: false, message: issues[0].message }
  }
  if (commentRequired.value && !comment.value.trim()) {
    return { ok: false, message: '该活动要求填写说明' }
  }
  return { ok: true, payload: { variables: toSetupVariables(model, values), comment: comment.value } }
}

onMounted(load)
defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.saf-alert {
  margin-bottom: 12px;
}

/* 一个下游活动一块，块间留白，避免几个下拉挤成一堆 */
.saf-slot {
  padding: 10px 12px;
  margin-bottom: 10px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}

.saf-slot-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.saf-hint {
  font-size: 12px;
  color: #8c8c8c;
}
</style>
