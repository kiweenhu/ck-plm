<template>
  <div class="mendix-designer">
    <!-- ========== 顶部工具栏 ========== -->
    <div class="md-toolbar">
      <div class="md-toolbar-left">
        <a-button type="text" size="small" @click="$emit('back')">
          <template #icon><ArrowLeftOutlined /></template>
        </a-button>
        <span class="md-project-name">{{ classificationName || 'IBA布局设计器' }}</span>
        <a-divider type="vertical" />

        <!-- 操作选择 -->
        <a-select
          v-model:value="currentOpCode"
          size="small"
          style="width: 120px"
          @change="onOperationChange"
        >
          <a-select-option v-for="op in operationList" :key="op.code" :value="op.code">
            <span style="display:flex;align-items:center;gap:6px">
              <component :is="opIcon(op.code)" style="font-size:12px" />
              {{ op.name }}
            </span>
          </a-select-option>
        </a-select>
      </div>

      <div class="md-toolbar-center" v-show="designMode==='design'">
        <a-space size="2">
          <a-tooltip title="撤销"><a-button type="text" size="small" :disabled="undoStack.length===0" @click="undo"><UndoOutlined /></a-button></a-tooltip>
          <a-tooltip title="重做"><a-button type="text" size="small" :disabled="redoStack.length===0" @click="redo"><RedoOutlined /></a-button></a-tooltip>
          <a-divider type="vertical" />
          <a-radio-group v-model:value="canvasMode" size="small" button-style="solid">
            <a-radio-button value="desktop"><DesktopOutlined /></a-radio-button>
            <a-radio-button value="tablet"><TabletOutlined /></a-radio-button>
            <a-radio-button value="phone"><MobileOutlined /></a-radio-button>
          </a-radio-group>
        </a-space>
      </div>
      <div class="md-toolbar-center" v-show="designMode==='preview'">
        <a-radio-group v-model:value="canvasMode" size="small" button-style="solid">
          <a-radio-button value="desktop"><DesktopOutlined /></a-radio-button>
          <a-radio-button value="tablet"><TabletOutlined /></a-radio-button>
          <a-radio-button value="phone"><MobileOutlined /></a-radio-button>
        </a-radio-group>
      </div>

      <div class="md-toolbar-right">
        <a-space size="4">
          <template v-if="designMode==='design'">
            <a-button size="small" ghost @click="resetLayout"><template #icon><ClearOutlined /></template>重置</a-button>
            <a-button size="small" ghost @click="importFromAttrs"><template #icon><ImportOutlined /></template>一键导入</a-button>
            <a-button type="primary" size="small" :loading="saving" @click="saveLayout"><template #icon><SaveOutlined /></template>保存</a-button>
          </template>
          <a-button
            size="small"
            :type="designMode === 'preview' ? 'primary' : 'default'"
            @click="designMode = designMode === 'design' ? 'preview' : 'design'"
          >
            <template #icon><EyeOutlined /></template>{{ designMode === 'design' ? '预览' : '设计' }}
          </a-button>
        </a-space>
      </div>
    </div>

    <!-- ========== 主体 ========== -->
    <div class="md-body">
      <!-- 左栏：工具箱 -->
      <div class="md-left-panel">
        <div class="md-panel-tabs">
          <div class="md-panel-tab" :class="{ active: activeToolTab === 'widgets' }" @click="activeToolTab='widgets'">
            <AppstoreOutlined />
            <span>控件</span>
          </div>
          <div class="md-panel-tab" :class="{ active: activeToolTab === 'data' }" @click="activeToolTab='data'">
            <DatabaseOutlined />
            <span>字段</span>
          </div>
          <div class="md-panel-tab" :class="{ active: activeToolTab === 'outline' }" @click="activeToolTab='outline'">
            <BlockOutlined />
            <span>大纲</span>
          </div>
        </div>

        <div class="md-panel-content">
          <!-- 控件面板 -->
          <div v-show="activeToolTab === 'widgets'" class="md-widget-grid">
            <div
              v-for="w in widgets" :key="w.type"
              class="md-widget-item"
              draggable="true"
              @dragstart="onWidgetDragStart($event, w)"
              @dragend="onWidgetDragEnd"
            >
              <component :is="w.icon" style="font-size:18px;color:#1464a5" />
              <span class="md-widget-label">{{ w.label }}</span>
            </div>
          </div>

          <!-- 数据字段面板 -->
          <div v-show="activeToolTab === 'data'">
            <a-input-search v-model:value="attrSearch" placeholder="搜索字段..." size="small" style="margin-bottom:8px" allow-clear />
            <div class="md-data-list">
              <div
                v-for="attr in filteredDataAttrs" :key="attr.fieldName"
                class="md-data-item"
                :class="{ used: isAttrUsed(attr.fieldName) }"
                draggable="true"
                @dragstart="onDataDragStart($event, attr)"
                @dragend="onWidgetDragEnd"
              >
                <FileTextOutlined style="color:#8c8c8c;font-size:12px;flex-shrink:0" />
                <div class="md-data-info">
                  <span class="md-data-name">{{ attr.displayName || attr.fieldName }}</span>
                  <span class="md-data-field">{{ attr.fieldName }} · {{ dataTypeLabel(attr.dataType) }}</span>
                </div>
              </div>
              <a-empty v-if="filteredDataAttrs.length === 0" description="暂无 IBA 字段" :image-style="{ height: '36px' }" />
            </div>
          </div>

          <!-- 大纲面板 -->
          <div v-show="activeToolTab === 'outline'" class="md-outline">
            <div v-for="sec in outlineSections" :key="sec.key" class="md-outline-section">
              <div class="md-outline-section-title" @click="scrollToSection(sec.key)">
                <component :is="sectionIcon(sec.key)" style="font-size:12px" />
                {{ sec.label }}
                <span class="md-outline-count">{{ sec.count }}</span>
              </div>
              <div
                v-for="(item, idx) in sec.items" :key="idx"
                class="md-outline-item"
                :class="{ active: outlineActive[sec.key] === idx }"
                @click="focusOutlineItem(sec.key, idx, item)"
              >
                <span class="md-outline-dot" />
                {{ item.label || item.fieldName || item }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 中栏画布 -->
      <div
        class="md-canvas-wrap"
        :class="'canvas-' + canvasMode"
        ref="canvasWrapRef"
        @keydown="onKeydown"
        tabindex="0"
      >
        <div class="md-canvas-scroll">
          <div class="md-canvas" :class="{ 'md-canvas-preview': designMode === 'preview' }" ref="canvasRef">
            <!-- 页面头部 -->
            <div class="md-page-header">
              <span class="md-page-title">{{ currentOpName }}</span>
              <span class="md-page-subtitle">{{ classificationName || '分类节点' }}</span>
            </div>

            <!-- ===== 表单区 ===== -->
            <div class="md-section" id="canvas-form">
              <div class="md-section-bar">
                <span style="display:flex;align-items:center;gap:6px">
                  <FormOutlined />编辑表单
                </span>
                <a-space size="2" v-if="designMode === 'design'">
                  <span
                    v-if="!editingFormName"
                    class="md-form-name-text"
                    @click="editingFormName = true"
                  >{{ layout.form.name || '编辑表单' }}</span>
                  <a-input
                    v-else
                    ref="formNameInputRef"
                    v-model:value="layout.form.name"
                    size="small"
                    style="width:160px"
                    @blur="finishEditFormName"
                    @pressEnter="finishEditFormName"
                  />
                </a-space>
                <span v-else style="font-size:12px;color:#888">{{ layout.form.name || '编辑表单' }}</span>
              </div>
              <div
                class="md-drop-zone"
                :class="{ 'md-drag-over': dragTargetZone === 'form', 'md-zone-empty': layout.form.fields.length === 0 }"
                @dragover.prevent="onZoneDragOver('form')"
                @dragleave="onZoneDragLeave"
                @drop="onZoneDrop($event, 'form')"
                @click="selectCanvasField('form', -1)"
              >
                <div v-if="layout.form.fields.length === 0" class="md-drop-hint">
                  <InboxOutlined style="font-size:24px;color:#ccc" />
                  <span>拖拽控件或字段到此处</span>
                </div>
                <div v-else class="md-form-preview" @click.stop>
                  <template v-for="(item, idx) in layout.form.fields" :key="idx">
                    <!-- 表单分组 -->
                    <template v-if="item.type === 'group'">
                      <div
                        class="md-form-group"
                        :class="{ 'md-field-selected': isFieldSelected('form', idx) }"
                        :draggable="designMode === 'design'"
                        @dragstart="onFieldDragStart($event, 'form', idx)"
                        @click.stop="selectCanvasField('form', idx)"
                      >
                        <div class="md-form-group-header">
                          <FolderOutlined />
                          <span>{{ item.label || '分组' }}</span>
                          <a-button v-if="designMode === 'design'" type="text" size="small" danger class="md-group-close" @click.stop="removeField('form', idx)"><CloseOutlined /></a-button>
                        </div>
                        <div
                          class="md-form-group-body"
                          :class="{ 'md-zone-empty': !item.children || item.children.length === 0 }"
                          @dragover.prevent="onGroupDragOver(idx)"
                          @drop="onGroupDrop($event, 'form', idx)"
                          @click.stop
                        >
                          <template v-if="item.children && item.children.length > 0">
                            <template v-for="(child, cIdx) in item.children" :key="cIdx">
                              <!-- 分组内的布局行 -->
                              <template v-if="child.type === 'layout-row'">
                                <div
                                  class="md-layout-row"
                                  :class="{ 'md-field-selected': isFieldSelected('form', cIdx, idx) }"
                                  :draggable="designMode === 'design'"
                                  @dragstart="onFieldDragStart($event, 'form', cIdx, idx)"
                                  @click.stop="selectCanvasField('form', cIdx, idx)"
                                >
                                  <div v-if="designMode === 'design'" class="md-layout-row-header">
                                    <ColumnWidthOutlined />
                                    <span class="md-layout-row-title">{{ child.label || `布局行 (${child.columns || 2}列)` }}</span>
                                    <a-button type="text" size="small" danger @click.stop="removeGroupChild(idx, cIdx)"><CloseOutlined /></a-button>
                                  </div>
                                  <div v-else class="md-layout-row-header-preview">
                                    <span class="md-layout-row-title-preview">{{ child.label || '布局行' }}</span>
                                  </div>
                                  <div
                                    class="md-layout-row-body"
                                    :class="{ 'md-zone-empty': !child.children || child.children.length === 0 }"
                                    @dragover.prevent="onGroupDragOver(idx, cIdx)"
                                    @drop="onGroupDrop($event, 'form', idx, cIdx)"
                                    @click.stop
                                  >
                                    <div
                                      v-for="(grand, gIdx) in (child.children || [])" :key="gIdx"
                                      class="md-form-row"
                                      :class="{ 'md-field-selected': isFieldSelected('form', gIdx, idx, cIdx) }"
                                      :style="{ width: layoutColWidth(child.columns) }"
                                      :draggable="designMode === 'design'"
                                      @dragstart="onFieldDragStart($event, 'form', gIdx, idx, cIdx)"
                                      @click.stop="selectCanvasField('form', gIdx, idx, cIdx)"
                                    >
                                      <span class="md-form-label">
                                        <span v-if="grand.required" class="md-required">*</span>
                                        {{ grand.label || grand.fieldName }}
                                      </span>
                                      <div class="md-form-control">
                                        <component :is="getPreviewComp(grand)" v-bind="getPreviewProps(grand)" style="width:100%" />
                                      </div>
                                      <a-button v-if="designMode === 'design'" type="text" size="small" class="md-form-close" @click.stop="removeLayoutRowChild(idx, cIdx, gIdx)"><CloseOutlined /></a-button>
                                    </div>
                                    <div v-if="(!child.children || child.children.length === 0) && designMode === 'design'" class="md-drop-hint" style="padding:8px">
                                      <span>拖拽字段到此处</span>
                                    </div>
                                  </div>
                                </div>
                              </template>
                              <!-- 分组内的普通字段 -->
                              <template v-else>
                                <div
                                  class="md-form-row"
                                  :class="{ 'md-field-selected': isFieldSelected('form', cIdx, idx) }"
                                  :draggable="designMode === 'design'"
                                  @dragstart="onFieldDragStart($event, 'form', cIdx, idx)"
                                  @click.stop="selectCanvasField('form', cIdx, idx)"
                                >
                                  <span class="md-form-label">
                                    <span v-if="child.required" class="md-required">*</span>
                                    {{ child.label || child.fieldName }}
                                  </span>
                                  <div class="md-form-control">
                                    <component :is="getPreviewComp(child)" v-bind="getPreviewProps(child)" style="width:100%" />
                                  </div>
                                  <a-button v-if="designMode === 'design'" type="text" size="small" class="md-form-close" @click.stop="removeGroupChild(idx, cIdx)"><CloseOutlined /></a-button>
                                </div>
                              </template>
                            </template>
                          </template>
                          <div v-if="(!item.children || item.children.length === 0) && designMode === 'design'" class="md-drop-hint" style="padding:8px">
                            <span>拖拽字段到此分组</span>
                          </div>
                        </div>
                      </div>
                    </template>

                    <!-- 表单布局行 -->
                    <template v-else-if="item.type === 'layout-row'">
                      <div
                        class="md-layout-row"
                        :class="{ 'md-field-selected': isFieldSelected('form', idx) }"
                        :draggable="designMode === 'design'"
                        @dragstart="onFieldDragStart($event, 'form', idx)"
                        @click.stop="selectCanvasField('form', idx)"
                      >
                        <div v-if="designMode === 'design'" class="md-layout-row-header">
                          <ColumnWidthOutlined />
                          <span class="md-layout-row-title">{{ item.label || `布局行 (${item.columns || 2}列)` }}</span>
                          <a-button type="text" size="small" danger @click.stop="removeField('form', idx)"><CloseOutlined /></a-button>
                        </div>
                        <div v-else class="md-layout-row-header-preview">
                          <span class="md-layout-row-title-preview">{{ item.label || '布局行' }}</span>
                        </div>
                        <div
                          class="md-layout-row-body"
                          :class="{ 'md-zone-empty': !item.children || item.children.length === 0 }"
                          @dragover.prevent="onGroupDragOver(idx)"
                          @drop="onGroupDrop($event, 'form', idx)"
                          @click.stop
                        >
                          <div
                            v-for="(child, cIdx) in (item.children || [])" :key="cIdx"
                            class="md-form-row"
                            :class="{ 'md-field-selected': isFieldSelected('form', cIdx, idx) }"
                            :style="{ width: layoutColWidth(item.columns) }"
                            :draggable="designMode === 'design'"
                            @dragstart="onFieldDragStart($event, 'form', cIdx, idx)"
                            @click.stop="selectCanvasField('form', cIdx, idx)"
                          >
                            <span class="md-form-label">
                              <span v-if="child.required" class="md-required">*</span>
                              {{ child.label || child.fieldName }}
                            </span>
                            <div class="md-form-control">
                              <component :is="getPreviewComp(child)" v-bind="getPreviewProps(child)" style="width:100%" />
                            </div>
                            <a-button v-if="designMode === 'design'" type="text" size="small" class="md-form-close" @click.stop="removeGroupChild(idx, cIdx)"><CloseOutlined /></a-button>
                          </div>
                          <div v-if="(!item.children || item.children.length === 0) && designMode === 'design'" class="md-drop-hint" style="padding:8px">
                            <span>拖拽字段到此处</span>
                          </div>
                        </div>
                      </div>
                    </template>

                    <!-- 普通表单字段 -->
                    <template v-else>
                      <div
                        class="md-form-row"
                        :class="{ 'md-field-selected': isFieldSelected('form', idx) }"
                        :draggable="designMode === 'design'"
                        @dragstart="onFieldDragStart($event, 'form', idx)"
                        @click.stop="selectCanvasField('form', idx)"
                      >
                        <span class="md-form-label">
                          <span v-if="item.required" class="md-required">*</span>
                          {{ item.label || item.fieldName }}
                        </span>
                        <div class="md-form-control">
                          <component :is="getPreviewComp(item)" v-bind="getPreviewProps(item)" style="width:100%" />
                        </div>
                        <a-button v-if="designMode === 'design'" type="text" size="small" class="md-form-close" @click.stop="removeField('form', idx)"><CloseOutlined /></a-button>
                      </div>
                    </template>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：属性面板 -->
      <div class="md-right-panel" v-if="selectedField">
        <div class="md-prop-title">{{ selectedField.field?.type === 'group' ? '分组属性' : selectedField.field?.type === 'layout-row' ? '布局行属性' : '字段属性' }}</div>
        <a-form layout="vertical" size="small">
          <a-form-item label="标签">
            <a-input v-model:value="selectedField.field.label" @change="markDirty" />
          </a-form-item>
          <a-form-item label="字段名" v-if="selectedField.field.fieldName">
            <a-input v-model:value="selectedField.field.fieldName" @change="markDirty" disabled />
          </a-form-item>

          <!-- 布局行属性 -->
          <template v-if="selectedField.field?.type === 'layout-row'">
            <a-form-item label="列数">
              <a-select v-model:value="selectedField.field.columns" :options="[{value:1,label:'1列'},{value:2,label:'2列'},{value:4,label:'4列'}]" @change="onLayoutColumnsChange" />
            </a-form-item>
          </template>

          <!-- 分组属性 -->
          <template v-if="selectedField.field?.type === 'group'">
            <a-form-item label="折叠">
              <a-switch v-model:checked="selectedField.field.collapsible" @change="markDirty" size="small" />
            </a-form-item>
          </template>

          <!-- 普通字段属性 -->
          <template v-if="!selectedField.field?.type || selectedField.field?.type === 'layout-row' || !selectedField.field?.type">
            <a-form-item label="UI控件" v-if="!selectedField.field?.type">
              <a-select
                v-model:value="selectedField.field.uiComponent"
                :options="widgetOptions"
                @change="onUiComponentChange"
              />
            </a-form-item>
            <a-form-item label="数据类型" v-if="selectedField.field.dataType">
              <a-tag>{{ selectedField.field.dataType }}</a-tag>
            </a-form-item>
            <a-row :gutter="8">
              <a-col :span="8">
                <a-form-item label="必填">
                  <a-switch v-model:checked="selectedField.field.required" @change="markDirty" size="small" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="只读">
                  <a-switch v-model:checked="selectedField.field.readonly" @change="markDirty" size="small" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="隐藏">
                  <a-switch v-model:checked="selectedField.field.hidden" @change="markDirty" size="small" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="占位提示">
              <a-input v-model:value="selectedField.field.placeholder" @change="markDirty" />
            </a-form-item>
            <a-form-item label="默认值">
              <a-input v-model:value="selectedField.field.defaultValue" @change="markDirty" />
            </a-form-item>
            <a-form-item label="标签布局" v-if="!selectedField.field?.type">
              <a-radio-group v-model:value="selectedField.field.labelLayout" @change="markDirty" size="small">
                <a-radio-button value="horizontal">水平</a-radio-button>
                <a-radio-button value="vertical">垂直</a-radio-button>
              </a-radio-group>
            </a-form-item>
          </template>
        </a-form>
        <a-divider style="margin:8px 0" />
        <a-button type="text" danger size="small" block @click="removeSelectedField">
          <template #icon><DeleteOutlined /></template>移除此{{ selectedField.field?.type === 'group' ? '分组' : selectedField.field?.type === 'layout-row' ? '布局行' : '字段' }}
        </a-button>
      </div>
    </div>

    <!-- 新增操作弹窗 -->
    <a-modal v-model:open="showAddOp" title="新增自定义操作" @ok="handleAddOperation" width="360px">
      <a-form layout="vertical">
        <a-form-item label="操作编码"><a-input v-model:value="newOpCode" placeholder="如：approve" /></a-form-item>
        <a-form-item label="操作名称"><a-input v-model:value="newOpName" placeholder="如：审批" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, shallowRef, onMounted, nextTick, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  ArrowLeftOutlined, UndoOutlined, RedoOutlined, DesktopOutlined, TabletOutlined, MobileOutlined,
  ClearOutlined, SaveOutlined, ImportOutlined, EyeOutlined, AppstoreOutlined, DatabaseOutlined,
  BlockOutlined, FileTextOutlined, InboxOutlined, FormOutlined, FolderOutlined, CloseOutlined,
  ColumnWidthOutlined, DeleteOutlined, PlusOutlined, StarOutlined, TableOutlined,
} from '@ant-design/icons-vue'
import { getClassificationIBAs, getClsIbaLayout, saveClsIbaLayout, deleteClsIbaLayout } from '@/api'

const props = defineProps({
  classificationOid: { type: String, required: true },
  classificationName: { type: String, default: '' },
})

const emit = defineEmits(['back'])

// ===== 操作列表 =====
const operationList = ref([
  { code: 'create', name: '新建页', builtin: 'true' },
  { code: 'update', name: '编辑页', builtin: 'true' },
  { code: 'detail', name: '详情页', builtin: 'true' },
])
const currentOpCode = ref('update')
const currentOpName = computed(() => operationList.value.find(o => o.code === currentOpCode.value)?.name || currentOpCode.value)
const showAddOp = ref(false)
const newOpCode = ref('')
const newOpName = ref('')

// ===== 画布模式 =====
const canvasMode = ref('desktop')
const designMode = ref('design')
const canvasWrapRef = ref(null)
const canvasRef = ref(null)
const formNameInputRef = ref(null)

// ===== 工具箱 =====
const activeToolTab = ref('widgets')
const attrSearch = ref('')

// ===== 状态 =====
const saving = ref(false)
const availableAttrs = ref([])
const dragTargetZone = ref(null)
let dragTargetGroupIdx = null
let dragTargetSubGroupIdx = null
const selectedField = ref(null)
const editingFormName = ref(false)

// ===== 撤销/重做 =====
const undoStack = ref([])
const redoStack = ref([])
const maxHistory = 30

// ===== 布局数据 =====
const layout = reactive({
  form: { enabled: true, name: '编辑表单', fields: [] }
})

// ===== 控件定义 =====
const widgets = [
  { type: 'input', label: '文本框', icon: shallowRef(FormOutlined) },
  { type: 'textarea', label: '文本域', icon: shallowRef(FormOutlined) },
  { type: 'input-number', label: '数字框', icon: shallowRef(FormOutlined) },
  { type: 'switch', label: '开关', icon: shallowRef(FormOutlined) },
  { type: 'datepicker', label: '日期选择', icon: shallowRef(FormOutlined) },
  { type: 'select', label: '下拉框', icon: shallowRef(FormOutlined) },
  { type: 'group', label: '分组', icon: shallowRef(FolderOutlined), isContainer: true },
  { type: 'layout-row', label: '布局行', icon: shallowRef(ColumnWidthOutlined), isContainer: true },
]

const widgetOptions = widgets.filter(w => !w.isContainer).map(w => ({ value: w.type, label: w.label }))

// ===== 图标 =====
function opIcon(code) {
  const map = { create: shallowRef(PlusOutlined), update: shallowRef(FormOutlined), detail: shallowRef(FileTextOutlined) }
  return map[code] ? shallowRef(map[code].value) : shallowRef(StarOutlined)
}

function sectionIcon(key) {
  return key === 'form' ? shallowRef(FormOutlined) : shallowRef(StarOutlined)
}

function dataTypeLabel(dt) {
  const map = { STRING: '文', NUMBER: '数', BOOLEAN: '布', DATE: '日', DATETIME: '时', LONGTEXT: '长', SELECT: '选' }
  return map[dt] || dt || '?'
}

// ===== 计算属性 =====
const filteredDataAttrs = computed(() => {
  const kw = attrSearch.value.toLowerCase()
  return availableAttrs.value.filter(a => {
    if (kw && !a.fieldName.toLowerCase().includes(kw) && !(a.displayName || '').toLowerCase().includes(kw)) return false
    return true
  })
})

function isAttrUsed(fieldName) {
  return layout.form.fields.some(item => {
    if (item.type === 'group' || item.type === 'layout-row') {
      return item.children && item.children.some(c => c.fieldName === fieldName)
    }
    return item.fieldName === fieldName
  })
}

function isFieldSelected(section, idx, groupIdx, subGroupIdx) {
  if (!selectedField.value || selectedField.value.section !== section) return false
  if (subGroupIdx !== undefined) {
    return selectedField.value.subGroupIndex === subGroupIdx && selectedField.value.groupIndex === groupIdx && selectedField.value.index === idx
  }
  if (groupIdx !== undefined) {
    return selectedField.value.groupIndex === groupIdx && selectedField.value.index === idx
  }
  return selectedField.value.groupIndex === undefined && selectedField.value.index === idx
}

// ===== 大纲 =====
const outlineActive = ref({})
const outlineSections = computed(() => {
  return [{ key: 'form', label: '编辑表单', count: layout.form.fields.length, items: layout.form.fields }]
})

function scrollToSection(sk) {
  const el = document.getElementById('canvas-' + sk)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function focusOutlineItem(sk, idx, item) {
  outlineActive.value = { [sk]: idx }
  selectCanvasField(sk, idx)
  scrollToSection(sk)
}

// ===== 预览组件 =====
function getPreviewComp(field) {
  const comp = field.uiComponent || 'input'
  if (comp === 'textarea') return 'div'
  if (comp === 'input-number') return 'div'
  if (comp === 'switch') return 'div'
  if (comp === 'datepicker') return 'div'
  if (comp === 'select') return 'div'
  return 'div'
}

function getPreviewProps(field) {
  const comp = field.uiComponent || 'input'
  if (comp === 'textarea') return { class: 'md-preview-textarea-preview' }
  if (comp === 'input-number') return { class: 'md-preview-textarea', style: 'width:120px' }
  if (comp === 'switch') return { class: 'md-preview-textarea', style: 'width:44px;height:22px;border-radius:11px' }
  if (comp === 'datepicker') return { class: 'md-preview-textarea', style: 'width:160px' }
  if (comp === 'select') return { class: 'md-preview-textarea', style: 'width:160px' }
  return { class: 'md-preview-textarea' }
}

// ===== 布局克隆/重置 =====
function cloneLayout() {
  const cloneField = (f) => {
    const cloned = { ...f }
    if (f.children) cloned.children = f.children.map(cloneField)
    return cloned
  }
  return { form: { enabled: layout.form.enabled, name: layout.form.name, fields: layout.form.fields.map(cloneField) } }
}

function applyLayout(snapshot) {
  layout.form = snapshot.form
  if (!layout.form.name) layout.form.name = '编辑表单'
}

function pushUndo() {
  undoStack.value.push(cloneLayout())
  if (undoStack.value.length > maxHistory) undoStack.value.shift()
  redoStack.value = []
}

function markDirty() {
  pushUndo()
}

function undo() {
  if (!undoStack.value.length) return
  redoStack.value.push(cloneLayout())
  applyLayout(undoStack.value.pop())
  selectedField.value = null
}

function redo() {
  if (!redoStack.value.length) return
  undoStack.value.push(cloneLayout())
  applyLayout(redoStack.value.pop())
  selectedField.value = null
}

function resetLayoutState() {
  layout.form = { enabled: true, name: '编辑表单', fields: [] }
  selectedField.value = null
  undoStack.value = []
  redoStack.value = []
}

// ===== 操作切换 =====
async function onOperationChange() {
  resetLayoutState()
  await loadLayoutForOp()
}

async function loadLayoutForOp() {
  if (!props.classificationOid || !currentOpCode.value) return
  try {
    const res = await getClsIbaLayout(props.classificationOid, currentOpCode.value)
    const data = res?.data || res
    if (data?.layoutJson) {
      const json = typeof data.layoutJson === 'string' ? JSON.parse(data.layoutJson) : data.layoutJson
      if (json) {
        layout.form = json.form || layout.form
      }
    }
  } catch { /* 尚未保存 */ }
}

async function handleAddOperation() {
  const code = (newOpCode.value || '').trim()
  const name = (newOpName.value || '').trim()
  if (!code) { message.warning('请输入操作编码'); return }
  if (!name) { message.warning('请输入操作名称'); return }
  if (!/^[a-zA-Z0-9_]+$/.test(code)) { message.warning('操作编码仅允许字母、数字、下划线'); return }
  if (operationList.value.some(o => o.code === code)) { message.warning('操作编码已存在'); return }

  showAddOp.value = false
  newOpCode.value = ''
  newOpName.value = ''

  operationList.value.push({ code, name, builtin: 'false' })
  currentOpCode.value = code
  resetLayoutState()
}

// ===== 拖拽 =====
let widgetDragData = null
let dataDragData = null
let fieldDragData = null

function onWidgetDragStart(e, widget) {
  widgetDragData = { type: 'widget', widget }
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('text/plain', widget.type)
}

function onDataDragStart(e, attr) {
  dataDragData = { type: 'data', attr: { ...attr } }
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('text/plain', attr.fieldName)
}

function onWidgetDragEnd() {
  widgetDragData = null
  dataDragData = null
  dragTargetZone.value = null
  dragTargetGroupIdx = null
  dragTargetSubGroupIdx = null
}

function onFieldDragStart(e, zone, idx, groupIdx, subGroupIdx) {
  fieldDragData = { type: 'field', zone, idx, groupIdx, subGroupIdx }
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', 'field-reorder')
}

function onZoneDragOver(zone) {
  dragTargetZone.value = zone
}

function onZoneDragLeave() {
  dragTargetZone.value = null
  dragTargetGroupIdx = null
  dragTargetSubGroupIdx = null
}

function isContainerWidget(type) {
  return type === 'group' || type === 'layout-row'
}

function buildFieldFromAttr(attr) {
  const uiComp = attr.uiComponent || 'input'
  return {
    fieldName: attr.fieldName,
    label: attr.displayName || attr.fieldName,
    source: attr.source || 'IBA',
    dataType: attr.dataType || 'STRING',
    uiComponent: uiComp,
    required: attr.required || false,
    readonly: false,
    defaultValue: attr.defaultValue || '',
    placeholder: '',
    labelLayout: 'horizontal',
  }
}

function createFieldFromWidget(type) {
  switch (type) {
    case 'group':
      return { type: 'group', label: '分组', collapsible: false, children: [] }
    case 'layout-row':
      return { type: 'layout-row', label: '布局行', columns: 2, children: [] }
    case 'textarea':
      return { fieldName: 'field_' + Date.now(), label: '文本域', source: 'SYSTEM', dataType: 'STRING', uiComponent: 'textarea', required: false, readonly: false, placeholder: '', defaultValue: '', labelLayout: 'horizontal' }
    case 'input-number':
      return { fieldName: 'field_' + Date.now(), label: '数字框', source: 'SYSTEM', dataType: 'NUMBER', uiComponent: 'input-number', required: false, readonly: false, placeholder: '', defaultValue: '', labelLayout: 'horizontal' }
    case 'switch':
      return { fieldName: 'field_' + Date.now(), label: '开关', source: 'SYSTEM', dataType: 'BOOLEAN', uiComponent: 'switch', required: false, readonly: false, labelLayout: 'horizontal' }
    case 'datepicker':
      return { fieldName: 'field_' + Date.now(), label: '日期选择', source: 'SYSTEM', dataType: 'DATE', uiComponent: 'datepicker', required: false, readonly: false, placeholder: '', defaultValue: '', labelLayout: 'horizontal' }
    case 'select':
      return { fieldName: 'field_' + Date.now(), label: '下拉框', source: 'SYSTEM', dataType: 'STRING', uiComponent: 'select', required: false, readonly: false, placeholder: '', defaultValue: '', labelLayout: 'horizontal' }
    default:
      return { fieldName: 'field_' + Date.now(), label: '文本框', source: 'SYSTEM', dataType: 'STRING', uiComponent: 'input', required: false, readonly: false, placeholder: '', defaultValue: '', labelLayout: 'horizontal' }
  }
}

function getZoneList(zone) {
  if (zone === 'form') return layout.form.fields
  return []
}

function zoneLabel(zone) {
  const map = { search: '搜索区', table: '列表区', form: '表单区' }
  return map[zone] || zone
}

function layoutColWidth(columns) {
  const map = { 1: '100%', 2: '50%', 4: '25%' }
  return map[columns] || '50%'
}

function onZoneDrop(e, zone) {
  dragTargetZone.value = null
  pushUndo()

  if (dataDragData) {
    const field = buildFieldFromAttr(dataDragData.attr)
    const list = getZoneList(zone)
    if (list.some(f => f.fieldName === field.fieldName)) {
      message.warning(`属性 "${field.fieldName}" 已在 ${zoneLabel(zone)} 中`)
      dataDragData = null
      return
    }
    list.push(field)
    const idx = list.length - 1
    selectedField.value = { section: zone, field: list[idx], index: idx }
    dataDragData = null
    return
  }

  if (widgetDragData) {
    const list = getZoneList(zone)
    const w = widgetDragData.widget
    if (isContainerWidget(w.type)) {
      if (zone !== 'form') {
        message.warning(`${w.label}只能用在表单区域`)
        widgetDragData = null
        return
      }
      const group = createFieldFromWidget(w.type)
      list.push(group)
      selectedField.value = { section: zone, field: group, index: list.length - 1 }
      widgetDragData = null
      return
    }
    const field = createFieldFromWidget(w.type)
    list.push(field)
    const idx = list.length - 1
    selectedField.value = { section: zone, field: list[idx], index: idx }
    widgetDragData = null
    return
  }

  if (fieldDragData) {
    const { zone: srcZone, idx: srcIdx, groupIdx: srcGroupIdx } = fieldDragData
    if (srcGroupIdx !== undefined && zone === 'form') {
      const group = layout.form.fields[srcGroupIdx]
      if (group && group.children && srcIdx < group.children.length) {
        const [moved] = group.children.splice(srcIdx, 1)
        const dstList = getZoneList(zone)
        dstList.push(moved)
        selectedField.value = { section: zone, field: moved, index: dstList.length - 1 }
      }
      fieldDragData = null
      return
    }
    if (srcZone === zone) {
      const list = getZoneList(zone)
      if (srcIdx < 0 || srcIdx >= list.length) { fieldDragData = null; return }
      const [moved] = list.splice(srcIdx, 1)
      let items
      if (zone === 'form') {
        const preview = e.currentTarget.querySelector('.md-form-preview')
        items = preview ? preview.children : []
      }
      let insertIdx = list.length
      const dropY = e.clientY
      items.forEach((item, i) => {
        const rect = item.getBoundingClientRect()
        if (dropY < rect.top + rect.height / 2) {
          insertIdx = Math.min(insertIdx, i)
        }
      })
      list.splice(Math.min(insertIdx, list.length), 0, moved)
      selectedField.value = { section: zone, field: moved, index: list.indexOf(moved) }
    } else {
      const srcList = getZoneList(srcZone)
      const dstList = getZoneList(zone)
      if (srcIdx < 0 || srcIdx >= srcList.length) { fieldDragData = null; return }
      const [moved] = srcList.splice(srcIdx, 1)
      dstList.push(moved)
      selectedField.value = { section: zone, field: moved, index: dstList.length - 1 }
    }
    fieldDragData = null
    return
  }
  fieldDragData = null
}

function onGroupDragOver(groupIdx, subGroupIdx) {
  dragTargetGroupIdx = groupIdx
  dragTargetSubGroupIdx = subGroupIdx !== undefined ? subGroupIdx : null
}

function onGroupDrop(e, zone, groupIdx, subGroupIdx) {
  dragTargetZone.value = null
  dragTargetSubGroupIdx = null
  pushUndo()

  let container = layout.form.fields[groupIdx]
  if (subGroupIdx !== undefined) {
    if (!container || container.type !== 'group' || !container.children) return
    container = container.children[subGroupIdx]
    if (!container || container.type !== 'layout-row') return
  }
  if (!container || (container.type !== 'group' && container.type !== 'layout-row')) return

  if (dataDragData) {
    const field = buildFieldFromAttr(dataDragData.attr)
    if (layout.form.fields.some(item =>
      (item.type !== 'group' && item.type !== 'layout-row') ? item.fieldName === field.fieldName
        : item.children && item.children.some(c => c.fieldName === field.fieldName)
    )) {
      message.warning(`属性 "${field.fieldName}" 已在表单中`)
      dataDragData = null
      return
    }
    container.children.push(field)
    selectedField.value = {
      section: zone, field, index: container.children.length - 1,
      groupIndex: groupIdx, subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
    }
    dataDragData = null
    return
  }

  if (widgetDragData) {
    const w = widgetDragData.widget
    const destType = container.type
    if (isContainerWidget(w.type)) {
      const isDroppingLayoutRow = w.type === 'layout-row'
      const isDestGroup = destType === 'group'
      if (!isDroppingLayoutRow || !isDestGroup) {
        message.warning(destType === 'layout-row' ? '布局行内不能放置其他容器' : '容器不能嵌套')
        widgetDragData = null
        return
      }
    }
    const field = createFieldFromWidget(w.type)
    container.children.push(field)
    selectedField.value = {
      section: zone, field, index: container.children.length - 1,
      groupIndex: groupIdx, subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
    }
    widgetDragData = null
    return
  }

  if (fieldDragData) {
    const { idx: srcIdx, groupIdx: srcGroupIdx, subGroupIdx: srcSubGroupIdx } = fieldDragData
    if (srcGroupIdx !== undefined) {
      if (srcGroupIdx === groupIdx && (srcSubGroupIdx ?? undefined) === (subGroupIdx ?? undefined)) {
        if (srcIdx < 0 || srcIdx >= (container.children?.length || 0)) { fieldDragData = null; return }
        const [moved] = container.children.splice(srcIdx, 1)
        let childNodes
        if (subGroupIdx !== undefined) {
          childNodes = Array.from(e.currentTarget.children).filter(el =>
            el.classList.contains('md-form-row') || el.classList.contains('md-layout-row')
          )
        } else {
          childNodes = Array.from(e.currentTarget.children).filter(el =>
            el.classList.contains('md-form-row') || el.classList.contains('md-layout-row')
          )
        }
        let insertIdx = container.children.length
        const dropY = e.clientY
        childNodes.forEach((item, i) => {
          const rect = item.getBoundingClientRect()
          if (dropY < rect.top + rect.height / 2) { insertIdx = Math.min(insertIdx, i) }
        })
        container.children.splice(Math.min(insertIdx, container.children.length), 0, moved)
        selectedField.value = {
          section: zone, field: moved, index: container.children.indexOf(moved),
          groupIndex: groupIdx, subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
        }
        fieldDragData = null
        return
      }
      let srcContainer
      if (srcSubGroupIdx !== undefined) {
        srcContainer = layout.form.fields[srcGroupIdx]?.children?.[srcSubGroupIdx]
      } else {
        srcContainer = layout.form.fields[srcGroupIdx]
      }
      if (srcContainer && srcContainer.children && srcIdx < srcContainer.children.length) {
        const item = srcContainer.children[srcIdx]
        if (item.type === 'group') { message.warning('不能将分组容器拖入另一个容器'); fieldDragData = null; return }
        if (item.type === 'layout-row' && container.type !== 'group') { message.warning('布局行只能放入分组容器'); fieldDragData = null; return }
        const [moved] = srcContainer.children.splice(srcIdx, 1)
        container.children.push(moved)
        selectedField.value = {
          section: zone, field: moved, index: container.children.length - 1,
          groupIndex: groupIdx, subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
        }
      }
    } else {
      const dstList = getZoneList(zone)
      if (srcIdx >= 0 && srcIdx < dstList.length) {
        const item = dstList[srcIdx]
        if (item.type === 'group') { message.warning('不能将分组拖入另一个容器'); fieldDragData = null; return }
        if (item.type === 'layout-row' && container.type !== 'group') { message.warning('布局行只能放入分组容器'); fieldDragData = null; return }
        const [moved] = dstList.splice(srcIdx, 1)
        container.children.push(moved)
        selectedField.value = {
          section: zone, field: moved, index: container.children.length - 1,
          groupIndex: groupIdx, subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
        }
      }
    }
    fieldDragData = null
    return
  }
  fieldDragData = null
}

// ===== 画布交互 =====
function selectCanvasField(section, idx, groupIdx, subGroupIdx) {
  const list = getZoneList(section)
  if (idx >= 0) {
    let field
    if (subGroupIdx !== undefined) {
      field = list[groupIdx]?.children?.[subGroupIdx]?.children?.[idx]
    } else if (groupIdx !== undefined) {
      field = list[groupIdx]?.children?.[idx]
    } else {
      field = list[idx]
    }
    if (field) selectedField.value = { section, field, index: idx, groupIndex: groupIdx, subGroupIndex: subGroupIdx }
  }
}

function removeField(section, idx) {
  pushUndo()
  const list = getZoneList(section)
  const removed = list[idx]
  list.splice(idx, 1)
  if (selectedField.value && selectedField.value.field === removed) {
    selectedField.value = null
  }
}

function removeGroupChild(groupIdx, childIdx) {
  pushUndo()
  const container = layout.form.fields[groupIdx]
  if (!container || (container.type !== 'group' && container.type !== 'layout-row')) return
  const removed = container.children[childIdx]
  container.children.splice(childIdx, 1)
  if (selectedField.value && selectedField.value.field === removed) {
    selectedField.value = null
  }
}

function removeLayoutRowChild(groupIdx, layoutRowIdx, childIdx) {
  pushUndo()
  const group = layout.form.fields[groupIdx]
  if (!group || group.type !== 'group' || !group.children) return
  const layoutRow = group.children[layoutRowIdx]
  if (!layoutRow || layoutRow.type !== 'layout-row' || !layoutRow.children) return
  const removed = layoutRow.children[childIdx]
  layoutRow.children.splice(childIdx, 1)
  if (selectedField.value && selectedField.value.field === removed) {
    selectedField.value = null
  }
}

function onLayoutColumnsChange() {
  markDirty()
}

function removeSelectedField() {
  if (!selectedField.value) return
  pushUndo()
  const { section, index, groupIndex } = selectedField.value
  if (groupIndex !== undefined) {
    const group = layout.form.fields[groupIndex]
    if (group) { group.children.splice(index, 1) }
  } else {
    const list = getZoneList(section)
    list.splice(index, 1)
  }
  selectedField.value = null
}

// ===== 属性面板 =====
function onUiComponentChange(newComponent) {
  if (!selectedField.value?.field) return
  markDirty()
}

// ===== 加载与保存 =====
async function loadAttrs() {
  if (!props.classificationOid) return
  try {
    const res = await getClassificationIBAs(props.classificationOid)
    const data = res?.data || res || []
    const list = Array.isArray(data) ? data : []
    availableAttrs.value = list.map(m => ({
      fieldName: (m.ibaCode || m.code || '').toLowerCase(),
      displayName: m.ibaName || m.name || m.ibaCode || m.code || '',
      source: 'IBA',
      dataType: (m.ibaDataType || m.dataType || 'STRING').toUpperCase(),
      required: m.required || false,
      defaultValue: m.defaultValue || '',
      uiComponent: resolveUiComponent(m.ibaDataType || m.dataType || 'STRING'),
    })).filter(a => a.fieldName)
  } catch {
    availableAttrs.value = []
  }
}

function resolveUiComponent(dataType) {
  if (!dataType) return 'input'
  switch (dataType.toUpperCase()) {
    case 'TEXT': case 'STRING': return 'input'
    case 'BOOLEAN': return 'switch'
    case 'INTEGER': case 'FLOAT': return 'input-number'
    case 'DATE': case 'DATETIME': return 'datepicker'
    case 'ENUM': return 'select'
    default: return 'input'
  }
}

async function saveLayout() {
  saving.value = true
  try {
    const op = operationList.value.find(o => o.code === currentOpCode.value)
    const payload = {
      clsOid: props.classificationOid,
      operationCode: currentOpCode.value,
      operationName: op ? op.name : currentOpCode.value,
      layoutJson: JSON.stringify({ form: layout.form })
    }
    const res = await saveClsIbaLayout(payload)
    if (res?.code === 200 || !res?.code) {
      message.success('布局已保存')
    } else {
      message.error(res?.message || '保存失败')
    }
  } catch { message.error('保存布局失败') }
  finally { saving.value = false }
}

function importFromAttrs() {
  pushUndo()
  const unused = availableAttrs.value.filter(a => !isAttrUsed(a.fieldName))
  for (const attr of unused) {
    const field = buildFieldFromAttr(attr)
    if (!layout.form.fields.some(f => f.fieldName === field.fieldName)) {
      layout.form.fields.push({ ...field, required: !!attr.required })
    }
  }
  message.success(`已导入 ${unused.length} 个属性`)
}

function resetLayout() {
  Modal.confirm({
    title: '确认重置',
    content: '将清除当前所有布局配置，确定重置吗？',
    okText: '确定', cancelText: '取消',
    onOk: () => {
      resetLayoutState()
      message.info('布局已重置')
    }
  })
}

function finishEditFormName() {
  editingFormName.value = false
  markDirty()
}

// ===== 键盘快捷键 =====
function onKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
    e.preventDefault()
    e.shiftKey ? redo() : undo()
  }
}

onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  loadAttrs()
  loadLayoutForOp()
})
</script>

<style scoped>
/* ===== 整体布局 ===== */
.mendix-designer {
  display: flex; flex-direction: column; height: 100vh;
  background: #e8ecf1; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* ===== 工具栏 ===== */
.md-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  height: 44px; padding: 0 12px; background: #1464a5;
  color: #fff; flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}
.md-toolbar-left, .md-toolbar-center, .md-toolbar-right {
  display: flex; align-items: center; gap: 6px;
}
.md-toolbar .ant-btn { color: rgba(255,255,255,0.85) !important; }
.md-toolbar .ant-btn:hover { color: #fff !important; background: rgba(255,255,255,0.15) !important; }
.md-toolbar .ant-btn-text[disabled] { color: rgba(255,255,255,0.35) !important; }
.md-toolbar .ant-divider-vertical { border-color: rgba(255,255,255,0.2); }
.md-toolbar .ant-radio-group { background: rgba(255,255,255,0.1); border-radius: 4px; }
.md-toolbar .ant-radio-button-wrapper {
  color: rgba(255,255,255,0.7); background: transparent; border: none;
  padding: 0 8px; line-height: 28px; height: 28px;
}
.md-toolbar .ant-radio-button-wrapper-checked {
  color: #1464a5; background: #fff; border-radius: 3px;
}
.md-toolbar :deep(.ant-select) { color: #333; }
.md-project-name { font-size: 14px; font-weight: 600; }

/* ===== 主体 ===== */
.md-body { display: flex; flex: 1; overflow: hidden; }

/* ===== 左栏 ===== */
.md-left-panel {
  width: 240px; background: #f7f8fa; border-right: 1px solid #d5dce6;
  display: flex; flex-direction: column; flex-shrink: 0;
}
.md-panel-tabs {
  display: flex; background: #e8ecf1; border-bottom: 1px solid #d5dce6;
}
.md-panel-tab {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 8px 4px; cursor: pointer; font-size: 10px; color: #666;
  transition: all 0.15s; border-bottom: 2px solid transparent;
}
.md-panel-tab:hover { color: #1464a5; background: rgba(20,100,165,0.05); }
.md-panel-tab.active { color: #1464a5; border-bottom-color: #1464a5; background: #fff; }
.md-panel-tab :deep(.anticon) { font-size: 14px; }
.md-panel-content { flex: 1; overflow-y: auto; padding: 8px; }

/* 控件网格 */
.md-widget-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 4px; }
.md-widget-item {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 10px 4px; border: 1px solid #e8ecf1; border-radius: 4px;
  cursor: grab; transition: all 0.15s; background: #fff; font-size: 11px; color: #555;
}
.md-widget-item:hover { border-color: #b3d4f0; background: #e6f4ff; }
.md-widget-item:active { cursor: grabbing; }
.md-widget-label { font-size: 10px; white-space: nowrap; }

/* 数据列表 */
.md-data-list { display: flex; flex-direction: column; gap: 2px; }
.md-data-item {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 8px; border: 1px solid transparent; border-radius: 4px;
  cursor: grab; transition: all 0.15s; font-size: 12px;
}
.md-data-item:hover { background: #e6f0fa; border-color: #b3d4f0; }
.md-data-item:active { cursor: grabbing; }
.md-data-item.used { opacity: 0.4; pointer-events: none; }
.md-data-info { flex: 1; display: flex; flex-direction: column; gap: 1px; }
.md-data-name { font-weight: 500; color: #333; font-size: 12px; }
.md-data-field { font-size: 10px; color: #999; }

/* 大纲 */
.md-outline { padding: 4px; }
.md-outline-section { margin-bottom: 4px; }
.md-outline-section-title {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 8px; font-size: 11px; font-weight: 600; color: #555;
  cursor: pointer; border-radius: 4px;
}
.md-outline-section-title:hover { background: #e8ecf1; }
.md-outline-count { margin-left: auto; font-size: 10px; color: #999; }
.md-outline-item {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 8px 4px 24px; font-size: 11px; color: #666;
  cursor: pointer; border-radius: 3px;
}
.md-outline-item:hover { background: #e6f0fa; color: #1464a5; }
.md-outline-item.active { background: #d0e4f7; color: #1464a5; font-weight: 500; }
.md-outline-dot {
  width: 6px; height: 6px; border-radius: 50%; background: #c0c8d4; flex-shrink: 0;
}

/* ===== 中栏画布 ===== */
.md-canvas-wrap {
  flex: 1; display: flex; flex-direction: column;
  background: #d0d7e0; padding: 20px 0;
  transition: all 0.3s; overflow: hidden; min-height: 0;
}
.md-canvas-wrap.canvas-desktop .md-canvas { width: 100%; max-width: 960px; }
.md-canvas-wrap.canvas-tablet .md-canvas { width: 100%; max-width: 768px; }
.md-canvas-wrap.canvas-phone .md-canvas { width: 100%; max-width: 375px; }
.md-canvas-scroll { flex: 1; overflow-y: auto; display: flex; justify-content: center; padding: 0 16px; }
.md-canvas {
  background: #fff; border-radius: 6px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  min-height: 100%; padding: 20px;
}

/* 页面头部 */
.md-page-header {
  display: flex; align-items: baseline; gap: 12px;
  padding-bottom: 12px; margin-bottom: 16px; border-bottom: 1px solid #f0f0f0;
}
.md-page-title { font-size: 18px; font-weight: 700; color: #1a1a1a; }
.md-page-subtitle { font-size: 12px; color: #999; }

/* 分区 */
.md-section { margin-bottom: 16px; }
.md-section-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; background: #f7f8fa; border: 1px solid #e8ecf1;
  border-radius: 4px 4px 0 0; font-size: 12px; font-weight: 600; color: #555;
  justify-content: space-between;
}
.md-section-bar :deep(.anticon) { color: #1464a5; }

.md-form-name-text {
  cursor: pointer; padding: 2px 6px; border-radius: 3px;
  border: 1px solid transparent; transition: all 0.2s;
  flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.md-form-name-text:hover { border-color: #d5dce6; background: #fff; }

/* 放置区域 */
.md-drop-zone {
  min-height: 52px; padding: 8px; border: 1px dashed #d5dce6; border-top: none;
  border-radius: 0 0 4px 4px; transition: all 0.2s;
}
.md-drop-zone.md-drag-over {
  background: #e6f4ff; border-color: #1464a5; border-style: solid;
  box-shadow: inset 0 0 0 2px rgba(20,100,165,0.15);
}
.md-drop-zone.md-zone-empty { display: flex; align-items: center; justify-content: center; }
.md-drop-hint {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 16px; color: #bbb; font-size: 12px;
}

/* 表单预览 */
.md-form-preview { display: flex; flex-wrap: wrap; gap: 0; }
.md-form-row {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 6px 8px; cursor: pointer; border-radius: 4px;
  transition: all 0.15s; box-sizing: border-box;
}
.md-form-row:hover { background: #f0f5ff; }
.md-form-row.md-field-selected { background: #bae0ff; outline: 2px solid #1464a5; outline-offset: -2px; }
.md-form-label {
  width: 80px; flex-shrink: 0; text-align: right;
  font-size: 12px; color: #555; padding-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.md-required { color: #ff4d4f; margin-right: 2px; }
.md-form-control {
  flex: 1; display: flex; align-items: center; gap: 4px; min-width: 0;
}
.md-form-close { visibility: hidden; opacity: 0; padding: 0 !important; min-width: auto !important; height: auto !important; }
.md-form-row:hover .md-form-close { visibility: visible; opacity: 1; }
.md-preview-textarea { width: 100%; height: 28px; border: 1px solid #d9d9d9; border-radius: 4px; background: #f5f5f5; }
.md-preview-textarea-preview { width: 100%; height: 60px; border: 1px solid #d9d9d9; border-radius: 4px; background: #fff; }

/* ===== 预览模式 ===== */
.md-preview-bar {
  padding: 4px 0; font-size: 12px; font-weight: 600; color: #555;
  display: flex; align-items: center; gap: 6px;
  border-bottom: 1px solid #f0f0f0; margin-bottom: 8px;
}
.md-canvas-wrap:has(.md-canvas-preview) .md-canvas { max-width: 100% !important; }
.md-canvas-preview .md-form-row { cursor: default; }
.md-canvas-preview .md-form-row:hover { background: transparent; }
.md-canvas-preview .md-field-chip { cursor: default; }
.md-canvas-preview .md-field-chip:hover { border-color: #d6e4ff; background: #f0f5ff; }
.md-canvas-preview .md-table-th { cursor: default; }
.md-canvas-preview .md-table-th:hover { background: transparent; }

/* ===== 布局行容器 ===== */
.md-layout-row {
  width: 100% !important;
  margin: 6px 0;
  border: 1px dashed #d9b3ff;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.15s;
}
.md-layout-row.md-field-selected {
  border-color: #722ed1;
  border-style: solid;
  box-shadow: 0 0 0 2px rgba(114,46,209,0.15);
}
.md-layout-row-header {
  display: flex; align-items: center; gap: 8px;
  padding: 5px 10px;
  background: #f9f0ff;
  border-bottom: 1px solid #efdbff;
  font-size: 11px; color: #722ed1; user-select: none;
}
.md-layout-row-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.md-layout-row-header-preview {
  display: flex; align-items: center; gap: 8px;
  padding: 3px 10px; background: #f9f9f9; border-bottom: 1px solid #eee;
  font-size: 11px;
}
.md-layout-row-title-preview { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #888; }
.md-layout-row-body {
  display: flex; flex-wrap: wrap;
  padding: 4px 8px; min-height: 40px; background: #fafbfc;
}
.md-layout-row-body.md-zone-empty {
  border: 1px dashed #d9d9d9; border-radius: 4px;
  margin: 4px 8px; display: flex; align-items: center; justify-content: center;
}
.md-layout-row-body .md-form-row { flex-shrink: 0; }
.md-canvas-preview .md-layout-row { border-color: #e8e8e8; cursor: default; }
.md-canvas-preview .md-layout-row:hover { background: transparent; }

/* 表单分组 */
.md-form-group {
  width: 100% !important; margin: 4px 0;
  border: 1px dashed #b3d4f0; border-radius: 6px; overflow: hidden;
  cursor: pointer; transition: all 0.15s;
}
.md-form-group.md-field-selected {
  border-color: #1464a5; border-style: solid;
  box-shadow: 0 0 0 2px rgba(20,100,165,0.15);
}
.md-form-group-header {
  display: flex; align-items: center; gap: 8px;
  padding: 5px 10px;
  background: #e6f4ff; border-bottom: 1px solid #d0e4f7;
  font-size: 11px; color: #1464a5; user-select: none;
}
.md-group-close { visibility: hidden; opacity: 0; padding: 0 !important; min-width: auto !important; height: auto !important; }
.md-form-group:hover .md-group-close { visibility: visible; opacity: 1; }
.md-form-group-body { padding: 4px 8px; min-height: 40px; background: #fafbfc; }
.md-form-group-body.md-zone-empty {
  border: 1px dashed #d9d9d9; border-radius: 4px;
  margin: 4px 8px; display: flex; align-items: center; justify-content: center;
}
.md-canvas-preview .md-form-group { border-color: #e8e8e8; cursor: default; }
.md-canvas-preview .md-form-group:hover { background: transparent; }

/* ===== 右栏属性面板 ===== */
.md-right-panel {
  width: 260px; background: #fff; border-left: 1px solid #d5dce6;
  padding: 12px; overflow-y: auto; flex-shrink: 0;
}
.md-prop-title {
  font-size: 13px; font-weight: 600; color: #333;
  padding-bottom: 8px; margin-bottom: 8px; border-bottom: 1px solid #f0f0f0;
}
</style>
