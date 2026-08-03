<template>
  <div class="pl-dashboard">
    <!-- 顶部信息栏 -->
    <header class="pl-header">
      <div class="pl-header-main">
        <div class="pl-header-info">
          <a-breadcrumb class="pl-breadcrumb">
            <a-breadcrumb-item><router-link to="/product">产品系列</router-link></a-breadcrumb-item>
            <!-- 产品型号：显示所属产品系列 -->
            <a-breadcrumb-item v-if="line?.nodeType === 'PRODUCT_MODEL'">
              {{ parentLineName || '加载中...' }}
            </a-breadcrumb-item>
            <!-- 产品系列且有父级：显示父系列 -->
            <a-breadcrumb-item v-else-if="line?.parentOid && parentLineName">
              {{ parentLineName }}
            </a-breadcrumb-item>
            <a-breadcrumb-item>{{ line?.name || '加载中...' }}</a-breadcrumb-item>
          </a-breadcrumb>
          <div class="pl-title-row">
            <a-tag :color="line?.nodeType === 'PRODUCT_MODEL' ? 'purple' : (line?.parentOid ? 'green' : 'blue')" size="small">
              {{ line?.nodeType === 'PRODUCT_MODEL' ? '产品型号' : (line?.parentOid ? '子系列' : '根系列') }}
            </a-tag>
            <h2 class="pl-title">{{ line?.name || '—' }}</h2>
            <a-tag v-if="line?.code" color="default" class="pl-code-tag">{{ line.code }}</a-tag>
          </div>
          <p class="pl-meta" v-if="line">
            <template v-if="line.description">{{ line.description }}<span class="pl-meta-sep" /></template>
            <span>创建于 {{ formatTime(line.createdAt) }}</span>
          </p>
        </div>

        <div class="pl-header-stats" v-if="line">
          <!-- 产品系列：显示子系列数量 -->
          <template v-if="line.nodeType !== 'PRODUCT_MODEL'">
            <div
              class="pl-stat-pill"
              :class="{ 'pl-stat-pill--active': showChildren }"
              @click="showChildren = !showChildren"
            >
              <TeamOutlined class="pl-stat-pill-icon" style="color:#52c41a" />
              <span class="pl-stat-pill-num">{{ children.length }}</span>
              <span class="pl-stat-pill-label">子系列</span>
              <CaretDownOutlined v-if="children.length" class="pl-stat-pill-arrow" :class="{ up: showChildren }" />
            </div>
            <div class="pl-stat-pill">
              <GoldOutlined class="pl-stat-pill-icon" style="color:#1677ff" />
              <span class="pl-stat-pill-num">{{ modelCount }}</span>
              <span class="pl-stat-pill-label">型号</span>
            </div>
          </template>
          <!-- 产品型号：不显示子系列和型号数量 -->
          <div class="pl-stat-pill">
            <ExperimentOutlined class="pl-stat-pill-icon" style="color:#722ed1" />
            <span class="pl-stat-pill-num">{{ stageDefs.length }}</span>
            <span class="pl-stat-pill-label">阶段</span>
          </div>
        </div>
      </div>

      <!-- 子系列展开（仅产品系列显示） -->
      <transition name="pl-collapse">
        <div v-if="line && line.nodeType !== 'PRODUCT_MODEL' && showChildren && children.length" class="pl-children-panel">
          <div class="pl-children-header">
            <TeamOutlined /> 子产品系列 ({{ children.length }})
          </div>
          <div class="pl-children-grid">
            <a-card
              v-for="child in children" :key="child.oid"
              class="pl-child-card" hoverable size="small"
              @click="router.push(`/product/${child.oid}`)"
            >
              <span class="pl-child-code">{{ child.code }}</span>
              <span class="pl-child-name">{{ child.name }}</span>
              <span v-if="child.description" class="pl-child-desc">{{ child.description }}</span>
            </a-card>
          </div>
        </div>
      </transition>
    </header>

    <!-- 研发阶段管线 -->
    <section class="pl-pipeline">
      <a-tabs v-model:activeKey="activeStage" class="pl-stage-tabs">
        <a-tab-pane v-for="(stage, idx) in stageDefs" :key="stage.oid">
          <template #tab>
            <span class="pl-tab-label">
              <a-badge :count="idx + 1" :number-style="{ backgroundColor: stage.color, boxShadow: 'none' }" />
              <component :is="stage.icon" class="pl-tab-icon" :style="{ color: stage.color }" />
              <span>{{ stage.title }}</span>
            </span>
          </template>

          <!-- 过程资料区域 -->
          <div class="pl-folders" v-if="line">

            <div v-if="folderTree.length === 0 && parentFolderTree.length === 0" class="pl-folders-empty">
              <InboxOutlined class="pl-folders-empty-icon" />
              <p>暂无文件夹</p>
              <a-button type="dashed" size="small" @click="openCreateFolder(null)">
                <FolderAddOutlined /> 新建文件夹
              </a-button>
            </div>

            <div v-else class="pl-folders-body">
              <!-- 左侧树：合并显示（同名节点合一） -->
              <div class="pl-folders-tree">
                <FolderTreeNode
                  v-for="node in mergedTree" :key="node.oid"
                  :node="node"
                  :selected-folder-oid="selectedFolder?.oid"
                  :expanded-folders="expandedFolders"
                  @select="selectFolder"
                  @toggle="toggleFolder"
                  @create-sub="openCreateFolder"
                  @rename="openRenameFolder"
                  @delete="removeFolder"
                  @clone-inherited="cloneInheritedFolder"
                />
                <a-button type="dashed" size="small" block style="margin-top: 8px" @click="openCreateFolder(null)">
                  <FolderAddOutlined /> 新建文件夹
                </a-button>
              </div>

              <!-- 右侧内容 -->
              <div class="pl-folders-content">
                <div v-if="!selectedFolder" class="pl-folders-content-empty">
                  <FileTextOutlined class="pl-folders-content-empty-icon" />
                  <p>点击左侧文件夹查看过程资料</p>
                </div>
                <!-- 合并节点：Tab 切换 继承资料 / 自有资料 -->
                <template v-else-if="selectedFolder._mergedInherited">
                  <div class="pl-folders-content-files">
                    <a-tabs v-model:activeKey="mergedDocTab" size="small" class="pl-merged-tabs">
                    <a-tab-pane key="inherited" tab="继承资料">
                      <template #tab>
                        <span><LinkOutlined style="font-size:11px" /> 继承资料 <a-tag color="default" size="small" style="margin-left:4px">{{ parentName }}</a-tag></span>
                      </template>
                      <DataTable
                        :columns="inheritedDocColumns"
                        :data-source="filteredInheritedDocuments"
                        :loading="inheritedDocLoading"
                        :pagination="false"
                        row-key="oid"
                        size="small"
                        :searchable="true"
                        search-placeholder="搜索继承资料..."
                        :search-fields="['code', 'name', 'typeDefinitionName']"
                      >
                        <template #toolbar>
                          <a-button v-if="!isMarketValidation" type="primary" size="small" @click="openCreatePart" style="margin-right:8px">
                            <PlusOutlined /> 创建零组件
                          </a-button>
                          <a-button size="small" @click="openCreateDocument">
                            <PlusOutlined /> 创建文档
                          </a-button>
                        </template>
                        <template #bodyCell="{ column, record }">
                          <template v-if="column.key === 'checkout_status'">
                            <a-tooltip v-if="record.checkedOut" placement="top">
                              <template #title>
                                <div>检出人: {{ record.checkedOutBy || '-' }}</div>
                                <div v-if="record.checkedOutComment">注释: {{ record.checkedOutComment }}</div>
                                <div v-if="record.checkedOutAt">时间: {{ record.checkedOutAt ? record.checkedOutAt.substring(0,19).replace('T',' ') : '' }}</div>
                              </template>
                              <LockOutlined style="color:#fa8c16;font-size:13px" />
                            </a-tooltip>
                          </template>
                          <template v-else-if="column.key === 'code'">
                            <a-tag color="blue" size="small">{{ record.code || '-' }}</a-tag>
                          </template>
                          <template v-else-if="column.key === 'typeDefinitionName'">
                            <a-tag color="default" size="small">{{ record.typeDefinitionName || record.typeDefinitionCode || '-' }}</a-tag>
                          </template>
                          <template v-else-if="column.key === 'status'">
                            <a-tag :color="statusColor(record.statusCode)" size="small">{{ record.statusName || record.statusCode || '-' }}</a-tag>
                          </template>
                          <template v-else-if="column.key === 'checkout'">
                            <a-tag v-if="record.checkedOut" color="orange" size="small">已检出: {{ record.checkedOutBy }}</a-tag>
                            <a-tag v-else color="green" size="small">已检入</a-tag>
                          </template>
                          <template v-else-if="column.key === 'action'">
                            <a-dropdown :trigger="['click']">
                              <a-button type="link" size="small">操作 <DownOutlined style="font-size:10px;margin-left:2px" /></a-button>
                              <template #overlay>
                                <a-menu @click="(e) => handleDocAction(e, record)">
                                  <a-menu-item key="view"><EyeOutlined /> 详情</a-menu-item>
                                  <a-menu-item v-if="!record.checkedOut" key="checkout"><LockOutlined /> 检出</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="edit"><EditOutlined /> 编辑</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="checkin"><CheckOutlined /> 检入</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="undoCheckout"><RollbackOutlined /> 取消检出</a-menu-item>
                                  <a-menu-divider />
                                  <a-menu-item v-if="record.ckfileOid" key="download"><DownloadOutlined /> 下载主文件</a-menu-item>
                                </a-menu>
                              </template>
                            </a-dropdown>
                          </template>
                        </template>
                      </DataTable>
                    </a-tab-pane>
                    <a-tab-pane key="own" tab="自有资料">
                      <DataTable
                        :columns="docColumns"
                        :data-source="filteredDocuments"
                        :loading="docLoading"
                        :pagination="false"
                        row-key="oid"
                        size="small"
                        :searchable="true"
                        search-placeholder="搜索资料..."
                        :search-fields="['code', 'name', 'typeDefinitionName']"
                      >
                        <template #toolbar-left>
                          <div class="pl-content-bar-inline">
                            <FolderOutlined style="color:#faad14;font-size:14px" />
                            <span style="color:#999;font-size:12px;margin-right:4px">当前位置：</span>
                            <strong style="font-size:13px">{{ getFolderPath(selectedFolder) }}</strong>
                            <a-tag v-if="selectedFolder.children?.length" color="blue" size="small">
                              {{ selectedFolder.children.length }} 个子目录
                            </a-tag>
                          </div>
                        </template>
                        <template #toolbar>
                          <a-button v-if="!isMarketValidation" type="primary" size="small" @click="openCreatePart" style="margin-right:8px">
                            <PlusOutlined /> 创建零组件
                          </a-button>
                          <a-button size="small" @click="openCreateDocument">
                            <PlusOutlined /> 创建文档
                          </a-button>
                        </template>
                        <template #bodyCell="{ column, record }">
                          <template v-if="column.key === 'checkout_status'">
                            <a-tooltip v-if="record.checkedOut" placement="top">
                              <template #title>
                                <div>检出人: {{ record.checkedOutBy || '-' }}</div>
                                <div v-if="record.checkedOutComment">注释: {{ record.checkedOutComment }}</div>
                                <div v-if="record.checkedOutAt">时间: {{ record.checkedOutAt ? record.checkedOutAt.substring(0,19).replace('T',' ') : '' }}</div>
                              </template>
                              <LockOutlined style="color:#fa8c16;font-size:13px" />
                            </a-tooltip>
                          </template>
                          <template v-else-if="column.key === 'code'">
                            <a-tag color="blue" size="small">{{ record.code || '-' }}</a-tag>
                          </template>
                          <template v-else-if="column.key === 'typeDefinitionName'">
                            <a-tag color="default" size="small">{{ record.typeDefinitionName || record.typeDefinitionCode || '-' }}</a-tag>
                          </template>
                          <template v-else-if="column.key === 'status'">
                            <a-tag :color="statusColor(record.statusCode)" size="small">{{ record.statusName || record.statusCode || '-' }}</a-tag>
                          </template>
                          <template v-else-if="column.key === 'checkout'">
                            <a-tag v-if="record.checkedOut" color="orange" size="small">已检出: {{ record.checkedOutBy }}</a-tag>
                            <a-tag v-else color="green" size="small">已检入</a-tag>
                          </template>
                          <template v-else-if="column.key === 'action'">
                            <a-dropdown :trigger="['click']">
                              <a-button type="link" size="small">操作 <DownOutlined style="font-size:10px;margin-left:2px" /></a-button>
                              <template #overlay>
                                <a-menu @click="(e) => handleDocAction(e, record)">
                                  <a-menu-item key="view"><EyeOutlined /> 详情</a-menu-item>
                                  <a-menu-item v-if="!record.checkedOut" key="checkout"><LockOutlined /> 检出</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="edit"><EditOutlined /> 编辑</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="checkin"><CheckOutlined /> 检入</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="undoCheckout"><RollbackOutlined /> 取消检出</a-menu-item>
                                  <a-menu-item v-if="record.ckfileOid" key="download"><DownloadOutlined /> 下载主文件</a-menu-item>
                                  <a-menu-divider />
                                  <a-menu-item key="move"><SwapOutlined /> 移动</a-menu-item>
                                  <a-menu-divider />
                                  <a-menu-item key="lifecycle"><ExperimentOutlined /> 设置生命周期状态</a-menu-item>
                                  <a-menu-item key="workflow"><SendOutlined /> 发起流程</a-menu-item>
                                  <a-menu-divider />
                                  <a-menu-item key="delete" danger><DeleteOutlined /> 删除</a-menu-item>
                                </a-menu>
                              </template>
                            </a-dropdown>
                          </template>
                        </template>
                      </DataTable>
                    </a-tab-pane>
                  </a-tabs>
                  </div>
                </template>
                <!-- 纯继承文件夹：只读 -->
                <template v-else-if="selectedFolder._inherited">
                  <div class="pl-folders-content-bar pl-folders-content-bar--inherited">
                    <LinkOutlined style="color:#8c8c8c" />
                    <strong>{{ selectedFolder.name }}</strong>
                    <a-tag color="default" size="small">继承 · 只读</a-tag>
                    <span style="margin-left: auto;">
                      <a-button type="dashed" size="small" @click="cloneInheritedFolder(selectedFolder)">
                        <CopyOutlined /> 克隆到本产品
                      </a-button>
                    </span>
                  </div>
                  <div class="pl-folders-content-files">
                    <EyeOutlined class="pl-folders-content-files-icon" style="font-size:36px;opacity:.3" />
                    <p>此文件夹继承自「{{ parentName }}」</p>
                    <p class="pl-folders-content-hint">点击「克隆到本产品」可创建同名可写文件夹</p>
                  </div>
                </template>
                <!-- 自有文件夹：文档列表 -->
                <template v-else>
                  <div class="pl-folders-content-files">
                    <DataTable
                      :columns="docColumns"
                      :data-source="filteredDocuments"
                      :loading="docLoading"
                      :pagination="false"
                      row-key="oid"
                      size="small"
                      :searchable="true"
                      search-placeholder="搜索文档..."
                      :search-fields="['code', 'name', 'typeDefinitionName']"
                    >
                      <template #toolbar-left>
                        <div class="pl-content-bar-inline">
                          <FolderOutlined style="color:#faad14;font-size:14px" />
                          <span style="color:#999;font-size:12px;margin-right:4px">当前位置：</span>
                          <strong style="font-size:13px">{{ getFolderPath(selectedFolder) }}</strong>
                          <a-tag v-if="selectedFolder.children?.length" color="blue" size="small">
                            {{ selectedFolder.children.length }} 个子目录
                          </a-tag>
                        </div>
                      </template>
                      <template #toolbar>
                        <a-button v-if="!isMarketValidation" type="primary" size="small" @click="openCreatePart" style="margin-right:8px">
                          <PlusOutlined /> 创建零组件
                        </a-button>
                        <a-button size="small" @click="openCreateDocument">
                          <PlusOutlined /> 创建文档
                        </a-button>
                      </template>
                      <template #bodyCell="{ column, record }">
                        <template v-if="column.key === 'checkout_status'">
                          <a-tooltip v-if="record.checkedOut" placement="top">
                            <template #title>
                              <div>检出人: {{ record.checkedOutBy || '-' }}</div>
                              <div v-if="record.checkedOutComment">注释: {{ record.checkedOutComment }}</div>
                              <div v-if="record.checkedOutAt">时间: {{ record.checkedOutAt ? record.checkedOutAt.substring(0,19).replace('T',' ') : '' }}</div>
                            </template>
                            <LockOutlined style="color:#fa8c16;font-size:13px" />
                          </a-tooltip>
                        </template>
                        <template v-else-if="column.key === 'code'">
                          <a-tag color="blue" size="small">{{ record.code || '-' }}</a-tag>
                        </template>
                        <template v-else-if="column.key === 'typeDefinitionName'">
                          <a-tag color="default" size="small">{{ record.typeDefinitionName || record.typeDefinitionCode || '-' }}</a-tag>
                        </template>
                        <template v-else-if="column.key === 'status'">
                          <a-tag :color="statusColor(record.statusCode)" size="small">{{ record.statusName || record.statusCode || '-' }}</a-tag>
                        </template>
                        <template v-else-if="column.key === 'checkout'">
                          <a-tag v-if="record.checkedOut" color="orange" size="small">已检出: {{ record.checkedOutBy }}</a-tag>
                          <a-tag v-else color="green" size="small">已检入</a-tag>
                        </template>
                        <template v-else-if="column.key === 'action'">
                                                        <a-dropdown :trigger="['click']">
                                <a-button type="link" size="small">
                                  操作 <DownOutlined style="font-size:10px;margin-left:2px" />
                                </a-button>
                                <template #overlay>
                                  <a-menu @click="(e) => handleDocAction(e, record)">
                                    <a-menu-item key="view"><EyeOutlined /> 详情</a-menu-item>
                                    <a-menu-item v-if="!record.checkedOut" key="checkout"><LockOutlined /> 检出</a-menu-item>
                                    <a-menu-item v-if="canUndoCheckout(record)" key="edit"><EditOutlined /> 编辑</a-menu-item>
                                    <a-menu-item v-if="canUndoCheckout(record)" key="checkin"><CheckOutlined /> 检入</a-menu-item>
                                    <a-menu-item v-if="canUndoCheckout(record)" key="undoCheckout"><RollbackOutlined /> 取消检出</a-menu-item>
                                    <a-menu-item v-if="record.ckfileOid" key="download"><DownloadOutlined /> 下载主文件</a-menu-item>
                                    <a-menu-divider />
                                    <a-menu-item key="move"><SwapOutlined /> 移动</a-menu-item>
                                    <a-menu-divider />
                                    <a-menu-item key="lifecycle"><ExperimentOutlined /> 设置生命周期状态</a-menu-item>
                                    <a-menu-item key="workflow"><SendOutlined /> 发起流程</a-menu-item>
                                    <a-menu-divider />
                                    <a-menu-item key="delete" danger><DeleteOutlined /> 删除</a-menu-item>
                                  </a-menu>
                                </template>
                              </a-dropdown>
                        </template>
                      </template>
                    </DataTable>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </section>

    <!-- 文件夹弹窗 -->
    <a-modal
      v-model:visible="folderModalVisible"
      :title="editingFolder ? '重命名文件夹' : '新建文件夹'"
      :ok-text="editingFolder ? '保存' : '创建'"
      cancel-text="取消"
      @ok="confirmFolder"
    >
      <a-input v-model:value="folderModalName" placeholder="请输入文件夹名称" @keyup.enter="confirmFolder" />
    </a-modal>

    <!-- 文档创建弹窗：先选类型 → 再按类型专属布局渲染表单 -->
    <a-modal
      v-model:visible="docModalVisible"
      title="创建文档"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="docModalSaving"
      :ok-button-props="{ disabled: !selectedDocType }"
      @ok="confirmDocument"
      @cancel="resetDocModal"
      width="620px"
    >
      <div style="margin-bottom: 16px">
        <div style="margin-bottom:4px;font-size:12px;color:#666">选择文档类型</div>
        <DocumentTypeSelect
          v-model="docTypeSelectValue"
          placeholder="请选择文档类型（必选）"
          @change="onDocTypeChange"
        />
      </div>

      <a-divider v-if="selectedDocType" style="margin:12px 0" />

      <div v-if="selectedDocType" style="margin-top:8px">
        <a-tag color="blue" style="margin-bottom:12px">{{ selectedDocType.name || selectedDocType.code }} - 创建表单</a-tag>
        <DynamicForm
          ref="docFormRef"
          :key="docFormEntityCode"
          :entity-code="docFormEntityCode"
          operation-code="create"
          fallback-entity-code="DOCUMENT"
          :current-container-oid="line?.oid"
          :current-stage-oid="activeStage"
          :stage-options="stageOptions"
          v-model="docForm"
        />
      </div>
      <div v-else style="padding:24px 0;text-align:center;color:#999">
        <InboxOutlined style="font-size:32px;color:#d9d9d9;margin-bottom:8px;display:block" />
        请先选择文档类型，系统将加载对应的创建表单
      </div>
    </a-modal>

    <!-- 零组件创建弹窗 -->
    <a-modal
      v-model:visible="partModalVisible"
      title="创建零组件"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="partModalSaving"
      :ok-button-props="{ disabled: !selectedPartType }"
      @ok="confirmPart"
      @cancel="resetPartModal"
      width="640px"
    >
      <div style="margin-bottom:8px">
        <span style="color:#8c8c8c">选择零组件类型</span>
        <a-select
          v-model:value="partTypeSelectValue"
          style="width:100%;margin-top:4px"
          placeholder="请选择零组件类型"
          :options="partTypeOptions"
          :field-names="{ label: 'name', value: 'code' }"
          show-search
          :filter-option="(input, option) => (option?.name || option?.label || '').toLowerCase().includes((input||'').toLowerCase())"
          @change="onPartTypeChange"
        />
      </div>

      <a-divider v-if="selectedPartType" style="margin:12px 0" />

      <div v-if="selectedPartType" style="margin-top:8px">
        <a-tag color="blue" style="margin-bottom:12px">{{ selectedPartType.name || selectedPartType.code }} - 创建表单</a-tag>
        <DynamicForm
          ref="partFormRef"
          :key="partFormEntityCode"
          :entity-code="partFormEntityCode"
          operation-code="create"
          :context="{ containerType: 'PRODUCT_LINE', containerOid: line?.oid, currentStageOid: activeStage, folderOid: selectedFolder?.oid, stageOid: activeStage }"
          :current-stage-oid="activeStage"
          :stage-options="stageOptions"
          v-model="partForm"
        />
      </div>
      <div v-else style="padding:24px 0;text-align:center;color:#999">
        <InboxOutlined style="font-size:32px;color:#d9d9d9;margin-bottom:8px;display:block" />
        请先选择零组件类型，系统将加载对应的创建表单
      </div>
    </a-modal>

    <!-- 检出注释弹窗 -->
    <a-modal
      v-model:visible="checkoutModalVisible"
      title="检出文档"
      ok-text="确认检出"
      cancel-text="取消"
      :confirm-loading="checkoutSaving"
      :ok-button-props="{ disabled: !checkoutComment.trim() }"
      @ok="confirmCheckout"
      @cancel="checkoutModalVisible = false"
    >
      <div class="pl-checkout-info" v-if="checkoutTargetDoc">
        <div class="pl-checkout-doc-line">
          <a-tag color="blue" size="small">{{ checkoutTargetDoc.code || '-' }}</a-tag>
          <span class="pl-checkout-doc-name">{{ checkoutTargetDoc.name || '-' }}</span>
          <span class="pl-checkout-doc-sep">·</span>
          <span class="pl-checkout-doc-version">{{ checkoutTargetDoc.displayVersion || '-' }}</span>
          <a-tag :color="statusColor(checkoutTargetDoc.statusCode)" size="small" style="margin-left:6px">
            {{ checkoutTargetDoc.statusName || checkoutTargetDoc.statusCode || '-' }}
          </a-tag>
        </div>
        <div style="margin-top:10px;margin-bottom:4px;font-size:12px;color:#666">检出注释</div>
        <a-textarea
          v-model:value="checkoutComment"
          placeholder="请输入检出注释（如：修改方向、检出原因等）"
          :rows="3"
          :maxlength="500"
          show-count
        />
      </div>
    </a-modal>

    <!-- 文档详情查看器 -->
    <DocumentViewer
      v-model:visible="docViewerVisible"
      :doc="docViewerDoc"
      @close="docViewerVisible = false"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  AuditOutlined, BulbOutlined, FundProjectionScreenOutlined, ToolOutlined,
  RocketOutlined, ShoppingCartOutlined,
  TeamOutlined, GoldOutlined, ExperimentOutlined, CaretDownOutlined,
  FolderOutlined, FolderAddOutlined,
  FileTextOutlined, InboxOutlined, PlusOutlined, LinkOutlined, EyeOutlined,
  DeleteOutlined, CopyOutlined, DownOutlined,
  EditOutlined, CheckOutlined, SwapOutlined, SendOutlined, LockOutlined, RollbackOutlined, DownloadOutlined,
} from '@ant-design/icons-vue'
import { getProductLine, getProductLineChildren, getFolderTree, createFolder, updateFolder, deleteFolder,
         getDocuments, createDocument, deleteDocument, checkoutDocument as checkoutDocApi, undoCheckoutDocument as undoCheckoutApi,
         getStages, getFolderDocumentDetails, getDocumentDownloadUrl, createPart, getTypeDefinitionTree } from '@/api'
import { useUserStore } from '@/stores/user'
import FolderTreeNode from './FolderTreeNode.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import DocumentTypeSelect from '@/components/DocumentTypeSelect.vue'
import DataTable from '@/components/DataTable.vue'
import DocumentViewer from './DocumentViewer.vue'
import { recordOperation } from '@/composables/useActivity'
import { registerDynamicStages } from '@/utils/stageDefs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const line = ref(null)
const children = ref([])
const modelCount = ref(0)
const showChildren = ref(false)
const activeStage = ref(null) // 存储当前阶段的 oid

/** 当前阶段是否为市场验证阶段（不需要创建零组件，只需交付文档） */
const isMarketValidation = computed(() => {
  const stage = stageDefs.value.find(s => s.oid === activeStage.value)
  return stage?.key === 'MARKET_VALIDATION'
})

const folderTree = ref([])
const parentFolderTree = ref([])
const parentName = ref('')
const parentLineName = ref('') // 父级产品系列名称（用于面包屑显示）
const expandedFolders = ref({})
const folderModalVisible = ref(false)
const folderModalName = ref('')
const folderModalParentOid = ref(null)
const editingFolder = ref(null)
const selectedFolder = ref(null)

/** 递归获取文件夹从根到当前节点的全路径 */
function getFolderPath(node) {
  if (!node) return ''
  const pathParts = []
  let current = node
  while (current) {
    pathParts.unshift(current.name)
    current = current._parent || null
  }
  return pathParts.join(' / ')
}

/** 递归给每个节点设置 _parent 引用 */
function setParentsRecursive(nodes, parent) {
  if (!nodes) return
  for (const n of nodes) {
    n._parent = parent
    if (n.children?.length) setParentsRecursive(n.children, n)
  }
}

/** 合并树：同名继承文件夹与自有文件夹合并为单节点 */
const mergedTree = computed(() => {
  const ownByName = {}
  for (const node of folderTree.value) {
    ownByName[node.name] = node
  }
  const result = []
  const matchedOwnNames = new Set()
  // 先处理继承节点：同名则合并到自有节点
  for (const inh of parentFolderTree.value) {
    const own = ownByName[inh.name]
    if (own) {
      own._mergedInherited = inh
      matchedOwnNames.add(own.name)
      result.push(own)
    } else {
      result.push(inh)  // 纯继承节点
    }
  }
  // 自有没有匹配到的节点
  for (const node of folderTree.value) {
    if (!matchedOwnNames.has(node.name)) {
      result.push(node)
    }
  }
  return result
})

// ==================== 文档状态 ====================
const inheritedDocuments = ref([])  // 合并节点时继承文件夹中的文档
const mergedDocTab = ref('own')    // 合并节点当前 Tab：inherited / own
const docModalVisible = ref(false)
const docModalSaving = ref(false)
const docFormRef = ref(null)
const docForm = ref({ name: '', typeDefinitionCode: '', description: '', stageOid: '', folderOid: '', containerOid: '', containerType: '', ckfileOid: '', attachmentOid: '' })
/** DocumentTypeSelect 自身的 v-model 值（oid） */
const docTypeSelectValue = ref(undefined)
/** 用户选中的文档类型节点数据 { oid, code, name } */
const selectedDocType = ref(null)
/** 当前 DynamicForm 使用的 entityCode（类型专属 code，或回退到 DOCUMENT） */
const docFormEntityCode = ref('DOCUMENT')

// ==================== 零组件创建 ====================
const partModalVisible = ref(false)
const partModalSaving = ref(false)
const partFormRef = ref(null)
const partForm = ref({ name: '', typeDefinitionCode: '', description: '', stageOid: '', folderOid: '', containerOid: '', containerType: '', classificationOid: '', ckfileOid: '', attachmentOid: '' })
const partTypeSelectValue = ref(undefined)
const selectedPartType = ref(null)
const partFormEntityCode = ref('PART')
const partTypeOptions = ref([])
const partTypeLoading = ref(false)

async function loadPartTypes() {
  partTypeLoading.value = true
  try {
    const res = await getTypeDefinitionTree()
    const tree = res?.data || res || []
    // 找到 PART 根节点，收集它及其所有子孙类型
    const flat = []
    const findPartNode = (nodes) => {
      for (const n of nodes) {
        if (n.code === 'PART') {
          collectAllDescendants(n, flat)
          return
        }
        if (n.children) findPartNode(n.children)
      }
    }
    findPartNode(Array.isArray(tree) ? tree : [])
    partTypeOptions.value = flat
  } catch { partTypeOptions.value = [] }
  finally { partTypeLoading.value = false }
}

function collectAllDescendants(node, result) {
  if (node.oid && node.code) result.push(node)
  if (node.children) {
    for (const child of node.children) {
      collectAllDescendants(child, result)
    }
  }
}

function openCreatePart() {
  partTypeSelectValue.value = undefined
  selectedPartType.value = null
  partFormEntityCode.value = 'PART'
  const isModel = line.value?.nodeType === 'PRODUCT_MODEL'
  const plOid = isModel ? line.value?.parentOid : line.value?.oid
  partForm.value = {
    name: '',
    typeDefinitionCode: '',
    description: '',
    stageOid: activeStage.value || stageDefs.value[0]?.oid || '',
    folderOid: selectedFolder.value?.oid || '',
    containerOid: plOid || '',
    containerType: isModel ? 'PRODUCT_MODEL' : 'PRODUCT_LINE',
    classificationOid: '',
    ckfileOid: '',
    attachmentOid: '',
  }
  loadPartTypes()
  partModalVisible.value = true
}

function onPartTypeChange(typeCode) {
  if (typeCode) {
    const found = partTypeOptions.value.find(t => t.code === typeCode)
    selectedPartType.value = found || { code: typeCode, name: typeCode }
    partFormEntityCode.value = typeCode
    partForm.value.typeDefinitionCode = typeCode
  } else {
    selectedPartType.value = null
    partFormEntityCode.value = 'PART'
    partForm.value.typeDefinitionCode = ''
  }
}

function resetPartModal() {
  partTypeSelectValue.value = undefined
  selectedPartType.value = null
  partFormEntityCode.value = 'PART'
  partForm.value = {
    name: '', typeDefinitionCode: '', description: '',
    stageOid: '', folderOid: '', containerOid: '', containerType: '',
    classificationOid: '', ckfileOid: '', attachmentOid: '',
  }
}

async function confirmPart() {
  if (!partForm.value.name?.trim()) { message.warning('请输入零组件名称'); return }
  partModalSaving.value = true
  try {
    const res = await createPart(partForm.value)
    if (res.code === 200) {
      message.success('零组件创建成功')
      partModalVisible.value = false
      resetPartModal()
    } else {
      message.error(res.message || '创建失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '创建零组件失败')
  } finally { partModalSaving.value = false }
}

// 研发阶段（从后端 API 动态加载，替换硬编码字典）
const stageDefs = ref([])

/** 仪表盘可见阶段的选项列表 [{label, value}]，供 stage-select 控件使用 */
const stageOptions = computed(() =>
  stageDefs.value.map(s => ({ label: s.title || s.name || s.oid, value: s.oid }))
)

/** 图标名称 → Vue 组件映射（后端存 icon 名称字符串，前端按名查找组件） */
const iconComponentMap = {
  ShoppingCartOutlined, AuditOutlined, BulbOutlined,
  FundProjectionScreenOutlined, ToolOutlined, RocketOutlined,
  // 扩展：用户自定义阶段时可选择更多图标
  ExperimentOutlined, GoldOutlined, TeamOutlined,
  FileTextOutlined, FolderOutlined, InboxOutlined,
}

/** 根据 icon 名称字符串获取对应的 Vue 组件引用 */
const resolveIcon = (iconName) => {
  if (!iconName) return ShoppingCartOutlined
  return iconComponentMap[iconName] || iconComponentMap[Object.keys(iconComponentMap).find(
    k => k.toLowerCase() === iconName?.toLowerCase()
  )] || ShoppingCartOutlined
}

/**
 * 将后端 Stage 对象转换为前端 stageDefs 条目格式。
 * 后端 Stage: { oid, code, name, icon, color, description, sortOrder, defaultFolders, showOnDashboard }
 * 前端条目:   { oid, key, title, icon(Vue组件), color, description, items(string[]), showOnDashboard }
 */
const mapStageToDef = (stage) => ({
  oid: stage.oid,
  key: stage.code,
  title: stage.name,
  icon: resolveIcon(stage.icon),
  color: stage.color || '#1677ff',
  description: stage.description || '',
  items: (() => {
    try { return JSON.parse(stage.defaultFolders || '[]') }
    catch { return [] }
  })(),
  showOnDashboard: stage.showOnDashboard,
  // 保留原始 stage 对象引用，用于后续更新
  _stage: stage,
})

/* ==================== 工具函数 ==================== */
const formatTime = (s) => s ? s.replace('T', ' ').substring(0, 19) : '-'

/* ==================== 继承文件夹标记 ==================== */
const markInheritedRecursive = (nodes, sourceName) => {
  for (const n of nodes) {
    n._inherited = true
    n._sourceName = sourceName
    if (n.children?.length) markInheritedRecursive(n.children, sourceName)
  }
}

const onInheritedAction = () => {
  message.info('继承的文件夹仅可查看，无法编辑或删除')
}

/* ==================== 文件夹操作 ==================== */
const toggleFolder = (node) => { expandedFolders.value[node.oid] = !expandedFolders.value[node.oid] }
const selectFolder = (node) => {
  selectedFolder.value = node
  inheritedDocuments.value = []
  docTypeFilter.value = ''  // 重置类型筛选
  if (node && node._mergedInherited) {
    // 合并节点：同时加载继承资料和自有资料
    loadInheritedDocuments(node._mergedInherited.oid)
    loadDocuments(node.oid)
  } else if (node && !node._inherited) {
    loadDocuments(node.oid)
  } else {
    documents.value = []
  }
}

const openCreateFolder = (parentOid) => {
  editingFolder.value = null
  folderModalParentOid.value = parentOid
  folderModalName.value = ''
  folderModalVisible.value = true
}

const openRenameFolder = (folder) => {
  if (folder._inherited) return onInheritedAction()
  editingFolder.value = folder
  folderModalParentOid.value = null
  folderModalName.value = folder.name
  folderModalVisible.value = true
}

const confirmFolder = async () => {
  const name = folderModalName.value.trim()
  if (!name) return message.warning('请输入文件夹名称')
  try {
    if (editingFolder.value) {
      await updateFolder(editingFolder.value.oid, { name })
      message.success('已重命名')
    } else {
      await createFolder({ ownerOid: line.value.oid, stageOid: activeStage.value, parentFolderOid: folderModalParentOid.value, name, sortOrder: 0 })
      message.success('已创建')
    }
    folderModalVisible.value = false
    loadFolders()
  } catch { message.error('操作失败') }
}

const removeFolder = async (oid) => {
  try { await deleteFolder(oid); message.success('已删除'); loadFolders() }
  catch { message.error('删除失败') }
}

/* ==================== 文档操作 ==================== */
const documents = ref([])
const docLoading = ref(false)

/** DataTable 列定义 */
const docColumns = [
  { title: '', dataIndex: 'checkedOut', key: 'checkout_status', width: 36, align: 'center' },
  { title: '编码', dataIndex: 'code', key: 'code', width: 140 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true, width: 180 },
  { title: '文档类型', dataIndex: 'typeDefinitionName', key: 'typeDefinitionName', width: 120 },
  { title: '版本', dataIndex: 'displayVersion', key: 'displayVersion', width: 70 },
  { title: '生命周期状态', key: 'status', width: 100 },
  { title: '检出状态', key: 'checkout', width: 100 },
  { title: '操作', key: 'action', width: 110, fixed: 'right' }
]

/** 继承资料专用列（只读，操作仅查看） */
const inheritedDocColumns = [
  { title: '', dataIndex: 'checkedOut', key: 'checkout_status', width: 36, align: 'center' },
  { title: '编码', dataIndex: 'code', key: 'code', width: 140 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true, width: 180 },
  { title: '文档类型', dataIndex: 'typeDefinitionName', key: 'typeDefinitionName', width: 120 },
  { title: '版本', dataIndex: 'displayVersion', key: 'displayVersion', width: 70 },
  { title: '生命周期状态', key: 'status', width: 100 },
  { title: '检出状态', key: 'checkout', width: 100 },
  { title: '操作', key: 'action', width: 110 }
]

// ==================== 文档类型筛选 ====================
const docTypeFilter = ref('')           // 当前选中的类型过滤值（空=全部）
const docKeyword = ref('')              // 文档搜索关键词

/** 所有文档的唯一类型列表（用于筛选下拉） */
const docTypeOptions = computed(() => {
  const types = new Map()
  for (const d of [...documents.value, ...inheritedDocuments.value]) {
    const name = d.typeDefinitionName || d.typeDefinitionCode
    if (name) types.set(name, name)
  }
  return Array.from(types.keys())
})

/** 筛选后的自有文档 */
const filteredDocuments = computed(() => {
  if (!docTypeFilter.value) return documents.value
  return documents.value.filter(d =>
    (d.typeDefinitionName || d.typeDefinitionCode) === docTypeFilter.value
  )
})

/** 筛选后的继承文档 */
const filteredInheritedDocuments = computed(() => {
  if (!docTypeFilter.value) return inheritedDocuments.value
  return inheritedDocuments.value.filter(d =>
    (d.typeDefinitionName || d.typeDefinitionCode) === docTypeFilter.value
  )
})

async function loadDocuments(folderOid) {
  docLoading.value = true
  try {
    const res = await getFolderDocumentDetails(folderOid)
    if (res.code === 200) documents.value = res.data || []
    else documents.value = []
  } catch { documents.value = [] }
  finally { docLoading.value = false }
}

function searchDocuments() {
  if (!selectedFolder.value) return
  loadDocuments(selectedFolder.value.oid)
}

/** 加载继承文件夹中的文档（合并节点时使用父系列 oid 查询） */
const inheritedDocLoading = ref(false)

async function loadInheritedDocuments(folderOid) {
  inheritedDocLoading.value = true
  try {
    const res = await getFolderDocumentDetails(folderOid)
    if (res.code === 200) inheritedDocuments.value = res.data || []
    else inheritedDocuments.value = []
  } catch { inheritedDocuments.value = [] }
  finally { inheritedDocLoading.value = false }
}

/** 克隆继承文件夹到本产品（创建同名根文件夹） */
const cloneInheritedFolder = async (node) => {
  if (!node._inherited) return
  const name = node.name
  try {
    await createFolder({
      ownerOid: line.value.oid,
      stageOid: activeStage.value,
      parentFolderOid: null,
      name,
      sortOrder: 0,
    })
    message.success(`已在本产品创建同名文件夹「${name}」`)
    await loadFolders()
  } catch { message.error('克隆文件夹失败') }
}

const openCreateDocument = () => {
  docTypeSelectValue.value = undefined
  selectedDocType.value = null
  docFormEntityCode.value = 'DOCUMENT'
  // containerOid: 产品型号取 parentOid，产品系列取自身 oid；containerType 标识归属类型
  const isModel = line.value?.nodeType === 'PRODUCT_MODEL'
  const plOid = isModel ? line.value?.parentOid : line.value?.oid
  docForm.value = {
    name: '',
    typeDefinitionCode: '',
    description: '',
    stageOid: activeStage.value || stageDefs.value[0]?.oid || '',
    folderOid: selectedFolder.value?.oid || '',
    containerOid: plOid || '',
    containerType: isModel ? 'PRODUCT_MODEL' : 'PRODUCT_LINE',
    ckfileOid: '',
    attachmentOid: '',
  }
  docModalVisible.value = true
}

/**
 * 文档类型选中回调。
 * 根据所选类型节点的 code 切换 DynamicForm 的 entityCode，
 * 从而加载该类型的专属 page_layout；若专属布局缺失，
 * DynamicForm 会通过 fallbackEntityCode 回退到 DOCUMENT 布局。
 */
function onDocTypeChange(typeNode) {
  if (typeNode) {
    selectedDocType.value = typeNode
    docFormEntityCode.value = typeNode.code
    // 将 typeDefinitionCode 回写到表单数据（存 TypeDefinition.code）
    docForm.value.typeDefinitionCode = typeNode.code
  } else {
    selectedDocType.value = null
    docFormEntityCode.value = 'DOCUMENT'
    docForm.value.typeDefinitionCode = ''
  }
}

/** 关闭创建弹窗时重置状态 */
function resetDocModal() {
  docTypeSelectValue.value = undefined
  selectedDocType.value = null
  docFormEntityCode.value = 'DOCUMENT'
  docForm.value = {
    name: '', typeDefinitionCode: '', description: '',
    stageOid: '', folderOid: '', containerOid: '', containerType: '', ckfileOid: '', attachmentOid: '',
  }
}

const confirmDocument = async () => {
  // 使用 DynamicForm 的校验能力
  if (docFormRef.value) {
    const errors = docFormRef.value.validate()
    if (errors.length > 0) return message.warning(errors[0])
  }
  const name = docForm.value.name?.trim()
  if (!name) return message.warning('请输入文档名称')
  if (!selectedFolder.value) return message.warning('请先选择文件夹')
  docModalSaving.value = true
  try {
    const res = await createDocument({
      name,
      typeDefinitionCode: selectedDocType.value?.code || '',
      description: docForm.value.description || '',
      containerOid: docForm.value.containerOid || '',
      containerType: docForm.value.containerType || 'PRODUCT_LINE',
      folderOid: selectedFolder.value.oid,
      stageOid: activeStage.value,
      location: selectedFolder.value.name,
      ckfileOid: docForm.value.ckfileOid || '',
      attachmentOid: docForm.value.attachmentOid || '',
    })
    if (res.code === 200) {
      message.success('文档创建成功')
      docModalVisible.value = false
      resetDocModal()
      loadDocuments(selectedFolder.value.oid)
    } else {
      message.error(res.message || '创建失败')
    }
  } catch { message.error('创建文档失败') }
  finally { docModalSaving.value = false }
}

const removeDocument = async (doc) => {
  try {
    const res = await deleteDocument(doc.oid)
    if (res.code === 200) {
      message.success('文档已删除')
      loadDocuments(selectedFolder.value.oid)
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}

/** 生命周期状态颜色 */
function statusColor(code) {
  const map = { DRAFT: 'default', INWORK: 'processing', REVIEW: 'warning', APPROVED: 'success', RELEASED: 'blue', OBSOLETE: 'error' }
  return map[code] || 'default'
}

// ==================== 文档详情查看器 ====================
const docViewerVisible = ref(false)
const docViewerDoc = ref(null)

// ==================== 检出 ====================
const checkoutModalVisible = ref(false)
const checkoutComment = ref('')
const checkoutSaving = ref(false)
const checkoutTargetDoc = ref(null)

/** 确认检出 */
async function confirmCheckout() {
  if (!checkoutTargetDoc.value) return
  if (!checkoutComment.value.trim()) return message.warning('请输入检出注释')
  checkoutSaving.value = true
  try {
    const res = await checkoutDocApi(checkoutTargetDoc.value.oid, checkoutComment.value.trim())
    if (res.code === 200) {
      message.success('检出成功')
      recordOperation({ action: '检出文档', target: checkoutTargetDoc.value.code + ' ' + checkoutTargetDoc.value.name })
      checkoutModalVisible.value = false
      checkoutComment.value = ''
      checkoutTargetDoc.value = null
      // 刷新当前文件夹的文档列表
      if (selectedFolder.value) {
        if (selectedFolder.value._mergedInherited) {
          loadDocuments(selectedFolder.value.oid)
        } else {
          loadDocuments(selectedFolder.value.oid)
        }
      }
    } else {
      message.error(res.message || '检出失败')
    }
  } catch (e) {
    message.error('检出失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    checkoutSaving.value = false
  }
}

/** 统一文档操作分发 */
function handleDocAction({ key }, doc) {
  switch (key) {
    case 'view': docViewerDoc.value = doc; docViewerVisible.value = true; break
    case 'checkout': checkoutDocument(doc); break
    case 'edit': editDocument(doc); break
    case 'checkin': checkinDocument(doc); break
    case 'undoCheckout': undoCheckoutDocument(doc); break
    case 'download': downloadDocument(doc); break
    case 'move': moveDocument(doc); break
    case 'lifecycle': message.info(`设置生命周期: ${doc.name}`); break
    case 'workflow': message.info(`发起流程: ${doc.name}`); break
    case 'delete':
      Modal.confirm({
        title: '确定删除该文档？', content: '删除后数据不可恢复',
        okText: '删除', okType: 'danger', cancelText: '取消',
        onOk: () => removeDocument(doc)
      })
      break
  }
}

/** 检出文档 —— 弹出注释输入框 */
async function checkoutDocument(doc) {
  checkoutTargetDoc.value = doc
  checkoutComment.value = ''
  checkoutModalVisible.value = true
}

/** 编辑文档（检出后才可编辑） */
function editDocument(doc) {
  message.info(`编辑: ${doc.name}`)
  // TODO: 打开编辑弹窗或跳转到编辑页
}

/** 检入文档 */
async function checkinDocument(doc) {
  message.info(`检入: ${doc.name}`)
  // TODO: 调用检入 API
}

/** 当前用户是否可以取消检出 */
function canUndoCheckout(doc) {
  if (!doc.checkedOut) return false
  const currentUser = userStore.username
  return currentUser && doc.checkedOutBy === currentUser
}

/** 下载主文档文件 */
function downloadDocument(doc) {
  if (!doc.ckfileOid) {
    message.warning('该文档没有主文件可供下载')
    return
  }
  // 直接用后端链接，Content-Disposition: attachment 决定文件名
  window.open(getDocumentDownloadUrl(doc.ckfileOid), '_blank')
}

/** 取消检出文档 */
async function undoCheckoutDocument(doc) {
  Modal.confirm({
    title: `确认取消检出 "${doc.code || doc.name}"？`,
    content: '取消检出将丢弃本次检出后的所有修改，恢复为检出前的版本。',
    okText: '确认取消检出',
    cancelText: '保留检出',
    okType: 'danger',
    onOk: async () => {
      try {
        const res = await undoCheckoutApi(doc.oid)
        if (res.code === 200) {
          message.success(`已取消检出: ${doc.code || doc.name}`)
          // 刷新当前页面数据
          if (selectedFolder.value) {
            if (selectedFolder.value._mergedInherited) {
              loadInheritedDocuments(selectedFolder.value._mergedInherited.oid)
            }
            loadDocuments(selectedFolder.value.oid)
          }
        }
      } catch { /* 错误已在拦截器中处理 */ }
    }
  })
}

/** 移动文档 */
function moveDocument(doc) {
  message.info(`移动: ${doc.name}`)
  // TODO: 打开移动弹窗
}

const loadFolders = async () => {
  if (!line.value) return
  parentFolderTree.value = []
  parentName.value = ''

  // 加载本系列文件夹
  try {
    const res = await getFolderTree(line.value.oid, activeStage.value)
    if (res.code === 200) {
      folderTree.value = res.data || []
      setParentsRecursive(folderTree.value, null)
    }
  } catch { folderTree.value = [] }

  // 父级引用：parentOid 统一表示父级（产品系列自身 parentOid 或产品型号的归属产品系列）
  const parentOid = line.value.parentOid || line.value.productLineOid
  if (parentOid) {
    try {
      // 并行加载父系列信息及其阶段列表
      const [parentRes, parentStagesRes] = await Promise.all([
        getProductLine(parentOid),
        getStages(parentOid),
      ])
      if (parentRes.code === 200) {
        parentName.value = parentRes.data.name || '父产品系列'
      }
      // 找到与当前阶段 code 匹配的父系列阶段 OID
      const currentStage = stageDefs.value.find(s => s.oid === activeStage.value)
      let parentStageOid = null
      if (currentStage && parentStagesRes.code === 200 && parentStagesRes.data?.length) {
        const matched = parentStagesRes.data.find(ps => ps.code === currentStage.key)
        if (matched) parentStageOid = matched.oid
      }
      // 用父系列的阶段 OID 加载文件夹
      if (parentStageOid) {
        const parentFolderRes = await getFolderTree(parentOid, parentStageOid)
        if (parentFolderRes.code === 200 && parentFolderRes.data?.length) {
          markInheritedRecursive(parentFolderRes.data, parentName.value)
          parentFolderTree.value = parentFolderRes.data
        }
      }
    } catch { /* 父系列加载失败不阻塞 */ }
  }

  // 合并展开状态
  const expanded = {}
  mergedTree.value.forEach(n => { expanded[n.oid] = true })
  expandedFolders.value = expanded
}

/* ==================== 数据加载 ==================== */
const loadProductLine = async () => {
  const oid = route.params.oid; if (!oid) return
  try {
    const res = await getProductLine(oid)
    if (res.code === 200) line.value = res.data
    loadChildren(oid)
    // 加载父级产品系列名称（用于面包屑显示）
    // parentOid 统一表示父级
    const parentOid = line.value.parentOid || line.value.productLineOid
    if (parentOid) {
      const parentRes = await getProductLine(parentOid)
      if (parentRes.code === 200) {
        parentLineName.value = parentRes.data.name || '上级产品系列'
      }
    } else {
      parentLineName.value = ''
    }
    await loadStages(oid)  // 先加载阶段列表（含 activeStage 修正）
    loadFolders()           // 再按当前阶段加载文件夹
  } catch { message.error('加载产品线信息失败') }
}

/** 加载产品线自有研发阶段（从 ck_stage 表），若无则回退兜底默认 */
const loadStages = async (oid) => {
  try {
    const res = await getStages(oid)
    if (res.code === 200 && res.data?.length > 0) {
      // 注册动态阶段到全局查找表（供 StageDisplay 使用）
      registerDynamicStages(res.data)
      // 过滤：只显示 showOnDashboard !== false 的阶段
      const allStages = res.data.map(mapStageToDef)
      stageDefs.value = allStages.filter(s => s.showOnDashboard !== false)
      // 确保 activeStage 在有效范围内（使用 stageOid）
      if (!stageDefs.value.find(s => s.oid === activeStage.value)) {
        activeStage.value = stageDefs.value[0]?.oid || null
      }
      return
    }
  } catch { /* API 不可用时回退到硬编码默认阶段 */ }

  // 兜底：无阶段数据时使用前端默认 6 个阶段（兼容老数据，此时使用 stage key 作为 oid）
  stageDefs.value = DEFAULT_STAGE_DEFS_FALLBACK
  activeStage.value = stageDefs.value[0]?.key || null
  // 兜底阶段也注册
  registerDynamicStages(DEFAULT_STAGE_DEFS_FALLBACK.map(s => ({ oid: s.key, name: s.title, color: s.color, description: s.description })))
}

/** 前端兜底默认阶段定义（与 stageDefs.js 保持完全一致，当后端 API 无数据时使用） */
const DEFAULT_STAGE_DEFS_FALLBACK = [
  { oid: 'MARKET_VALIDATION', key: 'MARKET_VALIDATION', title: '市场验证', icon: ShoppingCartOutlined, color: '#eb2f96', description: '验证产品市场可行性与用户需求匹配度', items: ['市场调研分析', '目标用户验证', '竞品对标', '市场可行性评估'] },
  { oid: 'REQUIREMENTS',      key: 'REQUIREMENTS',      title: '需求论证', icon: AuditOutlined, color: '#1677ff', description: '论证产品需求合理性与技术实现路径', items: ['需求分析', '需求评审', '技术可行性论证'] },
  { oid: 'SOLUTION',         key: 'SOLUTION',          title: '方案设计', icon: BulbOutlined, color: '#722ed1', description: '确定系统方案与关键技术选型', items: ['系统架构设计', '方案评审', '关键技术选型验证'] },
  { oid: 'DETAILED',          key: 'DETAILED',          title: '详细设计', icon: FundProjectionScreenOutlined, color: '#13c2c2', description: '完成各专业详细设计及DFMEA分析', items: ['软件详细设计', '硬件原理图', '结构设计', 'DFMEA分析'] },
  { oid: 'PROCESS',           key: 'PROCESS',           title: '工艺规划', icon: ToolOutlined, color: '#fa8c16', description: '完成生产工艺规划与试产准备', items: ['生产工艺设计', '工装夹具设计', 'BOM编制', '试产计划'] },
  { oid: 'TRIAL',             key: 'TRIAL',             title: '试产',     icon: RocketOutlined, color: '#52c41a', description: '小批量试产验证并完成转量产决策', items: ['小批量试产验证', '问题追踪', '试产评审', '转量产决策'] },
]

const loadChildren = async (oid) => {
  try { const r = await getProductLineChildren(oid); if (r.code === 200) children.value = r.data || [] }
  catch { children.value = [] }
}

watch(activeStage, () => { selectedFolder.value = null; documents.value = []; inheritedDocuments.value = []; loadFolders() })
onMounted(loadProductLine)
</script>

<style scoped>
/* ===== 设计变量 ===== */
.pl-dashboard {
  --pl-primary: #1677ff;
  --pl-primary-light: #e6f4ff;
  --pl-primary-bg: #f0f5ff;
  --pl-text: #1a1a2e;
  --pl-text-sec: #595959;
  --pl-text-muted: #8c8c8c;
  --pl-border: #f0f0f0;
  --pl-bg: #fafbfc;
  --pl-radius: 8px;
  --pl-shadow: 0 1px 4px rgba(0,0,0,.04);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 头部 ===== */
.pl-header {
  flex-shrink: 0;
  background: #fff;
  border: 1px solid var(--pl-border);
  border-radius: var(--pl-radius);
  padding: 20px 24px 16px;
  margin-bottom: 16px;
  box-shadow: var(--pl-shadow);
  position: relative;
  overflow: hidden;
}
.pl-header::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: linear-gradient(90deg, #1677ff, #722ed1, #eb2f96);
}

.pl-header-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.pl-header-info { min-width: 0; }

.pl-breadcrumb { margin-bottom: 6px; }

.pl-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.pl-title { margin: 0; font-size: 22px; font-weight: 700; color: var(--pl-text); letter-spacing: -.3px; }
.pl-code-tag { font-family: monospace; font-size: 12px; }

.pl-meta {
  margin: 0;
  font-size: 12px;
  color: var(--pl-text-muted);
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pl-meta-sep { display: inline-block; width: 3px; height: 3px; border-radius: 50%; background: #d9d9d9; }

/* 统计 pill */
.pl-header-stats {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.pl-stat-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 20px;
  background: var(--pl-bg);
  border: 1px solid var(--pl-border);
  cursor: default;
  transition: all .2s;
}
.pl-stat-pill--active { background: #f6ffed; border-color: #b7eb8f; cursor: pointer; }
.pl-stat-pill--active:hover { border-color: #52c41a; box-shadow: 0 0 0 2px rgba(82,196,26,.1); }

.pl-stat-pill-icon { font-size: 18px; flex-shrink: 0; }
.pl-stat-pill-num { font-size: 20px; font-weight: 700; color: var(--pl-text); line-height: 1; }
.pl-stat-pill-label { font-size: 12px; color: var(--pl-text-muted); }

.pl-stat-pill-arrow {
  font-size: 12px; color: #bbb; transition: transform .25s;
}
.pl-stat-pill-arrow.up { transform: rotate(180deg); }

/* 子系列面板 */
.pl-children-panel {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--pl-border);
}
.pl-children-header {
  font-size: 13px; font-weight: 600; color: var(--pl-text-sec);
  margin-bottom: 10px; display: flex; align-items: center; gap: 6px;
}
.pl-children-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 8px;
}
.pl-child-card {
  border-radius: 6px;
  border-color: var(--pl-border);
  transition: all .2s;
  cursor: pointer;
}
.pl-child-card:hover { border-color: var(--pl-primary); box-shadow: 0 1px 6px rgba(22,119,255,.1); }
.pl-child-card :deep(.ant-card-body) {
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.pl-child-code {
  font-size: 11px; color: var(--pl-primary);
  background: var(--pl-primary-light);
  padding: 1px 6px; border-radius: 4px;
  font-family: monospace;
}
.pl-child-name { font-size: 13px; font-weight: 500; color: var(--pl-text); }
.pl-child-desc {
  font-size: 12px; color: #bbb;
  flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0;
}

.pl-collapse-enter-active,
.pl-collapse-leave-active { transition: all .25s ease; overflow: hidden; }
.pl-collapse-enter-from,
.pl-collapse-leave-to { opacity: 0; max-height: 0; margin-top: 0; padding-top: 0; }

/* ===== 阶段管线 ===== */
.pl-pipeline {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.pl-stage-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.pl-stage-tabs :deep(.ant-tabs-content-holder) { flex: 1; overflow-y: auto; }
.pl-stage-tabs :deep(.ant-tabs-nav) { margin-bottom: 12px; }
.pl-stage-tabs :deep(.ant-tabs-tab) { padding: 8px 16px; }

.pl-tab-label {
  display: flex; align-items: center; gap: 6px;
}
.pl-tab-icon { font-size: 14px; }

/* 阶段描述条 */
.pl-stage-desc {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--pl-text-sec);
  background: #fafafa;
  border: 1px solid var(--pl-border);
  border-radius: 6px;
  overflow: hidden;
}
.pl-stage-desc-icon { font-size: 18px; flex-shrink: 0; opacity: .7; }
.pl-stage-desc-text { color: var(--pl-text-muted); flex-shrink: 0; }
.pl-stage-desc-sep { width: 1px; height: 14px; background: #e8e8e8; flex-shrink: 0; }
.pl-stage-desc-items { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ===== 文件夹区域 ===== */
.pl-folders { margin-top: 8px; }

.pl-folders-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px; font-weight: 600;
  color: var(--pl-text);
}
.pl-folders-header .ant-btn { margin-left: auto; }

.pl-inherited-hint { flex-shrink: 0; }

.pl-folders-empty {
  padding: 48px 0;
  text-align: center;
  color: var(--pl-text-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.pl-folders-empty p { margin: 0; }
.pl-folders-empty-icon { font-size: 40px; opacity: .3; }

/* 两栏布局 */
.pl-folders-body {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  min-height: 360px;
}

/* 左侧树 */
.pl-folders-tree {
  border: 1px solid var(--pl-border);
  border-radius: var(--pl-radius);
  padding: 6px 0;
  background: #fff;
  overflow-y: auto;
  max-height: 480px;
}

/* 树内分组头 */
.pl-tree-group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--pl-text-muted);
  border-bottom: 1px dashed var(--pl-border);
  margin: 2px 8px 4px;
}
.pl-tree-group-header-icon { font-size: 13px; opacity: .6; }

/* 右侧内容 */
.pl-folders-content {
  border: 1px solid var(--pl-border);
  border-radius: var(--pl-radius);
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pl-folders-content-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--pl-text-muted);
  gap: 8px;
}
.pl-folders-content-empty p { margin: 0; }
.pl-folders-content-empty-icon { font-size: 48px; opacity: .35; }

.pl-folders-content-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--pl-border);
  background: var(--pl-bg);
  font-size: 13px;
  color: var(--pl-text);
}
.pl-folders-content-bar--inherited {
  background: #fffbe6;
  border-bottom-color: #ffe58f;
}

.pl-content-bar-inline {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pl-folders-content-files {
  flex: 1;
  display: flex;
  flex-direction: column;
  color: var(--pl-text-muted);
  gap: 0;
  overflow: hidden;
}
.pl-folders-content-files p { margin: 0; }
.pl-folders-content-files-icon { font-size: 40px; opacity: .3; }

.pl-folders-content-hint {
  font-size: 12px;
  color: #ccc;
}

/* ===== 文档列表 ===== */
.pl-doc-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow-y: auto;
}

.pl-doc-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fafafa;
  border-radius: 6px;
  transition: background .15s;
}
.pl-doc-item:hover {
  background: var(--pl-primary-light);
}

.pl-doc-item-icon {
  font-size: 20px;
  color: #1677ff;
  flex-shrink: 0;
}

.pl-doc-item-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pl-doc-item-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--pl-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-doc-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--pl-text-muted);
}

.pl-doc-item-number {
  font-family: monospace;
  color: #999;
}

/* ===== 合并节点双区布局 ===== */
.pl-merged-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pl-merged-section {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.pl-merged-section--inherited {
  border-bottom: 1px dashed #e8e8e8;
}
.pl-merged-section--inherited .pl-doc-item {
  background: #fffbe6;
}

.pl-merged-section-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  color: var(--pl-text);
  background: var(--pl-bg);
  border-bottom: 1px solid var(--pl-border);
  flex-shrink: 0;
}

.pl-merged-section-files {
  flex: 1;
  min-height: 120px;
  max-height: 220px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--pl-text-muted);
  padding: 8px;
}

.pl-merged-section-files .pl-doc-list {
  width: 100%;
  align-items: stretch;
}

.pl-doc-item--inherited {
  opacity: .78;
  cursor: default;
}

/* 树内继承分组头（保留向后兼容） */
.pl-tree-group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--pl-text-muted);
  border-bottom: 1px dashed var(--pl-border);
  margin: 2px 8px 4px;
}
.pl-tree-group-header-icon { font-size: 13px; opacity: .6; }

/* ===== 检出弹窗 ===== */
.pl-checkout-doc-line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  font-size: 13px;
}
.pl-checkout-doc-name {
  font-weight: 600;
  color: #1a1a2e;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pl-checkout-doc-sep {
  color: #d9d9d9;
  font-weight: 300;
}
.pl-checkout-doc-version {
  font-family: monospace;
  font-size: 12px;
  color: #8c8c8c;
}
</style>
