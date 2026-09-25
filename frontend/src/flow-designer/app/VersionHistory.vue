<template>
  <a-drawer :open="open" title="版本历史" width="560" @close="emit('close')">
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
  </a-drawer>
</template>

<script setup>
/**
 * 版本历史抽屉。
 *
 * <p>载入历史版本<b>不直接覆盖后端</b>，只把 DSL 放进画布并标记为改动 ——
 * 于是「回退」也是一次可审计的保存（生成新版本），而不是把历史抹掉。
 */
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { templateRepository } from './template-service'

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

const columns = [
  { title: '版本', key: 'version', width: 80 },
  { title: '变更说明', dataIndex: 'changeNote', key: 'changeNote' },
  { title: '部署', key: 'deployed', width: 90 },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 150 },
]

async function reload() {
  if (!props.templateOid) {
    versions.value = []
    return
  }
  loading.value = true
  const result = await templateRepository.versions(props.templateOid)
  loading.value = false
  if (result.ok) {
    // 新版本在前，便于查看最近改动
    versions.value = [...(result.data ?? [])].sort((a, b) => (b.version ?? 0) - (a.version ?? 0))
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
