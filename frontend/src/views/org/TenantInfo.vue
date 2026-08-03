<template>
  <div class="tenant-info-page">
    <!-- 页头 -->
    <div class="tenant-info-header">
      <div class="tenant-info-header-left">
        <h3 class="tenant-info-title">企业信息</h3>
        <span class="tenant-info-subtitle">查阅和编辑企业基本信息</span>
      </div>
    </div>

    <a-spin :spinning="loading">
      <!-- 统计栏 -->
      <div class="tenant-stats-bar">
        <div class="tenant-stat-item">
          <ApartmentOutlined class="tenant-stat-icon" />
          <span class="tenant-stat-value">{{ stats.deptCount }}</span>
          <span class="tenant-stat-label">部门</span>
        </div>
        <a-divider type="vertical" style="height:24px" />
        <div class="tenant-stat-item">
          <TeamOutlined class="tenant-stat-icon" />
          <span class="tenant-stat-value">{{ stats.userCount }}</span>
          <span class="tenant-stat-label">人员</span>
        </div>
        <a-divider type="vertical" style="height:24px" />
        <div class="tenant-stat-item">
          <SafetyCertificateOutlined class="tenant-stat-icon" />
          <span class="tenant-stat-value">{{ stats.roleCount }}</span>
          <span class="tenant-stat-label">角色</span>
        </div>
      </div>

      <div class="tenant-info-content">
        <!-- 查看模式 -->
        <template v-if="!editing">
          <a-descriptions :column="2" bordered size="small" title="企业基本信息">
            <a-descriptions-item label="企业标识">
              <a-tag color="blue">{{ info.tenantId || '-' }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="企业名称">{{ info.name || '-' }}</a-descriptions-item>
            <a-descriptions-item label="企业状态">
              <a-tag :color="statusColor">{{ statusText }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="联系人">{{ info.contactName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="联系邮箱">{{ info.contactEmail || '-' }}</a-descriptions-item>
            <a-descriptions-item label="创建时间">{{ info.createdAt || '-' }}</a-descriptions-item>
          </a-descriptions>
          <div style="margin-top: 16px">
            <a-button type="primary" @click="startEdit">编辑企业信息</a-button>
          </div>
        </template>

        <!-- 编辑模式 -->
        <template v-else>
          <a-card title="编辑企业信息" size="small">
            <a-form :model="form" layout="vertical" style="max-width: 560px">
              <a-form-item label="企业名称" required>
                <a-input v-model:value="form.name" placeholder="企业/公司名称" size="large" />
              </a-form-item>
              <a-form-item label="联系人">
                <a-input v-model:value="form.contactName" placeholder="联系人姓名" size="large" />
              </a-form-item>
              <a-form-item label="联系邮箱">
                <a-input v-model:value="form.contactEmail" placeholder="contact@example.com" size="large" />
              </a-form-item>
              <a-form-item>
                <a-space>
                  <a-button @click="cancelEdit">取消</a-button>
                  <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
                </a-space>
              </a-form-item>
            </a-form>
          </a-card>
        </template>
      </div>
    </a-spin>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getCurrentTenant, updateTenant, getOrgTree, getAllUsers, getRoles } from '@/api'
import { ApartmentOutlined, TeamOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'

const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const info = reactive({})

const stats = reactive({
  deptCount: 0,
  userCount: 0,
  roleCount: 0
})

const form = reactive({
  name: '',
  contactName: '',
  contactEmail: ''
})

const statusText = computed(() => {
  const map = { ACTIVE: '已激活', PENDING: '待审核', REJECTED: '已驳回', SUSPENDED: '已停用', DISABLED: '已禁用' }
  return map[info.status] || info.status || '-'
})

const statusColor = computed(() => {
  const map = { ACTIVE: 'green', PENDING: 'orange', REJECTED: 'red', SUSPENDED: 'orange', DISABLED: 'default' }
  return map[info.status] || 'default'
})

function countOrgNodes(nodes) {
  let count = 0
  for (const n of nodes) {
    count++
    if (n.children?.length) count += countOrgNodes(n.children)
  }
  return count
}

async function loadStats() {
  try {
    const [orgRes, userRes, roleRes] = await Promise.all([
      getOrgTree(),
      getAllUsers(),
      getRoles()
    ])
    if (orgRes.code === 200) stats.deptCount = countOrgNodes(orgRes.data || [])
    if (userRes.code === 200) stats.userCount = Array.isArray(userRes.data) ? userRes.data.length : 0
    if (roleRes.code === 200) stats.roleCount = Array.isArray(roleRes.data) ? roleRes.data.length : 0
  } catch { /* 统计加载失败不影响主功能 */ }
}

async function loadInfo() {
  loading.value = true
  try {
    const res = await getCurrentTenant()
    if (res.code === 200 && res.data) {
      Object.keys(info).forEach(k => delete info[k])
      Object.assign(info, res.data)
    } else {
      message.error(res.message || '加载企业信息失败')
    }
  } catch {
    message.error('加载企业信息失败')
  } finally {
    loading.value = false
  }
}

function startEdit() {
  form.name = info.name || ''
  form.contactName = info.contactName || ''
  form.contactEmail = info.contactEmail || ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

async function handleSave() {
  if (!form.name?.trim()) { message.warning('企业名称不能为空'); return }
  saving.value = true
  try {
    const res = await updateTenant(info.oid, {
      name: form.name.trim(),
      contactName: form.contactName || '',
      contactEmail: form.contactEmail || ''
    })
    if (res.code === 200) {
      message.success('企业信息已更新')
      editing.value = false
      await loadInfo()
    } else {
      message.error(res.message || '更新失败')
    }
  } catch {
    message.error('更新失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => { loadInfo(); loadStats() })
</script>

<style scoped>
.tenant-info-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.tenant-info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}

.tenant-info-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.tenant-info-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.tenant-info-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

.tenant-stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  flex-shrink: 0;
  margin-bottom: 12px;
}

.tenant-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.tenant-stat-icon {
  font-size: 14px;
  color: #1677ff;
}

.tenant-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.tenant-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

.tenant-info-content {
  padding: 16px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}
</style>
