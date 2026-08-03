<template>
  <div class="st-page">
    <div class="st-header">
      <div class="st-header-left">
        <h3 class="st-title">研发阶段模板</h3>
        <span class="st-subtitle">定义研发阶段元数据，创建产品线/型号时从此模板生成阶段实例</span>
      </div>
    </div>

    <div class="st-stats-bar">
      <div class="st-stat-item">
        <RocketOutlined class="st-stat-icon" />
        <span class="st-stat-value">{{ templates.length }}</span>
        <span class="st-stat-label">阶段总数</span>
      </div>
      <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="st-stat-hint">
        暂无研发阶段模板，请点击「克隆平台模板」快速初始化
      </div>
    </div>

    <div class="st-table-wrap">
      <DataTable
        :columns="columns"
        :data-source="templates"
        :loading="loading"
        search-placeholder="搜索阶段编码 / 名称..."
        :search-fields="['code', 'name']"
        :enable-resize="true"
        :show-column-toggle="true"
        :max-height="420"
        row-key="oid"
        size="middle"
      >
        <template #toolbar>
          <a-space>
            <a-button v-if="!isPlatformAdmin && !hasAnyTemplate" type="primary" size="small" @click="handleCloneFromPlatform" :loading="cloning">
              <template #icon><CopyOutlined /></template>
              克隆平台模板
            </a-button>
            <a-button type="primary" size="small" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新增阶段
            </a-button>
          </a-space>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'color'">
            <span class="st-color-dot" :style="{ background: record.color }" />
            <span>{{ record.color }}</span>
          </template>
          <template v-else-if="column.key === 'defaultFolders'">
            <a-tooltip :title="record.defaultFolders">
              <span class="st-folders-preview">{{ formatFolders(record.defaultFolders) }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space size="small">
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确定删除该阶段模板？" @confirm="handleDelete(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </DataTable>
    </div>

    <a-modal
      v-model:open="modal.visible"
      :title="modal.isEdit ? '编辑阶段模板' : '新增阶段模板'"
      @ok="handleSave"
      :confirm-loading="modal.saving"
      width="520px"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-form-item label="阶段编码" required>
          <a-input v-model:value="modal.form.code" placeholder="如 TRIAL" :disabled="modal.isEdit" />
        </a-form-item>
        <a-form-item label="阶段名称" required>
          <a-input v-model:value="modal.form.name" placeholder="如 试产" />
        </a-form-item>
        <a-form-item label="图标">
          <a-input v-model:value="modal.form.icon" placeholder="Ant Design 图标名，如 RocketOutlined" />
        </a-form-item>
        <a-form-item label="标识色">
          <a-input v-model:value="modal.form.color" placeholder="#52c41a">
            <template #addonBefore>
              <span class="st-color-dot" :style="{ background: modal.form.color || '#ccc' }" />
            </template>
          </a-input>
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="modal.form.sortOrder" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="默认文件夹（JSON数组）">
          <a-textarea v-model:value="modal.form.defaultFolders" :rows="3" placeholder='["文件夹1","文件夹2"]' />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="modal.form.description" :rows="2" placeholder="阶段描述" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, RocketOutlined, CopyOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'
import axios from 'axios'

const request = axios.create({ baseURL: '/api' })
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
request.interceptors.response.use(res => res.data)

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const templates = ref([])
const loading = ref(false)
const cloning = ref(false)

const hasAnyTemplate = computed(() => templates.value.length > 0)

const columns = [
  { title: '阶段编码', dataIndex: 'code', key: 'code', width: 160 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 100 },
  { title: '图标', dataIndex: 'icon', key: 'icon', width: 140 },
  { title: '标识色', key: 'color', width: 100 },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 60 },
  { title: '默认文件夹', key: 'defaultFolders', width: 160, ellipsis: true },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '操作', key: 'action', width: 140 }
]

function formatFolders(json) {
  if (!json) return '-'
  try {
    const arr = typeof json === 'string' ? JSON.parse(json) : json
    return arr.join(', ')
  } catch { return json }
}

async function loadTemplates() {
  loading.value = true
  try {
    const res = await request.get('/stage-templates')
    if (res.code === 200) templates.value = res.data || []
  } catch { message.error('加载失败') }
  finally { loading.value = false }
}

const modal = reactive({
  visible: false, isEdit: false, saving: false, editingOid: null,
  form: { code: '', name: '', icon: '', color: '', sortOrder: 1, defaultFolders: '', description: '' }
})

function openCreate() {
  modal.editingOid = null; modal.isEdit = false
  modal.form = { code: '', name: '', icon: '', color: '', sortOrder: 1, defaultFolders: '', description: '' }
  modal.visible = true
}

function openEdit(record) {
  modal.editingOid = record.oid; modal.isEdit = true
  modal.form = {
    code: record.code, name: record.name || '',
    icon: record.icon || '', color: record.color || '',
    sortOrder: record.sortOrder || 1,
    defaultFolders: typeof record.defaultFolders === 'string' ? record.defaultFolders : JSON.stringify(record.defaultFolders || []),
    description: record.description || ''
  }
  modal.visible = true
}

async function handleSave() {
  const { code, name } = modal.form
  if (!code?.trim()) { message.warning('编码不能为空'); return }
  if (!name?.trim()) { message.warning('名称不能为空'); return }
  modal.saving = true
  try {
    const payload = {
      code: code.trim(), name: name.trim(),
      icon: modal.form.icon, color: modal.form.color,
      sortOrder: modal.form.sortOrder,
      defaultFolders: modal.form.defaultFolders,
      description: modal.form.description
    }
    let res
    if (modal.isEdit) {
      res = await request.put(`/stage-templates/${modal.editingOid}`, payload)
    } else {
      res = await request.post('/stage-templates', payload)
    }
    if (res.code === 200) {
      message.success(modal.isEdit ? '更新成功' : '创建成功')
      modal.visible = false
      await loadTemplates()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch { message.error('操作失败') }
  finally { modal.saving = false }
}

async function handleDelete(record) {
  try {
    const res = await request.delete(`/stage-templates/${record.oid}`)
    if (res.code === 200) {
      message.success('已删除')
      await loadTemplates()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}

async function handleCloneFromPlatform() {
  cloning.value = true
  try {
    const res = await request.post('/stage-templates/clone-from-platform')
    if (res.code === 200) {
      const count = res.data || 0
      if (count > 0) {
        message.success(`成功克隆 ${count} 个平台模板到本租户，现在可以自由修改了`)
      } else {
        message.info('本租户已拥有全部平台模板，无需重复克隆')
      }
      await loadTemplates()
    } else {
      message.error(res.message || '克隆失败')
    }
  } catch { message.error('克隆失败') }
  finally { cloning.value = false }
}

onMounted(loadTemplates)
</script>

<style scoped>
.st-page { padding: 0; }
.st-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; }
.st-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.st-subtitle { font-size: 13px; color: #8c8c8c; }
.st-stats-bar { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; }
.st-stat-item { display: flex; align-items: center; gap: 6px; }
.st-stat-icon { font-size: 16px; color: #1677ff; }
.st-stat-value { font-size: 18px; font-weight: 600; }
.st-stat-label { font-size: 12px; color: #8c8c8c; }
.st-stat-hint { font-size: 12px; color: #fa8c16; }
.st-table-wrap { margin-top: 0; }
.st-color-dot { display: inline-block; width: 12px; height: 12px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
.st-folders-preview { color: #666; font-size: 12px; cursor: pointer; }
</style>
