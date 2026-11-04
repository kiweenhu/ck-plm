<template>
  <div class="industry-pack-page">
    <a-page-header
      title="行业包"
      sub-title="查看平台内置的行业与内嵌方法论内容（只读；安装由启动引导与租户初始化完成）"
    />

    <!-- 非平台管理员：无权限提示（与租户审核同口径） -->
    <a-result
      v-if="!isPlatformAdmin"
      status="403"
      title="无访问权限"
      sub-title="仅平台管理员可查看行业包内容"
    >
      <template #extra>
        <a-button type="primary" @click="$router.push('/home')">返回首页</a-button>
      </template>
    </a-result>

    <template v-else>
      <!-- 顶部概览 -->
      <a-card :bordered="false" style="margin-top: 16px">
        <a-row :gutter="16">
          <a-col :span="6">
            <a-statistic title="平台版本" :value="platformVersion || '—'" />
          </a-col>
          <a-col :span="6">
            <a-statistic title="可见行业包" :value="packs.length" />
          </a-col>
          <a-col :span="6">
            <a-statistic title="已安装" :value="installedCount" />
          </a-col>
          <a-col :span="6">
            <a-statistic title="校验不通过" :value="invalidCount">
              <template #value>
                <span :style="{ color: invalidCount > 0 ? '#cf1322' : '#3f8600' }">{{ invalidCount }}</span>
              </template>
            </a-statistic>
          </a-col>
        </a-row>
        <a-alert
          v-if="invalidCount > 0"
          type="warning"
          show-icon
          style="margin-top: 12px"
          message="有行业包未通过校验"
          :description="invalidReasons"
        />
      </a-card>

      <a-row :gutter="16" style="margin-top: 16px">
        <!-- 左：包列表 -->
        <a-col :span="7">
          <a-card :bordered="false" title="行业包列表" size="small">
            <a-spin :spinning="loading">
              <a-list :data-source="packs" size="small">
                <template #renderItem="{ item }">
                  <a-list-item
                    :class="['pack-item', { active: current && current.packId === item.packId }]"
                    @click="selectPack(item)"
                  >
                    <a-list-item-meta>
                      <template #title>
                        <span class="pack-name">{{ item.name }}</span>
                        <a-tag v-if="isInstalled(item)" color="green" style="margin-left: 6px">已安装</a-tag>
                        <!-- 未启用的包不进租户引导的可选项：这里明确标出来，免得平台管理员以为它丢了 -->
                        <a-tag v-if="item.enabled === false" style="margin-left: 4px">未启用</a-tag>
                        <a-tag
                          v-else-if="item.selectableInOnboarding"
                          color="blue"
                          style="margin-left: 4px"
                        >可引导选装</a-tag>
                      </template>
                      <template #description>
                        <div class="pack-id">{{ item.packId }}</div>
                        <div class="pack-meta">
                          <span v-if="item.industry">行业 {{ item.industry }}</span>
                          <span v-if="item.devMethod">· 方法论 {{ item.devMethod }}</span>
                          <span v-if="item.targetScale">· {{ item.targetScale }}</span>
                        </div>
                        <div class="pack-counts">{{ countsText(item.resourceCounts) }}</div>
                      </template>
                    </a-list-item-meta>
                  </a-list-item>
                </template>
              </a-list>
              <a-empty v-if="!loading && packs.length === 0" description="未发现行业包" />
            </a-spin>
          </a-card>
        </a-col>

        <!-- 右：内容详情 -->
        <a-col :span="17">
          <a-card :bordered="false" size="small">
            <a-empty v-if="!current" description="请选择左侧的行业包" />
            <template v-else>
              <a-tabs v-model:activeKey="activeTab">
                <!-- ① 概览 -->
                <a-tab-pane key="overview" tab="概览">
                  <a-descriptions :column="2" size="small" bordered>
                    <a-descriptions-item label="包名">{{ content.name }}</a-descriptions-item>
                    <a-descriptions-item label="坐标">{{ content.packId }}</a-descriptions-item>
                    <a-descriptions-item label="行业">{{ content.industry || '（平台级，不限行业）' }}</a-descriptions-item>
                    <a-descriptions-item label="内嵌方法论">{{ content.devMethod || '—' }}</a-descriptions-item>
                    <a-descriptions-item label="适用规模">{{ content.targetScale || '—' }}</a-descriptions-item>
                    <a-descriptions-item label="管控级别">{{ content.controlLevel || '—' }}</a-descriptions-item>
                    <a-descriptions-item label="父包">{{ content.basePack || '（无依赖）' }}</a-descriptions-item>
                    <a-descriptions-item label="版本区间">
                      {{ content.engineRange || '不限' }}
                      <a-tag :color="content.engineRangeSatisfied ? 'green' : 'red'" style="margin-left: 6px">
                        当前 {{ content.platformVersion }}
                      </a-tag>
                    </a-descriptions-item>
                    <a-descriptions-item label="主粒度">{{ content.primaryScopeType || '—' }}</a-descriptions-item>
                    <a-descriptions-item label="授权">{{ content.license || '—' }}</a-descriptions-item>
                    <a-descriptions-item label="内容指纹">
                      <span class="mono">{{ shortHash(content.contentHash) }}</span>
                    </a-descriptions-item>
                    <a-descriptions-item label="安装状态">
                      <a-tag :color="content.installedAtPlatform ? 'green' : 'default'">
                        平台：{{ content.installedAtPlatform || '未安装' }}
                      </a-tag>
                      <a-tag v-if="content.installedAtTenant !== undefined"
                              :color="content.installedAtTenant ? 'green' : 'default'">
                        本租户：{{ content.installedAtTenant || '未安装' }}
                      </a-tag>
                    </a-descriptions-item>
                  </a-descriptions>

                  <a-divider orientation="left">能力开关</a-divider>
                  <a-table
                    :columns="capabilityColumns"
                    :data-source="content.capabilities || []"
                    :pagination="false"
                    row-key="code"
                    size="small"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'implemented'">
                        <a-tag :color="record.implemented ? 'green' : 'orange'">
                          {{ record.implemented ? '已实现' : '平台未实现' }}
                        </a-tag>
                      </template>
                      <template v-else-if="column.key === 'enabled'">
                        <a-tag :color="record.default ? 'green' : 'default'">
                          {{ record.default ? '默认启用' : '默认关闭' }}
                        </a-tag>
                      </template>
                    </template>
                  </a-table>
                  <a-empty v-if="!(content.capabilities || []).length" description="本包未声明能力开关" />
                </a-tab-pane>

                <!-- ② 方法论内容 -->
                <a-tab-pane key="content" tab="方法论内容">
                  <a-alert
                    v-if="!resources.length"
                    type="info"
                    show-icon
                    message="本包资源尚未抽取"
                    description="P0 只建了包机制骨架；P1 会把现有资产（软类型 / 分类 / 编码与版本规则 / 生命周期 / 阶段模板）抽取成包内模板文件，届时这里会逐条列出内容。"
                  />
                  <template v-else>
                    <a-divider orientation="left">内容亮点</a-divider>
                    <a-list :data-source="content.previewHints || []" size="small">
                      <template #renderItem="{ item }">
                        <a-list-item>
                          <a-tag color="blue">{{ item.resource }}</a-tag>
                          <span style="margin-left: 8px">{{ item.highlight }}</span>
                        </a-list-item>
                      </template>
                    </a-list>

                    <a-divider orientation="left">资源清单</a-divider>
                    <a-table
                      :columns="resourceColumns"
                      :data-source="resources"
                      :pagination="false"
                      row-key="file"
                      size="small"
                    >
                      <template #bodyCell="{ column, record }">
                        <template v-if="column.key === 'fileExists'">
                          <a-tag :color="record.fileExists ? 'green' : 'orange'">
                            {{ record.fileExists ? '已就位' : '待抽取' }}
                          </a-tag>
                        </template>
                        <template v-if="column.key === 'items'">
                          <a-popover v-if="(record.items || []).length" title="条目预览" trigger="click">
                            <template #content>
                              <div class="items-pop">
                                <div v-for="(it, i) in record.items" :key="i" class="items-row">
                                  <span class="mono">{{ it.code || it.identifier }}</span>
                                  <span style="margin-left: 8px">{{ it.name }}</span>
                                  <a-tag v-if="it.industry" style="margin-left: 8px">{{ it.industry }}</a-tag>
                                  <a-tag v-if="it.dataType" color="blue" style="margin-left: 4px">{{ it.dataType }}</a-tag>
                                  <a-tag v-if="it.stage" style="margin-left: 4px">{{ stageLabel(it.stage) }}</a-tag>
                                  <a-tag v-if="it.discipline" style="margin-left: 4px">{{ it.discipline }}</a-tag>
                                  <div v-if="it.note" style="color: #999; font-size: 12px; margin-top: 2px">{{ it.note }}</div>
                                </div>
                              </div>
                            </template>
                            <a>查看 {{ record.items.length }} 条</a>
                          </a-popover>
                          <span v-else>—</span>
                        </template>
                      </template>
                    </a-table>
                  </template>
                </a-tab-pane>

                <!-- ③ 画像与参数 -->
                <a-tab-pane key="params" tab="画像与参数">
                  <a-divider orientation="left">企业画像题（驱动推荐与参数建议）</a-divider>
                  <a-table
                    :columns="questionColumns"
                    :data-source="content.questionnaire || []"
                    :pagination="false"
                    row-key="code"
                    size="small"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'options'">
                        <a-tag v-for="opt in (record.options || [])" :key="opt" style="margin-bottom: 4px">{{ opt }}</a-tag>
                      </template>
                      <template v-if="column.key === 'affects'">
                        <div v-for="(a, i) in (record.affects || [])" :key="i" class="mono small">
                          {{ a.parameter || a.recommend ? '影响推荐' : '' }}
                        </div>
                      </template>
                    </template>
                  </a-table>
                  <a-empty v-if="!(content.questionnaire || []).length" description="本包未定义画像题" />

                  <a-divider orientation="left">可调参数（引导页可改，含按规模建议值）</a-divider>
                  <a-table
                    :columns="parameterColumns"
                    :data-source="content.parameters || []"
                    :pagination="false"
                    row-key="code"
                    size="small"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'code'">
                        <span class="mono">{{ record.code }}</span>
                      </template>
                      <template v-if="column.key === 'suggest'">
                        <a-tag v-for="(v, k) in (record.suggest || {})" :key="k" style="margin-bottom: 4px">
                          {{ k }} → {{ v }}
                        </a-tag>
                      </template>
                    </template>
                  </a-table>
                  <a-empty v-if="!(content.parameters || []).length" description="本包未定义可调参数" />
                </a-tab-pane>

                <!-- ④ 待办与示例 -->
                <a-tab-pane key="next" tab="待办与示例">
                  <a-divider orientation="left">装完后的待办引导</a-divider>
                  <a-list :data-source="content.nextSteps || []" size="small">
                    <template #renderItem="{ item }">
                      <a-list-item>
                        <span>{{ item.text }}</span>
                        <a-tag v-if="item.route" style="margin-left: 8px" color="blue">{{ item.route }}</a-tag>
                        <a-tag v-else-if="item.action" style="margin-left: 8px" color="purple">{{ item.action }}</a-tag>
                      </a-list-item>
                    </template>
                  </a-list>
                  <a-empty v-if="!(content.nextSteps || []).length" description="本包未定义后续待办" />

                  <a-divider orientation="left">示例数据</a-divider>
                  <a-descriptions v-if="content.sample" :column="1" size="small" bordered>
                    <a-descriptions-item label="示例名称">{{ content.sample.name }}</a-descriptions-item>
                    <a-descriptions-item label="默认安装">
                      {{ content.sample.autoInstall ? '是（随包自动安装）' : '否' }}
                    </a-descriptions-item>
                    <a-descriptions-item label="可清理">{{ content.sample.removable ? '是（精确回滚）' : '否' }}</a-descriptions-item>
                    <a-descriptions-item label="说明">{{ content.sample.note }}</a-descriptions-item>
                  </a-descriptions>
                  <a-empty v-else description="本包不含示例数据" />
                </a-tab-pane>

                <!-- ⑤ 安装预演 -->
                <a-tab-pane key="plan" tab="安装预演">
                  <a-space>
                    <a-button type="primary" :loading="planning" @click="runPlan">预演（不写库）</a-button>
                    <span class="hint">只算不写：回答"真装会得到什么"，可反复点，不会产生任何数据。</span>
                  </a-space>

                  <template v-if="planReports.length">
                    <a-divider orientation="left">安装链（父包在前）</a-divider>
                    <a-table
                      :columns="planColumns"
                      :data-source="planReports"
                      :pagination="false"
                      row-key="packId"
                      size="small"
                      style="margin-top: 12px"
                    >
                      <template #bodyCell="{ column, record }">
                        <template v-if="column.key === 'summary'">
                          <span v-for="(v, k) in record.summary" :key="k" class="sum-chip">
                            {{ k }}：新增 {{ v[0] }} / 跳过 {{ v[1] }} / 失败 {{ v[3] }}
                          </span>
                          <span v-if="!Object.keys(record.summary || {}).length" class="hint">（本包暂无资源）</span>
                        </template>
                        <template v-if="column.key === 'installState'">
                          <a-tag :color="record.installState === 'PLANNED' ? 'blue' : 'default'">
                            {{ record.installState }}
                          </a-tag>
                        </template>
                      </template>
                    </a-table>
                    <a-alert
                      v-for="(err, i) in planErrors"
                      :key="i"
                      type="error"
                      show-icon
                      style="margin-top: 8px"
                      :message="err"
                    />
                  </template>
                </a-tab-pane>
              </a-tabs>
            </template>
          </a-card>
        </a-col>
      </a-row>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import {
  getIndustryPacks,
  getInstalledIndustryPacks,
  validateIndustryPacks,
  getIndustryPackContent,
  planIndustryPack
} from '@/api'

const userStore = useUserStore()
/** 仅平台管理员可查看行业包内容（与租户审核页同口径） */
const isPlatformAdmin = computed(() => (userStore.roles || []).includes('PLATFORM_ADMIN'))

const loading = ref(false)
const planning = ref(false)
const packs = ref([])
const installed = ref([])
const validations = ref([])
const platformVersion = ref('')
const current = ref(null)
const content = ref({})
const activeTab = ref('overview')
const planReports = ref([])

const capabilityColumns = [
  { title: '能力', dataIndex: 'name', key: 'name' },
  { title: '编码', dataIndex: 'code', key: 'code' },
  { title: '默认', key: 'enabled', width: 120 },
  {
    // 包的声明 ≠ 系统的事实：这里显示平台到底有没有这个功能（未实现的不启用，装上也不落租户）
    title: '平台实现', key: 'implemented', width: 130
  },
  { title: '说明', dataIndex: 'note', key: 'note' }
]

const resourceColumns = [
  {
    // 与引导页同一口径：中文名来自包清单的 display，没声明时回落类型码
    title: '资源类型', key: 'type', width: 140,
    customRender: ({ record }) => record.display || record.type
  },
  { title: '模板文件', dataIndex: 'file', key: 'file' },
  {
    title: '声明条数', dataIndex: 'declaredCount', key: 'declaredCount', width: 90,
    customHeaderCell: () => ({ title: '清单里声明的安装条数（真正写库的行数）' })
  },
  {
    title: '预览条目', key: 'items', width: 110,
    customHeaderCell: () => ({ title: '从模板文件抽出的可预览行；分类树节点、生命周期模板内的状态行等嵌套条目一并计入，故可能多于声明条数' })
  },
  { title: '文件', key: 'fileExists', width: 90 }
]

const questionColumns = [
  { title: '画像问题', dataIndex: 'text', key: 'text' },
  { title: '选项', key: 'options' },
  { title: '影响', key: 'affects', width: 220 }
]

const parameterColumns = [
  { title: '参数', key: 'code', width: 300 },
  { title: '名称', dataIndex: 'label', key: 'label' },
  { title: '默认值', dataIndex: 'default', key: 'default', width: 90 },
  { title: '按规模建议', key: 'suggest' },
  { title: '说明', dataIndex: 'hint', key: 'hint' }
]

const planColumns = [
  { title: '包', dataIndex: 'packId', key: 'packId', width: 240 },
  { title: '状态', key: 'installState', width: 110 },
  { title: '逐组汇总', key: 'summary' }
]

const resources = computed(() => content.value.resources || [])
const planErrors = computed(() => {
  const errs = []
  planReports.value.forEach(r => (r.errors || []).forEach(e => errs.push(r.packId + '：' + e)))
  return errs
})

const invalidCount = computed(() => validations.value.filter(v => !v.valid).length)
const invalidReasons = computed(() =>
  validations.value.filter(v => !v.valid)
    .map(v => v.packId + '：' + (v.problems || []).join('；'))
    .join('\n')
)
const installedCount = computed(() => {
  const keys = new Set(installed.value.map(i => i.pack_code + '@' + i.pack_version))
  return keys.size
})

function isInstalled(item) {
  return installed.value.some(i => (i.pack_code + '@' + i.pack_version) === item.packId)
}

function countsText(counts) {
  const entries = Object.entries(counts || {})
  if (!entries.length) return '资源待抽取（P1）'
  return entries.map(([k, v]) => k + ' ' + v).join(' · ')
}

function shortHash(hash) {
  return hash ? hash.substring(0, 12) + '…' : '—'
}

// 条目里的 stage 是裸码（如 IPD_CONCEPT），界面显示中文阶段名 ——
// 名字取自**同一个包里 stages.json 的条目**（单一来源，不在前端硬编码；查不到回落原码）。
// 交付件与阶段的关系就写在这里：阶段门按「本阶段交付件完成率」判定，标签显示的是同一份数据。
function stageLabel(code) {
  if (!code) return ''
  const stageRes = (content.value.resources || []).find(r => r.type === 'STAGE')
  const hit = ((stageRes && stageRes.items) || []).find(s => s.code === code)
  return (hit && hit.name) || code
}

async function loadAll() {
  if (!isPlatformAdmin.value) return
  loading.value = true
  try {
    const [availableRes, installedRes, validateRes] = await Promise.all([
      getIndustryPacks(), getInstalledIndustryPacks(), validateIndustryPacks()
    ])
    packs.value = availableRes.data || []
    installed.value = installedRes.data || []
    validations.value = (validateRes.data && validateRes.data.packs) || []
    platformVersion.value = (validateRes.data && validateRes.data.platformVersion) || ''
    if (!current.value && packs.value.length) {
      selectPack(packs.value[0])
    }
  } catch (e) {
    message.error('加载行业包失败：' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function selectPack(item) {
  current.value = item
  planReports.value = []
  try {
    const res = await getIndustryPackContent(item.code, item.version)
    content.value = res.data || {}
  } catch (e) {
    message.error('加载包内容失败：' + (e.message || e))
  }
}

async function runPlan() {
  if (!current.value) return
  planning.value = true
  try {
    const res = await planIndustryPack(current.value.code, current.value.version)
    planReports.value = res.data || []
    message.success('预演完成（未写库）')
  } catch (e) {
    message.error('预演失败：' + (e.message || e))
  } finally {
    planning.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.industry-pack-page :deep(.ant-page-header) { padding-left: 0; }
.pack-item { cursor: pointer; border-radius: 6px; padding: 8px 10px !important; transition: background 0.2s; }
.pack-item:hover { background: rgba(22, 119, 255, 0.06); }
.pack-item.active { background: rgba(22, 119, 255, 0.12); }
.pack-name { font-weight: 600; }
.pack-id { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; color: #8c8c8c; }
.pack-meta { font-size: 12px; color: #595959; margin-top: 2px; }
.pack-counts { font-size: 12px; color: #8c8c8c; margin-top: 2px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
.small { font-size: 12px; }
.hint { color: #8c8c8c; font-size: 12px; }
.items-pop { max-height: 320px; overflow: auto; min-width: 280px; }
.items-row { padding: 2px 0; font-size: 12px; }
.sum-chip { display: inline-block; margin-right: 10px; font-size: 12px; }
</style>
