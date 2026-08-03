<template>
  <div class="placeholder-page">
    <a-result
      status="info"
      title="持续改进"
      sub-title="管理质量改进计划、CAPA、PDCA 循环及改进成果跟踪"
    >
      <template #extra>
        <a-space>
          <a-button type="primary">新建改进工单</a-button>
          <a-button>质量统计</a-button>
        </a-space>
      </template>
    </a-result>
    <div class="feature-list">
      <a-row :gutter="16">
        <a-col :span="6" v-for="f in features" :key="f.title">
          <a-card :title="f.title" size="small" hoverable>
            <p>{{ f.desc }}</p>
          </a-card>
        </a-col>
      </a-row>
    </div>
  </div>
</template>

<script setup>
const features = [
  { title: '改进工单', desc: '问题记录、根因分析与方案制定' },
  { title: 'CAPA', desc: '纠正与预防措施全流程闭环' },
  { title: 'PDCA', desc: '计划-执行-检查-改进循环管理' },
  { title: '统计分析', desc: '改进效率、趋势与 KPI 看板' }
]
</script>

<style scoped>
.placeholder-page { padding: 16px 0; }
.feature-list { margin-top: 32px; }
</style>
