<template>
  <a-modal
    :open="open"
    title="关于 CK-PLM"
    :width="'min(720px, 94vw)'"
    :footer="null"
    :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' }"
    @cancel="close"
  >
    <div class="about-head">
      <div class="about-logo">CK</div>
      <div class="about-title">
        <div class="about-name">
          CK-PLM
          <a-tag color="green" class="about-ver">v{{ appVersion }}</a-tag>
        </div>
        <div class="about-sub">开源产品生命周期管理系统 · Open Source PLM</div>
      </div>
    </div>

    <a-descriptions :column="1" size="small" bordered class="about-desc">
      <a-descriptions-item label="许可协议">Apache-2.0（源码开放，可商用、可二次开发）</a-descriptions-item>
      <a-descriptions-item label="技术栈">
        Spring Boot 3 · Vue 3 · PostgreSQL · MyBatis · Flowable 7 / BPMN 2.0
      </a-descriptions-item>
      <a-descriptions-item label="社区站点">
        <a href="https://ckai-tech.group" target="_blank" rel="noopener noreferrer">ckai-tech.group</a>
      </a-descriptions-item>
      <a-descriptions-item label="源码仓库">
        <a href="https://github.com/kiweenhu/ck-plm" target="_blank" rel="noopener noreferrer">
          github.com/kiweenhu/ck-plm
        </a>
      </a-descriptions-item>
      <a-descriptions-item label="版权">© 2026 - 2029 深圳市乘恺科技有限公司</a-descriptions-item>
    </a-descriptions>

    <p class="about-tip">使用说明与操作手册见用户菜单 →「使用说明」。</p>
  </a-modal>
</template>

<script setup>
/** 版本号：构建期由 vite.config.js 的 define 注入（取 git 最新 tag，与 GitHub tag 对齐） */
const appVersion = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.3.1'

const props = defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['update:open'])

const close = () => emit('update:open', false)
</script>

<style scoped>
.about-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.about-logo {
  flex-shrink: 0;
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #fff;
  background: linear-gradient(135deg, #1677ff, #00d4ff);
}

.about-name {
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.about-ver {
  margin-left: 6px;
  font-size: 11px;
}

.about-sub {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.about-desc :deep(.ant-descriptions-item-label) {
  width: 92px;
  color: rgba(0, 0, 0, 0.55);
}

.about-tip {
  margin: 12px 0 0;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
</style>
