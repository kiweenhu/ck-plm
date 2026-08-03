<template>
  <div class="er-page">
    <!-- 页头 -->
    <div class="er-header">
      <h3 class="er-title">企业资源</h3>
      <span class="er-subtitle">统一管理企业图片、元器件、标准件、文档等核心资源</span>
    </div>

    <!-- Tab 切换 -->
    <a-tabs v-model:activeKey="activeTab" class="er-tabs">
      <a-tab-pane key="component" tab="电子元器件库">
        <div class="placeholder-module">
          <a-result status="info" title="电子元器件库" sub-title="管理电阻、电容、IC、连接器等电子元器件规格与库存信息">
            <template #extra>
              <a-space>
                <a-button type="primary">开始使用</a-button>
                <a-button>了解更多</a-button>
              </a-space>
            </template>
          </a-result>
          <div class="feature-grid">
            <a-row :gutter="16">
              <a-col :span="6" v-for="f in features.component" :key="f.title">
                <a-card :title="f.title" size="small" hoverable><p>{{ f.desc }}</p></a-card>
              </a-col>
            </a-row>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="stdPart" tab="结构标准件图库">
        <div class="placeholder-module">
          <a-result status="info" title="结构标准件图库" sub-title="管理螺钉、螺母、垫圈、销钉等国家标准件图纸与型号">
            <template #extra>
              <a-space>
                <a-button type="primary">开始使用</a-button>
                <a-button>了解更多</a-button>
              </a-space>
            </template>
          </a-result>
          <div class="feature-grid">
            <a-row :gutter="16">
              <a-col :span="6" v-for="f in features.stdPart" :key="f.title">
                <a-card :title="f.title" size="small" hoverable><p>{{ f.desc }}</p></a-card>
              </a-col>
            </a-row>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="genPart" tab="结构通用件图库">
        <div class="placeholder-module">
          <a-result status="info" title="结构通用件图库" sub-title="管理企业自定义的通用结构件、机加件、钣金件等图纸库">
            <template #extra>
              <a-space>
                <a-button type="primary">开始使用</a-button>
                <a-button>了解更多</a-button>
              </a-space>
            </template>
          </a-result>
          <div class="feature-grid">
            <a-row :gutter="16">
              <a-col :span="6" v-for="f in features.genPart" :key="f.title">
                <a-card :title="f.title" size="small" hoverable><p>{{ f.desc }}</p></a-card>
              </a-col>
            </a-row>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="doc" tab="企业技术文档知识库">
        <div class="placeholder-module">
          <a-result status="info" title="企业技术文档知识库" sub-title="管理产品规格书、工艺规范、检验标准、操作手册等技术文档">
            <template #extra>
              <a-space>
                <a-button type="primary">开始使用</a-button>
                <a-button>了解更多</a-button>
              </a-space>
            </template>
          </a-result>
          <div class="feature-grid">
            <a-row :gutter="16">
              <a-col :span="6" v-for="f in features.doc" :key="f.title">
                <a-card :title="f.title" size="small" hoverable><p>{{ f.desc }}</p></a-card>
              </a-col>
            </a-row>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="media" tab="产品图册">
        <MediaSpace />
      </a-tab-pane>

      <a-tab-pane key="other" tab="其他">
        <div class="placeholder-module">
          <a-result status="info" title="其他资源" sub-title="管理其他未分类的企业资源，支持自定义扩展">
            <template #extra>
              <a-space>
                <a-button type="primary">开始使用</a-button>
                <a-button>了解更多</a-button>
              </a-space>
            </template>
          </a-result>
          <div class="feature-grid">
            <a-row :gutter="16">
              <a-col :span="6" v-for="f in features.other" :key="f.title">
                <a-card :title="f.title" size="small" hoverable><p>{{ f.desc }}</p></a-card>
              </a-col>
            </a-row>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import MediaSpace from '@/views/media/MediaSpace.vue'

const activeTab = ref('component')

const features = {
  component: [
    { title: '元器件分类', desc: '按类型、封装、参数分类管理' },
    { title: '规格参数', desc: '电压、功率、精度等完整参数表' },
    { title: '库存管理', desc: '安全库存预警与批次追溯' },
    { title: 'BOM 关联', desc: '与产品 BOM 自动关联引用' },
  ],
  stdPart: [
    { title: '国标库', desc: '内置 GB 标准件参数化模型' },
    { title: '3D 预览', desc: '在线预览 STEP/IGES 三维模型' },
    { title: '一键调用', desc: 'CAD 插件直接调用标准件' },
    { title: '版本管理', desc: '标准更新后自动版本切换' },
  ],
  genPart: [
    { title: '企业件库', desc: '企业自定义通用件图纸集中管理' },
    { title: '分类标签', desc: '按功能、材料、工艺多维分类' },
    { title: '借用管理', desc: '跨产品借用与变更影响分析' },
    { title: '生命周期', desc: '从试制到量产全生命周期跟踪' },
  ],
  doc: [
    { title: '文档模板', desc: '标准化技术文档模板库' },
    { title: '全文检索', desc: '文档内容全文搜索与标签检索' },
    { title: '权限控制', desc: '按部门/角色控制查看与下载' },
    { title: '版本追溯', desc: '文档修订历史与差异对比' },
  ],
  other: [
    { title: '自定义分类', desc: '支持自行创建资源分类与属性' },
    { title: '灵活扩展', desc: '动态表单适应不同资源类型' },
    { title: '统一检索', desc: '跨资源类型全局模糊搜索' },
    { title: '导入导出', desc: 'Excel 批量导入与数据导出' },
  ],
}
</script>

<style scoped>
.er-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.er-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 16px;
  flex-shrink: 0;
  border-bottom: 1px solid #f0f0f0;
}

.er-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
}

.er-subtitle {
  font-size: 13px;
  color: #8c8c8c;
}

.er-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.er-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 16px;
  flex-shrink: 0;
}

.er-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  overflow: auto;
}

.placeholder-module {
  padding: 8px 0;
}

.feature-grid {
  margin-top: 32px;
}
</style>
