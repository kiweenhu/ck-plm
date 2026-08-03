<template>
  <div class="profile-page">
    <!-- ===== 个人信息卡片 ===== -->
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
            <a-tag v-for="r in store.roles" :key="r" color="blue">{{ r }}</a-tag>
            <span v-if="!store.roles.length">-</span>
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-badge
              :status="profile.enabled ? 'success' : 'error'"
              :text="profile.enabled ? '启用' : '禁用'"
            />
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

    <!-- ===== 修改密码卡片 ===== -->
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
              <a-input-password v-model:value="pwdForm.newPassword" placeholder="请输入新密码" />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { UserOutlined, EditOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { getCurrentUser, updateProfile, changePassword } from '@/api'

const store = useUserStore()

const loading = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref(null)
const pwdFormRef = ref(null)

// 个人信息表单
const profile = reactive({
  username: '',
  displayName: '',
  email: '',
  phone: '',
  enabled: true
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

// ---- 加载用户信息 ----
async function loadProfile() {
  loading.value = true
  try {
    const res = await getCurrentUser()
    if (res.code === 200 && res.data) {
      const user = res.data
      profile.username = user.username || ''
      profile.displayName = user.displayName || ''
      profile.email = user.email || ''
      profile.phone = user.phone || ''
      profile.enabled = user.enabled !== false
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

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.profile-page {
  max-width: 800px;
  margin: 0 auto;
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
  max-width: 600px;
}

.password-card {
  margin-bottom: 24px;
}
</style>
