<template>
  <div class="lwc-page">
    <div class="lwc-intro">
      数模转换中心把 CAD 源文件转成浏览器可查看的轻量化模型（glb），实际干活的是独立部署的转换服务
      <b>CK-CADForge</b>。这里配的是「CK-PLM 怎么找到它、愿意等多久、多大的文件才送进去」——
      保存后<b>立即生效</b>（后端刷新运行时快照并重建 HTTP 客户端），不用改 yml、也不用重启。
    </div>

    <a-alert
      v-if="!canEdit"
      class="lwc-alert"
      type="info"
      show-icon
      message="只读视图"
      description="转换器是整个部署共享的一套基础设施（所有租户的文件都送同一个服务转），只有平台管理员可以修改。"
    />

    <a-spin :spinning="loading">
      <a-form layout="vertical" :model="form" :disabled="!canEdit">
        <!-- 服务连接 -->
        <a-card size="small" class="lwc-card" title="服务连接">
          <template #extra>
            <a-space :size="8">
              <a-tag :color="form.enabled ? 'green' : 'default'">
                {{ form.enabled ? '已启用' : '未启用' }}
              </a-tag>
              <a-button size="small" :loading="testing" :disabled="!form.baseUrl" @click="testConnection">
                <ApiOutlined /> 测试连接
              </a-button>
            </a-space>
          </template>

          <a-form-item label="启用轻量化转换">
            <a-switch v-model:checked="form.enabled" />
            <span class="lwc-hint">
              关掉后触发转换直接记为「已跳过」（SKIPPED），适合还没部署转换器的环境 —— 模型详情里不会出现一排失败记录
            </span>
          </a-form-item>

          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <a-form-item label="转换器地址" required>
                <a-input v-model:value="form.baseUrl" placeholder="http://cadforge:8090" allow-clear />
                <span class="lwc-hint">
                  CK-CADForge 的基地址，提交任务与健康探测都走它。
                  内网/回环地址还需在 plm.integration.allowed-hosts 里放行（出站策略默认拦私网，防 SSRF），
                  否则现象与「转换器没起」一模一样
                </span>
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="结果回调地址">
                <a-input
                  v-model:value="form.callbackUrl"
                  placeholder="http://ck-plm:8082/api/internal/cad/convert/callback"
                  allow-clear
                />
                <span class="lwc-hint">
                  转换器要能访问到这个地址：容器部署请填<b>服务名</b>而不是 localhost，否则回调永远进不来
                </span>
              </a-form-item>
            </a-col>
          </a-row>

          <a-form-item label="服务间回调令牌">
            <a-input-password
              v-model:value="form.callbackToken"
              :placeholder="cfg.callbackTokenSet ? '已配置（不回传明文），留空表示不修改' : '尚未配置'"
              autocomplete="new-password"
              allow-clear
            />
            <span class="lwc-hint">
              {{
                cfg.callbackTokenSet
                  ? '已配置：明文不落浏览器，改超时/地址时留空即可保持原令牌'
                  : '未配置：令牌为空时后端会拒绝所有回调，转换结果只能靠看门狗兜底收下'
              }}
            </span>
          </a-form-item>
        </a-card>

        <!-- 超时与限额 -->
        <a-card size="small" class="lwc-card" title="超时与限额">
          <a-row :gutter="16">
            <a-col :xs="12" :md="8">
              <a-form-item label="连接超时">
                <a-input-number v-model:value="form.connectTimeoutMs" :min="100" :max="60000" :step="500" style="width:100%" />
                <span class="lwc-hint">毫秒，100 ~ 60000</span>
              </a-form-item>
            </a-col>
            <a-col :xs="12" :md="8">
              <a-form-item label="读取超时">
                <a-input-number v-model:value="form.readTimeoutMs" :min="100" :max="300000" :step="1000" style="width:100%" />
                <span class="lwc-hint">毫秒，100 ~ 300000；提交任务本身应当很快，慢说明转换器有问题</span>
              </a-form-item>
            </a-col>
            <a-col :xs="12" :md="8">
              <a-form-item label="源文件大小上限">
                <a-input-number v-model:value="form.maxSourceSizeMb" :min="1" :max="4096" :step="10" style="width:100%" />
                <span class="lwc-hint">MB，1 ~ 4096；超限直接跳过，不进流水线</span>
              </a-form-item>
            </a-col>
            <a-col :xs="12" :md="8">
              <a-form-item label="最大尝试次数">
                <a-input-number v-model:value="form.maxAttempts" :min="1" :max="10" style="width:100%" />
                <span class="lwc-hint">同一内容指纹超过此次数即熔断，避免畸形文件反复烧 CPU</span>
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="16">
              <a-form-item label="支持的扩展名">
                <a-input v-model:value="form.supportedExtensions" placeholder=".step,.stp,.iges,.igs,.stl" allow-clear />
                <span class="lwc-hint">逗号分隔，不区分大小写，保存时统一规范成小写带点；留空表示不限制</span>
              </a-form-item>
            </a-col>
          </a-row>
        </a-card>

        <!-- 看门狗 -->
        <a-card size="small" class="lwc-card" title="兜底扫描（看门狗）">
          <div class="lwc-hint lwc-hint-block">
            回调可能因为网络、重启、令牌不一致而丢失。看门狗定时把「提交后长时间没有下文」的任务找出来，
            主动向转换器问一次结果 —— 问到了就按回调同样的逻辑收下，问不到才判失败。
          </div>

          <a-form-item label="启用看门狗">
            <a-switch v-model:checked="form.watchdogEnabled" />
            <span class="lwc-hint">关掉后只依赖回调；回调丢了任务会一直停在「转换中」</span>
          </a-form-item>

          <a-row :gutter="16">
            <a-col :xs="12" :md="8">
              <a-form-item label="扫描间隔">
                <a-input-number v-model:value="form.watchdogIntervalSec" :min="10" :max="3600" :step="10" style="width:100%" />
                <span class="lwc-hint">秒，10 ~ 3600；改完立即按新间隔节流，不必重启</span>
              </a-form-item>
            </a-col>
            <a-col :xs="12" :md="8">
              <a-form-item label="判定超时">
                <a-input-number v-model:value="form.staleAfterMinutes" :min="1" :max="1440" style="width:100%" />
                <span class="lwc-hint">分钟，1 ~ 1440；提交后超过这么久仍无结果才算卡住</span>
              </a-form-item>
            </a-col>
            <a-col :xs="12" :md="8">
              <a-form-item label="单轮扫描条数">
                <a-input-number v-model:value="form.watchdogBatchSize" :min="1" :max="500" style="width:100%" />
                <span class="lwc-hint">1 ~ 500；积压很多时也别一次问太多，免得压垮转换器</span>
              </a-form-item>
            </a-col>
          </a-row>
        </a-card>
      </a-form>
    </a-spin>

    <div class="lwc-footer">
      <a-space :size="12" wrap>
        <a-button v-if="canEdit" type="primary" :loading="saving" @click="save">
          <SaveOutlined /> 保存并生效
        </a-button>
        <a-button :loading="loading" @click="load">重新读取</a-button>
        <span v-if="cfg.updatedAt" class="lwc-footer-meta">
          最后更新 {{ formatTime(cfg.updatedAt) }}<template v-if="cfg.updater"> · {{ cfg.updater }}</template>
        </span>
      </a-space>
    </div>

    <!-- 连接测试结果 -->
    <a-modal v-model:open="testVisible" title="转换器连接测试" :footer="null" width="600px">
      <a-alert
        class="lwc-test-alert"
        type="info"
        show-icon
        message="测试的是已保存并生效的配置"
        description="改了地址/超时请先保存再测，否则测的还是旧地址。"
      />

      <template v-if="testResult">
        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="连通">
            <a-tag :color="testResult.ok ? 'green' : 'red'">{{ testResult.ok ? '正常' : '不通' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="耗时">
            {{ testResult.latencyMs != null ? testResult.latencyMs + ' ms' : '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="探测地址" :span="2">
            {{ testResult.baseUrl || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="转换引擎">
            <a-tag :color="engineColor">{{ testResult.engine || '-' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="并发上限">
            {{ testResult.maxConcurrency ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="车道分配">
            {{ laneText }}
          </a-descriptions-item>
          <a-descriptions-item label="排队 / 运行中">
            {{ (testResult.queued ?? 0) + ' / ' + (testResult.running ?? 0) }}
          </a-descriptions-item>
        </a-descriptions>

        <a-alert
          v-if="testResult.ok && isMockEngine"
          class="lwc-test-alert"
          type="warning"
          show-icon
          message="当前跑的是 mock 引擎"
          description="转换器启动时没加载到 OCCT，产出的是演示几何而不是源文件的真实模型。请用装了 OCC 的解释器重启转换器。"
        />
        <a-alert
          v-if="testResult.error"
          class="lwc-test-alert"
          type="error"
          show-icon
          message="连接失败"
          :description="testResult.error"
        />
      </template>
      <div v-else class="lwc-test-loading">
        <a-spin size="small" /> <span>正在探测 /healthz…</span>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ApiOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { getCadForgeConfig, updateCadForgeConfig, testCadForgeConnection } from '@/api'

const userStore = useUserStore()
const canEdit = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const cfg = ref({})
const testVisible = ref(false)
const testResult = ref(null)

// watchdogIntervalMs 在库里是毫秒，表单按秒填：120000 比"两分钟"更不容易填错数量级
const defaultForm = () => ({
  enabled: false,
  baseUrl: '',
  callbackUrl: '',
  callbackToken: '',
  connectTimeoutMs: 3000,
  readTimeoutMs: 10000,
  maxSourceSizeMb: 200,
  supportedExtensions: '.step,.stp,.iges,.igs,.stl',
  maxAttempts: 3,
  watchdogEnabled: true,
  watchdogIntervalSec: 120,
  staleAfterMinutes: 15,
  watchdogBatchSize: 50,
})
const form = ref(defaultForm())

// healthz 的 engine 可能是 mock / mock-xxx / occt / occt-7.7.2：按前缀判，别只盯等于 'mock'
const isMockEngine = computed(() => /^mock/i.test(String(testResult.value?.engine || '')))
const engineColor = computed(() => (isMockEngine.value ? 'orange' : 'green'))
const laneText = computed(() => {
  const lanes = testResult.value?.lanes
  if (!lanes || typeof lanes !== 'object') return '-'
  return Object.entries(lanes).map(([k, v]) => `${k}: ${v}`).join(' / ') || '-'
})

async function load() {
  loading.value = true
  try {
    const res = await getCadForgeConfig()
    if (res.code === 200 && res.data) {
      cfg.value = res.data
      form.value = {
        enabled: !!res.data.enabled,
        baseUrl: res.data.baseUrl || '',
        callbackUrl: res.data.callbackUrl || '',
        // 令牌不回传明文：留空 = 不改
        callbackToken: '',
        connectTimeoutMs: res.data.connectTimeoutMs ?? 3000,
        readTimeoutMs: res.data.readTimeoutMs ?? 10000,
        maxSourceSizeMb: res.data.maxSourceSizeMb ?? 200,
        supportedExtensions: res.data.supportedExtensions || '',
        maxAttempts: res.data.maxAttempts ?? 3,
        watchdogEnabled: res.data.watchdogEnabled !== false,
        watchdogIntervalSec: Math.max(10, Math.round((res.data.watchdogIntervalMs ?? 120000) / 1000)),
        staleAfterMinutes: res.data.staleAfterMinutes ?? 15,
        watchdogBatchSize: res.data.watchdogBatchSize ?? 50,
      }
    } else {
      message.error(res.message || '读取配置失败')
    }
  } catch {
    message.error('读取配置失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  const f = form.value
  if (!f.baseUrl || !/^https?:\/\//.test(f.baseUrl.trim())) {
    return message.warning('转换器地址不能为空，且必须以 http:// 或 https:// 开头')
  }
  if (f.callbackUrl && !/^https?:\/\//.test(f.callbackUrl.trim())) {
    return message.warning('回调地址必须以 http:// 或 https:// 开头')
  }

  saving.value = true
  try {
    const res = await updateCadForgeConfig({
      enabled: f.enabled,
      baseUrl: f.baseUrl.trim(),
      callbackUrl: f.callbackUrl ? f.callbackUrl.trim() : '',
      callbackToken: f.callbackToken ? f.callbackToken.trim() : '',
      connectTimeoutMs: f.connectTimeoutMs,
      readTimeoutMs: f.readTimeoutMs,
      maxSourceSizeMb: f.maxSourceSizeMb,
      supportedExtensions: f.supportedExtensions,
      maxAttempts: f.maxAttempts,
      watchdogEnabled: f.watchdogEnabled,
      staleAfterMinutes: f.staleAfterMinutes,
      watchdogBatchSize: f.watchdogBatchSize,
      watchdogIntervalMs: Math.round(f.watchdogIntervalSec * 1000),
    })
    if (res.code === 200) {
      message.success('已保存，配置立即生效')
      await load()
    } else {
      message.error(res.message || '保存失败')
    }
  } catch {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  testing.value = true
  testResult.value = null
  testVisible.value = true
  try {
    const res = await testCadForgeConnection()
    if (res.code === 200) {
      testResult.value = res.data || { ok: false, error: '转换器没有返回内容' }
    } else {
      testResult.value = { ok: false, error: res.message || '测试失败' }
    }
  } catch {
    testResult.value = { ok: false, error: '请求失败，请检查后端日志' }
  } finally {
    testing.value = false
  }
}

function formatTime(str) {
  if (!str) return '-'
  return String(str).replace('T', ' ').substring(0, 19)
}

onMounted(load)
</script>

<style scoped>
.lwc-page {
  max-width: 960px;
}

.lwc-intro {
  margin-bottom: 16px;
  padding: 10px 14px;
  background: #f6f9ff;
  border-left: 3px solid #1677ff;
  border-radius: 4px;
  font-size: 13px;
  color: #595959;
  line-height: 1.7;
}

.lwc-alert {
  margin-bottom: 16px;
}

.lwc-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.lwc-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.6;
}

.lwc-hint-block {
  margin: 0 0 16px;
}

/* 底部操作条：表单很长，保存按钮跟着滚动到底才出现太费事 */
.lwc-footer {
  position: sticky;
  bottom: 0;
  padding: 12px 0 8px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}

.lwc-footer-meta {
  font-size: 12px;
  color: #bfbfbf;
}

.lwc-test-alert {
  margin-bottom: 12px;
}

.lwc-test-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  font-size: 13px;
  color: #8c8c8c;
}
</style>
