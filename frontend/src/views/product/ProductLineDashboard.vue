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
            <!-- 整机视图入口（2026-10-11）：驾驶舱 / 指标属"系列·型号整体"（无阶段维度），
                 已从本页页尾拆出为独立页 —— 点此进入 /product/:oid/overview -->
            <a-button
              v-if="line?.oid && (has('METRIC_MANAGEMENT') || has('DASHBOARD'))"
              size="small"
              @click="router.push(`/product/${line.oid}/overview`)"
            >
              <template #icon><BarChartOutlined /></template>整机视图
            </a-button>
            <a-tag v-if="line?.code" color="default" class="pl-code-tag">{{ line.code }}</a-tag>
          </div>
        </div>

        <!-- 阶段头（并入页头左列，分两行）：上行 = 当前阶段（按"第一个未通过的门"推，P3 口径；
             存量对象 / 未配门时退回第一阶段），下行 = 阶段汇总（门状态）+ 切换下拉。
             左右次序见样式区 .pl-header-main > * 的 order —— 它与标题块是左右对调的。 -->
        <div class="pl-stage-head" v-if="line">
          <div class="pl-stage-current">
            <!-- 不再写「当前阶段」四字标签：它就贴在标题旁边、下拉框又带箭头，含义自明 -->
            <component :is="activeStageDef?.icon" class="pl-tab-icon" :style="{ color: activeStageDef?.color }" />
            <!-- 阶段说明挪进 title 悬停：并进页头后横向寸土寸金 -->
            <span class="pl-stage-title" :title="activeStageDef?.description">{{ activeStageDef?.title }}</span>
          </div>
          <!-- 下行：汇总信息（门状态）与切换下拉 —— "过没过门"和"切到哪一阶段"本是两件事 -->
          <div class="pl-stage-summary">
            <a-tag :color="currentStageGateColor">{{ currentStageGateText }}</a-tag>
            <a-select
              v-model:value="activeStage"
              size="small"
              style="width:130px"
              :options="stageOptions"
              @change="userPickedStage = true"
            />
          </div>
        </div>

        <!-- 右侧一列：统计信息在上，描述与创建信息在它下面。
             描述/创建原本占着标题下面的一整行，左侧因此比右侧高出两行；收到右列后
             左侧只剩「面包屑 + 标题 + 编码」，页头少一行 —— 内容区就多一行可用高度 -->
        <div class="pl-header-side" v-if="line">
          <div class="pl-header-stats">
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

          <!-- 描述是变长的：自己截断（悬停看全），"创建于"始终留在可见位置 -->
          <p class="pl-meta">
            <span v-if="line.description" class="pl-meta-desc" :title="line.description">{{ line.description }}</span>
            <span v-if="line.description" class="pl-meta-sep" />
            <span class="pl-meta-created">创建于 {{ formatTime(line.createdAt) }}</span>
          </p>
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

    <!-- 研发阶段管线（2026-10-11 上移：与页头的阶段切换器直接相邻 —— 原来"整机视角"插在两者之间，
         既把阶段管线挤出首屏（用户找不到交付件/阶段门/评审/规则），又让人误以为"指标/驾驶舱属于当前阶段"） -->
    <section class="pl-pipeline">
      <!-- 注：阶段头（当前阶段 + 下拉切换）已并入页头同一行（见上方 .pl-header-main 内），
           它原来在此独占一整行 —— 用户反馈"页头占的篇幅太多"，故合并。 -->

      <!-- 阶段内视图：研发资料 / 交付件 / 阶段门 / 评审 / 检查规则 —— 后四类按当前阶段过滤；
           未启用对应能力（ck_tenant_capability）的面板不出现。
           注：2026-10-11「过程资料」定名「研发资料」（用户要求）—— 内部标识 key="docs" 与
           相关变量名（stageView 的 docs 值）保留，改显示名不牵动内部标识 -->
      <a-tabs v-model:activeKey="stageView" size="small" class="pl-stage-view">
        <!--
          物料清单：该系列 / 型号下的所有零件，一行一个零件（最新大版本的最新小版本）。
          刻意不按阶段 / 文件夹过滤 —— 同一颗料可能跨多个阶段使用，按阶段切只会让它在
          几个页签里重复出现。
          2026-10-22：只认当前容器的 container_oid，不回溯父系列 —— 型号页不再显示
          挂在父系列上的零件（父子容器各看各的）。
          2026-10-22：列与「研发资料」对齐（编码/名称/类型/版本/单位/生命周期/检出/操作），
          操作列直接复用零件的能力，不再只是"BOM 用量汇总"的只读视图。
        -->
        <a-tab-pane key="bom" tab="物料清单">
          <div class="pl-bom-materials">
            <div class="pl-bom-materials-bar">
              <span class="pl-bom-materials-hint">
                本{{ line?.nodeType === 'PRODUCT_MODEL' ? '型号' : '产品' }}下所有零件的最新大版本 · 最新小版本，不区分阶段与文件夹
              </span>
              <span class="pl-bom-materials-count">共 {{ bomMaterials.length }} 个零件</span>
            </div>
            <DataTable
              scroll-x="max-content"
              :columns="bomMaterialColumns"
              :data-source="bomMaterials"
              :loading="bomMaterialsLoading"
              :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 个零件` }"
              row-key="oid"
              size="small"
              search-placeholder="搜索零组件编码/名称..."
              :search-fields="['code', 'name', 'typeDefinitionName']"
            >
              <!-- 空清单时最需要的动作：先建一个零组件（研发资料页签也有同一入口）。
                   放 #toolbar（右侧工具区）与搜索/视图同排靠右。 -->
              <template #toolbar>
                <!-- 零件已无「所属阶段」属性（2026-10-22）：入口不再按阶段模板的 managedObjectTypes 判断 -->
                <a-tooltip title="新建零组件">
                  <a-button type="primary" size="small" @click="openCreatePart">
                    <template #icon><ToolOutlined /></template>
                    新建零组件
                  </a-button>
                </a-tooltip>
              </template>
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'checkout_status'">
                  <a-tooltip v-if="record.checkedOut" placement="top">
                    <template #title>
                      <div>检出人: {{ record.checkedOutBy || '-' }}</div>
                      <div v-if="record.checkedOutComment">注释: {{ record.checkedOutComment }}</div>
                    </template>
                    <LockOutlined style="color:#fa8c16;font-size:13px" />
                  </a-tooltip>
                </template>
                <!--
                  缩略图（2026-10-26 用户要求）：零件关联了工程数据（CAD 描述）且转换器已出图时显示，
                  没有就给同尺寸占位 —— 行高一致、列宽不跳动。不点击（本页三维浏览入口在「产品数模」页签）。
                -->
                <template v-else-if="column.key === 'thumb'">
                  <a-image
                    v-if="record.thumbnailCkfileOid"
                    :src="ckFileStreamUrl(record.thumbnailCkfileOid)"
                    :width="40"
                    :height="40"
                    :preview="false"
                    class="pl-bom-thumb"
                    :alt="record.name || '缩略图'"
                  />
                  <div v-else class="pl-bom-thumb-empty" :title="'未关联 CAD 描述（或无缩略图）'">
                    <PictureOutlined />
                  </div>
                </template>
                <template v-else-if="column.key === 'code'">
                  <a class="pl-code-link" @click="openPartDetail(record)">{{ record.code || '-' }}</a>
                </template>
                <template v-else-if="column.key === 'typeDefinitionName'">
                  <a-tag color="geekblue" size="small">{{ record.typeDefinitionName || record.typeCode || '-' }}</a-tag>
                </template>
                <template v-else-if="column.key === 'status'">
                  <a-tag :color="statusColor(record.statusCode)" size="small">{{ record.statusName || record.statusCode || '-' }}</a-tag>
                </template>
                <template v-else-if="column.key === 'checkout'">
                  <a-tag v-if="record.checkedOut" color="orange" size="small">已检出: {{ record.checkedOutBy }}</a-tag>
                  <a-tag v-else color="green" size="small">已检入</a-tag>
                </template>
                <template v-else-if="column.key === 'action'">
                  <!-- 复用研发资料行菜单：后端已把 entityType 标成 PART，分派到零件的检出/检入/修订。
                       注意注释别写进 #overlay 里 —— Vue dev 构建会保留注释节点，而 antd Dropdown
                       的 renderOverlay 只取插槽返回的第一个 vnode，注释当头会把整个菜单吞掉（点击无反应）。 -->
                  <a-dropdown :trigger="['click']">
                    <a-button type="link" size="small">操作 <DownOutlined style="font-size:10px;margin-left:2px" /></a-button>
                    <template #overlay>
                      <a-menu @click="(e) => handleMixedAction(e, record)">
                        <a-menu-item key="view"><EyeOutlined /> 详情</a-menu-item>
                        <a-menu-item key="rename"><FormOutlined /> 重命名</a-menu-item>
                        <a-menu-item key="saveAs"><CopyOutlined /> 另存为</a-menu-item>
                        <a-menu-item key="history"><HistoryOutlined /> 查看历史版本</a-menu-item>
                        <a-menu-divider />
                        <a-menu-item v-if="!record.checkedOut" key="checkout"><LockOutlined /> 检出</a-menu-item>
                        <a-menu-item v-if="!record.checkedOut" key="checkoutEdit"><EditOutlined /> 检出并编辑</a-menu-item>
                        <a-menu-item v-if="canUndoCheckout(record)" key="edit"><EditOutlined /> 编辑</a-menu-item>
                        <a-menu-item v-if="canUndoCheckout(record)" key="checkin"><CheckOutlined /> 检入</a-menu-item>
                        <a-menu-item v-if="canUndoCheckout(record)" key="undoCheckout"><RollbackOutlined /> 取消检出</a-menu-item>
                        <a-menu-divider />
                        <a-menu-item key="move"><SwapOutlined /> 移动</a-menu-item>
                        <a-menu-item v-if="!record.checkedOut && record.revisable !== false" key="revise"><BranchesOutlined /> 修订</a-menu-item>
                        <a-menu-divider />
                        <a-menu-item v-if="!record.checkedOut" key="lifecycle"><ExperimentOutlined /> 设置生命周期状态</a-menu-item>
                        <a-menu-item v-if="!record.checkedOut" key="workflow"><SendOutlined /> 发起流程</a-menu-item>
                        <a-menu-item v-if="!record.checkedOut" key="delete" danger><DeleteOutlined /> 删除</a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                </template>
              </template>
            </DataTable>
          </div>
        </a-tab-pane>

        <!--
          产品数模：该系列 / 型号配套的三维数模（.step/.stp/.iges/.igs/.stl）。
          与「物料清单」同口径，不随阶段过滤 —— 一份整机数模往往贯穿多个阶段，
          按阶段切只会让同一份数模在几个页签里重复出现。
          一行 = 一个工程文档最新迭代上的一个数模文件 + 它最近一次的轻量化状态。
        -->
        <a-tab-pane key="cad" tab="产品数模">
          <div class="pl-cad-models">
            <div class="pl-bom-materials-bar">
              <span class="pl-bom-materials-hint">
                来自{{ line?.nodeType === 'PRODUCT_MODEL' ? '本型号及所属系列' : '本产品' }}下工程数据（ENG_DOCUMENT）<strong>最新版本</strong>的三维数模主文件，一行一份文件
                <a-tooltip title="归档入口为临时方案：后续由 MCAD 集成插件在 CAD 中保存时自动归档工程数据与数模，不再需要手工上传">
                  <InfoCircleOutlined style="margin-left:4px;color:#999;cursor:help" />
                </a-tooltip>
              </span>
              <span class="pl-bom-materials-count">
                <a-tooltip v-if="!cadViewerAvailable" title="未部署三维转换器 CK-CADForge，只能下载源文件">
                  <a-tag color="default" style="margin-right:8px">三维预览不可用</a-tag>
                </a-tooltip>
                共 {{ cadModels.length }} 份数模
              </span>
            </div>
            <DataTable
              scroll-x="max-content"
              :columns="cadModelColumns"
              :data-source="cadModels"
              :loading="cadModelsLoading"
              :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 份数模` }"
              :row-selection="cadRowSelection"
              row-key="sourceCkfileOid"
              size="small"
              search-placeholder="搜索工程数据/文件名..."
              :search-fields="['engDocNumber', 'engDocName', 'cadName', 'fileName']"
            >
              <template #toolbar>
                <!--
                  批量入口：与 MCAD 集成后一次会新增几十份数模，逐个点操作菜单再点"生成预览"
                  是纯体力活。选中行后这里直接入队，结果在弹窗里分类回显。
                -->
                <a-button
                  v-if="cadSelectedKeys.length"
                  size="small"
                  :loading="cadBatchSubmitting"
                  style="margin-right:8px"
                  @click="batchConvertCad"
                >
                  <template #icon><ExperimentOutlined /></template>
                  批量生成预览 ({{ cadSelectedKeys.length }})
                </a-button>
                <!--
                  数模的载体是工程数据（ENG_DOCUMENT / EPMDocument），不是通用文档：
                  只有 EPMDocument 才带 cadName/cadType/cadTool，主文件也挂在
                  ck_eng_document_iteration.ckfile_oid 上 —— 本列表正是按这条链查的。
                  显隐判断带上 DOCUMENT：阶段模板目前只声明 FUNCTIONAL/PART/DOCUMENT，
                  没有 ENG_DOCUMENT，单看它会让按钮永远不出现。
                  放 #toolbar（右侧工具区）而不是 #toolbar-left：归档入口与搜索/视图同排靠右。
                -->
                <a-tooltip
                  v-if="stageManagedObjects.has('ENG_DOCUMENT') || stageManagedObjects.has('DOCUMENT')"
                  title="工程数据归档：新建工程数据（ENG_DOCUMENT），主文件挂 STEP / IGES / STL 等三维数模。当前为临时入口 —— 后续由 MCAD 集成插件在 CAD 中保存时自动归档"
                >
                  <a-button type="primary" size="small" @click="openCreateEngDocument">
                    <template #icon><ExperimentOutlined /></template>
                    工程数据归档
                  </a-button>
                </a-tooltip>
              </template>
              <template #bodyCell="{ column, record }">
                <!--
                  缩略图由转换器渲染（kind=thumbnail），与用户点开看到的 GLB 同一份几何。
                  没有时给同尺寸占位而不是留空：行高一致、列宽不跳动。
                -->
                <!-- 缩略图就是三维浏览入口：列表里最自然的点击目标就是那张图 -->
                <template v-if="column.key === 'thumb'">
                  <div
                    class="pl-cad-thumb-wrap"
                    :class="{ 'is-clickable': canOpenCadView(record) }"
                    :title="canOpenCadView(record) ? '三维浏览' : (record.thumbnailCkfileOid ? '' : '尚未生成缩略图')"
                    @click="onCadThumbClick(record)"
                  >
                    <a-image
                      v-if="record.thumbnailCkfileOid"
                      :src="ckFileStreamUrl(record.thumbnailCkfileOid)"
                      :width="48"
                      :height="48"
                      :preview="false"
                      class="pl-cad-thumb"
                      :alt="record.fileName || '缩略图'"
                    />
                    <div v-else class="pl-cad-thumb-empty">
                      <PictureOutlined />
                    </div>
                    <!-- 检出 = 独占编辑锁：角标挂在缩略图上，扫列表时一眼就知道这份现在动不了 -->
                    <a-tooltip v-if="record.checkedOut" placement="bottom">
                      <template #title>
                        <div>已检出: {{ record.checkedOutBy || '-' }}</div>
                        <div v-if="record.checkedOutComment">注释: {{ record.checkedOutComment }}</div>
                      </template>
                      <span class="pl-cad-thumb-lock" @click.stop><LockFilled /></span>
                    </a-tooltip>
                  </div>
                </template>
                <template v-else-if="column.key === 'doc'">
                  <div>{{ record.engDocNumber || '—' }}</div>
                  <div v-if="record.engDocName" class="pl-cad-sub">{{ record.engDocName }}</div>
                </template>
                <template v-else-if="column.key === 'file'">
                  <div class="pl-cad-file">
                    <a-tag :color="cadFormatColor(record.fileName)">{{ cadFormatLabel(record.fileName) }}</a-tag>
                    <span :title="record.fileName">{{ record.fileName }}</span>
                  </div>
                  <div class="pl-cad-sub">{{ formatSize(record.fileSize) }}</div>
                </template>
                <template v-else-if="column.key === 'checkout'">
                  <a-tooltip v-if="record.checkedOut" placement="top">
                    <template #title>
                      <div>检出人: {{ record.checkedOutBy || '-' }}</div>
                      <div v-if="record.checkedOutComment">注释: {{ record.checkedOutComment }}</div>
                    </template>
                    <a-tag color="orange" size="small">已检出: {{ record.checkedOutBy }}</a-tag>
                  </a-tooltip>
                  <a-tag v-else color="green" size="small">已检入</a-tag>
                </template>
                <template v-else-if="column.key === 'status'">
                  <a-tooltip :title="cadStatusMeta(record).tip || undefined">
                    <a-tag :color="cadStatusMeta(record).color">{{ cadStatusMeta(record).text }}</a-tag>
                  </a-tooltip>
                  <!--
                    转换中把"在干什么"也说出来：只有百分比时，卡在 85% 的用户
                    完全不知道是在压缩模型还是在渲染缩略图，只能猜。
                    QUEUED 没有百分比，画一条不确定进度条而不是 0% 的死条。
                  -->
                  <div
                    v-if="record.status === 'RUNNING' || record.status === 'QUEUED'"
                    class="pl-cad-progress"
                  >
                    <a-progress
                      :percent="record.status === 'RUNNING' ? (record.progress ?? 0) : 0"
                      :show-info="false"
                      size="small"
                      status="active"
                    />
                    <div class="pl-cad-sub" :title="cadProgressText(record)">{{ cadProgressText(record) }}</div>
                  </div>
                </template>
                <template v-else-if="column.key === 'model'">
                  <template v-if="record.status === 'SUCCESS'">
                    <div v-if="record.triangleCount != null">{{ formatCount(record.triangleCount) }} 面片</div>
                    <div class="pl-cad-sub">
                      <span v-if="record.partCount != null">{{ formatCount(record.partCount) }} 个零件</span>
                      <span v-if="record.units"> · {{ record.units }}</span>
                    </div>
                  </template>
                  <span v-else class="pl-cad-sub">—</span>
                </template>
                <!--
                  操作列与「物料清单」对齐成下拉菜单：动作一多，平铺的 link 按钮会把操作列撑成一片蓝字，
                  而且平铺时"检出/检入"这类有前置条件的动作只能靠消失来表达，用户看不懂自己少了什么。
                -->
                <template v-else-if="column.key === 'action'">
                  <a-dropdown :trigger="['click']">
                    <a-button type="link" size="small">操作 <DownOutlined style="font-size:10px;margin-left:2px" /></a-button>
                    <template #overlay>
                      <a-menu @click="(e) => handleCadModelAction(e, record)">
                        <a-menu-item key="view3d" :disabled="!canOpenCadView(record)">
                          <EyeOutlined /> 三维浏览
                        </a-menu-item>
                        <a-menu-item key="download"><DownloadOutlined /> 下载数模</a-menu-item>
                        <a-menu-item key="downloadAssembly"><GoldOutlined /> 下载装配数模</a-menu-item>
                        <a-menu-divider />
                        <a-menu-item v-if="!record.checkedOut" key="checkout"><LockOutlined /> 检出</a-menu-item>
                        <a-menu-item v-if="canUndoCheckout(record)" key="checkin"><CheckOutlined /> 检入</a-menu-item>
                        <a-menu-item v-if="canUndoCheckout(record)" key="undoCheckout"><RollbackOutlined /> 撤销检出</a-menu-item>
                        <a-menu-divider />
                        <a-menu-item v-if="!record.checkedOut" key="lifecycle"><ExperimentOutlined /> 设置生命周期状态</a-menu-item>
                        <a-menu-item v-if="cadViewerAvailable && isConvertibleSource(record) && !record.status" key="convert">
                          <ExperimentOutlined /> 生成轻量化预览
                        </a-menu-item>
                        <a-menu-item v-if="cadViewerAvailable && (record.status === 'FAILED' || record.status === 'TIMEOUT')" key="retry">
                          <RollbackOutlined /> 重新转换
                        </a-menu-item>
                      </a-menu>
                    </template>
                  </a-dropdown>
                </template>
              </template>
            </DataTable>
          </div>
        </a-tab-pane>

        <a-tab-pane key="docs" tab="研发资料">
          <!-- 研发资料区域（原"过程资料"，2026-10-11 定名） -->
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
                <div class="pl-folders-tree-scroll">
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
                </div>
                <a-button type="dashed" size="small" block class="pl-tree-new-btn" @click="openCreateFolder(null)">
                  <FolderAddOutlined /> 新建文件夹
                </a-button>
              </div>

              <!-- 右侧内容 -->
              <div class="pl-folders-content">
                <div v-if="!selectedFolder" class="pl-folders-content-empty">
                  <FileTextOutlined class="pl-folders-content-empty-icon" />
                  <p>点击左侧文件夹查看研发资料</p>
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
                        scroll-x="max-content"
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
                          <a-tooltip title="新建文档"><a-button v-if="stageManagedObjects.has('DOCUMENT')" size="small" @click="openCreateDocument"><FileTextOutlined /></a-button></a-tooltip>
                          <a-tooltip title="新建功能架构"><a-button v-if="stageManagedObjects.has('FUNCTIONAL') && has('FUNCTIONAL_ARCHITECTURE')" size="small" @click="openCreateFunctional"><BranchesOutlined /></a-button></a-tooltip>
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
                            <!-- 编码即详情入口：点编码去对象自己的详情，比先展开"操作"再点"详情"少一步 -->
                            <a class="pl-code-link" @click="openRecordDetail(record)">{{ record.code || '-' }}</a>
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
                                  <a-menu-item key="rename"><FormOutlined /> 重命名</a-menu-item>
                                  <a-menu-item v-if="!record.checkedOut" key="checkout"><LockOutlined /> 检出</a-menu-item>
                                  <a-menu-item v-if="!record.checkedOut" key="checkoutEdit"><EditOutlined /> 检出并编辑</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="edit"><EditOutlined /> 编辑</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="checkin"><CheckOutlined /> 检入</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="undoCheckout"><RollbackOutlined /> 取消检出</a-menu-item>
                                  <a-menu-divider />
                                  <a-menu-item v-if="record.ckfileOid" key="download"><DownloadOutlined /> 下载主文件</a-menu-item>
                                  <!-- 修订权限由对象当前状态在模板里的 revisable 决定（后端同时兜底拦截） -->
                                  <a-menu-item v-if="!record.checkedOut && record.revisable !== false" key="revise"><BranchesOutlined /> 修订</a-menu-item>
                                </a-menu>
                              </template>
                            </a-dropdown>
                          </template>
                        </template>
                      </DataTable>
                    </a-tab-pane>
                    <a-tab-pane key="own" tab="自有资料">
                      <DataTable
                        scroll-x="max-content"
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
                          <a-tooltip title="新建文档"><a-button v-if="stageManagedObjects.has('DOCUMENT')" size="small" @click="openCreateDocument"><FileTextOutlined /></a-button></a-tooltip>
                          <a-tooltip title="新建功能架构"><a-button v-if="stageManagedObjects.has('FUNCTIONAL') && has('FUNCTIONAL_ARCHITECTURE')" size="small" @click="openCreateFunctional"><BranchesOutlined /></a-button></a-tooltip>
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
                            <!-- 编码即详情入口：点编码去对象自己的详情，比先展开"操作"再点"详情"少一步 -->
                            <a class="pl-code-link" @click="openRecordDetail(record)">{{ record.code || '-' }}</a>
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
                                  <a-menu-item key="rename"><FormOutlined /> 重命名</a-menu-item>
                                  <a-menu-item v-if="!record.checkedOut" key="checkout"><LockOutlined /> 检出</a-menu-item>
                                  <a-menu-item v-if="!record.checkedOut" key="checkoutEdit"><EditOutlined /> 检出并编辑</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="edit"><EditOutlined /> 编辑</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="checkin"><CheckOutlined /> 检入</a-menu-item>
                                  <a-menu-item v-if="canUndoCheckout(record)" key="undoCheckout"><RollbackOutlined /> 取消检出</a-menu-item>
                                  <a-menu-item v-if="record.ckfileOid" key="download"><DownloadOutlined /> 下载主文件</a-menu-item>
                                  <a-menu-divider />
                                  <a-menu-item key="move"><SwapOutlined /> 移动</a-menu-item>
                                  <!-- 修订权限由对象当前状态在模板里的 revisable 决定（后端同时兜底拦截） -->
                                  <a-menu-item v-if="!record.checkedOut && record.revisable !== false" key="revise"><BranchesOutlined /> 修订</a-menu-item>
                                  <a-menu-divider />
                                  <a-menu-item v-if="!record.checkedOut" key="lifecycle"><ExperimentOutlined /> 设置生命周期状态</a-menu-item>
                                  <a-menu-item v-if="!record.checkedOut" key="workflow"><SendOutlined /> 发起流程</a-menu-item>
                                  <a-menu-divider />
                                  <a-menu-item v-if="!record.checkedOut" key="delete" danger><DeleteOutlined /> 删除</a-menu-item>
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
                <!-- 自有文件夹：零组件 + 文档合并列表 -->
                <template v-else>
                  <div class="pl-folders-content-files">
                    <!-- 2026-10-11 去掉 stretch：撑满模式会让表体固定高度自滚（实测表体 71px / 内容 151px），
                         与外层滚动叠加成研发资料页签的"并排双滚动条"；改为自然高度，统一交给整页（.pl-dashboard）滚动 -->
                    <DataTable
                      scroll-x="max-content"
                      :columns="mixedColumns"
                      :data-source="filteredMixed"
                      :loading="docLoading || partLoading"
                      :pagination="{ pageSize: 10 }"
                      row-key="oid"
                      size="small"
                      :searchable="true"
                      search-placeholder="搜索零组件/文档..."
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
                        <!-- 业务对象过滤：右对齐（与新建/搜索/视图同一行） -->
                        <a-select
                          v-model:value="bizObjectFilter"
                          size="small"
                          style="min-width:140px;margin-right:8px"
                          :options="bizObjectFilterOptions"
                          placeholder="全部对象"
                        />
                        <!-- 零组件统一在「物料清单」页签创建：研发资料只管文档 / 功能架构，
                             同一件事两个入口只会让人在两处各建一个零件（2026-11-03 用户要求移除） -->
                        <a-tooltip title="新建文档"><a-button v-if="stageManagedObjects.has('DOCUMENT')" size="small" @click="openCreateDocument"><FileTextOutlined /></a-button></a-tooltip>
                        <a-tooltip title="新建功能架构"><a-button v-if="stageManagedObjects.has('FUNCTIONAL') && has('FUNCTIONAL_ARCHITECTURE')" size="small" @click="openCreateFunctional"><BranchesOutlined /></a-button></a-tooltip>
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
                          <!-- 编码即详情入口（阶段页签的零组件 + 文档混合列表） -->
                          <a class="pl-code-link" @click="openRecordDetail(record)">{{ record.code || '-' }}</a>
                        </template>

                        <template v-else-if="column.key === 'typeDefinitionName'">
                          <a-tag :color="record.entityType === 'PART' ? 'geekblue' : 'default'" size="small">
                            {{ record.typeDefinitionName || record.typeDefinitionCode || '-' }}
                          </a-tag>
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
                              <a-menu @click="(e) => handleMixedAction(e, record)">
                                <a-menu-item key="view"><EyeOutlined /> 详情</a-menu-item>
                                <a-menu-item key="rename"><FormOutlined /> 重命名</a-menu-item>
                                <a-menu-item v-if="record.entityType === 'PART'" key="saveAs"><CopyOutlined /> 另存为</a-menu-item>
                                <a-menu-item key="history"><HistoryOutlined /> 查看历史版本</a-menu-item>
                                <a-menu-item v-if="!record.checkedOut" key="checkout"><LockOutlined /> 检出</a-menu-item>
                                <a-menu-item v-if="!record.checkedOut" key="checkoutEdit"><EditOutlined /> 检出并编辑</a-menu-item>
                                <a-menu-item v-if="canUndoCheckout(record)" key="edit"><EditOutlined /> 编辑</a-menu-item>
                                <a-menu-item v-if="canUndoCheckout(record)" key="checkin"><CheckOutlined /> 检入</a-menu-item>
                                <a-menu-item v-if="canUndoCheckout(record)" key="undoCheckout"><RollbackOutlined /> 取消检出</a-menu-item>
                                <a-menu-item v-if="record.ckfileOid" key="download"><DownloadOutlined /> 下载主文件</a-menu-item>
                                <a-menu-divider />
                                <a-menu-item key="move"><SwapOutlined /> 移动</a-menu-item>
                                <!-- 修订 = 大版本递增（revision+1，小版本重置为 1）：零组件与文档同一套语义 -->
                                <a-menu-item v-if="!record.checkedOut && record.revisable !== false" key="revise"><BranchesOutlined /> 修订</a-menu-item>
                                <a-menu-divider />
                                <a-menu-item v-if="!record.checkedOut" key="lifecycle"><ExperimentOutlined /> 设置生命周期状态</a-menu-item>
                                <a-menu-item v-if="!record.checkedOut" key="workflow"><SendOutlined /> 发起流程</a-menu-item>
                                <a-menu-divider />
                                <a-menu-item v-if="!record.checkedOut" key="delete" danger><DeleteOutlined /> 删除</a-menu-item>
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

            <!-- 阶段内的四类方法论实例：按本阶段过滤（评审按本阶段的门匹配）；能力未启用则不出现 -->
            <a-tab-pane v-if="has('DELIVERABLE_MANAGEMENT')" key="deliverable">
              <template #tab>交付件 ({{ visibleCount(stageDeliverables) }})</template>
              <MethodologyInstancePanel
                kind="deliverable"
                :rows="stageDeliverables"
                :all-rows="instances.deliverables"
                :loading="instancesLoading"
                :stage-names="stageNames"
                show-artifacts
                :artifact-summary="artifactSummary"
                :permissions="instances.permissions"
                @open-artifacts="openArtifactModal"
                @tailor="onTailor"
              />
            </a-tab-pane>
            <!--
              阶段门（含评审）合成一个页签（2026-10-29）：
              面板内按类型分组（技术门 / 决策门），技术门可展开看挂在该门下的评审。
              评审不再是独立页签 —— 它的归属只有"挂哪道门"，拆成两页等于把一段从属关系拆开看，
              用户要在两个页签间来回对"这条评审属于哪道门"。
            -->
            <!--
              阶段门（2026-10-29 扁平化）：一个页签、两层内容，但技术评审不再包一层"技术门"：
              ① 决策门（DCP）—— 出 Go / Hold / Terminate 与商业留痕，是真正意义上的"门"；
              ② 技术评审（TR）—— 出问题清单与整改项，直接挂在本阶段下，一份评审 = 一场会。
              技术评审原本挂技术门下，多出来的那层壳既没人决策也没人看，已移除。
            -->
            <a-tab-pane v-if="has('GATE_REVIEW')" key="gate">
              <template #tab>阶段门 ({{ visibleCount(stageGates) + visibleCount(stageReviews) }})</template>
              <div class="stage-block">
                <div class="stage-block-head">
                  <span class="stage-block-title">决策门（DCP）</span>
                  <span class="stage-block-count">{{ visibleCount(stageGates) }}</span>
                  <span class="stage-block-hint">Go / Hold / Terminate 与商业留痕；过门即对外承诺</span>
                </div>
                <!-- 某阶段没有决策门是正常状态（如开发阶段只有技术评审）：不做全集回落、给专属空态 -->
                <MethodologyInstancePanel
                  kind="gate"
                  :rows="stageGates"
                  :all-rows="instances.gates.filter(g => g.type !== 'TECHNICAL_REVIEW')"
                  :mismatch-fallback="false"
                  empty-hint="本阶段没有决策评审内容"
                  :loading="instancesLoading"
                  :stage-names="stageNames"
                  :permissions="instances.permissions"
                  :gate-check="gateCheckMap"
                  @start-process="openGateProcess"
                  @open-process="openProcessInstance"
                  @tailor="onTailor"
                />
              </div>
              <div class="stage-block">
                <div class="stage-block-head">
                  <span class="stage-block-title">技术评审（TR）</span>
                  <span class="stage-block-count">{{ visibleCount(stageReviews) }}</span>
                  <span class="stage-block-hint">挂在本阶段下，一份评审 = 一场会，结论与留痕在流程里</span>
                </div>
                <MethodologyInstancePanel
                  kind="review"
                  :rows="stageReviews"
                  :all-rows="instances.reviews"
                  :mismatch-fallback="false"
                  empty-hint="本阶段没有技术评审内容"
                  :loading="instancesLoading"
                  :stage-names="stageNames"
                  :permissions="instances.permissions"
                  @start-process="openGateProcess"
                  @open-process="openProcessInstance"
                  @tailor="onTailor"
                />
              </div>
            </a-tab-pane>
            <a-tab-pane v-if="has('RULE_ENGINE')" key="rule">
              <template #tab>检查规则 ({{ visibleCount(stageRules) }})</template>
              <MethodologyInstancePanel kind="rule" :rows="stageRules" :all-rows="instances.rules" :loading="instancesLoading" :stage-names="stageNames" :permissions="instances.permissions" @tailor="onTailor" />
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

    <!-- 文档创建弹窗：先选类型 → 再按类型专属布局渲染表单（样式与新建零组件弹窗一致） -->
    <a-modal
      v-model:visible="docModalVisible"
      title="新建文档"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="docModalSaving"
      :ok-button-props="{ disabled: !selectedDocType }"
      @ok="confirmDocument"
      @cancel="resetDocModal"
      width="640px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <div style="margin-bottom:8px">
        <span style="color:#8c8c8c">选择文档类型</span>
        <div style="margin-top:4px">
          <DocumentTypeSelect
            v-model="docTypeSelectValue"
            placeholder="请选择文档类型（必选）"
            @change="onDocTypeChange"
          />
        </div>
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
          :type-definition-oid="selectedDocType?.oid"
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
      title="新建零组件"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="partModalSaving"
      :ok-button-props="{ disabled: !selectedPartType }"
      @ok="confirmPart"
      @cancel="resetPartModal"
      width="640px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <div style="margin-bottom:8px">
        <span style="color:#8c8c8c">选择零组件类型</span>
        <a-tree-select
          v-model:value="partTypeSelectValue"
          style="width:100%;margin-top:4px"
          placeholder="请选择零组件类型"
          :tree-data="partTypeOptions"
          :field-names="{ label: 'name', value: 'code', children: 'children' }"
          tree-default-expand-all
          show-search
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
          :type-definition-oid="selectedPartType?.oid"
          v-model="partForm"
        />
      </div>
      <div v-else style="padding:24px 0;text-align:center;color:#999">
        <InboxOutlined style="font-size:32px;color:#d9d9d9;margin-bottom:8px;display:block" />
        请先选择零组件类型，系统将加载对应的创建表单
      </div>
    </a-modal>

    <!-- 功能架构创建弹窗 -->
    <a-modal
      v-model:visible="functionalModalVisible"
      title="新建功能架构"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="functionalSaving"
      :ok-button-props="{ disabled: !selectedFunctionalType }"
      @ok="confirmFunctional"
      @cancel="resetFunctionalModal"
      width="640px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <div style="margin-bottom:8px">
        <span style="color:#8c8c8c">选择功能架构类型</span>
        <a-tree-select
          v-model:value="functionalTypeSelectValue"
          style="width:100%;margin-top:4px"
          placeholder="请选择功能架构类型"
          :tree-data="functionalTypeOptions"
          :field-names="{ label: 'name', value: 'code', children: 'children' }"
          tree-default-expand-all
          show-search
          @change="onFunctionalTypeChange"
        />
      </div>

      <a-divider v-if="selectedFunctionalType" style="margin:12px 0" />

      <div v-if="selectedFunctionalType" style="margin-top:8px">
        <a-tag color="blue" style="margin-bottom:12px">{{ selectedFunctionalType.name || selectedFunctionalType.code }} - 创建表单</a-tag>
        <DynamicForm
          ref="functionalFormRef"
          :key="functionalFormEntityCode"
          :entity-code="functionalFormEntityCode"
          operation-code="create"
          :fallback-entity-code="'FUNCTIONAL'"
          :current-stage-oid="activeStage"
          :stage-options="stageOptions"
          :type-definition-oid="selectedFunctionalType?.oid"
          v-model="functionalForm"
        />
      </div>
      <div v-else style="padding:24px 0;text-align:center;color:#999">
        <InboxOutlined style="font-size:32px;color:#d9d9d9;margin-bottom:8px;display:block" />
        请先选择功能架构类型，系统将加载对应的创建表单
      </div>
    </a-modal>

    <!-- 检出注释弹窗 -->
    <a-modal
      v-model:visible="checkoutModalVisible"
      :title="checkoutTargetDoc?.entityType === 'PART' ? '检出零组件'
        : checkoutTargetDoc?.entityType === 'ENG_DOCUMENT' ? '检出工程数据' : '检出文档'"
      ok-text="确认检出"
      cancel-text="取消"
      :confirm-loading="checkoutSaving"
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
        <div style="margin-top:10px;margin-bottom:4px;font-size:12px;color:#666">检出注释（可选）</div>
        <a-textarea
          v-model:value="checkoutComment"
          placeholder="请输入检出注释（如：修改方向、检出原因等，可不填）"
          :rows="3"
          :maxlength="500"
          show-count
        />
      </div>
    </a-modal>

    <!-- 历史版本弹窗 -->
    <a-modal
      v-model:visible="historyModalVisible"
      :title="historyTitle"
      :footer="null"
      width="860px"
    >
      <div style="margin-bottom:8px;display:flex;align-items:center;justify-content:space-between">
        <span style="font-size:12px;color:#8c8c8c">勾选两个版本可进行对比</span>
        <a-button
          type="primary"
          size="small"
          :disabled="historySelectedRows.length !== 2"
          @click="openCompareModal"
        >
          <template #icon><SwapOutlined /></template>
          对比所选版本 ({{ historySelectedRows.length }}/2)
        </a-button>
      </div>
      <a-table
        :columns="historyColumns"
        :data-source="historyData"
        :loading="historyLoading"
        :pagination="false"
        row-key="oid"
        size="small"
        :row-selection="historyRowSelection"
        :scroll="{ y: 420 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'displayVersion'">
            <a-tag color="blue">{{ record.displayVersion || '-' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status?.code)" size="small">
              {{ record.status?.displayName || record.status?.code || '-' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'latest'">
            <a-tag v-if="record.latest" color="green" size="small">最新</a-tag>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'checkout'">
            <a-tag v-if="record.checkedOut" color="orange" size="small">已检出: {{ record.checkedOutBy || '-' }}</a-tag>
            <a-tag v-else color="green" size="small">已检入</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            <span style="font-size:12px;color:#8c8c8c">{{ record.createdAt ? String(record.createdAt).substring(0,19).replace('T',' ') : '-' }}</span>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- 编辑弹窗（文档/零组件） -->
    <a-modal
      v-model:visible="editModalVisible"
      :title="editTitle"
      :confirm-loading="editModalSaving"
      @cancel="editModalVisible = false"
      width="640px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <DynamicForm
        ref="editFormRef"
        :key="editEntityOid + '-' + editEntityCode"
        :entity-code="editEntityCode"
        operation-code="update"
        :entity-oid="editEntityOid"
        :fallback-entity-code="editEntityType === 'DOC' ? 'DOCUMENT' : 'PART'"
        :current-stage-oid="activeStage"
        :stage-options="stageOptions"
        v-model="editForm"
      />
      <template #footer>
        <a-button @click="editModalVisible = false">取消</a-button>
        <a-button @click="confirmEdit" :loading="editModalSaving">保存</a-button>
        <a-button type="primary" @click="confirmSaveAndCheckin" :loading="editModalSaving">保存并检入</a-button>
      </template>
    </a-modal>

    <!-- 删除弹窗（选择删除范围） -->
    <a-modal
      v-model:visible="deleteModalVisible"
      :title="`删除${deleteTarget?.entityType === 'PART' ? '零组件' : '文档'}`"
      ok-text="确认删除"
      cancel-text="取消"
      :ok-button-props="{ danger: true }"
      :confirm-loading="deleteSaving"
      @ok="confirmDelete"
      @cancel="deleteModalVisible = false"
      width="480px"
    >
      <div v-if="deleteTarget" style="padding:4px 0">
        <p style="margin-bottom:16px">
          对象：<b>{{ deleteTarget.code || deleteTarget.name }}</b>
        </p>
        <a-radio-group v-model:value="deleteScope">
          <a-space direction="vertical">
            <a-radio value="all">删除所有版本（彻底删除该对象及其全部历史版本）</a-radio>
            <a-radio value="latest">删除最新小版本（仅删除当前最新版本）</a-radio>
          </a-space>
        </a-radio-group>
        <a-alert
          v-if="deleteScope === 'all'"
          type="warning"
          show-icon
          message="删除所有版本后数据不可恢复，请谨慎操作。"
          style="margin-top:16px"
        />
      </div>
    </a-modal>

    <!-- 重命名弹窗 -->
    <a-modal
      v-model:visible="renameModalVisible"
      :title="`重命名${renameTarget?.entityType === 'PART' ? '零组件' : '文档'}`"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="renameSaving"
      @ok="confirmRename"
      @cancel="renameModalVisible = false"
      width="420px"
    >
      <div v-if="renameTarget" style="padding:4px 0">
        <p style="margin-bottom:12px">
          对象：<b>{{ renameTarget.code || renameTarget.name }}</b>
        </p>
        <a-input
          v-model:value="renameName"
          placeholder="请输入新名称"
          maxlength="100"
          @pressEnter="confirmRename"
        />
      </div>
    </a-modal>

    <!-- 另存为弹窗 -->
    <a-modal
      v-model:visible="saveAsModalVisible"
      title="另存为零组件"
      ok-text="另存为"
      cancel-text="取消"
      :confirm-loading="saveAsSaving"
      @ok="confirmSaveAs"
      @cancel="saveAsModalVisible = false"
      width="420px"
    >
      <div v-if="saveAsTarget" style="padding:4px 0">
        <p style="margin-bottom:12px">
          源对象：<b>{{ saveAsTarget.code || saveAsTarget.name }}</b>
        </p>
        <a-input
          v-model:value="saveAsName"
          placeholder="请输入新名称"
          maxlength="100"
          @pressEnter="confirmSaveAs"
        />
        <p style="margin-top:12px;color:#8c8c8c;font-size:12px">
          将复制当前零组件的类型、分类、单位、来源及 IBA 属性值，生成一个新编号的对象。
        </p>
      </div>
    </a-modal>

    <!-- 移动弹窗 -->
    <a-modal
      v-model:visible="moveModalVisible"
      :title="`移动${moveTarget?.entityType === 'PART' ? '零组件' : '文档'}`"
      ok-text="确定移动"
      cancel-text="取消"
      :confirm-loading="moveSaving"
      @ok="confirmMove"
      @cancel="moveModalVisible = false"
      width="480px"
    >
      <div v-if="moveTarget" style="padding:4px 0">
        <p style="margin-bottom:16px">
          对象：<b>{{ moveTarget.code || moveTarget.name }}</b>
        </p>
        <a-form layout="vertical">
          <a-form-item label="产品系列/型号/资源库" required>
            <a-tree-select
              v-model:value="moveSelectedOwner"
              :tree-data="moveProductTree"
              :loading="moveProductLoading"
              :tree-default-expand-all="true"
              show-search
              tree-node-filter-prop="title"
              :field-names="{ label: 'title', key: 'key', value: 'value', children: 'children' }"
              placeholder="请选择产品系列 / 型号 / 资源库"
              style="width:100%"
              @change="onMoveOwnerChange"
            />
          </a-form-item>
          <!--
            阶段与文件夹只对「产品系列 / 型号」成立：
              资源库里没有研发阶段，文件夹树本身又是按「系列 + 阶段」组织的，
              所以选了资源库就一并隐藏 —— 留着空的必填"阶段"只会让人以为漏填了。
          -->
          <a-form-item v-if="!moveToLibrary" label="阶段" required>
            <a-select
              v-model:value="moveSelectedStage"
              :loading="moveStageLoading"
              placeholder="请先选择产品系列/型号"
              style="width:100%"
              @change="onMoveStageChange"
              :options="moveStages.map(s => ({ label: s.name || s.title || s.code, value: s.oid }))"
            />
          </a-form-item>
          <a-form-item v-if="moveToLibrary" label="目标">
            <span style="color:#595959">
              移入资源库「{{ moveSelectedLibraryName }}」——
              {{ moveLibUsesCategory ? '该库按分类节点归档' : '资源库没有研发阶段，可直接选文件夹' }}
            </span>
          </a-form-item>
          <!-- 按分类归档的资源库（元器件库/标准件库/通用件库）：位置 = 分类节点（来自分类管理） -->
          <a-form-item v-if="moveLibUsesCategory" label="分类">
            <a-tree-select
              v-model:value="moveSelectedCategoryOid"
              :tree-data="moveCategoryTree"
              :loading="moveCategoryLoading"
              :tree-default-expand-all="true"
              show-search
              tree-node-filter-prop="title"
              :field-names="{ label: 'title', key: 'key', value: 'value', children: 'children' }"
              :placeholder="moveCategoryTree.length ? '请选择分类节点' : '该资源库尚未绑定分类，请在业务配置中绑定'"
              allow-clear
              style="width:100%"
            />
          </a-form-item>
          <!-- 文件夹：系列/型号按"系列 + 阶段"组织，按文件夹组织的资源库按"库 + 库阶段"组织 -->
          <a-form-item v-if="!moveLibUsesCategory" label="文件夹">
            <a-tree-select
              v-model:value="moveSelectedFolder"
              :tree-data="moveFolderTree"
              :loading="moveFolderLoading"
              :tree-default-expand-all="true"
              show-search
              tree-node-filter-prop="title"
              :field-names="{ label: 'name', key: 'oid', value: 'oid', children: 'children' }"
              :placeholder="moveFolderPlaceholder"
              style="width:100%"
            />
          </a-form-item>
        </a-form>
      </div>
    </a-modal>

    <!-- 版本对比弹窗 -->
    <a-modal
      v-model:visible="compareModalVisible"
      title="版本对比"
      :footer="null"
      width="860px"
    >
      <div
        v-for="group in compareGroups"
        :key="group.key"
        class="compare-group"
      >
        <div class="compare-group-title">{{ group.title }}</div>
        <a-table
          v-if="group.rows.length > 0"
          :columns="compareColumns"
          :data-source="group.rows"
          :pagination="false"
          row-key="field"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'field'">
              <span style="font-weight:500">{{ record.field }}</span>
            </template>
            <template v-else-if="column.key === 'left'">
              <span :style="record.diff ? 'color:#fa541c;font-weight:500' : ''">{{ record.left || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'right'">
              <span :style="record.diff ? 'color:#fa541c;font-weight:500' : ''">{{ record.right || '-' }}</span>
            </template>
          </template>
        </a-table>
        <a-empty v-else description="无该类型属性" :image-style="{ height: '24px' }" />
      </div>
    </a-modal>

    <!--
      新建工程数据（ENG_DOCUMENT）：产品数模的唯一入口。
      刻意不复用「新建文档」弹窗 —— 数模必须落在 ck_eng_document 上，
      建通用文档（ck_document）虽然也能挂文件，但它没有 cadName/cadType/cadTool，
      也不会出现在本页签列表里。
    -->
    <a-modal
      v-model:visible="engDocModalVisible"
      title="新建工程数据"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="engDocSaving"
      width="640px"
      centered
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
      @ok="confirmEngDocument"
      @cancel="resetEngDocModal"
    >
      <a-alert
        type="info"
        show-icon
        banner
        style="margin-bottom:12px"
        message="工程数据（ENG_DOCUMENT）用于承载三维数模：可直接上传 CATIA / Creo / NX / SolidWorks 原生数模归档；上传 STEP / IGES / STL 后系统会转换成轻量化文件，生成三维预览"
      />
      <DynamicForm
        ref="engDocFormRef"
        entity-code="ENG_DOCUMENT"
        operation-code="create"
        :current-container-oid="line?.oid"
        :current-stage-oid="activeStage"
        :stage-options="stageOptions"
        v-model="engDocForm"
      />
    </a-modal>

    <!--
      批量生成预览的结果。
      只列"没进去的"（跳过 / 失败）：一次勾 30 份，把 28 条成功也逐条列出来，
      用户反而找不到那 2 条要处理的。
    -->
    <a-modal
      v-model:open="cadBatchResultVisible"
      title="批量生成轻量化预览"
      :footer="null"
      width="640px"
      centered
    >
      <div v-if="cadBatchResult" class="pl-batch-result">
        <div class="pl-batch-summary">
          共 <b>{{ cadBatchResult.total }}</b> 份：
          已入队 <b class="ok">{{ cadBatchResult.accepted }}</b>、
          命中缓存 <b>{{ cadBatchResult.cached }}</b>、
          跳过 <b class="warn">{{ cadBatchResult.skipped }}</b>、
          失败 <b class="err">{{ cadBatchResult.failed }}</b>
        </div>
        <a-alert
          type="info"
          show-icon
          :closable="false"
          style="margin:10px 0"
          message="转换在后台排队执行。整体进度、排队位次与失败原因可在「数模转换」页面查看。"
        />
        <a-table
          v-if="cadBatchProblemItems.length"
          :data-source="cadBatchProblemItems"
          :pagination="false"
          size="small"
          row-key="ckfileOid"
          :columns="[
            { title: '文件', key: 'file' },
            { title: '结果', key: 'result', width: 80 },
            { title: '原因', key: 'message' }
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'file'">
              <span :title="record.ckfileOid">{{ cadFileNameOf(record.ckfileOid) }}</span>
            </template>
            <template v-else-if="column.key === 'result'">
              <a-tag :color="record.result === 'SKIPPED' ? 'warning' : 'error'">
                {{ record.result === 'SKIPPED' ? '跳过' : '失败' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'message'">
              <span class="pl-batch-msg" :title="record.message">{{ record.message || '—' }}</span>
            </template>
          </template>
        </a-table>
        <div v-else class="pl-batch-ok">全部提交成功</div>
      </div>
    </a-modal>

    <!-- 三维浏览已改为独立页面（/product/cad-view/:iterationOid）：弹窗装不下复杂装配体 -->

    <!-- 文档详情查看器 -->
    <DocumentViewer
      v-model:visible="docViewerVisible"
      :doc="docViewerDoc"
      @close="docViewerVisible = false"
    />

    <!-- 交付件产出物关联（写入口唯一：只从交付件面板打开，见 MethodologyInstancePanel 注释） -->
    <DeliverableArtifactModal
      v-model:open="artifactModalOpen"
      :deliverable="artifactDeliverable"
      :owner-oid="line?.oid"
      @changed="onArtifactChanged"
    />

    <!-- 发起流程（共享弹框：按类型+状态解析该发起的流程，再经 Flowable 启动）
         process-key 非空 = 阶段门 / 评审行的"直传"发起（跳过类型-状态解析）；
         preset = 这条门/评审行已经在申请单里定好的字段（评审模板 / 阶段 / 门） -->
    <StartProcessModal
      v-model:visible="startProcessVisible"
      :business="startProcessTarget"
      :process-key="startProcessKey"
      :preset="startProcessPreset"
      @success="onProcessStarted"
    />

    <!-- 设置生命周期状态（共享弹框：按类型绑定的生命周期模板给出"初始状态 / 指定状态"两条路） -->
    <SetLifecycleStateModal
      v-model:visible="lifecycleVisible"
      :record="lifecycleTarget"
      @success="refreshStageLists"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  AuditOutlined, BulbOutlined, FundProjectionScreenOutlined, ToolOutlined,
  RocketOutlined, ShoppingCartOutlined,
  TeamOutlined, GoldOutlined, ExperimentOutlined, CaretDownOutlined,
  FolderOutlined, FolderAddOutlined,
  FileTextOutlined, InboxOutlined, PlusOutlined, LinkOutlined, EyeOutlined,
  PictureOutlined, LockFilled,
  DeleteOutlined, CopyOutlined, DownOutlined,
  EditOutlined, CheckOutlined, SwapOutlined, SendOutlined, LockOutlined, RollbackOutlined, DownloadOutlined, HistoryOutlined,
  BranchesOutlined, FormOutlined, BarChartOutlined, InfoCircleOutlined,
} from '@ant-design/icons-vue'
import { getProductLine, getProductLineChildren, getProductLineStats, getFolderTree, createFolder, updateFolder, deleteFolder,
         getDocuments, createDocument, deleteDocument, deleteDocumentLatestIteration, renameDocument, moveDocumentApi, checkoutDocument as checkoutDocApi, undoCheckoutDocument as undoCheckoutApi,
         checkoutPart as checkoutPartApi, undoCheckoutPart as undoCheckoutPartApi, checkinPart as checkinPartApi, checkinDocument as checkinDocApi,
         // 工程数据（ENG_DOCUMENT）的检出三兄弟：数模列表的行对象是 EPMDocument，不是 DOCUMENT
         checkoutEngineeringDocument, checkinEngineeringDocument, undoCheckoutEngineeringDocument,
         getStages, getFolderDocumentDetails, getDocumentDownloadUrl, createPart, createFunctional, getTypeDefinitionTree, getPartsByFolder, deletePart, deletePartLatestIteration, renamePart, saveAsPart, movePart,
         getPartIterations, getDocumentIterations, getClassification, getClassificationIBAs,
         updatePart, updateDocument, getProductLineTree, getProductModels, getResourceChildren,
         getLibraryCategoryTree, getElectronicComponentContext, getPackageSymbolContext,
         reviseObject, resolveReviseEntityType, resolveEntityApiPath,
        listMethodologyInstances, getDeliverableArtifactSummary, tailorInstance, getIssueGateCheck,
        getBomMaterials, getContainerCadModels, convertCadModel, retryCadVisualization, convertCadBatch,
        getCadViewerAvailable, ckFileStreamUrl, createSoftTypeInstance, getCKFile, downloadContainerCadModels } from '@/api'
import { useUserStore } from '@/stores/user'
import FolderTreeNode from './FolderTreeNode.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import DocumentTypeSelect from '@/components/DocumentTypeSelect.vue'
import DataTable from '@/components/DataTable.vue'
import DocumentViewer from './DocumentViewer.vue'
// 三维查看器已迁到独立页面 CadViewerPage.vue（按路由懒加载），本页不再引它
import DeliverableArtifactModal from '@/components/DeliverableArtifactModal.vue'
import StartProcessModal from '@/components/StartProcessModal.vue'
import SetLifecycleStateModal from '@/components/SetLifecycleStateModal.vue'
import MethodologyInstancePanel from '@/components/MethodologyInstancePanel.vue'
import { useCapabilities } from '@/composables/useCapabilities'
import { recordOperation } from '@/composables/useActivity'
import { registerDynamicStages } from '@/utils/stageDefs'
import { isDecisionGate } from '@/utils/gateDefs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const line = ref(null)
const children = ref([])
const modelCount = ref(0)
const showChildren = ref(false)
const activeStage = ref(null) // 存储当前阶段的 oid
// ---- 方法论实例（六类）与能力开关 ----
// 能力来自 ck_tenant_capability（包里声明且平台已实现的能力才会在账本里）→ 决定入口显不显示；
// 六类实例一次取回（GET /api/instances），阶段过滤在本地做 —— 切阶段不发请求。
const { load: loadCapabilities, has } = useCapabilities()
const instances = ref({ deliverables: [], gates: [], reviews: [], rules: [], metrics: [], dashboards: [], permissions: null })
const instancesLoading = ref(false)
// 阶段内视图：bom（物料清单）| docs（研发资料，2026-10-11 由"过程资料"定名）| deliverable | gate | rule
// 2026-10-23：默认页签由 docs 改为 bom —— 进详情页第一眼该看到物料清单（BOM 是最核心的结构信息）
// 2026-10-29：评审页签并入「阶段门」（技术门展开看评审），见下方别名表
/** 全部页签 key（含按能力隐藏的几个，用于校验 URL 里的 tab） */
const STAGE_VIEW_KEYS = ['bom', 'cad', 'docs', 'inherited', 'own', 'deliverable', 'gate', 'rule']

/**
 * 旧链接兼容：`?tab=review` 一律落到「阶段门」。
 *
 * <p>为什么要留：页签 key 记在 URL 上（见下面的注释），用户的书签、作战地图的下钻、
 * 聊天里发过的链接都可能还带着 `tab=review`。直接把 review 从白名单里删掉，
 * 这些链接会静默退回 bom —— 点了「评审」却落到物料清单，比报错更难排查。
 */
const STAGE_VIEW_ALIASES = { review: 'gate' }

function normalizeStageView(tab) {
  const raw = String(tab || '')
  const key = STAGE_VIEW_ALIASES[raw] || raw
  return STAGE_VIEW_KEYS.includes(key) ? key : 'bom'
}

/**
 * 页签状态记在 URL 上（?tab=cad）。
 *
 * <p>为什么不能只放内存：任何一次整页重载（F5、前端热更新、外链跳回）都会把
 * {@code ref('bom')} 打回默认值 —— 用户在产品数模里做完检出/撤销检出，
 * 回来发现自己被扔回物料清单，看着就像"操作把页面刷了"。
 */
const stageView = ref(normalizeStageView(route.query.tab))

// 页签 → URL（replace，不往浏览器历史里塞一堆 tab）
watch(stageView, (view) => {
  if (String(route.query.tab || '') === view) return
  router.replace({ query: { ...route.query, tab: view } })
})

// URL → 页签（浏览器前进/后退、外部链接带 tab）
watch(() => route.query.tab, (tab) => {
  const next = normalizeStageView(tab)
  if (next !== stageView.value) stageView.value = next
})

// ---- 物料清单（新页签）----
// 与阶段 / 文件夹无关：容器下所有零件的最新大版本 · 最新小版本，一行一个零件；
// 用量与「使用于」来自 BOM 汇总（没有 BOM 行时 0 / —）。
// 懒加载：零件与 BOM 行动辄上百条，看研发资料时没必要先查一遍。
const bomMaterials = ref([])
const bomMaterialsLoading = ref(false)
const bomLoaded = ref(false)

/**
 * 物料清单列：与「研发资料」列表同一套列（2026-10-22 用户要求对齐）。
 *
 * 一行 = 本系列 / 型号下的一个零件（最新大版本 · 最新小版本），
 * 不再是"BOM 行的物料汇总"——所以操作列直接用零件自己的能力（检出/检入/修订…）。
 */
const bomMaterialColumns = [
  { title: '', dataIndex: 'checkedOut', key: 'checkout_status', width: 32, align: 'center' },
  // 缩略图放最前（与「产品数模」同口径）：扫列表先认形状，再看编码/版本
  { title: '缩略图', key: 'thumb', width: 64 },
  { title: '编码', dataIndex: 'code', key: 'code', width: 110 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true, width: 150 },
  { title: '类型', dataIndex: 'typeDefinitionName', key: 'typeDefinitionName', width: 100 },
  { title: '版本', dataIndex: 'displayVersion', key: 'displayVersion', width: 60 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 50 },
  { title: '生命周期', key: 'status', width: 90 },
  { title: '检出', key: 'checkout', width: 80 },
  { title: '操作', key: 'action', width: 88, fixed: 'right' },
]

/** 用量：整数原样、小数最多两位 —— 用量是 DOUBLE，直接渲染会冒出 1.2000000000000002 */
function formatQuantity(value) {
  if (value == null) return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}

// ---- 产品数模（新页签）----
// 与物料清单同口径：按容器整体聚合一次，不随阶段过滤。
// 一行 = 一个工程文档最新迭代上的一个 CAD 源文件 + 它最近一次的轻量化状态；
// 没有转换记录的行 status 为空（未转换），而不是被过滤掉 ——
// 否则用户看到的是"这个产品没有数模"，而事实是"有数模但还没生成预览"。
const cadModels = ref([])
const cadModelsLoading = ref(false)
const cadModelsLoaded = ref(false)
/** 转换器是否部署：未部署时隐藏"生成预览"，只留下载（不给用户永远转不完的加载圈） */
const cadViewerAvailable = ref(false)
/** 正在提交转换的源文件 oid —— 按钮 loading 兼防重复点击 */
const cadSubmitting = ref('')

const cadModelColumns = [
  // 缩略图放最前：用户扫列表时先认形状，再看编码/版本 —— 和文件管理器一个道理
  { title: '缩略图', key: 'thumb', width: 72 },
  { title: '工程数据', key: 'doc', width: 240, ellipsis: true },
  { title: '数模文件', key: 'file', ellipsis: true },
  { title: '版本', dataIndex: 'iterationVersion', key: 'version', width: 80 },
  // 检出状态与物料清单同口径（挂在迭代上）：检出 = 独占编辑锁，
  // 不显示的话用户会去点一个注定失败的编辑动作
  { title: '检出', key: 'checkout', width: 130 },
  { title: '轻量化状态', key: 'status', width: 130 },
  { title: '模型信息', key: 'model', width: 150 },
  { title: '操作', key: 'action', width: 150 },
]

const loadCadModels = async (force = false, silent = false) => {
  const containerOid = line.value?.oid
  if (!containerOid) {
    cadModels.value = []
    return
  }
  if (cadModelsLoaded.value && !force) return
  if (!silent) cadModelsLoading.value = true
  try {
    const res = await getContainerCadModels(containerOid)
    cadModels.value = (res.code === 200 && res.data) ? res.data : []
  } catch {
    if (!silent) cadModels.value = []
  } finally {
    if (!silent) cadModelsLoading.value = false
  }
  cadModelsLoaded.value = true
  if (cadModels.value.some(m => m.status === 'QUEUED' || m.status === 'RUNNING')) startCadPolling()
}

/** 转换器可用性：进页签查一次即可，部署状态不会在会话中途变化 */
const loadCadAvailability = async () => {
  try {
    const res = await getCadViewerAvailable()
    cadViewerAvailable.value = !!(res.code === 200 && res.data?.available)
  } catch {
    cadViewerAvailable.value = false
  }
}

/**
 * 只在"排队中 / 转换中"时开轮询，跑完立刻停。
 * 常驻定时器会让一个打开着的看板永远在发请求 —— 这是看板页最常见的性能投诉来源。
 */
const CAD_POLL_INTERVAL = 4000
let cadPollTimer = null

function stopCadPolling() {
  if (cadPollTimer) {
    clearInterval(cadPollTimer)
    cadPollTimer = null
  }
}

function startCadPolling() {
  if (cadPollTimer) return
  cadPollTimer = setInterval(async () => {
    const pending = cadModels.value.some(m => m.status === 'QUEUED' || m.status === 'RUNNING')
    if (!pending) {
      stopCadPolling()
      return
    }
    await loadCadModels(true, true)
  }, CAD_POLL_INTERVAL)
}

/** 转换状态 → 标签文案/颜色/提示；status 为空是"未转换"，与 SKIPPED（重试无用）严格区分 */
function cadStatusMeta(record) {
  switch (record?.status) {
    case 'SUCCESS':
      return {
        text: '已生成',
        color: 'success',
        tip: record.convertedAt
          ? `完成于 ${String(record.convertedAt).replace('T', ' ').slice(0, 19)}`
          : '轻量化产物已就绪'
      }
    case 'QUEUED':
      return { text: '排队中', color: 'default', tip: '已提交转换器，等待处理（转换器并发有限，前一个完成后自动开始）' }
    case 'RUNNING': {
      // 阶段名由转换器给（如「网格化零件 42/118」），比"正在处理"有用得多
      const stage = record.stage ? `：${record.stage}` : ''
      const pct = record.progress != null ? `（${record.progress}%）` : ''
      return { text: '转换中', color: 'processing', tip: `转换器正在处理${stage}${pct}` }
    }
    case 'FAILED':
      return { text: '转换失败', color: 'error', tip: record.errorMessage || '转换失败，可重新转换' }
    case 'TIMEOUT':
      return { text: '转换超时', color: 'error', tip: record.errorMessage || '转换超时，可重新转换' }
    case 'SKIPPED':
      return {
        text: '已跳过',
        color: 'warning',
        tip: record.errorMessage || '转换器判定该文件无需/无法转换，重试不会改变结果'
      }
    default:
      // 原生数模（CATIA/Creo/NX/SolidWorks）不会去转换 —— 状态空着不代表"漏转"
      if (!isConvertibleSource(record)) {
        return {
          text: '仅归档',
          color: 'default',
          tip: '原生数模只归档保存，转换器读不了；需要三维预览请另存 STEP 后再上传'
        }
      }
      return { text: '未转换', color: 'default', tip: '还没生成轻量化预览' }
  }
}

/** 列表里的进度文案：阶段名优先，百分比作为补充；两者都没有就退回状态词 */
function cadProgressText(record) {
  if (record?.status === 'QUEUED') return '排队中'
  const stage = record?.stage || '转换中'
  return record?.progress != null ? `${stage} · ${record.progress}%` : stage
}

/** 扩展名标签：STEP / IGES 同族但不同工具导出，颜色区分一眼可辨 */
function cadFormatLabel(fileName) {
  const name = String(fileName || '')
  const dot = name.lastIndexOf('.')
  return dot >= 0 ? name.slice(dot + 1).toUpperCase() : 'CAD'
}

function cadFormatColor(fileName) {
  const ext = cadFormatLabel(fileName)
  if (ext === 'STL') return 'green'
  if (ext === 'STEP' || ext === 'STP') return 'blue'
  return 'purple'
}

function formatSize(bytes) {
  if (bytes == null) return '—'
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

function formatCount(n) {
  const v = Number(n)
  return Number.isFinite(v) ? v.toLocaleString('zh-CN') : '—'
}

/**
 * 提交轻量化转换。
 *
 * 后端是异步语义：立刻返回 QUEUED，结果靠轮询/回调推进，不要在这里等。
 * 幂等：源文件内容未变且已有成功产物时后端直接复用，所以"生成预览"重复点也安全。
 */
async function convertCad(record) {
  if (!record?.iterationOid || !record?.sourceCkfileOid) return
  if (cadSubmitting.value) return
  cadSubmitting.value = record.sourceCkfileOid
  try {
    const res = await convertCadModel({
      engDocIterationOid: record.iterationOid,
      sourceCkfileOid: record.sourceCkfileOid
    })
    if (res.code !== 200) {
      message.error(res.message || '提交转换失败')
      return
    }
    message.success(res.data?.status === 'SUCCESS' ? '已有可复用的轻量化产物' : '已提交转换，完成后自动刷新')
    await loadCadModels(true, true)
    startCadPolling()
  } catch (e) {
    message.error(e?.response?.data?.message || '提交转换失败')
  } finally {
    cadSubmitting.value = ''
  }
}

// ---- 批量生成轻量化预览 ----

/**
 * 勾选行 → 一次提交。
 *
 * <p>为什么不循环调单条接口：N 次 HTTP、N 个 message 提示，中途失败也没法汇总。
 * 后端 `/convert-batch` 一次返回分类计数（已提交 / 命中缓存 / 跳过 / 失败）。
 *
 * <p>勾选状态按 <b>sourceCkfileOid</b> 记：它是这张表的 row-key，
 * 而 cadVisOid 在"还没转换过"的行上是空的。
 */
const cadSelectedKeys = ref([])
const cadSelectedRows = ref([])

const cadRowSelection = computed(() => ({
  selectedRowKeys: cadSelectedKeys.value,
  onChange: (keys, rows) => {
    cadSelectedKeys.value = keys
    cadSelectedRows.value = rows
  },
  // 缺迭代或缺源文件的行提交必然 400，直接禁掉勾选，别让用户勾了再吃错误提示
  getCheckboxProps: (record) => ({
    disabled: !record?.iterationOid || !record?.sourceCkfileOid
  })
}))

const cadBatchSubmitting = ref(false)
const cadBatchResultVisible = ref(false)
const cadBatchResult = ref(null)

async function batchConvertCad() {
  if (!cadSelectedRows.value.length || cadBatchSubmitting.value) return
  cadBatchSubmitting.value = true
  try {
    const items = cadSelectedRows.value.map((r) => ({
      iterationOid: r.iterationOid,
      ckfileOid: r.sourceCkfileOid
    }))
    const res = await convertCadBatch(items)
    if (res.code !== 200) {
      message.error(res.message || '批量提交失败')
      return
    }
    cadBatchResult.value = res.data
    cadBatchResultVisible.value = true
    cadSelectedKeys.value = []
    cadSelectedRows.value = []
    await loadCadModels(true, true)
    startCadPolling()
  } catch (e) {
    message.error(e?.response?.data?.message || '批量提交失败')
  } finally {
    cadBatchSubmitting.value = false
  }
}

/** 明细里只列"没进去的"（跳过/失败）：成功的逐条列出来只是噪音 */
const cadBatchProblemItems = computed(() => {
  const items = cadBatchResult.value?.items || []
  return items.filter((i) => i.result === 'SKIPPED' || i.result === 'FAILED')
})

/** 从当前列表反查文件名 —— 提交结果里只带 oid，直接展示 oid 用户认不出是哪份 */
function cadFileNameOf(ckfileOid) {
  const hit = cadModels.value.find((m) => m.sourceCkfileOid === ckfileOid)
  return hit?.fileName || ckfileOid
}

/** 失败 / 超时后手动重试：走 retry 接口（保留尝试次数）；没有记录时退回重新提交 */
async function retryCad(record) {
  if (!record?.cadVisOid) return convertCad(record)
  if (cadSubmitting.value) return
  cadSubmitting.value = record.sourceCkfileOid
  try {
    const res = await retryCadVisualization(record.cadVisOid)
    if (res.code !== 200) {
      message.error(res.message || '重试失败')
      return
    }
    message.success('已重新提交转换')
    await loadCadModels(true, true)
    startCadPolling()
  } catch (e) {
    message.error(e?.response?.data?.message || '重试失败')
  } finally {
    cadSubmitting.value = ''
  }
}

/** 下载源文件（数模本体，不是轻量化产物） */
function downloadCadSource(record) {
  if (!record?.sourceCkfileOid) return
  window.open(ckFileStreamUrl(record.sourceCkfileOid), '_blank')
}

/**
 * 「下载装配数模」：本容器下全部数模源文件打成一个 zip。
 *
 * 为什么不是逐个 window.open：浏览器会把连续弹窗当广告拦掉，
 * 用户最后拿到几个算几个 —— 这种"看起来跑了但少了一半"最难排查。
 */
async function downloadCadAssembly() {
  const containerOid = line.value?.oid
  if (!containerOid) return
  message.loading({ content: '正在打包本产品下的数模…', key: 'cadPackage', duration: 0 })
  try {
    const blob = await downloadContainerCadModels(containerOid)
    if (!(blob instanceof Blob) || blob.size === 0) {
      throw new Error('空包')
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${line.value.code || 'cad-models'}.zip`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    message.success({ content: '已开始下载装配数模', key: 'cadPackage' })
  } catch (e) {
    // 404 = 本产品下没有任何可下载的数模文件（后端明确拒发空包）
    message.error({ content: '下载装配数模失败：本产品下没有可打包的数模文件', key: 'cadPackage' })
  }
}

/**
 * 产品数模行的操作分发 —— 与「物料清单」同一套动作语义。
 *
 * 检出 / 检入 / 撤销检出 三件事针对的是<b>工程数据（ENG_DOCUMENT）</b>，
 * 不是通用文档：数模的载体是 EPMDocument，走 DOCUMENT 的接口会报"对象不存在"。
 */
function handleCadModelAction({ key }, record) {
  switch (key) {
    case 'view3d':
      openCadView(record)
      break
    case 'download':
      downloadCadSource(record)
      break
    case 'downloadAssembly':
      downloadCadAssembly()
      break
    case 'checkout':
      // 用 engDocOid / engDocNumber：行的 oid 是源文件 oid（row-key=sourceCkfileOid），
      // code/name 也要换成工程数据的，否则提示语里会冒出 "undefined undefined"
      checkoutTargetDoc.value = {
        ...record,
        oid: record.engDocOid,
        entityType: 'ENG_DOCUMENT',
        code: record.engDocNumber,
        name: record.engDocName || record.fileName,
      }
      checkoutComment.value = ''
      checkoutThenEdit.value = false
      checkoutModalVisible.value = true
      break
    case 'checkin':
      checkinRecord(asEngDoc(record))
      break
    case 'undoCheckout':
      undoCheckoutRecord(asEngDoc(record))
      break
    case 'lifecycle':
      openLifecycle({
        oid: record.engDocOid,
        name: record.engDocName || record.fileName,
        code: record.engDocNumber,
        typeDefinitionCode: 'ENG_DOCUMENT',
        statusCode: record.iterationStatus,
      })
      break
    case 'convert':
      convertCad(record)
      break
    case 'retry':
      retryCad(record)
      break
  }
}

/** 数模行 → 工程数据实体（检入 / 撤销检出按 ENG_DOCUMENT 分派，提示语用工程数据编码） */
function asEngDoc(record) {
  return {
    ...record,
    oid: record.engDocOid,
    entityType: 'ENG_DOCUMENT',
    code: record.engDocNumber,
    name: record.engDocName || record.fileName,
  }
}

// ---- 三维浏览：跳独立页面 ----
/**
 * 三维浏览不再开弹窗。
 *
 * <p>装配体一复杂，弹窗的可视区域、结构树宽度都不够，还会和页面滚动条互相挤压；
 * 改成整页工作台（生成、转换状态、结构树都在同一页里看）。
 * 页面自己按 iterationOid 查最新转换记录，所以刷新 / 直接粘贴 URL 都能打开。
 */
function openCadView(record) {
  if (!record?.iterationOid) return
  router.push({
    name: 'CadViewer',
    params: { iterationOid: record.iterationOid },
    query: {
      code: record.engDocNumber || undefined,
      title: record.fileName || undefined,
      version: record.iterationVersion || undefined,
      // 页面顶部「下载数模」用；查询参数里冗余存一份，刷新后仍可用
      fileOid: record.sourceCkfileOid || undefined
    }
  })
}

/**
 * 这一行能不能进三维浏览 —— 只有"已生成且拿到 GLB"才行。
 *
 * 缩略图点击入口与操作菜单里的「三维浏览」共用这一个判断：
 * 两处各写一遍条件，早晚会出现"图能点、菜单却置灰"的不一致。
 */
function canOpenCadView(record) {
  return record?.status === 'SUCCESS' && !!record?.glbCkfileOid
}

/** 点缩略图进三维浏览（缩略图是列表里最自然的点击目标） */
function onCadThumbClick(record) {
  if (!canOpenCadView(record)) return
  openCadView(record)
}

// ---- 新建工程数据（ENG_DOCUMENT）----
// 走统一创建入口 POST /softtype-instances：后端按 type_definition.root_type_code
// 路由到 EngineeringDocumentServiceImpl（落 ck_eng_document + ck_eng_document_iteration），
// 前端不再硬编码实体端点。表单由 ENG_DOCUMENT/create 的 page_layout 渲染。
const engDocModalVisible = ref(false)
const engDocSaving = ref(false)
const engDocForm = ref({})
const engDocFormRef = ref(null)
/** 打开弹窗时「所属产品」的默认值，用于判断用户是否手动改过 */
let engDocDefaultOwnerOid = ''

function openCreateEngDocument() {
  const isModel = line.value?.nodeType === 'PRODUCT_MODEL'
  const defaultOwner = line.value?.oid || ''
  engDocDefaultOwnerOid = defaultOwner
  engDocForm.value = {
    // 与「新建文档」同一容器口径：型号页的工程数据落在父系列上
    containerOid: (isModel ? (line.value?.parentOid || line.value?.oid) : line.value?.oid) || undefined,
    containerType: isModel ? 'PRODUCT_MODEL' : 'PRODUCT_LINE',
    stageOid: activeStage.value || stageDefs.value[0]?.oid || undefined,
    folderOid: selectedFolder.value?.oid || undefined,
    // 基准布局里「所属产品」绑的是 ownerOid（不是 containerOid）——
    // 不预填这一笔，下拉框永远是空的（2026-10-22 反馈）。
    // productOid 是设计器改过布局时可能出现的另一种写法，一并预填。
    ownerOid: defaultOwner || undefined,
    productOid: defaultOwner || undefined,
    typeDefinitionCode: 'ENG_DOCUMENT'
  }
  engDocModalVisible.value = true
}

function resetEngDocModal() {
  engDocForm.value = {}
  engDocDefaultOwnerOid = ''
  engDocModalVisible.value = false
}

async function confirmEngDocument() {
  const errors = engDocFormRef.value?.validate?.() || []
  if (errors.length > 0) {
    message.warning(errors[0])
    return
  }
  const name = engDocForm.value.name?.trim()
  if (!name) {
    message.warning('请输入名称')
    return
  }
  const payload = { ...(engDocFormRef.value?.getFormData?.() || engDocForm.value) }
  // 分类 IBA 值从顶层剔除、单独放 clsIbaValues —— 否则会被当成实体 IBA 写进 ck_type_iba_data
  if (engDocFormRef.value) {
    const clsIbaFieldNames = engDocFormRef.value.getClsIbaFieldNames?.() || []
    clsIbaFieldNames.forEach(k => delete payload[k])
    const clsIbaValues = engDocFormRef.value.getClsIbaValues?.() || {}
    if (Object.keys(clsIbaValues).length > 0) payload.clsIbaValues = clsIbaValues
  }
  // 容器/阶段以当前页面上下文为准（表单里允许改文件夹，但不允许把数模建到别的产品下）
  payload.name = name
  payload.typeDefinitionCode = payload.typeDefinitionCode || 'ENG_DOCUMENT'
  // 「所属产品」是 product-select 绑 ownerOid：用户改选后要真的落到容器列，
  // 否则下拉改了、存下来的还是打开弹窗时的容器（显示与保存不一致）。
  // 没改过就保持页面口径（型号页落在父系列上）。
  const pickedOwner = engDocForm.value.ownerOid || engDocForm.value.productOid || ''
  const pickedType = engDocForm.value.containerType || engDocForm.value.productType ||
    (line.value?.nodeType === 'PRODUCT_MODEL' ? 'PRODUCT_MODEL' : 'PRODUCT_LINE')
  if (pickedOwner && pickedOwner !== engDocDefaultOwnerOid) {
    payload.containerOid = pickedOwner
    payload.containerType = pickedType
  } else {
    payload.containerOid = engDocForm.value.containerOid
    payload.containerType = pickedType
  }
  // ownerOid / productOid 只是布局字段名，工程数据的归属列是 containerOid —— 别把别名写进实体
  delete payload.ownerOid
  delete payload.productOid
  delete payload.productType
  payload.stageOid = activeStage.value
  if (engDocForm.value.folderOid) payload.folderOid = engDocForm.value.folderOid

  engDocSaving.value = true
  try {
    const res = await createSoftTypeInstance(payload)
    if (res?.code !== 200) {
      message.error(res?.message || '创建工程数据失败')
      return
    }
    message.success(`已创建：${res.data?.number || res.data?.name || name}`)
    engDocModalVisible.value = false
    await loadCadModels(true, true)
    // STEP / IGES / STL 上传后直接送转换：用户已经在表单里选了数模文件，
    // 不该再让他回到列表里点一次「生成预览」
    await autoConvertEngDocument(res.data, payload.ckfileOid)
  } catch (e) {
    message.error(e?.response?.data?.message || '创建工程数据失败')
  } finally {
    engDocSaving.value = false
  }
}

/** 轻量化转换器认识的中性交换格式（原生数模不在其中，后端只会 SKIPPED） */
const CONVERTIBLE_EXT = ['.step', '.stp', '.iges', '.igs', '.stl']

/**
 * 该数模的源文件是否可转换。
 * 原生数模（CATIA / Creo / NX / SolidWorks）与只有网络地址的文件都不送转换 ——
 * 转换器读不了，提交只会得到一条"不支持的格式"。
 */
function isConvertibleSource(record) {
  const name = String(record?.fileName || '').toLowerCase()
  return CONVERTIBLE_EXT.some(ext => name.endsWith(ext))
}

/**
 * 新建工程数据后自动送一次轻量化转换。
 *
 * <p>只对 STEP / IGES / STL 提交：CATIA / Creo / NX / SolidWorks 原生数模转换器读不了，
 * 提交也只会落一条 SKIPPED 记录，反让列表多出"不支持"的噪音。
 * <p>失败不阻断创建 —— 列表里仍可手动点「生成预览」。
 */
async function autoConvertEngDocument(created, sourceCkfileOid) {
  const iterationOid = created?.iterationOid
  const ckfileOid = sourceCkfileOid || created?.ckfileOid
  if (!iterationOid || !ckfileOid || !cadViewerAvailable.value) return
  try {
    const info = await getCKFile(ckfileOid)
    const fileName = String(info?.data?.fileName || '').toLowerCase()
    if (!CONVERTIBLE_EXT.some(ext => fileName.endsWith(ext))) return
    const res = await convertCadModel({ engDocIterationOid: iterationOid, sourceCkfileOid: ckfileOid })
    if (res?.code !== 200) return
    await loadCadModels(true, true)
    startCadPolling()
  } catch {
    /* 自动转换失败不阻断创建 */
  }
}

onBeforeUnmount(stopCadPolling)

/** 阶段 code → 阶段名（与阶段页签同一份数据，面板里显示中文阶段名） */
const stageNames = computed(() => Object.fromEntries(stageDefs.value.map(s => [s.key, s.title])))

/** 当前阶段的 code（stageDefs 的 key 即阶段 code，实例的 stageKey 与它同源） */
const activeStageCode = computed(() => stageDefs.value.find(s => s.oid === activeStage.value)?.key || null)

/**
 * 我能不能看见「本项目不适用」（被裁剪）的条目 —— 与 MethodologyInstancePanel 的
 * {@code canSeeTailored} **同一口径**：只有本项目团队里的产品经理（canTailor）与管理员。
 * 面板据此决定行的显隐，页签计数必须跟着一致，否则会"写 9 条、只看到 6 条"。
 */
const canSeeTailored = computed(() => {
  const p = instances.value.permissions || {}
  return !!(p.canTailor || p.admin)
})

/** 页签计数：与面板实际渲染的行数一致（非产品经理不计被裁剪的条目） */
function visibleCount(list) {
  return canSeeTailored.value ? list.length : list.filter((r) => !r.disabled).length
}

const stageDeliverables = computed(() => instances.value.deliverables.filter(d => d.stageKey === activeStageCode.value))
/**
 * 阶段门 = 决策门（DCP）。技术评审已提到阶段层（2026-10-29 扁平化），不再有"技术门"这一层，
 * 因此这里排掉老数据里残留的 TECHNICAL_REVIEW 门行 —— 它们在界面上不再占一行，
 * 对应的评审由下面的 stageReviews 直接平铺出来。
 */
const stageGates = computed(() => instances.value.gates.filter(
  g => g.stageKey === activeStageCode.value && g.type !== 'TECHNICAL_REVIEW'))
/** 规则：本阶段的 + 不限定阶段的（后者每个阶段都该看到） */
const stageRules = computed(() => instances.value.rules.filter(r => !r.stageKey || r.stageKey === activeStageCode.value))
/**
 * 技术评审：新数据直接带 stageKey（挂阶段）；老数据只有 gate（挂在技术门下）→
 * 用"门 → 所属阶段"回落，所以老库不用迁移也能落到正确的阶段。
 */
const stageReviews = computed(() => {
  const all = instances.value.gates
  return instances.value.reviews.filter((rv) => {
    if (rv.stageKey) return rv.stageKey === activeStageCode.value
    if (!rv.gate) return false
    const gate = all.find(g => g.code === rv.gate)
    return !!gate && gate.stageKey === activeStageCode.value
  })
})

// ==================== 交付件产出物关联（2026-10-11） ====================
// 产出物的写入口唯一：只从交付件面板（MethodologyInstancePanel → 行内「产出物」列）打开弹窗
// —— 用户要求"只在交付件管理页面可以关联"；文档 / 对象详情后续只做只读回显。
const artifactSummary = ref({})      // 交付件 oid → 已关联产出物数
const artifactModalOpen = ref(false)
const artifactDeliverable = ref(null)

async function loadArtifactSummary() {
  const oid = line.value?.oid
  if (!oid) return
  try {
    const res = await getDeliverableArtifactSummary(oid)
    artifactSummary.value = res?.code === 200 ? (res.data || {}) : {}
  } catch { artifactSummary.value = {} }
}

function openArtifactModal(record) {
  artifactDeliverable.value = record
  artifactModalOpen.value = true
}

/**
 * 产出物关联变更（绑定 / 解除）后：状态列与计数都要刷新。
 * 交付件状态由后端在 /api/instances 返回前重算（绑定即"进行中"，解除可退回"未开始"），
 * 所以这里必须重拉实例 —— 只刷计数会看到"产出物 1 项、状态还是未开始"的割裂。
 */
function onArtifactChanged() {
  loadArtifactSummary()
  loadInstances()
}

/**
 * 阶段门的 ISSUE_OPEN 自动检查（门 oid → 判定）："开门前还有几个未闭环问题"。
 *
 * <p>只在租户启用 GATE_REVIEW 时拉；现算不落库。门没声明 ISSUE_OPEN / 拉取失败 →
 * map 为空 → 门面板不显示「未闭环问题」列（不给空诺）。
 */
const gateCheckMap = ref({})

async function loadGateCheck(ownerOid) {
  gateCheckMap.value = {}
  if (!ownerOid || !has('GATE_REVIEW')) return
  try {
    const res = await getIssueGateCheck(ownerOid)
    if (res?.code === 200 && Array.isArray(res.data?.gates)) {
      const map = {}
      for (const g of res.data.gates) {
        if (g?.gateOid) map[g.gateOid] = g
      }
      gateCheckMap.value = map
    }
  } catch { /* 检查失败只影响该列显隐，不阻断资料区 */ }
}

async function loadInstances() {
  const oid = line.value?.oid
  if (!oid) return
  instancesLoading.value = true
  try {
    const res = await listMethodologyInstances(oid)
    if (res?.code === 200 && res.data) {
      instances.value = {
        deliverables: res.data.deliverables || [],
        gates: res.data.gates || [],
        reviews: res.data.reviews || [],
        rules: res.data.rules || [],
        metrics: res.data.metrics || [],
        dashboards: res.data.dashboards || [],
        // 「我在这个项目下能做什么」（项目级团队角色）—— 面板按钮显隐的唯一依据
        permissions: res.data.permissions || null
      }
    }
  } catch { /* 读不到不阻断资料区：各面板显示"暂无" */ }
  finally { instancesLoading.value = false }
  // 交付件面板「产出物」列的计数：与实例同批加载（失败不阻断，列显示 0 项）
  loadArtifactSummary()
  // 阶段门面板「未闭环问题」列：同一批加载（门没声明 ISSUE_OPEN 时该列不显示）
  loadGateCheck(oid)
}

/** 当前阶段的定义（单卡片展示的就是它） */
const activeStageDef = computed(() => stageDefs.value.find(s => s.oid === activeStage.value) || null)

/**
 * 按阶段 code 分组的门实例 —— 只收决策门。
 *
 * <p>技术评审已提到阶段层（2026-10-29 扁平化），老库里残留的 TECHNICAL_REVIEW 门行
 * 既不会被人决策、也永远不会 PASSED：算进"本阶段未配置门 / 门未通过 x/y"只会让它永远红着，
 * 并让阶段推断永远停在最早那个阶段（见 {@link inferredStageOid}）。
 */
const gatesByStageKey = computed(() => {
  const map = {}
  for (const g of instances.value.gates) {
    if (!isDecisionGate(g)) continue
    const k = g.stageKey || ''
    ;(map[k] = map[k] || []).push(g)
  }
  return map
})

/**
 * "当前阶段" = 第一个**门未通过**的阶段（P3 口径：门按本阶段交付件完成率判定）。
 * 语义边界如实写清，免得以后被当成 bug：
 *  · 某阶段没配门 → 跳过它，不把它算作"未通过"；
 *  · 一个阶段有多道门 → 任一道未 PASSED 即算本阶段未通过；
 *  · 所有配了门的阶段都过了 → 停在最后一道门所在阶段（活儿干完了）；
 *  · 压根没有门实例（存量对象 / 未装方法论）→ 退回第一阶段。
 * 今天 P3 判定还没做，门全是 PENDING → 实际效果是"永远停在第一阶段"，这是预期，不是坏掉。
 */
const inferredStageOid = computed(() => {
  const stages = stageDefs.value
  if (!stages.length) return null
  const byStage = gatesByStageKey.value
  for (const s of stages) {
    const list = byStage[s.key] || []
    if (!list.length) continue
    if (list.some(g => g.status !== 'PASSED')) return s.oid
  }
  const lastWithGate = [...stages].reverse().find(s => (byStage[s.key] || []).length)
  return (lastWithGate || stages[0]).oid
})

/** 用户是否自己动过下拉 —— 动过之后不再自动抢选中 */
const userPickedStage = ref(false)

// 注：自动选中"当前阶段"的那个 watch 放在 stageDefs 声明之后（见 stageDefs 附近）。
// 原因：watch 在创建时就会读一次来源，写在这里会撞上 stageDefs 的暂时性死区
// （ReferenceError: Cannot access 'stageDefs' before initialization）。

/** 当前阶段的门状态（卡片上那个标签） */
const currentStageGates = computed(() => gatesByStageKey.value[activeStageCode.value] || [])
const currentStageGateText = computed(() => {
  const list = currentStageGates.value
  if (!list.length) return '本阶段未配置门'
  const passed = list.filter(g => g.status === 'PASSED').length
  return passed === list.length ? `门已通过 ${passed}/${list.length}` : `门未通过 ${passed}/${list.length}`
})
const currentStageGateColor = computed(() =>
  currentStageGates.value.length && currentStageGates.value.every(g => g.status === 'PASSED') ? 'green' : 'default'
)

/** 当前阶段管理的业务对象类型集合（按阶段模板的 managedObjectTypes 解析） */
const stageManagedObjects = computed(() => {
  const stage = stageDefs.value.find(s => s.oid === activeStage.value)
  if (!stage || !stage.managedObjectTypes) {
    // 兜底：未配置时默认全部可见（向后兼容）
    return new Set(['FUNCTIONAL', 'PART', 'DOCUMENT'])
  }
  try {
    const arr = typeof stage.managedObjectTypes === 'string'
      ? JSON.parse(stage.managedObjectTypes)
      : stage.managedObjectTypes
    return new Set(Array.isArray(arr) ? arr : [])
  } catch {
    return new Set()
  }
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

/** 递归按创建时间正序排序文件夹树（同级节点：最早创建的在前） */
function sortFolderTreeByCreatedAtAsc(nodes) {
  if (!nodes) return
  nodes.sort((a, b) => {
    const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0
    const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0
    return ta - tb
  })
  for (const n of nodes) {
    if (n.children?.length) sortFolderTreeByCreatedAtAsc(n.children)
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
const partForm = ref({ name: '', typeDefinitionCode: '', description: '', stageOid: '', folderOid: '', containerOid: '', containerType: '', clsOid: '', ckfileOid: '', attachmentOid: '' })
const functionalModalVisible = ref(false)
const functionalSaving = ref(false)
const functionalFormRef = ref(null)
const functionalForm = ref({ name: '', typeDefinitionCode: '', description: '', stageOid: '', folderOid: '', containerOid: '', containerType: '', clsOid: '', ckfileOid: '', attachmentOid: '' })
const functionalTypeSelectValue = ref(undefined)
const selectedFunctionalType = ref(null)
const functionalFormEntityCode = ref('FUNCTIONAL')
const functionalTypeOptions = ref([])
const partTypeSelectValue = ref(undefined)
const selectedPartType = ref(null)
const partFormEntityCode = ref('PART')
const partTypeOptions = ref([])
const partTypeLoading = ref(false)

// ==================== 编辑（文档/零组件） ====================
const editModalVisible = ref(false)
const editModalSaving = ref(false)
const editFormRef = ref(null)
const editForm = ref({})
const editEntityType = ref('')   // 'PART' | 'DOC'
const editEntityOid = ref(null)
const editEntityCode = ref('')   // 类型编码（typeDefinitionCode），用于加载布局
const editTitle = ref('')

function openEditModal(record) {
  const isPart = record.entityType === 'PART'
  editEntityType.value = isPart ? 'PART' : 'DOC'
  editEntityOid.value = record.oid
  editEntityCode.value = record.typeDefinitionCode || (isPart ? 'PART' : 'DOCUMENT')
  editTitle.value = `编辑${isPart ? '零组件' : '文档'}：${record.code || ''} ${record.name || ''}`
  editForm.value = {}
  editModalVisible.value = true
}

/** 执行保存（不关闭弹窗/不提示，由调用方决定后续行为） */
async function performSave() {
  if (editFormRef.value) {
    const errors = editFormRef.value.validate()
    if (errors.length > 0) {
      message.warning(errors[0])
      return false
    }
  }
  editModalSaving.value = true
  try {
    const isPart = editEntityType.value === 'PART'
    const formData = editFormRef.value?.getFormData() || editForm.value
    const payload = { ...formData }
    if (editFormRef.value) {
      const clsIbaFieldNames = editFormRef.value.getClsIbaFieldNames() || []
      clsIbaFieldNames.forEach(k => delete payload[k])
      const clsIbaValues = editFormRef.value.getClsIbaValues() || {}
      if (Object.keys(clsIbaValues).length > 0) {
        payload.clsIbaValues = clsIbaValues
      }
    }
    ;['clsOid', 'stageOid', 'folderOid', 'containerOid', 'ckfileOid', 'attachmentOid'].forEach(k => {
      if (payload[k] != null && String(payload[k]).trim() === '') payload[k] = null
    })
    const res = isPart
      ? await updatePart(editEntityOid.value, payload)
      : await updateDocument(editEntityOid.value, payload)
    if (res.code === 200) {
      if (selectedFolder.value) {
        if (isPart) loadParts(selectedFolder.value.oid)
        loadDocuments(selectedFolder.value.oid)
      }
      return true
    }
    message.error(res.message || '保存失败')
    return false
  } catch (e) {
    message.error(e?.response?.data?.message || '保存失败')
    return false
  } finally {
    editModalSaving.value = false
  }
}

async function confirmEdit() {
  const ok = await performSave()
  if (ok) {
    message.success('保存成功')
    editModalVisible.value = false
  }
}

/** 保存并检入：保存修改后立即解除检出版本 */
async function confirmSaveAndCheckin() {
  const ok = await performSave()
  if (!ok) return
  const isPart = editEntityType.value === 'PART'
  editModalSaving.value = true
  try {
    const res = isPart
      ? await checkinPartApi(editEntityOid.value)
      : await checkinDocApi(editEntityOid.value)
    if (res.code === 200) {
      message.success('保存并检入成功')
      editModalVisible.value = false
      if (selectedFolder.value) {
        if (isPart) loadParts(selectedFolder.value.oid)
        loadDocuments(selectedFolder.value.oid)
      }
    } else {
      message.warning(res.message || '保存成功，但检入失败')
      editModalVisible.value = false
    }
  } catch (e) {
    message.warning(e?.response?.data?.message || '保存成功，但检入失败')
    editModalVisible.value = false
  } finally {
    editModalSaving.value = false
  }
}

/**
 * 产品线/型号内禁止创建的类型（电子元器件必须归档在企业资源库-元器件库，
 * 只能通过「企业资源 → 元器件库 → 新建IPN」创建）。
 */
const FORBIDDEN_PART_TYPE_CODES = ['ELECTRONIC']

/** 递归剔除禁止选择的类型节点（连同其子树） */
function filterForbiddenTypes(node) {
  if (!node) return null
  if (FORBIDDEN_PART_TYPE_CODES.includes((node.code || '').toUpperCase())) return null
  return {
    ...node,
    children: (node.children || [])
      .map(filterForbiddenTypes)
      .filter(Boolean),
  }
}

async function loadPartTypes() {
  partTypeLoading.value = true
  try {
    const res = await getTypeDefinitionTree()
    const tree = res?.data || res || []
    // 找到 PART 根节点，作为树形展示（含其子孙子类型），并剔除禁止的类型
    const partNode = findPartNode(Array.isArray(tree) ? tree : [])
    const filtered = filterForbiddenTypes(partNode)
    partTypeOptions.value = filtered ? [filtered] : []
  } catch { partTypeOptions.value = [] }
  finally { partTypeLoading.value = false }
}

function findPartNode(nodes) {
  for (const n of nodes) {
    if (n.code === 'PART') return n
    if (n.children) {
      const f = findPartNode(n.children)
      if (f) return f
    }
  }
  return null
}

function findNodeByCode(nodes, code) {
  for (const n of nodes) {
    if (n.code === code) return n
    if (n.children) {
      const f = findNodeByCode(n.children, code)
      if (f) return f
    }
  }
  return null
}

function openCreatePart() {
  partTypeSelectValue.value = undefined
  selectedPartType.value = null
  partFormEntityCode.value = 'PART'
  const isModel = line.value?.nodeType === 'PRODUCT_MODEL'
  // 2026-10-22：零件管理独立于阶段与文件夹 —— 新建时不再携带 stageOid / folderOid，
  // 归属只认当前容器自身 oid（型号页建件就挂型号，与「物料清单」只查当前容器一致）；
  // 「物料清单」是零件的统一入口
  partForm.value = {
    name: '',
    typeDefinitionCode: '',
    description: '',
    containerOid: line.value?.oid || '',
    containerType: isModel ? 'PRODUCT_MODEL' : 'PRODUCT_LINE',
    clsOid: '',
    ckfileOid: '',
    attachmentOid: '',
  }
  loadPartTypes()
  partModalVisible.value = true
}

function onPartTypeChange(typeCode) {
  if (typeCode) {
    const found = findNodeByCode(partTypeOptions.value, typeCode)
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
    containerOid: '', containerType: '',
    clsOid: '', ckfileOid: '', attachmentOid: '',
  }
}

/**
 * 新建 / 另存为零组件成功后的列表刷新。
 *
 * <p>物料清单按容器聚合（getBomMaterials），还带着 bomLoaded 缓存 —— 只刷「当前文件夹」的话，
 * 在物料清单页签里新建完列表一行都不会变，用户只能按 F5 才看得到刚建的零件。
 * 当前就在该页签时强制重取；不在时作废缓存，切过去时 watch 会重新拉。
 */
function refreshAfterPartCreated() {
  if (selectedFolder.value) loadParts(selectedFolder.value.oid)
  if (stageView.value === 'bom') loadBomMaterials(true)
  else bomLoaded.value = false
}

async function confirmPart() {
  if (!partForm.value.name?.trim()) { message.warning('请输入零组件名称'); return }
  partModalSaving.value = true
  try {
    // 外键字段空字符串转 null，避免外键约束失败
    const payload = { ...partForm.value }
    // 分类 IBA 字段值单独提交到 ck_cls_iba_data（分类节点级）
    if (partFormRef.value) {
      const clsIbaFieldNames = partFormRef.value.getClsIbaFieldNames() || []
      clsIbaFieldNames.forEach(k => delete payload[k])
      const clsIbaValues = partFormRef.value.getClsIbaValues() || {}
      if (Object.keys(clsIbaValues).length > 0) {
        payload.clsIbaValues = clsIbaValues
      }
    }
    ;['clsOid', 'stageOid', 'folderOid', 'containerOid', 'ckfileOid', 'attachmentOid'].forEach(k => {
      if (payload[k] != null && String(payload[k]).trim() === '') payload[k] = null
    })
    const res = await createPart(payload)
    if (res.code === 200) {
      message.success('零组件创建成功')
      partModalVisible.value = false
      resetPartModal()
      refreshAfterPartCreated()
    } else {
      message.error(res.message || '创建失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '新建零组件失败')
  } finally { partModalSaving.value = false }
}

// 研发阶段（从后端 API 动态加载，替换硬编码字典）
const stageDefs = ref([])

// 进页自动选中"当前阶段"（= 第一个门未通过的阶段，见 inferredStageOid）。
// 必须放在 stageDefs 声明**之后**：watch 创建时会立刻读一次来源，放前面会撞上
// stageDefs 的暂时性死区（Cannot access 'stageDefs' before initialization）。
// 等阶段清单与实例都就绪后再定；用户自己动过下拉就不再抢。
watch(
  [stageDefs, instances, instancesLoading],
  () => {
    if (userPickedStage.value) return
    if (!stageDefs.value.length) return
    if (instancesLoading.value) return
    const target = inferredStageOid.value
    if (target && target !== activeStage.value) activeStage.value = target
  }
)

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
  // 阶段管理的业务对象类型（用于控制"新建文档 / 功能架构"按钮的显示；
  // 零件已无阶段属性，不再参与判断 —— 见 .pd / 物料清单工具栏的注释）
  managedObjectTypes: stage.managedObjectTypes,
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
  parts.value = []
  if (node && node._mergedInherited) {
    // 合并节点：同时加载继承资料和自有资料
    loadInheritedDocuments(node._mergedInherited.oid)
    loadDocuments(node.oid)
    loadParts(node.oid)
  } else if (node && !node._inherited) {
    loadDocuments(node.oid)
    loadParts(node.oid)
  } else {
    documents.value = []
    parts.value = []
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

// ==================== 零组件（Part）列表 ====================
const parts = ref([])
const partLoading = ref(false)

/** 零组件 + 文档合并列定义（紧凑列宽+操作列固定右侧，避免横向滚动条） */
const mixedColumns = [
  { title: '', dataIndex: 'checkedOut', key: 'checkout_status', width: 32, align: 'center' },
  { title: '编码', dataIndex: 'code', key: 'code', width: 110 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true, width: 150 },
  { title: '类型', dataIndex: 'typeDefinitionName', key: 'typeDefinitionName', width: 100 },
  { title: '版本', dataIndex: 'displayVersion', key: 'displayVersion', width: 60 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 50 },
  { title: '生命周期', key: 'status', width: 90 },
  { title: '检出', key: 'checkout', width: 80 },
  { title: '操作', key: 'action', width: 88, fixed: 'right' }
]

// ==================== 业务对象过滤（文件夹内容按业务对象类型过滤） ====================

/** 当前业务对象过滤值：'ALL'=全部对象，否则为 TypeDefinition.code */
const bizObjectFilter = ref('ALL')
/** source=ootb 的业务对象类型清单（来自类型定义树，与其他来源/模型定义页面同源） */
const ootbTypeOptions = ref([])
/** 子类型code → 顶层对象code 映射（通过 rootTypeCode 建立归属关系） */
const typeRootCodeMap = ref({})

/** 过滤下拉选项：全部对象 + source=ootb 的类型清单 */
const bizObjectFilterOptions = computed(() => [
  { value: 'ALL', label: '全部对象' },
  ...ootbTypeOptions.value.map(t => ({ value: t.code, label: t.name || t.code })),
])

/** 拉取类型定义树，展平筛选 source 为 ootb（系统内置根类型 + 预置子类型，不含 USER 自定义） */
async function loadBizObjectFilterOptions() {
  try {
    const res = await getTypeDefinitionTree()
    const tree = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
    const flat = []
    const walk = (nodes) => {
      for (const n of (nodes || [])) {
        flat.push(n)
        if (n.children?.length) walk(n.children)
      }
    }
    walk(tree)
    // 只显示模型定义的顶层对象（type_kind=OOTB 的根类型，如 PART/DOCUMENT/FUNCTIONAL）。
    // 注意大小写不敏感：存量数据中 source/type_kind 存在小写 ootb（仅 FUNCTIONAL 为大写）。
    // 预置子类型（type_kind=SOFT_TYPE）与用户自定义（source=USER）不出现在下拉中，
    // 但过滤时通过 rootTypeCode 自动包含顶层对象下的全部子类型
    ootbTypeOptions.value = flat.filter(t =>
      ((t.source || '').toLowerCase() === 'ootb' || (t.typeKind || '').toLowerCase() === 'ootb')
        // 排除域锚点（DOMAIN）：它是业务域的命名空间根，不是可创建的业务对象类型
        && (t.typeKind || '').toUpperCase() !== 'DOMAIN'
    )
    // 建立 子类型code → 顶层对象code 映射，供过滤时判断对象归属
    const rootMap = {}
    for (const t of flat) {
      if (t.code) rootMap[t.code] = t.rootTypeCode || t.code
    }
    typeRootCodeMap.value = rootMap
  } catch { ootbTypeOptions.value = [] }
}

/** 零组件 + 文档合并列表（Part 标记 entityType='PART'），按业务对象过滤：
    下拉只含顶层对象，过滤时通过 子类型→顶层 映射把其下全部子类型的对象一并包含 */
const filteredMixed = computed(() => {
  const partItems = parts.value.map(p => ({ ...p, entityType: 'PART' }))
  const docItems = documents.value.map(d => ({ ...d, entityType: 'DOC' }))
  const all = [...partItems, ...docItems]
  const selected = bizObjectFilter.value
  if (!selected || selected === 'ALL') return all
  const rootMap = typeRootCodeMap.value
  return all.filter(i => {
    const code = i.typeDefinitionCode
    if (code === selected) return true
    // 对象的类型（或其类型未知时按原值）解析到顶层 code，与选中值比较
    const root = rootMap[code] || code
    return root === selected
  })
})

async function loadParts(folderOid) {
  partLoading.value = true
  try {
    const res = await getPartsByFolder(folderOid)
    if (res.code === 200) parts.value = res.data || []
    else parts.value = []
  } catch { parts.value = [] }
  finally { partLoading.value = false }
}

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

function openCreateFunctional() {
  functionalTypeSelectValue.value = undefined
  selectedFunctionalType.value = null
  functionalFormEntityCode.value = 'FUNCTIONAL'
  const isModel = line.value?.nodeType === 'PRODUCT_MODEL'
  const plOid = isModel ? line.value?.parentOid : line.value?.oid
  functionalForm.value = {
    name: '',
    typeDefinitionCode: '',
    description: '',
    stageOid: activeStage.value || stageDefs.value[0]?.oid || '',
    folderOid: selectedFolder.value?.oid || '',
    containerOid: plOid || '',
    containerType: isModel ? 'PRODUCT_MODEL' : 'PRODUCT_LINE',
    clsOid: '',
    ckfileOid: '',
    attachmentOid: '',
  }
  loadFunctionalTypes()
  functionalModalVisible.value = true
}

async function loadFunctionalTypes() {
  try {
    const res = await getTypeDefinitionTree()
    const tree = res?.data || res || []
    // 找到 FUNCTIONAL 根节点，作为树形展示（含其子类型：系统/子系统/CI）
    const functionalNode = findNodeByCode(Array.isArray(tree) ? tree : [], 'FUNCTIONAL')
    functionalTypeOptions.value = functionalNode ? [functionalNode] : []
  } catch { functionalTypeOptions.value = [] }
}

function onFunctionalTypeChange(typeCode) {
  if (typeCode) {
    const found = findNodeByCode(functionalTypeOptions.value, typeCode)
    selectedFunctionalType.value = found || { code: typeCode, name: typeCode }
    functionalFormEntityCode.value = typeCode
    functionalForm.value.typeDefinitionCode = typeCode
  } else {
    selectedFunctionalType.value = null
    functionalFormEntityCode.value = 'FUNCTIONAL'
    functionalForm.value.typeDefinitionCode = ''
  }
}

function resetFunctionalModal() {
  functionalTypeSelectValue.value = undefined
  selectedFunctionalType.value = null
  functionalFormEntityCode.value = 'FUNCTIONAL'
  functionalForm.value = {
    name: '', typeDefinitionCode: '', description: '',
    stageOid: '', folderOid: '', containerOid: '', containerType: '',
    clsOid: '', ckfileOid: '', attachmentOid: '',
  }
  functionalModalVisible.value = false
}

async function confirmFunctional() {
  if (!selectedFunctionalType.value) { message.warning('请先选择功能架构类型'); return }
  if (!functionalForm.value.name?.trim()) { message.warning('请输入功能架构名称'); return }
  functionalSaving.value = true
  try {
    const payload = { ...functionalForm.value }
    if (functionalFormRef.value) {
      const clsIbaFieldNames = functionalFormRef.value.getClsIbaFieldNames() || []
      clsIbaFieldNames.forEach(k => delete payload[k])
      const clsIbaValues = functionalFormRef.value.getClsIbaValues() || {}
      if (Object.keys(clsIbaValues).length > 0) {
        payload.clsIbaValues = clsIbaValues
      }
    }
    ;['clsOid', 'stageOid', 'folderOid', 'containerOid', 'ckfileOid', 'attachmentOid'].forEach(k => {
      if (payload[k] != null && String(payload[k]).trim() === '') payload[k] = null
    })
    const res = await createFunctional(payload)
    if (res.code === 200) {
      message.success('功能架构创建成功')
      functionalModalVisible.value = false
      if (selectedFolder.value) loadDocuments(selectedFolder.value.oid)
    } else {
      message.error(res.message || '创建失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '新建功能架构失败')
  } finally { functionalSaving.value = false }
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
    // 所属产品默认到"当前系列 / 型号"（2026-10-11 用户反馈：原来为空需手选 —— 文档本就归属当前对象）
    ownerOid: line.value?.oid || '',
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
    // 以 DynamicForm 的表单数据为基础（含动态 IBA/分类字段等）
    const formData = docFormRef.value?.getFormData() || { ...docForm.value }
    const payload = { ...formData }
    // 分类 IBA 字段值单独提交到 ck_cls_iba_data（分类节点级）
    if (docFormRef.value) {
      const clsIbaFieldNames = docFormRef.value.getClsIbaFieldNames() || []
      clsIbaFieldNames.forEach(k => delete payload[k])
      const clsIbaValues = docFormRef.value.getClsIbaValues() || {}
      if (Object.keys(clsIbaValues).length > 0) {
        payload.clsIbaValues = clsIbaValues
      }
    }
    // 强制覆盖必填字段（容器、阶段、文件夹等以当前弹窗上下文为准）
    payload.name = name
    payload.typeDefinitionCode = selectedDocType.value?.code || ''
    payload.containerOid = docForm.value.containerOid || ''
    payload.containerType = docForm.value.containerType || 'PRODUCT_LINE'
    payload.folderOid = selectedFolder.value.oid
    payload.stageOid = activeStage.value
    payload.location = selectedFolder.value.name
    // 外键字段空字符串转 null
    ;['clsOid', 'stageOid', 'folderOid', 'containerOid', 'ckfileOid', 'attachmentOid'].forEach(k => {
      if (payload[k] != null && String(payload[k]).trim() === '') payload[k] = null
    })
    const res = await createDocument(payload)
    if (res.code === 200) {
      message.success('文档创建成功')
      docModalVisible.value = false
      resetDocModal()
      loadDocuments(selectedFolder.value.oid)
    } else {
      message.error(res.message || '创建失败')
    }
  } catch { message.error('新建文档失败') }
  finally { docModalSaving.value = false }
}

// ==================== 删除（支持选择删除范围） ====================
const deleteModalVisible = ref(false)
const deleteSaving = ref(false)
const deleteTarget = ref(null)
const deleteScope = ref('all')   // 'all' = 删除所有版本, 'latest' = 删除最新小版本

/** 打开删除弹窗 */
function openDeleteModal(record) {
  deleteTarget.value = record
  deleteScope.value = 'all'
  deleteModalVisible.value = true
}

/** 确认删除 */
async function confirmDelete() {
  if (!deleteTarget.value) return
  const record = deleteTarget.value
  const isPart = record.entityType === 'PART'
  deleteSaving.value = true
  try {
    const isLatest = deleteScope.value === 'latest'
    const res = isLatest
      ? (isPart ? await deletePartLatestIteration(record.oid) : await deleteDocumentLatestIteration(record.oid))
      : (isPart ? await deletePart(record.oid) : await deleteDocument(record.oid))
    if (res.code === 200) {
      message.success(isLatest ? '已删除最新小版本' : '已删除')
      deleteModalVisible.value = false
      if (selectedFolder.value) {
        if (isPart) loadParts(selectedFolder.value.oid)
        loadDocuments(selectedFolder.value.oid)
      }
    } else {
      message.error(res.message || '删除失败')
    }
  } catch (e) {
    message.error('删除失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    deleteSaving.value = false
  }
}

/** 修订（通用入口）：实体类型由记录的类型编码推导，后端按 entityType 路由到对应 Provider */
function reviseRecord(record) {
  const isPart = record.entityType === 'PART'
  // 与检出/检入同一套架构：前端不按宿主各写一条链路，只把 entityType 交给统一入口
  const entityType = resolveReviseEntityType(record.typeDefinitionCode)
    || (isPart ? 'PART' : 'DOCUMENT')
  Modal.confirm({
    title: `确认修订 "${record.code || record.name}"？`,
    content: '将基于当前最新版本创建一个新的大版本（大版本号递增，小版本重置为 1），当前版本转为历史版本。',
    okText: '确认修订',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await reviseObject(entityType, record.oid)
        if (res.code === 200) {
          message.success(`已修订: ${record.code || record.name}`)
          if (selectedFolder.value) {
            if (isPart) loadParts(selectedFolder.value.oid)
            loadDocuments(selectedFolder.value.oid)
          }
        } else {
          message.error(res.message || '修订失败')
        }
      } catch (e) {
        message.error('修订失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
      }
    }
  })
}

// ==================== 重命名 ====================
const renameModalVisible = ref(false)
const renameSaving = ref(false)
const renameTarget = ref(null)
const renameName = ref('')

/** 打开重命名弹窗 */
function openRenameModal(record) {
  renameTarget.value = record
  renameName.value = record.name || ''
  renameModalVisible.value = true
}

/** 确认重命名 */
async function confirmRename() {
  if (!renameTarget.value) return
  const record = renameTarget.value
  const isPart = record.entityType === 'PART'
  const name = renameName.value?.trim()
  if (!name) { message.warning('请输入名称'); return }
  renameSaving.value = true
  try {
    const res = isPart
      ? await renamePart(record.oid, name)
      : await renameDocument(record.oid, name)
    if (res.code === 200) {
      message.success('重命名成功')
      renameModalVisible.value = false
      if (selectedFolder.value) {
        if (isPart) loadParts(selectedFolder.value.oid)
        loadDocuments(selectedFolder.value.oid)
      }
    } else {
      message.error(res.message || '重命名失败')
    }
  } catch (e) {
    message.error('重命名失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    renameSaving.value = false
  }
}

// ==================== 另存为 ====================
const saveAsModalVisible = ref(false)
const saveAsSaving = ref(false)
const saveAsTarget = ref(null)
const saveAsName = ref('')

/** 打开零组件详情（当前窗口跳转，返回用浏览器后退） */
function openPartDetail(record) {
  router.push({ name: 'PartDetail', params: { oid: record.oid } })
}

/**
 * 编码列点击 → 对象详情。
 *
 * <p>与行菜单「详情」同一套分派：零组件在当前窗口跳转独立详情页；文档直接用本页的详情抽屉
 * （列表 VO 已带主文件与检出信息，不必再回查一次）。子类型通过 resolveEntityApiPath 跟随宿主。
 */
function openRecordDetail(record) {
  if (!record) return
  if (record.entityType === 'PART' || resolveEntityApiPath(record.typeDefinitionCode) === 'parts') {
    openPartDetail(record)
    return
  }
  docViewerDoc.value = record
  docViewerVisible.value = true
}

/** 打开另存为弹窗 */
function openSaveAsModal(record) {
  saveAsTarget.value = record
  saveAsName.value = `${record.name || ''} - 副本`
  saveAsModalVisible.value = true
}

/** 确认另存为 */
async function confirmSaveAs() {
  if (!saveAsTarget.value) return
  const record = saveAsTarget.value
  const name = saveAsName.value?.trim()
  if (!name) { message.warning('请输入名称'); return }
  saveAsSaving.value = true
  try {
    const res = await saveAsPart(record.oid, name)
    if (res.code === 200) {
      message.success('另存为成功')
      saveAsModalVisible.value = false
      refreshAfterPartCreated()
    } else {
      message.error(res.message || '另存为失败')
    }
  } catch (e) {
    message.error('另存为失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    saveAsSaving.value = false
  }
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
const checkoutThenEdit = ref(false)

/**
 * 检出 / 检入 / 取消检出成功后，把所有正在展示这些对象的列表重新拉一遍。
 *
 * 为什么不能只刷「当前文件夹」：物料清单（bomLoaded）与产品数模（cadModelsLoaded）
 * 是两个独立页签，各自缓存了一份数据。不主动失效的话，用户撤销检出后
 * 那两列的「已检出: 谁」会一直挂着旧值，看起来像"操作没生效"。
 * 两个 loader 都必须带 force，否则被各自的缓存标志直接挡回来。
 */
function refreshAfterCheckoutChange() {
  if (selectedFolder.value) {
    loadParts(selectedFolder.value.oid)
    if (selectedFolder.value._mergedInherited) {
      loadInheritedDocuments(selectedFolder.value._mergedInherited.oid)
    }
    loadDocuments(selectedFolder.value.oid)
  }
  // 没打开的页签只作废缓存（切过去时 watch 会重新拉），
  // 不为一个用户看不见的列表白发两个请求
  if (stageView.value === 'bom') loadBomMaterials(true)
  else bomLoaded.value = false
  if (stageView.value === 'cad') loadCadModels(true, true)
  else cadModelsLoaded.value = false
}

/** 确认检出 */
async function confirmCheckout() {
  if (!checkoutTargetDoc.value) return
  const entityType = checkoutTargetDoc.value.entityType || 'DOC'
  const isPart = entityType === 'PART'
  const isEngDoc = entityType === 'ENG_DOCUMENT'
  const targetOid = checkoutTargetDoc.value.oid
  const comment = checkoutComment.value.trim()
  checkoutSaving.value = true
  try {
    const res = isPart
      ? await checkoutPartApi(targetOid, comment)
      : isEngDoc
        ? await checkoutEngineeringDocument(targetOid, comment)
        : await checkoutDocApi(targetOid, comment)
    if (res.code === 200) {
      message.success('检出成功')
      recordOperation({
        action: isPart ? '检出零组件' : isEngDoc ? '检出工程数据' : '检出文档',
        target: checkoutTargetDoc.value.code + ' ' + checkoutTargetDoc.value.name
      })
      const target = checkoutTargetDoc.value
      const shouldEditAfter = checkoutThenEdit.value
      checkoutModalVisible.value = false
      checkoutComment.value = ''
      checkoutTargetDoc.value = null
      checkoutThenEdit.value = false
      // 文件夹内容 + 物料清单 + 产品数模 一起刷新（检出列要立刻变成"已检出: 我"）
      refreshAfterCheckoutChange()
      // 检出并编辑：立即打开编辑对话框
      if (shouldEditAfter && target) {
        openEditModal(target)
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

/* ==================== 发起流程（共享弹框） ==================== */

const startProcessVisible = ref(false)
/** 传给共享弹框的业务对象 */
const startProcessTarget = ref(null)
/**
 * 直传流程 key（阶段门 / 评审行专用）。
 *
 * <p>非空时弹框跳过「类型 + 状态 → 流程」解析，直接按 key 准备发起
 * —— 见 {@link StartProcessModal} 的 processKey 属性与 /start-option-by-key。
 * 普通入口（零组件 / 文档…）打开时置空，两个入口互不串味。
 */
const startProcessKey = ref('')
/**
 * 申请单预设值（阶段门 / 评审行专用）：`{ reviewTemplate, stage, gate }`。
 *
 * <p>从 TR1 行点「发起」时，"评哪场、哪个阶段、哪道门"其实在行上就已经定了，
 * 不该让用户在表单里再从一长串 code 里挑一遍 —— 见 {@link presetFromGateRow}。
 * 普通入口（零组件 / 文档…）打开时置空。
 */
const startProcessPreset = ref(null)

/**
 * 行数据 → 技术评审申请单的三个预设值。
 *
 * <p>口径全部来自行本身，不猜：
 * <ul>
 *   <li><b>门行</b>（ck_gate）：{@code code} = 门码（TR1）、{@code stageKey} = 所处阶段（IPD_CONCEPT），
 *       评审模板取"服务于这道门"的那场评审（ck_review.gate = 门码）；</li>
 *   <li><b>评审行</b>（ck_review）：{@code code} 本身就是评审模板码（TR1_CONCEPT），
 *       {@code gate} = 所属门码，阶段取该门的 stageKey。</li>
 * </ul>
 * 取不到就留空（表单仍可手选），不硬编任何 code。
 */
function presetFromGateRow(record) {
  if (!record) return null
  const gates = instances.value.gates || []
  const reviews = instances.value.reviews || []
  const code = record.code || ''
  // 评审行：评审码本身即模板码。判据用 type（评审 TECHNICAL vs 门 TECHNICAL_REVIEW/DECISION_REVIEW），
  // 不用"有没有 gate"——扁平化后新评审没有 gate，老评审才有。
  // 阶段优先取评审自己的 stageKey，老数据再回门上去取。
  if (record.type === 'TECHNICAL') {
    const gate = record.gate ? gates.find((g) => g.code === record.gate) : null
    return { reviewTemplate: code, gate: record.gate || '', stage: record.stageKey || gate?.stageKey || '' }
  }
  // 门行：门码 + 阶段已知 → 评审模板取本门下的评审（优先未裁剪的）
  const review = reviews.find((r) => r.gate === code && !r.disabled) || reviews.find((r) => r.gate === code)
  return { reviewTemplate: review?.code || '', gate: code, stage: record.stageKey || '' }
}

/**
 * 阶段门 / 评审行的「发起」：行上自带流程 key
 * （TR* → {@code technical-review}、CHARTER/PDCP/ADCP/LDCP → {@code gate-decision}），
 * 发起上下文业务对象 = **当前产品系列** —— 门/评审本来就是"这个系列要走的那道审批"。
 */
function openGateProcess(record) {
  // 开门前的 ISSUE_OPEN 自动检查：不通过先提示 —— 走不走由人决策，但不能不知情
  const check = record?.oid ? gateCheckMap.value[record.oid] : null
  if (check && !check.passed) {
    const fails = (check.checks || []).filter((c) => !c.passed)
      .map((c) => c.message || c.type).join('；')
    Modal.confirm({
      title: '这道门的自动检查未通过',
      content: `${fails}。建议先处理（问题到「持续改进」页闭环、交付件到交付件面板补齐）；仍要发起时，请在决策留痕里说明原因。`,
      okText: '仍要发起',
      cancelText: '先去处理',
      onOk: () => doOpenGateProcess(record)
    })
    return
  }
  doOpenGateProcess(record)
}

/** 真正的发起动作（过了上面的检查提示后调用） */
function doOpenGateProcess(record) {
  startProcessKey.value = record?.processKey || ''
  startProcessPreset.value = presetFromGateRow(record)
  startProcessTarget.value = line.value
    ? {
        oid: line.value.oid,
        name: line.value.name || line.value.code,
        code: line.value.code,
        // 产品系列的类型码是稳定事实（对应 ck_type_definition 的 PRODUCT_LINE）
        typeDefinitionCode: 'PRODUCT_LINE',
        statusCode: line.value.statusCode || line.value.status,
        statusName: line.value.statusName,
      }
    : null
  startProcessVisible.value = true
}

/**
 * 打开「发起流程」弹框。
 *
 * <p>解析（类型 + 状态 → 流程模板）与 Flowable 启动都在共享组件
 * {@link StartProcessModal} 里，本页只负责把当前业务对象传进去
 * —— 零组件 / 文档 / 工程数据共用同一套交互。
 */
function openStartProcess(record) {
  // 普通入口：清掉上一个门/评审行留下的直传 key 与申请单预设，走回"按类型 + 状态解析"
  startProcessKey.value = ''
  startProcessPreset.value = null
  startProcessTarget.value = {
    oid: record.oid,
    name: record.name || record.code,
    code: record.code || record.number,
    // typeDefinitionCode 是权威字段（各列表 DTO 都有），entityType 仅作兜底
    typeDefinitionCode: record.typeDefinitionCode || record.entityType,
    typeDefinitionName: record.typeDefinitionName,
    statusCode: record.statusCode || record.status?.code,
    statusName: record.statusName || record.status?.displayName,
    // 生命周期模板子版本（解析该发哪个流程用）
    lifecycleTemplateIterationOid: record.lifecycleTemplateIterationOid || null,
  }
  startProcessVisible.value = true
  }

  // ==================== 设置生命周期状态 ====================

  const lifecycleVisible = ref(false)
  /** 传给弹框的对象（字段做兼容：零组件/文档两类列表 DTO 的编码字段名不同） */
  const lifecycleTarget = ref(null)

  /**
  * 打开「设置生命周期状态」弹框。
  *
  * <p>候选项（初始状态 / 指定状态）与可达性由共享弹框按<b>类型绑定的生命周期模板</b>取
  * （见 components/SetLifecycleStateModal.vue），本页只负责把当前对象传进去。
  */
  function openLifecycle(record) {
  lifecycleTarget.value = {
    oid: record.oid,
    name: record.name,
    code: record.code || record.number,
    typeDefinitionCode: record.typeDefinitionCode || record.entityType,
    statusCode: record.statusCode || record.status?.code,
  }
  lifecycleVisible.value = true
  }

  /* ==================== 实例裁剪（交付件 / 阶段门 / 评审 / 检查规则） ==================== */

/**
 * 权限**来自后端**（随 `GET /api/instances` 的 permissions 返回，2026-10-14 起是项目级的）：
 * 按"我在该系列 / 型号团队里的角色"判 —— 裁剪 = 产品经理；发起流程 = 产品经理 / 项目经理 / IPDT 组长；
 * 关联交付项 = 产品经理，或该件的责任人（谁负责谁关联）。前端不再自己看全局角色。
 *
 * <p>面板据此显隐按钮；真正的闸门在后端（tailor / 关联 / 发起三处独立校验，无权限返 403）。
 */

/**
 * 面板里点了裁剪 / 编辑：标记「本项目不适用」/ 恢复、改强制项、改责任人（交付件）。
 *
 * <p>成功后重拉实例 —— 状态、计数（"已裁剪 N 项"）与权限摘要一起刷新；
 * 失败按后端给的原因提示（403 = 没有裁剪权，400 = 类别/参数问题）。
 */
async function onTailor(kind, record, patch) {
  if (!record?.oid || !kind) return
  const what = patch.ownerRole !== undefined
    ? `责任人已改为「${patch.ownerRole}」`
    : patch.mandatory !== undefined
      ? (patch.mandatory ? '已改为强制' : '已改为可选')
      : (patch.disabled ? '已标记「本项目不适用」' : '已恢复适用')
  try {
    const res = await tailorInstance(kind, record.oid, patch)
    if (res?.code === 200) {
      message.success(`「${record.name || record.code}」${what}`)
      loadInstances()
    } else {
      message.error(res?.message || '裁剪失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '裁剪失败，请稍后重试')
  }
}

/** 刷新当前文件夹下的列表（与本页其它写操作后的刷新口径一致） */
function refreshStageLists() {
  if (!selectedFolder.value) return
  loadParts(selectedFolder.value.oid)
  if (selectedFolder.value._mergedInherited) {
    loadInheritedDocuments(selectedFolder.value._mergedInherited.oid)
  }
  loadDocuments(selectedFolder.value.oid)
}

/**
 * 流程发起成功后：列表照旧刷，但**还必须重拉方法论实例** ——
 * 阶段门 / 评审的状态来自 {@code /api/instances} 的现算结果（流程在跑 → 进行中），
 * 只刷零件 / 文档的话，用户看到的是"流程已发起、评审行还是未开始"的割裂
 * （2026-10-26 用户反馈"流程发起后，评审清单上没有变化"）。
 */
function onProcessStarted() {
  refreshStageLists()
  loadInstances()
}

/** 评审行的「查看流程」：推到流程实例详情页（进度、各节点意见、流程变量都在那里） */
function openProcessInstance(record) {
  const id = record?.processInstanceId
  if (!id) return
  router.push({ name: 'ProcessInstanceDetail', params: { id } })
}

/** 统一文档操作分发 */
function handleDocAction({ key }, doc) {
  switch (key) {
    case 'view': docViewerDoc.value = doc; docViewerVisible.value = true; break
    case 'rename': openRenameModal({ ...doc, entityType: 'DOC' }); break
    case 'checkout': checkoutDocument(doc); break
    case 'checkoutEdit': checkoutAndEdit(doc); break
    case 'edit': editDocument(doc); break
    case 'checkin': checkinDocument(doc); break
    case 'undoCheckout': undoCheckoutDocument(doc); break
    case 'download': downloadDocument(doc); break
    case 'move': openMoveModal({ ...doc, entityType: 'DOC' }); break
    case 'revise': reviseRecord({ ...doc, entityType: 'DOC' }); break
    case 'lifecycle': openLifecycle({ ...doc, entityType: 'DOC' }); break
    case 'workflow': openStartProcess({ ...doc, entityType: 'DOC' }); break
    case 'delete':
      openDeleteModal({ ...doc, entityType: 'DOC' })
      break
  }
}

/** 统一零组件/文档操作分发 */
function handleMixedAction({ key }, record) {
  const isPart = record.entityType === 'PART'
  switch (key) {
    case 'view':
      if (isPart) openPartDetail(record)
      else { docViewerDoc.value = record; docViewerVisible.value = true }
      break
    case 'rename':
      openRenameModal(record)
      break
    case 'saveAs':
      openSaveAsModal(record)
      break
    case 'history':
      openHistoryModal(record)
      break
    case 'checkout':
      // 通用检出：弹出注释输入框（record 含 entityType 标记）
      checkoutTargetDoc.value = record
      checkoutComment.value = ''
      checkoutModalVisible.value = true
      break
    case 'checkoutEdit':
      checkoutAndEdit(record)
      break
    case 'edit':
      openEditModal(record)
      break
    case 'checkin':
      checkinRecord(record)
      break
    case 'undoCheckout':
      undoCheckoutRecord(record)
      break
    case 'download':
      if (isPart) message.warning('零组件暂无主文件')
      else downloadDocument(record)
      break
    case 'move':
      openMoveModal(record)
      break
    case 'revise':
      reviseRecord(record)
      break
    case 'lifecycle':
      openLifecycle(record)
      break
    case 'workflow':
      openStartProcess(record)
      break
    case 'delete':
      openDeleteModal(record)
      break
  }
}

/** 取消检出（通用，区分零组件/文档） */
function undoCheckoutRecord(record) {
  const isPart = record.entityType === 'PART'
  const isEngDoc = record.entityType === 'ENG_DOCUMENT'
  Modal.confirm({
    title: `确认取消检出 "${record.code || record.name}"？`,
    content: '取消检出将丢弃本次检出后的所有修改，恢复为检出前的版本。',
    okText: '确认取消检出',
    cancelText: '保留检出',
    okType: 'danger',
    onOk: async () => {
      try {
        const res = isPart
          ? await undoCheckoutPartApi(record.oid)
          : isEngDoc
            ? await undoCheckoutEngineeringDocument(record.oid)
            : await undoCheckoutApi(record.oid)
        if (res.code === 200) {
          message.success(`已取消检出: ${record.code || record.name}`)
          refreshAfterCheckoutChange()
        } else {
          message.error(res.message || '取消检出失败')
        }
      } catch (e) {
        message.error('取消检出失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
      }
    }
  })
}

/** 检入（通用，区分零组件/文档），确认后解除检出并将副本保存为新版本 */
function checkinRecord(record) {
  const isPart = record.entityType === 'PART'
  const isEngDoc = record.entityType === 'ENG_DOCUMENT'
  Modal.confirm({
    title: `确认检入 "${record.code || record.name}"？`,
    content: '检入后，本次检出修改的副本将保存为新的版本，并解除检出锁定。',
    okText: '确认检入',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = isPart
          ? await checkinPartApi(record.oid)
          : isEngDoc
            ? await checkinEngineeringDocument(record.oid)
            : await checkinDocApi(record.oid)
        if (res.code === 200) {
          message.success(`已检入: ${record.code || record.name}`)
          refreshAfterCheckoutChange()
        } else {
          message.error(res.message || '检入失败')
        }
      } catch (e) {
        message.error('检入失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
      }
    }
  })
}

      // ==================== 历史版本 ====================
const historyModalVisible = ref(false)
const historyLoading = ref(false)
const historyTitle = ref('')
const historyColumns = [
  { title: '版本', dataIndex: 'displayVersion', key: 'displayVersion', width: 100 },
  { title: '大版本', dataIndex: 'revision', key: 'revision', width: 80 },
  { title: '小版本', dataIndex: 'iteration', key: 'iteration', width: 80 },
  { title: '生命周期状态', key: 'status', width: 120 },
  { title: '最新', dataIndex: 'latest', key: 'latest', width: 70 },
  { title: '检出状态', key: 'checkout', width: 100 },
  { title: '创建人', dataIndex: 'creator', key: 'creator', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
]
const historyData = ref([])

// 版本勾选对比
const historySelectedRowKeys = ref([])
const historySelectedRows = ref([])
const historyRowSelection = computed(() => ({
  selectedRowKeys: historySelectedRowKeys.value,
  onChange: (keys, rows) => {
    if (keys.length > 2) {
      message.warning('最多只能勾选两个版本进行对比')
      historySelectedRowKeys.value = keys.slice(0, 2)
      historySelectedRows.value = rows.slice(0, 2)
      return
    }
    historySelectedRowKeys.value = keys
    historySelectedRows.value = rows
  },
}))

const compareModalVisible = ref(false)
const compareColumns = [
  { title: '版本', key: 'field', dataIndex: 'field', width: 160 },
  { title: '基线版本', key: 'left', dataIndex: 'left' },
  { title: '目标版本', key: 'right', dataIndex: 'right' },
]
/** 分组对比结果：[{ key, title, rows }] */
const compareGroups = ref([])

/** 可对比的字段定义（label + 取值函数） */
function buildCompareFields(record, clsInfo) {
  const fmtTime = (v) => v ? String(v).substring(0, 19).replace('T', ' ') : ''
  const statusText = (r) => r.status?.displayName || r.status?.code || ''
  return [
    { field: '版本', val: record.displayVersion || (record.revision ? `${record.revision}.${record.iteration}` : '') },
    { field: '大版本', val: record.revision || '' },
    { field: '小版本', val: record.iteration != null ? String(record.iteration) : '' },
    { field: '生命周期状态', val: statusText(record) },
    { field: '检出状态', val: record.checkedOut ? `已检出(${record.checkedOutBy || ''})` : '已检入' },
    { field: '检出注释', val: record.checkedOutComment || '' },
    { field: '单位', val: record.unit || '' },
    { field: '来源', val: record.source || '' },
    { field: '分类', val: clsInfo?.name || '' },
    { field: '创建人', val: record.creator || '' },
    { field: '创建时间', val: fmtTime(record.createdAt) },
  ]
}

/** 格式化 IBA 属性值（JSONB 可能是对象/数组/字符串） */
function formatIbaValue(val) {
  if (val == null) return ''
  if (typeof val === 'string') return val
  try { return JSON.stringify(val) } catch { return String(val) }
}

/** 加载分类信息（名称 + IBA 显示名映射，用于 code → 显示名） */
async function loadClsInfo(clsOid) {
  if (!clsOid) return { name: '', nameMap: {} }
  try {
    const [clsRes, ibaDefRes] = await Promise.all([
      getClassification(clsOid),
      getClassificationIBAs(clsOid),
    ])
    const cls = clsRes?.data || clsRes
    const ibaDefs = ibaDefRes?.data || ibaDefRes || []
    const nameMap = {}
    ;(Array.isArray(ibaDefs) ? ibaDefs : []).forEach(def => {
      const code = def.ibaCode || def.code
      const name = def.ibaDisplayName || def.ibaName || def.displayName || def.name || code
      if (code) nameMap[code] = name
    })
    return { name: cls?.name || cls?.code || clsOid, nameMap }
  } catch {
    return { name: clsOid, nameMap: {} }
  }
}

async function openCompareModal() {
  if (historySelectedRows.value.length !== 2) {
    message.warning('请先勾选两个版本进行对比')
    return
  }
  const [a, b] = historySelectedRows.value

  // 加载两个版本的分类信息（分类名称 + 分类 IBA 属性值）
  const [clsA, clsB] = await Promise.all([
    loadClsInfo(a.clsOid),
    loadClsInfo(b.clsOid),
  ])

  // 1. 基本属性对比
  const fieldsA = buildCompareFields(a, clsA)
  const fieldsB = buildCompareFields(b, clsB)
  const basicRows = fieldsA.map((fa, i) => {
    const fb = fieldsB[i]
    return { field: fa.field, left: fa.val, right: fb.val, diff: fa.val !== fb.val }
  })

  // 2. 实体 IBA 属性对比
  const ibaRows = []
  const entityIbaCodes = new Set([
    ...Object.keys(a.entityIba || {}),
    ...Object.keys(b.entityIba || {}),
  ])
  entityIbaCodes.forEach(code => {
    const left = formatIbaValue(a.entityIba?.[code])
    const right = formatIbaValue(b.entityIba?.[code])
    ibaRows.push({
      field: code,
      left,
      right,
      diff: left !== right,
    })
  })

  // 3. 分类 IBA 属性对比（对象实例值，ck_cls_iba_data，entity_oid = 对象 oid）
  const clsIbaRows = []
  const clsIbaCodes = new Set([
    ...Object.keys(a.clsIba || {}),
    ...Object.keys(b.clsIba || {}),
  ])
  clsIbaCodes.forEach(code => {
    const label = clsA.nameMap?.[code] || clsB.nameMap?.[code] || code
    const left = formatIbaValue(a.clsIba?.[code])
    const right = formatIbaValue(b.clsIba?.[code])
    clsIbaRows.push({
      field: label,
      left,
      right,
      diff: left !== right,
    })
  })

  compareGroups.value = [
    { key: 'basic', title: '基本属性', rows: basicRows },
    { key: 'iba', title: 'IBA 属性', rows: ibaRows },
    { key: 'clsIba', title: '分类 IBA 属性', rows: clsIbaRows },
  ]

  // 设置对比弹窗列名：直接使用 displayVersion 作为列名（如 A.1、B.2），
  // 避免「版本 A」「版本 B」这种命名在同大版本下造成的歧义，
  // 同时也能正确区分未来可能涉及的不同大版本
  const verA = a.displayVersion || (a.revision ? `${a.revision}.${a.iteration}` : '')
  const verB = b.displayVersion || (b.revision ? `${b.revision}.${b.iteration}` : '')
  compareColumns[0].title = '版本'
  compareColumns[1].title = verA || '基线版本'
  compareColumns[2].title = verB || '目标版本'
  compareModalVisible.value = true
}

async function openHistoryModal(record) {
  const isPart = record.entityType === 'PART'
  historyTitle.value = `${isPart ? '零组件' : '文档'}历史版本：${record.code || ''} ${record.name || ''}`
  historyModalVisible.value = true
  historyLoading.value = true
  historyData.value = []
  historySelectedRowKeys.value = []
  historySelectedRows.value = []
  try {
    const res = isPart
      ? await getPartIterations(record.oid)
      : await getDocumentIterations(record.oid)
    const list = res?.data || res || []
    historyData.value = (Array.isArray(list) ? list : []).map(it => ({
      ...it,
      displayVersion: it.displayVersion || (it.revision ? `${it.revision}.${it.iteration}` : '-'),
    }))
  } catch {
    historyData.value = []
  } finally {
    historyLoading.value = false
  }
}

/** 检出文档 —— 弹出注释输入框 */
async function checkoutDocument(doc) {
  checkoutTargetDoc.value = doc
  checkoutComment.value = ''
  checkoutModalVisible.value = true
}

/** 检出并编辑：检出成功后自动打开编辑对话框 */
function checkoutAndEdit(record) {
  checkoutTargetDoc.value = record
  checkoutComment.value = ''
  checkoutThenEdit.value = true
  checkoutModalVisible.value = true
}

/** 编辑文档（检出后才可编辑） */
function editDocument(doc) {
  openEditModal(doc)
}

/** 检入文档（文档 Tab） */
function checkinDocument(doc) {
  checkinRecord({ ...doc, entityType: 'DOC' })
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

// ==================== 移动 ====================
const moveModalVisible = ref(false)
const moveSaving = ref(false)
const moveTarget = ref(null)
const moveProductLoading = ref(false)
const moveProductTree = ref([])
/** oid → { nodeType: 'PRODUCT_LINE' | 'PRODUCT_MODEL' | 'RESOURCE_LIBRARY', seriesOid?, containerType? } */
const moveProductMeta = ref({})
/** 资源库分组节点的哨兵值：它只负责把子库归到一起，本身不是可移动目标 */
const MOVE_LIBRARY_GROUP = '__resource-library__'
/** 资源库（企业资源库根节点下的子库） */
const moveLibraries = ref([])
/** 当前选中的目标是不是资源库：是则没有阶段/文件夹 */
const moveToLibrary = computed(() => {
  const meta = moveProductMeta.value[moveSelectedOwner.value] || {}
  return meta.nodeType === 'RESOURCE_LIBRARY'
})
/** 选中的资源库名（提示行用） */
const moveSelectedLibraryName = computed(() => {
  const lib = moveLibraries.value.find(l => l.oid === moveSelectedOwner.value)
  return lib?.name || lib?.code || ''
})
const moveSelectedOwner = ref(undefined)
const moveStageLoading = ref(false)
const moveStages = ref([])
const moveSelectedStage = ref(undefined)
const moveFolderLoading = ref(false)
const moveFolderTree = ref([])
const moveSelectedFolder = ref(undefined)
/**
 * 选中资源库时该库自己的阶段（如「封装·图符库」的「封装图符」）。
 *
 * <p>资源库不显示"研发阶段"，但它的文件夹就挂在这个阶段下 —— 既有创建路径也是这么写的
 * （FootprintSymbolLibrary.vue 里 `payload.stageOid = c.stageOid`），所以移动时要把它带上，
 * 否则会出现"对象的文件夹属于某阶段、对象自己却没有阶段"的不一致。
 */
const moveLibraryStageOid = ref('')

/** 文件夹下拉的占位提示：资源库没配阶段时文件夹就没有来源，要说清而不是给个空列表 */
const moveFolderPlaceholder = computed(() => {
  if (!moveToLibrary.value) return '请选择目标文件夹（可选）'
  return moveLibraryStageOid.value ? '请选择目标文件夹（可选）' : '该资源库下暂无文件夹'
})

/**
 * 按「分类节点」归档的资源库：元器件库 / 标准件库 / 通用件库。
 *
 * <p>这三类资源库在界面上的位置标识是<b>分类树</b>（来自分类管理，左侧是分类而不是文件夹），
 * 所以移动时选的是分类节点；其余资源库（封装·图符库 / 技术文档知识库 / 产品图库 / 其他）
 * 按文件夹组织 —— 一个库到底看哪种，由库的 code 决定，不靠用户猜。
 */
const MOVE_CATEGORY_LIBRARY_CODES = ['COMPONENT', 'STD_PART', 'GEN_PART']

/** 分类树（来自分类管理，未绑定时为空数组） */
const moveCategoryTree = ref([])
const moveCategoryLoading = ref(false)
const moveSelectedCategoryOid = ref(undefined)

/** 当前选中的资源库是否按分类归档 */
const moveLibUsesCategory = computed(() => {
  const meta = moveProductMeta.value[moveSelectedOwner.value] || {}
  return meta.nodeType === 'RESOURCE_LIBRARY' && MOVE_CATEGORY_LIBRARY_CODES.includes(meta.code)
})

/** 打开移动弹窗 */
async function openMoveModal(record) {
  moveTarget.value = record
  moveSelectedOwner.value = undefined
  moveStages.value = []
  moveSelectedStage.value = undefined
  moveFolderTree.value = []
  moveSelectedFolder.value = undefined
  moveModalVisible.value = true
  await loadMoveProductTree()
  // 默认回填当前所属目标、阶段、文件夹（无需用户从头选择）
  if (record.containerOid) {
    moveSelectedOwner.value = record.containerOid
    // 资源库分支里目标位置已经加载好了，这里只需回填选中项
    await onMoveOwnerChange(record.containerOid)
    if (moveToLibrary.value) {
      // 分类归档的库回填分类；文件夹归档的库回填文件夹
      if (moveLibUsesCategory.value) {
        if (record.clsOid) moveSelectedCategoryOid.value = record.clsOid
      } else if (record.folderOid) {
        moveSelectedFolder.value = record.folderOid
      }
    } else if (record.stageOid) {
      moveSelectedStage.value = record.stageOid
      await onMoveStageChange(record.stageOid)
      if (record.folderOid) moveSelectedFolder.value = record.folderOid
    }
  }
}

/** 加载产品系列 + 型号树（外加资源库分组） */
async function loadMoveProductTree() {
  moveProductLoading.value = true
  try {
    // 两条来源互不影响：系列树拿不到也要把资源库列出来（它是另一类目标）
    const [lineRes, libRes] = await Promise.all([
      getProductLineTree(),
      getResourceChildren().catch(() => null),
    ])
    const lines = lineRes?.code === 200 ? (lineRes.data || []) : []
    const libs = libRes?.code === 200 ? (libRes.data || []) : []
    moveLibraries.value = libs
    const tree = []
    const meta = {}

    async function buildNode(lineNode) {
      const node = {
        title: lineNode.name || lineNode.code,
        value: lineNode.oid,
        key: lineNode.oid,
      }
      meta[lineNode.oid] = { nodeType: 'PRODUCT_LINE', seriesOid: lineNode.oid }
      try {
        const modelsRes = await getProductModels({ productLineOid: lineNode.oid })
        if (modelsRes.code === 200 && modelsRes.data?.length) {
          const modelNodes = modelsRes.data.map(m => {
            meta[m.oid] = { nodeType: 'PRODUCT_MODEL', seriesOid: lineNode.oid }
            return { title: m.name || m.code, value: m.oid, key: m.oid }
          })
          node.children = modelNodes
        }
      } catch { /* ignore */ }
      if (lineNode.children?.length) {
        const childNodes = await Promise.all(lineNode.children.map(buildNode))
        node.children = [...(node.children || []), ...childNodes]
      }
      return node
    }

    for (const line of lines) {
      tree.push(await buildNode(line))
    }
    // 资源库分组：分组节点本身不可选（它只是把子库归到一处），子库是可移动的目标
    if (libs.length) {
      tree.push({
        title: '资源库',
        value: MOVE_LIBRARY_GROUP,
        key: MOVE_LIBRARY_GROUP,
        selectable: false,
        children: libs.map(l => {
          meta[l.oid] = {
            nodeType: 'RESOURCE_LIBRARY',
            containerType: l.containerType || 'CORP_RESOURCE',
            // code 决定这个库是"选分类"还是"选文件夹"（见 MOVE_CATEGORY_LIBRARY_CODES）
            code: l.code,
            name: l.name,
          }
          return { title: l.name || l.code, value: l.oid, key: l.oid }
        }),
      })
    }
    moveProductTree.value = tree
    moveProductMeta.value = meta
  } catch {
    moveProductTree.value = []
    moveProductMeta.value = {}
  } finally {
    moveProductLoading.value = false
  }
}

/** 选择目标（产品系列/型号/资源库） → 加载阶段（资源库则是该库自己的阶段与文件夹） */
async function onMoveOwnerChange(ownerOid) {
  moveSelectedStage.value = undefined
  moveFolderTree.value = []
  moveSelectedFolder.value = undefined
  moveLibraryStageOid.value = ''
  moveSelectedCategoryOid.value = undefined
  moveCategoryTree.value = []
  if (!ownerOid) { moveStages.value = []; return }
  // 资源库：不显示"研发阶段"，位置由"分类节点"或"文件夹"决定（按库的 code 分流）
  if (moveProductMeta.value[ownerOid]?.nodeType === 'RESOURCE_LIBRARY') {
    moveStages.value = []
    await loadMoveLibraryContext(ownerOid)
    return
  }
  const meta = moveProductMeta.value[ownerOid] || {}
  const seriesOid = meta.seriesOid || ownerOid
  moveStageLoading.value = true
  try {
    const res = await getStages(seriesOid)
    moveStages.value = res.code === 200 ? (res.data || []) : []
  } catch {
    moveStages.value = []
  } finally {
    moveStageLoading.value = false
  }
}

/**
 * 加载资源库的目标位置：按分类归档的库给分类树，其余给文件夹树。
 *
 * <p>两类库的"位置"来源不同（这是实测与既有代码共同确认的）：
 * <ul>
 *   <li><b>按分类归档</b>（元器件库 COMPONENT / 标准件库 STD_PART / 通用件库 GEN_PART）：
 *       位置 = 分类节点，分类树来自分类管理（{@code getLibraryCategoryTree}）；</li>
 *   <li><b>按文件夹组织</b>（封装·图符库等）：文件夹挂在该库<b>自己的阶段</b>下
 *       （实测「封装·图符库」有「电阻符号 / 电容符号 / 封装」，属于阶段「封装图符」），
 *       与 FootprintSymbolLibrary.vue 同一约定。</li>
 * </ul>
 *
 * <p>库自己的阶段无论是哪种，都要取出来写进 stageOid（与既有创建路径一致），
 * 但它不显示给用户：资源库场景里它没有"研发阶段"的业务含义。
 */
async function loadMoveLibraryContext(libOid) {
  moveFolderLoading.value = true
  moveCategoryLoading.value = true
  try {
    moveLibraryStageOid.value = await resolveLibraryStageOid(libOid)

    if (moveLibUsesCategory.value) {
      // 分类归档：不碰文件夹（那里本来也没有文件夹）
      moveFolderTree.value = []
      const meta = moveProductMeta.value[libOid] || {}
      const res = await getLibraryCategoryTree(meta.code)
      // 接口返回的是"该库绑定的分类根节点"（未绑定则为 null）：取它的子节点作为可选的分类
      const root = res?.code === 200 ? res.data : null
      moveCategoryTree.value = (root?.children || []).map(toCategoryNode)
      return
    }

    moveCategoryTree.value = []
    if (!moveLibraryStageOid.value) {
      moveFolderTree.value = []
      return
    }
    const res = await getFolderTree(libOid, moveLibraryStageOid.value)
    moveFolderTree.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    moveFolderTree.value = []
    moveCategoryTree.value = []
  } finally {
    moveFolderLoading.value = false
    moveCategoryLoading.value = false
  }
}

/**
 * 解析资源库自己的阶段 oid。
 *
 * <p>为什么不能用 {@code getStages(库 oid)} 打天下：资源库（及其阶段）是<b>平台级</b>数据，
 * 而阶段查询挂在 {@code /product-lines/{oid}/stages} 下，在租户侧会查不到 —— 结果是 stage_oid 传空，
 * 而 {@code ck_part.stage_oid} 是 <b>NOT NULL</b>，移动直接失败在数据库约束上。
 *
 * <p>所以优先用"库自己的上下文接口"（库页面本来就在用它，租户侧也能拿到）：
 * 元器件库 → {@code /resource-libraries/context}；封装·图符库 → {@code /package-symbols/context}；
 * 其余库（标准件库 / 通用件库 等）退回按归属者查阶段。
 */
async function resolveLibraryStageOid(libOid) {
  const code = (moveProductMeta.value[libOid] || {}).code
  const fromContext = async (fn) => {
    try {
      const res = await fn()
      return res?.code === 200 ? (res.data?.stageOid || '') : ''
    } catch {
      return ''
    }
  }
  if (code === 'COMPONENT') {
    const oid = await fromContext(getElectronicComponentContext)
    if (oid) return oid
  }
  if (code === 'PACKAGE_SYMBOL') {
    const oid = await fromContext(getPackageSymbolContext)
    if (oid) return oid
  }
  try {
    const st = await getStages(libOid)
    const stages = st?.code === 200 ? (st.data || []) : []
    return stages[0]?.oid || ''
  } catch {
    return ''
  }
}

/** 分类节点 → 树选择控件的数据形状（字段名对齐 value/label/key/children） */
function toCategoryNode(node) {
  return {
    title: node.displayName || node.name || node.code,
    value: node.oid,
    key: node.oid,
    children: (node.children || []).map(toCategoryNode),
  }
}

/** 选择阶段 → 加载文件夹 */
async function onMoveStageChange(stageOid) {
  moveSelectedFolder.value = undefined
  moveFolderTree.value = []
  if (!stageOid) return
  const meta = moveProductMeta.value[moveSelectedOwner.value] || {}
  const seriesOid = meta.seriesOid || moveSelectedOwner.value
  moveFolderLoading.value = true
  try {
    const res = await getFolderTree(seriesOid, stageOid)
    moveFolderTree.value = res.code === 200 ? (res.data || []) : []
  } catch {
    moveFolderTree.value = []
  } finally {
    moveFolderLoading.value = false
  }
}

/** 确认移动 */
async function confirmMove() {
  if (!moveTarget.value) return
  const record = moveTarget.value
  const isPart = record.entityType === 'PART'
  const ownerOid = moveSelectedOwner.value
  if (!ownerOid) { message.warning('请选择产品系列/型号/资源库'); return }
  const meta = moveProductMeta.value[ownerOid] || {}
  // 分组节点不是目标（节点上已标 selectable:false，这里再兜一道）
  if (meta.nodeType === MOVE_LIBRARY_GROUP) { message.warning('请选择具体的资源库'); return }
  const toLibrary = meta.nodeType === 'RESOURCE_LIBRARY'
  const containerOid = meta.nodeType === 'PRODUCT_MODEL' ? meta.seriesOid : ownerOid
  const containerType = toLibrary
    ? (meta.containerType || 'CORP_RESOURCE')
    : (meta.nodeType === 'PRODUCT_MODEL' ? 'PRODUCT_MODEL' : 'PRODUCT_LINE')
  // 资源库不显示"研发阶段"，但它自己的阶段要写进 stageOid（文件夹就挂在那里）；
  // 文件夹两种目标都允许：系列/型号下按"系列 + 阶段"，资源库下按"库 + 库阶段"。
  let stageOid = toLibrary ? (moveLibraryStageOid.value || '') : (moveSelectedStage.value || '')
  if (toLibrary && !stageOid) {
    // 兜底：不信"加载时是否已就绪"，提交前再解析一次。
    // ck_part.stage_oid 是 NOT NULL，空值会在数据库约束上直接失败（用户看到的 PSQLException 就是它）。
    stageOid = await resolveLibraryStageOid(ownerOid)
    moveLibraryStageOid.value = stageOid
  }
  if (toLibrary && !stageOid) {
    message.error('该资源库未配置阶段，无法归档：请先在该资源库下配置阶段后再移动')
    return
  }
  // 分类归档的资源库不涉及文件夹；其余目标（系列/型号、按文件夹组织的资源库）用文件夹
  const folderOid = moveLibUsesCategory.value ? '' : (moveSelectedFolder.value || '')
  // 分类节点：后端 move 一并落库（null = 本次不涉及分类）。必须走 move：
  // ck_part 的 update 是全量列更新，单独"只改分类"的更新会把其它列冲成 NULL。
  const clsOid = moveLibUsesCategory.value ? (moveSelectedCategoryOid.value || null) : null
  if (!toLibrary && !stageOid) { message.warning('请选择阶段'); return }
  moveSaving.value = true
  try {
    const data = { containerOid, containerType, folderOid, stageOid, clsOid }
    const res = isPart ? await movePart(record.oid, data) : await moveDocumentApi(record.oid, data)
    if (res.code === 200) {
      if (isPart && clsOid && res.data && res.data.clsOid !== clsOid) {
        // 后端若还没重启（旧版 move 不认识 clsOid），分类不会写进去 —— 明确告知，不静默通过。
        // 只看 Part：分类（ck_part.cls_oid）是 Part 特有字段，文档走的是另一条 move。
        message.warning('已移动，但「分类」未保存：后端需重启以启用移动接口的分类字段')
      } else {
        message.success(
          toLibrary ? `已移入资源库「${moveSelectedLibraryName.value || '资源库'}」` : '移动成功',
        )
      }
      moveModalVisible.value = false
      if (selectedFolder.value) {
        if (isPart) loadParts(selectedFolder.value.oid)
        loadDocuments(selectedFolder.value.oid)
      }
    } else {
      message.error(res.message || '移动失败')
    }
  } catch (e) {
    message.error('移动失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    moveSaving.value = false
  }
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
      // 按创建时间正序排序（同级文件夹：最早创建的在前），并对每级子文件夹递归排序
      sortFolderTreeByCreatedAtAsc(folderTree.value)
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
          sortFolderTreeByCreatedAtAsc(parentFolderTree.value)
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
    // 换了产品：物料清单 / 产品数模必须作废重取（否则会显示上一个产品的数据）
    bomLoaded.value = false
    bomMaterials.value = []
    cadModelsLoaded.value = false
    cadModels.value = []
    stopCadPolling()
    loadChildren(oid)
    loadModelCount(oid)
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
    await loadFolders()           // 再按当前阶段加载文件夹
    ensureFirstFolderSelected()   // 默认选中第一个文件夹并展示其内容
  } catch { message.error('加载产品线信息失败') }
}

/**
 * 拉当前容器的物料清单（懒加载：切到「物料清单」页签才查）。
 *
 * <p>{@code bomLoaded} 是"这份数据已经取过"的标记：没有它，每次切页签都会重查一遍；
 * 但它必须跟着容器一起重置，否则换了产品还显示上一个产品的物料。
 */
const loadBomMaterials = async (force = false) => {
  // 传容器自身 oid：后端只按 container_oid = 该 oid 过滤，不回溯父系列（型号页不混入系列零件）
  const containerOid = line.value?.oid
  if (!containerOid) {
    bomMaterials.value = []
    return
  }
  if (bomLoaded.value && !force) return
  bomMaterialsLoading.value = true
  try {
    const res = await getBomMaterials(containerOid)
    bomMaterials.value = (res.code === 200 && res.data) ? res.data : []
  } catch {
    bomMaterials.value = []
    bomMaterialsLoading.value = false
    return
  }
  bomMaterialsLoading.value = false
  bomLoaded.value = true
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
      // 从整机视图下钻（?stage=<code>）：按 code 定位到该阶段，并标记"用户已选定"，
      // 免得自动推断（第一个门未通过的阶段）把它抢走。命中不了（阶段被裁掉）就走原推断。
      const wanted = String(route.query.stage || '')
      const hit = wanted ? stageDefs.value.find(s => s.key === wanted) : null
      if (hit) {
        activeStage.value = hit.oid
        userPickedStage.value = true
      }
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

/**
 * 型号数量 —— 页头「型号」那颗统计。
 *
 * <p>数据源是 {@code GET /product-lines/stats}（与「系列/型号」列表的 modelCount 同一份统计），
 * 不是再拼一次查询。曾经这里根本没赋值：{@code modelCount} 声明在那儿、模板也在用，
 * 但从没人给它写过值，于是永远显示 0（2026-10-25 用户发现"型号统计不正确"）。
 */
const loadModelCount = async (oid) => {
  modelCount.value = 0
  try {
    const res = await getProductLineStats()
    if (res.code === 200 && res.data) {
      modelCount.value = res.data[oid]?.modelCount || 0
    }
  } catch {
    // 统计取不到就保持 0：宁可少显示，也不编一个数字
    modelCount.value = 0
  }
}

/** 加载完成后，如果当前阶段存在文件夹且未选中任何文件夹，自动选中第一个并展示其内容 */
function ensureFirstFolderSelected() {
  if (folderTree.value.length === 0) { selectedFolder.value = null; return }
  if (selectedFolder.value) return
  selectFolder(folderTree.value[0])
}

// 懒加载：切到页签才查（BOM 行可能上百条、数模文件可能很大，不该跟研发资料一起查）
// 同时监听容器 oid：默认页签就是 bom 时"切换"事件根本不会发生，只有 oid 到位才补得上首次查询；
// 换产品时 line.oid 变化也会重查（bomLoaded 在 loadProductLine 里已重置）。
watch([stageView, () => line.value?.oid], ([view]) => {
  if (!line.value?.oid) return
  if (view === 'bom') loadBomMaterials()
  if (view === 'cad') {
    loadCadModels()
    loadCadAvailability()
  }
}, { immediate: true })

watch(activeStage, async () => {
  selectedFolder.value = null
  documents.value = []
  inheritedDocuments.value = []
  parts.value = []
  await loadFolders()
  ensureFirstFolderSelected()
})
onMounted(() => {
  // 能力开关先拉（决定六个面板显不显示）；实例要等 line 就位才知道归属 oid，故跟在它后面
  loadCapabilities()
  loadProductLine().then(loadInstances)
  // 业务对象过滤下拉：加载 source=ootb 的类型清单（与其他来源/模型定义页面同源）
  loadBizObjectFilterOptions()
})
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
  /* 整页可纵向滚动：新增「整机视角」后，短屏上"只让内层滚"会把阶段区压成 0 高
     （元素在 DOM 里、点得到，但什么都看不见）。 */
  overflow-y: auto;
}

/* ===== 头部 ===== */
.pl-header {
  flex-shrink: 0;
  background: #fff;
  border: 1px solid var(--pl-border);
  border-radius: var(--pl-radius);
  /* 页头是"每条都要看一眼"的信息条，本该尽量矮：上下内边距与下外边距各收紧一档，
     把省下的高度让给内容区（阶段页签 + 文件夹树 + 表格）—— 用户反馈"往上移动、加大利用空间" */
  padding: 12px 20px 10px;
  margin-bottom: 12px;
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
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  /* 阶段头并进来后，窄屏允许换行（否则会把统计 pill 挤扁） */
  flex-wrap: wrap;
}
/* 页头三块的左右次序：阶段头 ⇄ 标题块（用户要求"交换位置"，并让产品系列/型号尽量靠右）。
   用 order 而不搬动 DOM：这块模板改过多次，集中一处可读，也不必再切一次结构。 */
.pl-header-main > .pl-stage-head  { order: 1; }                      /* 阶段头：最左 */
.pl-header-main > .pl-header-info { order: 2; margin-left: auto; }   /* 标题块：吃满中间空档、贴住右侧统计列 */
.pl-header-main > .pl-header-side { order: 3; }                      /* 统计 + 描述：最右 */

.pl-header-info { min-width: 0; }

.pl-breadcrumb { margin-bottom: 2px; }

.pl-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  /* 描述/创建已挪到右列，标题行下面不再需要预留空挡 */
  margin-bottom: 0;
}
.pl-title { margin: 0; font-size: 22px; font-weight: 700; color: var(--pl-text); letter-spacing: -.3px; }
.pl-code-tag { font-family: monospace; font-size: 12px; }

.pl-meta {
  margin: 0;
  font-size: 12px;
  color: var(--pl-text-muted);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  max-width: 100%;
  min-width: 0;
}
/* 描述是变长的：由它自己截断（悬停看全）——不能让它把"创建于"挤出可视区。
   注意 text-overflow 加在 flex 容器上对子项无效，必须落在这一层 */
.pl-meta-desc { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
.pl-meta-created { flex-shrink: 0; }
.pl-meta-sep { display: inline-block; width: 3px; height: 3px; border-radius: 50%; background: #d9d9d9; flex-shrink: 0; }

/* 右侧一列：统计 pill 在上，描述/创建信息在它下面（同一列、靠右对齐） */
.pl-header-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
  max-width: 52%;
  min-width: 0;
}

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
  /* 比原来的 8px 14px 矮 4px：三个 pill 并排是页头里第二高的元素，收一点就够看 */
  padding: 6px 12px;
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
  flex: 1 0 auto;      /* 空间不足时不被压缩 —— 短屏交给整页滚动兜住 */
  min-height: 60vh;    /* 阶段区保底高度：否则面板会被压成 0 高（看得见页签、点得到，但什么都看不到） */
  /* 2026-10-11：随"只保留外层滚动条"改为 visible —— 内层不再自滚后需随内容自然增高，
     否则 overflow:hidden 会把超出的阶段内容裁掉 */
  overflow: visible;
  display: flex;
  flex-direction: column;
}
.pl-stage-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.pl-stage-tabs :deep(.ant-tabs-content-holder) { flex: 1; min-height: 0; overflow-y: auto; }
.pl-stage-tabs :deep(.ant-tabs-content) { height: 100%; }
.pl-stage-tabs :deep(.ant-tabs-tabpane) { height: 100%; min-height: 0; }
.pl-stage-tabs :deep(.ant-tabs-nav) { margin-bottom: 12px; flex-shrink: 0; }
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
.pl-folders {
  margin-top: 8px;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

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

/* 两栏布局：撑满剩余高度，左右两栏各自内部滚动。
   grid-template-rows: minmax(0,1fr) 让行高撑满 flex 分配的空间 */
.pl-folders-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 260px 1fr;
  grid-template-rows: minmax(0, 1fr);
  gap: 16px;
}

/* 左侧树 */
.pl-folders-tree {
  border: 1px solid var(--pl-border);
  border-radius: var(--pl-radius);
  background: #fff;
  display: flex;
  flex-direction: column;
}
/* 树内容区：2026-10-11 起不再独立滚动（原先 max-height:560px + overflow-y:auto 会与外层
   页签滚动叠加成"并排双滚动条"）—— 树随内容自然变高，统一交给整页（.pl-dashboard）滚动 */
.pl-folders-tree-scroll {
  flex: 1 1 auto;
  padding: 6px 0 8px;
}
/* 新建文件夹按钮：在滚动区下方，始终可见可点击 */
.pl-folders-tree :deep(.pl-tree-new-btn) {
  flex-shrink: 0;
  box-sizing: border-box;
  width: 100%;
  margin: 0 0 8px;
  border-top: 1px solid var(--pl-border);
  border-radius: 0 0 6px 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
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
  min-height: 0;
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

/* 编码列超链接：等宽字体便于逐字符对照，hover 才出下划线，整列不至于花 */
.pl-code-link {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
}
.pl-code-link:hover {
  text-decoration: underline;
}

.pl-folders-content-files {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  color: var(--pl-text-muted);
  gap: 0;
}
.pl-folders-content-files p { margin: 0; }
.pl-folders-content-files-icon { font-size: 40px; opacity: .3; }

.pl-folders-content-hint {
  font-size: 12px;
  color: #ccc;
}

/* ===== 滚动条美化：纤细半透明，减少视觉干扰 ===== */
/* webkit 浏览器（Chrome/Edge/Safari） */
.pl-folders-tree-scroll::-webkit-scrollbar,
.pl-folders-tree-scroll::-webkit-scrollbar-track,
.pl-folders-tree-scroll::-webkit-scrollbar-thumb,
.pl-folders-content-files::-webkit-scrollbar,
.pl-folders-content-files::-webkit-scrollbar-track,
.pl-folders-content-files::-webkit-scrollbar-thumb {
  width: 6px;
  height: 6px;
}
.pl-folders-tree-scroll::-webkit-scrollbar-track,
.pl-folders-content-files::-webkit-scrollbar-track {
  background: transparent;
}
.pl-folders-tree-scroll::-webkit-scrollbar-thumb,
.pl-folders-content-files::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
  transition: background 0.2s;
}
.pl-folders-tree-scroll::-webkit-scrollbar-thumb:hover,
.pl-folders-content-files::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.35);
}

/* antd a-table 表体/表头/分页器区域滚动条 */
.pl-folders-content-files :deep(.ant-table-body)::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.pl-folders-content-files :deep(.ant-table-body)::-webkit-scrollbar-track {
  background: transparent;
}
.pl-folders-content-files :deep(.ant-table-body)::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
}
.pl-folders-content-files :deep(.ant-table-body)::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.35);
}

/* Firefox 兼容 */
.pl-folders-tree-scroll,
.pl-folders-content-files,
.pl-folders-content-files :deep(.ant-table-body) {
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.2) transparent;
}

/* ===== 文档列表 ===== */
.pl-doc-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 1px;
  /* 2026-10-11 起不再独立滚动（原 overflow-y:auto 与页签/整页滚动叠加成双条）：自然高度 */
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
  /* 2026-10-11 起不再独立滚动（原 max-height:220px + overflow-y:auto 会与外层叠加成双条） */
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

/* ===== 版本对比分组 ===== */
.compare-group {
  margin-bottom: 16px;
}
.compare-group:last-child {
  margin-bottom: 0;
}
.compare-group-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  line-height: 1.2;
}

/* ===== 新建零组件弹窗：紧凑一屏展示 ===== */
.part-create-modal .ant-modal-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--pl-border);
}
.part-create-modal .ant-modal-title {
  font-size: 15px;
}
.part-create-modal .ant-modal-footer {
  padding: 10px 16px;
  border-top: 1px solid var(--pl-border);
}
.part-create-modal :deep(.ant-form-item) {
  margin-bottom: 12px;
}
.part-create-modal :deep(.ant-form-item-label) {
  padding-bottom: 2px;
}
.part-create-modal :deep(.ant-form-item-label > label) {
  font-size: 13px;
  color: #595959;
}
.part-create-modal :deep(.df-cls-section) {
  margin-top: 10px;
  padding-top: 10px;
}
.part-create-modal :deep(.df-cls-section-header) {
  margin-bottom: 8px;
}
.part-create-modal :deep(.df-cls-section-title) {
  font-size: 14px;
}
.part-create-modal :deep(.dynamic-form) {
  min-height: 0;
}
.part-create-modal :deep(.ant-form) {
  padding-right: 4px;
}
/* 整机视角（指标 / 驾驶舱）2026-10-11 起为独立页 /product/:oid/overview
   （ProductOverview.vue）—— 相关样式（.pl-overview 一族）随之迁走 */
/* 阶段内视图页签（研发资料 / 交付件 / 阶段门 / 评审 / 检查规则）
   注：原先把"阶段区内部滚动"搬到这里（.pl-stage-tabs 时代的历史包袱）；
   ★ 2026-10-11 改回**只保留外层一条滚动条**（用户反馈：五个页签各出现两条并排竖条）：
   外层 = .pl-dashboard 的 overflow-y:auto（整页滚），本层不再自滚 ——
   内容随自然高度增长，表格也不设 max-height（见 MethodologyInstancePanel）。 */
.pl-stage-view {
  display: flex;
  flex-direction: column;
}
.pl-stage-view :deep(.ant-tabs-content-holder) { overflow: visible; }
.pl-stage-view :deep(.ant-tabs-content) { height: auto; }
.pl-stage-view :deep(.ant-tabs-tabpane) { height: auto; min-height: 0; }
.pl-stage-view :deep(.ant-tabs-nav) { margin-bottom: 8px; flex-shrink: 0; }
.pl-stage-view :deep(.ant-tabs-tab) { font-size: 13px; }

/* 物料清单页签：一行 = 一颗物料（不是一条 BOM 行）——
   顶部先把口径写出来，免得用户对着表格猜"为什么数量是加起来的" */
.pl-bom-materials { margin-top: 8px; }
.pl-bom-materials-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--pl-text-muted);
  font-size: 12px;
}
.pl-bom-materials-hint strong { font-weight: 600; }
.pl-bom-materials-count { flex-shrink: 0; display: inline-flex; align-items: center; }

/* 产品数模页签：一行 = 一份三维数模文件。
   单元格里用「主值 + 灰色副值」两层，避免为次要信息多开列把表格撑宽 */
.pl-cad-models { margin-top: 8px; }
.pl-cad-sub {
  color: var(--pl-text-muted);
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 批量提交结果 */
.pl-batch-summary {
  font-size: 13px;
  color: #262626;
}
.pl-batch-summary b {
  font-variant-numeric: tabular-nums;
}
.pl-batch-summary .ok { color: #389e0d; }
.pl-batch-summary .warn { color: #d48806; }
.pl-batch-summary .err { color: #cf1322; }
.pl-batch-msg {
  font-size: 12px;
  color: #8c8c8c;
  display: inline-block;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pl-batch-ok {
  padding: 12px 0;
  text-align: center;
  color: #389e0d;
  font-size: 13px;
}

/* 转换中：进度条 + 阶段文案压在同一格里，列宽 130 也放得下 */
.pl-cad-progress {
  margin-top: 2px;
}
.pl-cad-progress :deep(.ant-progress) {
  margin: 0;
  line-height: 1;
}
.pl-cad-file {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
/* 缩略图外层：锁角标要绝对定位在右下角，需要一个定位上下文 */
.pl-cad-thumb-wrap {
  position: relative;
  display: inline-block;
  width: 48px;
  height: 48px;
  transition: transform 0.15s ease;
}
/* 可点进三维浏览时给足反馈：手型 + 描边变主色 + 轻微上浮，
   否则用户根本猜不到这张小图能点 */
.pl-cad-thumb-wrap.is-clickable {
  cursor: pointer;
}
.pl-cad-thumb-wrap.is-clickable:hover {
  transform: translateY(-1px);
}
.pl-cad-thumb-wrap.is-clickable:hover :deep(.ant-image-img),
.pl-cad-thumb-wrap.is-clickable:hover .pl-cad-thumb-empty {
  border-color: #1677ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.2);
}
/* 检出角标：橙色圆底 + 白色描边，压在缩略图右下角，不遮挡主体 */
.pl-cad-thumb-lock {
  position: absolute;
  right: -5px;
  bottom: -5px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fa8c16;
  color: #fff;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
  cursor: default;
}
/* 缩略图：转换器出的是透明底 PNG，衬一层浅灰底避免白件在白底上"消失" */
.pl-cad-thumb :deep(.ant-image-img) {
  width: 48px;
  height: 48px;
  object-fit: contain;
  border-radius: 4px;
  border: 1px solid var(--pl-border, #f0f0f0);
  background: #fafafa;
}
.pl-cad-thumb-empty {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  border: 1px dashed var(--pl-border, #e5e5e5);
  background: #fafafa;
  color: #bfbfbf;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* ===== 物料清单缩略图（40px：比「产品数模」的 48px 小一号，物料行更密） ===== */
.pl-bom-thumb :deep(.ant-image-img) {
  width: 40px;
  height: 40px;
  object-fit: contain;
  border-radius: 4px;
  border: 1px solid var(--pl-border, #f0f0f0);
  background: #fafafa;
}
.pl-bom-thumb-empty {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  border: 1px dashed var(--pl-border, #e5e5e5);
  background: #fafafa;
  color: #d9d9d9;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pl-cad-file > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 阶段头：并入页头左列（原为独立卡片，独吞一整行 —— 用户反馈"页头占的篇幅太多"）。
   分两行：上行=阶段名，下行=门状态与切换下拉（用户要求"阶段与汇总信息分两行"）。
   分隔线在右侧 —— 它现在站在页头最左边（与标题块左右对调）。 */
.pl-stage-head {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding-right: 16px;
  border-right: 1px solid var(--pl-border);
}
.pl-stage-current { display: flex; align-items: center; gap: 8px; min-width: 0; }
.pl-stage-title { font-size: 15px; font-weight: 600; }
/* 下行：门状态 + 切换下拉 */
.pl-stage-summary { display: flex; align-items: center; gap: 8px; }
/* 注：.pl-stage-switch / .pl-stage-desc / .pl-stage-switch-label 已随改版删除 ——
   说明文字改 title 悬停，切换下拉直接收进 .pl-stage-summary。 */

/* ===== 阶段门页签里的两块（决策门 / 技术评审） ===== */
.stage-block + .stage-block { margin-top: 18px; }
.stage-block-head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; }
.stage-block-title { font-size: 13px; font-weight: 600; color: #262626; }
.stage-block-count {
  min-width: 20px; padding: 0 6px; border-radius: 9px;
  background: #f0f5ff; color: #1677ff; font-size: 12px; text-align: center;
}
.stage-block-hint { font-size: 12px; color: #8c8c8c; }
</style>
