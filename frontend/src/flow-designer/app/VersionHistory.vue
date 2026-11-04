<template>
  <a-drawer :open="open" title="版本历史" width="560" @close="emit('close')">
    <!-- 跨版本对比：哪两版、以谁为基线由用户选（默认「上一版 → 最新版」） -->
    <div class="compare-bar">
      <span class="muted">对比</span>
      <a-select v-model:value="compareFrom" size="small" style="width: 88px" :options="versionOptions" />
      <span class="muted">→</span>
      <a-select v-model:value="compareTo" size="small" style="width: 88px" :options="versionOptions" />
      <a-button size="small" :disabled="!canCompare" @click="openCompare(compareFrom, compareTo)">对比</a-button>
    </div>
    <a-table
      :data-source="versions"
      :columns="columns"
      :loading="loading"
      :pagination="false"
      row-key="oid"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'version'">
          <a-tag :color="record.version === currentVersion ? 'blue' : 'default'">v{{ record.version }}</a-tag>
        </template>
        <template v-else-if="column.key === 'deployed'">
          <a-tag v-if="record.deployed" color="success">已部署</a-tag>
          <span v-else class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space size="small">
            <a @click="load(record)">载入画布</a>
            <a v-if="record.bpmnXml" @click="viewXml(record)">查看 BPMN</a>
            <a
              v-if="previousVersionOf(record)"
              @click="openCompare(previousVersionOf(record), record.version)"
            >对比上一版</a>
          </a-space>
        </template>
      </template>
    </a-table>
    <div class="history-hint">
      载入历史版本会把该版本的 DSL 放入画布（标记为未保存改动）；如需回退，载入后点「保存」生成新版本。<br />
      只是看看、不想改的话，点工具栏「转至最新版」即可回到最新版（不产生新版本）。<br />
      已部署的版本不可删除（流程引擎仍引用它）：流程一旦部署过就不能删除，不再使用请改为「停止部署」。
    </div>

    <a-modal v-model:open="xmlVisible" title="该版本的 BPMN 部署快照" width="840px" :footer="null">
      <pre class="history-xml">{{ xmlContent }}</pre>
    </a-modal>

    <VersionDiffModal
      :open="compareVisible"
      :template-oid="templateOid"
      :from-version="compareFrom"
      :to-version="compareTo"
      :from-note="noteOf(compareFrom)"
      :to-note="noteOf(compareTo)"
      @close="compareVisible = false"
    />
  </a-drawer>
</template>

<script setup>
/**
 * 版本历史抽屉。
 *
 * <p>载入历史版本<b>不直接覆盖后端</b>，只把 DSL 放进画布并标记为改动 ——
 * 于是「回退」也是一次可审计的保存（生成新版本），而不是把历史抹掉。
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { templateRepository } from './template-service'
import VersionDiffModal from './VersionDiffModal.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  templateOid: { type: String, default: null },
  currentVersion: { type: Number, default: null },
})

const emit = defineEmits(['close', 'load'])

const versions = ref([])
const loading = ref(false)
const xmlVisible = ref(false)
const xmlContent = ref('')

// ==================== 版本对比（M3）====================

/** 对比弹窗：基线版本 / 目标版本（null = 还没选，或版本不足两个） */
const compareVisible = ref(false)
const compareFrom = ref(null)
const compareTo = ref(null)

const versionOptions = computed(() =>
  versions.value.map((version) => ({ value: version.version, label: `v${version.version}` })),
)
const canCompare = computed(
  () => !!compareFrom.value && !!compareTo.value && compareFrom.value !== compareTo.value,
)

/** 版本号 → 变更说明（弹窗标题下面那行「说明 A → 说明 B」） */
function noteOf(version) {
  return versions.value.find((item) => item.version === version)?.changeNote ?? ''
}

/** 行内「对比上一版」：返回比该版本小的最大版本号，没有更早版本时返回 null */
function previousVersionOf(record) {
  const smaller = versions.value
    .filter((item) => item.version < record.version)
    .map((item) => item.version)
  return smaller.length ? Math.max(...smaller) : null
}

function openCompare(from, to) {
  if (!from || !to || from === to) {
    return
  }
  compareFrom.value = from
  compareTo.value = to
  compareVisible.value = true
}

/**
 * 默认对比「上一版 → 最新版」；用户已选过的两版若都还在，就不动他的选择
 * （刷新版本列表不该把正在看的对比重置掉）。
 */
function syncCompareDefaults() {
  const numbers = versions.value.map((item) => item.version).sort((a, b) => a - b)
  if (numbers.length < 2) {
    compareFrom.value = null
    compareTo.value = null
    return
  }
  if (numbers.includes(compareFrom.value) && numbers.includes(compareTo.value)) {
    return
  }
  compareFrom.value = numbers[numbers.length - 2]
  compareTo.value = numbers[numbers.length - 1]
}

const columns = [
  { title: '版本', key: 'version', width: 80 },
  { title: '变更说明', dataIndex: 'changeNote', key: 'changeNote' },
  { title: '部署', key: 'deployed', width: 90 },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 210 },
]

async function reload() {
  if (!props.templateOid) {
    versions.value = []
    syncCompareDefaults()
    return
  }
  loading.value = true
  const result = await templateRepository.versions(props.templateOid)
  loading.value = false
  if (result.ok) {
    // 新版本在前，便于查看最近改动
    versions.value = [...(result.data ?? [])].sort((a, b) => (b.version ?? 0) - (a.version ?? 0))
    syncCompareDefaults()
  }
}

watch(
  () => [props.open, props.templateOid],
  ([isOpen]) => {
    if (isOpen) {
      reload()
    }
  },
)

async function load(record) {
  const result = await templateRepository.loadVersion(props.templateOid, record.version)
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  emit('load', { dsl: result.data.dsl, version: record.version })
  message.success(`已载入 v${record.version}，保存后生效`)
}

function viewXml(record) {
  xmlContent.value = record.bpmnXml ?? ''
  xmlVisible.value = true
}
</script>

<style scoped>
.muted {
  color: #8c8c8c;
}
/* 版本对比的工具条：两版下拉 + 对比按钮，与表格同宽、留一点呼吸 */
.compare-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.history-hint {
  margin-top: 12px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.7;
}
.history-xml {
  max-height: 460px;
  overflow: auto;
  font-size: 12px;
  background: #fafafa;
  padding: 10px;
  border-radius: 6px;
}
</style>
