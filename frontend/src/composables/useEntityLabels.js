import { ref } from 'vue'
import { getChangeDocument } from '@/api'
import { fetchEntityInfo } from '@/composables/useEntityInfo'
import { buildChangeDocLabel, buildEntityLabel } from '@/utils/entityRef'
import {
  CHANGE_NOTICE_STATUS_COLOR,
  CHANGE_NOTICE_STATUS_LABEL,
  CHANGE_REQUEST_STATUS_COLOR,
  CHANGE_REQUEST_STATUS_LABEL,
  CHANGE_TASK_STATUS_COLOR,
  CHANGE_TASK_STATUS_LABEL,
} from '@/utils/changeFlow'

/** 变更三类单据的状态词表：kind → { label, color }（同一空间里的状态码，按单据类型翻） */
const CHANGE_STATUS_META = {
  REQUEST: { label: CHANGE_REQUEST_STATUS_LABEL, color: CHANGE_REQUEST_STATUS_COLOR },
  NOTICE: { label: CHANGE_NOTICE_STATUS_LABEL, color: CHANGE_NOTICE_STATUS_COLOR },
  TASK: { label: CHANGE_TASK_STATUS_LABEL, color: CHANGE_TASK_STATUS_COLOR },
}

/**
 * 是否变更类流程 —— 只有它们才按 businessKey 回查单据。
 *
 * <p>别的流程（文档评审等）businessKey 也是某个 oid，但那不是变更单据，
 * 无差别回查等于每行白跑一次 404。
 */
function isChangeFlow(row) {
  return String(row?.processDefinitionKey || '').startsWith('change-')
}

/**
 * 列表行的「业务对象」标签 —— 给一批行算出 {@code row._biz}，模板只做绑定。
 *
 * <p><b>为什么要有这一层</b>：流程监控的「业务标识」与任务中心的「业务对象」要显示的是同一行字
 * （编码 + 名称 + 大版本 + 生命周期状态），数据来源也一样（后端只给实体引用，编码/名称/状态
 * 由前端回查）。两处各写一遍必然漂移：一处有版本、一处没版本，或一处查一处不查。
 *
 * <p>同一页里多个行可能指向同一个对象（同一对象的多个任务），按 oid 去重只查一次。
 *
 * <p><b>两条来源</b>：
 * <ol>
 *   <li>软类型实例 —— 后端给实体引用（{@code entityOid} + typeCode），按 oid 回查编码/名称/状态；</li>
 *   <li>变更单据（请求 / 通告 / 任务）—— 它们不是软类型实例，流程里只有 businessKey，
 *       后端只把 businessKey 递过来，前端问一次 {@code /plm/change-documents/{oid}} 换成
 *       「编号 + 标题 + 状态」。没有这一层，变更类任务那行就只剩"设置审批人 + 流程名"，
 *       同一个流程挂几十张单据时根本分不清办的是哪一条。</li>
 * </ol>
 *
 * <p>回查失败（无权限 / 对象已删）不编造：{@link buildEntityLabel} 会给出"引用本身"作兜底文案，
 * 至少能跟人核对，而不是一片空白。
 */
export function useEntityLabels() {
  /** entityOid → 展示信息（编码/名称/生命周期状态），页内复用，避免同一对象重复请求 */
  const infos = ref({})

  /**
   * 给这批行挂上 {@code _biz}。
   *
   * <p>调用方应"先 attach 再上屏"（{@code await attach(rows); list.value = rows}），
   * 否则会先闪一下没有业务对象的旧样式。
   */
  async function attach(rows) {
    const refs = new Map()
    const changeKeys = new Set()
    for (const row of rows || []) {
      const ref = row?.entities?.[0]
      if (ref?.entityOid) {
        if (!refs.has(ref.entityOid)) {
          refs.set(ref.entityOid, ref)
        }
        continue
      }
      // 没有实体引用但有 businessKey：变更单据走这条（非变更流程不查，避免白跑 404）
      if (row?.businessKey && isChangeFlow(row)) {
        changeKeys.add(row.businessKey)
      }
    }

    const merged = {}
    await Promise.all([...refs.entries()].map(async ([oid, ref]) => {
      if (merged[oid]) return
      const info = await fetchEntityInfo(ref)
      if (info) merged[oid] = info
    }))
    infos.value = merged

    // 变更单据：businessKey → 单据标签（同一 businessKey 的多个任务只查一次）
    const changeLabels = new Map()
    await Promise.all([...changeKeys].map(async (businessKey) => {
      try {
        const res = await getChangeDocument(businessKey)
        if (res?.code !== 200 || !res.data) return
        const meta = CHANGE_STATUS_META[res.data.kind]
        changeLabels.set(businessKey, buildChangeDocLabel({
          ...res.data,
          statusName: meta?.label?.[res.data.status] || res.data.status || '',
          statusColor: meta?.color?.[res.data.status] || 'blue',
        }))
      } catch {
        /* 不是变更单据（或已删）：当作"没有业务对象"，不要为此打扰用户 */
      }
    }))

    for (const row of rows || []) {
      const ref = row?.entities?.[0]
      if (ref?.entityOid) {
        row._biz = buildEntityLabel(ref, merged[ref.entityOid])
      } else if (row?.businessKey && changeLabels.has(row.businessKey)) {
        row._biz = changeLabels.get(row.businessKey)
      }
    }
  }

  return { infos, attach }
}
