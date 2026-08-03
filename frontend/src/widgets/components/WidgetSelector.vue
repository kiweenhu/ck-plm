<template>
  <a-select
    :value="modelValue"
    size="small"
    @change="onChange"
  >
    <a-select-option
      v-for="w in fieldWidgets"
      :key="w.type"
      :value="w.type"
    >
      {{ w.label }} {{ w.type }}
    </a-select-option>
  </a-select>
</template>

<script setup>
import { WIDGETS, getFieldWidgetTypes } from '../catalog'

defineProps({
  modelValue: { type: String, default: 'input' }
})
const emit = defineEmits(['update:modelValue', 'change'])

function onChange(val) {
  emit('update:modelValue', val)
  emit('change', val)
}

/** 可作为字段控件使用的 widget 列表（排除容器） */
const fieldWidgets = WIDGETS.filter(w => !w.special)
</script>
