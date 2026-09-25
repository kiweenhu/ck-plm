<script setup>
/**
 * 「设置审批人」任务表单。
 *
 * <p>这是**产品内置的固定表单**：结构不由使用者自定义，而是列出流程里下游所有
 * 审批 / 会签 / 办理活动（由 {@link buildAssigneeSetupModel} 从流程定义得出），
 * 每项让发起人在**该活动指定的角色成员**中挑人。
 *
 * <p>设计器里用 `preview` 模式预览（同一组件、同一份派生逻辑，
 * 因此不存在「预览与运行不一致」）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { cachedOptions, loadOptions, loadRoleMembers } from '../panel/options'
import { buildAssigneeSetupModel, toSetupPayload, validateAssigneeSetup } from './assignee-setup'

const props = defineProps({
  /** 流程定义（表单结构由它派生） */
  dsl: { type: Object, required: true },
  /** 本「设置审批人」活动 id */
  setterNodeId: { type: String, required: true },
  /** 填写值：活动 id → 人员 oid 列表 */
  modelValue: { type: Object, default: () => ({}) },
  /** 预览模式：只读展示结构（设计器用） */
  preview: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const model = computed(() => buildAssigneeSetupModel(props.dsl, props.setterNodeId))
const values = computed(() => props.modelValue ?? {})
const issues = computed(() => validateAssigneeSetup(model.value, values.value))
const payload = computed(() => toSetupPayload(model.value, values.value))

const allUsers = ref(cachedOptions('USERS') ?? [])
/** 角色成员：角色 code 组合 → 选项（按需加载，不预取全部角色） */
const roleMembers = ref({})

/** 槽位的候选选项：有角色约束 → 该角色成员；否则退化为全体用户 */
function optionsOf(slot) {
  if (slot.roleCodes.length === 0) {
    return allUsers.value
  }
  return roleMembers.value[keyOf(slot.roleCodes)] ?? []
}

function keyOf(roleCodes) {
  return [...roleCodes].sort().join(',')
}

/** 按当前槽位（及其角色）加载候选：无效的接口会降级为空列表，不阻断表单结构展示 */
async function loadCandidates() {
  allUsers.value = await loadOptions('USERS')
  const keys = new Map()
  for (const slot of model.value.slots) {
    if (slot.roleCodes.length > 0) {
      keys.set(keyOf(slot.roleCodes), slot.roleCodes)
    }
  }
  for (const [key, roleCodes] of keys) {
    roleMembers.value = { ...roleMembers.value, [key]: await loadRoleMembers(roleCodes) }
  }
}

onMounted(loadCandidates)
// 流程改动可能带来新的槽位或新的角色 → 重新取候选（角色组合没变时走缓存，不重复请求）
watch(
  () => model.value.slots.map((slot) => keyOf(slot.roleCodes)).join('|'),
  () => {
    void loadCandidates()
  },
)

function issueOf(nodeId) {
  return issues.value.find((i) => i.nodeId === nodeId)?.message
}

function placeholderOf(slot) {
  const base = slot.roleCodes.length ? '从该角色成员中选择' : '选择人员'
  return slot.allowMultiple ? `${base}（可多人）` : base
}

/**
 * 选中结果统一成<b>人员 oid 列表</b>：模型与载荷层都按列表工作（见 assignee-setup），
 * 单选只是 UI 约束 —— 把单选包成单元素数组，值类型就不必分叉。
 */
function toList(slot, picked) {
  if (slot.allowMultiple) {
    return picked || []
  }
  return picked ? [picked] : []
}

function onPick(nodeId, picked) {
  if (props.preview) return
  emit('update:modelValue', { ...values.value, [nodeId]: picked })
}
</script>

<template>
  <div class="setup-form">
    <div class="setup-form__intro">
      请在下方为后续每个人工活动指定人员。表单随流程自动生成：后面的审批 / 会签 /
      办理活动都会出现在这里，每项的候选人限定为<strong>该活动指定的角色成员</strong>。
      未指定角色的活动，候选范围为全部用户。
    </div>

    <a-alert
      v-if="!model.slots.length"
      type="warning"
      show-icon
      message="流程里还没有需要指定人员的活动"
      description="本活动下游需有审批 / 会签 / 办理活动：把它们连在「设置审批人」之后，就会自动出现在这里。"
    />

    <div v-for="slot in model.slots" :key="slot.nodeId" class="setup-slot">
      <div class="setup-slot__head">
        <span class="setup-slot__name">{{ slot.nodeName }}</span>
        <a-tag color="blue">{{ slot.kind }}</a-tag>
        <a-tag v-if="slot.minPeople > 1" color="orange">至少 {{ slot.minPeople }} 人</a-tag>
        <a-tag v-else-if="slot.allowMultiple" color="orange">可多选</a-tag>
        <span v-if="slot.roleCodes.length" class="setup-slot__roles">
          角色：{{ slot.roleCodes.join('、') }}
        </span>
        <span v-else class="setup-slot__roles setup-slot__roles--none">
          未指定角色 · 候选为全部用户
        </span>
      </div>
      <!-- 单选 / 多选与运行期表单（components/task/taskForms/SetupAssigneeForm）同一口径：
           审批单签与办理单选，会签与多签审批多选。设计器里能选两个，运行期却不接受，是最坏的一种"不一致" -->
      <a-select
        :mode="slot.allowMultiple ? 'multiple' : 'default'"
        :value="slot.allowMultiple ? (values[slot.nodeId] || []) : (values[slot.nodeId] || [])[0]"
        :options="optionsOf(slot)"
        :disabled="preview"
        :status="issueOf(slot.nodeId) ? 'error' : undefined"
        :placeholder="placeholderOf(slot)"
        size="small"
        show-search
        option-filter-prop="label"
        @change="(picked) => onPick(slot.nodeId, toList(slot, picked))"
      />
      <div v-if="issueOf(slot.nodeId)" class="setup-slot__error">{{ issueOf(slot.nodeId) }}</div>
    </div>

    <div v-if="model.warnings.length" class="setup-form__warnings">
      <div v-for="(warn, index) in model.warnings" :key="index">• {{ warn }}</div>
    </div>

    <div v-if="!preview && model.slots.length" class="setup-form__footer">
      已指定 {{ payload.length }} / {{ model.slots.length }} 个活动
    </div>
  </div>
</template>

<style scoped>
.setup-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.setup-form__intro {
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.6;
}
.setup-slot {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 8px 10px;
  background: #fafafa;
}
.setup-slot__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}
.setup-slot__name {
  font-weight: 500;
}
.setup-slot__roles {
  color: #8c8c8c;
  font-size: 12px;
}
.setup-slot__roles--none {
  color: #d48806;
}
.setup-slot__error {
  color: #ff4d4f;
  font-size: 12px;
  margin-top: 4px;
}
.setup-form__warnings {
  color: #d48806;
  font-size: 12px;
  line-height: 1.6;
}
.setup-form__footer {
  color: #8c8c8c;
  font-size: 12px;
}
</style>
