/**
 * 「发起流程」要带上的业务对象清单 —— 纯数据整理，脱离组件也能单测。
 *
 * <p><b>背景</b>：以前一次流程只能带一个业务对象（弹框里那一行"业务对象"），
 * 但实务里常有多条同类型对象一起走同一道审批（一批元器件一次 NPI）。
 * 后端支持 {@code entities[]} 之后，前端要负责两件事：
 * <ol>
 *   <li>把清单整理成后端要的形态（去重、字段改名）；清单<b>不分主次</b> ——
 *       它们是一起走审批的同行者，界面上不贴"主对象 / 关联"这类标签；</li>
 *   <li>候选对象只给<b>同类型</b>的：一次流程只针对一种类型，混进别的类型
 *       （如把 STRUCTURAL 零件塞进 ELECTRONIC 的流程），流程变量与审批意见都会对不上。</li>
 * </ol>
 */

/** 取对象 oid：列表行用 oid，后端形态用 entityOid，两种都认 */
function oidOf(item) {
  return item?.oid || item?.entityOid || ''
}

/** 取类型编码：列表行用 typeDefinitionCode，发起载荷用 typeCode，两种都认 */
function typeCodeOf(item) {
  return item?.typeDefinitionCode || item?.typeCode || undefined
}

/** 取类型显示名（没有就回落到编码） */
function typeNameOf(item) {
  return item?.typeDefinitionName || item?.typeName || typeCodeOf(item) || undefined
}

/** 取生命周期状态 code：不同接口的字段名不一（statusCode / status / lifecycleStatus） */
function statusCodeOf(item) {
  return item?.statusCode || item?.status || item?.lifecycleStatus || undefined
}

/**
 * 状态的两种标识：code 与显示名分开取（`status` 可能是字符串，也可能是 {code, displayName}）。
 *
 * <p>为什么要分开：不同来源的对象字段不一样 —— 企业资源/元器件库的列表行常常只有状态显示名
 * （`statusName` = 「草稿」「工作中」）而没有 `statusCode`。只认 code 会让比较恒为"未知"、
 * 校验被整段跳过 —— 「在工作中」的对象就是这么混进「草稿」批次的。
 */
function statusParts(item) {
  const raw = item?.status
  const code = normalizeStatus(
    item?.statusCode || item?.lifecycleStatus || (typeof raw === 'string' ? raw : raw?.code),
  )
  const name = normalizeStatus(
    item?.statusName
    || (raw && typeof raw === 'object' ? (raw.displayName || raw.name) : ''),
  )
  return { code, name }
}

/** 状态标识（给"这个对象带没带状态"用）：code 优先，缺了退回显示名 */
export function statusKeyOf(item) {
  const { code, name } = statusParts(item)
  return code || name
}

/** 状态展示名（没有就回落 code） */
export function statusLabelOf(item) {
  const raw = item?.status
  return item?.statusName
    || (raw && typeof raw === 'object' ? (raw.displayName || raw.name) : '')
    || item?.statusCode
    || ''
}

/**
 * 两个对象是否处于同一生命周期状态。
 *
 * <p>比较口径必须成对：**code 对 code、显示名对显示名**。用"拼接后的标识"互比会出假阳性
 * （一侧 `DRAFT`、另一侧「草稿」，本是一种状态却被判为不一致）。
 *
 * @returns {{same: boolean, known: boolean}} `known=false` 表示两者拿不到可比的同口径信息
 *          （例如一侧只有 code、另一侧只有显示名）—— 这种情况不拦，交由调用方补查后再判。
 */
export function sameStatus(a, b) {
  const x = statusParts(a)
  const y = statusParts(b)
  if (x.code && y.code) return { same: x.code === y.code, known: true }
  if (x.name && y.name) return { same: x.name === y.name, known: true }
  return { same: true, known: false }
}

/**
 * 整理成发起接口的 {@code entities} 载荷。
 *
 * <p><b>清单里不分主次</b>：这些对象是一起走同一道审批的同行者，界面上不给谁贴"主/从"标签。
 * 后端确实要有个东西去填 {@code businessObjectSet.primary}，取的只是<b>数组第一条</b>——
 * 那是数据形态上的入口，不是业务上的高下。
 *
 * <p>重复 oid 只留第一次（手滑加两遍不该让后端记两行关联）；没有 oid 的项直接丢掉。
 * 类型 / 编码按后端字段名给（{@code typeCode} / {@code entityCode}），缺了就不传 ——
 * 大版本一律由后端按对象当前版本解析，前端不猜。
 */
export function startEntityPayload(list = []) {
  const out = []
  const seen = new Set()
  for (const item of list || []) {
    const oid = oidOf(item)
    if (!oid || seen.has(oid)) {
      continue
    }
    seen.add(oid)
    out.push({
      entityOid: oid,
      typeCode: typeCodeOf(item),
      entityCode: item?.code || item?.entityCode || undefined,
    })
  }
  return out
}

/**
 * 可选为关联对象的候选项：**同类型 + 同生命周期状态**，且**尚未选中**。
 *
 * <p>为什么要卡状态：一批对象是<b>一起走同一道审批</b>的（审批意见、流程变量都按这一个
 * 上下文产出）。类型不同，流程变量与审批意见对不上；状态不同，则"这次审批在推进谁的状态"
 * 本身就是笔糊涂账 —— 草稿与已发布一起进同一道审批，出来该落在哪个状态上没人说得清。
 * 所以候选只列与<b>当前已选对象</b>同类型同状态的那些。
 *
 * <p>候选来自全局搜索（按名称/编码模糊匹配），因此这里要筛三道：类型、状态、已选中。
 * 没有 oid 的结果直接丢。状态比较忽略大小写（后端 code 可能是 ISSUED / issued）。
 * 返回值带上类型/状态字段：清单里要用它们显示类型与状态标签，缺了就会是一片空白。
 *
 * @param {Array<object|null>|null|undefined} results 全局搜索结果（可能含 null 行，容忍掉）
 * @param {{typeCode?: string, statusCode?: string, excludeOids?: string[]}} [scope]
 * @returns {Array<{oid: string, code: string, name: string, typeDefinitionCode?: string,
 *   typeDefinitionName?: string, statusCode?: string, statusName?: string}>}
 */
export function entityCandidates(results, { typeCode = undefined, statusCode = undefined, excludeOids = [] } = {}) {
  const exclude = new Set((excludeOids || []).filter(Boolean))
  const wantedStatus = normalizeStatus(statusCode)
  return (results || [])
    .filter((item) => oidOf(item) && !exclude.has(oidOf(item)))
    .filter((item) => !typeCode || typeCodeOf(item) === typeCode)
    .filter((item) => !wantedStatus || normalizeStatus(statusCodeOf(item)) === wantedStatus)
    .map((item) => ({
      oid: oidOf(item),
      code: item?.code || '',
      name: item?.name || '',
      typeDefinitionCode: typeCodeOf(item),
      typeDefinitionName: typeNameOf(item),
      statusCode: statusCodeOf(item),
      statusName: item?.statusName || undefined,
    }))
}

/**
 * 被"状态不同"挡掉的同类型对象 —— 只用于给用户一句解释。
 *
 * <p>搜到 8 条却只列出 2 条，用户会以为搜索坏了。这里把"同类型但状态不同"的挑出来，
 * 让界面能说清「另有 N 条同类型对象因状态不是『草稿』而不参与本批」。
 *
 * @param {Array<object|null>|null|undefined} results 全局搜索结果
 * @param {{typeCode?: string, statusCode?: string, excludeOids?: string[]}} [scope]
 * @returns {Array<{oid?: string}>} 被状态挡下的同类型对象（调用方只用来数个数 / 报名字）
 */
export function entityCandidateRejects(results, { typeCode = undefined, statusCode = undefined, excludeOids = [] } = {}) {
  const exclude = new Set((excludeOids || []).filter(Boolean))
  const wantedStatus = normalizeStatus(statusCode)
  return (results || []).filter((item) => {
    const oid = oidOf(item)
    if (!oid || exclude.has(oid)) return false
    if (typeCode && typeCodeOf(item) !== typeCode) return false
    if (!wantedStatus) return false
    return normalizeStatus(statusCodeOf(item)) !== wantedStatus
  })
}

/** 状态 code 归一：忽略大小写与空白，未填视为"未设置" */
function normalizeStatus(value) {
  return String(value ?? '').trim().toUpperCase()
}

/**
 * 整批对象是否属于"同一桶"（同类型 + 同状态）。
 *
 * <p>提交前最后一道自检：候选列表已经做过过滤，但清单里可能混进过历史数据、
 * 或用户先加了 A 再改了别的对象的类型/状态。发出去之前必须拦下 ——
 * 后端的闸门是"逐对象是否已有实例在跑"，管不到这一层。
 */
export function sameProcessingBucket(list = []) {
  const rows = (list || []).filter(Boolean)
  if (rows.length <= 1) {
    return { ok: true }
  }
  const head = rows[0]
  const headType = typeCodeOf(head)
  for (const row of rows.slice(1)) {
    if (headType && typeCodeOf(row) && typeCodeOf(row) !== headType) {
      return {
        ok: false,
        reason: `只能把同类型对象放进同一道审批：「${row.code || row.name || '该对象'}」是 `
          + `${typeNameOf(row) || '其它类型'}，与本批的 ${typeNameOf(head) || headType} 不同`,
      }
    }
    // 状态比较走 sameStatus（code 对 code、名对名）：只认一种口径会整段失效或被误拦
    const verdict = sameStatus(head, row)
    if (verdict.known && !verdict.same) {
      return {
        ok: false,
        reason: '只能把相同生命周期状态的对象放进同一道审批：'
          + `「${row.code || row.name || '该对象'}」当前状态「${statusLabelOf(row) || '未知'}」`
          + `与本批的「${statusLabelOf(head) || statusKeyOf(head)}」不一致`,
      }
    }
  }
  return { ok: true }
}

/**
 * 正在流程中的对象不能选：命中时返回置灰/拦截的理由，否则 null。
 *
 * <p><b>为什么单独成一个函数</b>：这条规则在弹窗里有两处要用（候选置灰 + 添加时复检），
 * 两处各写一遍必然漂移 —— 一处改了另一处忘改，就会出现"灰着却能加进来"。
 * 判定结果由后端给（{@code GET /workflow/instance/running-entities}），
 * 因为"是否在流程中"是关联表 + 引擎运行时的事，前端无从推断。
 */
export function runningBlockReason(entity, runningOids) {
  const oid = oidOf(entity)
  if (!oid || !runningOids) {
    return null
  }
  const hit = runningOids instanceof Set
    ? runningOids.has(oid)
    : Array.isArray(runningOids) && runningOids.includes(oid)
  return hit ? '已在流程中（需先完成或终止该流程）' : null
}

/**
 * 关联对象的行内文案（编码 + 名称）：清单里靠编码认对象，名称给人看。
 *
 * <p>两者都没有时给一句占位而不是空白 —— 空白行会让人以为数据没加载出来。
 */
export function entityLine(item) {
  const code = item?.code || item?.entityCode || ''
  const name = item?.name || ''
  if (code && name) return `${code} ${name}`
  return code || name || '（未知对象）'
}

/**
 * 提示语里当"主语"的对象说法：名称（编码）。
 *
 * <p>与 {@link entityLine} 刻意不同：那是清单里逐行认对象，编码在前便于扫读；
 * 这里是嵌进一句人话里的主语，名称在前才念得通 ——
 * 「20W电容（PART-202609-0020）的『元部件引入』已启动」。
 *
 * @param {{code?: string, entityCode?: string, name?: string}|null} item 业务对象（列表行或发起载荷）
 * @returns {string} 如「20W电容（PART-202609-0020）」
 */
export function entitySubject(item) {
  const code = item?.code || item?.entityCode || ''
  const name = item?.name || ''
  if (name && code) return `${name}（${code}）`
  return name || code || '业务对象'
}

/**
 * 发起成功后的提示语 —— 面向用户的一句话，不是面向日志的实例号。
 *
 * <p>以前弹的是「流程已发起（实例 5dcba301-…）」：UUID 对用户没有意义，
 * 也没说清"发的是哪条对象、哪个流程、接下来该去哪"。这里一次说清三件事。
 *
 * <p>清单是"一批对象一起走同一道审批"的形态：多于一条时报第一条 + 总数，
 * 把 4 条全塞进提示会变成一段读不完的文字（整批对象在办理页「业务实体」栏里看得全）。
 *
 * @param {object} [input]
 * @param {Array<object>} [input.entities] 本次关联的对象清单（取第一条 + 总数）
 * @param {object|null} [input.business] 发起上下文对象（清单为空时兜底）
 * @param {string} [input.processName] 流程模板显示名
 * @returns {string} 如「20W电容（PART-202609-0020）的「元部件引入」已启动，请到任务中心跟进。」
 */
export function startedNotice({ entities = [], business = null, processName = '' } = {}) {
  const list = (entities || []).filter(Boolean)
  const first = list[0] || business || null
  const head = entitySubject(first)
  const objects = list.length > 1 ? `${head}等 ${list.length} 个对象` : head
  return `${objects}的「${processName || '流程'}」已启动，请到任务中心跟进。`
}
