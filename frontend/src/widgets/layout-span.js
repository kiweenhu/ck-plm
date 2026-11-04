/**
 * 布局行里"一个字段占多宽"的判定 —— 运行态（RenderFields）与设计器画布（PageDesigner）**共用这一份**。
 *
 * <p>为什么要有这个：布局行只配了列数（1/2/4），早先所有子字段一律等分。可有些控件在小格里
 * 必然被挤坏 —— 富文本自带工具条（一行 8 个按钮）、文本域要三行高度、文件上传与数据表格
 * 本质就是整块区域。用 2 列塞进去的结果是：工具条换行、编辑器里出现横向滚动条、
 * 上传区被压成一条缝。这类控件不该要求用户在设计器里手工算宽度，这里按控件类型自动给整行。
 *
 * <p>优先级（从高到低）：
 * <ol>
 *   <li>{@code field.span}：24 栅格宽度（1~24），设计器/布局 JSON 里显式写了就以它为准；</li>
 *   <li>{@code field.colSpan}：按"格"计（2 列布局里写 2 = 整行），换算成 24 栅格；</li>
 *   <li>{@code field.fullWidth === true}：强制整行；</li>
 *   <li>控件类型判定（{@link WIDE_WIDGET_TYPES} / {@code dataType=LONGTEXT}）→ 整行；</li>
 *   <li>其余 → 平均分（{@code 24 / columns}）。</li>
 * </ol>
 */
import { getWidgetByType, normalizeWidgetType } from './catalog'

/** 24 栅格总宽（antd Row/Col 口径） */
export const GRID = 24

/**
 * 天生需要整行的控件：
 * - {@code textarea} / {@code rich-text}：多行高度，半宽会让工具条换行、内容区出横向滚动条；
 * - {@code file-upload}：上传区是整块拖拽区域；
 * - {@code data-table}：子表格放半宽等于不可用。
 */
const WIDE_WIDGET_TYPES = new Set(['textarea', 'rich-text', 'file-upload', 'data-table'])

/** 该字段是否应当占满整行 */
export function isWideField(field) {
  if (!field) return false
  if (field.fullWidth === true) return true
  const type = normalizeWidgetType(field.uiComponent || field.type)
  if (WIDE_WIDGET_TYPES.has(type)) return true
  // LONGTEXT 的控件（含后续新增的长文本类）同样按整行处理，不必逐个登记
  return getWidgetByType(type)?.dataType === 'LONGTEXT'
}

/**
 * 字段在布局行里占的 24 栅格宽度。
 *
 * @param {object} field   字段定义
 * @param {number} columns 所在布局行的列数（1/2/4 或任意正整数）
 * @returns {number} 1~24
 */
export function resolveFieldSpan(field, columns = 2) {
  const cols = Number(columns) > 0 ? Number(columns) : 2
  const span = Number(field?.span)
  if (Number.isFinite(span) && span > 0) {
    return Math.min(GRID, Math.max(1, Math.round(span)))
  }
  const colSpan = Number(field?.colSpan)
  if (Number.isFinite(colSpan) && colSpan > 0) {
    return Math.min(GRID, Math.max(1, Math.round((GRID / cols) * colSpan)))
  }
  return isWideField(field) ? GRID : Math.floor(GRID / cols)
}

/**
 * CSS 宽度百分比 —— 设计器画布用 flex 百分比布列，与运行态 {@link resolveFieldSpan} 同口径。
 */
export function resolveFieldWidthPercent(field, columns = 2) {
  return `${((resolveFieldSpan(field, columns) / GRID) * 100).toFixed(2)}%`
}
