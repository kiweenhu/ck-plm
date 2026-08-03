<template>
  <div class="business-config">
    <!-- 左上：业务配置中心标题 -->
    <div class="bc-header">
      <h2 class="bc-title">业务配置中心</h2>
      <p class="bc-desc">
>        集中管理软类型（SoftType）元数据与系统基础配置。涵盖类型建模、属性扩展、生命周期、版本/编码规则、
        视图布局、研发阶段模板、单位体系、分类绑定、数据字典等，支持运行时动态配置，无需重启服务或修改代码。
      </p>
    </div>

    <div class="bc-body">
      <!-- 左侧模块导航 -->
      <div class="bc-sidebar">
        <div
          v-for="mod in visibleModules"
          :key="mod.key"
          :class="['bc-module-item', { active: activeModule === mod.key }]"
          @click="switchModule(mod)"
        >
          <component :is="mod.icon" class="bc-module-icon" />
          <span class="bc-module-label">{{ mod.label }}</span>
          <span v-if="mod.badge" class="bc-module-badge">{{ mod.badge }}</span>
        </div>
      </div>

      <!-- 右侧内容区 -->
      <div class="bc-content">
        <!-- 数据模型子模块 -->
        <template v-if="activeModule === 'datamodel'">
          <div class="bc-embedded-page">
            <TypePage />
          </div>
        </template>

        <!-- 角色定义子模块 -->
        <!-- 版本规则子模块 -->
        <template v-else-if="activeModule === 'versionrule'">
          <div class="bc-embedded-page">
            <VersionRuleConfig />
          </div>
        </template>

        <!-- 生命周期子模块 -->
        <template v-else-if="activeModule === 'lifecycle'">
          <div class="bc-embedded-page">
            <LifecycleConfig />
          </div>
        </template>

        <!-- 视图定义子模块 -->
        <template v-else-if="activeModule === 'view'">
          <div class="bc-embedded-page">
            <ViewConfig />
          </div>
        </template>

        <!-- 编码规则子模块 -->
        <template v-else-if="activeModule === 'numberrule'">
          <div class="bc-embedded-page">
            <NumberRuleConfig />
          </div>
        </template>

        <!-- 研发阶段子模块 -->
        <template v-else-if="activeModule === 'stagetemplate'">
          <div class="bc-embedded-page">
            <StageTemplateConfig />
          </div>
        </template>

        <!-- 平台成员子模块 -->
        <template v-else-if="activeModule === 'platform'">
          <div class="bc-embedded-page">
            <PlatformMemberConfig />
          </div>
        </template>

        <!-- 单位配置子模块 -->
        <template v-else-if="activeModule === 'unit'">
          <div class="bc-embedded-page">
            <UnitConfig />
          </div>
        </template>

        <!-- 占位模块 -->
        <template v-else>
          <div class="bc-placeholder">
            <component :is="activeModDef?.icon" :style="{ fontSize: '48px', color: '#bfbfbf' }" />
            <h3>{{ activeModDef?.label }}</h3>
            <p>{{ activeModDef?.desc }}</p>
            <a-tag color="processing">规划中</a-tag>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import {
  ClusterOutlined,
  BookOutlined,
  CompassOutlined,
  ToolOutlined,
  ThunderboltOutlined,
  TeamOutlined,
  NumberOutlined,
  BarcodeOutlined,
  RetweetOutlined,
  EyeOutlined,
  RocketOutlined,
} from '@ant-design/icons-vue'
import TypePage from '@/views/system/TypePage.vue'
import VersionRuleConfig from '@/views/system/VersionRuleConfig.vue'
import PlatformMemberConfig from '@/views/system/PlatformMemberConfig.vue'
import NumberRuleConfig from '@/views/system/NumberRuleConfig.vue'
import LifecycleConfig from '@/views/system/LifecycleConfig.vue'
import ViewConfig from '@/views/system/ViewConfig.vue'
import StageTemplateConfig from '@/views/system/StageTemplateConfig.vue'
import UnitConfig from '@/views/system/UnitConfig.vue'

// ---- 模块定义 ----
const modules = [
  { key: 'datamodel',  label: '模型定义',     icon: ClusterOutlined,    desc: '业务对象建模、属性定义、IBA 软属性扩展与关系映射' },
  { key: 'lifecycle',  label: '生命周期',     icon: RetweetOutlined,    desc: '定义对象生命周期阶段、状态与流转规则，含审批流程集成' },
  { key: 'view',       label: '视图定义',     icon: EyeOutlined,        desc: '按业务角色定义数据视图，控制字段可见性与布局切换' },
  { key: 'versionrule',label: '版本规则',    icon: NumberOutlined,     desc: '大版本/小版本编码模板，含 CheckIn/CheckOut 版本号生成策略' },
  { key: 'numberrule',  label: '编码规则',    icon: BarcodeOutlined,    desc: '物料、文档等业务对象的编码段组合模板，支持分类码与流水号' },
  { key: 'stagetemplate', label: '研发阶段',  icon: RocketOutlined,     desc: '研发阶段元数据模板，创建产品线/型号时按模板生成阶段节点' },
  { key: 'platform',   label: '平台成员',     icon: TeamOutlined,       desc: '平台级角色（管理员/租户管理员）成员分配与管理' },
  { key: 'dict',       label: '数据字典',     icon: BookOutlined,       desc: '枚举值、代码表与标准化参考数据集中维护' },
  { key: 'unit',       label: '单位配置',     icon: CompassOutlined,    desc: '计量单位体系定义，含量纲类型、SI 标准、换算系数与偏移量' },
  { key: 'cad',        label: 'CAD 集成',     icon: ToolOutlined,       desc: 'AutoCAD / SolidWorks / CATIA 设计工具连接与数据同步配置' },
  { key: 'lightweight',label: '轻量化转换',   icon: ThunderboltOutlined,desc: '3D 模型轻量化处理、多格式转换引擎与服务端点配置' },
]

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

// 过滤后的模块列表：平台成员仅平台管理员可见
const visibleModules = computed(() => {
  if (isPlatformAdmin.value) return modules
  return modules.filter(m => m.key !== 'platform')
})

const activeModule = ref('datamodel')
const activeModDef = computed(() => modules.find(m => m.key === activeModule.value))

function switchModule(mod) {
  activeModule.value = mod.key
}
</script>

<style scoped>
.business-config {
  margin: -24px;        /* 抵消 layout-content 的 padding */
  height: calc(100vh - 56px - 32px);
  display: flex;
  flex-direction: column;
}

/* ===== 顶栏 ===== */
.bc-header {
  padding: 16px 24px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
  flex-shrink: 0;
}
.bc-title {
  margin: 0 0 4px 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}
.bc-desc {
  margin: 0;
  font-size: 13px;
  color: #8c8c8c;
  line-height: 1.5;
}

/* ===== 主体 ===== */
.bc-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== 左侧模块导航 ===== */
.bc-sidebar {
  width: 200px;
  flex-shrink: 0;
  background: #fafafa;
  border-right: 1px solid #f0f0f0;
  padding: 8px 0;
  overflow-y: auto;
}
.bc-module-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  cursor: pointer;
  color: #595959;
  font-size: 14px;
  transition: all 0.2s;
  border-left: 3px solid transparent;
  position: relative;
}
.bc-module-item:hover {
  background: #e6f4ff;
  color: #1677ff;
}
.bc-module-item.active {
  background: #e6f4ff;
  color: #1677ff;
  font-weight: 500;
  border-left-color: #1677ff;
}
.bc-module-icon {
  font-size: 16px;
  flex-shrink: 0;
}
.bc-module-label {
  flex: 1;
}
.bc-module-badge {
  background: #1677ff;
  color: #fff;
  font-size: 11px;
  border-radius: 10px;
  padding: 0 6px;
  height: 18px;
  line-height: 18px;
  min-width: 18px;
  text-align: center;
}

/* ===== 右侧内容 ===== */
.bc-content {
  flex: 1;
  overflow: auto;
  background: #fff;
}

/* 占位模块样式 */
.bc-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: #8c8c8c;
}
.bc-placeholder h3 {
  margin: 0;
  font-size: 20px;
  color: #434343;
}
.bc-placeholder p {
  margin: 0;
  font-size: 14px;
  color: #8c8c8c;
}

/* 工作流 tabs */
/* 嵌入数据模型时撑满容器 */
.bc-content :deep(.softtype-page) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 16px 24px;
}
.bc-content :deep(.softtype-page .top-bar) {
  flex-shrink: 0;
}
.bc-content :deep(.softtype-page .content-area) {
  flex: 1;
  overflow: hidden;
}
.bc-content :deep(.softtype-page .tree-container) {
  overflow: auto;
}
.bc-content :deep(.softtype-page .detail-panel) {
  overflow: auto;
}

/* 嵌入子页面通用样式 */
.bc-embedded-page {
  height: 100%;
  overflow: auto;
  padding: 16px 24px;
}
</style>
