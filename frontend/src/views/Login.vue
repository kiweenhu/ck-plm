<template>
  <div class="login-container">
    <!-- 背景层 -->
    <div class="bg-layer">
      <div class="stars stars-sm"></div>
      <div class="stars stars-md"></div>
      <div class="stars stars-lg"></div>
      <div class="grid-mesh"></div>
      <div class="particle particle-1"></div>
      <div class="particle particle-2"></div>
      <div class="particle particle-3"></div>
      <div class="particle particle-4"></div>
      <div class="particle particle-5"></div>
      <div class="particle particle-6"></div>
      <div class="particle particle-7"></div>
      <div class="particle particle-8"></div>
      <div class="shooting-star shooting-star-1"></div>
      <div class="shooting-star shooting-star-2"></div>
      <div class="scan-line"></div>
      <div class="glow-orb glow-orb-1"></div>
      <div class="glow-orb glow-orb-2"></div>
      <div class="glow-orb glow-orb-3"></div>
    </div>

    <!-- 品牌头部 -->
    <div class="brand-header">
      <div class="system-logo">
        <img src="@/assets/logo-icon.png" alt="CK-PLM" width="48" height="48" />
      </div>
      <div class="title-row">
        <h1 class="system-title">CK-PLM</h1>
        <span class="oss-badge">OPEN SOURCE</span>
      </div>
      <p class="system-subtitle">开源产品生命周期管理系统</p>
    </div>

    <!-- 主体双栏：登录表单 + PLM能力卡片 -->
    <div class="content-area">
      <!-- 登录卡片 -->
      <div class="login-card" v-if="!isRegistering">
        <div class="card-border-glow"></div>
        <h2 class="login-card-title">登录</h2>

        <a-form
          ref="formRef"
          :model="formState"
          :rules="rules"
          layout="vertical"
          @finish="handleLogin"
          autocomplete="off"
        >
          <a-form-item name="username">
            <a-input
              v-model:value="formState.username"
              size="large"
              placeholder="请输入用户名"
              :prefix="h(UserOutlined)"
              allow-clear
            />
          </a-form-item>

          <a-form-item name="password">
            <a-input-password
              v-model:value="formState.password"
              size="large"
              placeholder="请输入密码"
              :prefix="h(LockOutlined)"
              @keydown.enter="handleLogin"
            />
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              :loading="loading"
              block
            >
              {{ loading ? '登录中...' : '登 录' }}
            </a-button>
          </a-form-item>
        </a-form>

        <div class="register-toggle">
          <span class="register-hint">还没有账号？</span>
          <a class="register-link" @click="isRegistering = true">注册租户</a>
        </div>
      </div>

      <!-- 注册卡片 -->
      <div class="login-card register-card" v-else>
        <div class="card-border-glow"></div>
        <h2 class="login-card-title">注册租户</h2>
        <p class="register-subtitle">注册后将自动创建管理员账号</p>

        <a-form
          ref="registerFormRef"
          :model="registerState"
          :rules="registerRules"
          layout="vertical"
          @finish="handleRegister"
          autocomplete="off"
        >
          <a-form-item name="tenantId">
            <a-input
              v-model:value="registerState.tenantId"
              size="large"
              placeholder="租户标识（全局唯一，如 my-company）"
              :prefix="h(UserOutlined)"
              allow-clear
            />
          </a-form-item>

          <a-form-item name="name">
            <a-input
              v-model:value="registerState.name"
              size="large"
              placeholder="公司/组织名称"
              :prefix="h(UserOutlined)"
              allow-clear
            />
          </a-form-item>

          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item name="contactName">
                <a-input
                  v-model:value="registerState.contactName"
                  size="large"
                  placeholder="联系人"
                  allow-clear
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item name="contactEmail">
                <a-input
                  v-model:value="registerState.contactEmail"
                  size="large"
                  placeholder="联系邮箱"
                  allow-clear
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-form-item name="adminUsername">
            <a-input
              v-model:value="registerState.adminUsername"
              size="large"
              placeholder="管理员用户名"
              allow-clear
            />
          </a-form-item>

          <a-form-item name="adminPassword">
            <a-input-password
              v-model:value="registerState.adminPassword"
              size="large"
              placeholder="管理员密码（至少 4 位）"
              :prefix="h(LockOutlined)"
            />
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              :loading="registerLoading"
              block
            >
              {{ registerLoading ? '注册中...' : '注 册' }}
            </a-button>
          </a-form-item>
        </a-form>

        <div class="register-toggle">
          <a class="register-link" @click="isRegistering = false">← 返回登录</a>
        </div>
      </div>

      <!-- PLM 能力卡片 -->
      <div class="plm-capability-card">
        <h3 class="capability-title">平台能力</h3>
        <p class="capability-desc">CK-PLM 为企业提供全栈产品生命周期管理</p>

        <div class="tech-tags">
          <span class="tech-tag spring">Spring Boot 3</span>
          <span class="tech-tag vue">Vue 3</span>
          <span class="tech-tag pg">PostgreSQL</span>
          <span class="tech-tag">MyBatis</span>
          <span class="tech-tag">BPMN 2.0</span>
        </div>

        <div class="capability-list">
          <div class="capability-item">
            <span class="capability-icon">📦</span>
            <div class="capability-info">
              <span class="capability-name">产品全生命周期管理</span>
              <span class="capability-hint">产品系列 · 型号 · 研发阶段</span>
            </div>
          </div>
          <div class="capability-item">
            <span class="capability-icon">🔧</span>
            <div class="capability-info">
              <span class="capability-name">软类型动态建模</span>
              <span class="capability-hint">实体属性 · IBA扩展 · 页面设计器</span>
            </div>
          </div>
          <div class="capability-item">
            <span class="capability-icon">🗂️</span>
            <div class="capability-info">
              <span class="capability-name">分类与编码管理</span>
              <span class="capability-hint">分类树 · 编码规则 · 版本控制</span>
            </div>
          </div>
          <div class="capability-item">
            <span class="capability-icon">🔄</span>
            <div class="capability-info">
              <span class="capability-name">复合实体与迭代</span>
              <span class="capability-hint">Revision/Iteration · 检出检入</span>
            </div>
          </div>
          <div class="capability-item">
            <span class="capability-icon">🔌</span>
            <div class="capability-info">
              <span class="capability-name">可视化工作流引擎</span>
              <span class="capability-hint">BPMN 2.0 · 生命周期 · 审批</span>
            </div>
          </div>
          <div class="capability-item">
            <span class="capability-icon">🛡️</span>
            <div class="capability-info">
              <span class="capability-name">多租户与权限控制</span>
              <span class="capability-hint">租户隔离 · RBAC · 平台管理</span>
            </div>
          </div>
        </div>

        <div class="capability-footer">
          <a href="https://github.com" target="_blank" class="oss-link">
            <svg viewBox="0 0 16 16" width="15" height="15" fill="currentColor">
              <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/>
            </svg>
            查看源代码
          </a>
          <span class="oss-link-divider">|</span>
          <span class="oss-link">Apache-2.0</span>
          <span class="oss-link-divider">|</span>
          <span class="oss-link">v1.0.0</span>
        </div>
      </div>
    </div>

    <!-- 底部 -->
    <div class="login-footer">
      <span>深圳市乘恺科技有限公司 2026~2029</span>
    </div>
  </div>
</template>

<script setup>
import { h, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { login, registerTenant } from '@/api'
import { useUserStore } from '@/stores/user'
import { recordLogin } from '@/composables/useActivity'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const isRegistering = ref(false)

const formState = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, message: '密码至少 4 位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  try { await formRef.value.validate() } catch { return }
  loading.value = true
  try {
    const res = await login(formState.username.trim(), formState.password)
    if (res.code === 200 && res.data) {
      userStore.setLogin(res.data)
      message.success(`欢迎回来，${res.data.displayName || res.data.username}`)
      recordLogin(res.data.username)
      setTimeout(() => router.replace('/home'), 600)
    }
  } finally {
    loading.value = false
  }
}

const registerFormRef = ref()
const registerLoading = ref(false)
const registerState = reactive({
  tenantId: '', name: '', contactName: '', contactEmail: '',
  adminUsername: '', adminPassword: ''
})

const registerRules = {
  tenantId: [
    { required: true, message: '请输入租户标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9][a-zA-Z0-9_-]{1,48}$/, message: '2-49位，字母/数字/下划线/连字符', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入公司/组织名称', trigger: 'blur' }],
  contactEmail: [{ type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
  adminUsername: [
    { required: true, message: '请输入管理员用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9][a-zA-Z0-9._@-]{2,49}$/, message: '3-50位，字母或数字开头', trigger: 'blur' }
  ],
  adminPassword: [
    { required: true, message: '请输入管理员密码', trigger: 'blur' },
    { min: 4, message: '密码至少 4 位', trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  try { await registerFormRef.value.validate() } catch { return }
  registerLoading.value = true
  try {
    const res = await registerTenant({
      tenantId: registerState.tenantId.trim(),
      name: registerState.name.trim(),
      contactName: registerState.contactName.trim(),
      contactEmail: registerState.contactEmail.trim(),
      adminUsername: registerState.adminUsername.trim(),
      adminPassword: registerState.adminPassword,
      adminDisplayName: registerState.contactName?.trim() || registerState.adminUsername.trim()
    })
    if (res.code === 200) {
      message.success('注册申请已提交，请等待管理员审核通过后登录')
      isRegistering.value = false
    }
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
/* ========== 容器与背景 ========== */
.login-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0a0f28 0%, #151d48 25%, #1a2655 45%, #15204a 65%, #0c1440 85%, #1a1140 100%);
  background-size: 400% 400%;
  animation: bgShift 15s ease-in-out infinite;
  padding: 24px;
  position: relative;
  overflow: hidden;
}

@keyframes bgShift {
  0%, 100% { background-position: 0% 50%; }
  25% { background-position: 100% 0%; }
  50% { background-position: 100% 100%; }
  75% { background-position: 0% 100%; }
}

/* ========== 背景层 ========== */
.bg-layer { position: absolute; inset: 0; pointer-events: none; z-index: 0; }

/* 星空 */
.stars { position: absolute; inset: 0; animation: starTwinkle 3s ease-in-out infinite; }
.stars-sm {
  width: 1px; height: 1px;
  box-shadow: 50px 80px #fff, 180px 30px rgba(200,220,255,0.7), 320px 150px rgba(180,200,255,0.8), 500px 60px rgba(255,255,255,0.6), 650px 200px rgba(200,210,255,0.9), 780px 90px #fff, 120px 350px rgba(180,200,255,0.7), 400px 280px rgba(255,255,255,0.5), 700px 380px rgba(200,220,255,0.8), 860px 300px #fff, 950px 120px rgba(180,200,255,0.6), 60px 500px rgba(255,255,255,0.7), 280px 450px rgba(200,210,255,0.9), 550px 520px rgba(180,200,255,0.6), 830px 480px #fff, 1000px 420px rgba(200,220,255,0.7), 150px 600px rgba(255,255,255,0.5), 450px 650px rgba(180,200,255,0.8), 680px 600px #fff, 900px 650px rgba(200,210,255,0.6);
  animation-delay: 0s;
}
.stars-md {
  width: 2px; height: 2px; border-radius: 50%;
  box-shadow: 90px 55px rgba(160,200,255,0.8), 350px 85px #fff, 550px 130px rgba(180,200,255,0.7), 750px 45px rgba(200,220,255,0.9), 30px 220px rgba(255,255,255,0.6), 450px 210px rgba(160,200,255,0.8), 850px 190px #fff, 150px 380px rgba(180,200,255,0.7), 600px 340px rgba(200,220,255,0.9), 950px 350px rgba(160,200,255,0.6), 80px 480px #fff, 380px 500px rgba(200,210,255,0.8), 720px 450px rgba(180,200,255,0.7), 1000px 520px rgba(255,255,255,0.5), 220px 620px rgba(160,200,255,0.9), 520px 580px #fff, 760px 620px rgba(200,220,255,0.7), 40px 700px rgba(180,200,255,0.6), 880px 700px rgba(255,255,255,0.8), 950px 70px rgba(160,200,255,0.7);
  animation-delay: -1s;
}
.stars-lg {
  width: 3px; height: 3px; border-radius: 50%;
  box-shadow: 200px 40px #fff, 480px 90px rgba(180,200,255,0.8), 680px 30px rgba(200,220,255,0.9), 300px 160px rgba(255,255,255,0.7), 800px 140px #fff, 100px 300px rgba(160,200,255,0.8), 500px 270px rgba(200,210,255,0.9), 880px 250px rgba(255,255,255,0.6), 980px 180px rgba(180,200,255,0.7), 60px 420px #fff, 420px 410px rgba(200,220,255,0.8), 700px 370px rgba(160,200,255,0.7), 1000px 400px #fff, 250px 520px rgba(180,200,255,0.9), 600px 500px rgba(255,255,255,0.6), 850px 550px rgba(200,210,255,0.8), 130px 630px rgba(160,200,255,0.7), 950px 600px #fff;
  animation-delay: -2s;
}
@keyframes starTwinkle {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* 流星 */
.shooting-star {
  position: absolute; width: 2px; height: 2px; background: #fff; border-radius: 50%;
  box-shadow: 0 0 8px 2px rgba(255,255,255,0.8), 0 0 20px 6px rgba(0,212,255,0.4);
  animation: shoot linear infinite; opacity: 0;
}
.shooting-star::after {
  content: ''; position: absolute; top: 50%; right: 100%;
  width: 80px; height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.8));
  transform: translateY(-50%);
}
.shooting-star-1 { top: 15%; left: 60%; animation-duration: 4s; animation-delay: 0s; }
.shooting-star-2 { top: 25%; left: 30%; animation-duration: 5s; animation-delay: -2.5s; }
@keyframes shoot {
  0% { transform: translate(0, 0) rotate(-25deg); opacity: 0; }
  5% { opacity: 1; }
  15% { transform: translate(-300px, 180px) rotate(-25deg); opacity: 0; }
  100% { transform: translate(-300px, 180px) rotate(-25deg); opacity: 0; }
}

/* 网格 */
.grid-mesh {
  position: absolute; inset: 0;
  background-image: linear-gradient(rgba(0,212,255,0.06) 1px, transparent 1px), linear-gradient(90deg, rgba(0,212,255,0.06) 1px, transparent 1px);
  background-size: 60px 60px; background-position: center center;
  transform: perspective(500px) rotateX(60deg) scaleY(2.2);
  transform-origin: center bottom;
  animation: gridPulse 4s ease-in-out infinite;
}
@keyframes gridPulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

/* 扫描线 */
.scan-line {
  position: absolute; left: 0; right: 0; height: 2px;
  background: linear-gradient(90deg, transparent 0%, rgba(0,212,255,0.15) 20%, rgba(0,212,255,0.4) 50%, rgba(0,212,255,0.15) 80%, transparent 100%);
  top: 0; animation: scanDown 6s linear infinite;
  box-shadow: 0 0 20px rgba(0,212,255,0.3);
}
@keyframes scanDown {
  0% { top: -2px; }
  100% { top: 100%; }
}

/* 光晕 */
.glow-orb { position: absolute; border-radius: 50%; filter: blur(80px); opacity: 0.18; animation: orbFloat 8s ease-in-out infinite; }
.glow-orb-1 { width: 600px; height: 600px; background: radial-gradient(circle, rgba(64,150,255,0.4) 0%, rgba(0,180,240,0.2) 30%, transparent 70%); top: -250px; left: -150px; animation-delay: 0s; }
.glow-orb-2 { width: 550px; height: 550px; background: radial-gradient(circle, rgba(120,80,240,0.35) 0%, rgba(80,40,200,0.15) 30%, transparent 70%); bottom: -200px; right: -120px; animation-delay: -3s; }
.glow-orb-3 { width: 400px; height: 400px; background: radial-gradient(circle, rgba(0,200,220,0.3) 0%, rgba(0,160,200,0.12) 30%, transparent 70%); top: 45%; left: 60%; transform: translate(-50%, -50%); animation-delay: -6s; }
@keyframes orbFloat {
  0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.25; }
  33% { transform: translate(40px, -30px) scale(1.15); opacity: 0.45; }
  66% { transform: translate(-30px, 20px) scale(0.85); opacity: 0.18; }
}

/* 粒子 */
.particle { position: absolute; width: 2px; height: 2px; background: #00d4ff; border-radius: 50%; box-shadow: 0 0 6px #00d4ff, 0 0 14px #00d4ff; animation: particleRise linear infinite; opacity: 0; }
.particle-1 { left: 10%; animation-duration: 7s; animation-delay: 0s; }
.particle-2 { left: 25%; animation-duration: 9s; animation-delay: -2s; width: 3px; height: 3px; }
.particle-3 { left: 40%; animation-duration: 8s; animation-delay: -4s; }
.particle-4 { left: 55%; animation-duration: 10s; animation-delay: -1s; width: 3px; height: 3px; }
.particle-5 { left: 70%; animation-duration: 7.5s; animation-delay: -3s; }
.particle-6 { left: 85%; animation-duration: 8.5s; animation-delay: -5s; }
.particle-7 { left: 15%; animation-duration: 11s; animation-delay: -6s; }
.particle-8 { left: 60%; animation-duration: 9.5s; animation-delay: -7s; width: 3px; height: 3px; }
@keyframes particleRise {
  0% { top: 100%; opacity: 0; transform: translateX(0); }
  10% { opacity: 0.8; }
  90% { opacity: 0.2; }
  100% { top: -10%; opacity: 0; transform: translateX(30px); }
}

/* ========== 品牌头部 ========== */
.brand-header { position: relative; z-index: 10; text-align: center; margin-bottom: 32px; animation: brandIn 0.6s ease-out; }
@keyframes brandIn { from { opacity: 0; transform: translateY(-12px); } to { opacity: 1; transform: translateY(0); } }
.system-logo { margin-bottom: 10px; display: inline-block; filter: drop-shadow(0 0 16px rgba(0,212,255,0.3)); animation: logoGlow 3s ease-in-out infinite; }
@keyframes logoGlow {
  0%, 100% { filter: drop-shadow(0 0 16px rgba(0,212,255,0.3)); }
  50% { filter: drop-shadow(0 0 28px rgba(0,212,255,0.6)); }
}
.title-row { display: flex; align-items: center; justify-content: center; gap: 12px; margin-bottom: 4px; }
.system-title { font-size: 28px; font-weight: 800; color: #e8edf5; margin: 0; letter-spacing: 1px; text-shadow: 0 0 20px rgba(0,212,255,0.3); font-family: 'Segoe UI', 'PingFang SC', sans-serif; }
.oss-badge { display: inline-block; font-size: 10px; font-weight: 700; letter-spacing: 1.5px; padding: 3px 12px; border-radius: 12px; background: linear-gradient(135deg, rgba(22,200,100,0.2), rgba(0,200,120,0.15)); border: 1px solid rgba(22,200,100,0.35); color: #4ade80; text-shadow: 0 0 8px rgba(74,222,128,0.4); animation: badgePulse 2.5s ease-in-out infinite; }
@keyframes badgePulse { 0%, 100% { box-shadow: 0 0 8px rgba(74,222,128,0.2); } 50% { box-shadow: 0 0 18px rgba(74,222,128,0.45); } }
.system-subtitle { font-size: 13px; color: rgba(167,178,200,0.7); margin: 0; letter-spacing: 2px; }

/* ========== 主体双栏 ========== */
.content-area { position: relative; z-index: 10; display: flex; gap: 20px; align-items: stretch; max-width: 940px; width: 100%; animation: cardIn 0.6s ease-out; }
@keyframes cardIn { from { opacity: 0; transform: translateY(20px) scale(0.97); } to { opacity: 1; transform: translateY(0) scale(1); } }

/* ========== 登录卡片 ========== */
.login-card {
  position: relative; flex: 0 0 400px; max-width: 420px;
  background: rgba(18,25,52,0.65); backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px);
  border-radius: 16px; border: 1px solid rgba(0,212,255,0.12);
  box-shadow: 0 0 60px rgba(0,180,255,0.05), 0 8px 32px rgba(0,0,0,0.45), inset 0 1px 0 rgba(255,255,255,0.04);
  padding: 36px 40px; overflow: hidden;
}
.card-border-glow {
  position: absolute; top: 0; left: -100%; width: 100%; height: 1px;
  background: linear-gradient(90deg, transparent 0%, rgba(0,212,255,0.6) 50%, transparent 100%);
  animation: borderSweep 4s ease-in-out infinite;
}
@keyframes borderSweep { 0% { left: -100%; } 100% { left: 200%; } }
.login-card-title { font-size: 18px; font-weight: 600; color: #e8edf5; text-align: center; margin: 0 0 28px; letter-spacing: 2px; }

/* 输入框 */
:deep(.ant-input-affix-wrapper) { background: rgba(255,255,255,0.08) !important; border: 1px solid rgba(255,255,255,0.12) !important; border-radius: 8px !important; color: #e8edf5 !important; transition: all 0.3s ease; }
:deep(.ant-input-affix-wrapper:hover) { border-color: rgba(0,212,255,0.3) !important; box-shadow: 0 0 12px rgba(0,212,255,0.08); }
:deep(.ant-input-affix-wrapper:focus), :deep(.ant-input-affix-wrapper-focused) { border-color: rgba(0,212,255,0.5) !important; box-shadow: 0 0 16px rgba(0,212,255,0.15) !important; }
:deep(.ant-input) { background: transparent !important; color: #e8edf5 !important; }
:deep(.ant-input::placeholder) { color: rgba(167,178,200,0.5) !important; }
:deep(.ant-input-password-icon) { color: rgba(167,178,200,0.6) !important; }
:deep(.ant-input-password-icon:hover) { color: rgba(0,212,255,0.8) !important; }
:deep(.anticon) { color: rgba(167,178,200,0.5) !important; }
:deep(.ant-input-affix-wrapper-focused .anticon) { color: rgba(0,212,255,0.7) !important; }
:deep(.ant-form-item-explain-error) { color: rgba(255,77,79,0.8) !important; }

/* 登录按钮 */
:deep(.ant-btn-primary) {
  background: linear-gradient(135deg, #00d4ff 0%, #1677ff 100%) !important; border: none !important;
  border-radius: 8px !important; font-weight: 600; letter-spacing: 4px; height: 44px;
  transition: all 0.3s ease; box-shadow: 0 4px 20px rgba(0,212,255,0.25);
}
:deep(.ant-btn-primary:hover) { background: linear-gradient(135deg, #33ddff 0%, #3888ff 100%) !important; box-shadow: 0 4px 28px rgba(0,212,255,0.4); transform: translateY(-1px); }
:deep(.ant-btn-primary:active) { transform: translateY(0); }

/* 注册切换 */
.register-toggle { text-align: center; margin-top: 12px; padding-top: 12px; border-top: 1px solid rgba(0,212,255,0.08); }
.register-hint { font-size: 13px; color: rgba(167,178,200,0.5); margin-right: 6px; }
.register-link { font-size: 13px; color: rgba(0,212,255,0.7); cursor: pointer; transition: color 0.3s ease; text-decoration: none; }
.register-link:hover { color: rgba(0,212,255,1); text-decoration: underline; }
.register-subtitle { font-size: 12px; color: rgba(167,178,200,0.5); text-align: center; margin: -18px 0 20px; }
.register-card { flex: 0 0 440px; max-width: 460px; }

/* ========== PLM 能力卡片 ========== */
.plm-capability-card {
  flex: 1; min-width: 0;
  background: rgba(18,25,52,0.45); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border-radius: 16px; border: 1px solid rgba(74,222,128,0.12);
  box-shadow: 0 0 40px rgba(74,222,128,0.03), 0 8px 32px rgba(0,0,0,0.4);
  padding: 32px 28px; display: flex; flex-direction: column;
}
.capability-title { font-size: 16px; font-weight: 600; color: #e8edf5; margin: 0 0 4px; letter-spacing: 1px; }
.capability-desc { font-size: 12px; color: rgba(167,178,200,0.55); margin: 0 0 18px; }

/* 技术栈标签 */
.tech-tags { display: flex; gap: 5px; flex-wrap: wrap; margin-bottom: 20px; }
.tech-tag { font-size: 10px; padding: 2px 8px; border-radius: 4px; font-weight: 500; color: rgba(167,178,200,0.7); background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); }
.tech-tag.spring { color: #6db33f; background: rgba(109,179,63,0.1); border-color: rgba(109,179,63,0.25); }
.tech-tag.vue { color: #42d392; background: rgba(66,211,146,0.1); border-color: rgba(66,211,146,0.25); }
.tech-tag.pg { color: #4da6ff; background: rgba(77,166,255,0.1); border-color: rgba(77,166,255,0.25); }

/* 能力列表 */
.capability-list { display: flex; flex-direction: column; gap: 8px; flex: 1; }
.capability-item { display: flex; align-items: flex-start; gap: 12px; padding: 10px 14px; border-radius: 8px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.05); transition: all 0.3s ease; }
.capability-item:hover { background: rgba(74,222,128,0.05); border-color: rgba(74,222,128,0.18); transform: translateX(3px); }
.capability-icon { font-size: 18px; line-height: 1; flex-shrink: 0; margin-top: 1px; }
.capability-info { display: flex; flex-direction: column; gap: 1px; }
.capability-name { font-size: 12px; font-weight: 600; color: #e8edf5; letter-spacing: 0.3px; }
.capability-hint { font-size: 10px; color: rgba(167,178,200,0.5); }

/* 底部链接 */
.capability-footer { margin-top: 18px; padding-top: 14px; border-top: 1px solid rgba(255,255,255,0.06); display: flex; align-items: center; gap: 8px; font-size: 11px; }
.oss-link { display: inline-flex; align-items: center; gap: 4px; color: rgba(167,178,200,0.55); text-decoration: none; transition: color 0.3s ease; }
.oss-link:hover { color: rgba(74,222,128,0.8); }
.oss-link-divider { color: rgba(167,178,200,0.2); }

/* ========== 页脚 ========== */
.login-footer { position: relative; z-index: 10; margin-top: 24px; font-size: 12px; color: rgba(167,178,200,0.4); letter-spacing: 0.5px; }

/* ========== 响应式 ========== */
@media (max-width: 820px) {
  .content-area { flex-direction: column; align-items: center; max-width: 420px; }
  .login-card { flex: none; width: 100%; max-width: 100%; }
  .plm-capability-card { width: 100%; }
}
@media (max-width: 480px) {
  .login-card { padding: 28px 20px; }
  .plm-capability-card { padding: 24px 18px; }
  .system-title { font-size: 20px; }
  .oss-badge { font-size: 9px; padding: 2px 8px; }
  .capability-item { padding: 8px 10px; gap: 8px; }
  .capability-icon { font-size: 16px; }
  .capability-name { font-size: 11px; }
  .capability-hint { font-size: 9px; }
}
</style>
