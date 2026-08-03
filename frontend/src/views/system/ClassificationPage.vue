<template>
  <div class="cls-page">
    <!-- 页头 -->
    <div class="cls-header">
      <div class="cls-header-left">
        <h3 class="cls-title">分类管理</h3>
        <span class="cls-subtitle">管理系统分类树，支持多级分类结构</span>
      </div>
      <div class="cls-header-right">
        <a-button type="primary" ghost @click="openIBAExtension">
          <template #icon><PlusOutlined /></template>
          IBA扩展
        </a-button>
      </div>
    </div>

    <!-- 统计栏 -->
    <div class="cls-stats-bar">
      <div class="cls-stat-item">
        <ApartmentOutlined class="cls-stat-icon" />
        <span class="cls-stat-value">{{ totalCount }}</span>
        <span class="cls-stat-label">分类总数</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="cls-stat-item">
        <span class="cls-stat-value">{{ rootCount }}</span>
        <span class="cls-stat-label">根分类</span>
      </div>
      <div class="cls-stat-item">
        <span class="cls-stat-value">{{ childCount }}</span>
        <span class="cls-stat-label">子分类</span>
      </div>
    </div>

    <!-- 主体：左树 + 右详情 -->
    <div class="cls-body">
      <!-- 左侧树 -->
      <div class="cls-left">
        <div class="cls-left-header">
          <span class="cls-left-title">分类树</span>
          <a-space size="small">
            <a-button size="small" @click="expandAll">展开全部</a-button>
            <a-button size="small" @click="collapseAll">收起全部</a-button>
          </a-space>
        </div>
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索分类..."
          style="margin-bottom: 8px"
          allow-clear
          @search="handleSearch"
        />
        <a-spin :spinning="loading" class="cls-tree-spin">
          <a-tree
            v-if="treeData.length"
            v-model:expandedKeys="expandedKeys"
            :tree-data="treeData"
            :field-names="{ title: 'name', key: 'oid', children: 'children' }"
            :selected-keys="selectedKeys"
            block-node
            @select="onSelectNode"
          >
            <template #title="{ name, code }">
              <span class="cls-tree-node">
                <FolderOutlined class="cls-tree-icon" />
                <span class="cls-tree-name">{{ name }}</span>
                <span class="cls-tree-code">{{ code }}</span>
              </span>
            </template>
          </a-tree>
          <a-empty v-else description="暂无分类，请新建" />
        </a-spin>
        <div class="cls-left-footer">
          <a-button type="dashed" block size="small" @click="openCreate(null)">
            <template #icon><PlusOutlined /></template>
            新建根分类
          </a-button>
        </div>
      </div>

      <!-- 右侧详情 -->
      <div class="cls-right">
        <div v-if="!selectedNode && !isCreating" class="cls-placeholder">
          <a-empty description="请选择左侧分类查看详情，或新建分类" />
        </div>

        <!-- 新建/编辑模式 -->
        <template v-if="isEditing || isCreating">
          <div class="cls-detail-header">
            <span class="cls-detail-title">{{ isCreating ? '新建分类' : '编辑分类' }}</span>
            <a-space>
              <a-button size="small" @click="cancelEdit">取消</a-button>
              <a-button type="primary" size="small" :loading="saving" @click="handleSave">保存</a-button>
            </a-space>
          </div>
          <a-form :model="form" layout="vertical" class="cls-form">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="分类标识" required>
                  <a-input v-model:value="form.identifier" placeholder="唯一标识，如 product-category" size="large" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="分类码">
                  <a-input v-model:value="form.code" placeholder="分类码" size="large" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="名称" required>
                  <a-input v-model:value="form.name" placeholder="名称" size="large" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="显示名称">
                  <a-input v-model:value="form.displayName" placeholder="显示名称" size="large" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="排序">
                  <a-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" size="large" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="缩略图">
                  <ImageUploader v-model:value="form.thumbnail" />
                </a-form-item>
              </a-col>
              <a-col :span="24">
                <a-form-item label="描述">
                  <a-textarea v-model:value="form.description" :rows="3" placeholder="分类描述..." size="large" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </template>

        <!-- 查看模式：Tab 切换 基本信息 / IBA属性 -->
        <template v-if="!isEditing && !isCreating && selectedNode">
          <div class="cls-breadcrumb">
            <span class="cls-breadcrumb-label">当前位置：</span>
            <a-breadcrumb>
              <a-breadcrumb-item>分类管理</a-breadcrumb-item>
              <a-breadcrumb-item v-for="(item, idx) in breadcrumbPath" :key="idx">{{ item }}</a-breadcrumb-item>
            </a-breadcrumb>
          </div>
          <div class="cls-detail-header">
            <span class="cls-detail-title">{{ detail.name || '分类详情' }}</span>
            <a-space>
              <a-button type="primary" size="small" @click="startEdit">编辑</a-button>
              <a-button size="small" @click="openCreate(selectedNode.oid)">
                <template #icon><PlusOutlined /></template>
                新建子分类
              </a-button>
              <a-popconfirm
                title="确定删除该分类？若存在子分类则无法删除。"
                @confirm="handleDelete"
              >
                <a-button size="small" danger>
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </div>

          <a-tabs v-model:activeKey="detailTab" size="small" @change="onDetailTabChange">
            <a-tab-pane key="basic" tab="基本信息" />
            <a-tab-pane key="iba" tab="IBA属性" />
          </a-tabs>

          <!-- 基本信息 -->
          <div v-if="detailTab === 'basic'">
            <a-descriptions :column="2" bordered size="small">
              <a-descriptions-item label="分类标识">
                <a-tag color="blue">{{ detail.identifier || '-' }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="分类码">{{ detail.code || '-' }}</a-descriptions-item>
              <a-descriptions-item label="名称">{{ detail.name }}</a-descriptions-item>
              <a-descriptions-item label="显示名称">{{ detail.displayName || '-' }}</a-descriptions-item>
              <a-descriptions-item label="描述" :span="2">{{ detail.description || '-' }}</a-descriptions-item>
              <a-descriptions-item label="父分类">{{ parentName || '根分类' }}</a-descriptions-item>
              <a-descriptions-item label="排序">{{ detail.sortOrder ?? 0 }}</a-descriptions-item>
              <a-descriptions-item label="缩略图" :span="2">
                <img v-if="detail.thumbnail" :src="detail.thumbnail" class="cls-thumbnail" />
                <span v-else>-</span>
              </a-descriptions-item>
            </a-descriptions>
          </div>

          <!-- IBA属性 -->
          <div v-if="detailTab === 'iba'">
            <a-tabs v-model:activeKey="ibaSubTab" size="small" class="cls-iba-subtabs">
              <!-- 编辑表单（使用布局渲染） -->
              <a-tab-pane key="update" tab="编辑表单">
                <ClsIbaFormPage
                  :key="'update_' + detail.oid"
                  :classification-oid="detail.oid"
                  operation-code="update"
                  @saved="onIbaFormSaved"
                  @design-layout="openLayoutDesigner"
                />
              </a-tab-pane>

              <!-- 新建表单（使用布局渲染） -->
              <a-tab-pane key="create" tab="新建表单">
                <ClsIbaFormPage
                  :key="'create_' + detail.oid"
                  :classification-oid="detail.oid"
                  operation-code="create"
                  @saved="onIbaFormSaved"
                  @design-layout="openLayoutDesigner"
                />
              </a-tab-pane>

              <!-- 详情表单（只读） -->
              <a-tab-pane key="detail" tab="详情表单">
                <ClsIbaFormPage
                  :key="'detail_' + detail.oid"
                  :classification-oid="detail.oid"
                  operation-code="detail"
                  @design-layout="openLayoutDesigner"
                />
              </a-tab-pane>

              <!-- 布局设计 -->
              <a-tab-pane key="designer" tab="布局设计">
                <div class="cls-iba-designer-placeholder">
                  <a-empty description="为分类的 IBA 属性设计表单布局">
                    <a-button type="primary" @click="openLayoutDesigner">打开布局设计器</a-button>
                  </a-empty>
                </div>
              </a-tab-pane>

              <!-- IBA关联管理 -->
              <a-tab-pane key="mappings" tab="关联IBA">
                <div class="cls-iba-header">
                  <span class="cls-iba-count">已分配 {{ ibaMappings.length }} 个 IBA 属性</span>
                  <a-button size="small" type="primary" @click="openAssignIBA">
                    <template #icon><PlusOutlined /></template>
                    分配IBA属性
                  </a-button>
                </div>
                <a-table
                  :columns="ibaColumns"
                  :data-source="ibaMappings"
                  :loading="ibaLoading"
                  row-key="oid"
                  size="small"
                  :pagination="false"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'ibaCode'">
                      <a-tag>{{ record.ibaCode }}</a-tag>
                    </template>
                    <template v-if="column.key === 'ibaDataType'">
                      <a-tag color="cyan">{{ record.ibaDataType }}</a-tag>
                    </template>
                    <template v-if="column.key === 'required'">
                      <a-badge :status="record.required ? 'error' : 'default'" :text="record.required ? '必填' : '可选'" />
                    </template>
                    <template v-if="column.key === 'action'">
                      <a-popconfirm
                        title="确定移除此 IBA 关联？"
                        @confirm="handleRemoveIBA(record)"
                      >
                        <a-button type="link" size="small" danger>移除</a-button>
                      </a-popconfirm>
                    </template>
                  </template>
                </a-table>
                <a-empty v-if="!ibaLoading && !ibaMappings.length" description="暂未分配 IBA 属性" style="margin-top: 24px" />
              </a-tab-pane>
            </a-tabs>
          </div>
        </template>
      </div>
    </div>

    <!-- 分配 IBA 属性弹窗 -->
    <a-modal
      v-model:open="ibaAssignVisible"
      title="分配 IBA 属性"
      @ok="handleAssignIBA"
      width="600px"
    >
      <a-input-search
        v-model:value="ibaSearchKey"
        placeholder="搜索 IBA..."
        style="margin-bottom: 12px"
        @search="loadUnassignedIBAs"
      />
      <a-checkbox-group v-model:value="selectedIBAIds" style="width: 100%">
        <div v-for="iba in unassignedIBAs" :key="iba.oid" class="cls-iba-item">
          <a-checkbox :value="iba.oid">
            <span class="cls-iba-label">{{ iba.name }}</span>
            <a-tag color="cyan" size="small" style="margin-left: 8px">{{ iba.dataType }}</a-tag>
            <span class="cls-iba-code">{{ iba.code }}</span>
          </a-checkbox>
        </div>
      </a-checkbox-group>
      <a-empty v-if="!unassignedIBAs.length" description="暂无可分配的 IBA 属性" />
    </a-modal>

    <!-- IBA 扩展抽屉（全局） -->
    <IBAExtension ref="ibaExtRef" />

    <!-- IBA 布局设计器抽屉 -->
    <a-drawer
      v-model:open="layoutDesignerVisible"
      title=""
      :width="960"
      placement="right"
      :mask-closable="false"
      :closable="false"
      :body-style="{ padding: 0 }"
    >
      <ClsIbaLayoutDesigner
        v-if="layoutDesignerVisible && detail.oid"
        :classification-oid="detail.oid"
        :classification-name="detail.name || detail.identifier || ''"
        @back="layoutDesignerVisible = false"
      />
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined, ApartmentOutlined, FolderOutlined } from '@ant-design/icons-vue'
import ImageUploader from '@/components/ImageUploader.vue'
import IBAExtension from '@/components/IBAExtension.vue'
import ClsIbaFormPage from '@/components/ClsIbaFormPage.vue'
import ClsIbaLayoutDesigner from '@/views/system/ClsIbaLayoutDesigner.vue'
import {
  getClassificationTree, getClassification,
  createClassification, updateClassification, deleteClassification,
  getClassificationIBAs, getUnassignedClsIBAs, batchAssignClsIBAs, removeClsIBAMapping
} from '@/api'

// ===== 状态 =====
const loading = ref(false)
const saving = ref(false)
const treeData = ref([])
const selectedKeys = ref([])
const selectedNode = ref(null)
const detail = reactive({})
const isEditing = ref(false)
const isCreating = ref(false)
const keyword = ref('')
const expandedKeys = ref([])
const detailTab = ref('basic')
const ibaSubTab = ref('values')

// IBA 相关
const ibaMappings = ref([])
const ibaLoading = ref(false)
const ibaAssignVisible = ref(false)
const unassignedIBAs = ref([])
const selectedIBAIds = ref([])
const ibaSearchKey = ref('')
const ibaColumns = [
  { title: 'IBA编码', key: 'ibaCode', dataIndex: 'ibaCode', width: 120 },
  { title: 'IBA名称', key: 'ibaName', dataIndex: 'ibaName' },
  { title: '数据类型', key: 'ibaDataType', dataIndex: 'ibaDataType', width: 90 },
  { title: '必填', key: 'required', dataIndex: 'required', width: 70 },
  { title: '操作', key: 'action', width: 70 },
]

const form = reactive({
  identifier: '',
  code: '',
  name: '',
  displayName: '',
  description: '',
  thumbnail: '',
  parentOid: '',
  sortOrder: 0
})

// ===== 统计 =====
const totalCount = ref(0)
const rootCount = ref(0)
const childCount = computed(() => totalCount.value - rootCount.value)

function countNodes(nodes) {
  let total = 0
  for (const n of nodes) {
    total++
    if (n.children?.length) total += countNodes(n.children)
  }
  return total
}

const parentName = computed(() => {
  if (!detail.parentOid) return null
  const findNode = (nodes, oid) => {
    for (const n of nodes) {
      if (n.oid === oid) return n.name
      if (n.children) {
        const found = findNode(n.children, oid)
        if (found) return found
      }
    }
    return null
  }
  return findNode(treeData.value, detail.parentOid)
})

/** 面包屑路径：从根到当前节点 */
const breadcrumbPath = computed(() => {
  if (!selectedNode.value || !detail.oid) return []
  const findPath = (nodes, targetOid, path) => {
    for (const n of nodes) {
      if (n.oid === targetOid) { path.push(n.name); return true }
      if (n.children?.length) {
        path.push(n.name)
        if (findPath(n.children, targetOid, path)) return true
        path.pop()
      }
    }
    return false
  }
  const path = []
  findPath(treeData.value, detail.oid, path)
  return path
})

// ===== 树操作 =====
function expandAll() {
  const collect = (nodes) => {
    const keys = []
    for (const n of nodes) {
      keys.push(n.oid)
      if (n.children?.length) keys.push(...collect(n.children))
    }
    return keys
  }
  expandedKeys.value = collect(treeData.value)
}

function collapseAll() {
  expandedKeys.value = []
}

// ===== 数据加载 =====
async function loadTree() {
  loading.value = true
  try {
    const res = await getClassificationTree()
    const data = res?.data || res || []
    treeData.value = Array.isArray(data) ? data : []
    totalCount.value = countNodes(treeData.value)
    rootCount.value = treeData.value.length
    // 默认展开第一层
    expandedKeys.value = treeData.value.map(n => n.oid)
  } catch { treeData.value = [] }
  finally { loading.value = false }
}

async function onSelectNode(keys, { node }) {
  if (!keys.length) return
  selectedKeys.value = keys
  isEditing.value = false
  isCreating.value = false
  try {
    const res = await getClassification(keys[0])
    const d = res?.data || res
    if (d) {
      Object.keys(detail).forEach(k => delete detail[k])
      Object.assign(detail, d)
      selectedNode.value = node
    }
  } catch { /* ignore */ }
}

function handleSearch() {
  if (!keyword.value) { loadTree(); return }
  const filter = (nodes) => {
    return nodes.reduce((acc, n) => {
      const match = n.name?.toLowerCase().includes(keyword.value.toLowerCase())
        || n.code?.toLowerCase().includes(keyword.value.toLowerCase())
        || n.identifier?.toLowerCase().includes(keyword.value.toLowerCase())
      const filteredChildren = n.children ? filter(n.children) : []
      if (match || filteredChildren.length) {
        acc.push({ ...n, children: filteredChildren.length ? filteredChildren : n.children })
      }
      return acc
    }, [])
  }
  getClassificationTree().then(res => {
    const data = res?.data || res || []
    treeData.value = filter(Array.isArray(data) ? data : [])
    // 搜索时展开全部
    expandedKeys.value = collectAllKeys(treeData.value)
  }).catch(() => {})
}

function collectAllKeys(nodes) {
  const keys = []
  for (const n of nodes) {
    keys.push(n.oid)
    if (n.children?.length) keys.push(...collectAllKeys(n.children))
  }
  return keys
}

// ===== CRUD 操作 =====
function openCreate(parentOid) {
  isCreating.value = true
  selectedKeys.value = []
  selectedNode.value = null
  isEditing.value = true
  Object.assign(form, {
    identifier: '', code: '', name: '', displayName: '',
    description: '', thumbnail: '', parentOid: parentOid || '', sortOrder: 0
  })
  Object.keys(detail).forEach(k => delete detail[k])
}

function startEdit() {
  isEditing.value = true
  Object.assign(form, {
    identifier: detail.identifier || '',
    code: detail.code || '',
    name: detail.name || '',
    displayName: detail.displayName || '',
    description: detail.description || '',
    thumbnail: detail.thumbnail || '',
    parentOid: detail.parentOid || '',
    sortOrder: detail.sortOrder ?? 0
  })
}

function cancelEdit() {
  isEditing.value = false
  isCreating.value = false
  if (!detail.oid) {
    selectedNode.value = null
    selectedKeys.value = []
  }
}

async function handleSave() {
  if (!form.name?.trim()) { message.warning('请输入名称'); return }
  if (!form.identifier?.trim()) { message.warning('请输入分类标识'); return }
  saving.value = true
  try {
    if (detail.oid) {
      await updateClassification(detail.oid, form)
      message.success('更新成功')
      Object.assign(detail, form)
    } else {
      await createClassification(form)
      message.success('创建成功')
    }
    isEditing.value = false
    isCreating.value = false
    await loadTree()
    if (detail.oid) {
      selectedKeys.value = [detail.oid]
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '操作失败')
  } finally { saving.value = false }
}

function handleDelete() {
  if (!detail.oid) return
  deleteClassification(detail.oid).then(() => {
    message.success('删除成功')
    selectedKeys.value = []
    selectedNode.value = null
    Object.keys(detail).forEach(k => delete detail[k])
    loadTree()
  }).catch(e => {
    message.error(e?.response?.data?.message || '删除失败')
  })
}

// ===== IBA 关联操作 =====
function onDetailTabChange(key) {
  if (key === 'iba' && detail.oid) {
    loadIBAMappings(detail.oid)
  }
}

async function loadIBAMappings(classificationOid) {
  ibaLoading.value = true
  try {
    const res = await getClassificationIBAs(classificationOid)
    ibaMappings.value = res?.data || res || []
  } catch { ibaMappings.value = [] }
  finally { ibaLoading.value = false }
}

function openAssignIBA() {
  if (!detail.oid) return
  ibaAssignVisible.value = true
  selectedIBAIds.value = []
  loadUnassignedIBAs()
}

async function loadUnassignedIBAs(keyword) {
  try {
    const res = await getUnassignedClsIBAs(detail.oid, keyword)
    unassignedIBAs.value = res?.data || res || []
  } catch { unassignedIBAs.value = [] }
}

async function handleAssignIBA() {
  if (!selectedIBAIds.value.length) {
    message.warning('请选择要分配的 IBA')
    return
  }
  try {
    await batchAssignClsIBAs(detail.oid, selectedIBAIds.value)
    message.success('IBA 属性分配成功')
    ibaAssignVisible.value = false
    loadIBAMappings(detail.oid)
  } catch (e) {
    message.error(e?.response?.data?.message || '分配失败')
  }
}

async function handleRemoveIBA(record) {
  try {
    await removeClsIBAMapping(record.oid)
    message.success('IBA 关联已移除')
    loadIBAMappings(detail.oid)
  } catch (e) {
    message.error(e?.response?.data?.message || '移除失败')
  }
}

// ===== IBA 扩展 =====
const ibaExtRef = ref(null)
function openIBAExtension() {
  ibaExtRef.value?.open()
}

// ===== IBA 布局设计器 =====
const layoutDesignerVisible = ref(false)
function openLayoutDesigner() {
  layoutDesignerVisible.value = true
}

// ===== IBA 表单保存回调 =====
function onIbaFormSaved() {
  // 保存成功后刷新关联列表
  if (detail.oid) loadIBAMappings(detail.oid)
}

// ===== 初始化 =====
onMounted(() => { loadTree() })
</script>

<style scoped>
.cls-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* ===== 页头 ===== */
.cls-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}

.cls-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.cls-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cls-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.cls-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 统计栏 ===== */
.cls-stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  flex-shrink: 0;
  margin-bottom: 12px;
}

.cls-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.cls-stat-icon {
  font-size: 14px;
  color: #1677ff;
}

.cls-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.cls-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 主体 ===== */
.cls-body {
  flex: 1;
  min-height: 0;
  display: flex;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
}

/* ===== 左侧树 ===== */
.cls-left {
  width: 300px;
  min-width: 300px;
  border-right: 1px solid #f0f0f0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cls-left-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.cls-left-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

.cls-tree-spin {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.cls-tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cls-tree-icon {
  font-size: 14px;
  color: #1677ff;
}

.cls-tree-name {
  font-size: 14px;
}

.cls-tree-code {
  font-size: 11px;
  color: #999;
  background: #f5f5f5;
  padding: 0 4px;
  border-radius: 2px;
}

.cls-left-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

/* ===== 右侧详情 ===== */
.cls-right {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.cls-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}

.cls-breadcrumb {
  display: flex;
  align-items: center;
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.cls-breadcrumb-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-right: 4px;
  white-space: nowrap;
}

.cls-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.cls-detail-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
}

.cls-form {
  max-width: 720px;
}

.cls-thumbnail {
  max-width: 200px;
  max-height: 120px;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}

/* ===== IBA 属性 ===== */
.cls-iba-subtabs {
  margin-top: -4px;
}
.cls-iba-subtabs :deep(.ant-tabs-nav) {
  margin-bottom: 12px;
}

.cls-iba-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.cls-iba-count {
  font-size: 13px;
  color: #666;
}

.cls-iba-item {
  padding: 6px 0;
  border-bottom: 1px solid #fafafa;
}

.cls-iba-label {
  font-size: 13px;
}

.cls-iba-code {
  font-size: 11px;
  color: #999;
  margin-left: 8px;
}

.cls-iba-designer-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
</style>
