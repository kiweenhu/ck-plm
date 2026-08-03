<template>
  <div class="cifp-root">
    <a-spin :spinning="loading" tip="加载中...">
      <!-- 加载失败或无布局 -->
      <a-empty
        v-if="!loading && !layoutData"
        description="未配置表单布局，请先设计布局"
      >
        <a-button type="primary" @click="$emit('design-layout')">去设计布局</a-button>
      </a-empty>

      <!-- 表单 -->
      <a-form
        v-else-if="layoutData"
        ref="formRef"
        :model="formData"
        layout="vertical"
        class="cifp-form"
      >
        <!-- 表单头部（编辑/详情时显示分类信息） -->
        <div v-if="operationCode !== 'create' && classification" class="cifp-header">
          <span class="cifp-header-name">{{ classification.name || '分类节点' }}</span>
          <code class="cifp-header-code">{{ classification.identifier || classification.code }}</code>
        </div>

        <RenderFields
          :fields="displayFields"
          :form-data="formData"
          @update="onFieldUpdate"
        />

        <!-- 保存按钮（详情模式不显示） -->
        <div v-if="operationCode !== 'detail'" class="cifp-actions">
          <a-space>
            <a-button type="primary" :loading="saving" @click="handleSave">
              <template #icon><SaveOutlined /></template>
              保存
            </a-button>
          </a-space>
        </div>
      </a-form>
    </a-spin>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import { SaveOutlined } from '@ant-design/icons-vue'
import RenderFields from '@/components/RenderFields.js'
import {
  getClassification, getClassificationIBAs,
  getClassificationIBAValues, saveClassificationIBAValues,
  getClsIbaLayout,
} from '@/api'

const props = defineProps({
  /** 分类 OID */
  classificationOid: { type: String, required: true },
  /** 操作编码：create / update / detail */
  operationCode: { type: String, required: true },
})

const emit = defineEmits(['design-layout', 'saved', 'cancel'])

// ===== 状态 =====
const formRef = ref(null)
const loading = ref(false)
const saving = ref(false)
const layoutData = ref(null)
const classification = ref(null)
const formData = reactive({})

// 详情模式：标记所有字段为只读
const displayFields = computed(() => {
  const fields = layoutData.value?.form?.fields || []
  if (props.operationCode === 'detail') {
    return fields.map(f => ({ ...f, readonly: true }))
  }
  return fields
})

// ===== 加载布局和数据 =====
async function loadAll() {
  if (!props.classificationOid) return
  loading.value = true
  try {
    // 1. 加载布局
    await loadLayout()

    // 2. 加载分类基本信息
    try {
      const clsRes = await getClassification(props.classificationOid)
      classification.value = clsRes?.data || clsRes || null
    } catch { classification.value = null }

    // 3. 加载 IBA 关联定义
    const ibaRes = await getClassificationIBAs(props.classificationOid)
    const ibaList = ibaRes?.data || ibaRes || []

    // 4. 加载已存的 IBA 值（编辑/详情模式）
    if (props.operationCode !== 'create') {
      try {
        const valuesRes = await getClassificationIBAValues(props.classificationOid)
        const savedValues = valuesRes?.data || valuesRes || {}
        // 填充表单数据
        Object.keys(formData).forEach(k => delete formData[k])
        for (const iba of (Array.isArray(ibaList) ? ibaList : [])) {
          const code = iba.ibaCode || iba.code
          if (!code) continue
          const val = savedValues[code]
          formData[code] = val !== undefined && val !== null ? val : getDefaultValue(iba)
        }
        // 也填充布局默认值
        initLayoutDefaults()
      } catch {
        initLayoutDefaults()
      }
    } else {
      // 新建模式：使用默认值
      Object.keys(formData).forEach(k => delete formData[k])
      initLayoutDefaults()
    }
  } catch {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function loadLayout() {
  try {
    const res = await getClsIbaLayout(props.classificationOid, props.operationCode)
    const layout = res?.data || res
    if (layout?.layoutJson) {
      let json = layout.layoutJson
      if (typeof json === 'string') {
        try { json = JSON.parse(json) } catch { json = null }
      }
      layoutData.value = json
      return
    }
  } catch { /* ignore */ }
  layoutData.value = null
}

/** 根据布局字段默认值初始化表单 */
function initLayoutDefaults() {
  const fields = layoutData.value?.form?.fields || []
  for (const field of fields) {
    if (field.fieldName && field.defaultValue !== undefined && field.defaultValue !== '') {
      if (!(field.fieldName in formData)) {
        formData[field.fieldName] = field.defaultValue
      }
    }
  }
}

function getDefaultValue(iba) {
  const dtype = (iba.ibaDataType || iba.dataType || 'STRING').toUpperCase()
  if (dtype === 'BOOLEAN') return false
  return ''
}

// ===== 事件 =====
function onFieldUpdate(key, val) {
  formData[key] = val
}

// ===== 公开方法 =====
function getFormData() {
  return { ...formData }
}

async function handleSave() {
  const values = {}
  const fields = layoutData.value?.form?.fields || []
  for (const field of fields) {
    if (!field.fieldName) continue
    const val = formData[field.fieldName]
    if (val !== undefined) {
      values[field.fieldName] = val
    }
  }

  saving.value = true
  try {
    await saveClassificationIBAValues(props.classificationOid, values)
    message.success('保存成功')
    emit('saved', values)
  } catch (e) {
    message.error(e?.response?.data?.message || '保存失败')
    throw e
  } finally {
    saving.value = false
  }
}

defineExpose({ getFormData, handleSave })

// ===== 监听 =====
watch(() => [props.classificationOid, props.operationCode], () => {
  if (props.classificationOid) loadAll()
})

onMounted(() => {
  if (props.classificationOid) loadAll()
})
</script>

<style scoped>
.cifp-root {
  min-height: 100px;
}

.cifp-form {
  max-width: 900px;
}

.cifp-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  margin-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.cifp-header-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
}

.cifp-header-code {
  font-size: 12px;
  color: #8c8c8c;
  background: #f5f5f5;
  padding: 1px 8px;
  border-radius: 3px;
}

.cifp-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
