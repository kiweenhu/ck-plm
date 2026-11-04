<template>
  <a-modal :open="open" title="版本对比" width="880px" :footer="null" @cancel="emit('close')">
    <div v-if="loading" class="diff-loading">
      <a-spin size="small" /> 正在加载两个版本…
    </div>

    <a-alert v-else-if="error" type="warning" show-icon :message="error" />

    <template v-else-if="diff">
      <div class="diff-head">
        <a-tag>v{{ fromVersion }}</a-tag>
        <span class="arrow">→</span>
        <a-tag color="blue">v{{ toVersion }}</a-tag>
        <span v-if="fromNote || toNote" class="notes">
          {{ fromNote || '—' }} → {{ toNote || '—' }}
        </span>
      </div>

      <a-alert
        v-if="diff.empty"
        type="success"
        show-icon
        message="两个版本的语义完全一致"
        description="可能只调整了画布布局（坐标 / 折点 / 锚点）—— 这类改动不影响运行。"
      />

      <template v-else>
        <div class="diff-summary">
          <span class="chip added">新增 {{ diff.summary.added }}</span>
          <span class="chip removed">删除 {{ diff.summary.removed }}</span>
          <span class="chip changed">修改 {{ diff.summary.changed }}</span>
        </div>

        <div v-if="diff.dslVersion" class="diff-section">
          <div class="section-title">DSL 版本</div>
          <div class="diff-item changed">
            <span class="mark">~</span>
            <div class="item-body">
              <div class="item-title">{{ diff.dslVersion.label }}</div>
              <div class="change-line">
                {{ formatDiffValue(diff.dslVersion.before, diff.dslVersion.key) }}
                →
                {{ formatDiffValue(diff.dslVersion.after, diff.dslVersion.key) }}
              </div>
            </div>
          </div>
        </div>

        <div v-for="section in sections" :key="section.title" class="diff-section">
          <div v-if="section.items.length" class="section-title">
            {{ section.title }}<span class="count">{{ section.items.length }}</span>
          </div>
          <div
            v-for="item in section.items"
            :key="item.key"
            class="diff-item"
            :class="item.kind.toLowerCase()"
          >
            <span class="mark">{{ markOf(item.kind) }}</span>
            <div class="item-body">
              <div class="item-title">{{ item.title }}</div>
              <div v-for="line in item.lines" :key="line" class="change-line">{{ line }}</div>
            </div>
          </div>
        </div>
      </template>
    </template>
  </a-modal>
</template>

<script setup>
/**
 * 版本对比弹窗（M3「版本 diff」的展示层）。
 *
 * <p>职责边界：本组件只负责<b>取两个版本、把 `diffDsl` 的结果排成可读的清单</b>。
 * 差异怎么算（按 id 配对、忽略布局、字段展开）全在 `@flow-dsl-core` 的 `diffDsl` 里 ——
 * 那是纯函数、有单测；这里不重复任何判定，避免"界面算一套、测试算另一套"。
 */
import { computed, ref, watch } from 'vue'
import { DiffKind, diffDsl, formatDiffValue } from '@flow-dsl-core'
import { templateRepository } from './template-service'

const props = defineProps({
  open: { type: Boolean, default: false },
  templateOid: { type: String, default: null },
  fromVersion: { type: Number, default: null },
  toVersion: { type: Number, default: null },
  fromNote: { type: String, default: '' },
  toNote: { type: String, default: '' },
})

const emit = defineEmits(['close'])

const loading = ref(false)
const error = ref('')
const diff = ref(null)

const sections = computed(() => {
  const value = diff.value
  if (!value) {
    return []
  }
  return [
    {
      title: '流程属性',
      items: value.meta.map((item) => ({
        key: `meta-${item.key}`,
        kind: DiffKind.CHANGED,
        title: item.label,
        lines: [line(item.before, item.after, item.key)],
      })),
    },
    {
      title: '流程变量',
      items: value.variables.map((item) => ({
        key: `var-${item.name}`,
        kind: item.kind,
        title: `变量「${item.name}」`,
        lines: item.changes.map((change) => line(change.before, change.after, change.path, change.label)),
      })),
    },
    {
      title: '节点',
      items: value.nodes.map((item) => ({
        key: `node-${item.id}`,
        kind: item.kind,
        title: `节点「${item.name}」`,
        lines: item.changes.map((change) => line(change.before, change.after, change.path, change.label)),
      })),
    },
    {
      title: '连线',
      items: value.edges.map((item) => ({
        key: `edge-${item.id}`,
        kind: item.kind,
        title: `连线「${item.label}」`,
        lines: item.changes.map((change) => line(change.before, change.after, change.path, change.label)),
      })),
    },
  ]
})

/** 一行字段变化：「审批人·策略：指定角色 → 部门主管」 */
function line(before, after, path, label) {
  const text = `${formatDiffValue(before, path)} → ${formatDiffValue(after, path)}`
  return label ? `${label}：${text}` : text
}

function markOf(kind) {
  if (kind === DiffKind.ADDED) return '+'
  if (kind === DiffKind.REMOVED) return '−'
  return '~'
}

async function reload() {
  if (!props.open || !props.templateOid || !props.fromVersion || !props.toVersion) {
    return
  }
  loading.value = true
  error.value = ''
  diff.value = null
  const [fromResult, toResult] = await Promise.all([
    templateRepository.loadVersion(props.templateOid, props.fromVersion),
    templateRepository.loadVersion(props.templateOid, props.toVersion),
  ])
  loading.value = false
  if (!fromResult.ok || !toResult.ok) {
    error.value = (!fromResult.ok ? fromResult.error : '') || (!toResult.ok ? toResult.error : '') || '加载版本失败'
    return
  }
  diff.value = diffDsl(fromResult.data.dsl, toResult.data.dsl)
}

watch(() => [props.open, props.templateOid, props.fromVersion, props.toVersion], reload)
</script>

<style scoped>
.diff-loading {
  padding: 32px;
  text-align: center;
  color: #8c8c8c;
}
.diff-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.diff-head .arrow {
  color: #8c8c8c;
}
.diff-head .notes {
  margin-left: 8px;
  font-size: 12px;
  color: #8c8c8c;
}
.diff-summary {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}
.chip {
  padding: 2px 10px;
  font-size: 12px;
  border-radius: 10px;
}
.chip.added {
  color: #237804;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}
.chip.removed {
  color: #a8071a;
  background: #fff1f0;
  border: 1px solid #ffa39e;
}
.chip.changed {
  color: #096dd9;
  background: #e6f7ff;
  border: 1px solid #91d5ff;
}
.diff-section {
  margin-top: 14px;
}
.section-title {
  margin-bottom: 6px;
  font-weight: 600;
  font-size: 13px;
}
.section-title .count {
  margin-left: 6px;
  font-weight: 400;
  color: #8c8c8c;
}
.diff-item {
  display: flex;
  gap: 8px;
  padding: 6px 8px;
  border-left: 3px solid transparent;
  background: #fafafa;
  border-radius: 4px;
  margin-bottom: 4px;
}
.diff-item.added {
  border-left-color: #52c41a;
}
.diff-item.removed {
  border-left-color: #ff4d4f;
}
.diff-item.changed {
  border-left-color: #1677ff;
}
.mark {
  width: 12px;
  font-weight: 700;
  text-align: center;
}
.diff-item.added .mark {
  color: #52c41a;
}
.diff-item.removed .mark {
  color: #ff4d4f;
}
.diff-item.changed .mark {
  color: #1677ff;
}
.item-body {
  flex: 1;
  min-width: 0;
}
.item-title {
  font-size: 13px;
}
.change-line {
  font-size: 12px;
  color: #595959;
  line-height: 1.7;
  word-break: break-all;
}
</style>
