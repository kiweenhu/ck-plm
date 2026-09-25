import { ref, watch } from 'vue'
import { getEntityByCode } from '@/api'

/**
 * 业务实体展示信息回查 —— 由「实体引用」得到人能读的一行字。
 *
 * <p>流程侧只给<b>引用</b>（{@code typeCode + entityOid + 大版本}），不直接给编码/名称/状态：
 * 那要么回查业务表、要么由各宿主自行解释，让流程模块去读 Part / Document 就等于反向依赖各业务模块。
 * 所以显示信息的回查在前端做，且<b>字段口径只写在这一处</b> —— 软类型详情的字段名各宿主略有差别
 * （编号是 number 还是 code、状态是 statusName 还是别的），映射散到各页面必然会分叉。
 *
 * <p>回查是<b>锦上添花</b>：查不到（无权限 / 已删除）就退化为只显示引用，绝不影响主流程，所以失败只吞掉。
 */

/**
 * 按实体引用回查展示信息。
 *
 * @param {{entityOid?: string, typeCode?: string}} entity 实体引用
 * @returns {Promise<{oid: string, code: string, name: string, statusName: string,
 *   revision: string, displayVersion: string} | null>} 查不到时返回 null
 */
export async function fetchEntityInfo(entity) {
  const oid = entity?.entityOid
  // 没有 typeCode 就没有统一入口可路由（后端按 typeDefinitionCode 分派），明确跳过
  if (!oid || !entity?.typeCode) return null
  try {
    const res = await getEntityByCode(entity.typeCode, oid)
    const data = res?.data || {}
    return {
      oid,
      code: data.number || data.code || '',
      name: data.name || data.displayName || '',
      /** 生命周期状态显示名（迭代状态，如「已发布」） */
      statusName: data.statusName || '',
      /** 大版本（A）与小版本（A.2）：小版本是"当前最新"，大版本才是流程绑定的那个 */
      revision: data.revision || '',
      displayVersion: data.displayVersion || '',
    }
  } catch {
    return null
  }
}

/**
 * 任务上下文里的实体展示信息。
 *
 * <p><b>为什么做成 composable 而不是各组件各查一遍</b>：页头与「通用信息 · 业务实体」
 * 要的是同一份结果。各查一遍就是同一个实体两次请求，而且两处还可能显示不一致
 * （一个查到了、一个没查到）。由办理页调用一次，结果下传给子组件。
 *
 * @param {import('vue').Ref<object|null>} context 任务上下文（GET /workflow/task/{id}/context 的 data）
 * @returns {{ infos: import('vue').Ref<Record<string, object>> }}
 *   entityOid → 展示信息；查询失败或没有权限的实体不会出现在里面
 */
export function useEntityInfo(context) {
  const infos = ref({})

  async function resolve() {
    const list = context.value?.entities || []
    const resolved = await Promise.all(list.map((entity) => fetchEntityInfo(entity)))
    const next = {}
    for (const info of resolved) {
      if (info) next[info.oid] = info
    }
    // 整体替换而不是合并：换了任务就该丢掉上一条的实体名，否则会串味
    infos.value = next
  }

  watch(context, resolve, { immediate: true })

  return { infos }
}
