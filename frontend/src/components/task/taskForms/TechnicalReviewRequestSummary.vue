<template>
  <div class="trs">
    <!-- 标题跟着组件走：5 个节点要么都有一块"申请（只读）"，要么都没有，别让各表各判一次 -->
    <div class="trs-title">技术评审申请（只读）</div>

    <a-spin :spinning="loading" size="small">
      <a-descriptions v-if="hasInfo" bordered size="small" :column="2" class="trs-fields">
        <a-descriptions-item label="待评对象" :span="2">
          <div v-if="targets.length" class="trs-targets">
            <span v-for="t in targets" :key="t.oid" class="trs-target">
              <b>{{ t.code }}</b>
              <span v-if="t.name" class="trs-muted">{{ t.name }}</span>
              <a-tag v-if="t.version" size="small">版本 {{ t.version }}</a-tag>
            </span>
          </div>
          <span v-else class="trs-muted">该流程未关联业务对象</span>
        </a-descriptions-item>
        <a-descriptions-item label="评审模板">{{ templateLabel || '—' }}</a-descriptions-item>
        <a-descriptions-item label="所处阶段">{{ stageLabel || '—' }}</a-descriptions-item>
        <a-descriptions-item label="关联阶段门">{{ gateLabel || '—' }}</a-descriptions-item>
        <!-- 送审材料直接列在格子里且**可点开**：只给交付项名与 code，看不出它关联了哪些业务对象，
             而那正是核对前要看的东西（2026-10-29 用户口径，与申请单同一条路径） -->
        <a-descriptions-item label="送审材料">
          <div v-if="materials.length" class="trs-materials">
            <template v-for="m in materials" :key="m.code">
              <a
                v-if="m.deliverable"
                class="trs-material-link"
                :title="`查看「${m.name || m.code}」关联的业务对象（产出物）`"
                @click="openMaterial(m)"
              >
                {{ m.name || m.code }}<span v-if="m.name" class="trs-muted">（{{ m.code }}）</span>
              </a>
              <span v-else class="trs-material">
                {{ m.name || m.code }}<span v-if="m.name" class="trs-muted">（{{ m.code }}）</span>
              </span>
            </template>
          </div>
          <span v-else class="trs-muted">—</span>
        </a-descriptions-item>
      </a-descriptions>

      <div v-if="hasMaterialLink" class="trs-hint">
        送审材料点名称可看它关联的业务对象（产出物）—— 只读，关联关系在「交付件」面板维护。
      </div>

      <a-empty
        v-if="!loading && !hasInfo"
        description="未取到技术评审申请内容 —— 该流程未关联业务对象，也没有填过的申请单变量"
        :image-style="{ height: '32px' }"
        class="trs-empty"
      />
    </a-spin>

    <!-- 交付项详情 = 复用交付件面板的「产出物关联」弹窗（与申请单同一个组件、同一口径）：
         传 readonly 去掉「添加 / 解除产出物」—— 写入入口仍然只有交付件管理页 -->
    <DeliverableArtifactModal
      v-model:open="materialVisible"
      :deliverable="materialDetail"
      :owner-oid="ownerOid"
      readonly
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import {
  getTaskContext,
  getProcessInstanceDetail,
  listReviewTemplates,
  listStageTemplates,
  listGateTemplates,
  listMethodologyInstances,
} from '@/api'
import { fetchEntityInfo } from '@/composables/useEntityInfo'
import DeliverableArtifactModal from '@/components/DeliverableArtifactModal.vue'
import { asCodeList, variableOf } from './variables'

/**
 * 「技术评审申请」只读摘要 —— 技术评审（TR）流程**各办理节点共用**的一块。
 *
 * <h3>为什么要单独抽出来</h3>
 * <p>用户反馈（2026-10-29）：办到「检查项逐条核对」「问题登记与整改分派」等节点时，
 * <b>看不到申请单填过的内容</b> —— 检查项、送审材料都成了悬空的，核对与分派只能凭记忆。
 * 同一份申请内容要在 5 个节点上都看得到，各写一遍必然漂移，所以收口在这里
 * （与「元器件引入申请信息」的 {@code QualificationRequestSummary} 同一手法）。
 *
 * <h3>数据来源</h3>
 * <ol>
 *   <li><b>流程变量</b>（{@code ckplmTechReview_*}，申请节点写入）：评审模板 / 所处阶段 /
 *       关联阶段门 / 送审材料 / 待评对象编码串；</li>
 *   <li><b>流程关联实体</b>（{@code context.entities}）：待评对象的编码、名称、版本；</li>
 *   <li><b>交付件</b>（{@code /instances} 的 deliverables，与申请单下拉同一数据源）：
 *       送审材料的名称与整行 —— 点名称打开「产出物关联」看它关联了哪些业务对象。</li>
 * </ol>
 *
 * <p>全部 fail-soft：查不到就退回 code 或显示「—」，绝不因为"摘要取不到"挡住办理。
 * 只读组件，不暴露 collect()。
 */
const props = defineProps({
  /** 任务 id */
  taskId: { type: String, default: '' },
})

const loading = ref(true)
const targets = ref([])
const stageLabel = ref('')
const gateLabel = ref('')
const templateLabel = ref('')
/** 送审材料：交付件 code + 名称 + 整行（点开「关联业务对象」吃的就是整行） */
const materials = ref([])
/** 归属单元 oid —— 产出物弹窗用它标注「本型号 / 其它容器」 */
const ownerOid = ref('')

const hasInfo = computed(() =>
  targets.value.length > 0
  || materials.value.length > 0
  || !!stageLabel.value
  || !!gateLabel.value
  || !!templateLabel.value,
)
/** 有可点开的材料名时才给那句操作提示 —— 一条都点不开就别画饼 */
const hasMaterialLink = computed(() => materials.value.some((m) => !!m.deliverable))

/** 模板行 →「名称（编码）」；找不到就显示裸 code，不编造 */
function labelOfTemplate(rows, code) {
  if (!code) return ''
  const hit = (rows || []).find((row) => row.code === code)
  return hit?.name ? `${hit.name}（${code}）` : code
}

async function load() {
  loading.value = true
  targets.value = []
  stageLabel.value = ''
  gateLabel.value = ''
  templateLabel.value = ''
  materials.value = []
  ownerOid.value = ''
  if (!props.taskId) {
    loading.value = false
    return
  }
  try {
    const ctxRes = await getTaskContext(props.taskId).catch(() => null)
    const ctx = ctxRes?.code === 200 ? ctxRes.data : null
    const entityRefs = ctx?.entities || []
    const instId = ctx?.process?.id || null
    const detailRes = instId ? await getProcessInstanceDetail(instId).catch(() => null) : null
    const variables = detailRes?.code === 200 ? detailRes.data?.variables : null

    const templateCode = variableOf(variables, 'ckplmTechReview_reviewTemplate')
    const stageCode = variableOf(variables, 'ckplmTechReview_stage')
    const gateCode = variableOf(variables, 'ckplmTechReview_gate')
    const materialCodes = asCodeList(variableOf(variables, 'ckplmTechReview_materialRefs'))

    const [infos, reviewRes, stageRes, gateRes] = await Promise.all([
      Promise.all(entityRefs.map((ref) => fetchEntityInfo(ref).catch(() => null))),
      templateCode ? listReviewTemplates().catch(() => null) : null,
      stageCode ? listStageTemplates().catch(() => null) : null,
      gateCode ? listGateTemplates().catch(() => null) : null,
    ])

    targets.value = entityRefs.map((ref, idx) => ({
      oid: ref.entityOid,
      code: infos[idx]?.code || ref.entityCode || ref.entityOid,
      name: infos[idx]?.name || '',
      version: ref.entityVersion || infos[idx]?.revision || '',
    }))
    // 流程没关联实体时退回申请时记下的编码串：至少知道评的是谁
    if (!targets.value.length) {
      targets.value = asCodeList(variableOf(variables, 'ckplmTechReview_targetObjects'))
        .map((code) => ({ oid: code, code, name: '', version: '' }))
    }

    // 只认"申请单真的选了模板"：没变量时不留兜底名，免得把别的模板当成申请内容。
    // 变量可能是 code，也可能是历史上被写进去的展示名 —— 两种都认，显示统一成「名称（code）」
    const templates = reviewRes?.code === 200 ? (reviewRes.data || []) : []
    const tpl = templates.find((row) => row.code === templateCode)
      || templates.find((row) => row.name === templateCode)
    templateLabel.value = templateCode
      ? (tpl?.name ? `${tpl.name}（${tpl.code}）` : templateCode)
      : ''
    stageLabel.value = labelOfTemplate(stageRes?.code === 200 ? stageRes.data : [], stageCode)
    gateLabel.value = labelOfTemplate(gateRes?.code === 200 ? gateRes.data : [], gateCode)

    // 材料：名称与整行都取自本归属单元的交付件（与申请单下拉同一数据源）
    ownerOid.value = entityRefs[0]?.entityOid || ''
    let rowByCode = new Map()
    if (materialCodes.length && ownerOid.value) {
      const res = await listMethodologyInstances(ownerOid.value).catch(() => null)
      const rows = res?.code === 200 ? (res.data?.deliverables || []) : []
      rowByCode = new Map(rows.map((row) => [row.code, row]))
    }
    materials.value = materialCodes.map((code) => {
      const row = rowByCode.get(code)
      return { code, name: row?.name || '', deliverable: row || null }
    })
  } catch {
    // 摘要只是"看得更清楚"的辅助，取不到不影响核对与提交
  } finally {
    loading.value = false
  }
}

// ==================== 交付项详情（关联业务对象） ====================

const materialVisible = ref(false)
const materialDetail = ref(null)

/**
 * 点送审材料名 → 打开「产出物关联」（只读）。
 *
 * <p>复用的正是申请单选材料时那个弹窗：交付项"要交什么、关联了哪些产出物"只有一处口径，
 * 在这里另写一张列表必然与交付件面板漂移。回查不到该交付件（已裁剪 / 换阶段）时不给空抽屉。
 */
function openMaterial(m) {
  if (!m?.deliverable) return
  materialDetail.value = m.deliverable
  materialVisible.value = true
}

watch(() => props.taskId, load, { immediate: true })
</script>

<style scoped>
/* 与各表单自己的 .trc/.tri/.trf/.trr-section 同一视觉：蓝色竖条 + 加粗 */
.trs-title {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.trs-fields {
  margin-bottom: 6px;
}

.trs-targets {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.trs-target {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 送审材料：格子内一条一行（材料多时自然换行，不把表格撑宽） */
.trs-materials {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.trs-material { color: #333; }

/* 可点开的材料名：与申请单同一视觉（蓝字 + 虚下划线），让人知道点得进去 */
.trs-material-link {
  color: #1677ff;
  cursor: pointer;
  border-bottom: 1px dashed transparent;
}
.trs-material-link:hover { border-bottom-color: #1677ff; }

/* 操作提示：小一号、灰一点，别和申请内容抢注意力 */
.trs-hint {
  margin: 6px 0 14px;
  font-size: 12px;
  color: #8c8c8c;
}

.trs-muted {
  color: #8c8c8c;
  font-size: 12px;
}

.trs-empty {
  margin: 4px 0 12px;
}
</style>
