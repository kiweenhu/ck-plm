import { ref } from 'vue'
import { fetchEntityInfo } from '@/composables/useEntityInfo'
import { buildEntityLabel } from '@/utils/entityRef'

/**
 * 列表行的「业务对象」标签 —— 给一批行算出 {@code row._biz}，模板只做绑定。
 *
 * <p><b>为什么要有这一层</b>：流程监控的「业务标识」与任务中心的「业务对象」要显示的是同一行字
 * （编码 + 名称 + 大版本 + 生命周期状态），数据来源也一样（后端只给实体引用，编码/名称/状态
 * 由前端回查）。两处各写一遍必然漂移：一处有版本、一处没版本，或一处查一处不查。
 *
 * <p>同一页里多个行可能指向同一个对象（同一对象的多个任务），按 oid 去重只查一次。
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
    for (const row of rows || []) {
      const ref = row?.entities?.[0]
      if (ref?.entityOid && !refs.has(ref.entityOid)) {
        refs.set(ref.entityOid, ref)
      }
    }

    const merged = { ...infos.value }
    await Promise.all([...refs.entries()].map(async ([oid, ref]) => {
      if (merged[oid]) return
      const info = await fetchEntityInfo(ref)
      if (info) merged[oid] = info
    }))
    infos.value = merged

    for (const row of rows || []) {
      const ref = row?.entities?.[0]
      if (ref?.entityOid) {
        row._biz = buildEntityLabel(ref, merged[ref.entityOid])
      }
    }
  }

  return { infos, attach }
}
