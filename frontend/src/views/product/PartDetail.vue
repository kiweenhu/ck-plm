<template>
  <div class="part-detail">
    <!-- 顶部标题栏（参考产品系列卡片样式：渐变边条 + 面包屑 + 主信息） -->
    <div class="pd-header">
      <div class="pd-header-main">
        <div class="pd-title">
          <!-- 面包屑：所属产品系列/型号 + 文件夹路径 -->
          <div class="pd-breadcrumb" v-if="productContainer || folderPath">
            <ApartmentOutlined style="color:#1677ff" />
            <a-tag v-if="productContainer" color="cyan" class="pd-location-tag">
              {{ productContainer.name || productContainer.code }}
              <span v-if="productContainer.type === 'PRODUCT_MODEL'" class="pd-location-sub">型号</span>
              <span v-else class="pd-location-sub">系列</span>
            </a-tag>
            <!-- 所属研发阶段（来自 Part.stageOid，与产品线阶段同源显示） -->
            <template v-if="stageName">
              <span class="pd-breadcrumb-sep">/</span>
              <a-tag color="purple" class="pd-location-tag">{{ stageName }}</a-tag>
            </template>
            <span v-if="productContainer && folderPath" class="pd-breadcrumb-sep">/</span>
            <FolderOutlined v-if="folderPath" style="color:#1677ff" />
            <span v-if="folderPath" class="pd-breadcrumb-folder">{{ folderPath }}</span>
          </div>
          <!-- 主标题 + 编码 + meta tag（同一行） -->
          <div class="pd-title-row">
            <h2 class="pd-name">{{ part?.name || '零组件详情' }}</h2>
            <code class="pd-code">{{ part?.number || '-' }}</code>
            <a-tag color="blue">版本 {{ part?.displayVersion || '-' }}</a-tag>
            <a-tag :color="statusColor(part?.statusCode)">{{ part?.statusName || part?.statusCode || '-' }}</a-tag>
            <a-tag v-if="part?.checkedOut" color="orange">已检出: {{ part.checkedOutBy }}</a-tag>
            <a-tag v-else color="green">已检入</a-tag>
            <!-- 转至最新版本：仅当当前查看的是历史版本时显示 -->
            <a-button
              v-if="!isLatestVersion && part"
              type="link"
              size="small"
              class="pd-go-latest"
              @click="onGoToLatestVersion"
            >
              <ArrowRightOutlined /> 转至最新版本
            </a-button>
          </div>
          <!-- 底部时间信息 -->
          <div class="pd-meta-time" v-if="part?.createdAt">
            <ClockCircleOutlined style="color:#8c8c8c" />
            <span>创建于 {{ fmtTime(part?.createdAt) }}</span>
            <span v-if="part?.updater" class="pd-meta-time-sep">·</span>
            <span v-if="part?.updater">{{ part.updater }} 更新于 {{ fmtTime(part?.updatedAt) }}</span>
          </div>
        </div>
        <a-button @click="goBack" class="pd-back-btn">
          <ArrowLeftOutlined /> 返回
        </a-button>
      </div>
    </div>

    <a-spin :spinning="loading" tip="加载零组件详情...">
      <a-tabs v-model:activeKey="activeTab" class="pd-tabs">
        <!-- ========== 1. BOM 结构 ========== -->
        <a-tab-pane key="bom" tab="BOM 结构">
          <!-- Windchill 风格工具栏：分组操作 + 竖线分隔 -->
          <div class="bom-toolbar">
            <!-- 操作分组（每个分组标题在上、按钮在下） -->
            <div class="bom-toolbar-groups">
              <!-- 展开层级：设置 BOM 树默认展开几层 -->
              <div class="bom-toolbar-group">
                <span class="bom-group-label">展开</span>
                <a-button-group>
                  <a-dropdown :trigger="['click']">
                    <a-button size="small" title="设置默认展开层级（当前：{{ expandLabel }}）">
                      <ExpandOutlined /> {{ expandLabel }} <DownOutlined />
                    </a-button>
                    <template #overlay>
                      <a-menu :selected-keys="[String(expandLevel)]" @click="onExpandLevelChange">
                        <a-menu-item key="0">全部展开</a-menu-item>
                        <a-menu-item key="1">展开 1 层</a-menu-item>
                        <a-menu-item key="2">展开 2 层</a-menu-item>
                        <a-menu-item key="3">展开 3 层</a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                </a-button-group>
              </div>

              <div class="bom-toolbar-group">
                <span class="bom-group-label">检出/检入</span>
                <a-button-group>
                  <a-tooltip :title="roTitle('检出选中行')"><a-button size="small" :disabled="!isLatestVersion || !resolveBomTargetPartOid() || bomSelectedCheckedOut" @click="onCheckoutPart"><ExportOutlined /></a-button></a-tooltip>
                  <a-tooltip :title="roTitle('检入选中行')"><a-button size="small" :disabled="!isLatestVersion || !resolveBomTargetPartOid() || !bomSelectedCheckedOut" @click="onCheckinPart"><ImportOutlined /></a-button></a-tooltip>
                  <a-tooltip :title="roTitle('取消检出')"><a-button size="small" :disabled="!isLatestVersion || !resolveBomTargetPartOid() || !bomSelectedCheckedOut" @click="onUndoCheckoutPart"><RollbackOutlined /></a-button></a-tooltip>
                </a-button-group>
              </div>

              <div class="bom-toolbar-group">
                <span class="bom-group-label">添加/移除</span>
                <a-button-group>
                  <a-tooltip :title="roTitle('添加已有子件')"><a-button size="small" :disabled="!isLatestVersion || !part" @click="onAddExistingChild"><PlusSquareOutlined /></a-button></a-tooltip>
                  <a-tooltip :title="roTitle('新建零组件作为子件')"><a-button size="small" :disabled="!isLatestVersion || !part" @click="onNewChild"><PlusCircleOutlined /></a-button></a-tooltip>
                  <a-tooltip :title="roTitle('移除选中子件')"><a-button size="small" danger :disabled="!isLatestVersion || !bomSelectedRow || bomSelectedRow.isRoot" @click="onRemoveChild"><DeleteOutlined /></a-button></a-tooltip>
                </a-button-group>
              </div>

              <div class="bom-toolbar-group">
                <span class="bom-group-label">行操作</span>
                <a-button-group>
                  <a-tooltip title="上移选中行（同步更新行号）"><a-button size="small" :loading="movingBomRow" :disabled="!isLatestVersion || !bomSelectedRow || bomSelectedRow.isRoot" @click="onMoveBomRow('up')"><ArrowUpOutlined /></a-button></a-tooltip>
                  <a-tooltip title="下移选中行（同步更新行号）"><a-button size="small" :loading="movingBomRow" :disabled="!isLatestVersion || !bomSelectedRow || bomSelectedRow.isRoot" @click="onMoveBomRow('down')"><ArrowDownOutlined /></a-button></a-tooltip>
                </a-button-group>
              </div>

              <div class="bom-toolbar-group">
                <span class="bom-group-label">替代</span>
                <a-button-group>
                  <a-tooltip :title="roTitle('设置替代')"><a-button size="small" :disabled="!isLatestVersion || !bomSelectedRow || bomSelectedRow.isRoot" @click="onSubstitute"><SwapOutlined /></a-button></a-tooltip>
                  <a-tooltip :title="roTitle('成组替代')"><a-button size="small" :disabled="!isLatestVersion || !bomSelectedRow || bomSelectedRow.isRoot" @click="onGroupSubstitute"><ClusterOutlined /></a-button></a-tooltip>
                  <a-tooltip :title="roTitle('取消替代')"><a-button size="small" :disabled="!isLatestVersion || !bomSelectedRow || bomSelectedRow.isRoot" @click="onCancelSubstitute"><UndoOutlined /></a-button></a-tooltip>
                </a-button-group>
              </div>

              <div class="bom-toolbar-group">
                <span class="bom-group-label">过滤</span>
                <a-button-group>
                  <a-dropdown :trigger="['click']">
                    <a-tooltip title="切换 BOM 视图">
                      <a-button size="small"><EyeOutlined /><DownOutlined /></a-button>
                    </a-tooltip>
                    <template #overlay>
                      <a-menu :selected-keys="[bomView]" @click="onViewChange">
                        <a-menu-item key="design">设计视图</a-menu-item>
                        <a-menu-item key="manufacturing">制造视图</a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                  <a-dropdown :trigger="['click']">
                    <a-tooltip title="过滤器设置">
                      <a-button size="small"><FilterOutlined /><DownOutlined /></a-button>
                    </a-tooltip>
                    <template #overlay>
                      <a-menu @click="onFilterAction">
                        <a-menu-item key="current">当前过滤器</a-menu-item>
                        <a-menu-item key="edit">编辑过滤器</a-menu-item>
                        <a-menu-divider />
                        <a-menu-item key="clear">清除过滤器</a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                  <!-- 显示/隐藏：控制 BOM 表格列的显隐 -->
                  <a-dropdown :trigger="['click']">
                    <a-tooltip title="表格列显示与隐藏">
                      <a-button size="small"><EyeOutlined /><DownOutlined /></a-button>
                    </a-tooltip>
                    <template #overlay>
                      <div style="padding: 8px 12px; background: #fff; border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.15); min-width: 150px">
                        <a-checkbox
                          v-for="col in toggleableColumns"
                          :key="col.key"
                          :checked="bomColumnVisibility[col.key] !== false"
                          style="display:block;margin:4px 0"
                          @change="(e) => onToggleColumn(col.key, e.target.checked)"
                        >
                          {{ col.title }}
                        </a-checkbox>
                      </div>
                    </template>
                  </a-dropdown>
                </a-button-group>
              </div>

              <div class="bom-toolbar-group">
                <span class="bom-group-label">报告</span>
                <a-button-group>
                  <a-tooltip title="导出 BOM"><a-button size="small" :disabled="!bomTotalCount" @click="onExportBom"><DownloadOutlined /></a-button></a-tooltip>
                  <a-tooltip :title="roTitle('导入 BOM')"><a-button size="small" :disabled="!isLatestVersion" @click="onImportBom"><UploadOutlined /></a-button></a-tooltip>
                  <a-tooltip title="成本报告"><a-button size="small" @click="onCostReport"><DollarOutlined /></a-button></a-tooltip>
                  <a-button title="对比两个版本的 BOM 差异" size="small" :disabled="historyList.length < 2" @click="onCompareBom"><DiffOutlined /></a-button>
                </a-button-group>
              </div>

              <!-- 子件统计：靠右 -->
              <div class="bom-toolbar-group bom-toolbar-count-group">
                <span class="bom-group-label">统计</span>
                <span class="bom-toolbar-count">共 {{ bomTotalCount }} 个子件</span>
              </div>
            </div>
          </div>

          <!-- 两栏布局：左侧 BOM 结构树 + 右侧选中项详情面板（中间可拖拽调整宽度） -->
          <div class="bom-layout" ref="bomLayoutRef">
            <!-- 左侧：BOM 结构树（主题 + 数量），宽度可拖拽 -->
            <div class="bom-left" :style="{ width: bomLeftWidth ? `${bomLeftWidth}px` : '55%' }">
              <a-table
                :columns="bomColumnsFiltered"
                :data-source="bomTreeData"
                :loading="bomLoading"
                :pagination="false"
                row-key="oid"
                size="small"
                v-model:expandedRowKeys="bomExpandedKeys"
                children-column-name="children"
                :row-class-name="bomRowClassName"
                :custom-row="bomCustomRow"
                :locale="{ emptyText: '暂无 BOM 子件' }"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'subject'">
                    <div class="bom-subject">
                      <!-- 树形连接线：rail（竖线） + branch（竖线 + 水平线连接到父级） -->
                      <span v-if="(record._depth || 0) > 0" class="bom-tree-cells">
                        <template v-for="i in ((record._depth || 0) - 1)" :key="`rail-${i}`">
                          <span class="bom-tree-cell bom-tree-rail"></span>
                        </template>
                        <span class="bom-tree-cell bom-tree-branch"></span>
                      </span>
                      <a-tooltip v-if="record.childCheckedOut" :title="`已检出: ${record.childCheckedOutBy || ''}`">
                        <LockOutlined class="bom-subject-lock" />
                      </a-tooltip>
                      <span v-if="record.isRoot" class="bom-subject-name bom-subject-name-root">
                        {{ record.childName }}
                      </span>
                      <span v-else class="bom-subject-name">{{ record.childName }}</span>
                      <code class="bom-subject-code" :class="{ 'bom-subject-code-root': record.isRoot }">{{ record.childNumber || '-' }}</code>
                      <a-tag v-if="record.childVersion" color="blue" size="small">{{ record.childVersion }}</a-tag>
                      <a-tag v-if="record.childView" color="default" size="small">{{ record.childView }}</a-tag>
                      <a-tag v-if="record.childStatus" :color="statusColor(record.childStatus)" size="small">
                        {{ record.childStatus }}
                      </a-tag>
                    </div>
                  </template>
                  <!-- 这几列取 bomRowValue：正在编辑的这一层以右侧「子件清单」的草稿为准，
                       否则会出现"右边改成 3 了、左边还显示 2" -->
                  <template v-else-if="column.key === 'quantity'">
                    <span v-if="record.isRoot" style="color:#bfbfbf">-</span>
                    <span v-else>{{ bomRowValue(record, 'quantity') ?? '-' }}</span>
                  </template>
                  <template v-else-if="column.key === 'lineNumber'">
                    <span v-if="record.isRoot" style="color:#bfbfbf">-</span>
                    <span v-else>{{ bomRowValue(record, 'lineNumber') ?? '-' }}</span>
                  </template>
                  <template v-else-if="column.key === 'unit'">
                    <span v-if="record.isRoot" style="color:#bfbfbf">-</span>
                    <span v-else>{{ bomRowValue(record, 'unit') || '-' }}</span>
                  </template>
                  <template v-else-if="column.key === 'unitCost'">
                    <span v-if="record.isRoot" style="color:#bfbfbf">-</span>
                    <span v-else>{{ bomRowValue(record, 'unitCost') ?? '-' }}</span>
                  </template>
                </template>
              </a-table>
            </div>

            <!-- 可拖拽分隔条：调整左右两栏宽度 -->
            <div
              class="bom-splitter"
              :class="{ 'bom-splitter-active': isDraggingSplitter }"
              @mousedown="startDragSplitter"
            >
              <span class="bom-splitter-handle"></span>
            </div>

            <!-- 右侧：选中项详情面板（子件清单 / 详情 / 轻量化 / 替代情况 / 关联文档） -->
            <div class="bom-right">
              <a-tabs v-model:activeKey="bomRightTab" size="small" class="bom-right-tabs">
                <!-- 子件清单：行号/数量/单位/单位成本 可编辑 -->
                <a-tab-pane key="items" tab="子件清单">
                  <a-table
                    :columns="bomItemsColumns"
                    :data-source="bomItemsList"
                    :loading="bomLoading"
                    :pagination="false"
                    row-key="oid"
                    size="small"
                    :locale="{ emptyText: '暂无子件' }"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'childNumber'">
                        <code style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959">{{ record.childNumber || '-' }}</code>
                      </template>
                      <template v-else-if="column.key === 'lineNumber'">
                        <a-input-number v-model:value="record.lineNumber" size="small" :min="1" :precision="0" :controls="false" :disabled="!bomItemsEditable" style="width:100%" />
                      </template>
                      <template v-else-if="column.key === 'quantity'">
                        <a-input-number v-model:value="record.quantity" size="small" :min="0" :controls="false" :disabled="!bomItemsEditable" style="width:100%" />
                      </template>
                      <template v-else-if="column.key === 'unit'">
                        <UnitSelect v-model="record.unit" size="small" :disabled="!bomItemsEditable" />
                      </template>
                      <template v-else-if="column.key === 'unitCost'">
                        <a-input-number v-model:value="record.unitCost" size="small" :min="0" :controls="false" :disabled="!bomItemsEditable" style="width:100%" />
                      </template>
                    </template>
                  </a-table>
                  <div class="bom-items-actions">
                    <span class="bom-items-tip">
                      「{{ bomItemsParentName }}」的子件 · 共 {{ bomItemsList.length }} 个
                      <a-tag v-if="!bomItemsEditable" color="orange" size="small" style="margin-left:6px">未检出 · 只读</a-tag>
                      <!-- 改动是否已落到本地数据上一眼可见（此前失焦回弹会让人怀疑"改了没生效"） -->
                      <a-tag v-if="bomItemsDirty" color="blue" size="small" style="margin-left:6px">有未保存修改</a-tag>
                    </span>
                    <a-button type="primary" size="small" :loading="savingBomItems" :disabled="!bomItemsEditable" @click="saveBomItems">
                      <SaveOutlined /> 保存
                    </a-button>
                  </div>
                </a-tab-pane>

                <!-- 详情：选中项（父件或子件）信息 -->
                <a-tab-pane key="detail" tab="详情">
                  <div v-if="bomSelectedRow" class="bom-detail-pane">
                    <a-descriptions :column="1" size="small" bordered>
                      <a-descriptions-item label="名称">{{ bomSelectedRow.childName || '-' }}</a-descriptions-item>
                      <a-descriptions-item label="编码">{{ bomSelectedRow.childNumber || '-' }}</a-descriptions-item>
                      <a-descriptions-item label="版本">{{ bomSelectedRow.childVersion || '-' }}</a-descriptions-item>
                      <a-descriptions-item label="视图">{{ bomSelectedRow.childView || '-' }}</a-descriptions-item>
                      <a-descriptions-item label="状态">
                        <a-tag v-if="bomSelectedRow.childStatus" :color="statusColor(bomSelectedRow.childStatus)" size="small">{{ bomSelectedRow.childStatus }}</a-tag>
                        <span v-else>-</span>
                      </a-descriptions-item>
                      <a-descriptions-item v-if="bomSelectedRow.childCheckedOut" label="检出">
                        <a-tag color="orange" size="small">已检出: {{ bomSelectedRow.childCheckedOutBy }}</a-tag>
                      </a-descriptions-item>
                      <!-- 子件专属：BOM 行属性 -->
                      <template v-if="!bomSelectedRow.isRoot">
                        <a-descriptions-item label="数量">{{ bomSelectedRow.quantity ?? '-' }}</a-descriptions-item>
                        <a-descriptions-item label="单位">{{ bomSelectedRow.unit || '-' }}</a-descriptions-item>
                        <a-descriptions-item label="单位成本(RMB)">{{ bomSelectedRow.unitCost != null ? formatCost(bomSelectedRow.unitCost) : '-' }}</a-descriptions-item>
                        <a-descriptions-item label="引用方式">
                          <a-tag v-if="bomSelectedRow.childIterationOid" color="success" size="small">精确</a-tag>
                          <a-tag v-else color="warning" size="small">跟随最新</a-tag>
                        </a-descriptions-item>
                      </template>
                    </a-descriptions>
                  </div>
                  <a-empty v-else description="请选择左侧 BOM 项查看详情" :image-style="{ height: '48px' }" />
                </a-tab-pane>

                <!-- 轻量化 -->
                <a-tab-pane key="lightweight" tab="轻量化">
                  <a-empty description="轻量化预览功能开发中" :image-style="{ height: '48px' }" />
                </a-tab-pane>

                <!-- 替代情况 -->
                <a-tab-pane key="substitute" tab="替代情况">
                  <a-empty description="替代情况功能开发中" :image-style="{ height: '48px' }" />
                </a-tab-pane>

                <!-- 关联文档 -->
                <a-tab-pane key="docs" tab="关联文档">
                  <a-empty description="关联文档功能开发中" :image-style="{ height: '48px' }" />
                </a-tab-pane>
              </a-tabs>
            </div>
          </div>
        </a-tab-pane>

        <!-- ========== 2. 基本属性 ========== -->
        <a-tab-pane key="basic" tab="基本属性">
          <a-card title="基本信息" size="small" :bordered="false" style="margin-bottom:16px">
            <a-descriptions :column="3" bordered size="small">
              <a-descriptions-item label="名称">{{ part?.name || '-' }}</a-descriptions-item>
              <a-descriptions-item label="编码">{{ part?.number || '-' }}</a-descriptions-item>
              <a-descriptions-item label="类型">{{ part?.typeDefinitionCode || '-' }}</a-descriptions-item>
              <a-descriptions-item label="描述" :span="3">{{ part?.description || '-' }}</a-descriptions-item>
              <a-descriptions-item label="单位">{{ part?.unit || '-' }}</a-descriptions-item>
              <a-descriptions-item label="来源">{{ part?.source || '-' }}</a-descriptions-item>
              <a-descriptions-item label="视图">{{ part?.view || '-' }}</a-descriptions-item>
              <a-descriptions-item label="大版本">{{ part?.revision || '-' }}</a-descriptions-item>
              <a-descriptions-item label="小版本">{{ part?.iteration != null ? part.iteration : '-' }}</a-descriptions-item>
              <a-descriptions-item label="生命周期">
                <a-tag :color="statusColor(part?.statusCode)" size="small">{{ part?.statusName || part?.statusCode || '-' }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="检出状态">
                <a-tag v-if="part?.checkedOut" color="orange" size="small">已检出: {{ part.checkedOutBy }}</a-tag>
                <a-tag v-else color="green" size="small">已检入</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="检出注释">{{ part?.checkedOutComment || '-' }}</a-descriptions-item>
              <a-descriptions-item label="创建人">{{ part?.creator || '-' }}</a-descriptions-item>
              <a-descriptions-item label="创建时间">{{ fmtTime(part?.createdAt) }}</a-descriptions-item>
              <a-descriptions-item label="更新人">{{ part?.updater || '-' }}</a-descriptions-item>
            </a-descriptions>
          </a-card>

          <a-card title="IBA 属性" size="small" :bordered="false" style="margin-bottom:16px">
            <a-empty v-if="ibaEntries.length === 0" description="暂无 IBA 属性" :image-style="{ height: '32px' }" />
            <a-descriptions v-else :column="3" bordered size="small">
              <a-descriptions-item v-for="(item, idx) in ibaEntries" :key="idx" :label="item.label">
                {{ item.value }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>

          <a-card title="分类 IBA 属性" size="small" :bordered="false" style="margin-bottom:16px">
            <a-empty v-if="clsIbaEntries.length === 0" description="暂无分类 IBA 属性（请先在基本信息中绑定分类）" :image-style="{ height: '32px' }" />
            <a-descriptions v-else :column="3" bordered size="small">
              <a-descriptions-item v-for="(item, idx) in clsIbaEntries" :key="idx" :label="item.label">
                {{ item.value }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>

          <a-card title="历史版本" size="small" :bordered="false">
            <a-table
              :columns="historyColumns"
              :data-source="historyList"
              :loading="historyLoading"
              :pagination="false"
              row-key="oid"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'version'">
                  <a-tooltip title="点击查看该版本详情" placement="top">
                    <a class="pd-history-link" @click="onViewHistoryVersion(record)">
                      {{ record.displayVersion || (record.revision ? `${record.revision}.${record.iteration}` : '-') }}
                    </a>
                  </a-tooltip>
                </template>
                <template v-else-if="column.key === 'status'">
                  <a-tag :color="statusColor(record.status?.code)" size="small">
                    {{ record.status?.displayName || record.status?.code || '-' }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'checkedOut'">
                  <a-tag v-if="record.checkedOut" color="orange" size="small">已检出: {{ record.checkedOutBy }}</a-tag>
                  <a-tag v-else color="green" size="small">已检入</a-tag>
                </template>
                <template v-else-if="column.key === 'view'">
                  <span>{{ record.view?.name || record.view?.code || '-' }}</span>
                </template>
                <template v-else-if="column.key === 'createdAt'">
                  <span style="font-size:12px;color:#8c8c8c">{{ fmtTime(record.createdAt) }}</span>
                </template>
              </template>
            </a-table>
          </a-card>
        </a-tab-pane>

        <!-- ========== 3. CAD 描述（条件渲染） ========== -->
        <a-tab-pane key="cad" :tab="cadTabLabel">
          <div v-if="isStructural" class="cad-grid">
            <a-row :gutter="16">
              <a-col :span="8">
                <a-card title="三维模型" size="small">
                  <a-empty description="暂无三维模型" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
              <a-col :span="8">
                <a-card title="二维图纸" size="small">
                  <a-empty description="暂无二维图纸" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
              <a-col :span="8">
                <a-card title="工程图" size="small">
                  <a-empty description="暂无工程图" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
            </a-row>
          </div>
          <div v-else-if="isElectronic" class="cad-grid">
            <a-row :gutter="16">
              <a-col :span="8">
                <a-card title="图符" size="small">
                  <a-empty description="暂无图符" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
              <a-col :span="8">
                <a-card title="封装" size="small">
                  <a-empty description="暂无封装" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
              <a-col :span="8">
                <a-card title="Datasheet" size="small">
                  <a-empty description="暂无 Datasheet" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
            </a-row>
          </div>
          <div v-else-if="isElectrical" class="cad-grid">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-card title="电气原理图" size="small">
                  <a-empty description="暂无电气原理图" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
              <a-col :span="12">
                <a-card title="电气接线图" size="small">
                  <a-empty description="暂无电气接线图" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
            </a-row>
          </div>
          <div v-else-if="isSoftware" class="cad-grid">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-card title="软件架构图" size="small">
                  <a-empty description="暂无软件架构图" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
              <a-col :span="12">
                <a-card title="流程图" size="small">
                  <a-empty description="暂无流程图" :image-style="{ height: '48px' }" />
                </a-card>
              </a-col>
            </a-row>
          </div>
          <a-empty v-else description="该类型无需 CAD 描述" :image-style="{ height: '64px' }" />
        </a-tab-pane>

        <!-- ========== 3.5 双向替代（EQUIVALENT 可替代清单） ========== -->
        <a-tab-pane key="twoWaySubstitute" tab="双向替代">
          <!-- 统计栏（用户管理风格）：清单统计 + 操作按钮 -->
          <div class="tab-stats-bar">
            <div class="tab-stat-item">
              <SwapOutlined class="tab-stat-icon" />
              <span class="tab-stat-value">{{ alternateList.length }}</span>
              <span class="tab-stat-label">可替代件</span>
            </div>
            <a-divider type="vertical" style="height:24px" />
            <div class="tab-stat-item">
              <span class="tab-stat-value">{{ alternateList.filter(l => l.enabled !== false).length }}</span>
              <span class="tab-stat-label">启用</span>
            </div>
            <div class="tab-stat-actions">
              <a-button size="small" type="primary" @click="onAddAlternate">
                <PlusOutlined /> 添加替代件
              </a-button>
              <a-button size="small" danger :disabled="!alternateSelectedRowKeys.length" @click="onRemoveAlternates">
                <DeleteOutlined /> 删除替代
              </a-button>
            </div>
          </div>
          <DataTable
            :columns="alternateColumns"
            :data-source="alternateList"
            :loading="alternateLoading"
            :pagination="false"
            row-key="oid"
            size="small"
            :row-selection="{ selectedRowKeys: alternateSelectedRowKeys, onChange: keys => (alternateSelectedRowKeys = keys) }"
            empty-text="暂无可替代部件，点击「添加替代件」建立等效替代关系"
            searchable
            search-placeholder="搜索名称/编码..."
            :search-fields="['_otherName', '_otherNumber']"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'part'">
                <span style="color:#262626">{{ record._otherName }}</span>
                <router-link
                  v-if="record._otherOid"
                  :to="`/part/${record._otherOid}`"
                  class="alternate-part-link"
                  :title="`查看 ${record._otherName || ''} 最新版本详情`"
                >{{ record._otherNumber || '-' }}</router-link>
                <code v-else style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959;margin-left:6px">{{ record._otherNumber || '-' }}</code>
              </template>
              <template v-else-if="column.key === 'alternateType'">
                <a-tag :color="(ALTERNATE_TYPE_MAP[record.alternateType] || {}).color || 'default'" size="small">
                  {{ (ALTERNATE_TYPE_MAP[record.alternateType] || {}).label || record.alternateType || '-' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'alternateQuantity'">
                {{ record.alternateQuantity ?? 1 }}
              </template>
              <template v-else-if="column.key === 'alternateUnit'">
                {{ record.alternateUnit || '-' }}
              </template>
              <template v-else-if="column.key === 'enabled'">
                <a-tag v-if="record.enabled !== false" color="green" size="small">启用</a-tag>
                <a-tag v-else color="default" size="small">停用</a-tag>
              </template>
            </template>
          </DataTable>
        </a-tab-pane>

        <!-- ========== 4. 关联文档（说明 DESCRIPTION / 参考 REFERENCE） ========== -->
        <a-tab-pane key="relatedDocs" tab="关联文档">
          <a-tabs v-model:activeKey="docTab" size="small">
            <!-- 说明文档 -->
            <a-tab-pane key="desc" tab="说明">
              <!-- 统计栏（用户管理风格） -->
              <div class="tab-stats-bar">
                <div class="tab-stat-item">
                  <FileTextOutlined class="tab-stat-icon" />
                  <span class="tab-stat-value">{{ descDocList.length }}</span>
                  <span class="tab-stat-label">说明文档</span>
                </div>
                <div class="tab-stat-actions">
                  <a-button size="small" type="primary" @click="onAddDocLink('DESCRIBES')">
                    <PlusOutlined /> 添加说明文档
                  </a-button>
                  <a-button size="small" danger :disabled="!descSelectedKeys.length" @click="onRemoveDocLinks('DESCRIPTION')">
                    <DeleteOutlined /> 删除关联
                  </a-button>
                </div>
              </div>
              <DataTable
                :columns="docLinkColumns"
                :data-source="descDocList"
                :loading="docLinksLoading"
                :pagination="false"
                row-key="oid"
                size="small"
                :row-selection="{ selectedRowKeys: descSelectedKeys, onChange: keys => (descSelectedKeys = keys) }"
                empty-text="暂无说明文档，点击「添加说明文档」建立关联"
                searchable
                search-placeholder="搜索名称/编码..."
                :search-fields="['_docName', '_docNumber']"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'doc'">
                    <span style="color:#262626">{{ record._docName }}</span>
                    <a
                      v-if="record.documentOid"
                      class="alternate-part-link"
                      title="查看文档详情"
                      @click="viewDocDetail(record)"
                    >{{ record._docNumber || '-' }}</a>
                    <code v-else style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959;margin-left:6px">{{ record._docNumber || '-' }}</code>
                  </template>
                  <template v-else-if="column.key === 'docType'">
                    <a-tag color="blue" size="small">{{ record._docType || '-' }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'createdAt'">
                    {{ fmtTime(record.createdAt) }}
                  </template>
                </template>
              </DataTable>
            </a-tab-pane>
            <!-- 参考文档 -->
            <a-tab-pane key="ref" tab="参考">
              <!-- 统计栏（用户管理风格） -->
              <div class="tab-stats-bar">
                <div class="tab-stat-item">
                  <BookOutlined class="tab-stat-icon" />
                  <span class="tab-stat-value">{{ refDocList.length }}</span>
                  <span class="tab-stat-label">参考文档</span>
                </div>
                <div class="tab-stat-actions">
                  <a-button size="small" type="primary" @click="onAddDocLink('REFERENCE')">
                    <PlusOutlined /> 添加参考文档
                  </a-button>
                  <a-button size="small" danger :disabled="!refSelectedKeys.length" @click="onRemoveDocLinks('REFERENCE')">
                    <DeleteOutlined /> 删除关联
                  </a-button>
                </div>
              </div>
              <DataTable
                :columns="docLinkColumns"
                :data-source="refDocList"
                :loading="docLinksLoading"
                :pagination="false"
                row-key="oid"
                size="small"
                :row-selection="{ selectedRowKeys: refSelectedKeys, onChange: keys => (refSelectedKeys = keys) }"
                empty-text="暂无参考文档，点击「添加参考文档」建立关联"
                searchable
                search-placeholder="搜索名称/编码..."
                :search-fields="['_docName', '_docNumber']"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'doc'">
                    <span style="color:#262626">{{ record._docName }}</span>
                    <a
                      v-if="record.documentOid"
                      class="alternate-part-link"
                      title="查看文档详情"
                      @click="viewDocDetail(record)"
                    >{{ record._docNumber || '-' }}</a>
                    <code v-else style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959;margin-left:6px">{{ record._docNumber || '-' }}</code>
                  </template>
                  <template v-else-if="column.key === 'docType'">
                    <a-tag color="blue" size="small">{{ record._docType || '-' }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'createdAt'">
                    {{ fmtTime(record.createdAt) }}
                  </template>
                </template>
              </DataTable>
            </a-tab-pane>
          </a-tabs>
        </a-tab-pane>

        <!-- ========== 5. 被使用情况 ========== -->
        <a-tab-pane key="usedBy" tab="被使用情况">
          <a-table
            :columns="usedByColumns"
            :data-source="usedByList"
            :loading="usedByLoading"
            :pagination="false"
            row-key="oid"
            size="small"
            :locale="{ emptyText: '未被其他零组件使用' }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'exactRef'">
                <a-tag v-if="record.childIterationOid" color="success" size="small">精确</a-tag>
                <a-tag v-else color="warning" size="small">跟随最新</a-tag>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- ========== 6. 流程情况（关联流程：执行中 / 已执行 两栏，所有业务对象共用同一组件） ========== -->
        <a-tab-pane key="workflow" tab="流程情况">
          <RelatedProcesses :entity-oid="oid" />
        </a-tab-pane>

        <!-- ========== 7. 变更情况 ========== -->
        <a-tab-pane key="changes" tab="变更情况">
          <a-empty description="变更情况功能开发中" :image-style="{ height: '64px' }" />
        </a-tab-pane>

        <!-- ========== 8. 关联需求 ========== -->
        <a-tab-pane key="requirements" tab="关联需求">
          <a-empty description="需求管理开发中" :image-style="{ height: '64px' }" />
        </a-tab-pane>

        <!-- ========== 9. 关联问题 ========== -->
        <a-tab-pane key="issues" tab="关联问题">
          <a-empty description="问题管理开发中" :image-style="{ height: '64px' }" />
        </a-tab-pane>
      </a-tabs>
    </a-spin>

    <!-- 检出注释弹窗 -->
    <a-modal
      v-model:visible="checkoutModalVisible"
      title="检出零组件"
      ok-text="确认检出"
      cancel-text="取消"
      :confirm-loading="checkoutSaving"
      @ok="confirmCheckout"
      @cancel="checkoutModalVisible = false"
      width="480px"
      centered
    >
      <div v-if="checkoutTarget" style="margin-bottom:12px">
        <a-tag color="blue" size="small">{{ checkoutTarget.number || '-' }}</a-tag>
        <span style="font-weight:500;margin-left:6px">{{ checkoutTarget.name || '-' }}</span>
        <span style="color:#8c8c8c;margin-left:6px">· {{ checkoutTarget.version || '-' }}</span>
      </div>
      <div style="margin-bottom:4px;font-size:12px;color:#666">检出注释（可选）</div>
      <a-textarea
        v-model:value="checkoutComment"
        placeholder="请输入检出注释（如：修改方向、检出原因等，可不填）"
        :rows="3"
        :maxlength="500"
        show-count
      />
    </a-modal>

    <!-- 添加已有子件弹窗 -->
    <a-modal
      v-model:visible="addExistingModalVisible"
      title="添加已有的零组件"
      ok-text="添加"
      cancel-text="取消"
      :confirm-loading="false"
      :ok-button-props="{ disabled: !addExistingSelectedRowKeys.length }"
      @ok="confirmAddExisting"
      @cancel="addExistingModalVisible = false"
      width="720px"
      centered
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <!-- 过滤区：产品系列/型号（下拉可选）+ 名称/编码搜索 -->
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px;flex-wrap:wrap">
        <span style="color:#8c8c8c;font-size:12px;white-space:nowrap">所属容器</span>
        <a-select
          v-model:value="addExistingContainerOid"
          placeholder="选择产品系列/型号"
          allow-clear
          show-search
          style="width: 240px"
          @change="onAddExistingContainerChange"
        >
          <a-select-option v-for="o in containerOptions" :key="o.value" :value="o.value">
            {{ o.label }}
          </a-select-option>
        </a-select>
        <a-input-search
          v-model:value="addExistingKeyword"
          placeholder="按名称或编码搜索"
          style="flex:1;min-width:180px;margin-left:8px"
          allow-clear
          @search="onAddExistingSearch"
        />
      </div>

      <a-table
        :columns="addExistingColumns"
        :data-source="addExistingList"
        :loading="addExistingLoading"
        :pagination="{ pageSize: 10, showSizeChanger: true, pageSizeOptions: ['10', '20', '50'], showTotal: (t) => `共 ${t} 条` }"
        :row-selection="{ selectedRowKeys: addExistingSelectedRowKeys, onChange: onAddExistingSelectChange }"
        row-key="oid"
        size="small"
        :locale="{ emptyText: '暂无可选零组件' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'number'">
            <code style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959">{{ record.number || '-' }}</code>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag color="blue">{{ record.typeDefinitionCode || '-' }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- 文档详情查看抽屉（关联文档编码点击打开，复用产品线 DocumentViewer） -->
    <DocumentViewer
      v-model:visible="docViewerVisible"
      :doc="docViewerDoc"
      @close="docViewerVisible = false"
    />

    <!-- 添加替代件弹窗（双向替代 EQUIVALENT）：可搜索选择 Part 对象 -->
    <a-modal
      v-model:visible="addAlternateModalVisible"
      title="添加替代件"
      ok-text="添加"
      cancel-text="取消"
      :ok-button-props="{ disabled: !addAlternateSelectedRowKeys.length }"
      @ok="confirmAddAlternate"
      @cancel="addAlternateModalVisible = false"
      width="720px"
      centered
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <!-- 过滤区：产品系列/型号（下拉可选）+ 名称/编码搜索 -->
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px;flex-wrap:wrap">
        <span style="color:#8c8c8c;font-size:12px;white-space:nowrap">所属容器</span>
        <a-select
          v-model:value="addAlternateContainerOid"
          placeholder="选择产品系列/型号"
          allow-clear
          show-search
          style="width: 240px"
          @change="onAddAlternateContainerChange"
        >
          <a-select-option v-for="o in containerOptions" :key="o.value" :value="o.value">
            {{ o.label }}
          </a-select-option>
        </a-select>
        <a-input-search
          v-model:value="addAlternateKeyword"
          placeholder="按名称或编码搜索"
          style="flex:1;min-width:180px;margin-left:8px"
          allow-clear
          @search="onAddAlternateSearch"
        />
      </div>

      <a-table
        :columns="addExistingColumns"
        :data-source="addAlternateList"
        :loading="addAlternateLoading"
        :pagination="{ pageSize: 10, showSizeChanger: true, pageSizeOptions: ['10', '20', '50'], showTotal: (t) => `共 ${t} 条` }"
        :row-selection="{ selectedRowKeys: addAlternateSelectedRowKeys, onChange: keys => (addAlternateSelectedRowKeys = keys) }"
        row-key="oid"
        size="small"
        :locale="{ emptyText: '暂无可选零组件' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'number'">
            <code style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959">{{ record.number || '-' }}</code>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag color="blue">{{ record.typeDefinitionCode || '-' }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- 添加关联文档弹窗（参考 REFERENCE / 说明 DESCRIPTION）：可搜索选择 Document 对象 -->
    <a-modal
      v-model:visible="addDocModalVisible"
      :title="addDocLinkType === 'REFERENCE' ? '添加参考文档' : '添加说明文档'"
      ok-text="添加"
      cancel-text="取消"
      :ok-button-props="{ disabled: !addDocSelectedRowKeys.length }"
      @ok="confirmAddDocLink"
      @cancel="addDocModalVisible = false"
      width="720px"
      centered
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <!-- 过滤区：产品系列/型号（下拉可选）+ 名称/编码搜索（本地过滤） -->
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px;flex-wrap:wrap">
        <span style="color:#8c8c8c;font-size:12px;white-space:nowrap">所属容器</span>
        <a-select
          v-model:value="addDocContainerOid"
          placeholder="选择产品系列/型号"
          allow-clear
          show-search
          style="width: 240px"
          @change="onAddDocContainerChange"
        >
          <a-select-option v-for="o in containerOptions" :key="o.value" :value="o.value">
            {{ o.label }}
          </a-select-option>
        </a-select>
        <a-input-search
          v-model:value="addDocKeyword"
          placeholder="按名称或编码过滤"
          style="flex:1;min-width:180px;margin-left:8px"
          allow-clear
        />
      </div>

      <a-table
        :columns="addExistingColumns"
        :data-source="filteredAddDocList"
        :loading="addDocLoading"
        :pagination="{ pageSize: 10, showSizeChanger: true, pageSizeOptions: ['10', '20', '50'], showTotal: (t) => `共 ${t} 条` }"
        :row-selection="{ selectedRowKeys: addDocSelectedRowKeys, onChange: keys => (addDocSelectedRowKeys = keys) }"
        row-key="oid"
        size="small"
        :locale="{ emptyText: '暂无可选文档' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'number'">
            <code style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959">{{ record.number || '-' }}</code>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag color="blue">{{ record.typeDefinitionCode || '-' }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- BOM 成本报告（卷积）：总成本 + 逐层明细 + 口径与数据缺口提示 -->
    <BomCostReportModal
      v-model:open="costReportOpen"
      :parent-iteration-oid="currentIterationOid"
      :fallback-label="part?.name"
    />

    <!-- BOM 版本对比对话框：选择两个版本，预计算并展示 BOM 差异（新增/移除/修改行数） -->
    <a-modal
      :visible="compareModalVisible"
      @update:visible="val => (compareModalVisible = val)"
      :destroy-on-hidden="true"
      title="BOM 版本对比"
      :footer="null"
      width="640px"
      centered
      :body-style="{ padding: '16px 20px 8px' }"
      @cancel="compareModalVisible = false"
    >
      <div class="bom-compare-row">
        <a-select v-model:value="compareFromOid" placeholder="源版本（起始）" style="flex:1">
          <a-select-option v-for="o in compareHistoryOptions" :key="o.value" :value="o.value">
            {{ o.label }}
          </a-select-option>
        </a-select>
        <span>→</span>
        <a-select v-model:value="compareToOid" placeholder="目标版本（结束）" style="flex:1">
          <a-select-option v-for="o in compareHistoryOptions" :key="o.value" :value="o.value">
            {{ o.label }}
          </a-select-option>
        </a-select>
        <a-button type="primary" :loading="compareLoading" @click="loadBomDiff">对比</a-button>
      </div>

      <!-- 对比结果：无差异提示 或 差异明细列表 -->
      <div v-if="compareResult" class="bom-compare-result">
        <div v-if="compareIsEmpty" style="padding:8px 0 16px">
          <a-result status="success" title="两版本之间无差异" />
        </div>
        <template v-else>
          <a-alert type="warning" show-icon style="margin-bottom:12px">
            <template #message>
              新增 <b style="color:#52c41a">{{ compareResult.addedCount || 0 }}</b> 行，移除 <b style="color:#ff4d4f">{{ compareResult.removedCount || 0 }}</b> 行，修改 <b style="color:#faad14">{{ compareResult.changedCount || 0 }}</b> 行
            </template>
          </a-alert>
          <div class="bom-compare-detail">
            <div v-if="compareDetail?.added?.length" class="bom-compare-block bom-compare-add">
              <div class="bom-compare-block-title">
                <span class="bom-dot bom-dot-add" /> 新增（目标版本新增的子件）
              </div>
              <ul>
                <li v-for="(r, i) in compareDetail.added" :key="'a'+i">{{ compareRowText(r) }}</li>
              </ul>
            </div>
            <div v-if="compareDetail?.removed?.length" class="bom-compare-block bom-compare-del">
              <div class="bom-compare-block-title">
                <span class="bom-dot bom-dot-del" /> 移除（源版本存在、目标版本已删除）
              </div>
              <ul>
                <li v-for="(r, i) in compareDetail.removed" :key="'r'+i">{{ compareRowText(r) }}</li>
              </ul>
            </div>
            <div v-if="compareDetail?.changed?.length" class="bom-compare-block bom-compare-chg">
              <div class="bom-compare-block-title">
                <span class="bom-dot bom-dot-chg" /> 修改（用量 / 单位 / 版本等变化）
              </div>
              <ul>
                <li v-for="(r, i) in compareDetail.changed" :key="'c'+i">
                  {{ compareRowText(r) }}
                  <span v-if="r.from && r.from.quantity !== r.quantity" class="bom-compare-from-to">
                    （{{ r.from.quantity }}{{ r.from.unit || '' }} → {{ r.quantity }}{{ r.unit || '' }}）
                  </span>
                </li>
              </ul>
            </div>
            <div
              v-if="!compareDetail?.added?.length && !compareDetail?.removed?.length && !compareDetail?.changed?.length"
              style="padding:4px 0 16px"
            >
              <a-empty description="未检测到差异明细" />
            </div>
          </div>
        </template>
      </div>

      <div v-if="compareLoaded && !compareResult" class="bom-compare-empty">
        <a-empty description="对比未返回结果，请检查所选版本是否有效" />
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, PlusOutlined, PlusSquareOutlined, PlusCircleOutlined, DownloadOutlined, PartitionOutlined, ApartmentOutlined, FolderOutlined, ExportOutlined, ImportOutlined, InfoCircleOutlined, LockOutlined, RollbackOutlined, EyeOutlined, FilterOutlined, DownOutlined, DeleteOutlined, UploadOutlined, DollarOutlined, SwapOutlined, ClusterOutlined, UndoOutlined, ClockCircleOutlined, ArrowRightOutlined, ArrowUpOutlined, ArrowDownOutlined, SaveOutlined, DiffOutlined, ExpandOutlined, FileTextOutlined, BookOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { registerDynamicStages, getStageTitle } from '@/utils/stageDefs'
import {
  getEntityByCode, getPartIterations,
  getBomLinksByParentIteration, getBomLinksByChildPart, getBomTree,
  createBomLinks, deleteBomLinks, updateBomLinks,
  getPartAlternateLinksByPart,
  createPartAlternateLink, deletePartAlternateLink,
  getPartDocLinks, createPartDocLink, deletePartDocLink, promotePartDocLink,
  getDocuments, getDocument, getFolderDocumentDetails,
  getProductLine, getProductModel, getProductLines, getProductModels, getFolderByOid, getClassificationIBAs,
  getStages,
  checkoutPart, checkinPart, undoCheckoutPart,
  getPartsByContainer,
  getBomDiff, compareBomVersions
} from '@/api'
import UnitSelect from '@/components/UnitSelect.vue'
import DataTable from '@/components/DataTable.vue'
import RelatedProcesses from '@/components/RelatedProcesses.vue'
import BomCostReportModal from '@/components/BomCostReportModal.vue'
import DocumentViewer from './DocumentViewer.vue'

const route = useRoute()
const router = useRouter()

const oid = computed(() => route.params.oid)
// 可选迭代 oid：查看历史版本详情时通过路由传入
const iterationOid = computed(() => route.params.iterationOid || null)
const activeTab = ref('bom')

const loading = ref(false)
const part = ref(null)
// 所属产品系列/型号
const productContainer = ref(null)
// 文件夹完整路径（从自身向上拼接，最多 6 层防循环）
const folderPath = ref('')
// Part 所属研发阶段名称（按 part.stageOid 从产品系列阶段清单中解析）
const stageName = ref('')
// 检出/检入
const checkoutModalVisible = ref(false)
const checkoutSaving = ref(false)
const checkoutComment = ref('')
// 当前检出操作的目标 Part oid（针对选中的 BOM 行，根行=当前 Part，子件=该子件）
const checkoutTargetOid = ref(null)
/** 检出目标的展示信息（编码/名称/版本），弹窗中展示，确保与实际检出对象一致 */
const checkoutTarget = ref(null)

// 历史版本
const historyLoading = ref(false)
const historyList = ref([])

// BOM 结构（按最新父迭代）
const bomLoading = ref(false)
const bomList = ref([])
const latestIterationOid = ref('')

// ==================== BOM 版本对比 ====================
const compareModalVisible = ref(false)
/** BOM 成本报告弹窗（口径＝当前迭代，见 onCostReport） */
const costReportOpen = ref(false)
const compareFromOid = ref(null)
const compareToOid = ref(null)
const compareLoading = ref(false)
const compareLoaded = ref(false)
const compareResult = ref(null)
/** 解析后的差异明细 { added: [], removed: [], changed: [] } */
const compareDetail = ref(null)

/**
 * 两个版本之间是否无差异。
 *
 * <p>判据用三个计数：早先模板里调的是 `compareResult.isEmpty?.()` —— 后端返回的是实体
 * （没有 isEmpty 方法），`?.()` 恒为 undefined，于是"无差异"这个分支从来没显示过。
 */
const compareIsEmpty = computed(() => {
  const r = compareResult.value
  return !!r && !(r.addedCount || r.removedCount || r.changedCount)
})

/** 对话框中可选择的迭代列表（展示版本号；最新版附加标记） */
const compareHistoryOptions = computed(() =>
  (historyList.value || []).map(h => {
    let ver = h.displayVersion
    if (!ver && h.revision != null) ver = `${h.revision}.${h.iteration}`
    return {
      label: `${ver || '未知版本'}${h.latest ? '（最新）' : ''}`,
      value: h.oid,
      latest: !!h.latest,
    }
  })
)

/** 打开 BOM 版本对比对话框：默认填入「当前查看版本 → 最新版本」 */
function onCompareBom() {
  compareModalVisible.value = true
  compareLoaded.value = false
  compareResult.value = null
  // 默认：当前查看的迭代 vs 最新迭代
  compareFromOid.value = currentIterationOid.value
  const latest = (historyList.value || []).find(h => h.latest)
    || (historyList.value || [])[0]
  compareToOid.value = latest?.oid || null
}

/** 调用后端 BOM Diff 接口读取预计算的差异数据 */
async function loadBomDiff() {
  if (!compareFromOid.value || !compareToOid.value) {
    message.warning('请选择源版本和目标版本')
    return
  }
  if (compareFromOid.value === compareToOid.value) {
    message.warning('源版本和目标版本不能相同')
    return
  }
  compareLoading.value = true
  try {
    // 实时对比两个版本的顶层 BOM 行（新增/移除/修改），不依赖预计算 diff
    const res = await compareBomVersions(compareFromOid.value, compareToOid.value)
    compareLoaded.value = true
    // 取 data（统一包装）或整体（裸返回）：用 ?? 而不是 ||，否则后端返回 data:null 时
    // 会把整个信封当成结果渲染
    compareResult.value = res?.data ?? res ?? null
    // 解析 diffJson 为结构化明细 { added, removed, changed }
    compareDetail.value = parseCompareDetail(compareResult.value)
    if (!compareResult.value || compareIsEmpty.value) {
      message.info('两版本之间暂无差异')
    }
  } catch (e) {
    compareLoaded.value = true
    compareResult.value = null
    compareDetail.value = null
    message.error('对比失败：' + (e?.response?.data?.message || e.message))
  } finally {
    compareLoading.value = false
  }
}

/** 解析 diffJson：{"added":[...],"removed":[...],"changed":[...]} → 结构化数组 */
function parseCompareDetail(result) {
  if (!result || !result.diffJson) return null
  try {
    const obj = JSON.parse(result.diffJson)
    return { added: obj.added || [], removed: obj.removed || [], changed: obj.changed || [] }
  } catch {
    return null
  }
}

/** 对比结果中某行的展示文本：编码 名称 版本 ×数量 单位 */
function compareRowText(row) {
  if (!row) return ''
  const ver = row.version ? ` ${row.version}` : ''
  const qty = row.quantity != null ? ` ×${row.quantity}${row.unit || ''}` : ''
  return `${row.number || row.childPartOid || '?'} ${row.name || ''}${ver}${qty}`.trim()
}
/**
 * 当前查看的迭代 OID（BOM 读写的口径）。
 *
 * <p>优先级：**检出中的工作副本** → URL 指定的历史版本 → Part 详情返回的当前迭代 → 最新迭代。
 *
 * <p>为什么"检出态"必须排在最前：检出不是给同一个迭代上锁，而是<b>新建一个小版本迭代</b>
 * 并把 BOM 行整份复制过去（新 oid，见 PartCheckoutProvider#checkout）。检出前那个迭代
 * 从此只是只读历史 —— 如果页面（尤其是从历史版本链接进来、URL 里带着迭代 oid 时）
 * 还在读检出前那一套行，用户改的、保存的就是那一套：工作副本没变，
 * 检入后自然"改了却没生效"。BOM 是版本相关的数据，读写口径必须与工作副本一致。
 */
const currentIterationOid = computed(() => {
  const worked = part.value?.iterationOid
  if (part.value?.checkedOut && worked) {
    return worked
  }
  // 注意：取 BOM 时不能一律用 latestIterationOid，否则查看 A.3 这类历史版本时，
  // 加载到的始终是最新版本 A.4 的 BOM（历史版本 BOM 与最新版在 ck_bom_links 中本就不同）。
  return iterationOid.value || worked || latestIterationOid.value
})
// BOM 行选中状态（点击选中，用背景色高亮）
const bomSelectedRow = ref(null)
// BOM 树形展开的节点 key（默认展开根行，保证子件以树形层级显示）
const bomExpandedKeys = ref([])

// 被使用情况（反向 BOM）
const usedByLoading = ref(false)
const usedByList = ref([])

// 双向替代（PartAlternateLink）
const alternateLoading = ref(false)
const alternateList = ref([])      // 双向替代清单（对称关系，不区分角色端）

const historyColumns = [
  { title: '版本', key: 'version', width: 100 },
  { title: '大版本', dataIndex: 'revision', key: 'revision', width: 90 },
  { title: '小版本', dataIndex: 'iteration', key: 'iteration', width: 90 },
  { title: '视图', key: 'view', width: 100 },
  { title: '生命周期状态', key: 'status', width: 140 },
  { title: '检出状态', key: 'checkedOut', width: 150 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 100 },
  { title: '来源', dataIndex: 'source', key: 'source', width: 100 },
  { title: '创建时间', key: 'createdAt', width: 170 },
]

// 左侧 BOM 结构树列：只展示「主题 + 数量」，行号/单位/单位成本等移到右侧子件清单
const bomColumns = [
  { title: '主题', key: 'subject' },
  { title: '数量', key: 'quantity', width: 80, align: 'center' },
  { title: '行号', key: 'lineNumber', width: 70, align: 'center' },
  { title: '单位', key: 'unit', width: 80, align: 'center' },
  // 标明币种：这个字段是金额，光写"单位成本"会被当成与币种无关的比值
  { title: '单位成本(RMB)', key: 'unitCost', width: 132, align: 'right' },
]

// 右侧「子件清单」可编辑表格列
const bomItemsColumns = [
  { title: '编码', dataIndex: 'childNumber', key: 'childNumber', width: 140, ellipsis: true },
  { title: '行号', dataIndex: 'lineNumber', key: 'lineNumber', width: 70, align: 'center' },
  { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 90, align: 'center' },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 80, align: 'center' },
  { title: '单位成本(RMB)', dataIndex: 'unitCost', key: 'unitCost', width: 132, align: 'right' },
]

// 右侧面板当前激活的 tab
const bomRightTab = ref('items')

// ==================== 两栏布局：可拖拽分隔条 ====================

/** 左侧 BOM 树宽度（px）；为 0 时使用默认 55% 比例 */
const bomLeftWidth = ref(0)
/** 两栏容器引用 */
const bomLayoutRef = ref(null)
/** 是否正在拖拽分隔条 */
const isDraggingSplitter = ref(false)

/** 拖拽分隔条：按下开始 */
function startDragSplitter(e) {
  isDraggingSplitter.value = true
  document.addEventListener('mousemove', onDragSplitter)
  document.addEventListener('mouseup', stopDragSplitter)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  e.preventDefault()
}

/** 拖拽分隔条：移动时调整左侧宽度（限制左右最小 240px） */
function onDragSplitter(e) {
  if (!isDraggingSplitter.value || !bomLayoutRef.value) return
  const rect = bomLayoutRef.value.getBoundingClientRect()
  const min = 240
  const max = Math.max(min, rect.width - 240)
  let width = e.clientX - rect.left
  if (width < min) width = min
  if (width > max) width = max
  bomLeftWidth.value = width
}

/** 拖拽分隔条：松开结束 */
function stopDragSplitter() {
  isDraggingSplitter.value = false
  document.removeEventListener('mousemove', onDragSplitter)
  document.removeEventListener('mouseup', stopDragSplitter)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

// 组件卸载时清理拖拽监听
onUnmounted(stopDragSplitter)

/** 可切换显隐的列 */
const toggleableColumns = bomColumns
/** 列显隐状态：{ [key]: boolean }。默认只展示主题/数量，行号/单位/单位成本由用户在「过滤→列显隐」中自由开启 */
const bomColumnVisibility = ref({ lineNumber: false, unit: false, unitCost: false })
/** 根据显隐状态过滤后的列 */
const bomColumnsFiltered = computed(() =>
  bomColumns.filter(c => bomColumnVisibility.value[c.key] !== false)
)

/** 切换某列的显隐 */
function onToggleColumn(key, visible) {
  bomColumnVisibility.value[key] = visible
}

const usedByColumns = [
  { title: '父迭代 OID', dataIndex: 'parentIterationOid', key: 'parentIterationOid', ellipsis: true },
  { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 100 },
  { title: '引用方式', key: 'exactRef', width: 120 },
]

/** 双向替代列表列定义（另一端部件 + 替代属性） */
const alternateColumns = [
  { title: '部件', key: 'part', ellipsis: true },
  { title: '替代类型', key: 'alternateType', width: 110 },
  { title: '数量', key: 'alternateQuantity', width: 80, align: 'center' },
  { title: '单位', key: 'alternateUnit', width: 80, align: 'center' },
  { title: '状态', key: 'enabled', width: 80, align: 'center' },
]

/** 替代类型中文映射 */
const ALTERNATE_TYPE_MAP = {
  EQUIVALENT: { label: '等效替代', color: 'green' },
  COMPLETE: { label: '完全替代', color: 'blue' },
  PARTIAL: { label: '部分替代', color: 'orange' },
  SUBSTITUTE: { label: '临时替代', color: 'red' },
}

const addExistingColumns = [
  { title: '编码', key: 'number', width: 180 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '类型', key: 'type', width: 120 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
]

/** 固定字段（不视为 IBA 属性） */
const FIXED_FIELDS = new Set([
  'oid', 'name', 'number', 'description', 'typeDefinitionCode',
  'containerOid', 'containerType', 'folderOid', 'stageOid', 'clsOid',
  'tenantOid', 'creator', 'createdAt', 'updater', 'updatedAt',
  'revision', 'iteration', 'iterationOid', 'displayVersion',
  'checkedOut', 'checkedOutBy', 'checkedOutComment',
  'unit', 'source', 'view', 'statusCode', 'statusName',
  'clsIba', 'new', 'persisted',
])

const ibaEntries = computed(() => {
  if (!part.value) return []
  return Object.entries(part.value)
    .filter(([k, v]) => !FIXED_FIELDS.has(k) && v !== null && v !== undefined && v !== '')
    .map(([k, v]) => ({ label: k, value: formatIbaValue(v) }))
})

/** 分类 IBA 属性（来自 ck_cls_iba_data，由后端放在 part.clsIba 嵌套字段） */
/** 分类 IBA 定义（code → displayName 映射），用于把属性 code 翻译为显示名称 */
const clsIbaDefs = ref([])

/**
 * 从 IBA 定义列表中查找 code 对应的显示名称。
 * 优先用 ibaDisplayName，其次 displayName、name，最后回退到 code。
 */
function resolveClsIbaLabel(code) {
  if (!code) return code
  const def = clsIbaDefs.value.find(d => (d.ibaCode || d.code) === code)
  if (!def) return code
  return def.ibaDisplayName || def.displayName || def.name || code
}

const clsIbaEntries = computed(() => {
  const clsIba = part.value?.clsIba
  if (!clsIba || typeof clsIba !== 'object') return []
  return Object.entries(clsIba)
    .filter(([k, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => ({ label: resolveClsIbaLabel(k), value: formatIbaValue(v) }))
})

/** 是否为结构件（含 STRUCTURAL / STRUCTURE） */
const isStructural = computed(() => {
  const c = (part.value?.typeDefinitionCode || '').toUpperCase()
  return c.includes('STRUCT')
})

/** 是否为电子元器件 */
const isElectronic = computed(() => (part.value?.typeDefinitionCode || '').toUpperCase() === 'ELECTRONIC')
/** 是否为电气件 */
const isElectrical = computed(() => (part.value?.typeDefinitionCode || '').toUpperCase() === 'ELECTRICAL')
/** 是否为软件 */
const isSoftware = computed(() => (part.value?.typeDefinitionCode || '').toUpperCase() === 'SOFTWARE')

/** CAD 描述 Tab 标题：按对象类型动态化（电子元器件=符号.封装，电气件=电气图，软件=原理图） */
const cadTabLabel = computed(() => {
  const c = (part.value?.typeDefinitionCode || '').toUpperCase()
  if (c === 'ELECTRONIC') return '符号.封装'
  if (c === 'ELECTRICAL') return '电气图'
  if (c === 'SOFTWARE') return '原理图'
  return 'CAD 描述'
})

function formatIbaValue(v) {
  if (v == null) return ''
  if (typeof v === 'string') return v
  try { return JSON.stringify(v) } catch { return String(v) }
}

function statusColor(code) {
  const map = { DRAFT: 'default', INWORK: 'processing', REVIEW: 'warning', APPROVED: 'success', RELEASED: 'blue', OBSOLETE: 'error' }
  return map[code] || 'default'
}

function fmtTime(v) {
  return v ? String(v).substring(0, 19).replace('T', ' ') : '-'
}

/** 格式化单位成本：保留最多 2 位小数，千分位分隔 */
function formatCost(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

function goBack() {
  if (window.history.length > 1) router.back()
  else window.close()
}

function pickList(res) {
  if (Array.isArray(res)) return res
  if (res && Array.isArray(res.data)) return res.data
  return []
}

async function loadDetail() {
  if (!oid.value) return
  loading.value = true
  try {
    // 有迭代 oid 时加载指定历史版本，否则加载最新版本
    const params = iterationOid.value ? { iterationOid: iterationOid.value } : undefined
    const res = await getEntityByCode('PART', oid.value, params)
    if (res.code === 200 && res.data) {
      part.value = res.data
      // 加载所属容器（产品系列/型号）和文件夹路径
      loadContainer()
      loadFolderPath()
      // 加载分类 IBA 定义（用于显示名称映射）
      if (part.value.clsOid) loadClsIbaDefs(part.value.clsOid)
    }
  } catch { part.value = null }
  finally { loading.value = false }
}

/** 加载分类 IBA 定义列表（用于将 code 映射为显示名称） */
async function loadClsIbaDefs(clsOid) {
  try {
    const r = await getClassificationIBAs(clsOid)
    clsIbaDefs.value = r?.data || r || []
  } catch { clsIbaDefs.value = [] }
}

/**
 * 加载 Part 所属的产品系列 / 产品型号。
 * 根据 part.containerType 选择对应接口。
 */
async function loadContainer() {
  const coid = part.value?.containerOid
  const ctype = (part.value?.containerType || '').toUpperCase()
  if (!coid) { productContainer.value = null; return }
  try {
    let r = null
    if (ctype === 'PRODUCT_MODEL') {
      r = await getProductModel(coid)
    } else if (ctype === 'PRODUCT_LINE') {
      r = await getProductLine(coid)
    }
    const data = r?.data || r
    if (r && (r.code === 200 || r.code === undefined) && data) {
      productContainer.value = {
        oid: data.oid,
        name: data.name,
        code: data.code,
        type: ctype || 'PRODUCT_LINE',
      }
      // 顺带加载所属研发阶段名称（注册动态阶段表 → 按 part.stageOid 解析）
      loadStageName(data.oid)
    } else {
      productContainer.value = null
    }
  } catch {
    productContainer.value = null
  }
}

/** 加载 Part 所属研发阶段名称：产品系列的阶段清单 → 注册到全局阶段表 → 按 stageOid 取名 */
async function loadStageName(seriesOid) {
  if (!seriesOid) { stageName.value = ''; return }
  try {
    const res = await getStages(seriesOid)
    const list = res?.code === 200 ? (res.data || []) : []
    if (list.length) {
      // 注册动态阶段（oid → 名称），与产品线仪表盘共享同一映射
      registerDynamicStages(list)
      const stageOid = part.value?.stageOid || ''
      const title = getStageTitle(stageOid)
      // 无法解析（注册表中不存在）时回退为原始 oid 展示无意义，置空隐藏
      stageName.value = (title && title !== stageOid) ? title : ''
    }
  } catch { stageName.value = '' }
}

/**
 * 加载 Part 所属文件夹的完整路径（递归向上拼接父文件夹名）。
 * 限制最大深度 6 层，避免异常循环。
 */
async function loadFolderPath() {
  let currentOid = part.value?.folderOid
  if (!currentOid) { folderPath.value = ''; return }
  const segments = []
  let depth = 0
  while (currentOid && depth < 6) {
    try {
      const r = await getFolderByOid(currentOid)
      const data = r?.data || r
      if (!data || (r && r.code !== 200)) break
      if (data.name) segments.unshift(data.name)
      currentOid = data.parentOid || data.parentFolderOid
    } catch { break }
    depth++
  }
  folderPath.value = segments.join(' / ')
}

async function loadHistory() {
  if (!oid.value) return
  historyLoading.value = true
  try {
    const res = await getPartIterations(oid.value)
    const list = pickList(res)
    historyList.value = list.map(it => ({
      ...it,
      displayVersion: it.displayVersion || (it.revision ? `${it.revision}.${it.iteration}` : '-'),
    }))
    const latest = list.find(it => it.latest) || list[list.length - 1]
    latestIterationOid.value = latest?.oid || ''
  } catch { historyList.value = [] }
  finally { historyLoading.value = false }
}

/** 当前查看的 Part 迭代是否是最新版本（用于"转至最新版本"按钮的显示判断） */
const isLatestVersion = computed(() => {
  if (!historyList.value.length || !part.value) return true
  const latest = historyList.value.find(it => it.latest) || historyList.value[historyList.value.length - 1]
  if (!latest) return true
  return latest.revision === part.value.revision && latest.iteration === part.value.iteration
})

/** 查看历史版本（非最新）时为只读，BOM 修改类操作应被禁用 */
const readonlyTip = '历史版本为只读，请先转至最新版本'
/** 只读提示（用于禁用按钮的 tooltip 动态 title） */
const roTitle = (t) => (!isLatestVersion.value ? readonlyTip : t)

/** BOM 默认展开层级：0=全部展开，1/2/3=展开对应层数 */
const expandLevel = ref(1) // 默认展开 1 层（仅展开根节点，显示第一层子件）
const expandLabel = computed(() => ({ 0: '全部', 1: '1 层', 2: '2 层', 3: '3 层' })[expandLevel.value] || '全部')

/** 展开层级下拉选择：切换后按新层级刷新默认展开状态（无需重新拉取 BOM） */
function onExpandLevelChange({ key }) {
  expandLevel.value = Number(key)
  if (part.value?.oid) {
    bomExpandedKeys.value = computeExpandKeys(`__root__${part.value.oid}`, bomList.value, expandLevel.value)
  }
}

async function loadBom() {
  // 用当前查看的迭代（可能是历史版本 A.3）取 BOM，而不是恒定的最新迭代
  const iterOid = currentIterationOid.value
  if (!iterOid) { bomList.value = []; return }
  bomLoading.value = true
  try {
    // 后端递归返回完整多层 BOM 树（含子件展示信息），无需前端 N+1 查询
    const res = await getBomTree(iterOid)
    const list = pickList(res)
    bomList.value = Array.isArray(list) ? list : []
    if (part.value?.oid) {
      // 按设定的层级展开（根行始终展开；0 表示全部展开）
      bomExpandedKeys.value = computeExpandKeys(`__root__${part.value.oid}`, bomList.value, expandLevel.value)
    }
  } catch { bomList.value = [] }
  finally { bomLoading.value = false }
}

/**
 * 计算默认展开的节点 key 列表。
 * @param rootKey  根行 key
 * @param topNodes BOM 树顶层子件（一级子件视为深度 1）
 * @param level    展开层数；<=0 表示全部展开（depth < level 的节点展开）
 */
function computeExpandKeys(rootKey, topNodes, level) {
  const keys = []
  const unlimited = !level || level <= 0
  if (topNodes && topNodes.length) keys.push(rootKey) // 根有子件即展开
  const walk = (nodes, depth) => {
    for (const n of (nodes || [])) {
      if (n.children && n.children.length && (unlimited || depth < level)) {
        keys.push(n.oid)
        walk(n.children, depth + 1)
      }
    }
  }
  walk(topNodes, 1)
  return keys
}

/** 递归展平 BOM 树节点（用于子件清单/统计） */
function flattenTreeNodes(nodes) {
  const result = []
  for (const n of (nodes || [])) {
    result.push(n)
    if (n.children && n.children.length) {
      result.push(...flattenTreeNodes(n.children))
    }
  }
  return result
}

/** 在（转换后的）树节点中按 oid 递归查找节点 */
function findNodeInTreeData(nodes, oid) {
  for (const n of (nodes || [])) {
    if (n.oid === oid) return n
    if (n.children && n.children.length) {
      const found = findNodeInTreeData(n.children, oid)
      if (found) return found
    }
  }
  return null
}

/** 收集"禁止添加"的 Part oid 集合：选中节点自身 + 所有祖先（防止自引用 / 死循环） */
function collectForbiddenPartOids() {
  const forbidden = new Set()
  // 根节点（当前 Part）始终是祖先，禁止挂到任何子节点下
  if (part.value?.oid) forbidden.add(part.value.oid)

  const selected = bomSelectedRow.value
  if (!selected || selected.isRoot) {
    // 未选中或选中根行：仅禁止当前 Part 自身
    return forbidden
  }

  // 选中子件：从树中找到该节点到根的路径，收集路径上所有节点的 childPartOid
  const targetOid = selected.oid
  const path = []
  function dfs(nodes, trail) {
    for (const n of (nodes || [])) {
      const newTrail = [...trail, n]
      if (n.oid === targetOid) {
        path.push(...newTrail)
        return true
      }
      if (n.children && n.children.length) {
        if (dfs(n.children, newTrail)) return true
      }
    }
    return false
  }
  dfs(bomTreeData.value, [])
  for (const node of path) {
    if (node.childPartOid) forbidden.add(node.childPartOid)
  }
  return forbidden
}

// ==================== 右侧「子件清单」可编辑表格 ====================

/** 当前在编辑的这一层子件（左侧树里的真实节点对象）：选中根行/未选中 → 一级子件；选中子件 → 它的直接下层 */
function bomItemsSourceNodes() {
  const selected = bomSelectedRow.value
  return (selected && !selected.isRoot) ? (selected.children || []) : (bomList.value || [])
}

/** 把树节点摊平成表格行（列名与列定义对应；保留原始字段，保存时回传避免覆盖） */
function toBomItemsRows() {
  return bomItemsSourceNodes().map(b => ({
    oid: b.oid,
    // 左侧树的节点与原始节点字段名不完全一样（前者已带 childName/childNumber），两种都认
    childName: b.childName || b.childPartName || '-',
    childNumber: b.childNumber || b.childPartNumber || '-',
    lineNumber: b.lineNumber,
    quantity: b.quantity,
    unit: b.unit,
    unitCost: b.unitCost,
    // 保留原始字段，保存时回传避免覆盖
    code: b.code,
    name: b.name,
    description: b.description,
    parentIterationOid: b.parentIterationOid,
    childPartOid: b.childPartOid,
    childIterationOid: b.childIterationOid,
    resolvedIterationOid: b.resolvedIterationOid,
  }))
}

/**
 * 「子件清单」数据源：**编辑草稿**，表格直接绑它。
 *
 * <p>为什么必须是 ref 而不是 computed：早先它是 computed 里 `map` 出来的<b>新对象</b>，
 * 输入框把值写进这些临时对象 —— 可它们不是响应式的，父组件不会因为这次修改重渲染，
 * 于是子组件在失焦时按手里那份旧 prop 重新格式化，显示就"弹回原值"；
 * 而保存读的恰恰又是那个已被改过的临时对象，所以"保存后刷新才是新值"。
 * <b>显示与保存读的不是同一份数据</b>，是这两个现象的共同根因。
 *
 * <p>换成 ref 草稿后：输入即改到真状态（响应式、即时生效），保存读的也是同一份，
 * 且"改没改"可以被下面的 `bomItemsDirty` 观察。
 */
const bomItemsList = ref([])

/** 从左侧树重建草稿（只在来源变化时调用，别在每次重渲染时覆盖用户正在编辑的值） */
function syncBomItemsList() {
  bomItemsList.value = toBomItemsRows()
}

// 来源 = 当前选中节点 + 整棵 BOM 树：切换选中、保存后重新拉取、切换版本 都会重建草稿
watch([() => bomSelectedRow.value?.oid, bomList], syncBomItemsList, { immediate: true })

/** 草稿与来源是否已不同（有未保存修改）：让"改动有没有生效"一眼可见 */
const bomItemsDirty = computed(
  () => JSON.stringify(bomItemsList.value) !== JSON.stringify(toBomItemsRows())
)

/** 草稿按 oid 索引：左侧树展示同一行时优先取它，避免"右边改了、左边还是旧值" */
const bomDraftByOid = computed(() => {
  const map = new Map()
  for (const row of bomItemsList.value) map.set(row.oid, row)
  return map
})

/** 左侧树某行要显示的字段值：正在编辑的这一层以草稿为准（与右侧清单保持一致） */
function bomRowValue(record, field) {
  const draft = bomDraftByOid.value.get(record.oid)
  return draft ? draft[field] : record[field]
}

/** 右侧子件清单的父节点名称（选中节点名，未选中/根行时为当前 Part 名） */
/** 右侧子件清单的父节点名称（选中节点名，未选中/根行时为当前 Part 名） */
const bomItemsParentName = computed(() => {
  const selected = bomSelectedRow.value
  if (selected && !selected.isRoot) return selected.childName || selected.childPartName || '-'
  return part.value?.name || '当前 Part'
})

/** 子件清单是否可编辑：父节点必须处于「检出」状态，否则只读 */
const bomItemsEditable = computed(() => {
  const selected = bomSelectedRow.value
  if (selected && !selected.isRoot) {
    return !!selected.childCheckedOut
  }
  return !!part.value?.checkedOut
})

/** 当前选中 BOM 行的目标 Part 主对象 oid（根行=当前 Part；子件行=该子件） */
function resolveBomTargetPartOid() {
  const selected = bomSelectedRow.value
  if (selected && !selected.isRoot) return selected.childPartOid || null
  return part.value?.oid || null
}

/** 当前选中 BOM 行节点是否处于检出状态（用于工具栏检出/检入按钮） */
const bomSelectedCheckedOut = computed(() => {
  const selected = bomSelectedRow.value
  if (selected && !selected.isRoot) return !!selected.childCheckedOut
  return !!part.value?.checkedOut
})

/** BOM 子件总数（递归展平后的所有层级节点数，用于左侧树工具栏统计） */
const bomTotalCount = computed(() => flattenTreeNodes(bomList.value || []).length)

/** 保存子件清单编辑状态 */
const savingBomItems = ref(false)

/** 保存子件清单的编辑（行号/数量/单位/单位成本） */
async function saveBomItems() {
  // 二次校验：父项必须处于检出状态才允许编辑保存
  if (!bomItemsEditable.value) {
    message.warning(`「${bomItemsParentName.value}」未检出，不可编辑子件清单`)
    return
  }
  const list = bomItemsList.value
  if (!list.length) { message.warning('暂无子件可保存'); return }
  // 记录当前选中节点 oid，保存刷新后重新定位，避免选中状态指向旧对象
  const selectedOid = bomSelectedRow.value?.oid || null
  savingBomItems.value = true
  let success = 0
  /** 未生效的行与原因：逐行攒起来一起提示，避免弹一屏 toast */
  const failed = []
  try {
    for (const item of list) {
      try {
        const res = await updateBomLinks(item.oid, {
          code: item.code,
          name: item.name,
          description: item.description,
          parentIterationOid: item.parentIterationOid,
          childPartOid: item.childPartOid,
          childIterationOid: item.childIterationOid,
          resolvedIterationOid: item.resolvedIterationOid,
          quantity: item.quantity,
          unit: item.unit,
          lineNumber: item.lineNumber,
          unitCost: item.unitCost,
        })
        if (res?.code === 200) {
          // 后端回读的行必须与提交的值一致。不一致说明这次保存没落到库里
          // （行已被检出的工作副本取代 / 被删除等）—— 必须报出来，
          // 否则就是"界面说已保存、刷新后又是旧值"（用户实际遇到的现象）。
          const mismatch = bomRowMismatch(res.data, item)
          if (mismatch) {
            failed.push(`「${item.childName || item.childNumber}」${mismatch}`)
          } else {
            success++
          }
        } else if (res) {
          // 后端明确拒绝（拦截器已按 message 提示过一次）：这里补上"是哪一行"，
          // 并让下面的刷新把界面拉回服务端真实值
          failed.push(`「${item.childName || item.childNumber}」${res.message || '保存被拒绝'}`)
        }
      } catch (e) {
        failed.push(`「${item.childName || item.childNumber}」${e?.response?.data?.message || e.message}`)
      }
    }
    if (failed.length) {
      message.warning(
        `有 ${failed.length} 行未生效：${failed.slice(0, 3).join('；')}${failed.length > 3 ? ' …' : ''}`,
      )
    }
    if (success > 0) {
      message.success(`已保存 ${success} 个子件`)
    }
    // 不管成败都刷新：把服务端的真实值摆到界面上，别让"以为存上了"的值留在表格里
    if (success > 0 || failed.length) {
      await loadBom()
      // 重新定位选中节点（刷新后的新树节点）
      if (selectedOid) {
        bomSelectedRow.value = findNodeInTreeData(bomTreeData.value, selectedOid)
      }
    }
  } finally {
    savingBomItems.value = false
  }
}

/**
 * 提交后核对：服务端返回的行与提交值不一致时给出可读说明（一致返回 null）。
 *
 * <p>为什么要在前端再核一遍：UPDATE 只要事务提交了就算"成功"，
 * 而"写的是不是我这一行、值有没有被别的逻辑改回去"只有比对回读结果才知道。
 * 数量/行号/单位成本按数值比、单位按原值比 —— 避免 `'2'` 与 `2` 这种类型差异误报。
 */
function bomRowMismatch(saved, submitted) {
  if (!saved) {
    return '服务端未返回该行'
  }
  const num = (value) => (value === null || value === undefined || value === '' ? null : Number(value))
  const diffs = []
  if (num(saved.quantity) !== num(submitted.quantity)) {
    diffs.push(`数量 ${submitted.quantity ?? '-'} → ${saved.quantity ?? '-'}`)
  }
  if (num(saved.lineNumber) !== num(submitted.lineNumber)) {
    diffs.push(`行号 ${submitted.lineNumber ?? '-'} → ${saved.lineNumber ?? '-'}`)
  }
  if (num(saved.unitCost) !== num(submitted.unitCost)) {
    diffs.push(`单位成本 ${submitted.unitCost ?? '-'} → ${saved.unitCost ?? '-'}`)
  }
  if ((saved.unit ?? null) !== (submitted.unit ?? null)) {
    diffs.push(`单位 ${submitted.unit || '-'} → ${saved.unit || '-'}`)
  }
  return diffs.length ? `未生效（服务端仍为 ${diffs.join('、')}）` : null
}

/** BOM 行上移/下移进行中（按钮 loading） */
const movingBomRow = ref(false)

/** 在树中找到包含指定 oid 的兄弟数组（即其父节点的 children，根行的 children 即一级 BOM 行） */
function findSiblingsInTree(nodes, oid) {
  for (const n of (nodes || [])) {
    if (n.children && n.children.length) {
      if (n.children.some(c => c.oid === oid)) return n.children
      const found = findSiblingsInTree(n.children, oid)
      if (found) return found
    }
  }
  return null
}

/**
 * BOM 行上移/下移：把选中行在其兄弟列表中移动一位，
 * 然后按新顺序以 10/20/30… 步长整体重编行号（跳过行号未变化的行），保证行号唯一且与显示顺序一致。
 */
async function onMoveBomRow(direction) {
  const row = bomSelectedRow.value
  if (!row || row.isRoot) { message.warning('请先选中要移动的 BOM 行'); return }
  if (!isLatestVersion.value) { message.warning('非最新版本，不可调整行序'); return }
  const siblings = findSiblingsInTree(bomTreeData.value, row.oid)
  if (!siblings || siblings.length < 2) { message.info('该层级只有一个子件，无需移动'); return }
  const idx = siblings.findIndex(n => n.oid === row.oid)
  if (idx < 0) { message.error('未在树中定位到选中行'); return }
  const targetIdx = direction === 'up' ? idx - 1 : idx + 1
  if (targetIdx < 0) { message.info('已是最顶部，无法上移'); return }
  if (targetIdx >= siblings.length) { message.info('已是最底部，无法下移'); return }

  // 计算移动后的新顺序
  const moved = [...siblings]
  const [item] = moved.splice(idx, 1)
  moved.splice(targetIdx, 0, item)

  const selectedOid = row.oid
  movingBomRow.value = true
  let success = 0
  try {
    for (let i = 0; i < moved.length; i++) {
      const n = moved[i]
      const newLineNumber = (i + 1) * 10
      if (n.lineNumber === newLineNumber) continue // 行号未变化，跳过
      try {
        const res = await updateBomLinks(n.oid, {
          code: n.code,
          name: n.name,
          description: n.description,
          parentIterationOid: n.parentIterationOid,
          childPartOid: n.childPartOid,
          childIterationOid: n.childIterationOid,
          resolvedIterationOid: n.resolvedIterationOid,
          quantity: n.quantity,
          unit: n.unit,
          lineNumber: newLineNumber,
          unitCost: n.unitCost,
        })
        if (res?.code === 200) success++
      } catch (e) {
        message.error(`更新行号失败（${n.childName || n.childNumber || n.oid}）：${e?.response?.data?.message || e.message}`)
      }
    }
    if (success > 0) {
      message.success(`已${direction === 'up' ? '上移' : '下移'}「${row.childName || row.childNumber}」并更新行号`)
      await loadBom()
      // 刷新后重新定位选中节点，保持选中状态
      bomSelectedRow.value = findNodeInTreeData(bomTreeData.value, selectedOid)
    } else {
      message.info('行号未发生变化')
    }
  } finally {
    movingBomRow.value = false
  }
}

/**
 * 树形 BOM 数据：根行 = 当前 Part；子行 = 一级 BomLinks。
 */
const bomTreeData = computed(() => {
  const p = part.value || {}
  const root = {
    oid: `__root__${p.oid || ''}`,
    isRoot: true,
    _depth: 0,
    lineNumber: null,
    childPartOid: p.oid,
    childName: p.name || '-',
    childNumber: p.number || '-',
    childVersion: p.displayVersion || (p.revision != null ? `${p.revision}.${p.iteration ?? ''}`.replace(/\.$/, '') : '-'),
    childView: p.view || '-',
    childStatus: p.statusCode || '-',
    childCheckedOut: !!p.checkedOut,
    childCheckedOutBy: p.checkedOutBy || '',
    quantity: null,
    unit: null,
    unitCost: null,
    childIterationOid: null,
  }
  const children = (bomList.value || []).map(b => convertTreeNode(b, 1))
  // 只有存在子节点时才挂 children；空数组会让 a-table 渲染出无意义的 +/- 图标
  const rootNode = { ...root }
  if (children.length) rootNode.children = children
  return [rootNode]
})

/** 递归转换后端树节点为前端行数据（补齐展示字段 + 层级深度 + 子节点） */
function convertTreeNode(node, depth) {
  // 后端 getBomTree 对叶子节点也返回 children:[]，先用解构把它排除，
  // 否则 a-table 会为没有下挂节点的行渲染出无意义的 +/- 展开图标
  const { children, ...rest } = node
  const row = {
    ...rest,
    isRoot: false,
    _depth: depth,
    childName: node.childPartName || '-',
    childNumber: node.childPartNumber || '-',
    childVersion: node.childVersion || '-',
    childView: node.childView || '-',
    childStatus: node.childStatus || '-',
    childCheckedOut: !!node.childCheckedOut,
    childCheckedOutBy: node.childCheckedOutBy || '',
    childLatestIterationOid: node.childLatestIterationOid || '',
  }
  // 只有真正有下挂节点时才挂 children
  if (children && children.length) {
    row.children = children.map(c => convertTreeNode(c, depth + 1))
  }
  return row
}

/** BOM 行高亮 class：选中行加背景色 */
function bomRowClassName(record) {
  return bomSelectedRow.value && record.oid === bomSelectedRow.value.oid
    ? 'bom-row-selected'
    : ''
}

/** BOM 行点击：选中当前行并高亮 */
function bomCustomRow(record) {
  return {
    onClick: () => {
      bomSelectedRow.value = record
    },
    style: { cursor: 'pointer' },
  }
}

/** BOM 操作：添加已有子件（占位，后续接入已有部件选择器） */
// ==================== 添加已有子件 ====================

const addExistingModalVisible = ref(false)
const addExistingLoading = ref(false)
const addExistingList = ref([])
const addExistingKeyword = ref('')
const addExistingSelectedRowKeys = ref([])
// 弹窗选中的产品系列/型号 oid（默认 = 当前 Part 的 containerOid）
const addExistingContainerOid = ref(null)
// 产品系列/型号下拉选项
const productLinesForSelect = ref([])
const productModelsForSelect = ref([])
/** 产品系列/型号下拉选项（合并） */
const containerOptions = computed(() => {
  const opts = []
  productLinesForSelect.value.forEach(l => opts.push({
    value: l.oid,
    label: `${l.name || l.code}（系列）`,
    type: 'series',
  }))
  productModelsForSelect.value.forEach(m => opts.push({
    value: m.oid,
    label: `${m.name || m.code}（型号）`,
    type: 'model',
  }))
  return opts
})

/** 加载产品系列 + 型号（用于下拉选项） */
async function loadContainerOptions() {
  try {
    const [lineRes, modelRes] = await Promise.all([
      getProductLines().catch(() => ({ data: [] })),
      getProductModels().catch(() => ({ data: [] })),
    ])
    productLinesForSelect.value = lineRes?.data || lineRes || []
    productModelsForSelect.value = modelRes?.data || modelRes || []
  } catch { productLinesForSelect.value = []; productModelsForSelect.value = [] }
}

/** 打开"添加已有子件"弹窗：默认容器 = 当前 Part 的 containerOid */
/** 添加子件前确保选中行（目标父件）已检出：未检出则静默自动检出，失败返回 false */
async function ensureTargetCheckedOut() {
  const targetOid = resolveBomTargetPartOid()
  if (!targetOid) {
    message.warning('请先选择左侧 BOM 节点')
    return false
  }
  if (bomSelectedCheckedOut.value) return true // 已检出，直接放行
  try {
    const res = await checkoutPart(targetOid, '添加子件前自动检出')
    if (res.code === 200) {
      message.success('已自动检出选中行')
      // 刷新详情与 BOM 树，并重新定位选中行（同步检出状态到子件清单）
      await loadDetail()
      await loadHistory()
      await loadBom()
      reselectBomRow()
      return true
    }
    message.error(res.message || '自动检出失败')
    return false
  } catch (e) {
    message.error(e?.response?.data?.message || '自动检出失败')
    return false
  }
}

function onAddExistingChild() {
  addExistingModalVisible.value = true
  addExistingKeyword.value = ''
  addExistingSelectedRowKeys.value = []
  addExistingContainerOid.value = part.value?.containerOid || null
  loadContainerOptions()
  loadAddExistingParts()
  // 默认将选中的行检出（异步执行，不阻塞弹窗打开）
  ensureTargetCheckedOut()
}

/** 容器（产品系列/型号）切换 */
function onAddExistingContainerChange() {
  addExistingSelectedRowKeys.value = []
  loadAddExistingParts()
}

/** 加载可选 Part 清单（按当前选中的容器 + 关键字过滤） */
async function loadAddExistingParts() {
  const containerOid = addExistingContainerOid.value
  if (!containerOid) { addExistingList.value = []; return }
  addExistingLoading.value = true
  try {
    const res = await getPartsByContainer(containerOid, addExistingKeyword.value.trim() || undefined)
    const list = res?.data || res || []
    const arr = Array.isArray(list) ? list : []
    // 过滤掉选中节点自身及其所有祖先（防止自引用 / 死循环）
    const forbidden = collectForbiddenPartOids()
    addExistingList.value = arr.filter(p => p && p.oid && !forbidden.has(p.oid))
  } catch { addExistingList.value = [] }
  finally { addExistingLoading.value = false }
}

/** 弹窗搜索 */
function onAddExistingSearch() {
  loadAddExistingParts()
}

/** 复选框选中变化（多选） */
function onAddExistingSelectChange(keys, rows) {
  addExistingSelectedRowKeys.value = keys
}

/** 解析添加/新建操作的目标父件（基于左侧 BOM 树当前选中节点）：
 *  - 未选中或选中根行 → 当前 Part
 *  - 选中子件行 → 该子件的最新迭代（用于在其下添加孙件）
 * 返回 { iterationOid, name } 或 null。
 */
function resolveBomTargetParent() {
  const row = bomSelectedRow.value
  if (!row || row.isRoot) {
    if (part.value?.iterationOid) {
      return {
        iterationOid: part.value.iterationOid,
        name: part.value.name || part.value.number || '当前 Part',
      }
    }
    return null
  }
  // 子件行：使用该子件自身的最新迭代 oid
  const iterOid = row.childLatestIterationOid || row.childIterationOid || row.resolvedIterationOid
  if (!iterOid) return null
  return {
    iterationOid: iterOid,
    name: row.childName || row.childNumber || '选中子件',
  }
}

async function confirmAddExisting() {
  const keys = addExistingSelectedRowKeys.value
  if (!keys.length) { message.warning('请先勾选至少一个零组件'); return }
  // 基于左侧选中的 BOM 节点决定父件（支持多层 BOM：在子件下添加孙件）
  const target = resolveBomTargetParent()
  if (!target) { message.error('目标父件没有可用迭代，无法添加子件。请先选中左侧 BOM 节点。'); return }
  // 二次防护：过滤掉选中节点自身及其祖先（防止自引用 / 死循环）
  const forbidden = collectForbiddenPartOids()
  const selectedParts = addExistingList.value.filter(p => keys.includes(p.oid) && !forbidden.has(p.oid))
  if (!selectedParts.length) {
    message.warning('不能将自己或上级物料挂到选中项下面，以免形成循环引用')
    return
  }

  addExistingLoading.value = true
  let success = 0
  try {
    for (const p of selectedParts) {
      try {
        // 行号由后端按「10/20/30 步长编号」自动分配（父迭代内唯一），无需前端传 lineNumber
        const res = await createBomLinks({
          parentIterationOid: target.iterationOid,
          childPartOid: p.oid,
          quantity: 1,
          unit: 'ea', // 默认单位：ea（个/件）
        })
        if (res?.code === 200) {
          success++
        } else {
          message.error(`添加 ${p.name || p.number} 失败：${res?.message || '未知错误'}`)
        }
      } catch (e) {
        message.error(`添加 ${p.name || p.number} 失败：${e?.response?.data?.message || e.message}`)
      }
    }
    if (success > 0) {
      message.success(`已成功添加 ${success} 个子件到「${target.name}」`)
      addExistingModalVisible.value = false
      // 刷新 BOM 列表
      await loadBom()
    }
  } finally {
    addExistingLoading.value = false
  }
}

/** BOM 操作：新建子件（占位，后续接入新建部件流程） */
async function onNewChild() {
  const target = resolveBomTargetParent()
  if (!target) {
    message.warning('请先选择左侧 BOM 节点作为新建子件的父件')
    return
  }
  // 默认将选中的行检出，检出失败则中止
  const ok = await ensureTargetCheckedOut()
  if (!ok) return
  message.info(`新建子件功能开发中（目标父件：${target.name}）`)
}

/** BOM 操作：移除选中的 BOM 行（删除 BomLinks），删除前二次确认 */
function onRemoveChild() {
  const row = bomSelectedRow.value
  if (!row || row.isRoot) return
  if (!row.oid) { message.error('缺少 BOM 行标识，无法移除'); return }
  const childLabel = row.childName || row.childNumber || row.childPartOid || ''
  Modal.confirm({
    title: '确认移除子件',
    content: `确定要从 BOM 中移除子件「${childLabel}」吗？移除后该引用关系将被删除。`,
    okText: '移除',
    okType: 'danger',
    cancelText: '取消',
    centered: true,
    onOk: async () => {
      try {
        const res = await deleteBomLinks(row.oid)
        if (res?.code === 200) {
          message.success(`已移除子件：${childLabel}`)
          bomSelectedRow.value = null
          await loadBom()
        } else {
          message.error(res?.message || '移除失败')
          return Promise.reject(res?.message || '移除失败')
        }
      } catch (e) {
        message.error('移除子件失败：' + (e?.response?.data?.message || e.message))
        return Promise.reject(e)
      }
    },
  })
}

// ==================== 过滤操作栏 ====================

/** 当前 BOM 视图 */
const bomView = ref('design')

/** 视图切换（占位，后续接入按视图过滤 BOM 行） */
function onViewChange({ key }) {
  bomView.value = key
  const labelMap = { design: '设计视图', manufacturing: '制造视图' }
  message.info(`已切换到${labelMap[key] || key}`)
}

/** 过滤器操作（占位，后续接入完整过滤逻辑） */
function onFilterAction({ key }) {
  if (key === 'clear') {
    message.info('已清除过滤器')
  } else if (key === 'edit') {
    message.info('编辑过滤器功能开发中')
  } else if (key === 'current') {
    message.info('当前过滤器功能开发中')
  }
}

/** BOM 操作：导出（占位，后续接入 BOM 导出接口） */
function onExportBom() {
  message.info('BOM 导出功能开发中')
}

/** BOM 报告：导入（占位，后续接入 BOM 导入接口） */
function onImportBom() {
  message.info('BOM 导入功能开发中')
}

/**
 * BOM 报告：成本报告（卷积）。
 *
 * <p>口径是<b>当前迭代</b>（工具栏上的版本选择器决定），与左侧 BOM 结构看的是同一版 ——
 * 成本报告如果不跟版本走，就会出现"结构看着是 A.4、成本算的是 B.1"的错配。
 */
function onCostReport() {
  if (!currentIterationOid.value) {
    message.warning('尚未确定当前版本，无法计算成本')
    return
  }
  costReportOpen.value = true
}

// ==================== 替代操作栏 ====================

/** 替代：为选中的 BOM 行指定单个替代件（占位，后续接入替代件选择器） */
function onSubstitute() {
  const row = bomSelectedRow.value
  if (!row || row.isRoot) return
  message.info(`替代功能开发中：${row.childName || row.childNumber}`)
}

/** 成组替代：为多个 BOM 行指定成组替代（占位） */
function onGroupSubstitute() {
  const row = bomSelectedRow.value
  if (!row || row.isRoot) return
  message.info(`成组替代功能开发中：${row.childName || row.childNumber}`)
}

/** 取消替代：移除选中 BOM 行的替代关系（占位） */
function onCancelSubstitute() {
  const row = bomSelectedRow.value
  if (!row || row.isRoot) return
  message.info(`取消替代功能开发中：${row.childName || row.childNumber}`)
}

/** 检出：打开检出注释弹窗 */
function onCheckoutPart() {
  const targetOid = resolveBomTargetPartOid()
  if (!targetOid) return
  const selected = bomSelectedRow.value
  // 弹窗展示实际检出目标的信息：选中子件行=该子件，否则=当前 Part（根件）
  if (selected && !selected.isRoot) {
    checkoutTarget.value = {
      number: selected.childNumber || '-',
      name: selected.childName || '-',
      version: selected.childVersion || '-',
    }
  } else {
    checkoutTarget.value = {
      number: part.value?.number || '-',
      name: part.value?.name || '-',
      version: part.value?.displayVersion || '-',
    }
  }
  checkoutTargetOid.value = targetOid
  checkoutComment.value = ''
  checkoutModalVisible.value = true
}

/** 确认检出（针对选中的 BOM 行目标 Part） */
/** 刷新 BOM 后重新定位选中行：旧对象上的检出状态等字段已过期，必须换成新树中的节点引用 */
function reselectBomRow() {
  const selectedOid = bomSelectedRow.value?.oid || null
  if (selectedOid) {
    bomSelectedRow.value = findNodeInTreeData(bomTreeData.value, selectedOid) || null
  }
}

async function confirmCheckout() {
  const targetOid = checkoutTargetOid.value
  if (!targetOid) return
  checkoutSaving.value = true
  try {
    const res = await checkoutPart(targetOid, checkoutComment.value.trim())
    if (res.code === 200) {
      message.success('检出成功')
      checkoutModalVisible.value = false
      checkoutComment.value = ''
      checkoutTargetOid.value = null
      checkoutTarget.value = null
      // 刷新详情与 BOM 树，更新检出状态，并重新定位选中行（刷新子件清单可编辑状态）
      await loadDetail()
      await loadHistory()
      await loadBom()
      reselectBomRow()
    } else {
      message.error(res.message || '检出失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '检出失败')
  } finally {
    checkoutSaving.value = false
  }
}

/** 检入（针对选中的 BOM 行目标 Part） */
async function onCheckinPart() {
  const targetOid = resolveBomTargetPartOid()
  if (!targetOid) return
  checkoutSaving.value = true
  try {
    const res = await checkinPart(targetOid)
    if (res.code === 200) {
      message.success('检入成功')
      await loadDetail()
      await loadHistory()
      await loadBom()
      reselectBomRow()
    } else {
      message.error(res.message || '检入失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '检入失败')
  } finally {
    checkoutSaving.value = false
  }
}

/** 取消检出：解除检出，不保留副本（与检入不同） */
async function onUndoCheckoutPart() {
  const targetOid = resolveBomTargetPartOid()
  if (!targetOid) return
  checkoutSaving.value = true
  try {
    const res = await undoCheckoutPart(targetOid)
    if (res.code === 200) {
      message.success('取消检出成功')
      await loadDetail()
      await loadHistory()
      await loadBom()
      reselectBomRow()
    } else {
      message.error(res.message || '取消检出失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '取消检出失败')
  } finally {
    checkoutSaving.value = false
  }
}

/** 转至最新版本：当前查看的是历史版本时，先跳转到无 iterationOid 的 URL（触发 watch 重新加载最新版本） */
async function onGoToLatestVersion() {
  if (!oid.value) return
  // 当前在历史版本 URL 上：跳转到主对象 URL，由 watch 触发 loadDetail（不带 iterationOid 参数 → 加载最新版本）
  if (iterationOid.value) {
    router.replace({ name: 'PartDetail', params: { oid: oid.value } })
    message.success('已切换到最新版本')
    return
  }
  // 当前已在最新版本 URL 上：手动刷新
  loading.value = true
  try {
    await loadDetail()
    message.success('已切换到最新版本')
  } catch (e) {
    message.error('切换到最新版本失败')
  } finally {
    loading.value = false
  }
}

/** 查看历史版本详情：跳转到带 iterationOid 的 PartDetail 页面（复用当前页面） */
function onViewHistoryVersion(record) {
  if (!record?.oid || !oid.value) return
  router.push({ name: 'PartDetail', params: { oid: oid.value, iterationOid: record.oid } })
}

async function loadUsedBy() {
  if (!oid.value) return
  usedByLoading.value = true
  try {
    const res = await getBomLinksByChildPart(oid.value)
    usedByList.value = pickList(res)
  } catch { usedByList.value = [] }
  finally { usedByLoading.value = false }
}

/** 加载双向替代清单：双向对称（不区分角色端），附加另一端部件名称/编码 */
async function loadAlternates() {
  if (!oid.value) return
  alternateLoading.value = true
  try {
    const res = await getPartAlternateLinksByPart(oid.value)
    const list = pickList(res)
    // 双向替代是对称关系：另一端 = roleA/roleB 中不是当前 Part 的那一端
    alternateList.value = await Promise.all(list.map(async (link) => {
      const otherOid = link.roleAPartOid === oid.value ? link.roleBPartOid : link.roleAPartOid
      let otherName = '-'
      let otherNumber = '-'
      if (otherOid) {
        try {
          const r = await getEntityByCode('PART', otherOid)
          const p = r?.code === 200 ? (r.data || {}) : {}
          otherName = p.name || '-'
          otherNumber = p.number || '-'
        } catch { /* 忽略单个部件查询失败 */ }
      }
      return { ...link, _otherOid: otherOid || '', _otherName: otherName, _otherNumber: otherNumber }
    }))
  } catch {
    alternateList.value = []
  } finally {
    alternateLoading.value = false
  }
}

// ==================== 双向替代：添加替代件 / 删除替代 ====================

/** 替代清单选中行（支持多选） */
const alternateSelectedRowKeys = ref([])

/** 添加替代件弹窗状态（复用「添加已有子件」的容器选项与列定义） */
const addAlternateModalVisible = ref(false)
const addAlternateLoading = ref(false)
const addAlternateList = ref([])
const addAlternateKeyword = ref('')
const addAlternateSelectedRowKeys = ref([])
const addAlternateContainerOid = ref(null)

/** 打开添加替代件弹窗 */
function onAddAlternate() {
  if (!oid.value) { message.warning('缺少当前部件信息'); return }
  addAlternateModalVisible.value = true
  addAlternateKeyword.value = ''
  addAlternateSelectedRowKeys.value = []
  addAlternateContainerOid.value = part.value?.containerOid || null
  loadContainerOptions()
  loadAddAlternateParts()
}

/** 容器切换：清空选择并重新加载可选 Part */
function onAddAlternateContainerChange() {
  addAlternateSelectedRowKeys.value = []
  loadAddAlternateParts()
}

/** 搜索触发 */
function onAddAlternateSearch() {
  loadAddAlternateParts()
}

/** 加载可选 Part 清单：排除当前 Part 自身与已建立替代关系的部件 */
async function loadAddAlternateParts() {
  const containerOid = addAlternateContainerOid.value
  if (!containerOid) { addAlternateList.value = []; return }
  addAlternateLoading.value = true
  try {
    const res = await getPartsByContainer(containerOid, addAlternateKeyword.value.trim() || undefined)
    const list = res?.data || res || []
    const arr = Array.isArray(list) ? list : []
    // 排除自身 + 已在替代清单中的部件（双向替代不允许重复建立）
    const existingOids = new Set(alternateList.value.map(l => (l.roleAPartOid === oid.value ? l.roleBPartOid : l.roleAPartOid)))
    addAlternateList.value = arr.filter(p => p && p.oid && p.oid !== oid.value && !existingOids.has(p.oid))
  } catch { addAlternateList.value = [] }
  finally { addAlternateLoading.value = false }
}

/** 确认添加：为每个选中 Part 建立双向替代关系（EQUIVALENT，后端自动规范化 roleA/roleB 有序对） */
async function confirmAddAlternate() {
  if (!addAlternateSelectedRowKeys.value.length) return
  const targets = addAlternateList.value.filter(p => addAlternateSelectedRowKeys.value.includes(p.oid))
  if (!targets.length) return
  let success = 0
  for (const p of targets) {
    try {
      const res = await createPartAlternateLink({
        roleAPartOid: oid.value,
        roleBPartOid: p.oid,
        alternateType: 'EQUIVALENT',
        alternateQuantity: 1,
        enabled: true,
      })
      if (res?.code === 200) success++
      else message.error(`添加「${p.name || p.number}」失败：${res?.message || '未知错误'}`)
    } catch (e) {
      message.error(`添加「${p.name || p.number}」失败：${e?.response?.data?.message || e.message}`)
    }
  }
  if (success > 0) {
    message.success(`已添加 ${success} 个替代件`)
    addAlternateModalVisible.value = false
    await loadAlternates()
  }
}

/** 删除替代：支持多选，删除前提醒确认 */
function onRemoveAlternates() {
  if (!alternateSelectedRowKeys.value.length) return
  const rows = alternateList.value.filter(l => alternateSelectedRowKeys.value.includes(l.oid))
  const labels = rows.map(r => `${r._otherName || '-'}(${r._otherNumber || '-'})`).join('、')
  Modal.confirm({
    title: '确认删除替代关系',
    content: `确定要删除与「${labels}」的双向替代关系吗？删除后双方将不再互为替代件。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    centered: true,
    onOk: async () => {
      let success = 0
      for (const row of rows) {
        try {
          const res = await deletePartAlternateLink(row.oid)
          if (res?.code === 200) success++
        } catch (e) {
          message.error(`删除「${row._otherName || row.oid}」失败：${e?.response?.data?.message || e.message}`)
        }
      }
      if (success > 0) {
        message.success(`已删除 ${success} 条替代关系`)
        alternateSelectedRowKeys.value = []
        await loadAlternates()
      }
    },
  })
}

// ==================== 关联文档（参考 REFERENCE / 说明 DESCRIPTION） ====================

/** 关联文档清单列定义 */
const docLinkColumns = [
  { title: '文档', key: 'doc', ellipsis: true },
  { title: '文档类型', key: 'docType', width: 140 },
  { title: '关联时间', key: 'createdAt', width: 160 },
]

const docLinksLoading = ref(false)
const docLinksList = ref([])       // 全部关联（两类一次拉取，前端按类型过滤）
const docTab = ref('desc')         // 当前子 tab：desc（说明，默认）/ ref（参考）
const refSelectedKeys = ref([])    // 参考文档选中行
const descSelectedKeys = ref([])   // 说明文档选中行

const refDocList = computed(() => docLinksList.value.filter(l => l.linkType === 'REFERENCE'))
const descDocList = computed(() => docLinksList.value.filter(l => l.linkType === 'DESCRIBES'))

/** 加载关联文档清单：附加文档名称/编码/类型 */
async function loadDocLinks() {
  if (!oid.value) return
  docLinksLoading.value = true
  try {
    // 迭代级关联：传 partOid，由后端解析到最新迭代
    const res = await getPartDocLinks({ partOid: oid.value })
    const list = pickList(res)
    docLinksList.value = await Promise.all(list.map(async (link) => {
      let docName = '-'
      let docNumber = '-'
      let docType = '-'
      if (link.docMasterOid) {
        try {
          const r = await getEntityByCode('DOCUMENT', link.docMasterOid)
          const d = r?.code === 200 ? (r.data || {}) : {}
          docName = d.name || '-'
          docNumber = d.number || '-'
          docType = d.typeDefinitionCode || '-'
        } catch { /* 忽略单个文档查询失败 */ }
      }
      return { ...link, _docName: docName, _docNumber: docNumber, _docType: docType }
    }))
  } catch {
    docLinksList.value = []
  } finally {
    docLinksLoading.value = false
  }
}

/** 添加文档弹窗状态（复用「添加替代件」的容器选项与列定义，关键词本地过滤） */
const addDocModalVisible = ref(false)
const addDocLoading = ref(false)
const addDocList = ref([])               // 当前容器下全量文档
const addDocKeyword = ref('')
const addDocSelectedRowKeys = ref([])
const addDocContainerOid = ref(null)
const addDocLinkType = ref('REFERENCE')  // 本次添加的关联类型

/** 按关键词本地过滤（名称/编码），避免后端不支持关键字参数 */
const filteredAddDocList = computed(() => {
  const kw = addDocKeyword.value.trim().toLowerCase()
  if (!kw) return addDocList.value
  return addDocList.value.filter(d =>
    (d.name || '').toLowerCase().includes(kw) || (d.number || '').toLowerCase().includes(kw)
  )
})

/** 打开添加文档弹窗（linkType：REFERENCE / DESCRIPTION） */
function onAddDocLink(linkType) {
  if (!oid.value) { message.warning('缺少当前部件信息'); return }
  addDocLinkType.value = linkType
  addDocModalVisible.value = true
  addDocKeyword.value = ''
  addDocSelectedRowKeys.value = []
  addDocContainerOid.value = part.value?.containerOid || null
  loadContainerOptions()
  loadAddDocs()
}

/** 容器切换：清空选择并重新加载可选文档 */
function onAddDocContainerChange() {
  addDocSelectedRowKeys.value = []
  loadAddDocs()
}

/** 加载当前容器下的文档清单，排除已建立同类型关联的文档 */
async function loadAddDocs() {
  const containerOid = addDocContainerOid.value
  if (!containerOid) { addDocList.value = []; return }
  addDocLoading.value = true
  try {
    const res = await getDocuments({ containerOid })
    const arr = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
    // 排除当前类型下已关联的文档（后端唯一约束兜底）
    const linked = new Set(
      docLinksList.value.filter(l => l.linkType === addDocLinkType.value).map(l => l.docMasterOid)
    )
    addDocList.value = arr.filter(d => d && d.oid && !linked.has(d.oid))
  } catch { addDocList.value = [] }
  finally { addDocLoading.value = false }
}

/** 确认添加：为每个选中文档建立部件-文档关联 */
async function confirmAddDocLink() {
  if (!addDocSelectedRowKeys.value.length) return
  const targets = addDocList.value.filter(d => addDocSelectedRowKeys.value.includes(d.oid))
  if (!targets.length) return
  const typeLabel = addDocLinkType.value === 'REFERENCE' ? '参考' : '说明'
  let success = 0
  for (const d of targets) {
    try {
      const res = await createPartDocLink({
        partOid: oid.value,
        docMasterOid: d.oid,
        linkType: addDocLinkType.value,
      })
      if (res?.code === 200) success++
      else message.error(`添加「${d.name || d.number}」失败：${res?.message || '未知错误'}`)
    } catch (e) {
      message.error(`添加「${d.name || d.number}」失败：${e?.response?.data?.message || e.message}`)
    }
  }
  if (success > 0) {
    message.success(`已添加 ${success} 个${typeLabel}文档`)
    addDocModalVisible.value = false
    await loadDocLinks()
  }
}

/** 删除关联（参考/说明各自入口，linkType 用于定位选中集合并过滤）：删除前提醒确认 */function onRemoveDocLinks(linkType) {
  const keys = linkType === 'REFERENCE' ? refSelectedKeys.value : descSelectedKeys.value
  if (!keys.length) return
  const rows = docLinksList.value.filter(l => keys.includes(l.oid))
  const labels = rows.map(r => `${r._docName || '-'}(${r._docNumber || '-'})`).join('、')
  const typeLabel = linkType === 'REFERENCE' ? '参考' : '说明'
  Modal.confirm({
    title: `确认删除${typeLabel}文档关联`,
    content: `确定要删除与「${labels}」的${typeLabel}文档关联吗？删除后可重新添加。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    centered: true,
    onOk: async () => {
      let success = 0
      for (const row of rows) {
        try {
          const res = await deletePartDocLink(row.oid, linkType)
          if (res?.code === 200) success++
        } catch (e) {
          message.error(`删除「${row._docName || row.oid}」失败：${e?.response?.data?.message || e.message}`)
        }
      }
      if (success > 0) {
        message.success(`已删除 ${success} 条${typeLabel}文档关联`)
        if (linkType === 'REFERENCE') refSelectedKeys.value = []
        else descSelectedKeys.value = []
        await loadDocLinks()
      }
    },
  })
}

// ==================== 文档详情查看（复用产品线的 DocumentViewer 抽屉） ====================

const docViewerVisible = ref(false)
const docViewerDoc = ref(null)

/** 查看关联文档详情：DocumentViewer 需要迭代级字段（编码/版本/生命周期/主文件），
 *  与产品线列表同源：先取 master 拿 folderOid，再从 folder-details VO 中定位该文档 */
async function viewDocDetail(record) {
  if (!record.documentOid) return
  try {
    // 1. 取 master 数据（获取 folderOid）
    const masterRes = await getDocument(record.documentOid)
    const master = masterRes?.data || masterRes
    if (!master) { message.error('未找到文档数据'); return }
    // 2. 从文件夹详情 VO 列表中定位该文档（含 displayVersion/statusName/ckfileOid 等迭代字段）
    let vo = null
    if (master.folderOid) {
      try {
        const detailsRes = await getFolderDocumentDetails(master.folderOid)
        const list = pickList(detailsRes)
        vo = list.find(d => d.oid === record.documentOid) || null
      } catch { /* 忽略，降级用 master */ }
    }
    docViewerDoc.value = vo
      ? { ...vo, entityType: 'DOC' }
      : { ...master, code: master.code || master.number, entityType: 'DOC' }
    docViewerVisible.value = true
  } catch (e) {
    message.error(e?.response?.data?.message || '加载文档详情失败')
  }
}

// 切换 tab 时按需懒加载
watch(activeTab, (key) => {
  if (key === 'bom' && bomList.value.length === 0 && !bomLoading.value) loadBom()
  if (key === 'usedBy' && usedByList.value.length === 0 && !usedByLoading.value) loadUsedBy()
  if (key === 'twoWaySubstitute' && alternateList.value.length === 0 && !alternateLoading.value) loadAlternates()
  if (key === 'relatedDocs' && docLinksList.value.length === 0 && !docLinksLoading.value) loadDocLinks()
})

// 监听迭代 oid 变化：切换到不同历史版本时重新加载详情 + 按新迭代重新加载 BOM
watch(() => route.params.iterationOid, async () => {
  if (!oid.value) return
  await loadDetail()
  // 历史版本的 BOM 与最新版本在 ck_bom_links 中是不同记录，必须重新拉取
  if (activeTab.value === 'bom') loadBom()
})

// 路由参数 oid 变化（如从双向替代清单超链接跳转到另一部件详情）：
// 同一路由记录组件被复用，必须完整重载，否则页面数据停留在上一个部件
watch(() => route.params.oid, async (newOid, oldOid) => {
  if (!newOid || newOid === oldOid) return
  // 重置页面状态，避免上个部件的选中行/BOM/Tab 状态残留
  bomSelectedRow.value = null
  bomList.value = []
  alternateList.value = []
  docLinksList.value = []
  refSelectedKeys.value = []
  descSelectedKeys.value = []
  usedByList.value = []
  activeTab.value = 'bom'
  await loadDetail()
  await loadHistory()
  if (activeTab.value === 'bom') loadBom()
})

// 当前查看迭代变化时（如详情数据更新），同步刷新 BOM
watch(currentIterationOid, () => {
  if (activeTab.value === 'bom') loadBom()
})

onMounted(async () => {
  await loadDetail()
  await loadHistory()
  // 默认 tab 为 BOM 结构，需在 latestIterationOid 就绪后主动加载 BOM
  if (activeTab.value === 'bom') loadBom()
})
</script>

<style scoped>
.part-detail {
  /* 顶部页头（白卡+阴影） + Tabs 内容区，外层 padding 由 MainLayout.layout-content 提供 */
}
.pd-header {
  position: relative;
  background: #fff;
  border-radius: 8px;
  padding: 12px 20px 2px;
  margin-bottom: 2px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}
/* 顶部彩色渐变边条（参考产品系列卡片视觉） */
.pd-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #722ed1 0%, #eb2f96 50%, #2f54eb 100%);
  border-radius: 8px 8px 0 0;
}
.pd-header-main {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.pd-title {
  flex: 1;
  min-width: 0;
}
/* 面包屑：所属产品系列/型号 + 文件夹路径 */
.pd-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #595959;
  flex-wrap: wrap;
}
.pd-breadcrumb .pd-location-tag {
  font-weight: 500;
}
.pd-breadcrumb .pd-location-sub {
  margin-left: 4px;
  font-size: 11px;
  opacity: 0.85;
}
.pd-breadcrumb-sep {
  color: #d9d9d9;
  margin: 0 2px;
}
.pd-breadcrumb-folder {
  color: #262626;
}
/* 主标题行：名称 + 编码 */
.pd-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}
.pd-name {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.3;
  color: #1a1a2e;
}
.pd-code {
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #595959;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
}
/* meta tag 组 */
.pd-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
/* 底部时间信息 */
.pd-meta-time {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  flex-wrap: wrap;
}
.pd-meta-time-sep {
  color: #d9d9d9;
  margin: 0 4px;
}
/* 返回按钮：垂直对齐到顶部 */
.pd-back-btn {
  flex-shrink: 0;
  align-self: flex-start;
}

/* 历史版本超链接 */
.pd-history-link {
  font-weight: 600;
  color: #1677ff;
  text-decoration: none;
  cursor: pointer;
  border-bottom: 1px dashed #91caff;
  padding-bottom: 1px;
  transition: color 0.2s, border-color 0.2s;
}
.pd-history-link:hover {
  color: #0958d9;
  border-bottom: 1px solid #0958d9;
}
.pd-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 4px 20px 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  /* 防止被外层 a-layout-content 的 flex 强制撑高，避免内部出现横向滚动条 */
  min-height: 0;
  height: auto;
}
.pd-tabs :deep(.ant-tabs-tab) {
  font-size: 14px;
}
.pd-tabs :deep(.ant-tabs-content-holder) {
  overflow: visible;
}
.pd-tabs :deep(.ant-tabs-content) {
  max-height: none;
  overflow: visible;
}
.pd-tabs :deep(.ant-tabs-tabpane) {
  overflow: visible;
}

/* BOM 结构工具栏：搜索 + 检出/检入 + 操作按钮组 */
.bom-toolbar {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
  padding: 4px 8px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}
.bom-toolbar-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e8e8e8;
}
.bom-toolbar-count {
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
  align-self: center;
}
.bom-toolbar-groups {
  display: flex;
  flex-wrap: wrap;
  row-gap: 8px;
  align-items: center;
}
.bom-toolbar-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 0 12px;
  border-right: 1px solid #d9d9d9;
}
.bom-toolbar-group:first-child {
  padding-left: 0;
}
.bom-toolbar-group:last-child {
  border-right: none;
  padding-right: 0;
}
/* 统计分组：靠右、取消右边框 */
.bom-toolbar-count-group {
  margin-left: auto;
  padding-right: 0;
  border-right: none;
}
.bom-toolbar-count-group .bom-toolbar-count {
  align-self: flex-start;
}

/* ===== Tab 内统计栏（对齐用户管理页面 user-stats-bar 风格） ===== */
.tab-stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  margin-bottom: 12px;
}
.tab-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}
.tab-stat-icon {
  font-size: 14px;
  color: #1677ff;
}
.tab-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}
.tab-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}
.tab-stat-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 部件编码超链接：指向该 Part 最新版本详情页 */
.alternate-part-link {
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  margin-left: 6px;
  color: #1677ff;
  text-decoration: none;
}
.alternate-part-link:hover {
  background: #e6f4ff;
  text-decoration: underline;
}

/* ===== BOM 版本对比对话框 ===== */
.bom-compare-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.bom-compare-arrow {
  color: #1677ff;
  font-size: 16px;
  flex-shrink: 0;
}
.bom-compare-result {
  margin-top: 8px;
  padding: 4px 0 12px;
}
.bom-compare-empty {
  margin-top: 8px;
  padding: 8px 0 12px;
}

/* ===== 差异明细列表 ===== */
.bom-compare-detail {
  max-height: 340px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bom-compare-block {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 6px 12px;
}
.bom-compare-block-title {
  font-size: 12px;
  font-weight: 600;
  color: #595959;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.bom-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.bom-dot-add { background: #52c41a; }
.bom-dot-del { background: #ff4d4f; }
.bom-dot-chg { background: #faad14; }
.bom-compare-block ul {
  margin: 0;
  padding-left: 18px;
}
.bom-compare-block li {
  font-size: 12.5px;
  line-height: 1.9;
  color: #262626;
}
.bom-compare-from-to {
  color: #faad14;
  font-size: 12px;
}
.bom-group-label {
  font-size: 11px;
  color: #595959;
  font-weight: 600;
  white-space: nowrap;
  letter-spacing: 0.3px;
  line-height: 1.2;
  display: block;
  text-align: center;
}
/* 工具栏内的按钮统一紧凑样式 */
.bom-toolbar :deep(.ant-btn-sm) {
  font-size: 12px;
  padding: 0 8px;
  height: 24px;
  line-height: 22px;
}
.bom-toolbar :deep(.ant-btn-group .ant-btn-sm) {
  padding: 0 8px;
}
.bom-toolbar :deep(.ant-btn-group) {
  display: inline-flex;
  flex-wrap: nowrap;
}
.bom-selected-info {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 220px;
  max-width: 420px;
  padding: 2px 10px;
  background: #fff;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  overflow: hidden;
}
.bom-selected-name {
  font-weight: 500;
  color: #1a1a2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bom-selected-code {
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #595959;
  white-space: nowrap;
}
.bom-selected-empty {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #bfbfbf;
  white-space: nowrap;
}

/* BOM 主体列：名称 + 编码 + 版本 + 视图 + 状态（inline-flex 与展开图标同行） */
.bom-subject {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
  vertical-align: middle;
  max-width: 100%;
  overflow: hidden;
}
.bom-subject-name {
  font-weight: normal;
  color: #262626;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bom-subject-lock {
  color: #fa8c16;
  font-size: 13px;
  flex-shrink: 0;
}
.bom-subject-name-root {
  font-weight: normal;
  color: #1a1a2e;
}
.bom-subject-code {
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #595959;
  white-space: nowrap;
  flex-shrink: 0;
}
.bom-subject-code-root {
  background: #e6f4ff;
  color: #1677ff;
}
.bom-subject :deep(.ant-tag) {
  margin-inline-end: 0;
  white-space: nowrap;
}

/* ===== BOM 树形连接线：rail（竖线）+ branch（竖线 + 水平线连接到父级） ===== */
/* 关闭 antd-vue 自带缩进，缩进完全由连接线 cell 提供（避免双重缩进） */
:deep(.bom-left .ant-table-row-indent) {
  padding-left: 0 !important;
  display: none;
}

/* ===== BOM 树展开/收缩图标：缩小尺寸 + 弱化白色背景 ===== */
:deep(.bom-left .ant-table-row-expand-icon) {
  width: 14px;
  height: 14px;
  line-height: 12px;
  border: 1px solid #d9d9d9;
  background: transparent;
  border-radius: 2px;
  outline: none;
  box-sizing: border-box;
}
:deep(.bom-left .ant-table-row-expand-icon:hover) {
  border-color: #1677ff;
  color: #1677ff;
}
:deep(.bom-left .ant-table-row-expand-icon::before) {
  top: 5px;
  right: 2px;
  left: 2px;
  height: 1px;
}
:deep(.bom-left .ant-table-row-expand-icon::after) {
  top: 2px;
  bottom: 2px;
  left: 5px;
  width: 1px;
}
/* 折叠状态（+）需要显示竖线，展开状态（-）隐藏竖线 */
:deep(.bom-left .ant-table-row-expand-icon-collapsed::after) {
  transform: rotate(0deg);
  opacity: 1;
}
:deep(.bom-left .ant-table-row-expand-icon-expanded::after) {
  opacity: 0;
}
/* 叶子节点占位符（无 +/- 图标）也缩小 */
:deep(.bom-left .ant-table-row-spaced) {
  width: 14px;
  height: 14px;
  background: transparent;
}
/* 收窄展开图标所在单元格 */
:deep(.bom-left .ant-table-row-expand-icon-cell) {
  width: 24px !important;
  min-width: 24px !important;
  padding-left: 4px !important;
  padding-right: 2px !important;
}
.bom-tree-cells {
  display: inline-flex;
  gap: 0;
  flex-shrink: 0;
  vertical-align: middle;
  margin-right: 2px;
}
.bom-tree-cell {
  display: inline-block;
  width: 12px;
  height: 24px;
  position: relative;
  flex-shrink: 0;
}
.bom-tree-cell::before {
  /* Windchill 风格：单条深蓝虚线（rail 竖线 + branch 前后连线） */
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 0;
  border-left: 1px dashed #1677ff;
}
.bom-tree-branch::after {
  /* branch 水平前后连线：深蓝虚线连到父级与子节点之间 */
  content: '';
  position: absolute;
  left: 0;
  top: calc(50% - 0.5px);
  right: 0;
  height: 0;
  border-top: 1px dashed #1677ff;
}

/* BOM 行选中高亮（背景色，不用单选框） */
:deep(.bom-row-selected) > td {
  background-color: #e6f4ff !important;
}
:deep(.bom-row-selected:hover) > td {
  background-color: #d6eaff !important;
}

/* ===== BOM 两栏布局：左侧结构树 + 右侧详情面板 ===== */
.bom-layout {
  display: flex;
  gap: 0;
  align-items: stretch;
  min-height: 320px;
}
.bom-left {
  flex: none;
  min-width: 0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: auto;
}
/* 可拖拽分隔条 */
.bom-splitter {
  flex: none;
  width: 12px;
  cursor: col-resize;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}
.bom-splitter-handle {
  width: 2px;
  height: 40px;
  background: #e8e8e8;
  border-radius: 1px;
  transition: background-color 0.2s;
}
.bom-splitter:hover .bom-splitter-handle,
.bom-splitter-active .bom-splitter-handle {
  background: #1677ff;
}
.bom-right {
  flex: 1 1 auto;
  min-width: 0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.bom-right-tabs {
  height: 100%;
  padding-top: 8px;
  padding-left: 12px;
  padding-right: 12px;
}
.bom-right-tabs :deep(.ant-tabs-content-holder) {
  overflow: auto;
}
/* 子件清单底部操作条 */
.bom-items-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}
.bom-items-tip {
  font-size: 12px;
  color: #8c8c8c;
}
.bom-detail-pane {
  padding: 8px;
}
.cad-grid {
  padding: 4px 0;
}
</style>