<template>
  <div class="fsc-page">
    <!-- 存储概览卡片 -->
    <a-row :gutter="16" class="fsc-summary-row" v-if="filteredSummary.length > 0">
      <a-col :span="6" v-for="s in categorySummary" :key="s.categoryCode">
        <a-card :class="['fsc-summary-card', 'fsc-summary-' + s.categoryCode.toLowerCase()]" size="small" hoverable>
          <div class="fsc-summary-header">
            <component :is="categoryIcon(s.categoryCode)" class="fsc-summary-icon" />
            <span class="fsc-summary-title">{{ s.categoryName }}</span>
          </div>
          <div class="fsc-summary-body">
            <div class="fsc-summary-count">{{ s.fileCount }} <span class="fsc-summary-unit">个文件</span></div>
            <div class="fsc-summary-size">占用 {{ s.totalSizeDisplay }}</div>
            <div class="fsc-summary-free" v-if="s.freeDisplay && s.freeDisplay !== '—'">可用 {{ s.freeDisplay }}</div>
          </div>
          <a-progress v-if="s.usagePercent != null" :percent="s.usagePercent" :show-info="false" size="small" :stroke-color="usageColor(s.usagePercent)" />
          <div class="fsc-summary-footer">
            <a-tag color="default" size="small">{{ s.storageType }}</a-tag>
            <span class="fsc-summary-path" :title="s.storagePath">{{ s.storagePath || '-' }}</span>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6" v-if="totalSummary">
        <a-card class="fsc-summary-card fsc-summary-total" size="small">
          <div class="fsc-summary-header">
            <DatabaseOutlined class="fsc-summary-icon" style="color:#52c41a" />
            <span class="fsc-summary-title">{{ totalSummary.categoryName }}</span>
          </div>
          <div class="fsc-summary-body">
            <div class="fsc-summary-count">{{ totalSummary.fileCount }} <span class="fsc-summary-unit">个文件</span></div>
            <div class="fsc-summary-size">总占用 {{ totalSummary.totalSizeDisplay }}</div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 配置列表 -->
    <a-card :title="isPlatformAdmin ? '所有租户存储配置' : '文件存储配置'" class="fsc-table-card">
      <template #extra>
        <a-space>
          <a-tag v-if="!loading" color="default" size="small">{{ filteredConfigList.length }} 个类别</a-tag>
          <a-button size="small" @click="loadData" :loading="loading">刷新</a-button>
          <a-button type="primary" size="small" @click="openCreate">
            <PlusOutlined /> 新增配置
          </a-button>
        </a-space>
      </template>

      <!-- 租户筛选 -->
      <div class="fsc-filter-bar" v-if="isPlatformAdmin && tenantOptionsComputed.length > 1">
        <span class="fsc-filter-label">筛选租户：</span>
        <a-select
          v-model:value="selectedTenant"
          :options="tenantOptionsComputed"
          style="width: 360px"
          size="small"
          placeholder="全部租户"
          @change="onTenantChange"
        />
        <span class="fsc-filter-count" v-if="selectedTenant">{{ filteredConfigList.length }} / {{ configList.length }} 条</span>
      </div>

      <a-table
        :columns="columns"
        :data-source="filteredConfigList"
        :pagination="false"
        size="small"
        row-key="oid"
        :loading="loading"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'categoryCode'">
            <component :is="categoryIcon(record.categoryCode)" style="margin-right:4px;font-size:13px" />
            <a-tag color="blue" size="small">{{ record.categoryCode }}</a-tag>
          </template>
          <template v-else-if="column.key === 'storageType'">
            <a-tag :color="storageTypeColor(record.storageType)">{{ record.storageType }}</a-tag>
          </template>
          <template v-else-if="column.key === 'maxFileSizeMb'">
            {{ record.maxFileSizeMb }} MB
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'tenant'">
            <a-tag v-if="record.tenantOid === PLATFORM_TENANT_OID" color="purple" size="small">平台级</a-tag>
            <a-tag v-else color="green" size="small" :title="record.tenantOid">{{ getTenantLabel(record.tenantOid) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openEdit(record)"><EditOutlined /></a-button>
            <a-popconfirm title="确定删除？" @confirm="doDelete(record.oid)">
              <a-button type="link" size="small" danger><DeleteOutlined /></a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:visible="modalVisible"
      :title="editingId ? '编辑配置' : '新增配置'"
      :ok-text="editingId ? '保存' : '创建'"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="handleSave"
      width="580px"
    >
      <a-form :model="form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="类别编码" required>
              <a-input v-model:value="form.categoryCode" :disabled="!!editingId" placeholder="GALLERY / MAIN_DOC" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="类别名称" required>
              <a-input v-model:value="form.categoryName" placeholder="图册 / 主文档" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="存储类型">
              <a-select v-model:value="form.storageType" :options="storageTypeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="存储路径" required>
              <a-input v-model:value="form.storagePath" :placeholder="form.storageType === 'S3' ? 'bucket-name' : '/data/plm/files'" />
            </a-form-item>
          </a-col>
        </a-row>
        <template v-if="form.storageType === 'S3'">
          <a-divider orientation="left" style="font-size:12px;margin:4px 0">MinIO / S3 连接配置</a-divider>
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="服务端点" required>
                <a-input v-model:value="form.endpoint" placeholder="http://192.168.1.100:9000" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="桶名称">
                <a-input v-model:value="form.bucketName" placeholder="默认使用存储路径" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="Access Key" required>
                <a-input v-model:value="form.accessKey" placeholder="minioadmin" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="Secret Key" required>
                <a-input-password v-model:value="form.secretKey" placeholder="••••••••" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="前端访问 URL">
            <a-input v-model:value="form.baseUrl" placeholder="http://minio.example.com" />
          </a-form-item>
        </template>
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="单文件最大 (MB)">
              <a-input-number v-model:value="form.maxFileSizeMb" :min="1" :max="10000" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="总容量上限 (MB)">
              <a-input-number v-model:value="form.maxCapacityMb" :min="1" :max="999999" style="width:100%" placeholder="设置后可监控使用率" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="告警阈值 %">
              <a-input-number v-model:value="form.alertThresholdPercent" :min="1" :max="99" style="width:100%" placeholder="默认80" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="6">
            <a-form-item label="排序">
              <a-input-number v-model:value="form.sortOrder" :min="0" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="启用">
              <a-switch v-model:checked="form.enabled" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined, EditOutlined, DeleteOutlined, DatabaseOutlined,
  FileTextOutlined, PictureOutlined, PaperClipOutlined, ToolOutlined
} from '@ant-design/icons-vue'
import {
  getFileStorageConfigs, createFileStorageConfig, updateFileStorageConfig, deleteFileStorageConfig, getFileStorageSummary,
  getActiveTenants
} from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const PLATFORM_TENANT_OID = '00000000-0000-0000-0000-000000000000'
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const loading = ref(false)
const configList = ref([])
const summaryList = ref([])
const selectedTenant = ref(null)
const tenantNameMap = ref({}) // oid → 租户名称

// 从配置列表中提取租户选项（优先使用租户名称）
const tenantOptionsComputed = computed(() => {
  const seen = new Set()
  const options = [{ value: '', label: '全部租户' }]
  configList.value.forEach(c => {
    const oid = c.tenantOid || ''
    if (!seen.has(oid)) {
      seen.add(oid)
      const name = tenantNameMap.value[oid]
      options.push({
        value: oid,
        label: oid === PLATFORM_TENANT_OID
          ? '平台级（系统配置）'
          : name ? `${name} (${oid.substring(0, 8)}...)` : `租户 ${oid.substring(0, 8)}...`
      })
    }
  })
  return options
})

function getTenantLabel(oid) {
  if (oid === PLATFORM_TENANT_OID) return '平台级'
  const name = tenantNameMap.value[oid]
  return name || (oid ? oid.substring(0, 8) + '...' : '—')
}

// 按租户过滤配置列表
const filteredConfigList = computed(() => {
  if (!isPlatformAdmin.value || !selectedTenant.value) return configList.value
  const tenant = selectedTenant.value === PLATFORM_TENANT_OID ? PLATFORM_TENANT_OID : selectedTenant.value
  return configList.value.filter(c => c.tenantOid === tenant)
})

// 按租户过滤统计
const filteredSummary = computed(() => {
  if (!isPlatformAdmin.value || !selectedTenant.value) return summaryList.value
  const tenant = selectedTenant.value === PLATFORM_TENANT_OID ? PLATFORM_TENANT_OID : selectedTenant.value
  return summaryList.value.filter(s => {
    const matchingConfig = configList.value.find(c =>
      c.categoryCode === s.categoryCode && c.tenantOid === tenant
    )
    return !!matchingConfig
  })
})

const columns = computed(() => {
  const cols = [
    { title: '类别编码', key: 'categoryCode', width: 130 },
    { title: '类别名称', dataIndex: 'categoryName', key: 'categoryName', width: 70 },
    { title: '存储类型', dataIndex: 'storageType', key: 'storageType', width: 60 },
    { title: '存储路径', dataIndex: 'storagePath', key: 'storagePath', ellipsis: true, width: 180 },
    { title: '容量(MB)', dataIndex: 'maxCapacityMb', key: 'maxCapacityMb', width: 70 },
    { title: '阈值%', dataIndex: 'alertThresholdPercent', key: 'alertThresholdPercent', width: 50 },
    { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 45 },
    { title: '状态', key: 'enabled', width: 45 },
  ]
  if (isPlatformAdmin.value) {
    cols.splice(1, 0, { title: '所属租户', key: 'tenant', width: 160, ellipsis: true })
  }
  cols.push({ title: '操作', key: 'action', width: 70 })
  return cols
})

const storageTypeOptions = [
  { value: 'LOCAL', label: '本地存储' },
  { value: 'NAS', label: 'NAS网络存储' },
  { value: 'S3', label: 'S3对象存储' },
  { value: 'HDFS', label: 'HDFS分布式' }
]

function categoryIcon(code) {
  const map = { GALLERY: PictureOutlined, MAIN_DOC: FileTextOutlined, ATTACHMENT: PaperClipOutlined, CAD_MODEL: ToolOutlined }
  return map[code] || FileTextOutlined
}
function storageTypeColor(type) {
  const map = { LOCAL: 'blue', NAS: 'purple', S3: 'orange', HDFS: 'green' }
  return map[type] || 'default'
}

const categorySummary = computed(() => {
  const list = isPlatformAdmin.value ? filteredSummary.value : summaryList.value
  return list.filter(s => s.categoryCode !== 'TOTAL')
})
const totalSummary = computed(() => {
  const list = isPlatformAdmin.value ? filteredSummary.value : summaryList.value
  return list.find(s => s.categoryCode === 'TOTAL')
})

function usageColor(percent) {
  if (percent >= 90) return '#ff4d4f'
  if (percent >= 80) return '#fa8c16'
  return '#1677ff'
}

function onTenantChange() {
  // 切换租户时重新加载统计
  loadSummary()
}

// ========== 加载 ==========
async function loadData() {
  loading.value = true
  try {
    const promises = [getFileStorageConfigs(), getFileStorageSummary()]
    // 平台管理员额外加载租户名称
    if (isPlatformAdmin.value) {
      promises.push(getActiveTenants())
    }
    const results = await Promise.all(promises)
    if (results[0].code === 200) configList.value = results[0].data || []
    if (results[1].code === 200) summaryList.value = results[1].data || []
    // 构建租户名称映射
    if (isPlatformAdmin.value && results[2] && results[2].code === 200) {
      const tenants = results[2].data || []
      const map = {}
      tenants.forEach(t => { map[t.oid] = t.name || t.tenantId || '' })
      tenantNameMap.value = map
    }
  } catch { message.error('加载失败') }
  finally { loading.value = false }
}

async function loadSummary() {
  try {
    const sumRes = await getFileStorageSummary()
    if (sumRes.code === 200) summaryList.value = sumRes.data || []
  } catch { /* ignore */ }
}

// ========== 编辑弹窗 ==========
const modalVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const defaultForm = () => ({ categoryCode: '', categoryName: '', storageType: 'LOCAL', storagePath: '', maxFileSizeMb: 100, maxCapacityMb: null, alertThresholdPercent: 80, sortOrder: 0, enabled: true, description: '', endpoint: '', accessKey: '', secretKey: '', bucketName: '', baseUrl: '' })
const form = ref(defaultForm())

function openCreate() {
  editingId.value = null
  form.value = defaultForm()
  modalVisible.value = true
}
function openEdit(record) {
  editingId.value = record.oid
  form.value = { ...defaultForm(), ...record }
  if (record.storageType === 'S3' && record.secretKey) {
    form.value.secretKey = ''
  }
  modalVisible.value = true
}
async function handleSave() {
  if (!form.value.categoryCode || !form.value.categoryName || !form.value.storagePath) {
    return message.warning('请填写必填字段')
  }
  if (form.value.storageType === 'S3') {
    if (!form.value.endpoint) return message.warning('MinIO 服务端点不能为空')
    if (!form.value.accessKey) return message.warning('Access Key 不能为空')
    if (!editingId.value && !form.value.secretKey) return message.warning('Secret Key 不能为空')
  }
  saving.value = true
  try {
    const res = editingId.value
      ? await updateFileStorageConfig(editingId.value, form.value)
      : await createFileStorageConfig(form.value)
    if (res.code === 200) {
      message.success(editingId.value ? '已保存' : '已创建')
      modalVisible.value = false
      loadData()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch { message.error('操作失败') }
  finally { saving.value = false }
}
async function doDelete(oid) {
  try {
    const res = await deleteFileStorageConfig(oid)
    if (res.code === 200) {
      message.success('已删除')
      loadData()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}

onMounted(loadData)
</script>

<style scoped>
.fsc-page { max-width: 100%; }

.fsc-summary-row { margin-bottom: 16px; }
.fsc-summary-card { border-radius: 8px; height: 100%; }
.fsc-summary-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.fsc-summary-icon { font-size: 18px; }
.fsc-summary-title { font-size: 13px; font-weight: 600; color: #1a1a2e; }
.fsc-summary-body { margin: 6px 0 8px; }
.fsc-summary-count { font-size: 22px; font-weight: 700; color: #1a1a2e; }
.fsc-summary-unit { font-size: 12px; font-weight: 400; color: #8c8c8c; }
.fsc-summary-size { font-size: 12px; color: #8c8c8c; margin-top: 1px; }
.fsc-summary-free { font-size: 11px; color: #52c41a; margin-top: 2px; }
.fsc-summary-footer { display: flex; align-items: center; gap: 6px; margin-top: 8px; }
.fsc-summary-path { font-size: 10px; color: #bfbfbf; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 140px; }

.fsc-summary-gallery .fsc-summary-icon { color: #eb2f96; }
.fsc-summary-main_doc .fsc-summary-icon, .fsc-summary-cad_model .fsc-summary-icon { color: #1677ff; }
.fsc-summary-attachment .fsc-summary-icon { color: #fa8c16; }
.fsc-summary-total { background: #f6ffed; border-color: #b7eb8f; }

.fsc-table-card { border-radius: 8px; }

.fsc-filter-bar {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 12px; padding: 6px 12px;
  background: #fafafa; border-radius: 6px;
}
.fsc-filter-label { font-size: 12px; color: #666; white-space: nowrap; }
.fsc-filter-count { font-size: 11px; color: #8c8c8c; margin-left: 4px; }
</style>
