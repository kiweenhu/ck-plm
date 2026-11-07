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

        <!--
          社区支持：企业侧登录被拦（未审核 / 已驳回 / 已暂停）时，这里直接承载原因，
          并把用户引向社区贡献者 —— toast 三秒就没了，用户来不及照着提示去联系人。
          平时也常驻，遇到审核或接入问题随时能找到人；二维码可点开看大图再扫。
        -->
        <div class="community-support" :class="{ 'is-blocked': tenantBlockMessage }">
          <a-image
            class="community-qr"
            :src="communityWechatQr"
            :width="tenantBlockMessage ? 80 : 44"
            :height="tenantBlockMessage ? 80 : 44"
            alt="社区微信二维码"
          />
          <div class="community-text">
            <p class="community-title">
              {{ tenantBlockMessage ? tenantBlockTitle : '企业审核遇到问题？' }}
            </p>
            <p class="community-desc">
              {{ tenantBlockMessage || '点击二维码，添加社区贡献者微信协助处理企业审核与接入问题' }}
            </p>
            <p v-if="tenantBlockMessage" class="community-action">
              扫码联系社区贡献者，协助跟进审核
            </p>
          </div>
        </div>

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
              placeholder="租户标识（如 ckai-tech.group）"
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
              <a-form-item name="contactPhone">
                <a-input
                  v-model:value="registerState.contactPhone"
                  size="large"
                  placeholder="手机号 / 固话"
                  allow-clear
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-form-item name="contactEmail">
            <a-input
              v-model:value="registerState.contactEmail"
              size="large"
              placeholder="联系邮箱"
              allow-clear
            />
          </a-form-item>

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

      <!--
        差异化特性卡：只保留 4 条核心特性，避免能力清单式罗列。
        标题写给"还没进来的人"看 —— 登录页不是产品手册，不说「关键特性」，而是回答选型时的第一问。
      -->
      <div class="plm-capability-card">
        <h3 class="capability-title">为什么选择 CK-PLM</h3>

        <div class="tech-tags">
          <span class="tech-tag spring">Spring Boot 3</span>
          <span class="tech-tag vue">Vue 3</span>
          <span class="tech-tag pg">PostgreSQL</span>
          <span class="tech-tag">MyBatis</span>
          <span class="tech-tag flowable">Flowable 7</span>
          <span class="tech-tag">BPMN 2.0</span>
        </div>

        <!-- 数据见 script 里的 capabilities：带色图标 + NEW 标记，避免 emoji 在各平台渲染不一致 -->
        <div class="capability-list">
          <div
            v-for="cap in capabilities"
            :key="cap.name"
            class="capability-item"
            :style="{ '--cap-color': cap.color }"
          >
            <span class="capability-icon"><component :is="cap.icon" /></span>
            <div class="capability-info">
              <span class="capability-name">
                {{ cap.name }}
                <em v-if="cap.isNew" class="capability-new">NEW</em>
              </span>
              <span class="capability-hint">{{ cap.hint }}</span>
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
          <span class="oss-link">v{{ appVersion }}</span>
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
import { computed, h, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  UserOutlined, LockOutlined,
  ApartmentOutlined, LayoutOutlined, AppstoreOutlined, GithubOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { login, registerTenant } from '@/api'
import { useUserStore } from '@/stores/user'
import { recordLogin } from '@/composables/useActivity'
// 社区站点（ckai-tech.group）页脚那张微信二维码，随包发布，避免登录页依赖外网图片
import communityWechatQr from '@/assets/community-wechat-qr.png'
// 路由层缓存的「行业初始化状态」在登录成功时必须清掉（见该函数注释）——
// 否则租户管理员在"审批通过前登录过"之后再登录，会拿旧状态直接进 home，要按 F5 才去引导页
import { resetOnboardingState } from '@/router'

const router = useRouter()
const userStore = useUserStore()

/** 版本号：构建期由 vite.config.js 的 define 注入（单一来源：frontend/package.json 的 version） */
const appVersion = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.2.0'

/**
 * 差异化特性（登录页只讲 4 条关键特性，不做全能力罗列）：
 *   1. 开源模式 —— 传统商业 PLM 是闭源黑盒 + 高额 License，CK-PLM 源码全开放（Open Core）
 *   2. 内置研发域管理体系与流程 —— 预置智能硬件（IPD）/ 汽车（APQP）/ 军工（构型管理）等行业方法论
 *   3. 多专业域与类型继承 —— 产品、架构、电子、结构、电气、软件 6 大业务域对象，软类型树开箱即用
 *   4. 零改表扩展 —— 传统 PLM 加字段/改页面要走二开和升级合并，CK-PLM 用低代码设计器 + IBA 直接搞定
 */
const capabilities = [
  { icon: GithubOutlined, color: '#4ade80', name: '真开源 · Apache-2.0', hint: '源码 100% 开放 · 告别闭源黑盒与天价 License' },
  { icon: AppstoreOutlined, color: '#f9a03f', name: '内置研发域管理体系与流程', hint: '智能硬件（IPD）· 汽车（APQP）· 军工（构型管理）等' },
  { icon: ApartmentOutlined, color: '#22d3ee', name: '多专业域与类型继承', hint: '产品、架构、电子、结构、电气、软件 6 大业务域对象 · 软类型树 · 新品类零代码接入', isNew: true },
  { icon: LayoutOutlined, color: '#a78bfa', name: '零改表低代码扩展', hint: '页面设计器编排列表/表单/详情 · IBA 扩展属性即配即用', isNew: true }
]

const formRef = ref()
const loading = ref(false)
const isRegistering = ref(false)

/**
 * 企业侧原因导致的登录阻断 —— 未审核 / 已驳回 / 已暂停 / 已停用。
 *
 * <p>后端统一用 code=403 + 中文文案返回（AuthController#describeTenantBlock / tenantStatusMessage），
 * 企业侧文案里必有「企业」或「租户」；「用户已被禁用 / 已被锁定」是账号自身问题，不在此列。
 */
const isTenantBlocked = (res) => res.code === 403 && /企业|租户/.test(res.message || '')

/** 企业未过审的提示文案：留在页面上，替代一闪而过的 toast，用户要照着它去联系社区贡献者 */
const tenantBlockMessage = ref('')

/** 按后端文案落回真实状态，避免把「已驳回 / 已暂停」也说成"还在审核中" */
const tenantBlockTitle = computed(() => {
  const msg = tenantBlockMessage.value
  if (msg.includes('审核中')) return '企业正在审核中'
  if (msg.includes('未通过审核')) return '企业审核未通过'
  if (msg.includes('暂停')) return '企业已暂停使用'
  if (msg.includes('停用')) return '企业已停用'
  return '企业暂时无法登录'
})

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
  tenantBlockMessage.value = ''
  try {
    // 关掉拦截器的自动 toast：登录页要自己在卡片里留一条持久提示（尤其是企业未过审时），
    // 否则同一条错误会弹两遍，且 toast 消失后用户就看不到该联系谁了
    const res = await login(formState.username.trim(), formState.password, { skipErrorToast: true })
    if (res.code === 200 && res.data) {
      userStore.setLogin(res.data)
      // 换身份进入系统：清掉路由层缓存的「行业初始化状态」—— 否则会拿上一个账号
      // 或"租户还没被审批通过时"的旧状态做判断，表现为「首次登录进了 home、按 F5 才去引导页」
      resetOnboardingState()
      message.success(`欢迎回来，${res.data.displayName || res.data.username}`)
      recordLogin(res.data.username)
      setTimeout(() => router.replace('/home'), 600)
    } else if (isTenantBlocked(res)) {
      tenantBlockMessage.value = res.message || '您的企业尚未通过平台审核'
    } else {
      message.error(res.message || '登录失败')
    }
  } finally {
    loading.value = false
  }
}

const registerFormRef = ref()
const registerLoading = ref(false)
const registerState = reactive({
  tenantId: '', name: '', contactName: '', contactEmail: '', contactPhone: '',
  adminUsername: '', adminPassword: ''
})

/**
 * 联系电话：11 位手机号 / 带区号的固话（可带分机）/ 400、800 热线。
 *
 * <p>与后端 {@code TenantServiceImpl#validateRequest} 保持同一条规则 —— 原来前后端都只校验
 * "只含数字和几个符号"，于是「1501259622」这样的 10 位号码也能提交。
 */
const PHONE_PATTERN = /^(?:1[3-9]\d{9}|0\d{2,3}-?\d{7,8}(?:-\d{1,6})?|(?:400|800)-?\d{3}-?\d{4})$/

const registerRules = {
  tenantId: [
    { required: true, message: '请输入租户标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9][a-zA-Z0-9._-]{1,48}$/, message: '2-49位，字母/数字/点/下划线/连字符', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入公司/组织名称', trigger: 'blur' }],
  contactEmail: [{ type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
  // 联系电话选填；填了就要是合法号码（空格忽略，方便按 3-4-4 分组输入）
  contactPhone: [{
    validator: (_rule, value) => {
      const phone = (value || '').replace(/\s/g, '')
      if (!phone) return Promise.resolve()
      return PHONE_PATTERN.test(phone)
        ? Promise.resolve()
        : Promise.reject('请输入有效联系电话（11 位手机号，或 0755-12345678 这样的固话）')
    },
    trigger: 'blur'
  }],
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
      // 提交前去掉分隔用的空格，存库统一成 11 位手机号或 0755-12345678 这种形态
      contactPhone: (registerState.contactPhone || '').replace(/\s/g, ''),
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
  /* 与右侧能力卡等高：内容垂直居中，避免表单下方留出大片空白 */
  display: flex; flex-direction: column; justify-content: center;
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

/* 社区支持（企业审核 / 接入问题）：常驻低调，企业没过审时转成警示态 */
.community-support {
  display: flex; align-items: center; gap: 12px;
  margin-top: 18px; padding: 12px;
  border-radius: 10px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(0,212,255,0.12);
  transition: all 0.3s ease;
}
.community-support.is-blocked {
  background: rgba(249,160,63,0.08);
  border-color: rgba(249,160,63,0.35);
  box-shadow: 0 0 18px -8px rgba(249,160,63,0.6);
}
/* a-image 会把 class 透到 <img> 上（不是外层 .ant-image），选择器要按 img 写 */
.community-qr { cursor: zoom-in; }
:deep(img.community-qr) { display: block; border-radius: 6px; background: #fff; }
/* 外层 .ant-image 是 flex 子项，默认会被文案挤压 → 二维码被横向压扁（扫不出来），必须禁止收缩 */
.community-support :deep(.ant-image) { flex-shrink: 0; }
.community-text { min-width: 0; }
.community-title { margin: 0 0 4px; font-size: 13px; font-weight: 600; color: #e8edf5; }
.community-support.is-blocked .community-title { color: #f9a03f; }
.community-desc { margin: 0; font-size: 11.5px; line-height: 1.5; color: rgba(167,178,200,0.6); }
.community-action { margin: 4px 0 0; font-size: 11.5px; line-height: 1.5; color: rgba(249,160,63,0.9); }

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
.capability-title { font-size: 16px; font-weight: 600; color: #e8edf5; margin: 0 0 18px; letter-spacing: 1px; }
.capability-desc { font-size: 12px; color: rgba(167,178,200,0.55); margin: 0 0 18px; }

/* 技术栈标签 */
.tech-tags { display: flex; gap: 5px; flex-wrap: wrap; margin-bottom: 16px; }
.tech-tag { font-size: 10px; padding: 2px 8px; border-radius: 4px; font-weight: 500; color: rgba(167,178,200,0.7); background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); }
.tech-tag.spring { color: #6db33f; background: rgba(109,179,63,0.1); border-color: rgba(109,179,63,0.25); }
.tech-tag.vue { color: #42d392; background: rgba(66,211,146,0.1); border-color: rgba(66,211,146,0.25); }
.tech-tag.pg { color: #4da6ff; background: rgba(77,166,255,0.1); border-color: rgba(77,166,255,0.25); }
.tech-tag.flowable { color: #f9a03f; background: rgba(249,160,63,0.1); border-color: rgba(249,160,63,0.25); }

/* 能力列表：每项自带主色 --cap-color（由模板按数据注入）；4 条精简版加大间距与内边距，避免卡片下半部留白 */
.capability-list { display: flex; flex-direction: column; gap: 12px; flex: 1; justify-content: center; }
.capability-item { display: flex; align-items: flex-start; gap: 12px; padding: 12px 14px; border-radius: 10px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.05); transition: all 0.3s ease; }
.capability-item:hover { background: rgba(255,255,255,0.06); border-color: var(--cap-color, rgba(74,222,128,0.18)); transform: translateX(3px); box-shadow: 0 0 20px -10px var(--cap-color, rgba(74,222,128,0.5)); }
/* 图标块：统一尺寸 + 主色描边，替代原来各平台渲染不一的 emoji */
.capability-icon {
  flex-shrink: 0; width: 26px; height: 26px; border-radius: 7px; margin-top: 1px;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 14px; color: var(--cap-color, #4ade80);
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08);
  box-shadow: inset 0 0 12px -6px var(--cap-color, #4ade80);
  transition: all 0.3s ease;
}
.capability-item:hover .capability-icon { border-color: var(--cap-color, #4ade80); box-shadow: 0 0 14px -5px var(--cap-color, #4ade80); }
.capability-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.capability-name { display: inline-flex; align-items: center; gap: 6px; font-size: 13.5px; font-weight: 600; color: #e8edf5; letter-spacing: 0.3px; }
.capability-new { font-style: normal; font-size: 9px; font-weight: 700; letter-spacing: 0.5px; line-height: 13px; padding: 0 5px; border-radius: 4px; color: #4ade80; background: rgba(74,222,128,0.12); border: 1px solid rgba(74,222,128,0.28); }
.capability-hint { font-size: 11.5px; line-height: 1.4; color: rgba(167,178,200,0.55); }

/* 底部链接 */
.capability-footer { margin-top: 14px; padding-top: 12px; border-top: 1px solid rgba(255,255,255,0.06); display: flex; align-items: center; gap: 8px; font-size: 11px; }
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
