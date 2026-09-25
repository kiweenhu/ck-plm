<template>
  <div class="am-page">
    <!-- 页头 -->
    <div class="am-header">
      <div class="am-header-left">
        <h3 class="am-title">角色成员</h3>
        <span class="am-subtitle">按角色维护成员：选中角色后添加/移除，成员即获得该角色的权限</span>
      </div>
      <!-- 角色选择放在页头：本页所有增删都作用于选中的这个角色，先选角色才有下面的表 -->
      <a-select
        v-model:value="roleOid"
        class="am-role-select"
        :options="roleOptions"
        :loading="rolesLoading"
        show-search
        option-filter-prop="label"
        placeholder="选择角色"
        @change="onRoleChange"
      />
    </div>

    <!-- 统计栏 -->
    <div class="am-stats-bar">
      <div class="am-stat-item">
        <UserOutlined class="am-stat-icon" />
        <span class="am-stat-value">{{ members.length }}</span>
        <span class="am-stat-label">当前成员</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="am-stat-item">
        <SafetyOutlined class="am-stat-icon" />
        <span class="am-stat-value">{{ currentRole?.code || '—' }}</span>
        <span class="am-stat-label">{{ currentRole?.name || '未选择角色' }}</span>
      </div>
      <!-- 平台级角色牵一发动全身（如平台管理员可管所有租户），标出来免得当业务角色随手改 -->
      <a-tag v-if="currentRole" :color="currentRole.roleType === 'PLATFORM' ? 'purple' : 'blue'">
        {{ currentRole.roleType === 'PLATFORM' ? '平台级角色' : '业务角色' }}
      </a-tag>
    </div>

    <!-- 成员表格（当前选中角色的成员） -->
    <div class="am-table-wrapper">
      <DataTable
        :columns="columns"
        :data-source="members"
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
          <a-button type="primary" size="small" :disabled="!roleOid" @click="showAddModal">
            <template #icon><PlusOutlined /></template>
            添加成员
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
              :title="removeTitle(record)"
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

    <!-- 添加成员弹窗 -->
    <a-modal
      v-model:open="addModalVisible"
      :title="`添加成员到「${currentRole?.name || '该角色'}」`"
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
            placeholder="选择或搜索用户"
            :filter-option="false"
            :options="userOptions"
            @search="handleSearchUser"
            @dropdown-visible-change="onUserDropdownOpen"
            :loading="userSearchLoading"
            size="large"
            style="width: 100%"
          >
            <template #notFoundContent>
              <a-empty description="没有可添加的用户" :image="false" />
            </template>
          </a-select>
          <div class="am-form-hint">
            仅显示当前租户的用户；已是该角色成员的不再列出；可直接从名单里选，也可输入关键字搜索
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, UserOutlined, SafetyOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { getRoles, getRoleMembers, addRoleMember, removeRoleMember, getAllUsers } from '@/api'
import { visibleRoles } from '@/utils/roleVisibility'
import { useUserStore } from '@/stores/user'
import DataTable from '@/components/DataTable.vue'

/**
 * 角色成员 —— 选中一个角色，维护它的成员。
 *
 * <p><b>为什么从「企业管理员」改成本页</b>：以前这一页写死 TENANT_ADMIN（调 /roles/admin-members），
 * 想给别的角色分人就得去别的入口。角色本来就是权限的载体，分人这件事应当对<b>所有角色</b>一致 ——
 * 于是改成「先选角色，再维护成员」，接口也从专用端点换回通用的
 * {@code /roles/{oid}/members}（新增/移除本来就是通用的）。
 *
 * <p><b>能配哪些角色</b>：由 utils/roleVisibility 说了算 —— 租户管理员看不到「平台管理员」
 * （租户下不可能出现平台管理员，能选到就等于能把自己加进去、跨出租户）。
 * 平台级与业务级其余角色都列出来，用标签区分级别。
 */

/** 登录态：可见角色取决于当前登录人 */
const userStore = useUserStore()
// 直接刷新进本页时 store 可能还没水合，先从本地登录态恢复一次（内部只在空时填充）
userStore.init()

const loading = ref(false)
const rolesLoading = ref(false)
const addLoading = ref(false)
/** 接口原样返回的角色清单 */
const rawRoles = ref([])
/**
 * 本页可见 / 可维护的角色（租户管理员不含平台管理员，见 utils/roleVisibility）。
 * 下拉、统计、默认选中都基于它 —— 只藏 UI 不改数据源的话，
 * 「记住的上次选择」这类逻辑还会把隐藏角色捞回来。
 */
const roles = computed(() => visibleRoles(rawRoles.value, userStore.roles))
/** 当前选中的角色 oid —— 本页所有增删都作用于它 */
const roleOid = ref('')
/** 选中角色的成员 */
const members = ref([])

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username', width: 200 },
  { title: '显示名', dataIndex: 'displayName', key: 'displayName', width: 160 },
  { title: '邮箱', key: 'email', width: 220, ellipsis: true },
  { title: '操作', key: 'action', width: 100 },
]

const currentRole = computed(() => roles.value.find(r => r.oid === roleOid.value) || null)

/** 下拉选项「名称（编码）」：名称给人看，编码是配置/接口里用的，两个都要 */
const roleOptions = computed(() => roles.value.map(r => ({
  value: r.oid,
  label: r.name ? `${r.name}（${r.code}）` : r.code,
})))

/** 上次看过的角色；换浏览器/清缓存后回到默认 */
const ROLE_STORAGE_KEY = 'ckplm.role-members.roleOid'

/**
 * 默认角色：上次选过且还在 → 企业管理员(TENANT_ADMIN) → 第一个。
 * 落回 TENANT_ADMIN 是因为本页原先只维护它，进来先看到熟悉的那份名单，再换别的角色。
 */
function pickDefaultRole() {
  let remembered = ''
  try { remembered = localStorage.getItem(ROLE_STORAGE_KEY) || '' } catch { /* 隐私模式 */ }
  if (remembered && roles.value.some(r => r.oid === remembered)) return remembered
  const admin = roles.value.find(r => r.code === 'TENANT_ADMIN')
  return (admin || roles.value[0])?.oid || ''
}

async function fetchRoles() {
  rolesLoading.value = true
  try {
    const res = await getRoles()
    if (res.code === 200) {
      rawRoles.value = res.data || []
      roleOid.value = pickDefaultRole()
    } else {
      message.error(res.message || '获取角色列表失败')
    }
  } catch { message.error('获取角色列表失败') }
  finally { rolesLoading.value = false }
  await fetchMembers()
}

async function fetchMembers() {
  if (!roleOid.value) {
    members.value = []
    return
  }
  loading.value = true
  try {
    const res = await getRoleMembers(roleOid.value)
    if (res.code === 200) {
      members.value = res.data || []
    } else {
      message.error(res.message || '获取成员列表失败')
      members.value = []
    }
  } catch {
    message.error('获取成员列表失败')
    members.value = []
  } finally { loading.value = false }
}

/** 换角色：记住选择（下次进来还是它）并重新取成员 */
function onRoleChange() {
  try { localStorage.setItem(ROLE_STORAGE_KEY, roleOid.value) } catch { /* 忽略 */ }
  fetchMembers()
}

// ---- 添加成员 ----
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

/** 当前登录用户名（store 未初始化时退回 localStorage）—— 用于"移除自己"的提示 */
const currentUsername = computed(() => {
  if (userStore.username) return userStore.username
  try { return JSON.parse(localStorage.getItem('user') || '{}').username || '' } catch { return '' }
})

/**
 * 移除确认文案。
 *
 * <p>把角色名写进确认框：这一页能改任意角色，光说"确认移除"看不出是从哪儿移除。
 * 移除自己时再补一句 —— 例如把自己移出 TENANT_ADMIN 之后就进不了本页了，
 * 这种一步到位的"自锁"值得在点下去之前说清楚。
 */
function removeTitle(record) {
  const who = record.displayName || record.username
  const base = `确认从「${currentRole.value?.name || '该角色'}」移除「${who}」？`
  return record.username && record.username === currentUsername.value
    ? `${base}（这是你自己，移除后将失去该角色带来的权限）`
    : base
}

function showAddModal() {
  if (!roleOid.value) {
    message.warning('请先选择角色')
    return
  }
  selectedUserOid.value = null
  userOptions.value = []
  addModalVisible.value = true
  // 弹窗一打开就把候选拉回来：打开下拉就该有人可选，不必先敲字
  loadCandidates()
}

async function handleRemove(record) {
  try {
    const res = await removeRoleMember(roleOid.value, record.oid)
    if (res.code === 200) {
      message.success(`已从「${currentRole.value?.name || '该角色'}」移除「${record.displayName || record.username}」`)
      await fetchMembers()
    } else {
      message.error(res.message || '移除失败')
    }
  } catch { /* 请求层已提示 */ }
}

/**
 * 候选用户选项（value = 用户 oid）。
 *
 * <p>已经是该角色成员的人不出现在候选里 —— 再选一次没有意义。
 */
function toUserOptions(users) {
  const existingOids = new Set(members.value.map(m => m.oid))
  return (users || [])
    .filter(u => !existingOids.has(u.oid))
    .map(u => ({
      value: u.oid,
      label: `${u.displayName || u.username} (${u.username})`
    }))
}

/**
 * 取候选用户。keyword 为空 = 「默认清单」（当前租户全部用户）。
 *
 * <p><b>为什么要默认清单</b>：下拉原来只在输入关键字时才查（关键字为空直接清空选项），
 * 于是一打开是个空下拉 —— 等于要求用户先知道名字怎么拼。而这里的常见动作恰恰是
 * "在名单里扫一眼挑一个"，所以打开就给全量，输入才是缩小范围的手段。
 */
async function loadCandidates(keyword = '') {
  userSearchLoading.value = true
  try {
    const res = await getAllUsers(keyword ? { keyword } : {})
    userOptions.value = res?.code === 200 ? toUserOptions(res.data) : []
  } catch { userOptions.value = [] }
  finally { userSearchLoading.value = false }
}

/**
 * 打开下拉就回到默认清单。
 *
 * <p>每次打开都重新取，而不是"空才取"：否则搜过一次「Ro」之后关闭再打开，
 * 输入框是空的、列表却还是上次那个子集 —— 看起来像"这个角色只能加这几个人"。
 */
function onUserDropdownOpen(open) {
  if (open) loadCandidates()
}

/** 输入即搜索；清空则回到默认清单，而不是留一个空下拉 */
function handleSearchUser(keyword) {
  loadCandidates(keyword?.trim() || '')
}

async function handleAdd() {
  if (!selectedUserOid.value) return
  if (!roleOid.value) {
    message.error('请先选择角色')
    return
  }
  addLoading.value = true
  try {
    const res = await addRoleMember(roleOid.value, selectedUserOid.value)
    if (res.code === 200) {
      message.success(`已加入「${currentRole.value?.name || '该角色'}」`)
      addModalVisible.value = false
      selectedUserOid.value = null
      userOptions.value = []
      await fetchMembers()
    } else {
      message.error(res.message || '添加失败')
    }
  } catch { message.error('添加失败') }
  finally { addLoading.value = false }
}

onMounted(fetchRoles)
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
  min-width: 0;
}

/* 角色选择贴在页头右侧：它是本页的前置条件，不该混在表格工具栏里（工具栏是"对成员做什么"） */
.am-role-select {
  width: 260px;
  flex-shrink: 0;
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
