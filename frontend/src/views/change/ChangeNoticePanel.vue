<template>
  <div class="chg-panel">
    <!-- 口径条（与产品页「物料清单」同一版式）：左边说清这段列表是什么，右边给条数。
         状态筛选与各自条数在**页面头部**（ChangeControl 的状态概览标签），
         本面板只按父级给的状态取数（props.status）。 -->
    <div class="ck-list-bar">
      <span>本企业全部变更通告（ECN）：由变更请求派发或直接新建，关闭前须完成其下变更任务</span>
      <span class="ck-list-bar__count">共 {{ total }} 条</span>
    </div>

    <DataTable
      scroll-x="max-content"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      pagination-mode="backend"
      :pagination="{ current: page, pageSize }"
      :total="total"
      search-placeholder="搜索编号 / 标题 / 变更原因..."
      :search-fields="['code', 'name', 'reason']"
      row-key="oid"
      size="small"
      empty-text="暂无变更通告"
      @change="handleTableChange"
      @update:search="handleSearch"
    >
      <!-- 状态筛选在页头（见上方的状态概览标签）；主按钮留在右侧与搜索、视图同排 -->
      <template #toolbar>
        <a-button type="primary" size="small" @click="openCreate">
          <template #icon><PlusOutlined /></template>
          新建变更通告
        </a-button>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'code'">
          <a-tag color="geekblue" size="small">{{ record.code }}</a-tag>
        </template>
        <template v-else-if="column.key === 'name'">
          <a class="chg-link" @click="openDetail(record)">{{ record.name }}</a>
          <a-tag v-if="record.approvalBypassed" color="orange" size="small" class="chg-bypass-tag">免审</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="CHANGE_NOTICE_STATUS_COLOR[record.status] || 'default'" size="small">
            {{ CHANGE_NOTICE_STATUS_LABEL[record.status] || record.status }}
          </a-tag>
        </template>
        <!-- 类型 / 分级：只用通告的租户靠这两列判断轻重，藏在详情里等于没有 -->
        <template v-else-if="column.key === 'changeType'">
          <span>{{ CHANGE_TYPE_LABEL[record.changeType] || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'changeLevel'">
          <a-tag v-if="record.changeLevel" :color="CHANGE_LEVEL_COLOR[record.changeLevel] || 'default'" size="small">
            {{ CHANGE_LEVEL_LABEL[record.changeLevel] || record.changeLevel }}
          </a-tag>
          <span v-else>—</span>
        </template>
        <template v-else-if="column.key === 'planEnd'">
          <span>{{ record.planEnd || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space size="small">
            <!-- 详情对所有人开放；其余动作只有责任人可点。disabled 的按钮不触发鼠标事件，
                 所以外面套一层 span 让 tooltip 还能弹出"为什么不能点" -->
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <!-- 编辑入口在列表上：详情抽屉只读，避免"看着详情改数据"时看不出改了哪一版 -->
            <a-tooltip
              v-if="noticeEditable(record.status)"
              :title="isOwner(record) ? undefined : ownerHint(record)"
            >
              <span>
                <a-button
                  type="link"
                  size="small"
                  :disabled="!isOwner(record)"
                  @click="openEdit(record)"
                >
                  编辑
                </a-button>
              </span>
            </a-tooltip>
            <a-tooltip
              v-if="primaryNoticeAction(record.status)"
              :title="isOwner(record) ? undefined : ownerHint(record)"
            >
              <span>
                <a-button
                  type="link"
                  size="small"
                  :disabled="!isOwner(record)"
                  @click="openTransition(record, primaryNoticeAction(record.status))"
                >
                  {{ noticeActionLabel(primaryNoticeAction(record.status), approvalRequired) }}
                </a-button>
              </span>
            </a-tooltip>
            <a-popconfirm
              v-if="record.status === 'DRAFT'"
              :title="isOwner(record) ? '确定删除该变更通告？受影响对象与任务会一并删除' : ownerHint(record)"
              :ok-button-props="{ disabled: !isOwner(record) }"
              :cancel-button-props="{ disabled: !isOwner(record) }"
              @confirm="handleDelete(record)"
            >
              <a-button type="link" size="small" danger :disabled="!isOwner(record)">删除</a-button>
            </a-popconfirm>
            <!--
              管理员通道：单据被卡住（审批在外部系统挂了 / 流程实例丢了 / 结论回写失败）时，
              直接把状态拨到它该在的位置。与「提交审批」这类正常动作分开放，
              并且只对租户管理员露出（服务端另有 403 兜底）。
            -->
            <a-button
              v-if="isTenantAdmin"
              type="link"
              size="small"
              @click="openStatusForm(record)"
            >
              设置状态
            </a-button>
          </a-space>
        </template>
      </template>
    </DataTable>

    <!-- 新建 / 编辑 -->
    <a-modal
      v-model:open="modal.visible"
      :title="modal.isEdit ? '编辑变更通告' : '新建变更通告'"
      :confirm-loading="modal.saving"
      :width="formWidth"
      centered
      ok-text="保存"
      cancel-text="取消"
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' }"
      @ok="handleSave"
    >
      <!--
        一张变更通告由两部分组成：基本属性（改什么 / 多严重 / 为什么）与更改事项
        （谁来改 = 草稿变更任务清单）。分成两个页签而不是一屏到底：
        后者的行数随变更规模增长，混在一起会把前半段的判定字段挤出视野。
      -->
      <a-tabs v-model:activeKey="modal.tab" class="chg-modal-tabs">
        <a-tab-pane key="basic" tab="基本属性" force-render>
          <!-- 选了来源请求后，标题 / 说明等已自动带入：不说明一句，用户会以为要重打一遍 -->
          <a-alert
            v-if="!modal.isEdit && modal.prefilledFrom"
            type="info"
            show-icon
            class="chg-flow-hint"
            :message="prefillMessage"
          />
          <!-- 编辑派生单时也要说清"为什么这几项是灰的"，否则看起来像权限问题 -->
          <a-alert
            v-else-if="lockedFields.length"
            type="info"
            show-icon
            class="chg-flow-hint"
            message="本通告派生自变更请求：变更类型 / 分级 / 优先级 / 影响 是来源请求判定结果的快照，不能在此修改（变更原因可改写）"
          />
          <DynamicForm
            ref="formRef"
            v-model="modal.form"
            entity-code="CHANGE_NOTICE"
            :operation-code="modal.isEdit ? 'update' : 'create'"
            :widget-options="{ issueOptions: modal.seedIssues }"
            :readonly-fields="lockedFields"
            skip-resource-loading
          />
          <div v-if="!modal.isEdit" class="chg-hint">
            已关闭 / 已取消的问题无法关联。未选来源请求时，变更类型 / 分级 / 优先级 / 变更原因 / 执行方案 必须填全；
            选了来源请求则由 ECR 带入（判定类字段只读，变更原因与执行方案可改写）。
          </div>
        </a-tab-pane>

        <a-tab-pane key="impl" tab="更改事项" force-render>
          <a-alert
            type="info"
            show-icon
            class="chg-flow-hint"
            :message="modal.isEdit
              ? '更改事项 = 本通告下的变更任务清单：新增 / 删除随本弹窗的「保存」一起生效；已开始处理的任务不能在这里删'
              : '更改事项 = 变更任务清单：先在这里排好工，保存通告时一并创建；通告审批通过后自动生效，无需再点派发'"
          />
          <div class="chg-modal-task-bar">
            <a-button type="dashed" size="small" :disabled="!modalTaskEditable" @click="openModalTaskForm">
              + 新增变更任务
            </a-button>
            <span class="chg-muted">
              任务的负责对象从「基本属性」里已选的变更对象中挑；一个对象只归一张任务
            </span>
          </div>
          <a-empty v-if="!modal.tasks.length" description="尚未规划变更任务" :image-style="{ height: '40px' }" />
          <a-table
            v-else
            :columns="modalTaskColumns"
            :data-source="modal.tasks"
            :pagination="false"
            :row-key="(record) => record.oid || record.key"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <span v-if="record._local" class="chg-muted">待保存</span>
                <span v-else>{{ record.code }}</span>
              </template>
              <template v-else-if="column.key === 'taskType'">
                <span>{{ TASK_TYPE_LABEL[record.taskType] || '—' }}</span>
              </template>
              <template v-else-if="column.key === 'planEnd'">
                <span>{{ formatDay(record.planEnd) }}</span>
              </template>
              <template v-else-if="column.key === 'status'">
                <a-tag v-if="record._local" color="default">草稿（保存后生效）</a-tag>
                <a-tag v-else :color="CHANGE_TASK_STATUS_COLOR[record.status] || 'default'">
                  {{ CHANGE_TASK_STATUS_LABEL[record.status] || record.status }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'op'">
                <a-button
                  type="link"
                  size="small"
                  danger
                  :disabled="!canRemoveModalTask(record)"
                  @click="removeModalTask(record)"
                >
                  删除
                </a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-modal>

    <!-- 流转 -->
    <a-modal
      v-model:open="flow.visible"
      :title="flow.action ? noticeActionLabel(flow.action, approvalRequired) : '流转'"
      :confirm-loading="flow.saving"
      :ok-button-props="{ disabled: submitFlowBlocked }"
      width="680px"
      centered
      ok-text="确定"
      cancel-text="取消"
      @ok="handleFlow"
    >
      <!--
        提交流程：先把"这张单会走哪条流程、那条流程部署没部署"摆出来 —— 与「发起流程」弹框
        同一套层次（结论卡片 + 流程信息网格）。没部署就不给提交，而不是让人点完吃一次报错。
      -->
      <template v-if="isApprovalSubmit">
        <div class="ck-summary">
          <div class="ck-summary__object">
            <span class="ck-summary__name">{{ flow.record?.code || '' }}</span>
            <span class="ck-summary__code">{{ flow.record?.name || '' }}</span>
            <a-tag
              v-if="flow.record?.status"
              size="small"
              :color="CHANGE_NOTICE_STATUS_COLOR[flow.record.status] || 'default'"
            >
              {{ CHANGE_NOTICE_STATUS_LABEL[flow.record.status] || flow.record.status }}
            </a-tag>
          </div>
          <div class="ecn-flow-state" v-if="!approvalFlow.loading && approvalFlow.found">
            <a-tag v-if="approvalFlow.deployedVersion" color="green" size="small">
              已部署 v{{ approvalFlow.deployedVersion }}
            </a-tag>
            <a-tag v-else color="orange" size="small">尚未部署</a-tag>
            <a-tag color="blue" size="small">
              v{{ approvalFlow.latestVersion ?? '-' }}
            </a-tag>
          </div>
        </div>

        <a-alert
          v-if="submitFlowBlocked"
          type="warning"
          show-icon
          class="chg-flow-hint"
          :message="submitFlowBlockReason"
          :description="submitFlowBlockHint"
        />
        <a-alert
          v-else
          type="info"
          show-icon
          class="chg-flow-hint"
          message="提交后立即发起审批流程：单据进入「审批中」，审批环节在「任务中心」办理，结论会自动回写到本单据"
        />

        <div class="ck-section" v-if="approvalFlow.loaded && !approvalFlow.loading && approvalFlow.key">
          <div class="ck-section__title">流程信息</div>
          <div class="ecn-info">
            <div class="ecn-info__item">
              <span class="ecn-info__label">流程名称</span>
              <span class="ecn-info__value">{{ approvalFlow.name || '（未在流程清单里找到）' }}</span>
            </div>
            <div class="ecn-info__item">
              <span class="ecn-info__label">流程 key</span>
              <code class="ecn-info__value ecn-info__code">{{ approvalFlow.key }}</code>
            </div>
            <div class="ecn-info__item">
              <span class="ecn-info__label">最新版本</span>
              <span class="ecn-info__value ecn-info__value--tags">
                <a-tag color="blue" size="small">v{{ approvalFlow.latestVersion ?? '-' }}</a-tag>
                <span v-if="approvalFlow.latestCreatedAt" class="ecn-info__muted">
                  {{ approvalFlow.latestCreatedAt }}
                </span>
              </span>
            </div>
            <div class="ecn-info__item">
              <span class="ecn-info__label">部署状态</span>
              <span class="ecn-info__value ecn-info__value--tags">
                <a-tag v-if="approvalFlow.deployedVersion" color="green" size="small">
                  已部署 v{{ approvalFlow.deployedVersion }}
                </a-tag>
                <a-tag v-else color="orange" size="small">尚未部署</a-tag>
              </span>
            </div>
            <div v-if="approvalFlow.changeNote" class="ecn-info__item ecn-info__item--full">
              <span class="ecn-info__label">变更说明</span>
              <div class="ecn-info__value ecn-info__value--text">{{ approvalFlow.changeNote }}</div>
            </div>
            <div v-if="approvalFlow.description" class="ecn-info__item ecn-info__item--full">
              <span class="ecn-info__label">流程说明</span>
              <div class="ecn-info__value ecn-info__value--text">{{ approvalFlow.description }}</div>
            </div>
          </div>
        </div>
      </template>

      <a-alert
        v-else-if="flow.action === 'SUBMIT' && !approvalRequired"
        type="warning"
        show-icon
        class="chg-flow-hint"
        message="本租户已开启免审：提交即通过，原因会记入审计"
      />
      <a-alert
        v-else-if="flow.action === 'CLOSE'"
        type="info"
        show-icon
        class="chg-flow-hint"
        message="关闭前需确保本通告下的变更任务（子流程）全部验收完成，否则会被退回"
      />
      <a-form layout="vertical">
        <a-form-item
          :label="flow.action === 'SUBMIT' && !approvalRequired ? '免审原因' : '说明'"
          :required="noticeRequiresComment(flow.action) || (flow.action === 'SUBMIT' && !approvalRequired)"
        >
          <a-textarea v-model:value="flow.comment" :rows="3" :maxlength="2000" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 管理员设置状态（运维纠错）：跳过流转规则与完成门槛，因此必须填原因 -->
    <a-modal
      v-model:open="statusForm.visible"
      :title="`设置状态 · ${statusForm.code || ''}`"
      :confirm-loading="statusForm.saving"
      width="520px"
      centered
      ok-text="设置"
      cancel-text="取消"
      @ok="handleSetStatus"
    >
      <a-alert
        type="warning"
        show-icon
        class="chg-flow-hint"
        message="管理员操作：跳过正常流转规则与完成门槛（受影响对象是否执行完、子任务是否关闭），本次操作会记入处理记录"
      />
      <a-form layout="vertical">
        <a-form-item label="目标状态" required>
          <a-select
            v-model:value="statusForm.status"
            :options="statusOptions"
            placeholder="选择目标状态"
          />
        </a-form-item>
        <a-form-item label="原因" required>
          <a-textarea
            v-model:value="statusForm.reason"
            :rows="3"
            :maxlength="500"
            placeholder="如：审批已在外部系统线下完成，结论回写失败，手工置为已批准"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情 -->
    <a-drawer v-model:open="drawer.visible" title="变更通告" width="860" :body-style="{ padding: '16px 20px' }">
      <a-spin :spinning="drawer.loading">
        <template v-if="drawer.notice">
          <div class="chg-drawer-head">
            <div>
              <a-tag color="geekblue">{{ drawer.notice.code }}</a-tag>
              <b class="chg-drawer-title">{{ drawer.notice.name }}</b>
              <a-tag v-if="drawer.notice.approvalBypassed" color="orange">免审通过</a-tag>
            </div>
          </div>

          <a-alert
            v-if="drawer.notice.approvalBypassed && drawer.notice.bypassReason"
            type="warning"
            show-icon
            class="chg-flow-hint"
            :message="'免审原因：' + drawer.notice.bypassReason"
          />

          <a-descriptions :column="3" size="small" bordered>
            <a-descriptions-item label="状态">
              <a-tag :color="CHANGE_NOTICE_STATUS_COLOR[drawer.notice.status] || 'default'">
                {{ CHANGE_NOTICE_STATUS_LABEL[drawer.notice.status] || drawer.notice.status }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="责任人">{{ drawer.notice.owner || '—' }}</a-descriptions-item>
            <!-- 来源请求可点：执行人常要回看 ECR 的原因 / 方案，只给一个编号等于让他自己去找 -->
            <a-descriptions-item label="来源请求">
              <template v-if="drawer.request">
                <a class="chg-link" @click="openSourceRequest(drawer.request.oid)">{{ drawer.request.code }}</a>
                <div v-if="drawer.request.name" class="chg-muted">{{ drawer.request.name }}</div>
              </template>
              <span v-else class="chg-muted">—</span>
            </a-descriptions-item>
            <!-- 变更信息：独立 ECN 是判定依据；派生单里是 ECR 判定结果的快照（只读） -->
            <a-descriptions-item label="变更类型">
              {{ CHANGE_TYPE_LABEL[drawer.notice.changeType] || '—' }}
            </a-descriptions-item>
            <a-descriptions-item label="变更分级">
              <a-tag v-if="drawer.notice.changeLevel" :color="CHANGE_LEVEL_COLOR[drawer.notice.changeLevel] || 'default'">
                {{ CHANGE_LEVEL_LABEL[drawer.notice.changeLevel] || drawer.notice.changeLevel }}
              </a-tag>
              <span v-else class="chg-muted">—</span>
            </a-descriptions-item>
            <a-descriptions-item label="优先级">
              {{ PRIORITY_LABEL[drawer.notice.priority] || '—' }}
            </a-descriptions-item>
            <a-descriptions-item label="认证/合规影响">
              {{ CERT_IMPACT_LABEL[drawer.notice.certImpact] || '未评估' }}
            </a-descriptions-item>
            <a-descriptions-item label="客户/合同影响">
              {{ CUSTOMER_IMPACT_LABEL[drawer.notice.customerImpact] || '未评估' }}
            </a-descriptions-item>
            <a-descriptions-item label="在制/库存影响">
              {{ INVENTORY_IMPACT_LABEL[drawer.notice.inventoryImpact] || '未评估' }}
            </a-descriptions-item>
            <a-descriptions-item label="计划开始">{{ drawer.notice.planStart || '—' }}</a-descriptions-item>
            <a-descriptions-item label="计划完成">{{ drawer.notice.planEnd || '—' }}</a-descriptions-item>
            <a-descriptions-item label="批准时间">{{ formatDateTime(drawer.notice.approvedAt) }}</a-descriptions-item>
            <!-- 审批流程：只显示"审批中"等于把进度藏起来，给个入口直达实例详情（节点经路 / 办理人） -->
            <a-descriptions-item label="评审流程">
              <a
                v-if="drawer.notice.processInstanceId"
                class="chg-link"
                @click="openProcessInstance(drawer.notice.processInstanceId)"
              >查看流程</a>
              <span v-else class="chg-muted">未发起</span>
            </a-descriptions-item>
            <a-descriptions-item label="关联问题" :span="2">
              <a-space v-if="drawer.issues.length" wrap size="small">
                <a-tag v-for="it in drawer.issues" :key="it.oid" color="orange">
                  {{ it.code }} {{ it.name }}
                </a-tag>
              </a-space>
              <span v-else class="chg-muted">未关联问题</span>
            </a-descriptions-item>
            <a-descriptions-item label="变更原因" :span="3">
              <div class="chg-pre">{{ drawer.notice.reason || '—' }}</div>
            </a-descriptions-item>
            <a-descriptions-item label="执行方案" :span="3">
              <div class="chg-pre">{{ drawer.notice.proposal || '—' }}</div>
            </a-descriptions-item>
            <a-descriptions-item label="补充说明" :span="3">
              <div class="chg-pre">{{ drawer.notice.description || '—' }}</div>
            </a-descriptions-item>
            <a-descriptions-item label="补充材料" :span="3">
              <a
                v-if="drawer.attachment"
                :href="getAttachmentDownloadUrl(drawer.attachment.oid)"
                target="_blank"
                rel="noopener"
              >
                {{ drawer.attachment.fileName }}
              </a>
              <span v-else class="chg-muted">—</span>
            </a-descriptions-item>
          </a-descriptions>

          <a-space v-if="availableNoticeActions(drawer.notice.status).length" class="chg-drawer-actions">
            <a-tooltip
              v-for="action in availableNoticeActions(drawer.notice.status)"
              :key="action"
              :title="isOwner(drawer.notice) ? undefined : ownerHint(drawer.notice)"
            >
              <span>
                <a-button
                  :type="action === 'CANCEL' || action === 'REJECT' ? 'default' : 'primary'"
                  :danger="action === 'CANCEL' || action === 'REJECT'"
                  size="small"
                  :disabled="!isOwner(drawer.notice)"
                  @click="openTransition(drawer.notice, action)"
                >
                  {{ noticeActionLabel(action, approvalRequired) }}
                </a-button>
              </span>
            </a-tooltip>
          </a-space>

          <!-- 受影响对象 -->
          <a-divider orientation="left" class="chg-divider">
            受影响对象（{{ drawer.affectedObjects.length }}）
            <a-button
              v-if="noticeAffectedEditable(drawer.notice.status)"
              type="link"
              size="small"
              @click="openAffectedForm"
            >
              + 添加
            </a-button>
          </a-divider>
          <a-empty v-if="!drawer.affectedObjects.length" description="尚未添加受影响对象" :image-style="{ height: '40px' }" />
          <a-table
            v-else
            :columns="affectedColumns"
            :data-source="drawer.affectedObjects"
            :pagination="false"
            row-key="oid"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'entityType'">
                <a-tag>{{ ENTITY_TYPE_LABEL[record.entityType] || record.entityType }}</a-tag>
              </template>
              <template v-else-if="column.key === 'entity'">
                <span>{{ record.entityCode || '—' }}</span>
                <span v-if="record.entityName" class="chg-muted"> · {{ record.entityName }}</span>
              </template>
              <template v-else-if="column.key === 'container'">
                <span>{{ record.containerName || '—' }}</span>
              </template>
              <template v-else-if="column.key === 'baseVersion'">
                <span>{{ record.baseVersion }}</span>
                <span v-if="record.resultVersion" class="chg-muted"> → {{ record.resultVersion }}</span>
              </template>
              <template v-else-if="column.key === 'task'">
                <a-select
                  v-if="taskAssignable(record)"
                  :value="record.taskOid"
                  size="small"
                  allow-clear
                  placeholder="待分配"
                  :options="taskOptions"
                  style="width: 100%"
                  @change="(value) => handleAssignTask(record, value)"
                />
                <span v-else>{{ taskName(record.taskOid) }}</span>
              </template>
              <template v-else-if="column.key === 'execute'">
                <a-tag v-if="record.executedAt" color="green">已执行</a-tag>
                <a-tag v-else color="default">待执行</a-tag>
              </template>
              <template v-else-if="column.key === 'op'">
                <a-popconfirm
                  v-if="noticeAffectedEditable(drawer.notice.status)"
                  title="确定移除该受影响对象？"
                  @confirm="handleRemoveAffected(record)"
                >
                  <a-button type="link" size="small" danger>移除</a-button>
                </a-popconfirm>
              </template>
            </template>
          </a-table>

          <!-- 更改事项（= 变更任务）：审批前是清单，审批通过时后端自动置为可执行，无需人手派发 -->
          <a-divider orientation="left" class="chg-divider">
            更改事项（变更任务，{{ drawer.tasks.length }}）
            <a-button v-if="taskPlannable" type="link" size="small" @click="openTaskForm">+ 新建</a-button>
          </a-divider>
          <a-empty v-if="!drawer.tasks.length" description="尚未规划任务" :image-style="{ height: '40px' }" />
          <a-table
            v-else
            :columns="taskColumns"
            :data-source="drawer.tasks"
            :pagination="false"
            row-key="oid"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'taskType'">
                <span>{{ TASK_TYPE_LABEL[record.taskType] || '—' }}</span>
              </template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="CHANGE_TASK_STATUS_COLOR[record.status] || 'default'">
                  {{ CHANGE_TASK_STATUS_LABEL[record.status] || record.status }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'op'">
                <a-space size="small">
                  <a-button
                    v-for="action in availableTaskActions(record.status)"
                    :key="action"
                    type="link"
                    size="small"
                    :disabled="taskRequiresOwner(action) && record.assignee !== currentUsername"
                    @click="openTaskTransition(record, action)"
                  >
                    {{ CHANGE_TASK_ACTION_LABEL[action] }}
                  </a-button>
                </a-space>
              </template>
            </template>
          </a-table>
          <div v-if="drawer.tasks.length" class="chg-hint">
            关闭变更通告前，以上任务必须全部验收完成（子流程先关）。
          </div>

          <a-divider orientation="left" class="chg-divider">处理记录</a-divider>
          <a-empty v-if="!drawer.activities.length" description="暂无记录" :image-style="{ height: '40px' }" />
          <a-timeline v-else>
            <a-timeline-item v-for="act in drawer.activities" :key="act.oid">
              <div class="chg-act-head">
                <b>{{ CHANGE_NOTICE_ACTION_LABEL[act.action] || act.action }}</b>
                <span v-if="act.fromStatus && act.toStatus" class="chg-act-flow">
                  {{ CHANGE_NOTICE_STATUS_LABEL[act.fromStatus] || act.fromStatus }}
                  →
                  {{ CHANGE_NOTICE_STATUS_LABEL[act.toStatus] || act.toStatus }}
                </span>
                <span class="chg-act-time">{{ formatDateTime(act.createdAt) }}</span>
              </div>
              <div class="chg-act-op">{{ act.operator || act.creator || '—' }}</div>
              <div v-if="act.comment" class="chg-act-comment">{{ act.comment }}</div>
            </a-timeline-item>
          </a-timeline>
        </template>
      </a-spin>
    </a-drawer>

    <!-- 添加受影响对象 -->
    <a-modal
      v-model:open="affectedForm.visible"
      title="添加受影响对象"
      width="600px"
      centered
      ok-text="添加"
      cancel-text="取消"
      :confirm-loading="affectedForm.saving"
      @ok="handleAddAffected"
    >
      <a-alert
        type="info"
        show-icon
        class="chg-flow-hint"
        message="变更作用在具体版本上：必须指定被修订的那一版（如 A.2）。对象选择器在 M2 接入，当前手工填写。"
      />
      <a-form :model="affectedForm.form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="对象类型" required>
              <a-select v-model:value="affectedForm.form.entityType" :options="ENTITY_TYPE_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="对象编码">
              <a-input v-model:value="affectedForm.form.entityCode" placeholder="如 P-1001" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="对象名称">
              <a-input v-model:value="affectedForm.form.entityName" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="主对象 oid" required>
          <a-input v-model:value="affectedForm.form.entityOid" placeholder="ck_part / ck_document / ck_eng_document 的 oid" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="受影响迭代 oid" required>
              <a-input v-model:value="affectedForm.form.baseIterationOid" placeholder="具体版本的 oid" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="受影响版本" required>
              <a-input v-model:value="affectedForm.form.baseVersion" placeholder="如 A.2" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注">
          <a-input v-model:value="affectedForm.form.note" :maxlength="500" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新建任务 -->
    <a-modal
      v-model:open="taskForm.visible"
      title="新建变更任务"
      :width="taskFormWidth"
      centered
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="taskForm.saving"
      @ok="handleCreateTask"
    >
      <DynamicForm
        ref="taskFormRef"
        v-model="taskForm.form"
        entity-code="CHANGE_TASK"
        operation-code="create"
        skip-resource-loading
      />
      <!-- 更改事项 = 变更任务：这张任务负责改哪些对象（对象 → 事项的归属）。
           与通告「基本属性」无关 —— 这里挑的是任务自己的执行范围，服务端搜索、可多选 -->
      <a-form layout="vertical" class="chg-task-scope">
        <a-form-item label="包含的变更对象">
          <ChangeAffectedObjectSelect
            v-model:value="taskForm.form.affectedOids"
            :seed-options="taskForm.seedScope"
            :container-oid="taskScopeContainerOid"
            :exclude-oids="noticeChosenOids"
            placeholder="可选：输入编号或名称搜索零件 / 文档"
          />
        </a-form-item>
        <!-- 收集：选一个零件，把它关联的文档 / 替代件一次带进来（BOM 结构不在范围内） -->
        <div class="chg-collect-bar">
          <a-button size="small" :loading="taskForm.collecting" @click="collectRelatedObjects">
            收集关联对象
          </a-button>
          <span class="chg-muted">把所选零件关联的文档、替代件一并加入本任务（可多次收集）</span>
        </div>
        <div class="chg-hint">
          这里选的是这张任务要改的对象，不需要先在「基本属性」里选过；
          保存时会按对象当前版本加入本通告的受影响对象，并归到这张任务下。
        </div>
      </a-form>
    </a-modal>

    <!-- 任务流转 -->
    <a-modal
      v-model:open="taskFlow.visible"
      :title="taskFlow.action ? CHANGE_TASK_ACTION_LABEL[taskFlow.action] : '任务流转'"
      width="480px"
      centered
      ok-text="确定"
      cancel-text="取消"
      :confirm-loading="taskFlow.saving"
      @ok="handleTaskFlow"
    >
      <a-form layout="vertical">
        <a-form-item label="说明" :required="taskRequiresComment(taskFlow.action)">
          <a-textarea v-model:value="taskFlow.comment" :rows="3" :maxlength="2000" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import { ChangeAffectedObjectSelect } from '@/components/change/changeFieldWidgets'
import {
  addChangeAffectedByEntity,
  addChangeAffectedObject,
  assignChangeAffectedTask,
  createChangeNotice,
  createChangeTask,
  deleteChangeNotice,
  deleteChangeTask,
  getAttachment,
  getAttachmentDownloadUrl,
  getChangeConfig,
  getChangeNotice,
  getChangeRequest,
  getProcessTemplateVersion,
  listChangeNotices,
  listProcessTemplates,
  listRelatedChangeObjects,
  removeChangeAffectedObject,
  setChangeNoticeStatus,
  transitionChangeNotice,
  transitionChangeTask,
  updateChangeNotice
} from '@/api'
import {
  CERT_IMPACT_LABEL,
  CHANGE_LEVEL_COLOR,
  CHANGE_LEVEL_LABEL,
  CHANGE_NOTICE_ACTION_LABEL,
  CHANGE_NOTICE_STATUS_COLOR,
  CHANGE_NOTICE_STATUS_LABEL,
  CHANGE_TASK_ACTION_LABEL,
  CHANGE_TASK_STATUS_COLOR,
  CHANGE_TASK_STATUS_LABEL,
  CHANGE_TYPE_LABEL,
  CUSTOMER_IMPACT_LABEL,
  ENTITY_TYPE_LABEL,
  ENTITY_TYPE_OPTIONS,
  INVENTORY_IMPACT_LABEL,
  PRIORITY_LABEL,
  TASK_TYPE_LABEL,
  availableNoticeActions,
  availableTaskActions,
  noticeActionLabel,
  noticeAffectedEditable,
  noticeEditable,
  noticeRequiresComment,
  primaryNoticeAction,
  taskRequiresComment,
  taskRequiresOwner
} from '@/utils/changeFlow'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const currentUsername = computed(() => userStore.username || '')

/**
 * 是否租户管理员 —— 「设置状态」入口只对管理员露出。
 *
 * <p>口径与主布局的「变更设置」入口一致（TENANT_ADMIN / ADMIN / PLATFORM_ADMIN）；
 * 服务端 {@code POST /change-notices/{oid}/status} 有同样的校验，这里只管别把
 * 一个点了就 403 的按钮摆给普通成员。
 */
const isTenantAdmin = computed(() => (userStore.roles || [])
  .some((role) => ['TENANT_ADMIN', 'ADMIN', 'PLATFORM_ADMIN'].includes(role)))

/**
 * 除「详情」外，变更通告的所有动作都只归责任人。
 *
 * <p>与后端 `ChangeNoticeFlow.requiresOwner` + `ChangeNoticeServiceImpl#assertOwner` 同口径：
 * owner 为空时退回创建人（creator）—— 通告的责任人可从来源请求继承，也可能留空，
 * 只认 owner 会让这类单据永远推不动。
 *
 * <p>审批动作 APPROVE / REJECT 不在此列：那是审批人的结论（且由流程回调写库）。
 */
function isOwner(record) {
  const owner = record?.owner || record?.creator || ''
  return !!owner && owner === currentUsername.value
}

function ownerHint(record) {
  const owner = record?.owner || record?.creator
  return owner ? `只有责任人「${owner}」可以执行` : '未指派责任人，无法执行'
}

const columns = [
  { title: '编号', dataIndex: 'code', key: 'code', width: 120 },
  { title: '标题', dataIndex: 'name', key: 'name', width: 280 },
  { title: '类型', key: 'changeType', width: 100 },
  { title: '分级', key: 'changeLevel', width: 90 },
  { title: '状态', key: 'status', width: 100 },
  { title: '责任人', dataIndex: 'owner', key: 'owner', width: 110 },
  { title: '计划完成', key: 'planEnd', width: 110 },
  { title: '操作', key: 'action', width: 220, fixed: 'right' }
]

const affectedColumns = [
  { title: '类型', key: 'entityType', width: 100 },
  { title: '对象', key: 'entity', width: 180 },
  // 所属容器：录入时快照，只用于展示与筛选（不跟随对象迁移更新）
  { title: '所属容器', key: 'container', width: 140 },
  { title: '受影响版本', key: 'baseVersion', width: 130 },
  { title: '任务', key: 'task', width: 150 },
  { title: '状态', key: 'execute', width: 90 },
  { title: '操作', key: 'op', width: 80 }
]

const taskColumns = [
  { title: '编号', dataIndex: 'code', key: 'code', width: 100 },
  { title: '标题', dataIndex: 'name', key: 'name', width: 200 },
  { title: '类型', key: 'taskType', width: 80 },
  { title: '责任人', dataIndex: 'assignee', key: 'assignee', width: 100 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'op', width: 160 }
]

/** 弹窗「更改事项」页签的列：本地行没有 code，编号列显示"待保存" */
const modalTaskColumns = [
  { title: '编号', key: 'code', width: 100 },
  { title: '标题', dataIndex: 'name', key: 'name', width: 220 },
  { title: '类型', key: 'taskType', width: 90 },
  { title: '责任人', dataIndex: 'assignee', key: 'assignee', width: 110 },
  { title: '计划完成', key: 'planEnd', width: 110 },
  { title: '状态', key: 'status', width: 120 },
  { title: '操作', key: 'op', width: 80 }
]

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

/**
 * 状态过滤由**页面头部**控制（见 ChangeControl 的状态概览标签）。
 *
 * <p>为什么不在本面板里做、再把标签 Teleport 到页头：Teleport 在首屏挂载时目标节点还没上文档
 * （整棵树是一次性挂上去的），会直接报 "Failed to locate Teleport target"。统计与筛选因此都归父级，
 * 本面板只按 `props.status` 取数，列表刷新后回 `changed` 让父级重取计数。
 */
const props = defineProps({ status: { type: String, default: undefined } })
const emit = defineEmits(['update:status', 'changed'])

const filters = reactive({ status: props.status || undefined, keyword: '' })
const approvalRequired = ref(true)
/** 审批流程 key（「变更控制 → 设置」；空 = 没配，提交时后端会拦） */
const approvalProcessKey = ref('')

/** 页头换了筛选（或清空）：同步内部状态并回到第一页重取 */
watch(() => props.status, (value) => {
  const next = value || undefined
  if (next === (filters.status || undefined)) return
  filters.status = next
  page.value = 1
  loadList()
})

/** 布局驱动表单（字段定义在 ck_type_page_layout 的 CHANGE_NOTICE/create|update） */
const formRef = ref(null)
/** 新建变更任务的布局表单（CHANGE_TASK/create） */
const taskFormRef = ref(null)
/** 弹窗宽度跟随布局列数（由 DynamicForm 计算；两列布局用 560/680px 会把下拉挤成一条） */
const formWidth = computed(() => formRef.value?.recommendedWidth || '680px')
const taskFormWidth = computed(() => taskFormRef.value?.recommendedWidth || '560px')

/**
 * issueLoaded：当前 form.issueOids 是否是完整可信的关联列表。
 *
 * <p>false 时保存不会发送该字段（后端 patch 语义 = 这次不改关联）——
 * 编辑时详情没拉到，不能拿空数组把已有的关联冲掉。
 */
const modal = reactive({
  visible: false,
  saving: false,
  isEdit: false,
  oid: '',
  /** 页签：basic = 基本属性；impl = 更改事项（草稿变更任务清单） */
  tab: 'basic',
  issueLoaded: false,
  form: emptyForm(),
  /** 编辑 / 选来源请求时回填的关联问题（{oid, code, name}），交给 change-issue-selector 作 seed */
  seedIssues: [],
  /** 已带入的来源请求（{code, name}）：弹窗里给一句说明，免得用户以为要重打 */
  prefilledFrom: null,
  /**
   * 从来源请求实际写进表单的值（字段名 → 当时的值）。
   *
   * <p>清除来源请求时按它回滚：值还等于"带进来的那份"就清掉（用户没动过），
   * 已经改过的保留 —— 清来源不该顺手删掉人手写的内容，也不该留下一串来源不明
   * 的判定信息（用户 2026-11-03：*"清除来源变更请求后，对应带出的值没有清除"*）。
   */
  applied: null,
  /**
   * 更改事项（第二部分）：任务清单。
   *
   * <p>既有任务是服务端行（带 oid / code / status），新增的是 {_local: true} 的本地行 ——
   * 两者混在一个列表里，点「保存」才真正落库（见 persistModalTasks）。
   * 为什么不边加边存：本弹窗是"新建 + 编辑"共用的，用户在新建时排的工属于这张单的一部分，
   * 取消建单不该在服务端留下几张孤儿任务。
   */
  tasks: [],
  /**
   * 编辑时详情带回的「本通告已收录对象」oid（affectedObjects 的 entityOid）。
   *
   * <p>新建任务是"往本通告里加对象"，已经在单上的对象不该再被挑一次 ——
   * 这份与本地草稿任务的 affectedEntityOids 合起来，就是「本通告已经选择过的对象」全集。
   */
  chosenAffectedOids: [],
  /** 编辑时在弹窗里删掉的既有任务 oid：点保存才真删（取消不动线上数据） */
  removedTaskOids: [],
  /** 当前通告状态（编辑时由详情带回）：决定任务还能不能增删，与后端 isTaskPlannable 同口径 */
  noticeStatus: ''
})
const flow = reactive({
  visible: false,
  saving: false,
  oid: '',
  action: '',
  comment: '',
  /** 本次流转的单据（弹窗里要显示"提交哪一张"：编号 / 标题 / 当前状态） */
  record: null
})

/**
 * 审批流程（ECN 提交审批时真正被拉起的那条）。
 *
 * <p>以前弹窗只有一句"提交后发起审批流程"，用户看不到走的是哪条流程、部署没部署 ——
 * 流程 key 配错或模板没部署，要等点了确定才被后端退回。这里按配置里的
 * {@code approvalProcessKey} 去流程清单里查一次，把名称 / 版本 / 部署状态 / 说明摆出来
 * （与「发起流程」弹框同一口径），没部署就直接不给提交。
 */
const approvalFlow = reactive({
  loading: false,
  /** 查过一次（无论是否命中）：查不到时说"未在流程清单里找到"，不再重复请求 */
  loaded: false,
  /** 流程清单里有这一条 */
  found: false,
  key: '',
  name: '',
  description: '',
  latestVersion: null,
  latestCreatedAt: '',
  deployedVersion: null,
  /** 最新版本的变更说明（要按版本号再查一次，查不到就空着） */
  changeNote: ''
})
/**
 * 管理员「设置状态」弹窗。
 *
 * <p>{@code current} 记的是打开时的状态：同一状态不出现在下拉里（设了等于没设），
 * 服务端也会拒绝；{@code APPROVING} 一律不给选 —— 那需要真实流程实例，
 * 硬设会让单据等一个永远不会来的审批回调。
 */
const statusForm = reactive({
  visible: false,
  saving: false,
  oid: '',
  code: '',
  current: '',
  status: undefined,
  reason: ''
})

const statusOptions = computed(() => Object.entries(CHANGE_NOTICE_STATUS_LABEL)
  .filter(([key]) => key !== 'APPROVING' && key !== statusForm.current)
  .map(([value, label]) => ({ value, label })))
const drawer = reactive({
  visible: false,
  loading: false,
  notice: null,
  request: null,
  affectedObjects: [],
  tasks: [],
  activities: [],
  issues: [],
  /** 补充材料的元信息（单据上只存 attachmentOid） */
  attachment: null
})
const affectedForm = reactive({ visible: false, saving: false, form: emptyAffected() })
/**
 * source：'drawer' = 详情抽屉里直接建单（立即落库）；'modal' = 通告弹窗内规划（随保存一起落库）。
 *
 * <p>{@code seedScope} 是「收集关联对象」带进来的候选（oid → 文案）：控件只认自己搜过的候选，
 * 不把收集结果喂回去，新加的对象就显示成裸 uuid。{@code collecting} 只用于按钮 loading。
 */
const taskForm = reactive({
  visible: false,
  saving: false,
  collecting: false,
  source: 'drawer',
  seedScope: [],
  form: emptyTask()
})
const taskFlow = reactive({ visible: false, saving: false, oid: '', action: '', comment: '' })

const taskPlannable = computed(() => {
  const status = drawer.notice?.status
  return ['DRAFT', 'REJECTED', 'APPROVED', 'IN_WORK'].includes(status)
})

/** 更改事项下拉：本通告下的变更任务 */
const taskOptions = computed(() => (drawer.tasks || []).map((t) => ({
  value: t.oid,
  label: `${t.code} ${t.name}${t.status === 'DRAFT' ? '（待生效）' : ''}`
})))

/**
 * 弹窗「更改事项」还能不能增删任务。
 *
 * <p>新建随意；编辑跟随通告状态 —— 与后端 {@code ChangeNoticeFlow.isTaskPlannable} 同口径
 * （审批中不允许改分工），否则按钮点得动、保存时才被后端打回。
 */
const modalTaskEditable = computed(() =>
  !modal.isEdit || ['DRAFT', 'REJECTED', 'APPROVED', 'IN_WORK'].includes(modal.noticeStatus))

/**
 * 变更任务挑对象时的检索范围：跟随本单的「变更系列/型号」，没选就不限范围。
 *
 * <p>任务上的对象是它自己的执行范围，与「基本属性」无关 —— 这里只借用产品范围做过滤，
 * 避免把别的产品下的零件挂进来。
 */
const taskScopeContainerOid = computed(() => (
  taskForm.source === 'drawer' ? (drawer.notice?.productOid || '') : (modal.form.productOid || '')
))

/**
 * 本通告已经选择过的对象（新建任务的「包含的变更对象」里要置灰）。
 *
 * <p>两个来源缺一不可：抽屉里是服务端已落库的受影响对象；弹窗里通告可能还没创建
 * （新建）或对象还攒在其它草稿任务里，只能从本地清单拼。漏掉任何一边，
 * 同一对象就能在同一张单里被两张任务各挑一次。
 *
 * <p>「处在变更中的对象」不在这里判 —— 候选接口按对象带 {@code changeCode}，
 * 由控件自己置灰（那张单可能根本不是本单，前端没有它的对象清单）。
 */
const noticeChosenOids = computed(() => {
  const chosen = new Set()
  if (taskForm.source === 'drawer') {
    for (const obj of drawer.affectedObjects || []) {
      if (obj.entityOid) chosen.add(obj.entityOid)
    }
  } else {
    for (const oid of modal.chosenAffectedOids || []) chosen.add(oid)
    for (const task of modal.tasks || []) {
      for (const oid of task.affectedEntityOids || []) chosen.add(oid)
    }
  }
  return [...chosen]
})

function taskAssignable(record) {
  if (!record || record.executedAt) return false
  return !['COMPLETED', 'CLOSED', 'CANCELLED'].includes(drawer.notice?.status)
}

function emptyForm() {
  return {
    name: '',
    requestOid: undefined,
    // 变更本身的定性信息：独立 ECN 自己填；派生单从来源请求带入，
    // 判定类字段锁定（见 lockedFields），变更原因带入但可改写
    changeType: undefined,
    changeLevel: undefined,
    priority: undefined,
    certImpact: undefined,
    customerImpact: undefined,
    inventoryImpact: undefined,
    reason: '',
    proposal: '',
    planStart: null,
    planEnd: null,
    owner: undefined,
    description: '',
    issueOids: []
  }
}

function emptyAffected() {
  return {
    entityType: 'PART',
    entityCode: '',
    entityName: '',
    entityOid: '',
    baseIterationOid: '',
    baseVersion: '',
    note: ''
  }
}

/** affectedOids：这张任务负责改哪些对象（不落库到任务，创建后逐条写回对象的 taskOid） */
function emptyTask() {
  return {
    name: '',
    taskType: undefined,
    assignee: undefined,
    planEnd: null,
    description: '',
    affectedOids: []
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listChangeNotices({
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    if (res.code === 200) {
      rows.value = res.data.rows || []
      total.value = res.data.total || 0
      // 列表变了（增删改 / 换页 / 换筛选）→ 让父级重取页头的状态计数
      emit('changed')
    } else {
      message.error(res.message || '加载失败')
    }
  } catch {
    message.error('加载变更通告失败')
  } finally {
    loading.value = false
  }
}

async function loadConfig() {
  try {
    const res = await getChangeConfig()
    if (res.code === 200) {
      approvalRequired.value = res.data.approvalRequired !== false
      approvalProcessKey.value = res.data.approvalProcessKey || ''
    }
  } catch {
    /* 配置读取失败按默认（必须审批）处理 */
  }
}

/**
 * 查一次"提交审批会拉起的那条流程"（名称 / 版本 / 部署状态 / 说明）。
 *
 * <p>每次都重查：用户可能刚在「流程清单」里部署完就回来提交，缓存会让弹窗仍显示"尚未部署"。
 * 查不到不编造，按"流程清单里找不到"处理；只有拿到确切结论时才禁用提交。
 */
async function loadApprovalFlow() {
  if (approvalFlow.loading) return
  approvalFlow.loading = true
  const key = approvalProcessKey.value
  // 每次重查都先清掉上一次的结论：流程可能刚被删/换 key，留着旧值会显示"已部署"却能提交失败
  Object.assign(approvalFlow, {
    key,
    found: false,
    name: '',
    description: '',
    latestVersion: null,
    latestCreatedAt: '',
    deployedVersion: null,
    changeNote: ''
  })
  try {
    if (!key) return
    const res = await listProcessTemplates({})
    const hit = res?.code === 200 ? (res.data || []).find((item) => item.key === key) : null
    if (!hit) return
    Object.assign(approvalFlow, {
      found: true,
      name: hit.displayName || hit.name || key,
      description: hit.description || '',
      latestVersion: hit.latestVersion ?? null,
      deployedVersion: hit.deployedVersion ?? null
    })
    // 变更说明长在版本行上：单独取一次最新版（取不到只是少一行，不影响提交）
    if (hit.latestVersion) {
      const ver = await getProcessTemplateVersion(hit.oid, hit.latestVersion).catch(() => null)
      if (ver?.code === 200 && ver.data) {
        approvalFlow.changeNote = ver.data.changeNote || ''
        approvalFlow.latestCreatedAt = String(ver.data.createdAt || '').substring(0, 19).replace('T', ' ')
      }
    }
  } catch {
    /* 查不到就不拦：真正的判定在后端 startProcess */
  } finally {
    approvalFlow.loaded = true
    approvalFlow.loading = false
  }
}

function reload() {
  page.value = 1
  loadList()
}

/** 本次流转是否会拉起审批流程：提交审批（需审批时）与老的两步式「发起审批」都算 */
const isApprovalSubmit = computed(() =>
  (flow.action === 'SUBMIT' && approvalRequired.value) || flow.action === 'START_APPROVAL')

/**
 * 现在提交必然被退回的原因（空串 = 可以提交）。
 *
 * <p>三档都说清楚：没配 key / 流程清单里没这条 / 有但没部署。以前这些都要等后端
 * {@code startProcess} 抛错才知道，用户拿到的只是一句"提交失败"。
 */
const submitFlowBlockReason = computed(() => {
  // 正在查的时候什么都不判断：否则每次重开弹窗都会先闪一句"找不到流程"
  if (!isApprovalSubmit.value || !approvalFlow.loaded || approvalFlow.loading) return ''
  if (!approvalFlow.key) return '未配置审批流程：请到「变更控制 → 设置」填写审批流程 key，或开启免审'
  if (!approvalFlow.found) return `流程清单里找不到「${approvalFlow.key}」这条流程，提交会被退回`
  if (!approvalFlow.deployedVersion) return '这条审批流程还没有部署，现在提交会被退回'
  return ''
})

const submitFlowBlocked = computed(() => !!submitFlowBlockReason.value)

/** 不能提交时补一句"去哪儿修"（光说不能提交，用户还是找不到出口） */
const submitFlowBlockHint = computed(() => {
  if (!submitFlowBlocked.value) return ''
  if (approvalFlow.key && approvalFlow.found) {
    return '请先在「流程清单」里部署该流程；或到「变更控制 → 设置」把审批流程换成已部署的那条。'
  }
  return '流程模板可在「业务配置 → 流程模板」查看；未配置流程也可以到「变更控制 → 设置」开启免审。'
})

function handleSearch(value) {
  filters.keyword = value || ''
  reload()
}

function handleTableChange({ pagination }) {
  if (pagination) {
    page.value = pagination.current || 1
    pageSize.value = pagination.pageSize || 20
  }
  loadList()
}

function openCreate() {
  modal.isEdit = false
  modal.oid = ''
  modal.tab = 'basic'
  modal.issueLoaded = true
  modal.form = emptyForm()
  modal.seedIssues = []
  modal.prefilledFrom = null
  modal.applied = null
  modal.tasks = []
  modal.removedTaskOids = []
  modal.chosenAffectedOids = []
  modal.noticeStatus = ''
  modal.visible = true
}

/**
 * 派生单里不允许在通告上改的字段。
 *
 * <p>变更类型 / 分级 / 优先级 / 三项影响 都是 ECR 的判定结论（分级还决定
 * ECR 走单人、会签还是 CCB）—— 通告再判一次，两张单就会各说一套，审批路径失去意义。
 * 只锁**已经有值**的字段：老通告早于这组字段，空值仍允许补录，不然就是个死胡同。
 *
 * <p>变更原因（reason）不在其中：它虽然是 ECR 带过来的起点，但通告面对的是不同的执行范围，
 * 允许改写并在通告上重述理由（用户 2026-11-03：*"选择来源变更请求后，变更原因带过来了，
 * 但是不可编辑了"*）。清来源请求时它照样会被回滚，见 prefill 里的 applied.reason。
 */
const LOCKED_WHEN_DERIVED = [
  'changeType', 'changeLevel', 'priority',
  'certImpact', 'customerImpact', 'inventoryImpact'
]

const lockedFields = computed(() => {
  if (!modal.form.requestOid) return []
  return LOCKED_WHEN_DERIVED.filter((name) => {
    const value = modal.form[name]
    return value !== undefined && value !== null && String(value).trim() !== ''
  })
})

/** 快速换来源请求时，旧响应不能盖掉新选的（每次请求发号，回来时对不上就丢弃） */
let prefillToken = 0

/** 空值口径：undefined / null / 空串在表单里都表示"没填" */
function isBlank(value) {
  return value === undefined || value === null || String(value).trim() === ''
}

/**
 * 富文本取值归一：去标签、解常见实体、空白折叠。
 *
 * <p>富文本编辑器回写的是 HTML：来源请求里的纯文本"验证评审门槛"进编辑器再出来
 * 就成了 {@code <p>验证评审门槛</p>}。按裸字符串比，用户明明没动过的值也会被判成
 * "改过了"，清来源请求时就回滚不掉（用户 2026-11-03：*"清除来源变更请求后，
 * 变更原因没有清除"*）。比较只看文本内容，格式微调不算改动。
 */
function plainTextOf(value) {
  if (value === undefined || value === null) return ''
  return String(value)
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&amp;/gi, '&')
    .replace(/\s+/g, ' ')
    .trim()
}

/** 数组按内容比、标量按等值比：判断某字段是否还是"上次自动带进来的那份" */
function sameValue(current, applied) {
  if (Array.isArray(current) || Array.isArray(applied)) {
    const left = Array.isArray(current) ? current : []
    const right = Array.isArray(applied) ? applied : []
    return left.length === right.length && left.every((item, index) => item === right[index])
  }
  if (isBlank(current) && isBlank(applied)) return true
  if (typeof current === 'string' || typeof applied === 'string') {
    return plainTextOf(current) === plainTextOf(applied)
  }
  return current === applied
}

/**
 * 撤销上一次自动带入：只回填"值还等于带进来那份"的字段（用户没动过），
 * 用户改过的原样保留 —— 清来源不该顺手删掉人手写的内容，也不该留下
 * 一串"请求已经没了、判定信息还在"的来源不明数据。
 */
function revertPrefill() {
  const applied = modal.applied
  modal.applied = null
  modal.prefilledFrom = null
  if (applied) {
    const defaults = emptyForm()
    Object.keys(applied).forEach((name) => {
      if (sameValue(modal.form[name], applied[name])) modal.form[name] = defaults[name]
    })
  }
  // 关联问题的 label 只服务于来源带进来的那批，一并撤掉
  modal.seedIssues = []
}

/**
 * 选了来源请求就把该请求的"变更信息"带到通告上：
 * 标题、补充说明、类型 / 分级 / 优先级 / 三项影响 / 变更原因（派生单只读）、执行方案、
 * 关联问题、计划完成、责任人、变更对象。
 *
 * <p>这是表单上的即时反馈 —— 后端建单时也会做同样的兜底（见 ChangeNoticeServiceImpl#create），
 * 但只在保存后生效，用户看不到"到底带了什么"，所以两边都要做。
 *
 * <p>标题 / 执行方案 / 所属容器只在"空着"或"还是上次自动带进来的"时覆盖：用户改过就不再动 ——
 * 换一张来源请求时，把人手写的方案冲掉比不更新更糟。判定类字段是只读的，直接按来源覆盖。
 *
 * <p>清空来源请求时按 modal.applied 快照回滚（见 revertPrefill）：带进来且没被改过的清掉，
 * 用户改过的保留。否则请求已经清掉、判定信息还留在表单上，就成了来源不明的数据。
 *
 * <p>只在新建时生效：编辑已有通告时换来源请求不应该默默改掉已填的计划与对象。
 */
watch(
  () => modal.form.requestOid,
  async (requestOid) => {
    if (modal.isEdit) return
    if (!requestOid) {
      // 在途的旧响应作废：请求已经清空了，不能等它回来再把值填上
      prefillToken += 1
      revertPrefill()
      return
    }
    const token = ++prefillToken
    try {
      const res = await getChangeRequest(requestOid)
      if (token !== prefillToken || res.code !== 200) return
      const request = res.data?.request || {}
      const issues = res.data?.issues || []
      // 本次真正写进表单的值：清来源时按它回滚。没记进来的字段说明是用户自己的，不动
      const applied = {}

      modal.seedIssues = issues
      modal.form.issueOids = issues.map((i) => i.oid)
      applied.issueOids = [...modal.form.issueOids]

      // 标题：从来源请求带过来（用户改过就不覆盖）
      const nameAuto = isBlank(modal.form.name) || modal.form.name === modal.prefilledFrom?.name
      if (nameAuto && request.name) {
        modal.form.name = request.name
        applied.name = request.name
      }

      // 判定类字段：派生单只读，直接按来源覆盖（后端 create 同样按来源兜底）
      modal.form.changeType = request.changeType || undefined
      modal.form.changeLevel = request.changeLevel || undefined
      modal.form.priority = request.priority || undefined
      modal.form.certImpact = request.certImpact || undefined
      modal.form.customerImpact = request.customerImpact || undefined
      modal.form.inventoryImpact = request.inventoryImpact || undefined
      LOCKED_WHEN_DERIVED.forEach((name) => { applied[name] = modal.form[name] })

      // 变更原因：也按来源带入，但**不锁**——通告可以按自己的执行范围重述理由。
      // 仍记进 applied，清来源时按它回滚（否则"清掉来源、原因还留着"的老问题会回来）
      modal.form.reason = request.reason || ''
      applied.reason = modal.form.reason

      // 执行方案：以 ECR 的建议方案为起点，但允许在通告上改写（用户改过就不再覆盖）
      const proposalAuto = isBlank(modal.form.proposal)
        || modal.form.proposal === modal.applied?.proposal
      if (proposalAuto) {
        modal.form.proposal = request.proposal || ''
        applied.proposal = modal.form.proposal
      }

      // 补充说明：同标题口径（空着或还是自动带进来的才覆盖）——
      // 请求里的说明就是"为什么要改"，通告上重打一遍没有意义
      const descriptionAuto = isBlank(modal.form.description)
        || modal.form.description === modal.applied?.description
      if (descriptionAuto && request.description) {
        modal.form.description = request.description
        applied.description = request.description
      }

      // 计划完成 / 责任人：以来源请求为准（用户仍可再改）
      modal.form.planEnd = request.dueDate || null
      applied.planEnd = modal.form.planEnd
      modal.form.owner = request.owner || undefined
      applied.owner = modal.form.owner
      // 所属容器：没选或还是上次继承来的就跟随来源请求（与后端 create 的兜底一致）
      const productAuto = isBlank(modal.form.productOid)
        || modal.form.productOid === modal.applied?.productOid
      if (productAuto) {
        modal.form.productOid = request.productOid || undefined
        modal.form.productType = request.productType || undefined
        applied.productOid = modal.form.productOid
        applied.productType = modal.form.productType
      }
      modal.applied = applied
      modal.prefilledFrom = { code: request.code || '', name: request.name || '' }
    } catch {
      /* 拉不到就让用户手填，不阻塞建单 */
    }
  }
)

const prefillMessage = computed(() => {
  const source = modal.prefilledFrom
  if (!source) return ''
  const title = source.name ? `「${source.name}」` : ''
  return `已从 ${source.code}${title} 带入变更信息：${lockedFields.value.length ? '类型 / 分级 / 优先级 / 影响（有值即锁定，判定以来源请求为准）、' : ''}变更原因（可改写）、标题、补充说明、执行方案、计划完成、责任人、关联问题`
})

async function openEdit(record) {
  modal.isEdit = true
  modal.oid = record.oid
  modal.tab = 'basic'
  modal.issueLoaded = false
  modal.seedIssues = []
  modal.prefilledFrom = null
  modal.applied = null
  // 更改事项：先按列表行占位，详情回来后用服务的清单覆盖（取消时不动任何线上数据）
  modal.tasks = []
  modal.removedTaskOids = []
  modal.chosenAffectedOids = []
  modal.noticeStatus = record.status || ''
  modal.form = {
    name: record.name,
    requestOid: record.requestOid || undefined,
    productOid: record.productOid || undefined,
    productType: record.productType || undefined,
    changeType: record.changeType || undefined,
    changeLevel: record.changeLevel || undefined,
    priority: record.priority || undefined,
    certImpact: record.certImpact || undefined,
    customerImpact: record.customerImpact || undefined,
    inventoryImpact: record.inventoryImpact || undefined,
    reason: record.reason || '',
    proposal: record.proposal || '',
    planStart: record.planStart || null,
    planEnd: record.planEnd || null,
    owner: record.owner || undefined,
    description: record.description || '',
    // 补充材料：不回填 oid，编辑框会显示成"没传过"，用户也就无从替换旧附件
    attachmentOid: record.attachmentOid || undefined,
    issueOids: []
  }
  modal.visible = true
  // 列表行里没有关联问题，编辑时补一次详情（顺带把已选问题的 label 备好）
  try {
    const res = await getChangeNotice(record.oid)
    if (res.code === 200) {
      const issues = res.data?.issues || []
      modal.seedIssues = issues
      modal.form.issueOids = issues.map((i) => i.oid)
      modal.issueLoaded = true
      // 所属容器以详情为准（列表列可能没带 productOid）；详情结构是 { notice, request, issues }
      const notice = res.data?.notice || {}
      modal.form.productOid = notice.productOid || modal.form.productOid
      modal.form.productType = notice.productType || modal.form.productType
      // 更改事项：任务清单随详情带回（新增/删除都攒在本地，点保存才落库）
      modal.noticeStatus = notice.status || modal.noticeStatus
      modal.tasks = (res.data?.tasks || []).map((task) => ({ ...task }))
      // 本通告已收录的对象：新建任务时这些不能再被挑一次（服务端是幂等的，
      // 挑重复了不会报错，只会让"这个对象归哪张任务"变成最后一句话说了算）
      modal.chosenAffectedOids = (res.data?.affectedObjects || [])
        .map((obj) => obj.entityOid)
        .filter(Boolean)
    }
  } catch {
    /* 详情拉不到就保持"不改关联"，不阻塞其它字段 */
  }
}

/**
 * 当前布局里实际存在的字段名（递归展开 layout-row / group）。
 *
 * <p>必填校验只针对"布局里真有的字段"：租户升级后布局还没同步、或自己把字段删了时，
 * 不能拿一个界面上根本不存在的字段把人卡死。
 */
function layoutFieldNames() {
  const names = new Set()
  const walk = (fields) => {
    for (const field of fields || []) {
      if (field?.fieldName) names.add(field.fieldName)
      if (field?.children?.length) walk(field.children)
    }
  }
  walk(formRef.value?.layoutData?.form?.fields)
  return names
}

async function handleSave() {
  // 校验不过一律回到「基本属性」：字段都长在那半边，停在「更改事项」页签上只会看到
  // 一句"某某不能为空"，却找不到该填的地方
  if (!modal.form.name || !modal.form.name.trim()) {
    modal.tab = 'basic'
    message.warning('请填写标题')
    return
  }
  // 独立新建（没选来源请求）：这五项是审批人判断"改什么、多严重、为什么、怎么做"的唯一依据。
  // 派生单不校验 —— 它们从 ECR 带过来且只读，ECR 那边缺值不该卡住通告。
  if (!modal.form.requestOid) {
    const available = layoutFieldNames()
    const missing = [
      ['changeType', '变更类型'],
      ['changeLevel', '变更分级'],
      ['priority', '优先级'],
      ['reason', '变更原因'],
      ['proposal', '执行方案']
    ].find(([key]) => available.has(key) && !String(modal.form[key] ?? '').trim())
    if (missing) {
      modal.tab = 'basic'
      message.warning(`请填写${missing[1]}（独立新建的变更通告需要完整说明变更信息）`)
      return
    }
  }
  // 必填校验交给布局：字段定义在 ck_type_page_layout，前端不再重复一份
  const errors = formRef.value?.validate?.() || []
  if (errors.length) {
    modal.tab = 'basic'
    message.warning(errors[0])
    return
  }
  const payload = { ...modal.form }
  // patch 语义：新建时留空 → 让后端按来源请求自动带入未关闭的问题；
  // 编辑时详情没拉到 → 不发送，避免用空数组冲掉已有关系
  if (!modal.issueLoaded || (!modal.isEdit && !(payload.issueOids && payload.issueOids.length))) {
    delete payload.issueOids
  }
  modal.saving = true
  try {
    const res = modal.isEdit
      ? await updateChangeNotice(modal.oid, payload)
      : await createChangeNotice(payload)
    if (res.code !== 200) {
      message.error(res.message || '保存失败')
      return
    }
    // 第二部分（更改事项）：单据存下来之后再落任务 —— 新建时任务才有 notice_oid 可挂
    const noticeOid = modal.isEdit ? modal.oid : res.data?.oid
    const taskResult = noticeOid
      ? await persistModalTasks(noticeOid)
      : { created: 0, failed: 0, removed: 0, objectFailed: 0 }
    const done = []
    if (taskResult.created) done.push(`新建 ${taskResult.created} 项变更任务`)
    if (taskResult.removed) done.push(`删除 ${taskResult.removed} 项变更任务`)
    let text = modal.isEdit ? '已保存' : '已创建'
    if (done.length) text += `，${done.join('、')}`
    // 单据已存住，任务失败单独提示：不要让用户以为整单没保存而重填一遍
    if (taskResult.failed) {
      message.warning(`${text}；${taskResult.failed} 项变更任务未能创建，请到详情里补录`)
    } else if (taskResult.objectFailed) {
      message.warning(`${text}；${taskResult.objectFailed} 个对象未能挂到任务上（对象没版本或不在本租户），可在详情里调整`)
    } else {
      message.success(text)
    }
    modal.visible = false
    reload()
    if (drawer.visible && drawer.notice?.oid === modal.oid) openDetail({ oid: modal.oid })
  } catch (e) {
    message.error(e?.message || '保存失败')
  } finally {
    modal.saving = false
  }
}

/**
 * 把「更改事项」页签的改动落到服务端：先删既有任务，再按清单新增。
 *
 * <p>删除打的是编辑时攒下的 removedTaskOids（取消就什么都不发生）；新增打的是本地行。
 * 新增任务的 status 由后端按通告状态决定（审批前 DRAFT，审批后 OPEN），前端不传。
 */
async function persistModalTasks(noticeOid) {
  let removed = 0
  for (const oid of modal.removedTaskOids) {
    try {
      const res = await deleteChangeTask(oid)
      if (res.code === 200) removed += 1
    } catch {
      /* 删不掉的（已被派发 / 已开始）不阻塞整单保存，只是不计入"已删除" */
    }
  }
  const drafts = modal.tasks.filter((task) => task._local)
  if (!drafts.length) return { created: 0, failed: 0, removed, objectFailed: 0 }
  let created = 0
  let failed = 0
  let objectFailed = 0
  for (const draft of drafts) {
    try {
      const res = await createChangeTask({
        noticeOid,
        name: draft.name,
        taskType: draft.taskType,
        assignee: draft.assignee,
        planStart: draft.planStart,
        planEnd: draft.planEnd,
        description: draft.description
      })
      if (res.code !== 200 || !res.data?.oid) {
        failed += 1
        continue
      }
      created += 1
      const attach = await attachObjectsToTask(noticeOid, res.data.oid, draft.affectedEntityOids)
      objectFailed += attach.failed
    } catch {
      failed += 1
    }
  }
  return { created, failed, removed, objectFailed }
}

/**
 * 把任务挑的对象挂到这张任务上（一条对象只归一张任务）。
 *
 * <p>顺序固定：先按对象 oid 加入本通告的受影响对象（版本由服务端回查、已存在则复用既有行），
 * 再改派到任务 —— 受影响对象是下游修订执行的清单，任务只是"谁来做"的归属。
 * 单条失败不打断其余对象：少挂一个比整张任务创建回滚更容易补救（详情里能再改派）。
 */
async function attachObjectsToTask(noticeOid, taskOid, entityOids) {
  let attached = 0
  let failed = 0
  for (const entityOid of entityOids || []) {
    try {
      const res = await addChangeAffectedByEntity(noticeOid, entityOid)
      const affectedOid = res.code === 200 ? res.data?.oid : null
      if (!affectedOid) {
        failed += 1
        continue
      }
      await assignChangeAffectedTask(noticeOid, affectedOid, taskOid)
      attached += 1
    } catch {
      /* 对象没版本 / 不属于本租户 / 通告已过草稿阶段：该对象挂不上，其余照旧 */
      failed += 1
    }
  }
  return { attached, failed }
}

function openTransition(record, action) {
  flow.oid = record.oid
  flow.action = action
  flow.comment = ''
  flow.record = record
  flow.visible = true
  // 「提交审批」要先把会拉起的那条流程查出来（名称 / 版本 / 部署状态），
  // 其余动作不涉及流程，不必多打一次请求
  if ((action === 'SUBMIT' && approvalRequired.value) || action === 'START_APPROVAL') {
    loadApprovalFlow()
  }
}

async function handleFlow() {
  if (submitFlowBlocked.value) {
    message.warning(submitFlowBlockReason.value)
    return
  }
  const needComment = noticeRequiresComment(flow.action) || (flow.action === 'SUBMIT' && !approvalRequired.value)
  if (needComment && !flow.comment.trim()) {
    message.warning(flow.action === 'SUBMIT' ? '免审提交必须填写原因' : '请填写说明')
    return
  }
  flow.saving = true
  try {
    const res = await transitionChangeNotice(flow.oid, { action: flow.action, comment: flow.comment })
    if (res.code === 200) {
      // 提交审批与「发起审批」都会把单子送进流程：提示要指向下一步去哪办，
      // 否则用户以为"提交完了就等"，不知道审批人要去任务中心点
      const label = noticeActionLabel(flow.action, approvalRequired.value)
      const startedApproval = flow.action === 'START_APPROVAL'
        || (flow.action === 'SUBMIT' && approvalRequired.value)
      message.success(
        startedApproval ? `${label}完成，请在「任务中心」办理审批` : '已' + label
      )
      flow.visible = false
      reload()
      if (drawer.visible && drawer.notice?.oid === flow.oid) openDetail({ oid: flow.oid })
    } else {
      message.error(res.message || '流转失败')
    }
  } catch (e) {
    message.error(e?.message || '流转失败')
  } finally {
    flow.saving = false
  }
}

async function openDetail(record) {
  drawer.visible = true
  drawer.loading = true
  try {
    const res = await getChangeNotice(record.oid)
    if (res.code === 200) {
      drawer.notice = res.data.notice
      drawer.request = res.data.request
      drawer.affectedObjects = res.data.affectedObjects || []
      drawer.tasks = res.data.tasks || []
      drawer.activities = res.data.activities || []
      // 从 ECR 转来的通告会在这里看到被带过来的那几条（已关闭的在服务端就过滤掉了）
      drawer.issues = res.data.issues || []
      // 补充材料：按 attachmentOid 回查文件名；查不到就当没传
      drawer.attachment = null
      if (drawer.notice.attachmentOid) {
        const att = await getAttachment(drawer.notice.attachmentOid).catch(() => null)
        if (att?.code === 200) drawer.attachment = att.data
      }
    } else {
      message.error(res.message || '加载详情失败')
    }
  } catch {
    message.error('加载详情失败')
  } finally {
    drawer.loading = false
  }
}

function openStatusForm(record) {
  statusForm.oid = record.oid
  statusForm.code = record.code
  statusForm.current = record.status
  statusForm.status = undefined
  statusForm.reason = ''
  statusForm.visible = true
}

/**
 * 提交管理员设置：原因必填 —— 这个操作跳过全部门槛，事后只能靠这条记录解释
 * "为什么一张没执行完的单变成了已关闭"。
 */
async function handleSetStatus() {
  if (!statusForm.status) {
    message.warning('请选择目标状态')
    return
  }
  if (!statusForm.reason.trim()) {
    message.warning('请填写原因（会记入处理记录）')
    return
  }
  statusForm.saving = true
  try {
    const res = await setChangeNoticeStatus(statusForm.oid, {
      status: statusForm.status,
      reason: statusForm.reason.trim()
    })
    if (res.code === 200) {
      message.success('状态已设置')
      statusForm.visible = false
      reload()
      // 抽屉正开着同一张单：重新拉一次，别让详情头部的状态标签停在旧值
      if (drawer.visible && drawer.notice?.oid === statusForm.oid) {
        openDetail({ oid: statusForm.oid })
      }
    } else {
      message.error(res.message || '设置失败')
    }
  } catch (e) {
    message.error(e?.message || '设置失败')
  } finally {
    statusForm.saving = false
  }
}

async function handleDelete(record) {
  try {
    const res = await deleteChangeNotice(record.oid)
    if (res.code === 200) {
      message.success('已删除')
      reload()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch (e) {
    message.error(e?.message || '删除失败')
  }
}

function openAffectedForm() {
  affectedForm.form = emptyAffected()
  affectedForm.visible = true
}

async function handleAddAffected() {
  const form = affectedForm.form
  if (!form.entityOid.trim() || !form.baseIterationOid.trim() || !form.baseVersion.trim()) {
    message.warning('必须填写主对象 oid、受影响迭代 oid 与版本')
    return
  }
  affectedForm.saving = true
  try {
    const res = await addChangeAffectedObject(drawer.notice.oid, form)
    if (res.code === 200) {
      message.success('已添加')
      affectedForm.visible = false
      openDetail({ oid: drawer.notice.oid })
    } else {
      message.error(res.message || '添加失败')
    }
  } catch (e) {
    message.error(e?.message || '添加失败')
  } finally {
    affectedForm.saving = false
  }
}

async function handleRemoveAffected(record) {
  try {
    const res = await removeChangeAffectedObject(drawer.notice.oid, record.oid)
    if (res.code === 200) {
      message.success('已移除')
      openDetail({ oid: drawer.notice.oid })
    } else {
      message.error(res.message || '移除失败')
    }
  } catch (e) {
    message.error(e?.message || '移除失败')
  }
}

/** 对象改派到某个更改事项；taskOid 为空 = 取消归属 */
async function handleAssignTask(record, taskOid) {
  try {
    const res = await assignChangeAffectedTask(drawer.notice.oid, record.oid, taskOid)
    if (res.code === 200) {
      message.success(taskOid ? '已归入该更改事项' : '已取消归属')
      openDetail({ oid: drawer.notice.oid })
    } else {
      message.error(res.message || '改派失败')
    }
  } catch (e) {
    message.error(e?.message || '改派失败')
  }
}

/** 详情抽屉里建任务：单据已存在，创建即落库 */
function openTaskForm() {
  taskForm.source = 'drawer'
  taskForm.form = emptyTask()
  // 上一次任务收集来的候选不能带进新任务：它们对应的是上一张任务的范围
  taskForm.seedScope = []
  taskForm.form.assignee = currentUsername.value || undefined
  taskForm.visible = true
}

/** 通告弹窗「更改事项」页签里建任务：先挂在本地清单上，随「保存」一起落库 */
function openModalTaskForm() {
  if (!modalTaskEditable.value) return
  taskForm.source = 'modal'
  taskForm.form = emptyTask()
  taskForm.seedScope = []
  taskForm.form.assignee = currentUsername.value || modal.form.owner || undefined
  taskForm.visible = true
}

/**
 * 收集所选对象关联的其他对象（零件 → 关联文档 / 替代件），并入本任务的变更对象。
 *
 * <p>口径见 {@code ChangeRequestService#relatedObjects}：BOM 结构不收集。
 * 收集只影响表单里的取值，落库仍走「保存」时的 addAffectedByEntity + 改派，
 * 所以取消建单不会在通告上留下一堆对象。
 */
async function collectRelatedObjects() {
  const selected = Array.isArray(taskForm.form.affectedOids) ? [...taskForm.form.affectedOids] : []
  if (!selected.length) {
    message.info('先选一个零件，再收集它关联的文档 / 替代件')
    return
  }
  taskForm.collecting = true
  try {
    const res = await listRelatedChangeObjects(selected)
    if (res.code !== 200) {
      message.error(res.message || '收集失败')
      return
    }
    // 已选 + 本通告已收录的都不再收：收进来也只会显示成「（已在本变更中）」的置灰项，
    // 白让用户以为收集成功了
    const existing = new Set([...selected, ...noticeChosenOids.value])
    const fresh = (res.data || []).filter((row) => row.oid && !existing.has(row.oid))
    if (!fresh.length) {
      message.info('没有新的关联对象可收集')
      return
    }
    const RELATION_LABEL = { DOCUMENT: '关联文档', ALTERNATE: '替代件' }
    // 控件只认自己搜过的候选：不把收集结果塞回 seedOptions，新对象会显示成裸 oid
    taskForm.seedScope = [
      ...(taskForm.seedScope || []),
      ...fresh.map((row) => ({
        value: row.oid,
        entityType: row.entityType,
        entityCode: row.code,
        entityName: `${row.name || ''}（${RELATION_LABEL[row.relation] || '关联对象'}）`.trim()
      }))
    ]
    taskForm.form.affectedOids = [...selected, ...fresh.map((row) => row.oid)]
    const docs = fresh.filter((row) => row.relation === 'DOCUMENT').length
    const alternates = fresh.filter((row) => row.relation === 'ALTERNATE').length
    message.success(`收集到 ${fresh.length} 个关联对象（关联文档 ${docs}、替代件 ${alternates}）`)
  } catch (e) {
    message.error(e?.message || '收集失败')
  } finally {
    taskForm.collecting = false
  }
}

/** 从弹窗清单里移除：既有任务只是记账（保存时才删），本地行直接扔掉 */
function removeModalTask(record) {
  const index = modal.tasks.findIndex((task) => (task.oid || task.key) === (record.oid || record.key))
  if (index < 0) return
  if (!record._local && record.oid) modal.removedTaskOids.push(record.oid)
  modal.tasks.splice(index, 1)
}

/** 能不能删：已派发 / 已开始的任务后端不允许删，别让按钮点得动再被打回 */
function canRemoveModalTask(record) {
  if (!modalTaskEditable.value) return false
  return record._local || ['DRAFT', 'OPEN'].includes(record.status)
}

async function handleCreateTask() {
  if (!taskForm.form.name || !taskForm.form.name.trim()) {
    message.warning('请填写任务标题')
    return
  }
  const errors = taskFormRef.value?.validate?.() || []
  if (errors.length) {
    message.warning(errors[0])
    return
  }
  const { affectedOids, ...rest } = taskForm.form
  // 弹窗内规划：不落库，先进清单（保存通告时统一创建，取消建单不留孤儿任务）
  if (taskForm.source === 'modal') {
    modal.tasks.push({
      key: `draft-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      _local: true,
      ...rest,
      name: String(rest.name || '').trim(),
      // 状态是展示用的占位：真正落库时由后端按通告状态决定（审批前 DRAFT）
      status: 'DRAFT',
      // 范围存对象 oid：通告还没创建，受影响对象行还不存在（见 persistModalTasks）
      affectedEntityOids: [...(affectedOids || [])]
    })
    taskForm.visible = false
    return
  }
  taskForm.saving = true
  try {
    const res = await createChangeTask({ ...rest, noticeOid: drawer.notice.oid })
    if (res.code === 200) {
      // 事项范围：创建成功后再把对象逐条加入本通告并归到这张任务下
      const taskOid = res.data?.oid
      const attach = taskOid ? await attachObjectsToTask(drawer.notice.oid, taskOid, affectedOids) : { failed: 0 }
      if (attach.failed) {
        message.warning(`${attach.failed} 个对象未能挂到任务上（对象没版本，或通告已过草稿阶段不能再加受影响对象）`)
      } else {
        message.success(res.data?.status === 'DRAFT' ? '已加入更改事项清单（审批通过后派发）' : '任务已创建')
      }
      taskForm.visible = false
      openDetail({ oid: drawer.notice.oid })
    } else {
      message.error(res.message || '创建失败')
    }
  } catch (e) {
    message.error(e?.message || '创建失败')
  } finally {
    taskForm.saving = false
  }
}

function openTaskTransition(record, action) {
  taskFlow.oid = record.oid
  taskFlow.action = action
  taskFlow.comment = ''
  taskFlow.visible = true
}

async function handleTaskFlow() {
  if (taskRequiresComment(taskFlow.action) && !taskFlow.comment.trim()) {
    message.warning('请填写说明')
    return
  }
  taskFlow.saving = true
  try {
    const res = await transitionChangeTask(taskFlow.oid, { action: taskFlow.action, comment: taskFlow.comment })
    if (res.code === 200) {
      message.success('已' + CHANGE_TASK_ACTION_LABEL[taskFlow.action])
      taskFlow.visible = false
      openDetail({ oid: drawer.notice.oid })
    } else {
      message.error(res.message || '流转失败')
    }
  } catch (e) {
    message.error(e?.message || '流转失败')
  } finally {
    taskFlow.saving = false
  }
}

function taskName(taskOid) {
  if (!taskOid) return '—'
  const task = drawer.tasks.find((t) => t.oid === taskOid)
  return task ? task.code : '—'
}

function formatDateTime(value) {
  if (!value) return '—'
  const s = String(value).replace('T', ' ')
  return s.length >= 16 ? s.slice(5, 16) : s
}

/** 只到天：布局里的日期控件没声明 valueFormat 时回的是 dayjs 对象，String() 会变成英文长串 */
function formatDay(value) {
  if (!value) return '—'
  if (typeof value?.format === 'function') return value.format('YYYY-MM-DD')
  const s = String(value)
  return s.length >= 10 ? s.slice(0, 10) : s
}

const route = useRoute()
const router = useRouter()

/** 评审流程实例详情：/workflow/instance/:id 对普通成员开放（见 router 的 ADMIN_ROUTES 注释） */
function openProcessInstance(instanceId) {
  if (!instanceId) return
  router.push({ path: `/workflow/instance/${instanceId}` })
}

/**
 * 打开来源变更请求：切到「变更请求」页签并展开它的详情抽屉。
 *
 * <p>先收起本抽屉 —— 页签容器是 destroy-inactive=false，抽屉不会随页签切换自动关，
 * 两层遮罩叠在一起会挡住刚打开的那张请求（与 ChangeRequestPanel 跳通告详情的处理一致）。
 */
function openSourceRequest(oid) {
  if (!oid) return
  drawer.visible = false
  router.push({ path: '/change', query: { tab: 'request', requestOid: oid } })
}

/**
 * 从对象详情页「变更情况」或全局搜索跳进来（`?noticeOid=…`）：直接把那张单的详情抽屉打开，
 * 不让用户再在列表里找一遍。openDetail 只吃 oid，详情仍以服务端返回为准。
 *
 * <p>用 watch 而不是只在 onMounted 里读一次：已经停在变更控制页时再搜一张单，
 * 组件不会重建，只读 mounted 就什么都不发生。
 */
function openFromRoute() {
  const noticeOid = route.query.noticeOid
  if (!noticeOid) return
  openDetail({ oid: String(noticeOid) })
  // 与变更请求同一口径：一次性指令用完就擦掉，否则 F5 会再弹一次详情抽屉
  const query = { ...route.query }
  delete query.noticeOid
  router.replace({ query })
}

watch(() => route.query.noticeOid, openFromRoute)

onMounted(() => {
  loadList()
  loadConfig()
  openFromRoute()
})

defineExpose({ reload })
</script>

<style scoped>
.chg-panel { display: flex; flex-direction: column; height: 100%; }

.chg-link { color: #1677ff; cursor: pointer; }
.chg-bypass-tag { margin-left: 6px; }
.chg-drawer-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; gap: 12px; }
.chg-drawer-title { margin-left: 8px; }
.chg-drawer-actions { margin-top: 12px; }
.chg-divider { margin: 16px 0 12px; }
.chg-flow-hint { margin-bottom: 12px; }
.chg-pre { white-space: pre-wrap; word-break: break-word; }
.chg-muted { color: #8c8c8c; }
.chg-hint { color: #8c8c8c; font-size: 12px; margin-top: 6px; line-height: 1.6; }
.chg-collect-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: -2px 0 8px; font-size: 12px; }
.chg-act-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.chg-act-flow { color: #8c8c8c; font-size: 12px; }
.chg-act-time { color: #bfbfbf; font-size: 12px; margin-left: auto; }
.chg-act-op { color: #595959; font-size: 12px; margin-top: 2px; }
.chg-act-comment { margin-top: 4px; padding: 6px 8px; background: #fafafa; border-radius: 4px; white-space: pre-wrap; }
/* 新建 / 编辑弹窗：基本属性 | 更改事项 两个页签 */
.chg-modal-tabs :deep(.ant-tabs-nav) { margin-bottom: 12px; }
.chg-modal-task-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  font-size: 12px;
}

/* 提交审批弹窗：单据结论条 +「流程信息」网格，视觉层次与「发起流程」弹框一致 */
.ck-summary { margin-bottom: 12px; }
.ecn-flow-state {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  white-space: nowrap;
}
.ecn-info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  background: #f0f0f0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
}
.ecn-info__item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  padding: 6px 10px;
  background: #fff;
}
.ecn-info__item--full { grid-column: 1 / -1; }
.ecn-info__label {
  flex: 0 0 auto;
  font-size: 12px;
  line-height: 20px;
  color: #8c8c8c;
  white-space: nowrap;
}
.ecn-info__value {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 13px;
  line-height: 20px;
  color: rgba(0, 0, 0, 0.88);
  word-break: break-word;
}
.ecn-info__value--tags { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.ecn-info__value--text { line-height: 1.65; white-space: pre-wrap; }
.ecn-info__code { font-family: monospace; font-size: 12px; color: #595959; }
.ecn-info__muted { font-size: 12px; color: #8c8c8c; }
</style>
