<template>
  <div class="user-page">
    <!-- 页头 -->
    <div class="user-header">
      <div class="user-header-left">
        <h3 class="user-title">用户管理</h3>
        <span class="user-subtitle">管理本租户下的所有用户</span>
      </div>
    </div>

    <!-- 统计栏 -->
    <div class="user-stats-bar">
      <div class="user-stat-item">
        <TeamOutlined class="user-stat-icon" />
        <span class="user-stat-value">{{ users.length }}</span>
        <span class="user-stat-label">用户总数</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="user-stat-item">
        <span class="user-stat-value">{{ users.filter(u => u.enabled).length }}</span>
        <span class="user-stat-label">已启用</span>
      </div>
      <div class="user-stat-item">
        <span class="user-stat-value">{{ users.filter(u => u.locked).length }}</span>
        <span class="user-stat-label">已锁定</span>
      </div>
    </div>

    <!-- DataTable -->
    <DataTable
      ref="dataTableRef"
      :columns="columns"
      :data-source="users"
      :loading="loading"
      :pagination="false"
      row-key="oid"
      :show-view="false"
      :show-search="true"
      search-placeholder="搜索用户名、姓名、邮箱..."
      @search="handleSearch"
    >
      <template #toolbar-left>
        <span class="user-position-label">当前位置：</span>
        <a-breadcrumb>
          <a-breadcrumb-item>用户管理</a-breadcrumb-item>
        </a-breadcrumb>
      </template>
      <template #toolbar-right>
        <a-button type="primary" size="small" @click="openUserModal(null)">
          <template #icon><user-add-outlined /></template>
          添加用户
        </a-button>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">
          <a-tag :color="record.enabled ? 'green' : 'red'">
            {{ record.enabled ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'locked'">
          <a-tag :color="record.locked ? 'orange' : 'default'">
            {{ record.locked ? '已锁定' : '正常' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'orgName'">
          <span>{{ getOrgName(record.orgOid) }}</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space size="small">
            <a-button type="link" size="small" @click="openUserModal(record)">编辑</a-button>
            <a-dropdown>
              <a-button type="link" size="small">更多 <down-outlined /></a-button>
              <template #overlay>
                <a-menu @click="(e) => handleMenuAction(e, record)">
                  <a-menu-item key="toggleEnabled">
                    {{ record.enabled ? '禁用' : '启用' }}
                  </a-menu-item>
                  <a-menu-item key="toggleLocked">
                    {{ record.locked ? '解锁' : '锁定' }}
                  </a-menu-item>
                  <a-menu-item key="resetPwd">重置密码</a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="delete" danger>删除</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </template>
    </DataTable>

    <!-- 用户新增/编辑弹窗 -->
    <a-modal
      v-model:open="userModal.visible"
      :title="userModal.isEdit ? '编辑用户' : '添加用户'"
      @ok="handleUserSave"
      :confirm-loading="userModal.saving"
      width="480px"
    >
      <a-form :model="userModal.form" layout="vertical">
        <a-form-item label="用户名" required>
          <a-input
            v-model:value="userModal.form.username"
            placeholder="登录账户名"
            :disabled="userModal.isEdit"
            size="large"
          />
        </a-form-item>
        <a-form-item label="密码" :required="!userModal.isEdit">
          <a-input-password v-model:value="userModal.form.password" placeholder="登录密码" size="large" />
        </a-form-item>
        <a-form-item label="姓名">
          <a-input v-model:value="userModal.form.displayName" placeholder="用户姓名" size="large" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="userModal.form.email" placeholder="user@example.com" size="large" />
        </a-form-item>
        <a-form-item label="电话">
          <a-input v-model:value="userModal.form.phone" placeholder="手机号或座机" size="large" />
        </a-form-item>
        <a-form-item label="所属部门">
          <a-tree-select
            v-model:value="userModal.form.orgOid"
            :tree-data="orgTree"
            :field-names="{ children: 'children', label: 'name', value: 'oid' }"
            placeholder="请选择部门"
            tree-default-expand-all
            allow-clear
            style="width: 100%"
            size="large"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="userModal.form.enabled" checked-children="启用" un-checked-children="禁用" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  UserAddOutlined, DownOutlined, TeamOutlined
} from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import {
  getAllUsers, createUser, updateUser, deleteUser, resetPassword, getOrgTree
} from '@/api'

// ==================== 组织树（用于显示部门名） ====================
const orgTree = ref([])
const orgMap = ref({})

function buildOrgMap(nodes) {
  for (const n of nodes) {
    orgMap.value[n.oid] = n.name
    if (n.children?.length) buildOrgMap(n.children)
  }
}

function getOrgName(orgOid) {
  return orgMap.value[orgOid] || ''
}

// ==================== 用户列表 ====================
const dataTableRef = ref(null)
const users = ref([])
const loading = ref(false)

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username', ellipsis: true, width: 140 },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName', ellipsis: true, width: 120 },
  { title: '邮箱', dataIndex: 'email', key: 'email', ellipsis: true, width: 200 },
  { title: '电话', dataIndex: 'phone', key: 'phone', ellipsis: true, width: 130 },
  { title: '所属部门', key: 'orgName', width: 140 },
  { title: '状态', key: 'enabled', width: 70 },
  { title: '锁定', key: 'locked', width: 70 },
  { title: '操作', key: 'action', width: 160 }
]

async function loadUsers() {
  loading.value = true
  try {
    const params = {}
    if (dataTableRef.value?.searchKeyword?.trim()) {
      params.keyword = dataTableRef.value.searchKeyword.trim()
    }
    const res = await getAllUsers(params)
    if (res.code === 200) {
      users.value = res.data || []
    } else {
      message.error(res.message || '加载用户列表失败')
      users.value = []
    }
  } catch {
    message.error('加载用户列表失败')
    users.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(keyword) {
  loadUsers()
}

// ==================== 用户弹窗 ====================
const userModal = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingOid: null,
  form: {
    username: '', password: '', displayName: '',
    email: '', phone: '', orgOid: null, enabled: true
  }
})

function openUserModal(user) {
  userModal.editingOid = user?.oid || null
  userModal.isEdit = !!user
  if (user) {
    userModal.form = {
      username: user.username,
      password: '',
      displayName: user.displayName || '',
      email: user.email || '',
      phone: user.phone || '',
      orgOid: user.orgOid || null,
      enabled: user.enabled !== false
    }
  } else {
    userModal.form = {
      username: '', password: '', displayName: '',
      email: '', phone: '', orgOid: null, enabled: true
    }
  }
  userModal.visible = true
}

async function handleUserSave() {
  const { username, password } = userModal.form
  if (!username?.trim()) { message.warning('用户名不能为空'); return }
  if (!userModal.isEdit && !password?.trim()) { message.warning('密码不能为空'); return }
  userModal.saving = true
  try {
    const payload = {
      username: username.trim(),
      displayName: userModal.form.displayName || '',
      email: userModal.form.email || '',
      phone: userModal.form.phone || '',
      orgOid: userModal.form.orgOid || null,
      enabled: userModal.form.enabled
    }
    if (password?.trim()) payload.password = password.trim()

    let res
    if (userModal.isEdit) {
      const updatePayload = { ...payload }
      delete updatePayload.password
      delete updatePayload.username
      res = await updateUser(userModal.editingOid, updatePayload)
      if (password?.trim()) {
        await resetPassword(userModal.editingOid, password.trim())
      }
    } else {
      res = await createUser(payload)
    }
    if (res.code === 200) {
      message.success(userModal.isEdit ? '用户更新成功' : '用户创建成功')
      userModal.visible = false
      await loadUsers()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  } finally {
    userModal.saving = false
  }
}

// ==================== 更多菜单操作 ====================
async function handleMenuAction({ key }, record) {
  if (key === 'toggleEnabled') {
    try {
      const res = await updateUser(record.oid, { enabled: !record.enabled })
      if (res.code === 200) {
        message.success(record.enabled ? '用户已禁用' : '用户已启用')
        await loadUsers()
      } else {
        message.error(res.message || '操作失败')
      }
    } catch { message.error('操作失败') }
  } else if (key === 'toggleLocked') {
    try {
      const res = await updateUser(record.oid, { locked: !record.locked })
      if (res.code === 200) {
        message.success(record.locked ? '用户已解锁' : '用户已锁定')
        await loadUsers()
      } else {
        message.error(res.message || '操作失败')
      }
    } catch { message.error('操作失败') }
  } else if (key === 'resetPwd') {
    const pwd = prompt(`请输入「${record.displayName || record.username}」的新密码（至少4位）：`)
    if (!pwd || pwd.length < 4) {
      if (pwd !== null) message.warning('密码至少4位')
      return
    }
    try {
      const res = await resetPassword(record.oid, pwd)
      if (res.code === 200) {
        message.success('密码已重置')
      } else {
        message.error(res.message || '重置失败')
      }
    } catch { message.error('重置失败') }
  } else if (key === 'delete') {
    if (!confirm(`确定删除用户「${record.displayName || record.username}」？`)) return
    try {
      const res = await deleteUser(record.oid)
      if (res.code === 200) {
        message.success('用户已删除')
        await loadUsers()
      } else {
        message.error(res.message || '删除失败')
      }
    } catch { message.error('删除失败') }
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  try {
    const res = await getOrgTree()
    if (res.code === 200) {
      orgTree.value = res.data || []
      buildOrgMap(orgTree.value)
    }
  } catch { /* 组织树加载失败不影响主功能 */ }
  await loadUsers()
})
</script>

<style scoped>
.user-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.user-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}

.user-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.user-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.user-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

.user-stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  flex-shrink: 0;
  margin-bottom: 12px;
}

.user-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.user-stat-icon {
  font-size: 14px;
  color: #1677ff;
}

.user-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.user-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

.user-position-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-right: 4px;
  white-space: nowrap;
}
</style>
