import {
  BellOutlined, ClockCircleOutlined, CheckCircleOutlined,
  SoundOutlined, SwapOutlined, ApartmentOutlined, UserSwitchOutlined,
} from '@ant-design/icons-vue'

/**
 * 通知的「长什么样」与「点了去哪」—— 铃铛下拉与通知中心共用同一份，避免两处各写一套后漂移。
 *
 * <p>类型编码由后端写入 `ck_notification.type`：TASK 待办 / TASK_DELEGATE 委派 /
 * TASK_TRANSFER 转办 / PROCESS_RESULT 流程结果 / ANNOUNCEMENT 公告 /
 * TENANT_REGISTRATION 租户注册审核。
 */

/** 类型 → 展示元信息（图标直接给组件，模板用 <component :is>） */
export function notifTypeMeta(type) {
  switch (type) {
    case 'TASK':
      return { label: '待办', color: 'blue', icon: ClockCircleOutlined }
    case 'TASK_DELEGATE':
      return { label: '委派', color: 'orange', icon: UserSwitchOutlined }
    case 'TASK_TRANSFER':
      return { label: '转办', color: 'orange', icon: SwapOutlined }
    case 'PROCESS_RESULT':
      return { label: '流程结果', color: 'green', icon: CheckCircleOutlined }
    case 'ANNOUNCEMENT':
      return { label: '公告', color: 'gold', icon: SoundOutlined }
    case 'TENANT_REGISTRATION':
      return { label: '租户审核', color: 'purple', icon: ApartmentOutlined }
    default:
      return { label: '通知', color: 'default', icon: BellOutlined }
  }
}

/**
 * 点击通知去哪。
 *
 * <p>刻意写成"按 targetType 映射"而不是在页面里 if 某一种类型 —— 原来的实现只认
 * 租户审核，其它通知点了没反应，等于通知是死的。
 */
export function notifLink(notification) {
  const type = notification?.targetType
  const oid = notification?.targetOid
  switch (type) {
    case 'TASK':
      return '/workflow/task'
    case 'PROCESS_INSTANCE':
      return '/workflow/monitor'
    case 'TENANT':
      return '/system/tenants'
    case 'PART':
      return oid ? `/part/${oid}` : ''
    default:
      return ''
  }
}

/** 通知时间：先给"多久前"，精确时刻用 tooltip 补（列表里空间宝贵） */
export function notifRelativeTime(time) {
  if (!time) return ''
  const diff = Date.now() - new Date(time).getTime()
  if (!Number.isFinite(diff) || diff < 0) return ''
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  return days < 30 ? `${days} 天前` : `${Math.floor(days / 30)} 个月前`
}

export function notifFullTime(time) {
  if (!time) return ''
  const d = new Date(time)
  return Number.isNaN(d.getTime()) ? String(time).substring(0, 19).replace('T', ' ')
    : d.toLocaleString('zh-CN', { hour12: false })
}

// ==================== 顶栏滚动公告条 ====================

/** 滚动条最多显示几条 */
export const TICKER_MAX = 5
/** 每条正文的截断长度：滚动条是"扫一眼"，太长要滚很久；完整正文在 tooltip 里 */
export const TICKER_CONTENT_MAX = 48

/**
 * 滚动条的「新鲜期」：只放这个时长内的未读。
 *
 * <p>滚动条是"现在有什么新消息"的位置，不是消息中心。原来它把列表前 5 条一股脑放进去，
 * 于是一条三天前的公告会一直滚到被点开为止 —— 内容早过期了却一直在眼前晃，
 * 真正的新消息反而被它盖过去。
 */
export const TICKER_MAX_AGE_HOURS = 6

/** 滚动条没有内容时的提示：把规则说出来，省得让人以为这块坏了 */
export const TICKER_EMPTY_TEXT = `近 ${TICKER_MAX_AGE_HOURS} 小时无新消息`

/**
 * 一条消息够不够格进滚动条：**未读** 且 **在新鲜期内**。
 *
 * <p>没有时间戳的（老数据 / 字段缺失）一律不进：判断不了新不新，而这里的承诺就是"只放新消息"。
 * 时间比现在还晚的（时钟偏差）按新消息处理，不额外判负。
 */
export function isTickerItem(n, now = Date.now()) {
  if (!n || n.isRead) return false
  const at = new Date(n.createdAt).getTime()
  if (!Number.isFinite(at)) return false
  return now - at <= TICKER_MAX_AGE_HOURS * 3600 * 1000
}

/**
 * 滚动条里的条目（未读 + 新鲜期内，最新的在前）。
 *
 * <p>入参就是铃铛下拉那份列表（同一份数据、不额外请求），这里只做筛选与截断。
 */
export function notifTickerItems(notifications, now = Date.now()) {
  return (notifications || [])
    .filter(n => isTickerItem(n, now))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, TICKER_MAX)
}

/**
 * 一条消息的滚动文案：「标题：正文摘要」，未读以 ● 起头。
 *
 * <p>带上正文是必须的 —— 只给标题的话「系统升级公告」这种标题看不出到底说了什么，
 * 用户还得点进通知中心才知道内容。
 */
export function notifTickerLine(n) {
  const title = (n?.title || '').trim()
  const content = (n?.content || '').replace(/\s+/g, ' ').trim()
  const brief = content.length > TICKER_CONTENT_MAX
    ? content.slice(0, TICKER_CONTENT_MAX) + '…' : content
  const body = title && brief ? `${title}：${brief}` : (title || brief || '通知')
  return `${n?.isRead ? '' : '● '}${body}`
}

/** 滚动条整行文案；没有消息时给一句话而不是空串（空白会让人以为这块坏了） */
export function notifTickerText(notifications, now = Date.now()) {
  const items = notifTickerItems(notifications, now)
  if (!items.length) return TICKER_EMPTY_TEXT
  // 末尾补一个分隔符，滚动衔接处不会"粘"在一起
  return items.map(notifTickerLine).join('    ｜    ') + '    ｜    '
}

/**
 * 是否需要滚动。
 *
 * <p>滚动条里现在只会有未读（见 {@link isTickerItem}），所以"有内容要显示"就等于"还在滚"；
 * 一条都没有时静态展示一句话 —— 没有新消息却一直在眼前晃，干扰大于提示。
 */
export function notifTickerShouldScroll(notifications, now = Date.now()) {
  return notifTickerItems(notifications, now).length > 0
}

/** 悬停提示：标题 + 完整正文（滚动条里是截断的，这里给全） */
export function notifTickerTooltip(notifications, now = Date.now()) {
  const items = notifTickerItems(notifications, now)
  if (!items.length) return TICKER_EMPTY_TEXT
  return items
    .map(n => {
      const content = (n?.content || '').replace(/\s+/g, ' ').trim()
      const title = (n?.title || '').trim() || '通知'
      return content ? `${title}：${content}` : title
    })
    .join('　｜　')
}

/**
 * 通知变化信号 —— 让"页面里发的公告/标记的已读"立刻反映到右上角铃铛上。
 *
 * <p>为什么需要：铃铛的未读数是 MainLayout 自己的一份状态，本来只靠 30 秒轮询更新，
 * 于是在公告页点了"发布"之后，列表立刻有了、铃铛却要等半分钟才亮 —— 看起来就像"通知没出现"。
 * 轮询仍然保留（别人发的通知只能靠它），这里只补上"自己刚做完的事立刻生效"。
 */
export const NOTIF_CHANGED_EVENT = 'ckplm:notifications-changed'

export function emitNotifChanged() {
  try { window.dispatchEvent(new Event(NOTIF_CHANGED_EVENT)) } catch { /* ignore */ }
}
