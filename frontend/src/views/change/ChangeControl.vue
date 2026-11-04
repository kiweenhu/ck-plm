<template>
  <div class="chg-page">
    <div class="chg-head">
      <div class="chg-title">
        <h2>变更控制</h2>
        <span class="chg-sub">{{ requestEnabled ? '变更请求（ECR）→ 变更通告（ECN）→ 变更任务' : '变更通告（ECN）→ 变更任务' }}</span>
      </div>
      <div class="chg-head-right">
        <!--
          状态概览（「全部」+ 各状态条数），点一下按它过滤、再点取消。放在页头而不是表格工具栏里 ——
          它回答的是"这一页现在有多少单"。三个页签各有一套，口径来自各自的 stats 接口。
        -->
        <div class="chg-head-stats">
          <span
            v-for="pill in STAT_PILLS[activeKey]"
            :key="pill.key"
            class="chg-head-pill"
            :class="{ 'chg-head-pill--active': isStatusActive(pill) }"
            @click="toggleStatus(pill)"
          >
            <b class="chg-head-pill-num">{{ statusCount(pill) }}</b>
            <span class="chg-head-pill-label">{{ pill.label }}</span>
          </span>
        </div>
        <a-button v-if="isTenantAdmin" size="small" @click="openConfig">
          <template #icon><SettingOutlined /></template>
          变更设置
        </a-button>
      </div>
    </div>

    <!-- size="small"：与产品页（物料清单 / 研发资料）的页签同一档字号，切换页面不"变大变小" -->
    <a-tabs
      v-model:activeKey="activeKey"
      size="small"
      class="chg-tabs"
      :destroy-inactive-tab-pane="false"
      @change="onTabChange"
    >
      <!-- 「是否启用变更请求」由「变更设置」控制：只用 ECN 驱动改动的租户可以彻底不显示这一页 -->
      <a-tab-pane v-if="requestEnabled" key="request" tab="变更请求">
        <ChangeRequestPanel
          v-if="loaded.request"
          v-model:status="tabStatus.request"
          @changed="loadStats('request')"
        />
      </a-tab-pane>
      <a-tab-pane key="notice" tab="变更通告">
        <!-- 状态筛选由页头顶部的概览标签控制；列表一变（增删改 / 换页 / 换筛选）就回 changed 重取计数 -->
        <ChangeNoticePanel
          :key="configVersion"
          v-if="loaded.notice"
          v-model:status="tabStatus.notice"
          @changed="loadStats('notice')"
        />
      </a-tab-pane>
      <a-tab-pane key="task" tab="我的任务">
        <ChangeTaskPanel
          v-if="loaded.task"
          v-model:status="tabStatus.task"
          v-model:only-mine="taskOnlyMine"
          @changed="loadStats('task')"
        />
      </a-tab-pane>
    </a-tabs>

    <a-modal
      v-model:open="config.visible"
      title="变更设置"
      width="560px"
      centered
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="config.saving"
      @ok="saveConfig"
    >
      <a-spin :spinning="config.loading">
        <a-form layout="vertical">
          <a-form-item label="启用变更请求（ECR）">
            <a-switch v-model:checked="config.form.requestEnabled" />
            <div class="chg-hint">
              关闭后「变更控制」不再出现「变更请求」页签 —— 直接提变更通告即可。
              存量变更请求不会被删除，重新打开即恢复入口。默认开启。
            </div>
          </a-form-item>
          <a-form-item label="必须审批">
            <a-switch v-model:checked="config.form.approvalRequired" />
            <div class="chg-hint">
              关闭后即为「免审」：提交直接通过，不再走审批流程。每次免审都会记入审计（原因必填）。
              默认开启。
            </div>
          </a-form-item>
          <a-form-item label="免审时允许修订已发布对象">
            <a-switch v-model:checked="config.form.bypassForReleased" :disabled="config.form.approvalRequired" />
            <div class="chg-hint">
              默认关闭：免审是为了少一道流程，不是为了让已发布版本可以被无声改动。
              该限制随 M2 的处置执行一起生效。
            </div>
          </a-form-item>
          <a-form-item label="变更请求（ECR）流程">
            <a-select
              v-model:value="config.form.requestProcessKey"
              :options="processOptionsFor(config.form.requestProcessKey)"
              show-search
              option-filter-prop="label"
              placeholder="选择流程模板"
            />
          </a-form-item>
          <a-form-item label="变更通告（ECN）审批流程">
            <a-select
              v-model:value="config.form.approvalProcessKey"
              :options="processOptionsFor(config.form.approvalProcessKey)"
              show-search
              option-filter-prop="label"
              placeholder="选择流程模板"
            />
          </a-form-item>
          <a-form-item label="变更任务（ECT）流程">
            <a-select
              v-model:value="config.form.taskProcessKey"
              :options="processOptionsFor(config.form.taskProcessKey)"
              show-search
              option-filter-prop="label"
              placeholder="选择流程模板"
            />
            <div class="chg-hint">
              候选来自本租户的流程清单（流程清单页 / 设计器里可改）。流程随 platform-base
              包装载；自己新建或改过流程后，重新打开本弹窗即可选到。<br />
              内置流程是一条父子链：change-request 的「派发变更通告」按清单拉起变更通告流程，
              变更通告的「派发变更任务」再拉起 change-task（多实例，父单等子流程全部结束才继续）——
              所以跑通顶层入口，子流程会自动起，不必逐个手动发起。<br />
              变更通告（ECN）审批流程按<b>企业模式二选一</b>：启用变更请求的企业选
              <code>change-notice-derived</code>（执行方案评审 + 合规确认，两级 —— 影响面已由 ECR 审过）；
              只用变更通告的企业选 <code>change-notice</code>（影响面评审 + 变更审批 + 变更管理员终审，三级）。
              两者节点不同，选错会审多或审少。
            </div>
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SettingOutlined } from '@ant-design/icons-vue'
import ChangeRequestPanel from './ChangeRequestPanel.vue'
import ChangeNoticePanel from './ChangeNoticePanel.vue'
import ChangeTaskPanel from './ChangeTaskPanel.vue'
import {
  getChangeConfig,
  getChangeNoticeStats,
  getChangeRequestStats,
  getChangeTaskStats,
  listProcessTemplates,
  updateChangeConfig
} from '@/api'
import {
  CHANGE_NOTICE_STATUS_LABEL,
  CHANGE_REQUEST_STATUS_LABEL,
  CHANGE_TASK_STATUS_LABEL
} from '@/utils/changeFlow'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/**
 * 「变更设置」入口只对租户管理员露出。
 *
 * <p>里面是**租户级策略**（要不要审批、免审能否改已发布对象），改它等于替全租户做主。
 * 服务端 PUT 有同样的校验（ChangeConfigController#isTenantAdmin），这里只管别把入口摆给
 * 没权限的人 —— 让他点进去再吃 403 是更差的体验。口径与 MainLayout 一致。
 */
const isTenantAdmin = computed(() => (userStore.roles || [])
  .some(role => ['TENANT_ADMIN', 'ADMIN', 'PLATFORM_ADMIN'].includes(role)))

const route = useRoute()
const router = useRouter()

/**
 * 页签可由路由指定（`?tab=request|notice|task`）——
 * 对象详情页的「变更情况」跳到通告详情时会带上 `tab=notice&noticeOid=…`，
 * 不认这个参数就会停在默认页签上、把人晾在列表里。
 */
const TAB_KEYS = ['request', 'notice', 'task']
/** 默认页签：变更通告是主战场（也是地址栏没有 tab 时的落点） */
const DEFAULT_TAB = 'notice'
const initialTab = TAB_KEYS.includes(String(route.query.tab || '')) ? String(route.query.tab) : DEFAULT_TAB

const activeKey = ref(initialTab)

/**
 * 用户点页签时把页签写回 URL —— 否则 F5 只会重建到默认页签「变更通告」
 * （用户 2026-11-03：*"在变更控制导航栏，F5刷新就是这个页面"*，抱怨的就是这个）。
 *
 * <p>为什么挂在 {@code @change} 上而不是 {@code watch(activeKey)}：
 * 路由 watcher（下方 `route.query.tab`）会先把 activeKey 改掉，若在那里也回写 URL，
 * 会把刚跳进来的 `?noticeOid=…` 一并洗掉 —— 详情抽屉就永远打不开了。
 *
 * <p>回写时**只保留 tab**，故意丢掉 `noticeOid` / `requestOid`：它们是一次性指令
 * （"打开这张单"），留在地址栏里会让下次刷新在「我的任务」页签上莫名弹出一个抽屉。
 */
function onTabChange(key) {
  if (String(route.query.tab || '') === key) return
  router.replace({ query: { tab: key } })
}

/** 变更设置保存后自增：作为通告面板的 key，强制重建以重新读取免审开关 */
const configVersion = ref(0)

/**
 * 三个页签懒挂载：首次切换才创建组件。
 * 变更通告是主战场（默认页签），请求与任务按需加载，避免一进页面就打三组接口。
 */
const loaded = reactive({
  request: initialTab === 'request',
  notice: initialTab === 'notice',
  task: initialTab === 'task'
})

watch(activeKey, (key) => {
  if (key === 'request') loaded.request = true
  if (key === 'notice') loaded.notice = true
  if (key === 'task') loaded.task = true
})

/**
 * 是否启用变更请求（租户级开关，配在「变更设置」里）。
 *
 * <p>关闭后连页签都不渲染 —— 只用 ECN 驱动改动的团队不必看着一个永远没单的页签。
 * 读不到配置时按"开启"处理：多一个空页签，好过少一个能用的入口。
 */
const requestEnabled = ref(true)

async function loadRequestEnabled() {
  try {
    const res = await getChangeConfig()
    if (res.code === 200 && res.data) requestEnabled.value = res.data.requestEnabled !== false
  } catch {
    /* 读失败按开启处理 */
  }
  if (!requestEnabled.value) {
    loaded.request = false
    if (activeKey.value === 'request') activeKey.value = 'notice'
  }
}

onMounted(loadRequestEnabled)

/**
 * 页签跟着路由走：全局搜索命中变更单后跳 `?tab=request&requestOid=…`，
 * 此时若已经停在变更控制页，组件不会重建 —— 只认 initialTab 就会停在旧页签上，
 * 用户看到的是"搜到了却打不开"。
 */
watch(() => route.query.tab, (tab) => {
  const key = String(tab || '')
  // 地址栏没有 tab = 从左侧「变更控制」重新进来（菜单指向 /change）：回到默认页签，
  // 否则会出现"URL 是干净的、界面还停在「我的任务」"这种一半状态
  if (!key) {
    activeKey.value = DEFAULT_TAB
    return
  }
  // 变更请求已停用时，`?tab=request`（全局搜索命中 ECR）不该把人跳到一个不存在的页签
  if (key === 'request' && !requestEnabled.value) return
  if (TAB_KEYS.includes(key) && key !== activeKey.value) activeKey.value = key
})

/**
 * 三个页签各自的「状态概览」：全部 + 状态机里定义过的每个状态（没单的也显示 0）。
 *
 * <p>位置在**页头**（用户 2026-11-01：*"统计信息没有移动到顶部"*、*"变更请求没有统计信息"*）：
 * 这些数字回答的是"这一页现在有多少单"，钉在表格工具栏上会随页面滚走。点一下按该状态过滤、
 * 再点取消，「全部」= 清空过滤。口径直接取前端状态机镜像的标签表 —— 加状态时这里不用跟着改。
 *
 * <p>为什么状态与计数都在父级：页头是父级的 DOM，子组件 Teleport 上去在首屏挂载时
 * 目标节点还没上文档（整棵树一次性挂载），会直接报 "Failed to locate Teleport target"。
 */
function buildPills(labels) {
  return [
    { key: 'total', label: '全部', status: null },
    ...Object.entries(labels).map(([status, label]) => ({ key: status, label, status }))
  ]
}

const STAT_PILLS = {
  request: buildPills(CHANGE_REQUEST_STATUS_LABEL),
  notice: buildPills(CHANGE_NOTICE_STATUS_LABEL),
  task: buildPills(CHANGE_TASK_STATUS_LABEL)
}

/** 每个页签当前选中的状态（undefined = 全部），经 v-model 下发给对应面板 */
const tabStatus = reactive({ request: undefined, notice: undefined, task: undefined })

/** 「只看我的」只作用于任务页签；放在父级才能在切换时按同一口径重取计数 */
const taskOnlyMine = ref(true)

/** 每个页签的计数 —— { total, counts } */
const statsByTab = reactive({
  request: { total: 0, counts: {} },
  notice: { total: 0, counts: {} },
  task: { total: 0, counts: {} }
})

function statusCount(pill) {
  const stats = statsByTab[activeKey.value] || { total: 0, counts: {} }
  return pill.key === 'total' ? stats.total : (stats.counts[pill.key] || 0)
}

function isStatusActive(pill) {
  return (tabStatus[activeKey.value] || null) === (pill.status || null)
}

function toggleStatus(pill) {
  const tab = activeKey.value
  const target = pill.status || null
  if (isStatusActive(pill)) {
    if (!target) return                 // 已在「全部」上，不必再取一次
    tabStatus[tab] = undefined
  } else {
    tabStatus[tab] = target || undefined
  }
}

/** 按页签取计数：请求 / 通告各一条 stats 接口；任务要带上「只看我的」口径 */
async function loadStats(tab) {
  const key = tab || activeKey.value
  try {
    let res
    if (key === 'request') {
      res = await getChangeRequestStats()
    } else if (key === 'task') {
      res = await getChangeTaskStats(taskOnlyMine.value ? { assignee: userStore.username } : {})
    } else {
      res = await getChangeNoticeStats()
    }
    if (res.code === 200 && res.data) {
      statsByTab[key].total = res.data.total || 0
      statsByTab[key].counts = res.data.counts || {}
    }
  } catch {
    /* 计数失败不影响列表 */
  }
}

// 切到已挂载过的页签时重取一次计数（首次进入由面板列表加载完回 changed 触发；
// 「只看我的」变化同样走 changed —— 面板重取列表后父级再取计数，口径自然一致）
watch(activeKey, (key) => {
  if (loaded[key]) loadStats(key)
})

const config = reactive({
  visible: false,
  loading: false,
  saving: false,
  form: {
    requestEnabled: true,
    approvalRequired: true,
    bypassForReleased: false,
    requestProcessKey: 'change-request',
    approvalProcessKey: 'change-notice',
    taskProcessKey: 'change-task'
  }
})

/**
 * 流程模板下拉候选 —— 来自流程清单（本租户已装/已建的模板）。
 *
 * <p>为什么是下拉而不是手填 key：手填错一个字符设计期毫无感知，到「发起审批」才炸
 * （那时才抛「发起审批流程失败（key=xxx）」）。label 里带上 key，因为配置存的是 key 而非显示名。
 *
 * <p>拉不到清单时下拉为空，但不会清掉已保存的值（见 processOptionsFor 的补位）。
 */
const processOptions = ref([])

async function loadProcessOptions() {
  try {
    const res = await listProcessTemplates({})
    if (res.code !== 200) return
    processOptions.value = (res.data || []).map((t) => ({
      value: t.key,
      label: `${t.displayName || t.name || t.key}（${t.key}）${t.deployedVersion ? '' : ' · 未部署'}`
    }))
  } catch {
    /* 清单拉不到就留空：配置项仍显示原值，保存不受影响 */
  }
}

/**
 * 给某个 key 补一条「清单里没有」的选项。
 *
 * <p>存量配置可能是手填时代留下的，或流程已被删除（如 change-notice-approval）。
 * 不补的话 a-select 会显示一个裸 key 甚至空白，看不出「它已经不存在了」。
 */
function processOptionsFor(current) {
  const options = processOptions.value
  if (!current || options.some((option) => option.value === current)) {
    return options
  }
  return [{ value: current, label: `${current} · 流程清单中不存在` }, ...options]
}

async function openConfig() {
  config.visible = true
  config.loading = true
  loadProcessOptions()   // 与配置读取并行：选项晚到不影响弹窗打开
  try {
    const res = await getChangeConfig()
    if (res.code === 200 && res.data) {
      config.form = {
        requestEnabled: res.data.requestEnabled !== false,
        approvalRequired: res.data.approvalRequired !== false,
        bypassForReleased: res.data.bypassForReleased === true,
        requestProcessKey: res.data.requestProcessKey || 'change-request',
        approvalProcessKey: res.data.approvalProcessKey || 'change-notice',
        taskProcessKey: res.data.taskProcessKey || 'change-task'
      }
    }
  } catch {
    message.error('读取变更设置失败')
  } finally {
    config.loading = false
  }
}

async function saveConfig() {
  config.saving = true
  try {
    const res = await updateChangeConfig(config.form)
    if (res.code === 200) {
      message.success('设置已保存')
      config.visible = false
      configVersion.value += 1
      // 开关变化立刻反映到页签上：关掉时若正停在变更请求，退回变更通告
      requestEnabled.value = config.form.requestEnabled !== false
      if (!requestEnabled.value && activeKey.value === 'request') activeKey.value = 'notice'
    } else {
      message.error(res.message || '保存失败')
    }
  } catch (e) {
    message.error(e?.message || '保存失败')
  } finally {
    config.saving = false
  }
}

</script>

<style scoped>
/*
  页面自身不再叠左右内边距（用户 2026-11-01：*"红色框的宽度对齐后面的截图"*）——
  外层布局已给左右各 24px，产品页（.pl-dashboard padding: 0）就是靠这一层定宽的；
  这里原来又加了 20px，页头卡片与表格比产品页窄了 40px。顶部仍只补 2px（+ 布局 16px = 18px）。
*/
.chg-page { padding: 2px 0 16px; display: flex; flex-direction: column; height: 100%; }
/*
  页头与产品页页头（ProductLineDashboard 的 .pl-header）**同一套视觉**（用户 2026-11-01：
  *"红色框的样式对齐"*）：白底 + 1px 边框 + 8px 圆角 + 一层轻阴影 + 顶部 3px 品牌渐变条
  （渐变条靠 ::before 画，故容器要 relative + overflow:hidden，否则会盖住圆角）。
  版式也照抄：标题在左、统计与动作在右，内边距 12px 20px 10px、下外边距 12px。
*/
.chg-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  padding: 12px 20px 10px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, .04);
  position: relative;
  overflow: hidden;
}
.chg-head::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: linear-gradient(90deg, #1677ff, #722ed1, #eb2f96);
}
.chg-title { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.chg-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -.3px;
  color: #1a1a2e;
}
.chg-sub { color: #8c8c8c; font-size: 12px; }
.chg-head-right { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; justify-content: flex-end; }

/* 状态概览：一排可点的「条数 + 状态」胶囊，形状照产品页的 .pl-stat-pill（20px 圆角、浅底、细边），
   既是统计也是筛选：点一下按该状态过滤，再点取消 */
.chg-head-stats { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }

.chg-head-pill {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 20px;
  border: 1px solid #f0f0f0;
  background: #fafbfc;
  cursor: pointer;
  user-select: none;
  transition: all .2s;
}
.chg-head-pill:hover { border-color: #91caff; }
.chg-head-pill-num { font-size: 14px; font-weight: 700; color: #1a1a2e; line-height: 1.2; }
.chg-head-pill-label { font-size: 12px; color: #8c8c8c; }
.chg-head-pill--active { background: #e6f4ff; border-color: #91caff; }
.chg-head-pill--active .chg-head-pill-num,
.chg-head-pill--active .chg-head-pill-label { color: #1677ff; }
.chg-tabs { flex: 1; min-height: 0; }
.chg-tabs :deep(.ant-tabs-content-holder) { min-height: 0; }
.chg-tabs :deep(.ant-tabs-content) { height: 100%; }
.chg-hint { color: #8c8c8c; font-size: 12px; margin-top: 6px; line-height: 1.6; }
</style>
