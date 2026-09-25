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
    fallback: entityRefFallbackText(ref),
  }
}
