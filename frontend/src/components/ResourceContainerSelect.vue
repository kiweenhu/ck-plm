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
    <div v-if="locked" class="rcs-lock-hint">{{ lockHint }}</div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { getResourceChildren, getProductModel, getProductLine } from '@/api'

/** 容器类型 → 可读文案：解析不出名称时的兜底（与 BOM 替代弹窗同一口径） */
const CONTAINER_TYPE_LABEL = {
  PRODUCT_LINE: '产品系列',
  LINE: '产品系列',
  PRODUCT_MODEL: '产品型号',
  MODEL: '产品型号',
  CORP_RESOURCE: '企业资源',
  FOLDER: '文件夹',
}

/**
 * 构建容器选择（PageDesigner 业务组件：resource-container-select）
 *
 * 三种情形：
 * 1) <b>无已知条件</b>（尚未进入任何库/产品）→ 可自由选择「企业资源库」下的任一子库
 *    （元器件库 / 标准件库 / 通用件库 / 封装·图符库 / 技术文档库 …，即 CORP_RESOURCE 根节点下的子容器）；
 * 2) <b>已进入某一个资源库</b> → 默认为该库，不可修改（判定：currentContainerOid 命中某个子库）；
 * 3) <b>容器由上下文给定但不是资源库</b>（在产品系列/型号下建零件 → containerOid 是产品 oid）→
 *    同样锁定不可改，并把 oid 解析成名称显示。
 *    早期这里只认得资源库，第 3 种情形下 a-select 找不到对应选项，就把 containerOid
 *    <b>原样当文本显示</b>（界面上出现一串 oid）；BOM 替代弹窗、本组件都踩过同一个坑。
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
  /** 容器类型（containerType 字段的值，可选）：解析不出名称时用来给出可读兜底文案 */
  containerType: { type: String, default: null },
})
const emit = defineEmits(['update:value', 'update:containerType'])

const loading = ref(false)
const libraries = ref([])
/** 非资源库容器的显示名（按 oid 解析一次） */
const externalLabel = ref(null)

/** 命中的资源库；为 null 表示「无已知条件」 */
const lockedLibrary = computed(() => {
  if (!props.currentContainerOid) return null
  return libraries.value.find(l => l.oid === props.currentContainerOid) || null
})

/** 取值是「非资源库容器」（产品系列/型号/文件夹…）：不是选项里的任何一项 */
const isExternal = computed(() =>
  !!props.value && !libraries.value.some(l => l.oid === props.value)
)

const locked = computed(() => !!lockedLibrary.value || isExternal.value)

/** 已锁定 → 取命中库；否则用外部 v-model */
const innerValue = computed(() => (lockedLibrary.value ? lockedLibrary.value.oid : (props.value || undefined)))

const lockHint = computed(() => {
  if (lockedLibrary.value) {
    return `已进入「${lockedLibrary.value.name || lockedLibrary.value.code}」，构建容器自动锁定为该库，不可修改`
  }
  if (isExternal.value) {
    return `构建容器已由当前上下文确定（${externalLabel.value || CONTAINER_TYPE_LABEL[props.containerType] || '当前容器'}），不可修改`
  }
  return ''
})

const options = computed(() => {
  const opts = libraries.value.map(l => ({ label: l.name || l.code, value: l.oid, code: l.code }))
  // 取值不在候选里（产品/文件夹等）时补一条兜底项：值仍是真 oid，文案换成名称或可读类型，
  // 避免 a-select 把内部标识直接显示出来
  if (isExternal.value && props.value) {
    const typeLabel = CONTAINER_TYPE_LABEL[props.containerType]
    opts.unshift({
      label: externalLabel.value || (typeLabel ? `${typeLabel}（当前容器）` : '当前容器'),
      value: props.value,
    })
  }
  return opts
})

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

/**
 * 解析非资源库容器的显示名：容器可能是产品型号或产品系列（零件在产品下创建时）。
 * 探测失败不影响取值，只是退回可读兜底文案。
 */
async function resolveExternalLabel() {
  const oid = props.value
  externalLabel.value = null
  if (!oid || libraries.value.some(l => l.oid === oid)) return
  for (const probe of [() => getProductModel(oid), () => getProductLine(oid)]) {
    try {
      const res = await probe()
      const data = res?.data ?? res
      const name = data?.name || data?.title
      if (name) {
        externalLabel.value = name
        return
      }
    } catch {
      /* 该 oid 不是这一类容器，继续探测下一种 */
    }
  }
}

/** 命中资源库时回填取值与 containerType（不可修改） */
function syncLocked() {
  if (!lockedLibrary.value) return
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
watch(() => props.value, resolveExternalLabel)

onMounted(async () => {
  await loadLibraries()
  await resolveExternalLabel()
})
</script>

<style scoped>
.rcs-lock-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.4;
}
</style>
