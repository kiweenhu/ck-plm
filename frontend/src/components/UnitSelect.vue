<template>
  <a-select
    v-model:value="selectedValue"
    :loading="loading"
    :show-search="true"
    :allow-clear="allowClear"
    :placeholder="placeholder"
    :disabled="disabled"
    :filter-option="filterOption"
    style="width: 100%"
    @change="onChange"
  >
    <a-select-opt-group v-for="group in unitGroups" :key="group.quantityType" :label="group.displayName">
      <a-select-option v-for="unit in group.units" :key="unit.name" :value="unit.name">
        <span>{{ unit.display || unit.name }}</span>
        <code v-if="unit.name !== unit.display" style="margin-left:4px;font-size:11px;color:#8c8c8c">{{ unit.name }}</code>
      </a-select-option>
    </a-select-opt-group>
  </a-select>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { Select, SelectOption, SelectOptGroup, message } from 'ant-design-vue'
import { getUnitListGrouped } from '@/api'

// ==================== Props & Emits ====================

const props = defineProps({
  modelValue: { type: String, default: undefined },
  placeholder: { type: String, default: '请选择单位' },
  disabled: { type: Boolean, default: false },
  allowClear: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue', 'change'])

// ==================== 内部状态 ====================

const selectedValue = ref(props.modelValue)
const loading = ref(false)
const unitGroups = ref([])

// 双向绑定
watch(() => props.modelValue, (val) => {
  if (val !== selectedValue.value) selectedValue.value = val
})

watch(selectedValue, (val) => {
  emit('update:modelValue', val)
})

// ==================== 搜索过滤 ====================

function filterOption(input, option) {
  // option.value 是 unit.name（如 kg），option.label 是 slot 内容（不好直接匹配）
  // 需要同时匹配 name 和 display
  const keyword = input.toLowerCase()
  const allUnits = unitGroups.value.flatMap(g => g.units)
  const found = allUnits.find(u => u.name === option.value)
  if (!found) return false
  return (found.name || '').toLowerCase().includes(keyword)
    || (found.display || '').toLowerCase().includes(keyword)
}

// ==================== 事件 ====================

function onChange(value) {
  const allUnits = unitGroups.value.flatMap(g => g.units)
  const unit = allUnits.find(u => u.name === value)
  emit('change', unit ? { name: unit.name, display: unit.display, quantityType: unit.quantityType } : null)
}

// ==================== 加载数据 ====================

async function loadUnits() {
  loading.value = true
  try {
    const res = await getUnitListGrouped()
    const data = res?.data || res || {}
    // data 格式：{ "DISCRETE": [...], "MASS": [...], ... }
    const quantityTypeNames = {
      DISCRETE: '离散计数', MASS: '质量', LENGTH: '长度', AREA: '面积',
      VOLUME: '体积', TIME: '时间', TEMPERATURE: '温度', ANGLE: '角度',
      ELECTRIC_CURRENT: '电流', LUMINOUS_INTENSITY: '发光强度'
    }
    unitGroups.value = Object.entries(data).map(([qt, units]) => ({
      quantityType: qt,
      displayName: quantityTypeNames[qt] || qt,
      units: Array.isArray(units) ? units : []
    })).filter(g => g.units.length > 0)
  } catch {
    message.error('加载单位列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadUnits)

defineExpose({ loadUnits, selectedValue })
</script>
