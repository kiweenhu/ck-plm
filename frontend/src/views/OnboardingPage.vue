<template>
  <div class="onboarding-page">
    <div class="onboarding-card">
      <!-- 加载中 -->
      <a-spin v-if="loading" tip="正在读取初始化状态..." style="width: 100%" />

      <!-- ① 非租户管理员：等待页（设计口径：普通成员看到拦截页） -->
      <a-result
        v-else-if="state && !state.isTenantAdmin && state.needsOnboarding"
        status="info"
        title="等待企业管理员完成行业初始化"
        :sub-title="state.waitingHint"
      >
        <template #icon><clock-circle-outlined /></template>
        <template #extra>
          <a-space>
            <a-button type="primary" @click="loadAll">刷新</a-button>
            <a-button @click="logout">退出登录</a-button>
          </a-space>
        </template>
      </a-result>

      <!-- ② 已完成：正常进系统 -->
      <a-result
        v-else-if="state && !state.needsOnboarding"
        status="success"
        :title="'行业初始化已完成：' + (state.industryCode || '—')"
        :sub-title="'研发方法论 ' + (state.devMethodCode || '—') + '（' + (state.industryPackCode || '—') + '）；由 ' + (state.onboardedBy || '—') + ' 于 ' + (state.onboardedAt || '—') + ' 完成；本租户已配置 ' + (state.tenantStageCount || 0) + ' 个阶段模板'"
      >
        <template #extra>
          <a-button type="primary" @click="$router.replace('/home')">进入系统</a-button>
        </template>
      </a-result>

      <!-- ③ 租户管理员：欢迎说明页 + 四步引导 -->
      <template v-else-if="state">
        <!--
          欢迎与说明（step < 0）—— 审核通过的管理员登录后先看到这一页。
          直接把新租户推进向导太突然：得先交代"为什么是我、要做什么、能不能反悔、同事会看到什么"，
          再由本人点「开始配置」进入向导。这一步不写任何数据。
        -->
        <div v-if="step < 0">
          <a-result
            status="success"
            :title="'企业审核已通过 —— ' + state.tenantName"
            sub-title="还剩最后一步：为这家企业选定行业与研发方法论，之后全体成员即可正常使用。"
          />
          <div style="margin: 8px 0 20px; color: #333; line-height: 2">
            <h3 style="margin: 0 0 6px; font-size: 15px">为什么需要你来做这件事</h3>
            <ul style="margin: 0; padding-left: 20px">
              <li>PLM 不是空白模板 —— <b>行业决定一切</b>：研发阶段、评审点、交付物口径、编号规则都随行业落地。</li>
              <li>只有<b>企业管理员</b>能做这个选择。完成之前，本企业其他成员登录后会看到"等待管理员完成行业初始化"。</li>
              <li>系统不替你猜：选择权在你，且<b>行业一经选定不可修改</b>（要换行业只能新开租户）。</li>
            </ul>
          </div>
          <div style="margin: 8px 0 20px; color: #333; line-height: 2">
            <h3 style="margin: 0 0 6px; font-size: 15px">接下来三步（约 2 分钟）</h3>
            <ol style="margin: 0; padding-left: 20px">
              <li><b>选择行业</b> —— 从平台提供的行业包里挑一个（如 智能硬件 · IPD、汽车 · APQP）。</li>
              <li><b>确认方法论与画像</b> —— 研发方法论由所选行业带出；顺手填企业规模与产品形态，供后续参数建议。</li>
              <li><b>行业解决方案</b> —— 先据实列出"会装哪几层、各新增多少"，<b>确认前不会写入任何数据</b>。</li>
            </ol>
          </div>
          <div class="onboarding-actions">
            <a-button type="primary" size="large" @click="step = 0">开始配置</a-button>
            <a-button type="link" @click="logout">退出登录</a-button>
          </div>
        </div>

        <!--
          标题与副标题：本页做的是"选行业 → 定方法论 → 装解决方案"的业务初始化引导。
          标题定名「业务配置引导（研发体系落地）」—— 2026-10-11 用户定名：
          原标题「行业配置」太笼统（没点出业务与研发体系的分量）。
          副标题随步骤切换（见 script 里的 stepHint）。
        -->
        <div v-if="step >= 0" class="onboarding-header">
          <h2>{{ state.tenantName }} · 业务配置引导（研发体系落地）</h2>
          <p class="sub" v-html="stepHint"></p>
        </div>

        <!--
          步骤条：标题自己做可点 —— 只允许往回走。
          不用 a-steps 的 @change：antd-vue 的步骤项只有在父层传了 onStepClick 时才可点，
          而 a-steps 包装层并不传（它只管样式与 emit），所以靠它点不动。往前跳需要先完成当前步
          （清单得先干跑出来），故第 4 步不给点击。
        -->
        <a-steps v-if="step >= 0" :current="step" size="small" style="margin: 20px 0 28px">
          <a-step>
            <template #title>
              <span :style="step > 0 ? 'cursor:pointer;color:#1677ff' : ''"
                    @click="step > 0 ? (step = 0) : null">选择行业</span>
            </template>
          </a-step>
          <a-step>
            <template #title>
              <span :style="step > 1 ? 'cursor:pointer;color:#1677ff' : ''"
                    @click="step > 1 ? (step = 1) : null">确认方法论与画像</span>
            </template>
          </a-step>
          <a-step>
            <template #title>
              <span :style="step > 2 ? 'cursor:pointer;color:#1677ff' : ''"
                    @click="step > 2 ? (step = 2) : null">行业解决方案</span>
            </template>
          </a-step>
          <a-step title="完成" />
        </a-steps>

        <!-- 第 1 步：选行业（方法论是每个行业包给出的结果） -->
        <div v-if="step === 0">
          <a-spin v-if="optionsLoading" />
          <a-empty v-else-if="options.length === 0" description="平台暂无可选的行业包（请确认行业包已随平台发布）" />
          <a-radio-group v-else v-model:value="selected" style="width: 100%">
            <div
              v-for="opt in options"
              :key="opt.packId"
              class="option-card"
              :class="{ active: selected === opt.industryCode, 'option-card--building': opt.enabled === false }"
              :style="opt.enabled === false ? { opacity: 0.65, cursor: 'not-allowed' } : null"
              @click="opt.enabled === false
                ? message.info((opt.disabledReason || '该行业包') + ' —— 正在建设中，敬请期待')
                : (selected = opt.industryCode)"
            >
              <a-radio :value="opt.industryCode" :disabled="opt.enabled === false">
                <span class="option-title">{{ opt.packName }}</span>
                <a-tag color="blue" style="margin-left: 8px">{{ opt.industryCode }}</a-tag>
                <a-tag color="green" style="margin-left: 4px">方法论 {{ opt.devMethod }}</a-tag>
                <a-tag v-if="opt.targetScale" style="margin-left: 4px">{{ opt.targetScale }}</a-tag>
                <!-- 平台已规划但尚未交付的行业：看得见、点不动、标明状态 -->
                <a-tag v-if="opt.enabled === false" color="orange" style="margin-left: 4px">正在建设中</a-tag>
              </a-radio>
              <div class="option-summary">{{ opt.summary }}</div>
              <div v-if="opt.segments && opt.segments.length" class="option-segments">
                细分行业：{{ opt.segments.map(s => s.name).join('、') }}
              </div>
              <!--
                本包提供的能力（capabilities.json）：选中的那张卡展开明细 —— 四张都展开会堆成一面墙，
                未选中的只报个数。换选方法论时能力清单随即变化，这正是"选之前就能看清得到什么"的用意。
                default=false 的标灰并写"未启用"（登记了但当前没生效，鼠标悬停看 note）。
              -->
              <div v-if="selected === opt.industryCode" class="option-capabilities">
                <span class="capability-label">本包提供的能力：</span>
                <a-tag
                  v-for="cap in (opt.capabilities || [])"
                  :key="cap.code"
                  :color="cap.default === false ? 'default' : 'green'"
                  :title="cap.note || cap.code"
                >{{ cap.name }}<span v-if="cap.default === false" class="capability-off">·未启用</span></a-tag>
                <span v-if="!(opt.capabilities || []).length" class="capability-empty">
                  该包未声明能力开关
                </span>
              </div>
              <div v-if="selected !== opt.industryCode && opt.capabilityCount" class="option-capabilities-hint">
                <template v-if="opt.enabled === false">建设中将提供 {{ opt.capabilityCount }} 项能力</template>
                <template v-else>提供 {{ opt.capabilityCount }} 项能力（选中后展开）</template>
              </div>
            </div>
          </a-radio-group>
        </div>

        <!-- 第 2 步：确认（行业 → 方法论 → 将落地的阶段）+ 企业画像 -->
        <div v-else-if="step === 1">
          <a-descriptions bordered size="small" :column="1">
            <a-descriptions-item label="企业">{{ state.tenantName }}（{{ state.tenantId }}）</a-descriptions-item>
            <a-descriptions-item label="选定行业">
              {{ selected }}
              <span style="color: #999">（{{ currentOption ? currentOption.packName : '' }}）</span>
            </a-descriptions-item>
            <a-descriptions-item label="研发方法论（行业带来的结果）">
              <a-tag color="green">{{ currentOption ? currentOption.devMethod : '—' }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="将落地的阶段">
              {{ currentOption && currentOption.stages.length ? currentOption.stages.join(' → ') : '—' }}
            </a-descriptions-item>
            <a-descriptions-item label="行业包">{{ currentOption ? currentOption.packId : '—' }}</a-descriptions-item>
          </a-descriptions>

          <a-divider orientation="left" style="margin-top: 24px">企业画像（用于参数建议与规则取值）</a-divider>
          <a-form layout="vertical">
            <a-row :gutter="16">
              <a-col :span="8">
                <a-form-item label="细分领域">
                  <a-select
                    v-model:value="profile.segment"
                    :key="selected"
                    placeholder="请选择细分行业"
                    allow-clear
                    :options="segmentOptions"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="企业规模">
                  <a-select v-model:value="profile.size" placeholder="请选择">
                    <a-select-option value="S">50 人以下</a-select-option>
                    <a-select-option value="M">50 ~ 300 人</a-select-option>
                    <a-select-option value="L">300 ~ 1000 人</a-select-option>
                    <a-select-option value="XL">1000 人以上</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="主要产品形态">
                  <!-- 选项来自所选行业包的 productForms 声明；一家企业常同时做多种形态，故多选 -->
                  <a-select
                    v-model:value="profile.products"
                    :key="'pf-' + selected"
                    mode="multiple"
                    placeholder="可多选"
                    allow-clear
                    :options="productFormOptions"
                  />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>

          <a-alert
            type="info"
            show-icon
            message="点「确认画像」会先生成本企业的行业解决方案（基座 → 行业共用基 → 行业包，层层递进），确认后才真正执行。"
          />
        </div>

        <!-- 第 3 步：行业解决方案（干跑，一行不写；按依赖层次层层递进） -->
        <div v-else-if="step === 2">
          <a-spin v-if="planLoading" tip="正在计算本企业将会得到的初始化内容..." />
          <template v-else>
            <a-alert
              :type="planTotals.failed ? 'error' : 'warning'"
              show-icon
              :message="'这是「' + (currentOption ? currentOption.packName : '') + '」带给本企业的行业解决方案，共 ' + planLayers.length + ' 层：新增 ' + planTotals.insert + ' 项' + (planTotals.skipReady ? '，已就绪 ' + planTotals.skipReady + ' 项' : '') + (planTotals.skipPending ? '，跳过 ' + planTotals.skipPending + ' 项（见下表说明）' : '') + (planTotals.failed ? '，失败 ' + planTotals.failed + ' 项（见下表动作列为失败的条目）' : '') + '。清单为干跑结果，确认前不会写入任何数据。'"
            />

            <div v-for="(layer, idx) in planLayers" :key="layer.packId" class="plan-layer">
              <div class="plan-layer-head">
                <span class="plan-layer-index">{{ idx + 1 }}</span>
                <span class="plan-layer-name">{{ layer.packName }}</span>
                <a-tag :color="layer.reused ? 'default' : 'green'">
                  {{ layer.reused ? '平台已就绪 · 全租户共享' : '将安装到本企业' }}
                </a-tag>
                <a-tag v-if="layer.insert" color="blue">将新增 {{ layer.insert }}</a-tag>
                <a-tag v-if="layer.skipReady && !layer.reused">已就绪 {{ layer.skipReady }}</a-tag>
                <a-tag v-if="layer.skipPending">跳过 {{ layer.skipPending }}</a-tag>
                <a-tag v-if="layer.failed" color="red">失败 {{ layer.failed }}</a-tag>
              </div>
              <div class="plan-layer-base">
                {{ layer.packId }}<span v-if="layer.basePackId"> · 依赖 {{ layer.basePackId }}</span>
              </div>
              <a-table
                size="small"
                :columns="planColumns"
                :data-source="layer.items"
                row-key="rowKey"
                :pagination="false"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'action'">
                    <a-tag :color="actionColor(record)" style="margin-inline-end: 0">
                      {{ actionSummary(record) }}
                    </a-tag>
                  </template>
                  <template v-else-if="column.key === 'resourceType'">
                    {{ record.resourceDisplay || record.resourceType }}
                  </template>
                  <template v-else-if="column.key === 'file'">
                    <span v-if="record.file">{{ record.file }}</span>
                    <span v-else style="color: #bbb">共 {{ record.total }} 条</span>
                  </template>
                  <template v-else-if="column.key === 'why'">
                    <!-- 汇总行的逐条明细：鼠标移上去就展开，条目一条不丢（62 个分类参数也放得下） -->
                    <a-popover
                      v-if="record.details && record.details.length"
                      placement="left"
                      :overlay-style="{ maxWidth: '640px' }"
                    >
                      <template #title>
                        {{ record.resourceDisplay }} · 明细 {{ record.details.length }} 条
                      </template>
                      <template #content>
                        <div style="max-height: 320px; overflow: auto; min-width: 420px">
                          <div
                            v-for="d in record.details"
                            :key="d.key"
                            style="display: flex; gap: 8px; font-size: 12px; line-height: 2"
                          >
                            <span style="flex: 0 0 52px; color: #999">{{ actionText(d.action, d.ready) }}</span>
                            <span style="flex: 0 0 176px; font-family: monospace">{{ d.key }}</span>
                            <span style="color: #666">{{ d.why }}</span>
                          </div>
                        </div>
                      </template>
                      <span style="cursor: help">
                        {{ record.why }}<span style="color: #bbb">（明细 {{ record.details.length }} 条）</span>
                      </span>
                    </a-popover>
                    <span v-else>{{ record.why }}</span>
                  </template>
                </template>
              </a-table>
            </div>

            <a-alert
              v-if="planErrors.length"
              type="error"
              show-icon
              style="margin-top: 16px"
              :message="'预演发现 ' + planErrors.length + ' 个问题：' + planErrors.join('；')"
            />
          </template>
        </div>

        <!-- 第 4 步：完成 —— **必须显式判 step === 3**。
             ★ 这里曾经是 v-else：step = -1（欢迎说明页，向导之前的初始值）不满足 0/1/2，
             于是欢迎页上会同时渲染出"行业解决方案已安装"（而 result 仍是空对象 {}，
             文案全成 undefined："已选定 undefined（方法论 undefined）…"）。
             完成态只在 completeOnboarding 成功（submit 里 step=3）之后才可能出现。 -->
        <div v-else-if="step === 3">
          <a-result status="success" title="行业解决方案已安装"
            :sub-title="'已选定 ' + (result.industryCode || '—') + '（方法论 ' + (result.devMethodCode || '—') + '），已安装行业包 ' + (result.packName || result.packId || '—') + '，落地 ' + (result.stageCount || 0) + ' 个阶段模板'+(result.detailCount ? '（安装明细 ' + result.detailCount + ' 条）' : '')">
            <template #extra>
              <a-button type="primary" @click="enterSystem">进入系统</a-button>
            </template>
          </a-result>
        </div>

        <!--
          安装进度：后端是**同步**返回（一次请求内把 3 层写完），没有可轮询的进度接口 ——
          故进度在前端按"层"推进（层次与新增数取自预演 layers，不编造阶段），
          到 92% 封顶、真完成时补到 100%；并保证最小展示时长（见 script 的 INSTALL_MIN_MS），
          安装很快时也不"一闪而过"。
        -->
        <a-modal
          :open="submitting"
          :closable="false"
          :mask-closable="false"
          :keyboard="false"
          :footer="null"
          :width="520"
          centered
          title="正在导入行业解决方案"
        >
          <div style="padding: 2px 0 14px; color: #555; line-height: 1.9">
            正把所选行业的解决方案写入本企业（共 {{ installTotalLayers }} 层）——
            按依赖顺序先装基础包、再装行业包。<b>请勿关闭页面</b>。
          </div>
          <a-progress
            :percent="installPercent"
            :status="installPercent >= 100 ? 'success' : 'active'"
          />
          <div style="margin-top: 14px">
            <div
              v-for="(l, i) in installLayerSteps"
              :key="l.key"
              style="display: flex; align-items: center; gap: 8px; line-height: 2.1; font-size: 13px"
              :style="{ color: i <= installLayerIndex ? '#1677ff' : '#bbb' }"
            >
              <check-circle-outlined v-if="i < installLayerIndex || installPercent >= 100" style="color: #52c41a" />
              <loading-outlined v-else-if="i === installLayerIndex" />
              <clock-circle-outlined v-else />
              <span>{{ l.name }}</span>
              <span style="color: #999; font-size: 12px">{{ l.note }}</span>
            </div>
          </div>
        </a-modal>

        <!-- 操作条：每一步都给足「往回」与「往前」的入口（步骤条也能点着往回走）；欢迎页有自己的按钮 -->
        <div v-if="step >= 0 && step < 3" class="onboarding-actions">
          <template v-if="step === 0">
            <a-button type="primary" :disabled="!selected" @click="step = 1">下一步</a-button>
          </template>
          <template v-else-if="step === 1">
            <a-button @click="step = 0">上一步：改选行业</a-button>
            <a-button type="primary" :loading="planLoading" @click="loadPlan">确认画像</a-button>
          </template>
          <template v-else>
            <a-button @click="step = 1">上一步：改画像</a-button>
            <a-button @click="step = 0">改选行业</a-button>
            <a-button type="primary" :loading="submitting" @click="submit">确认并安装行业解决方案</a-button>
          </template>
          <a-button type="link" @click="logout">退出登录</a-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { CheckCircleOutlined, ClockCircleOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { getOnboardingState, getOnboardingOptions, planOnboarding, completeOnboarding } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const optionsLoading = ref(false)
const planLoading = ref(false)
const submitting = ref(false)
const state = ref(null)
const options = ref([])
const step = ref(-1)   // -1 = 欢迎说明页（向导之前的一页）；0 起为向导的四步
const selected = ref('')
/** 引导页默认勾选的行业包（code 与行业包清单 industryCode 一致） */
const DEFAULT_INDUSTRY_CODE = 'SMART_HARDWARE'
const result = ref({})
const profile = ref({ segment: undefined, size: undefined, products: [] })
const planLayers = ref([])
const planErrors = ref([])

// ===== 安装进度（后端同步返回、无进度接口 → 前端按"层"推进；配合最小展示时长）=====
/** 最小展示时长：安装很快（本地库常 < 1s）时也让进度走完，避免"一闪而过" */
const INSTALL_MIN_MS = 2600

/** 进度百分比：定时器每 240ms 前进一小步、92% 封顶（不假装已完成）；真完成时由 submit 补到 100% */
const installPercent = ref(0)
let installTimer = null

/** 待装层次（取自预演 layers —— 包名与新增数都是预演算出来的，不编造阶段） */
const installLayerSteps = computed(() => {
  const layers = planLayers.value || []
  if (!layers.length) {
    return [{ key: 'all', name: '正在写入本企业…', note: '' }]
  }
  return layers.map((l, i) => ({
    key: l.packId || ('layer-' + i),
    name: l.packName || l.packId || ('第 ' + (i + 1) + ' 层'),
    note: l.reused ? '平台已就绪 · 直接复用' : (l.insert ? '将新增 ' + l.insert + ' 项' : '')
  }))
})
const installTotalLayers = computed(() => installLayerSteps.value.length)

/** "正在装第几层"（0 起）—— 由百分比派生，避免两套状态打架 */
const installLayerIndex = computed(() => {
  const total = installTotalLayers.value
  return Math.min(total - 1, Math.floor(installPercent.value / (92 / Math.max(1, total))))
})

function startInstallProgress() {
  installPercent.value = 0
  stopInstallProgress()
  installTimer = setInterval(() => {
    if (installPercent.value < 92) {
      installPercent.value = Math.min(92, installPercent.value + 3 + Math.round(Math.random() * 4))
    }
  }, 240)
}

function stopInstallProgress() {
  if (installTimer) {
    clearInterval(installTimer)
    installTimer = null
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

onUnmounted(stopInstallProgress)

/** 副标题随步骤切换：把"这一步在做什么"说清楚（原先只写第一步，却对三步都显示） */
const stepHint = computed(() => {
  if (step.value === 0) {
    return '第一步：<b>选择行业</b> —— 研发方法论（阶段、评审点与交付物口径）由所选行业带出；'
      + '<b>行业一经选定不可修改</b>，请确认后再提交。'
  }
  if (step.value === 1) {
    return '第二步：<b>确认方法论与企业画像</b> —— 方法论已由所选行业确定；企业规模与产品形态用于后续参数建议。'
  }
  if (step.value === 2) {
    return '第三步：<b>行业解决方案</b> —— 下面是真装将写入本企业的全部内容（按依赖顺序分层列出）；'
      + '<b>确认前不会写入任何数据</b>。'
  }
  return '安装已完成 —— 可进入系统。'
})

const currentOption = computed(() => options.value.find(o => o.industryCode === selected.value))

/** 细分领域下拉项 —— 直接来自所选行业包的 segments 声明 */
const segmentOptions = computed(() => {
  const segs = (currentOption.value && currentOption.value.segments) || []
  return segs.map(s => ({ value: s.code, label: s.name }))
})

/** 主要产品形态（多选）—— 来自所选行业包的 productForms 声明 */
const productFormOptions = computed(() => {
  const forms = (currentOption.value && currentOption.value.productForms) || []
  return forms.map(f => ({ value: f.code, label: f.name }))
})

/** 各层合计（"将新增 / 已就绪 / 跳过 / 失败"）—— 失败一定要露出来，否则界面会"显示正常但装丢了" */
const planTotals = computed(() => {
  let insert = 0
  let skipReady = 0
  let skipPending = 0
  let failed = 0
  for (const layer of planLayers.value) {
    insert += layer.insert || 0
    skipReady += layer.skipReady || 0
    skipPending += layer.skipPending || 0
    failed += layer.failed || 0
  }
  return { insert, skipReady, skipPending, failed }
})

/** 动作标签（单条明细：新增 / 已就绪 / 跳过 / 更新 / 失败）；SKIP 按后端给的 ready 细分 */
function actionText(action, ready) {
  if (action === 'INSERT') return '将新增'
  if (action === 'SKIP') return ready === false ? '跳过' : '已就绪'
  if (action === 'UPDATE') return '将更新'
  if (action === 'FAILED') return '失败'
  return action || '—'
}

/**
 * 汇总行的动作：一类资源一行，所以这里报"条数"而不是单个动作。
 * 只有一条时仍用动作词（"已就绪 / 跳过"），免得写成"已就绪 1"这种废话。
 * "已就绪"与"跳过"必须分开：后者是预期性跳过 / 前置未就绪（如预演时布局还没有可套用的类型），
 * 把它画成"已就绪"会让管理员以为东西已经在那儿了。
 */
function actionSummary(record) {
  if (!record.total || record.total <= 1) return actionText(record.action, record.ready)
  const parts = []
  if (record.insert) parts.push('将新增 ' + record.insert)
  if (record.skipReady) parts.push('已就绪 ' + record.skipReady)
  if (record.skipPending) parts.push('跳过 ' + record.skipPending)
  if (record.failed) parts.push('失败 ' + record.failed)
  return parts.length ? parts.join(' · ') : actionText(record.action, record.ready)
}

/** 汇总行的动作配色：失败最显眼；有新增给蓝；全已就绪/跳过给默认灰 */
function actionColor(record) {
  if (record.failed) return 'red'
  if (record.insert) return 'blue'
  return 'default'
}

/** 清单表格列 —— 一类资源一行；逐条明细在「说明」列的悬停气泡里（不丢了，只是不占版面） */
const planColumns = [
  { title: '动作', key: 'action', width: 160 },
  { title: '资源类型', key: 'resourceType', width: 150 },
  { title: '条目', key: 'file', width: 200 },
  { title: '说明（悬停看明细）', key: 'why' }
]

async function loadAll() {
  loading.value = true
  try {
    const res = await getOnboardingState()
    if (res.code === 200) {
      state.value = res.data
      if (res.data.needsOnboarding && res.data.isTenantAdmin) {
        await loadOptions()
      }
    } else {
      message.error(res.message || '读取初始化状态失败')
    }
  } catch (e) {
    message.error('读取初始化状态失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  optionsLoading.value = true
  try {
    const res = await getOnboardingOptions()
    if (res.code === 200) {
      options.value = res.data || []
      if (options.value.length && !selected.value) {
        // 默认选中「智能硬件 · IPD 研发体系包」：多数客户属于该行业，少点一次；
        // 该包不存在或仍在建设（enabled=false）时退回第一个，不至于预选一个点不动的项
        const preferred = options.value.find(
          o => o.industryCode === DEFAULT_INDUSTRY_CODE && o.enabled !== false)
        selected.value = preferred ? preferred.industryCode : options.value[0].industryCode
      }
    }
  } catch (e) {
    message.error('读取可选行业失败')
  } finally {
    optionsLoading.value = false
  }
}

/** 点「确认画像」：先干跑，把"将会得到什么"按依赖层次列给管理员看 */
async function loadPlan() {
  planLoading.value = true
  try {
    const res = await planOnboarding(selected.value,
      currentOption.value ? currentOption.value.devMethod : null)
    if (res.code === 200) {
      planLayers.value = (res.data && res.data.layers) || []
      planErrors.value = (res.data && res.data.errors) || []
      step.value = 2
    } else {
      message.error(res.message || '预演失败')
    }
  } catch (e) {
    message.error('预演失败')
  } finally {
    planLoading.value = false
  }
}

/**
 * 点「确认并安装行业解决方案」—— 真装（幂等）。
 *
 * <p>后端是**同步**返回（一次请求内把 3 层写完），没有可轮询的进度接口，故：
 * ① 进度在前端按"层"推进（层次取自预演，不编造阶段），92% 封顶，真完成时才 100%；
 * ② 保证最小展示时长 INSTALL_MIN_MS —— 安装很快时也让进度走完，避免"一闪而过"。
 */
async function submit() {
  submitting.value = true
  startInstallProgress()
  const startedAt = Date.now()
  try {
    const seg = profile.value.segment
    const segmentValid = segmentOptions.value.some(o => o.value === seg)
    const products = (profile.value.products || []).filter(
      p => productFormOptions.value.some(o => o.value === p))
    const payload = {
      industryCode: selected.value,
      devMethodCode: currentOption.value ? currentOption.value.devMethod : null,
      profile: JSON.stringify({
        segment: segmentValid ? seg : null,
        size: profile.value.size || null,
        products
      })
    }
    const res = await completeOnboarding(payload)
    // 最小展示时长：请求很快返回时也等满，让用户看清"装了哪几层"
    await sleep(Math.max(0, INSTALL_MIN_MS - (Date.now() - startedAt)))
    if (res.code === 200) {
      installPercent.value = 100
      await sleep(420)   // 让 100% 与"完成"可见后再翻到第 4 步
      result.value = res.data || {}
      step.value = 3
      message.success('行业解决方案已安装')
    } else {
      message.error(res.message || '初始化失败')
    }
  } catch (e) {
    message.error('初始化失败')
  } finally {
    stopInstallProgress()
    submitting.value = false
    installPercent.value = 0
  }
}

function enterSystem() {
  router.replace('/home')
}

function logout() {
  userStore.clearLogin()
  router.replace('/login')
}

onMounted(loadAll)
</script>

<style scoped>
.onboarding-page {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 16px;
}
.onboarding-card {
  width: 100%;
  max-width: 880px;
  background: #fff;
  border-radius: 8px;
  padding: 32px 36px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.onboarding-header h2 {
  margin: 0 0 8px;
  font-size: 20px;
}
.onboarding-header .sub {
  margin: 0;
  color: #666;
  line-height: 1.7;
}
.option-card {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 14px 18px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.option-card:hover {
  border-color: #91caff;
}
.option-card.active {
  border-color: #1677ff;
  background: #f0f7ff;
}
.option-title {
  font-size: 15px;
  font-weight: 600;
}
.option-summary {
  margin: 6px 0 0 24px;
  color: #666;
  font-size: 13px;
  line-height: 1.6;
}
.option-segments {
  margin: 4px 0 0 24px;
  color: #999;
  font-size: 12px;
  line-height: 1.6;
}
.option-capabilities {
  margin: 6px 0 0 24px;
  line-height: 2;
}
.capability-label {
  margin-right: 4px;
  color: #666;
  font-size: 12px;
}
.capability-off {
  color: #999;
}
.capability-empty {
  color: #bbb;
  font-size: 12px;
}
.option-capabilities-hint {
  margin: 4px 0 0 24px;
  color: #bbb;
  font-size: 12px;
}
.plan-layer {
  margin-top: 20px;
  border-left: 3px solid #1677ff;
  padding-left: 12px;
}
.plan-layer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.plan-layer-index {
  display: inline-flex;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #1677ff;
  color: #fff;
  font-size: 12px;
  align-items: center;
  justify-content: center;
}
.plan-layer-name {
  font-size: 15px;
  font-weight: 600;
}
.plan-layer-base {
  margin: 4px 0 8px;
  color: #999;
  font-size: 12px;
}
.onboarding-actions {
  margin-top: 28px;
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
