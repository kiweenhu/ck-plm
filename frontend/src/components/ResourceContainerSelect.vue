<template>
  <div class="rcs-wrap">
    <!--
      可编辑性只由上下文决定（组件自管）：
        未进入资源库（无已知条件）→ 可自由选择；
        已进入资源库              → 锁定为对应库，不可修改。
      因此这里不叠加 field.readonly / disabled —— 否则设计器一旦勾了「只读」，
      「无已知条件时可选」这一条就永远无法触发。
    -->
    <a-select
      :value="innerValue"
      :disabled="locked"
      :placeholder="placeholder"
      :options="options"
      :loading="loading"
      :allow-clear="!locked"
      :filter-option="filterOption"
      show-search
      style="width: 100%"
      @update:value="onChange"
    />
    <div v-if="locked" class="rcs-lock-hint">
      已进入「{{ lockedLibrary.name || lockedLibrary.code }}」，构建容器自动锁定为该库，不可修改
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { getResourceChildren } from '@/api'

/**
 * 构建容器选择（PageDesigner 业务组件：resource-container-select）
 *
 * 满足两个条件：
 * 1) <b>无已知条件</b>（尚未进入任何资源库）→ 可自由选择「企业资源库」下的任一子库
 *    （元器件库 / 标准件库 / 通用件库 / 封装·图符库 / 技术文档库 …，即 CORP_RESOURCE 根节点下的子容器）；
 * 2) <b>已进入某一个资源库</b> → 默认为对应的库，且不可修改。
 *    判定方式：父级传入的 {@code currentContainerOid} 命中某个子库的 oid。
 *
 * 取值约定：v-model 绑定<b>容器的 oid</b>（通常落在 containerOid 字段），
 * 选中同时通过 {@code update:containerType} 回传播该容器的 containerType，供 containerType 字段使用。
 */
const props = defineProps({
  value: { type: String, default: null },
  /** 保留以兼容 RenderFields 的通用传参；本组件的可编辑性由上下文（locked）决定，不使用此值 */
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '请选择构建容器' },
  /** 当前所处容器 oid：已进入资源库时由父级（DynamicForm.currentContainerOid）传入 */
  currentContainerOid: { type: String, default: null },
})
const emit = defineEmits(['update:value', 'update:containerType'])

const loading = ref(false)
const libraries = ref([])

/** 命中的资源库；为 null 表示「无已知条件」 */
const lockedLibrary = computed(() => {
  if (!props.currentContainerOid) return null
  return libraries.value.find(l => l.oid === props.currentContainerOid) || null
})
const locked = computed(() => !!lockedLibrary.value)

/** 已锁定 → 取命中库；否则用外部 v-model */
const innerValue = computed(() => (locked.value ? lockedLibrary.value.oid : (props.value || undefined)))

const options = computed(() =>
  libraries.value.map(l => ({ label: l.name || l.code, value: l.oid, code: l.code }))
)

function filterOption(input, option) {
  return String(option?.label || '').toLowerCase().includes(String(input || '').toLowerCase())
}

async function loadLibraries() {
  loading.value = true
  try {
    const res = await getResourceChildren()
    libraries.value = res?.data || res || []
  } catch {
    libraries.value = []
  } finally {
    loading.value = false
  }
  syncLocked()
}

/** 命中资源库时回填取值与 containerType（不可修改） */
function syncLocked() {
  if (!locked.value) return
  if (props.value !== lockedLibrary.value.oid) {
    emit('update:value', lockedLibrary.value.oid)
  }
  emit('update:containerType', lockedLibrary.value.containerType || 'CORP_RESOURCE')
}

function onChange(val) {
  if (locked.value || props.disabled) return
  emit('update:value', val || null)
  const lib = libraries.value.find(l => l.oid === val)
  emit('update:containerType', lib ? (lib.containerType || 'CORP_RESOURCE') : null)
}

watch(() => props.currentContainerOid, syncLocked)

onMounted(loadLibraries)
</script>

<style scoped>
.rcs-lock-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.4;
}
</style>
