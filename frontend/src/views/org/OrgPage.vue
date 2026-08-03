<template>
  <div class="org-dept-page" v-if="route.path === '/org/dept'">
    <!-- 左侧：部门架构树 -->
    <div class="dept-tree-panel">
      <div class="panel-header">
        <span class="panel-title">部门架构</span>
        <a-button type="primary" size="small" @click="openDeptModal(null)">
          <template #icon><plus-outlined /></template>
          新增
        </a-button>
      </div>
      <div class="tree-search">
        <a-input
          v-model:value="treeSearch"
          placeholder="搜索部门..."
          allow-clear
          size="small"
        >
          <template #prefix><search-outlined /></template>
        </a-input>
      </div>
      <div class="tree-container">
        <a-spin :spinning="treeLoading">
          <a-tree
            v-if="filteredTree.length > 0"
            :tree-data="filteredTree"
            v-model:selectedKeys="selectedDeptKeys"
            v-model:expandedKeys="expandedKeys"
            :field-names="{ children: 'children', title: 'name', key: 'oid' }"
            show-line
            block-node
            @select="onTreeSelect"
          >
            <template #title="nodeData">
              <a-dropdown :trigger="['contextmenu']" @contextmenu.prevent="contextMenuNode = nodeData">
                <div class="tree-node-row">
                  <span class="tree-node" :class="{ disabled: !nodeData.enabled }">
                    <apartment-outlined class="tree-icon" />
                    <span class="tree-name">{{ nodeData.name }}</span>
                    <span class="tree-code">{{ nodeData.code }}</span>
                  </span>
                  <span class="tree-actions" @click.stop>
                    <a-button type="link" size="small" @click="openDeptModal(nodeData)">
                      <edit-outlined />
                    </a-button>
                    <a-popconfirm
                      title="确定删除该部门？"
                      :description="'删除后将无法恢复'"
                      @confirm="handleDeleteDept(nodeData)"
                    >
                      <a-button type="link" size="small" danger>
                        <delete-outlined />
                      </a-button>
                    </a-popconfirm>
                  </span>
                </div>
                <template #overlay>
                  <a-menu @click="(e) => onDeptMenu(e, contextMenuNode)">
                    <a-menu-item key="edit">
                      <edit-outlined /> 编辑
                    </a-menu-item>
                    <a-menu-item key="addChild">
                      <plus-outlined /> 添加子部门
                    </a-menu-item>
                    <a-menu-divider />
                    <a-menu-item key="delete" danger>
                      <delete-outlined /> 删除
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </template>
          </a-tree>
          <a-empty v-else description="暂无部门数据" />
        </a-spin>
      </div>
    </div>

    <!-- 右侧：用户列表 -->
    <div class="user-panel">
      <div class="panel-header">
        <div class="panel-left">
          <span class="panel-title">
            {{ selectedDept ? selectedDept.name + ' - 用户' : '部门用户' }}
          </span>
          <a-tag v-if="selectedDept" color="blue">{{ selectedDept.code }}</a-tag>
        </div>
        <div class="panel-actions">
          <a-button
            type="primary"
            size="small"
            :disabled="!selectedDept"
            @click="openUserModal(null)"
          >
            <template #icon><user-add-outlined /></template>
            添加用户
          </a-button>
        </div>
      </div>

      <DataTable
        :columns="userColumns"
        :data-source="users"
        :loading="userLoading"
        :searchable="true"
        search-placeholder="搜索用户名/姓名/邮箱..."
        :search-fields="['username', 'displayName', 'email']"
        :show-column-toggle="true"
        :enable-resize="true"
        :max-height="420"
        pagination-mode="frontend"
        row-key="oid"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'red'">
              {{ record.enabled ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space size="small">
              <a-button type="link" size="small" @click="openUserModal(record)">编辑</a-button>
              <a-button type="link" size="small" @click="openMoveUserModal(record)">移动部门</a-button>
              <a-button type="link" size="small" @click="resetUserPwd(record)">重置密码</a-button>
              <a-popconfirm
                title="确定删除该用户？"
                @confirm="handleDeleteUser(record)"
              >
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </DataTable>
    </div>

    <!-- ======= 部门新增/编辑弹窗 ======= -->
    <a-modal
      v-model:open="deptModal.visible"
      :title="deptModal.isEdit ? '编辑部门' : '新增部门'"
      @ok="handleDeptSave"
      :confirm-loading="deptModal.saving"
      width="480px"
    >
      <a-form :model="deptModal.form" layout="vertical">
        <a-form-item label="部门编码" required>
          <a-input
            v-model:value="deptModal.form.code"
            placeholder="如 SALES、RD_SHENZHEN"
            :disabled="deptModal.isEdit"
          />
        </a-form-item>
        <a-form-item label="部门名称" required>
          <a-input v-model:value="deptModal.form.name" placeholder="如 销售部、深圳研发中心" />
        </a-form-item>
        <a-form-item label="上级部门">
          <a-tree-select
            v-model:value="deptModal.form.parentOid"
            :tree-data="treeData"
            :field-names="{ children: 'children', label: 'name', value: 'oid' }"
            placeholder="留空则为根部门"
            allow-clear
            tree-default-expand-all
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="deptModal.form.description" :rows="2" placeholder="部门职责、备注等" />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="deptModal.form.enabled" checked-children="启用" un-checked-children="禁用" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- ======= 用户新增/编辑弹窗 ======= -->
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
          />
        </a-form-item>
        <a-form-item label="密码" :required="!userModal.isEdit">
          <a-input-password
            v-model:value="userModal.form.password"
            placeholder="登录密码"
          />
        </a-form-item>
        <a-form-item label="姓名">
          <a-input v-model:value="userModal.form.displayName" placeholder="用户姓名" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="userModal.form.email" placeholder="user@example.com" />
        </a-form-item>
        <a-form-item label="电话">
          <a-input v-model:value="userModal.form.phone" placeholder="手机号或座机" />
        </a-form-item>
        <a-form-item label="所属部门">
          <a-tree-select
            v-model:value="userModal.form.orgOid"
            :tree-data="treeData"
            :field-names="{ children: 'children', label: 'name', value: 'oid' }"
            placeholder="请选择部门"
            tree-default-expand-all
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="userModal.form.enabled" checked-children="启用" un-checked-children="禁用" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- ======= 用户移动部门弹窗 ======= -->
    <a-modal
      v-model:open="moveUserModal.visible"
      title="移动用户到其他部门"
      @ok="handleMoveUser"
      :confirm-loading="moveUserModal.saving"
      width="460px"
    >
      <a-form layout="vertical">
        <a-form-item label="目标用户">
          <a-input
            :value="moveUserModal.user ? (moveUserModal.user.displayName || moveUserModal.user.username) : ''"
            disabled
          />
        </a-form-item>
        <a-form-item label="当前部门">
          <a-input
            :value="moveUserModal.currentDeptName"
            disabled
          />
        </a-form-item>
        <a-form-item label="目标部门" required>
          <a-tree-select
            v-model:value="moveUserModal.targetOrgOid"
            :tree-data="treeData"
            :field-names="{ children: 'children', label: 'name', value: 'oid' }"
            placeholder="请选择目标部门"
            tree-default-expand-all
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>

  <!-- 角色定义 -->
  <div v-else-if="route.path === '/org/roles'" class="org-roles-page">
    <RoleConfig />
  </div>

  <!-- 其他组织管理子页面保留占位 -->
  <div class="placeholder-page" v-else>
    <a-result status="info" :title="pageTitle" :sub-title="pageDesc" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  PlusOutlined, SearchOutlined, ApartmentOutlined,
  EditOutlined, DeleteOutlined, UserAddOutlined
} from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import RoleConfig from '@/views/system/RoleConfig.vue'
import {
  getOrgTree, createOrg, updateOrg, deleteOrg,
  getUsersByOrg, createUser, updateUser, deleteUser, resetPassword
} from '@/api'

const route = useRoute()

// ==================== 子页面映射 ====================
const pageMap = {
  '/org/users': { title: '用户管理', desc: '管理系统用户、账户状态与权限分配' },
  '/org/roles': { title: '角色定义', desc: '定义角色并配置菜单/操作/数据级权限' },
  '/org/dept':  { title: '部门架构', desc: '组织架构树维护、部门层级与人员归属' }
}
const current = computed(() => pageMap[route.path] || pageMap['/org/users'])
const pageTitle = computed(() => current.value.title)
const pageDesc  = computed(() => current.value.desc)

// ==================== 部门树 ====================
const treeData = ref([])
const treeLoading = ref(false)
const treeSearch = ref('')
const selectedDeptKeys = ref([])
const expandedKeys = ref([])
const selectedDept = ref(null)
const contextMenuNode = ref(null)

// 根据搜索词过滤树
const filteredTree = computed(() => {
  if (!treeSearch.value.trim()) return treeData.value
  return filterTree(treeData.value, treeSearch.value.trim().toLowerCase())
})

function filterTree(nodes, keyword) {
  return nodes.reduce((acc, node) => {
    const nameMatch = (node.name || '').toLowerCase().includes(keyword)
    const codeMatch = (node.code || '').toLowerCase().includes(keyword)
    const children = node.children?.length ? filterTree(node.children, keyword) : []
    if (nameMatch || codeMatch) {
      acc.push({ ...node, children: node.children || [] })
    } else if (children.length > 0) {
      acc.push({ ...node, children })
    }
    return acc
  }, [])
}

async function loadTree() {
  treeLoading.value = true
  try {
    const res = await getOrgTree()
    if (res.code === 200) {
      treeData.value = res.data || []
      // 默认全部展开
      expandedKeys.value = collectAllKeys(treeData.value)
    }
  } catch {
    message.error('加载部门树失败')
  } finally {
    treeLoading.value = false
  }
}

function collectAllKeys(nodes) {
  const keys = []
  for (const n of nodes) {
    keys.push(n.oid)
    if (n.children?.length) {
      keys.push(...collectAllKeys(n.children))
    }
  }
  return keys
}

function findNode(oid, nodes = treeData.value) {
  for (const n of nodes) {
    if (n.oid === oid) return n
    if (n.children?.length) {
      const found = findNode(oid, n.children)
      if (found) return found
    }
  }
  return null
}

// 监听部门选中变化 → 加载用户列表
watch(selectedDeptKeys, (keys) => {
  console.log('[OrgPage] watch selectedDeptKeys:', keys)
  if (!keys || keys.length === 0) {
    selectedDept.value = null
    users.value = []
    return
  }
  selectedDept.value = findNode(keys[0])
  console.log('[OrgPage] watch selectedDept:', selectedDept.value)
  loadUsers()
})

// a-tree @select 事件回调，确保选中节点时加载用户
function onTreeSelect(keys, info) {
  console.log('[OrgPage] onTreeSelect keys:', keys, 'info:', info)
  if (!keys || keys.length === 0) {
    selectedDept.value = null
    users.value = []
    return
  }
  selectedDept.value = findNode(keys[0])
  console.log('[OrgPage] onTreeSelect selectedDept:', selectedDept.value)
  loadUsers()
}

// ==================== 部门弹窗 ====================
const deptModal = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingOid: null,
  form: {
    code: '', name: '', parentOid: null,
    description: '', enabled: true
  }
})

function openDeptModal(node) {
  deptModal.editingOid = node?.oid || null
  deptModal.isEdit = !!node
  if (node) {
    deptModal.form = {
      code: node.code,
      name: node.name,
      parentOid: node.parentOid || null,
      description: node.description || '',
      enabled: node.enabled !== false
    }
  } else {
    deptModal.form = {
      code: '', name: '',
      parentOid: selectedDept.value?.oid || null,
      description: '', enabled: true
    }
  }
  deptModal.visible = true
}

function onDeptMenu({ key }, node) {
  const dept = findNode(node.key)
  if (!dept) return
  if (key === 'edit') openDeptModal(dept)
  else if (key === 'addChild') {
    deptModal.form = {
      code: '', name: '',
      parentOid: dept.oid,
      description: '', enabled: true
    }
    deptModal.editingOid = null
    deptModal.isEdit = false
    deptModal.visible = true
  } else if (key === 'delete') handleDeleteDept(dept)
}

async function handleDeptSave() {
  const { code, name } = deptModal.form
  if (!code?.trim() || !name?.trim()) {
    message.warning('部门编码和名称不能为空')
    return
  }
  deptModal.saving = true
  try {
    const payload = {
      code: code.trim(),
      name: name.trim(),
      parentOid: deptModal.form.parentOid || null,
      description: deptModal.form.description || '',
      enabled: deptModal.form.enabled
    }
    let res
    if (deptModal.isEdit) {
      res = await updateOrg(deptModal.editingOid, payload)
    } else {
      res = await createOrg(payload)
    }
    if (res.code === 200) {
      message.success(deptModal.isEdit ? '部门更新成功' : '部门创建成功')
      deptModal.visible = false
      await loadTree()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  } finally {
    deptModal.saving = false
  }
}

async function handleDeleteDept(dept) {
  // 先检查该部门下是否有用户
  try {
    const userRes = await getUsersByOrg(dept.oid)
    const userCount = (userRes.code === 200 && userRes.data) ? userRes.data.length : 0
    if (userCount > 0) {
      message.warning(`「${dept.name}」下存在 ${userCount} 个用户，请先将用户移出后再删除部门`)
      return
    }
  } catch { /* 检查失败则交由后端校验 */ }

  // 确认删除
  if (!confirm(`确定删除部门「${dept.name}」吗？此操作不可撤销。`)) return

  try {
    const res = await deleteOrg(dept.oid)
    if (res.code === 200) {
      message.success('部门已删除')
      if (selectedDept.value?.oid === dept.oid) {
        selectedDept.value = null
        selectedDeptKeys.value = []
      }
      await loadTree()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch {
    message.error('删除失败')
  }
}

// ==================== 用户列表 ====================
const users = ref([])
const userLoading = ref(false)

const userColumns = [
  { title: '用户名', dataIndex: 'username', key: 'username', ellipsis: true, width: 140 },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName', ellipsis: true },
  { title: '邮箱', dataIndex: 'email', key: 'email', ellipsis: true, width: 200 },
  { title: '电话', dataIndex: 'phone', key: 'phone', ellipsis: true, width: 130 },
  { title: '状态', key: 'enabled', width: 70 },
  { title: '操作', key: 'action', width: 280 }
]

async function loadUsers() {
  console.log('[OrgPage] loadUsers called, selectedDept:', selectedDept.value)
  if (!selectedDept.value) {
    users.value = []
    return
  }
  userLoading.value = true
  try {
    console.log('[OrgPage] fetching users for orgOid:', selectedDept.value.oid)
    const res = await getUsersByOrg(selectedDept.value.oid)
    console.log('[OrgPage] getUsersByOrg response:', res)
    if (res.code === 200) {
      users.value = res.data || []
      console.log('[OrgPage] users loaded:', users.value.length)
    } else {
      message.error(res.message || '加载用户失败')
    }
  } catch (err) {
    console.error('[OrgPage] 加载用户列表失败:', err)
    message.error('加载用户列表失败')
  } finally {
    userLoading.value = false
  }
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
      orgOid: user.orgOid || selectedDept.value?.oid || null,
      enabled: user.enabled !== false
    }
  } else {
    userModal.form = {
      username: '', password: '', displayName: '',
      email: '', phone: '',
      orgOid: selectedDept.value?.oid || null,
      enabled: true
    }
  }
  userModal.visible = true
}

async function handleUserSave() {
  const { username, password, displayName } = userModal.form
  if (!username?.trim()) { message.warning('用户名不能为空'); return }
  if (!userModal.isEdit && !password?.trim()) { message.warning('密码不能为空'); return }
  userModal.saving = true
  try {
    const payload = {
      username: username.trim(),
      displayName: displayName || '',
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
      let updateRes = await updateUser(userModal.editingOid, updatePayload)
      if (password?.trim()) {
        await resetPassword(userModal.editingOid, password.trim())
      }
      res = updateRes
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

async function handleDeleteUser(user) {
  try {
    const res = await deleteUser(user.oid)
    if (res.code === 200) {
      message.success('用户已删除')
      await loadUsers()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch {
    message.error('删除失败')
  }
}

async function resetUserPwd(user) {
  const pwd = prompt(`请输入「${user.displayName || user.username}」的新密码（至少4位）：`)
  if (!pwd || pwd.length < 4) {
    if (pwd !== null) message.warning('密码至少4位')
    return
  }
  try {
    const res = await resetPassword(user.oid, pwd)
    if (res.code === 200) {
      message.success('密码已重置')
    } else {
      message.error(res.message || '重置失败')
    }
  } catch {
    message.error('重置失败')
  }
}

// ==================== 用户移动部门弹窗 ====================
const moveUserModal = reactive({
  visible: false,
  saving: false,
  user: null,
  currentDeptName: '',
  targetOrgOid: null
})

function openMoveUserModal(user) {
  moveUserModal.user = user
  moveUserModal.targetOrgOid = null
  moveUserModal.currentDeptName = selectedDept.value?.name || '未知部门'
  moveUserModal.visible = true
}

async function handleMoveUser() {
  if (!moveUserModal.targetOrgOid) {
    message.warning('请选择目标部门')
    return
  }
  if (moveUserModal.targetOrgOid === moveUserModal.user.orgOid) {
    message.warning('目标部门与当前部门相同')
    return
  }
  moveUserModal.saving = true
  try {
    const res = await updateUser(moveUserModal.user.oid, {
      orgOid: moveUserModal.targetOrgOid
    })
    if (res.code === 200) {
      message.success('用户已成功移动到目标部门')
      moveUserModal.visible = false
      await loadUsers()
    } else {
      message.error(res.message || '移动失败')
    }
  } catch {
    message.error('移动失败')
  } finally {
    moveUserModal.saving = false
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  if (route.path === '/org/dept') {
    await loadTree()
  }
})

// 路由切换时重新加载
watch(() => route.path, async (path) => {
  if (path === '/org/dept') {
    await nextTick()
    await loadTree()
  }
})
</script>

<style scoped>
.org-dept-page {
  display: flex;
  gap: 0;
  height: calc(100vh - 160px);
  min-height: 500px;
}

/* ===== 左侧部门树面板 ===== */
.dept-tree-panel {
  width: 300px;
  min-width: 260px;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  border-radius: 8px 0 0 8px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.tree-search {
  padding: 8px 12px;
  background: #fff;
}

.tree-container {
  flex: 1;
  overflow: auto;
  padding: 8px 12px;
}

.tree-container :deep(.ant-tree) {
  background: transparent;
}

.tree-node-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  justify-content: space-between;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tree-node.disabled {
  opacity: 0.5;
}

.tree-actions {
  display: none;
  flex-shrink: 0;
  margin-left: auto;
}

.tree-node-row:hover .tree-actions {
  display: inline-flex;
  align-items: center;
}

.tree-icon {
  font-size: 14px;
  color: #1677ff;
}

.tree-name {
  font-size: 14px;
  color: #262626;
}

.tree-code {
  font-size: 12px;
  color: #8c8c8c;
  margin-left: 4px;
}

/* ===== 右侧用户面板 ===== */
.user-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: 0 8px 8px 0;
  padding: 0 16px 16px;
}

.user-panel .panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 12px;
  background: transparent;
}

.panel-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-actions {
  display: flex;
  align-items: center;
}

/* 占位页面 */
.placeholder-page {
  padding: 16px 0;
}

.org-roles-page {
  height: 100%;
  overflow: auto;
}
</style>
