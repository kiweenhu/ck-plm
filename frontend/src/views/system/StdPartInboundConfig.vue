<template>
  <div class="spi-config">
    <div class="spi-intro">
      标准件（国标/行标件）入<b>「标准件库」</b>时，要不要先过一道流程 ——
      由标准化岗确认标准号与规格、确认库内没有重复件 —— 再正式入库。
      关掉即「直接入库」；开启则必须绑定一条流程模板。
    </div>

    <a-form layout="vertical" :model="form" style="max-width:640px">
      <a-form-item label="启用标准件入库流程">
        <a-switch v-model:checked="form.enabled" />
        <span class="spi-hint">开启后必须选择一个流程模板</span>
      </a-form-item>

      <a-form-item label="入库流程模板">
        <a-select
          v-model:value="form.processTemplateOid"
          allow-clear
          show-search
          option-filter-prop="label"
          placeholder="选择流程模板"
          style="width:320px"
          :options="processTemplateOptions"
        />
        <span class="spi-hint">标准件入库时按此模板发起流程，流程通过后才进库</span>
      </a-form-item>

      <a-form-item label="说明">
        <a-textarea
          v-model:value="form.description"
          :rows="2"
          placeholder="例如：哪些标准件可免走流程、由谁确认"
        />
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
  getStdPartInboundConfig,
  saveStdPartInboundConfig,
  listProcessTemplates,
} from '@/api'

const saving = ref(false)
const processTemplateOptions = ref([])

const form = ref({
  enabled: false,
  processTemplateOid: null,
  description: '',
})

onMounted(async () => {
  await Promise.all([load(), loadProcessTemplates()])
})

async function load() {
  try {
    const res = await getStdPartInboundConfig()
    if (res?.code === 200 && res.data) {
      form.value = {
        ...form.value,
        ...res.data,
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

/** 流程模板下拉：复用流程引擎的模板清单（与「类型-状态绑流程」「通用件认定流程」同一接口） */
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
  if (form.value.enabled && !form.value.processTemplateOid) {
    message.warning('启用入库流程时必须选择一个流程模板')
    return
  }
  saving.value = true
  try {
    const res = await saveStdPartInboundConfig({
      ...form.value,
      processTemplateOid: form.value.processTemplateOid || null,
    })
    if (res?.code === 200) {
      message.success('已保存标准件入库流程配置')
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
.spi-config {
  padding: 4px 8px;
}
.spi-intro {
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
.spi-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #8c8c8c;
}
</style>
