<template>
  <div class="field-list">
    <template v-for="field in visibleList" :key="field.key">
      <!-- 只读引导（如「分支条件在连线上配」） -->
      <div v-if="field.control === 'hint'" class="field-hint">
        <span class="field-hint__title">{{ field.label }}</span>
        <span>{{ field.help }}</span>
      </div>

      <div v-else class="field-row" :class="{ 'field-row--inline': isInline(field) }">
        <label class="field-label">
          {{ field.label }}
          <span v-if="field.required" class="field-required">*</span>
        </label>

        <div class="field-control">
          <!-- 文本 -->
          <a-input
            v-if="field.control === 'text'"
            :value="asString(valueOf(field))"
            :disabled="field.disabled"
            :placeholder="field.placeholder"
            size="small"
            allow-clear
            @update:value="(v) => emit('change', field.key, v)"
          />

          <!-- 多行文本 -->
          <a-textarea
            v-else-if="field.control === 'textarea'"
            :value="asString(valueOf(field))"
            :disabled="field.disabled"
            :placeholder="field.placeholder"
            :rows="2"
            size="small"
            @update:value="(v) => emit('change', field.key, v)"
          />

          <!-- 密码 / 密钥：输入时遮蔽，不让连接凭据在面板上明文躺着 -->
          <a-input-password
            v-else-if="field.control === 'password'"
            :value="asString(valueOf(field))"
            :disabled="field.disabled"
            :placeholder="field.placeholder"
            size="small"
            allow-clear
            @update:value="(v) => emit('change', field.key, v)"
          />

          <!-- 数字 -->
          <a-input-number
            v-else-if="field.control === 'number'"
            :value="asNumber(valueOf(field))"
            :disabled="field.disabled"
            :min="field.min"
            :max="field.max"
            size="small"
            style="width: 100%"
            @update:value="(v) => emit('change', field.key, v)"
          />

          <!-- 开关 -->
          <a-switch
            v-else-if="field.control === 'switch'"
            :checked="!!valueOf(field)"
            :disabled="field.disabled"
            size="small"
            @update:checked="(v) => emit('change', field.key, v)"
          />

          <!-- 连线类型：做成按钮组而不是下拉 ——
               「默认分支」是分支节点必须配的一项，藏在下拉里就会出现
               「找不到设置默认分支的地方」（真实反馈） -->
          <a-radio-group
            v-else-if="field.control === 'edge-kind'"
            :value="asString(valueOf(field))"
            size="small"
            button-style="solid"
            @update:value="(v) => emit('change', field.key, v)"
          >
            <a-radio-button
              v-for="opt in edgeKindOptionsForField(field)"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </a-radio-button>
          </a-radio-group>

          <!-- 单选 / 多选下拉 -->
          <a-select
            v-else-if="field.control === 'select' || field.control === 'multi-select'"
            :value="field.control === 'multi-select' ? toStringArray(valueOf(field)) : asString(displayValueOf(field))"
            :mode="field.control === 'multi-select' ? 'multiple' : undefined"
            :disabled="field.disabled"
            :options="optionsFor(field)"
            :placeholder="field.placeholder || '请选择'"
            size="small"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 100%"
            @update:value="(v) => emit('change', field.key, v)"
          />

          <!-- 自有标签（自由输入 + 回车） -->
          <a-select
            v-else-if="field.control === 'tags'"
            :value="toStringArray(valueOf(field))"
            :disabled="field.disabled"
            mode="tags"
            :placeholder="field.placeholder || '输入后回车'"
            size="small"
            style="width: 100%"
            @update:value="(v) => emit('change', field.key, v)"
          />

          <!-- 审批人策略：递归渲染 assigneeFields -->
          <div v-else-if="field.control === 'assignee'" class="field-nested">
            <FieldRenderer
              :fields="assigneeFieldsOf(field)"
              :target="target"
              :context="context"
              @change="(path, v) => emit('change', path, v)"
            />
          </div>

          <!-- 服务选择：候选 = 前端声明的 + 后端已注册但没声明的（见 serviceOptionsForField） -->
          <a-select
            v-else-if="field.control === 'service-ref'"
            :value="asString(valueOf(field))"
            :disabled="field.disabled"
            :options="serviceOptionsForField(field)"
            placeholder="选择要执行的函数/服务"
            size="small"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 100%"
            @update:value="(v) => emit('change', field.key, v)"
          />

          <!-- 节点引用（驳回目标） -->
          <a-select
            v-else-if="field.control === 'node-ref'"
            :value="asString(valueOf(field))"
            :disabled="field.disabled"
            :options="nodeOptions"
            placeholder="选择目标节点"
            size="small"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 100%"
            @update:value="(v) => emit('change', field.key, v)"
          />

          <!-- 键值映射 -->
          <div v-else-if="field.control === 'key-value'" class="kv-editor">
            <div v-for="(entry, index) in keyValueEntries(field)" :key="index" class="kv-row">
              <a-input
                :value="entry.key"
                placeholder="参数名"
                size="small"
                @update:value="(v) => onKvChange(field, index, 'key', v)"
              />
              <a-input
                :value="entry.value"
                placeholder="取值"
                size="small"
                @update:value="(v) => onKvChange(field, index, 'value', v)"
              />
              <a-button size="small" type="text" danger @click="removeKv(field, index)">删</a-button>
            </div>
            <a-button size="small" type="dashed" block @click="addKv(field)">添加参数</a-button>
          </div>

          <!-- 「设置审批人」任务表单预览（结构由流程定义派生，非手工维护） -->
          <div v-else-if="field.control === 'assignee-setup-preview'" class="setup-preview">
            <a-button size="small" @click="showSetupPreview = true">
              预览任务表单（{{ setupSlotCount }} 项）
            </a-button>
            <a-modal
              v-model:open="showSetupPreview"
              title="设置审批人 · 任务表单预览"
              :footer="null"
              width="640px"
            >
              <SetAssigneeTaskForm :dsl="context.dsl" :setter-node-id="target.id" preview />
            </a-modal>
          </div>

          <!-- 可选子对象启用开关 -->
          <a-switch
            v-else-if="field.control === 'object-toggle'"
            :checked="hasValue(field)"
            size="small"
            @update:checked="(v) => onToggleObject(field, v)"
          />

          <!-- 对象数组（结束回调 / 流程变量） -->
          <div v-else-if="field.control === 'object-list'" class="object-list">
            <div v-for="(item, index) in listItems(field)" :key="index" class="object-item">
              <div class="object-item__head">
                <span>{{ itemTitle(field, item, index) }}</span>
                <a-button size="small" type="text" danger @click="removeItem(field, index)">删除</a-button>
              </div>
              <FieldRenderer
                :fields="field.itemFields || []"
                :target="item"
                :context="context"
                @change="(path, v) => onItemChange(field, index, path, v)"
              />
            </div>
            <a-button size="small" type="dashed" block @click="addItem(field)">添加一项</a-button>
          </div>

          <div v-if="field.help" class="field-help">{{ field.help }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
/**
 * 字段渲染器 —— 声明式字段的「执行端」。
 *
 * <p>只做三件事：按 `control` 分发控件、按 `visibleIf` 过滤、把改动以
 * `change(路径, 值)` 抛出（不直接改 DSL —— 写入与清理由 PropertyPanel 统一负责）。
 *
 * <p>`assignee` 与 `object-list` 通过<b>自引用递归</b>渲染子字段，
 * 因此「审批人策略」这一套字段只写了一次，却在发起人/审批人/办理人/通知人/
 * 逾期升级对象/子表单处全部复用。
 */
import { computed, ref, watchEffect } from 'vue'
import { BUILTIN_SERVICES, NODE_TYPE_LABEL } from '@flow-dsl-core'
import {
  assignByPath,
  getByPath,
  toStringArray,
  toKeyValueEntries,
  fromKeyValueEntries,
} from './field-schema'
import { assigneeFields, edgeKindOptionsWithCurrent } from './node-schemas'
import {
  cachedOptions,
  loadOptions,
  loadAutomationServices,
  mergeServiceOptions,
} from './options'
import { buildAssigneeSetupModel } from '../task/assignee-setup'
import SetAssigneeTaskForm from '../task/SetAssigneeTaskForm.vue'

/**
 * 后端已注册的服务（函数）清单。
 *
 * <p>跑一次即可（回调里没有响应式依赖 → watchEffect 只执行一遍）；失败降级为空数组：
 * 前端声明的服务照旧可选，不因为后端接口没通就让设计器用不了。
 */
const backendServices = ref([])
watchEffect(() => {
  loadAutomationServices().then((list) => {
    backendServices.value = list
  })
})

/**
 * 服务候选：前端声明的 + 后端已注册但没声明的。
 *
 * <p><b>追加而不是替换</b>：声明里带显示名与参数提示（面板靠它们渲染控件），
 * 后端清单只保证"有哪些已实现" —— 缺哪一份都会出事：少了声明，参数控件退化成手输；
 * 少了后端清单，新部署的函数在设计器里选不到。
 */
function serviceOptionsForField(field) {
  if (field.control !== 'service-ref') {
    return field.options || []
  }
  const options = mergeServiceOptions(field.options || [], backendServices.value)
  // 节点当前选的服务可能已经不在候选里（如「检出对象」已独立成节点库入口、不再下拉列出）：
  // 补一条占位项，否则下拉会把服务显示成裸 id —— 存量流程里这种节点不少
  const current = asString(valueOf(field))
  if (!current || options.some((option) => option.value === current)) {
    return options
  }
  const declared = BUILTIN_SERVICES.find((service) => service.id === current)
  return [{ value: current, label: declared?.label ?? current }, ...options]
}

const props = defineProps({
  fields: { type: Array, required: true },
  /** 被编辑对象（节点 / 连线 / 列表项） */
  target: { type: Object, required: true },
  /** SchemaContext：dsl + kind + target */
  context: { type: Object, required: true },
})

const emit = defineEmits(['change'])

/**
 * 连线类型的候选选项。
 *
 * <p>声明（`node-schemas`）是静态的，而候选取决于**流程结构** ——
 * 一个节点有多条出边时不能提供「普通流转」（无条件的边恒为真，会压死后面的分支），
 * 并行分支的出边则相反、只该有无条件。故在此按当前 DSL 计算。
 */
function edgeKindOptionsForField(field) {
  if (field.control !== 'edge-kind') {
    return field.options || []
  }
  return edgeKindOptionsWithCurrent(props.context.dsl, props.target)
}

/**
 * 「设置审批人」任务表单预览开关。
 *
 * <p>预览用的就是运行期那个组件、同一份派生逻辑（不是另画一个"示意图"），
 * 因此不可能出现「预览与运行不一致」。槽位数只为按钮文案，真实结构由组件内部派生。
 */
const showSetupPreview = ref(false)
const setupSlotCount = computed(
  () => buildAssigneeSetupModel(props.context.dsl, props.target.id).slots.length,
)

/** 异步选项缓存（按来源 id） */
const dynamicOptions = ref({})

watchEffect(() => {
  const sources = new Set()
  for (const field of props.fields) {
    if (field.optionsSource) sources.add(field.optionsSource)
  }
  for (const id of sources) {
    if (dynamicOptions.value[id]) continue
    const preset = cachedOptions(id)
    if (preset) {
      dynamicOptions.value = { ...dynamicOptions.value, [id]: preset }
      continue
    }
    loadOptions(id).then((items) => {
      dynamicOptions.value = { ...dynamicOptions.value, [id]: items }
    })
  }
})

/** 可见字段（每次渲染求值，保证切换策略/模式后立即重排） */
const visibleList = computed(() =>
  props.fields.filter((f) => (f.visibleIf ? f.visibleIf(props.target, props.context) : true)),
)

function valueOf(field) {
  return getByPath(props.target, field.key)
}

/**
 * 控件的显示值。
 *
 * <p>只读字段（如「设置审批人」的节点表单）若 DSL 里还没有值，显示该类型**固定的内置值** ——
 * 否则会呈现一个「灰掉的空框，谁也改不了」。显示值不落库：编译层按活动类型派生 formKey。
 */
function displayValueOf(field) {
  const value = valueOf(field)
  const empty = value === undefined || value === null || value === ''
  if (empty && field.disabled && field.defaultWhenEmpty) {
    return field.defaultWhenEmpty(props.context)
  }
  return value
}

function hasValue(field) {
  const value = valueOf(field)
  return !!value && typeof value === 'object'
}

/** 键值/数组类控件在窄面板里改行显示 */
function isInline(field) {
  return field.control === 'switch'
}

const asString = (v) => (v === undefined || v === null ? undefined : String(v))
const asNumber = (v) => (typeof v === 'number' ? v : undefined)

function optionsFor(field) {
  if (field.options) return field.options
  if (field.optionsFromDsl) return field.optionsFromDsl(props.context)
  if (field.optionsSource) return dynamicOptions.value[field.optionsSource] || []
  return []
}

/** 驳回目标候选：同模板内除自身外的所有节点 */
const nodeOptions = computed(() => {
  const current = props.context.kind === 'node' ? props.context.target?.id : undefined
  return (props.context.dsl?.nodes ?? [])
    .filter((n) => n.id !== current)
    .map((n) => ({ value: n.id, label: `${n.name}（${NODE_TYPE_LABEL[n.type] ?? n.type}）` }))
})

const assigneeFieldsOf = (field) => assigneeFields(field.key, field.group || '参与人')

/** object-toggle：勾选时写入模板对象，取消时整键删除（避免留下空壳绕过「未配置」判定） */
function onToggleObject(field, checked) {
  emit('change', field.key, checked ? (field.objectTemplate ? field.objectTemplate() : {}) : undefined)
}

// ==================== 键值映射 ====================

const keyValueEntries = (field) => toKeyValueEntries(valueOf(field))

function onKvChange(field, index, part, value) {
  const entries = [...keyValueEntries(field)]
  entries[index] = { ...entries[index], [part]: value }
  emit('change', field.key, fromKeyValueEntries(entries))
}

function addKv(field) {
  emit('change', field.key, fromKeyValueEntries([...keyValueEntries(field), { key: '', value: '' }]))
}

function removeKv(field, index) {
  const entries = keyValueEntries(field).filter((_, i) => i !== index)
  const next = fromKeyValueEntries(entries)
  emit('change', field.key, Object.keys(next).length ? next : undefined)
}

// ==================== 对象数组（递归编辑） ====================

const listItems = (field) => {
  const value = valueOf(field)
  return Array.isArray(value) ? value : []
}

function itemTitle(field, item, index) {
  const key = field.itemTitleKey
  const raw = key ? getByPath(item, key) : undefined
  if (raw) {
    const option = (field.itemFields || [])
      .find((f) => f.key === key)
      ?.options?.find((o) => o.value === raw)
    return option ? option.label : String(raw)
  }
  return `${field.label} ${index + 1}`
}

function onItemChange(field, index, path, value) {
  const items = [...listItems(field)]
  items[index] = assignByPath({ ...items[index] }, path, value)
  emit('change', field.key, items)
}

function addItem(field) {
  emit('change', field.key, [...listItems(field), field.newItem ? field.newItem() : {}])
}

function removeItem(field, index) {
  const items = listItems(field).filter((_, i) => i !== index)
  emit('change', field.key, items.length ? items : undefined)
}
</script>

<style scoped>
.field-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.field-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.field-row--inline {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.field-label {
  font-size: 12px;
  color: #595959;
  font-weight: 500;
}
.field-required {
  color: #ff4d4f;
  margin-left: 2px;
}
.field-control {
  min-width: 0;
}
.field-help {
  font-size: 11px;
  color: #bfbfbf;
  line-height: 1.5;
  margin-top: 2px;
}
.field-nested {
  padding: 8px;
  border-left: 2px solid #f0f0f0;
  background: #fafafa;
  border-radius: 4px;
}
.field-hint {
  font-size: 11px;
  line-height: 1.7;
  color: #8c8c8c;
  background: #fafafa;
  padding: 6px 8px;
  border-radius: 4px;
}
.field-hint__title {
  font-weight: 600;
  color: #595959;
  margin-right: 6px;
}
.kv-row {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
}
.object-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.object-item {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 8px;
  background: #fcfcfc;
}
.object-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #595959;
  margin-bottom: 6px;
}
</style>
