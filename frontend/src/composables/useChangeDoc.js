import { ref, watch } from 'vue'
import { getChangeDocument } from '@/api'
import { buildChangeDocLabel } from '@/utils/entityRef'
import { CHANGE_STATUS_META } from '@/composables/useEntityLabels'

/** 单据类型 → 业务称呼（给「业务实体」区那一行用） */
const KIND_LABEL = { REQUEST: '变更请求', NOTICE: '变更通告', TASK: '变更任务' }

/**
 * 变更单据（请求 / 通告 / 任务）的展示信息 —— 办理页「通用信息 · 业务实体」用。
 *
 * <p><b>为什么需要它</b>：变更单据不是软类型实例，发起流程时不会写
 * {@code ck_process_entity_set}，所以后端给的 {@code entities} 恒为空 ——
 * 办理页就一直显示"该流程未关联业务实体"，可实际上办的就是 ECR-0014 这张单。
 * 流程侧只交出 {@code businessKey}（单据 oid），由前端问一次
 * {@code /plm/change-documents/{oid}} 换成「类型 + 编号 + 标题 + 状态」。
 *
 * <p><b>只在没有实体引用时才查</b>：软类型流程（元器件认证、文档评审…）走原来的实体链路，
 * 那条已经有 {@code entities}，不该多打一次请求，更不该把两种东西混在一栏里。
 *
 * <p>查不到（非变更流程 / 单据已删 / 无权限）就当作"没有业务对象"，由页面显示原来的空态 ——
 * 不编造、不弹错。
 *
 * @param {import('vue').Ref<object|null>} context 任务上下文（GET /workflow/task/{id}/context 的 data）
 * @returns {{ doc: import('vue').Ref<object|null> }}
 *   `{ kind, kindLabel, name, code, status, statusColor, path }`；无变更单据时为 null
 */
export function useChangeDoc(context) {
  const doc = ref(null)

  async function resolve() {
    doc.value = null
    const ctx = context.value
    if (!ctx) return
    // 已有实体引用：这条流程走的是软类型对象，不需要也不应该再查单据
    if ((ctx.entities || []).length) return

    const businessKey = ctx.task?.businessKey
    const flowKey = String(
      ctx.process?.processDefinitionKey || ctx.task?.processDefinitionKey || '',
    )
    // 只有变更类流程的 businessKey 才是变更单据 oid；别的流程查过去只会 404
    if (!businessKey || !flowKey.startsWith('change-')) return

    try {
      const res = await getChangeDocument(businessKey)
      if (res?.code !== 200 || !res.data) return
      const data = res.data
      const meta = CHANGE_STATUS_META[data.kind]
      const label = buildChangeDocLabel({
        ...data,
        statusName: meta?.label?.[data.status] || data.status || '',
        statusColor: meta?.color?.[data.status] || 'blue',
      })
      if (!label) return
      doc.value = {
        ...label,
        kind: data.kind,
        kindLabel: KIND_LABEL[data.kind] || '业务单据',
      }
    } catch {
      /* 不是变更单据（或已删）：按"没有业务对象"处理，不打扰用户 */
    }
  }

  watch(context, resolve, { immediate: true })

  return { doc }
}
