<template>
  <div class="mpn-page">
    <a-tabs v-model:activeKey="tab" size="small">
      <!-- MPN 厂商（制造商 / 供应商）：与导航页「MPN 厂商」用的是同一对面板组件，
           放在这里是为了"从物料视角进来时，正好要建个供应商"不必跳走 -->
      <a-tab-pane key="mfr" tab="制造商">
        <ManufacturerPanel />
      </a-tab-pane>
      <a-tab-pane key="sup" tab="供应商">
        <SupplierPanel />
      </a-tab-pane>

      <!-- ==================== AML · AVL ==================== -->
      <!-- ★ 2026-10-11：AML·AVL 的表格/弹窗逻辑已抽到 components/PartMpnPanel.vue（Part 详情页的
           「MPN」页签用同一实现）—— 本页只负责"选件"，选中后交给共享组件维护。 -->
      <a-tab-pane key="aml" tab="AML · AVL">
        <div class="mpn-bar">
          <a-select
            v-model:value="partOid" show-search allow-clear
            placeholder="选择内部件（输入代码 / 名称搜索）"
            style="width: 420px" :options="partOptions" :loading="partLoading"
            :filter-option="false" @search="searchParts"
          />
          <span class="mpn-hint">选中内部件后，在下方维护它的认可料号（AML）与供货渠道（AVL）</span>
        </div>

        <PartMpnPanel v-if="partOid" :part-oid="partOid" />
        <a-empty v-else description="先在上方选择一个内部件" :image-style="{ height: '48px' }" />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ManufacturerPanel from '@/views/resource/ManufacturerPanel.vue'
import SupplierPanel from '@/views/resource/SupplierPanel.vue'
import PartMpnPanel from '@/components/PartMpnPanel.vue'
import { searchMpnParts } from '@/api'

// 企业资源页的「MPN 库」页签（2026-10-12 由「MPN 管理」定名）：MPN 厂商（复用面板）+ AML·AVL（选件后交给共享组件 PartMpnPanel）。
// MPN 厂商的主入口在导航页「MPN 厂商」；这里保留一份是为了就近维护（同一对组件，不是两份实现）。
// ★ 2026-10-11：AML·AVL 的表格/弹窗逻辑抽到 components/PartMpnPanel.vue —— Part 详情页的「MPN」页签
//   与这里共用同一实现（同一能力不做两份），本页只负责"选件"。
const tab = ref('mfr')

// ==================== 选件器 ====================
const partOid = ref(undefined)
const partOptions = ref([])
const partLoading = ref(false)

async function searchParts(keyword) {
  partLoading.value = true
  try {
    const res = await searchMpnParts(keyword, 50)
    const list = res?.code === 200 ? (res.data || []) : []
    partOptions.value = list.map(p => ({ value: p.oid, label: `${p.code} · ${p.name}` }))
  } catch { partOptions.value = [] } finally { partLoading.value = false }
}

// ==================== 初始化 ====================
onMounted(() => {
  searchParts('')
})
</script>

<style scoped>
.mpn-page { padding: 4px 0; }

.mpn-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.mpn-hint { color: #8c8c8c; font-size: 12px; }

/* AML·AVL 的汇总条 / 展开行样式已随实现在 components/PartMpnPanel.vue（pmp-* 前缀），此处不再保留副本 */
</style>
