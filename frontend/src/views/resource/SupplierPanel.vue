<template>
  <div class="sup-panel">
    <div class="mpn-bar">
      <a-input-search
        v-model:value="keyword" placeholder="按代码 / 简称 / 全称搜索"
        style="width: 260px" allow-clear @search="load"
      />
      <a-button type="primary" @click="openForm()">
        <PlusOutlined /> 新增供应商
      </a-button>
      <span class="mpn-hint">供应商是 AVL 的引用端；与制造商是两个独立对象（一家代理多家原厂）</span>
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
          <a-popconfirm title="确定删除该供应商？" ok-text="删除" cancel-text="取消" @confirm="remove(record)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
      <template #emptyText>
        <span>还没有录入供应商 —— 点「新增供应商」开始建名录（如 立创商城 / 得捷电子）</span>
      </template>
    </a-table>

    <a-modal
      v-model:open="formOpen" :title="form.oid ? '编辑供应商' : '新增供应商'"
      :width="560" @ok="submit" :confirm-loading="submitting"
    >
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="代码" required><a-input v-model:value="form.code" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="简称" required><a-input v-model:value="form.name" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="全称（合同主体）"><a-input v-model:value="form.fullName" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="联系人"><a-input v-model:value="form.contact" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="电话"><a-input v-model:value="form.phone" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="邮箱"><a-input v-model:value="form.email" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="官网"><a-input v-model:value="form.website" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="显示顺序"><a-input-number v-model:value="form.sortOrder" style="width:100%" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="备注"><a-textarea v-model:value="form.note" :rows="2" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="停用"><a-switch v-model:checked="form.disabled" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { listSuppliers, createSupplier, updateSupplier, deleteSupplier } from '@/api'

// 供应商名录面板（可复用）：与 ManufacturerPanel 同型，两处入口共用同一份实现。
const keyword = ref('')
const loading = ref(false)
const rows = ref([])
const submitting = ref(false)
const formOpen = ref(false)
const emptyForm = () => ({
  oid: null, code: '', name: '', fullName: '', contact: '', phone: '',
  email: '', website: '', note: '', disabled: false, sortOrder: 0
})
const form = ref(emptyForm())

const columns = [
  { title: '代码', dataIndex: 'code', key: 'code', width: 120 },
  { title: '简称', dataIndex: 'name', key: 'name', width: 170 },
  { title: '全称', dataIndex: 'fullName', key: 'fullName', ellipsis: true },
  { title: '联系人', dataIndex: 'contact', key: 'contact', width: 100 },
  { title: '电话', dataIndex: 'phone', key: 'phone', width: 130 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]

async function load(kw) {
  loading.value = true
  try {
    const res = await listSuppliers(typeof kw === 'string' ? kw : keyword.value)
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
    message.warning('代码与简称必填')
    return
  }
  submitting.value = true
  try {
    const res = form.value.oid
      ? await updateSupplier(form.value.oid, form.value)
      : await createSupplier(form.value)
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
  const res = await deleteSupplier(record.oid)
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
