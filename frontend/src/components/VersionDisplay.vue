<template>
  <div class="vd-display" :class="{ 'vd-display-empty': !version }">
    <BranchesOutlined class="vd-icon" :class="{ 'vd-icon-empty': !version }" />
    <template v-if="version">
      <a-tag color="blue" class="vd-tag">{{ version }}</a-tag>
      <span class="vd-hint">当前对象版本（只读）</span>
    </template>
    <span v-else class="vd-hint">由系统生成</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { BranchesOutlined } from '@ant-design/icons-vue'

/**
 * 构建版本显示（PageDesigner 业务组件：version-display）
 *
 * 只读展示，不可编辑，行为随表单场景自动切换：
 *   · 创建实体对象的 form 打开时（尚无版本）→ 显示「由系统生成」
 *   · 编辑页面（已有版本）                → 显示当前对象的对应版本
 *
 * 取值优先级：绑定值 displayVersion → formData.displayVersion → revision.iteration → revision。
 * 回退链用于容忍「编辑表单未把 displayVersion 放进 modelValue」的情况，
 * 确保编辑态一定能显示出当前对象版本，而不会误显示为「由系统生成」。
 */
const props = defineProps({
  /** 绑定值（displayVersion 字段） */
  value: { type: String, default: '' },
  /** 表单数据：displayVersion 缺失时回退 revision + iteration 拼装 */
  formData: { type: Object, default: () => ({}) },
  /** 是否禁用（只读展示，此属性保持接口兼容） */
  disabled: { type: Boolean, default: false },
})

const version = computed(() => {
  if (props.value) return String(props.value)
  const d = props.formData || {}
  if (d.displayVersion) return String(d.displayVersion)
  if (d.revision) return d.iteration ? `${d.revision}.${d.iteration}` : String(d.revision)
  return ''
})
</script>

<style scoped>
.vd-display {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 32px;
  padding: 4px 11px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #f5f5f5;
  font-size: 14px;
  line-height: 1.5715;
  cursor: default;
  user-select: none;
}

.vd-icon {
  font-size: 16px;
  color: #1677ff;
  flex-shrink: 0;
}

.vd-icon-empty {
  color: #bfbfbf;
}

.vd-tag {
  margin: 0;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.vd-hint {
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.vd-display-empty .vd-hint {
  color: #bfbfbf;
}
</style>
