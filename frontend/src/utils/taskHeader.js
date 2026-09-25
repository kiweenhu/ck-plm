/**
 * 任务办理页「页头文案」—— 纯函数，便于单测钉住口径（组件只做绑定）。
 *
 * <p><b>为什么页头要显示这些</b>：办理页是从任务中心<b>新窗口</b>打开的，一个人常同时开着好几个
 * （换个节点再开一个、对照另一个对象）。所以页头必须能一眼答出三件事：
 * <ol>
 *   <li><b>办的是哪一条对象</b> —— 业务实体名称 + 编码 + 大版本；</li>
 *   <li><b>走的哪条流程</b> —— 流程名；</li>
 *   <li><b>卡在哪个任务</b> —— 任务名。</li>
 * </ol>
 * 原先页头只有「任务名 + 流程名」，同时开两个窗口时两张卡片长得几乎一样，
 * 只能靠下面的「通用信息」逐行找实体 —— 那正是页头该省掉的动作。
 *
 * <p>这里全部是「数据 → 字符串」的纯函数，所以能脱离组件断言（含各种缺字段的降级）。
 */

/** 去掉空白并转成字符串（后端字段可能是数字，如 entityVersion） */
function text(value) {
  return value === null || value === undefined ? '' : String(value).trim()
}

/**
 * 页头主标题：业务实体的<b>名称</b>。
 *
 * <p>降级顺序：名称 → 编码 → 类型码 → 「业务实体」。回查失败（无权限 / 已被删除）时
 * 至少还显示类型码或编码，不出现空白标题 —— 空白会让人以为页头坏了。
 */
export function entityHeadline(entity, info) {
  const name = text(info?.name)
  if (name) return name
  const code = text(info?.code)
  if (code) return code
  return text(entity?.typeCode) || text(entity?.rootTypeCode) || '业务实体'
}

/** 页头副标题里的编码：没回查到就返回空串，让调用方整块不渲染（不留空位） */
export function entityCodeText(info) {
  return text(info?.code)
}

/**
 * 大版本文案（`版本 A`）。
 *
 * <p>取的是<b>流程关联的那个大版本</b>（{@code entityVersion}），与下方「通用信息 · 业务实体」
 * 同一口径 —— 用实体当前最新版本会与那一行对不上，同一页两处版本不一致更让人困惑。
 */
export function entityVersionText(entity) {
  const version = text(entity?.entityVersion)
  return version ? `版本 ${version}` : ''
}

/**
 * 「流程名 · 任务名」：缺哪一个就只显示另一个，都没有才退成兜底文案。
 *
 * <p>用 `·` 而不是两个标签：这两个是同一件事的上下文（在哪条流程的哪一步），
 * 分开加框会显得像两个并列的状态，反而要读两遍。
 */
export function flowTaskText(processName, taskName) {
  const parts = [text(processName), text(taskName)].filter(Boolean)
  return parts.join(' · ') || '办理任务'
}
