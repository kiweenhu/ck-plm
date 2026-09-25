<template>
  <div class="er-page">
    <!-- 页头 -->
    <div class="er-header">
      <h3 class="er-title">企业资源</h3>
      <span class="er-subtitle">统一管理企业图片、元器件、标准件、文档等核心资源</span>
    </div>

    <a-spin :spinning="loadingChildren">
      <!-- 动态页签：从 ck_container 资源子库加载 -->
      <a-tabs v-model:activeKey="activeTab" class="er-tabs">
        <a-tab-pane
          v-for="lib in resourceChildren"
          :key="lib.code"
          :tab="lib.name"
        >
          <!-- 元器件库 / 标准件库 / 通用件库：复用同一资源库组件（按 libCode 区分类型与容器） -->
          <component
            v-if="lib.code === 'COMPONENT' || lib.code === 'STD_PART' || lib.code === 'GEN_PART'"
            :is="componentLibraryView"
            :lib-code="lib.code"
          />
          <!-- 封装·图符库 -->
          <component
            v-else-if="lib.code === 'PACKAGE_SYMBOL'"
            :is="footprintSymbolView"
          />
          <!-- 产品图册 -->
          <MediaSpace v-else-if="lib.code === 'MEDIA'" />
          <!-- 其它资源子库：通用占位（自定义资源库入口） -->
          <div v-else class="er-resource-placeholder">
            <component
              :is="lib.icon || ExperimentOutlined"
              :style="{ fontSize: '48px', color: '#bfbfbf' }"
            />
            <h3>{{ lib.name }}</h3>
            <p v-if="lib.description" class="er-resource-desc">{{ lib.description }}</p>
            <p class="er-resource-hint">
              资源子库 code：<code>{{ lib.code }}</code>　·　
              排序：<code>{{ lib.sortOrder }}</code>　·　
              创建：{{ formatTime(lib.createdAt) }}
            </p>
            <a-tag color="processing">该子库暂未配置专属页面</a-tag>
            <div class="er-resource-actions">
              <a-button size="small" @click="onOpenChildren">查看子节点</a-button>
            </div>
          </div>
        </a-tab-pane>

        <!-- 加载失败 / 无任何资源子库时的占位 -->
        <a-tab-pane
          v-if="!loadingChildren && !resourceChildren.length"
          key="__empty"
          tab="暂未配置"
        >
          <a-result
            status="warning"
            title="尚未初始化企业级资源库"
            sub-title="请联系平台管理员检查后端启动初始化日志（ResourceContainerInitializer）"
          />
        </a-tab-pane>
      </a-tabs>
    </a-spin>
  </div>
</template>

<script setup>
import { ref, onMounted, markRaw } from 'vue'
import { ExperimentOutlined } from '@ant-design/icons-vue'
import MediaSpace from '@/views/media/MediaSpace.vue'
import ComponentLibrary from '@/views/resource/ComponentLibrary.vue'
import FootprintSymbolLibrary from '@/views/resource/FootprintSymbolLibrary.vue'
import { getResourceChildren } from '@/api'

const loadingChildren = ref(false)
const resourceChildren = ref([])
// 默认激活第一个子库
const activeTab = ref(undefined)
// 电子元器件库页（markRaw 避免响应式开销，组件无需变化）
const componentLibraryView = markRaw(ComponentLibrary)
// 封装·图符库页（左侧文件夹树 + 右侧封装/图符清单）
const footprintSymbolView = markRaw(FootprintSymbolLibrary)

async function loadResourceChildren() {
  loadingChildren.value = true
  try {
    const res = await getResourceChildren()
    if (res?.code === 200) {
      const list = res.data || []
      resourceChildren.value = list
      if (list.length && !activeTab.value) {
        activeTab.value = list[0].code
      }
    } else {
      resourceChildren.value = []
    }
  } catch {
    resourceChildren.value = []
  } finally {
    loadingChildren.value = false
  }
}

function formatTime(str) {
  if (!str) return '-'
  return String(str).replace('T', ' ').substring(0, 19)
}

function onOpenChildren() {
  // 预留：将来可跳转到该子库下的子节点/分类绑定等管理页
}

onMounted(loadResourceChildren)
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

/* 自定义资源库占位 */
.er-resource-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 16px;
  gap: 12px;
  color: #8c8c8c;
  text-align: center;
}
.er-resource-placeholder h3 {
  margin: 0;
  font-size: 20px;
  color: #434343;
}
.er-resource-desc {
  margin: 0;
  font-size: 13px;
  color: #8c8c8c;
  max-width: 520px;
  line-height: 1.5;
}
.er-resource-hint {
  margin: 0;
  font-size: 12px;
  color: #bfbfbf;
}
.er-resource-hint code {
  font-size: 11px;
  background: #f5f5f5;
  padding: 0 4px;
  border-radius: 3px;
  color: #595959;
}
.er-resource-actions {
  margin-top: 8px;
}
</style>
