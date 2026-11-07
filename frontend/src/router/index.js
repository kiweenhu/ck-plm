import { createRouter, createWebHistory } from 'vue-router'
import { verifyToken, getOnboardingState } from '@/api'
import { useUserStore } from '@/stores/user'

import MainLayout from '@/layout/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    // 行业初始化引导（租户管理员首登：选行业与研发方法）—— 独立全屏页，不进主框架
    path: '/onboarding',
    name: 'Onboarding',
    component: () => import('@/views/OnboardingPage.vue'),
    meta: { title: '行业初始化' }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/home',
    children: [
      // 首页
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/HomePage.vue'),
        meta: { title: '工作台' }
      },
      // 产品系列
      {
        path: 'product',
        name: 'ProductSeries',
        component: () => import('@/views/product/ProductSeries.vue'),
        meta: { title: '系列/型号' }
      },
      {
        path: 'product/:oid',
        name: 'ProductLineDashboard',
        component: () => import('@/views/product/ProductLineDashboard.vue'),
        meta: { title: '产品线管理' }
      },
      // 整机视图（系列 / 型号整体：上部驾驶舱 + 下部指标）—— 2026-10-11 从详情页尾拆出为独立页，
      // 入口在详情页页头「整机视图」按钮；与研发阶段无关（ck_metric / ck_dashboard 无 stage_key）。
      {
        path: 'product/:oid/overview',
        name: 'ProductOverview',
        component: () => import('@/views/product/ProductOverview.vue'),
        meta: { title: '整机视图' }
      },
      // 三维浏览：独立整页。装配体复杂时弹窗的可视区域与结构树宽度都不够，
      // 入口：产品数模列表的缩略图 / 「操作 → 三维浏览」。
      {
        path: 'product/cad-view/:iterationOid',
        name: 'CadViewer',
        component: () => import('@/views/product/CadViewerPage.vue'),
        meta: { title: '三维浏览' }
      },
      // 数模转换：2026-10-26 起并入「业务配置 → 轻量化转换」的第一个页签。
      // 这里只保留重定向 —— 旧的菜单外链、书签、聊天里贴过的地址都还指向 /cad/center，
      // 直接删路由会让人撞到空白页；跳过去至少落在同一个队列上。
      {
        path: 'cad/center',
        name: 'CadConvertCenter',
        redirect: { path: '/org/config', query: { module: 'lightweight', tab: 'queue' } }
      },
      {
        path: 'part/:oid/:iterationOid?',
        name: 'PartDetail',
        component: () => import('@/views/product/PartDetail.vue'),
        meta: { title: '零组件详情' }
      },
      // 企业资源
      {
        path: 'resource',
        name: 'EnterpriseResource',
        component: () => import('@/views/resource/EnterpriseResource.vue'),
        meta: { title: '企业资源' }
      },
      // MPN 厂商（制造商 / 供应商名录）—— MPN 管理的基础字典；显示名 2026-10-11 由「MPN 数据」定名。
      // 入口受 MPN_MANAGEMENT 能力控制（见 MainLayout 的菜单项），AML/AVL 仍在企业资源内按件挂。
      // meta.title 会拼进浏览器标签（见下方路由守卫），所以显示名必须与菜单、面包屑一致。
      {
        path: 'master-data',
        name: 'MasterData',
        component: () => import('@/views/resource/MasterDataPage.vue'),
        meta: { title: 'MPN 厂商' }
      },
      // 变更控制
      {
        path: 'change',
        name: 'ChangeControl',
        component: () => import('@/views/change/ChangeControl.vue'),
        meta: { title: '变更控制' }
      },
      // 持续改进
      {
        path: 'improve',
        name: 'ContinuousImprovement',
        component: () => import('@/views/improve/ContinuousImprovement.vue'),
        meta: { title: '持续改进' }
      },
      // 图片空间
      {
        path: 'media',
        name: 'MediaSpace',
        component: () => import('@/views/media/MediaSpace.vue'),
        meta: { title: '产品图册' }
      },
      // 分类管理
      {
        path: 'system/classifications',
        name: 'Classifications',
        component: () => import('@/views/system/ClassificationPage.vue'),
        meta: { title: '分类管理' }
      },
      // 操作日志（一级导航）
      {
        path: 'log',
        name: 'OperationLog',
        component: () => import('@/views/system/OperationLogPage.vue'),
        meta: { title: '操作日志' }
      },
      // 系统配置
      {
        path: 'system/basic',
        name: 'SystemBasic',
        component: () => import('@/views/system/BusinessConfig.vue'),
        meta: { title: '业务配置' }
      },
      {
        path: 'system/storage',
        name: 'FileStorageConfig',
        component: () => import('@/views/system/FileStorageConfig.vue'),
        meta: { title: '文件存储配置' }
      },
      {
        path: 'system/softtype',
        name: 'SoftType',
        component: () => import('@/views/system/TypePage.vue'),
        meta: { title: '模型配置' }
      },
      {
        path: 'system/designer',
        name: 'PageDesigner',
        component: () => import('@/views/system/PageDesigner.vue'),
        meta: { title: '页面设计器' }
      },
      {
        path: 'system/security',
        name: 'SystemSecurity',
        component: () => import('@/views/system/SystemPage.vue'),
        meta: { title: '安全策略' }
      },
      {
        path: 'system/tenants',
        name: 'TenantReview',
        component: () => import('@/views/system/TenantReview.vue'),
        meta: { title: '租户审核' }
      },
      {
        // 行业包：平台管理员查看各行业包的内容（行业 + 内嵌方法论）
        path: 'system/industry-packs',
        name: 'IndustryPacks',
        component: () => import('@/views/system/IndustryPackPage.vue'),
        meta: { title: '行业包' }
      },
      {
        // 问题反馈：平台管理员跨租户查看各租户用户提交的反馈，并回复到人（站内通知）
        path: 'system/feedback',
        name: 'FeedbackManage',
        component: () => import('@/views/system/FeedbackManage.vue'),
        meta: { title: '问题反馈' }
      },
      // 工作流
      // 「流程清单」已由新一代设计器承载（DSL + X6 画布，spec: docs/ck-plm-flow-designer-spec.md）：
      // 该页自带模板库（列表/新建/复制/启停/删除）+ 设计器 + 版本历史 + 部署，
      // 原先的 bpmn-js 实现与其 Activiti 属性面板已退役。
      {
        path: 'workflow/design',
        name: 'ProcessDesign',
        component: () => import('@/views/workflow/FlowDesignerPage.vue'),
        meta: { title: '流程清单' }
      },
      // 兼容旧链接（含带 ?templateOid= 的深链）：重定向到「流程清单」并保留查询参数
      {
        path: 'workflow/designer',
        redirect: (to) => ({ path: '/workflow/design', query: to.query })
      },
      {
        path: 'workflow/monitor',
        name: 'ProcessMonitor',
        component: () => import('@/views/workflow/ProcessMonitor.vue'),
        meta: { title: '流程监控' }
      },
      {
        // 流程实例详情：独立页面（此前是从监控列表弹的模态框，进度/变量在弹框里看不清）
        path: 'workflow/instance/:id',
        name: 'ProcessInstanceDetail',
        component: () => import('@/views/workflow/ProcessInstanceDetail.vue'),
        meta: { title: '流程详情' }
      },
      {
        path: 'workflow/task',
        name: 'TaskCenter',
        component: () => import('@/views/workflow/TaskCenter.vue'),
        meta: { title: '任务中心' }
      },
      // 任务办理：任务中心「办理」在新窗口打开的就是这个路径（独立页面，不是弹窗）
      {
        path: 'workflow/task/:id',
        name: 'TaskHandle',
        component: () => import('@/views/workflow/TaskHandle.vue'),
        meta: { title: '办理任务' }
      },
      // 通知中心（铃铛"查看全部"落这里；以前错落到租户审核页）
      {
        path: 'notifications',
        name: 'NotificationCenter',
        component: () => import('@/views/NotificationCenter.vue'),
        meta: { title: '通知公告' }
      },
      // 企业公告：与通知中心同一个组件，靠 meta 只显示公告（挂「企业组织」下，管理员可在此发布）
      {
        path: 'org/announcements',
        name: 'OrgAnnouncements',
        component: () => import('@/views/NotificationCenter.vue'),
        meta: { title: '企业公告', notificationType: 'ANNOUNCEMENT' }
      },
      // 企业组织
      {
        path: 'org/users',
        name: 'OrgUsers',
        component: () => import('@/views/org/UserManage.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'org/roles',
        name: 'OrgRoles',
        component: () => import('@/views/org/OrgPage.vue'),
        meta: { title: '角色定义' }
      },
      {
        path: 'org/info',
        name: 'OrgInfo',
        component: () => import('@/views/org/TenantInfo.vue'),
        meta: { title: '企业信息' }
      },
      {
        path: 'org/dept',
        name: 'OrgDept',
        component: () => import('@/views/org/OrgPage.vue'),
        meta: { title: '部门架构' }
      },
      {
        path: 'org/admins',
        name: 'AdminManager',
        component: () => import('@/views/org/AdminManager.vue'),
        meta: { title: '角色成员' }
      },
      {
        path: 'org/config',
        name: 'OrgBusinessConfig',
        component: () => import('@/views/system/BusinessConfig.vue'),
        meta: { title: '业务配置' }
      },
      // 个人信息
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/ProfilePage.vue'),
        meta: { title: '个人信息' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 标记是否已完成首次 token 验证 */
let verified = false

/** 需要平台管理员角色的路由 */
const PLATFORM_ADMIN_ROUTES = ['/system/tenants', '/system/industry-packs', '/system/feedback']

/**
 * 需要 ADMIN（租户管理员）角色的路由 —— 业务配置 / 流程管理相关。
 *
 * <p>`/workflow/design`（流程清单）在内：它是**流程模板的设计与部署**入口，
 * 与业务配置同级（菜单里也只对租户管理员显示，见 MainLayout）。
 * `/workflow/monitor`（流程监控）同样在内：那是全公司实例清单 + 挂起/终止/删除。
 * 只隐菜单不够 —— 手输 URL 或旧书签同样要拦住。
 *
 * <p><b>刻意不在名单里</b>：`/workflow/instance/:id`（流程实例详情）。
 * 它除了从流程监控进，也是产品详情里评审行「查看流程」的落点，
 * 普通成员（评审参与人）本来就要看 —— 拦了会把人挡在门外。
 */
const ADMIN_ROUTES = [
  '/system/classifications',
  '/system/basic',
  '/system/softtype',
  '/system/designer',
  '/system/security',
  '/system/storage',
  '/org/users',
  '/org/roles',
  '/org/dept',
  '/org/admins',
  '/org/config',
  '/workflow/design',
  '/workflow/monitor'
]

/** 行业初始化状态缓存（短期复用；登录后强制重取、从引导页离开时强制重取，见下） */
let onboardingState = null
/** 上次取到的时刻（用于短 TTL —— 审批通过这类"系统外部变化"靠它兜住） */
let onboardingFetchedAt = 0
/**
 * 缓存的存活时长。
 *
 * <p>为什么需要 TTL（2026-08-13 用户报的 bug）：租户管理员在**审批通过前**登录过一次，
 * 那时取到的状态（未进入引导流程）被缓存；平台审批通过后他在**同一浏览器会话**里重新登录
 * （SPA 内导航，页面没刷新 → 模块缓存还在）→ 守卫拿到旧缓存直接放进 home，
 * 必须按 F5（清空内存）才会重新请求、跳去引导页。登录后强制重取是主修（见 resetOnboardingState），
 * TTL 兜的是"登录后平台才审批通过"这类同会话内的变化。
 */
const ONBOARDING_TTL_MS = 30 * 1000

/**
 * 清空引导状态缓存 —— **登录成功后必须调用**（Login.vue）。
 *
 * <p>登录是"换身份进入系统"的动作：此前缓存的可能是别的账号、或本账号在
 * "租户还没被审批通过"时的状态。不清就会出现"要按 F5 才进引导页"。
 */
export function resetOnboardingState() {
  onboardingState = null
  onboardingFetchedAt = 0
}

/**
 * 读取租户行业初始化状态（带缓存）。
 * 未登录或接口异常时返回 null 并放行 —— 引导拦截绝不能把"没登录"误判成"未引导"，
 * 否则用户会被挡在登录页之外。
 */
async function loadOnboardingState(force) {
  const fresh = onboardingState && (Date.now() - onboardingFetchedAt) < ONBOARDING_TTL_MS
  if (fresh && !force) return onboardingState
  try {
    const res = await getOnboardingState()
    if (res.code === 200) {
      onboardingState = res.data
      onboardingFetchedAt = Date.now()
    }
  } catch (e) {
    /* 放行：交给下面的登录校验处理 */
  }
  return onboardingState
}

/**
 * 行业初始化拦截（返回 true 表示已 next()，调用方直接 return）：
 * <ul>
 *   <li>审核通过 ≠ 引导完成：未完成时，管理员去 /onboarding 选行业，普通成员在那看到等待页；</li>
 *   <li>已完成却停在引导页 → 回首页；</li>
 *   <li>从 /onboarding 离开时强制重取状态 —— 引导可能刚刚完成，缓存会把人弹回引导页。</li>
 * </ul>
 */
async function guardOnboarding(to, from, next) {
  const st = await loadOnboardingState(from && from.path === '/onboarding')
  if (!st) return false
  if (st.needsOnboarding && to.path !== '/onboarding') {
    next('/onboarding')
    return true
  }
  if (!st.needsOnboarding && to.path === '/onboarding') {
    next('/home')
    return true
  }
  return false
}

/* 路由守卫 —— 首次进入时用后端 verify 接口校验 localStorage token */
router.beforeEach(async (to, from, next) => {
  document.title = (to.meta.title || '乘恺科技 PLM') + ' - CK-PLM Platform'

  // 行业初始化引导：未完成先引导，再谈用系统（登录页除外）
  if (to.path !== '/login') {
    if (await guardOnboarding(to, from, next)) return
  }

  // 首次验证：localStorage 有 token → 调用后端 verify 接口确认有效性
  if (!verified) {
    const token = localStorage.getItem('token')
    if (token) {
      try {
        const res = await verifyToken()
        if (res.code === 200 && res.data) {
          const userStore = useUserStore()
          userStore.setLogin(res.data)
          verified = true
          if (to.path === '/login') {
            next('/home')
            return
          }
          // 检查平台管理员路由权限
          if (PLATFORM_ADMIN_ROUTES.includes(to.path) && !isPlatformAdmin()) {
            next('/home')
            return
          }
          // 检查 ADMIN（租户管理员）路由权限
          if (ADMIN_ROUTES.includes(to.path) && !isTenantAdmin()) {
            next('/home')
            return
          }
          next()
          return
        }
      } catch {
        // verify 失败（网络异常等），也视为 token 无效
      }
      // token 无效，清理并跳转登录
      const userStore = useUserStore()
      userStore.clearLogin()
      verified = true
      if (to.path !== '/login') {
        next('/login')
        return
      }
      next()
      return
    }
    verified = true
  }

  const storeToken = localStorage.getItem('token')
  // 未登录 → 非登录页则重定向
  if (to.path !== '/login' && !storeToken) {
    next('/login')
    return
  }
  // 已登录 → 登录页则重定向首页
  if (to.path === '/login' && storeToken) {
    next('/home')
    return
  }
  // 检查平台管理员路由权限
  if (PLATFORM_ADMIN_ROUTES.includes(to.path) && !isPlatformAdmin()) {
    next('/home')
    return
  }
  // 检查 ADMIN（租户管理员）路由权限
  if (ADMIN_ROUTES.includes(to.path) && !isTenantAdmin()) {
    next('/home')
    return
  }
  next()
})

/**
 * 判断当前用户是否为平台管理员（拥有 PLATFORM_ADMIN 角色）。
 */
function isPlatformAdmin() {
  const userStore = useUserStore()
  return userStore.roles && userStore.roles.includes('PLATFORM_ADMIN')
}

/**
 * 判断当前用户是否为租户管理员（拥有 TENANT_ADMIN/ADMIN 角色）。
 * 平台管理员（PLATFORM_ADMIN）也自动拥有租户管理员权限。
 */
function isTenantAdmin() {
  const userStore = useUserStore()
  return userStore.roles && (
    userStore.roles.includes('TENANT_ADMIN') || userStore.roles.includes('ADMIN') || userStore.roles.includes('PLATFORM_ADMIN')
  )
}

// 路由变化时自动记录最近访问
router.afterEach((to) => {
  if (to.path === '/login' || to.path === '/home') return
  const title = to.meta?.title || to.name || to.path
  let type = '系统'
  if (to.path.startsWith('/product')) type = '产品系列'
  else if (to.path.startsWith('/resource')) type = '企业资源'
  else if (to.path.startsWith('/change')) type = '变更单'
  import('@/composables/useActivity').then(({ recordAccess }) => {
    recordAccess({ name: title, type, path: to.path })
  })
})

export default router
