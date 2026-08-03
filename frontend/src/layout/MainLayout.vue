<template>
  <a-layout class="main-layout">
    <!-- ===== 侧边栏 ===== -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      :trigger="null"
      collapsible
      class="layout-sider"
      width="220"
    >
      <!-- Logo 区域 -->
      <div class="sider-logo" @click="goHome">
        <img src="@/assets/logo-icon.png" alt="logo" class="logo-img" />
        <span v-show="!collapsed" class="logo-text">乘恺科技&copy;</span>
      </div>

      <!-- 导航菜单 -->
      <a-menu
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        mode="inline"
        theme="dark"
        class="sider-menu"
        @click="handleMenuClick"
      >
        <!-- 个人中心 -->
        <a-menu-item key="/home">
          <template #icon><home-outlined /></template>
          <span>个人中心</span>
        </a-menu-item>

        <!-- 企业资源 -->
        <a-menu-item key="/resource">
          <template #icon><database-outlined /></template>
          <span>企业资源</span>
        </a-menu-item>

        <!-- 系列/型号 -->
        <a-menu-item key="/product">
          <template #icon><appstore-outlined /></template>
          <span>系列/型号</span>
        </a-menu-item>

        <!-- 变更控制 -->
        <a-menu-item key="/change">
          <template #icon><branches-outlined /></template>
          <span>变更控制</span>
        </a-menu-item>

        <!-- 持续改进 -->
        <a-menu-item key="/improve">
          <template #icon><rise-outlined /></template>
          <span>持续改进</span>
        </a-menu-item>

        <!-- 分类管理（仅租户管理员可见） -->
        <a-menu-item key="/system/classifications" v-if="isTenantAdmin">
          <template #icon><folder-outlined /></template>
          <span>分类管理</span>
        </a-menu-item>

        <!-- 业务配置（仅租户管理员可见） -->
        <a-menu-item key="/org/config" v-if="isTenantAdmin">
          <template #icon><setting-outlined /></template>
          <span>业务配置</span>
        </a-menu-item>

        <!-- 企业组织（仅 ADMIN/平台管理员可见） -->
        <a-sub-menu key="org" v-if="isTenantAdmin">
          <template #icon><team-outlined /></template>
          <template #title>企业组织</template>
          <a-menu-item key="/org/info">企业信息</a-menu-item>
          <a-menu-item key="/org/dept">部门架构</a-menu-item>
          <a-menu-item key="/org/users">用户管理</a-menu-item>
          <a-menu-item key="/org/roles">角色定义</a-menu-item>
          <a-menu-item key="/org/admins">角色成员</a-menu-item>
        </a-sub-menu>

        <!-- 工作流 -->
        <a-sub-menu key="workflow">
          <template #icon><apartment-outlined /></template>
          <template #title>工作流</template>
          <a-menu-item key="/workflow/task">任务中心</a-menu-item>
          <a-menu-item key="/workflow/monitor">流程监控</a-menu-item>
          <a-menu-item key="/workflow/design">流程清单</a-menu-item>
        </a-sub-menu>

        <!-- 操作日志（所有登录用户可见） -->
        <a-menu-item key="/log">
          <template #icon><file-text-outlined /></template>
          <span>操作日志</span>
        </a-menu-item>

        <!-- 系统配置（仅平台管理员可见） -->
        <a-sub-menu key="system" v-if="isPlatformAdmin">
          <template #icon><setting-outlined /></template>
          <template #title>系统配置</template>
          <a-menu-item key="/system/basic">业务配置</a-menu-item>
          <a-menu-item key="/system/storage">文件存储</a-menu-item>
          <a-menu-item key="/system/security">安全策略</a-menu-item>
          <a-menu-item key="/system/tenants">租户审核</a-menu-item>
        </a-sub-menu>
      </a-menu>

      <!-- 租户信息 -->
      <div class="sider-author">
        <div v-if="tenantDisplayName" class="tenant-name">{{ tenantDisplayName }}</div>
      </div>
    </a-layout-sider>

    <!-- ===== 右侧主区域 ===== -->
    <a-layout>
      <!-- 顶栏 -->
      <a-layout-header class="layout-header">
        <div class="header-left">
          <menu-fold-outlined
            v-if="!collapsed"
            class="collapse-btn"
            @click="collapsed = true"
          />
          <menu-unfold-outlined
            v-else
            class="collapse-btn"
            @click="collapsed = false"
          />
          <a-breadcrumb class="header-breadcrumb">
            <a-breadcrumb-item
              v-for="item in breadcrumbs"
              :key="item"
            >{{ item }}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <div class="header-right">
          <a-dropdown :trigger="['click']" v-if="notifications.length > 0">
            <a-badge :count="unreadCount" :overflow-count="99" size="small" class="header-badge">
              <bell-outlined class="header-icon" />
            </a-badge>
            <template #overlay>
              <div class="notif-dropdown">
                <div class="notif-header">
                  <span>通知</span>
                  <a @click="handleMarkAllRead">全部已读</a>
                </div>
                <a-menu class="notif-menu" @click="handleNotifClick">
                  <a-menu-item v-for="n in notifications.slice(0, 8)" :key="n.oid">
                    <div class="notif-item" :class="{ unread: !n.isRead }">
                      <div class="notif-dot" v-if="!n.isRead"></div>
                      <div class="notif-body">
                        <div class="notif-title">{{ n.title }}</div>
                        <div class="notif-content">{{ n.content }}</div>
                        <div class="notif-time">{{ n.createdAt }}</div>
                      </div>
                    </div>
                  </a-menu-item>
                </a-menu>
                <div class="notif-footer" v-if="notifications.length > 8">
                  <router-link to="/system/tenants">查看全部</router-link>
                </div>
              </div>
            </template>
          </a-dropdown>
          <a-badge :count="unreadCount" :overflow-count="99" size="small" class="header-badge" v-else>
            <bell-outlined class="header-icon" @click="goTenantReview" />
          </a-badge>
          <a-dropdown>
            <span class="user-avatar">
              <a-avatar size="small" :style="{ backgroundColor: '#00d4ff' }">
                {{ userStore.userDisplayName.charAt(0) }}
              </a-avatar>
              <span class="user-name">{{ userStore.userDisplayName }}</span>
              <down-outlined class="user-arrow" />
            </span>
            <template #overlay>
              <a-menu @click="handleUserMenu">
                <a-menu-item key="profile">
                  <user-outlined /> 个人信息
                </a-menu-item>
                <a-menu-item key="settings">
                  <setting-outlined /> 个人设置
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout">
                  <logout-outlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>

      <!-- 内容区 -->
      <a-layout-content class="layout-content">
        <router-view />
      </a-layout-content>

      <!-- 版权声明 -->
      <a-layout-footer class="layout-footer">
        深圳市乘恺科技有限公司 2026~2029
      </a-layout-footer>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { message } from 'ant-design-vue'
import { logout, getUnreadCount, getNotifications, markNotifRead, markAllNotifRead, getCurrentTenant } from '@/api'
import { recordLogout } from '@/composables/useActivity'
import {
  HomeOutlined, AppstoreOutlined, DatabaseOutlined,
  BranchesOutlined, RiseOutlined, TeamOutlined,
  SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined,
  BellOutlined, DownOutlined, UserOutlined, LogoutOutlined,
  ApartmentOutlined, FolderOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 初始化 user store
userStore.init()

const collapsed = ref(false)

/** 是否为平台管理员 */
const isPlatformAdmin = computed(() => {
  return userStore.roles && userStore.roles.includes('PLATFORM_ADMIN')
})

/** 是否为租户管理员（TENANT_ADMIN/ADMIN 角色或平台管理员） */
const isTenantAdmin = computed(() => {
  return userStore.roles && (
    userStore.roles.includes('TENANT_ADMIN') || userStore.roles.includes('ADMIN') || userStore.roles.includes('PLATFORM_ADMIN')
  )
})

/** 租户展示名称（优先用 store 中的 tenantName，其次用 tenantOid） */
const tenantDisplayName = computed(() => {
  return userStore.tenantName || userStore.tenantOid || ''
})

// ---- 菜单选中 ----
const selectedKeys = ref(['/home'])
const openKeys = ref([])

// 根据当前路由同步菜单选中
watch(() => route.path, (path) => {
  selectedKeys.value = [path]
  // 自动展开父菜单
  if (path.startsWith('/org/')) openKeys.value = ['org']
  else if (path.startsWith('/system/classifications')) openKeys.value = []
  else if (path.startsWith('/system/')) openKeys.value = ['system']
  else if (path.startsWith('/workflow/')) openKeys.value = ['workflow']
  else openKeys.value = []
}, { immediate: true })

// ---- 面包屑 ----
const breadcrumbMap = {
  '/home': '个人中心',
  '/product/:productId': '系列/型号',
  '/resource': '企业资源',
  '/change': '变更控制',
  '/improve': '持续改进',
  '/media': '产品图册',
  '/workflow/design': '工作流 / 流程清单',
  '/workflow/monitor': '工作流 / 流程监控',
  '/workflow/task': '工作流 / 任务中心',
  '/org/users': '企业组织 / 用户管理',
  '/org/roles': '企业组织 / 角色定义',
  '/org/info': '企业组织 / 企业信息',
  '/org/dept': '企业组织 / 部门架构',
  '/org/admins': '企业组织 / 角色成员',
  '/org/config': '业务配置',
  '/system/basic': '系统配置 / 业务配置',
  '/system/storage': '系统配置 / 文件存储',
  '/system/security': '系统配置 / 安全策略',
  '/system/designer': '业务配置 / 页面设计器',
  '/system/tenants': '系统配置 / 租户审核',
  '/log': '操作日志',
  '/profile': '个人信息'
}

const breadcrumbs = computed(() => {
  // 先精确匹配，再前缀匹配（支持带参数路由如 /product/:productId）
  let text = breadcrumbMap[route.path]
  if (!text) {
    const matched = Object.entries(breadcrumbMap).find(([key]) =>
      key.includes(':') && route.path.startsWith(key.split(':')[0])
    )
    if (matched) text = matched[1]
  }
  return text ? text.split(' / ') : []
})

// ---- 菜单点击 ----
function handleMenuClick({ key }) {
  router.push(key)
}

function goHome() {
  router.push('/home')
}

// ---- 通知系统 ----
const unreadCount = ref(0)
const notifications = ref([])
let pollTimer = null

const fetchUnreadCount = async () => {
  try { const res = await getUnreadCount(); if (res.code === 200) unreadCount.value = res.data || 0 } catch {}
}
const fetchNotifications = async () => {
  try { const res = await getNotifications(10); if (res.code === 200) notifications.value = res.data || [] } catch {}
}
const handleNotifClick = async ({ key }) => {
  const n = notifications.value.find(x => x.oid === key)
  if (n && !n.isRead) { await markNotifRead(key); await fetchUnreadCount() }
  if (n && (n.type === 'TENANT_REGISTRATION' || n.targetType === 'TENANT')) router.push('/system/tenants')
}
const handleMarkAllRead = async (e) => {
  e?.preventDefault?.()
  await markAllNotifRead()
  unreadCount.value = 0
  await fetchNotifications()
}
const goTenantReview = () => router.push('/system/tenants')

onMounted(async () => {
  // 获取当前租户名称（非平台租户时显示）
  try {
    const res = await getCurrentTenant()
    if (res.code === 200 && res.data) {
      userStore.tenantOid = res.data.tenantId || res.data.oid || ''
      userStore.tenantName = res.data.name || ''
      // 同步到 localStorage
      const stored = localStorage.getItem('user')
      if (stored) {
        try {
          const parsed = JSON.parse(stored)
          parsed.tenantOid = userStore.tenantOid
          parsed.tenantName = userStore.tenantName
          localStorage.setItem('user', JSON.stringify(parsed))
        } catch { /* ignore */ }
      }
    }
  } catch { /* ignore */ }
  fetchUnreadCount()
  pollTimer = setInterval(fetchUnreadCount, 30000)
})
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

// ---- 用户菜单 ----
async function handleUserMenu({ key }) {
  if (key === 'logout') {
    const username = userStore.user?.username || ''
    recordLogout(username)
    try {
      await logout()
    } catch { /* ignore */ }
    userStore.clearLogin()
    message.success('已退出登录')
    router.replace('/login')
  } else if (key === 'profile') {
    router.push('/profile')
  } else if (key === 'settings') {
    message.info('个人设置功能开发中')
  }
}
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
}

/* ===== 侧边栏 ===== */
.layout-sider {
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
  z-index: 10;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  position: relative;
}

.sider-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.sider-logo:hover {
  background: rgba(255, 255, 255, 0.05);
}

.logo-img {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.logo-text {
  color: #e8edf5;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  letter-spacing: 1px;
}

.sider-menu {
  border-inline-end: none !important;
  margin-top: 4px;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.sider-author {
  position: absolute;
  bottom: 16px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.35);
  letter-spacing: 1px;
  white-space: nowrap;
}

.tenant-name {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
  font-weight: 500;
  margin-bottom: 2px;
}

.author-text {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.25);
}

/* ===== 顶栏 ===== */
.layout-header {
  background: #fff !important;
  padding: 0 24px !important;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px !important;
  line-height: 56px !important;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  z-index: 9;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 18px;
  cursor: pointer;
  color: #595959;
  transition: color 0.2s;
}

.collapse-btn:hover {
  color: #1677ff;
}

.header-breadcrumb {
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-badge {
  cursor: pointer;
}

.header-icon {
  font-size: 18px;
  color: #8c8c8c;
  transition: color 0.2s;
}

.header-icon:hover {
  color: #1677ff;
}

/* ---- 通知下拉 ---- */
.notif-dropdown {
  width: 360px;
  max-height: 420px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
  font-weight: 600;
}

.notif-header a {
  font-size: 12px;
  font-weight: 400;
  color: #1677ff;
  cursor: pointer;
}

.notif-menu {
  border: none !important;
  max-height: 340px;
  overflow-y: auto;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 4px 0;
  white-space: normal;
}

.notif-item.unread {
  font-weight: 500;
}

.notif-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1677ff;
  margin-top: 6px;
  flex-shrink: 0;
}

.notif-body {
  flex: 1;
  min-width: 0;
}

.notif-title {
  font-size: 13px;
  color: #262626;
  line-height: 1.4;
}

.notif-content {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}

.notif-time {
  font-size: 11px;
  color: #bfbfbf;
  margin-top: 2px;
}

.notif-footer {
  padding: 8px 16px;
  text-align: center;
  border-top: 1px solid #f0f0f0;
}

.notif-footer a {
  font-size: 12px;
  color: #1677ff;
}

.user-avatar {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}

.user-avatar:hover {
  background: #f0f0f0;
}

.user-name {
  font-size: 14px;
  color: #262626;
}

.user-arrow {
  font-size: 10px;
  color: #8c8c8c;
}

/* ===== 内容区 ===== */
.layout-content {
  margin: 16px;
  padding: 24px;
  background: #fff;
  border-radius: 8px;
  min-height: calc(100vh - 56px - 32px);
  overflow: auto;
}

/* ===== 路由过渡动画 ===== */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.25s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* ===== 版权声明 ===== */
.layout-footer {
  text-align: center;
  font-size: 12px;
  color: #999;
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
</style>
