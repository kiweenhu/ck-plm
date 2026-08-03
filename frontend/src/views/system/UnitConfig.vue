<template>
  <div class="unit-config-page">
    <!-- 页头 -->
    <div class="uc-header">
      <div class="uc-header-left">
        <h3 class="uc-title">单位配置</h3>
        <span class="uc-subtitle">管理计量单位体系、量纲类型与换算规则</span>
      </div>
    </div>

    <!-- 统计栏 -->
    <div class="uc-stats-bar">
      <div class="uc-stat-item">
        <ExperimentOutlined class="uc-stat-icon" />
        <span class="uc-stat-value">{{ allUnits.length }}</span>
        <span class="uc-stat-label">单位总数</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="uc-stat-item">
        <a-tag color="blue" size="small">SI</a-tag>
        <span class="uc-stat-value">{{ siCount }}</span>
      </div>
      <div class="uc-stat-item">
        <a-tag color="purple" size="small">量纲</a-tag>
        <span class="uc-stat-value">{{ qtyTypeCount }}</span>
      </div>
    </div>

    <!-- 表格 -->
    <div class="uc-table-wrapper">
      <DataTable
        :columns="columns"
        :data-source="allUnits"
        :loading="loading"
        search-placeholder="搜索单位名称 / 显示符号 / 量纲..."
        :search-fields="['name', 'display', 'quantityType']"
        :enable-resize="true"
        :show-column-toggle="true"
        :max-height="480"
        row-key="oid"
        size="middle"
      >
        <template #toolbar>
          <a-button type="primary" size="small" @click="openCreate">
            <template #icon><PlusOutlined /></template>
            新增单位
          </a-button>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <code>{{ record.name }}</code>
          </template>
          <template v-else-if="column.key === 'display'">
            <span>{{ record.display || record.name }}</span>
          </template>
          <template v-else-if="column.key === 'quantityType'">
            <a-tag v-if="record.quantityType" color="purple">{{ qtyTypeLabel(record.quantityType) }}</a-tag>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'isSI'">
            <a-tag v-if="record.isSI" color="blue">SI</a-tag>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'baseUnitName'">
            <a-tag v-if="record.baseUnitName === record.name" color="green">基准</a-tag>
            <span v-else-if="record.baseUnitName">{{ record.baseUnitName }}</span>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'factor'">
            <span v-if="record.factor !== null && record.factor !== 1">{{ record.factor }}</span>
            <span v-else style="color:#bfbfbf">1</span>
          </template>
          <template v-else-if="column.key === 'offset'">
            <span v-if="record.offset !== null && record.offset !== 0">{{ record.offset }}</span>
            <span v-else style="color:#bfbfbf">0</span>
          </template>
          <template v-else-if="column.key === 'formula'">
            <code v-if="record.baseUnitName && record.baseUnitName !== record.name" class="uc-formula">
              1{{ record.display || record.name }} = {{ record.factor || 1 }}{{ record.baseUnitName }}
              <span v-if="record.offset && record.offset !== 0"> + {{ record.offset }}</span>
            </code>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space size="small">
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确定删除此单位？" @confirm="handleDelete(record.oid)">
                <a-button type="link" size="small" danger>
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </DataTable>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑单位' : '新增单位'"
      @ok="handleSave"
      :confirm-loading="saving"
      width="560px"
      :mask-closable="false"
    >
      <a-form :model="form" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="单位名称" required>
              <a-input
                v-model:value="form.name"
                placeholder="程序内唯一标识，如 kg、ea"
                :disabled="isEdit"
                size="large"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="显示符号">
              <a-input
                v-model:value="form.display"
                placeholder="UI 展示，默认同 name"
                size="large"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="量纲类型">
              <a-select
                v-model:value="form.quantityType"
                placeholder="选择量纲"
                allow-clear
                size="large"
                @change="onQtyTypeChange"
              >
                <a-select-option v-for="qt in quantityTypes" :key="qt.name" :value="qt.name">
                  {{ qt.displayName }} ({{ qt.name }})
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="SI 标准单位">
              <a-switch v-model:checked="form.isSI" checked-children="是" un-checked-children="否" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="基准单位">
              <a-select
                v-model:value="form.baseUnitName"
                placeholder="选择基准"
                allow-clear
                show-search
                size="large"
              >
                <a-select-option v-for="u in sameQtyUnits" :key="u.name" :value="u.name">
                  {{ u.display || u.name }}
                  <a-tag v-if="u.isSI" color="blue" size="small" style="margin-left:4px">SI</a-tag>
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="换算系数">
              <a-input-number v-model:value="form.factor" :step="0.001" style="width:100%" placeholder="1.0" size="large" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="偏移量">
              <a-input-number v-model:value="form.offset" :step="0.01" style="width:100%" placeholder="0" size="large" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="排序权重">
              <a-input-number v-model:value="form.sortOrder" :min="0" style="width:100%" placeholder="0" size="large" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="备注">
              <a-input v-model:value="form.description" placeholder="可选说明" size="large" />
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 换算公式预览 -->
        <div v-if="form.baseUnitName && form.name && form.baseUnitName !== form.name" class="uc-formula-preview">
          换算公式：1 <code>{{ form.display || form.name }}</code> =
          <code>{{ form.factor || 1 }}</code> <code>{{ form.baseUnitName }}</code>
          <span v-if="form.offset && form.offset !== 0"> + <code>{{ form.offset }}</code></span>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ExperimentOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { getUnitListGrouped, getUnitQuantityTypes, createUnit, updateUnit, deleteUnit } from '@/api'
import DataTable from '@/components/DataTable.vue'

// ==================== 表格列定义 ====================
const columns = [
  { title: '名称', dataIndex: 'name', key: 'name', width: 110 },
  { title: '显示', key: 'display', width: 80 },
  { title: '量纲', key: 'quantityType', width: 110 },
  { title: 'SI', key: 'isSI', width: 55, align: 'center' },
  { title: '基准单位', key: 'baseUnitName', width: 100 },
  { title: '系数', key: 'factor', width: 70, align: 'right' },
  { title: '偏移', key: 'offset', width: 65, align: 'right' },
  { title: '换算公式', key: 'formula', ellipsis: true },
  { title: '操作', key: 'action', width: 160 },
]

// ==================== 数据 ====================
const loading = ref(false)
const saving = ref(false)
const allData = ref({})
const quantityTypes = ref([])
const modalVisible = ref(false)
const isEdit = ref(false)
const editOid = ref(null)

const form = reactive({
  name: '', display: '', quantityType: null, isSI: false,
  baseUnitName: null, factor: 1.0, offset: 0.0, sortOrder: 0, description: ''
})

// ==================== 计算属性 ====================
const allUnits = computed(() => {
  const result = []
  for (const units of Object.values(allData.value)) {
    if (units && units.length > 0) result.push(...units)
  }
  return result
})

const siCount = computed(() => allUnits.value.filter(u => u.isSI).length)

const qtyTypeCount = computed(() => {
  const types = new Set(allUnits.value.map(u => u.quantityType).filter(Boolean))
  return types.size
})

const sameQtyUnits = computed(() => {
  if (!form.quantityType) return []
  return allData.value[form.quantityType] || []
})

// ==================== 方法 ====================
function qtyTypeLabel(type) {
  const qt = quantityTypes.value.find(q => q.name === type)
  return qt?.displayName || type
}

async function loadData() {
  loading.value = true
  try {
    const [dataRes, qtRes] = await Promise.all([
      getUnitListGrouped(), getUnitQuantityTypes()
    ])
    allData.value = dataRes?.data || {}
    quantityTypes.value = qtRes?.data || []
  } catch { message.error('加载单位数据失败') }
  finally { loading.value = false }
}

function resetForm() {
  Object.assign(form, {
    name: '', display: '', quantityType: null, isSI: false,
    baseUnitName: null, factor: 1.0, offset: 0.0, sortOrder: 0, description: ''
  })
}

function onQtyTypeChange(value) {
  if (value) {
    const qt = quantityTypes.value.find(q => q.name === value)
    form.baseUnitName = qt?.baseUnitName || null
  } else {
    form.baseUnitName = null
  }
}

function openCreate() {
  isEdit.value = false; editOid.value = null
  resetForm()
  modalVisible.value = true
}

function openEdit(record) {
  isEdit.value = true; editOid.value = record.oid
  Object.assign(form, {
    name: record.name || '',
    display: record.display || '',
    quantityType: record.quantityType || null,
    isSI: record.isSI || false,
    baseUnitName: record.baseUnitName || null,
    factor: record.factor ?? 1.0,
    offset: record.offset ?? 0.0,
    sortOrder: record.sortOrder ?? 0,
    description: record.description || ''
  })
  modalVisible.value = true
}

async function handleSave() {
  if (!form.name?.trim()) { message.warning('请输入单位名称'); return }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      display: form.display || form.name.trim(),
      quantityType: form.quantityType,
      isSI: form.isSI,
      baseUnitName: form.baseUnitName || null,
      factor: form.factor ?? 1.0,
      offset: form.offset ?? 0.0,
      sortOrder: form.sortOrder ?? 0,
      description: form.description || null
    }
    if (isEdit.value) {
      await updateUnit(editOid.value, payload)
      message.success('单位已更新')
    } else {
      await createUnit(payload)
      message.success('单位已创建')
    }
    modalVisible.value = false
    loadData()
  } catch (e) {
    message.error(e?.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}

async function handleDelete(oid) {
  try {
    await deleteUnit(oid)
    message.success('已删除')
    loadData()
  } catch (e) {
    message.error(e?.response?.data?.message || '删除失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.unit-config-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* ===== 页头 ===== */
.uc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}

.uc-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.uc-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.uc-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 统计栏 ===== */
.uc-stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  flex-shrink: 0;
  margin-bottom: 6px;
}

.uc-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.uc-stat-icon {
  font-size: 14px;
  color: #1677ff;
}

.uc-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.uc-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 表格容器 ===== */
.uc-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 表格内样式 ===== */
.uc-formula {
  font-size: 11px;
}

/* ===== 弹窗 ===== */
.uc-formula-preview {
  background: #f6f8fa;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  color: #555;
}
</style>
