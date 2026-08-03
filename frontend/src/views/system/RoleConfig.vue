<template>
  <div class="role-config-page">
    <!-- 页头 -->
    <div class="rc-header">
      <div class="rc-header-left">
        <h3 class="rc-title">角色定义</h3>
        <span class="rc-subtitle">管理系统角色定义、编码与权限描述</span>
      </div>
    </div>

    <!-- 角色统计 -->
    <div class="rc-stats-bar">
      <div class="rc-stat-item">
        <SafetyOutlined class="rc-stat-icon" />
        <span class="rc-stat-value">{{ allRoles.length }}</span>
        <span class="rc-stat-label">角色总数</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="rc-stat-item">
        <a-tag color="red" size="small">平台级</a-tag>
        <span class="rc-stat-value">{{ platformCount }}</span>
      </div>
      <div class="rc-stat-item">
        <a-tag color="green" size="small">自定义</a-tag>
        <span class="rc-stat-value">{{ businessCount }}</span>
      </div>
    </div>

    <!-- 角色表格 -->
    <div class="rc-table-wrapper">
      <DataTable
        :columns="columns"
        :data-source="allRoles"
        :loading="loading"
        search-placeholder="搜索角色编码 / 名称 / 描述..."
        :search-fields="['code', 'name', 'description']"
        :enable-resize="true"
        :show-column-toggle="true"
        :max-height="480"
        row-key="oid"
        size="middle"
      >
        <template #toolbar>
          <a-button type="primary" size="small" @click="openModal(null)">
            <template #icon><PlusOutlined /></template>
            新增角色
          </a-button>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag v-if="record.roleType === 'PLATFORM'" color="red">{{ record.code }}</a-tag>
            <a-tag v-else color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'roleType'">
            <a-tag v-if="record.roleType === 'PLATFORM'" color="red">
              <SafetyOutlined :style="{ fontSize:'11px', marginRight:4 }" />平台级
            </a-tag>
            <a-tag v-else color="green">自定义</a-tag>
          </template>
          <template v-else-if="column.key === 'description'">
            <span class="rc-cell-text">{{ record.description || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space size="small">
              <a-tooltip v-if="record.roleType === 'PLATFORM'" title="平台级角色不可编辑">
                <a-button type="link" size="small" disabled>编辑</a-button>
              </a-tooltip>
              <a-button v-else type="link" size="small" @click="openModal(record)">编辑</a-button>
              <a-tooltip v-if="record.roleType === 'PLATFORM'" title="平台级角色不可删除">
                <a-button type="link" size="small" danger disabled>删除</a-button>
              </a-tooltip>
              <a-popconfirm
                v-else
                title="确定删除该角色？关联用户的角色将被移除。"
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
      :title="modal.isEdit ? '编辑角色' : '新增角色'"
      @ok="handleSave"
      :confirm-loading="modal.saving"
      width="520px"
      :mask-closable="false"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-form-item label="角色编码" required>
          <a-input
            v-model:value="modal.form.code"
            placeholder="如 DESIGNER、APPROVER"
            :disabled="modal.isEdit"
            size="large"
          />
        </a-form-item>
        <a-form-item label="角色名称" required>
          <a-input
            v-model:value="modal.form.name"
            placeholder="如 设计师、审批员"
            size="large"
          />
        </a-form-item>
        <a-form-item label="角色类型">
          <a-radio-group v-model:value="modal.form.roleType" :disabled="modal.isEdit">
            <a-radio value="BUSINESS">
              <a-tag color="green" size="small" style="margin-right:4px">自定义</a-tag>
              业务角色
            </a-radio>
          </a-radio-group>
          <div class="rc-form-hint">
            平台级角色需通过系统初始化导入，不可手动创建
          </div>
        </a-form-item>
        <a-form-item label="角色描述">
          <a-textarea
            v-model:value="modal.form.description"
            :rows="3"
            placeholder="该角色的权限范围与职责说明..."
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, SafetyOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { getRoles, createRole, updateRole, deleteRole } from '@/api'
import DataTable from '@/components/DataTable.vue'

// ==================== 表格列定义 ====================
const columns = [
  { title: '角色编码', dataIndex: 'code', key: 'code', width: 150 },
  { title: '角色名称', dataIndex: 'name', key: 'name', ellipsis: true, width: 160 },
  { title: '角色类型', key: 'roleType', width: 100 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '修改时间', key: 'updatedAt', width: 170 },
  { title: '操作', key: 'action', width: 170 },
]

// ==================== 数据 ====================
const loading = ref(false)
const allRoles = ref([])

const platformCount = computed(() =>
  allRoles.value.filter(r => r.roleType === 'PLATFORM').length
)
const businessCount = computed(() =>
  allRoles.value.filter(r => r.roleType !== 'PLATFORM').length
)

// ==================== 弹窗 ====================
const modal = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingOid: null,
  form: { code: '', name: '', roleType: 'BUSINESS', description: '' },
})

function openModal(role) {
  modal.editingOid = role?.oid || null
  modal.isEdit = !!role
  if (role) {
    modal.form = {
      code: role.code || '',
      name: role.name || '',
      roleType: role.roleType || 'BUSINESS',
      description: role.description || '',
    }
  } else {
    modal.form = { code: '', name: '', roleType: 'BUSINESS', description: '' }
  }
  modal.visible = true
}

// ==================== 操作 ====================
async function loadRoles() {
  loading.value = true
  try {
    const res = await getRoles()
    if (res.code === 200) {
      allRoles.value = res.data || []
    }
  } catch {
    message.error('加载角色列表失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  const { code, name } = modal.form
  if (!code?.trim() || !name?.trim()) {
    message.warning('角色编码和名称不能为空')
    return
  }
  modal.saving = true
  try {
    const payload = {
      code: code.trim(),
      name: name.trim(),
      roleType: modal.form.roleType || 'BUSINESS',
      description: modal.form.description || '',
    }
    let res
    if (modal.isEdit) {
      res = await updateRole(modal.editingOid, payload)
    } else {
      res = await createRole(payload)
    }
    if (res.code === 200) {
      message.success(modal.isEdit ? '角色更新成功' : '角色创建成功')
      modal.visible = false
      await loadRoles()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  } finally {
    modal.saving = false
  }
}

async function handleDelete(role) {
  try {
    const res = await deleteRole(role.oid)
    if (res.code === 200) {
      message.success('角色已删除')
      await loadRoles()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch {
    message.error('删除失败')
  }
}

function formatTime(str) {
  if (!str) return '-'
  return str.replace('T', ' ').substring(0, 19)
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadRoles()
})
</script>

<style scoped>
.role-config-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* ===== 页头 ===== */
.rc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}

.rc-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.rc-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.rc-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 统计栏 ===== */
.rc-stats-bar {
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

.rc-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.rc-stat-icon {
  font-size: 14px;
  color: #1677ff;
}

.rc-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.rc-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 表格容器 ===== */
.rc-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 表格内样式 */
.rc-cell-text {
  color: #595959;
}

/* ===== 弹窗 ===== */
.rc-form-hint {
  color: #999;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.5;
}
</style>
