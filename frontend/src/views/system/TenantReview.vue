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
      <a-tabs v-model:activeKey="activeTab">
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

        <a-tab-pane key="done" tab="已处理">
          <a-empty description="历史记录功能开发中" />
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
import { getPendingTenants, approveTenant, rejectTenant } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/** 是否为平台管理员 */
const isPlatformAdmin = computed(() => {
  return userStore.roles && userStore.roles.includes('PLATFORM_ADMIN')
})

const activeTab = ref('pending')
const loading = ref(false)
const pendingList = ref([])

const columns = [
  { title: '租户标识', dataIndex: 'tenantId', key: 'tenantId' },
  { title: '公司名称', dataIndex: 'name', key: 'name' },
  { title: '联系人', dataIndex: 'contactName', key: 'contactName' },
  { title: '邮箱', dataIndex: 'contactEmail', key: 'contactEmail' },
  { title: '管理员', dataIndex: 'adminUsername', key: 'adminUsername' },
  { title: '申请时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action', width: 200 }
]

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

const handleApprove = async (record) => {
  try {
    const res = await approveTenant(record.oid)
    if (res.code === 200) {
      message.success(`租户「${record.name}」审核通过，管理员账号已创建`)
      await fetchPending()
    }
  } catch { /* handled */ }
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
    const res = await rejectTenant(rejectModal.record.oid, rejectModal.reason)
    if (res.code === 200) {
      message.success('已驳回')
      rejectModal.visible = false
      await fetchPending()
    }
  } catch { /* handled */ } finally {
    rejectModal.loading = false
  }
}

onMounted(() => {
  if (isPlatformAdmin.value) {
    fetchPending()
  }
})
</script>
