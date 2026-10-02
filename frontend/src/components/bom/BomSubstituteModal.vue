<template>
  <a-modal
    :open="open"
    :title="`局部替代 · ${row?.childName || row?.childNumber || ''}`"
    width="820px"
    centered
    :footer="null"
    :body-style="{ maxHeight: 'calc(100vh - 180px)', overflowY: 'auto', padding: '12px 16px 16px' }"
    @cancel="close"
  >
    <!-- 口径说明放在最上面：局部替代与「全局替代」（物料主数据级）是两回事，
         不写清楚，用户会以为在这里设的替代在别的 BOM 里也生效 -->
    <a-alert
      type="info"
      show-icon
      banner
      style="margin-bottom:12px"
      message="局部替代只在本 BOM 行生效"
      description="同一颗子件装在别的父件/别的 BOM 行下，是否允许被这个件替换要各自设置；若要在任意 BOM 中都允许替换，请到零件详情的「替代」页签建立全局替代。"
    />

    <!-- 本行信息：设替代前先确认设的是哪一行 -->
    <div class="bs-row-info">
      <span class="bs-row-label">本级子件</span>
      <code class="bs-code">{{ row?.childNumber || '-' }}</code>
      <span class="bs-name">{{ row?.childName || '-' }}</span>
      <a-tag v-if="row?.childVersion" color="default">{{ row.childVersion }}</a-tag>
      <span v-if="row?.quantity != null" class="bs-muted">用量 {{ row.quantity }}{{ row.unit || '' }}</span>
    </div>

    <!-- ========== 上栏：已设替代件 ========== -->
    <div class="bs-section-title">
      已设替代件
      <span class="bs-muted">（{{ list.length }}）</span>
      <a-button size="small" type="link" :loading="loading" @click="load">刷新</a-button>
    </div>
    <a-table
      :columns="existingColumns"
      :data-source="list"
      :loading="loading"
      :pagination="false"
      row-key="oid"
      size="small"
      :locale="{ emptyText: '尚未设置替代件' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'code'">
          <code style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959">
            {{ record.substitutePartNumber || '-' }}
          </code>
        </template>
        <template v-else-if="column.key === 'version'">{{ record.substituteVersion || '-' }}</template>
        <!-- 选件表的类型列：用零件自己的类型（与"替代类型"不是一回事） -->
        <template v-else-if="column.key === 'partType'">
          {{ record.typeDefinitionName || record.typeDefinitionCode || '-' }}
        </template>
        <template v-else-if="column.key === 'type'">
          <a-tooltip :title="typeOf(record.substituteType).desc">
            <a-tag :color="typeOf(record.substituteType).color">{{ typeOf(record.substituteType).label }}</a-tag>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'quantity'">
          {{ record.substituteQuantity ?? 1 }}{{ record.substituteUnit || '' }}
        </template>
        <template v-else-if="column.key === 'priority'">{{ record.priority ?? '-' }}</template>
        <template v-else-if="column.key === 'action'">
          <a-popconfirm title="删除该替代件？" ok-text="删除" cancel-text="取消" @confirm="remove(record)">
            <a class="bs-danger">删除</a>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- ========== 下栏：添加 ========== -->
    <div class="bs-section-title" style="margin-top:16px">添加替代件</div>

    <div class="bs-filter">
      <span class="bs-row-label">所属容器</span>
      <a-select
        v-model:value="containerOid"
        placeholder="选择产品系列/型号"
        allow-clear
        show-search
        style="width:240px"
        @change="onFilterChange"
      >
        <a-select-option v-for="o in displayContainerOptions" :key="o.value" :value="o.value">
          {{ o.label }}
        </a-select-option>
      </a-select>
      <a-input-search
        v-model:value="keyword"
        placeholder="按名称或编码搜索"
        style="flex:1;min-width:180px"
        allow-clear
        @search="onFilterChange"
      />
    </div>

    <!-- 参数：一次设定，应用到本次勾选的每个替代件（再单独改就去列表里重设该行） -->
    <div class="bs-filter" style="margin-bottom:8px">
      <span class="bs-row-label">替代类型</span>
      <a-select v-model:value="form.substituteType" style="width:150px">
        <a-select-option v-for="t in TYPES" :key="t.value" :value="t.value">{{ t.label }}</a-select-option>
      </a-select>
      <span class="bs-row-label">数量</span>
      <a-input-number v-model:value="form.substituteQuantity" :min="0" :step="1" style="width:90px" />
      <span class="bs-row-label">优先级</span>
      <a-input-number v-model:value="form.priority" :min="1" :step="1" style="width:90px" placeholder="越小越优先" />
      <a-input v-model:value="form.description" placeholder="说明（可选）" style="flex:1;min-width:160px" />
    </div>

    <a-table
      :columns="pickColumns"
      :data-source="parts"
      :loading="partsLoading"
      :pagination="{ pageSize: 8, showSizeChanger: false, showTotal: (t) => `共 ${t} 条` }"
      :row-selection="{ selectedRowKeys: selectedOids, onChange: (keys) => (selectedOids = keys) }"
      row-key="oid"
      size="small"
      :locale="{ emptyText: '暂无可选零组件' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'number'">
          <code style="font-size:12px;background:#f5f5f5;padding:1px 6px;border-radius:3px;color:#595959">
            {{ record.number || '-' }}
          </code>
        </template>
        <template v-else-if="column.key === 'type'">
          <a-tag color="blue">{{ record.typeDefinitionCode || '-' }}</a-tag>
        </template>
      </template>
    </a-table>

    <div class="bs-actions">
      <a-popconfirm
        v-if="list.length"
        title="确定清空本行的全部替代件？"
        ok-text="清空"
        cancel-text="取消"
        @confirm="clearAll"
      >
        <a-button danger :loading="saving">取消替代（清空本行）</a-button>
      </a-popconfirm>
      <span style="flex:1" />
      <a-button @click="close">关闭</a-button>
      <a-button type="primary" :loading="saving" :disabled="!selectedOids.length" @click="submit">
        添加（{{ selectedOids.length }}）
      </a-button>
    </div>
  </a-modal>
</template>

<script setup>
/**
 * 局部替代弹框 —— BOM 行工具栏「局部替代」的界面。
 *
 * <p>上栏看/删本行已设的替代件，下栏选零件添加。局部替代挂在 BOM 行上
 * （{@code ck_bom_substitute_link.bom_link_oid}），因此本组件的入口参数是<b>选中行的 oid</b>，
 * 不是零件 oid —— 同一颗料装在别处能不能被替换，各是各的。
 *
 * <p>选件沿用「添加已有子件 / 添加替代件」那套（容器下拉 + 关键字搜索 + 多选表格）：
 * 三处都是"从一个容器里挑零件"，交互保持一致，用户不用学第二遍。
 */
import { ref, watch, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getBomSubstitutesByLink,
  setBomSubstitute,
  deleteBomSubstitute,
  clearBomSubstitutes,
  getPartsByContainer,
} from '@/api'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 选中的 BOM 行（左侧树节点）：oid 是 BOM 行 oid，其余用于显示 */
  row: { type: Object, default: null },
  /** 容器下拉选项（由宿主页面提供，避免这里再加载一遍） */
  containerOptions: { type: Array, default: () => [] },
  /** 默认容器（当前父件的 containerOid） */
  defaultContainerOid: { type: String, default: '' },
  /** 默认容器的类型（part.containerType）：容器不是系列/型号时用来给出可读的兜底文案 */
  defaultContainerType: { type: String, default: '' },
})
const emit = defineEmits(['update:open', 'changed'])

/** 替代类型（与 schema.sql / 实体注释同源） */
const TYPES = [
  { value: 'EQUIVALENT', label: '等效替代', color: 'green', desc: '功能、性能完全一致，可无条件互换' },
  { value: 'COMPLETE', label: '完全替代', color: 'blue', desc: '替代件完全覆盖原子部件用途' },
  { value: 'PARTIAL', label: '部分替代', color: 'orange', desc: '仅在特定场景/条件下可替代' },
  { value: 'SUBSTITUTE', label: '临时替代', color: 'purple', desc: '原子部件缺货时的临时替代方案' },
]

const loading = ref(false)
const saving = ref(false)
const list = ref([])

const containerOid = ref(null)
const keyword = ref('')
const parts = ref([])
const partsLoading = ref(false)
const selectedOids = ref([])

const form = ref({
  substituteType: 'EQUIVALENT',
  substituteQuantity: 1,
  priority: null,
  description: '',
})

const existingColumns = [
  { title: '替代件编码', key: 'code', width: 150 },
  { title: '替代件名称', dataIndex: 'substitutePartName', key: 'name', ellipsis: true },
  { title: '版本', key: 'version', width: 70 },
  { title: '类型', key: 'type', width: 100 },
  { title: '数量', key: 'quantity', width: 90 },
  { title: '优先级', key: 'priority', width: 80 },
  { title: '说明', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '操作', key: 'action', width: 70 },
]

const pickColumns = [
  { title: '编码', dataIndex: 'number', key: 'number', width: 150 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  // 选件表的类型要用零件自己的字段（typeDefinitionName/Code），不能沿用下面那个
  // 针对"替代类型（EQUIVALENT…）"的槽 —— 两者含义不同，共用会显示成"等效替代"
  { title: '类型', key: 'partType', width: 110 },
  { title: '版本', dataIndex: 'displayVersion', key: 'displayVersion', width: 80 },
]

const bomLinkOid = computed(() => props.row?.oid || '')

/** 容器类型 → 可读文案（仅用于"容器不是系列/型号"时的兜底显示） */
const CONTAINER_TYPE_LABEL = {
  PRODUCT_LINE: '产品系列',
  LINE: '产品系列',
  PRODUCT_MODEL: '产品型号',
  MODEL: '产品型号',
  CORP_RESOURCE: '企业资源',
  FOLDER: '文件夹',
}

/**
 * 下拉实际渲染的选项：正常来自宿主传下来的「产品系列 + 型号」清单。
 *
 * <p>但零件的容器未必是系列/型号（库里的零件就有挂在企业资源下的），这时清单里没有它，
 * a-select 会把 containerOid <b>原样显示成 oid</b>（用户反馈过："所属容器显示的是 oid，不是名称"）。
 * 补一条兜底项：值仍是真 oid（选件查询照常可用），只是文案换成可读的类型名 ——
 * 界面上不该出现内部标识。
 */
const displayContainerOptions = computed(() => {
  const opts = props.containerOptions || []
  const cur = containerOid.value
  if (!cur || opts.some((o) => o.value === cur)) return opts
  const typeLabel = CONTAINER_TYPE_LABEL[props.defaultContainerType] || '当前容器'
  return [{ value: cur, label: `${typeLabel}（当前容器）` }, ...opts]
})

/** 打开时加载：已设替代件 + 默认容器下的可选零件（切行即重载） */
watch(
  () => [props.open, props.row?.oid],
  ([open]) => {
    if (!open) return
    selectedOids.value = []
    keyword.value = ''
    form.value = { substituteType: 'EQUIVALENT', substituteQuantity: 1, priority: null, description: '' }
    containerOid.value = props.defaultContainerOid || null
    load()
    loadParts()
  },
)

async function load() {
  if (!bomLinkOid.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const res = await getBomSubstitutesByLink(bomLinkOid.value)
    list.value = res?.code === 200 ? res.data || [] : []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 可选零件：排除该行本身的子件与已设替代件（后端也会拦，这里先不让人白点） */
async function loadParts() {
  if (!containerOid.value) {
    parts.value = []
    return
  }
  partsLoading.value = true
  try {
    const res = await getPartsByContainer(containerOid.value, keyword.value.trim() || undefined)
    const arr = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []
    const taken = new Set(list.value.map((l) => l.substitutePartOid))
    parts.value = arr.filter(
      (p) => p?.oid && p.oid !== props.row?.childPartOid && !taken.has(p.oid),
    )
  } catch {
    parts.value = []
  } finally {
    partsLoading.value = false
  }
}

function onFilterChange() {
  selectedOids.value = []
  loadParts()
}

async function submit() {
  if (!selectedOids.value.length) return
  const targets = parts.value.filter((p) => selectedOids.value.includes(p.oid))
  saving.value = true
  let ok = 0
  const failures = []
  for (const p of targets) {
    try {
      const res = await setBomSubstitute({
        bomLinkOid: bomLinkOid.value,
        substitutePartOid: p.oid,
        substituteType: form.value.substituteType,
        substituteQuantity: form.value.substituteQuantity,
        priority: form.value.priority,
        description: form.value.description,
      })
      if (res?.code === 200) ok++
      else failures.push(`${p.number || p.name}：${res?.message || '未知错误'}`)
    } catch (e) {
      failures.push(`${p.number || p.name}：${e?.response?.data?.message || e.message}`)
    }
  }
  saving.value = false
  if (ok) {
    message.success(`已设置 ${ok} 个替代件`)
    selectedOids.value = []
    await load()
    await loadParts()
    emit('changed')
  }
  if (failures.length) {
    Modal.warning({ title: '部分替代件未能设置', content: failures.join('；'), okText: '知道了' })
  }
}

async function remove(record) {
  try {
    const res = await deleteBomSubstitute(record.oid)
    if (res?.code === 200) {
      message.success('已删除')
      await load()
      await loadParts()
      emit('changed')
    } else {
      message.error(res?.message || '删除失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || e.message)
  }
}

async function clearAll() {
  saving.value = true
  try {
    const res = await clearBomSubstitutes(bomLinkOid.value)
    if (res?.code === 200) {
      message.success(`已清空 ${res.data ?? 0} 条替代关系`)
      await load()
      await loadParts()
      emit('changed')
    } else {
      message.error(res?.message || '取消失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || e.message)
  } finally {
    saving.value = false
  }
}

function typeOf(value) {
  return TYPES.find((t) => t.value === value) || { label: value || '等效替代', color: 'green', desc: '' }
}

function close() {
  emit('update:open', false)
}
</script>

<style scoped>
.bs-row-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #fafafa;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;
  flex-wrap: wrap;
}
.bs-row-label {
  font-size: 12px;
  color: #8c8c8c;
  white-space: nowrap;
}
.bs-code {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #595959;
}
.bs-name {
  font-weight: 500;
}
.bs-muted {
  color: #8c8c8c;
  font-size: 12px;
}
.bs-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #595959;
  margin-bottom: 8px;
}
.bs-section-title .ant-btn {
  margin-left: auto;
}
.bs-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.bs-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
.bs-danger {
  color: #ff4d4f;
}
</style>
