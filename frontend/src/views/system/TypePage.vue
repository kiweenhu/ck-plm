<template>
  <div class="st-page">
    <!-- 页头 -->
    <div class="st-header">
      <div class="st-header-left">
        <h3 class="st-title">模型定义</h3>
        <span class="st-subtitle">管理软类型（SoftType）元数据，包括类型定义、IBA属性、规则绑定及页面布局</span>
      </div>
      <div class="st-header-right">
        <a-button type="primary" ghost @click="ibaExtRef?.open()">
          <template #icon><PlusOutlined /></template>
          IBA扩展
        </a-button>
      </div>
    </div>

    <!-- 类型统计 -->
    <div class="st-stats-bar">
      <div class="st-stat-item">
        <ClusterOutlined class="st-stat-icon" />
        <span class="st-stat-value">{{ flatTypes.length }}</span>
        <span class="st-stat-label">类型总数</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="st-stat-item">
        <a-tag color="purple" size="small">内置</a-tag>
        <span class="st-stat-value">{{ ootbCount }}</span>
      </div>
      <div class="st-stat-item">
        <a-tag color="blue" size="small">自定义</a-tag>
        <span class="st-stat-value">{{ softCount }}</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="st-stat-item">
        <span class="st-stat-value">{{ selectedNode ? selectedNode.name : '-' }}</span>
        <span class="st-stat-label">当前选中</span>
      </div>
    </div>

    <!-- 主体：左侧树 + 右侧 Tabs -->
    <div class="st-body">
      <!-- 左侧类型树 -->
      <div class="st-tree-panel">
        <div class="st-tree-search">
          <a-input v-model:value="treeSearch" placeholder="搜索类型..." allow-clear size="small">
            <template #prefix><SearchOutlined /></template>
          </a-input>
        </div>
        <a-spin :spinning="treeLoading" class="st-tree-spin">
          <a-tree
            v-if="filteredTree.length > 0"
            :tree-data="filteredTree"
            :field-names="{ children: 'children', title: 'name', key: 'oid' }"
            v-model:selectedKeys="selectedKeys"
            v-model:expandedKeys="expandedKeys"
            block-node
            @select="onTreeSelect"
          >
            <template #title="nodeData">
              <div class="st-tree-node">
                <component :is="getIconComponent(nodeData.icon)" class="st-tree-icon" />
                <span class="st-tree-name">{{ nodeData.name }}</span>
                <a-tag v-if="nodeData.typeKind === 'OOTB'" color="purple" size="small" class="st-tree-tag">内置</a-tag>
                <a-tag v-else-if="nodeData.source === 'USER'" color="green" size="small" class="st-tree-tag">自定义</a-tag>
              </div>
            </template>
          </a-tree>
          <a-empty v-else description="暂无类型定义" :image-style="{ height: '40px' }" />
        </a-spin>
      </div>

      <!-- 右侧内容区 -->
      <div class="st-content">
        <template v-if="!selectedNode">
          <div class="st-empty">
            <ClusterOutlined :style="{ fontSize: '56px', color: '#d9d9d9' }" />
            <p>请在左侧选择一个类型查看详情</p>
          </div>
        </template>

        <template v-else>
          <a-tabs v-model:activeKey="activeTab" class="st-tabs" @change="onTabChange">
            <template #tabBarExtraContent>
              <a-button size="small" type="primary" ghost @click="goToPageDesigner">
                <template #icon><ToolOutlined /></template>
                页面设计器
              </a-button>
            </template>
            <!-- ========== Tab1: 基本信息 ========== -->
            <a-tab-pane key="basic" tab="基本信息">
              <a-spin :spinning="detailLoading">
                <!-- 基本信息网格 -->
                <div class="st-info-grid">
                  <div class="st-info-item">
                    <span class="st-info-label"><PictureOutlined class="st-info-icon" /> 图标</span>
                    <span class="st-info-value">
                      <component v-if="detail.icon" :is="getIconComponent(detail.icon)" :style="{ fontSize:'20px', color:'#1677ff' }" />
                      <span v-else class="st-info-value">-</span>
                    </span>
                  </div>
                  <div class="st-info-item">
                    <span class="st-info-label"><TagOutlined class="st-info-icon" /> 名称</span>
                    <span class="st-info-value">{{ detail.name || '-' }}</span>
                  </div>
                  <div class="st-info-item">
                    <span class="st-info-label"><CodeOutlined class="st-info-icon" /> 编码</span>
                    <span class="st-info-value"><code>{{ detail.code || '-' }}</code></span>
                  </div>
                  <div class="st-info-item">
                    <span class="st-info-label"><ApartmentOutlined class="st-info-icon" /> 类型</span>
                    <span class="st-info-value">
                      <a-tag :color="detail.typeKind === 'OOTB' ? 'purple' : 'blue'">
                        {{ detail.typeKind === 'OOTB' ? '系统内置' : '自定义类型' }}
                      </a-tag>
                    </span>
                  </div>
                  <div class="st-info-item">
                    <span class="st-info-label"><LinkOutlined class="st-info-icon" /> 父类型</span>
                    <span class="st-info-value">{{ detail.parentName || '-' }}</span>
                  </div>
                  <div class="st-info-item" v-if="detail.rootTypeCode">
                    <span class="st-info-label"><HomeOutlined class="st-info-icon" /> 所属根类型</span>
                    <span class="st-info-value"><code>{{ detail.rootTypeCode }}</code></span>
                  </div>
                  <div class="st-info-item">
                    <span class="st-info-label"><EyeOutlined class="st-info-icon" /> 显示名</span>
                    <span class="st-info-value">{{ detail.displayName || '-' }}</span>
                  </div>
                  <div class="st-info-item">
                    <span class="st-info-label"><CheckCircleOutlined class="st-info-icon" /> 状态</span>
                    <span class="st-info-value">
                      <a-tag :color="detail.enabled !== false ? 'green' : 'red'">
                        {{ detail.enabled !== false ? '启用' : '禁用' }}
                      </a-tag>
                    </span>
                  </div>
                  <div v-if="detail.description" class="st-info-item st-info-full">
                    <span class="st-info-label"><FileTextOutlined class="st-info-icon" /> 描述</span>
                    <span class="st-info-value">{{ detail.description }}</span>
                  </div>
                </div>

                <!-- 编辑 -->
                <div class="st-section">
                  <h4 class="st-section-title">
                    编辑类型
                    <a-tag v-if="!canEdit" color="orange" size="small" style="margin-left:6px">平台级</a-tag>
                  </h4>
                  <a-form :model="editForm" layout="vertical" size="small">
                    <a-row :gutter="16">
                      <a-col :span="12">
                        <a-form-item label="图标">
                          <div class="st-icon-picker" :class="{ 'st-icon-picker-disabled': !canEdit }" @click="canEdit && (iconModalVisible = true)">
                            <span v-if="editForm.icon" class="st-icon-picker-preview">
                              <component :is="getIconComponent(editForm.icon)" style="font-size:20px;color:#1677ff" />
                              <span style="margin-left:6px;font-size:12px;color:#8c8c8c">{{ getIconLabel(editForm.icon) }}</span>
                            </span>
                            <span v-else class="st-icon-picker-placeholder">点击选择图标</span>
                            <RightOutlined v-if="canEdit" style="font-size:10px;color:#bfbfbf;margin-left:auto" />
                          </div>
                        </a-form-item>
                      </a-col>
                      <a-col :span="12">
                        <a-form-item label="名称">
                          <a-input v-model:value="editForm.name" :disabled="!canEdit" />
                        </a-form-item>
                      </a-col>
                      <a-col :span="12">
                        <a-form-item label="编码">
                          <a-input v-model:value="editForm.code" disabled />
                        </a-form-item>
                      </a-col>
                      <a-col :span="12">
                        <a-form-item label="显示名">
                          <a-input v-model:value="editForm.displayName" :disabled="!canEdit" />
                        </a-form-item>
                      </a-col>
                      <a-col :span="12">
                        <a-form-item label="状态">
                          <a-switch v-model:checked="editForm.enabled" :disabled="!canEdit" checked-children="启用" un-checked-children="禁用" />
                        </a-form-item>
                      </a-col>
                      <a-col :span="24">
                        <a-form-item label="描述">
                          <a-textarea v-model:value="editForm.description" :disabled="!canEdit" :rows="2" />
                        </a-form-item>
                      </a-col>
                    </a-row>
                    <a-space>
                      <a-button v-if="canEdit" type="primary" size="small" :loading="saving" @click="handleUpdateType">保存修改</a-button>
                      <a-button v-if="canCreateChild" type="primary" size="small" ghost @click="openCreateChild">
                        <template #icon><PlusOutlined /></template>
                        新建子类型
                      </a-button>
                      <a-button v-if="canEdit && !isOotbType" size="small" danger @click="handleDeleteType">删除类型</a-button>
                    </a-space>
                  </a-form>
                </div>
              </a-spin>
            </a-tab-pane>

            <!-- ========== Tab2: 规则绑定 ========== -->
            <a-tab-pane key="rules" tab="规则绑定">
              <div class="st-rule-list">
                <RuleBindPanel
                  title="编码规则"
                  desc="为类型指定编码生成规则，创建实例时自动生成编码"
                  :loading="nrLoading"
                  :items="numberRules"
                  :selected-code="selectedNumberRuleCode"
                  :get-item-name="(item) => item.name || item.code"
                  :item-title-key="'name'"
                  :item-code-key="'code'"
                  :item-desc-key="'description'"
                  empty-text="暂无可用的编码规则，请先到编码规则模块创建"
                  @select="handleBindNumberRule"
                >
                  <template #detail="{ item }">
                    <a-spin :spinning="nrDetailLoading" size="small">
                      <div class="st-rule-detail">
                        <div class="st-rule-detail-row"><span class="st-rule-detail-label">编码</span><code>{{ item.code }}</code></div>
                        <div class="st-rule-detail-row"><span class="st-rule-detail-label">名称</span>{{ item.name }}</div>
                        <div class="st-rule-detail-row" v-if="item.description"><span class="st-rule-detail-label">描述</span>{{ item.description }}</div>
                        <div class="st-rule-detail-section">
                          <span class="st-rule-detail-label">编码格式</span>
                          <div class="st-segment-list">
                            <a-tag v-for="(seg, i) in (nrDetail?.segments || [])" :key="i" :color="seg.segmentType === 'SERIAL' ? 'blue' : 'default'" style="margin:2px">
                              {{ formatSegment(seg) }}
                            </a-tag>
                          </div>
                        </div>
                      </div>
                    </a-spin>
                  </template>
                </RuleBindPanel>
                <RuleBindPanel
                  title="版本规则"
                  desc="为类型指定版本号生成规则，控制版本的命名和迭代"
                  :loading="vrLoading"
                  :items="versionRules"
                  :selected-code="selectedVersionRuleCode"
                  :get-item-name="(item) => item.name || item.code"
                  :item-title-key="'name'"
                  :item-code-key="'code'"
                  :item-desc-key="'description'"
                  empty-text="暂无可用的版本规则，请先到版本规则模块创建"
                  @select="handleBindVersionRule"
                >
                  <template #detail="{ item }">
                    <a-spin :spinning="vrDetailLoading" size="small">
                      <div class="st-rule-detail">
                        <div class="st-rule-detail-row"><span class="st-rule-detail-label">编码</span><code>{{ item.code }}</code></div>
                        <div class="st-rule-detail-row"><span class="st-rule-detail-label">名称</span>{{ item.name }}</div>
                        <div class="st-rule-detail-row" v-if="item.description"><span class="st-rule-detail-label">描述</span>{{ item.description }}</div>
                        <div class="st-rule-detail-row">
                          <span class="st-rule-detail-label">规则定义</span>
                          <code class="st-rule-def">{{ vrDetail?.ruleDefinition || '-' }}</code>
                        </div>
                      </div>
                    </a-spin>
                  </template>
                </RuleBindPanel>
                <RuleBindPanel
                  title="生命周期模板"
                  desc="为类型指定生命周期模板，定义实例的状态流转规则"
                  :loading="lcLoading"
                  :items="lifecycleTemplates"
                  :selected-code="selectedLifecycleTemplateCode"
                  :get-item-name="(item) => item.name || item.code"
                  :item-title-key="'name'"
                  :item-code-key="'code'"
                  :item-desc-key="'description'"
                  empty-text="暂无可用的生命周期模板，请先到生命周期模块创建"
                  @select="handleBindLifecycleTemplate"
                >
                  <template #detail="{ item }">
                    <a-spin :spinning="lcDetailLoading" size="small">
                      <div class="st-rule-detail">
                        <div class="st-rule-detail-row"><span class="st-rule-detail-label">编码</span><code>{{ item.code }}</code></div>
                        <div class="st-rule-detail-row"><span class="st-rule-detail-label">名称</span>{{ item.name }}</div>
                        <div class="st-rule-detail-row" v-if="item.description"><span class="st-rule-detail-label">描述</span>{{ item.description }}</div>
                        <div class="st-rule-detail-section">
                          <span class="st-rule-detail-label">状态列表</span>
                          <div class="st-state-list">
                            <a-tag v-for="(s, i) in (lcDetail?.states || [])" :key="i" :color="i === 0 ? 'green' : 'default'" style="margin:2px">
                              {{ s.statusDisplayName || s.statusCode }}
                              <span v-if="lcDetail?.initialStateCode === s.statusCode" style="font-size:10px;margin-left:2px">(初始)</span>
                            </a-tag>
                          </div>
                        </div>
                      </div>
                    </a-spin>
                  </template>
                </RuleBindPanel>
                <!-- 绑定分类（仅当类型的 rootTypeCode 实体有 classificationOid 属性时显示） -->
                <div v-if="showClassificationBinding" class="rule-bind-panel">
                  <div class="rb-header">
                    <div class="rb-header-left">
                      <span v-if="selectedClassificationOid" class="rb-status-ok"><CheckCircleOutlined /> 已绑定: {{ clsDetail?.displayName || clsDetail?.name || (selectedClassificationOid && selectedClassificationOid.substring(0,8)) }}</span>
                      <span v-else class="rb-status-none"><WarningOutlined /> 未绑定分类</span>
                    </div>
                    <a-button size="small" :type="selectedClassificationOid ? 'default' : 'primary'" @click="showClsTreeModal">
                      <template #icon><component :is="selectedClassificationOid ? SwapOutlined : LinkOutlined" /></template>
                      {{ selectedClassificationOid ? '更换' : '绑定' }}
                    </a-button>

                  </div>
                  <div class="rb-desc">为类型指定默认分类，创建实例时自动继承</div>

                  <div v-if="selectedClassificationOid && clsDetail" class="rb-detail">
                    <a-collapse :bordered="false" :ghost="true">
                      <a-collapse-panel key="detail" header="查看分类详情">
                        <div class="st-rule-detail">
                          <div class="st-rule-detail-row"><span class="st-rule-detail-label">名称</span>{{ clsDetail.displayName || clsDetail.name }}</div>
                          <div class="st-rule-detail-row" v-if="clsDetail.identifier"><span class="st-rule-detail-label">标识</span><code>{{ clsDetail.identifier }}</code></div>
                          <div class="st-rule-detail-row" v-if="clsDetail.description"><span class="st-rule-detail-label">描述</span>{{ clsDetail.description }}</div>
                        </div>
                      </a-collapse-panel>
                    </a-collapse>
                  </div>

                  <!-- 分类树选择弹窗 -->
                  <a-modal v-model:open="clsTreeModalVisible" title="选择分类" width="520px" :footer="null">
                    <a-spin :spinning="clsLoading" size="small">
                      <a-empty v-if="!clsLoading && classificationTree.length === 0" :description="'暂无可用的分类，请先到分类管理模块创建'" :image-style="{ height: '32px' }" />
                      <div v-if="classificationTree.length > 0">
                        <!-- 搜索 + 操作栏 -->
                        <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
                          <a-input-search v-model:value="clsSearchText" placeholder="搜索分类名称或编码" allow-clear size="small" style="flex:1" />
                          <a-button size="small" @click="expandAllClsTree">展开</a-button>
                          <a-button size="small" @click="collapseAllClsTree">折叠</a-button>
                        </div>
                        <!-- 当前选中节点面包屑 -->
                        <div v-if="clsSelectedBreadcrumb.length > 0" style="margin-bottom:6px;font-size:12px;color:#8c8c8c">
                          所属: <a-breadcrumb style="display:inline">
                            <a-breadcrumb-item v-for="(b, i) in clsSelectedBreadcrumb" :key="i">{{ b }}</a-breadcrumb-item>
                          </a-breadcrumb>
                        </div>
                        <div style="max-height:360px;overflow-y:auto;border:1px solid #f0f0f0;border-radius:6px;padding:8px">
                          <a-tree
                            ref="clsTreeRef"
                            :tree-data="filteredClsTree"
                            :field-names="{ children: 'children', title: 'displayName', key: 'oid' }"
                            :default-expand-all="true"
                            :selected-keys="selectedClassificationOid ? [selectedClassificationOid] : []"
                            :expanded-keys="clsExpandedKeys"
                            @select="onClsTreeSelect"
                            @expand="onClsTreeExpand"
                          >
                            <template #title="nodeData">
                              <span>{{ nodeData.displayName || nodeData.name }}</span>
                              <code v-if="nodeData.code" style="margin-left:6px;font-size:11px;color:#8c8c8c">{{ nodeData.code }}</code>
                            </template>
                          </a-tree>
                        </div>
                      </div>
                    </a-spin>
                  </a-modal>
                </div>
              </div>
            </a-tab-pane>

            <!-- ========== Tab3: IBA 属性 ========== -->
            <a-tab-pane key="iba" tab="IBA属性">
              <a-tabs v-model:activeKey="ibaSubTab" size="small" class="st-iba-subtabs">
                <!-- 已分配 IBA -->
                <a-tab-pane key="mappings" tab="已分配">
                  <DataTable
                    :columns="ibaColumns"
                    :data-source="ibaMappings"
                    :loading="ibaLoading"
                    search-placeholder="搜索IBA属性..."
                    :search-fields="['ibaName', 'ibaCode']"
                    row-key="oid"
                    size="small"
                    :show-column-toggle="false"
                  >
                    <template #toolbar>
                      <a-button size="small" type="primary" @click="openIBAAdd">
                        <template #icon><PlusOutlined /></template>
                        添加IBA属性
                      </a-button>
                    </template>
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'ibaName'">{{ record.ibaName || record.iba?.name || '-' }}</template>
                      <template v-if="column.key === 'ibaCode'"><code>{{ record.ibaCode || record.iba?.code || '-' }}</code></template>
                      <template v-if="column.key === 'dataType'">{{ record.ibaDataType || record.iba?.dataType || '-' }}</template>
                      <template v-if="column.key === 'required'">
                        <a-tag :color="isRequired(record) ? 'red' : 'default'">{{ isRequired(record) ? '必填' : '可选' }}</a-tag>
                      </template>
                      <template v-if="column.key === 'action'">
                        <a-button size="small" type="link" danger @click="handleRemoveIBA(record)">移除</a-button>
                      </template>
                    </template>
                  </DataTable>
                </a-tab-pane>

                <!-- IBA 定义管理 -->
                <a-tab-pane key="defs" tab="IBA定义">
                  <div class="st-iba-def-header">
                    <a-button size="small" type="primary" @click="ibaExtRef?.open()">
                      <template #icon><PlusOutlined /></template>
                      IBA扩展
                    </a-button>
                    <span class="st-iba-def-count">共 {{ ibaDefs.length }} 项</span>
                  </div>
                  <DataTable
                    :columns="ibaDefColumns"
                    :data-source="ibaDefs"
                    :loading="ibaDefLoading"
                    search-placeholder="搜索IBA..."
                    :search-fields="['name', 'code']"
                    row-key="oid"
                    size="small"
                    :show-column-toggle="false"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'name'">{{ record.name }}</template>
                      <template v-if="column.key === 'code'"><code>{{ record.code }}</code></template>
                      <template v-if="column.key === 'dataType'">{{ record.dataType || 'STRING' }}</template>
                      <template v-if="column.key === 'action'">
                        <a-space size="small">
                          <a-button size="small" type="link" @click="ibaExtRef?.openEdit(record)">编辑</a-button>
                          <a-popconfirm title="确定删除该IBA属性？" @confirm="handleDeleteIBA(record)">
                            <a-button size="small" type="link" danger>删除</a-button>
                          </a-popconfirm>
                        </a-space>
                      </template>
                    </template>
                  </DataTable>
                </a-tab-pane>
              </a-tabs>
            </a-tab-pane>

            <!-- ========== Tab4: 继承属性 ========== -->
            <a-tab-pane key="attributes" tab="继承属性">
              <!-- OOTB 属性定义 -->
              <div class="st-section">
                <h4 class="st-section-title">
                  OOTB 属性
                  <a-tag color="blue" size="small" style="margin-left:6px">{{ attributeDefs.length }} 项</a-tag>
                </h4>
                  <DataTable
                    :columns="attrColumns"
                    :data-source="attributeDefs"
                    :loading="attrLoading"
                    search-placeholder="搜索属性..."
                    :search-fields="['displayName', 'fieldName']"
                    row-key="oid"
                    size="small"
                    :show-column-toggle="false"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'displayName'">
                        <span class="st-cell-name">{{ record.displayName }}</span>
                        <code class="st-cell-code">{{ record.fieldName }}</code>
                      </template>
                      <template v-if="column.key === 'source'">
                        <a-tag :color="record.source === 'IBA' ? 'orange' : 'blue'" size="small">{{ record.source || 'SYSTEM' }}</a-tag>
                      </template>
                      <template v-if="column.key === 'dataType'">{{ record.dataType || 'STRING' }}</template>
                      <template v-if="column.key === 'required'">
                        <a-tag :color="isAttrRequired(record) ? 'red' : 'default'">{{ isAttrRequired(record) ? '必填' : '可选' }}</a-tag>
                      </template>
                      <template v-if="column.key === 'constraints'">
                        <span class="st-cell-constraint">{{ formatConstraints(record) }}</span>
                      </template>
                    </template>
                  </DataTable>
              </div>

              <!-- 父类型继承 IBA 属性 -->
              <div v-if="detail.parentOid" class="st-section" style="margin-top:20px">
                <h4 class="st-section-title">
                  继承父类型 IBA 属性
                  <a-tag v-if="detail.parentName" color="purple" size="small" style="margin-left:6px">{{ detail.parentName }}</a-tag>
                  <a-tag color="orange" size="small" style="margin-left:4px">{{ inheritedIBAs.length }} 项</a-tag>
                </h4>
                <a-table
                  :columns="inheritedColumns"
                  :data-source="inheritedIBAs"
                  :loading="inheritedLoading"
                  :pagination="false"
                  row-key="ibaOid"
                  size="small"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'ibaName'">{{ record.ibaName || record.name || '-' }}</template>
                    <template v-if="column.key === 'ibaCode'"><code>{{ record.ibaCode || record.code || '-' }}</code></template>
                    <template v-if="column.key === 'dataType'">{{ record.ibaDataType || record.dataType || '-' }}</template>
                    <template v-if="column.key === 'required'">
                      <a-tag :color="record.required ? 'red' : 'default'">{{ record.required ? '必填' : '可选' }}</a-tag>
                    </template>
                    <template v-if="column.key === 'inheritFrom'">
                      <a-tag color="purple" size="small">{{ record.parentName || '父类型' }}</a-tag>
                    </template>
                  </template>
                </a-table>
                <a-empty v-if="!inheritedLoading && inheritedIBAs.length === 0" description="无继承属性" :image-style="{ height: '30px' }" />
              </div>
            </a-tab-pane>
          </a-tabs>
        </template>
      </div>
    </div>

    <!-- 新建子类型弹窗 -->
    <a-modal v-model:open="createModalVisible" title="新建子类型" :confirm-loading="saving" @ok="handleCreateChild" width="480px" :mask-closable="false">
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="编码" required>
          <a-input v-model:value="createForm.code" placeholder="类型编码（英文）" size="large" />
        </a-form-item>
        <a-form-item label="名称" required>
          <a-input v-model:value="createForm.name" placeholder="类型名称" size="large" />
        </a-form-item>
        <a-form-item label="显示名">
          <a-input v-model:value="createForm.displayName" placeholder="显示名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="createForm.description" :rows="2" placeholder="类型描述" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 添加 IBA 属性弹窗 -->
    <a-modal v-model:open="ibaModalVisible" title="添加 IBA 属性" width="640px" :footer="null" :mask-closable="false">
      <a-spin :spinning="ibaSelectLoading">
        <a-input-search v-model:value="ibaSearchKeyword" placeholder="搜索 IBA 属性" style="margin-bottom:12px" @search="loadUnassignedIBAs" size="large" />
        <DataTable
          :columns="ibaSelectColumns"
          :data-source="unassignedIBAs"
          :loading="ibaSelectLoading"
          row-key="oid"
          size="small"
          :show-column-toggle="false"
          :row-selection="{ selectedRowKeys: selectedIBAIds, onChange: onIBASelectChange }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">{{ record.name }}</template>
            <template v-if="column.key === 'code'"><code>{{ record.code }}</code></template>
            <template v-if="column.key === 'dataType'">{{ record.dataType }}</template>
          </template>
        </DataTable>
        <div style="margin-top:12px;text-align:right;">
          <a-button :disabled="selectedIBAIds.length === 0" type="primary" :loading="ibaAssigning" @click="handleAssignIBAs">
            确认添加 ({{ selectedIBAIds.length }})
          </a-button>
        </div>
      </a-spin>
    </a-modal>

    <!-- IBA 扩展组件 -->
    <IBAExtension ref="ibaExtRef" />


    <!-- 图标选择弹窗 -->
    <a-modal v-model:open="iconModalVisible" title="选择图标" width="600px" :footer="null" :mask-closable="false">
      <a-input-search v-model:value="iconSearch" placeholder="搜索图标..." style="margin-bottom:12px" size="large" />
      <div class="st-icon-grid">
        <div
          v-for="ico in filteredIconOptions"
          :key="ico.value"
          :class="['st-icon-item', { 'st-icon-item-selected': editForm.icon === ico.value }]"
          @click="selectIcon(ico.value)"
        >
          <component :is="ico.component" style="font-size:24px;color:#1677ff" />
          <span class="st-icon-item-label">{{ ico.label }}</span>
        </div>
      </div>
      <div v-if="filteredIconOptions.length === 0" style="text-align:center;padding:20px;color:#bfbfbf">未找到匹配的图标</div>
      <div style="margin-top:12px;text-align:right">
        <a-button v-if="editForm.icon" size="small" @click="selectIcon('')">清除图标</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { message } from 'ant-design-vue'
import {
  ReloadOutlined, DeleteOutlined, PlusOutlined, SearchOutlined, SaveOutlined, ToolOutlined, RightOutlined,
  FolderOutlined, FileOutlined, ClusterOutlined, PictureOutlined,
  TagOutlined, CodeOutlined, ApartmentOutlined, LinkOutlined, EyeOutlined, CheckCircleOutlined, FileTextOutlined,
  AppstoreOutlined, BookOutlined, BuildOutlined, BulbOutlined, CalendarOutlined, CameraOutlined,
  CarOutlined, CloudOutlined, CompassOutlined, CrownOutlined, DashboardOutlined, DatabaseOutlined,
  EnvironmentOutlined, ExperimentOutlined, FireOutlined, FlagOutlined, GiftOutlined, GlobalOutlined,
  HeartOutlined, HomeOutlined, InboxOutlined, KeyOutlined, LaptopOutlined, MailOutlined,
  MoneyCollectOutlined, NotificationOutlined, PhoneOutlined, PushpinOutlined, QrcodeOutlined,
  RocketOutlined, SafetyOutlined, SettingOutlined, ShopOutlined, SkinOutlined, SoundOutlined,
  StarOutlined, SwitcherOutlined, TabletOutlined, TagOutlined as TagOutlined2, ThunderboltOutlined,
  TrophyOutlined, UserOutlined, VideoCameraOutlined, WalletOutlined,
  AuditOutlined, BankOutlined, BarChartOutlined, BellOutlined, BlockOutlined, BorderOutlined,
  BranchesOutlined, BugOutlined, CarryOutOutlined, CiCircleOutlined, ClockCircleOutlined,
  CoffeeOutlined, ContainerOutlined, ControlOutlined, CopyOutlined, CopyrightOutlined,
  CreditCardOutlined, CustomerServiceOutlined, DeploymentUnitOutlined, DesktopOutlined,
  DingtalkOutlined, DisconnectOutlined, DollarOutlined, DotChartOutlined, DownloadOutlined,
  DragOutlined, DropboxOutlined, EditOutlined, ExceptionOutlined, ExclamationCircleOutlined,
  ExportOutlined, FieldBinaryOutlined, FieldNumberOutlined, FieldStringOutlined, FieldTimeOutlined,
  FilterOutlined, ForkOutlined, FormatPainterOutlined, FrownOutlined, FunctionOutlined,
  FundOutlined, GatewayOutlined, GoldOutlined, GroupOutlined, HddOutlined, HighlightOutlined,
  HistoryOutlined, HolderOutlined, IdcardOutlined, ImportOutlined, InfoCircleOutlined,
  InsertRowBelowOutlined, InteractionOutlined, LayoutOutlined, LikeOutlined, LineChartOutlined,
  LoadingOutlined, LockOutlined, MacCommandOutlined, MedicineBoxOutlined, MehOutlined,
  MenuOutlined, MergeCellsOutlined, MessageOutlined, MobileOutlined, MonitorOutlined,
  NodeExpandOutlined, NodeIndexOutlined, PartitionOutlined, PercentageOutlined, PieChartOutlined,
  PrinterOutlined, ProfileOutlined, ProjectOutlined, PropertySafetyOutlined, ReadOutlined,
  ReconciliationOutlined, RedEnvelopeOutlined, RestOutlined, RobotOutlined, ScanOutlined,
  ScheduleOutlined, ScissorOutlined, SecurityScanOutlined, SelectOutlined, SendOutlined,
  ShareAltOutlined, ShoppingCartOutlined, SisternodeOutlined, SketchOutlined, SlidersOutlined,
  SmileOutlined, SolutionOutlined, SplitCellsOutlined, StockOutlined, StopOutlined,
  SubnodeOutlined, SyncOutlined, TableOutlined, TagsOutlined, TeamOutlined, ToTopOutlined,
  TransactionOutlined, TranslationOutlined, UngroupOutlined, UnlockOutlined, UnorderedListOutlined,
  UploadOutlined, UsbOutlined, VerifiedOutlined, VerticalAlignBottomOutlined, WarningOutlined,
  WechatOutlined, WifiOutlined, WindowsOutlined, ZoomInOutlined, ZoomOutOutlined,
  SwapOutlined,
} from '@ant-design/icons-vue'
import RuleBindPanel from '@/views/system/RuleBindPanel.vue'
import DataTable from '@/components/DataTable.vue'
import IBAExtension from '@/components/IBAExtension.vue'
import {
  getTypeDefinitionTree, getTypeDefinition, createTypeDefinition,
  updateTypeDefinition, deleteTypeDefinition,
  getTypeNumberRuleLink, bindTypeNumberRule,
  getTypeVersionRuleLink, bindTypeVersionRule,
  getTypeLifecycleTemplateLink, bindTypeLifecycleTemplate,
  getTypeMappings, getUnassignedIBAs, batchAssignIBAs, removeIBAMapping,
  getAttributeDefinitions, batchUpdateAttributeLayout,
  getIBAList, deleteIBA,
  getInheritedMappings,
  getNumberRules, getNumberRule,
  getVersionRules, getVersionRuleByCode,
  getLifecycleTemplates, getLifecycleTemplate,
  getTypeClassificationLink, bindTypeClassification, unbindTypeClassification,
  getClassificationTree, getClassification,
} from '@/api'

// ============ 图标 ============
const ibaExtRef = ref(null)

const iconOptions = [
  // ---- 通用 ----
  { value: 'FolderOutlined', label: '文件夹', component: FolderOutlined },
  { value: 'FileOutlined', label: '文件', component: FileOutlined },
  { value: 'HomeOutlined', label: '首页', component: HomeOutlined },
  { value: 'SettingOutlined', label: '设置', component: SettingOutlined },
  { value: 'UserOutlined', label: '用户', component: UserOutlined },
  { value: 'TeamOutlined', label: '团队', component: TeamOutlined },
  // ---- 产品研发阶段交付件 ----
  { value: 'AppstoreOutlined', label: '产品系列', component: AppstoreOutlined },
  { value: 'ClusterOutlined', label: '产品型号', component: ClusterOutlined },
  { value: 'GlobalOutlined', label: '市场调研报告', component: GlobalOutlined },
  { value: 'BookOutlined', label: '技术文档', component: BookOutlined },
  { value: 'ProfileOutlined', label: '需求规格书', component: ProfileOutlined },
  { value: 'DashboardOutlined', label: '产品需求文档', component: DashboardOutlined },
  { value: 'SketchOutlined', label: '概念草图', component: SketchOutlined },
  { value: 'LayoutOutlined', label: '总体方案', component: LayoutOutlined },
  { value: 'BuildOutlined', label: '详细设计', component: BuildOutlined },
  { value: 'ToolOutlined', label: '工程图纸', component: ToolOutlined },
  { value: 'ExperimentOutlined', label: '样机验证', component: ExperimentOutlined },
  { value: 'SafetyOutlined', label: '测试报告', component: SafetyOutlined },
  { value: 'VerifiedOutlined', label: '验收报告', component: VerifiedOutlined },
  { value: 'CarryOutOutlined', label: '工艺文件', component: CarryOutOutlined },
  { value: 'ContainerOutlined', label: 'BOM清单', component: ContainerOutlined },
  { value: 'SendOutlined', label: '交付物', component: SendOutlined },
  { value: 'DeploymentUnitOutlined', label: '部署文档', component: DeploymentUnitOutlined },
  { value: 'AuditOutlined', label: '审计记录', component: AuditOutlined },
  { value: 'TrophyOutlined', label: '量产产品', component: TrophyOutlined },
  { value: 'HistoryOutlined', label: '变更记录', component: HistoryOutlined },
  { value: 'RobotOutlined', label: '自动化方案', component: RobotOutlined },
  { value: 'ControlOutlined', label: '控制计划', component: ControlOutlined },
  { value: 'ThunderboltOutlined', label: '快速原型', component: ThunderboltOutlined },
  { value: 'RocketOutlined', label: '发布版本', component: RocketOutlined },
  { value: 'SolutionOutlined', label: '解决方案', component: SolutionOutlined },
  { value: 'ProjectOutlined', label: '项目计划', component: ProjectOutlined },
  { value: 'PartitionOutlined', label: '模块设计', component: PartitionOutlined },
  { value: 'ForkOutlined', label: '派生型号', component: ForkOutlined },
  { value: 'MergeCellsOutlined', label: '集成方案', component: MergeCellsOutlined },
  { value: 'BranchesOutlined', label: '多方案对比', component: BranchesOutlined },
  // ---- 产品 & 研发 ----
  { value: 'ExperimentOutlined', label: '实验', component: ExperimentOutlined },
  { value: 'RocketOutlined', label: '火箭', component: RocketOutlined },
  { value: 'ProjectOutlined', label: '项目', component: ProjectOutlined },
  { value: 'SolutionOutlined', label: '方案', component: SolutionOutlined },
  { value: 'ControlOutlined', label: '控制', component: ControlOutlined },
  { value: 'PartitionOutlined', label: '分区', component: PartitionOutlined },
  { value: 'ForkOutlined', label: '分支', component: ForkOutlined },
  { value: 'BranchesOutlined', label: '多分支', component: BranchesOutlined },
  { value: 'MergeCellsOutlined', label: '合并', component: MergeCellsOutlined },
  { value: 'SplitCellsOutlined', label: '拆分', component: SplitCellsOutlined },
  { value: 'GatewayOutlined', label: '网关', component: GatewayOutlined },
  { value: 'SisternodeOutlined', label: '节点', component: SisternodeOutlined },
  { value: 'SubnodeOutlined', label: '子节点', component: SubnodeOutlined },
  { value: 'NodeExpandOutlined', label: '展开节点', component: NodeExpandOutlined },
  { value: 'NodeIndexOutlined', label: '节点索引', component: NodeIndexOutlined },
  // ---- 交付件 ----
  { value: 'CarryOutOutlined', label: '执行', component: CarryOutOutlined },
  { value: 'SendOutlined', label: '发送', component: SendOutlined },
  { value: 'DownloadOutlined', label: '下载', component: DownloadOutlined },
  { value: 'UploadOutlined', label: '上传', component: UploadOutlined },
  { value: 'ExportOutlined', label: '导出', component: ExportOutlined },
  { value: 'ImportOutlined', label: '导入', component: ImportOutlined },
  { value: 'ContainerOutlined', label: '容器', component: ContainerOutlined },
  { value: 'HddOutlined', label: '硬盘', component: HddOutlined },
  { value: 'UsbOutlined', label: 'USB', component: UsbOutlined },
  { value: 'PrinterOutlined', label: '打印机', component: PrinterOutlined },
  { value: 'DesktopOutlined', label: '桌面', component: DesktopOutlined },
  { value: 'MobileOutlined', label: '手机', component: MobileOutlined },
  { value: 'TabletOutlined', label: '平板', component: TabletOutlined },
  { value: 'LaptopOutlined', label: '笔记本', component: LaptopOutlined },
  { value: 'MonitorOutlined', label: '显示器', component: MonitorOutlined },
  // ---- 数据 & 文档 ----
  { value: 'DatabaseOutlined', label: '数据库', component: DatabaseOutlined },
  { value: 'TableOutlined', label: '表格', component: TableOutlined },
  { value: 'BarChartOutlined', label: '柱状图', component: BarChartOutlined },
  { value: 'LineChartOutlined', label: '折线图', component: LineChartOutlined },
  { value: 'PieChartOutlined', label: '饼图', component: PieChartOutlined },
  { value: 'DotChartOutlined', label: '散点图', component: DotChartOutlined },
  { value: 'PercentageOutlined', label: '百分比', component: PercentageOutlined },
  { value: 'StockOutlined', label: '库存', component: StockOutlined },
  { value: 'ReadOutlined', label: '阅读', component: ReadOutlined },
  { value: 'ProfileOutlined', label: '文档', component: ProfileOutlined },
  { value: 'BookOutlined', label: '书本', component: BookOutlined },
  { value: 'CopyOutlined', label: '复制', component: CopyOutlined },
  { value: 'ScissorOutlined', label: '剪切', component: ScissorOutlined },
  { value: 'HighlightOutlined', label: '高亮', component: HighlightOutlined },
  { value: 'EditOutlined', label: '编辑', component: EditOutlined },
  { value: 'FormatPainterOutlined', label: '格式刷', component: FormatPainterOutlined },
  // ---- 属性 & 字段 ----
  { value: 'FieldStringOutlined', label: '字符串', component: FieldStringOutlined },
  { value: 'FieldNumberOutlined', label: '数字', component: FieldNumberOutlined },
  { value: 'FieldBinaryOutlined', label: '二进制', component: FieldBinaryOutlined },
  { value: 'FieldTimeOutlined', label: '时间', component: FieldTimeOutlined },
  { value: 'FilterOutlined', label: '过滤', component: FilterOutlined },
  { value: 'TagsOutlined', label: '标签', component: TagsOutlined },
  { value: 'TagOutlined', label: '标记', component: TagOutlined },
  // ---- 状态 & 流程 ----
  { value: 'CheckCircleOutlined', label: '完成', component: CheckCircleOutlined },
  { value: 'ClockCircleOutlined', label: '时钟', component: ClockCircleOutlined },
  { value: 'HistoryOutlined', label: '历史', component: HistoryOutlined },
  { value: 'SyncOutlined', label: '同步', component: SyncOutlined },
  { value: 'DeploymentUnitOutlined', label: '部署', component: DeploymentUnitOutlined },
  { value: 'InteractionOutlined', label: '交互', component: InteractionOutlined },
  { value: 'TransactionOutlined', label: '事务', component: TransactionOutlined },
  { value: 'RestOutlined', label: '休息', component: RestOutlined },
  { value: 'StopOutlined', label: '停止', component: StopOutlined },
  { value: 'LoadingOutlined', label: '加载中', component: LoadingOutlined },
  { value: 'SelectOutlined', label: '选择', component: SelectOutlined },
  // ---- 安全 & 权限 ----
  { value: 'SafetyOutlined', label: '安全', component: SafetyOutlined },
  { value: 'LockOutlined', label: '锁定', component: LockOutlined },
  { value: 'UnlockOutlined', label: '解锁', component: UnlockOutlined },
  { value: 'KeyOutlined', label: '钥匙', component: KeyOutlined },
  { value: 'SecurityScanOutlined', label: '安全扫描', component: SecurityScanOutlined },
  { value: 'VerifiedOutlined', label: '已验证', component: VerifiedOutlined },
  { value: 'AuditOutlined', label: '审计', component: AuditOutlined },
  { value: 'PropertySafetyOutlined', label: '资产安全', component: PropertySafetyOutlined },
  // ---- 通知 & 消息 ----
  { value: 'BellOutlined', label: '铃铛', component: BellOutlined },
  { value: 'NotificationOutlined', label: '通知', component: NotificationOutlined },
  { value: 'MessageOutlined', label: '消息', component: MessageOutlined },
  { value: 'MailOutlined', label: '邮件', component: MailOutlined },
  { value: 'PhoneOutlined', label: '电话', component: PhoneOutlined },
  { value: 'CustomerServiceOutlined', label: '客服', component: CustomerServiceOutlined },
  // ---- 工具 & 其他 ----
  { value: 'ToolOutlined', label: '工具', component: ToolOutlined },
  { value: 'LayoutOutlined', label: '布局', component: LayoutOutlined },
  { value: 'SlidersOutlined', label: '滑块', component: SlidersOutlined },
  { value: 'FunctionOutlined', label: '函数', component: FunctionOutlined },
  { value: 'BugOutlined', label: 'Bug', component: BugOutlined },
  { value: 'RobotOutlined', label: '机器人', component: RobotOutlined },
  { value: 'CoffeeOutlined', label: '咖啡', component: CoffeeOutlined },
  { value: 'ScanOutlined', label: '扫描', component: ScanOutlined },
  { value: 'QrcodeOutlined', label: '二维码', component: QrcodeOutlined },
  { value: 'CameraOutlined', label: '相机', component: CameraOutlined },
  { value: 'VideoCameraOutlined', label: '视频', component: VideoCameraOutlined },
  { value: 'SoundOutlined', label: '声音', component: SoundOutlined },
  { value: 'WifiOutlined', label: 'WiFi', component: WifiOutlined },
  { value: 'CloudOutlined', label: '云', component: CloudOutlined },
  { value: 'GlobalOutlined', label: '全球', component: GlobalOutlined },
  { value: 'CalendarOutlined', label: '日历', component: CalendarOutlined },
  { value: 'ScheduleOutlined', label: '日程', component: ScheduleOutlined },
  { value: 'CarOutlined', label: '汽车', component: CarOutlined },
  { value: 'ShoppingCartOutlined', label: '购物车', component: ShoppingCartOutlined },
  { value: 'CreditCardOutlined', label: '信用卡', component: CreditCardOutlined },
  { value: 'DollarOutlined', label: '美元', component: DollarOutlined },
  { value: 'WalletOutlined', label: '钱包', component: WalletOutlined },
  { value: 'TrophyOutlined', label: '奖杯', component: TrophyOutlined },
  { value: 'StarOutlined', label: '星星', component: StarOutlined },
  { value: 'LikeOutlined', label: '点赞', component: LikeOutlined },
  { value: 'HeartOutlined', label: '心形', component: HeartOutlined },
  { value: 'SmileOutlined', label: '微笑', component: SmileOutlined },
  { value: 'MehOutlined', label: '平淡', component: MehOutlined },
  { value: 'FrownOutlined', label: '皱眉', component: FrownOutlined },
  { value: 'BulbOutlined', label: '灯泡', component: BulbOutlined },
  { value: 'FireOutlined', label: '火焰', component: FireOutlined },
  { value: 'ThunderboltOutlined', label: '闪电', component: ThunderboltOutlined },
  { value: 'FlagOutlined', label: '旗帜', component: FlagOutlined },
  { value: 'PushpinOutlined', label: '图钉', component: PushpinOutlined },
  { value: 'GiftOutlined', label: '礼物', component: GiftOutlined },
  { value: 'CrownOutlined', label: '皇冠', component: CrownOutlined },
  { value: 'BankOutlined', label: '银行', component: BankOutlined },
  { value: 'ShopOutlined', label: '商店', component: ShopOutlined },
  { value: 'CompassOutlined', label: '指南针', component: CompassOutlined },
  { value: 'EnvironmentOutlined', label: '环境', component: EnvironmentOutlined },
  { value: 'MedicineBoxOutlined', label: '医药箱', component: MedicineBoxOutlined },
  { value: 'SkinOutlined', label: '皮肤', component: SkinOutlined },
  { value: 'AppstoreOutlined', label: '应用', component: AppstoreOutlined },
  { value: 'DashboardOutlined', label: '仪表盘', component: DashboardOutlined },
  { value: 'ClusterOutlined', label: '集群', component: ClusterOutlined },
  { value: 'BlockOutlined', label: '区块', component: BlockOutlined },
  { value: 'BorderOutlined', label: '边框', component: BorderOutlined },
  { value: 'UngroupOutlined', label: '取消分组', component: UngroupOutlined },
  { value: 'GroupOutlined', label: '分组', component: GroupOutlined },
  { value: 'SwitcherOutlined', label: '切换', component: SwitcherOutlined },
  { value: 'MenuOutlined', label: '菜单', component: MenuOutlined },
  { value: 'UnorderedListOutlined', label: '列表', component: UnorderedListOutlined },
  { value: 'ShareAltOutlined', label: '分享', component: ShareAltOutlined },
  { value: 'TranslationOutlined', label: '翻译', component: TranslationOutlined },
  { value: 'InfoCircleOutlined', label: '信息', component: InfoCircleOutlined },
  { value: 'WarningOutlined', label: '警告', component: WarningOutlined },
  { value: 'ExclamationCircleOutlined', label: '感叹号', component: ExclamationCircleOutlined },
  { value: 'ExceptionOutlined', label: '异常', component: ExceptionOutlined },
  { value: 'ReconciliationOutlined', label: '对账', component: ReconciliationOutlined },
  { value: 'SketchOutlined', label: '草图', component: SketchOutlined },
  { value: 'DragOutlined', label: '拖拽', component: DragOutlined },
  { value: 'InsertRowBelowOutlined', label: '插入行', component: InsertRowBelowOutlined },
  { value: 'VerticalAlignBottomOutlined', label: '底部对齐', component: VerticalAlignBottomOutlined },
  { value: 'ToTopOutlined', label: '置顶', component: ToTopOutlined },
  { value: 'ZoomInOutlined', label: '放大', component: ZoomInOutlined },
  { value: 'ZoomOutOutlined', label: '缩小', component: ZoomOutOutlined },
  { value: 'DisconnectOutlined', label: '断开', component: DisconnectOutlined },
  { value: 'CopyrightOutlined', label: '版权', component: CopyrightOutlined },
  { value: 'MacCommandOutlined', label: '命令', component: MacCommandOutlined },
  { value: 'IdcardOutlined', label: '身份证', component: IdcardOutlined },
  { value: 'GoldOutlined', label: '金牌', component: GoldOutlined },
  { value: 'FundOutlined', label: '基金', component: FundOutlined },
  { value: 'RedEnvelopeOutlined', label: '红包', component: RedEnvelopeOutlined },
  { value: 'MoneyCollectOutlined', label: '收款', component: MoneyCollectOutlined },
  { value: 'HolderOutlined', label: '持有', component: HolderOutlined },
  { value: 'WindowsOutlined', label: 'Windows', component: WindowsOutlined },
  { value: 'WechatOutlined', label: '微信', component: WechatOutlined },
  { value: 'DingtalkOutlined', label: '钉钉', component: DingtalkOutlined },
  { value: 'DropboxOutlined', label: 'Dropbox', component: DropboxOutlined },
  { value: 'CiCircleOutlined', label: 'CI', component: CiCircleOutlined },
]

const iconMap = {}
iconOptions.forEach(o => { iconMap[o.value] = o.component })

const iconModalVisible = ref(false)
const iconSearch = ref('')

const filteredIconOptions = computed(() => {
  if (!iconSearch.value) return iconOptions
  const kw = iconSearch.value.toLowerCase()
  return iconOptions.filter(o => o.label.includes(kw) || o.value.toLowerCase().includes(kw))
})

function getIconComponent(iconName) {
  if (!iconName) return FileOutlined
  return iconMap[iconName] || FileOutlined
}

function getIconLabel(iconName) {
  if (!iconName) return ''
  const found = iconOptions.find(o => o.value === iconName)
  return found ? found.label : iconName
}

function selectIcon(value) {
  editForm.icon = value
  iconModalVisible.value = false
  iconSearch.value = ''
}

// ============ 树 ============
const treeData = ref([])
const treeLoading = ref(false)
const treeSearch = ref('')
const selectedKeys = ref([])
const expandedKeys = ref([])
const selectedNode = ref(null)
const activeTab = ref('basic')

// 权限
const PLATFORM_OID = '00000000-0000-0000-0000-000000000000'
const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))
// 当前选中的是否为 OOTB 系统预置类型
const isOotbType = computed(() => detail.value?.typeKind === 'OOTB')
// 平台级类型只有平台管理员可编辑；OOTB 类型平台管理员不可创建子类型，租户管理员可以
const canEdit = computed(() => {
  if (!detail.value.tenantOid) return true
  if (detail.value.tenantOid === PLATFORM_OID) return isPlatformAdmin.value
  return true
})
// 新建子类型：非OOTB类型 或 (OOTB类型 且 租户管理员)
const canCreateChild = computed(() => !isOotbType.value || (isOotbType.value && !isPlatformAdmin.value))

function flattenTree(nodes) {
  let result = []
  for (const n of (nodes || [])) {
    result.push(n)
    if (n.children) result = result.concat(flattenTree(n.children))
  }
  return result
}
const flatTypes = computed(() => flattenTree(treeData.value))
const ootbCount = computed(() => flatTypes.value.filter(t => t.typeKind === 'OOTB').length)
const softCount = computed(() => flatTypes.value.filter(t => t.typeKind !== 'OOTB').length)

function buildTree(nodes) {
  return (nodes || []).map(n => ({ ...n, key: n.oid, children: n.children ? buildTree(n.children) : undefined }))
}

const filteredTree = computed(() => {
  if (!treeSearch.value) return treeData.value
  const kw = treeSearch.value.toLowerCase()
  const filter = (nodes) => {
    const result = []
    for (const n of nodes) {
      const match = (n.name || '').toLowerCase().includes(kw) || (n.code || '').toLowerCase().includes(kw)
      const filteredChildren = n.children ? filter(n.children) : []
      if (match || filteredChildren.length > 0) result.push({ ...n, children: filteredChildren.length > 0 ? filteredChildren : n.children })
    }
    return result
  }
  return filter(treeData.value)
})

async function loadTree() {
  treeLoading.value = true
  try {
    const res = await getTypeDefinitionTree()
    treeData.value = buildTree(res?.data || res || [])
    if (treeData.value.length > 0) expandedKeys.value = treeData.value.map(n => n.oid)
  } catch (e) {
    message.error('加载类型树失败')
  } finally {
    treeLoading.value = false
  }
}

function onTreeSelect(keys, { node }) {
  if (keys.length === 0) return
  selectedKeys.value = keys
  selectedNode.value = node
  activeTab.value = 'basic'
  loadDetail(node.oid)
}

// ============ 详情 ============
const detail = ref({})
const detailLoading = ref(false)
const editForm = reactive({ name: '', code: '', icon: '', displayName: '', description: '', enabled: true })
const saving = ref(false)

async function loadDetail(oid) {
  detailLoading.value = true
  try {
    const res = await getTypeDefinition(oid)
    detail.value = res?.data || res || {}
    Object.assign(editForm, {
      name: detail.value.name || '', code: detail.value.code || '',
      icon: detail.value.icon || '',
      displayName: detail.value.displayName || '', description: detail.value.description || '',
      enabled: detail.value.enabled !== false,
    })
    loadNumberRules(); loadVersionRules(); loadLifecycleTemplates()
    loadNumberRuleLink(oid); loadVersionRuleLink(oid); loadLifecycleTemplateLink(oid)
    if (detail.value?.rootTypeCode) {
      loadClassificationTree()
      loadClassificationLink(oid)
    }
  } catch (e) {
    message.error('加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function onTabChange(key) {
  if (key === 'iba') { loadIBAMappings(selectedNode.value?.oid); loadIBADefs() }
  if (key === 'attributes') { loadAttributeDefs(); loadInheritedIBAs() }
}

async function handleUpdateType() {
  saving.value = true
  try {
    await updateTypeDefinition(selectedNode.value.oid, {
      name: editForm.name, icon: editForm.icon,
      displayName: editForm.displayName,
      description: editForm.description, enabled: editForm.enabled,
    })
    message.success('保存成功')
    await loadTree()
    loadDetail(selectedNode.value.oid)
  } catch (e) { message.error('保存失败') } finally { saving.value = false }
}

async function handleDeleteType() {
  if (!confirm('确定删除该类型吗？此操作不可撤销。')) return
  try {
    await deleteTypeDefinition(selectedNode.value.oid)
    message.success('删除成功')
    selectedNode.value = null; selectedKeys.value = []; detail.value = {}
    await loadTree()
  } catch (e) { message.error('删除失败') }
}

// ============ 创建子类型 ============
const createModalVisible = ref(false)
const createForm = reactive({ code: '', name: '', displayName: '', description: '' })

function openCreateChild() {
  Object.assign(createForm, { code: '', name: '', displayName: '', description: '' })
  createModalVisible.value = true
}

async function handleCreateChild() {
  if (!createForm.code || !createForm.name) { message.warning('请填写编码和名称'); return }
  saving.value = true
  try {
    const res = await createTypeDefinition({ ...createForm, parentOid: selectedNode.value.oid, typeKind: 'SOFT_TYPE' })
    const childOid = res?.data?.oid || res?.oid
    message.success('创建成功')
    createModalVisible.value = false

    // 继承父类型的规则绑定：编码规则、版本规则、生命周期模板、IBA 属性
    if (childOid) {
      const inheritTasks = []
      if (selectedNumberRuleCode.value) {
        inheritTasks.push(
          bindTypeNumberRule(childOid, selectedNumberRuleCode.value).catch(() => {})
        )
      }
      if (selectedVersionRuleCode.value) {
        inheritTasks.push(
          bindTypeVersionRule(childOid, selectedVersionRuleCode.value).catch(() => {})
        )
      }
      if (selectedLifecycleTemplateCode.value) {
        inheritTasks.push(
          bindTypeLifecycleTemplate(childOid, selectedLifecycleTemplateCode.value).catch(() => {})
        )
      }
      // 继承父类型的 IBA 属性
      inheritTasks.push(
        inheritParentIBAs(selectedNode.value.oid, childOid, selectedNode.value.code)
      )
      await Promise.all(inheritTasks)
    }

    await loadTree()
  } catch (e) { message.error('创建失败') } finally { saving.value = false }
}

/** 将父类型直接关联的 IBA 属性批量复制给子类型 */
async function inheritParentIBAs(parentOid, childOid, entityCode) {
  try {
    const res = await getTypeMappings(parentOid)
    const mappings = res?.data || res || []
    const ibaOids = (Array.isArray(mappings) ? mappings : []).map(m => m.ibaOid).filter(Boolean)
    if (ibaOids.length === 0) return
    await batchAssignIBAs(childOid, ibaOids)
  } catch { /* 静默失败，不影响创建流程 */ }
}

// ============ 规则绑定 ============
const numberRules = ref([])
const selectedNumberRuleCode = ref(null)
const nrLoading = ref(false)
async function loadNumberRules() { try { const r = await getNumberRules(); numberRules.value = r?.data || r || [] } catch { /* ignore */ } }
async function loadNumberRuleLink(oid) {
  nrLoading.value = true
  try { const r = await getTypeNumberRuleLink(oid); selectedNumberRuleCode.value = (r?.data || r)?.numberRuleCode || null }
  catch { selectedNumberRuleCode.value = null } finally { nrLoading.value = false }
}
async function handleBindNumberRule(item) {
  try { await bindTypeNumberRule(selectedNode.value.oid, item.code); message.success('编码规则绑定成功'); selectedNumberRuleCode.value = item.code }
  catch (e) { message.error('绑定失败') }
}

const versionRules = ref([])
const selectedVersionRuleCode = ref(null)
const vrLoading = ref(false)
async function loadVersionRules() { try { const r = await getVersionRules(); versionRules.value = r?.data || r || [] } catch { /* ignore */ } }
async function loadVersionRuleLink(oid) {
  vrLoading.value = true
  try { const r = await getTypeVersionRuleLink(oid); selectedVersionRuleCode.value = (r?.data || r)?.versionRuleCode || null }
  catch { selectedVersionRuleCode.value = null } finally { vrLoading.value = false }
}
async function handleBindVersionRule(item) {
  try { await bindTypeVersionRule(selectedNode.value.oid, item.code); message.success('版本规则绑定成功'); selectedVersionRuleCode.value = item.code }
  catch (e) { message.error('绑定失败') }
}

const lifecycleTemplates = ref([])
const selectedLifecycleTemplateCode = ref(null)
const lcLoading = ref(false)
async function loadLifecycleTemplates() { try { const r = await getLifecycleTemplates(); lifecycleTemplates.value = r?.data || r || [] } catch { /* ignore */ } }
async function loadLifecycleTemplateLink(oid) {
  lcLoading.value = true
  try { const r = await getTypeLifecycleTemplateLink(oid); selectedLifecycleTemplateCode.value = (r?.data || r)?.lifecycleTemplateCode || null }
  catch { selectedLifecycleTemplateCode.value = null } finally { lcLoading.value = false }
}
async function handleBindLifecycleTemplate(item) {
  try { await bindTypeLifecycleTemplate(selectedNode.value.oid, item.code); message.success('生命周期模板绑定成功'); selectedLifecycleTemplateCode.value = item.code }
  catch (e) { message.error('绑定失败') }
}

// ============ 规则详情加载 ============
const nrDetail = ref(null)
const nrDetailLoading = ref(false)
const vrDetail = ref(null)
const vrDetailLoading = ref(false)
const lcDetail = ref(null)
const lcDetailLoading = ref(false)

// 监听编码规则绑定变化，加载详情
watch(selectedNumberRuleCode, async (code) => {
  if (!code) { nrDetail.value = null; return }
  nrDetailLoading.value = true
  try { const r = await getNumberRule(code); nrDetail.value = r?.data || r || null }
  catch { nrDetail.value = null } finally { nrDetailLoading.value = false }
})
watch(selectedVersionRuleCode, async (code) => {
  if (!code) { vrDetail.value = null; return }
  vrDetailLoading.value = true
  try { const r = await getVersionRuleByCode(code); vrDetail.value = r?.data || r || null }
  catch { vrDetail.value = null } finally { vrDetailLoading.value = false }
})
watch(selectedLifecycleTemplateCode, async (code) => {
  if (!code) { lcDetail.value = null; return }
  lcDetailLoading.value = true
  try { const r = await getLifecycleTemplate(code); lcDetail.value = r?.data || r || null }
  catch { lcDetail.value = null } finally { lcDetailLoading.value = false }
})

// ==================== 分类绑定 ====================
const classificationTree = ref([])
const selectedClassificationOid = ref(null)
const clsLoading = ref(false)
const clsDetail = ref(null)
const clsDetailLoading = ref(false)

// 判断当前选中的类型是否应该显示分类绑定区域
// 规则：如果该类型的 rootTypeCode 对应的 OOTB 实体（如 Part）有 classificationOid 属性
const showClassificationBinding = computed(() => {
  if (!detail.value) return false
  const rootCode = detail.value.rootTypeCode
  if (!rootCode) return false
  // 当前项目中，Part 实体有 classificationOid 字段
  const CLASSIFICATION_ENTITIES = ['PART', 'DOCUMENT']
  return CLASSIFICATION_ENTITIES.includes(rootCode.toUpperCase())
})

async function loadClassificationTree() {
  clsLoading.value = true
  try {
    const r = await getClassificationTree()
    classificationTree.value = r?.data || r || []
  } catch { classificationTree.value = [] } finally { clsLoading.value = false }
}

async function loadClassificationLink(typeOid) {
  clsLoading.value = true
  try {
    const r = await getTypeClassificationLink(typeOid)
    const link = r?.data
    selectedClassificationOid.value = link?.classificationOid || null
    if (link?.classificationOid) {
      try {
        const cr = await getClassification(link.classificationOid)
        clsDetail.value = cr?.data || cr || null
      } catch { clsDetail.value = null }
      buildBreadcrumb(link.classificationOid)
    } else {
      clsDetail.value = null
      clsSelectedBreadcrumb.value = []
    }
  } catch {
    selectedClassificationOid.value = null
    clsDetail.value = null
    clsSelectedBreadcrumb.value = []
  } finally { clsLoading.value = false }
}

const clsTreeModalVisible = ref(false)
const clsSearchText = ref('')
const clsExpandedKeys = ref([])
const clsTreeRef = ref(null)
const clsSelectedBreadcrumb = ref([])

function showClsTreeModal() {
  clsTreeModalVisible.value = true
  clsSearchText.value = ''
}

// 根据搜索文本过滤分类树
const filteredClsTree = computed(() => {
  const keyword = clsSearchText.value?.trim().toLowerCase()
  if (!keyword) return classificationTree.value
  const filter = (nodes) => {
    const result = []
    for (const node of nodes) {
      const name = (node.displayName || node.name || '').toLowerCase()
      const code = (node.code || '').toLowerCase()
      const children = node.children ? filter(node.children) : []
      if (name.includes(keyword) || code.includes(keyword) || children.length > 0) {
        result.push({ ...node, children: children.length > 0 ? children : node.children })
      }
    }
    return result
  }
  return filter(classificationTree.value)
})

function expandAllClsTree() {
  if (clsTreeRef.value) {
    // 收集所有有子节点的 key
    const keys = collectAllKeys(classificationTree.value)
    clsExpandedKeys.value = keys
  }
}

function collapseAllClsTree() {
  clsExpandedKeys.value = []
}

function collectAllKeys(nodes) {
  const keys = []
  for (const node of nodes) {
    if (node.children && node.children.length > 0) {
      keys.push(node.oid)
      keys.push(...collectAllKeys(node.children))
    }
  }
  return keys
}

function onClsTreeExpand(keys) {
  clsExpandedKeys.value = keys
}

// 根据选中节点构建面包屑
function buildBreadcrumb(classificationOid) {
  const path = []
  const find = (nodes, targetOid, parents) => {
    for (const node of nodes) {
      if (node.oid === targetOid) {
        path.push(...parents, node.displayName || node.name)
        return true
      }
      if (node.children && find(node.children, targetOid, [...parents, node.displayName || node.name])) {
        return true
      }
    }
    return false
  }
  find(classificationTree.value, classificationOid, [])
  clsSelectedBreadcrumb.value = path
}

function onClsTreeSelect(selectedKeys, { node }) {
  if (!selectedKeys || selectedKeys.length === 0) return
  const classificationOid = selectedKeys[0]
  buildBreadcrumb(classificationOid)
  clsTreeModalVisible.value = false
  handleBindClassification(classificationOid)
}

async function handleBindClassification(classificationOid) {
  if (!selectedNode.value?.oid) return
  try {
    if (classificationOid) {
      await bindTypeClassification(selectedNode.value.oid, classificationOid)
      selectedClassificationOid.value = classificationOid
      try {
        const cr = await getClassification(classificationOid)
        clsDetail.value = cr?.data || cr || null
      } catch { clsDetail.value = null }
      message.success('已绑定分类')
    } else {
      await unbindTypeClassification(selectedNode.value.oid)
      selectedClassificationOid.value = null
      clsDetail.value = null
      message.success('已解除分类绑定')
    }
  } catch {
    message.error('绑定分类失败')
  }
}

function formatSegment(seg) {
  if (!seg) return '-'
  switch (seg.segmentType) {
    case 'CONST': return `固定: ${seg.fixedValue || ''}`
    case 'SEPARATOR': return `分隔: ${seg.fixedValue || '-'}`
    case 'YEAR': return '年(YYYY)'
    case 'MONTH': return '月(MM)'
    case 'DAY': return '日(DD)'
    case 'SERIAL': return `流水号(${seg.serialLength || 4}位)`
    default: return seg.segmentType || '-'
  }
}

// ============ IBA 已分配 ============
const ibaMappings = ref([])
const ibaLoading = ref(false)
const ibaColumns = [
  { title: '属性名', key: 'ibaName', dataIndex: 'ibaName' },
  { title: '编码', key: 'ibaCode', dataIndex: 'ibaCode' },
  { title: '数据类型', key: 'dataType', dataIndex: 'dataType' },
  { title: '是否必填', key: 'required', dataIndex: 'required', width: 80 },
  { title: '操作', key: 'action', width: 80 },
]
function isRequired(record) {
  const val = record.required
  if (val === true || val === 'true' || val === 1 || val === '1') return true
  return false
}
async function loadIBAMappings(oid) {
  if (!oid) return; ibaLoading.value = true
  try { const r = await getTypeMappings(oid); ibaMappings.value = r?.data || r || [] }
  catch { ibaMappings.value = [] } finally { ibaLoading.value = false }
}

const ibaModalVisible = ref(false)
const ibaSearchKeyword = ref('')
const unassignedIBAs = ref([])
const selectedIBAIds = ref([])
const ibaSelectLoading = ref(false)
const ibaAssigning = ref(false)
const ibaSelectColumns = [
  { title: '属性名', key: 'name', dataIndex: 'name' },
  { title: '编码', key: 'code', dataIndex: 'code' },
  { title: '数据类型', key: 'dataType', dataIndex: 'dataType' },
]

function openIBAAdd() { ibaSearchKeyword.value = ''; selectedIBAIds.value = []; loadUnassignedIBAs(); ibaModalVisible.value = true }
async function loadUnassignedIBAs() {
  ibaSelectLoading.value = true
  try { const r = await getUnassignedIBAs(selectedNode.value.oid, ibaSearchKeyword.value || undefined); unassignedIBAs.value = r?.data || r || [] }
  catch { unassignedIBAs.value = [] } finally { ibaSelectLoading.value = false }
}
function onIBASelectChange(keys) { selectedIBAIds.value = keys }
async function handleAssignIBAs() {
  if (selectedIBAIds.value.length === 0) return; ibaAssigning.value = true
  try { await batchAssignIBAs(selectedNode.value.oid, selectedIBAIds.value); message.success('属性添加成功'); ibaModalVisible.value = false; await loadIBAMappings(selectedNode.value.oid) }
  catch (e) { message.error('添加失败') } finally { ibaAssigning.value = false }
}
async function handleRemoveIBA(record) {
  try { await removeIBAMapping(record.oid); message.success('移除成功'); await loadIBAMappings(selectedNode.value.oid) }
  catch (e) { message.error('移除失败') }
}

// ============ IBA 定义管理 ============
const ibaSubTab = ref('mappings')
const ibaDefs = ref([])
const ibaDefLoading = ref(false)
const ibaDefColumns = [
  { title: '名称', key: 'name', dataIndex: 'name' },
  { title: '编码', key: 'code', dataIndex: 'code' },
  { title: '数据类型', key: 'dataType', dataIndex: 'dataType' },
  { title: '操作', key: 'action', width: 120 },
]
async function loadIBADefs() { ibaDefLoading.value = true; try { const r = await getIBAList(); ibaDefs.value = r?.data || r || [] } catch { ibaDefs.value = [] } finally { ibaDefLoading.value = false } }


async function handleDeleteIBA(record) { try { await deleteIBA(record.oid); message.success('IBA属性已删除'); await loadIBADefs() } catch (e) { message.error('删除失败') } }

// ============ 属性定义 ============
const attributeDefs = ref([])
const attrLoading = ref(false)
const attrChanged = ref(false)
const attrSaving = ref(false)
const uiComponentOptions = [
  { value: 'input', label: '文本框' }, { value: 'textarea', label: '文本域' },
  { value: 'select', label: '下拉框' }, { value: 'switch', label: '开关' },
  { value: 'datepicker', label: '日期选择' }, { value: 'input-number', label: '数字框' },
]
const attrColumns = [
  { title: '属性名', key: 'displayName', width: 160 },
  { title: '来源', key: 'source', width: 70 },
  { title: '数据类型', key: 'dataType', width: 80 },
  { title: '必填', key: 'required', width: 60 },
  { title: '约束', key: 'constraints', ellipsis: true },
]

function isAttrRequired(record) {
  const val = record.required
  if (val === true || val === 'true' || val === 1 || val === '1') return true
  return false
}

function formatConstraints(record) {
  const parts = []
  // constraintsJson 可能是字符串或已解析的对象
  let cj = record.constraintsJson
  if (typeof cj === 'string' && cj) {
    try { cj = JSON.parse(cj) } catch { cj = null }
  }
  if (cj) {
    if (cj.min !== undefined && cj.max !== undefined) parts.push(`数值: ${cj.min}~${cj.max}`)
    else if (cj.min !== undefined) parts.push(`最小值: ${cj.min}`)
    else if (cj.max !== undefined) parts.push(`最大值: ${cj.max}`)
    if (cj.minLength !== undefined && cj.maxLength !== undefined) parts.push(`长度: ${cj.minLength}~${cj.maxLength}`)
    else if (cj.minLength !== undefined) parts.push(`最小长度: ${cj.minLength}`)
    else if (cj.maxLength !== undefined) parts.push(`最大长度: ${cj.maxLength}`)
    if (cj.pattern) parts.push(`正则: ${cj.pattern}`)
    if (cj.enumValues && cj.enumValues.length > 0) parts.push(`枚举: ${cj.enumValues.join(', ')}`)
    if (cj.step !== undefined) parts.push(`步长: ${cj.step}`)
  }
  // defaultValue
  if (record.defaultValue) parts.push(`默认: ${record.defaultValue}`)
  return parts.length > 0 ? parts.join('; ') : '-'
}
async function loadAttributeDefs() {
  if (!selectedNode.value) return; attrLoading.value = true; attrChanged.value = false
  try { const r = await getAttributeDefinitions(selectedNode.value.code || selectedNode.value.name, selectedNode.value.oid, 'SoftType'); attributeDefs.value = (r?.data || r || []).map(d => ({ ...d })) }
  catch { attributeDefs.value = [] } finally { attrLoading.value = false }
}
function markAttrChanged() { attrChanged.value = true }
async function saveAttrLayouts() {
  if (!selectedNode.value) return; attrSaving.value = true
  try {
    const data = attributeDefs.value.map(d => ({ oid: d.oid, required: d.required, listable: d.listable, editable: d.editable, searchable: d.searchable, uiComponent: d.uiComponent, sortOrder: d.sortOrder }))
    await batchUpdateAttributeLayout(data)
    message.success('布局保存成功'); attrChanged.value = false
  } catch (e) { message.error('保存布局失败') } finally { attrSaving.value = false }
}

// ============ 继承属性（父类型的 IBA） ============
const inheritedIBAs = ref([])
const inheritedLoading = ref(false)
const inheritedColumns = [
  { title: '属性名', key: 'ibaName', dataIndex: 'ibaName' },
  { title: '编码', key: 'ibaCode', dataIndex: 'ibaCode' },
  { title: '数据类型', key: 'dataType', dataIndex: 'dataType' },
  { title: '是否必填', key: 'required', dataIndex: 'required' },
  { title: '继承来源', key: 'inheritFrom', dataIndex: 'inheritFrom' },
]

async function loadInheritedIBAs() {
  if (!selectedNode.value || !detail.value.parentOid) {
    inheritedIBAs.value = []
    return
  }
  inheritedLoading.value = true
  try {
    const res = await getInheritedMappings(selectedNode.value.oid, selectedNode.value.code || '')
    const data = res?.data || res || []
    inheritedIBAs.value = (Array.isArray(data) ? data : []).map(d => ({
      ...d,
      parentName: d.parentTypeName || d.parentName || detail.value.parentName || '祖先类型',
    }))
  } catch {
    inheritedIBAs.value = []
  } finally {
    inheritedLoading.value = false
  }
}

// ============ 跳转 PageDesigner ============
const router = useRouter()
function goToPageDesigner() {
  if (!selectedNode.value) { console.warn('[goToPageDesigner] selectedNode is null'); message.warning('请先选择一个类型'); return }
  const oid = selectedNode.value.oid
  if (!oid) { console.warn('[goToPageDesigner] selectedNode has no oid', selectedNode.value); message.error('节点数据异常，请刷新重试'); return }
  console.log('[goToPageDesigner] 跳转参数:', { entityOid: oid, entityType: 'SOFT_TYPE', entityCode: selectedNode.value.code || '', entityName: selectedNode.value.name || '' })
  router.push({ path: '/system/designer', query: { entityOid: oid, entityType: 'SOFT_TYPE', entityCode: selectedNode.value.code || '', entityName: selectedNode.value.name || '' } })
}

// ============ 初始化 ============
onMounted(() => { loadTree() })
</script>

<style scoped>
.st-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* ===== 页头 ===== */
.st-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}
.st-header-left { display: flex; align-items: baseline; gap: 8px; }
.st-header-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.st-title { margin: 0; font-size: 18px; font-weight: 600; color: #1a1a2e; }
.st-subtitle { font-size: 12px; color: #8c8c8c; }

/* ===== 统计栏 ===== */
.st-stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  flex-shrink: 0;
  margin-bottom: 6px;
}
.st-stat-item { display: flex; align-items: center; gap: 5px; }
.st-stat-icon { font-size: 14px; color: #1677ff; }
.st-stat-value { font-size: 14px; font-weight: 700; color: #1a1a2e; min-width: 24px; text-align: center; }
.st-stat-label { font-size: 12px; color: #8c8c8c; }

/* ===== 主体 ===== */
.st-body {
  flex: 1;
  display: flex;
  gap: 12px;
  overflow: hidden;
  min-height: 0;
}

/* 左侧树 */
.st-tree-panel {
  width: 240px;
  flex-shrink: 0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}
.st-tree-search { padding: 8px; border-bottom: 1px solid #f0f0f0; }
.st-tree-spin { flex: 1; overflow: auto; padding: 6px; }
.st-tree-node { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.st-tree-icon { font-size: 13px; color: #8c8c8c; flex-shrink: 0; }
.st-tree-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.st-tree-tag { font-size: 10px; line-height: 16px; }

/* 右侧内容 */
.st-content {
  flex: 1;
  min-width: 0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 12px 16px;
  overflow: hidden;
  background: #fff;
  display: flex;
  flex-direction: column;
}
.st-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #bfbfbf;
  gap: 12px;
}

/* Tabs */
.st-tabs { height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.st-tabs :deep(.ant-tabs-nav) { flex-shrink: 0; }
.st-tabs :deep(.ant-tabs-content-holder) { flex: 1; min-height: 0; overflow: hidden; }
.st-tabs :deep(.ant-tabs-content) { height: 100%; overflow: auto; }

/* 基本信息网格 */
.st-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: #f0f0f0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 16px;
}
.st-info-item {
  background: #fff;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.st-info-full { grid-column: 1 / -1; }
.st-info-label { font-size: 12px; color: #8c8c8c; display: flex; align-items: center; gap: 4px; }
.st-info-icon { font-size: 12px; color: #1677ff; }
.st-info-value { font-size: 14px; color: #1a1a2e; font-weight: 500; }

/* 编辑区域 */
.st-section { margin-top: 16px; padding-top: 16px; border-top: 1px solid #f0f0f0; }
.st-section-title { font-size: 14px; font-weight: 600; margin: 0 0 12px 0; color: #1a1a2e; }

/* 规则绑定 */
.st-rule-list { display: flex; flex-direction: column; gap: 12px; }
.st-rule-detail { padding: 4px 0; }
.st-rule-detail-row { display: flex; align-items: center; gap: 8px; padding: 3px 0; font-size: 13px; }
.st-rule-detail-label { font-size: 12px; color: #8c8c8c; min-width: 56px; flex-shrink: 0; }
.st-rule-detail-section { padding: 6px 0; }
.st-rule-def { font-size: 12px; background: #f5f5f5; padding: 2px 6px; border-radius: 4px; }
.st-segment-list { margin-top: 4px; }
.st-state-list { margin-top: 4px; }

/* IBA */
.st-iba-subtabs { margin-top: -8px; }
.st-iba-subtabs :deep(.ant-tabs-nav) { margin-bottom: 12px; }

/* 属性定义表格内样式 */
.st-cell-name { font-weight: 500; font-size: 13px; }
.st-cell-code { font-size: 11px; color: #8c8c8c; margin-left: 6px; }
.st-cell-constraint { font-size: 12px; color: #595959; }

/* 图标选择器 */
.st-icon-picker {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 11px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color .2s;
  min-height: 32px;
}
.st-icon-picker:hover { border-color: #1677ff; }
.st-icon-picker-disabled { background: #f5f5f5; cursor: not-allowed; }
.st-icon-picker-disabled:hover { border-color: #d9d9d9; }
.st-icon-picker-preview { display: flex; align-items: center; }
.st-icon-picker-placeholder { font-size: 13px; color: #bfbfbf; }

.st-icon-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
}
.st-icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 4px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  cursor: pointer;
  transition: all .15s;
}
.st-icon-item:hover { border-color: #1677ff; background: #f0f5ff; }
.st-icon-item-selected { border-color: #1677ff; background: #e6f4ff; }
.st-icon-item-label { font-size: 11px; color: #8c8c8c; }

/* IBA 定义头部 */
.st-iba-def-header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.st-iba-def-count { font-size: 12px; color: #8c8c8c; }

.st-form-hint { font-size: 11px; color: #8c8c8c; margin-top: 4px; line-height: 1.4; }

/* 规则绑定面板通用样式（与 RuleBindPanel.vue 一致） */
.rule-bind-panel { padding: 0; }
.rb-header { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; background: #fafafa; border-radius: 6px; margin-bottom: 8px; }
.rb-header-left { display: flex; align-items: center; gap: 6px; }
.rb-status-ok { font-size: 13px; color: #52c41a; font-weight: 500; }
.rb-status-none { font-size: 13px; color: #fa8c16; font-weight: 500; }
.rb-desc { font-size: 12px; color: #8c8c8c; margin-bottom: 8px; }
.rb-detail { margin-bottom: 8px; }
</style>
