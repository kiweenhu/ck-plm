<template>
  <a-spin :spinning="loading">
    <div class="pup-card">
      <div class="pup-head">
        <span class="pup-title">零件被引用情况</span>
        <span class="pup-muted">
          直接被 {{ parentCount }} 个父件引用 · 共 {{ rows.length }} 条 BOM 行
        </span>
      </div>

      <div v-if="path.length" class="pup-path">
        <span class="pup-path-label">查看范围</span>
        <a class="pup-path-node" @click="goTo(-1)">{{ rootLabel }}</a>
        <template v-for="(node, idx) in path" :key="node.partOid || idx">
          <span class="pup-path-sep">›</span>
          <a v-if="idx < path.length - 1" class="pup-path-node" @click="goTo(idx)">{{ node.label }}</a>
          <span v-else class="pup-path-current">{{ node.label }}</span>
        </template>
        <a-button type="link" size="small" class="pup-back" @click="goTo(path.length - 2)">返回上一层</a-button>
      </div>

      <a-alert v-if="loadError" type="warning" show-icon class="pup-alert" :message="loadError" />
      <a-alert
        v-else-if="!loading && !rows.length"
        type="info"
        show-icon
        class="pup-alert"
        message="该零件当前没有被其他零组件引用（反向 BOM 为空），无法据此判断跨型号复用情况"
      />
      <a-table
        :columns="columns"
        :data-source="rows"
        size="small"
        :pagination="false"
        :row-key="(r) => r.linkOid || r.parentIterationOid"
        :locale="{ emptyText: '未被其他零组件使用' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'parentNumber'">
            <a
              class="pup-link"
              :title="`向上追溯「${record.parentName || '未知父件'}」的被使用情况`"
              @click="drillUp(record)"
            >{{ record.parentNumber || '-' }}</a>
            <a-tag v-if="record.parentView" size="small" color="blue" class="pup-view">{{ record.parentView }}</a-tag>
          </template>
          <template v-else-if="column.key === 'parentName'">
            {{ record.parentName || '未知父件' }}
          </template>
          <template v-else-if="column.key === 'parentVersion'">
            <a-tag size="small">{{ record.parentVersion || '-' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'exactRef'">
            <a-tag v-if="record.exactReference" color="success" size="small">精确</a-tag>
            <a-tag v-else color="warning" size="small">跟随最新</a-tag>
          </template>
        </template>
      </a-table>
      <div class="pup-tip">点父件编码可逐层向上追溯其被使用情况；本面板只读，不影响审批结论。</div>
    </div>
  </a-spin>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getPartUsedBy } from '@/api'

/**
 * 「待认定零件被谁引用」只读面板 —— 通用件升级认定流程的领导审批与会签节点共用。
 *
 * <p>通用件认定的判据是「跨型号复用」，光给审批人一个意见框等于让他凭记忆签字。
 * 这里调现成的反向 BOM 接口（与零件详情「被使用情况」同源），列出直接父件；
 * 点父件编码可沿 where-used 链逐层向上，看到最终挂在哪个型号 / 顶层件上。
 *
 * <p>只读、且从不阻止办理：取不到引用关系只是少一块信息（如业务对象未关联、
 * 接口异常），renderable 仍由内层结论表单决定。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：businessOid = 发起时关联的主零件 oid */
  form: { type: Object, default: null }
})

const partOid = computed(() => String(props.form?.businessOid || ''))
const loading = ref(false)
const loadError = ref('')
const rows = ref([])
/** 向上追溯的面包屑：[{ partOid, label }]，末项即当前查看范围 */
const path = ref([])

const scopeOid = computed(() => path.value.length
  ? path.value[path.value.length - 1].partOid
  : partOid.value)
const rootLabel = computed(() => {
  const head = props.form?.entities?.[0] || {}
  return [head.code || head.number, head.name].filter(Boolean).join(' ') || '当前零件'
})
const parentCount = computed(() => new Set(rows.value.map((r) => r.parentPartOid).filter(Boolean)).size)

const columns = [
  { title: '父件编码', key: 'parentNumber', width: 180 },
  { title: '父件名称', key: 'parentName', ellipsis: true },
  { title: '版本', key: 'parentVersion', width: 80, align: 'center' },
  { title: '用量', dataIndex: 'quantity', key: 'quantity', width: 80, align: 'center' },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 70, align: 'center' },
  { title: '引用方式', key: 'exactRef', width: 100, align: 'center' }
]

async function load() {
  const oid = scopeOid.value
  if (!oid) {
    loadError.value = '取不到零件标识（流程未关联业务对象），无法加载被引用情况'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await getPartUsedBy(oid)
    if (res?.code !== 200) {
      loadError.value = res?.message || '被引用情况加载失败'
      rows.value = []
      return
    }
    const data = res?.data
    rows.value = Array.isArray(data) ? data : (data?.list || [])
  } catch {
    loadError.value = '被引用情况加载失败'
    rows.value = []
  } finally {
    loading.value = false
  }
}

/** 点父件编码：以它为范围继续往上查一层 */
function drillUp(record) {
  if (!record?.parentPartOid) {
    loadError.value = '该父件已不存在（可能已被删除），无法继续向上追溯'
    return
  }
  path.value = [
    ...path.value,
    {
      partOid: record.parentPartOid,
      label: [record.parentNumber, record.parentName, record.parentVersion].filter(Boolean).join(' ') || '上层零组件'
    }
  ]
  load()
}

/** 面包屑回退：idx = -1 回到发起时的零件，否则回到第 idx 层父件 */
function goTo(idx) {
  path.value = idx < 0 ? [] : path.value.slice(0, idx + 1)
  load()
}

watch(partOid, () => {
  path.value = []
  load()
})
onMounted(load)
</script>

<style scoped>
.pup-card {
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fcfcfc;
}

.pup-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-bottom: 8px;
}

.pup-title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.pup-muted {
  flex: 1;
  font-size: 12px;
  color: #8c8c8c;
}

.pup-path {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 6px;
  margin-bottom: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  background: #f5f5f5;
}

.pup-path-label {
  margin-right: 4px;
  font-size: 12px;
  color: #8c8c8c;
}

.pup-path-node,
.pup-path-current {
  font-size: 12px;
}

.pup-path-current {
  font-weight: 600;
  color: #262626;
}

.pup-path-sep {
  color: #bfbfbf;
}

.pup-back {
  margin-left: auto;
  padding: 0 4px;
}

.pup-link {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.pup-view {
  margin-left: 6px;
}

.pup-alert {
  margin-bottom: 10px;
}

.pup-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #8c8c8c;
}
</style>
