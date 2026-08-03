<template>
  <div class="platform-member-page">
    <!-- 页头 -->
    <div class="pm-header">
      <div class="pm-header-left">
        <h3 class="pm-title">平台成员管理</h3>
        <span class="pm-subtitle">管理平台级角色的成员分配</span>
      </div>
    </div>

    <!-- 角色选择 + 信息卡片 -->
    <div class="pm-role-bar">
      <div class="pm-role-select">
        <span class="pm-role-label">当前角色</span>
        <a-select
          v-model:value="selectedRoleOid"
          style="width: 280px"
          placeholder="选择平台级角色"
          :loading="rolesLoading"
          size="large"
          @change="onRoleChange"
        >
          <a-select-option v-for="r in platformRoles" :key="r.oid" :value="r.oid">
            <div class="pm-role-option">
              <a-tag color="red" size="small">平台级</a-tag>
              <span class="pm-role-option-name">{{ r.name }}</span>
              <span class="pm-role-option-code">{{ r.code }}</span>
            </div>
          </a-select-option>
        </a-select>
      </div>

      <div v-if="currentRole" class="pm-role-stats">
        <div class="pm-stat-item">
          <span class="pm-stat-value">{{ allMembers.length }}</span>
          <span class="pm-stat-label">成员数</span>
        </div>
        <div class="pm-stat-item">
          <span class="pm-stat-value">{{ currentRole.code }}</span>
          <span class="pm-stat-label">角色编码</span>
        </div>
      </div>
    </div>

    <!-- 角色描述 -->
    <div v-if="currentRole" class="pm-role-desc-card">
      <a-tag color="red">平台级角色</a-tag>
      <span class="pm-role-desc-text">{{ currentRole.description || '暂无角色描述' }}</span>
    </div>

    <!-- 未选择角色提示 -->
    <div v-if="!selectedRoleOid && !rolesLoading" class="pm-placeholder">
      <TeamOutlined :style="{ fontSize: '48px', color: '#d9d9d9' }" />
      <p>请选择一个平台级角色以查看其成员</p>
    </div>

    <!-- 成员表格 -->
    <div v-if="selectedRoleOid" class="pm-table-wrapper">
      <DataTable
        :key="selectedRoleOid"
        :columns="columns"
        :data-source="allMembers"
        :loading="membersLoading"
        :searchable="true"
        search-placeholder="搜索成员名称..."
        :search-fields="['username', 'displayName']"
        :show-column-toggle="true"
        :enable-resize="true"
        :max-height="400"
        pagination-mode="frontend"
        row-key="oid"
        size="middle"
      >
        <template #toolbar>
          <a-button type="primary" size="small" @click="openAddModal">
            <template #icon><user-add-outlined /></template>
            添加成员
          </a-button>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'username'">
            <a-avatar size="small" style="margin-right:8px;background:#1677ff;vertical-align:middle">
              {{ (record.displayName || record.username).charAt(0) }}
            </a-avatar>
            <span class="pm-username">{{ record.username }}</span>
            <a-tag v-if="record.enabled === false" color="default" style="margin-left:6px">已禁用</a-tag>
            <a-tag v-if="record.locked" color="error" style="margin-left:4px">已锁定</a-tag>
          </template>
          <template v-if="column.key === 'email'">
            <span class="pm-cell-text">{{ record.email || '-' }}</span>
          </template>
          <template v-if="column.key === 'phone'">
            <span class="pm-cell-text">{{ record.phone || '-' }}</span>
          </template>
          <template v-if="column.key === 'action'">
            <a-popconfirm
              title="确定将该成员移除此角色？"
              @confirm="handleRemoveMember(record)"
            >
              <a-button type="link" size="small" danger>
                <template #icon><CloseOutlined /></template>
                移除
              </a-button>
            </a-popconfirm>
          </template>
        </template>
      </DataTable>
    </div>

    <!-- 添加成员弹窗 -->
    <a-modal
      v-model:open="addModal.visible"
      title="添加成员"
      @ok="handleAddMember"
      :confirm-loading="addModal.saving"
      :ok-button-props="{ disabled: !selectedCandidate }"
      width="680px"
    >
      <div class="pm-modal-info">
        <span>将添加到角色：</span>
        <a-tag v-if="currentRole" color="red">{{ currentRole.name }}</a-tag>
      </div>

      <DataTable
        :columns="candidateColumns"
        :data-source="addModal.candidates"
        :loading="addModal.searching"
        :searchable="true"
        search-placeholder="搜索用户名或显示名称..."
        :search-fields="['username', 'displayName']"
        :show-column-toggle="false"
        :enable-resize="false"
        pagination-mode="frontend"
        row-key="oid"
        size="small"
        :row-selection="{ type: 'radio', selectedRowKeys: addModal.selectedKeys, onChange: onCandidateSelect }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'username'">
            <a-avatar size="small" :style="{ marginRight:'8px', background:'#1677ff', verticalAlign:'middle' }">
              {{ (record.displayName || record.username).charAt(0) }}
            </a-avatar>
            {{ record.username }}
            <a-tag v-if="record.enabled === false" color="default" size="small" :style="{ marginLeft:4 }">已禁用</a-tag>
            <a-tag v-if="record.locked" color="error" size="small" :style="{ marginLeft:2 }">已锁定</a-tag>
          </template>
          <template v-if="column.key === 'email'">
            {{ record.email || '-' }}
          </template>
        </template>
      </DataTable>

      <div v-if="addModal.candidates.length === 0 && !addModal.searching && addModal.searched" class="pm-empty-hint">
        <a-empty :image="aEmptyImage.simple" description="没有可添加的用户" />
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { Empty } from 'ant-design-vue'
import { UserAddOutlined, TeamOutlined, CloseOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import {
  getPlatformRoles, getRoleMembers, addRoleMember, removeRoleMember, getAllUsers
} from '@/api'

const aEmptyImage = Empty.PRESENTED_IMAGE_DEFAULT

// ==================== 表格列定义 ====================
const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username', width: 200 },
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName', ellipsis: true },
  { title: '邮箱', key: 'email', width: 240 },
  { title: '电话', key: 'phone', width: 150 },
  { title: '操作', key: 'action', width: 90 }
]

const candidateColumns = [
  { title: '用户名', dataIndex: 'username', key: 'username', width: 180 },
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName', ellipsis: true },
  { title: '邮箱', key: 'email', width: 220 }
]

// ==================== 平台角色 ====================
const rolesLoading = ref(false)
const platformRoles = ref([])
const selectedRoleOid = ref(null)

const currentRole = computed(() =>
  platformRoles.value.find(r => r.oid === selectedRoleOid.value) || null
)

async function loadPlatformRoles() {
  rolesLoading.value = true
  try {
    const res = await getPlatformRoles()
    if (res.code === 200) {
      platformRoles.value = res.data || []
      if (!selectedRoleOid.value && platformRoles.value.length > 0) {
        selectedRoleOid.value = platformRoles.value[0].oid
        await loadMembers()
      }
    }
  } catch {
    message.error('加载平台角色失败')
  } finally {
    rolesLoading.value = false
  }
}

// ==================== 成员列表 ====================
const membersLoading = ref(false)
const allMembers = ref([])

async function loadMembers() {
  if (!selectedRoleOid.value) {
    allMembers.value = []
    return
  }
  membersLoading.value = true
  try {
    const res = await getRoleMembers(selectedRoleOid.value)
    if (res.code === 200) {
      allMembers.value = res.data || []
    }
  } catch {
    message.error('加载成员列表失败')
  } finally {
    membersLoading.value = false
  }
}

async function handleRemoveMember(record) {
  try {
    const res = await removeRoleMember(selectedRoleOid.value, record.oid)
    if (res.code === 200) {
      message.success('已移除成员')
      await loadMembers()
    } else {
      message.error(res.message || '移除失败')
    }
  } catch {
    message.error('移除成员失败')
  }
}

function onRoleChange() {
  loadMembers()
}

// ==================== 添加成员弹窗 ====================
const addModal = reactive({
  visible: false,
  candidates: [],
  searching: false,
  saving: false,
  selectedKeys: [],
  searched: false
})

let selectedCandidate = null

function onCandidateSelect(keys, rows) {
  addModal.selectedKeys = keys
  selectedCandidate = rows.length > 0 ? rows[0] : null
}

function openAddModal() {
  addModal.visible = true
  addModal.candidates = []
  addModal.selectedKeys = []
  addModal.searched = false
  selectedCandidate = null
  searchUsers()
}

async function searchUsers() {
  addModal.searching = true
  addModal.searched = true
  try {
    const res = await getAllUsers({ keyword: '' })
    if (res.code === 200) {
      const all = res.data || []
      const memberOids = new Set(allMembers.value.map(m => m.oid))
      addModal.candidates = all.filter(u => !memberOids.has(u.oid))
    } else {
      addModal.candidates = []
    }
  } catch {
    message.error('搜索用户失败')
  } finally {
    addModal.searching = false
  }
}

async function handleAddMember() {
  if (!selectedCandidate) {
    message.warning('请选择一个用户')
    return
  }
  addModal.saving = true
  try {
    const res = await addRoleMember(selectedRoleOid.value, selectedCandidate.oid)
    if (res.code === 200) {
      message.success('已添加成员')
      addModal.visible = false
      await loadMembers()
    } else {
      message.error(res.message || '添加失败')
    }
  } catch {
    message.error('添加成员失败')
  } finally {
    addModal.saving = false
  }
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadPlatformRoles()
})
</script>

<style scoped>
.platform-member-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 页头 ===== */
.pm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 20px;
  flex-shrink: 0;
}

.pm-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.pm-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
}

.pm-subtitle {
  font-size: 13px;
  color: #8c8c8c;
}

/* ===== 角色选择栏 ===== */
.pm-role-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  flex-shrink: 0;
  margin-bottom: 12px;
}

.pm-role-select {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pm-role-label {
  font-weight: 600;
  color: #434343;
  font-size: 14px;
  flex-shrink: 0;
}

/* 角色选项 */
.pm-role-option {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.pm-role-option-name {
  font-weight: 500;
  flex-shrink: 0;
}

.pm-role-option-code {
  color: #999;
  font-size: 12px;
  margin-left: auto;
  flex-shrink: 0;
}

/* 成员统计 */
.pm-role-stats {
  display: flex;
  align-items: center;
  gap: 32px;
}

.pm-stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.pm-stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #1677ff;
  line-height: 1.2;
}

.pm-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 角色描述卡片 ===== */
.pm-role-desc-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  background: #fff7e6;
  border: 1px solid #ffe58f;
  border-radius: 6px;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.pm-role-desc-text {
  color: #8c6d16;
  font-size: 13px;
  line-height: 1.5;
}

/* ===== 未选择角色 ===== */
.pm-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #bfbfbf;
  font-size: 14px;
}

.pm-placeholder p {
  margin: 0;
}

/* ===== 表格容器 ===== */
.pm-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 表格内全局样式 */
.pm-username {
  font-weight: 500;
  vertical-align: middle;
}

.pm-cell-text {
  color: #595959;
}

/* ===== 弹窗 ===== */
.pm-modal-info {
  padding: 10px 0 16px;
  color: #595959;
  font-size: 14px;
}

/* 空提示 */
.pm-empty-hint {
  padding: 24px 0;
}
</style>
