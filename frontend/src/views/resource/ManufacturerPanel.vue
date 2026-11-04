<template>
  <div class="mfr-panel">
    <div class="mpn-bar">
      <a-input-search
        v-model:value="keyword" placeholder="按代码 / 名称 / 英文名搜索"
        style="width: 260px" allow-clear @search="load"
      />
      <a-button type="primary" @click="openForm()">
        <PlusOutlined /> 新增制造商
      </a-button>
      <span class="mpn-hint">该制造商被 AML 引用后不能删，只能「停用」—— 历史 AML 需要保留引用</span>
    </div>

    <a-table
      :columns="columns" :data-source="rows" :loading="loading"
      :pagination="false" row-key="oid" size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.disabled ? 'default' : 'green'">
            {{ record.disabled ? '已停用' : '启用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="openForm(record)">编辑</a-button>
          <a-popconfirm title="确定删除该制造商？" ok-text="删除" cancel-text="取消" @confirm="remove(record)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
      <template #emptyText>
        <span>还没有录入制造商 —— 点「新增制造商」开始建名录（如 TI · 德州仪器）</span>
      </template>
    </a-table>

    <a-modal
      v-model:open="formOpen" :title="form.oid ? '编辑制造商' : '新增制造商'"
      :width="560" @ok="submit" :confirm-loading="submitting"
    >
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="代码" required><a-input v-model:value="form.code" placeholder="如 TI / MURATA" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="名称" required><a-input v-model:value="form.name" placeholder="如 德州仪器" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="英文名"><a-input v-model:value="form.englishName" placeholder="如 Texas Instruments" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="原产地"><a-input v-model:value="form.country" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="官网"><a-input v-model:value="form.website" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="显示顺序"><a-input-number v-model:value="form.sortOrder" style="width:100%" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.note" :rows="2" /></a-form-item></a-col>
          <a-col :span="24">
            <a-form-item label="停用">
              <a-switch v-model:checked="form.disabled" />
              <span class="mpn-hint">停用后仍保留历史 AML 引用，只是不再作为新选的候选</span>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { listManufacturers, createManufacturer, updateManufacturer, deleteManufacturer } from '@/api'

// 制造商名录面板（可复用）：导航页「MPN 厂商」与企业资源页「MPN 库」共用同一份实现，
// 避免两处各写一套表格与表单（同一能力、同一数据）。
const keyword = ref('')
const loading = ref(false)
const rows = ref([])
const submitting = ref(false)
const formOpen = ref(false)
const emptyForm = () => ({
  oid: null, code: '', name: '', englishName: '', country: '',
  website: '', note: '', disabled: false, sortOrder: 0
})
const form = ref(emptyForm())

const columns = [
  { title: '代码', dataIndex: 'code', key: 'code', width: 120 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '英文名', dataIndex: 'englishName', key: 'englishName', width: 180 },
  { title: '原产地', dataIndex: 'country', key: 'country', width: 110 },
  { title: '官网', dataIndex: 'website', key: 'website', ellipsis: true },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]

async function load(kw) {
  loading.value = true
  try {
    const res = await listManufacturers(typeof kw === 'string' ? kw : keyword.value)
    rows.value = res?.code === 200 ? (res.data || []) : []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function openForm(record) {
  form.value = record ? { ...record } : emptyForm()
  formOpen.value = true
}

async function submit() {
  if (!form.value.code || !form.value.name) {
    message.warning('代码与名称必填')
    return
  }
  submitting.value = true
  try {
    const res = form.value.oid
      ? await updateManufacturer(form.value.oid, form.value)
      : await createManufacturer(form.value)
    if (res?.code === 200) {
      message.success('已保存')
      formOpen.value = false
      load()
    } else {
      message.error(res?.message || '保存失败')
    }
  } finally {
    submitting.value = false
  }
}

async function remove(record) {
  const res = await deleteManufacturer(record.oid)
  if (res?.code === 200) {
    message.success('已删除')
    load()
  } else {
    message.error(res?.message || '删除失败')
  }
}

onMounted(() => load(''))

defineExpose({ load })
</script>

<style scoped>
.mpn-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.mpn-hint { color: #8c8c8c; font-size: 12px; margin-left: 8px; }
</style>
