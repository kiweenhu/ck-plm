<template>
  <div class="placeholder-page">
    <a-result
      status="info"
      :title="pageTitle"
      :sub-title="pageDesc"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageMap = {
  '/system/basic':    { title: '业务配置', desc: '编码规则、审批流程、通知模板、文档类型等业务参数配置' },
  '/system/security': { title: '安全策略', desc: '密码规则、登录限制、会话管理与审计策略' },
  '/system/log':      { title: '操作日志', desc: '用户操作审计、登录记录与异常行为监控' }
}

const current = computed(() => pageMap[route.path] || pageMap['/system/basic'])
const pageTitle = computed(() => current.value.title)
const pageDesc = computed(() => current.value.desc)
</script>

<style scoped>
.placeholder-page { padding: 16px 0; }
</style>
