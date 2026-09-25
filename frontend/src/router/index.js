import { createRouter, createWebHistory } from 'vue-router'
import { verifyToken } from '@/api'
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
    path: '/',
    component: MainLayout,
    redirect: '/home',
    children: [
      // 首页
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/HomePage.vue'),
        meta: { title: '个人中心' }
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
        meta: { title: '产品线管业' }
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
const PLATFORM_ADMIN_ROUTES = ['/system/tenants']

/** 需要 ADMIN（租户管理员）角色的路由 —— 业务配置相关 */
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
  '/org/config'
]

/* 路由守卫 —— 首次进入时用后端 verify 接口校验 localStorage token */
router.beforeEach(async (to, from, next) => {
  document.title = (to.meta.title || '乘恺科技 PLM') + ' - 完全AI编码的PLM'

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
