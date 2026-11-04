<template>
  <a-spin :spinning="loading">
    <div class="esp-card">
      <div class="esp-head">
        <span class="esp-title">变更请求</span>
        <a-tag v-if="request.code" color="blue">{{ request.code }}</a-tag>
        <a-tag v-if="request.changeLevel" :color="CHANGE_LEVEL_COLOR[request.changeLevel] || 'default'">
          {{ CHANGE_LEVEL_LABEL[request.changeLevel] || request.changeLevel }}变更
        </a-tag>
        <span v-if="loadError" class="esp-warn">{{ loadError }}</span>
      </div>
      <a-descriptions :column="2" size="small" bordered>
        <a-descriptions-item label="标题" :span="2">{{ request.name || '—' }}</a-descriptions-item>
        <a-descriptions-item label="变更类型">{{ CHANGE_TYPE_LABEL[request.changeType] || '—' }}</a-descriptions-item>
        <a-descriptions-item label="优先级">{{ PRIORITY_LABEL[request.priority] || '—' }}</a-descriptions-item>
        <a-descriptions-item label="责任人">{{ request.owner || '—' }}</a-descriptions-item>
        <a-descriptions-item label="期望完成">{{ request.dueDate || '—' }}</a-descriptions-item>
        <a-descriptions-item label="变更原因" :span="2">
          <div class="esp-pre">{{ request.reason || '—' }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="建议方案" :span="2">
          <div class="esp-pre">{{ request.proposal || '—' }}</div>
        </a-descriptions-item>
        <a-descriptions-item v-if="request.description" label="补充说明" :span="2">
          <div class="esp-pre">{{ request.description }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="发起时的影响初判" :span="2">
          <a-space size="large" wrap>
            <span>认证/合规：{{ CERT_IMPACT_LABEL[request.certImpact] || '未评估' }}</span>
            <span>客户/合同：{{ CUSTOMER_IMPACT_LABEL[request.customerImpact] || '未评估' }}</span>
            <span>在制/库存：{{ INVENTORY_IMPACT_LABEL[request.inventoryImpact] || '未评估' }}</span>
          </a-space>
        </a-descriptions-item>
        <a-descriptions-item v-if="attachment" label="补充材料" :span="2">
          <a :href="getAttachmentDownloadUrl(attachment.oid)" target="_blank" rel="noopener">
            {{ attachment.fileName }}
          </a>
          <span v-if="attachment.fileSize != null" class="esp-muted">
            （{{ formatFileSize(attachment.fileSize) }}）
          </span>
        </a-descriptions-item>
      </a-descriptions>
    </div>

    <div v-if="affectedObjects.length" class="esp-card">
      <div class="esp-head">
        <span class="esp-title">预估受影响对象</span>
        <span class="esp-muted">共 {{ affectedObjects.length }} 项</span>
      </div>
      <a-table
        :columns="objectColumns"
        :data-source="affectedObjects"
        size="small"
        row-key="oid"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'target'">
            <span class="esp-code">{{ record.entityCode }}</span>
            {{ record.entityName }}
          </template>
          <template v-else-if="column.key === 'container'">
            {{ record.containerName || '—' }}
          </template>
          <template v-else-if="column.key === 'note'">
            {{ record.note || '—' }}
          </template>
        </template>
      </a-table>
    </div>
  </a-spin>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getAttachment, getAttachmentDownloadUrl, getChangeRequest } from '@/api'
import {
  CERT_IMPACT_LABEL,
  CHANGE_LEVEL_COLOR,
  CHANGE_LEVEL_LABEL,
  CHANGE_TYPE_LABEL,
  CUSTOMER_IMPACT_LABEL,
  INVENTORY_IMPACT_LABEL,
  PRIORITY_LABEL
} from '@/utils/changeFlow'

/**
 * 变更请求的只读摘要 —— 会签类表单共用的那一块（影响分析会签 / CCB 决策会签）。
 *
 * <p>数据从流程变量 {@code requestOid} 回查单据：ECR 发起评审时不写
 * {@code ck_process_entity_set}，{@code form.businessOid} 恒为空，不能靠它取业务对象。
 *
 * <p>抽出来的原因：两个会签要看的单据信息完全一致，写两份就会出现
 * "影响分析那边加了字段、CCB 那边没加"的偏差。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO），只用到 variables.requestOid */
  form: { type: Object, default: null }
})

const loading = ref(false)
const loadError = ref('')
const request = ref({})
const affectedObjects = ref([])
const attachment = ref(null)

const objectColumns = [
  { key: 'target', title: '对象', dataIndex: 'entityName' },
  { key: 'container', title: '所属', dataIndex: 'containerName', width: 180 },
  { key: 'note', title: '备注', dataIndex: 'note', width: 200 }
]

const requestOid = computed(() => {
  const value = props.form?.variables?.requestOid
  return value ? String(value) : ''
})

function formatFileSize(bytes) {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

async function load() {
  const oid = requestOid.value
  if (!oid) {
    loadError.value = '取不到变更请求标识（流程变量 requestOid 为空）'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await getChangeRequest(oid)
    if (res?.code === 200 && res.data) {
      request.value = res.data.request || {}
      affectedObjects.value = res.data.affectedObjects || []
      const attOid = request.value.attachmentOid
      if (attOid) {
        const att = await getAttachment(attOid).catch(() => null)
        if (att?.code === 200) attachment.value = att.data
      }
    } else {
      loadError.value = res?.message || '变更请求详情加载失败'
    }
  } catch {
    loadError.value = '变更请求详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

defineExpose({ reload: load })
</script>

<style scoped>
.esp-card {
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fcfcfc;
}

.esp-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-bottom: 10px;
}

.esp-title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.esp-warn {
  font-size: 12px;
  color: #d46b08;
}

.esp-muted {
  font-size: 12px;
  color: #8c8c8c;
}

.esp-pre {
  white-space: pre-wrap;
  line-height: 1.6;
}

.esp-code {
  margin-right: 6px;
  font-family: 'Consolas', monospace;
  font-size: 12px;
  color: #595959;
}
</style>
