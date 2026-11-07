/**
 * 反馈的「类型 / 状态」怎么显示 —— 平台管理端的处理页与用户端的「我的反馈」共用同一份。
 *
 * <p>两边必须一致：管理员把一条标成「已回复」，用户在页脚看到的若还是「待处理」，
 * 用户就会以为没人管。所以文案只在这里定义一次。
 *
 * <p>状态编码与后端 {@link cn.ck.plm.base.feedback.entity.Feedback} 的常量一一对应。
 */

export const FEEDBACK_CATEGORY_META = {
  ISSUE: { label: '使用问题', color: 'blue' },
  BUG: { label: '缺陷', color: 'red' },
  FEATURE: { label: '新功能建议', color: 'purple' }
}

export const FEEDBACK_STATUS_META = {
  NEW: { label: '待处理', color: 'orange' },
  PROCESSING: { label: '跟进中', color: 'blue' },
  DONE: { label: '已回复', color: 'green' },
  REJECTED: { label: '不采纳', color: 'default' }
}

export function feedbackCategoryMeta(code) {
  return FEEDBACK_CATEGORY_META[code] || { label: code || '-', color: 'default' }
}

export function feedbackStatusMeta(code) {
  return FEEDBACK_STATUS_META[code] || { label: code || '-', color: 'default' }
}

/** 时间展示：yyyy-MM-dd HH:mm（列表里秒没意义，还占宽度） */
export function feedbackTime(timeStr) {
  if (!timeStr) return '-'
  try {
    const d = new Date(timeStr)
    if (isNaN(d.getTime())) return String(timeStr).substring(0, 19).replace('T', ' ')
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return String(timeStr).substring(0, 19).replace('T', ' ')
  }
}
