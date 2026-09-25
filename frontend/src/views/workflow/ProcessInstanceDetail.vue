<template>
  <div class="pid-page">
    <!-- 页头：从流程监控点进来，返回入口就在最左边（独立页面必须有明确出口） -->
    <div class="pid-head">
      <a-button type="text" class="pid-back" @click="goMonitor">
        <template #icon><ArrowLeftOutlined /></template>
        流程监控
      </a-button>
      <a-divider type="vertical" />
      <h2 class="pid-title">{{ detail?.processDefinitionName || '流程详情' }}</h2>
      <a-tag v-if="detail" :color="statusColor(detail.status)">{{ statusText(detail.status) }}</a-tag>
      <a-tag v-if="detail?.processDefinitionKey" color="blue">{{ detail.processDefinitionKey }}</a-tag>
    </div>

    <a-spin :spinning="loading">
      <a-result v-if="notFound" status="info" title="看不到这条流程实例" :sub-title="notFoundMessage">
        <template #extra>
          <a-space>
            <a-button type="primary" @click="goMonitor">返回流程监控</a-button>
            <a-button @click="reload">刷新看看</a-button>
          </a-space>
        </template>
      </a-result>

      <template v-else-if="detail">
        <a-card size="small" class="pid-card" title="实例信息">
          <a-descriptions :column="2" size="small" :label-style="labelStyle">
            <!-- 业务标识与列表同一份渲染（components/EntityRefLabel）：
                 名称可点进该大版本的最新版本，编码/版本/生命周期状态一并给出 -->
            <a-descriptions-item label="业务标识" :span="2">
              <EntityRefLabel v-if="entityLabel" :label="entityLabel" />
              <span v-else class="pid-muted">该流程未关联业务对象</span>
            </a-descriptions-item>
            <a-descriptions-item label="流程名称">{{ detail.processDefinitionName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="流程 Key">{{ detail.processDefinitionKey || '—' }}</a-descriptions-item>
            <a-descriptions-item label="发起人">{{ detail.startUserId || '—' }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusColor(detail.status)">{{ statusText(detail.status) }}</a-tag>
            </a-descriptions-item>
            <!-- 运行中才谈得上"卡在哪、等谁办"；已结束的这两项是空 -->
            <a-descriptions-item v-if="isRunning" label="当前节点">
              {{ detail.currentActivityName || '—' }}
            </a-descriptions-item>
            <a-descriptions-item v-if="isRunning" label="当前办理人">
              {{ detail.currentAssignees || '未指派' }}
            </a-descriptions-item>
            <a-descriptions-item label="开始时间">{{ fmtTime(detail.startTime) }}</a-descriptions-item>
            <a-descriptions-item label="结束时间">{{ detail.endTime ? fmtTime(detail.endTime) : '—' }}</a-descriptions-item>
            <a-descriptions-item v-if="detail.deleteReason" label="终止原因" :span="2">
              {{ detail.deleteReason }}
            </a-descriptions-item>
            <a-descriptions-item label="实例 ID" :span="2">
              <span class="pid-mono">{{ detail.id }}</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card size="small" class="pid-card" title="流程进度">
          <!-- 节点可点开：看这一步后台执行了什么、报了什么错（自动服务节点尤其需要） -->
          <ProcessActivityTimeline
            :activities="activities"
            :loading="activitiesLoading"
            clickable
            @select="openNodeDetail"
          />
        </a-card>

        <a-card size="small" class="pid-card" title="流程变量">
          <a-empty v-if="!variableRows.length" :image-style="{ height: '32px' }" description="无流程变量" />
          <a-table v-else :columns="variableColumns" :data-source="variableRows" size="small"
            row-key="name" :pagination="false" />
        </a-card>
      </template>
    </a-spin>

    <!-- 节点执行详情：点开流程进度里的某个节点（自动服务跑了什么、报了什么错） -->
    <ProcessNodeDetailDrawer
      v-model:open="nodeDrawerOpen"
      :activity="nodeActivity"
      :instance-id="instanceId"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { getProcessInstanceDetail, getInstanceActivities } from '@/api'
import ProcessActivityTimeline from '@/components/ProcessActivityTimeline.vue'
import ProcessNodeDetailDrawer from '@/components/ProcessNodeDetailDrawer.vue'
import EntityRefLabel from '@/components/EntityRefLabel.vue'
import { useEntityInfo } from '@/composables/useEntityInfo'
import { buildEntityLabel } from '@/utils/entityRef'

/**
 * 流程实例详情 —— 独立页面（原先是从监控列表弹的模态框）。
 *
 * <p><b>为什么不要弹框</b>：详情里有三块要对着看的内容（实例信息 / 流程进度 / 流程变量），
 * 进度动辄十几个节点、变量几十行，弹框里只能靠内部滚动看一小条；
 * 而且监控列表是常开着对照的，弹框一关就回到起点。
 *
 * <p><b>数据口径与列表完全一致</b>：业务标识用同一份 {@code utils/entityRef.buildEntityLabel}
 * 与同一个 {@code EntityRefLabel} 组件，实体信息也用同一个 {@code useEntityInfo} 回查 ——
 * 详情页与列表显示同一行字却不一样，是最容易发生也最难被发现的漂移。
 */
const route = useRoute()
const router = useRouter()

const instanceId = computed(() => String(route.params.id || ''))
const loading = ref(false)
const notFound = ref(false)
const notFoundMessage = ref('')
const detail = ref(null)
const activities = ref([])
const activitiesLoading = ref(false)

const labelStyle = { width: '96px', color: '#8c8c8c' }
const variableColumns = [
  { title: '变量名', dataIndex: 'name', key: 'name', width: 280 },
  { title: '值', dataIndex: 'value', key: 'value' },
]

const isRunning = computed(() => detail.value?.status === 'running')

/** 实体展示信息：与监控列表同一份回查（后端只给引用，编码/名称/状态由前端回查） */
const { infos: entityInfos } = useEntityInfo(computed(() => detail.value))
const entityLabel = computed(() => {
  const ref = detail.value?.entities?.[0]
  return buildEntityLabel(ref, entityInfos.value[ref?.entityOid])
})

/** 流程变量按变量名排序：多次打开顺序一致，便于对照 */
const variableRows = computed(() => {
  const vars = detail.value?.variables || {}
  return Object.keys(vars).sort().map((name) => ({ name, value: displayValue(vars[name]) }))
})

function displayValue(value) {
  if (value === null || value === undefined) return '—'
  // 数组/对象（如 ckplmSetupAssignees_<活动id> 的人员 oid 列表）照原样给 JSON，不做美化
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

function statusText(status) {
  return ({ running: '运行中', suspended: '已挂起', completed: '已完成', terminated: '已终止' })[status] || status || '—'
}

function statusColor(status) {
  return ({ running: 'blue', suspended: 'orange', completed: 'green', terminated: 'red' })[status] || 'default'
}

function fmtTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('zh-CN', { hour12: false })
}

async function reload() {
  notFound.value = false
  loading.value = true
  try {
    const res = await getProcessInstanceDetail(instanceId.value)
    if (res?.code === 200 && res.data) {
      detail.value = res.data
      loadActivities()
    } else {
      detail.value = null
      notFound.value = true
      notFoundMessage.value = res?.message || '流程实例不存在或无权查看'
    }
  } catch (e) {
    detail.value = null
    notFound.value = true
    // 实例不在当前租户、或已被删除清理：都归到"看不到"，不显示后端堆栈
    notFoundMessage.value = '流程实例不存在或无权查看（可能已被删除，或不属于当前租户）'
  } finally {
    loading.value = false
  }
}

/** 进度单独取、单独降级：取不到只是少一块，不该把整页变成"看不到" */
async function loadActivities() {
  activitiesLoading.value = true
  try {
    const res = await getInstanceActivities(instanceId.value)
    activities.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    activities.value = []
  } finally {
    activitiesLoading.value = false
  }
}

function goMonitor() {
  router.push('/workflow/monitor')
}

/** 节点执行详情抽屉（点开流程进度里的节点） */
const nodeDrawerOpen = ref(false)
const nodeActivity = ref(null)

function openNodeDetail(activity) {
  nodeActivity.value = activity
  nodeDrawerOpen.value = true
}

onMounted(reload)
</script>

<style scoped>
.pid-page {
  padding: 0;
}

.pid-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin-bottom: 14px;
}

.pid-back {
  padding-left: 0;
  color: #595959;
}

.pid-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #262626;
}

.pid-card + .pid-card {
  margin-top: 14px;
}

.pid-muted {
  color: #8c8c8c;
}

.pid-mono {
  font-family: 'Consolas', 'Monaco', monospace;
  color: #595959;
}
</style>
