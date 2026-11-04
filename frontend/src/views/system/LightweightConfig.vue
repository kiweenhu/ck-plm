<template>
  <div class="lwc-tabs">
    <!--
      2026-10-26 用户要求：「数模转换」从左侧主导航挪进「业务配置 → 轻量化转换」，
      并在这里做成两个页签 —— 队列在左、服务配置在右。

      为什么两件事放在一起：它们是同一条链路的两个视角。队列回答"现在谁没转完"，
      配置回答"转换器在哪、等多久"；过去队列在主导航、配置在业务配置，
      出问题时要在两处之间来回跳（而且改完配置还得自己回去看队列）。
    -->
    <a-tabs v-model:activeKey="activeTab" class="lwc-tabs-bar" @change="onTabChange">
      <a-tab-pane key="queue">
        <template #tab>
          <span class="lwc-tab-label"><DeploymentUnitOutlined /> 数模转换</span>
        </template>
        <CadConvertCenter embedded />
      </a-tab-pane>

      <a-tab-pane key="config">
        <template #tab>
          <span class="lwc-tab-label"><SettingOutlined /> 轻量化转换服务配置</span>
        </template>
        <CadForgeServiceConfig />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DeploymentUnitOutlined, SettingOutlined } from '@ant-design/icons-vue'
import CadConvertCenter from '@/views/product/CadConvertCenter.vue'
import CadForgeServiceConfig from '@/views/system/CadForgeServiceConfig.vue'

const route = useRoute()
const router = useRouter()

// 深链：/org/config?module=lightweight&tab=config 直接落到服务配置页签
// （未带 tab 时落在第一个页签「数模转换」—— 用户口径：第一个页签是队列）
const TABS = ['queue', 'config']
const activeTab = ref(TABS.includes(route.query.tab) ? route.query.tab : 'queue')

/**
 * 页签写回 query：刷新 / 复制链接 / 从队列里点开某条记录再返回，都还停在同一页签。
 * 只管写法、不反向 watch query —— 否则和 activeKey 互相触发。
 */
function onTabChange(key) {
  if (route.query.tab === key) return
  router.replace({ query: { ...route.query, tab: key } })
}
</script>

<style scoped>
.lwc-tabs {
  padding-top: 4px;
}

/* 页签条与内容之间留一点呼吸，避免队列的统计卡紧贴着 tab 下边线 */
.lwc-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 12px;
}

.lwc-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
