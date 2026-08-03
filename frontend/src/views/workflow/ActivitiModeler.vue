<template>
  <div class="activiti-modeler-wrapper">
    <!-- 工具栏 -->
    <div class="modeler-toolbar">
      <a-space>
        <a-input
          v-model:value="modelName"
          placeholder="流程名称"
          style="width: 200px"
          @change="markDirty"
        />
        <a-input
          v-model:value="modelKey"
          placeholder="流程标识 Key"
          style="width: 180px"
          :disabled="!!modelId"
          @change="markDirty"
        />
        <a-input
          v-model:value="modelDescription"
          placeholder="描述（可选）"
          style="width: 220px"
          @change="markDirty"
        />
        <a-select
          v-model:value="modelCategory"
          placeholder="流程分类"
          style="width: 160px"
          allowClear
          showSearch
          @change="markDirty"
        >
          <a-select-option v-for="c in categories" :key="c" :value="c">{{ c }}</a-select-option>
        </a-select>
        <a-divider type="vertical" />
        <a-button type="primary" :loading="saving" @click="saveModel">
          <template #icon><SaveOutlined /></template>
          保存
        </a-button>
        <a-button v-if="modelId" @click="exportXml">
          <template #icon><ExportOutlined /></template>
          导出 XML
        </a-button>
        <a-tag v-if="dirty" color="orange">未保存</a-tag>
        <a-tag v-if="!dirty && modelId" color="green">已保存</a-tag>
      </a-space>
      <a-button size="small" @click="togglePanel">
        <template #icon><SettingOutlined /></template>
        属性面板
      </a-button>
    </div>

    <!-- 主区域：画布 + 属性面板 -->
    <div class="modeler-main">
      <!-- bpmn-js 画布 -->
      <div class="modeler-canvas">
        <div ref="canvasRef" class="bpmn-canvas"></div>
      </div>

      <!-- 属性面板侧边栏 -->
      <div v-show="panelVisible" class="modeler-properties">
        <div class="properties-header">
          <h4>属性面板</h4>
          <a-button size="small" type="text" @click="togglePanel">
            <CloseOutlined />
          </a-button>
        </div>
        <div ref="propertiesRef" class="properties-content"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import {
  SaveOutlined, ExportOutlined,
  CloseOutlined, SettingOutlined
} from '@ant-design/icons-vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import {
  BpmnPropertiesPanelModule,
  BpmnPropertiesProviderModule
} from 'bpmn-js-properties-panel'

// bpmn-js 样式
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
// 属性面板样式
import '@bpmn-io/properties-panel/dist/assets/properties-panel.css'

// Activiti 7 扩展
import { activitiModdleDescriptor, ActivitiPropertiesProviderModule } from './activiti'
import api from '@/api'

const props = defineProps({
  modelId: { type: String, default: '' },
  initialCategory: { type: String, default: '' }
})

const canvasRef = ref(null)
const propertiesRef = ref(null)
const modelName = ref('')
const modelKey = ref('')
const modelDescription = ref('')
const modelCategory = ref('')
const categories = ref([])
const saving = ref(false)
const dirty = ref(false)
const panelVisible = ref(true)
const internalModelId = ref(props.modelId || '')

let bpmnModeler = null

// 空 BPMN 模板（Activiti 7 命名空间）
const EMPTY_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:activiti="http://activiti.org/bpmn"
  xmlns:xsd="http://www.w3.org/2001/XMLSchema"
  targetNamespace="http://www.activiti.org/test">
  <process id="process" name="新流程" isExecutable="true">
    <startEvent id="start" name="开始" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_process">
    <bpmndi:BPMNPlane id="BPMNPlane_process" bpmnElement="process">
      <bpmndi:BPMNShape id="BPMNShape_start" bpmnElement="start">
        <omgdc:Bounds x="200" y="160" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <omgdc:Bounds x="207" y="200" width="22" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

onMounted(async () => {
  await nextTick()
  initModeler()
})

onBeforeUnmount(() => {
  if (bpmnModeler) {
    bpmnModeler.destroy()
    bpmnModeler = null
  }
})

function togglePanel() {
  panelVisible.value = !panelVisible.value
  // 面板切换后需要重绘画布
  nextTick(() => {
    if (bpmnModeler) {
      bpmnModeler.get('canvas').resized()
    }
  })
}

function initModeler() {
  if (!canvasRef.value) return

  bpmnModeler = new BpmnModeler({
    container: canvasRef.value,
    // 属性面板挂载到侧边栏容器
    propertiesPanel: {
      parent: propertiesRef.value
    },
    additionalModules: [
      BpmnPropertiesPanelModule,
      BpmnPropertiesProviderModule,
      // Activiti 7 自定义属性面板
      ActivitiPropertiesProviderModule
    ],
    // 注册 Activiti 7 Moddle 扩展（支持 activiti:assignee 等属性）
    moddleExtensions: {
      activiti: activitiModdleDescriptor
    }
  })

  // 监听模型变更
  bpmnModeler.on('commandStack.changed', () => {
    markDirty()
  })

  // 加载模型
  loadModel()
}

async function loadModel() {
  try {
    // 拉取分类列表
    const catRes = await api.get('/modeler/categories')
    if (catRes.code === 200) {
      categories.value = catRes.data || []
    }

    if (internalModelId.value) {
      const modelRes = await api.get(`/modeler/models/${internalModelId.value}`)
      if (modelRes.code === 200 && modelRes.data) {
        modelName.value = modelRes.data.name || ''
        modelKey.value = modelRes.data.key || ''
        modelDescription.value = modelRes.data.description || ''
        modelCategory.value = modelRes.data.category || ''
        if (modelRes.data.bpmnXml) {
          await bpmnModeler.importXML(modelRes.data.bpmnXml)
        } else {
          await bpmnModeler.importXML(EMPTY_BPMN)
        }
      }
    } else {
      modelName.value = '新流程'
      modelKey.value = ''
      modelDescription.value = ''
      modelCategory.value = props.initialCategory || ''
      await bpmnModeler.importXML(EMPTY_BPMN)
    }
    dirty.value = false
  } catch (e) {
    console.error('加载模型失败:', e)
    try {
      await bpmnModeler.importXML(EMPTY_BPMN)
    } catch (_) { /* ignore */ }
  }
}

function markDirty() {
  dirty.value = true
}

async function saveModel() {
  if (!modelName.value.trim()) {
    message.warning('请输入流程名称')
    return
  }
  if (!modelKey.value.trim()) {
    message.warning('请输入流程标识 Key')
    return
  }
  saving.value = true
  try {
    const { xml } = await bpmnModeler.saveXML({ format: true })
    // 将工具栏的名称/Key 同步写入 BPMN XML，确保部署后流程定义展示正确
    const finalXml = updateProcessXmlMetadata(xml, modelKey.value, modelName.value, modelDescription.value)

    const isNew = !internalModelId.value
    let saveRes = null

    if (isNew) {
      saveRes = await api.post('/modeler/models', {
        name: modelName.value,
        key: modelKey.value,
        description: modelDescription.value,
        category: modelCategory.value || '',
        bpmnXml: finalXml
      })
      if (saveRes.code === 200 && saveRes.data) {
        internalModelId.value = saveRes.data.id
      } else {
        message.error('创建模型失败')
        return
      }
    } else {
      saveRes = await api.put(`/modeler/models/${internalModelId.value}`, {
        name: modelName.value,
        key: modelKey.value,
        description: modelDescription.value,
        category: modelCategory.value || '',
        bpmnXml: finalXml
      })
      if (saveRes.code !== 200 || !saveRes.data) {
        message.error('更新模型失败')
        return
      }
    }

    if (isNew) {
      // 首次保存自动部署，确保流程出现在清单中
      const deployRes = await api.post('/workflow/definition/deploy', {
        name: modelName.value,
        key: modelKey.value,
        category: modelCategory.value || '',
        description: modelDescription.value,
        bpmnXml: finalXml
      })
      if (deployRes.code !== 200) {
        // 模型已保存但部署失败，不阻塞
        message.warning('模型已保存但部署失败，可在清单中重新部署')
        dirty.value = false
        return
      }
    }

    dirty.value = false
    const ver = saveRes?.data?.version
    const vText = ver != null ? `(v${ver})` : ''
    if (isNew) {
      message.success(`流程已创建${vText}并部署`)
    } else if (ver != null) {
      message.success(`模型已保存(v${ver})，可在清单中点击"部署"发布到引擎`)
    } else {
      message.success('模型已保存，可在清单中点击"部署"发布到引擎')
    }
  } catch (e) {
    console.error('保存失败:', e)
    message.error('保存失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

async function exportXml() {
  try {
    const { xml } = await bpmnModeler.saveXML({ format: true })
    const finalXml = updateProcessXmlMetadata(xml, modelKey.value, modelName.value, modelDescription.value)
    const blob = new Blob([finalXml], { type: 'application/xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${modelKey.value || 'process'}.bpmn20.xml`
    a.click()
    URL.revokeObjectURL(url)
    message.success('BPMN XML 已导出')
  } catch (e) {
    message.error('导出失败')
  }
}

/**
 * 将工具栏中的流程名称、Key、描述 同步写入 BPMN XML。
 * Activiti 引擎从 XML 中读取 process id/name/description，部署参数中的对应值仅影响 deployment 记录。
 */
function updateProcessXmlMetadata(xml, key, name, description) {
  const parser = new DOMParser()
  const doc = parser.parseFromString(xml, 'application/xml')
  // 检查是否有解析错误（如命名空间问题导致的 parseError）
  const errorNode = doc.querySelector('parsererror')
  if (errorNode) {
    console.warn('BPMN XML 解析异常，使用原始 XML:', errorNode.textContent)
    return xml
  }

  const process = doc.querySelector('process')
  if (process) {
    const oldId = process.getAttribute('id')
    if (key && oldId !== key) {
      process.setAttribute('id', key)
      // 同步更新 BPMNDiagram 中的 bpmnElement 引用
      const plane = doc.querySelector('BPMNPlane')
      if (plane && plane.getAttribute('bpmnElement') === oldId) {
        plane.setAttribute('bpmnElement', key)
      }
    }
    if (name) {
      process.setAttribute('name', name)
    }
    // 将描述写入 <documentation> 子元素，Activiti 会将其作为 ProcessDefinition.description
    if (description) {
      let docEl = process.querySelector(':scope > documentation')
      if (!docEl) {
        docEl = doc.createElementNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'documentation')
        // 插到第一个子元素之前
        if (process.firstChild) {
          process.insertBefore(docEl, process.firstChild)
        } else {
          process.appendChild(docEl)
        }
      }
      docEl.textContent = description
    }
  }

  return new XMLSerializer().serializeToString(doc)
}
</script>

<style scoped>
.activiti-modeler-wrapper {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 260px);
  min-height: 500px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  overflow: hidden;
}

.modeler-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.modeler-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.modeler-canvas {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.bpmn-canvas {
  width: 100%;
  height: 100%;
}

.modeler-properties {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 2px solid #e8e8e8;
  overflow: hidden;
}

.properties-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.properties-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.properties-content {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

/* 属性面板内部样式覆盖 */
.properties-content :deep(.bio-properties-panel) {
  --font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.properties-content :deep(.bio-properties-panel-header) {
  font-size: 13px;
}

.properties-content :deep(.bio-properties-panel-group-header-button) {
  font-size: 13px;
}

.properties-content :deep(.bio-properties-panel-entry) {
  font-size: 13px;
}

/* bpmn-js 画布样式覆盖 */
.bpmn-canvas :deep(.bjs-powered-by) {
  display: none;
}

.bpmn-canvas :deep(.djs-palette) {
  left: 10px;
  top: 10px;
}

.bpmn-canvas :deep(.djs-context-pad) {
  background: #fff;
}

.bpmn-canvas :deep(.djs-minimap) {
  right: 10px;
  bottom: 10px;
}
</style>
