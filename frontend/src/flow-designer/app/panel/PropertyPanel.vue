<template>
  <div class="property-panel">
    <div class="panel-head">
      <a-tag :color="headColor">{{ headLabel }}</a-tag>
      <span class="panel-head__sub">{{ headSub }}</span>
    </div>

    <a-empty v-if="!target" description="选中节点、连线或模板以编辑属性" :image-style="{ height: '46px' }" />

    <template v-else>
      <div v-for="group in groups" :key="group.group || '__default'" class="panel-group">
        <div v-if="group.group" class="panel-group__title">{{ group.group }}</div>
        <FieldRenderer :fields="group.fields" :target="target" :context="ctx" @change="onFieldChange" />
      </div>

      <!-- 自定义变量（模板根字段 variables）：模板自己声明的变量，节点里用 ${变量名} 引用。
           与下面的「内置变量」用同一套行样式 —— 两份清单是一件事的两面（哪些要自己声明、
           哪些平台已经给了），长得不一样会让人以为是两类东西。
           新增/编辑走弹框：变量有七个字段，就地展开会把面板撑得很长，且一眼看不出"一共几个"。 -->
      <div v-if="mode === 'meta'" class="panel-group">
        <div class="panel-group__title">
          自定义变量
          <span class="panel-group__hint">模板自己声明，共 {{ declaredVariables.length }} 个</span>
        </div>

        <div v-if="!declaredVariables.length" class="var-row var-row--empty">
          尚未声明自定义变量
        </div>

        <div v-for="(v, index) in declaredVariables" :key="`${v.name}-${index}`" class="var-row">
          <div class="var-row__head">
            <span class="var-row__name">{{ v.name }}</span>
            <span class="var-row__type">{{ v.type }}</span>
            <span class="var-row__actions">
              <a-button size="small" type="text" @click="openVariableEditor(index)">编辑</a-button>
              <a-button size="small" type="text" danger @click="removeVariable(index)">删除</a-button>
            </span>
          </div>
          <div class="var-row__help">{{ variableSummary(v) }}</div>
        </div>

        <a-button size="small" type="dashed" block class="var-add" @click="openVariableEditor(-1)">
          添加变量
        </a-button>

        <!-- 添加 / 编辑弹框：字段声明复用 node-schemas 的 variableFields（与校验、DSL schema 同源），
             这里只负责"临时草稿 → 确认后整列表写回" -->
        <a-modal
          v-model:open="variableModal.open"
          :title="variableModal.index >= 0 ? '编辑变量' : '添加变量'"
          width="440px"
          :mask-closable="false"
          ok-text="确定"
          cancel-text="取消"
          @ok="applyVariable"
        >
          <FieldRenderer
            :fields="variableItemFields"
            :target="variableModal.draft"
            :context="ctx"
            @change="onVariableDraftChange"
          />
          <div v-if="draftNameIsBuiltin" class="var-modal__warn">
            「{{ variableModal.draft.name }}」是内置变量名：运行期平台写入的值会覆盖它，建议换个名字
          </div>
        </a-modal>
      </div>

      <!-- 内置变量：运行期平台自动注入的那批（发起写 initiator / 业务对象上下文，办理写 approved…）。
           必须与上面那张"自定义变量"清单并排可见 —— 只看上面那张，使用者会以为
           ${initiator} / ${approved} 这类变量不存在，于是把它们又声明一遍（声明了也不生效，
           平台值会覆盖），或者干脆不敢用条件分支。这里只读：它们不由模板决定，改也无处可改。 -->
      <div v-if="mode === 'meta'" class="panel-group">
        <div class="panel-group__title">
          内置变量
          <span class="panel-group__hint">运行期自动注入，无需声明</span>
        </div>
        <div class="var-row" v-for="v in BUILTIN_VARIABLES" :key="v.name">
          <div class="var-row__head">
            <span class="var-row__name">{{ v.display }}</span>
            <span class="var-row__type">{{ builtinVariableTypeLabel(v.type) }}</span>
            <span class="var-row__hint">{{ v.timing }}</span>
          </div>
          <div class="var-row__help">{{ v.help }}</div>
        </div>
        <div class="var-row__note">
          模板里可直接用 ${变量名} 引用；不要在上面「自定义变量」里声明同名变量 ——
          运行期平台写入的值会覆盖它。
        </div>
      </div>

      <!-- 当前对象的校验结果（与画布角标同源） -->
      <div v-if="issues.length" class="panel-issues">
        <div class="panel-group__title">本项问题</div>
        <div
          v-for="(issue, index) in issues"
          :key="index"
          class="panel-issue"
          :class="`is-${issue.level.toLowerCase()}`"
        >
          {{ issue.message }}
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
/**
 * 属性面板 —— 按声明式字段渲染「当前选中对象」的属性。
 *
 * <p>写入语义是<b>整对象替换</b>而非浅合并：字段可见性变化会删除嵌套字段
 * （如把审批模式从「会签」改成「单签」要删掉 passRule），而浅合并表达不了删除，
 * 会让 DSL 残留「看着配了、其实不该存在」的数据。
 */
import { computed, reactive } from 'vue'
import { message } from 'ant-design-vue'
import {
  BUILTIN_VARIABLES,
  NODE_TYPE_LABEL,
  builtinVariableTypeLabel,
  isBuiltinVariableName,
} from '@flow-dsl-core'
import FieldRenderer from './FieldRenderer.vue'
import { assignByPath, groupFields, pruneHidden, visibleFields } from './field-schema'
import { edgeFields, metaFields, nodeFields, variableFields } from './node-schemas'
import { applyVariableDraft, variableNameError, variableSummary } from './variable-list'

const props = defineProps({
  dsl: { type: Object, required: true },
  report: { type: Object, default: null },
  selection: { type: Object, default: () => ({ nodeIds: [], edgeIds: [] }) },
})

const emit = defineEmits(['patch-node', 'patch-edge', 'patch-meta', 'patch-variables'])

/** 编辑模式：节点优先于连线；都未选中即编辑模板 */
const mode = computed(() => {
  if (props.selection?.nodeIds?.length) return 'node'
  if (props.selection?.edgeIds?.length) return 'edge'
  return 'meta'
})

const selectedNode = computed(() =>
  mode.value === 'node' ? props.dsl.nodes.find((n) => n.id === props.selection.nodeIds[0]) : undefined,
)
const selectedEdge = computed(() =>
  mode.value === 'edge' ? props.dsl.edges.find((e) => e.id === props.selection.edgeIds[0]) : undefined,
)

const target = computed(() => {
  if (mode.value === 'node') return selectedNode.value
  if (mode.value === 'edge') return selectedEdge.value
  return props.dsl.meta
})

const ctx = computed(() => ({ dsl: props.dsl, target: target.value, kind: mode.value }))

const fields = computed(() => {
  if (mode.value === 'node') {
    return selectedNode.value ? nodeFields(selectedNode.value.type) : []
  }
  if (mode.value === 'edge') {
    return edgeFields()
  }
  return metaFields()
})

/** 只渲染可见字段非空的分组，避免出现空标题 */
const groups = computed(() => {
  if (!target.value) return []
  return groupFields(fields.value)
    .map((g) => ({ group: g.group, fields: visibleFields(g.fields, ctx.value) }))
    .filter((g) => g.fields.length > 0)
})

// ==================== 自定义变量（模板声明的 variables）====================

/** 模板自己声明的变量（DSL 根字段 variables） */
const declaredVariables = computed(() => props.dsl.variables ?? [])

/** 变量编辑弹框的字段声明：与校验、DSL schema 同一份（node-schemas.variableFields） */
const variableItemFields = variableFields()

/** 弹框草稿：`index = -1` 表示新增；确认前不动 DSL（取消即丢弃） */
const variableModal = reactive({ open: false, index: -1, draft: {} })

/** 草稿名与内置变量重名 —— 运行期会被平台值覆盖，提交前就提示 */
const draftNameIsBuiltin = computed(() =>
  isBuiltinVariableName(String(variableModal.draft?.name ?? '').trim()))

function openVariableEditor(index) {
  variableModal.index = index
  variableModal.draft = index >= 0
    ? { ...declaredVariables.value[index] }
    : { name: '', type: 'STRING' }
  variableModal.open = true
}

function onVariableDraftChange(path, value) {
  variableModal.draft = assignByPath({ ...variableModal.draft }, path, value)
}

function removeVariable(index) {
  emit('patch-variables', declaredVariables.value.filter((_, i) => i !== index))
}

/**
 * 确认添加 / 编辑。
 *
 * <p>校验挡在写回之前（逻辑在 variable-list.ts，可单测）：不通过就把弹框留着让人改 ——
 * 关掉它等于把刚填的内容一起丢掉。
 */
function applyVariable() {
  const error = variableNameError(
    String(variableModal.draft?.name ?? ''),
    declaredVariables.value,
    variableModal.index,
  )
  if (error) {
    message.warning(error)
    return
  }
  emit('patch-variables', applyVariableDraft(
    declaredVariables.value,
    variableModal.draft,
    variableModal.index,
  ))
  variableModal.open = false
}

const headLabel = computed(() => {
  if (mode.value === 'node') {
    return selectedNode.value ? NODE_TYPE_LABEL[selectedNode.value.type] ?? selectedNode.value.type : '节点'
  }
  if (mode.value === 'edge') return '连线'
  return '流程模板'
})

const headColor = computed(() => (mode.value === 'edge' ? 'blue' : mode.value === 'node' ? 'purple' : 'green'))

const headSub = computed(() => {
  if (mode.value === 'node') return selectedNode.value?.id ?? ''
  if (mode.value === 'edge') {
    return selectedEdge.value ? `${selectedEdge.value.source} → ${selectedEdge.value.target}` : ''
  }
  return props.dsl.meta.key
})

const issues = computed(() => {
  if (!props.report) return []
  if (mode.value === 'node' && selectedNode.value) {
    return props.report.byNode?.[selectedNode.value.id] ?? []
  }
  if (mode.value === 'edge' && selectedEdge.value) {
    return props.report.byEdge?.[selectedEdge.value.id] ?? []
  }
  return []
})

/** 字段改动：写值 → 清理失效字段 → 整对象抛给上层 */
function onFieldChange(path, value) {
  if (!target.value) return
  const written = assignByPath({ ...target.value }, path, value)
  const cleaned = pruneHidden(fields.value, ctx.value, written)
  if (mode.value === 'node') {
    emit('patch-node', selectedNode.value.id, cleaned)
  } else if (mode.value === 'edge') {
    emit('patch-edge', selectedEdge.value.id, cleaned)
  } else {
    emit('patch-meta', cleaned)
  }
}

</script>

<style scoped>
.property-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel-head__sub {
  font-size: 12px;
  color: #8c8c8c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.panel-group {
  border-top: 1px solid #f5f5f5;
  padding-top: 8px;
}
.panel-group__title {
  font-size: 12px;
  color: #8c8c8c;
  font-weight: 600;
  margin-bottom: 6px;
}
.panel-group__hint {
  font-weight: 400;
  color: #bfbfbf;
  margin-left: 6px;
}
/* 变量行样式：自定义变量与内置变量共用一套 —— 两份清单并排，长得不一样会让人以为是两类东西 */
.var-row {
  padding: 5px 0;
  border-bottom: 1px dashed #f0f0f0;
}
.var-row:last-of-type {
  border-bottom: none;
}
.var-row--empty {
  font-size: 12px;
  color: #bfbfbf;
}
.var-row__head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.var-row__name {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #262626;
}
.var-row__type,
.var-row__hint {
  font-size: 11px;
  color: #8c8c8c;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 3px;
  padding: 0 4px;
}
/* 编辑 / 删除推到行尾：行首留给"这个变量叫什么、什么类型" */
.var-row__actions {
  margin-left: auto;
  flex-shrink: 0;
}
.var-row__help {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.6;
  margin-top: 2px;
}
.var-row__note {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.6;
  margin-top: 6px;
  padding: 5px 8px;
  border-radius: 4px;
  background: #fafafa;
}
.var-add {
  margin-top: 6px;
}
.var-modal__warn {
  font-size: 12px;
  line-height: 1.6;
  margin-top: 8px;
  padding: 5px 8px;
  border-radius: 4px;
  background: #fffbe6;
  color: #d48806;
}
.panel-issues {
  border-top: 1px solid #f5f5f5;
  padding-top: 8px;
}
.panel-issue {
  font-size: 12px;
  line-height: 1.6;
  padding: 5px 8px;
  border-radius: 4px;
  margin-bottom: 5px;
  background: #fafafa;
}
.panel-issue.is-error {
  background: #fff1f0;
  color: #cf1322;
}
.panel-issue.is-warning {
  background: #fffbe6;
  color: #d48806;
}
</style>
