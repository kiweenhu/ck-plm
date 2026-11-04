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
          v-for="lib in visibleLibraries"
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
          <!-- 技术文档知识库：业务对象是通用文档 Document（不是封装库的 EPMDocument） -->
          <component
            v-else-if="lib.code === 'TECH_DOC'"
            :is="techDocView"
          />
          <!-- 产品图册 -->
          <MediaSpace v-else-if="lib.code === 'MEDIA'" />
          <!-- 其余未配置专属页面的子库：通用占位（自定义资源库入口） -->
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

        <!-- MPN 库（参考 Windchill 的 AML/AVL）：固定页签，仅启用该能力的企业可见。
             显示名 2026-10-12 由「MPN 管理」定名「MPN 库」（用户要求）——
             能力名仍是 MPN 管理（MPN_MANAGEMENT），内部标识 key="__mpn" / 组件名 MpnManagement 均保留。
             key 用 __ 前缀避免与动态子库 code 撞（沿用 __empty 的约定） -->
        <a-tab-pane v-if="hasMpn" key="__mpn" tab="MPN 库">
          <MpnManagement />
        </a-tab-pane>
      </a-tabs>
    </a-spin>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, markRaw } from 'vue'
import { ExperimentOutlined } from '@ant-design/icons-vue'
import MediaSpace from '@/views/media/MediaSpace.vue'
import ComponentLibrary from '@/views/resource/ComponentLibrary.vue'
import FootprintSymbolLibrary from '@/views/resource/FootprintSymbolLibrary.vue'
import TechDocLibrary from '@/views/resource/TechDocLibrary.vue'
import MpnManagement from '@/views/resource/MpnManagement.vue'
import { getResourceChildren } from '@/api'
import { useCapabilities } from '@/composables/useCapabilities'

// 「MPN 库」页签的显示条件：本租户能力账本里有 MPN_MANAGEMENT（MPN 管理能力）。
// 与其它页签同口径 —— 能力没启用就不显示入口（而不是点了发现没有数据）。
const { load: loadCapabilities, has } = useCapabilities()
const hasMpn = computed(() => has('MPN_MANAGEMENT'))

const loadingChildren = ref(false)
const resourceChildren = ref([])
// 默认激活第一个子库
const activeTab = ref(undefined)
// 电子元器件库页（markRaw 避免响应式开销，组件无需变化）
const componentLibraryView = markRaw(ComponentLibrary)
// 封装·图符库页（左侧文件夹树 + 右侧封装/图符清单）
const footprintSymbolView = markRaw(FootprintSymbolLibrary)
// 技术文档知识库页：同样是「左树 + 右列表」，但对象是 Document（ck_document）
const techDocView = markRaw(TechDocLibrary)
/**
 * 不再出页签的子库。
 *
 * <p>「其他」下线（2026-10-22）：它的语义是"没配专属页面的库都塞这里"，
 * 如今元器件/标准件/通用件/封装图符/技术文档/产品图册都有专属页面，
 * 它只剩一个文档+工程数据+零件+功能件的混排列表，和一套与专属库重复的文件夹操作，
 * 反而让人以为还有一类资源没归类。
 *
 * <p>只隐藏入口，不删容器行（ck_resource_container 里的 TECH_DOC/OTHER 由装包生成，
 * 删行会让已建在该容器下的数据变成孤儿）。
 */
const HIDDEN_LIB_CODES = new Set(['OTHER'])

// 资源子库 ↔ 能力码：容器（ck_resource_container）决定"有哪些库"，能力账本决定"这个租户能不能用"。
// 两者都对才出现 —— 否则会出现"点进去是个空库"，那比不显示更糟。
// 没登记映射的子库（技术文档 / 产品图册 / 其他等）不受能力控制，照旧显示。
//
// ★ 这两个定义必须放在 resourceChildren / activeTab **之后**：
//   computed 是惰性的，但 watch 在创建时要立即读一次源 —— 摆在前面会撞上 const 的 TDZ
//   （"Cannot access 'resourceChildren' before initialization"），页面直接白屏。
//   这类错编译与静态诊断都发现不了，只有真开页面才暴露。
const LIB_CAPABILITY = {
  COMPONENT: 'COMPONENT_MANAGEMENT',
  PACKAGE_SYMBOL: 'ECAD_MANAGEMENT',
  STD_PART: 'STD_PART_LIBRARY',
  GEN_PART: 'GEN_PART_QUALIFY'
}
const visibleLibraries = computed(() =>
  resourceChildren.value.filter(lib => {
    if (HIDDEN_LIB_CODES.has(lib.code)) return false
    const cap = LIB_CAPABILITY[lib.code]
    return !cap || has(cap)
  })
)

// 能力是异步到的：若能力到达后当前选中的子库被过滤掉了，切到第一个可见的
// （否则 activeTab 停在一个不存在的页签上，页面看起来是空的）
watch(visibleLibraries, (list) => {
  if (list.length && !list.some(l => l.code === activeTab.value)) {
    activeTab.value = list[0].code
  }
})

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

onMounted(() => {
  loadResourceChildren()
  // 能力账本读一次（模块级单例，多了也只是共享同一个 Promise）；MPN 页签据此显隐
  loadCapabilities()
})
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
