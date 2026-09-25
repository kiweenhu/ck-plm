<template>
  <div class="psl-page">
    <div class="psl-body">
      <!-- 左侧：文件夹树（参照阶段页面） -->
      <div class="psl-tree">
        <div class="psl-tree-header">
          <FolderOutlined style="color:#faad14" />
          <span>文件夹</span>
        </div>
        <div class="psl-tree-scroll">
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
          <!-- 新建文件夹按钮（参照阶段页面：树底部常驻 dashed 按钮） -->
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

      <!-- 右侧：封装 / 图符清单 -->
      <div class="psl-content">
        <DataTable
          :columns="itemColumns"
          :data-source="itemList"
          :loading="loading"
          :pagination="{ pageSize: 15, showSizeChanger: true }"
          row-key="oid"
          size="small"
          searchable
          search-placeholder="搜索编码/名称..."
          :search-fields="['number', 'name']"
        >
          <template #toolbar-left>
            <div class="psl-bar-inline">
              <FolderOutlined style="color:#faad14;font-size:14px" />
              <span style="color:#999;font-size:12px;margin-right:4px">当前位置：</span>
              <strong style="font-size:13px">{{ currentFolderPath }}</strong>
              <a-select
                v-model:value="typeFilter"
                size="small"
                style="width:120px;margin-left:12px"
                :options="typeFilterOptions"
              />
            </div>
          </template>
          <template #toolbar>
            <a-button type="primary" size="small" :disabled="!ctx?.containerOid" @click="openCreateModal">
              <PlusOutlined /> 新建
            </a-button>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'typeDefinitionCode'">
              <a-tag :color="record.typeDefinitionCode === 'FOOTPRINT' ? 'blue' : 'green'" size="small">
                {{ record.typeDefinitionCode === 'FOOTPRINT' ? '封装' : '图符' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'number'">
              <router-link
                class="psl-link"
                :to="`/part/${record.oid}`"
                :title="`查看 ${record.name || ''} 详情`"
              >{{ record.number || '-' }}</router-link>
            </template>
            <template v-else-if="column.key === 'spec'">
              <span v-if="record.typeDefinitionCode === 'FOOTPRINT'">
                {{ record.ipcName || '-' }}　·　{{ record.padCount ?? '-' }} 焊盘　·　{{ record.mountType || '-' }}
              </span>
              <span v-else>
                {{ record.pinCount ?? '-' }} 引脚　·　{{ record.symbolCategory || '-' }}
              </span>
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
            <template v-else-if="column.key === 'action'">
              <PartRowActionMenu
                :record="record"
                :show-download="false"
                move-mode="folder"
                @success="loadItems"
              />
            </template>
          </template>
        </DataTable>
      </div>
    </div>

    <!-- 新建弹窗：先选类型（封装/图符），再渲染对应动态表单 -->
    <a-modal
      v-model:visible="createModalVisible"
      :title="createType === 'FOOTPRINT' ? '新建封装' : '新建图符'"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="createSaving"
      width="640px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
      @ok="confirmCreate"
    >
      <div v-if="!ctx?.containerOid" class="psl-create-warn">
        封装·图符库容器尚未初始化，请联系平台管理员检查后端初始化日志。
      </div>

      <div class="psl-create-type">
        <span class="psl-create-type-label">类型</span>
        <a-select
          v-model:value="createType"
          size="small"
          style="width:160px"
          :options="[
            { value: 'FOOTPRINT', label: '封装' },
            { value: 'SYMBOL', label: '图符' },
          ]"
        />
        <span class="psl-create-type-hint">编码留空时按编码规则自动生成</span>
      </div>

      <DynamicForm
        v-if="createModalVisible && ctx?.containerOid"
        ref="dynamicFormRef"
        :key="createType"
        :entity-code="createType"
        operation-code="create"
        fallback-entity-code="PART"
        :current-container-oid="ctx.containerOid"
        v-model="form"
      />
    </a-modal>

    <!-- 新建文件夹弹窗 -->
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
import { ref, computed, reactive, onMounted } from 'vue'
import { message, Empty } from 'ant-design-vue'
import { FolderOutlined, PlusOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import PartRowActionMenu from '@/components/PartRowActionMenu.vue'
import FolderTreeNode from '@/views/product/FolderTreeNode.vue'
import {
  getFolderTree, createSoftTypeInstance, getPackageSymbols, getPackageSymbolContext,
  createFolder,
} from '@/api'

const aEmptyImage = Empty

// ==================== 归属上下文 / 文件夹树 ====================
const ctx = ref(null)
const folderTree = ref([])
const selectedFolder = ref(null)
const expandedFolders = ref({})

const typeFilter = ref('ALL')
const typeFilterOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'FOOTPRINT', label: '封装' },
  { value: 'SYMBOL', label: '图符' },
]

const itemList = ref([])
const loading = ref(false)

const itemColumns = [
  { title: '类型', key: 'typeDefinitionCode', width: 70, align: 'center' },
  { title: '编码', key: 'number', width: 170 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '规格', key: 'spec', width: 240, ellipsis: true },
  { title: '版本', key: 'displayVersion', width: 70, align: 'center' },
  { title: '生命周期', key: 'status', width: 90 },
  { title: '检出', key: 'checkout', width: 110 },
  { title: '操作', key: 'action', width: 110, align: 'center' },
]

function statusColor(code) {
  const map = {
    DRAFT: 'default', INWORK: 'processing', REVIEW: 'warning',
    APPROVED: 'success', RELEASED: 'blue', OBSOLETE: 'error',
    WORKING: 'processing', APPROVING: 'warning', PUBLISHED: 'success',
    OFFLINE: 'default', ARCHIVED: 'error',
  }
  return map[code] || 'default'
}

function fmtTime(str) {
  if (!str) return '-'
  return String(str).replace('T', ' ').substring(0, 19)
}

/** 当前文件夹路径（根 → 当前） */
const currentFolderPath = computed(() => {
  if (!selectedFolder.value) return '全部'
  return selectedFolder.value.name || '全部'
})

async function loadContext() {
  try {
    const res = await getPackageSymbolContext()
    ctx.value = res?.code === 200 ? (res.data || null) : null
  } catch {
    ctx.value = null
  }
}

async function loadFolderTree() {
  const c = ctx.value
  if (!c?.containerOid || !c?.stageOid) { folderTree.value = []; return }
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
    const res = await getPackageSymbols({
      folderOid: selectedFolder.value?.oid || undefined,
      typeCode: typeFilter.value === 'ALL' ? undefined : typeFilter.value,
    })
    if (res?.code === 200) {
      const data = res.data || {}
      itemList.value = (data.items || []).map(i => ({
        ...i,
        createdAt: fmtTime(i.createdAt || i.created_at),
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
    [node.oid]: expandedFolders.value[node.oid] === false,
  }
}

// ==================== 新建文件夹 ====================
const createFolderVisible = ref(false)
const createFolderName = ref('')
const createFolderSaving = ref(false)
const creatingParentOid = ref(null)
const creatingParentName = ref('')

/**
 * 打开新建文件夹弹窗。
 * @param parentOid 父文件夹 oid（null=顶级，与阶段页面 openCreateFolder 一致）
 */
function openCreateFolder(parentOid) {
  creatingParentOid.value = parentOid || null
  creatingParentName.value = ''
  if (parentOid) {
    // 递归查找父文件夹名（用于弹窗展示上级）
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
    message.warning('封装·图符库容器未就绪，无法创建文件夹')
    return
  }
  const name = createFolderName.value?.trim()
  if (!name) { message.warning('请输入文件夹名称'); return }
  createFolderSaving.value = true
  try {
    const res = await createFolder({
      ownerOid: c.containerOid,
      stageOid: c.stageOid,
      parentOid: creatingParentOid.value,
      name,
    })
    if (res?.code === 200) {
      message.success('文件夹已创建')
      createFolderVisible.value = false
      await loadFolderTree()
      // 自动展开父级（如有）
      if (creatingParentOid.value) {
        expandedFolders.value = {
          ...expandedFolders.value,
          [creatingParentOid.value]: true,
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

// ==================== 新建（封装/图符）===================
const createModalVisible = ref(false)
const createSaving = ref(false)
const createType = ref('FOOTPRINT')
const dynamicFormRef = ref(null)
const form = ref({})

function openCreateModal() {
  if (!ctx.value?.containerOid) {
    message.warning('封装·图符库容器未就绪，请检查后端初始化')
    return
  }
  // 默认落到左侧当前选中的文件夹，保持与页面上下文一致（未选中任何文件夹时留空，由用户在表单中选择）
  form.value = {
    typeDefinitionCode: createType.value,
    folderOid: selectedFolder.value?.oid || null,
  }
  createModalVisible.value = true
}

async function confirmCreate() {
  const c = ctx.value
  if (!c?.containerOid) {
    message.warning('封装·图符库容器未就绪，请检查后端初始化')
    return
  }
  createSaving.value = true
  try {
    const payload = { ...(form.value || {}) }
    payload.typeDefinitionCode = createType.value
    payload.containerOid = c.containerOid
    payload.containerType = c.containerType || 'CORP_RESOURCE'
    payload.stageOid = c.stageOid
    // 以表单字段为准（允许用户改成别的文件夹），未填写时回退到左侧当前选中文件夹
    payload.folderOid = form.value?.folderOid || selectedFolder.value?.oid || null
    if (!payload.name?.trim()) {
      message.warning('请输入名称')
      return
    }
    payload.name = payload.name.trim()

    // 走软类型统一入口：由后端按 root_type_code 路由到 ENG_DOCUMENT 宿主（ck_eng_document），
    // 与列表查询（/package-symbols 读 ck_eng_document）保持一致
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
.psl-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 两栏布局（参照阶段页面 / 元器件库） ===== */
.psl-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 16px;
}

/* ===== 左侧文件夹树 ===== */
.psl-tree {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
  overflow: hidden;
}
.psl-tree-header {
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
.psl-tree-scroll {
  flex: 1;
  overflow: auto;
  padding: 6px 8px;
}

/* ===== 右侧清单 ===== */
.psl-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.psl-bar-inline {
  display: flex;
  align-items: center;
}
.psl-link {
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #1677ff;
  text-decoration: none;
}
.psl-link:hover {
  background: #e6f4ff;
  text-decoration: underline;
}

/* ===== 新建弹窗 ===== */
.psl-create-warn {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 4px;
  color: #ad6800;
  font-size: 12px;
}
.psl-create-type {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.psl-create-type-label {
  color: #8c8c8c;
}
.psl-create-type-hint {
  color: #8c8c8c;
  font-size: 12px;
}
</style>
