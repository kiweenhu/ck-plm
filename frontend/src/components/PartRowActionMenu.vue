<!--
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * 行操作下拉菜单（共享组件）—— 支持两类宿主：
 *   • Part 体系（PART / ELECTRONIC 等软类型）        → /parts/*
 *   • 工程数据体系（ENG_DOCUMENT / FOOTPRINT / SYMBOL）→ /eng-documents/*
 *
 * <p>宿主由记录的 typeDefinitionCode 经 resolveEntityApiPath 推导后自动分派（见 api 适配层），
 * 因此同一套操作能力对零组件列表、元器件库与封装·图符库完全一致，无需各处重复实现。
 *
 * <p>支持操作：详情 / 重命名 / 另存为 / 查看历史版本 / 检出 / 检出并编辑 / 编辑 /
 * 检入 / 取消检出 / 下载主文件 / 移动（或变更分类）/ 新建视图版本 /
 * 设置生命周期状态 / 发起流程 / 删除。
 *
 * <p>仅 Part 宿主提供的操作：详情（工程数据暂无详情页）、另存为（工程数据暂无该接口）。
 *
 * <p>所有写操作成功后 emit('success')，由宿主页面负责刷新列表。
-->
<template>
  <span>
    <a-dropdown :trigger="['click']">
      <a-button type="link" size="small">
        操作 <DownOutlined style="font-size:10px;margin-left:2px" />
      </a-button>
      <template #overlay>
        <a-menu @click="onMenuClick">
          <!-- 详情 / 另存为：工程数据（封装 / 图符）暂无详情路由与另存为接口，故仅 Part 宿主提供 -->
          <a-menu-item v-if="!isEngDoc" key="view"><EyeOutlined /> 详情</a-menu-item>
          <a-menu-item key="rename"><FormOutlined /> 重命名</a-menu-item>
          <a-menu-item v-if="!isEngDoc" key="saveAs"><CopyOutlined /> 另存为</a-menu-item>
          <a-menu-item key="history"><HistoryOutlined /> 查看历史版本</a-menu-item>
          <a-menu-item v-if="!isCheckedOut" key="checkout"><LockOutlined /> 检出</a-menu-item>
          <a-menu-item v-if="!isCheckedOut" key="checkoutEdit"><EditOutlined /> 检出并编辑</a-menu-item>
          <a-menu-item v-if="isCheckedOut" key="edit"><EditOutlined /> 编辑</a-menu-item>
          <a-menu-item v-if="isCheckedOut" key="checkin"><CheckOutlined /> 检入</a-menu-item>
          <a-menu-item v-if="isCheckedOut" key="undoCheckout"><RollbackOutlined /> 取消检出</a-menu-item>
          <a-menu-item v-if="showDownload && record.ckfileOid" key="download"><DownloadOutlined /> 下载主文件</a-menu-item>
          <a-menu-divider />
          <a-menu-item key="move"><SwapOutlined /> {{ moveLabel }}</a-menu-item>
          <a-menu-item key="newViewVersion"><BranchesOutlined /> 新建视图版本</a-menu-item>
          <a-menu-divider />
          <a-menu-item v-if="!isCheckedOut" key="lifecycle"><ExperimentOutlined /> 设置生命周期状态</a-menu-item>
          <a-menu-item v-if="!isCheckedOut" key="workflow"><SendOutlined /> 发起流程</a-menu-item>
          <a-menu-divider />
          <a-menu-item v-if="!isCheckedOut" key="delete" danger><DeleteOutlined /> 删除</a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>

    <!-- 重命名 -->
    <a-modal
      v-model:visible="renameVisible"
      title="重命名"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="confirmRename"
      width="420px"
    >
      <p style="margin-bottom:12px">对象：<b>{{ record.code || record.name }}</b></p>
      <a-input
        v-model:value="renameName"
        placeholder="请输入新名称"
        maxlength="100"
        @pressEnter="confirmRename"
      />
    </a-modal>

    <!-- 另存为 -->
    <a-modal
      v-model:visible="saveAsVisible"
      title="另存为"
      ok-text="另存为"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="confirmSaveAs"
      width="420px"
    >
      <p style="margin-bottom:12px">源对象：<b>{{ record.code || record.name }}</b></p>
      <a-input
        v-model:value="saveAsName"
        placeholder="请输入新名称"
        maxlength="100"
        @pressEnter="confirmSaveAs"
      />
      <p style="margin-top:12px;color:#8c8c8c;font-size:12px">
        将复制当前对象的类型、分类、单位、来源及 IBA 属性值，生成一个新编号的对象。
      </p>
    </a-modal>

    <!-- 历史版本 -->
    <a-modal
      v-model:visible="historyVisible"
      :title="historyTitle"
      :footer="null"
      width="860px"
    >
      <a-table
        :columns="historyColumns"
        :data-source="historyData"
        :loading="historyLoading"
        :pagination="false"
        row-key="oid"
        size="small"
        :scroll="{ y: 420 }"
      >
        <template #bodyCell="{ column, record: r }">
          <template v-if="column.key === 'displayVersion'">
            <a-tag color="blue">{{ r.displayVersion || '-' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(r.status?.code)" size="small">
              {{ r.status?.displayName || r.status?.code || '-' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'latest'">
            <a-tag v-if="r.latest" color="green" size="small">最新</a-tag>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'checkout'">
            <a-tag v-if="r.checkedOut" color="orange" size="small">已检出: {{ r.checkedOutBy || '-' }}</a-tag>
            <a-tag v-else color="green" size="small">已检入</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            <span style="font-size:12px;color:#8c8c8c">{{ fmt(r.createdAt) }}</span>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- 检出（填写注释） -->
    <a-modal
      v-model:visible="checkoutVisible"
      title="检出"
      ok-text="确认检出"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="confirmCheckout"
    >
      <div style="margin-bottom:8px">
        <a-tag color="blue" size="small">{{ record.code || '-' }}</a-tag>
        <span style="font-weight:500;margin-left:6px">{{ record.name || '-' }}</span>
        <span style="color:#8c8c8c;margin-left:6px">· {{ record.displayVersion || '-' }}</span>
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

    <!-- 移动 / 变更分类 -->
    <a-modal
      v-model:visible="moveVisible"
      :title="moveLabel"
      ok-text="确定"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="confirmMove"
      width="480px"
    >
      <p style="margin-bottom:16px">对象：<b>{{ record.code || record.name }}</b></p>
      <a-form layout="vertical">
        <a-form-item :label="moveMode === 'category' ? '目标分类' : '目标位置'" required>
          <a-tree-select
            v-model:value="moveTargetOid"
            :tree-data="moveTree"
            :loading="moveLoading"
            :tree-default-expand-all="true"
            show-search
            tree-node-filter-prop="title"
            :field-names="{ label: 'title', key: 'key', value: 'value', children: 'children' }"
            placeholder="请选择"
            style="width:100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑（动态表单，按 typeDefinitionCode 加载对应布局） -->
    <a-modal
      v-model:visible="editVisible"
      title="编辑"
      :confirm-loading="saving"
      @cancel="editVisible = false"
      width="640px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    >
      <DynamicForm
        v-if="editVisible"
        ref="editFormRef"
        :key="record.oid"
        :entity-code="record.typeDefinitionCode || 'PART'"
        operation-code="update"
        :entity-oid="record.oid"
        fallback-entity-code="PART"
        v-model="editForm"
      />
      <template #footer>
        <a-button @click="editVisible = false">取消</a-button>
        <a-button type="primary" @click="confirmEdit" :loading="saving">保存</a-button>
      </template>
    </a-modal>

    <!-- 删除（选择删除范围） -->
    <a-modal
      v-model:visible="deleteVisible"
      title="删除"
      ok-text="确认删除"
      cancel-text="取消"
      :ok-button-props="{ danger: true }"
      :confirm-loading="saving"
      @ok="confirmDelete"
      width="480px"
    >
      <p style="margin-bottom:16px">对象：<b>{{ record.code || record.name }}</b></p>
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
    </a-modal>

    <!-- 发起流程（共享弹框：按类型+状态解析该发起的流程，再经 Flowable 启动） -->
    <StartProcessModal
      v-model:visible="startProcessVisible"
      :business="startProcessTarget"
      @success="done()"
    />

    <!-- 设置生命周期状态（共享弹框：按类型绑定的生命周期模板给出"初始状态 / 指定状态"两条路） -->
    <SetLifecycleStateModal
      v-model:visible="lifecycleVisible"
      :record="lifecycleTarget"
      @success="done()"
    />
  </span>
</template>

<script setup>
import { ref, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import {
  EyeOutlined, FormOutlined, CopyOutlined, HistoryOutlined, LockOutlined,
  EditOutlined, CheckOutlined, RollbackOutlined, DownloadOutlined,
  SwapOutlined, BranchesOutlined, ExperimentOutlined, SendOutlined,
  DeleteOutlined, DownOutlined,
} from '@ant-design/icons-vue'
import DynamicForm from '@/components/DynamicForm.vue'
import StartProcessModal from '@/components/StartProcessModal.vue'
import SetLifecycleStateModal from '@/components/SetLifecycleStateModal.vue'
import {
  renamePart, saveAsPart, getPartIterations, movePart, newViewVersionPart,
  checkoutPart as checkoutPartApi, checkinPart as checkinPartApi,
  undoCheckoutPart as undoCheckoutPartApi, deletePart, deletePartLatestIteration,
  updatePart, getDocumentDownloadUrl, getLibraryCategoryTree,
  // 工程数据（EngineeringDocument：CAD 设计数据 / 封装 / 图符）宿主接口
  renameEngineeringDocument, moveEngineeringDocument, newViewVersionEngineeringDocument,
  checkoutEngineeringDocument, checkinEngineeringDocument, undoCheckoutEngineeringDocument,
  deleteEngineeringDocument, deleteEngineeringDocumentLatestIteration,
  updateEngineeringDocument, getEngineeringDocumentIterations,
  resolveEntityApiPath,
} from '@/api'

const props = defineProps({
  /** 行数据：需含 oid / name / code / checkedOut；typeDefinitionCode 用于编辑时加载对应布局 */
  record: { type: Object, required: true },
  /** 是否显示「下载主文件」（元器件无主文件时传 false） */
  showDownload: { type: Boolean, default: true },
  /** 移动模式：'folder'=移动文件夹（默认） / 'category'=变更元器件分类 */
  moveMode: { type: String, default: 'folder' },
  /** folder 模式下的目标产品/文件夹树 [{ value, title, children }] */
  moveTreeData: { type: Array, default: () => [] },
})
const emit = defineEmits(['success'])

const router = useRouter()
const isCheckedOut = computed(() => !!props.record.checkedOut)
const moveLabel = computed(() => (props.moveMode === 'category' ? '变更分类' : '移动'))
const saving = ref(false)

// ==================== 宿主分派（Part / 工程数据）====================

/**
 * 记录所属宿主的 API 路径：由 typeDefinitionCode 推导
 * （PART / ELECTRONIC → 'parts'；ENG_DOCUMENT / FOOTPRINT / SYMBOL → 'eng-documents'）。
 */
const hostPath = computed(() => resolveEntityApiPath(props.record.typeDefinitionCode) || 'parts')

/** 是否为工程数据（CAD 设计数据：3D 数模 / 2D 工程图 / 封装 / 图符）宿主 */
const isEngDoc = computed(() => hostPath.value === 'eng-documents')

/**
 * 按宿主分派的 API 适配层 —— 使同一套行操作对 Part 与工程数据均可复用。
 *
 * <p>此前所有操作硬编码 Part 接口，导致工程数据（封装 / 图符）的 oid
 * 被提交到 /parts/* 而失败。现按记录类型自动分派。
 */
const api = computed(() => (isEngDoc.value
  ? {
      rename: renameEngineeringDocument,
      getIterations: getEngineeringDocumentIterations,
      checkout: checkoutEngineeringDocument,
      checkin: checkinEngineeringDocument,
      undoCheckout: undoCheckoutEngineeringDocument,
      update: updateEngineeringDocument,
      move: moveEngineeringDocument,
      newViewVersion: newViewVersionEngineeringDocument,
      remove: deleteEngineeringDocument,
      removeLatest: deleteEngineeringDocumentLatestIteration,
    }
  : {
      rename: renamePart,
      getIterations: getPartIterations,
      checkout: checkoutPartApi,
      checkin: checkinPartApi,
      undoCheckout: undoCheckoutPartApi,
      update: updatePart,
      move: movePart,
      newViewVersion: newViewVersionPart,
      remove: deletePart,
      removeLatest: deletePartLatestIteration,
    }))

const statusColor = (code) => ({
  DRAFT: 'default', INWORK: 'processing', REVIEW: 'warning',
  APPROVED: 'success', RELEASED: 'blue', OBSOLETE: 'error',
}[code] || 'default')

const fmt = (v) => (v ? String(v).substring(0, 19).replace('T', ' ') : '-')

/** 操作成功：提示并通知宿主刷新列表 */
function done(msg) {
  if (msg) message.success(msg)
  emit('success')
}

// ==================== 菜单分发 ====================

function onMenuClick({ key }) {
  switch (key) {
    case 'view': return openDetail()
    case 'rename': return openRename()
    case 'saveAs': return openSaveAs()
    case 'history': return openHistory()
    case 'checkout': return openCheckout(false)
    case 'checkoutEdit': return openCheckout(true)
    case 'edit': return openEdit()
    case 'checkin': return doCheckin()
    case 'undoCheckout': return doUndoCheckout()
    case 'download': return doDownload()
    case 'move': return openMove()
    case 'newViewVersion': return doNewViewVersion()
    case 'lifecycle': return openLifecycle()
    case 'workflow': return openStartProcess()
    case 'delete': return openDelete()
  }
}

/** 详情：新窗口打开零组件详情页 */
function openDetail() {
  const href = router.resolve({ name: 'PartDetail', params: { oid: props.record.oid } }).href
  window.open(href, '_blank')
}

// ==================== 发起流程 ====================

const startProcessVisible = ref(false)
/** 传给共享弹框的业务对象（字段做兼容：不同列表 DTO 的状态字段名不同） */
const startProcessTarget = ref(null)

/**
 * 打开「发起流程」弹框。
 *
 * <p>解析与启动都在共享组件 {@link StartProcessModal} 里完成 ——
 * 各宿主页面只负责把"当前业务对象"传进来，避免每个页面各写一套解析/发起逻辑。
 */
function openStartProcess() {
  const record = props.record
  startProcessTarget.value = {
    oid: record.oid,
    name: record.name || record.code,
    code: record.code || record.number,
    typeDefinitionCode: record.typeDefinitionCode
      || (isEngDoc.value ? 'ENG_DOCUMENT' : 'PART'),
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
/** 传给弹框的对象（字段做兼容：不同列表 DTO 的编码/类型字段名不同） */
const lifecycleTarget = ref(null)

/**
 * 打开「设置生命周期状态」弹框。
 *
 * <p>候选项（初始状态 / 指定状态）与可达性都由共享弹框按<b>类型绑定的生命周期模板</b>取
 * （见 components/SetLifecycleStateModal.vue），本组件只负责把当前对象传进去。
 */
function openLifecycle() {
  const record = props.record
  lifecycleTarget.value = {
    oid: record.oid,
    name: record.name,
    code: record.code || record.number,
    typeDefinitionCode: record.typeDefinitionCode
      || (isEngDoc.value ? 'ENG_DOCUMENT' : 'PART'),
    statusCode: record.statusCode || record.status?.code,
  }
  lifecycleVisible.value = true
}

// ==================== 重命名 ====================

const renameVisible = ref(false)
const renameName = ref('')

function openRename() {
  renameName.value = props.record.name || ''
  renameVisible.value = true
}

async function confirmRename() {
  const name = renameName.value?.trim()
  if (!name) { message.warning('请输入名称'); return }
  saving.value = true
  try {
    const res = await api.value.rename(props.record.oid, name)
    if (res?.code === 200) { renameVisible.value = false; done('重命名成功') }
    else message.error(res?.message || '重命名失败')
  } catch (e) {
    message.error('重命名失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    saving.value = false
  }
}

// ==================== 另存为 ====================

const saveAsVisible = ref(false)
const saveAsName = ref('')

function openSaveAs() {
  saveAsName.value = `${props.record.name || ''} - 副本`
  saveAsVisible.value = true
}

async function confirmSaveAs() {
  const name = saveAsName.value?.trim()
  if (!name) { message.warning('请输入名称'); return }
  saving.value = true
  try {
    const res = await saveAsPart(props.record.oid, name)
    if (res?.code === 200) { saveAsVisible.value = false; done('另存为成功') }
    else message.error(res?.message || '另存为失败')
  } catch (e) {
    message.error('另存为失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    saving.value = false
  }
}

// ==================== 查看历史版本 ====================

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyData = ref([])
const historyTitle = ref('')
const historyColumns = [
  { title: '版本', key: 'displayVersion', width: 100 },
  { title: '大版本', dataIndex: 'revision', key: 'revision', width: 80 },
  { title: '小版本', dataIndex: 'iteration', key: 'iteration', width: 80 },
  { title: '生命周期状态', key: 'status', width: 120 },
  { title: '最新', key: 'latest', width: 70 },
  { title: '检出状态', key: 'checkout', width: 140 },
  { title: '创建人', dataIndex: 'creator', key: 'creator', width: 100 },
  { title: '创建时间', key: 'createdAt', width: 170 },
]

async function openHistory() {
  historyTitle.value = `历史版本：${props.record.code || ''} ${props.record.name || ''}`
  historyVisible.value = true
  historyLoading.value = true
  try {
    const res = await api.value.getIterations(props.record.oid)
    historyData.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    historyData.value = []
  } finally {
    historyLoading.value = false
  }
}

// ==================== 检出 / 检入 / 取消检出 ====================

const checkoutVisible = ref(false)
const checkoutComment = ref('')
let thenEdit = false

function openCheckout(withEdit) {
  thenEdit = withEdit
  checkoutComment.value = ''
  checkoutVisible.value = true
}

async function confirmCheckout() {
  saving.value = true
  try {
    const res = await api.value.checkout(props.record.oid, checkoutComment.value.trim())
    if (res?.code === 200) {
      checkoutVisible.value = false
      done('检出成功')
      if (thenEdit) openEdit()
    } else {
      message.error(res?.message || '检出失败')
    }
  } catch (e) {
    message.error('检出失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    saving.value = false
    thenEdit = false
  }
}

function doCheckin() {
  Modal.confirm({
    title: `确认检入 "${props.record.code || props.record.name}"？`,
    content: '检入后，本次检出修改的副本将保存为新的版本，并解除检出锁定。',
    okText: '确认检入',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await api.value.checkin(props.record.oid)
        if (res?.code === 200) done('已检入')
        else message.error(res?.message || '检入失败')
      } catch (e) {
        message.error('检入失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
      }
    },
  })
}

function doUndoCheckout() {
  Modal.confirm({
    title: `确认取消检出 "${props.record.code || props.record.name}"？`,
    content: '取消检出将丢弃本次检出后的所有修改，恢复为检出前的版本。',
    okText: '确认取消检出',
    cancelText: '保留检出',
    okType: 'danger',
    onOk: async () => {
      try {
        const res = await api.value.undoCheckout(props.record.oid)
        if (res?.code === 200) done('已取消检出')
        else message.error(res?.message || '取消检出失败')
      } catch (e) {
        message.error('取消检出失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
      }
    },
  })
}

// ==================== 下载主文件 ====================

async function doDownload() {
  try {
    const res = await getDocumentDownloadUrl(props.record.oid)
    const url = res?.data?.url || res?.url
    if (url) window.open(url, '_blank')
    else message.warning('未获取到下载地址')
  } catch {
    message.error('获取下载地址失败')
  }
}

// ==================== 移动 / 变更分类 ====================

const moveVisible = ref(false)
const moveLoading = ref(false)
const moveTargetOid = ref(undefined)
const categoryTree = ref([])

async function openMove() {
  moveTargetOid.value = undefined
  moveVisible.value = true
  // 分类模式：按需加载元器件分类树
  if (props.moveMode === 'category' && !categoryTree.value.length) {
    moveLoading.value = true
    try {
      const res = await getLibraryCategoryTree()
      const tree = res?.code === 200 ? (res.data || null) : null
      categoryTree.value = tree ? [toTreeOption(tree)] : []
    } catch {
      categoryTree.value = []
    } finally {
      moveLoading.value = false
    }
  }
}

function toTreeOption(n) {
  return {
    value: n.oid,
    title: n.displayName || n.name,
    children: (n.children || []).map(toTreeOption),
  }
}

const moveTree = computed(() =>
  props.moveMode === 'category' ? categoryTree.value : props.moveTreeData
)

async function confirmMove() {
  if (!moveTargetOid.value) { message.warning('请选择目标'); return }
  saving.value = true
  try {
    const res = props.moveMode === 'category'
      ? await api.value.update(props.record.oid, { clsOid: moveTargetOid.value })
      : await api.value.move(props.record.oid, { folderOid: moveTargetOid.value })
    if (res?.code === 200) { moveVisible.value = false; done('操作成功') }
    else message.error(res?.message || '操作失败')
  } catch (e) {
    message.error('操作失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    saving.value = false
  }
}

// ==================== 编辑 ====================

const editVisible = ref(false)
const editForm = ref({})
const editFormRef = ref(null)

function openEdit() {
  editForm.value = {}
  editVisible.value = true
}

async function confirmEdit() {
  saving.value = true
  try {
    const res = await api.value.update(props.record.oid, { ...(editForm.value || {}) })
    if (res?.code === 200) { editVisible.value = false; done('保存成功') }
    else message.error(res?.message || '保存失败')
  } catch (e) {
    message.error('保存失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    saving.value = false
  }
}

// ==================== 新建视图版本 ====================

function doNewViewVersion() {
  Modal.confirm({
    title: `确认新建视图版本 "${props.record.code || props.record.name}"？`,
    content: '将基于当前最新版本创建一个新的大版本（版本号递增，小版本重置为 1）。',
    okText: '确认新建',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await api.value.newViewVersion(props.record.oid)
        if (res?.code === 200) done('已新建视图版本')
        else message.error(res?.message || '新建视图版本失败')
      } catch (e) {
        message.error('新建视图版本失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
      }
    },
  })
}

// ==================== 删除 ====================

const deleteVisible = ref(false)
const deleteScope = ref('all')

function openDelete() {
  deleteScope.value = 'all'
  deleteVisible.value = true
}

async function confirmDelete() {
  saving.value = true
  try {
    const isLatest = deleteScope.value === 'latest'
    const res = isLatest
      ? await api.value.removeLatest(props.record.oid)
      : await api.value.remove(props.record.oid)
    if (res?.code === 200) {
      deleteVisible.value = false
      done(isLatest ? '已删除最新小版本' : '已删除')
    } else {
      message.error(res?.message || '删除失败')
    }
  } catch (e) {
    message.error('删除失败: ' + (e?.response?.data?.message || e?.message || '网络错误'))
  } finally {
    saving.value = false
  }
}
</script>
