<template>
  <a-select
    v-model:value="selectedValue"
    :allow-clear="allowClear"
    :placeholder="placeholder"
    :disabled="disabled"
    style="width: 100%"
    @change="onChange"
  >
    <a-select-option v-for="opt in sourceOptions" :key="opt.value" :value="opt.value">
      {{ opt.label }}
    </a-select-option>
  </a-select>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Select, SelectOption } from 'ant-design-vue'

// ==================== Props & Emits ====================

const props = defineProps({
  modelValue: { type: String, default: undefined },
  placeholder: { type: String, default: '请选择来源' },
  disabled: { type: Boolean, default: false },
  allowClear: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue', 'change'])

// ==================== 固定下拉值 ====================

/** 来源选项：自制、委外、采购 */
const sourceOptions = [
  { label: '自制', value: '自制' },
  { label: '委外', value: '委外' },
  { label: '采购', value: '采购' },
]

// ==================== 内部状态 ====================

const selectedValue = ref(props.modelValue)

// 双向绑定
watch(() => props.modelValue, (val) => {
  if (val !== selectedValue.value) selectedValue.value = val
})

watch(selectedValue, (val) => {
  emit('update:modelValue', val)
})

// ==================== 事件 ====================

function onChange(value) {
  const opt = sourceOptions.find(o => o.value === value)
  emit('change', opt ? { value: opt.value, label: opt.label } : null)
}

defineExpose({ selectedValue, sourceOptions })
</script>
