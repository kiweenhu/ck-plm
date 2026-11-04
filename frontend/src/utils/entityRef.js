/**
 * 业务实体引用 → 页面链接 —— 纯函数，便于单测钉住口径。
 *
 * <p><b>为什么要单独一处</b>：流程侧给的是「实体引用」（typeCode / oid / 大版本），
 * 而"点进去看什么"是个业务决定，不是随手拼一个 URL。散在各页面拼，早晚拼出
 * 两种口径（一个给最新版本、一个给发起时那个版本）。
 */

/** 零组件宿主：流程关联的业务对象里，目前只有它有大版本详情页 */
const PART_ROOT = 'PART'

/**
 * 实体引用 → 详情页路径；没有可跳转的详情页时返回空串（调用方据此只显示文字）。
 *
 * <p><b>刻意不带 iterationOid</b>：{@code /part/:oid} 即"该大版本的最新版本"
 * （详情页不带迭代参数时由后端取最新迭代）。带上发起流程那一刻的迭代 oid，
 * 等于把人锁在一个历史小版本上 —— 流程还在这条大版本上继续产出 A.2 / A.3，看旧的没有意义。
 *
 * <p>只对零组件宿主给链接：文档 / 工程数据等还没有可复用的大版本详情路由，
 * 这时宁可只显示信息 —— 链到一个打不开的地址比没有链接更糟。
 *
 * @param {{entityOid?: string, typeCode?: string, rootTypeCode?: string}|null} [ref] 实体引用
 * @returns {string} 如 {@code /part/3dcbaa0a-…}；不适用时为空串
 */
export function entityDetailPath(ref) {
  const oid = ref?.entityOid
  if (!oid) return ''
  const isPart = ref.rootTypeCode === PART_ROOT || ref.typeCode === PART_ROOT
  return isPart ? `/part/${oid}` : ''
}

/**
 * 实体引用的"兜底文案"：回查不到名称时至少给出能核对的东西。
 *
 * <p>正常情况下用不到它 —— 只有无权限或对象已被删除，回查才会失败。
 *
 * @param {{entityOid?: string, typeCode?: string}|null} [ref] 实体引用
 * @returns {string} 如 {@code ELECTRONIC · 3dcbaa0a-…}
 */

export function entityRefFallbackText(ref) {
  const type = (ref?.typeCode || '').trim()
  const oid = (ref?.entityOid || '').trim()
  if (!oid) return ''
  return type ? `${type} · ${oid}` : oid
}

/**
 * 组装「业务标识」的展示模型（流程监控列表 / 流程详情共用）。
 *
 * <p>为什么要有这一层：同一行字在列表与详情两处都要显示，两处各拼一次字符串，
 * 早晚出现"列表有版本、详情没版本"。模板只做绑定，拼法只写在这里。
 *
 * @param {{entityOid?: string, entityVersion?: string, typeCode?: string, rootTypeCode?: string}} [ref] 实体引用
 * @param {{code?: string, name?: string, statusName?: string, revision?: string}|null} [info] 回查结果（可能没有）
 * @returns {{resolved: boolean, path: string, name: string, code: string, version: string,
 *   status: string, fallback: string}|null} 没有实体引用时返回 null
 */
export function buildEntityLabel(ref, info) {
  if (!ref?.entityOid) return null
  const resolved = !!info
  return {
    /** 是否回查到了名称 —— 没回查到就只显示兜底引用（不编造） */
    resolved,
    /** 详情页路径；空串 = 没有可跳转的页面，只显示文字 */
    path: resolved ? entityDetailPath(ref) : '',
    name: resolved ? (info.name || info.code || '') : '',
    code: info?.code || '',
    // 版本取流程绑定的那个大版本（与「任务办理页」同一口径），回查里的 revision 只作兜底
    version: (ref.entityVersion || info?.revision || '').trim(),
    status: info?.statusName || '',
    /** 状态标签颜色；不传时沿用原来的绿色（软类型实例的状态都是"已发布/审核中"这类既成事实） */
    statusColor: info?.statusColor || 'green',
    fallback: entityRefFallbackText(ref),
  }
}

/**
 * 变更单据（请求 / 通告 / 任务）的展示模型 —— 与 {@link buildEntityLabel} 同一形状。
 *
 * <p><b>为什么不复用 buildEntityLabel</b>：那个函数是"实体引用 + 回查结果"的口径
 * （oid / 大版本 / typeCode），而变更单据只有 oid，且没有大版本、没有对象详情页。
 * 硬套会让没必要的字段（version / path）永远是空串，反而看不出是有意为之。
 *
 * <p>没有详情路由，{@code path} 恒为空 —— 宁可只显示文字，也不链到一个打不开的地址。
 *
 * @param {{kind?: string, oid?: string, code?: string, name?: string, statusName?: string,
 *   statusColor?: string}|null} doc 后端反查结果（kind / code / name / status）+ 前端翻好的状态文案
 * @returns {{resolved: boolean, path: string, name: string, code: string, version: string,
 *   status: string, statusColor: string, fallback: string}|null}
 */
export function buildChangeDocLabel(doc) {
  if (!doc?.oid) return null
  return {
    resolved: true,
    path: changeDocPath(doc.kind, doc.oid),
    // 名称优先、编码兜底：单据一定有编码，标题却可能没填
    name: doc.name || doc.code || '',
    code: doc.code || '',
    version: '',
    status: doc.statusName || '',
    statusColor: doc.statusColor || 'blue',
    fallback: doc.code || doc.oid,
  }
}

/**
 * 变更单据的详情入口 —— 变更控制页的**详情抽屉**，靠 query 一次性指令打开。
 *
 * <p>为什么不是独立路由：变更请求 / 通告 / 任务的详情都做在「变更控制」的抽屉里，
 * 没有 per-oid 的页面。沿用面板已经认的 `?tab=…&xxxOid=…`（见 ChangeNoticePanel#openFromRoute），
 * 面板打开后会把 oid 参数擦掉，刷新不会再弹一次。
 *
 * <p>变更任务（TASK）暂时没有"按 oid 打开"的入口，返回空串 —— 宁可不可点，
 * 也不链到一个打不开的地址。
 */
export function changeDocPath(kind, oid) {
  const id = String(oid || '').trim()
  if (!id) return ''
  if (kind === 'REQUEST') return `/change?tab=request&requestOid=${id}`
  if (kind === 'NOTICE') return `/change?tab=notice&noticeOid=${id}`
  return ''
}
