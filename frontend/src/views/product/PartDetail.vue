<template>
  <div class="part-detail">
    <!-- 顶部标题栏（参考产品系列卡片样式：渐变边条 + 面包屑 + 主信息） -->
    <div class="pd-header">
      <div class="pd-header-main">
        <div class="pd-title">
          <!-- 面包屑：所属产品系列 / 型号。
               零件管理独立于阶段与文件夹（2026-10-22），不再展示「研发阶段」「所属文件夹」 -->
          <div class="pd-breadcrumb" v-if="productContainer">
            <ApartmentOutlined style="color:#1677ff" />
            <a-tag v-if="productContainer" color="cyan" class="pd-location-tag">
              {{ productContainer.name || productContainer.code }}
              <span v-if="productContainer.type === 'PRODUCT_MODEL'" class="pd-location-sub">型号</span>
              <span v-else class="pd-location-sub">系列</span>
            </a-tag>
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
            <!-- 时间信息：与标题 / 标签同一行（2026-10-22 用户要求从下一行移上来） -->
            <div class="pd-meta-time" v-if="part?.createdAt">
              <ClockCircleOutlined style="color:#8c8c8c" />
              <span>创建于 {{ fmtTime(part?.createdAt) }}</span>
              <span v-if="part?.updater" class="pd-meta-time-sep">·</span>
              <span v-if="part?.updater">{{ part.updater }} 更新于 {{ fmtTime(part?.updatedAt) }}</span>
            </div>
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
                  <a-tooltip :title="roTitle('局部替代（仅本 BOM 行生效）')"><a-button size="small" :disabled="!isLatestVersion || !bomSelectedRow || bomSelectedRow.isRoot" @click="onSubstitute"><SwapOutlined /></a-button></a-tooltip>
                  <!-- 成组替代的原料侧取自"选中行的下挂"，所以必须先选中一行 -->
                  <a-tooltip :title="roTitle('成组替代（选中行的一组下挂 ↔ 一组替代物料）')"><a-button size="small" :disabled="!isLatestVersion || !bomSelectedRow" @click="onGroupSubstitute"><ClusterOutlined /></a-button></a-tooltip>
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
                    <!-- 有过滤条件时按钮高亮、并把它写进 tooltip —— 条件是折叠起来的，最容易被忘掉 -->
                    <a-tooltip :title="hasBomFilter ? `过滤器设置（当前：${bomFilterSummary}）` : '过滤器设置'">
                      <a-button size="small" :type="hasBomFilter ? 'primary' : 'default'">
                        <FilterOutlined /><DownOutlined />
                      </a-button>
                    </a-tooltip>
                    <template #overlay>
                      <a-menu :selected-keys="bomFilterSelectedKeys" @click="onFilterAction">
                        <!-- 替代关系是"过滤器"体系里的一个条件（不在旁边另占一个按钮）：
                             勾选 = 只看设有局部替代的 BOM 行，父级层级保留 -->
                        <a-menu-item key="substituteOnly">
                          <SwapOutlined />
                          <span>只看有替代的行</span>
                          <span class="bom-filter-num">{{ substitutedRowCount }}</span>
                          <CheckOutlined v-if="onlySubstituted" class="bom-filter-check" />
                        </a-menu-item>
                        <a-menu-divider />
                        <!-- 只读展示：当前生效的条件（没条件时显示"无"） -->
                        <a-menu-item key="current" disabled>当前过滤器：{{ bomFilterSummary }}</a-menu-item>
                        <a-menu-item key="edit">编辑过滤器…</a-menu-item>
                        <a-menu-item key="clear" :disabled="!hasBomFilter">清除过滤器</a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                  <!-- 显示/隐藏：控制 BOM 表格列的显隐。
                       图标用齿轮而不是眼睛：眼睛已被前面「切换 BOM 视图」占用，
                       两个一模一样的按钮并排（都带下拉箭头）谁也分不清是哪个 -->
                  <a-dropdown :trigger="['click']">
                    <a-tooltip title="表格列显示与隐藏">
                      <a-button size="small"><SettingOutlined /><DownOutlined /></a-button>
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

              <!-- 快照：把当前 BOM 行定格一份，供事后对比（BOM 改完就回不去了，快照是唯一的基线） -->
              <div class="bom-toolbar-group">
                <span class="bom-group-label">快照</span>
                <a-button-group>
                  <a-tooltip title="新增快照（保存当前 BOM 行）"><a-button size="small" @click="openSnapshotModal"><CameraOutlined /></a-button></a-tooltip>
                  <a-tooltip title="快照记录与对比"><a-button size="small" @click="openSnapshotList"><HistoryOutlined /></a-button></a-tooltip>
                </a-button-group>
              </div>

              <div class="bom-toolbar-group">
                <span class="bom-group-label">报告</span>
                <a-button-group>
                  <!-- 导出：先选格式再导 —— 四种格式用途不同（Excel 给人看、CSV 给系统导、PDF 给打印评审） -->
                  <a-dropdown :trigger="['click']">
                    <a-tooltip title="导出 BOM（csv / xls / xlsx / pdf）">
                      <a-button size="small" :disabled="!bomTotalCount" :loading="bomExporting"><DownloadOutlined /></a-button>
                    </a-tooltip>
                    <template #overlay>
                      <a-menu @click="onExportBom">
                        <a-menu-item key="xlsx">Excel 工作簿（.xlsx）</a-menu-item>
                        <a-menu-item key="xls">Excel 97-2003（.xls）</a-menu-item>
                        <a-menu-item key="csv">CSV 表格（.csv）</a-menu-item>
                        <a-menu-divider />
                        <a-menu-item key="pdf">PDF 报表（.pdf）</a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                  <a-tooltip :title="roTitle('导入 BOM')"><a-button size="small" :disabled="!isLatestVersion" @click="onImportBom"><UploadOutlined /></a-button></a-tooltip>
                  <!-- 成本报告入口受 BOM_COST 能力控制（与其它能力入口同口径：账本里没有就不显示） -->
                  <a-tooltip v-if="has('BOM_COST')" title="成本报告"><a-button size="small" @click="onCostReport"><DollarOutlined /></a-button></a-tooltip>
                  <a-button title="对比两个版本的 BOM 差异" size="small" :disabled="historyList.length < 2" @click="onCompareBom"><DiffOutlined /></a-button>
                </a-button-group>
              </div>

              <!-- 子件统计：靠右 -->
              <div class="bom-toolbar-group bom-toolbar-count-group">
                <span class="bom-group-label">统计</span>
                <span class="bom-toolbar-count">
                  共 {{ bomTotalCount }} 个子件<template v-if="substitutedRowCount"> · {{ substitutedRowCount }} 行有替代</template>
                </span>
              </div>
            </div>
          </div>

          <!-- 两栏布局：左侧 BOM 结构树 + 右侧选中项详情面板（中间可拖拽调整宽度） -->
          <div class="bom-layout" ref="bomLayoutRef">
            <!-- 左侧：BOM 结构树（主题 + 数量），宽度可拖拽 -->
            <div class="bom-left" :style="{ width: bomLeftWidth ? `${bomLeftWidth}px` : '55%' }">
              <a-table
                :columns="bomColumnsFiltered"
                :data-source="bomTreeDataFiltered"
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
                      <!-- 局部替代标记：有替代件才出现（无替代不占位，免得整列都是灰点）。
                           数字 = 替代件总数，全部停用时置灰；明细 hover 才拉 —— 每行预加载
                           会给整棵树加 N 次请求，而只有看懂标记的人才会去 hover -->
                      <a-tooltip
                        v-if="!record.isRoot && record.substituteCount"
                        placement="top"
                        @openChange="(open) => open && loadSubTip(record)"
                      >
                        <template #title>
                          <div class="bom-sub-tip">
                            <div class="bom-sub-tip-head">
                              局部替代 {{ record.substituteCount }} 个<template v-if="record.substituteEnabledCount < record.substituteCount">（启用 {{ record.substituteEnabledCount }} 个）</template>
                            </div>
                            <div v-if="subTipOf(record).loading" class="bom-sub-tip-note">加载中…</div>
                            <template v-else>
                              <div
                                v-for="s in subTipOf(record).list"
                                :key="s.oid"
                                class="bom-sub-tip-row"
                                :class="{ 'bom-sub-tip-row-off': s.enabled === false }"
                              >
                                <code>{{ s.substitutePartNumber || '-' }}</code>
                                <span class="bom-sub-tip-name">{{ s.substitutePartName || '-' }}</span>
                                <span v-if="s.substituteVersion" class="bom-sub-tip-ver">{{ s.substituteVersion }}</span>
                                <span class="bom-sub-tip-qty">×{{ s.substituteQuantity ?? 1 }}</span>
                                <span v-if="s.enabled === false" class="bom-sub-tip-off">停用</span>
                              </div>
                              <div v-if="!subTipOf(record).list.length" class="bom-sub-tip-note">（暂无明细）</div>
                              <div class="bom-sub-tip-foot">仅本 BOM 行生效 · 点击打开「替代情况」</div>
                            </template>
                          </div>
                        </template>
                        <span
                          class="bom-sub-badge"
                          :class="{ 'bom-sub-badge-off': !record.substituteEnabledCount }"
                          @click.stop="onClickSubBadge(record)"
                        >
                          <SwapOutlined />
                          <span>{{ record.substituteCount }}</span>
                        </span>
                      </a-tooltip>
                      <!-- 成组替代标记：本行是某个"整组替换"的原料侧成员时出现。
                           与局部替代分成两个标记 —— 一个是"这行换几颗料"，一个是"这行属于几组整组替换"，
                           后者不允许拆开换，含义完全不同 -->
                      <a-tooltip
                        v-if="!record.isRoot && record.substituteGroupCount"
                        placement="top"
                        @openChange="(open) => open && loadGroupTip()"
                      >
                        <template #title>
                          <div class="bom-sub-tip">
                            <div class="bom-sub-tip-head">成组替代 {{ record.substituteGroupCount }} 组</div>
                            <div v-if="groupCache.loading" class="bom-sub-tip-note">加载中…</div>
                            <template v-else>
                              <div v-for="g in groupsOf(record)" :key="g.oid" class="bom-sub-tip-row">
                                <span class="bom-sub-tip-name">{{ g.name || '未命名替代组' }}</span>
                                <span class="bom-sub-tip-ver">{{ (g.substitutes || []).length }} 颗替代物料</span>
                                <span class="bom-sub-tip-off">{{ g.atomicReplace === false ? '可拆开换' : '整组替换' }}</span>
                              </div>
                              <div class="bom-sub-tip-foot">整组替换：拆开只换其中一个不成立 · 点击打开成组替代</div>
                            </template>
                          </div>
                        </template>
                        <span class="bom-group-badge" @click.stop="onClickGroupBadge">
                          <ClusterOutlined />
                          <span>{{ record.substituteGroupCount }}</span>
                        </span>
                      </a-tooltip>
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

                <!--
                  轻量化：选中 BOM 行的三维预览。
                  链条＝零件迭代 →（工程数据关联）→ 工程数据迭代 → 轻量化 GLB；
                  没关联 / 没转过 / 转换中 三种状态各有对应入口，不再是一句"开发中"。
                -->
                <a-tab-pane key="lightweight" tab="轻量化">
                  <template v-if="bomSelectedRow">
                    <div class="bom-cad-head">
                      <span class="bom-cad-title" :title="`${bomSelectedRow.childNumber || ''} ${bomSelectedRow.childName || ''}`">
                        {{ bomSelectedRow.childNumber || '-' }} {{ bomSelectedRow.childName || '' }}
                      </span>
                      <a-tag v-if="bomCadModel?.iterationVersion" size="small">数模 {{ bomCadModel.iterationVersion }}</a-tag>
                      <a-button size="small" type="link" :loading="bomCadLoading" @click="loadBomCad">刷新</a-button>
                    </div>

                    <a-empty
                      v-if="!bomCadAvailable && !bomCadLoading"
                      description="未部署三维转换器 CK-CADForge，无法生成轻量化预览"
                      :image-style="{ height: '48px' }"
                    />
                    <div v-else-if="bomCadLoading" class="bom-cad-loading">
                      <a-spin tip="读取轻量化状态..." />
                    </div>
                    <template v-else-if="bomCadReady">
                      <div class="bom-cad-viewer">
                        <!-- 右栏窄：不展示结构树、也不放右侧按钮列，模型占满整块画布 -->
                        <CadViewer
                          :glb-url="bomCadGlbUrl"
                          :structure-url="bomCadStructureUrl"
                          :stats="bomCadStats"
                          :show-structure="false"
                          :show-toolbar="false"
                        />
                      </div>
                    </template>
                    <template v-else-if="bomCadWorking">
                      <CadConvertProgress
                        :status="bomCadModel?.status || 'QUEUED'"
                        :progress="bomCadModel?.progress ?? null"
                        :stage="bomCadModel?.stage || ''"
                        :updated-at="bomCadModel?.statusUpdatedAt"
                      />
                    </template>
                    <!-- 没关联工程数据：说清去哪儿补，而不是只说"暂无" -->
                    <a-empty
                      v-else-if="!bomCadModel"
                      description="该版本未关联可轻量化的 CAD 数模"
                      :image-style="{ height: '48px' }"
                    >
                      <div style="font-size:12px;color:#8c8c8c;line-height:1.7">
                        在「CAD 描述 → 工程数据」关联 STEP / IGES / STL 后，<br />
                        这里就能生成三维预览（原生数模只归档、不转换）
                      </div>
                    </a-empty>
                    <!-- 有数模但没转过 / 转过但失败 -->
                    <template v-else>
                      <a-alert
                        v-if="bomCadMessage"
                        type="warning"
                        show-icon
                        :message="bomCadMessage"
                        style="margin-bottom:12px"
                      />
                      <a-empty :description="bomCadMessage ? '上次未成功' : '尚未生成轻量化预览'" :image-style="{ height: '48px' }">
                        <a-button
                          type="primary"
                          size="small"
                          :disabled="!bomCadModel?.sourceCkfileOid"
                          @click="startBomCadConvert"
                        >{{ bomCadMessage ? '重新生成' : '生成轻量化' }}</a-button>
                      </a-empty>
                    </template>
                  </template>
                  <a-empty v-else description="请选择左侧 BOM 项查看轻量化" :image-style="{ height: '48px' }" />
                </a-tab-pane>

                <!-- 替代情况：选中 BOM 行的局部替代明细（源表 ck_bom_substitute_link） -->
                <a-tab-pane key="substitute" tab="替代情况">
                  <template v-if="bomSelectedRow && !bomSelectedRow.isRoot">
                    <div class="tab-stats-bar">
                      <div class="tab-stat-item">
                        <SwapOutlined class="tab-stat-icon" />
                        <span class="tab-stat-value">{{ rowSubstitutes.length }}</span>
                        <span class="tab-stat-label">本行替代件</span>
                      </div>
                      <a-divider type="vertical" style="height:24px" />
                      <div class="tab-stat-item">
                        <span class="tab-stat-value">{{ rowSubstituteEnabledCount }}</span>
                        <span class="tab-stat-label">启用</span>
                      </div>
                      <div class="tab-stat-actions">
                        <a-button size="small" type="primary" :disabled="!isLatestVersion" @click="onOpenSubstituteFromTab">
                          <PlusOutlined /> 设置替代
                        </a-button>
                        <a-button
                          size="small"
                          danger
                          :loading="rowSubsBusy"
                          :disabled="!rowSubstitutes.length || !isLatestVersion"
                          @click="onClearRowSubstitutes"
                        >
                          <DeleteOutlined /> 清空
                        </a-button>
                      </div>
                    </div>
                    <a-table
                      :columns="rowSubstituteColumns"
                      :data-source="rowSubstitutes"
                      :loading="rowSubsLoading"
                      :pagination="false"
                      row-key="oid"
                      size="small"
                      :locale="{ emptyText: '本行未设替代件，点「设置替代」添加' }"
                    >
                      <template #bodyCell="{ column, record }">
                        <template v-if="column.key === 'part'">
                          <a class="bom-sub-link" @click="onGotoSubstitutePart(record)">
                            {{ record.substitutePartName || '-' }}
                          </a>
                          <code style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959;margin-left:6px">{{ record.substitutePartNumber || '-' }}</code>
                        </template>
                        <template v-else-if="column.key === 'version'">{{ record.substituteVersion || '-' }}</template>
                        <template v-else-if="column.key === 'type'">
                          <a-tag :color="(ALTERNATE_TYPE_MAP[record.substituteType] || {}).color || 'default'" size="small">
                            {{ (ALTERNATE_TYPE_MAP[record.substituteType] || {}).label || record.substituteType || '-' }}
                          </a-tag>
                        </template>
                        <template v-else-if="column.key === 'quantity'">
                          {{ record.substituteQuantity ?? 1 }}{{ record.substituteUnit || '' }}
                        </template>
                        <template v-else-if="column.key === 'priority'">{{ record.priority ?? '-' }}</template>
                        <template v-else-if="column.key === 'enabled'">
                          <a-tag :color="record.enabled === false ? 'default' : 'success'" size="small">
                            {{ record.enabled === false ? '停用' : '启用' }}
                          </a-tag>
                        </template>
                        <template v-else-if="column.key === 'action'">
                          <a-button
                            type="link"
                            size="small"
                            danger
                            :disabled="!isLatestVersion"
                            @click="onDeleteRowSubstitute(record)"
                          >
                            删除
                          </a-button>
                        </template>
                      </template>
                    </a-table>
                    <div class="bom-sub-tab-note">
                      局部替代<b>仅在本 BOM 行</b>生效；"这颗料在任意 BOM 中都可被替换"属于全局替代，见零件页签「双向替代」。
                    </div>
                  </template>
                  <a-empty v-else description="请先在左侧选择一条 BOM 行" :image-style="{ height: '48px' }" />
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
              <a-descriptions-item label="分类">{{ clsName || '-' }}</a-descriptions-item>
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

          <!--
            工程数据（3D 数模 / 2D 工程图 / 封装 / 图符）：零件版本 ↔ 工程数据版本，对应 Windchill EPMBuildRule。
            2026-10-26 用户要求：从「关联文档 → 工程数据」子页签移到本页签 —— 工程数据本就是 CAD 描述的
            实物证据，和三维模型 / 图符放一处，不用在两个页签之间来回切。

            <p>原来这里还有一句「该类型无需 CAD 描述」的空状态：它只看零件类型
            （STRUCT / ELECTRONIC / ELECTRICAL / SOFTWARE 之外一律这么说），跟有没有工程数据、
            跟业务配置里停用了哪个对象类型**毫无关系** —— 关掉 ENG_DOCUMENT 相关对象它照样出现，
            而且和下面的「工程数据」表自相矛盾（说没 CAD 描述，却又列着关联的数模）。
            现在直接不显示：没有卡片就只渲染工程数据，页签本身仍有用。
          -->
          <div class="cad-eng" :class="{ 'cad-eng--solo': !hasCadCards }">
            <div class="cad-eng-title">
              <ApartmentOutlined class="tab-stat-icon" />
              工程数据
              <span class="cad-eng-hint">
                零件版本 ↔ 工程数据版本 · 一个零件版本<b>只能</b>关联一份工程数据
              </span>
            </div>
            <div class="tab-stats-bar">
              <div class="tab-stat-item">
                <span class="tab-stat-value">{{ engDocLinks.length }}</span>
                <span class="tab-stat-label">已关联</span>
              </div>
              <div class="tab-stat-actions">
                <!-- 已关联一份就把入口关掉：一个零件版本只允许一份工程数据（后端同样会拦） -->
                <a-tooltip :title="engDocLinks.length ? '已关联一份工程数据，要换请先「解除关联」' : '选择一个工程数据并建立关联'">
                  <span>
                    <a-button size="small" type="primary" :disabled="engDocLinks.length > 0" @click="onAddEngDocLink">
                      <PlusOutlined /> 关联工程数据
                    </a-button>
                  </span>
                </a-tooltip>
                <a-button size="small" danger :disabled="!engDocSelectedKeys.length" @click="onRemoveEngDocLinks">
                  <DeleteOutlined /> 解除关联
                </a-button>
              </div>
            </div>
            <DataTable
              scroll-x="max-content"
              :columns="engDocLinkColumns"
              :data-source="engDocLinks"
              :loading="engDocLoading"
              :pagination="false"
              row-key="oid"
              size="small"
              :row-selection="{ selectedRowKeys: engDocSelectedKeys, onChange: keys => (engDocSelectedKeys = keys) }"
              empty-text="暂无关联工程数据，点击「关联工程数据」建立关系"
              searchable
              search-placeholder="搜索编号/名称..."
              :search-fields="['engDocNumber', 'engDocName']"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'engDoc'">
                  <span style="color:#262626">{{ record.engDocName || '-' }}</span>
                  <code style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959;margin-left:6px">{{ record.engDocNumber || '-' }}</code>
                </template>
                <template v-else-if="column.key === 'engDocType'">
                  <a-tag color="cyan">{{ record.engDocType || '-' }}</a-tag>
                </template>
                <template v-else-if="column.key === 'engDocVersion'">
                  <span>{{ record.engDocVersion || '-' }}</span>
                  <a-tag v-if="!record.pinned" style="margin-left:4px">跟随最新</a-tag>
                </template>
                <template v-else-if="column.key === 'assocType'">
                  <a-tag :color="ENG_DOC_ASSOC_COLOR[record.assocType] || 'default'">
                    {{ ENG_DOC_ASSOC_LABEL[record.assocType] || record.assocType }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'buildRules'">
                  <a-space v-if="hasBuildRule(record)" size="small" wrap>
                    <a-tag v-if="record.buildStructure" color="blue">结构</a-tag>
                    <a-tag v-if="record.buildAttribute" color="purple">属性</a-tag>
                    <a-tag v-if="record.buildRepresentation" color="geekblue">表示</a-tag>
                  </a-space>
                  <span v-else style="color:#bfbfbf">—</span>
                </template>
                <template v-else-if="column.key === 'createdAt'">
                  {{ fmtTime(record.createdAt) }}
                </template>
              </template>
            </DataTable>
          </div>
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
            scroll-x="max-content"
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

        <!-- ========== 3.6 MPN（认可制造商料号 AML + 供货渠道 AVL） ==========
             共享组件：与企业资源页「MPN 库」的 AML·AVL 页签同一实现（见 components/PartMpnPanel.vue）。
             件由本页 oid 直接传入，不需要选件器。
             ★ 2026-10-11：本页签受能力开关控制，**所属能力 = MPN 管理（MPN_MANAGEMENT**，
               声明于 electronics-core/capabilities.json）—— 与导航「MPN 厂商」菜单、企业资源页
               「MPN 库」页签**同一开关**：能力关掉，三处入口一起消失（不出现"点进去什么都没有"）。 -->
        <a-tab-pane v-if="hasMpn" key="mpn" tab="MPN">
          <PartMpnPanel :part-oid="oid" />
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
                scroll-x="max-content"
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
                scroll-x="max-content"
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

        <!-- ========== 5. 被使用情况（反向 BOM，可逐层往上追溯） ========== -->
        <a-tab-pane key="usedBy" tab="被使用情况">
          <!--
            往上追溯：本条链路上每一层的"查看范围"。点前面的层可回退，
            最后一个是当前正在看的零件（它的父件由下面的表格回答）。
          -->
          <div v-if="usedByDrill.length" class="usedby-scope">
            <span class="usedby-scope-label">查看范围</span>
            <a class="usedby-scope-node" @click="onUsedByDrillTo(-1)">{{ usedByRootLabel }}</a>
            <template v-for="(node, idx) in usedByDrill" :key="node.partOid">
              <span class="usedby-scope-sep">›</span>
              <a v-if="idx < usedByDrill.length - 1" class="usedby-scope-node" @click="onUsedByDrillTo(idx)">{{ node.label }}</a>
              <span v-else class="usedby-scope-current">{{ node.label }}</span>
            </template>
            <a-button type="link" size="small" style="padding:0 4px" @click="onUsedByBack">返回上一层</a-button>
          </div>
          <a-table
            :columns="usedByColumns"
            :data-source="usedByList"
            :loading="usedByLoading"
            :pagination="false"
            :row-key="r => r.linkOid || r.parentIterationOid"
            size="small"
            :locale="{ emptyText: '未被其他零组件使用' }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'parentNumber'">
                <!-- 点编码 = 以该父件为范围，继续看它被谁使用（往上一层） -->
                <a
                  class="usedby-parent-link"
                  :title="`向上追溯「${record.parentName || '未知父件'}」的被使用情况`"
                  @click="onUsedByDrillUp(record)"
                >{{ record.parentNumber || '-' }}</a>
                <a-tag v-if="record.parentView" size="small" color="blue" style="margin-left:6px">{{ record.parentView }}</a-tag>
              </template>
              <template v-else-if="column.key === 'parentName'">
                {{ record.parentName || '未知父件' }}
              </template>
              <template v-else-if="column.key === 'parentVersion'">
                <a-tag size="small">{{ record.parentVersion || '-' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'exactRef'">
                <a-tag v-if="record.exactReference" color="success" size="small">精确</a-tag>
                <a-tag v-else color="warning" size="small">跟随最新</a-tag>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- ========== 6. 流程情况（关联流程：执行中 / 已执行 两栏，所有业务对象共用同一组件） ========== -->
        <a-tab-pane key="workflow" tab="流程情况">
          <RelatedProcesses :entity-oid="oid" />
        </a-tab-pane>

        <!--
          ========== 7. 变更情况 ==========
          口径（2026-10-26 用户要求）：按**变更通告（ECN）的变更对象**反查 —— 这张零件在某张通告里
          是变更对象（ck_change_affected_object，主对象级），就出现在这里；一行 = 该通告上的这一条变更对象，
          所以能看到"被改的是哪一版、改出来的又是哪一版"。
        -->
        <a-tab-pane key="changes" tab="变更情况">
          <div class="tab-stats-bar">
            <div class="tab-stat-item">
              <DiffOutlined class="tab-stat-icon" />
              <span class="tab-stat-value">{{ changeNotices.length }}</span>
              <span class="tab-stat-label">变更通告</span>
            </div>
            <a-divider type="vertical" style="height:24px" />
            <div class="tab-stat-item">
              <span class="tab-stat-value">{{ changeExecutedCount }}</span>
              <span class="tab-stat-label">已执行（已出新版本）</span>
            </div>
            <div class="tab-stat-actions">
              <a-button size="small" :loading="changeLoading" @click="loadChangeNotices">
                <RollbackOutlined /> 刷新
              </a-button>
            </div>
          </div>
          <DataTable
            scroll-x="max-content"
            :columns="changeNoticeColumns"
            :data-source="changeNotices"
            :loading="changeLoading"
            :pagination="false"
            row-key="affectedOid"
            size="small"
            empty-text="暂无变更通告把该零件作为变更对象"
            searchable
            search-placeholder="搜索通告编号 / 标题..."
            :search-fields="['noticeCode', 'noticeName']"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'notice'">
                <a class="pd-change-link" @click="openChangeNotice(record)">{{ record.noticeCode || '-' }}</a>
                <span class="pd-change-title">{{ record.noticeName || '-' }}</span>
              </template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="CHANGE_NOTICE_STATUS_COLOR[record.status] || 'default'">
                  {{ CHANGE_NOTICE_STATUS_LABEL[record.status] || record.status || '-' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'baseVersion'">
                <a-tag color="blue" size="small">{{ record.baseVersion || '-' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'resultVersion'">
                <a-tag v-if="record.resultVersion" color="green" size="small">{{ record.resultVersion }}</a-tag>
                <span v-else style="color:#bfbfbf">未执行</span>
              </template>
              <template v-else-if="column.key === 'executedAt'">
                <span v-if="record.executedAt">{{ fmtTime(record.executedAt) }}</span>
                <span v-else style="color:#bfbfbf">—</span>
                <a-tag v-if="record.locked" color="orange" size="small" style="margin-left:4px">变更占用中</a-tag>
              </template>
              <template v-else-if="column.key === 'noticeCreatedAt'">
                {{ fmtTime(record.noticeCreatedAt) }}
              </template>
            </template>
          </DataTable>
        </a-tab-pane>

        <!-- ========== 8. 关联需求 ========== -->
        <a-tab-pane key="requirements" tab="关联需求">
          <a-empty description="需求管理开发中" :image-style="{ height: '64px' }" />
        </a-tab-pane>

        <!-- ========== 9. 关联问题 ========== -->
        <!--
          口径：问题的「关联对象」= 本零组件（relatedType=PART + relatedCode=编码）。
          问题只存关联对象的类型 + 编码（ck_issue.related_type / related_code），所以这里按编码查 ——
          同一编码的所有版本共享同一批问题（问题不是版本级数据）。
        -->
        <a-tab-pane key="issues" tab="关联问题">
          <div class="issue-link-bar">
            <span class="issue-link-hint">
              关联对象为本零组件（{{ part?.number || '-' }}）的问题 · 未闭环优先排序
            </span>
            <a-button size="small" :loading="relatedIssuesLoading" @click="loadRelatedIssues">
              <ReloadOutlined /> 刷新
            </a-button>
          </div>
          <DataTable
            :columns="relatedIssueColumns"
            :data-source="relatedIssues"
            :loading="relatedIssuesLoading"
            :pagination="{ pageSize: 10 }"
            :show-total="(t) => `共 ${t} 个问题`"
            row-key="oid"
            size="small"
            search-placeholder="搜索问题编码/标题..."
            :search-fields="['code', 'name']"
            empty-text="暂无关联问题（在「持续改进」里把关联对象选成本零组件即可）"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <a class="issue-code-link" @click="openIssue(record)">{{ record.code || '-' }}</a>
              </template>
              <template v-else-if="column.key === 'issueType'">
                <a-tag v-if="record.issueType" color="blue" size="small">{{ record.issueType }}</a-tag>
                <span v-else style="color:#bfbfbf">未分类</span>
              </template>
              <template v-else-if="column.key === 'severity'">
                <a-tag :color="ISSUE_SEVERITY_COLOR[record.severity] || 'default'" size="small">
                  {{ ISSUE_SEVERITY_LABEL[record.severity] || record.severity || '-' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="ISSUE_STATUS_COLOR[record.status] || 'default'" size="small">
                  {{ ISSUE_STATUS_LABEL[record.status] || record.status || '-' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'dueDate'">
                <span :class="{ 'issue-overdue': isIssueOverdue(record) }">{{ record.dueDate || '-' }}</span>
              </template>
              <template v-else-if="column.key === 'createdAt'">{{ fmtTime(record.createdAt) }}</template>
            </template>
          </DataTable>
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
      <!-- 过滤区：所属容器（企业资源库下钻到子库 / 产品系列 / 型号）+ 名称/编码搜索 -->
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px;flex-wrap:wrap">
        <span style="color:#8c8c8c;font-size:12px;white-space:nowrap">所属容器</span>
        <a-tree-select
          v-model:value="addExistingContainerOid"
          placeholder="选择产品系列/型号/企业资源库"
          allow-clear
          show-search
          tree-node-filter-prop="title"
          :tree-default-expand-all="true"
          :tree-data="addExistingContainerTree"
          :field-names="{ label: 'title', value: 'value', children: 'children' }"
          style="width: 260px"
          @change="onAddExistingContainerChange"
        />
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

    <!--
      新建零组件作为子件：创建表单与「物料清单」页签的「新建零组件」同一套 ——
      先选类型，再按类型专属布局填表（不是另起一套简化表单，免得两个入口建出来的件字段不一致）。
      区别只有两点：容器固定为当前 Part 的容器；建完自动挂到选中的 BOM 节点下。
    -->
    <a-modal
      v-model:visible="newChildModalVisible"
      title="新建零组件作为子件"
      ok-text="创建并添加"
      cancel-text="取消"
      :confirm-loading="newChildSaving"
      :ok-button-props="{ disabled: !newChildSelectedType }"
      @ok="confirmNewChild"
      @cancel="resetNewChildModal"
      width="640px"
      centered
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <div v-if="newChildTarget" style="margin-bottom:10px;font-size:12px;color:#8c8c8c">
        将作为「{{ newChildTarget.name }}」的子件（数量 1 · 单位 ea，建完可在右侧子件清单里改）
      </div>
      <div style="margin-bottom:8px">
        <span style="color:#8c8c8c">选择零组件类型</span>
        <a-tree-select
          v-model:value="newChildTypeSelectValue"
          style="width:100%;margin-top:4px"
          placeholder="请选择零组件类型"
          :tree-data="newChildTypeOptions"
          :field-names="{ label: 'name', value: 'code', children: 'children' }"
          tree-default-expand-all
          show-search
          @change="onNewChildTypeChange"
        />
      </div>

      <a-divider v-if="newChildSelectedType" style="margin:12px 0" />

      <div v-if="newChildSelectedType" style="margin-top:8px">
        <a-tag color="blue" style="margin-bottom:12px">{{ newChildSelectedType.name || newChildSelectedType.code }} - 创建表单</a-tag>
        <DynamicForm
          ref="newChildFormRef"
          :key="newChildEntityCode"
          :entity-code="newChildEntityCode"
          operation-code="create"
          :context="{ containerType: part?.containerType, containerOid: part?.containerOid }"
          :current-container-oid="part?.containerOid"
          :type-definition-oid="newChildSelectedType?.oid"
          v-model="newChildForm"
        />
      </div>
      <div v-else style="padding:24px 0;text-align:center;color:#999">
        <InboxOutlined style="font-size:32px;color:#d9d9d9;margin-bottom:8px;display:block" />
        请先选择零组件类型，系统将加载对应的创建表单
      </div>
    </a-modal>

    <!-- 文档详情查看抽屉（关联文档编码点击打开，复用产品线 DocumentViewer） -->
    <DocumentViewer
      v-model:visible="docViewerVisible"
      :doc="docViewerDoc"
      @close="docViewerVisible = false"
    />

    <!-- 局部替代弹窗：上栏看/删本行已设替代件，下栏选零件添加（仅本 BOM 行生效） -->
    <BomSubstituteModal
      v-model:open="substituteModalOpen"
      :row="bomSelectedRow"
      :container-options="containerOptions"
      :default-container-oid="part?.containerOid || ''"
      :default-container-type="part?.containerType || ''"
      @changed="onSubstituteChanged"
    />

    <!-- 成组替代弹窗：一组 BOM 行 ↔ 一组替代物料（整组替换），挂在当前父件迭代上 -->
    <BomSubstituteGroupModal
      v-model:open="substituteGroupModalOpen"
      :parent-iteration-oid="groupParentIterationOid"
      :part-name="part?.name || ''"
      :scope-name="bomItemsParentName"
      :source-options="bomSourceOptions"
      :container-options="containerOptions"
      :default-container-oid="part?.containerOid || ''"
      :default-container-type="part?.containerType || ''"
      @changed="onSubstituteGroupChanged"
    />

    <!-- 编辑过滤器：目前只有「替代关系」一个条件，以后的状态/检出/视图等条件都往这里加 -->
    <a-modal
      v-model:open="filterModalOpen"
      title="编辑过滤器"
      ok-text="应用"
      cancel-text="取消"
      width="480px"
      @ok="applyBomFilterEdit"
    >
      <div class="bom-filter-option">
        <a-checkbox v-model:checked="filterDraft.substituteOnly">只看有替代的行</a-checkbox>
        <div class="bom-filter-option-desc">
          局部替代挂在 BOM 行上（只在该行生效）。勾选后只保留设有替代的行，<b>父级层级一起保留</b>，
          便于看清它挂在哪一层。典型用例：替代件评审、变更影响确认。
        </div>
      </div>
      <div class="bom-filter-preview">
        当前这张 BOM：<b>{{ substitutedRowCount }}</b> 行设有替代 / 共 {{ bomTotalCount }} 个子件
      </div>
    </a-modal>

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

    <!-- 关联工程数据弹窗：选工程数据 + 关联类型 + 构建规则（EPMBuildRule） -->
    <a-modal
      v-model:visible="addEngDocModalVisible"
      title="关联工程数据"
      ok-text="关联"
      cancel-text="取消"
      :ok-button-props="{ disabled: !addEngDocSelectedRowKeys.length }"
      :confirm-loading="addEngDocSaving"
      @ok="confirmAddEngDocLinks"
      @cancel="addEngDocModalVisible = false"
      width="780px"
      centered
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px;flex-wrap:wrap">
        <span style="color:#8c8c8c;font-size:12px;white-space:nowrap">所属容器</span>
        <a-select
          v-model:value="addEngDocContainerOid"
          placeholder="选择产品系列/型号"
          allow-clear
          show-search
          style="width: 220px"
          @change="loadAddEngDocs"
        >
          <a-select-option v-for="o in containerOptions" :key="o.value" :value="o.value">
            {{ o.label }}
          </a-select-option>
        </a-select>
        <a-input-search
          v-model:value="addEngDocKeyword"
          placeholder="按名称或编号过滤"
          style="flex:1;min-width:180px;margin-left:8px"
          allow-clear
        />
      </div>
      <div style="display:flex;align-items:center;gap:12px;margin-bottom:10px;flex-wrap:wrap">
        <span style="color:#8c8c8c;font-size:12px;white-space:nowrap">关联类型</span>
        <a-select v-model:value="addEngDocAssocType" :options="ENG_DOC_ASSOC_OPTIONS" style="width: 180px" />
        <a-checkbox v-model:checked="addEngDocBuildStructure">构建结构</a-checkbox>
        <a-checkbox v-model:checked="addEngDocBuildAttribute">构建属性</a-checkbox>
        <a-checkbox v-model:checked="addEngDocBuildRepresentation">构建表示</a-checkbox>
      </div>
      <!-- 单选：一个零件版本只能挂一份工程数据（EPMBuildRule 的基数口径，后端也会拦第二条） -->
      <div style="margin-bottom:8px;color:#8c8c8c;font-size:12px">
        选择一个工程数据（单选）—— 一个零件版本只能关联一份，需要换对象请先解除原有关系
      </div>
      <a-table
        :columns="addEngDocColumns"
        :data-source="filteredAddEngDocList"
        :loading="addEngDocLoading"
        :pagination="{ pageSize: 10, showSizeChanger: true, pageSizeOptions: ['10', '20', '50'], showTotal: (t) => `共 ${t} 条` }"
        :row-selection="{ type: 'radio', selectedRowKeys: addEngDocSelectedRowKeys, onChange: keys => (addEngDocSelectedRowKeys = keys) }"
        row-key="oid"
        size="small"
        :locale="{ emptyText: '暂无可选工程数据（先选所属容器）' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'number'">
            <code style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959">{{ record.number || '-' }}</code>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag color="cyan">{{ record.typeDefinitionCode || '-' }}</a-tag>
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

    <!--
      新增 BOM 快照：把当前版本（可能是检出中的工作副本）的顶层 BOM 行定格一份。
      快照只读 —— 建完之后 BOM 再改也不会回写它，这正是「对比」能成立的前提。
    -->
    <a-modal
      v-model:visible="snapshotModalVisible"
      title="新增 BOM 快照"
      ok-text="保存快照"
      cancel-text="取消"
      :confirm-loading="snapshotSaving"
      @ok="confirmCreateSnapshot"
      width="520px"
      centered
      :body-style="{ padding: '16px 20px 8px' }"
    >
      <div class="snapshot-tip">
        将把「{{ part?.number || '' }} {{ part?.name || '' }}」当前版本（{{ part?.displayVersion || '-' }}）的
        {{ bomList.length }} 条 BOM 行定格为一份快照。
      </div>
      <a-form layout="vertical" style="margin-top:12px">
        <a-form-item label="快照名称" required>
          <a-input v-model:value="snapshotForm.name" :maxlength="100" placeholder="如：设计基线 / 评审冻结" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea
            v-model:value="snapshotForm.remark"
            :rows="3"
            :maxlength="500"
            show-count
            placeholder="可选：这次快照的用途或背景"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!--
      快照记录 + 对比：勾选 1 条 → 与当前 BOM 对比；勾选 2 条 → 两份快照互比。
      结果复用「BOM 版本对比」的分块样式，字段名与后端差异行一致（added/removed/changed）。
    -->
    <a-modal
      v-model:visible="snapshotListVisible"
      title="BOM 快照记录"
      :footer="null"
      width="880px"
      centered
      :body-style="{ padding: '12px 16px 16px' }"
    >
      <div class="snapshot-bar">
        <span class="snapshot-bar-hint">勾选 1 条与当前 BOM 对比，勾选 2 条互相对比</span>
        <a-button
          size="small"
          type="primary"
          :loading="snapshotDiffLoading"
          :disabled="snapshotSelectedRowKeys.length !== 1"
          @click="compareSnapshotWithCurrent"
        >与当前 BOM 对比</a-button>
        <a-button
          size="small"
          :loading="snapshotDiffLoading"
          :disabled="snapshotSelectedRowKeys.length !== 2"
          @click="compareTwoSnapshots"
        >两条快照对比</a-button>
      </div>
      <a-table
        :columns="snapshotColumns"
        :data-source="snapshotList"
        :loading="snapshotListLoading"
        :row-selection="{ selectedRowKeys: snapshotSelectedRowKeys, onChange: onSnapshotSelectChange }"
        row-key="oid"
        size="small"
        :pagination="{ pageSize: 8, showSizeChanger: false, showTotal: (t) => `共 ${t} 份快照` }"
        :locale="{ emptyText: '还没有快照。点工具栏「快照 → 新增快照」把当前 BOM 存一份' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <!-- 名称过长会被省略号截断，做成链接：点开看这份快照定格的 BOM 明细 -->
            <a
              class="snapshot-name-link"
              :title="`查看快照「${record.name || ''}」的 BOM 明细`"
              @click="openSnapshotDetail(record)"
            >{{ record.name || '-' }}</a>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ fmtTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'remark'">
            <span :title="record.remark || ''">{{ record.remark || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" danger @click="onDeleteSnapshot(record)">删除</a-button>
          </template>
        </template>
      </a-table>

      <div v-if="snapshotDiff" class="bom-compare-result snapshot-diff">
        <a-divider style="margin:16px 0 12px">对比结果</a-divider>
        <div class="snapshot-diff-labels">
          <span>{{ snapshotDiff.fromLabel }}</span>
          <span>→</span>
          <span>{{ snapshotDiff.toLabel }}</span>
        </div>
        <div v-if="snapshotDiffIsEmpty" style="padding:8px 0 4px">
          <a-result status="success" title="两侧 BOM 无差异" />
        </div>
        <template v-else>
          <a-alert type="warning" show-icon style="margin-bottom:12px">
            <template #message>
              新增 <b style="color:#52c41a">{{ snapshotDiff.addedCount || 0 }}</b> 行，移除 <b style="color:#ff4d4f">{{ snapshotDiff.removedCount || 0 }}</b> 行，修改 <b style="color:#faad14">{{ snapshotDiff.changedCount || 0 }}</b> 行
            </template>
          </a-alert>
          <div class="bom-compare-detail">
            <div v-if="snapshotDiff.added?.length" class="bom-compare-block bom-compare-add">
              <div class="bom-compare-block-title">
                <span class="bom-dot bom-dot-add" /> 新增（目标侧多出来的子件）
              </div>
              <ul>
                <li v-for="(r, i) in snapshotDiff.added" :key="'sa'+i">{{ compareRowText(r) }}</li>
              </ul>
            </div>
            <div v-if="snapshotDiff.removed?.length" class="bom-compare-block bom-compare-del">
              <div class="bom-compare-block-title">
                <span class="bom-dot bom-dot-del" /> 移除（源侧有、目标侧已删除）
              </div>
              <ul>
                <li v-for="(r, i) in snapshotDiff.removed" :key="'sr'+i">{{ compareRowText(r) }}</li>
              </ul>
            </div>
            <div v-if="snapshotDiff.changed?.length" class="bom-compare-block bom-compare-chg">
              <div class="bom-compare-block-title">
                <span class="bom-dot bom-dot-chg" /> 修改（用量 / 单位 / 版本 / 单位成本变化）
              </div>
              <ul>
                <li v-for="(r, i) in snapshotDiff.changed" :key="'sc'+i">
                  {{ compareRowText(r) }}
                  <span v-if="r.from && r.from.quantity !== r.quantity" class="bom-compare-from-to">
                    （{{ r.from.quantity }}{{ r.from.unit || '' }} → {{ r.quantity }}{{ r.unit || '' }}）
                  </span>
                </li>
              </ul>
            </div>
            <div
              v-if="!snapshotDiff.added?.length && !snapshotDiff.removed?.length && !snapshotDiff.changed?.length"
              style="padding:4px 0 8px"
            >
              <a-empty description="未检测到差异明细" />
            </div>
          </div>
        </template>
      </div>
    </a-modal>

    <!--
      快照内容：快照记录里点「快照名称」打开 —— 只读展示这份快照定格的顶层 BOM 行。
      数据取单份快照接口（列表接口不带 snapshotJson，几百行明细不该跟着列表一起传）。
    -->
    <a-modal
      v-model:visible="snapshotDetailVisible"
      :title="`快照内容 · ${snapshotDetail?.name || ''}`"
      :footer="null"
      width="880px"
      centered
      :body-style="{ padding: '12px 16px 16px' }"
    >
      <a-spin :spinning="snapshotDetailLoading">
        <a-descriptions v-if="snapshotDetail" size="small" :column="2" bordered class="snapshot-detail-meta">
          <a-descriptions-item label="对应版本">{{ snapshotDetail.versionLabel || '-' }}</a-descriptions-item>
          <a-descriptions-item label="顶层子件行数">{{ snapshotDetail.itemCount ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="创建">{{ snapshotDetail.creator || '-' }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ fmtTime(snapshotDetail.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="2">{{ snapshotDetail.remark || '-' }}</a-descriptions-item>
        </a-descriptions>
        <a-table
          :columns="snapshotItemColumns"
          :data-source="snapshotDetailItems"
          size="small"
          :pagination="snapshotDetailItems.length > 10
            ? { pageSize: 10, showTotal: (t) => `共 ${t} 行` }
            : false"
          row-key="lineNumber"
          :locale="{ emptyText: '这份快照里没有 BOM 行' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'number'">
              <code class="snapshot-item-code">{{ record.number || '-' }}</code>
            </template>
            <template v-else-if="column.key === 'version'">
              <a-tag size="small">{{ record.version || '-' }}</a-tag>
            </template>
          </template>
        </a-table>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, PlusOutlined, PlusSquareOutlined, PlusCircleOutlined, DownloadOutlined, PartitionOutlined, ApartmentOutlined, ExportOutlined, ImportOutlined, InfoCircleOutlined, LockOutlined, RollbackOutlined, EyeOutlined, FilterOutlined, DownOutlined, DeleteOutlined, UploadOutlined, DollarOutlined, SwapOutlined, ClusterOutlined, UndoOutlined, ClockCircleOutlined, ArrowRightOutlined, ArrowUpOutlined, ArrowDownOutlined, SaveOutlined, DiffOutlined, ExpandOutlined, FileTextOutlined, BookOutlined, CheckOutlined, SettingOutlined, InboxOutlined, CameraOutlined, HistoryOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getEntityByCode, getPartIterations,
  getBomLinksByParentIteration, getPartUsedBy, getBomTree, exportBomFile, clearBomSubstitutes,
  createBomLinks, deleteBomLinks, updateBomLinks,
  getPartAlternateLinksByPart,
  createPartAlternateLink, deletePartAlternateLink,
  getPartDocLinks, createPartDocLink, deletePartDocLink, promotePartDocLink,
  getEngDocPartLinks, createEngDocPartLink, deleteEngDocPartLink, getEngineeringDocuments,
  listChangeNoticesByEntity,
  getDocuments, getDocument, getFolderDocumentDetails,
  getProductLine, getProductModel, getProductLines, getProductModels, getClassificationIBAs,
  getClassification,
  checkoutPart, checkinPart, undoCheckoutPart,
  getPartsByContainer, getResourceRoot, getResourceChildren,
  getBomDiff, compareBomVersions,
  getBomSubstitutesByLink, deleteBomSubstitute,
  getBomSubstituteGroups,
  createPart, getTypeDefinitionTree,
  createBomSnapshot, getBomSnapshots, getBomSnapshot, deleteBomSnapshot, compareBomSnapshots,
  getCadViewerAvailable, getCadModelByPartIteration, convertCadModel, ckFileStreamUrl,
  listIssues,
} from '@/api'
// 问题状态 / 严重度的文案与色板与「持续改进」页同源，避免两处各写一套映射
import { ISSUE_STATUS_LABEL, ISSUE_STATUS_COLOR, ISSUE_SEVERITY_LABEL, ISSUE_SEVERITY_COLOR } from '@/utils/issueFlow'
import UnitSelect from '@/components/UnitSelect.vue'
import BomSubstituteGroupModal from '@/components/bom/BomSubstituteGroupModal.vue'
import DataTable from '@/components/DataTable.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import PartMpnPanel from '@/components/PartMpnPanel.vue'
import RelatedProcesses from '@/components/RelatedProcesses.vue'
import BomCostReportModal from '@/components/BomCostReportModal.vue'
import BomSubstituteModal from '@/components/bom/BomSubstituteModal.vue'
import CadConvertProgress from '@/components/cad/CadConvertProgress.vue'
/**
 * 三维查看器懒加载：three.js 打包后近 4 MB，静态引入会让整个零件详情页先背上渲染引擎。
 * 只有真进「轻量化」页签、真有 GLB 时才去拉。
 */
const CadViewer = defineAsyncComponent(() => import('@/components/cad/CadViewer.vue'))
import DocumentViewer from './DocumentViewer.vue'
import { useCapabilities } from '@/composables/useCapabilities'
// 变更通告的状态文案 / 色板与「变更控制」页同源，避免两处各写一套映射
import { CHANGE_NOTICE_STATUS_COLOR, CHANGE_NOTICE_STATUS_LABEL } from '@/utils/changeFlow'

// 能力判定：BOM 成本报告入口是否显示（见 BOM 工具栏的 v-if="has('BOM_COST')"）。
// 能力账本由 MainLayout（导航宿主）统一加载，这里只读，不必再调 load。
const { has } = useCapabilities()
/** MPN 管理（MPN_MANAGEMENT）是否对本租户启用 —— 「MPN」页签的开关（与导航菜单、企业资源页签同源） */
const hasMpn = computed(() => has('MPN_MANAGEMENT'))

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

// ==================== BOM 快照 ====================
// 快照 = 某一时刻父件迭代顶层 BOM 行的定格（后端 ck_bom_snapshot，只读）。
// 三个动作：新增（定格当前 BOM）、记录（列表 / 删除）、对比（快照 ↔ 当前 BOM，或两份快照互比）。
const snapshotModalVisible = ref(false)
const snapshotSaving = ref(false)
const snapshotForm = ref({ name: '', remark: '' })

const snapshotListVisible = ref(false)
const snapshotListLoading = ref(false)
const snapshotList = ref([])
const snapshotSelectedRowKeys = ref([])
const snapshotDiffLoading = ref(false)
/** 对比结果：{fromLabel,toLabel,addedCount,removedCount,changedCount,added[],removed[],changed[]} */
const snapshotDiff = ref(null)

const snapshotDiffIsEmpty = computed(() => {
  const d = snapshotDiff.value
  return !!d && !(d.addedCount || d.removedCount || d.changedCount)
})

const snapshotColumns = [
  { title: '快照名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
  { title: '版本', dataIndex: 'versionLabel', key: 'versionLabel', width: 76 },
  { title: '子件行数', dataIndex: 'itemCount', key: 'itemCount', width: 90, align: 'center' },
  { title: '备注', key: 'remark', ellipsis: true },
  { title: '创建人', dataIndex: 'creator', key: 'creator', width: 100 },
  { title: '创建时间', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 70, align: 'center' },
]

// ==================== 快照内容（只读明细） ====================
// 列表接口刻意不带 snapshotJson（几百行很占带宽），点名称时才按 oid 取单份快照。
const snapshotDetailVisible = ref(false)
const snapshotDetailLoading = ref(false)
/** 快照元信息（名称/版本/行数/创建人/备注） */
const snapshotDetail = ref(null)
/** 快照里定格的 BOM 行（snapshotJson.items） */
const snapshotDetailItems = ref([])

const snapshotItemColumns = [
  { title: '行号', dataIndex: 'lineNumber', key: 'lineNumber', width: 70, align: 'center' },
  { title: '编码', key: 'number', width: 190 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '版本', key: 'version', width: 80, align: 'center' },
  { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 80, align: 'center' },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 70, align: 'center' },
  { title: '单位成本(RMB)', dataIndex: 'unitCost', key: 'unitCost', width: 120, align: 'right' },
]

/** 快照 JSON → BOM 行数组（{"items":[...]}；解析失败按空处理，不把异常抛到渲染层） */
function parseSnapshotItems(json) {
  if (!json) return []
  try {
    const parsed = typeof json === 'string' ? JSON.parse(json) : json
    const items = parsed?.items
    return Array.isArray(items) ? items : []
  } catch {
    return []
  }
}

/** 点快照名称：打开只读明细（先用列表行回填头部，明细到了再覆盖） */
async function openSnapshotDetail(record) {
  if (!record?.oid) return
  snapshotDetail.value = { ...record }
  snapshotDetailItems.value = []
  snapshotDetailVisible.value = true
  snapshotDetailLoading.value = true
  try {
    const res = await getBomSnapshot(record.oid)
    if (res?.code === 200 && res.data) {
      snapshotDetail.value = res.data
      snapshotDetailItems.value = parseSnapshotItems(res.data.snapshotJson)
    } else {
      message.error(res?.message || '读取快照内容失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '读取快照内容失败')
  } finally {
    snapshotDetailLoading.value = false
  }
}

/** 默认名称：编码 + 版本 + 快照 —— 记录一多，"未命名快照"根本分不清 */
function defaultSnapshotName() {
  const number = part.value?.number || ''
  const version = part.value?.displayVersion || ''
  return `${number} ${version} 快照`.trim()
}

function openSnapshotModal() {
  if (!currentIterationOid.value) { message.warning('当前没有可快照的 BOM 版本'); return }
  snapshotForm.value = { name: defaultSnapshotName(), remark: '' }
  snapshotModalVisible.value = true
}

async function confirmCreateSnapshot() {
  const name = (snapshotForm.value.name || '').trim()
  if (!name) { message.warning('请填写快照名称'); return }
  snapshotSaving.value = true
  try {
    const res = await createBomSnapshot({
      iterationOid: currentIterationOid.value,
      name,
      remark: (snapshotForm.value.remark || '').trim() || null,
    })
    if (res?.code === 200) {
      message.success(`快照「${name}」已保存（${res.data?.itemCount ?? 0} 行）`)
      snapshotModalVisible.value = false
      // 存完顺手把记录打开：下一步十有八九是要对比
      openSnapshotList()
    } else {
      message.error(res?.message || '保存快照失败')
    }
  } catch (e) {
    // 别只回一句"保存快照失败"：网络断 / 后端没起 / 超时都是这一句，用户没法自救
    const reason = e?.response?.data?.message || e?.message || '网络错误（确认后端已启动后重试）'
    message.error(`保存快照失败：${reason}`)
  } finally {
    snapshotSaving.value = false
  }
}

async function openSnapshotList() {
  snapshotListVisible.value = true
  snapshotDiff.value = null
  snapshotSelectedRowKeys.value = []
  await loadSnapshots()
}

async function loadSnapshots() {
  if (!part.value?.oid) { snapshotList.value = []; return }
  snapshotListLoading.value = true
  try {
    // 按零件取（跨版本）：快照记录的价值就在于"同一个件、不同时期的 BOM"
    const res = await getBomSnapshots({ partOid: part.value.oid })
    snapshotList.value = res?.code === 200 ? (res.data || []) : []
  } catch (e) {
    snapshotList.value = []
    message.error('读取快照记录失败：' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    snapshotListLoading.value = false
  }
}

/** 对比只需要两侧：超过两条时保留最后两条，并说清原因 */
function onSnapshotSelectChange(keys) {
  if (keys.length > 2) {
    message.info('对比最多只能选两条快照')
    snapshotSelectedRowKeys.value = keys.slice(-2)
  } else {
    snapshotSelectedRowKeys.value = keys
  }
  snapshotDiff.value = null
}

async function runSnapshotCompare(params) {
  snapshotDiffLoading.value = true
  snapshotDiff.value = null
  try {
    const res = await compareBomSnapshots(params)
    if (res?.code === 200) {
      snapshotDiff.value = res.data || null
      if (snapshotDiffIsEmpty.value) message.info('两侧 BOM 无差异')
    } else {
      message.error(res?.message || '快照对比失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '快照对比失败')
  } finally {
    snapshotDiffLoading.value = false
  }
}

/** 选中 1 条快照 → 与当前查看版本的 BOM 比 */
function compareSnapshotWithCurrent() {
  const oid = snapshotSelectedRowKeys.value[0]
  if (!oid) { message.warning('请选择一份快照'); return }
  if (!currentIterationOid.value) { message.warning('当前没有可对比的 BOM 版本'); return }
  runSnapshotCompare({ from: oid, toIteration: currentIterationOid.value })
}

/** 选中 2 条快照 → 互相比（先选的作为源，后选的作为目标） */
function compareTwoSnapshots() {
  const [from, to] = snapshotSelectedRowKeys.value
  if (!from || !to) { message.warning('请选择两份快照'); return }
  runSnapshotCompare({ from, toSnapshot: to })
}

function onDeleteSnapshot(record) {
  Modal.confirm({
    title: '删除快照',
    content: `确定删除快照「${record.name || '-'}」吗？删除后无法恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    centered: true,
    onOk: async () => {
      try {
        const res = await deleteBomSnapshot(record.oid)
        if (res?.code === 200) {
          message.success('快照已删除')
          snapshotSelectedRowKeys.value = snapshotSelectedRowKeys.value.filter(k => k !== record.oid)
          snapshotDiff.value = null
          await loadSnapshots()
        } else {
          message.error(res?.message || '删除失败')
        }
      } catch (e) {
        message.error(e?.response?.data?.message || '删除失败')
      }
    },
  })
}

// ==================== 关联问题（问题的「关联对象」= 本零组件） ====================
// 问题的关联对象只存「类型 + 编码」，所以按 relatedType=PART + relatedCode=零件编码 查；
// 同编码的所有版本共享同一批问题（问题是对象级的，不是版本级的）。
const relatedIssues = ref([])
const relatedIssuesLoading = ref(false)

const relatedIssueColumns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 110 },
  { title: '标题', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '类型', dataIndex: 'issueType', key: 'issueType', width: 110 },
  { title: '严重度', dataIndex: 'severity', key: 'severity', width: 80, align: 'center' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90, align: 'center' },
  { title: '责任人', dataIndex: 'owner', key: 'owner', width: 100 },
  { title: '计划完成', dataIndex: 'dueDate', key: 'dueDate', width: 110 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
]

/** 本地日期 yyyy-MM-dd：dueDate 只到天，不用 UTC 比较，免得跨时区把"今天到期"算成逾期 */
function todayYmd() {
  const now = new Date()
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${mm}-${dd}`
}

function isIssueOverdue(record) {
  if (!record?.dueDate) return false
  if (record.status === 'CLOSED' || record.status === 'CANCELLED') return false
  return String(record.dueDate).slice(0, 10) < todayYmd()
}

async function loadRelatedIssues() {
  const code = part.value?.number
  if (!code) { relatedIssues.value = []; return }
  relatedIssuesLoading.value = true
  try {
    const res = await listIssues({ relatedType: 'PART', relatedCode: code, page: 1, pageSize: 100 })
    const data = res?.code === 200 ? res.data : null
    if (Array.isArray(data?.rows)) relatedIssues.value = data.rows
    else if (Array.isArray(data)) relatedIssues.value = data
    else relatedIssues.value = []
  } catch {
    relatedIssues.value = []
  } finally {
    relatedIssuesLoading.value = false
  }
}

/** 打开问题：跳「持续改进」并直接展开该问题的详情抽屉（见 ContinuousImprovement 的 ?issue= 处理） */
function openIssue(record) {
  if (!record?.oid) return
  router.push({ path: '/improve', query: { issue: record.oid } })
}

// 切到「关联问题」页签才查（进页面时不必为一个看不见的列表多发请求）；换零件也要重查
watch([activeTab, () => part.value?.number], ([tab]) => {
  if (tab === 'issues') loadRelatedIssues()
})
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
/**
 * 往上追溯的层级栈：空 = 看本页零件；每 push 一项就往上走一层（该父件被谁使用）。
 * 栈里存的是"父件"的 oid 与展示文案，回退时只截断栈，不依赖服务端返回顺序。
 */
const usedByDrill = ref([])
/** 当前查看范围的零件 oid（栈顶；栈空时就是本页零件） */
const usedByScopeOid = computed(() => usedByDrill.value.length
  ? usedByDrill.value[usedByDrill.value.length - 1].partOid
  : oid.value)
/** 范围条第一段：本页零件（编码 名称 版本） */
const usedByRootLabel = computed(() => {
  const p = part.value || {}
  const text = [p.number, p.name, p.displayVersion].filter(Boolean).join(' ')
  return text || '当前零组件'
})

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

// ==================== 轻量化（右侧页签：选中 BOM 行的三维预览） ====================
/*
 * 数据链条：零件迭代 →（ck_eng_doc_part_link / EPMBuildRule）→ 工程数据迭代 → 主文件 → 轻量化 GLB。
 * 后端一次查询就能给全（getCadModelByPartIteration），前端只负责渲染与轮询状态，
 * 不在浏览器里串 3~4 个请求拼这条链。
 */
const bomCadLoading = ref(false)
/** 选中行的数模状态（ContainerCadModelVO 口径）；null = 没关联可轻量化的数模 */
const bomCadModel = ref(null)
/** 转换器是否可用：未部署时不给"生成"按钮，避免点出一个永远转不完的圈 */
const bomCadAvailable = ref(false)
/** 失败 / 跳过原因 */
const bomCadMessage = ref('')
const bomCadWorking = ref(false)
let bomCadPollTimer = null
let bomCadPollTicks = 0
/** 轮询上限：150 次 × 2 秒 = 5 分钟（与文档预览同一口径，转换不该让用户无限等） */
const BOM_CAD_POLL_MAX = 150

const bomCadReady = computed(() => bomCadModel.value?.status === 'SUCCESS' && !!bomCadModel.value?.glbCkfileOid)
const bomCadGlbUrl = computed(() => (bomCadReady.value ? ckFileStreamUrl(bomCadModel.value.glbCkfileOid) : ''))
const bomCadStructureUrl = computed(() =>
  bomCadModel.value?.structureCkfileOid ? ckFileStreamUrl(bomCadModel.value.structureCkfileOid) : ''
)
const bomCadStats = computed(() => ({
  triangles: bomCadModel.value?.triangleCount,
  parts: bomCadModel.value?.partCount,
  units: bomCadModel.value?.units,
}))

/** 选中 BOM 行对应的零件迭代：子件优先精确引用 → 解析缓存 → 最新；根行用当前查看版本 */
function bomRowIterationOid(row) {
  if (!row) return null
  if (row.isRoot) return currentIterationOid.value || null
  return row.childIterationOid || row.resolvedIterationOid || row.childLatestIterationOid || null
}

/** 读到状态后统一收口：转换中继续轮询，成功渲染，失败给重试入口 */
function applyBomCadModel(model) {
  bomCadModel.value = model || null
  const st = model?.status
  if (st === 'QUEUED' || st === 'RUNNING') {
    bomCadWorking.value = true
    bomCadMessage.value = ''
    if (!bomCadPollTimer) startBomCadPolling()
    return
  }
  bomCadWorking.value = false
  bomCadMessage.value = (st === 'FAILED' || st === 'SKIPPED' || st === 'TIMEOUT')
    ? (model.errorMessage || '上次转换未成功')
    : ''
}

/** 加载选中行的轻量化状态（换行 / 切到本页签 / 点刷新时调用） */
async function loadBomCad() {
  stopBomCadPolling()
  bomCadModel.value = null
  bomCadMessage.value = ''
  bomCadWorking.value = false
  const iterOid = bomRowIterationOid(bomSelectedRow.value)
  if (!iterOid) return
  bomCadLoading.value = true
  try {
    if (!bomCadAvailable.value) {
      const r = await getCadViewerAvailable()
      bomCadAvailable.value = !!(r?.code === 200 && r.data?.available)
    }
    if (!bomCadAvailable.value) return
    const res = await getCadModelByPartIteration(iterOid)
    if (res?.code === 200) applyBomCadModel(res.data)
  } catch {
    bomCadModel.value = null
  } finally {
    bomCadLoading.value = false
  }
}

/** 提交转换：提交后按"重新读状态"处理，不把 CadVisualization 的字段混进数模 VO */
async function startBomCadConvert() {
  const model = bomCadModel.value
  if (!model?.iterationOid || !model?.sourceCkfileOid) {
    message.warning('该零组件没有可转换的数模文件')
    return
  }
  bomCadWorking.value = true
  bomCadMessage.value = ''
  try {
    const res = await convertCadModel({
      engDocIterationOid: model.iterationOid,
      sourceCkfileOid: model.sourceCkfileOid,
    })
    if (res?.code !== 200) {
      bomCadWorking.value = false
      bomCadMessage.value = res?.message || '提交转换失败'
      return
    }
    const iterOid = bomRowIterationOid(bomSelectedRow.value)
    const fresh = iterOid ? await getCadModelByPartIteration(iterOid).catch(() => null) : null
    if (fresh?.code === 200 && fresh.data) applyBomCadModel(fresh.data)
    else if (!bomCadPollTimer) startBomCadPolling()
  } catch (e) {
    bomCadWorking.value = false
    bomCadMessage.value = e?.response?.data?.message || '提交转换失败，请检查转换器是否运行'
  }
}

function startBomCadPolling() {
  stopBomCadPolling()
  const iterOid = bomRowIterationOid(bomSelectedRow.value)
  if (!iterOid) return
  bomCadPollTicks = 0
  bomCadPollTimer = setInterval(async () => {
    bomCadPollTicks++
    if (bomCadPollTicks > BOM_CAD_POLL_MAX) {
      stopBomCadPolling()
      bomCadWorking.value = false
      bomCadMessage.value = '转换超时，请稍后重试'
      return
    }
    try {
      const res = await getCadModelByPartIteration(iterOid)
      if (res?.code === 200 && res.data) applyBomCadModel(res.data)
    } catch {
      /* 单次轮询失败不打断，等下一拍 */
    }
  }, 2000)
}

function stopBomCadPolling() {
  if (bomCadPollTimer) {
    clearInterval(bomCadPollTimer)
    bomCadPollTimer = null
  }
}

// 切到「轻量化」页签才去查（打开零件详情不多发请求）；切走就停轮询
watch(bomRightTab, (tab) => {
  if (tab === 'lightweight') loadBomCad()
  else stopBomCadPolling()
})
// 页签开着时换选中行：重查这一行的数模
watch(() => bomSelectedRow.value?.oid, () => {
  if (bomRightTab.value === 'lightweight') loadBomCad()
})
onUnmounted(stopBomCadPolling)

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

/**
 * 被使用情况的列：父件按「编码 / 名称 / 版本」展示。
 *
 * <p>原来这里直接显示 {@code parentIterationOid} —— 那是内部标识，用户看不出"装在哪个件上"。
 * 编码列是可点的：点它即以该父件为范围继续往上追溯一层（见 onUsedByDrillUp）。
 */
const usedByColumns = [
  { title: '编码', key: 'parentNumber', width: 190 },
  { title: '名称', key: 'parentName', ellipsis: true },
  { title: '版本', key: 'parentVersion', width: 90, align: 'center' },
  { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 90, align: 'center' },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 80, align: 'center' },
  { title: '引用方式', key: 'exactRef', width: 110, align: 'center' },
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

/** 「替代情况」页签的列定义（局部替代 = BomSubstituteVO） */
const rowSubstituteColumns = [
  { title: '替代件', key: 'part', ellipsis: true },
  { title: '版本', key: 'version', width: 70 },
  { title: '类型', key: 'type', width: 100 },
  { title: '数量', key: 'quantity', width: 80, align: 'center' },
  { title: '优先级', key: 'priority', width: 70, align: 'center' },
  { title: '状态', key: 'enabled', width: 70, align: 'center' },
  { title: '操作', key: 'action', width: 70, align: 'center' },
]

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
/**
 * 本类型有没有 CAD 描述卡片（三维模型 / 图符 / 电气图 / 架构图）。
 *
 * <p>没有就只渲染「工程数据」区块，不再给一句「该类型无需 CAD 描述」——
 * 那句话只由零件类型决定，跟工程数据有没有关联、跟业务配置里停用哪个对象类型都无关，
 * 摆在工程数据表上面只会自相矛盾。
 */
const hasCadCards = computed(() => isStructural.value || isElectronic.value || isElectrical.value || isSoftware.value)

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
      // 加载所属容器（产品系列/型号）
      loadContainer()
      // 加载分类 IBA 定义（用于显示名称映射）与分类名称（"基本信息"区展示"分类"）
      if (part.value.clsOid) {
        loadClsIbaDefs(part.value.clsOid)
        loadClsName(part.value.clsOid)
      } else {
        clsName.value = ''
      }
    }
  } catch { part.value = null }
  finally { loading.value = false }
}

/** 所属分类名称（ck_classification.name）——"基本信息"区展示用 */
const clsName = ref('')

/** 按 clsOid 解析分类名称；取不到就留空（界面显示 '-'，不显示无意义的 oid） */
async function loadClsName(clsOid) {
  try {
    const r = await getClassification(clsOid)
    const data = r?.data || r
    clsName.value = (r && r.code === 200 && data) ? (data.name || data.displayName || '') : ''
  } catch { clsName.value = '' }
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
    } else {
      productContainer.value = null
    }
  } catch {
    productContainer.value = null
  }
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

/**
 * 当前在编辑的这一层子件：选中根行/未选中 → 一级子件；选中子件 → 它的直接下层。
 *
 * <p><b>必须按 oid 从当前树里重新取节点，不能用 bomSelectedRow 里的那份引用。</b>
 * 因为重载 BOM 之后（保存、检出/检入、上移下移…）才重新定位选中行，而草稿是在
 * `bomList` 变化时重建的 —— 那一刻 bomSelectedRow 还指着<b>重载前的旧节点</b>，
 * 它的 children 是旧迭代的行。症状：保存成功、库里已是新值，界面却回到旧值
 * （"保存后又恢复成原来的数量"，用户实际报过）。按 oid 取当前树就与重载时序无关了。
 */
function bomItemsSourceNodes() {
  const selected = bomSelectedRow.value
  if (selected && !selected.isRoot) {
    const fresh = findNodeInTreeData(bomTreeData.value, selected.oid)
    return (fresh || selected).children || []
  }
  return bomList.value || []
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

/** 「只看有替代」开关：替代件的评审/变更场景需要能在这几百行里一眼定位 */
const onlySubstituted = ref(false)

/** 收集所有"有子节点"的行的 key（展开用） */
function collectExpandableKeys(nodes) {
  const keys = []
  const walk = (list) => {
    for (const n of (list || [])) {
      if (n.children && n.children.length) {
        keys.push(n.oid)
        walk(n.children)
      }
    }
  }
  walk(nodes)
  return keys
}

/** 切换「只看有替代」：打开时顺手展开全部层级，否则命中的行被折叠父级挡住，看起来像"筛没了" */
function toggleOnlySubstituted() {
  onlySubstituted.value = !onlySubstituted.value
  if (onlySubstituted.value) bomExpandedKeys.value = collectExpandableKeys(bomTreeData.value)
}

/**
 * 树数据（过滤后）：只看有替代时，保留"自身或子孙有替代"的节点。
 *
 * <p>父链必须一起保留 —— 否则命中的子件被折叠在父级里，用户根本看不到它。
 * 保留下来的内部节点做浅拷贝（只换 children），不动源节点上的字段。
 */
const bomTreeDataFiltered = computed(() => {
  if (!onlySubstituted.value) return bomTreeData.value
  const filter = (nodes) => nodes.reduce((acc, node) => {
    const kids = node.children && node.children.length ? filter(node.children) : []
    if (node.substituteCount > 0 || kids.length) {
      acc.push(kids.length ? { ...node, children: kids } : node)
    }
    return acc
  }, [])
  return filter(bomTreeData.value)
})

/** 设了替代的行数（统计区显示：让人知道这张 BOM 有多少行挂过替代） */
const substitutedRowCount = computed(() => {
  let count = 0
  const walk = (nodes) => {
    for (const n of (nodes || [])) {
      if (!n.isRoot && n.substituteCount > 0) count += 1
      if (n.children && n.children.length) walk(n.children)
    }
  }
  walk(bomTreeData.value)
  return count
})

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

/*
 * 企业资源库：作为「所属容器」的一种可选口径。
 * 根节点（CORP_RESOURCE）本身不直接挂零组件 —— 零件实际挂在元器件库 / 标准件库 / 通用件库等
 * 资源子库上（containerOid = 子库 oid），所以选中根节点时展开成各子库逐个查询再合并
 * （见 loadAddExistingParts / resolveQueryContainerOids）。
 */
const resourceRootOid = ref(null)
const resourceLibraries = ref([])

/** 容器类型 → 可读文案（仅用于容器不在候选里时的兜底显示，与 BOM 替代弹窗同一口径） */
const CONTAINER_TYPE_LABEL = {
  PRODUCT_LINE: '产品系列',
  LINE: '产品系列',
  PRODUCT_MODEL: '产品型号',
  MODEL: '产品型号',
  CORP_RESOURCE: '企业资源',
  FOLDER: '文件夹',
}

/**
 * 只放文档 / 图片、不挂零组件的资源子库 —— 选件的「所属容器」里不列它们，
 * 免得点进去永远是空清单（封装·图符库存的是 EPMDocument，技术文档库是 Document，产品图册是图片）。
 */
const NON_PART_LIBRARY_CODES = new Set(['PACKAGE_SYMBOL', 'TECH_DOC', 'MEDIA'])

/** 能挂零组件的资源子库（元器件库 / 标准件库 / 通用件库 / 其他 …） */
const partLibraries = computed(() =>
  resourceLibraries.value.filter(l => l.oid && !NON_PART_LIBRARY_CODES.has(String(l.code || '').toUpperCase())),
)

/**
 * 「添加已有零组件」弹窗的容器树：
 *   企业资源库（全部库）→ 元器件库 / 标准件库 / 通用件库 …
 *   产品系列 / 型号（保持扁平可选）
 * 选父节点 = 汇总下挂子库；选子库 = 只看该库。
 */
const addExistingContainerTree = computed(() => {
  const nodes = []
  if (resourceRootOid.value) {
    nodes.push({
      value: resourceRootOid.value,
      title: '企业资源库（全部库）',
      children: partLibraries.value.map(l => ({ value: l.oid, title: l.name || l.code })),
    })
  }
  containerOptions.value.forEach(o => nodes.push({ value: o.value, title: o.label }))
  // 当前容器的 oid 不在候选里（零件本身就在资源子库 / 文件夹里）时补一条兜底项：
  // 否则下拉找不到对应节点，会把 containerOid 原样当文本显示
  const cur = addExistingContainerOid.value
  const hasCur = nodes.some(n => n.value === cur || (n.children || []).some(c => c.value === cur))
  if (cur && !hasCur) {
    nodes.unshift({ value: cur, title: containerLabelOf(cur, part.value?.containerType) })
  }
  return nodes
})

/** 容器文案：命中资源子库取库名，其余按容器类型给可读兜底（不暴露 oid） */
function containerLabelOf(containerOid, containerType) {
  const lib = resourceLibraries.value.find(l => l.oid === containerOid)
  if (lib) return lib.name || lib.code
  const label = CONTAINER_TYPE_LABEL[String(containerType || '').toUpperCase()]
  return label ? `${label}（当前容器）` : '当前容器'
}

/** 加载产品系列 + 型号 + 企业资源库（用于下拉选项） */
async function loadContainerOptions() {
  try {
    const [lineRes, modelRes, rootRes, libRes] = await Promise.all([
      getProductLines().catch(() => ({ data: [] })),
      getProductModels().catch(() => ({ data: [] })),
      // 资源库未初始化 / 无权限时按空态处理，不影响产品系列/型号的选项
      getResourceRoot().catch(() => null),
      getResourceChildren().catch(() => ({ data: [] })),
    ])
    productLinesForSelect.value = lineRes?.data || lineRes || []
    productModelsForSelect.value = modelRes?.data || modelRes || []
    resourceRootOid.value = rootRes?.data?.oid || null
    const libs = libRes?.data
    resourceLibraries.value = Array.isArray(libs) ? libs : []
  } catch {
    productLinesForSelect.value = []
    productModelsForSelect.value = []
    resourceRootOid.value = null
    resourceLibraries.value = []
  }
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

/** 容器（产品系列/型号/企业资源库）切换 */
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
    const keyword = addExistingKeyword.value.trim() || undefined
    const oids = await resolveQueryContainerOids(containerOid)
    const results = await Promise.all(
      oids.map(oid => getPartsByContainer(oid, keyword).catch(() => ({ data: [] }))),
    )
    // 多容器合并（企业资源库）：按 oid 去重，保持各子库返回顺序
    const merged = []
    const seen = new Set()
    results.forEach(res => {
      const arr = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []
      arr.forEach(p => {
        if (p?.oid && !seen.has(p.oid)) {
          seen.add(p.oid)
          merged.push(p)
        }
      })
    })
    // 过滤掉选中节点自身及其所有祖先（防止自引用 / 死循环）
    const forbidden = collectForbiddenPartOids()
    addExistingList.value = merged.filter(p => !forbidden.has(p.oid))
  } catch { addExistingList.value = [] }
  finally { addExistingLoading.value = false }
}

/**
 * 实际要查询的容器 oid 列表。
 *
 * <p>普通容器（产品系列/型号/资源子库）→ 只查它自己；
 * 选「企业资源库」根节点 → 展开成各资源子库：零件挂在子库上，按根 oid 精确查只会得到空清单。
 */
async function resolveQueryContainerOids(containerOid) {
  if (containerOid !== resourceRootOid.value) return [containerOid]
  if (!resourceLibraries.value.length) {
    try {
      const res = await getResourceChildren()
      resourceLibraries.value = Array.isArray(res?.data) ? res.data : []
    } catch { return [] }
  }
  return partLibraries.value.map(l => l.oid).filter(Boolean)
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

// ==================== 新建零组件作为子件 ====================
// 与「物料清单」页签的「新建零组件」是同一套创建表单：先选类型 → 按类型专属布局填表。
// 差别只有两点：① 容器固定为当前 Part 的容器（否则建出来的件在该产品的物料清单里查不到）；
// ② 建完立刻用 createBomLinks 挂到选中的 BOM 节点下，不用再走一遍「添加已有子件」。
const newChildModalVisible = ref(false)
const newChildSaving = ref(false)
const newChildFormRef = ref(null)
const newChildForm = ref({})
const newChildEntityCode = ref('PART')
const newChildTypeSelectValue = ref(undefined)
const newChildSelectedType = ref(null)
const newChildTypeOptions = ref([])
/** 本次新建的目标父件 { iterationOid, name }：打开弹窗时定下来，提交时用 */
const newChildTarget = ref(null)

/** 产品线/型号内禁止创建的类型（电子元器件必须走「企业资源 → 元器件库 → IPN申请」） */
const FORBIDDEN_PART_TYPE_CODES = ['ELECTRONIC']

/** 递归剔除禁止选择的类型节点（连同其子树）—— 与物料清单同一个口径 */
function filterForbiddenPartTypes(node) {
  if (!node) return null
  if (FORBIDDEN_PART_TYPE_CODES.includes((node.code || '').toUpperCase())) return null
  return {
    ...node,
    children: (node.children || []).map(filterForbiddenPartTypes).filter(Boolean),
  }
}

/** 类型树里定位 PART 根节点（其下挂全部零件软类型） */
function findRootPartTypeNode(nodes) {
  for (const n of nodes) {
    if (n.code === 'PART') return n
    if (n.children) {
      const found = findRootPartTypeNode(n.children)
      if (found) return found
    }
  }
  return null
}

function findTypeNodeByCode(nodes, code) {
  for (const n of nodes) {
    if (n.code === code) return n
    if (n.children) {
      const found = findTypeNodeByCode(n.children, code)
      if (found) return found
    }
  }
  return null
}

async function loadNewChildPartTypes() {
  try {
    const res = await getTypeDefinitionTree()
    const tree = res?.data || res || []
    const partNode = findRootPartTypeNode(Array.isArray(tree) ? tree : [])
    const filtered = filterForbiddenPartTypes(partNode)
    newChildTypeOptions.value = filtered ? [filtered] : []
  } catch { newChildTypeOptions.value = [] }
}

function onNewChildTypeChange(typeCode) {
  if (typeCode) {
    const found = findTypeNodeByCode(newChildTypeOptions.value, typeCode)
    newChildSelectedType.value = found || { code: typeCode, name: typeCode }
    newChildEntityCode.value = typeCode
    newChildForm.value.typeDefinitionCode = typeCode
  } else {
    newChildSelectedType.value = null
    newChildEntityCode.value = 'PART'
    newChildForm.value.typeDefinitionCode = ''
  }
}

function resetNewChildModal() {
  newChildTypeSelectValue.value = undefined
  newChildSelectedType.value = null
  newChildEntityCode.value = 'PART'
  newChildForm.value = {}
  newChildTarget.value = null
}

/** BOM 操作：新建零组件并作为子件挂到选中的节点下 */
async function onNewChild() {
  if (!isLatestVersion.value) { message.warning(readonlyTip); return }
  const target = resolveBomTargetParent()
  if (!target) {
    message.warning('请先选择左侧 BOM 节点作为新建子件的父件')
    return
  }
  // 默认将选中的行检出，检出失败则中止
  const ok = await ensureTargetCheckedOut()
  if (!ok) return
  resetNewChildModal()
  newChildTarget.value = target
  // 与物料清单同一套预设：名称/类型在弹窗里填，容器跟着当前 Part
  newChildForm.value = {
    name: '',
    typeDefinitionCode: '',
    description: '',
    containerOid: part.value?.containerOid || '',
    containerType: part.value?.containerType || '',
    clsOid: '',
    ckfileOid: '',
    attachmentOid: '',
  }
  loadNewChildPartTypes()
  newChildModalVisible.value = true
}

async function confirmNewChild() {
  if (!newChildSelectedType.value) { message.warning('请选择零组件类型'); return }
  if (!newChildForm.value.name?.trim()) { message.warning('请输入零组件名称'); return }
  // 目标父件的迭代必须重新取：打开弹窗时的「自动检出」会新建工作副本迭代并把 BOM 行整份复制过去，
  // 沿用打开时抓的旧 iterationOid 会把新子件挂到历史版本上（列表刷新后看不到）
  const target = resolveBomTargetParent() || newChildTarget.value
  if (!target?.iterationOid) { message.warning('目标父件没有可用迭代，无法添加子件'); return }
  newChildSaving.value = true
  try {
    const payload = { ...newChildForm.value }
    // 分类 IBA 单独提交到分类节点（与物料清单的创建逻辑一致）
    if (newChildFormRef.value) {
      const clsIbaFieldNames = newChildFormRef.value.getClsIbaFieldNames() || []
      clsIbaFieldNames.forEach(k => delete payload[k])
      const clsIbaValues = newChildFormRef.value.getClsIbaValues() || {}
      if (Object.keys(clsIbaValues).length > 0) payload.clsIbaValues = clsIbaValues
    }
    payload.typeDefinitionCode = payload.typeDefinitionCode || newChildSelectedType.value.code
    // 容器必须落在当前 Part 所在的产品上，否则这颗料不会出现在该产品的物料清单里
    if (!payload.containerOid) payload.containerOid = part.value?.containerOid || null
    if (!payload.containerType) payload.containerType = part.value?.containerType || null
    // 外键字段空字符串转 null，避免外键约束失败
    ;['clsOid', 'stageOid', 'folderOid', 'containerOid', 'ckfileOid', 'attachmentOid'].forEach(k => {
      if (payload[k] != null && String(payload[k]).trim() === '') payload[k] = null
    })

    const created = await createPart(payload)
    if (created?.code !== 200) {
      message.error(created?.message || '创建零组件失败')
      return
    }
    const child = created.data || {}
    if (!child.oid) {
      message.error('零组件已创建，但接口未返回对象标识，无法加入 BOM')
      return
    }
    const label = child.number || child.name || payload.name

    const linkRes = await createBomLinks({
      parentIterationOid: target.iterationOid,
      childPartOid: child.oid,
      quantity: 1,
      unit: 'ea',
    })
    if (linkRes?.code !== 200) {
      // 零件已经建出来了：别让用户以为整件事失败又建一颗，提示改用「添加已有子件」挂上
      newChildModalVisible.value = false
      resetNewChildModal()
      message.warning(`零组件 ${label} 已创建，但加入 BOM 失败：${linkRes?.message || '未知错误'}；可用「添加已有子件」挂到当前节点`)
      return
    }

    // 目标父件是子件行时先把它展开，否则新建的孙件被折叠在下面，看起来像没加进去
    const parentRowOid = bomSelectedRow.value && !bomSelectedRow.value.isRoot ? bomSelectedRow.value.oid : null
    newChildModalVisible.value = false
    resetNewChildModal()
    await loadBom()
    if (parentRowOid && !bomExpandedKeys.value.includes(parentRowOid)) {
      bomExpandedKeys.value = [...bomExpandedKeys.value, parentRowOid]
    }
    message.success(`已创建零组件 ${label} 并添加为「${target.name}」的子件`)
  } catch (e) {
    message.error(e?.response?.data?.message || '新建零组件失败')
  } finally {
    newChildSaving.value = false
  }
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

/** 是否有生效中的过滤条件（目前只有「替代关系」一个，后续条件往这里加） */
const hasBomFilter = computed(() => onlySubstituted.value)

/** 过滤条件摘要：出现在「当前过滤器」与过滤器按钮的 tooltip 上 —— 折叠起来的条件最容易被忘掉 */
const bomFilterSummary = computed(() => (onlySubstituted.value
  ? `只看有替代的行（${substitutedRowCount.value} 行）`
  : '无'))

/** 下拉菜单里"已勾选"的条件（菜单高亮用） */
const bomFilterSelectedKeys = computed(() => (onlySubstituted.value ? ['substituteOnly'] : []))

/** 编辑过滤器弹窗：草稿与当前状态分开，点「应用」才生效 */
const filterModalOpen = ref(false)
const filterDraft = ref({ substituteOnly: false })

function openFilterEdit() {
  filterDraft.value = { substituteOnly: onlySubstituted.value }
  filterModalOpen.value = true
}

function applyBomFilterEdit() {
  const next = !!filterDraft.value.substituteOnly
  filterModalOpen.value = false
  if (next === onlySubstituted.value) return
  toggleOnlySubstituted()
  message.success(next
    ? `已启用过滤：只看有替代的行（${substitutedRowCount.value} 行）`
    : '已取消「只看有替代的行」')
}

/** 过滤器操作：替代关系是这套"过滤器"体系的第一个条件 */
function onFilterAction({ key }) {
  if (key === 'substituteOnly') {
    toggleOnlySubstituted()
    message.success(onlySubstituted.value
      ? `已启用过滤：只看有替代的行（${substitutedRowCount.value} 行）`
      : '已取消「只看有替代的行」')
    return
  }
  if (key === 'edit') {
    openFilterEdit()
    return
  }
  if (key === 'clear') {
    if (!hasBomFilter.value) return
    onlySubstituted.value = false
    message.info('已清除过滤器')
  }
}

/** 导出中的转圈状态（大 BOM 生成要几秒，没反馈用户会重复点） */
const bomExporting = ref(false)

/**
 * BOM 报告：导出（csv / xls / xlsx / pdf）。
 *
 * <p>口径＝<b>当前查看的那一版</b>（工具栏版本选择器决定），与左侧 BOM 结构、成本报告同一版 ——
 * 导出跟着版本走，不自行挑"最新版"，否则会出现"页面看的是 A.4、导出的是 B.1"的错配。
 *
 * <p>失败时后端返回的是 JSON（被包在 blob 里）：把它读出来给用户一句能懂的话，
 * 而不是干巴巴的"服务器错误"。
 */
async function onExportBom({ key }) {
  const iterOid = currentIterationOid.value
  if (!iterOid) {
    message.warning('尚未确定当前版本，无法导出')
    return
  }
  const format = key || 'xlsx'
  bomExporting.value = true
  try {
    const blob = await exportBomFile(iterOid, format)
    saveBlob(blob, exportFileName(format))
    message.success(`BOM 已导出（${format.toUpperCase()}）`)
  } catch (e) {
    const detail = await errorMessageOf(e)
    message.error(detail || 'BOM 导出失败，请稍后重试')
  } finally {
    bomExporting.value = false
  }
}

/** 文件名与后端同构：编码-版本-BOM-时间戳.扩展名（后端也在响应头里给了一份） */
function exportFileName(format) {
  const iter = (historyList.value || []).find(h => h.oid === currentIterationOid.value) || {}
  const code = part.value?.number || 'BOM'
  const version = iter.displayVersion || ''
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
    + `-${pad(now.getHours())}${pad(now.getMinutes())}`
  return [code, version, `BOM-${stamp}`].filter(Boolean).join('-') + `.${format}`
}

/** 触发浏览器下载（用完即回收 objectURL，否则大文件会一直占着内存） */
function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 从失败响应里取一句可读的话（blob 响应要先把 JSON 读出来） */
async function errorMessageOf(error) {
  const data = error?.response?.data
  if (data instanceof Blob) {
    try {
      return JSON.parse(await data.text())?.message || null
    } catch {
      return null
    }
  }
  return data?.message || null
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

/** 局部替代弹框开关 */
const substituteModalOpen = ref(false)

/**
 * 局部替代：给选中的 BOM 行设置替代件（仅该行生效）。
 *
 * <p>入口参数是<b>选中行的 oid</b>（BOM 行 oid），不是零件 oid —— 局部替代挂在 BOM 行上，
 * 同一颗料装在别处能不能被替换是另一回事（那是「全局替代」的口径，见页签上的「添加替代件」）。
 */
function onSubstitute() {
  const row = bomSelectedRow.value
  if (!row || row.isRoot) return
  // 先把容器下拉的选项加载好：弹窗里那个「所属容器」是 a-select，值只有落在
  // options 里才显示成名称；选项为空时它只能把 containerOid 原样回显（用户看到的是一个 oid）。
  // 另外三个「选零件/选文档」弹窗打开时都做了这一步，这里保持一致。
  loadContainerOptions()
  substituteModalOpen.value = true
}

// ==================== 局部替代的可视化（行内标记 + 「替代情况」页签） ====================
//
// 数据源只有一处：ck_bom_substitute_link（挂在 BOM 行上，只在该行生效）。
// 树上每行的数量由后端 buildTree 一次性批量回填（substituteCount / substituteEnabledCount）；
// 明细按需拉取 —— hover 行内标记、或选中行切到「替代情况」页签时才请求。

/** 行内标记的工具提示缓存：{ [bomLinkOid]: { loading, list } }
 *  用 oid 作键而不是挂在行对象上：「只看有替代」会把树浅拷贝一份，
 *  挂在行上的缓存被拷贝后就与源对象脱钩（改了源、拷贝里还是旧的） */
const subTipCache = ref({})
const EMPTY_SUB_TIP = { loading: false, list: [] }

/** 取某行的工具提示数据（取不到给空对象，模板里不必再判空） */
function subTipOf(record) {
  return subTipCache.value[record?.oid] || EMPTY_SUB_TIP
}

function setSubTip(oid, value) {
  subTipCache.value = { ...subTipCache.value, [oid]: value }
}

/** 清掉某行的缓存（替代增删后明细已过期） */
function invalidateSubTip(oid) {
  if (!subTipCache.value[oid]) return
  const next = { ...subTipCache.value }
  delete next[oid]
  subTipCache.value = next
}

/** 悬停行内标记时加载明细（每行预加载会给整棵树加 N 次请求，所以只在 hover 时拉） */
async function loadSubTip(record) {
  const oid = record?.oid
  if (!oid || subTipCache.value[oid]) return
  setSubTip(oid, { loading: true, list: [] })
  try {
    const res = await getBomSubstitutesByLink(oid)
    setSubTip(oid, { loading: false, list: res?.data || [] })
  } catch {
    setSubTip(oid, { loading: false, list: [] })
  }
}

/** 「替代情况」页签：选中行的替代件清单 */
const rowSubstitutes = ref([])
const rowSubsLoading = ref(false)
const rowSubsBusy = ref(false)
const rowSubstituteEnabledCount = computed(() => rowSubstitutes.value.filter(s => s.enabled !== false).length)

/** 加载选中行的替代件 */
async function loadRowSubstitutes() {
  const row = bomSelectedRow.value
  if (!row || row.isRoot || !row.oid) {
    rowSubstitutes.value = []
    return
  }
  rowSubsLoading.value = true
  try {
    const res = await getBomSubstitutesByLink(row.oid)
    rowSubstitutes.value = res?.data || []
  } catch {
    rowSubstitutes.value = []
  } finally {
    rowSubsLoading.value = false
  }
}

/**
 * 把某行的替代数量回填到树上（增删替代后不必整棵树重载，交互不闪）。
 *
 * <p>同时更新"选中行"那一份：开了「只看有替代」时，选中行可能是过滤拷贝出来的对象。
 */
function patchSubstituteCount(bomLinkOid, total, enabled) {
  // 注意：一定要改 bomList（源树），不能改 bomTreeData.value ——
  // 后者是 computed，每次产出的是 {...rest} 拷出来的行对象；改副本既不触发重渲染
  // （它不在依赖里），下一次重算还会被覆盖。真的踩过：改完替代件，行内标记不动。
  const node = findNodeInTreeData(bomList.value, bomLinkOid)
  if (node && !node.isRoot) {
    node.substituteCount = total
    node.substituteEnabledCount = enabled
  }
  if (bomSelectedRow.value && bomSelectedRow.value.oid === bomLinkOid) {
    bomSelectedRow.value.substituteCount = total
    bomSelectedRow.value.substituteEnabledCount = enabled
  }
  invalidateSubTip(bomLinkOid)
}

/** 重新拉当前行的替代件并回填数量（弹窗变动 / 页签删除 / 清空 三个入口共用） */
async function refreshRowSubstitutes() {
  const row = bomSelectedRow.value
  await loadRowSubstitutes()
  if (row && !row.isRoot) {
    patchSubstituteCount(row.oid, rowSubstitutes.value.length, rowSubstituteEnabledCount.value)
  }
}

/** 点行内标记：选中该行并切到「替代情况」页签（浏览不打断 —— 不直接弹编辑框） */
function onClickSubBadge(record) {
  bomSelectedRow.value = record
  bomRightTab.value = 'substitute'
  loadRowSubstitutes()
}

/** 页签里的「设置替代」：复用同一个弹窗（它自己会刷新清单并 emit changed） */
function onOpenSubstituteFromTab() {
  onSubstitute()
}

/** 页签里删除一条替代 */
function onDeleteRowSubstitute(record) {
  Modal.confirm({
    title: `删除替代件「${record.substitutePartName || record.substitutePartNumber}」？`,
    content: '只影响本 BOM 行；零件主数据上的全局替代关系不受影响。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      rowSubsBusy.value = true
      try {
        const res = await deleteBomSubstitute(record.oid)
        if (res?.code === 200) {
          message.success('已删除')
          await refreshRowSubstitutes()
        } else {
          message.error(res?.message || '删除失败')
        }
      } catch (e) {
        message.error(e?.response?.data?.message || e.message)
      } finally {
        rowSubsBusy.value = false
      }
    },
  })
}

/** 页签里清空本行全部替代（与工具栏「取消替代」同一动作，入口不同） */
function onClearRowSubstitutes() {
  onCancelSubstitute()
}

/** 跳到替代件的零件页 */
function onGotoSubstitutePart(record) {
  if (!record?.substitutePartOid) return
  router.push({ name: 'PartDetail', params: { oid: record.substitutePartOid } })
}

/** 替代关系变动后的钩子（弹窗里增/删/清空都会触发）：刷新页签清单 + 回填行内标记 */
function onSubstituteChanged() {
  refreshRowSubstitutes()
}

// 选中行变化、或切到「替代情况」页签时，加载该行的替代件
watch([() => bomSelectedRow.value?.oid, bomRightTab], ([, tab]) => {
  if (tab === 'substitute') loadRowSubstitutes()
})

// ==================== 成组替代（一组行 ↔ 一组物料） ====================
//
// 与局部替代的分工：局部替代换"一颗料"、挂在一条 BOM 行上；成组替代换"一组行"、挂在父件迭代上，
// 带"整组替换"约束。所以行内是两个标记：⇄ 是局部替代、⧉ 是成组替代。

/** 成组替代弹窗开关 */
const substituteGroupModalOpen = ref(false)

/** 本版全部成组替代组（行内标记的 tooltip 与"改完同步计数"共用；按迭代缓存） */
const groupCache = ref({ iterationOid: '', loading: false, groups: [] })

/**
 * 成组替代弹窗的「原料侧」候选：<b>选中行的下挂</b>（与右侧「子件清单」同一口径）。
 *
 * <p>口径必须与选中行一致：成组替代说的是"这一层里哪几行被整组换掉"
 * （典型场景就是同一父件下的几个分立元件换成一颗集成模块）。早先这里把整棵树拍平，
 * 结果把选中行自己和更深层的行混进同一张候选表，用户看到的是"平级"的假象。
 */
const bomSourceOptions = computed(() => bomItemsSourceNodes().map((b) => ({
  oid: b.oid,
  lineNumber: b.lineNumber,
  // 树的节点与原始节点字段名不同（前者已带 childName/childNumber），两种都认
  partNumber: b.childNumber || b.childPartNumber || '-',
  partName: b.childName || b.childPartName || '-',
  // 子件主对象 oid：弹窗用它把"已选作原料侧"的物料从替代侧候选里剔掉
  partOid: b.childPartOid,
  quantity: b.quantity,
  unit: b.unit,
  // 这一行"属于哪一版"：成组替代的组必须挂在原料侧行所属的迭代上，
  // 否则后端会以「原料侧的 BOM 行不属于当前版本」拒掉（选了子件行时它与当前迭代不同）
  parentIterationOid: b.parentIterationOid,
})))

/**
 * 成组替代弹窗的挂载迭代 = <b>原料侧行所属的那一版</b>。
 *
 * <p>不能直接用 {@code currentIterationOid}：候选是"选中行的下挂"，选了子件行时它的下挂
 * 属于<b>那个子件的迭代</b>，与当前页面的迭代不是同一个 —— 组挂错版本，后端必拒
 * （用户报的「原料侧的 BOM 行不属于当前版本」就是这么来的）。
 * 一行都没有时退回当前迭代（按钮此时是禁用的，取什么都不会用到）。
 */
const groupParentIterationOid = computed(() => {
  const first = bomSourceOptions.value[0]
  return (first && first.parentIterationOid) || currentIterationOid.value || ''
})

/**
 * 打开成组替代弹窗。
 *
 * <p>原料侧 = 选中行的下挂，所以必须先有选中行（根行也吃：那就是一级子件）。
 * 容器下拉的选项要先备好，否则它只能回显 oid（局部替代那次踩过）。
 */
function onGroupSubstitute() {
  if (!bomSelectedRow.value) {
    message.warning('请先在左侧选中一条 BOM 行 —— 成组替代的原料侧取自它的下挂')
    return
  }
  loadContainerOptions()
  substituteGroupModalOpen.value = true
}

/** 点行内 ⧉ 标记：同样打开弹窗（不改变选中行 —— 组不依附某一行） */
function onClickGroupBadge() {
  onGroupSubstitute()
}

/** 悬停 ⧉ 时才拉本版全部组（一次拉完，之后命中缓存） */
function loadGroupTip() {
  loadSubstituteGroups()
}

/** 本行被哪些组引用（tooltip 明细） */
function groupsOf(record) {
  const oid = record?.oid
  if (!oid) return []
  return (groupCache.value.groups || []).filter(
    (g) => (g.sources || []).some((m) => m.bomLinkOid === oid),
  )
}

/** 拉取本版成组替代（force=false 时命中"按迭代"的缓存） */
async function loadSubstituteGroups(force = false) {
  const iterOid = currentIterationOid.value
  if (!iterOid) return []
  if (!force && groupCache.value.iterationOid === iterOid) return groupCache.value.groups
  // 加载中保留上一批 groups：清空会让 tooltip 先闪一下"暂无明细"，
  // 更糟的是与行内计数互相打架（本行计数是后端给的，前端只是在改完组后回填）
  groupCache.value = { iterationOid: iterOid, loading: true, groups: groupCache.value.groups || [] }
  try {
    const res = await getBomSubstituteGroups(iterOid)
    const list = res?.code === 200 ? res.data || [] : []
    groupCache.value = { iterationOid: iterOid, loading: false, groups: list }
  } catch {
    groupCache.value = { iterationOid: iterOid, loading: false, groups: [] }
  }
  applySubstituteGroupCounts(groupCache.value.groups)
  return groupCache.value.groups
}

/**
 * 把"每行被几组引用"回填到树上（改完组不必整树重载，交互不闪）。
 *
 * <p>计数口径 = 该行作为<b>原料侧</b>成员出现在几个组里，与后端树接口的
 * {@code substituteGroupCount} 保持一致。
 */
function applySubstituteGroupCounts(groups) {
  const counts = {}
  for (const g of (groups || [])) {
    for (const m of (g.sources || [])) {
      if (m.bomLinkOid) counts[m.bomLinkOid] = (counts[m.bomLinkOid] || 0) + 1
    }
  }
  const walk = (nodes) => {
    for (const n of (nodes || [])) {
      // 只回填"属于本版"的行：跨层的行（parentIterationOid 指向子件的迭代）不在这批组的作用域里，
      // 拿本版的组去算它只会把后端给的计数抹成 0 —— 症状就是鼠标移上去、行内 ⧉ 标记凭空消失
      // （tooltip 的锚点没了，浮层也跟着关）。
      if (!n.parentIterationOid || n.parentIterationOid === groupCache.value.iterationOid) {
        const next = counts[n.oid] || 0
        if (n.substituteGroupCount !== next) n.substituteGroupCount = next
      }
      if (n.children && n.children.length) walk(n.children)
    }
  }
  // 同 patchSubstituteCount：改源树（bomList），不是 computed 产出的副本，否则界面不会更新
  walk(bomList.value)
  const sel = bomSelectedRow.value
  if (sel && !sel.isRoot && (!sel.parentIterationOid || sel.parentIterationOid === groupCache.value.iterationOid)) {
    sel.substituteGroupCount = counts[sel.oid] || 0
  }
}

/** 弹窗里增/改/删之后：刷新缓存与行内计数（弹窗自己维护列表） */
function onSubstituteGroupChanged(groups) {
  groupCache.value = { iterationOid: currentIterationOid.value, loading: false, groups: groups || [] }
  applySubstituteGroupCounts(groupCache.value.groups)
}

// 换版本（切历史版本 / 检出）后缓存失效：旧版本的组不能拿去解释新版本的树
watch(currentIterationOid, () => {
  groupCache.value = { iterationOid: '', loading: false, groups: [] }
})

/**
 * 取消替代：清空选中 BOM 行的全部<b>局部</b>替代。
 *
 * <p>两个名字很像的按钮作用域完全不同（这个只删本行的局部替代，不动零件主数据上的全局替代），
 * 所以确认框里把边界写明 —— 用户分不清时最容易误删的就是这一类。
 */
function onCancelSubstitute() {
  const row = bomSelectedRow.value
  if (!row || row.isRoot) return
  Modal.confirm({
    title: `清空「${row.childName || row.childNumber}」这一行的全部替代件？`,
    content: '只影响本 BOM 行的局部替代；零件主数据上的全局替代关系不受影响。',
    okText: '清空',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await clearBomSubstitutes(row.oid)
        if (res?.code === 200) {
          message.success(`已清空 ${res.data ?? 0} 条替代关系`)
          // 两处显示同步归零：行内标记（substituteCount）+「替代情况」页签清单
          await refreshRowSubstitutes()
        } else {
          message.error(res?.message || '取消失败')
        }
      } catch (e) {
        message.error(e?.response?.data?.message || e.message)
      }
    },
  })
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

/**
 * 加载「被使用情况」：按当前查看范围（默认本页零件，往上追溯后是栈顶那个父件）。
 * 返回的每个父件都带 parentPartOid，点一下就是再往上走一层。
 */
async function loadUsedBy() {
  const scopeOid = usedByScopeOid.value
  if (!scopeOid) return
  usedByLoading.value = true
  try {
    const res = await getPartUsedBy(scopeOid)
    usedByList.value = pickList(res)
  } catch { usedByList.value = [] }
  finally { usedByLoading.value = false }
}

/** 点到某个父件：以它为范围，继续看它被谁使用（往上一层） */
function onUsedByDrillUp(record) {
  if (!record?.parentPartOid) {
    message.warning('该父件已不存在（可能已被删除），无法继续向上追溯')
    return
  }
  usedByDrill.value.push({
    partOid: record.parentPartOid,
    label: [record.parentNumber, record.parentName, record.parentVersion].filter(Boolean).join(' ') || '上层零组件',
  })
  loadUsedBy()
}

/** 范围条回退：idx = -1 回到本页零件，否则回到第 idx 层父件 */
function onUsedByDrillTo(idx) {
  usedByDrill.value = idx < 0 ? [] : usedByDrill.value.slice(0, idx + 1)
  loadUsedBy()
}

/** 返回上一层 */
function onUsedByBack() {
  usedByDrill.value = usedByDrill.value.slice(0, -1)
  loadUsedBy()
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

// ==================== 关联工程数据（零件版本 ↔ 工程数据版本，EPMBuildRule） ====================

/** 关联类型文案（OWNER / CONTENT / CONTRIBUTING_IMAGE / IMAGE，对齐 Windchill EPMBuildRule） */
const ENG_DOC_ASSOC_LABEL = {
  OWNER: '主数据',
  CONTENT: '内容',
  CONTRIBUTING_IMAGE: '贡献图纸',
  IMAGE: '附图',
}
const ENG_DOC_ASSOC_COLOR = {
  OWNER: 'red',
  CONTENT: 'blue',
  CONTRIBUTING_IMAGE: 'orange',
  IMAGE: 'default',
}
const ENG_DOC_ASSOC_OPTIONS = Object.entries(ENG_DOC_ASSOC_LABEL).map(([value, label]) => ({ value, label }))

const engDocLinkColumns = [
  { title: '工程数据', key: 'engDoc', ellipsis: true },
  { title: '类型', key: 'engDocType', width: 120 },
  { title: '版本', key: 'engDocVersion', width: 130 },
  { title: '关联类型', key: 'assocType', width: 110 },
  { title: '构建规则', key: 'buildRules', width: 170 },
  { title: '关联时间', key: 'createdAt', width: 160 },
]

const engDocLinks = ref([])
const engDocLoading = ref(false)
const engDocSelectedKeys = ref([])

/** 构建规则是否勾选了任意一项（用于表格里显示「—」占位） */
function hasBuildRule(record) {
  return !!(record?.buildStructure || record?.buildAttribute || record?.buildRepresentation)
}

/** 加载当前零件版本的工程数据关联（后端已补齐零件/工程数据的编号、名称、版本） */
async function loadEngDocLinks() {
  if (!oid.value) return
  engDocLoading.value = true
  try {
    const res = await getEngDocPartLinks({ partOid: oid.value })
    engDocLinks.value = pickList(res)
  } catch {
    engDocLinks.value = []
  } finally {
    engDocLoading.value = false
  }
}

// ---- 关联工程数据弹窗 ----
const addEngDocModalVisible = ref(false)
const addEngDocLoading = ref(false)
const addEngDocSaving = ref(false)
const addEngDocList = ref([])
const addEngDocKeyword = ref('')
const addEngDocSelectedRowKeys = ref([])
const addEngDocContainerOid = ref(null)
const addEngDocAssocType = ref('CONTENT')
const addEngDocBuildStructure = ref(false)
const addEngDocBuildAttribute = ref(false)
const addEngDocBuildRepresentation = ref(false)

const addEngDocColumns = [
  { title: '编号', key: 'number', width: 160 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '类型', key: 'type', width: 130 },
]

/** 按关键词本地过滤（名称/编号），避免依赖后端关键字参数 */
const filteredAddEngDocList = computed(() => {
  const kw = addEngDocKeyword.value.trim().toLowerCase()
  if (!kw) return addEngDocList.value
  return addEngDocList.value.filter(d =>
    (d.name || '').toLowerCase().includes(kw) || (d.number || '').toLowerCase().includes(kw)
  )
})

function onAddEngDocLink() {
  if (!oid.value) { message.warning('缺少当前部件信息'); return }
  addEngDocModalVisible.value = true
  addEngDocKeyword.value = ''
  addEngDocSelectedRowKeys.value = []
  addEngDocAssocType.value = 'CONTENT'
  addEngDocBuildStructure.value = false
  addEngDocBuildAttribute.value = false
  addEngDocBuildRepresentation.value = false
  addEngDocContainerOid.value = part.value?.containerOid || null
  loadContainerOptions()
  loadAddEngDocs()
}

/** 加载当前容器下的工程数据，排除已关联的（后端唯一约束兜底） */
async function loadAddEngDocs() {
  const containerOid = addEngDocContainerOid.value
  if (!containerOid) { addEngDocList.value = []; return }
  addEngDocLoading.value = true
  try {
    const res = await getEngineeringDocuments({ containerOid })
    const arr = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
    const linked = new Set(engDocLinks.value.map(l => l.engDocumentOid))
    addEngDocList.value = arr.filter(d => d && d.oid && !linked.has(d.oid))
  } catch { addEngDocList.value = [] }
  finally { addEngDocLoading.value = false }
}

/**
 * 关联工程数据（单选）。
 *
 * <p>一个零件版本只能挂**一份**工程数据（2026-10-26 用户口径），所以这里只提交选中的那一条；
 * 后端 create 里还有一道同样的校验 —— 两个页签同时点、或绕过弹窗直接调接口，也只会成功一条。
 */
async function confirmAddEngDocLinks() {
  const target = addEngDocList.value.find(d => addEngDocSelectedRowKeys.value.includes(d.oid))
  if (!target) return
  addEngDocSaving.value = true
  try {
    const res = await createEngDocPartLink({
      partOid: oid.value,
      engDocumentOid: target.oid,
      assocType: addEngDocAssocType.value,
      buildStructure: addEngDocBuildStructure.value,
      buildAttribute: addEngDocBuildAttribute.value,
      buildRepresentation: addEngDocBuildRepresentation.value,
    })
    if (res?.code === 200) {
      message.success(`已关联工程数据「${target.number || target.name || target.oid}」`)
      addEngDocModalVisible.value = false
      await loadEngDocLinks()
    } else {
      message.error(res?.message || '关联失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || e.message || '关联失败')
  } finally {
    addEngDocSaving.value = false
  }
}

function onRemoveEngDocLinks() {
  const keys = engDocSelectedKeys.value
  if (!keys.length) return
  const rows = engDocLinks.value.filter(l => keys.includes(l.oid))
  const labels = rows.map(r => `${r.engDocName || '-'}(${r.engDocNumber || '-'})`).join('、')
  Modal.confirm({
    title: '确认解除关联',
    content: `确定要解除与「${labels}」的关联吗？解除后可重新关联。`,
    okText: '解除',
    okType: 'danger',
    cancelText: '取消',
    centered: true,
    onOk: async () => {
      let success = 0
      for (const row of rows) {
        try {
          const res = await deleteEngDocPartLink(row.oid)
          if (res?.code === 200) success++
        } catch (e) {
          message.error(`解除「${row.engDocName || row.oid}」失败：${e?.response?.data?.message || e.message}`)
        }
      }
      if (success > 0) {
        message.success(`已解除 ${success} 条关联`)
        engDocSelectedKeys.value = []
        await loadEngDocLinks()
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

// ==================== 变更情况（变更通告的变更对象反查） ====================

/**
 * 一行 = 某张变更通告上的**这一条变更对象**（后端 `/change-notices/by-entity` 拼好的）。
 *
 * <p>为什么不是"零件自己有哪些变更记录"：变更的落点是通告的受影响对象
 * （{@code ck_change_affected_object}，记在主对象上、指向具体被修订的那一版），
 * 所以反查这张表才能同时说清"哪张单改的、改的是哪一版、改出来的又是哪一版"。
 */
const changeNotices = ref([])
const changeLoading = ref(false)
/** 已执行（已产出新大版本）的条数 —— 与未执行的分开数，看进度一眼就够 */
const changeExecutedCount = computed(() => changeNotices.value.filter(row => !!row.executedAt).length)

const changeNoticeColumns = [
  { title: '变更通告', key: 'notice', ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '被修订版本', key: 'baseVersion', width: 110 },
  { title: '修订产物', key: 'resultVersion', width: 110 },
  { title: '执行时间', key: 'executedAt', width: 190 },
  { title: '责任人', key: 'owner', width: 110 },
  { title: '创建时间', key: 'noticeCreatedAt', width: 160 },
]

async function loadChangeNotices() {
  if (!oid.value) {
    return
  }
  changeLoading.value = true
  try {
    const res = await listChangeNoticesByEntity('PART', oid.value)
    changeNotices.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    changeNotices.value = []
  } finally {
    changeLoading.value = false
  }
}

/**
 * 点通告编号跳到「变更控制 → 变更通告」并直接打开那张单的详情抽屉。
 *
 * <p>只带 noticeOid：列表页据此定位（见 ChangeNoticePanel 的 onMounted），
 * 不带完整对象 —— 通告详情以服务端为准，跳过去看到的就是最新状态。
 */
function openChangeNotice(record) {
  if (!record?.noticeOid) {
    return
  }
  router.push({ path: '/change', query: { tab: 'notice', noticeOid: record.noticeOid } })
}

// 切换 tab 时按需懒加载
watch(activeTab, (key) => {
  if (key === 'bom' && bomList.value.length === 0 && !bomLoading.value) loadBom()
  if (key === 'usedBy' && usedByList.value.length === 0 && !usedByLoading.value) loadUsedBy()
  if (key === 'twoWaySubstitute' && alternateList.value.length === 0 && !alternateLoading.value) loadAlternates()
  if (key === 'relatedDocs' && docLinksList.value.length === 0 && !docLinksLoading.value) loadDocLinks()
  // 工程数据已并入「CAD 描述」页签（2026-10-26）：切到它时再拉，打开别的页签不多发请求
  if (key === 'cad' && engDocLinks.value.length === 0 && !engDocLoading.value) loadEngDocLinks()
  if (key === 'changes' && changeNotices.value.length === 0 && !changeLoading.value) loadChangeNotices()
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
  // 被使用情况可能停在某个父件上，换零件必须回到"看这个零件"这一层
  usedByDrill.value = []
  // 变更情况同样按零件缓存，换零件必须清掉（否则会显示上一个零件的变更历史）
  changeNotices.value = []
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
  /* 边框 / 圆角 / 阴影与产品系列页头同一套（.pl-header）：只有极淡阴影时卡片边缘几乎看不出来 */
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  /* 时间信息移进标题行后，底部留白由 12px 改 6px，保持与原先相近的呼吸感 */
  padding: 12px 20px 6px;
  margin-bottom: 2px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
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
/* 时间信息：与标题行同一行，用竖线与左侧标签拉开距离（窄屏时随 flex-wrap 折行） */
.pd-meta-time {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: 8px;
  padding-left: 10px;
  border-left: 1px solid #f0f0f0;
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
  /* 与页头同一套描边，避免两张卡片一有边一无边显得割裂 */
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 4px 20px 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
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
  /* 右侧详情栏是可拖窄的：内容整体横向滚动，
     否则「本行替代件」「启用」这类标签会被挤成两行 */
  overflow-x: auto;
}
.tab-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  white-space: nowrap;
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
  white-space: nowrap;
}
.tab-stat-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
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

/* ===== 被使用情况页签：往上追溯的范围条 + 父件链接 ===== */
.usedby-scope {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
  padding: 6px 10px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  font-size: 12px;
}
.usedby-scope-label {
  color: #8c8c8c;
}
.usedby-scope-node {
  color: #1677ff;
  cursor: pointer;
}
.usedby-scope-node:hover {
  text-decoration: underline;
}
.usedby-scope-sep {
  color: #bfbfbf;
}
.usedby-scope-current {
  color: #262626;
  font-weight: 500;
}
.usedby-parent-link {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #1677ff;
  cursor: pointer;
}
.usedby-parent-link:hover {
  text-decoration: underline;
}

/* ===== 关联问题页签 ===== */
.issue-link-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.issue-link-hint {
  flex: 1;
  font-size: 12px;
  color: #8c8c8c;
}
.issue-code-link {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #1677ff;
  cursor: pointer;
}
.issue-code-link:hover {
  background: #e6f4ff;
  text-decoration: underline;
}
.issue-overdue {
  color: #ff4d4f;
}

/* ===== BOM 快照（新增 / 记录 / 对比） ===== */
/* 快照名称是可点的超链接：点开看这份快照定格的 BOM 明细 */
.snapshot-name-link {
  color: #1677ff;
  cursor: pointer;
}
.snapshot-name-link:hover {
  text-decoration: underline;
}
/* 快照明细里的编码：与其它页签的 code 视觉一致 */
.snapshot-item-code {
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #595959;
}
.snapshot-detail-meta {
  margin-bottom: 12px;
}
.snapshot-tip {
  font-size: 12px;
  line-height: 1.7;
  color: #8c8c8c;
  background: #fafafa;
  border-radius: 4px;
  padding: 8px 10px;
}
.snapshot-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.snapshot-bar-hint {
  flex: 1;
  font-size: 12px;
  color: #8c8c8c;
}
.snapshot-diff-labels {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #595959;
  margin-bottom: 8px;
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

/* ===== 局部替代的行内标记（BOM 树）===== */
/* 与工具栏「替代」按钮同一套图标（SwapOutlined），位置固定在状态标签之后；
   尺寸压到 18px 是为了不把 BOM 行的行高撑起来 */
.bom-sub-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  height: 18px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 1;
  color: #531dab;
  background: #f9f0ff;
  border: 1px solid #d3adf7;
  border-radius: 9px;
  cursor: pointer;
}
.bom-sub-badge:hover {
  background: #efdbff;
}
/* 替代件全被停用：置灰 —— 别让人以为这行现在还能被替换 */
.bom-sub-badge-off {
  color: #8c8c8c;
  background: #fafafa;
  border-color: #d9d9d9;
}
.bom-sub-badge-off:hover {
  background: #f0f0f0;
}

/* 成组替代的行内标记：橙色系，与局部替代的紫色系一眼分得开
   （两个标记含义不同：⇄ 是"这行换几颗料"，⧉ 是"这行属于几组整组替换"） */
.bom-group-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  height: 18px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 1;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 9px;
  cursor: pointer;
}
.bom-group-badge:hover {
  background: #ffe7ba;
}

/* 标记的工具提示：深色底，所以用半透明白做层次，不引主题色 */
.bom-sub-tip {
  max-width: 440px;
}
.bom-sub-tip-head {
  font-weight: 600;
  margin-bottom: 4px;
}
.bom-sub-tip-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 1px 0;
}
.bom-sub-tip-row code {
  background: rgba(255, 255, 255, 0.16);
  padding: 0 4px;
  border-radius: 3px;
}
.bom-sub-tip-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bom-sub-tip-ver,
.bom-sub-tip-qty {
  opacity: 0.8;
}
.bom-sub-tip-row-off {
  opacity: 0.6;
}
.bom-sub-tip-off {
  color: #ffccc7;
}
.bom-sub-tip-note {
  opacity: 0.75;
}
.bom-sub-tip-foot {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
  opacity: 0.75;
}

/* ===== 「替代情况」页签 ===== */
.bom-sub-link {
  color: #1677ff;
}
.bom-sub-link:hover {
  text-decoration: underline;
}
.bom-sub-tab-note {
  margin-top: 8px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.6;
}

/* ===== 过滤器菜单里的条件项 +「编辑过滤器」弹窗 ===== */
.bom-filter-num {
  margin-left: 6px;
  font-size: 12px;
  color: #8c8c8c;
}
.bom-filter-check {
  margin-left: 4px;
  font-size: 12px;
  color: #1677ff;
}
.bom-filter-option {
  margin-bottom: 12px;
}
.bom-filter-option-desc {
  margin: 6px 0 0 24px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.7;
}
.bom-filter-preview {
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  font-size: 12px;
  color: #595959;
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
/* ===== 右侧「轻量化」页签 ===== */
.bom-cad-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 12px;
}
.bom-cad-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
  font-weight: 500;
}
.bom-cad-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
}
/* 右栏本身不宽，给模型一个固定高度就够看；结构树在查看器里是可折叠的 */
.bom-cad-viewer {
  height: 360px;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}
.cad-grid {
  padding: 4px 0;
}

/* 工程数据区块：挂在 CAD 卡片下方，用一条虚线把"描述"与"关联的数据"分开 */
.cad-eng {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #f0f0f0;
}
/* 没有 CAD 卡片时（工程数据独占本页签）：不需要那条分隔线和上边距 */
.cad-eng--solo {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}
.cad-eng-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}
.cad-eng-hint {
  font-size: 12px;
  font-weight: 400;
  color: #8c8c8c;
}

/* 变更情况：通告编号是可点进详情的链接，标题跟在后面（同一格里省一列） */
.pd-change-link {
  font-family: monospace;
  font-size: 12px;
  color: #1677ff;
  cursor: pointer;
}
.pd-change-link:hover {
  text-decoration: underline;
}
.pd-change-title {
  margin-left: 8px;
  color: #262626;
}
</style>