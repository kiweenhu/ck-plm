<template>
  <a-form layout="vertical">
    <!-- ⓪ 只读：上游「技术评审申请单」填过的内容（各 TR 节点共用同一个摘要组件）——
         分派整改前先得看清评的是谁、送审了什么材料，以及材料关联了哪些业务对象 -->
    <TechnicalReviewRequestSummary :task-id="taskId" />

    <!-- ① 上游检查项结果（只读） -->
    <div class="tri-section">检查项核对结果</div>

    <a-spin :spinning="loading">
      <template v-if="loadedUpstream">
        <div class="tri-summary">
          <a-tag v-if="upstream.verdictLabel" :color="verdictColor">{{ upstream.verdictLabel }}</a-tag>
          <span>不符合 <b :class="{ 'tri-danger': failItems.length > 0 }">{{ failItems.length }}</b> 项</span>
          <span>其中阻断 <b :class="{ 'tri-danger': blockCount > 0 }">{{ blockCount }}</b> 项</span>
          <span v-if="upstream.reviewLabel" class="tri-muted">评审模板：{{ upstream.reviewLabel }}</span>
        </div>

        <a-table
          v-if="failItems.length"
          :columns="CHECK_COLUMNS"
          :data-source="failItems"
          :pagination="false"
          row-key="seq"
          size="small"
          class="tri-table"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'severity'">
              <a-tag :color="record.severity === 'BLOCK' ? 'error' : 'warning'" size="small">
                {{ record.severity }}
              </a-tag>
            </template>
          </template>
        </a-table>
        <a-alert
          v-else
          type="success"
          show-icon
          class="tri-alert"
          message="上游检查项没有不符合项"
          description="可以只写办理说明后提交；若另需登记已知问题，用下面的「添加一条」。"
        />
      </template>

      <a-alert
        v-else-if="!loading"
        type="warning"
        show-icon
        class="tri-alert"
        message="未取到上游检查结果"
        description="可能流程未经过「检查项逐条核对」，或该节点尚未留下结论。仍可手工登记问题并提交 —— 不因为取不到上游结果就卡住办理。"
      />
    </a-spin>

    <!-- ② 问题登记与分派 -->
    <div class="tri-section">
      问题登记与整改分派
      <span class="tri-section-tip">登记后立即写入「持续改进 → 问题」，责任人收到待办</span>
    </div>

    <a-table
      :columns="ISSUE_COLUMNS"
      :data-source="issues"
      :pagination="false"
      row-key="_key"
      size="small"
      class="tri-table"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <a-input v-model:value="record.name" size="small" placeholder="问题描述（必填）" :disabled="record.registered" />
        </template>
        <template v-else-if="column.key === 'severity'">
          <a-select v-model:value="record.severity" size="small" style="width: 100%" :disabled="record.registered">
            <a-select-option value="BLOCK">阻断</a-select-option>
            <a-select-option value="WARN">警告</a-select-option>
          </a-select>
        </template>
        <!-- 责任人用人员单选（取值 username，与 ck_issue.owner / 状态机的 UserContext 口径一致）：
             手打用户名既会写错，也会造出"没人能处理"的问题单 —— 后端的 requiresOwner
             就是拿登录账号与 owner 比对，填错一个字这条问题就永远转不动 -->
        <template v-else-if="column.key === 'owner'">
          <ChangeUserSelect
            :value="record.owner"
            :disabled="record.registered"
            placeholder="选择责任人"
            @update:value="(val) => (record.owner = val || '')"
          />
        </template>
        <template v-else-if="column.key === 'dueDate'">
          <a-date-picker
            v-model:value="record.dueDate"
            size="small"
            style="width: 100%"
            value-format="YYYY-MM-DD"
            placeholder="整改期限"
            :disabled="record.registered"
          />
        </template>
        <template v-else-if="column.key === 'state'">
          <a-tag v-if="record.registered" color="green" size="small">{{ record.code || '已登记' }}</a-tag>
          <span v-else class="tri-muted">待登记</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button v-if="!record.registered" type="link" size="small" danger @click="removeRow(record)">删除</a-button>
          <span v-else class="tri-muted">已入库</span>
        </template>
      </template>
      <template #emptyText>
        <div class="tri-empty">
          还没有问题。<a v-if="failItems.length" @click="importFails">从上游不符合项导入</a>
          <span v-else>点下面「添加一条」</span>
        </div>
      </template>
    </a-table>

    <div class="tri-actions">
      <a-space>
        <a-button size="small" @click="addRow()">
          <template #icon><PlusOutlined /></template>添加一条
        </a-button>
        <a-button v-if="failItems.length" size="small" @click="importFails">从不符合项导入</a-button>
      </a-space>
      <a-button
        type="primary"
        ghost
        size="small"
        :loading="registering"
        :disabled="!pendingCount"
        @click="registerAll"
      >
        登记并分派{{ pendingCount ? `（${pendingCount}）` : '' }}
      </a-button>
    </div>

    <!-- ③ 办理说明 -->
    <div class="tri-section">办理说明</div>
    <a-form-item label="整改要求 / 说明">
      <a-textarea
        v-model:value="model.comment"
        :rows="3"
        placeholder="例如：整改进度要求、复评时间、责任划分；无补充可留空（提交后记入本节点意见）"
      />
    </a-form-item>
  </a-form>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { ChangeUserSelect } from '@/components/change/changeFieldWidgets'
import { createIssue, getTaskContext, getProcessInstanceDetail, listReviewTemplates } from '@/api'
import TechnicalReviewRequestSummary from './TechnicalReviewRequestSummary.vue'

/**
 * 「技术评审问题登记与分派」表单 —— 技术评审（TR）流程 `n_issue` 节点。
 *
 * <h3>三块，对应节点要干的三件事</h3>
 * <ol>
 *   <li><b>看检查结果</b>：上游「检查项逐条核对」的结论从流程变量读（同一份数据，
 *       不另算一遍）——{@code ckplmTechReview_verdict / _failItems / _blockCount}。
 *       检查项原文按 {@code ckplmTechReview_reviewTemplate} 回到评审模板的 {@code checkItems} 取，
 *       与核对表单同一套解析；取不到就只显示序号与级别，不猜文字。</li>
 *   <li><b>登记问题</b>：逐行填描述 / 严重度 / 责任人 / 期限，点「登记并分派」立即调
 *       {@code POST /api/issues} 落库（不等到提交节点才写 —— 登记是独立事实，
 *       用户中途离开也不该丢）。</li>
 *   <li><b>提交节点</b>：{@code collect()} 只做校验与流程变量，提交后流程转「等待整改结果」。</li>
 * </ol>
 *
 * <h3>为什么登记按钮与提交分开</h3>
 * <p>渲染器（TaskFormRenderer）的 {@code collect()} 是<b>同步</b>纯函数，提交一次性完成；
 * 而登记问题要走 HTTP、要拿到后端生成的编号回显。硬塞进提交流程会让"提交失败但问题已入库"
 * 与"问题入库失败但节点已推进"两种错位都可能发生。分开之后：登记失败可以重试，提交失败不会重复建问题。
 *
 * <h3>产品归属</h3>
 * <p>技术评审可能评零件/文档，不一定挂产品。有产品实体（PRODUCT_LINE / PRODUCT_MODEL）就带上，
 * 没有就留空 —— {@code ck_issue.product_oid} 可空，编一个假归属比空着更糟。
 */
const props = defineProps({
  /** 任务 id —— 取上下文与流程变量用 */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
})

const CHECK_COLUMNS = [
  { title: '#', dataIndex: 'seq', key: 'seq', width: 56 },
  { title: '检查项', dataIndex: 'text', key: 'text' },
  { title: '级别', key: 'severity', width: 84 },
  { title: '核对说明', dataIndex: 'note', key: 'note', width: 220 },
]

const ISSUE_COLUMNS = [
  { title: '问题描述', key: 'name' },
  { title: '严重度', key: 'severity', width: 108 },
  { title: '责任人', key: 'owner', width: 180 },
  { title: '整改期限', key: 'dueDate', width: 150 },
  { title: '状态', key: 'state', width: 110 },
  { title: '操作', key: 'action', width: 80 },
]

const VERDICT_LABEL = {
  PASS: '通过', PASS_WITH_CONDITION: '有条件通过', FAIL: '不通过',
}

const loading = ref(true)
/** 登记问题时带上的产品归属（流程未关联产品则为空） */
const productRef = ref(null)
/** 是否真的取到了上游检查结果（取不到 ≠ 没有不符合项，两者必须分开显示） */
const loadedUpstream = ref(false)
const upstream = ref({ verdict: '', reviewLabel: '', reviewTemplateCode: '' })
const failItems = ref([])
const issues = ref([])
const registering = ref(false)
const model = ref({ comment: '' })

let rowSeq = 0

const blockCount = computed(() => failItems.value.filter((it) => it.severity === 'BLOCK').length)
const verdictColor = computed(() => {
  if (upstream.value.verdict === 'FAIL') return 'error'
  if (upstream.value.verdict === 'PASS_WITH_CONDITION') return 'warning'
  return 'success'
})

/** 从流程实例详情里取变量（结构容错：可能是 { name: value } 也可能是 [{name,value}]） */
function variableOf(variables, name) {
  if (!variables) return null
  if (Array.isArray(variables)) {
    const hit = variables.find((v) => v && v.name === name)
    return hit ? hit.value : null
  }
  return variables[name] ?? null
}

async function load() {
  loading.value = true
  try {
    // 1) 流程上下文 → 实例 id → 变量
    const ctxRes = props.taskId ? await getTaskContext(props.taskId).catch(() => null) : null
    const ctx = ctxRes?.code === 200 ? ctxRes.data : null
    const instId = ctx?.process?.id || null
    const detailRes = instId ? await getProcessInstanceDetail(instId).catch(() => null) : null
    const variables = detailRes?.code === 200 ? detailRes.data?.variables : null

    const verdict = variableOf(variables, 'ckplmTechReview_verdict')
    const templateCode = variableOf(variables, 'ckplmTechReview_reviewTemplate')
    const rawFails = variableOf(variables, 'ckplmTechReview_failItems')

    upstream.value.verdict = verdict || ''
    upstream.value.reviewTemplateCode = templateCode || ''

    // 2) 检查项原文：按评审模板码（也可能是模板名，核对表单写的是 label）回查
    let checkTexts = {}
    if (templateCode) {
      const reviewRes = await listReviewTemplates().catch(() => null)
      const templates = reviewRes?.code === 200 ? (reviewRes.data || []) : []
      const tpl = templates.find((t) => t.code === templateCode)
        || templates.find((t) => t.name === templateCode)
        || null
      if (tpl) {
        upstream.value.reviewLabel = tpl.name || tpl.code
        try {
          const parsed = JSON.parse(tpl.checkItems || '[]')
          if (Array.isArray(parsed)) {
            parsed.forEach((it, idx) => {
              checkTexts[it.seq ?? (idx + 1)] = it.text || it.name || ''
            })
          }
        } catch { /* 模板配错就当没有原文，只显示序号与级别 */ }
      } else {
        upstream.value.reviewLabel = templateCode
      }
    }

    // 3) 不符合项：核对表单只落"不符合"的项（seq / severity / note）
    let parsed = []
    if (rawFails) {
      try {
        const arr = JSON.parse(rawFails)
        if (Array.isArray(arr)) parsed = arr
      } catch { /* 变量格式不对就当没有，不挡办理 */ }
    }
    failItems.value = parsed.map((it) => ({
      seq: it.seq,
      severity: it.severity || 'WARN',
      note: it.note || '',
      text: checkTexts[it.seq] || `检查项 #${it.seq}`,
    }))

    loadedUpstream.value = Boolean(verdict || rawFails)

    // 4) 产品归属（有就带，没有就空着）
    const entities = Array.isArray(ctx?.entities) ? ctx.entities : []
    const product = entities.find((e) => {
      const code = String(e.typeCode || e.rootTypeCode || '').toUpperCase()
      return code.includes('PRODUCT')
    })
    if (product?.entityOid) {
      const code = String(product.typeCode || product.rootTypeCode || '').toUpperCase()
      // 必须是后端 IssueServiceImpl 认可的枚举名（PRODUCT_LINE / PRODUCT_MODEL），
      // 不是实体类型码的简写 —— 写成 'LINE' 会被 validateProduct 直接拒掉
      productRef.value = {
        type: code.includes('MODEL') ? 'PRODUCT_MODEL' : 'PRODUCT_LINE',
        oid: product.entityOid,
      }
    }
  } catch {
    loadedUpstream.value = false
  } finally {
    loading.value = false
  }
}

onMounted(load)

/* ==================== 问题行 ==================== */

const pendingCount = computed(() => issues.value.filter((r) => !r.registered).length)

function addRow(prefill) {
  issues.value.push({
    _key: ++rowSeq,
    name: prefill?.text || '',
    severity: prefill?.severity || 'WARN',
    owner: '',
    dueDate: null,
    registered: false,
    /** 登记成功后由后端返回，写进流程变量供「等待整改结果」节点精确加载 */
    oid: '',
    code: '',
  })
}

/** 一键把上游不符合项变成待登记行 —— 逐条手抄检查项是这类表单最常见的低效动作 */
function importFails() {
  if (!failItems.value.length) return
  failItems.value.forEach((it) => addRow(it))
  message.info(`已从不符合项带入 ${failItems.value.length} 条待登记问题`)
}

function removeRow(record) {
  issues.value = issues.value.filter((r) => r._key !== record._key)
}

async function registerAll() {
  const pending = issues.value.filter((r) => !r.registered)
  if (!pending.length) {
    message.info('没有待登记的问题')
    return
  }
  for (const row of pending) {
    if (!row.name.trim()) {
      message.warning('有问题描述为空：请填写，或删除该行')
      return
    }
    if (!String(row.owner || '').trim()) {
      message.warning(`「${row.name.trim()}」还没有分派责任人`)
      return
    }
  }
  registering.value = true
  let ok = 0
  try {
    for (const row of pending) {
      const res = await createIssue({
        name: row.name.trim(),
        severity: row.severity,
        owner: String(row.owner || '').trim(),
        dueDate: row.dueDate || undefined,
        source: 'OTHER',
        productType: productRef.value?.type,
        productOid: productRef.value?.oid,
      })
      if (res?.code !== 200) {
        // 不继续后面几行：失败原因由响应拦截器统一提示，继续只会刷一屏一样的错
        message.error(`「${row.name.trim()}」登记失败，已登记 ${ok} 条`)
        return
      }
      row.registered = true
      row.oid = res.data?.oid || ''
      row.code = res.data?.code || ''
      ok++
    }
    message.success(`已登记并分派 ${ok} 条问题`)
  } catch {
    // 同上：错误提示已由拦截器给出
  } finally {
    registering.value = false
  }
}

/* ==================== 提交契约 ==================== */

const submitText = computed(() => '提交问题清单并流转')
const successText = computed(() => '问题已登记，流程转「等待整改结果」')
const renderable = computed(() => true)

function collect() {
  if (pendingCount.value > 0) {
    return { ok: false, message: '还有未登记的问题行：请先「登记并分派」，或删除该行' }
  }
  const registered = issues.value.filter((r) => r.registered)
  if (!registered.length && failItems.value.length > 0) {
    return {
      ok: false,
      message: '上游存在不符合项，请至少登记一条问题；若这些不符合项已在别处登记，请在「办理说明」里写明后清空本表',
    }
  }

  const codes = registered.map((r) => r.code).filter(Boolean)
  return {
    ok: true,
    payload: {
      action: 'approve',
      comment: model.value.comment.trim()
        || (codes.length ? `本次登记问题 ${codes.length} 条：${codes.join('、')}` : '本次评审无需登记问题'),
      variables: {
        ckplmTechReview_issueCount: registered.length,
        ckplmTechReview_issueCodes: codes.join(','),
        // 下游「等待整改结果」节点靠它精确加载"本次评审登记的那几条问题"；
        // 老实例没这个变量时，那张表单会退化成"我名下未闭环的问题"
        ckplmTechReview_issueOids: registered.map((r) => r.oid).filter(Boolean).join(','),
        ckplmTechReview_issueBlockCount: registered.filter((r) => r.severity === 'BLOCK').length,
        ckplmTechReview_issueOwners: registered
          .map((r) => String(r.owner || '').trim())
          .filter(Boolean)
          .join(','),
      },
    },
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.tri-section {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}
.tri-section-tip {
  margin-left: 10px;
  font-weight: 400;
  font-size: 11px;
  color: #bfbfbf;
}
.tri-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #666;
}
.tri-muted { color: #bfbfbf; }
.tri-danger { color: #cf1322; }
.tri-table { margin-bottom: 8px; }
.tri-alert { margin-bottom: 10px; }
.tri-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.tri-empty { padding: 8px 0; font-size: 12px; color: #bfbfbf; }
</style>
