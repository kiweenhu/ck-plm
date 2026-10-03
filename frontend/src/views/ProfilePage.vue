<template>
  <div class="profile-page">
    <a-tabs v-model:activeKey="activeTab" class="profile-tabs">
      <!-- ==================== 账号资料 ==================== -->
      <a-tab-pane key="profile" tab="账号资料">
        <a-card :bordered="false" class="profile-card">
          <template #title>
            <div class="card-title-row">
              <user-outlined class="title-icon" />
              <span>个人信息</span>
            </div>
          </template>
          <template #extra>
            <a-space v-if="!editing">
              <a-button type="primary" @click="startEdit">
                <template #icon><edit-outlined /></template>
                编辑个人信息
              </a-button>
            </a-space>
          </template>

          <a-spin :spinning="loading">
            <!-- 姓名没填（或就是账号名）时提醒一次：右上角显示的是「姓名」，不是账号 -->
            <a-alert
              v-if="!editing && nameLooksLikeAccount"
              type="warning"
              show-icon
              message="姓名尚未填写"
              description="右上角显示的是「姓名」。请点「编辑个人信息」填写真实姓名并保存，否则会一直显示为账号。"
              style="margin-bottom: 16px"
            />

            <!-- 查看模式 -->
            <a-descriptions
              v-if="!editing"
              :column="2"
              bordered
              size="middle"
              class="profile-descriptions"
            >
              <a-descriptions-item label="用户名">
                {{ profile.username || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="姓名">
                {{ profile.displayName || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="邮箱">
                {{ profile.email || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="电话">
                {{ profile.phone || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="角色">
                <a-tag v-for="r in roleLabels" :key="r" color="blue">{{ r }}</a-tag>
                <span v-if="!roleLabels.length">-</span>
              </a-descriptions-item>
              <a-descriptions-item label="状态">
                <a-badge
                  :status="profile.enabled ? 'success' : 'error'"
                  :text="profile.enabled ? '启用' : '禁用'"
                />
              </a-descriptions-item>
              <a-descriptions-item label="所属租户">
                {{ store.tenantName || store.tenantOid || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="创建时间">
                {{ formatTime(profile.createdAt) }}
              </a-descriptions-item>
            </a-descriptions>

            <!-- 编辑模式 -->
            <a-form
              v-else
              :model="form"
              :rules="rules"
              ref="formRef"
              layout="vertical"
              class="profile-form"
            >
              <a-row :gutter="24">
                <a-col :span="12">
                  <a-form-item label="用户名">
                    <a-input :value="profile.username" disabled />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="姓名" name="displayName">
                    <a-input v-model:value="form.displayName" placeholder="请输入姓名" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="24">
                <a-col :span="12">
                  <a-form-item label="邮箱" name="email">
                    <a-input v-model:value="form.email" placeholder="请输入邮箱" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="电话" name="phone">
                    <a-input v-model:value="form.phone" placeholder="请输入电话" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item>
                <a-space>
                  <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
                  <a-button @click="cancelEdit">取消</a-button>
                </a-space>
              </a-form-item>
            </a-form>
          </a-spin>
        </a-card>
      </a-tab-pane>

      <!-- ==================== 安全设置 ==================== -->
      <a-tab-pane key="security" tab="安全设置">
        <!-- 修改密码 -->
        <a-card :bordered="false" class="password-card" title="修改密码">
          <a-form
            :model="pwdForm"
            :rules="pwdRules"
            ref="pwdFormRef"
            layout="vertical"
            class="password-form"
          >
            <a-row :gutter="24">
              <a-col :span="8">
                <a-form-item label="当前密码" name="oldPassword">
                  <a-input-password v-model:value="pwdForm.oldPassword" placeholder="请输入当前密码" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="新密码" name="newPassword">
                  <a-input-password v-model:value="pwdForm.newPassword" placeholder="至少 6 位" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="确认新密码" name="confirmPassword">
                  <a-input-password v-model:value="pwdForm.confirmPassword" placeholder="请再次输入新密码" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item>
              <a-button type="primary" :loading="changingPwd" @click="handleChangePassword">
                修改密码
              </a-button>
              <a-button style="margin-left: 12px" @click="resetPwdForm">重置</a-button>
            </a-form-item>
          </a-form>
        </a-card>

        <!-- 登录与设备 -->
        <a-card :bordered="false" class="security-card" title="登录与设备">
          <template #extra>
            <a-popconfirm
              title="将退出除当前设备外的所有登录会话，确定继续？"
              ok-text="确定退出"
              cancel-text="取消"
              @confirm="handleLogoutOthers"
            >
              <a-button danger :loading="loggingOutOthers">
                <template #icon><logout-outlined /></template>
                退出其他设备
              </a-button>
            </a-popconfirm>
          </template>

          <a-alert
            type="info"
            show-icon
            message="安全建议"
            description="在公共电脑上登录过、或怀疑密码泄露时：先修改密码，再点右上角把其它设备踢下线（本机不受影响）。"
            style="margin-bottom: 16px"
          />

          <a-spin :spinning="opsLoading">
            <a-list
              size="small"
              :data-source="recentOps"
              :locale="{ emptyText: '暂无登录记录' }"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <a-tag :color="opColor(item.action)" style="margin-right: 8px">
                        {{ item.action || '操作' }}
                      </a-tag>
                      <span>{{ item.target || '-' }}</span>
                    </template>
                    <template #description>
                      <span>{{ formatTime(item.time) }}</span>
                      <span v-if="item.ip" class="op-meta">IP {{ item.ip }}</span>
                      <span v-if="item.result && item.result !== 'SUCCESS'" class="op-fail">
                        结果 {{ item.result }}
                      </span>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
          </a-spin>
        </a-card>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, EditOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  getCurrentUser, updateProfile, changePassword,
  logoutOtherDevices, getUserRoles, getRecentOperations,
} from '@/api'

const store = useUserStore()
const route = useRoute()

/** 页签：支持从用户菜单直达（/profile?tab=security） */
const activeTab = ref(route.query.tab === 'security' ? 'security' : 'profile')
watch(() => route.query.tab, (tab) => {
  activeTab.value = tab === 'security' ? 'security' : 'profile'
})

const loading = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref(null)
const pwdFormRef = ref(null)

// 个人信息表单
const profile = reactive({
  oid: '',
  username: '',
  displayName: '',
  email: '',
  phone: '',
  enabled: true,
  createdAt: null,
})

const form = reactive({
  displayName: '',
  email: '',
  phone: ''
})

const rules = {
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }]
}

/** 姓名为空、或与账号相同 —— 此时右上角只能显示成账号，页面上提示一次 */
const nameLooksLikeAccount = computed(() => {
  const name = (profile.displayName || '').trim()
  return !name || name === (profile.username || '').trim()
})

// ---- 角色（拿到中文名，拿不到退回角色编码）----
const roleDetails = ref([])
const roleLabels = computed(() => {
  if (roleDetails.value.length) {
    return roleDetails.value.map((r) => (r.name ? `${r.name}（${r.code}）` : r.code))
  }
  return store.roles || []
})

async function loadRoles() {
  const oid = profile.oid || store.oid
  if (!oid) return
  try {
    const res = await getUserRoles(oid)
    if (res.code === 200 && Array.isArray(res.data)) roleDetails.value = res.data
  } catch {
    // 拿不到就只显示角色编码，不影响其它内容
  }
}

// 密码表单
const changingPwd = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule, value) => {
  if (value && value !== pwdForm.newPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// ---- 最近登录与操作 ----
const recentOps = ref([])
const opsLoading = ref(false)

async function loadRecentOps() {
  opsLoading.value = true
  try {
    const res = await getRecentOperations()
    if (res.code === 200 && Array.isArray(res.data)) {
      recentOps.value = res.data.slice(0, 12)
    }
  } catch {
    // 忽略，拦截器已提示
  } finally {
    opsLoading.value = false
  }
}

function opColor(action) {
  if (!action) return 'default'
  if (action.includes('登录')) return 'green'
  if (action.includes('注销') || action.includes('退出')) return 'default'
  if (action.includes('检出')) return 'orange'
  if (action.includes('检入')) return 'cyan'
  if (action.includes('创建')) return 'blue'
  if (action.includes('删除')) return 'red'
  return 'blue'
}

function formatTime(time) {
  if (!time) return '-'
  return String(time).replace('T', ' ').slice(0, 19)
}

// ---- 退出其他设备 ----
const loggingOutOthers = ref(false)

async function handleLogoutOthers() {
  loggingOutOthers.value = true
  try {
    const res = await logoutOtherDevices()
    if (res.code === 200) {
      const removed = res.data?.removed ?? 0
      message.success(removed > 0 ? `已退出其他 ${removed} 个登录会话` : '没有其他登录设备')
      loadRecentOps()
    }
  } catch {
    // 忽略，拦截器已提示
  } finally {
    loggingOutOthers.value = false
  }
}

// ---- 加载用户信息 ----
async function loadProfile() {
  loading.value = true
  try {
    const res = await getCurrentUser()
    if (res.code === 200 && res.data) {
      const user = res.data
      profile.oid = user.oid || ''
      profile.username = user.username || ''
      profile.displayName = user.displayName || ''
      profile.email = user.email || ''
      profile.phone = user.phone || ''
      profile.enabled = user.enabled !== false
      profile.createdAt = user.createdAt || null
    }
  } catch {
    // 忽略，拦截器已提示
  } finally {
    loading.value = false
  }
}

// ---- 编辑个人信息 ----
function startEdit() {
  form.displayName = profile.displayName
  form.email = profile.email
  form.phone = profile.phone
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  formRef.value?.resetFields()
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const res = await updateProfile({
      displayName: form.displayName,
      email: form.email || null,
      phone: form.phone || null
    })
    if (res.code === 200) {
      message.success('个人信息更新成功')
      profile.displayName = form.displayName
      profile.email = form.email || ''
      profile.phone = form.phone || ''
      // 同步到全局 store
      store.setUserInfo({
        displayName: form.displayName,
        email: form.email || '',
        phone: form.phone || ''
      })
      editing.value = false
    }
  } catch {
    // ignore
  } finally {
    saving.value = false
  }
}

// ---- 修改密码 ----
function resetPwdForm() {
  pwdFormRef.value?.resetFields()
}

async function handleChangePassword() {
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }

  changingPwd.value = true
  try {
    const res = await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    if (res.code === 200) {
      message.success('密码修改成功，下次登录请使用新密码')
      pwdFormRef.value.resetFields()
    }
  } catch {
    // ignore
  } finally {
    changingPwd.value = false
  }
}

onMounted(async () => {
  await loadProfile()
  loadRoles()
  loadRecentOps()
})
</script>

<style scoped>
.profile-page {
  max-width: 880px;
  margin: 0 auto;
}

.profile-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 16px;
}

.profile-card {
  margin-bottom: 24px;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  color: #1677ff;
  font-size: 18px;
}

.profile-descriptions :deep(.ant-descriptions-item-label) {
  width: 100px;
  font-weight: 500;
}

.profile-form,
.password-form {
  max-width: 700px;
}

.password-card {
  margin-bottom: 24px;
}

.security-card {
  margin-bottom: 24px;
}

.op-meta {
  margin-left: 12px;
  color: #8c8c8c;
}

.op-fail {
  margin-left: 12px;
  color: #ff4d4f;
}
</style>
