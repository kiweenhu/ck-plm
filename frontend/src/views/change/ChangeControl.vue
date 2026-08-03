<template>
  <div class="placeholder-page">
    <a-result
      status="info"
      title="变更控制"
      sub-title="管理工程变更请求 (ECR)、变更通知 (ECN) 和变更实施跟踪"
    >
      <template #extra>
        <a-space>
          <a-button type="primary">新建变更请求</a-button>
          <a-button>变更看板</a-button>
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
  { title: '变更请求', desc: 'ECR 提交、评审与影响分析' },
  { title: '变更通知', desc: 'ECN 审批、发布与通知分发' },
  { title: '实施跟踪', desc: '变更任务执行与进度监控' },
  { title: '版本控制', desc: '文档/BOM 版本关联与回溯' }
]
</script>

<style scoped>
.placeholder-page { padding: 16px 0; }
.feature-list { margin-top: 32px; }
</style>
