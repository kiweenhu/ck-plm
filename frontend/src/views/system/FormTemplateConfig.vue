<template>
  <div class="ftc">
    <div class="ftc-head">
      <div class="ftc-head__text">
        <h3 class="ftc-title">流程表单</h3>
        <p class="ftc-desc">
          流程节点办理时要填的表单。内置表单在应用启动时<b>自动登记</b>到表里（改不了编码、删不掉，可停用）；
          企业自己加的表单用自定义编码，建好后在流程设计器的节点属性「节点表单」里选中它。
          <b>同一种节点类型可以挂多张模板</b> —— 例如「审批」既可用内置的「审批意见」，也可用自定义的「技术评审单」。
        </p>
      </div>
      <div class="ftc-head__actions">
        <a-button size="small" @click="load" :loading="loading"><ReloadOutlined /> 刷新</a-button>
        <a-button type="primary" size="small" @click="openCreate"><PlusOutlined /> 新建表单</a-button>
      </div>
    </div>

    <!-- 同一节点类型挂了几张模板：这是"多模板"的直观入口 -->
    <div class="ftc-summary">
      <span class="ftc-summary__label">按节点类型</span>
      <a-tag v-for="item in typeSummary" :key="item.type" color="blue" class="ftc-summary__tag">
        {{ item.label }}：{{ item.count }} 张
      </a-tag>
      <span v-if="!typeSummary.length" class="ftc-summary__empty">暂无表单</span>
    </div>

    <div class="ftc-filter">
      <a-select
        v-model:value="nodeTypeFilter"
        size="small"
        style="width: 200px"
        :options="[{ value: '', label: '全部节点类型' }, ...nodeTypeOptions]"
      />
      <a-input v-model:value="keyword" size="small" placeholder="搜索名称 / 编码" allow-clear style="width: 200px" />
      <span class="ftc-filter__count">共 {{ filtered.length }} 张</span>
    </div>

    <a-table
      :columns="columns"
      :data-source="filtered"
      :loading="loading"
      row-key="oid"
      size="small"
      :pagination="false"
      :locale="{ emptyText: '暂无表单模板' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <div class="ftc-name">
            <span class="ftc-name__main">{{ record.name }}</span>
            <a-tag v-if="record.builtin" color="purple" class="ftc-name__tag">内置</a-tag>
          </div>
          <div class="ftc-name__desc">{{ record.description || '—' }}</div>
        </template>
        <template v-else-if="column.key === 'code'">
          <code class="ftc-code">{{ record.code }}</code>
        </template>
        <template v-else-if="column.key === 'nodeTypes'">
          <a-tag v-for="t in nodeTypesOf(record)" :key="t" class="ftc-type-tag">
            {{ formNodeTypeLabel(t) }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'enabled'">
          <a-switch
            :checked="record.enabled !== false"
            size="small"
            checked-children="启用"
            un-checked-children="停用"
            @change="(checked) => onToggleEnabled(record, checked)"
          />
        </template>
        <template v-else-if="column.key === 'sortOrder'">
          <span class="ftc-num">{{ record.sortOrder ?? 0 }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a class="ftc-link" @click="openEdit(record)">编辑</a>
          <!-- 内置表单不渲染删除：它由启动器登记，删了下次启动又回来 -->
          <a-popconfirm
            v-if="!record.builtin"
            title="删除这张表单？已引用它的流程节点会回落到内置默认表单"
            ok-text="删除"
            cancel-text="取消"
            @confirm="onDelete(record)"
          >
            <a class="ftc-link ftc-link--danger">删除</a>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? `编辑表单：${form.name}` : '新建流程表单'"
      :confirm-loading="saving"
      width="620px"
      @ok="onSubmit"
    >
      <a-form layout="vertical" class="ftc-form">
        <a-form-item label="表单名称" required>
          <a-input v-model:value="form.name" placeholder="如：技术评审单" :maxlength="128" />
        </a-form-item>

        <a-form-item label="表单编码" required>
          <a-input
            v-model:value="form.code"
            placeholder="如：TECH_REVIEW_FORM（字母/数字/下划线/中划线）"
            :disabled="editing"
          />
          <div class="ftc-form__hint">
            编码是节点引用它的唯一凭据（写进流程 DSL 的 formRef、编译为 BPMN 的 formKey）。
            <b>建好之后不要再改</b>：改了已引用它的流程就取不到表单了。
          </div>
        </a-form-item>

        <a-form-item label="适用节点类型" required>
          <a-select
            v-model:value="form.nodeTypes"
            mode="multiple"
            :disabled="editing && form.builtin"
            :options="nodeTypeOptions"
            placeholder="可以选多个：同一张表单允许挂到多种活动上"
          />
          <div class="ftc-form__hint">
            内置表单的适用范围由系统决定（运行期按它渲染），不可修改；自定义表单随意。
          </div>
        </a-form-item>

        <a-form-item label="说明">
          <a-textarea
            v-model:value="form.description"
            :rows="3"
            :maxlength="512"
            placeholder="这张表单用来做什么、什么情况下该选它"
          />
        </a-form-item>

        <a-form-item label="排序">
          <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" style="width: 140px" />
          <span class="ftc-form__hint" style="margin-left: 8px">同类型多张时决定下拉里的先后</span>
        </a-form-item>

        <a-form-item label="启用">
          <a-switch v-model:checked="form.enabled" />
          <span class="ftc-form__hint" style="margin-left: 8px">
            停用后设计器下拉里不再出现（已引用它的流程不受影响）
          </span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
/**
 * 业务配置 → 流程表单。
 *
 * <p>这张页面回答三个问题：
 * <ol>
 *   <li><b>系统里有哪些表单</b>：内置的在启动时自动登记（不再只存在于前端代码里），
 *       企业的自定义表单也在这里维护；</li>
 *   <li><b>每张表单能挂到哪种活动上</b>：同一节点类型可以有多张 —— 列表按节点类型统计，
 *       设计器下拉里按类型给出候选；</li>
 *   <li><b>怎么改</b>：内置的能停用、能改说明，但编码与适用范围不可改（那是运行期契约），
 *       也不能删（删了下次启动又回来，只会让人困惑）。</li>
 * </ol>
 *
 * <p>取数只有一份：后端的 {@code ck_process_form_template}。前端内置注册表
 * （{@code dsl-core/forms.ts}）只负责"用哪个 Vue 组件渲染"，不再重复维护一份清单。
 */
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import {
  createFormTemplate,
  deleteFormTemplate,
  listFormTemplates,
  updateFormTemplate,
} from '@/api'
import { FORM_NODE_TYPES, formNodeTypeLabel } from '@flow-dsl-core'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const keyword = ref('')
const nodeTypeFilter = ref('')

const nodeTypeOptions = FORM_NODE_TYPES.map((type) => ({
  value: type,
  label: formNodeTypeLabel(type),
}))

const columns = [
  { key: 'name', title: '表单', width: 260 },
  { key: 'code', title: '编码', width: 190 },
  { key: 'nodeTypes', title: '适用节点类型', width: 220 },
  { key: 'enabled', title: '状态', width: 90, align: 'center' },
  { key: 'sortOrder', title: '排序', width: 70, align: 'right' },
  { key: 'action', title: '操作', width: 110 },
]

/** nodeTypes 在表里是逗号分隔字符串（一种类型一张表单能挂多个活动） */
function nodeTypesOf(record) {
  return String(record?.nodeTypes || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter((item) => {
    if (nodeTypeFilter.value && !nodeTypesOf(item).includes(nodeTypeFilter.value)) {
      return false
    }
    if (!kw) {
      return true
    }
    return `${item.name || ''} ${item.code || ''}`.toLowerCase().includes(kw)
  })
})

/** 每个节点类型各挂了几张 —— "同一类型多种模板"在这里一眼可见 */
const typeSummary = computed(() =>
  nodeTypeOptions
    .map((option) => ({
      type: option.value,
      label: option.label,
      count: list.value.filter((item) => nodeTypesOf(item).includes(option.value)).length,
    }))
    .filter((item) => item.count > 0),
)

async function load() {
  loading.value = true
  try {
    const res = await listFormTemplates()
    list.value = res?.data || []
  } finally {
    loading.value = false
  }
}

// ==================== 新建 / 编辑 ====================

const modalOpen = ref(false)
const editing = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return {
    oid: '',
    code: '',
    name: '',
    nodeTypes: [],
    description: '',
    sortOrder: 100,
    enabled: true,
    builtin: false,
  }
}

function openCreate() {
  editing.value = false
  form.value = emptyForm()
  modalOpen.value = true
}

function openEdit(record) {
  editing.value = true
  form.value = {
    oid: record.oid,
    code: record.code,
    name: record.name,
    nodeTypes: nodeTypesOf(record),
    description: record.description || '',
    sortOrder: record.sortOrder ?? 100,
    enabled: record.enabled !== false,
    builtin: !!record.builtin,
  }
  modalOpen.value = true
}

async function onSubmit() {
  if (!form.value.name?.trim()) {
    message.warning('请填写表单名称')
    return
  }
  if (!editing.value && !form.value.code?.trim()) {
    message.warning('请填写表单编码')
    return
  }
  if (!form.value.nodeTypes?.length) {
    message.warning('请至少选择一个适用节点类型')
    return
  }
  const payload = {
    code: form.value.code.trim(),
    name: form.value.name.trim(),
    nodeTypes: form.value.nodeTypes.join(','),
    description: form.value.description,
    sortOrder: form.value.sortOrder,
    enabled: form.value.enabled,
  }
  saving.value = true
  try {
    const res = editing.value
      ? await updateFormTemplate(form.value.oid, payload)
      : await createFormTemplate(payload)
    // 失败原因由拦截器统一提示（后端把校验失败折成了 code≠200 的响应）
    if (res?.code === 200) {
      message.success(editing.value ? '已保存' : '已创建')
      modalOpen.value = false
      await load()
    }
  } finally {
    saving.value = false
  }
}

async function onToggleEnabled(record, checked) {
  const res = await updateFormTemplate(record.oid, {
    name: record.name,
    description: record.description,
    sortOrder: record.sortOrder,
    enabled: checked,
  })
  if (res?.code === 200) {
    record.enabled = checked
  }
}

async function onDelete(record) {
  const res = await deleteFormTemplate(record.oid)
  if (res?.code === 200) {
    message.success('已删除')
    await load()
  }
}

onMounted(load)
</script>

<style scoped>
.ftc-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.ftc-title {
  margin-bottom: 4px;
  font-size: 15px;
  font-weight: 600;
  color: #262626;
}

.ftc-desc {
  max-width: 900px;
  font-size: 12px;
  line-height: 1.7;
  color: #8c8c8c;
}

.ftc-head__actions {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.ftc-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.ftc-summary__label {
  font-size: 12px;
  color: #8c8c8c;
}

.ftc-summary__tag,
.ftc-type-tag {
  margin-inline-end: 0;
}

.ftc-summary__empty {
  font-size: 12px;
  color: #bfbfbf;
}

.ftc-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 8px;
}

.ftc-filter__count {
  font-size: 12px;
  color: #8c8c8c;
}

.ftc-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ftc-name__main {
  font-weight: 600;
  color: #262626;
}

.ftc-name__tag {
  margin-inline-end: 0;
}

.ftc-name__desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
  color: #8c8c8c;
}

.ftc-code {
  padding: 1px 6px;
  border-radius: 3px;
  background: #f5f5f5;
  font-family: monospace;
  font-size: 12px;
  color: #595959;
}

.ftc-num {
  font-variant-numeric: tabular-nums;
  color: #595959;
}

.ftc-link {
  margin-right: 12px;
}

.ftc-link--danger {
  color: #ff4d4f;
}

.ftc-form__hint {
  font-size: 12px;
  line-height: 1.6;
  color: #8c8c8c;
}
</style>
