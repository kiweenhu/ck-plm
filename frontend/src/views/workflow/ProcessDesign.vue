<template>
  <div class="process-design">
    <div class="page-header">
      <h2>流程清单</h2>
      <a-space>
        <a-button type="primary" @click="mode = 'design'; editingModelId = ''">
          新建流程
        </a-button>
      </a-space>
    </div>

    <!-- 列表模式 -->
    <div v-if="mode === 'list'" class="process-layout">
      <!-- 左侧分类清单 -->
      <div class="category-sidebar">
        <div class="category-title">流程分类</div>
        <ul class="category-list">
          <li
            :class="['category-item', { active: !filterCategory }]"
            @click="filterCategory = ''"
          >
            <span class="category-name">全部</span>
            <span class="category-count">{{ items.length }}</span>
          </li>
          <li
            v-for="cat in categoriesWithCount"
            :key="cat.name"
            :class="['category-item', { active: filterCategory === cat.name }]"
            @click="filterCategory = cat.name"
          >
            <span class="category-name">{{ cat.name }}</span>
            <span class="category-count">{{ cat.count }}</span>
          </li>
        </ul>
        <div class="category-actions">
          <a-button type="dashed" block size="small" @click="showCategoryMgr = true">
            管理分类
          </a-button>
        </div>
      </div>

      <!-- 右侧流程列表 -->
      <div class="process-content">
        <a-card :bodyStyle="{ padding: 0 }">
          <a-table
            :columns="columns"
            :dataSource="filteredItems"
            :loading="loading"
            rowKey="id"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'deployStatus'">
                <a-tag v-if="!record.deployed" color="orange">未部署</a-tag>
                <a-tag v-else-if="record.suspended" color="red">已挂起(v{{ record.version ?? '-' }})</a-tag>
                <a-tag v-else color="green">已部署(v{{ record.version ?? '-' }})</a-tag>
              </template>
              <template v-if="column.key === 'version'">
                <span v-if="record.deployed">v{{ record.version ?? '-' }}</span>
                <span v-else style="color:#999">-</span>
              </template>
              <template v-if="column.key === 'category'">
                <a-select
                  v-if="editCategoryId === record.id"
                  v-model:value="editCategoryValue"
                  style="width: 120px"
                  size="small"
                  showSearch
                  placeholder="选择分类"
                  :open="true"
                  @change="saveCategory(record)"
                  @blur="editCategoryId = ''"
                >
                  <a-select-option v-for="c in categories" :key="c" :value="c">{{ c }}</a-select-option>
                  <a-select-option value="">无分类</a-select-option>
                </a-select>
                <a-tag v-else-if="record.category" color="blue" style="cursor:pointer" @click="startEditCategory(record)">{{ record.category }}</a-tag>
                <a-tag v-else style="cursor:pointer;color:#999;border:1px dashed #d9d9d9" @click="startEditCategory(record)">未分类</a-tag>
              </template>
              <template v-if="column.key === 'action'">
                <a-space>
                  <a-button v-if="!record.deployed" size="small" type="primary"
                    @click="editUndeployedModel(record)">编辑</a-button>
                  <a-button v-else size="small" type="primary" @click="editDefinition(record)">编辑</a-button>

                  <a-popconfirm v-if="!record.deployed" title="确认部署此流程？" @confirm="deployUndeployedModel(record)">
                    <a-button size="small" type="primary" ghost :loading="deployingId === record.id">部署</a-button>
                  </a-popconfirm>

                  <a-button v-if="record.deployed" size="small" @click="viewDiagram(record)">流程图</a-button>
                  <a-button v-if="record.deployed" size="small" @click="viewXml(record)">BPMN</a-button>

                  <a-popconfirm v-if="record.deployed"
                    :title="record.suspended ? '确认激活此流程定义？' : '确认挂起此流程定义？'"
                    @confirm="toggleSuspend(record)">
                    <a-button size="small" :type="record.suspended ? 'primary' : 'dashed'">
                      {{ record.suspended ? '激活' : '挂起' }}
                    </a-button>
                  </a-popconfirm>

                  <a-button v-if="record.deployed" size="small" @click="showHistory(record)">历史版本</a-button>

                  <a-popconfirm v-if="record.deployed && record._modelVersion > record._deployedVersion"
                    title="模型已有新版本，确认部署至引擎？"
                    @confirm="deployDefinition(record)">
                    <a-button size="small" type="primary" :loading="deployingId === record.id">部署新版本</a-button>
                  </a-popconfirm>

                  <a-popconfirm v-if="record.deployed"
                    title="确认删除此流程定义？级联删除所有相关实例。"
                    @confirm="deleteDefinition(record)">
                    <a-button size="small" danger>删除</a-button>
                  </a-popconfirm>
                  <a-popconfirm v-else title="确认删除此未部署的模型？"
                    @confirm="deleteUndeployedModel(record)">
                    <a-button size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
          <a-empty v-if="!loading && filteredItems.length === 0" description="暂无流程，去创建一个吧" style="margin:40px 0;">
            <a-button type="primary" @click="mode = 'design'">去设计第一个流程</a-button>
          </a-empty>
        </a-card>
      </div>
    </div>

    <!-- 设计模式 -->
    <div v-if="mode === 'design'" style="display:flex;flex-direction:column;gap:12px;">
      <div style="display:flex;justify-content:flex-end;">
        <a-space>
          <a-button @click="mode = 'list'; loadAll()">
            <template #icon><UnorderedListOutlined /></template>
            返回列表
          </a-button>
        </a-space>
      </div>
      <ActivitiModeler
        :key="editingModelId"
        :modelId="editingModelId"
        :initialCategory="filterCategory"
      />
    </div>

    <!-- 查看流程图弹窗 -->
    <a-modal v-model:visible="showDiagramModal" title="流程图" width="900px" :footer="null"
      @afterClose="destroyDiagramViewer">
      <div v-if="diagramLoading" style="text-align:center;padding:60px 0;">
        <a-spin tip="加载流程图中..." />
      </div>
      <div ref="diagramRef" class="diagram-viewer-canvas" v-show="!diagramLoading"></div>
    </a-modal>

    <!-- 查看 BPMN XML 弹窗 -->
    <a-modal v-model:visible="showXmlModal" title="BPMN 2.0 XML" width="800px" :footer="null">
      <pre
        v-if="currentXml"
        style="max-height:500px;overflow:auto;background:#f5f5f5;padding:16px;border-radius:4px;"
      >{{ currentXml }}</pre>
      <a-empty v-else description="无法获取 BPMN XML" />
    </a-modal>

    <!-- 历史版本弹窗 -->
    <a-modal v-model:visible="showHistoryModal" :title="`历史版本 - ${historyRecord?.name || ''}`" width="800px" :footer="null">
      <a-table
        :columns="historyColumns"
        :dataSource="historyList"
        :loading="historyLoading"
        rowKey="id"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag v-if="record.suspended" color="red">已挂起</a-tag>
            <a-tag v-else color="green">已部署</a-tag>
          </template>
          <template v-if="column.key === 'isLatest'">
            <a-tag v-if="record.id === historyRecord?.id" color="blue">当前</a-tag>
            <span v-else style="color:#999">-</span>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button size="small" @click="viewDiagram(record)">流程图</a-button>
              <a-button size="small" @click="viewXml(record)">BPMN</a-button>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          <a-empty description="暂无历史版本记录" />
        </template>
      </a-table>
    </a-modal>

    <!-- 分类管理弹窗 -->
    <a-modal v-model:visible="showCategoryMgr" title="管理流程分类" :footer="null" width="480px">
      <div style="margin-bottom:12px;">
        <a-input-search
          v-model:value="newCategoryName"
          placeholder="输入新分类名称"
          enter-button="添加"
          :loading="categoryAdding"
          @search="handleAddCategory"
        />
      </div>
      <a-list :dataSource="categories" size="small" :locale="{ emptyText: '暂无分类' }">
        <template #renderItem="{ item }">
          <a-list-item>
            <span>{{ item }}</span>
            <a-popconfirm title="删除此分类将同时清除所有使用该分类的流程的分类标记，确认删除？" @confirm="handleDeleteCategory(item)">
              <a-button type="link" danger size="small">删除</a-button>
            </a-popconfirm>
          </a-list-item>
        </template>
      </a-list>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { UnorderedListOutlined } from '@ant-design/icons-vue'
import api from '@/api'
import ActivitiModeler from './ActivitiModeler.vue'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/bpmn-js.css'

const mode = ref('list')
const loading = ref(false)
const items = ref([])
const categories = ref([])
const filterCategory = ref('')
const editingModelId = ref('')
const deployingId = ref('')
const showDiagramModal = ref(false)
const showXmlModal = ref(false)
const diagramLoading = ref(false)
const currentXml = ref('')
const diagramRef = ref(null)

// 分类管理
const showCategoryMgr = ref(false)
const newCategoryName = ref('')
const categoryAdding = ref(false)

// 行内编辑
const editCategoryId = ref('')
const editCategoryValue = ref('')

// 历史版本
const showHistoryModal = ref(false)
const historyLoading = ref(false)
const historyList = ref([])
const historyRecord = ref(null)

const historyColumns = [
  { title: '版本号', dataIndex: 'version', key: 'version', align: 'center', width: 80 },
  { title: '流程名称', dataIndex: 'name', key: 'name' },
  { title: '分类', dataIndex: 'category', key: 'category', align: 'center', width: 100 },
  { title: '状态', key: 'status', align: 'center', width: 80 },
  { title: '部署ID', dataIndex: 'deploymentId', key: 'deploymentId', ellipsis: true },
  { title: '当前版本', key: 'isLatest', align: 'center', width: 80 },
  { title: '操作', key: 'action', width: 160 }
]

let bpmnViewer = null

const columns = [
  { title: '流程名称', dataIndex: 'name', key: 'name' },
  { title: '版本', key: 'version', align: 'center', width: 70 },
  { title: '分类', key: 'category', align: 'center', width: 140 },
  { title: '部署状态', key: 'deployStatus', align: 'center', width: 130 },
  { title: '操作', key: 'action', width: 480 }
]

/** 带数量的分类列表 */
const categoriesWithCount = computed(() => {
  const countMap = {}
  for (const item of items.value) {
    const cat = item.category || ''
    if (cat) {
      countMap[cat] = (countMap[cat] || 0) + 1
    }
  }
  return categories.value.map(name => ({
    name,
    count: countMap[name] || 0
  }))
})

/** 按筛选条件过滤 */
const filteredItems = computed(() => {
  if (!filterCategory.value) return items.value
  return items.value.filter(i => i.category === filterCategory.value)
})

/** 合并已部署定义 + 未部署模型，构建统一列表 */
async function loadAll() {
  loading.value = true
  try {
    const [defRes, modelRes, catRes] = await Promise.all([
      api.get('/workflow/definition/list'),
      api.get('/modeler/models'),
      api.get('/modeler/categories')
    ])

    const deployedList = (defRes.code === 200 ? defRes.data : []) || []
    const modelList = (modelRes.code === 200 ? modelRes.data : []) || []
    categories.value = (catRes.code === 200 ? catRes.data : []) || []

    // 构建 model key → model 的映射
    const modelByKey = new Map()
    for (const m of modelList) {
      modelByKey.set(m.key, m)
    }

    // 已部署项：category 优先取模型的，回退到引擎的
    const result = deployedList.map(d => {
      const model = modelByKey.get(d.key)
      return {
        id: d.id,
        _modelId: model?.id || null,
        _deployedVersion: d.version,
        _modelVersion: model ? model.version : d.version,
        name: d.name,
        key: d.key,
        description: d.description || '',
        version: model ? model.version : d.version,
        category: (model && model.category) ? model.category : (d.category || ''),
        deploymentId: d.deploymentId,
        suspended: d.suspended,
        deployed: true
      }
    })

    // 未部署项
    const deployedKeys = new Set(deployedList.map(d => d.key))
    for (const m of modelList) {
      if (!deployedKeys.has(m.key)) {
        result.push({
          id: m.id,
          _modelId: m.id,
          name: m.name,
          key: m.key,
          description: m.description || '',
          version: m.version,
          category: m.category || '',
          deployed: false,
          _bpmnXml: m.bpmnXml
        })
      }
    }

    items.value = result
  } finally {
    loading.value = false
  }
}

// ==================== 行内编辑分类 ====================

function startEditCategory(record) {
  editCategoryId.value = record.id
  editCategoryValue.value = record.category || undefined
}

async function saveCategory(record) {
  const val = editCategoryValue.value || ''
  const modelId = record._modelId || record.id
  if (!modelId) {
    message.error('未找到对应模型数据，请先编辑并保存此流程')
    editCategoryId.value = ''
    return
  }
  try {
    const res = await api.put(`/modeler/models/${modelId}/category`, { category: val })
    if (res.code === 200) {
      record.category = val
      message.success(val ? `分类已设为「${val}」` : '已清除分类')
      if (val && !categories.value.includes(val)) {
        categories.value.push(val)
        categories.value.sort()
      }
    } else {
      message.error('更新分类失败')
    }
  } catch (e) {
    message.error('更新分类失败')
  }
  editCategoryId.value = ''
}

// ==================== 分类管理 ====================

async function handleAddCategory(name) {
  const n = (name || '').trim()
  if (!n) {
    message.warning('请输入分类名称')
    return
  }
  categoryAdding.value = true
  try {
    const res = await api.post('/modeler/categories', { name: n })
    if (res.code === 200) {
      categories.value = res.data || []
      newCategoryName.value = ''
      message.success(`分类「${n}」已添加`)
    } else {
      message.error(res.message || '添加失败')
    }
  } catch (e) {
    message.error('添加失败')
  } finally {
    categoryAdding.value = false
  }
}

async function handleDeleteCategory(name) {
  try {
    const res = await api.delete('/modeler/categories', { params: { name } })
    if (res.code === 200) {
      categories.value = res.data || []
      for (const item of items.value) {
        if (item.category === name) item.category = ''
      }
      if (filterCategory.value === name) filterCategory.value = ''
      message.success(`分类「${name}」已删除`)
    } else {
      message.error(res.message || '删除失败')
    }
  } catch (e) {
    message.error('删除失败')
  }
}

// ==================== 已部署流程操作 ====================

async function showHistory(record) {
  historyRecord.value = record
  showHistoryModal.value = true
  historyLoading.value = true
  try {
    const res = await api.get(`/workflow/definition/versions/${record.key}`)
    if (res.code === 200) {
      historyList.value = res.data || []
    } else {
      historyList.value = []
      message.error(res.message || '获取历史版本失败')
    }
  } catch (e) {
    console.error('获取历史版本失败:', e)
    historyList.value = []
    message.error('获取历史版本失败')
  } finally {
    historyLoading.value = false
  }
}

async function viewDiagram(record) {
  showDiagramModal.value = true
  diagramLoading.value = true
  try {
    const res = await api.get(`/workflow/definition/${record.id}/xml`)
    if (res.code !== 200 || !res.data) {
      diagramLoading.value = false
      message.info('无法获取 BPMN XML')
      return
    }
    diagramLoading.value = false
    await nextTick()
    if (!bpmnViewer) {
      bpmnViewer = new BpmnViewer({ container: diagramRef.value })
    }
    await bpmnViewer.importXML(res.data)
    const canvas = bpmnViewer.get('canvas')
    canvas.zoom('fit-viewport')
  } catch (e) {
    console.error('渲染流程图失败:', e)
    diagramLoading.value = false
    message.error('渲染流程图失败')
  }
}

function destroyDiagramViewer() {
  if (bpmnViewer) {
    bpmnViewer.destroy()
    bpmnViewer = null
  }
}

async function editDefinition(record) {
  try {
    const xmlRes = await api.get(`/workflow/definition/${record.id}/xml`)
    if (xmlRes.code !== 200 || !xmlRes.data) {
      message.error('无法获取流程 BPMN XML')
      return
    }
    const upsertRes = await api.post('/modeler/models/upsert', {
      name: record.name,
      key: record.key,
      description: record.description || '',
      category: record.category || '',
      bpmnXml: xmlRes.data
    })
    if (upsertRes.code !== 200 || !upsertRes.data) {
      message.error('同步模型数据失败')
      return
    }
    editingModelId.value = upsertRes.data.id
    mode.value = 'design'
    message.success(`正在编辑流程：${record.name}`)
  } catch (e) {
    console.error('编辑流程失败:', e)
    message.error('编辑流程失败：' + (e.response?.data?.message || e.message || '未知错误'))
  }
}

async function viewXml(record) {
  try {
    const res = await api.get(`/workflow/definition/${record.id}/xml`)
    if (res.code === 200 && res.data) {
      currentXml.value = res.data
      showXmlModal.value = true
    } else {
      message.info('无法获取 BPMN XML')
    }
  } catch {
    message.error('获取 BPMN XML 失败')
  }
}

async function toggleSuspend(record) {
  try {
    const endpoint = record.suspended ? 'activate' : 'suspend'
    const res = await api.post(`/workflow/definition/${record.id}/${endpoint}`)
    if (res.code === 200) {
      message.success(record.suspended ? '流程定义已激活' : '流程定义已挂起')
      await loadAll()
    }
  } catch {
    message.error('操作失败')
  }
}

async function deleteDefinition(record) {
  if (!record.deploymentId) {
    message.error('缺少部署ID，无法删除')
    return
  }
  try {
    const res = await api.delete(`/workflow/definition/${record.deploymentId}`)
    if (res.code === 200) {
      message.success('流程及其所有历史版本已删除')
      await loadAll()
    } else {
      message.error(`删除失败：${res.message || '未知错误'}`)
    }
  } catch (e) {
    console.error('删除流程定义失败:', e)
    const msg = e.response?.data?.message || e.message || '未知错误'
    message.error(`删除失败：${msg}`)
  }
}

async function deployDefinition(record) {
  deployingId.value = record.id
  try {
    const modelRes = await api.get(`/modeler/models/by-key/${record.key}`)
    if (modelRes.code !== 200 || !modelRes.data) {
      message.error('未找到对应的模型数据，请先编辑并保存')
      return
    }
    const model = modelRes.data
    const deployRes = await api.post('/workflow/definition/deploy', {
      name: model.name,
      key: model.key,
      category: model.category || '',
      description: model.description || '',
      bpmnXml: model.bpmnXml
    })
    if (deployRes.code === 200) {
      message.success(`流程"${record.name}"已部署(v${model.version})`)
      await loadAll()
    } else {
      message.error(`部署失败：${deployRes.message || '未知错误'}`)
    }
  } catch (e) {
    console.error('部署失败:', e)
    message.error('部署失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    deployingId.value = ''
  }
}

// ==================== 未部署模型操作 ====================

function editUndeployedModel(record) {
  editingModelId.value = record.id
  mode.value = 'design'
  message.success(`正在编辑流程：${record.name}`)
}

async function deployUndeployedModel(record) {
  deployingId.value = record.id
  try {
    const deployRes = await api.post('/workflow/definition/deploy', {
      name: record.name,
      key: record.key,
      category: record.category || '',
      description: record.description || '',
      bpmnXml: record._bpmnXml || ''
    })
    if (deployRes.code === 200) {
      message.success(`流程"${record.name}"已部署`)
      await loadAll()
    } else {
      message.error(`部署失败：${deployRes.message || '未知错误'}`)
    }
  } catch (e) {
    console.error('部署失败:', e)
    message.error('部署失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    deployingId.value = ''
  }
}

async function deleteUndeployedModel(record) {
  try {
    const res = await api.delete(`/modeler/models/${record.id}`)
    if (res.code === 200) {
      message.success('模型已删除')
      await loadAll()
    } else {
      message.error(`删除失败：${res.message || '未知错误'}`)
    }
  } catch (e) {
    console.error('删除模型失败:', e)
    message.error('删除失败：' + (e.response?.data?.message || e.message || '未知错误'))
  }
}

onMounted(loadAll)
</script>

<style scoped>
.process-design {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}

/* 左右布局 */
.process-layout {
  display: flex;
  gap: 16px;
  min-height: 400px;
}

/* 左侧分类栏 */
.category-sidebar {
  width: 200px;
  min-width: 200px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.category-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #f0f0f0;
}

.category-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
  flex: 1;
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid transparent;
  color: #555;
  font-size: 13px;
}

.category-item:hover {
  background: #f5f5f5;
  color: #1677ff;
}

.category-item.active {
  background: #e6f4ff;
  color: #1677ff;
  border-left-color: #1677ff;
  font-weight: 500;
}

.category-item .category-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-item .category-count {
  font-size: 12px;
  color: #999;
  background: #f5f5f5;
  border-radius: 10px;
  padding: 0 8px;
  line-height: 20px;
  min-width: 28px;
  text-align: center;
}

.category-item.active .category-count {
  background: #bae0ff;
  color: #1677ff;
}

.category-actions {
  padding: 10px 12px;
  border-top: 1px solid #f0f0f0;
}

/* 右侧流程内容 */
.process-content {
  flex: 1;
  min-width: 0;
}

.diagram-viewer-canvas {
  min-height: 500px;
  width: 100%;
}
</style>
