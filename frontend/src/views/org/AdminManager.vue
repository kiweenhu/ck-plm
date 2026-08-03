<template>
  <div class="am-page">
    <!-- 页头 -->
    <div class="am-header">
      <div class="am-header-left">
        <h3 class="am-title">角色成员</h3>
        <span class="am-subtitle">管理当前租户的管理员账号，管理员拥有租户内的最高权限</span>
      </div>
    </div>

    <!-- 统计栏 -->
    <div class="am-stats-bar">
      <div class="am-stat-item">
        <UserOutlined class="am-stat-icon" />
        <span class="am-stat-value">{{ adminMembers.length }}</span>
        <span class="am-stat-label">管理员人数</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="am-stat-item">
        <SafetyOutlined class="am-stat-icon" />
        <span class="am-stat-value">TENANT_ADMIN</span>
        <span class="am-stat-label">角色</span>
      </div>
    </div>

    <!-- 管理员表格 -->
    <div class="am-table-wrapper">
      <DataTable
        :columns="columns"
        :data-source="adminMembers"
        :loading="loading"
        search-placeholder="搜索用户名 / 显示名 / 邮箱..."
        :search-fields="['username', 'displayName', 'email']"
        :enable-resize="true"
        :show-column-toggle="true"
        :max-height="400"
        row-key="oid"
        size="middle"
      >
        <template #toolbar>
          <a-button type="primary" size="small" @click="showAddModal">
            <template #icon><PlusOutlined /></template>
            添加管理员
          </a-button>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'username'">
            <a-avatar :size="28" :style="{ backgroundColor: avatarColor(record.username), marginRight: 8 }">
              {{ (record.displayName || record.username || '?').charAt(0) }}
            </a-avatar>
            <span>{{ record.username }}</span>
          </template>
          <template v-else-if="column.key === 'email'">
            <span class="am-cell-text">{{ record.email || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-popconfirm
              title="确认移除该管理员？"
              ok-text="移除"
              cancel-text="取消"
              @confirm="handleRemove(record)"
            >
              <a-button type="link" size="small" danger>
                <template #icon><DeleteOutlined /></template>
                移除
              </a-button>
            </a-popconfirm>
          </template>
        </template>
      </DataTable>
    </div>

    <!-- 添加管理员弹窗 -->
    <a-modal
      v-model:open="addModalVisible"
      title="添加管理员"
      @ok="handleAdd"
      :confirm-loading="addLoading"
      width="480px"
      :mask-closable="false"
    >
      <a-form layout="vertical">
        <a-form-item label="选择用户" required>
          <a-select
            v-model:value="selectedUserOid"
            show-search
            placeholder="搜索并选择用户"
            :filter-option="false"
            :options="userOptions"
            @search="handleSearchUser"
            :loading="userSearchLoading"
            size="large"
            style="width: 100%"
          >
            <template #notFoundContent>
              <a-empty description="请输入关键字搜索用户" :image="false" />
            </template>
          </a-select>
          <div class="am-form-hint">
            仅显示当前租户下可分配为管理员的用户
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, UserOutlined, SafetyOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { getAdminMembers, addRoleMember, removeRoleMember, getAllUsers } from '@/api'
import DataTable from '@/components/DataTable.vue'

const loading = ref(false)
const addLoading = ref(false)
const adminMembers = ref([])
const roleOid = ref('')

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username', width: 200 },
  { title: '显示名', dataIndex: 'displayName', key: 'displayName', width: 160 },
  { title: '邮箱', key: 'email', width: 220, ellipsis: true },
  { title: '操作', key: 'action', width: 100 },
]

// ---- 添加管理员 ----
const addModalVisible = ref(false)
const selectedUserOid = ref(null)
const userOptions = ref([])
const userSearchLoading = ref(false)

function avatarColor(name) {
  const colors = ['#1677ff', '#52c41a', '#fa8c16', '#722ed1', '#eb2f96', '#13c2c2']
  let hash = 0
  for (let i = 0; i < (name || '').length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return colors[Math.abs(hash) % colors.length]
}

async function fetchAdminMembers() {
  loading.value = true
  try {
    const res = await getAdminMembers()
    if (res.code === 200 && res.data) {
      roleOid.value = res.data.roleOid || ''
      adminMembers.value = res.data.members || []
    } else {
      message.error(res.message || '获取管理员列表失败')
    }
  } catch { message.error('获取管理员列表失败') }
  finally { loading.value = false }
}

function showAddModal() {
  selectedUserOid.value = null
  userOptions.value = []
  addModalVisible.value = true
}

async function handleRemove(record) {
  try {
    const res = await removeRoleMember(roleOid.value, record.oid)
    if (res.code === 200) {
      message.success(`已移除管理员「${record.displayName || record.username}」`)
      await fetchAdminMembers()
    }
  } catch { /* handled */ }
}

async function handleSearchUser(keyword) {
  if (!keyword || keyword.trim().length < 1) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const res = await getAllUsers({ keyword: keyword.trim() })
    if (res.code === 200 && res.data) {
      const existingOids = new Set(adminMembers.value.map(m => m.oid))
      userOptions.value = res.data
        .filter(u => !existingOids.has(u.oid))
        .map(u => ({
          value: u.oid,
          label: `${u.displayName || u.username} (${u.username})`
        }))
    }
  } catch { userOptions.value = [] }
  finally { userSearchLoading.value = false }
}

async function handleAdd() {
  if (!selectedUserOid.value) return
  if (!roleOid.value) {
    message.error('未找到租户管理员角色，请联系平台管理员')
    return
  }
  addLoading.value = true
  try {
    const res = await addRoleMember(roleOid.value, selectedUserOid.value)
    if (res.code === 200) {
      message.success('管理员添加成功')
      addModalVisible.value = false
      selectedUserOid.value = null
      userOptions.value = []
      await fetchAdminMembers()
    } else {
      message.error(res.message || '添加失败')
    }
  } catch { message.error('添加失败') }
  finally { addLoading.value = false }
}

onMounted(fetchAdminMembers)
</script>

<style scoped>
.am-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* ===== 页头 ===== */
.am-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}

.am-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.am-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.am-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 统计栏 ===== */
.am-stats-bar {
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

.am-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.am-stat-icon {
  font-size: 14px;
  color: #1677ff;
}

.am-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.am-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 表格容器 ===== */
.am-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.am-cell-text {
  color: #595959;
}

/* ===== 弹窗 ===== */
.am-form-hint {
  color: #999;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.5;
}
</style>
