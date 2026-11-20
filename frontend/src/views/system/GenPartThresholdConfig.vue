<template>
  <div class="gpt-config">
    <div class="gpt-intro">
      企业自研的结构件被<b>多个型号反复引用</b>之后，才值得被认定为「通用件」（可跨产品系列/型号重用）。
      这里配置的就是"多到什么程度算够"，以及认定走哪条流程。阈值已达成为硬门槛：
      物料清单里未达标的零件不会打开「升级为通用件」发起页，直接调发起接口也会被后端拦下。
    </div>

    <a-form layout="vertical" :model="form" style="max-width:640px">
      <a-form-item label="启用通用件认定统计">
        <a-switch v-model:checked="form.enabled" />
        <span class="gpt-hint">关掉后只保留配置，不再产生候选与通知</span>
      </a-form-item>

      <a-form-item label="跨型号数阈值">
        <a-input-number v-model:value="form.minModelCount" :min="1" :step="1" style="width:160px" />
        <span class="gpt-hint">被多少个<b>不同型号</b>引用才可发起（默认 3）。型号按产品系列/型号容器里 type=PART 的顶层对象去重，不按容器去重；同时满足两个阈值才放行</span>
      </a-form-item>

      <a-form-item label="引用次数阈值">
        <a-input-number v-model:value="form.minUsageCount" :min="1" :step="1" style="width:160px" />
        <span class="gpt-hint">在统计窗口内被多少条 <b>BOM 行</b>引用才可发起（默认 5）</span>
      </a-form-item>

      <a-form-item label="统计窗口（月）">
        <a-input-number v-model:value="form.statWindowMonths" :min="0" :step="1" style="width:160px" />
        <span class="gpt-hint">只看近 N 个月的引用；0 = 不限时间。口径按 BOM 行创建时间，修订重建 BOM 会刷新该时间</span>
      </a-form-item>

      <a-form-item label="适用类型">
        <a-select v-model:value="form.scopeTypeCodes" mode="multiple" allow-clear style="width:320px" :options="scopeOptions" />
        <span class="gpt-hint">可多选，命中任一类型的零件才进入认定 / 可发起升级（默认仅结构件）</span>
      </a-form-item>

      <a-form-item label="认定流程模板">
        <a-select
          v-model:value="form.processTemplateOid"
          allow-clear
          show-search
          option-filter-prop="label"
          placeholder="选择流程模板（留空 = 只通知、不自动发起）"
          style="width:320px"
          :options="processTemplateOptions"
        />
        <span class="gpt-hint">达到阈值后按此模板发起认定流程，流程结束回写类型并迁入通用件库</span>
      </a-form-item>

      <a-form-item label="说明">
        <a-textarea v-model:value="form.description" :rows="2" placeholder="写给下一个看这份配置的人" />
      </a-form-item>

      <a-button type="primary" :loading="saving" @click="save">
        <SaveOutlined /> 保存配置
      </a-button>
    </a-form>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { SaveOutlined } from '@ant-design/icons-vue'
import {
  getGenPartThresholdConfig,
  saveGenPartThresholdConfig,
  listProcessTemplates,
} from '@/api'

const saving = ref(false)
const processTemplateOptions = ref([])

const scopeOptions = [
  { value: 'STRUCTURAL', label: '结构件（企业自研结构件）' },
  { value: 'ELECTRICAL', label: '电气件' },
]

const form = ref({
  enabled: true,
  minModelCount: 3,
  minUsageCount: 5,
  statWindowMonths: 12,
  scopeTypeCodes: ['STRUCTURAL'],
  processTemplateOid: null,
  description: '',
})

onMounted(async () => {
  await Promise.all([load(), loadProcessTemplates()])
})

async function load() {
  try {
    const res = await getGenPartThresholdConfig()
    if (res?.code === 200 && res.data) {
      form.value = {
        ...form.value,
        ...res.data,
        // 后端对外暴露 scopeTypeCodes 数组；老数据没有时从 CSV 单值拆出来
        scopeTypeCodes: Array.isArray(res.data.scopeTypeCodes)
          ? res.data.scopeTypeCodes
          : String(res.data.scopeTypeCode || 'STRUCTURAL').split(',').map((s) => s.trim()).filter(Boolean),
        // 空串统一成 null，否则下拉框会显示成一个空选项
        processTemplateOid: res.data.processTemplateOid || null,
      }
    } else {
      message.error(res?.message || '读取配置失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '读取配置失败')
  }
}

/** 流程模板下拉：复用流程引擎的模板清单（与「类型-状态绑流程」用的是同一个接口） */
async function loadProcessTemplates() {
  try {
    const res = await listProcessTemplates({})
    const list = res?.data || res || []
    processTemplateOptions.value = (Array.isArray(list) ? list : [])
      .filter((t) => t?.oid)
      .map((t) => ({
        value: t.oid,
        label: t.displayName || t.name || t.key || t.oid,
      }))
  } catch {
    processTemplateOptions.value = []
  }
}

async function save() {
  saving.value = true
  try {
    const res = await saveGenPartThresholdConfig({
      ...form.value,
      scopeTypeCodes: form.value.scopeTypeCodes || [],
      // 两个字段一起发，值与数组一致，后端无论先解哪个 setter 都不会互相覆盖
      scopeTypeCode: (form.value.scopeTypeCodes || []).join(','),
      processTemplateOid: form.value.processTemplateOid || null,
    })
    if (res?.code === 200) {
      message.success('已保存通用件阈值配置')
      if (res.data) form.value = { ...form.value, ...res.data }
    } else {
      message.error(res?.message || '保存失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.gpt-config {
  padding: 4px 8px;
}
.gpt-intro {
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  font-size: 12px;
  color: #595959;
  line-height: 1.8;
  max-width: 720px;
}
.gpt-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #8c8c8c;
}
</style>
