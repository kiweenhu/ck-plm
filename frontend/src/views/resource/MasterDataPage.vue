<template>
  <div class="md-page">
    <div class="md-header">
      <h3 class="md-title">MPN 厂商</h3>
      <span class="md-subtitle">
        制造商与供应商名录 —— MPN 管理的基础字典：先在这里把「谁造的」「从谁买」建全，
        再到具体内部件上挂它的制造商料号（AML）与供货渠道（AVL）。
      </span>
    </div>

    <a-tabs v-model:activeKey="tab" size="small">
      <a-tab-pane key="mfr" tab="制造商">
        <ManufacturerPanel />
      </a-tab-pane>
      <a-tab-pane key="sup" tab="供应商">
        <SupplierPanel />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ManufacturerPanel from '@/views/resource/ManufacturerPanel.vue'
import SupplierPanel from '@/views/resource/SupplierPanel.vue'

// 导航页「MPN 厂商」：制造商 / 供应商两本名录。
// 与「企业资源 → MPN 库」里的同名页签用的是**同一对面板组件**，不是两份实现。
// 本页受 MPN_MANAGEMENT 能力控制（见 MainLayout 的菜单项与路由）。
// 命名说明：显示名 2026-10-11 由「MPN 数据」定名为「MPN 厂商」，内部标识（路由 path: master-data /
// 组件名 MasterDataPage）保留原名 —— 与本项目惯例一致（path: resource 显示为「企业资源」），
// 改显示名不必牵动 URL 与文件名。
const tab = ref('mfr')
</script>

<style scoped>
.md-page {
  padding: 16px 20px;
  height: 100%;
  overflow-y: auto;
}
.md-header { margin-bottom: 8px; }
.md-title {
  margin: 0 0 4px 0;
  font-size: 18px;
  font-weight: 600;
}
.md-subtitle {
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.6;
  display: block;
  max-width: 900px;
}
</style>
