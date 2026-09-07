<template>
  <div class="cl-page">
    <!-- 两栏布局：左分类树 + 右元器件清单（参考阶段页面布局） -->
    <div class="cl-body">
      <!-- 左侧：分类树 -->
      <div class="cl-tree">
        <div class="cl-tree-scroll">
          <div
            v-for="node in categoryTree"
            :key="node.oid"
            class="cl-tree-branch"
          >
            <div
              class="cl-tree-node"
              :class="{ 'cl-tree-node--active': selectedCategoryOid === node.oid }"
              @click="selectCategory(node.oid)"
            >
              <span
                v-if="node.children?.length"
                class="cl-tree-toggle"
                @click.stop="toggleNode(node.oid)"
              >{{ expandedNodes.has(node.oid) ? '▾' : '▸' }}</span>
              <span v-else class="cl-tree-toggle cl-tree-toggle--leaf"></span>
              <FolderOutlined class="cl-tree-icon" />
              <span class="cl-tree-name">{{ node.name }}</span>
              <a-dropdown :trigger="['click']">
                <a-button type="text" size="small" class="cl-tree-more" @click.stop>
                  <MoreOutlined style="font-size:11px" />
                </a-button>
                <template #overlay>
                  <a-menu @click="(e) => onCategoryMenu(e, node)">
                    <a-menu-item key="addSub">新建子分类</a-menu-item>
                    <a-menu-item key="rename">重命名</a-menu-item>
                    <a-menu-divider />
                    <a-menu-item key="delete" danger>删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
            <template v-if="node.children?.length && expandedNodes.has(node.oid)">
              <div
                v-for="child in node.children"
                :key="child.oid"
                class="cl-tree-node cl-tree-node--child"
                :class="{ 'cl-tree-node--active': selectedCategoryOid === child.oid }"
                @click="selectCategory(child.oid)"
              >
                <span class="cl-tree-toggle cl-tree-toggle--leaf"></span>
                <FolderOutlined class="cl-tree-icon" />
                <span class="cl-tree-name">{{ child.name }}</span>
                <a-dropdown :trigger="['click']">
                  <a-button type="text" size="small" class="cl-tree-more" @click.stop>
                    <MoreOutlined style="font-size:11px" />
                  </a-button>
                  <template #overlay>
                    <a-menu @click="(e) => onCategoryMenu(e, child)">
                      <a-menu-item key="rename">重命名</a-menu-item>
                      <a-menu-divider />
                      <a-menu-item key="delete" danger>删除</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </div>
            </template>
          </div>
        </div>
        <a-button type="dashed" size="small" block class="cl-tree-new-btn" @click="openCategoryModal(null)">
          <PlusOutlined /> 新建分类
        </a-button>
      </div>

      <!-- 右侧：元器件清单 -->
      <div class="cl-content">
        <DataTable
          :columns="componentColumns"
          :data-source="componentList"
          :loading="componentLoading"
          :pagination="{ pageSize: 15, showSizeChanger: true }"
          row-key="oid"
          size="small"
          searchable
          search-placeholder="搜索编码/名称/型号/厂商..."
        >
          <template #toolbar-left>
            <div class="cl-content-bar-inline">
              <FolderOutlined style="color:#faad14;font-size:14px" />
              <span style="color:#999;font-size:12px;margin-right:4px">当前位置：</span>
              <strong style="font-size:13px">{{ currentCategoryPath }}</strong>
              <a-button size="small" type="link" v-if="selectedCategoryOid" @click="selectCategory(null)">
                查看全部
              </a-button>
            </div>
          </template>
          <template #toolbar>
            <a-button type="primary" size="small" @click="openComponentModal(null)">
              <PlusOutlined /> 新建元器件
            </a-button>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'code'">
              <a-tag color="blue" size="small">{{ record.code || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'stockQty'">
              <span :class="{ 'cl-stock-warn': isLowStock(record) }">
                {{ record.stockQty ?? 0 }} {{ record.unit || 'ea' }}
              </span>
              <a-tooltip v-if="isLowStock(record)" title="低于安全库存">
                <WarningOutlined style="color:#fa8c16;margin-left:4px" />
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'unitCost'">
              ¥{{ record.unitCost ?? 0 }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a-button type="link" size="small" @click="openComponentModal(record)">编辑</a-button>
                <a-button type="link" size="small" danger @click="onRemoveComponent(record)">删除</a-button>
              </a-space>
            </template>
          </template>
        </DataTable>
      </div>
    </div>

    <!-- 新建/编辑分类弹窗 -->
    <a-modal
      v-model:visible="categoryModalVisible"
      :title="categoryModalParent ? '新建子分类' : '新建分类'"
      ok-text="创建"
      cancel-text="取消"
      width="420px"
      centered
      @ok="confirmCategory"
    >
      <div style="margin-bottom:8px;color:#8c8c8c;font-size:12px" v-if="categoryModalParent">
        父分类：{{ categoryModalParent.name }}
      </div>
      <a-input v-model:value="categoryModalName" placeholder="请输入分类名称" @pressEnter="confirmCategory" />
    </a-modal>

    <!-- 新建/编辑元器件弹窗 -->
    <a-modal
      v-model:visible="componentModalVisible"
      :title="componentModalEditing ? '编辑元器件' : '新建元器件'"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="componentSaving"
      width="640px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
      @ok="confirmComponent"
    >
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="元器件编码" help="留空则自动生成">
              <a-input v-model:value="componentForm.code" placeholder="如 EC-202609-000001" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="componentForm.name" placeholder="如 贴片电阻 10KΩ" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="所属分类">
              <a-tree-select
                v-model:value="componentForm.categoryOid"
                :tree-data="categoryTreeSelectData"
                :field-names="{ label: 'name', value: 'oid', children: 'children' }"
                placeholder="请选择分类"
                tree-default-expand-all
                allow-clear
                style="width:100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="型号规格">
              <a-input v-model:value="componentForm.model" placeholder="如 STM32F103C8T6" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="封装">
              <a-input v-model:value="componentForm.packageType" placeholder="如 0805 / LQFP48" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="关键参数/值">
              <a-input v-model:value="componentForm.valueSpec" placeholder="如 10KΩ ±1% / 100nF 16V" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="厂商">
              <a-input v-model:value="componentForm.manufacturer" placeholder="如 Yageo / TI" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="单位">
              <a-input v-model:value="componentForm.unit" placeholder="默认 ea" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="库存数量">
              <a-input-number v-model:value="componentForm.stockQty" :min="0" :precision="0" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="安全库存">
              <a-input-number v-model:value="componentForm.safeStockQty" :min="0" :precision="0" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="单位成本(¥)">
              <a-input-number v-model:value="componentForm.unitCost" :min="0" :precision="4" style="width:100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="描述">
          <a-textarea v-model:value="componentForm.description" :rows="2" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { FolderOutlined, PlusOutlined, MoreOutlined, WarningOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import {
  getComponentCategoryTree, createComponentCategory, updateComponentCategory, deleteComponentCategory,
  getElectronicComponents, createElectronicComponent, updateElectronicComponent, deleteElectronicComponent,
} from '@/api'

// ==================== 分类树 ====================
const categoryTree = ref([])
const selectedCategoryOid = ref(null)
const expandedNodes = ref(new Set())

async function loadCategoryTree() {
  try {
    const res = await getComponentCategoryTree()
    categoryTree.value = res?.code === 200 ? (res.data || []) : []
    // 默认展开第一层
    for (const n of categoryTree.value) {
      if (n.children?.length) expandedNodes.value.add(n.oid)
    }
  } catch { categoryTree.value = [] }
}

function toggleNode(oid) {
  const s = new Set(expandedNodes.value)
  s.has(oid) ? s.delete(oid) : s.add(oid)
  expandedNodes.value = s
}

function selectCategory(oid) {
  selectedCategoryOid.value = oid
  loadComponents()
}

/** 当前分类路径（用于位置条显示） */
const currentCategoryPath = computed(() => {
  if (!selectedCategoryOid.value) return '全部元器件'
  const path = []
  const find = (nodes, chain) => {
    for (const n of nodes) {
      if (n.oid === selectedCategoryOid.value) { path.push(...chain, n.name); return true }
      if (n.children?.length && find(n.children, [...chain, n.name])) return true
    }
    return false
  }
  find(categoryTree.value, [])
  return path.join(' / ') || '全部元器件'
})

// ==================== 分类新建/重命名/删除 ====================
const categoryModalVisible = ref(false)
const categoryModalName = ref('')
const categoryModalParent = ref(null)
const categoryModalEditingOid = ref(null)

function openCategoryModal(parentNode) {
  categoryModalParent.value = parentNode
  categoryModalEditingOid.value = null
  categoryModalName.value = ''
  categoryModalVisible.value = true
}

async function confirmCategory() {
  const name = categoryModalName.value.trim()
  if (!name) { message.warning('请输入分类名称'); return }
  try {
    const res = await createComponentCategory({
      name,
      parentCategoryOid: categoryModalParent.value?.oid || null,
      sortOrder: 0,
    })
    if (res?.code === 200) {
      message.success('分类已创建')
      categoryModalVisible.value = false
      await loadCategoryTree()
    } else {
      message.error(res?.message || '创建失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '创建失败')
  }
}

function onCategoryMenu({ key }, node) {
  if (key === 'addSub') {
    openCategoryModal(node)
  } else if (key === 'rename') {
    renameCategory(node)
  } else if (key === 'delete') {
    removeCategory(node)
  }
}

function renameCategory(node) {
  Modal.confirm({
    title: '重命名分类',
    content: `将「${node.name}」重命名为：`,
    okText: '确定',
    cancelText: '取消',
    centered: true,
    // 复用确认弹窗内容区携带输入框（用 h 渲染过于复杂，这里用第二个弹窗）
    onOk: async () => {
      const name = window.prompt('请输入新的分类名称', node.name)
      if (!name || !name.trim() || name.trim() === node.name) return
      try {
        const res = await updateComponentCategory(node.oid, { name: name.trim() })
        if (res?.code === 200) {
          message.success('已重命名')
          await loadCategoryTree()
        } else {
          message.error(res?.message || '重命名失败')
        }
      } catch (e) {
        message.error(e?.response?.data?.message || '重命名失败')
      }
    },
  })
}

function removeCategory(node) {
  Modal.confirm({
    title: '确认删除分类',
    content: `确定要删除分类「${node.name}」吗？分类下存在子分类或元器件时将无法删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    centered: true,
    onOk: async () => {
      try {
        const res = await deleteComponentCategory(node.oid)
        if (res?.code === 200) {
          message.success('已删除')
          if (selectedCategoryOid.value === node.oid) selectedCategoryOid.value = null
          await loadCategoryTree()
          await loadComponents()
        } else {
          message.error(res?.message || '删除失败')
        }
      } catch (e) {
        message.error(e?.response?.data?.message || '删除失败')
      }
    },
  })
}

// ==================== 元器件清单 ====================
const componentColumns = [
  { title: '编码', key: 'code', width: 150 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '型号规格', dataIndex: 'model', key: 'model', ellipsis: true, width: 170 },
  { title: '封装', dataIndex: 'packageType', key: 'packageType', width: 100 },
  { title: '关键参数', dataIndex: 'valueSpec', key: 'valueSpec', ellipsis: true, width: 140 },
  { title: '厂商', dataIndex: 'manufacturer', key: 'manufacturer', width: 120 },
  { title: '库存', key: 'stockQty', width: 110, align: 'center' },
  { title: '单位成本', key: 'unitCost', width: 100, align: 'right' },
  { title: '操作', key: 'action', width: 110, align: 'center' },
]

const componentList = ref([])
const componentLoading = ref(false)

async function loadComponents() {
  componentLoading.value = true
  try {
    const res = await getElectronicComponents({
      categoryOid: selectedCategoryOid.value || undefined,
    })
    componentList.value = res?.code === 200 ? (res.data || []) : []
  } catch { componentList.value = [] }
  finally { componentLoading.value = false }
}

function isLowStock(record) {
  return record.safeStockQty != null && record.safeStockQty > 0 &&
    (record.stockQty ?? 0) <= record.safeStockQty
}

// ==================== 元器件新建/编辑/删除 ====================
const componentModalVisible = ref(false)
const componentSaving = ref(false)
const componentModalEditing = ref(null)
const componentForm = reactive({
  code: '', name: '', categoryOid: null, model: '', packageType: '',
  valueSpec: '', manufacturer: '', stockQty: 0, safeStockQty: 0,
  unit: 'ea', unitCost: 0, description: '',
})

/** 分类树选择数据（tree-select） */
const categoryTreeSelectData = computed(() => categoryTree.value)

function openComponentModal(record) {
  componentModalEditing.value = record || null
  if (record) {
    Object.assign(componentForm, {
      code: record.code || '', name: record.name || '',
      categoryOid: record.categoryOid || null,
      model: record.model || '', packageType: record.packageType || '',
      valueSpec: record.valueSpec || '', manufacturer: record.manufacturer || '',
      stockQty: record.stockQty ?? 0, safeStockQty: record.safeStockQty ?? 0,
      unit: record.unit || 'ea', unitCost: record.unitCost ?? 0,
      description: record.description || '',
    })
  } else {
    Object.assign(componentForm, {
      code: '', name: '', categoryOid: selectedCategoryOid.value || null,
      model: '', packageType: '', valueSpec: '', manufacturer: '',
      stockQty: 0, safeStockQty: 0, unit: 'ea', unitCost: 0, description: '',
    })
  }
  componentModalVisible.value = true
}

async function confirmComponent() {
  if (!componentForm.name?.trim()) { message.warning('请输入元器件名称'); return }
  componentSaving.value = true
  try {
    const payload = { ...componentForm, name: componentForm.name.trim() }
    const res = componentModalEditing.value
      ? await updateElectronicComponent(componentModalEditing.value.oid, payload)
      : await createElectronicComponent(payload)
    if (res?.code === 200) {
      message.success(componentModalEditing.value ? '元器件已更新' : '元器件已创建')
      componentModalVisible.value = false
      await loadComponents()
    } else {
      message.error(res?.message || '保存失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '保存失败')
  } finally {
    componentSaving.value = false
  }
}

function onRemoveComponent(record) {
  Modal.confirm({
    title: '确认删除元器件',
    content: `确定要删除元器件「${record.name || record.code}」吗？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    centered: true,
    onOk: async () => {
      try {
        const res = await deleteElectronicComponent(record.oid)
        if (res?.code === 200) {
          message.success('已删除')
          await loadComponents()
        } else {
          message.error(res?.message || '删除失败')
        }
      } catch (e) {
        message.error(e?.response?.data?.message || '删除失败')
      }
    },
  })
}

onMounted(async () => {
  await loadCategoryTree()
  await loadComponents()
})
</script>

<style scoped>
.cl-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 两栏布局（参考阶段页面文件夹区） ===== */
.cl-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 240px 1fr;
  grid-template-rows: minmax(0, 1fr);
  gap: 16px;
}

/* ===== 左侧分类树 ===== */
.cl-tree {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.cl-tree-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 6px 0 8px;
}
.cl-tree-branch {
  display: block;
}
.cl-tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px 4px 10px;
  cursor: pointer;
  font-size: 13px;
  color: #262626;
  white-space: nowrap;
}
.cl-tree-node:hover {
  background: #f5f5f5;
}
.cl-tree-node--active {
  background: #e6f4ff;
  color: #1677ff;
}
.cl-tree-node--child {
  padding-left: 30px;
}
.cl-tree-toggle {
  width: 12px;
  flex-shrink: 0;
  font-size: 10px;
  color: #8c8c8c;
  text-align: center;
}
.cl-tree-toggle--leaf {
  visibility: hidden;
}
.cl-tree-icon {
  color: #faad14;
  font-size: 13px;
  flex-shrink: 0;
}
.cl-tree-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cl-tree-more {
  opacity: 0;
  flex-shrink: 0;
}
.cl-tree-node:hover .cl-tree-more {
  opacity: 1;
}
.cl-tree-new-btn {
  flex-shrink: 0;
  margin: 0 10px 8px;
}

/* ===== 右侧内容 ===== */
.cl-content {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.cl-content-bar-inline {
  display: flex;
  align-items: center;
  gap: 4px;
}
.cl-stock-warn {
  color: #fa8c16;
  font-weight: 600;
}
</style>
