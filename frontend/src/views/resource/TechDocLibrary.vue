<template>
  <div class="tdl-page">
    <div class="tdl-body">
      <!-- 左侧：文件夹树 -->
      <div class="tdl-tree">
        <div class="tdl-tree-header">
          <FolderOutlined style="color:#faad14" />
          <span>文件夹</span>
        </div>
        <div class="tdl-tree-scroll">
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
            @click="openCreateFolder(null)"
          >
            <PlusOutlined /> 新建文件夹
          </a-button>
        </div>
      </div>

      <!-- 右侧：技术文档清单 -->
      <div class="tdl-content">
        <DataTable
          scroll-x="max-content"
          :columns="itemColumns"
          :data-source="itemList"
          :loading="loading"
          :pagination="{ pageSize: 15, showSizeChanger: true }"
          row-key="oid"
          size="small"
          search-placeholder="搜索编码/名称/文件名..."
          :search-fields="['number', 'name', 'fileName']"
        >
          <template #toolbar-left>
            <div class="tdl-bar-inline">
              <FolderOutlined style="color:#faad14;font-size:14px" />
              <span style="color:#999;font-size:12px;margin-right:4px">当前位置：</span>
              <strong style="font-size:13px">{{ currentFolderPath }}</strong>
            </div>
          </template>
          <template #toolbar>
            <a-button type="primary" size="small" :disabled="!ctx?.containerOid" @click="openCreateModal">
              <PlusOutlined /> 新建
            </a-button>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'number'">
              <span class="tdl-code">{{ record.number || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'file'">
              <template v-if="record.fileName">
                <a class="tdl-link" :title="record.fileName" @click="downloadItem(record)">
                  {{ record.fileName }}
                </a>
                <div class="tdl-sub">{{ formatSize(record.fileSize) }}</div>
              </template>
              <span v-else class="tdl-sub">未上传主文档</span>
            </template>
            <template v-else-if="column.key === 'displayVersion'">
              <a-tag color="blue" size="small">{{ record.displayVersion || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.statusCode)" size="small">
                {{ record.statusName || record.statusCode || '-' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'checkout'">
              <a-tag v-if="record.checkedOut" color="orange" size="small">已检出: {{ record.checkedOutBy || '-' }}</a-tag>
              <a-tag v-else color="green" size="small">已检入</a-tag>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              <span class="tdl-sub">{{ record.createdAt || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a-button type="link" size="small" :disabled="!record.ckfileOid" @click="downloadItem(record)">
                  下载
                </a-button>
                <a-popconfirm
                  title="确定删除该文档？其迭代与主文件将一并删除"
                  @confirm="removeItem(record)"
                >
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </DataTable>
      </div>
    </div>

    <!--
      新建技术文档：业务对象是通用文档 Document（ck_document），
      与封装·图符库（EPMDocument / ck_eng_document）不是同一类对象 —— 表单也走各自的类型定义。
      主文档字段（ckfile-upload）就是知识本体（PDF / Word / Excel ...）。
    -->
    <a-modal
      v-model:visible="createModalVisible"
      title="新建技术文档"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="createSaving"
      width="640px"
      centered
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
      @ok="confirmCreate"
    >
      <div v-if="!ctx?.containerOid" class="tdl-create-warn">
        技术文档知识库容器尚未初始化，请联系平台管理员检查后端初始化日志。
      </div>
      <a-alert
        type="info"
        show-icon
        banner
        style="margin-bottom:12px"
        message="技术文档用于沉淀设计规范、技术方案、手册等知识；主文档支持 PDF / Word / Excel / 图片等格式"
      />
      <DynamicForm
        v-if="createModalVisible && ctx?.containerOid"
        ref="dynamicFormRef"
        entity-code="DOCUMENT"
        operation-code="create"
        :current-container-oid="ctx.containerOid"
        :current-stage-oid="ctx.stageOid"
        v-model="form"
      />
    </a-modal>

    <!-- 新建文件夹 -->
    <a-modal
      v-model:visible="createFolderVisible"
      title="新建文件夹"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="createFolderSaving"
      @ok="confirmCreateFolder"
      width="420px"
    >
      <a-form layout="vertical">
        <a-form-item v-if="creatingParentName" label="上级文件夹">
          <a-tag>{{ creatingParentName }}</a-tag>
        </a-form-item>
        <a-form-item label="文件夹名称" required>
          <a-input
            v-model:value="createFolderName"
            placeholder="请输入名称"
            @pressEnter="confirmCreateFolder"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message, Empty } from 'ant-design-vue'
import { FolderOutlined, PlusOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import FolderTreeNode from '@/views/product/FolderTreeNode.vue'
import {
  getFolderTree,
  createFolder,
  createSoftTypeInstance,
  deleteDocument,
  getDocumentDownloadUrl,
  getTechDocContext,
  getTechDocs
} from '@/api'

const aEmptyImage = Empty

// ==================== 子库上下文 / 文件夹树 ====================
const ctx = ref(null)
const folderTree = ref([])
const selectedFolder = ref(null)
const expandedFolders = ref({})

const itemList = ref([])
const loading = ref(false)

const itemColumns = [
  { title: '编码', key: 'number', width: 150 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '主文档', key: 'file', width: 220, ellipsis: true },
  { title: '版本', key: 'displayVersion', width: 70, align: 'center' },
  { title: '生命周期', key: 'status', width: 100 },
  { title: '检出', key: 'checkout', width: 130 },
  { title: '创建时间', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 120, align: 'center' }
]

function statusColor(code) {
  const map = {
    DRAFT: 'default', INWORK: 'processing', REVIEW: 'warning',
    APPROVED: 'success', RELEASED: 'blue', OBSOLETE: 'error',
    WORKING: 'processing', APPROVING: 'warning', PUBLISHED: 'success',
    OFFLINE: 'default', ARCHIVED: 'error'
  }
  return map[code] || 'default'
}

function fmtTime(str) {
  if (!str) return '-'
  return String(str).replace('T', ' ').substring(0, 19)
}

function formatSize(bytes) {
  if (bytes == null) return ''
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

const currentFolderPath = computed(() => {
  if (!selectedFolder.value) return '全部'
  return selectedFolder.value.name || '全部'
})

async function loadContext() {
  try {
    const res = await getTechDocContext()
    ctx.value = res?.code === 200 ? (res.data || null) : null
  } catch {
    ctx.value = null
  }
}

async function loadFolderTree() {
  const c = ctx.value
  if (!c?.containerOid || !c?.stageOid) {
    folderTree.value = []
    return
  }
  try {
    const res = await getFolderTree(c.containerOid, c.stageOid)
    folderTree.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    folderTree.value = []
  }
}

async function loadItems() {
  loading.value = true
  try {
    const res = await getTechDocs({
      folderOid: selectedFolder.value?.oid || undefined
    })
    if (res?.code === 200) {
      const data = res.data || {}
      itemList.value = (data.items || []).map(i => ({
        ...i,
        createdAt: fmtTime(i.createdAt)
      }))
      if (data.context) ctx.value = data.context
    } else {
      itemList.value = []
    }
  } catch {
    itemList.value = []
  } finally {
    loading.value = false
  }
}

function selectFolder(node) {
  selectedFolder.value = node
  loadItems()
}

function toggleFolder(node) {
  expandedFolders.value = {
    ...expandedFolders.value,
    [node.oid]: expandedFolders.value[node.oid] === false
  }
}

// ==================== 行操作 ====================
function downloadItem(record) {
  if (!record?.ckfileOid) {
    message.warning('该文档还没有上传主文档')
    return
  }
  window.open(getDocumentDownloadUrl(record.ckfileOid), '_blank')
}

async function removeItem(record) {
  try {
    const res = await deleteDocument(record.oid)
    if (res?.code === 200) {
      message.success('已删除')
      await loadItems()
    } else {
      message.error(res?.message || '删除失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '删除失败')
  }
}

// ==================== 新建文件夹 ====================
const createFolderVisible = ref(false)
const createFolderName = ref('')
const createFolderSaving = ref(false)
const creatingParentOid = ref(null)
const creatingParentName = ref('')

function openCreateFolder(parentOid) {
  creatingParentOid.value = parentOid || null
  creatingParentName.value = ''
  if (parentOid) {
    const findName = (nodes) => {
      for (const n of (nodes || [])) {
        if (n.oid === parentOid) return n.name
        if (n.children?.length) {
          const r = findName(n.children)
          if (r) return r
        }
      }
      return ''
    }
    creatingParentName.value = findName(folderTree.value)
  }
  createFolderName.value = ''
  createFolderVisible.value = true
}

async function confirmCreateFolder() {
  const c = ctx.value
  if (!c?.containerOid || !c?.stageOid) {
    message.warning('技术文档知识库容器未就绪，无法创建文件夹')
    return
  }
  const name = createFolderName.value?.trim()
  if (!name) {
    message.warning('请输入文件夹名称')
    return
  }
  createFolderSaving.value = true
  try {
    const res = await createFolder({
      ownerOid: c.containerOid,
      stageOid: c.stageOid,
      parentOid: creatingParentOid.value,
      name
    })
    if (res?.code === 200) {
      message.success('文件夹已创建')
      createFolderVisible.value = false
      await loadFolderTree()
      if (creatingParentOid.value) {
        expandedFolders.value = {
          ...expandedFolders.value,
          [creatingParentOid.value]: true
        }
      }
    } else {
      message.error(res?.message || '创建失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '创建失败')
  } finally {
    createFolderSaving.value = false
  }
}

// ==================== 新建技术文档 ====================
const createModalVisible = ref(false)
const createSaving = ref(false)
const dynamicFormRef = ref(null)
const form = ref({})

function openCreateModal() {
  if (!ctx.value?.containerOid) {
    message.warning('技术文档知识库容器未就绪，请检查后端初始化')
    return
  }
  // 默认落到左侧当前选中的文件夹（未选中时留空，由用户在表单里选）
  form.value = {
    typeDefinitionCode: 'DOCUMENT',
    folderOid: selectedFolder.value?.oid || null
  }
  createModalVisible.value = true
}

async function confirmCreate() {
  const c = ctx.value
  if (!c?.containerOid) {
    message.warning('技术文档知识库容器未就绪，请检查后端初始化')
    return
  }
  if (!form.value?.name?.trim()) {
    message.warning('请输入文档名称')
    return
  }
  createSaving.value = true
  try {
    const payload = { ...(form.value || {}) }
    // 分类 IBA 值从顶层剔除、单独放 clsIbaValues —— 否则会被当成实体 IBA 写进 ck_type_iba_data
    if (dynamicFormRef.value) {
      const clsIbaFieldNames = dynamicFormRef.value.getClsIbaFieldNames() || []
      clsIbaFieldNames.forEach(k => delete payload[k])
      const clsIbaValues = dynamicFormRef.value.getClsIbaValues() || {}
      if (Object.keys(clsIbaValues).length > 0) payload.clsIbaValues = clsIbaValues
    }
    payload.typeDefinitionCode = 'DOCUMENT'
    payload.containerOid = c.containerOid
    payload.containerType = c.containerType || 'CORP_RESOURCE'
    payload.stageOid = c.stageOid
    payload.folderOid = form.value?.folderOid || selectedFolder.value?.oid || null
    payload.name = form.value.name.trim()

    // 统一创建入口：后端按 root_type_code 路由到 DocumentServiceImpl（落 ck_document），
    // 与列表查询（/tech-docs 读 ck_document）保持一致
    const res = await createSoftTypeInstance(payload)
    if (res?.code === 200) {
      message.success(`已创建：${res.data?.number || res.data?.name || ''}`)
      createModalVisible.value = false
      await loadItems()
    } else {
      message.error(res?.message || '创建失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '创建失败')
  } finally {
    createSaving.value = false
  }
}

onMounted(async () => {
  await loadContext()
  await loadFolderTree()
  await loadItems()
})
</script>

<style scoped>
.tdl-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 两栏布局，与封装·图符库/阶段页面一致 */
.tdl-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 16px;
}

.tdl-tree {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
  overflow: hidden;
}
.tdl-tree-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #1a1a2e;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}
.tdl-tree-scroll {
  flex: 1;
  overflow: auto;
  padding: 6px 8px;
}

.tdl-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.tdl-bar-inline {
  display: flex;
  align-items: center;
}

/* 单元格里的「主值 + 灰色副值」两层 */
.tdl-code {
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #595959;
}
.tdl-sub {
  color: #999;
  font-size: 12px;
  line-height: 1.4;
}
.tdl-link {
  color: #1677ff;
  cursor: pointer;
}
.tdl-link:hover { text-decoration: underline; }

.tdl-create-warn {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fff7e6;
  border: 1px solid #ffe0a3;
  border-radius: 4px;
  color: #ad6800;
  font-size: 13px;
}
</style>
