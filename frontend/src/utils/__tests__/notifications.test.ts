/**
 * 顶栏滚动消息条：「显示什么」「哪些该显示」「要不要滚」。
 *
 * <p><b>背景</b>：这条窄带原来只拼标题，于是「系统升级公告」滚了半天也看不出公告说了什么；
 * 而且它无条件滚动、且把列表前 5 条一股脑放进去 —— 一条三天前（还标着未读）的公告会一直滚，
 * 真正的新消息反而被盖过去。本文件钉住三件事：
 * <ol>
 *   <li>文案里带<b>正文摘要</b>（超长截断，完整正文留给悬停提示）；</li>
 *   <li>只放<b>未读</b>且<b>新鲜期内</b>的消息（过期 / 已读 / 没有时间戳的都不放）；</li>
 *   <li><b>有内容才滚动</b>：没有就静态显示一句话，并说明规则。</li>
 * </ol>
 *
 * <p>这些都是「列表 → 字符串/布尔」的纯函数，所以能脱离组件直接断言 ——
 * 逻辑放在 `utils/notifications`（通知展示口径的共用处），组件只负责绑定。
 * 时间基准显式传入（默认取当前时间），因此"几小时前"这类断言不会随运行时刻漂移。
 */

import { describe, expect, it } from 'vitest'
import {
  TICKER_CONTENT_MAX,
  TICKER_EMPTY_TEXT,
  TICKER_MAX_AGE_HOURS,
  isTickerItem,
  notifTickerItems,
  notifTickerLine,
  notifTickerShouldScroll,
  notifTickerText,
  notifTickerTooltip,
} from '@/utils/notifications'

/** 固定"现在"：所有"几小时前"都相对它算 */
const NOW = new Date('2026-09-21T12:00:00+08:00').getTime()
const hoursAgo = (h: number) => new Date(NOW - h * 3600 * 1000).toISOString()

const announcement = {
  title: '系统升级公告',
  content: 'CK-PLM于今天下午16:30完成了版本升级，从0.15升级到0.20版本！！！',
  isRead: false,
  createdAt: hoursAgo(1),
}

/** 够格的第一条（未读 + 1 小时前） */
const fresh = (patch: Record<string, unknown> = {}) => ({ ...announcement, ...patch })

describe('滚动消息条文案', () => {
  it('显示正文摘要，而不是只有标题', () => {
    const text = notifTickerText([fresh()], NOW)
    expect(text).toContain('系统升级公告：CK-PLM于今天下午16:30完成了版本升级')
  })

  it('未读以 ● 起头，已读不加标记', () => {
    expect(notifTickerLine(announcement).startsWith('● ')).toBe(true)
    expect(notifTickerLine({ ...announcement, isRead: true }).startsWith('● ')).toBe(false)
  })

  it('超长正文截断加省略号，完整正文由悬停提示给出', () => {
    const longContent = 'x'.repeat(TICKER_CONTENT_MAX + 20)
    const long = fresh({ title: '公告', content: longContent })
    // 未读 → 行首带 ●（滚动条里只会是未读，故按实际形态断言）
    expect(notifTickerLine(long)).toBe(`● 公告：${'x'.repeat(TICKER_CONTENT_MAX)}…`)
    expect(notifTickerTooltip([long], NOW)).toContain(longContent)
  })

  it('没有标题时退化为正文；都没有则给「通知」', () => {
    expect(notifTickerLine({ title: '', content: '只有正文', isRead: true })).toBe('只有正文')
    expect(notifTickerLine({ title: '', content: '', isRead: true })).toBe('通知')
  })

  it('最多 5 条', () => {
    const many = Array.from({ length: 8 }, (_, i) => fresh({ title: `通知${i}`, content: '' }))
    const text = notifTickerText(many, NOW)
    expect((text.match(/｜/g) || []).length).toBe(5)
    expect(text).toContain('通知0')
    expect(text).not.toContain('通知5')
  })
})

describe('哪些消息该进滚动条（未读 + 新鲜期内）', () => {
  it('未读且在 6 小时内 → 进', () => {
    expect(isTickerItem(fresh(), NOW)).toBe(true)
    expect(isTickerItem(fresh({ createdAt: hoursAgo(TICKER_MAX_AGE_HOURS) }), NOW)).toBe(true)
  })

  it('超过 6 小时 → 不进（过期公告不该一直滚）', () => {
    expect(isTickerItem(fresh({ createdAt: hoursAgo(TICKER_MAX_AGE_HOURS + 0.1) }), NOW)).toBe(false)
    expect(isTickerItem(fresh({ createdAt: hoursAgo(72) }), NOW)).toBe(false)
  })

  it('已读 → 不进（哪怕刚刚才读的）', () => {
    expect(isTickerItem(fresh({ isRead: true }), NOW)).toBe(false)
  })

  it('没有时间戳 / 数据异常 → 不进（判断不了新不新，就不冒充新消息）', () => {
    expect(isTickerItem(fresh({ createdAt: undefined }), NOW)).toBe(false)
    expect(isTickerItem(fresh({ createdAt: '不是时间' }), NOW)).toBe(false)
    expect(isTickerItem(null, NOW)).toBe(false)
    expect(isTickerItem(undefined, NOW)).toBe(false)
  })

  it('筛选 + 按时间倒序 + 截断 5 条', () => {
    const items = notifTickerItems([
      fresh({ title: '3 小时前', createdAt: hoursAgo(3) }),
      fresh({ title: '已读', isRead: true, createdAt: hoursAgo(0.5) }),
      fresh({ title: '10 小时前', createdAt: hoursAgo(10) }),
      fresh({ title: '刚刚', createdAt: hoursAgo(0.1) }),
      fresh({ title: '5 小时前', createdAt: hoursAgo(5) }),
    ], NOW)
    expect(items.map((n: { title: string }) => n.title)).toEqual(['刚刚', '3 小时前', '5 小时前'])
  })
})

describe('是否滚动', () => {
  it('有够格的消息就滚', () => {
    expect(notifTickerShouldScroll([fresh()], NOW)).toBe(true)
  })

  it('只剩已读 / 只剩过期的 → 不滚', () => {
    expect(notifTickerShouldScroll([fresh({ isRead: true })], NOW)).toBe(false)
    expect(notifTickerShouldScroll([fresh({ createdAt: hoursAgo(8) })], NOW)).toBe(false)
  })

  it('没有消息时不滚，并说明规则而不是留空白', () => {
    expect(notifTickerText([], NOW)).toBe(TICKER_EMPTY_TEXT)
    expect(notifTickerTooltip([], NOW)).toBe(TICKER_EMPTY_TEXT)
    expect(TICKER_EMPTY_TEXT).toContain(String(TICKER_MAX_AGE_HOURS))
    expect(notifTickerShouldScroll([], NOW)).toBe(false)
    expect(notifTickerShouldScroll(undefined, NOW)).toBe(false)
  })
})
