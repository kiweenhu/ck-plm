<template>
  <a-modal
    :open="open"
    :title="`产出物关联 —— ${deliverable?.name || ''}`"
    width="760px"
    centered
    :footer="null"
    wrap-class-name="part-create-modal"
    :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    @cancel="$emit('update:open', false)"
  >
    <!-- 约束说明：让"为什么只列出这些对象"一眼可见（硬约束在服务端，此处只是解释） -->
    <div class="dam-hint">
      <a-tag color="blue">{{ deliverable?.code || '-' }}</a-tag>
      <span style="color:#595959;font-size:13px">产出物类别约束：</span>
      <a-tag v-if="requiredType" color="purple">{{ requiredType }}</a-tag>
      <a-tag v-else-if="requiredHost" color="purple">{{ hostLabel(requiredHost) }}</a-tag>
      <span v-else style="color:#8c8c8c;font-size:13px">不限</span>
      <span class="dam-hint-sub">（来自模板 objectType / bindMode；类别不符会被服务端拒绝）</span>
    </div>

    <!-- 已关联 -->
    <div class="dam-section-title">已关联产出物（{{ links.length }}）</div>
    <a-empty v-if="!links.length" :image-style="{ height: '32px' }" description="尚未关联产出物" />
    <div v-else class="dam-list">
      <div v-for="l in links" :key="l.oid" class="dam-row">
        <a-tag size="small" :color="hostColor(l.entityType)">{{ hostLabel(l.entityType) }}</a-tag>
        <a-tag size="small">{{ l.typeCode }}</a-tag>
        <!-- 编码/名称可点开产出物自己的详情（用户口径 2026-10-29：从交付件要能一路点到产出物的详情）：
             零组件走零件详情页，文档/工程数据走统一的详情抽屉 -->
        <a v-if="detailable(l)" class="dam-code dam-link" :title="`查看「${l.entityName || l.entityCode}」详情`"
          @click="openDetail(l)">{{ l.entityCode }}</a>
        <code v-else class="dam-code">{{ l.entityCode }}</code>
        <a v-if="detailable(l)" class="dam-name dam-link" :title="`查看「${l.entityName || l.entityCode}」详情`"
          @click="openDetail(l)">{{ l.entityName }}</a>
        <span v-else class="dam-name" :title="l.entityName">{{ l.entityName }}</span>
        <span v-if="isSelfContainer(l)" class="dam-self">本型号</span>
        <span v-else class="dam-other" :title="l.containerOid">其它容器</span>
        <a-popconfirm
          v-if="!readonly"
          title="解除该关联？（不影响产出物本身）"
          ok-text="解除"
          cancel-text="取消"
          @confirm="removeLink(l)"
        >
          <a-button type="link" size="small" danger>解除</a-button>
        </a-popconfirm>
      </div>
    </div>

    <!-- 只读模式：只回显，不提供第二个写入口 -->
    <div v-if="readonly" class="dam-readonly-note">
      关联关系在「交付件」面板的产出物列维护；这里只读回显。
    </div>

    <template v-else>
      <!-- 添加 -->
      <div class="dam-section-title" style="margin-top:16px">添加产出物</div>
      <a-input-search
        v-model:value="keyword"
        :placeholder="searchPlaceholder"
        allow-clear
        :loading="candLoading"
        @search="loadCandidates"
        @change="onKeywordChange"
      />
      <!-- 候选区固定最小高度：避免「搜索中 / 无结果」时弹窗高度塌缩 —— 用户会以为"没搜出来"（2026-10-11） -->
      <div class="dam-cand-box">
        <div v-if="candLoading" class="dam-cand-status">
          <a-spin size="small" /><span>搜索中…</span>
        </div>
        <a-empty
          v-else-if="!candidates.length"
          :image-style="{ height: '32px' }"
          :description="emptyHint"
        />
        <div v-else class="dam-list dam-cand">
          <div v-for="c in candidates" :key="c.entityType + c.oid" class="dam-row">
            <a-tag size="small" :color="hostColor(c.entityType)">{{ hostLabel(c.entityType) }}</a-tag>
            <a-tag size="small">{{ c.typeCode }}</a-tag>
            <code class="dam-code">{{ c.code }}</code>
            <span class="dam-name" :title="c.name">{{ c.name }}</span>
            <span v-if="c.containerOid === ownerOid" class="dam-self">本型号</span>
            <span v-else class="dam-other" :title="c.containerOid">其它容器</span>
            <!-- 候选里不会出现"已关联（无论本交付件还是别人）"的对象，所以只有「关联」一种状态 -->
            <a-button
              type="link"
              size="small"
              :disabled="linkingKey === (c.entityType + '|' + c.oid)"
              :loading="linkingKey === (c.entityType + '|' + c.oid)"
              @click="link(c)"
            >关联</a-button>
          </div>
        </div>
      </div>
    </template>
  </a-modal>

  <!-- 产出物详情：与列表页/办理页同一个抽屉，避免两处信息口径不一致 -->
  <DocumentViewer v-model:visible="viewerVisible" :doc="viewerDoc" @close="viewerVisible = false" />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  getDeliverableArtifactLinks,
  getDeliverableArtifactCandidates,
  createDeliverableArtifactLink,
  deleteDeliverableArtifactLink,
  getDocument,
  getEngineeringDocument,
  getFolderDocumentDetails,
  getFolderEngineeringDocumentDetails,
  resolveEntityApiPath,
} from '@/api'
import DocumentViewer from '@/views/product/DocumentViewer.vue'

/**
 * 交付件产出物关联弹窗（2026-10-11）。
 *
 * 设计要点（与后端 DeliverableArtifactLinkController 一一对应）：
 * - 硬约束：候选由服务端按交付件的 objectType / bindMode 过滤；此处只把约束"翻译"给用户看，
 *   不做前端过滤（避免两边规则漂移 —— 唯一真源在服务端）。
 * - 跨容器：允许引用资源库 / 其它型号的对象，行尾标注"本型号 / 其它容器"。
 * - 单一入口：关联的写入只发生在本弹窗（交付件管理页打开），其它位置只读回显。
 */
const props = defineProps({
  open: { type: Boolean, default: false },
  /** 交付件行（含 oid / code / name / objectType / bindMode / ownerOid） */
  deliverable: { type: Object, default: null },
  /** 当前页面所属系列 / 型号 oid，用于标注"本型号" */
  ownerOid: { type: String, default: null },
  /**
   * 只读模式（2026-10-29）：只回显已关联产出物，不出现「解除」与「添加产出物」。
   *
   * <p>用在任务表单里看"这条交付项交了什么东西"—— 写入的入口仍然只有交付件管理页，
   * 别的地方一律只读，免得同一份关联有第二个写入口。
   */
  readonly: { type: Boolean, default: false },
})
const emit = defineEmits(['update:open', 'changed'])

const router = useRouter()

const links = ref([])
const linksLoading = ref(false)
const candidates = ref([])
const candLoading = ref(false)
const keyword = ref('')
const linkingKey = ref('')



/** 约束展示：objectType 优先（硬约束到具体类型），否则退到 bindMode 宿主级 */
const requiredType = computed(() => (props.deliverable?.objectType || '').trim() || null)
const requiredHost = computed(() => {
  if (requiredType.value) return null
  const bm = (props.deliverable?.bindMode || '').trim().toUpperCase()
  if (bm === 'DOCUMENT') return 'DOCUMENT'
  if (bm === 'PART_SET') return 'PART'
  return null
})

const searchPlaceholder = computed(() =>
  '搜索 编码 / 名称（跨容器可搜：资源库、其它型号的对象也能引用）'
)
const emptyHint = computed(() => {
  if (keyword.value) return '没有匹配的产出物（已关联到其它交付件的对象不在此列）'
  if (requiredType.value) return `暂无「${requiredType.value}」类型的对象（先在对应库里创建，再回来关联）`
  return '暂无可关联的产出物（已关联到其它交付件的对象不在此列）'
})

function hostLabel(t) {
  return { DOCUMENT: '文档', ENG_DOCUMENT: '工程数据', PART: '零组件' }[t] || t || '-'
}
function hostColor(t) {
  return { DOCUMENT: 'blue', ENG_DOCUMENT: 'cyan', PART: 'purple' }[t] || 'default'
}
function isSelfContainer(row) {
  return !!props.ownerOid && row.containerOid === props.ownerOid
}

async function loadLinks() {
  if (!props.deliverable?.oid) return
  linksLoading.value = true
  try {
    const res = await getDeliverableArtifactLinks(props.deliverable.oid)
    links.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    links.value = []
  } finally {
    linksLoading.value = false
  }
}

async function loadCandidates() {
  if (!props.deliverable?.oid) return
  const seq = ++candSeq
  candLoading.value = true
  try {
    const res = await getDeliverableArtifactCandidates(props.deliverable.oid, keyword.value || undefined)
    if (seq !== candSeq) return
    candidates.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    if (seq === candSeq) candidates.value = []
  } finally {
    if (seq === candSeq) candLoading.value = false
  }
}

let keywordTimer = null
/** 请求序号：输入较快时先发的请求可能后到，用序号保证只有最后一次生效（否则旧结果会盖掉新结果） */
let candSeq = 0
function onKeywordChange() {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => loadCandidates(), 250)
}

async function link(c) {
  const key = c.entityType + '|' + c.oid
  linkingKey.value = key
  try {
    const res = await createDeliverableArtifactLink({
      deliverableOid: props.deliverable.oid,
      entityType: c.entityType,
      entityOid: c.oid,
    })
    if (res?.code === 200) {
      message.success('已关联')
      await loadLinks()
      // 刚关联的对象已"名花有主"，候选里不会再出现 —— 立即重查，避免看起来还能再点一次
      await loadCandidates()
      emit('changed')
    } else {
      message.error(res?.message || '关联失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || e?.message || '关联失败')
  } finally {
    linkingKey.value = ''
  }
}

async function removeLink(l) {
  try {
    const res = await deleteDeliverableArtifactLink(l.oid)
    if (res?.code === 200) {
      message.success('已解除关联')
      await loadLinks()
      // 解除后该对象重新变回"可关联"，候选要跟着刷新
      await loadCandidates()
      emit('changed')
    } else {
      message.error(res?.message || '解除失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || e?.message || '解除失败')
  }
}

// ==================== 产出物详情（2026-10-29） ====================

const viewerVisible = ref(false)
const viewerDoc = ref(null)

/** 该产出物有没有可打开的详情：零组件 / 文档 / 工程数据，其余类型不画可点样式 */
function detailable(l) {
  const apiPath = resolveEntityApiPath(l?.typeCode || l?.entityType)
  return apiPath === 'parts' || apiPath === 'documents' || apiPath === 'eng-documents'
}

/** 列表接口可能把结果包在分页对象里 —— 两种形状都认 */
function pickList(res) {
  if (Array.isArray(res?.data)) return res.data
  if (Array.isArray(res?.data?.records)) return res.data.records
  return []
}

/**
 * 打开产出物详情。
 *
 * <p>零组件有独立详情页（同窗口跳转，返回用浏览器后退）；文档/工程数据走统一详情抽屉。
 * 抽屉要的是**迭代级**字段（displayVersion / statusName / ckfileOid / checkedOut），
 * 而 master 只有主数据 —— 所以再按 folderOid 回查一次 folder-details 的 VO 定位本对象，
 * 查不到才退化成 master（宁可少两行，也不给一个空壳）。
 */
async function openDetail(l) {
  const apiPath = resolveEntityApiPath(l?.typeCode || l?.entityType)
  if (apiPath === 'parts') {
    router.push(`/part/${l.entityOid}`)
    return
  }
  if (apiPath !== 'documents' && apiPath !== 'eng-documents') {
    message.info('该业务对象类型暂未提供详情页')
    return
  }
  try {
    const isDoc = apiPath === 'documents'
    const res = isDoc ? await getDocument(l.entityOid) : await getEngineeringDocument(l.entityOid)
    const master = res?.data || res
    if (!master || res?.code !== 200) {
      message.error(res?.message || '未找到该对象')
      return
    }
    let doc = master
    if (master.folderOid) {
      try {
        const detailsRes = isDoc
          ? await getFolderDocumentDetails(master.folderOid)
          : await getFolderEngineeringDocumentDetails(master.folderOid)
        doc = pickList(detailsRes).find((d) => d.oid === l.entityOid) || master
      } catch { /* 回查失败就用 master，不挡查看 */ }
    }
    viewerDoc.value = { ...doc, oid: l.entityOid }
    viewerVisible.value = true
  } catch (e) {
    message.error(e?.response?.data?.message || e?.message || '加载详情失败')
  }
}

// 打开时拉数据；切换交付件时重拉
watch(
  () => [props.open, props.deliverable?.oid],
  ([open]) => {
    if (!open) return
    keyword.value = ''
    loadLinks()
    if (!props.readonly) loadCandidates()
  }
)
</script>

<style scoped>
.dam-hint {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  padding: 8px 12px; background: #fafafa; border: 1px solid #f0f0f0; border-radius: 6px;
}
.dam-hint-sub { color: #bfbfbf; font-size: 12px; }
.dam-section-title { font-size: 13px; font-weight: 600; color: #1a1a2e; margin: 4px 0 8px; }
.dam-list { display: flex; flex-direction: column; gap: 6px; }
/* 候选区：固定最小高度 —— 否则「搜索中 / 无结果」时弹窗高度塌缩，用户会以为"没搜出来"（2026-10-11） */
.dam-cand-box { min-height: 150px; margin-top: 10px; display: flex; flex-direction: column; }
.dam-cand-box .ant-empty { margin: auto; }
.dam-cand-status { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 28px 0; color: #8c8c8c; font-size: 13px; }
/* 只读模式下的说明：解释为什么这里不能加/删 */
.dam-readonly-note {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed #f0f0f0;
  font-size: 12px;
  color: #8c8c8c;
}
.dam-cand { max-height: 300px; overflow-y: auto; }
.dam-row {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px; border: 1px solid #f0f0f0; border-radius: 6px; background: #fff;
}
.dam-code { font-size: 12px; color: #595959; }
.dam-name { flex: 1; min-width: 0; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dam-self { color: #52c41a; font-size: 12px; flex-shrink: 0; }
.dam-other { color: #fa8c16; font-size: 12px; flex-shrink: 0; }

/* 可点开的产出物：蓝字 + hover 下划线，与页面里其它"能点进详情"的文本一致 */
.dam-link {
  color: #1677ff;
  cursor: pointer;
  border-bottom: 1px dashed transparent;
}
.dam-link:hover { border-bottom-color: #1677ff; }
</style>
