<template>
  <div class="version-rule-page">
    <!-- 页头 -->
    <div class="vr-header">
      <div class="vr-header-left">
        <h3 class="vr-title">版本规则</h3>
        <span class="vr-subtitle">管理业务对象版本编码模板与序号生成规则</span>
      </div>
    </div>

    <!-- 规则统计 -->
    <div class="vr-stats-bar">
      <div class="vr-stat-item">
        <NumberOutlined class="vr-stat-icon" />
        <span class="vr-stat-value">{{ allRules.length }}</span>
        <span class="vr-stat-label">规则总数</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="vr-stat-item">
        <CheckCircleOutlined class="vr-stat-icon" style="color:#52c41a" />
        <span class="vr-stat-value">{{ enabledCount }}</span>
        <span class="vr-stat-label">已启用</span>
      </div>
      <div class="vr-stat-item">
        <CloseCircleOutlined class="vr-stat-icon" style="color:#ff4d4f" />
        <span class="vr-stat-value">{{ disabledCount }}</span>
        <span class="vr-stat-label">已禁用</span>
      </div>
    </div>

    <!-- 规则表格 -->
    <div class="vr-table-wrapper">
      <DataTable
        :columns="columns"
        :data-source="allRules"
        :loading="loading"
        search-placeholder="搜索规则编码 / 名称 / 描述..."
        :search-fields="['code', 'name', 'description']"
        :enable-resize="true"
        :show-column-toggle="true"
        :max-height="420"
        row-key="oid"
        size="middle"
      >
        <template #toolbar>
          <a-button type="primary" size="small" @click="openModal(null)">
            <template #icon><PlusOutlined /></template>
            新增规则
          </a-button>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'ruleDefinition'">
            <code class="vr-rule-code">{{ record.ruleDefinition }}</code>
          </template>
          <template v-else-if="column.key === 'applicableType'">
            <a-tag v-if="record.applicableType" color="purple">{{ record.applicableType }}</a-tag>
            <span v-else class="vr-text-muted">通用</span>
          </template>
          <template v-else-if="column.key === 'sequenceValue'">
            <span class="vr-seq-value">{{ record.sequenceValue || 0 }}</span>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'success' : 'default'">
              {{ record.enabled ? '已启用' : '已禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'description'">
            <span class="vr-cell-text">{{ record.description || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'source'">
            <a-tag :color="isPlatformData(record) ? 'purple' : 'green'" size="small">
              {{ isPlatformData(record) ? '平台' : '本租户' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space size="small">
              <a-button type="link" size="small" @click="handleGenerate(record)" title="生成版本">
                <template #icon><PlayCircleOutlined /></template>
                生成
              </a-button>
              <a-button type="link" size="small" @click="openModal(record)" :disabled="!canEdit(record)">编辑</a-button>
              <a-popconfirm
                v-if="canEdit(record)"
                title="确定删除该版本规则？"
                @confirm="handleDelete(record)"
              >
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
      v-model:open="modal.visible"
      :title="modal.isEdit ? '编辑规则' : '新增规则'"
      @ok="handleSave"
      :confirm-loading="modal.saving"
      width="580px"
      :mask-closable="false"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-form-item label="规则编码" required>
          <a-input
            v-model:value="modal.form.code"
            placeholder="如 LETTER_8, DATE_SEQ"
            :disabled="modal.isEdit"
            size="large"
          />
        </a-form-item>
        <a-form-item label="规则名称" required>
          <a-input
            v-model:value="modal.form.name"
            placeholder="如 8位字母序列"
            size="large"
          />
        </a-form-item>
        <a-form-item label="规则定义" required>
          <a-input
            v-model:value="modal.form.ruleDefinition"
            placeholder="如 (A,B,C,D,E,F,G,H) 或 (PREFIX:DOC)-(SEQ:4)"
            size="large"
          />
          <div class="vr-form-hint">
            支持格式：(A,B,C) 字母序列 / (YYYYMMDD) 日期 / (SEQ:N) 序号 / (PREFIX:XXX) 前缀
          </div>
        </a-form-item>
        <a-form-item label="适用对象">
          <a-input
            v-model:value="modal.form.applicableType"
            placeholder="如 CK_DOCUMENT, CK_PRODUCT_MODEL（为空表示通用）"
            size="large"
          />
        </a-form-item>
        <a-form-item label="描述说明">
          <a-textarea
            v-model:value="modal.form.description"
            :rows="2"
            placeholder="规则的详细说明..."
          />
        </a-form-item>
        <a-form-item label="当前序号">
          <a-input-number
            v-model:value="modal.form.sequenceValue"
            :min="0"
            style="width: 200px"
            size="large"
          />
        </a-form-item>
        <a-form-item label="启用状态">
          <a-switch v-model:checked="modal.form.enabled" />
          <span style="margin-left: 12px">{{ modal.form.enabled ? '已启用' : '已禁用' }}</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 版本预览弹窗 -->
    <a-modal
      v-model:open="preview.visible"
      title="版本预览"
      :footer="null"
      width="400px"
    >
      <div class="vr-preview-box">
        <div class="vr-preview-label">生成的版本：</div>
        <div class="vr-preview-value">{{ preview.version }}</div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  NumberOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  PlayCircleOutlined
} from '@ant-design/icons-vue'
import { getVersionRules, createVersionRule, updateVersionRule, deleteVersionRule, generateNextVersion } from '@/api'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const PLATFORM_TENANT_OID = '00000000-0000-0000-0000-000000000000'
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))
const isPlatformData = (record) => record.tenantOid === PLATFORM_TENANT_OID
const canEdit = (record) => isPlatformAdmin.value || !isPlatformData(record)

// ==================== 表格列定义 ====================
const columns = [
  { title: '规则编码', dataIndex: 'code', key: 'code', width: 150 },
  { title: '规则名称', dataIndex: 'name', key: 'name', ellipsis: true, width: 150 },
  { title: '规则定义', dataIndex: 'ruleDefinition', key: 'ruleDefinition', width: 220 },
  { title: '适用对象', key: 'applicableType', width: 130 },
  { title: '当前序号', key: 'sequenceValue', width: 100, align: 'center' },
  { title: '来源', key: 'source', width: 70 },
  { title: '状态', key: 'enabled', width: 90, align: 'center' },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 180 },
]

// ==================== 数据 ====================
const loading = ref(false)
const allRules = ref([])

const enabledCount = computed(() => allRules.value.filter(r => r.enabled).length)
const disabledCount = computed(() => allRules.value.filter(r => !r.enabled).length)

// ==================== 弹窗 ====================
const modal = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingOid: null,
  form: {
    code: '',
    name: '',
    ruleDefinition: '',
    applicableType: '',
    description: '',
    sequenceValue: 0,
    enabled: true
  },
})

const preview = reactive({
  visible: false,
  version: '',
})

function openModal(rule) {
  modal.editingOid = rule?.oid || null
  modal.isEdit = !!rule
  if (rule) {
    modal.form = {
      code: rule.code || '',
      name: rule.name || '',
      ruleDefinition: rule.ruleDefinition || '',
      applicableType: rule.applicableType || '',
      description: rule.description || '',
      sequenceValue: rule.sequenceValue || 0,
      enabled: rule.enabled !== false,
    }
  } else {
    modal.form = {
      code: '',
      name: '',
      ruleDefinition: '',
      applicableType: '',
      description: '',
      sequenceValue: 0,
      enabled: true
    }
  }
  modal.visible = true
}

// ==================== 操作 ====================
async function loadRules() {
  loading.value = true
  try {
    const res = await getVersionRules()
    if (res.code === 200) {
      allRules.value = res.data || []
    }
  } catch {
    message.error('加载版本规则列表失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  const { code, name, ruleDefinition } = modal.form
  if (!code?.trim() || !name?.trim() || !ruleDefinition?.trim()) {
    message.warning('规则编码、名称和定义不能为空')
    return
  }
  modal.saving = true
  try {
    const payload = {
      code: code.trim(),
      name: name.trim(),
      ruleDefinition: ruleDefinition.trim(),
      applicableType: modal.form.applicableType?.trim() || null,
      description: modal.form.description?.trim() || '',
      sequenceValue: modal.form.sequenceValue || 0,
      enabled: modal.form.enabled,
    }
    let res
    if (modal.isEdit) {
      res = await updateVersionRule(modal.editingOid, payload)
    } else {
      res = await createVersionRule(payload)
    }
    if (res.code === 200) {
      message.success(modal.isEdit ? '规则更新成功' : '规则创建成功')
      modal.visible = false
      await loadRules()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  } finally {
    modal.saving = false
  }
}

async function handleDelete(rule) {
  try {
    const res = await deleteVersionRule(rule.oid)
    if (res.code === 200) {
      message.success('规则已删除')
      await loadRules()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch {
    message.error('删除失败')
  }
}

async function handleGenerate(rule) {
  try {
    const res = await generateNextVersion(rule.code)
    if (res.code === 200) {
      preview.version = res.data
      preview.visible = true
    } else {
      message.error(res.message || '生成版本失败')
    }
  } catch {
    message.error('生成版本失败')
  }
}

function formatTime(str) {
  if (!str) return '-'
  return str.replace('T', ' ').substring(0, 19)
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadRules()
})
</script>

<style scoped>
.version-rule-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* ===== 页头 ===== */
.vr-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}

.vr-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.vr-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.vr-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 统计栏 ===== */
.vr-stats-bar {
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

.vr-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.vr-stat-icon {
  font-size: 14px;
  color: #1677ff;
}

.vr-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.vr-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 表格容器 ===== */
.vr-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 表格内样式 ===== */
.vr-cell-text {
  color: #595959;
}

.vr-rule-code {
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  color: #d46b08;
}

.vr-seq-value {
  font-family: Consolas, Monaco, monospace;
  font-weight: 600;
  color: #1677ff;
}

.vr-text-muted {
  color: #bfbfbf;
}

/* ===== 弹窗 ===== */
.vr-form-hint {
  color: #999;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.5;
}

/* ===== 预览弹窗 ===== */
.vr-preview-box {
  text-align: center;
  padding: 16px;
}

.vr-preview-label {
  font-size: 13px;
  color: #8c8c8c;
  margin-bottom: 8px;
}

.vr-preview-value {
  font-size: 24px;
  font-weight: 700;
  font-family: Consolas, Monaco, monospace;
  color: #1677ff;
  padding: 12px 24px;
  background: #e6f4ff;
  border-radius: 8px;
  border: 1px solid #91caff;
}
</style>
