<template>
  <div class="tenant-review">
    <a-page-header title="租户审核" sub-title="审核新租户注册申请" />

    <!-- 非平台管理员：无权限提示 -->
    <a-result
      v-if="!isPlatformAdmin"
      status="403"
      title="无访问权限"
      sub-title="仅平台管理员可审核租户注册申请"
    >
      <template #extra>
        <a-button type="primary" @click="$router.push('/home')">返回首页</a-button>
      </template>
    </a-result>

    <template v-else>
    <a-card :bordered="false" style="margin-top: 16px">
      <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
        <a-tab-pane key="pending" :tab="'待审核 (' + pendingList.length + ')'">
          <a-table
            :columns="columns"
            :data-source="pendingList"
            :loading="loading"
            :pagination="false"
            row-key="oid"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag color="orange">待审核</a-tag>
              </template>
              <template v-if="column.key === 'action'">
                <a-space>
                  <a-popconfirm
                    title="确认通过该租户的注册申请？"
                    ok-text="通过"
                    cancel-text="取消"
                    @confirm="handleApprove(record)"
                  >
                    <a-button type="primary" size="small">
                      <check-outlined /> 通过
                    </a-button>
                  </a-popconfirm>
                  <a-button danger size="small" @click="showRejectModal(record)">
                    <close-outlined /> 驳回
                  </a-button>
                </a-space>
              </template>
            </template>
          </a-table>
          <a-empty v-if="!loading && pendingList.length === 0" description="暂无待审核租户" />
        </a-tab-pane>

        <a-tab-pane key="done" :tab="'已处理 (' + reviewedList.length + ')'">
          <a-table
            :columns="reviewedColumns"
            :data-source="reviewedList"
            :loading="reviewedLoading"
            :pagination="false"
            row-key="oid"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'result'">
                <a-tag :color="REVIEW_RESULT_COLOR[record.status] || 'default'">
                  {{ REVIEW_RESULT_LABEL[record.status] || record.status }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'approvedAt'">
                {{ formatAt(record.approvedAt) }}
              </template>
              <template v-else-if="column.key === 'rejectReason'">
                <span :class="{ 'tenant-review__muted': !record.rejectReason }">
                  {{ record.rejectReason || '—' }}
                </span>
              </template>
            </template>
          </a-table>
          <a-empty
            v-if="!reviewedLoading && reviewedList.length === 0"
            description="暂无已处理记录"
          />
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 驳回弹窗 -->
    <a-modal
      v-model:open="rejectModal.visible"
      title="驳回申请"
      @ok="handleReject"
      :confirmLoading="rejectModal.loading"
    >
      <a-form layout="vertical">
        <a-form-item label="驳回原因">
          <a-textarea
            v-model:value="rejectModal.reason"
            placeholder="请填写驳回原因"
            :rows="3"
          />
        </a-form-item>
      </a-form>
    </a-modal>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { CheckOutlined, CloseOutlined } from '@ant-design/icons-vue'
import { getPendingTenants, getReviewedTenants, approveTenant, rejectTenant } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/** 是否为平台管理员 */
const isPlatformAdmin = computed(() => {
  return userStore.roles && userStore.roles.includes('PLATFORM_ADMIN')
})

const activeTab = ref('pending')
const loading = ref(false)
const pendingList = ref([])

/** 已处理台账：通过 / 驳回 / 通过后又被停用、禁用的 */
const reviewedList = ref([])
const reviewedLoading = ref(false)
/** 是否已经拉过（切页签时才查，审核动作后置回 false 重拉） */
const reviewedLoaded = ref(false)

/** 审核结果标签：PENDING 不会出现在这里 */
const REVIEW_RESULT_LABEL = {
  ACTIVE: '已通过',
  REJECTED: '已驳回',
  SUSPENDED: '已停用',
  DISABLED: '已禁用'
}
const REVIEW_RESULT_COLOR = {
  ACTIVE: 'green',
  REJECTED: 'red',
  SUSPENDED: 'orange',
  DISABLED: 'default'
}

const columns = [
  { title: '租户标识', dataIndex: 'tenantId', key: 'tenantId' },
  { title: '公司名称', dataIndex: 'name', key: 'name' },
  { title: '联系人', dataIndex: 'contactName', key: 'contactName' },
  { title: '联系电话', dataIndex: 'contactPhone', key: 'contactPhone' },
  { title: '邮箱', dataIndex: 'contactEmail', key: 'contactEmail' },
  { title: '管理员', dataIndex: 'adminUsername', key: 'adminUsername' },
  { title: '申请时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action', width: 200 }
]

/**
 * 已处理列表的列。
 *
 * <p>与待审核列表的差别在最后四列：审核结果是**已发生的事实**，
 * 要能答出"谁批的 / 什么时候批的 / 为什么驳回"，这是台账的全部意义。
 */
const reviewedColumns = [
  { title: '租户标识', dataIndex: 'tenantId', key: 'tenantId' },
  { title: '公司名称', dataIndex: 'name', key: 'name' },
  { title: '联系人', dataIndex: 'contactName', key: 'contactName' },
  { title: '管理员', dataIndex: 'adminUsername', key: 'adminUsername' },
  { title: '审核结果', key: 'result', width: 100 },
  { title: '审核人', dataIndex: 'approvedBy', key: 'approvedBy', width: 110 },
  { title: '审核时间', key: 'approvedAt', width: 150 },
  { title: '驳回原因', key: 'rejectReason' }
]

/** 后端返回 ISO（2026-11-09T13:20:16），台账里只要到分钟 */
function formatAt(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

const rejectModal = reactive({
  visible: false,
  reason: '',
  record: null,
  loading: false
})

const fetchPending = async () => {
  loading.value = true
  try {
    const res = await getPendingTenants()
    if (res.code === 200) {
      pendingList.value = res.data || []
    }
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

/** 已处理台账（审核结果） */
const fetchReviewed = async () => {
  reviewedLoading.value = true
  try {
    const res = await getReviewedTenants()
    if (res.code === 200) {
      reviewedList.value = res.data || []
      reviewedLoaded.value = true
    }
  } catch {
    // handled by interceptor
  } finally {
    reviewedLoading.value = false
  }
}

/** 切到「已处理」才查一次：常用路径是待审核，不必每次进页面都多打一个请求 */
function onTabChange(key) {
  if (key === 'done' && !reviewedLoaded.value) fetchReviewed()
}

/**
 * 审核通过。
 *
 * <p>失败（400「租户不存在 / 不在待审核状态」）通常不是请求出错，而是<b>这行已经过期</b> ——
 * 申请被别处处理过，或测试数据被清理过。此时必须刷新列表，否则用户会对着一行早已不存在的
 * 记录反复点「通过」，每次只看到一句「租户不存在」。
 */
const handleApprove = async (record) => {
  try {
    const res = await approveTenant(record.oid, { skipErrorToast: true })
    if (res.code === 200) {
      message.success(`租户「${record.name}」审核通过，管理员账号已创建`)
    } else {
      message.warning(`${res.message || '审核失败'}，列表已刷新`)
    }
  } catch {
    message.error('审核请求失败，请稍后重试')
  } finally {
    // 台账变了：下次切到「已处理」重拉
    reviewedLoaded.value = false
    await fetchPending()
  }
}

const showRejectModal = (record) => {
  rejectModal.record = record
  rejectModal.reason = ''
  rejectModal.visible = true
}

const handleReject = async () => {
  if (!rejectModal.reason.trim()) {
    message.warning('请填写驳回原因')
    return
  }
  rejectModal.loading = true
  try {
    const res = await rejectTenant(rejectModal.record.oid, rejectModal.reason, { skipErrorToast: true })
    if (res.code === 200) {
      message.success('已驳回')
    } else {
      message.warning(`${res.message || '驳回失败'}，列表已刷新`)
    }
    rejectModal.visible = false
  } catch {
    message.error('驳回请求失败，请稍后重试')
    rejectModal.visible = false
  } finally {
    rejectModal.loading = false
    reviewedLoaded.value = false
    await fetchPending()
  }
}

onMounted(() => {
  if (isPlatformAdmin.value) {
    fetchPending()
  }
})
</script>

<style scoped>
.tenant-review__muted {
  color: #bfbfbf;
}
</style>
