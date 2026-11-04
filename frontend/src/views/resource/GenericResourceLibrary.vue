<template>
  <div class="grl-page">
    <div class="grl-body">
      <!-- 左侧：文件夹树（参照产品系列/型号阶段页；与封装·图符库同一套树交互） -->
      <div class="grl-tree">
        <div class="grl-tree-header">
          <FolderOutlined style="color:#faad14" />
          <span>文件夹</span>
        </div>
        <div class="grl-tree-scroll">
          <a-empty
            v-if="!folderTree.length"
            :image="aEmptyImage.PRESENTED_IMAGE_SIMPLE"
            description="暂无文件夹"
          />
          <FolderTreeNode
            v-for="node in folderTree"
            :key="node.oid"
            :node="node"
            :selected-folder-oid="selectedFolder?.oid"
            :expanded-folders="expandedFolders"
            @select="selectFolder"
            @toggle="toggleFolder"
          />
          <a-button
            type="dashed"
            size="small"
            block
            style="margin-top:8px"
            :disabled="!stageOid"
            @click="openCreateFolder(null)"
          >
            <PlusOutlined /> 新建文件夹
          </a-button>
        </div>
      </div>

      <!-- 右侧：当前文件夹下的业务对象（文档 / 工程数据 / 零组件 / 功能系统） -->
      <div class="grl-content">
        <DataTable
          scroll-x="max-content"
          :columns="columns"
          :data-source="displayItems"
          :loading="loading"
          :pagination="{ pageSize: 15, showSizeChanger: true }"
          row-key="key"
          size="small"
          searchable
          search-placeholder="搜索编码/名称..."
          :search-fields="['code', 'name']"
        >
          <template #toolbar-left>
            <div class="grl-bar-inline">
              <FolderOutlined style="color:#faad14;font-size:14px" />
              <span style="color:#999;font-size:12px;margin-right:4px">当前位置：</span>
              <strong style="font-size:13px">{{ currentFolderPath }}</strong>
              <a-select
                v-model:value="typeFilter"
                size="small"
                style="width:130px;margin-left:12px"
                :options="typeFilterOptions"
              />
            </div>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'objectType'">
              <a-tag :color="record.typeColor" size="small">{{ record.typeLabel }}</a-tag>
            </template>
            <template v-else-if="column.key === 'code'">
              <router-link
                v-if="record.route"
                class="grl-link"
                :to="record.route"
                :title="`查看 ${record.name || ''} 详情`"
              >{{ record.code || '-' }}</router-link>
              <!-- 文档/工程数据没有独立详情页：复用列表页同一个 DocumentViewer 抽屉 -->
              <a
                v-else-if="isDocRow(record)"
                class="grl-link"
                :title="`查看 ${record.name || ''} 详情`"
                @click="openDocDetail(record)"
              >{{ record.code || '-' }}</a>
              <span v-else>{{ record.code || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag v-if="record.statusName || record.statusCode" color="blue" size="small">
                {{ record.statusName || record.statusCode }}
              </a-tag>
              <span v-else style="color:#bfbfbf">-</span>
            </template>
          </template>
        </DataTable>
      </div>
    </div>

    <!-- 新建文件夹 -->
    <a-modal
      v-model:open="createFolderVisible"
      :title="creatingParentName ? `在「${creatingParentName}」下新建文件夹` : '新建文件夹'"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="createFolderSaving"
      @ok="confirmCreateFolder"
    >
      <a-input
        v-model:value="createFolderName"
        placeholder="文件夹名称"
        :maxlength="100"
        @press-enter="confirmCreateFolder"
      />
    </a-modal>

    <!-- 文档/工程数据详情：与产品线列表、零件详情共用同一个抽屉，信息口径一致 -->
    <DocumentViewer v-model:visible="docViewerVisible" :doc="docViewerDoc" @close="docViewerVisible = false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message, Empty } from 'ant-design-vue'
import { FolderOutlined, PlusOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import FolderTreeNode from '@/views/product/FolderTreeNode.vue'
import DocumentViewer from '@/views/product/DocumentViewer.vue'
import {
  createFolder,
  getFolderTree,
  getFolderDocumentDetails,
  getFolderEngineeringDocumentDetails,
  getPartsByFolder,
  getFunctionalsByFolder,
  getStages,
} from '@/api'

/**
 * 通用资源库视图 —— 给**没有专属页面**的资源子库（如「其他」）用：
 * 左侧 = 本库的文件夹树，右侧 = 当前文件夹下的**业务对象**（文档 / 工程数据 / 零组件 / 功能系统）。
 *
 * <p>与产品系列/型号的阶段页同一套交互（树 + 列表、树底常驻「新建文件夹」），
 * 与封装·图符库共用 `FolderTreeNode` 与 `/folders/*` 接口 —— 资源库的文件夹本来就同源。
 *
 * <h3>为什么按"归属阶段"取树</h3>
 * `/folders/tree` 要求 ownerOid + stageOid：资源子库的文件夹挂在它自己的**归属阶段**下
 * （与 Part.stage_oid 同源，见 ResourceLibraryInstaller）。阶段由包播种（platform-base 的
 * resourceLibraries.json），本组件按 `getStages(lib.oid)` 取第一个。
 *
 * <h3>右侧为什么要四类合并</h3>
 * 该库没有"存储类别"限制 —— 文件夹里可以放文档、工程数据、零组件、功能系统。
 * 四个"按文件夹取对象"的接口各取一份，前端归一化成一个列表（类型列区分），
 * 而不是逼用户先想"我要看哪类"。
 */
const props = defineProps({
  /** 资源子库（ck_resource_container 节点）：{ oid, code, name } */
  lib: { type: Object, required: true },
})

const aEmptyImage = Empty

// ==================== 归属阶段 / 文件夹树 ====================

const stageOid = ref('')
const folderTree = ref([])
const selectedFolder = ref(null)
const expandedFolders = ref({})

async function loadStage() {
  try {
    const res = await getStages(props.lib.oid)
    const rows = res?.code === 200 ? (res.data || []) : []
    stageOid.value = rows[0]?.oid || ''
  } catch {
    stageOid.value = ''
  }
}

async function loadFolderTree() {
  if (!stageOid.value) {
    folderTree.value = []
    return
  }
  try {
    const res = await getFolderTree(props.lib.oid, stageOid.value)
    folderTree.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    folderTree.value = []
  }
}

function findNode(nodes, oid) {
  for (const n of nodes || []) {
    if (n.oid === oid) return n
    const hit = findNode(n.children, oid)
    if (hit) return hit
  }
  return null
}

/** 选中文件夹（兼容传 node 或 oid） */
function selectFolder(nodeOrOid) {
  const node = typeof nodeOrOid === 'string' ? findNode(folderTree.value, nodeOrOid) : nodeOrOid
  selectedFolder.value = node || null
  loadItems()
}

function toggleFolder(node) {
  const oid = typeof node === 'string' ? node : node?.oid
  if (!oid) return
  expandedFolders.value = { ...expandedFolders.value, [oid]: !expandedFolders.value[oid] }
}

const currentFolderPath = computed(() => selectedFolder.value?.name || '未选择文件夹')

// ==================== 右侧：业务对象列表 ====================

const items = ref([])
const loading = ref(false)
const typeFilter = ref('ALL')

const typeFilterOptions = [
  { value: 'ALL', label: '全部类型' },
  { value: 'DOCUMENT', label: '文档' },
  { value: 'ENG_DOCUMENT', label: '工程数据' },
  { value: 'PART', label: '零组件' },
  { value: 'FUNCTIONAL', label: '功能系统' },
]

const columns = [
  { title: '类型', key: 'objectType', width: 100 },
  { title: '编码', key: 'code', width: 200 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '状态', key: 'status', width: 110 },
  { title: '更新时间', key: 'updatedAt', width: 160 },
]

const displayItems = computed(() =>
  typeFilter.value === 'ALL' ? items.value : items.value.filter((r) => r.objectType === typeFilter.value)
)

/** 四类接口的字段名不统一（number / code / documentNumber…）—— 归一化，缺字段不编造。
 *  raw 保留接口原始行：文档详情抽屉要迭代级字段（displayVersion/statusName/ckfileOid），
 *  这些在归一化后的扁平行里丢掉了，回查一次 master 又拿不全。 */
function normalize(row, type, label, color, route) {
  const oid = row?.oid || row?.masterOid || ''
  return {
    key: `${type}:${oid}`,
    oid,
    raw: row,
    objectType: type,
    typeLabel: label,
    typeColor: color,
    route: route && oid ? route(oid) : null,
    code: row?.number || row?.code || row?.documentNumber || row?.engNumber || row?.itemNumber || '',
    name: row?.name || row?.title || row?.displayName || '',
    statusName: row?.statusName || row?.status?.displayName || (typeof row?.status === 'string' ? row.status : ''),
    statusCode: row?.statusCode || row?.status?.code || '',
    updatedAt: (row?.updatedAt || row?.modifiedAt || row?.createdAt || '').toString().replace('T', ' ').slice(0, 16),
  }
}

async function loadItems() {
  const folderOid = selectedFolder.value?.oid
  if (!folderOid) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const [docs, engs, parts, funcs] = await Promise.all([
      getFolderDocumentDetails(folderOid).catch(() => null),
      getFolderEngineeringDocumentDetails(folderOid).catch(() => null),
      getPartsByFolder(folderOid).catch(() => null),
      getFunctionalsByFolder(folderOid).catch(() => null),
    ])
    const pick = (res) => (res?.code === 200 && Array.isArray(res.data) ? res.data : [])
    items.value = [
      ...pick(docs).map((d) => normalize(d, 'DOCUMENT', '文档', 'blue')),
      ...pick(engs).map((d) => normalize(d, 'ENG_DOCUMENT', '工程数据', 'geekblue')),
      ...pick(parts).map((d) => normalize(d, 'PART', '零组件', 'green', (oid) => `/part/${oid}`)),
      ...pick(funcs).map((d) => normalize(d, 'FUNCTIONAL', '功能系统', 'purple')),
    ]
  } finally {
    loading.value = false
  }
}

// ==================== 文档详情（复用产品线的 DocumentViewer 抽屉） ====================

const docViewerVisible = ref(false)
const docViewerDoc = ref(null)

/** 文档 / 工程数据没有独立详情页路由，点编码走抽屉 */
function isDocRow(record) {
  return record?.objectType === 'DOCUMENT' || record?.objectType === 'ENG_DOCUMENT'
}

/** 打开文档详情：直接用 folder-details 返回的迭代级 VO（DocumentVO 已含
 *  displayVersion / statusName / ckfileOid / checkedOut），无需再回查 master */
function openDocDetail(record) {
  if (!record?.raw?.oid) return
  // oid 以归一化结果为准（VO 缺 oid 时会用 masterOid 兜底），抽屉里的 CAD 可视化按它取数据
  docViewerDoc.value = { ...record.raw, oid: record.oid }
  docViewerVisible.value = true
}

// ==================== 新建文件夹 ====================

const createFolderVisible = ref(false)
const createFolderName = ref('')
const createFolderSaving = ref(false)
const creatingParentOid = ref(null)
const creatingParentName = ref('')

function openCreateFolder(nodeOrOid) {
  const oid = typeof nodeOrOid === 'string' ? nodeOrOid : nodeOrOid?.oid || null
  creatingParentOid.value = oid
  creatingParentName.value = oid ? (findNode(folderTree.value, oid)?.name || '') : ''
  createFolderName.value = ''
  createFolderVisible.value = true
}

async function confirmCreateFolder() {
  const name = createFolderName.value?.trim()
  if (!name) {
    message.warning('请输入文件夹名称')
    return
  }
  if (!stageOid.value) {
    message.warning('本库的归属阶段未就绪（请先安装/升级 platform-base 包），暂不能建文件夹')
    return
  }
  createFolderSaving.value = true
  try {
    const res = await createFolder({
      ownerOid: props.lib.oid,
      stageOid: stageOid.value,
      parentOid: creatingParentOid.value || undefined,
      name,
    })
    if (res?.code === 200) {
      message.success('文件夹已创建')
      createFolderVisible.value = false
      await loadFolderTree()
      if (creatingParentOid.value) {
        expandedFolders.value = { ...expandedFolders.value, [creatingParentOid.value]: true }
      }
      // 新文件夹自动选中：建完就是要往里放东西的
      const created = findNode(folderTree.value, res.data?.oid)
      if (created) {
        selectFolder(created)
      } else if (!selectedFolder.value && folderTree.value.length) {
        selectFolder(folderTree.value[0])
      }
    } else {
      message.error(res?.message || '创建失败')
    }
  } catch {
    message.error('创建失败，请稍后重试')
  } finally {
    createFolderSaving.value = false
  }
}

onMounted(async () => {
  await loadStage()
  await loadFolderTree()
  if (folderTree.value.length) {
    selectFolder(folderTree.value[0])
  }
})
</script>

<style scoped>
.grl-page { height: 100%; }
.grl-body { display: flex; gap: 12px; align-items: stretch; min-height: 480px; }

.grl-tree {
  width: 260px;
  flex: 0 0 260px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 8px;
  display: flex;
  flex-direction: column;
}
.grl-tree-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 4px 8px;
  font-weight: 600;
  color: #333;
  border-bottom: 1px solid #f5f5f5;
  margin-bottom: 8px;
}
.grl-tree-scroll { flex: 1; overflow: auto; }

.grl-content { flex: 1; min-width: 0; }

.grl-bar-inline { display: flex; align-items: center; }
.grl-link { color: #1677ff; cursor: pointer; }
</style>
