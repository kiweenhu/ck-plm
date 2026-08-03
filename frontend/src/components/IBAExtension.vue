<template>
  <!-- IBA 扩展右侧抽屉 -->
  <a-drawer
    v-model:open="visible"
    title="IBA属性扩展"
    :width="640"
    placement="right"
    :mask-closable="true"
  >
    <div class="iba-drawer-toolbar">
      <a-space>
        <a-input-search v-model:value="search" placeholder="搜索IBA编码/名称..." size="small" style="width:240px" allow-clear />
        <a-select v-model:value="typeFilter" placeholder="数据类型" size="small" style="width:120px" allow-clear :options="typeFilterOptions" />
      </a-space>
      <a-space>
        <a-button size="small" type="primary" @click="openCreate">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
      </a-space>
    </div>

    <!-- 统计 -->
    <div class="iba-drawer-stats" v-if="defs.length > 0">
      <span class="iba-drawer-stats-text">共 {{ filteredDefs.length }} / {{ defs.length }} 项</span>
    </div>

    <!-- IBA 属性表格 -->
    <a-spin :spinning="loading">
      <a-table
        :columns="columns"
        :data-source="filteredDefs"
        :loading="loading"
        :pagination="{ pageSize: 20, showSizeChanger: false, showTotal: (t) => `共 ${t} 项` }"
        row-key="oid"
        size="small"
        :scroll="{ y: 'calc(100vh - 260px)' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <span class="iba-drawer-cell-name">{{ record.name }}</span>
          </template>
          <template v-if="column.key === 'code'">
            <code class="iba-drawer-cell-code">{{ record.code }}</code>
          </template>
          <template v-if="column.key === 'dataType'">
            <a-tag size="small">{{ record.dataType || 'STRING' }}</a-tag>
          </template>
          <template v-if="column.key === 'description'">
            <span class="iba-drawer-cell-desc">{{ record.description || '-' }}</span>
          </template>
          <template v-if="column.key === 'action'">
            <a-space size="small">
              <a-button size="small" type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确定删除该IBA属性？" @confirm="handleDelete(record)">
                <a-button size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-spin>

    <!-- 编辑弹窗 -->
    <a-modal v-model:open="editVisible" :title="editOid ? '编辑IBA属性' : '新建IBA属性'"
      :confirm-loading="editSaving" @ok="handleSave" width="520px" :mask-closable="false">
      <a-form :model="editForm" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="编码" required>
              <a-input v-model:value="editForm.code" placeholder="iba_xxx" size="large" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="editForm.name" placeholder="属性名称" size="large" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="数据类型">
          <a-select v-model:value="editForm.dataType" placeholder="选择数据类型" size="large" :options="dataTypeOptions" />
        </a-form-item>
        <a-form-item label="默认值">
          <a-input v-model:value="editForm.defaultValue" placeholder="默认值" size="large" />
        </a-form-item>
        <a-form-item label="是否必填">
          <a-switch v-model:checked="editForm.required" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="editForm.description" :rows="2" placeholder="属性说明..." size="large" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-drawer>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { getIBAList, createIBA, updateIBA, deleteIBA } from '@/api'

const visible = ref(false)
const search = ref('')
const typeFilter = ref(null)
const defs = ref([])
const loading = ref(false)
const editVisible = ref(false)
const editOid = ref(null)
const editSaving = ref(false)
const editForm = reactive({ code: '', name: '', dataType: 'STRING', required: false, defaultValue: '', description: '' })

const dataTypeOptions = [
  { value: 'STRING', label: '字符串' }, { value: 'INTEGER', label: '整数' },
  { value: 'DOUBLE', label: '浮点数' }, { value: 'BOOLEAN', label: '布尔值' },
  { value: 'DATE', label: '日期' }, { value: 'DATETIME', label: '日期时间' },
]
const typeFilterOptions = [
  { value: 'STRING', label: '字符串' }, { value: 'INTEGER', label: '整数' },
  { value: 'DOUBLE', label: '浮点数' }, { value: 'BOOLEAN', label: '布尔值' },
  { value: 'DATE', label: '日期' }, { value: 'DATETIME', label: '日期时间' },
]

const columns = [
  { title: '名称', key: 'name', dataIndex: 'name', ellipsis: true, width: 160 },
  { title: '编码', key: 'code', dataIndex: 'code', width: 160 },
  { title: '数据类型', key: 'dataType', dataIndex: 'dataType', width: 90 },
  { title: '描述', key: 'description', dataIndex: 'description', ellipsis: true },
  { title: '操作', key: 'action', width: 120 },
]

const filteredDefs = computed(() => {
  let list = defs.value
  const kw = search.value
  if (kw) {
    const lower = kw.toLowerCase()
    list = list.filter(d => (d.name || '').toLowerCase().includes(lower) || (d.code || '').toLowerCase().includes(lower))
  }
  if (typeFilter.value) {
    list = list.filter(d => d.dataType === typeFilter.value)
  }
  return list
})

async function loadDefs() {
  loading.value = true
  try {
    const r = await getIBAList()
    defs.value = r?.data || r || []
  } catch { defs.value = [] }
  finally { loading.value = false }
}

function open() {
  visible.value = true
  search.value = ''
  typeFilter.value = null
  loadDefs()
}

function openCreate() {
  editOid.value = null
  Object.assign(editForm, { code: '', name: '', dataType: 'STRING', required: false, defaultValue: '', description: '' })
  editVisible.value = true
  loadDefs()
}

function openEdit(record) {
  editOid.value = record.oid
  Object.assign(editForm, {
    code: record.code || '', name: record.name || '', dataType: record.dataType || 'STRING',
    required: record.required || false, defaultValue: record.defaultValue || '',
    description: record.description || ''
  })
  editVisible.value = true
}

async function handleSave() {
  if (!editForm.code || !editForm.name) { message.warning('请填写编码和名称'); return }
  editSaving.value = true
  try {
    if (editOid.value) { await updateIBA(editOid.value, { ...editForm }); message.success('IBA属性已更新') }
    else { await createIBA({ ...editForm }); message.success('IBA属性已创建') }
    editVisible.value = false; editOid.value = null
    Object.assign(editForm, { code: '', name: '', dataType: 'STRING', defaultValue: '', description: '' })
    await loadDefs()
  } catch (e) { message.error('保存失败') } finally { editSaving.value = false }
}

async function handleDelete(record) {
  try { await deleteIBA(record.oid); message.success('IBA属性已删除'); await loadDefs() }
  catch (e) { message.error('删除失败') }
}

defineExpose({ open, openEdit })
</script>

<style scoped>
.iba-drawer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.iba-drawer-stats {
  padding: 4px 0 8px;
  color: #8c8c8c;
  font-size: 12px;
}

.iba-drawer-cell-name { font-weight: 500; }
.iba-drawer-cell-code { font-size: 12px; color: #1677ff; }
.iba-drawer-cell-desc { font-size: 12px; color: #8c8c8c; }
</style>
