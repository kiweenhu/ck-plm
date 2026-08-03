<template>
  <div class="mendix-designer">
    <!-- ========== 顶部工具栏 ========== -->
    <div class="md-toolbar">
      <div class="md-toolbar-left">
        <a-button type="text" size="small" @click="goBack">
          <template #icon><ArrowLeftOutlined /></template>
        </a-button>
        <span class="md-project-name">{{ entityName || '页面设计器' }}</span>
        <a-divider type="vertical" />

        <!-- 操作选择 -->
        <a-select
          v-model:value="currentOpCode"
          size="small"
          style="width: 160px"
          @change="onOperationChange"
        >
          <a-select-option v-for="op in operationList" :key="op.code" :value="op.code">
            <span style="display:flex;align-items:center;gap:6px">
              <component :is="opIcon(op.code)" style="font-size:12px" />
              {{ op.name }}
              <a-tag v-if="op.owner === 'platform'" size="small" color="purple" style="font-size:10px;line-height:16px;padding:0 4px">平台</a-tag>
              <a-tag v-else-if="op.owner === 'tenant'" size="small" color="green" style="font-size:10px;line-height:16px;padding:0 4px">本租户</a-tag>
            </span>
          </a-select-option>
        </a-select>
        <a-tooltip title="新增自定义操作">
          <a-button type="text" size="small" @click="showAddOp=true"><PlusOutlined /></a-button>
        </a-tooltip>
        <a-tooltip v-if="!isBuiltinOp" title="删除当前操作">
          <a-button type="text" size="small" danger @click="handleDeleteOperation"><DeleteOutlined /></a-button>
        </a-tooltip>
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
            <a-button v-if="canClone" size="small" ghost :loading="cloning" @click="handleClonePlatformLayout">
              <template #icon><CopyOutlined /></template>克隆平台布局
            </a-button>
            <a-button type="primary" size="small" :loading="saving" @click="saveLayout">
              <template #icon><SaveOutlined /></template>
              保存
            </a-button>
          </template>
          <a-divider type="vertical" v-if="designMode==='design'" />
          <a-button
            size="small"
            ghost
            @click="designMode = designMode==='design' ? 'preview' : 'design'"
          >
            <template #icon><component :is="designMode==='design' ? EyeOutlined : EditOutlined" /></template>
            {{ designMode==='design' ? '预览' : '退出预览' }}
          </a-button>
        </a-space>
      </div>
    </div>

    <!-- ========== 主体三栏 ========== -->
    <div class="md-body">
      <!-- 左栏：工具箱 (仅设计模式) -->
      <div class="md-left-panel" v-show="designMode==='design'">
        <div class="md-panel-tabs">
          <div
            v-for="tab in toolTabs"
            :key="tab.key"
            class="md-panel-tab"
            :class="{ active: activeToolTab===tab.key }"
            @click="activeToolTab=tab.key"
          >
            <component :is="tab.icon" />
            <span>{{ tab.label }}</span>
          </div>
        </div>

        <!-- 控件工具箱 -->
        <div class="md-panel-content" v-show="activeToolTab==='widgets'">
          <WidgetToolbox
            @dragstart="onWidgetDragStart"
            @dragend="onWidgetDragEnd"
          />
        </div>

        <!-- 属性工具箱（数据源） -->
        <div class="md-panel-content" v-show="activeToolTab==='data'">
          <a-input-search
            v-model:value="attrSearch"
            size="small"
            placeholder="搜索属性..."
            style="margin-bottom:8px"
          />
          <div class="md-data-list">
            <div
              v-for="attr in filteredDataAttrs"
              :key="attr.fieldName"
              class="md-data-item"
              :class="{ used: isAttrUsed(attr.fieldName) }"
              draggable="true"
              @dragstart="onDataDragStart($event, attr)"
              @dragend="onWidgetDragEnd"
            >
              <BarsOutlined style="font-size:11px;color:#999" />
              <div class="md-data-info">
                <span class="md-data-name">{{ attr.displayName || attr.fieldName }}</span>
                <span class="md-data-field">{{ attr.fieldName }}</span>
              </div>
              <a-tag size="small" style="font-size:9px;padding:0 3px;line-height:16px">
                {{ dataTypeLabel(attr.dataType) }}
              </a-tag>
            </div>
          </div>
          <a-empty v-if="!filteredDataAttrs.length" :image="simpleImage" description="暂无属性" style="margin-top:40px" />
        </div>

        <!-- 大纲 -->
        <div class="md-panel-content md-outline" v-show="activeToolTab==='outline'">
          <div class="md-outline-tree">
            <div
              v-for="(section, sk) in outlineSections"
              :key="sk"
              class="md-outline-section"
            >
              <div class="md-outline-section-title" @click="scrollToSection(sk)">
                <FolderOutlined style="font-size:11px" />
                {{ section.label }}
                <span class="md-outline-count">{{ section.count }}</span>
              </div>
              <template v-for="(item, idx) in section.items" :key="idx">
              <div
                v-if="!item.hidden"
                class="md-outline-item"
                :class="{ active: outlineActive?.[sk] === idx }"
                @click="focusOutlineItem(sk, idx, item)"
              >
                <span class="md-outline-dot" />
                {{ item.label || item.fieldName }}
              </div>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 中栏：WYSIWYG 画布 -->
      <div
        class="md-canvas-wrap"
        :class="'canvas-' + canvasMode"
        ref="canvasWrapRef"
      >
        <div class="md-canvas-scroll" ref="canvasScrollRef">
          <div class="md-canvas" :class="{ 'md-canvas-preview': designMode==='preview' }" ref="canvasRef">
            <!-- 页面头部 -->
            <div class="md-page-header">
              <span class="md-page-title">{{ currentOpInfo?.name || '页面标题' }}</span>
              <span class="md-page-subtitle">{{ entityName }}</span>
            </div>

            <!-- 搜索区（仅列表页） -->
            <div class="md-section" id="canvas-search" v-if="isListOp" v-show="layout.search.enabled || designMode==='design'">
              <div class="md-section-bar md-design-only" v-show="designMode==='design'">
                <SearchOutlined /> 搜索区域
                <a-switch v-model:checked="layout.search.enabled" size="small" />
              </div>
              <div class="md-section-bar md-preview-bar" v-show="designMode==='preview' && layout.search.enabled">
                <SearchOutlined /> 搜索条件
              </div>
              <div
                class="md-drop-zone md-search-zone"
                :class="designMode==='design' ? { 'md-drag-over': dragTargetZone==='search', 'md-zone-empty': !layout.search.fields.length } : ''"
                @dragover.prevent="designMode==='design' && onZoneDragOver('search')"
                @dragleave="designMode==='design' && onZoneDragLeave()"
                @drop.prevent="designMode==='design' && onZoneDrop($event, 'search')"
              >
                <div class="md-search-grid">
                <template v-for="(field, idx) in layout.search.fields" :key="field.fieldName">
                <div
                  v-if="!field.hidden"
                  class="md-field-chip md-field-search"
                    :class="designMode==='design' ? { 'md-field-selected': isFieldSelected('search', idx) } : ''"
                    :style="{ flex: '0 0 calc(33.333% - 8px)' }"
                    :draggable="designMode==='design'"
                    @dragstart.stop="designMode==='design' && onFieldDragStart($event, 'search', idx)"
                    @click.stop="designMode==='design' && selectCanvasField('search', idx)"
                  >
                    <span class="md-chip-label">{{ field.label }}</span>
                    <component v-if="designMode==='design'" :is="getWidgetIcon(field.uiComponent)" class="md-chip-comp" />
                    <a-button v-if="designMode==='design'" type="text" size="small" class="md-chip-close" @click.stop="removeField('search', idx)">
                      <CloseOutlined style="font-size:8px" />
                    </a-button>
                  </div>
                </template>
              </div>
                <div v-if="!layout.search.fields.length && designMode==='design'" class="md-drop-hint">
                  <InboxOutlined style="font-size:20px;color:#ccc" />
                  <span>拖入搜索条件</span>
                </div>
                <div v-if="!layout.search.fields.length && designMode==='preview'" class="md-preview-empty">
                  <span style="font-size:12px;color:#ccc">暂无搜索条件</span>
                </div>
              </div>
            </div>

            <!-- 表格上部操作区（仅列表页） -->
            <div class="md-section" id="canvas-toolbar" v-if="isListOp" v-show="layout.table.enabled || designMode==='design'">
              <div class="md-section-bar md-design-only" v-show="designMode==='design'">
                <SettingOutlined /> 表格上部操作区
                <a-switch v-model:checked="layout.table.toolbarEnabled" size="small" style="margin-left:auto" />
              </div>
              <div class="md-section-bar md-preview-bar" v-show="designMode==='preview' && layout.table.columns.length">
                <SettingOutlined style="color:#1464a5;font-size:13px" />
                <span style="font-size:12px;font-weight:600;color:#555">操作按钮</span>
              </div>
              <!-- 工具栏按钮配置 -->
              <div class="md-toolbar-config-panel" v-show="designMode==='design' || layout.table.toolbarEnabled">
                <div class="md-toolbar-btn-row">
                  <span class="md-toolbar-label">工具栏按钮：</span>
                  <a-space size="4" wrap>
                    <a-tag
                      v-for="btn in toolbarBtnOptions"
                      :key="btn.key"
                      :color="layout.table.toolbar.includes(btn.key) ? btn.color : undefined"
                      :style="{ cursor: designMode=='design' ? 'pointer' : 'default', opacity: layout.table.toolbar.includes(btn.key) ? 1 : 0.4 }"
                      @click="designMode=='design' && toggleToolbarBtn(btn.key)"
                    >{{ btn.label }}</a-tag>
                  </a-space>
                  <span v-if="designMode==='design'" class="md-toolbar-hint">点击切换按钮</span>
                </div>
                <a-divider style="margin:8px 0" />
                <div class="md-toolbar-btn-row">
                  <span class="md-toolbar-label">行操作：</span>
                  <a-space size="4">
                    <a-checkbox v-model:checked="layout.table.hasEdit" size="small" :disabled="designMode!='design'" @change="markDirty">行编辑</a-checkbox>
                    <a-checkbox v-model:checked="layout.table.hasDetail" size="small" :disabled="designMode!='design'" @change="markDirty">行详情</a-checkbox>
                    <a-checkbox v-model:checked="layout.table.hasDelete" size="small" :disabled="designMode!='design'" @change="markDirty">行删除</a-checkbox>
                  </a-space>
                </div>
              </div>
            </div>

            <!-- 表格列配置（仅列表页） -->
            <div class="md-section" id="canvas-table" v-if="isListOp" v-show="layout.table.enabled || designMode==='design'">
              <div class="md-section-bar md-design-only" v-show="designMode==='design'">
                <TableOutlined /> 表格列配置
                <a-switch v-model:checked="layout.table.enabled" size="small" style="margin-left:auto" />
              </div>
              <div v-show="designMode==='preview' && layout.table.columns.length">
                <div style="display:flex;align-items:center;gap:6px;padding:6px 0;margin-bottom:2px;">
                  <TableOutlined style="color:#1464a5;font-size:13px" />
                  <span style="font-size:12px;font-weight:600;color:#555">{{ currentOpInfo?.name || '列表' }}</span>
                </div>
              </div>
              <div
                class="md-drop-zone md-table-zone"
                :class="designMode==='design' ? { 'md-drag-over': dragTargetZone==='table', 'md-zone-empty': !layout.table.columns.length } : ''"
                @dragover.prevent="designMode==='design' && onZoneDragOver('table')"
                @dragleave="designMode==='design' && onZoneDragLeave()"
                @drop.prevent="designMode==='design' && onZoneDrop($event, 'table')"
              >
                <div class="md-table-preview" v-if="layout.table.columns.length">
                  <div class="md-table-header-row">
                    <span class="md-table-check-col"><a-checkbox disabled size="small" /></span>
                    <template v-for="(col, idx) in layout.table.columns" :key="col.fieldName">
                    <div
                      v-if="!col.hidden"
                      class="md-table-th"
                      :class="designMode==='design' ? { 'md-field-selected': isFieldSelected('table', idx) } : ''"
                      :style="col.fixed && designMode==='design' ? { position:'sticky', [col.fixed]:0, zIndex:1, background:'#fafafa' } : {}"
                      :draggable="designMode==='design'"
                      @dragstart.stop="designMode==='design' && onFieldDragStart($event, 'table', idx)"
                      @click.stop="designMode==='design' && selectCanvasField('table', idx)"
                    >
                      <span class="md-th-label">{{ col.label || col.fieldName }}</span>
                      <span v-if="col.sortable" class="md-th-sort">↕</span>
                      <span v-if="col.fixed && designMode==='design'" class="md-th-fixed">📌{{ col.fixed }}</span>
                      <a-button v-if="designMode==='design'" type="text" size="small" class="md-chip-close" @click.stop="removeField('table', idx)">
                        <CloseOutlined style="font-size:8px" />
                      </a-button>
                    </div>
                    </template>
                    <span class="md-table-action-col" v-if="designMode==='design'">操作</span>
                    <span class="md-table-action-col" v-else>操作</span>
                  </div>
                  <div class="md-table-data-row" v-for="r in 3" :key="r">
                    <span class="md-table-check-col"><a-checkbox disabled size="small" /></span>
                    <div
                      v-for="col in layout.table.columns"
                      :key="col.fieldName"
                      class="md-table-td"
                    >
                      <span class="md-td-placeholder">--</span>
                    </div>
                    <span class="md-table-action-col">
                      <a-tag size="small" v-if="layout.table.hasEdit" color="blue" style="margin:0 2px">编辑</a-tag>
                      <a-tag size="small" v-if="layout.table.hasDetail" style="margin:0 2px">详情</a-tag>
                      <a-tag size="small" v-if="layout.table.hasDelete" color="red" style="margin:0 2px">删除</a-tag>
                    </span>
                  </div>
                </div>
                <div v-if="!layout.table.columns.length && designMode==='design'" class="md-drop-hint">
                  <InboxOutlined style="font-size:20px;color:#ccc" />
                  <span>拖入表格列</span>
                </div>
              </div>
            </div>

            <!-- 表单区 -->
            <div class="md-section" id="canvas-form" v-show="layout.form.enabled || designMode==='design'">
              <div class="md-section-bar md-design-only" v-show="designMode==='design'">
                <FormOutlined />
                <span v-if="!editingFormName"
                  class="md-form-name-text"
                  @click="editingFormName = true"
                  :title="'点击编辑表单名称'"
                >{{ layout.form.name || '编辑表单' }}</span>
                <a-input
                  v-else
                  ref="formNameInputRef"
                  v-model:value="layout.form.name"
                  size="small"
                  style="flex:1;max-width:180px"
                  @blur="finishEditFormName"
                  @pressEnter="finishEditFormName"
                />
                <a-switch v-model:checked="layout.form.enabled" size="small" style="margin-left:auto" />
              </div>
              <div v-show="designMode==='preview'">
                <div style="display:flex;align-items:center;gap:6px;padding:6px 0;margin-bottom:2px;">
                  <FormOutlined style="color:#1464a5;font-size:13px" />
                  <span style="font-size:12px;font-weight:600;color:#555">{{ layout.form.name || '表单' }}</span>
                </div>
              </div>
              <div
                class="md-drop-zone md-form-zone"
                :class="designMode==='design' ? { 'md-drag-over': dragTargetZone==='form', 'md-zone-empty': !layout.form.fields.length } : ''"
                @dragover.prevent="designMode==='design' && onZoneDragOver('form')"
                @dragleave="designMode==='design' && onZoneDragLeave()"
                @drop.prevent="designMode==='design' && onZoneDrop($event, 'form')"
              >
                <div class="md-form-preview">
                  <template v-for="(item, idx) in layout.form.fields" :key="item.fieldName">
                    <!-- ========== 布局行容器 ========== -->
                    <div v-if="item.type==='layout-row'"
                      class="md-layout-row"
                      :class="designMode==='design' ? { 'md-field-selected': isFieldSelected('form', idx) } : ''"
                      @click.stop="designMode==='design' && selectCanvasField('form', idx)"
                    >
                      <div class="md-layout-row-header" v-if="designMode==='design'">
                        <InsertRowBelowOutlined style="font-size:11px;color:#722ed1" />
                        <span class="md-layout-row-title">{{ item.label || '布局行' }}</span>
                        <a-tag color="purple" size="small" style="font-size:9px;padding:0 4px;line-height:16px">{{ item.columns || 2 }}列</a-tag>
                        <a-button type="text" size="small" class="md-form-close" @click.stop="removeField('form', idx)">
                          <CloseOutlined style="font-size:8px" />
                        </a-button>
                      </div>
                      <div class="md-layout-row-header-preview" v-else>
                        <span class="md-layout-row-title-preview">{{ (item.children && item.children.length) ? (item.children.map(c => c.label).filter(Boolean).join(' · ') || '布局行') : '布局行' }}</span>
                        <span style="font-size:10px;color:#bbb">{{ item.columns || 2 }}列</span>
                      </div>
                      <div
                        class="md-layout-row-body"
                        :class="designMode==='design' ? { 'md-zone-empty': !(item.children && item.children.length) } : ''"
                        @dragover.prevent="designMode==='design' && onGroupDragOver(idx)"
                        @dragleave="designMode==='design' && onZoneDragLeave()"
                        @drop.stop.prevent="designMode==='design' && onGroupDrop($event, 'form', idx)"
                      >
                        <template v-if="item.children && item.children.length">
                          <div
                            v-for="(child, cIdx) in item.children"
                            :key="child.fieldName"
                            class="md-form-row"
                            :class="[designMode==='design' ? { 'md-field-selected': isFieldSelected('form', cIdx, idx) } : '', child.labelLayout === 'vertical' ? 'md-form-row-vertical' : '']"
                            :style="{ width: layoutColWidth(item.columns || 2) }"
                            :draggable="designMode==='design'"
                            @dragstart.stop="designMode==='design' && onFieldDragStart($event, 'form', cIdx, idx)"
                            @click.stop="designMode==='design' && selectCanvasField('form', cIdx, idx)"
                          >
                            <label class="md-form-label">
                              <span v-if="child.required" class="md-required">*</span>
                              {{ child.label }}
                            </label>
                            <div class="md-form-control">
                              <WidgetPreview :type="child.uiComponent" :mode="designMode" :placeholder="child.placeholder"
                                :userOptions="previewUserOptions" :orgTreeData="previewOrgTreeData"
                                :productLineTree="previewProductLineTree" :folderTree="previewFolderTree" />
                              <a-button v-if="designMode==='design'" type="text" size="small" class="md-form-close" @click.stop="removeGroupChild(idx, cIdx)">
                                <CloseOutlined style="font-size:8px" />
                              </a-button>
                            </div>
                          </div>
                        </template>
                        <div v-else-if="designMode==='design'" class="md-drop-hint" style="font-size:11px;padding:8px;min-height:40px;display:flex;align-items:center;justify-content:center">
                          <InboxOutlined style="font-size:14px;color:#ccc;margin-right:4px" />
                          <span style="color:#999">拖入字段到{{ item.columns || 2 }}列布局</span>
                        </div>
                      </div>
                    </div>
                    <!-- ========== 分组容器 ========== -->
                    <div v-else-if="item.type==='group'"
                      class="md-form-group"
                      :class="designMode==='design' ? { 'md-field-selected': isFieldSelected('form', idx) } : ''"
                      @click.stop="designMode==='design' && selectCanvasField('form', idx)"
                    >
                      <div class="md-form-group-header">
                        <CaretRightOutlined
                          :style="{ transform: item.collapsed ? '' : 'rotate(90deg)', fontSize:'10px', transition:'0.2s', cursor:'pointer' }"
                          @click.stop="item.collapsed = !item.collapsed"
                        />
                        <span class="md-group-title">{{ item.label }}</span>
                        <span class="md-group-badge">分组</span>
                        <a-button v-if="designMode==='design'" type="text" size="small" class="md-form-close" @click.stop="removeField('form', idx)">
                          <CloseOutlined style="font-size:8px" />
                        </a-button>
                      </div>
                      <div class="md-form-group-body" v-show="!item.collapsed">
                        <div
                          class="md-group-drop-zone"
                          :class="designMode==='design' ? { 'md-zone-empty': !item.children.length } : ''"
                          @dragover.prevent="designMode==='design' && onGroupDragOver(idx)"
                          @dragleave="designMode==='design' && onZoneDragLeave()"
                          @drop.stop.prevent="designMode==='design' && onGroupDrop($event, 'form', idx)"
                        >
                          <template v-for="(child, cIdx) in item.children" :key="child.fieldName">
                            <!-- 分组内的布局行 -->
                            <div v-if="child.type==='layout-row'"
                              class="md-layout-row"
                              :class="designMode==='design' ? { 'md-field-selected': isFieldSelected('form', cIdx, idx) } : ''"
                              @click.stop="designMode==='design' && selectCanvasField('form', cIdx, idx)"
                            >
                              <div class="md-layout-row-header" v-if="designMode==='design'">
                                <InsertRowBelowOutlined style="font-size:11px;color:#722ed1" />
                                <span class="md-layout-row-title">{{ child.label || '布局行' }}</span>
                                <a-tag color="purple" size="small" style="font-size:9px;padding:0 4px;line-height:16px">{{ child.columns || 2 }}列</a-tag>
                                <a-button type="text" size="small" class="md-form-close" @click.stop="removeGroupChild(idx, cIdx)">
                                  <CloseOutlined style="font-size:8px" />
                                </a-button>
                              </div>
                              <div class="md-layout-row-header-preview" v-else>
                                <span class="md-layout-row-title-preview">{{ (child.children && child.children.length) ? (child.children.map(c => c.label).filter(Boolean).join(' · ') || '布局行') : '布局行' }}</span>
                                <span style="font-size:10px;color:#bbb">{{ child.columns || 2 }}列</span>
                              </div>
                              <div
                                class="md-layout-row-body"
                                :class="designMode==='design' ? { 'md-zone-empty': !(child.children && child.children.length) } : ''"
                                @dragover.prevent="designMode==='design' && onGroupDragOver(idx, cIdx)"
                                @dragleave="designMode==='design' && onZoneDragLeave()"
                                @drop.stop.prevent="designMode==='design' && onGroupDrop($event, 'form', idx, cIdx)"
                              >
                                <template v-if="child.children && child.children.length">
                                  <div
                                    v-for="(grandChild, gcIdx) in child.children"
                                    :key="grandChild.fieldName"
                                    class="md-form-row"
                                    :class="[designMode==='design' ? { 'md-field-selected': isFieldSelected('form', gcIdx, idx, cIdx) } : '', grandChild.labelLayout === 'vertical' ? 'md-form-row-vertical' : '']"
                                    :style="{ width: layoutColWidth(child.columns || 2) }"
                                    :draggable="designMode==='design'"
                                    @dragstart.stop="designMode==='design' && onFieldDragStart($event, 'form', gcIdx, idx, cIdx)"
                                    @click.stop="designMode==='design' && selectCanvasField('form', gcIdx, idx, cIdx)"
                                  >
                                    <label class="md-form-label">
                                      <span v-if="grandChild.required" class="md-required">*</span>
                                      {{ grandChild.label }}
                                    </label>
                                    <div class="md-form-control">
                                      <WidgetPreview :type="grandChild.uiComponent" :mode="designMode" :placeholder="grandChild.placeholder"
                                        :userOptions="previewUserOptions" :orgTreeData="previewOrgTreeData"
                                        :productLineTree="previewProductLineTree" :folderTree="previewFolderTree" />
                                      <a-button v-if="designMode==='design'" type="text" size="small" class="md-form-close" @click.stop="removeLayoutRowChild(idx, cIdx, gcIdx)">
                                        <CloseOutlined style="font-size:8px" />
                                      </a-button>
                                    </div>
                                  </div>
                                </template>
                                <div v-if="!child.children?.length && designMode==='design'" class="md-drop-hint">
                                  <InboxOutlined style="font-size:14px;color:#ccc" />
                                  <span style="color:#999">拖入字段到{{ child.columns || 2 }}列布局</span>
                                </div>
                              </div>
                            </div>
                            <!-- 分组内的普通字段 -->
                            <div v-else
                              class="md-form-row"
                              :class="[designMode==='design' ? { 'md-field-selected': isFieldSelected('form', cIdx, idx) } : '', child.labelLayout === 'vertical' ? 'md-form-row-vertical' : '']"
                              :style="{ width: '50%' }"
                              :draggable="designMode==='design'"
                              @dragstart.stop="designMode==='design' && onFieldDragStart($event, 'form', cIdx, idx)"
                              @click.stop="designMode==='design' && selectCanvasField('form', cIdx, idx)"
                            >
                              <label class="md-form-label">
                                <span v-if="child.required" class="md-required">*</span>
                                {{ child.label }}
                              </label>
                              <div class="md-form-control">
                                <WidgetPreview :type="child.uiComponent" :mode="designMode" :placeholder="child.placeholder"
                                  :userOptions="previewUserOptions" :orgTreeData="previewOrgTreeData"
                                  :productLineTree="previewProductLineTree" :folderTree="previewFolderTree" />
                                <a-button v-if="designMode==='design'" type="text" size="small" class="md-form-close" @click.stop="removeGroupChild(idx, cIdx)">
                                  <CloseOutlined style="font-size:8px" />
                                </a-button>
                              </div>
                            </div>
                          </template>
                          <div v-if="!item.children.length && designMode==='design'" class="md-drop-hint" style="border:1px dashed #d9d9d9;border-radius:4px;min-height:40px;margin:4px">
                            <InboxOutlined style="font-size:14px;color:#ccc" />
                            <span style="font-size:11px;color:#999">拖入字段到此分组</span>
                          </div>
                        </div>
                      </div>
                    </div>
                    <!-- ========== 数据表格容器 ========== -->
                    <div v-else-if="item.type==='data-table'"
                      class="md-form-group"
                      :class="designMode==='design' ? { 'md-field-selected': isFieldSelected('form', idx) } : ''"
                      @click.stop="designMode==='design' && selectCanvasField('form', idx)"
                    >
                      <div class="md-form-group-header">
                        <CaretRightOutlined
                          :style="{ transform: item.collapsed ? '' : 'rotate(90deg)', fontSize:'10px', transition:'0.2s', cursor:'pointer' }"
                          @click.stop="item.collapsed = !item.collapsed"
                        />
                        <span class="md-group-title">{{ item.label }}</span>
                        <span class="md-group-badge" style="background:#1677ff">数据表格</span>
                        <span v-if="item.children?.length" style="font-size:10px;color:#999;margin-left:4px">{{ item.children.length }}列</span>
                        <a-button v-if="designMode==='design'" type="text" size="small" class="md-form-close" @click.stop="removeField('form', idx)">
                          <CloseOutlined style="font-size:8px" />
                        </a-button>
                      </div>
                      <div class="md-form-group-body" v-show="!item.collapsed">
                        <!-- 数据表格工具栏预览 -->
                        <div v-if="designMode==='design'" class="md-table-toolbar-preview" style="margin-bottom:6px">
                          <a-space size="4">
                            <a-tag v-if="item.hasAdd" size="small" color="green">新增行</a-tag>
                            <a-tag v-if="item.hasEdit" size="small" color="blue">编辑</a-tag>
                            <a-tag v-if="item.hasDelete" size="small" color="red">删除</a-tag>
                          </a-space>
                        </div>
                        <!-- 表格列拖放区 -->
                        <div
                          class="md-group-drop-zone"
                          :class="designMode==='design' ? { 'md-zone-empty': !item.children?.length } : ''"
                          @dragover.prevent="designMode==='design' && onGroupDragOver(idx)"
                          @dragleave="designMode==='design' && onZoneDragLeave()"
                          @drop.stop.prevent="designMode==='design' && onGroupDrop($event, 'form', idx)"
                        >
                          <!-- 表格列预览 -->
                          <div class="md-table-preview" v-if="item.children?.length">
                            <div class="md-table-header-row">
                              <span class="md-table-check-col"><a-checkbox disabled size="small" /></span>
                              <div
                                v-for="(col, cIdx) in item.children"
                                :key="col.fieldName"
                                class="md-table-th"
                                :class="designMode==='design' ? { 'md-field-selected': isFieldSelected('form', cIdx, idx) } : ''"
                                :draggable="designMode==='design'"
                                @dragstart.stop="designMode==='design' && onFieldDragStart($event, 'form', cIdx, idx)"
                                @click.stop="designMode==='design' && selectCanvasField('form', cIdx, idx)"
                              >
                                <span class="md-th-label">{{ col.label }}</span>
                                <a-tag v-if="col.isCustomColumn" size="small" color="orange" style="font-size:9px;padding:0 3px;margin-left:2px">统计</a-tag>
                                <a-button v-if="designMode==='design'" type="text" size="small" class="md-chip-close" @click.stop="removeGroupChild(idx, cIdx)">
                                  <CloseOutlined style="font-size:8px" />
                                </a-button>
                              </div>
                              <span class="md-table-action-col" v-if="item.hasEdit || item.hasDelete">操作</span>
                            </div>
                            <div class="md-table-data-row" v-for="r in 2" :key="r">
                              <span class="md-table-check-col"><a-checkbox disabled size="small" /></span>
                              <div v-for="col in item.children" :key="col.fieldName" class="md-table-td">
                                <span class="md-td-placeholder">--</span>
                              </div>
                              <span class="md-table-action-col" v-if="item.hasEdit || item.hasDelete">
                                <a-tag v-if="item.hasEdit" size="small" color="blue" style="margin:0 2px">编辑</a-tag>
                                <a-tag v-if="item.hasDelete" size="small" color="red" style="margin:0 2px">删除</a-tag>
                              </span>
                            </div>
                          </div>
                          <div v-if="!item.children?.length && designMode==='design'" class="md-drop-hint" style="border:1px dashed #d9d9d9;border-radius:4px;min-height:50px;margin:4px">
                            <InboxOutlined style="font-size:14px;color:#ccc" />
                            <span style="font-size:11px;color:#999">拖入数据属性到此表格作为列</span>
                            <a-divider type="vertical" />
                            <a-button type="dashed" size="small" @click.stop="addCustomColumn(idx)">+ 统计列</a-button>
                          </div>
                        </div>
                        <!-- 底部操作按钮（设计模式） -->
                        <div v-if="designMode==='design' && item.children?.length" style="padding:4px 8px;text-align:center">
                          <a-button type="dashed" size="small" @click.stop="addCustomColumn(idx)">
                            <PlusOutlined /> 添加统计列
                          </a-button>
                        </div>
                      </div>
                    </div>
                    <!-- ========== 普通字段 ========== -->
                    <div v-else-if="!item.hidden"
                      class="md-form-row"
                      :class="[designMode==='design' ? { 'md-field-selected': isFieldSelected('form', idx) } : '', item.labelLayout === 'vertical' ? 'md-form-row-vertical' : '']"
                      :style="{ width: '50%' }"
                      :draggable="designMode==='design'"
                      @dragstart.stop="designMode==='design' && onFieldDragStart($event, 'form', idx)"
                      @click.stop="designMode==='design' && selectCanvasField('form', idx)"
                    >
                      <label class="md-form-label">
                        <span v-if="item.required" class="md-required">*</span>
                        {{ item.label }}
                      </label>
                      <div class="md-form-control">
                        <WidgetPreview :type="item.uiComponent" :mode="designMode" :placeholder="item.placeholder"
                          :userOptions="previewUserOptions" :orgTreeData="previewOrgTreeData"
                          :productLineTree="previewProductLineTree" :folderTree="previewFolderTree" />
                        <a-button v-if="designMode==='design'" type="text" size="small" class="md-form-close" @click.stop="removeField('form', idx)">
                          <CloseOutlined style="font-size:8px" />
                        </a-button>
                      </div>
                    </div>
                  </template>
                </div>
                <div v-if="!layout.form.fields.length && designMode==='design'" class="md-drop-hint">
                  <InboxOutlined style="font-size:20px;color:#ccc" />
                  <span>拖入表单字段</span>
                </div>
                <div v-if="!layout.form.fields.length && designMode==='preview'" class="md-preview-empty" style="padding:16px;text-align:center">
                  <span style="font-size:12px;color:#ccc">暂无表单字段</span>
                </div>
              </div>
            </div>

            <!-- 页面底部 -->
            <div class="md-page-footer">
              <a-space size="8">
                <a-tag size="small" color="blue" v-if="['create','update'].includes(currentOpCode)">提交</a-tag>
                <a-tag size="small">取消</a-tag>
              </a-space>
              <span v-if="designMode==='design'" class="md-footer-hint">页面操作按钮</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：属性面板 (仅设计模式) -->
      <div class="md-right-panel" v-if="designMode==='design' && selectedField">
        <div class="md-props-header">
          <span>属性配置</span>
          <a-button type="text" size="small" @click="selectedField=null"><CloseOutlined style="font-size:10px" /></a-button>
        </div>
        <div class="md-props-body">
          <!-- ===== 布局行属性 ===== -->
          <a-form v-if="selectedField.field?.type==='layout-row'" layout="vertical" size="small" :label-col="{span:24}" :wrapper-col="{span:24}">
            <a-form-item label="布局类型">
              <a-tag color="purple">布局行容器</a-tag>
              <span style="font-size:11px;color:#999;margin-left:4px">包含 {{ selectedField.field.children?.length || 0 }} 个字段</span>
            </a-form-item>
            <a-form-item label="每行列数">
              <a-radio-group v-model:value="selectedField.field.columns" size="small" button-style="solid" @change="onLayoutColumnsChange">
                <a-radio-button :value="1">1列</a-radio-button>
                <a-radio-button :value="2">2列</a-radio-button>
                <a-radio-button :value="4">4列</a-radio-button>
              </a-radio-group>
              <div style="font-size:11px;color:#999;margin-top:4px">
                当前：每行{{ selectedField.field.columns }}列，子字段宽度 {{ layoutColWidth(selectedField.field.columns) }}
              </div>
            </a-form-item>
            <a-divider style="margin:12px 0" />

            <div v-if="widgetDocFor('layout-row')" class="md-field-doc">
              <div class="md-field-doc-title"><FileTextOutlined style="font-size:12px;color:#999" /> 使用说明</div>
              <div class="md-field-doc-text">{{ widgetDocFor('layout-row') }}</div>
            </div>

            <a-divider style="margin:12px 0" />
            <a-button type="dashed" danger block size="small" @click="removeSelectedField">
              <template #icon><DeleteOutlined /></template>
              删除此布局行（含所有字段）
            </a-button>
          </a-form>
          <!-- ===== 分组属性 ===== -->
          <a-form v-else-if="selectedField.field?.type==='group'" layout="vertical" size="small" :label-col="{span:24}" :wrapper-col="{span:24}">
            <a-form-item label="分组类型">
              <a-tag color="purple">分组容器</a-tag>
              <span style="font-size:11px;color:#999;margin-left:4px">包含 {{ selectedField.field.children?.length || 0 }} 个字段</span>
            </a-form-item>
            <a-form-item label="分组标题">
              <a-input v-model:value="selectedField.field.label" size="small" @change="markDirty" placeholder="请输入分组名称" />
            </a-form-item>
            <a-divider style="margin:12px 0" />

            <div v-if="widgetDocFor('form-group')" class="md-field-doc">
              <div class="md-field-doc-title"><FileTextOutlined style="font-size:12px;color:#999" /> 使用说明</div>
              <div class="md-field-doc-text">{{ widgetDocFor('form-group') }}</div>
            </div>

            <a-divider style="margin:12px 0" />
            <a-button type="dashed" danger block size="small" @click="removeSelectedField">
              <template #icon><DeleteOutlined /></template>
              删除此分组（含所有字段）
            </a-button>
          </a-form>

          <!-- ===== 数据表格属性 ===== -->
          <a-form v-else-if="selectedField.field?.type==='data-table'" layout="vertical" size="small" :label-col="{span:24}" :wrapper-col="{span:24}">
            <a-form-item label="表格类型">
              <a-tag color="blue">数据表格</a-tag>
              <span style="font-size:11px;color:#999;margin-left:4px">{{ selectedField.field.children?.length || 0 }} 列</span>
            </a-form-item>
            <a-form-item label="表格标题">
              <a-input v-model:value="selectedField.field.label" size="small" @change="markDirty" placeholder="请输入表格标题" />
            </a-form-item>
            <a-form-item label="数据绑定字段">
              <a-select
                v-model:value="selectedField.field.dataBindField"
                mode="multiple"
                size="small"
                show-search
                allow-clear
                option-filter-prop="label"
                placeholder="选择数据对象属性（可多选）"
                @change="onDataBindChange"
              >
                <a-select-option
                  v-for="attr in availableAttrs"
                  :key="attr.fieldName"
                  :value="attr.fieldName"
                  :label="(attr.displayName || attr.fieldName)"
                >
                  <span>{{ attr.displayName || attr.fieldName }}</span>
                  <span style="color:#999;font-size:11px;margin-left:4px">{{ attr.fieldName }}</span>
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="操作按钮">
              <a-space size="4">
                <a-checkbox v-model:checked="selectedField.field.hasEdit" size="small" @change="markDirty">编辑</a-checkbox>
                <a-checkbox v-model:checked="selectedField.field.hasDelete" size="small" @change="markDirty">删除</a-checkbox>
                <a-checkbox v-model:checked="selectedField.field.hasAdd" size="small" @change="markDirty">新增</a-checkbox>
              </a-space>
            </a-form-item>
            <a-form-item label="分页显示">
              <a-switch v-model:checked="selectedField.field.pagination" size="small" @change="markDirty" />
              <a-input-number
                v-if="selectedField.field.pagination"
                v-model:value="selectedField.field.pageSize"
                :min="5" :max="100"
                size="small" style="width:100px;margin-left:8px"
                @change="markDirty"
                placeholder="每页条数"
              />
            </a-form-item>
            <a-divider style="margin:12px 0" />

            <div v-if="widgetDocFor('data-table')" class="md-field-doc">
              <div class="md-field-doc-title"><FileTextOutlined style="font-size:12px;color:#999" /> 使用说明</div>
              <div class="md-field-doc-text">{{ widgetDocFor('data-table') }}</div>
            </div>

            <a-divider style="margin:12px 0" />
            <a-button type="dashed" danger block size="small" @click="removeSelectedField">
              <template #icon><DeleteOutlined /></template>
              删除此数据表格（含所有列）
            </a-button>
          </a-form>

          <!-- ===== 数据表格列属性 ===== -->
          <a-form v-else-if="selectedField.groupIndex !== undefined && getContainerAt(selectedField.groupIndex)?.type==='data-table'"
            layout="vertical" size="small" :label-col="{span:24}" :wrapper-col="{span:24}">
              <a-form-item label="列类型">
                <a-tag v-if="selectedField.field.isCustomColumn" color="orange">统计列</a-tag>
                <a-tag v-else-if="selectedField.field.source==='SYSTEM'" color="green">数据属性</a-tag>
                <a-tag v-else color="default">自定义</a-tag>
              </a-form-item>
              <a-form-item label="列标题">
                <a-input v-model:value="selectedField.field.label" size="small" @change="markDirty" />
              </a-form-item>
              <a-form-item label="列宽度 (px)">
                <a-input-number v-model:value="selectedField.field.width" :min="60" :max="600" size="small" style="width:100%" @change="markDirty" />
              </a-form-item>
              <template v-if="selectedField.field.isCustomColumn">
                <a-form-item label="统计表达式">
                  <a-input v-model:value="selectedField.field.customExpression" size="small" placeholder="如: sum(amount)" @change="markDirty" />
                </a-form-item>
              </template>
              <a-divider style="margin:12px 0" />
              <a-button type="dashed" danger block size="small" @click="removeSelectedField">
                <template #icon><DeleteOutlined /></template>
                移除此列
              </a-button>
          </a-form>

          <!-- ===== 字段属性 ===== -->
          <a-form v-else layout="vertical" size="small" :label-col="{span:24}" :wrapper-col="{span:24}">
            <!-- 带 bindFields 的控件：先展示默认绑定标签，字段名仍可选 -->
            <a-form-item v-if="selectedField.field.bindFields?.length" label="默认绑定">
              <a-space wrap>
                <a-tag
                  v-for="(bf, idx) in selectedField.field.bindFields"
                  :key="bf"
                  :color="idx === 0 ? 'blue' : 'default'"
                  size="small"
                >
                  {{ bf }}{{ idx === 0 ? ' (主)' : '' }}
                </a-tag>
              </a-space>
              <div style="font-size:10px;color:#bbb;margin-top:2px">控件预设的绑定字段，可手动修改</div>
            </a-form-item>
            <!-- 字段名（始终可选） -->
            <a-form-item label="字段名">
              <a-select
                v-model:value="selectedField.field.fieldName"
                size="small"
                show-search
                option-filter-prop="label"
                @change="onFieldNameChange"
              >
                <a-select-option
                  v-for="attr in availableAttrs"
                  :key="attr.fieldName"
                  :value="attr.fieldName"
                  :label="(attr.displayName || attr.fieldName) + ' ' + attr.fieldName"
                >
                  <span>{{ attr.displayName || attr.fieldName }}</span>
                  <span style="color:#999;font-size:11px;margin-left:4px">{{ attr.fieldName }}</span>
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="显示标签">
              <a-input v-model:value="selectedField.field.label" size="small" @change="markDirty" />
            </a-form-item>
            <a-form-item label="UI 控件">
              <WidgetSelector v-model="selectedField.field.uiComponent" @change="onUiComponentChange" />
            </a-form-item>



            <template v-if="selectedField.section==='table'">
              <a-form-item label="列宽度 (px)">
                <a-input-number v-model:value="selectedField.field.width" :min="60" :max="600" size="small" style="width:100%" @change="markDirty" />
              </a-form-item>
              <a-form-item label="固定列">
                <a-select v-model:value="selectedField.field.fixed" allow-clear placeholder="不固定" size="small" @change="markDirty">
                  <a-select-option value="left">左侧固定</a-select-option>
                  <a-select-option value="right">右侧固定</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="可排序">
                <a-switch v-model:checked="selectedField.field.sortable" size="small" @change="markDirty" />
              </a-form-item>
            </template>

            <template v-if="selectedField.section==='form'">
              <a-form-item label="必填">
                <a-switch v-model:checked="selectedField.field.required" size="small" @change="markDirty" />
              </a-form-item>
              <a-form-item label="只读">
                <a-switch v-model:checked="selectedField.field.readonly" size="small" @change="markDirty" />
              </a-form-item>
              <a-form-item label="标签位置">
                <a-radio-group v-model:value="selectedField.field.labelLayout" size="small" @change="markDirty">
                  <a-radio-button value="horizontal">左右</a-radio-button>
                  <a-radio-button value="vertical">上下</a-radio-button>
                </a-radio-group>
              </a-form-item>
            </template>

            <a-form-item label="默认值">
              <a-input v-model:value="selectedField.field.defaultValue" size="small" placeholder="选填" @change="markDirty" />
            </a-form-item>
            <a-form-item label="占位提示">
              <a-input v-model:value="selectedField.field.placeholder" size="small" placeholder="placeholder" @change="markDirty" />
            </a-form-item>

            <a-divider style="margin:12px 0" />

            <!-- 控件使用说明 -->
            <div v-if="widgetDocFor(selectedField.field.uiComponent)" class="md-field-doc">
              <div class="md-field-doc-title">
                <FileTextOutlined style="font-size:12px;color:#999" />
                使用说明
              </div>
              <div class="md-field-doc-text">{{ widgetDocFor(selectedField.field.uiComponent) }}</div>
            </div>
            <div v-else class="md-field-doc md-field-doc--empty">
              <span class="md-field-doc-text" style="color:#ccc;font-style:italic">无使用说明</span>
            </div>

            <a-divider style="margin:12px 0" />
            <a-button type="dashed" danger block size="small" html-type="button" @click.stop="removeSelectedField">
              <template #icon><DeleteOutlined /></template>
              从画布中移除
            </a-button>
          </a-form>
        </div>
      </div>
      <div class="md-right-panel md-right-empty" v-else-if="designMode==='design'">
        <a-empty :image="simpleImage" description="点击画布中的字段以编辑属性">
          <template #description>
            <span style="font-size:12px;color:#999">选中画布中的组件<br/>编辑其属性</span>
          </template>
        </a-empty>
      </div>
    </div>

    <!-- 新增自定义操作弹窗 -->
    <a-modal
      v-model:open="showAddOp"
      title="新增自定义操作"
      :mask-closable="false"
      @ok="handleAddOperation"
      width="400px"
    >
      <a-form layout="vertical" size="small">
        <a-form-item label="操作编码 (code)" required>
          <a-input
            v-model:value="newOpCode"
            placeholder="如: approve、import、custom_view"
            :maxlength="30"
          />
          <div style="color:#999;font-size:11px;margin-top:2px">
            仅限字母、数字、下划线，不可与已有操作重复
          </div>
        </a-form-item>
        <a-form-item label="操作名称" required>
          <a-input v-model:value="newOpName" placeholder="如: 审批页、导入页" :maxlength="50" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, shallowRef } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message, Modal, Empty } from 'ant-design-vue'
import {
  ArrowLeftOutlined, SearchOutlined, TableOutlined, FormOutlined,
  PlusOutlined, DeleteOutlined, InboxOutlined, CloseOutlined,
  UndoOutlined, RedoOutlined, DesktopOutlined, TabletOutlined, MobileOutlined,
  ClearOutlined, ImportOutlined, SaveOutlined, CaretRightOutlined, CopyOutlined,
  FolderOutlined, BarsOutlined, SettingOutlined,
  AppstoreOutlined, DatabaseOutlined, BlockOutlined,
  FileTextOutlined, StarOutlined,
  EyeOutlined, EditOutlined, InsertRowBelowOutlined
} from '@ant-design/icons-vue'
import {
  getAttributeDefinitions, getOperationList, getPageLayout,
  savePageLayout as savePageLayoutApi, deletePageLayout as deletePageLayoutApi,
  clonePageLayout,
  getOrgTree, getAllUsers, getProductLineTree, getAllFolderTree,
  getTypeDefinition, getTypeMappings
} from '@/api'
import { useUserStore } from '@/stores/user'
import {
  WidgetToolbox, WidgetSelector, WidgetPreview,
  getWidgetIcon, getWidgetByType, createFieldFromWidget, createCompanionFields, isContainerWidget
} from '@/widgets'

const router = useRouter()
const route = useRoute()
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE

// ===== 路由参数 =====
const entityOid = ref(route.query.entityOid || '')
const entityCode = ref(route.query.entityCode || '')
const entityName = ref(route.query.entityName || '')
const typeKind = ref(route.query.typeKind || 'SOFT_TYPE')

// ===== 类型定义（含 rootTypeCode） =====
const typeDefinition = ref(null)

// ===== 操作管理 =====
const operationList = ref([])
const currentOpCode = ref('list')
const currentOpInfo = computed(() => operationList.value.find(o => o.code === currentOpCode.value))
const isListOp = computed(() => currentOpCode.value === 'list')
const isBuiltinOp = computed(() => {
  const op = operationList.value.find(o => o.code === currentOpCode.value)
  return op ? op.builtin === 'true' : false
})
const currentLayoutTenantOid = ref(null) // 当前加载的 layout 所属租户

// 是否可克隆：租户管理员 + 系统操作 + 当前 layout 是平台级（未克隆过）
const canClone = computed(() => {
  if (isPlatformAdmin.value) return false
  if (!isBuiltinOp.value) return false
  // 如果已有租户级 layout（saved=true 且 tenantOid 非平台级），不可克隆
  const op = currentOpInfo.value
  if (op?.saved === 'true' && currentLayoutTenantOid.value !== PLATFORM_TENANT_OID) return false
  return true
})
const PLATFORM_TENANT_OID = '00000000-0000-0000-0000-000000000000'

const showAddOp = ref(false)
const newOpCode = ref('')
const newOpName = ref('')

// ===== 画布模式 =====
const canvasMode = ref('desktop')
const designMode = ref('design')  // 'design' | 'preview'

// ===== 预览模式真实数据（用户列表 / 组织树） =====
const previewUserOptions = ref([])
const previewOrgTreeData = ref([])
const previewProductLineTree = ref([])
const previewFolderTree = ref([])

async function loadPreviewData() {
  try {
    const [orgRes, userRes, productLineRes, folderRes] = await Promise.all([
      getOrgTree(),
      getAllUsers(),
      getProductLineTree(),
      getAllFolderTree()
    ])
    previewOrgTreeData.value = transformOrgTree(orgRes.data || [])
    const users = Array.isArray(userRes.data) ? userRes.data : (userRes.data?.records || [])
    previewUserOptions.value = users.map(u => ({
      label: u.displayName || u.username,
      value: u.oid || u.id
    }))
    previewProductLineTree.value = transformProductLineTree(productLineRes.data || [])
    previewFolderTree.value = transformFolderTree(folderRes.data || [])
  } catch {
    // 静默失败，预览时仅显示占位
  }
}

function transformOrgTree(nodes) {
  if (!nodes || !Array.isArray(nodes)) return []
  return nodes.map(n => ({
    title: n.name,
    value: n.oid,
    key: n.oid,
    children: n.children ? transformOrgTree(n.children) : undefined
  }))
}

/**
 * 将产品线树转为 TreeSelect 所需格式
 */
function transformProductLineTree(nodes, parentPath = '') {
  if (!nodes || !Array.isArray(nodes)) return []
  return nodes.map(n => {
    const label = parentPath ? `${parentPath} / ${n.name}` : n.name
    return {
      title: label,
      value: n.oid,
      key: n.oid,
      children: n.children ? transformProductLineTree(n.children, label) : undefined
    }
  })
}

/**
 * 将文件夹树转为 TreeSelect 所需格式
 */
function transformFolderTree(nodes, parentPath = '') {
  if (!nodes || !Array.isArray(nodes)) return []
  return nodes.map(n => {
    const label = parentPath ? `${parentPath} / ${n.name}` : n.name
    return {
      title: label,
      value: n.oid,
      key: n.oid,
      children: n.children?.length ? transformFolderTree(n.children, label) : undefined
    }
  })
}

watch(designMode, (mode) => {
  if (mode === 'preview') {
    loadPreviewData()
  }
})

// ===== 工具箱 =====
const activeToolTab = ref('widgets')
const attrSearch = ref('')

const toolTabs = [
  { key: 'widgets', label: '控件', icon: shallowRef(AppstoreOutlined) },
  { key: 'data', label: '字段', icon: shallowRef(DatabaseOutlined) },
  { key: 'outline', label: '大纲', icon: shallowRef(BlockOutlined) },
]

// ===== 状态 =====
const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))
const saving = ref(false)
const cloning = ref(false)
const availableAttrs = ref([])
const dragTargetZone = ref(null)
let dragTargetGroupIdx = null
let dragTargetSubGroupIdx = null
const selectedField = ref(null)  // { section, field, index }
const editingFormName = ref(false)
const canvasWrapRef = ref(null)
const canvasRef = ref(null)
const formNameInputRef = ref(null)

// 编辑表单名称内联
function finishEditFormName() {
  editingFormName.value = false
  markDirty()
}

// 撤销/重做
const undoStack = ref([])
const redoStack = ref([])
const maxHistory = 30

// 布局数据结构
const layout = reactive({
  search: { enabled: true, fields: [] },
  table: { enabled: true, toolbarEnabled: true, toolbar: ['create', 'export'], hasEdit: true, hasDelete: true, hasDetail: true, columns: [] },
  form: { enabled: true, name: '编辑表单', fields: [] }
})

// 工具栏可选按钮
const toolbarBtnOptions = [
  { key: 'create', label: '新建', color: 'blue' },
  { key: 'export', label: '导出', color: 'green' },
  { key: 'import', label: '导入', color: 'orange' },
  { key: 'refresh', label: '刷新', color: 'purple' },
  { key: 'batchDelete', label: '批量删除', color: 'red' },
]

function toggleToolbarBtn(key) {
  pushUndo()
  const idx = layout.table.toolbar.indexOf(key)
  if (idx > -1) {
    layout.table.toolbar.splice(idx, 1)
  } else {
    layout.table.toolbar.push(key)
  }
}

function cloneLayout() {
  const cloneField = (f) => {
    const cloned = { ...f }
    if (f.children) cloned.children = f.children.map(cloneField)
    if (Array.isArray(f.dataBindField)) cloned.dataBindField = [...f.dataBindField]
    return cloned
  }
  return {
    search: { enabled: layout.search.enabled, fields: layout.search.fields.map(cloneField) },
    table: { enabled: layout.table.enabled, toolbarEnabled: layout.table.toolbarEnabled, toolbar: [...layout.table.toolbar], hasEdit: layout.table.hasEdit, hasDelete: layout.table.hasDelete, hasDetail: layout.table.hasDetail, columns: layout.table.columns.map(cloneField) },
    form: { enabled: layout.form.enabled, name: layout.form.name, fields: layout.form.fields.map(cloneField) }
  }
}

function applyLayout(snapshot) {
  layout.search = snapshot.search
  layout.table = snapshot.table
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
  layout.search = { enabled: true, fields: [] }
  layout.table = { enabled: true, toolbarEnabled: true, toolbar: ['create', 'export'], hasEdit: true, hasDelete: true, hasDetail: true, columns: [] }
  layout.form = { enabled: true, name: '编辑表单', fields: [] }
  selectedField.value = null
  undoStack.value = []
  redoStack.value = []
}

// ===== 大纲 =====
const outlineActive = ref({})
const outlineSections = computed(() => {
  const sections = []
  if (isListOp.value) {
    sections.push(
      { key: 'search', label: '搜索区域', count: layout.search.fields.length, items: layout.search.fields },
      { key: 'toolbar', label: '表格操作区', count: layout.table.toolbar.length, items: layout.table.toolbar.map(k => ({ label: toolbarBtnOptions.find(b => b.key === k)?.label || k })) },
      { key: 'table', label: '表格列配置', count: layout.table.columns.length, items: layout.table.columns },
    )
  }
  sections.push(
    { key: 'form', label: '编辑表单', count: layout.form.fields.length, items: layout.form.fields },
  )
  return sections
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

// ===== 操作切换 =====
async function loadOperations() {
  if (!entityOid.value) return
  try {
    const res = await getOperationList(entityOid.value, entityCode.value)
    if (res.code === 200 && res.data) {
      operationList.value = res.data
    }
  } catch { /* ignore */ }
}

async function onOperationChange() {
  resetLayoutState()
  await loadLayoutForOp()
}

async function loadLayoutForOp() {
  if (!entityOid.value || !currentOpCode.value) return
  currentLayoutTenantOid.value = null
  try {
    const res = await getPageLayout(entityOid.value, currentOpCode.value)
    if (res.code === 200 && res.data) {
      currentLayoutTenantOid.value = res.data.tenantOid || null
      const json = typeof res.data.layoutJson === 'string'
        ? JSON.parse(res.data.layoutJson)
        : res.data.layoutJson
      if (json) {
        layout.search = json.search || layout.search
        layout.table = json.table || layout.table
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

  operationList.value.push({ code, name, builtin: 'false', saved: 'false' })
  currentOpCode.value = code
  resetLayoutState()
}

async function handleDeleteOperation() {
  const code = currentOpCode.value
  const op = operationList.value.find(o => o.code === code)
  if (!op || op.builtin === 'true') {
    message.warning('系统预置操作不可删除')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除操作 "${op.name}" (${code}) 及其布局配置吗？`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      try {
        await deletePageLayoutApi(entityOid.value, code)
        operationList.value = operationList.value.filter(o => o.code !== code)
        currentOpCode.value = 'list'
        resetLayoutState()
        await loadLayoutForOp()
        message.success('已删除')
      } catch (e) {
        message.error(e?.response?.data?.message || '删除失败')
      }
    }
  })
}

// ===== 数据属性 =====
const filteredDataAttrs = computed(() => {
  const kw = attrSearch.value.toLowerCase()
  return availableAttrs.value.filter(a => {
    if (kw && !a.fieldName.toLowerCase().includes(kw)
      && !(a.displayName || '').toLowerCase().includes(kw)) return false
    return true
  })
})

function isAttrUsed(fieldName) {
  return layout.search.fields.some(f => f.fieldName === fieldName)
    || layout.table.columns.some(f => f.fieldName === fieldName)
    || layout.form.fields.some(item => {
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

function dataTypeLabel(dt) {
  const map = { STRING: '文', NUMBER: '数', BOOLEAN: '布', DATE: '日', DATETIME: '时', LONGTEXT: '长', SELECT: '选' }
  return map[dt] || dt || '?'
}

function opIcon(code) {
  const map = {
    list: shallowRef(TableOutlined),
    create: shallowRef(PlusOutlined),
    update: shallowRef(FormOutlined),
    detail: shallowRef(FileTextOutlined),
  }
  return map[code] ? shallowRef(map[code].value) : shallowRef(StarOutlined)
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
    // 容器控件（分组、布局行）只能拖入表单区
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
    // 普通控件
    const field = createFieldFromWidget(w.type)
    list.push(field)
    // 创建复合字段的关联字段（如 ownerType 配合 ownerOid）
    const companions = createCompanionFields(w.type)
    companions.forEach(c => list.push(c))
    const idx = list.length - 1 - companions.length
    selectedField.value = { section: zone, field: list[idx], index: idx }
    widgetDragData = null
    return
  }

  if (fieldDragData) {
    const { zone: srcZone, idx: srcIdx, groupIdx: srcGroupIdx } = fieldDragData
    // 如果从分组内拖出到表单顶层
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
      // 同区域排序
      const list = getZoneList(zone)
      if (srcIdx < 0 || srcIdx >= list.length) { fieldDragData = null; return }
      const [moved] = list.splice(srcIdx, 1)
      // 按区域获取正确的 DOM 参照元素（避免 querySelectorAll 穿透容器匹配嵌套元素）
      let items
      if (zone === 'form') {
        const preview = e.currentTarget.querySelector('.md-form-preview')
        items = preview ? preview.children : []
      } else if (zone === 'table') {
        items = e.currentTarget.querySelectorAll('.md-table-th')
      } else {
        items = e.currentTarget.querySelectorAll('.md-field-chip')
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
      // 跨区域移动
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

// ===== 分组内拖放 =====
function onGroupDragOver(groupIdx, subGroupIdx) {
  dragTargetGroupIdx = groupIdx
  dragTargetSubGroupIdx = subGroupIdx !== undefined ? subGroupIdx : null
}
function onGroupDrop(e, zone, groupIdx, subGroupIdx) {
  dragTargetZone.value = null
  dragTargetSubGroupIdx = null
  pushUndo()
  // 解析容器：支持 3 级嵌套（group.layout-row → fields）
  let container = layout.form.fields[groupIdx]
  if (subGroupIdx !== undefined) {
    if (!container || container.type !== 'group' || !container.children) return
    container = container.children[subGroupIdx]
    if (!container || container.type !== 'layout-row') return
  }
  if (!container || (container.type !== 'group' && container.type !== 'layout-row' && container.type !== 'data-table')) return
  const isLayoutRow = container.type === 'layout-row'
  const isDataTable = container.type === 'data-table'

  if (dataDragData) {
    const field = buildFieldFromAttr(dataDragData.attr)
    // 数据表格容器不检查重复（同一属性可做多列），直接添加
    if (!isDataTable) {
      if (layout.form.fields.some(item =>
        (item.type !== 'group' && item.type !== 'layout-row' && item.type !== 'data-table') ? item.fieldName === field.fieldName
          : item.children && item.children.some(c => c.fieldName === field.fieldName)
      )) {
        message.warning(`属性 "${field.fieldName}" 已在表单中`)
        dataDragData = null
        return
      }
    }
    container.children.push(field)
    selectedField.value = {
      section: zone, field, index: container.children.length - 1,
      groupIndex: groupIdx,
      subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
    }
    dataDragData = null
    return
  }

  if (widgetDragData) {
    const w = widgetDragData.widget
    const destType = container.type
    if (isContainerWidget(w.type)) {
      // 允许 layout-row 放入 form-group，其他容器嵌套一律禁止
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
    // 创建复合字段的关联字段
    const companions = createCompanionFields(w.type)
    companions.forEach(c => container.children.push(c))
    selectedField.value = {
      section: zone, field, index: container.children.length - 1 - companions.length,
      groupIndex: groupIdx,
      subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
    }
    widgetDragData = null
    return
  }

  // 从分组被拖拽到另一个分组
  if (fieldDragData) {
    const { zone: srcZone, idx: srcIdx, groupIdx: srcGroupIdx, subGroupIdx: srcSubGroupIdx } = fieldDragData
    if (srcGroupIdx !== undefined) {
      // 同容器内重排：按鼠标位置计算插入位置
      if (srcGroupIdx === groupIdx && (srcSubGroupIdx ?? undefined) === (subGroupIdx ?? undefined)) {
        if (srcIdx < 0 || srcIdx >= (container.children?.length || 0)) {
          fieldDragData = null; return
        }
        const [moved] = container.children.splice(srcIdx, 1)
        // 根据容器类型查找对应的 DOM 元素来计算插入位置
        let childNodes
        if (isDataTable) {
          // 数据表格：匹配 .md-table-th 元素
          childNodes = e.currentTarget.querySelectorAll('.md-table-th')
        } else {
          childNodes = Array.from(e.currentTarget.children).filter(el =>
            el.classList.contains('md-form-row') || el.classList.contains('md-layout-row')
          )
        }
        let insertIdx = container.children.length
        const dropY = e.clientY
        childNodes.forEach((item, i) => {
          const rect = item.getBoundingClientRect()
          if (dropY < rect.top + rect.height / 2) {
            insertIdx = Math.min(insertIdx, i)
          }
        })
        container.children.splice(Math.min(insertIdx, container.children.length), 0, moved)
        selectedField.value = {
          section: zone, field: moved,
          index: container.children.indexOf(moved),
          groupIndex: groupIdx,
          subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
        }
        fieldDragData = null
        return
      }
      // 从嵌套容器内拖出（跨容器移动）
      let srcContainer
      if (srcSubGroupIdx !== undefined) {
        srcContainer = layout.form.fields[srcGroupIdx]?.children?.[srcSubGroupIdx]
      } else {
        srcContainer = layout.form.fields[srcGroupIdx]
      }
      if (srcContainer && srcContainer.children && srcIdx < srcContainer.children.length) {
        const item = srcContainer.children[srcIdx]
        // 阻止把整个容器拖入另一容器（仅允许普通字段和 layout-row 移动到 group）
        if (item.type === 'group') {
          message.warning('不能将分组容器拖入另一个容器')
          fieldDragData = null
          return
        }
        if (item.type === 'layout-row' && container.type !== 'group') {
          message.warning('布局行只能放入分组容器')
          fieldDragData = null
          return
        }
        const [moved] = srcContainer.children.splice(srcIdx, 1)
        container.children.push(moved)
        selectedField.value = {
          section: zone, field: moved, index: container.children.length - 1,
          groupIndex: groupIdx,
          subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
        }
      }
    } else {
      // 从表单顶层拖到容器内
      const dstList = getZoneList(zone)
      if (srcIdx >= 0 && srcIdx < dstList.length) {
        const item = dstList[srcIdx]
        if (item.type === 'group') {
          message.warning('不能将分组拖入另一个容器')
          fieldDragData = null
          return
        }
        if (item.type === 'layout-row' && container.type !== 'group') {
          message.warning('布局行只能放入分组容器')
          fieldDragData = null
          return
        }
        const [moved] = dstList.splice(srcIdx, 1)
        container.children.push(moved)
        selectedField.value = {
          section: zone, field: moved, index: container.children.length - 1,
          groupIndex: groupIdx,
          subGroupIndex: subGroupIdx !== undefined ? subGroupIdx : undefined
        }
      }
    }
    fieldDragData = null
    return
  }
  fieldDragData = null
}

function getZoneList(zone) {
  if (zone === 'search') return layout.search.fields
  if (zone === 'table') return layout.table.columns
  if (zone === 'form') return layout.form.fields
}

function zoneLabel(zone) {
  const map = { search: '搜索区', table: '列表区', form: '表单区' }
  return map[zone] || zone
}

/**
 * 根据列数计算列宽度（用于布局行内的子字段）
 * 1列 → 100%, 2列 → 50%, 4列 → 25%
 */
function layoutColWidth(columns) {
  const map = { 1: '100%', 2: '50%', 4: '25%' }
  return map[columns] || '50%'
}

function buildFieldFromAttr(attr) {
  const uiComp = attr.uiComponent || 'input'
  const w = getWidgetByType(uiComp)
  return {
    fieldName: attr.fieldName,
    label: attr.displayName || attr.fieldName,
    source: attr.source || 'SYSTEM',
    dataType: attr.dataType || 'STRING',
    uiComponent: uiComp,
    bindFields: w?.bindFields || null,
    description: w?.description || '',
    width: 150,
    required: attr.required || false,
    sortable: false, fixed: undefined,
    readonly: false, defaultValue: attr.defaultValue || '', placeholder: '',
    labelLayout: 'horizontal'
  }
}

/** 根据控件 type 获取其使用说明 */
function widgetDocFor(widgetType) {
  const w = getWidgetByType(widgetType)
  return w?.description || ''
}

/** UI 控件切换时同步更新使用说明 + 绑定字段 */
function onUiComponentChange(newComponent) {
  const w = getWidgetByType(newComponent)
  if (!selectedField.value?.field) return
  const field = selectedField.value.field
  if (w?.description) field.description = w.description
  // 新控件有 bindFields → 锁定字段名；无 bindFields → 清除绑定
  if (w?.bindFields?.length) {
    field.bindFields = w.bindFields
    field.fieldName = w.bindFields[0]
  } else {
    field.bindFields = null
  }
  markDirty()
}

// 字段名下拉切换时：同步更新 label、dataType、uiComponent 等
function onFieldNameChange(newFieldName) {
  const attr = availableAttrs.value.find(a => a.fieldName === newFieldName)
  if (!attr || !selectedField.value) return
  const field = selectedField.value.field
  field.label = attr.displayName || attr.fieldName
  field.dataType = attr.dataType || 'STRING'
  field.uiComponent = attr.uiComponent || 'input'
  field.source = attr.source || 'SYSTEM'
  field.required = attr.required || false
  field.defaultValue = attr.defaultValue || ''
  // 同步控件的使用说明
  const w = getWidgetByType(field.uiComponent)
  if (w?.description) field.description = w.description
  markDirty()
}

// ===== 画布交互 =====
function selectCanvasField(section, idx, groupIdx, subGroupIdx) {
  const list = getZoneList(section)
  if (idx >= 0) {
    let field
    if (subGroupIdx !== undefined) {
      // 3 级：group → layout-row → field
      field = list[groupIdx]?.children?.[subGroupIdx]?.children?.[idx]
    } else if (groupIdx !== undefined) {
      // 2 级：container → field
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
  // 移除主字段
  list.splice(idx, 1)
  // 同时移除伴随的隐藏辅助字段
  const bindFields = removed?.bindFields
  if (bindFields?.length > 1) {
    for (let i = list.length - 1; i >= 0; i--) {
      if (list[i].hidden && bindFields.includes(list[i].fieldName)) {
        list.splice(i, 1)
      }
    }
  }
  if (selectedField.value && selectedField.value.field === removed) {
    selectedField.value = null
  }
}

function removeGroupChild(groupIdx, childIdx) {
  pushUndo()
  const container = layout.form.fields[groupIdx]
  if (!container || (container.type !== 'group' && container.type !== 'layout-row' && container.type !== 'data-table')) return
  const removed = container.children[childIdx]
  container.children.splice(childIdx, 1)
  if (selectedField.value && selectedField.value.field === removed) {
    selectedField.value = null
  }
}

/** 删除分组内布局行的子字段 */
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
  const field = selectedField.value.field
  if (groupIndex !== undefined) {
    const group = layout.form.fields[groupIndex]
    if (group) {
      // 移除主字段
      group.children.splice(index, 1)
      // 同时移除该控件对应的隐藏辅助字段（如 ownerType）
      const bindFields = field.bindFields
      if (bindFields?.length > 1) {
        for (let i = group.children.length - 1; i >= 0; i--) {
          const c = group.children[i]
          if (c.hidden && bindFields.includes(c.fieldName)) {
            group.children.splice(i, 1)
          }
        }
      }
    }
  } else {
    const list = getZoneList(section)
    // 移除主字段
    list.splice(index, 1)
    // 同时移除该控件对应的隐藏辅助字段（如 ownerType 配合 ownerOid）
    const bindFields = field.bindFields
    if (bindFields?.length > 1) {
      for (let i = list.length - 1; i >= 0; i--) {
        const item = list[i]
        if (item.hidden && bindFields.includes(item.fieldName)) {
          list.splice(i, 1)
        }
      }
    }
  }
  selectedField.value = null
}

/** 获取指定索引的表单字段容器 */
function getContainerAt(idx) {
  return layout.form.fields[idx] || null
}

/** 为数据表格添加自定义统计列 */
function addCustomColumn(tableIdx) {
  pushUndo()
  const table = layout.form.fields[tableIdx]
  if (!table || table.type !== 'data-table') return
  const col = {
    fieldName: 'custom_col_' + Date.now(),
    label: '统计列' + (table.children.filter(c => c.isCustomColumn).length + 1),
    source: 'CUSTOM',
    dataType: 'STRING',
    uiComponent: 'input',
    width: 120,
    sortable: false,
    isCustomColumn: true,
    customExpression: ''
  }
  if (!table.children) table.children = []
  table.children.push(col)
  selectedField.value = { section: 'form', field: col, index: table.children.length - 1, groupIndex: tableIdx }
}

/**
 * 数据绑定字段变更：同步选中属性到表格列
 */
function onDataBindChange() {
  pushUndo()
  markDirty()
  const idx = selectedField.value?.index
  if (idx !== undefined) syncDataBindColumns(idx)
}

/**
 * 同步数据绑定字段选择到表格列：选中属性自动转为列，取消选中自动移除对应列
 */
function syncDataBindColumns(tableIdx) {
  const table = layout.form.fields[tableIdx]
  if (!table || table.type !== 'data-table') return
  const selectedFields = table.dataBindField || []
  if (!table.children) table.children = []
  // 拆分：属性列 vs 自定义/统计列
  const customCols = table.children.filter(c => c.isCustomColumn)
  const attrCols = table.children.filter(c => !c.isCustomColumn)
  const existingFieldNames = new Set(attrCols.map(c => c.fieldName))
  // 添加新选中的列
  for (const fieldName of selectedFields) {
    if (existingFieldNames.has(fieldName)) continue
    const attr = availableAttrs.value.find(a => a.fieldName === fieldName)
    if (!attr) continue
    const col = buildFieldFromAttr(attr)
    attrCols.push(col)
  }
  // 移除已取消选中的列
  const selectedSet = new Set(selectedFields)
  const keptAttrCols = attrCols.filter(c => selectedSet.has(c.fieldName))
  // 重新组装 children
  table.children = [...keptAttrCols, ...customCols]
}

// ===== 加载与保存 =====

/**
 * 加载属性定义：
 *   1. 获取当前实体自身的 IBA 属性（来自 ck_type_iba）
 *   2. 沿 parentOid 向上递归收集所有祖先类型的 AttributeDefinition
 *   3. 合并：自身 IBA 优先（同名覆盖祖先），一同展示到属性面板
 *
 * 例如：SOFT_TYPE "BOM" 继承自 DOCUMENT
 *   → 面板展示 DOCUMENT 的全部系统属性 + BOM 自己注册的 IBA 扩展属性
 */
async function loadAttrs() {
  const code = entityCode.value
  const oid = entityOid.value
  if (!code || !oid) return

  // 加载类型定义获取 rootTypeCode（子类型需要知道所属的根 OOTB 对象）
  if (!typeDefinition.value) {
    try {
      const res = await getTypeDefinition(oid)
      if (res?.code === 200 && res.data) {
        typeDefinition.value = res.data
      }
    } catch { /* ignore */ }
  }

  // rootTypeCode 用于查询所属 OOTB 内置对象的 AttributeDefinition
  const rootCode = typeDefinition.value?.rootTypeCode || code

  // 属性定义(根 OOTB 内置类型) + 自身 IBA(创建时已继承父类型)
  const [ownAttrs, ownIbas] = await Promise.all([
    fetchAttrDefs(rootCode, oid, typeKind.value),
    fetchOwnIBAs(oid, code)
  ])

  const merged = new Map()
  for (const a of ownIbas) merged.set(a.fieldName, a)
  for (const a of ownAttrs) merged.set(a.fieldName, a)
  availableAttrs.value = Array.from(merged.values())
}

/** 单次查询属性定义（不递归），失败返回空数组 */
async function fetchAttrDefs(code, oid, typeKind) {
  try {
    const res = await getAttributeDefinitions(code, oid, typeKind)
    return res?.code === 200 ? (res.data || []) : []
  } catch { return [] }
}

/** 将 TypeIBA 映射数据转换为类似 AttributeDefinition 的格式 */
function ibaMappingToAttr(mapping) {
  const dataType = mapping.ibaDataType || 'STRING'
  return {
    fieldName: (mapping.ibaCode || '').toLowerCase(),
    displayName: mapping.ibaDisplayName || mapping.ibaName || mapping.ibaCode || '',
    source: 'IBA',
    dataType: dataType,
    required: mapping.required || false,
    defaultValue: mapping.defaultValue || '',
    sortOrder: mapping.sortOrder || 0,
    enabled: true,
    searchable: true,
    listable: true,
    editable: true,
    uiComponent: resolveUiComponent(dataType),
    oid: mapping.oid,
    ibaOid: mapping.ibaOid
  }
}

/** 根据数据类型推断 UI 组件 */
function resolveUiComponent(dataType) {
  if (!dataType) return 'input'
  switch (dataType.toUpperCase()) {
    case 'TEXT':
    case 'STRING':   return 'input'
    case 'BOOLEAN':  return 'switch'
    case 'INTEGER':
    case 'FLOAT':    return 'input-number'
    case 'DATE':     return 'datepicker'
    case 'DATETIME': return 'datepicker'
    case 'ENUM':     return 'select'
    case 'URL':      return 'input'
    default:         return 'input'
  }
}

/** 获取当前类型在 ck_type_iba 中的 IBA 属性 */
async function fetchOwnIBAs(typeOid, entityCode) {
  try {
    const res = await getTypeMappings(typeOid)
    const data = res?.data || res || []
    return (Array.isArray(data) ? data : []).map(ibaMappingToAttr)
  } catch { return [] }
}

async function saveLayout() {
  saving.value = true
  try {
    const op = operationList.value.find(o => o.code === currentOpCode.value)
    const payload = {
      entityOid: entityOid.value,
      entityCode: entityCode.value,
      operationCode: currentOpCode.value,
      operationName: op ? op.name : currentOpCode.value,
      layoutJson: JSON.stringify(buildLayoutJson())
    }
    const res = await savePageLayoutApi(payload)
    if (res.code === 200) {
      const found = operationList.value.find(o => o.code === currentOpCode.value)
      if (found) found.saved = 'true'
      message.success('布局已保存')
    } else {
      message.error(res.message || '保存失败')
    }
  } catch { message.error('保存布局失败') }
  finally { saving.value = false }
}

async function handleClonePlatformLayout() {
  if (!entityOid.value || !currentOpCode.value) return
  cloning.value = true
  try {
    const res = await clonePageLayout({
      entityOid: entityOid.value,
      entityCode: entityCode.value,
      operationCode: currentOpCode.value
    })
    if (res.code === 200) {
      message.success('平台布局已克隆到本租户，现在可以自由编辑了')
      currentLayoutTenantOid.value = res.data?.tenantOid || null
      // 重新加载操作列表（更新下拉项标签）
      await loadOperations()
      await loadLayoutForOp()
    } else {
      message.error(res.message || '克隆失败')
    }
  } catch { message.error('克隆布局失败') }
  finally { cloning.value = false }
}

function buildLayoutJson() {
  const json = { form: layout.form }
  if (isListOp.value) {
    json.search = layout.search
    json.table = { ...layout.table, columns: layout.table.columns }
  }
  return json
}

function importFromAttrs() {
  pushUndo()
  const unused = availableAttrs.value.filter(a => !isAttrUsed(a.fieldName))
  for (const attr of unused) {
    const field = buildFieldFromAttr(attr)
    if (isListOp.value) {
      if (!layout.table.columns.some(f => f.fieldName === field.fieldName)) {
        layout.table.columns.push({ ...field, width: 150 })
      }
      if (attr.searchable !== false) {
        if (!layout.search.fields.some(f => f.fieldName === field.fieldName)) {
          layout.search.fields.push({ ...field })
        }
      }
    }
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

function goBack() {
  try {
    router.back()
  } catch {
    router.push(typeKind.value === 'SOFT_TYPE' ? '/system/softtype' : '/system/basic')
  }
}

// ===== 键盘快捷键 =====
function onKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
    e.preventDefault()
    e.shiftKey ? redo() : undo()
  }
}

onMounted(async () => {
  if (!entityOid.value) {
    message.warning('缺少实体参数')
    router.back()
    return
  }
  document.addEventListener('keydown', onKeydown)
  await loadOperations()
  if (operationList.value.length) {
    currentOpCode.value = operationList.value[0].code
  }
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
  transition: all 0.3s;
  overflow: hidden;
  min-height: 0;
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
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 3px;
  border: 1px solid transparent;
  transition: all 0.2s;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.md-form-name-text:hover {
  border-color: #d5dce6;
  background: #fff;
}

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

/* 搜索区网格 */
.md-search-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.md-field-chip {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 8px; background: #f0f5ff; border: 1px solid #d6e4ff;
  border-radius: 4px; cursor: pointer; font-size: 12px; transition: all 0.15s;
}
.md-field-chip:hover { border-color: #91caff; background: #e6f4ff; }
.md-field-chip.md-field-selected { border-color: #1464a5; background: #bae0ff; box-shadow: 0 0 0 2px rgba(20,100,165,0.15); }
.md-chip-label { font-weight: 500; color: #333; }
.md-chip-comp { font-size: 11px; color: #999; }
.md-chip-close { visibility: hidden; opacity: 0; padding: 0 !important; min-width: auto !important; height: auto !important; line-height: 1 !important; }
.md-field-chip:hover .md-chip-close { visibility: visible; opacity: 1; }

/* 表格预览 */
.md-table-toolbar-preview {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; background: #fafafa; border: 1px solid #f0f0f0; border-top: none;
}
.md-toolbar-hint { font-size: 11px; color: #ccc; }

/* 工具栏配置面板 */
.md-toolbar-config-panel {
  padding: 10px 12px;
  background: #fafafa;
  border: 1px dashed #e8e8e8;
  border-radius: 4px;
}
.md-toolbar-btn-row {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
}
.md-toolbar-label {
  font-size: 12px; color: #888; white-space: nowrap;
}
.md-table-preview { width: 100%; }
.md-table-header-row, .md-table-data-row {
  display: flex; align-items: center; gap: 0;
  border-bottom: 1px solid #f0f0f0;
}
.md-table-header-row { background: #fafafa; font-weight: 600; font-size: 12px; min-height: 32px; }
.md-table-data-row { font-size: 12px; min-height: 28px; }
.md-table-check-col { width: 36px; flex-shrink: 0; text-align: center; }
.md-table-action-col {
  width: 120px; flex-shrink: 0; text-align: center; font-size: 11px; color: #999;
}
.md-table-th {
  flex: 1; display: flex; align-items: center; gap: 4px;
  padding: 4px 8px; cursor: pointer; transition: all 0.15s;
  border-right: 1px solid #f0f0f0; min-width: 0; overflow: hidden;
}
.md-table-th:hover { background: #e6f4ff; }
.md-table-th.md-field-selected { background: #bae0ff; outline: 2px solid #1464a5; outline-offset: -2px; }
.md-table-td {
  flex: 1; padding: 4px 8px; border-right: 1px solid #f0f0f0; min-width: 0;
}
.md-td-placeholder { color: #ddd; }
.md-th-label { color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.md-th-sort { font-size: 10px; color: #999; }
.md-th-fixed { font-size: 9px; color: #fa8c16; }

/* 表单预览 */
.md-form-preview { display: flex; flex-wrap: wrap; gap: 0; }
.md-form-row {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 6px 8px; cursor: pointer; border-radius: 4px;
  transition: all 0.15s; box-sizing: border-box;
}
/* 垂直布局：标签在上，控件在下 */
.md-form-row-vertical {
  flex-direction: column; gap: 4px; align-items: stretch; padding: 8px;
}
.md-form-row-vertical .md-form-label {
  width: 100%; text-align: left; padding-top: 0;
}
.md-form-row-vertical .md-form-control {
  width: 100%;
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
.md-preview-textarea {
  width: 100%; height: 28px; border: 1px solid #d9d9d9; border-radius: 4px; background: #f5f5f5;
}
.md-preview-textarea-preview {
  width: 100%; height: 60px; border: 1px solid #d9d9d9; border-radius: 4px; background: #fff;
}

/* ===== 预览模式 ===== */
.md-preview-bar {
  padding: 4px 0; font-size: 12px; font-weight: 600; color: #555;
  display: flex; align-items: center; gap: 6px;
  border-bottom: 1px solid #f0f0f0; margin-bottom: 8px;
}
.md-preview-bar :deep(.anticon) { color: #1464a5; }

.md-preview-empty {
  text-align: center; padding: 12px;
}

/* 预览模式画布全宽 */
.md-canvas-wrap:has(.md-canvas-preview) .md-canvas {
  max-width: 100% !important;
}
.md-canvas-preview .md-form-row {
  cursor: default;
}
.md-canvas-preview .md-form-row:hover {
  background: transparent;
}
.md-canvas-preview .md-field-chip {
  cursor: default;
}
.md-canvas-preview .md-field-chip:hover {
  border-color: #d6e4ff; background: #f0f5ff;
}
.md-canvas-preview .md-table-th {
  cursor: default;
}
.md-canvas-preview .md-table-th:hover {
  background: transparent;
}

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
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  background: #f9f0ff;
  border-bottom: 1px solid #efdbff;
  font-size: 11px;
  color: #722ed1;
  user-select: none;
}
.md-layout-row-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.md-layout-row-header-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 10px;
  background: #f9f9f9;
  border-bottom: 1px solid #eee;
  font-size: 11px;
}
.md-layout-row-title-preview {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #888;
}
.md-layout-row-body {
  display: flex;
  flex-wrap: wrap;
  padding: 4px 8px;
  min-height: 40px;
  background: #fafbfc;
}
.md-layout-row-body.md-zone-empty {
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
  margin: 4px 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.md-layout-row-body .md-form-row {
  flex-shrink: 0;
}
.md-canvas-preview .md-layout-row {
  border-color: #e8e8e8;
  cursor: default;
}
.md-canvas-preview .md-layout-row:hover {
  background: transparent;
}

/* 表单分组 */
.md-form-group {
  width: 100% !important;
  margin: 4px 0;
  border: 1px solid #d5dce6;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.15s;
}
.md-form-group.md-field-selected {
  border-color: #1464a5;
  box-shadow: 0 0 0 2px rgba(20,100,165,0.15);
}
.md-form-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  background: #f7f9fc;
  border-bottom: 1px solid #eef1f5;
  font-size: 12px;
  font-weight: 600;
  color: #444;
  user-select: none;
}
.md-group-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.md-group-badge {
  font-size: 10px;
  color: #999;
  background: #eee;
  padding: 1px 6px;
  border-radius: 3px;
}
.md-form-group-body {
  padding: 4px 8px;
  background: #fafbfc;
  min-height: 40px;
}
.md-group-drop-zone {
  min-height: 40px;
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}
.md-group-drop-zone.md-zone-empty {
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
}
.md-canvas-preview .md-form-group {
  border-color: #e8e8e8;
  cursor: default;
}
.md-canvas-preview .md-form-group:hover {
  background: transparent;
}

/* 页面底部 */
.md-page-footer {
  display: flex; align-items: center; gap: 8px;
  padding-top: 16px; margin-top: 16px; border-top: 1px solid #f0f0f0;
}
.md-footer-hint { font-size: 11px; color: #ccc; }

/* ===== 右栏属性面板 ===== */
.md-right-panel {
  width: 280px; background: #fff; border-left: 1px solid #d5dce6;
  display: flex; flex-direction: column; flex-shrink: 0;
}
.md-right-empty { align-items: center; justify-content: center; }
.md-props-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px; border-bottom: 1px solid #f0f0f0;
  font-weight: 600; font-size: 13px; color: #333;
}
.md-props-body { flex: 1; overflow-y: auto; padding: 12px; }
.md-props-body :deep(.ant-form-item) { margin-bottom: 12px; }
.md-props-body :deep(.ant-form-item-label) { padding-bottom: 2px; }
.md-props-body :deep(.ant-form-item-label > label) { font-size: 11px; color: #888; }

/* ===== 滚动条美化 ===== */
.md-panel-content::-webkit-scrollbar,
.md-canvas-scroll::-webkit-scrollbar,
.md-props-body::-webkit-scrollbar { width: 5px; }
.md-panel-content::-webkit-scrollbar-thumb,
.md-canvas-scroll::-webkit-scrollbar-thumb,
.md-props-body::-webkit-scrollbar-thumb {
  background: #c0c8d4; border-radius: 3px;
}

/* ===== 控件使用说明 ===== */
.md-field-doc {
  background: #f0f7ff;
  border: 1px solid #d6e8ff;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 0;
}
.md-field-doc--empty {
  background: #fafafa;
  border-color: #f0f0f0;
}
.md-field-doc-title {
  font-size: 11px;
  font-weight: 600;
  color: #8c8c8c;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.md-field-doc-text {
  font-size: 11px;
  color: #555;
  line-height: 1.5;
}
</style>
