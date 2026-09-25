<template>
  <a-layout class="main-layout">
    <!-- ===== 侧边栏 ===== -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      :trigger="null"
      collapsible
      class="layout-sider"
      width="150"
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
          <!-- 企业公告：与通知中心同一组件（只显示 ANNOUNCEMENT），管理员可在此发布 -->
          <a-menu-item key="/org/announcements">企业公告</a-menu-item>
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
    <a-layout class="right-area">
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
        <div class="header-search">
          <a-auto-complete
            v-model:value="searchKeyword"
            :options="searchOptions"
            :filter-option="false"
            placeholder="搜索产品/零组件/文档（名称或编码）"
            allow-clear
            @search="onSearchInput"
            @select="onSearchSelect"
          >
            <template #option="option">
              <div class="search-option">
                <a-tag :color="searchTypeColor(option.type)" size="small">{{ searchTypeLabel(option.type) }}</a-tag>
                <span class="search-code">{{ option.code }}</span>
                <span class="search-name">{{ option.name }}</span>
              </div>
            </template>
            <template #notFoundContent>
              <div style="padding: 8px 12px; color: #8c8c8c; font-size: 13px;">无匹配结果</div>
            </template>
          </a-auto-complete>
        </div>
        <div class="header-right">
          <!-- CK-PLM 助手：用聊天的方式查系统数据（工具以当前用户权限执行） -->
          <AiAssistant />

          <!--
            最新消息滚动条：不点开铃铛也能看到"有什么新消息"。
            显示「标题：正文摘要」——只给标题的话，"系统升级公告"这种标题看不出到底说了什么。
            **只放未读且 6 小时以内的**（见 utils/notifications 的 isTickerItem）：
            过期的公告一直滚到被点开为止，会盖住真正的新消息。
            有内容才滚动，没有就静态显示一句话（含规则说明）—— 空白会让人以为这块坏了。
            悬停暂停（否则想看清时它正好滚走了），点击进通知中心。
          -->
          <a-tooltip :title="tickerTooltip">
            <div class="notif-ticker" @click="goNotifications"
              @mouseenter="tickerPaused = true" @mouseleave="tickerPaused = false">
              <SoundOutlined class="notif-ticker-icon" />
              <div class="notif-ticker-view">
                <div class="notif-ticker-track" :class="{ 'is-static': !tickerScrolling }"
                  :style="tickerScrolling
                    ? { animationDuration: tickerDuration, animationPlayState: tickerPaused ? 'paused' : 'running' }
                    : null">
                  <span class="notif-ticker-text">{{ tickerText }}</span>
                  <!-- 第二份只在滚动时存在：静态时它永远在裁剪区外，白占 DOM 还会被读屏重复念一遍 -->
                  <span v-if="tickerScrolling" class="notif-ticker-text">{{ tickerText }}</span>
                </div>
              </div>
            </div>
          </a-tooltip>
          <!-- 铃铛常驻：没有通知时也要能点开看"暂无通知"，而不是点了跳去租户审核页 -->
          <a-dropdown :trigger="['click']" @visible-change="onBellOpen">
            <a-badge :count="unreadCount" :overflow-count="99" size="small" class="header-badge">
              <bell-outlined class="header-icon" />
            </a-badge>
            <template #overlay>
              <div class="notif-dropdown">
                <div class="notif-header">
                  <span>通知</span>
                  <a v-if="unreadCount > 0" @click="handleMarkAllRead">全部已读</a>
                </div>
                <a-menu class="notif-menu" @click="handleNotifClick">
                  <a-menu-item v-for="n in notifications.slice(0, 6)" :key="n.oid">
                    <div class="notif-item" :class="{ unread: !n.isRead }">
                      <div class="notif-dot" v-if="!n.isRead"></div>
                      <div class="notif-body">
                        <div class="notif-title">{{ n.title }}</div>
                        <div class="notif-content">{{ n.content }}</div>
                        <!-- 相对时间：下拉里"3 小时前"比一串 ISO 时间有用得多 -->
                        <div class="notif-time">{{ notifRelativeTime(n.createdAt) }}</div>
                      </div>
                    </div>
                  </a-menu-item>
                  <div v-if="notifications.length === 0"
                    style="padding: 14px 16px; text-align: center; color: #8c8c8c; font-size: 13px;">
                    暂无通知
                  </div>
                </a-menu>
                <div class="notif-footer">
                  <router-link to="/notifications">查看全部</router-link>
                </div>
              </div>
            </template>
          </a-dropdown>
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
        © 2026 - 2029 乘恺科技 · 保留所有权利
      </a-layout-footer>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { message } from 'ant-design-vue'
import { logout, getUnreadCount, getNotifications, markNotifRead, markAllNotifRead, getCurrentTenant, globalSearch } from '@/api'
// 通知的"点了去哪"和相对时间与通知中心共用一份，避免两处各写一套后行为不一致
import {
  notifLink, notifRelativeTime, NOTIF_CHANGED_EVENT,
  notifTickerText, notifTickerTooltip, notifTickerShouldScroll,
} from '@/utils/notifications'
import AiAssistant from '@/components/AiAssistant.vue'
import { recordLogout } from '@/composables/useActivity'
import {
  HomeOutlined, AppstoreOutlined, DatabaseOutlined,
  BranchesOutlined, RiseOutlined, TeamOutlined,
  SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined,
  BellOutlined, DownOutlined, UserOutlined, LogoutOutlined,
  ApartmentOutlined, FolderOutlined,
  FileTextOutlined, SoundOutlined
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
  // 带参数的任务办理页（前缀匹配：/workflow/task/{id}）
  '/workflow/task/:id': '工作流 / 任务中心 / 办理任务',
  // 带参数的流程实例详情（前缀匹配：/workflow/instance/{id}）
  '/workflow/instance/:id': '工作流 / 流程监控 / 流程详情',
  '/org/users': '企业组织 / 用户管理',
  '/org/roles': '企业组织 / 角色定义',
  '/org/announcements': '企业组织 / 企业公告',
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
  // 取 20 条（铃铛只显示前 6 条）：顶栏滚动条要在其中挑"未读且 6 小时内的"，
  // 只取 10 条时容易被一串刚点开的已读挤掉，真正的新消息反而进不了滚动条
  try { const res = await getNotifications(20); if (res.code === 200) notifications.value = res.data || [] } catch {}
}
const handleNotifClick = async ({ key }) => {
  const n = notifications.value.find(x => x.oid === key)
  if (n && !n.isRead) { await markNotifRead(key); await fetchUnreadCount(); n.isRead = true }
  // 跳转规则按 targetType 统一解析（原来只认租户审核，其它通知点了没反应）
  const path = notifLink(n)
  if (path) router.push(path)
}
/**
 * 刷新铃铛（未读数 + 下拉列表）。
 *
 * <p>触发时机有三处：应用启动、30 秒轮询（别人发的通知靠它）、以及"自己刚做完的事"
 * —— 后者由通知相关页面发 {@link NOTIF_CHANGED_EVENT} 事件通知（见 utils/notifications）。
 */
const refreshBell = async () => {
  await Promise.all([fetchUnreadCount(), fetchNotifications()])
}

/** 点开铃铛时也刷一次：看到的必须是最新，而不是最多 30 秒前的 */
const onBellOpen = (visible) => {
  if (visible) refreshBell()
}

// ---- 顶栏滚动消息（与铃铛同一份数据，不额外请求）----

const tickerPaused = ref(false)

// 文案与"要不要滚"都是列表到字符串的纯函数，放在 utils/notifications 里便于单测
const tickerText = computed(() => notifTickerText(notifications.value))
/** 只在有未读时才滚（都已读还滚，等于没有新消息却一直晃） */
const tickerScrolling = computed(() => notifTickerShouldScroll(notifications.value))
const tickerTooltip = computed(() => notifTickerTooltip(notifications.value))

/** 滚动时长按字数走：长消息不会一闪而过（约 0.55 秒/字） */
const tickerDuration = computed(() =>
  `${Math.max(12, Math.min(90, tickerText.value.length * 0.55))}s`)

const goNotifications = () => router.push('/notifications')

const handleMarkAllRead = async (e) => {
  e?.preventDefault?.()
  await markAllNotifRead()
  unreadCount.value = 0
  await fetchNotifications()
}

// ---- 全局搜索 ----
const searchKeyword = ref('')
const searchOptions = ref([])
let searchTimer = null

function searchTypeColor(type) {
  return ({ PRODUCT_LINE: 'blue', PRODUCT_MODEL: 'cyan', PART: 'green', DOCUMENT: 'orange' })[type] || 'default'
}
function searchTypeLabel(type) {
  return ({ PRODUCT_LINE: '产品系列', PRODUCT_MODEL: '产品型号', PART: '零组件', DOCUMENT: '文档' })[type] || type
}

function onSearchInput(val) {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = (val || '').trim()
  if (!kw) { searchOptions.value = []; return }
  searchTimer = setTimeout(async () => {
    try {
      const res = await globalSearch(kw, 10)
      if (res.code === 200) {
        searchOptions.value = (res.data || []).map(r => ({ value: r.oid, ...r }))
      } else {
        searchOptions.value = []
      }
    } catch { searchOptions.value = [] }
  }, 300)
}

function onSearchSelect(value, option) {
  const item = option
  let path = ''
  let tip = ''
  switch (item.type) {
    case 'PRODUCT_LINE':
      path = `/product/${item.oid}`
      break
    case 'PRODUCT_MODEL':
      tip = `产品型号「${item.code} ${item.name}」请到「系列/型号」页面查看`
      path = '/product'
      break
    case 'PART':
      path = `/part/${item.oid}`
      break
    case 'DOCUMENT':
      tip = `文档「${item.code} ${item.name}」请到所属产品线下查看`
      path = '/product'
      break
  }
  if (tip) message.info(tip)
  if (path) router.push(path)
  searchKeyword.value = ''
  searchOptions.value = []
}

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
  refreshBell()
  pollTimer = setInterval(refreshBell, 30000)
  // 页面里刚发的公告 / 刚标的已读 → 立刻反映到铃铛，不必等下一轮轮询
  window.addEventListener(NOTIF_CHANGED_EVENT, refreshBell)
})
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  window.removeEventListener(NOTIF_CHANGED_EVENT, refreshBell)
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
  height: 100vh;
  overflow: hidden;
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

/*
 * ant-design 的 Sider 会在中间插一层 .ant-layout-sider-children，
 * 所有菜单都在它里面 —— 真正的纵向布局容器是这一层，不是 .layout-sider。
 * 少了这段，下面菜单的 flex:1 / min-height:0 都作用在错误的对象上（也就是改之前滚不动的原因）。
 */
.layout-sider :deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.sider-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  cursor: pointer;
  transition: all 0.2s;
  flex: 0 0 auto;
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
  /*
   * min-height:0 是这里的关键：flex 子项默认 min-height:auto（不小于内容高度），
   * 菜单项一多就会被内容撑高、再被侧边栏的 overflow:hidden 裁掉 —— 于是"overflow-y:auto
   * 写了却滚不动"。归零后菜单才会在剩余高度内滚动，展开多少分组都在。
   */
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  /*
   * 这里<b>不能</b>留"滚动条常驻占位"（scrollbar-gutter: stable）：
   * 它会在菜单右侧永久预留一条滚动条宽度，菜单内容区整体变窄 ——
   * 收起态下选中块/图标是按内容区居中的，于是看起来始终偏左（左右留白不等）。
   * 宁可让滚动条出现时才占 6px（影响极小），也不要常态性的偏心。
   */
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.25) transparent;
}

/* 深色侧边栏上的滚动条：默认配色几乎看不见，给一条半透明的 */
.sider-menu::-webkit-scrollbar {
  width: 6px;
}

.sider-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.18);
  border-radius: 3px;
}

.sider-menu::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.32);
}

.sider-menu::-webkit-scrollbar-track {
  background: transparent;
}

/* 窄宽度下菜单文字截断 */
.layout-sider :deep(.ant-menu-title-content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 菜单项：选中态 =====
 * antd 默认给选中项一块"实心蓝底 + 圆角 + 左右外边距"的色块。在 150px 窄栏（尤其收起成
 * 图标栏）里，它像一块浮在栏外的蓝方块，"我在哪一节"反而要靠猜。
 * 这里换成通栏行 + 左侧 3px 指示条 + 很轻的底色：
 *   - 通栏让"选中"与"这一栏"建立关系，不再是一个孤立方块；
 *   - 指示条承担"我在这"的信息，底色只负责拉开一点点层次，不喧宾夺主。
 */
.layout-sider :deep(.ant-menu-item),
.layout-sider :deep(.ant-menu-submenu-title) {
  margin: 2px 0 !important;
  margin-inline: 0 !important;
  width: 100% !important;
  border-radius: 0 !important;
}

/* 指示条：默认给整行铺一条（宽度 3px，位置贴着栏的左边缘） */
.layout-sider :deep(.ant-menu-item)::after,
.layout-sider :deep(.ant-menu-submenu-title)::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  inset-inline-start: 0;
  width: 3px;
  border: none !important;
  background: transparent;
  transform: none !important;
  opacity: 1 !important;
}

.layout-sider :deep(.ant-menu-dark .ant-menu-item-selected) {
  background: rgba(22, 119, 255, 0.16) !important;
  color: #fff !important;
  font-weight: 500;
}

.layout-sider :deep(.ant-menu-dark .ant-menu-item-selected)::after {
  background: #1677ff !important;
}

/* 子菜单里被选中的那一项，父级标题也点一条指示条 ——
   收起态看不到子项，靠它告诉用户"当前页在这个分组里" */
.layout-sider :deep(.ant-menu-dark .ant-menu-submenu-selected > .ant-menu-submenu-title) {
  color: #fff !important;
}

.layout-sider :deep(.ant-menu-dark .ant-menu-submenu-selected > .ant-menu-submenu-title)::after {
  background: rgba(22, 119, 255, 0.7) !important;
}

/* 收起态（只剩图标）：把"居中"这件事握在自己手里，不依赖 antd 的 padding 计算 ——
   图标与选中块都以整栏为基准居中，铺满整栏宽度 */
.layout-sider.ant-layout-sider-collapsed :deep(.ant-menu-item),
.layout-sider.ant-layout-sider-collapsed :deep(.ant-menu-submenu-title) {
  width: 100% !important;
  margin-inline: 0 !important;
  padding: 0 !important;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 收起态图标：antd 常态会给图标一个 margin-right（为文字留位置），
   收起时它会把图标顶偏 —— 归零，让 justify-content 真正决定位置 */
.layout-sider.ant-layout-sider-collapsed :deep(.ant-menu-item .anticon),
.layout-sider.ant-layout-sider-collapsed :deep(.ant-menu-submenu-title .anticon) {
  margin: 0 !important;
  font-size: 16px;
  line-height: 1;
}

/* 收起态必须让文字彻底不占位：antd 藏文字用的是 opacity（隐形的文字仍占宽度），
   不改成 display:none 的话，上面那句 justify-content:center 会把"图标 + 隐形文字"
   当整体居中 —— 图标反而更偏左 */
.layout-sider.ant-layout-sider-collapsed :deep(.ant-menu-title-content) {
  display: none !important;
}

/* 租户信息：作为 flex 的最后一项常驻底部（不再 absolute）——
   absolute 会盖在菜单最后几项上，菜单滚到底也看不清；现在是菜单先占满剩余高度、
   它自己占固定一块，两者互不侵占 */
.sider-author {
  flex: 0 0 auto;
  padding: 10px 12px 12px;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.35);
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

/* ===== 顶栏最新消息滚动条 =====
   一条窄带里横向滚动：轨道里放两份相同文本，位移 -50% 时正好等于一份，
   于是循环处永远不会出现"跳一下"。悬停暂停由 animationPlayState 控制（模板里绑）。 */
.notif-ticker {
  display: flex;
  align-items: center;
  gap: 6px;
  /* 比原来 240px 宽一些：这条现在要放"标题：正文摘要"，太窄的话静态时几乎只剩标题 */
  width: 320px;
  /* 高度写死，与 CK-PLM 助手胶囊同高：顶栏的 line-height: 56px 会继承进来，
     不挡住的话这里的文字行盒也是 56px，窄带会被撑得比顶栏还高 */
  height: 28px;
  box-sizing: border-box;
  /* 与铃铛的间距交给 .header-right 的 gap，这里不再自己加 margin */
  padding: 0 10px;
  border-radius: 14px;
  background: #f5f7fa;
  cursor: pointer;
  overflow: hidden;
  transition: background 0.2s;
}

.notif-ticker:hover {
  background: #eaf3ff;
}

.notif-ticker-icon {
  flex: 0 0 auto;
  color: #fa8c16;
  font-size: 13px;
}

.notif-ticker-view {
  flex: 1 1 auto;
  overflow: hidden;
  white-space: nowrap;
  /* 轨道是 inline-block，行盒高度跟着本元素的 line-height 走：必须断掉 56px 的继承，
     否则文字在 28px 高的窄带里被上下裁掉 */
  line-height: 1;
}

.notif-ticker-track {
  display: inline-block;
  white-space: nowrap;
  animation-name: notif-ticker-scroll;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

/* 全部已读：停掉滚动，按普通一行文字展示（放不下就省略号，悬停看全文） */
.notif-ticker-track.is-static {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  animation: none;
}

.notif-ticker-text {
  font-size: 12px;
  line-height: 1;
  color: #595959;
}

@keyframes notif-ticker-scroll {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}

.header-search {
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 0 24px;
  min-width: 0;
}
.header-search :deep(.ant-select) {
  width: 100%;
  max-width: 520px;
}

.search-option {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.4;
}
.search-code {
  font-weight: 500;
  color: #262626;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
.search-name {
  color: #8c8c8c;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  margin: 0;
  padding: 16px 24px 56px;
  background: #fff;
  border-radius: 0;
  flex: 1;
  min-height: 0;        /* 允许 flex item 收缩到父级分配高度，避免被内容撑开 */
  overflow: auto;
}

/* ===== 右侧主区域 ===== */
.right-area {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
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
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
  padding: 12px 24px;
  background: #1a1a1a;
  letter-spacing: 0.5px;
  z-index: 100;
}
</style>
