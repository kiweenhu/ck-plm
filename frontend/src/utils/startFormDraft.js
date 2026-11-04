/**
 * 发起流程 · 申请信息草稿（本地保存，2026-10-13 用户要求）。
 *
 * <h3>为什么是本地而不是服务端</h3>
 * <p>要挡住的是"填了一大堆、手滑关掉弹框就没了"这种<b>几分钟内</b>的意外，不是跨设备续填：
 * 服务端草稿要新建表、定租户隔离、写清理策略，成本远大于收益。本地存一份，
 * 提交成功后立刻清掉；同一台机器同一个人再打开时自动恢复。
 *
 * <h3>键与生命周期</h3>
 * <ul>
 *   <li>键 = 租户 + 用户 + 流程 key + 起点业务对象 + 表单 code ——
 *       "同一个器件的同一条流程的同一张表"才互相恢复，不会串到别的器件上；</li>
 *   <li>超过 {@link TTL_MS}（7 天）视为过期丢弃 —— 本地存储也不该无限堆积；</li>
 *   <li>提交成功后由调用方 {@link clearStartFormDraft} 清掉；读不出来（解析失败/过期）当没有。</li>
 * </ul>
 *
 * <p>纯函数 + 可注入 storage —— 单测不必真的碰 localStorage。
 */

/** 草稿保留时长：7 天 */
export const TTL_MS = 7 * 24 * 60 * 60 * 1000

/** 默认存储：浏览器 localStorage（不可用时返回 null，调用方按"没有草稿"处理） */
export function defaultStorage() {
  try {
    return typeof localStorage !== 'undefined' ? localStorage : null
  } catch {
    return null
  }
}

/**
 * 草稿键。
 *
 * @param {{tenantOid?: string, username?: string, processKey?: string, entityOid?: string, formKey?: string}} parts
 * @returns {string}
 */
export function draftKeyOf(parts = {}) {
  const seg = [parts.tenantOid, parts.username, parts.processKey, parts.entityOid, parts.formKey]
    .map((v) => (v == null ? '' : String(v)))
  return `ckplm:startFormDraft:${seg.join('|')}`
}

/**
 * 保存草稿。
 *
 * @param {string} key 见 {@link draftKeyOf}
 * @param {object} data 表单快照（组件 snapshot() 的返回值）
 * @param {Storage|null} [storage]
 * @param {number} [now] 便于测试注入时钟
 * @returns {boolean} 是否写入成功（存储不可用/写满时 false，不影响主流程）
 */
export function saveStartFormDraft(key, data, storage = defaultStorage(), now = Date.now()) {
  if (!key || !storage || !data || typeof data !== 'object') {
    return false
  }
  try {
    storage.setItem(key, JSON.stringify({ savedAt: now, data }))
    return true
  } catch {
    // 隐私模式 / 配额满：草稿是锦上添花，静默失败
    return false
  }
}

/**
 * 读取草稿。
 *
 * @returns {{savedAt: number, data: object}|null} 没有 / 过期 / 损坏时 null
 */
export function loadStartFormDraft(key, storage = defaultStorage(), now = Date.now()) {
  if (!key || !storage) {
    return null
  }
  try {
    const raw = storage.getItem(key)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw)
    const savedAt = Number(parsed?.savedAt) || 0
    if (!parsed?.data || typeof parsed.data !== 'object') {
      return null
    }
    if (now - savedAt > TTL_MS) {
      // 过期顺手清掉，别让旧草稿在某次"刚好没超时"的边界上被恢复
      storage.removeItem(key)
      return null
    }
    return { savedAt, data: parsed.data }
  } catch {
    return null
  }
}

/** 清除草稿（提交成功后调用） */
export function clearStartFormDraft(key, storage = defaultStorage()) {
  if (!key || !storage) {
    return
  }
  try {
    storage.removeItem(key)
  } catch {
    /* 忽略 */
  }
}

/** 草稿非空判断：没有任何字段值视为空（避免"存了个空对象还提示已恢复"） */
export function hasDraftContent(data) {
  if (!data || typeof data !== 'object') {
    return false
  }
  return Object.values(data).some((v) => {
    if (v == null || v === '') return false
    if (Array.isArray(v)) return v.length > 0
    return true
  })
}

/** 保存时间 → 人话（"今天 14:20 保存的草稿"） */
export function draftSavedAtText(savedAt, now = Date.now()) {
  if (!savedAt) {
    return ''
  }
  const d = new Date(savedAt)
  const pad = (n) => String(n).padStart(2, '0')
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const sameDay = new Date(now).toDateString() === d.toDateString()
  return sameDay ? `今天 ${time}` : `${d.getMonth() + 1} 月 ${d.getDate()} 日 ${time}`
}
