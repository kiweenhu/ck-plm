<template>
  <div class="task-handle">
    <div class="th-layout">
      <!-- ==================== 左列：流程进度 ====================
           与本行右列的页头对齐并吸顶常驻：办理时要一边填表一边看"卡在哪、别人提了什么"，
           把进度埋在通用信息里就得来回滚动 —— 它是办理过程中被看得最频繁的一块。 -->
      <aside v-if="showSide" class="th-side">
        <a-card size="small" title="流程进度" class="th-side-card">
          <!-- 可点开：办理时最需要知道"上一步的自动服务到底跑没跑、报了什么错" -->
          <ProcessActivityTimeline
            :activities="context?.activities || []"
            :loading="loading"
            compact
            clickable
            scroll-to-current
            @select="openNodeDetail"
          />
        </a-card>
      </aside>

      <!-- ==================== 右列：页头 + 通用信息 + 办理表单 ==================== -->
      <div class="th-main">
        <!-- 页头答「办的是哪一条」：业务实体（名称 + 编码 + 大版本）｜ 流程名 · 任务名。
             新窗口常同时开好几个，只给任务名+流程名时两张卡片长得几乎一样，
             只能翻下面的「通用信息」去找实体 —— 那正是页头该省掉的动作。 -->
        <div class="th-head">
          <div class="th-head-left">
            <a-button type="text" class="th-back" @click="goTaskCenter">
              <template #icon><ArrowLeftOutlined /></template>
              任务中心
            </a-button>
            <a-divider type="vertical" />

            <template v-if="primaryEntity">
              <h2 class="th-title">{{ entityTitle }}</h2>
              <span v-if="entityCode" class="th-entity-code">{{ entityCode }}</span>
              <a-tag v-if="entityVersion" color="blue">{{ entityVersion }}</a-tag>
              <span v-if="hasMoreEntities" class="th-muted">等 {{ entities.length }} 个实体</span>
              <a-divider type="vertical" />
              <span class="th-flow-task">{{ flowTask }}</span>
            </template>

            <!-- 没有关联业务实体的流程（纯审批流）：退回按任务显示，不摆一块空白 -->
            <template v-else>
              <h2 class="th-title">{{ task?.name || '办理任务' }}</h2>
              <a-tag v-if="task?.processDefinitionName" color="blue">{{ task.processDefinitionName }}</a-tag>
            </template>

            <a-tag v-if="task?.overdue" color="red">已逾期</a-tag>
          </div>
        </div>

        <a-spin :spinning="loading">
          <!-- 任务已被别人办掉 / 不存在：正常情况（两人同时打开），给一句人话 + 出口 -->
          <a-result v-if="notFound" status="info" title="该任务已不存在" :sub-title="notFoundMessage">
            <template #extra>
              <a-space>
                <a-button type="primary" @click="goTaskCenter">返回任务中心</a-button>
                <a-button @click="reload">刷新看看</a-button>
              </a-space>
            </template>
          </a-result>

          <!-- 办理完成 -->
          <a-result v-else-if="finished" status="success" title="已办理完成" :sub-title="finishedText">
            <template #extra>
              <a-space>
                <a-button type="primary" @click="closeWindow">关闭本窗口</a-button>
                <a-button @click="goTaskCenter">返回任务中心</a-button>
              </a-space>
            </template>
          </a-result>

          <template v-else-if="context">
            <!-- ① 通用信息：业务实体 / 流程 / 当前任务（渲染架构的固定部分）
                 实体编码/名称由本页统一回查后下传：页头与这里必须显示同一个名字 -->
            <TaskContextView :context="context" :entity-infos="entityInfos" />

            <!-- ② 任务表单：按 formKey 派发到模板（见 components/task/taskForms） -->
            <a-card size="small" class="th-card" :title="formCardTitle">
              <TaskFormRenderer :task-id="taskId" :form="context.form"
                :activities="context?.activities || []"
                @completed="onCompleted" @cancel="goTaskCenter" />
            </a-card>
          </template>
        </a-spin>
      </div>
    </div>

    <!-- 节点执行详情：点左栏进度里的任意节点，看这一步后台跑了什么、报了什么错 -->
    <ProcessNodeDetailDrawer
      v-model:open="nodeDrawerOpen"
      :activity="nodeActivity"
      :instance-id="instanceId"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { getTaskContext } from '@/api'
import TaskContextView from '@/components/task/TaskContextView.vue'
import TaskFormRenderer from '@/components/task/TaskFormRenderer.vue'
import ProcessActivityTimeline from '@/components/ProcessActivityTimeline.vue'
import ProcessNodeDetailDrawer from '@/components/ProcessNodeDetailDrawer.vue'
import { effectiveFormCode, hasOwnTaskForm, taskFormLabel } from '@/components/task/taskForms'
import { useEntityInfo } from '@/composables/useEntityInfo'
import {
  entityHeadline, entityCodeText, entityVersionText, flowTaskText,
} from '@/utils/taskHeader'

/**
 * 任务办理页 —— 从任务中心「办理」按钮<b>在新窗口打开</b>的独立页面。
 *
 * <p>为什么不用弹窗：办理时要一边填表单、一边核对"这条流程走到哪、别人提了什么意见"，
 * 还常要同时开着对象详情图纸对照；弹窗被表格夹在中间，多窗口对照时互相遮挡。
 *
 * <h3>渲染架构（本页只做组装）</h3>
 * <pre>
 *   GET /workflow/task/{id}/context   ← 一次给齐「通用信息 + 表单 + 进度」
 *     ├─ 页头                          ← 实体名称+编码+大版本 ｜ 流程名 · 任务名
 *     ├─ ProcessActivityTimeline      ← 左列 · 流程进度（紧凑排版，吸顶常驻）
 *     ├─ TaskContextView              ← 右列 · 通用信息：业务实体 / 流程 / 当前任务
 *     └─ TaskFormRenderer             ← 按 formKey 派发模板（taskForms/ 注册表）
 *          ├─ SetupAssigneeForm （内置：设置流程参与者）
 *          └─ GenericApproveForm（兜底：同意 / 驳回 + 意见）
 * </pre>
 * 页头里的实体编码/名称后端不给（只给引用），由本页回查一次后同时喂给页头与 TaskContextView
 * （见 composables/useEntityInfo）—— 两处各查一遍会出现同一实体两次请求且可能显示不一致。
 * 页面本身不含业务规则：信息怎么显示、表单怎么渲染、提交怎么校验，都在上面这些组件里，
 * 所以"再加一种节点表单"不需要动这一页。
 */
const route = useRoute()
const router = useRouter()

const taskId = computed(() => String(route.params.id || ''))
const loading = ref(false)
const context = ref(null)
const notFound = ref(false)
const notFoundMessage = ref('')
const finished = ref(false)
const finishedText = ref('')

const task = computed(() => context.value?.task || null)

// ==================== 节点执行详情（左栏进度里点开的那个抽屉）====================

const nodeDrawerOpen = ref(false)
const nodeActivity = ref(null)
/** 实例 id：节点日志按「实例 + 节点」查（上下文里的 process.id 就是实例 id） */
const instanceId = computed(() => context.value?.process?.id || '')

function openNodeDetail(activity) {
  nodeActivity.value = activity
  nodeDrawerOpen.value = true
}

// ==================== 页头：业务实体 + 流程 · 任务 ====================

/** 实体编码/名称由本页统一回查一次，页头与「通用信息」共用（见 composables/useEntityInfo） */
const { infos: entityInfos } = useEntityInfo(context)

const entities = computed(() => context.value?.entities || [])
/** 页头只讲第一个实体；多实体时补一句"等 N 个"，完整清单在「通用信息」里 */
const primaryEntity = computed(() => entities.value[0] || null)
const hasMoreEntities = computed(() => entities.value.length > 1)

const entityTitle = computed(() =>
  entityHeadline(primaryEntity.value, entityInfos.value[primaryEntity.value?.entityOid]))
const entityCode = computed(() =>
  entityCodeText(entityInfos.value[primaryEntity.value?.entityOid]))
const entityVersion = computed(() => entityVersionText(primaryEntity.value))
/** 流程名与「通用信息」同一取法：先流程实例、退回任务上的定义名 */
const flowTask = computed(() => flowTaskText(
  context.value?.process?.processDefinitionName || task.value?.processDefinitionName,
  task.value?.name))

/** 结果态（已不存在 / 已办理完成）不摆进度栏：那两种情形下进度已无参考价值，单列居中更清爽 */
const showSide = computed(() => !!context.value && !notFound.value && !finished.value)

/**
 * 卡片标题带上"用的是哪个表单模板"，让人知道自己填的是什么。
 *
 * <p>用 {@link effectiveFormCode} 而不是裸的 formKey：部署时没写 formKey 的老实例
 * 也要按节点类型显示出专属模板名（否则标题停在「办理」，与下面渲染的表单对不上）。
 */
const formCardTitle = computed(() => {
  const code = effectiveFormCode(context.value?.form)
  if (!code || !hasOwnTaskForm(code)) return '办理'
  return `办理 · ${taskFormLabel(code)}`
})

async function reload() {
  notFound.value = false
  finished.value = false
  loading.value = true
  try {
    const res = await getTaskContext(taskId.value)
    if (res?.code === 200 && res.data) {
      context.value = res.data
    } else {
      context.value = null
      notFound.value = true
      notFoundMessage.value = res?.message || '任务不存在或已被办理'
    }
  } catch {
    context.value = null
    notFound.value = true
    notFoundMessage.value = '拿不到任务信息，可能已被办理或没有权限'
  } finally {
    loading.value = false
  }
}

function onCompleted(result) {
  if (result?.ok === false) {
    // 并发办理：别人先办掉了 —— 刷新成"已不存在"，别停在一个假装还能提交的表单上
    notFoundMessage.value = result.message || '任务已被处理'
    context.value = null
    notFound.value = true
    return
  }
  finishedText.value = result?.text || '审批结果已提交，流程继续流转'
  finished.value = true
}

/** 从任务中心点开（window.opener）时直接关窗回列表；被当成标签页打开时用路由返回 */
function closeWindow() {
  window.close()
  goTaskCenter()
}

function goTaskCenter() {
  router.push('/workflow/task')
}

onMounted(reload)
</script>

<style scoped>
.task-handle {
  padding: 16px 20px 24px;
}

/* 两列：左列（流程进度）固定宽并吸顶，右列内容自适应 */
.th-layout {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.th-main {
  flex: 1 1 auto;
  min-width: 0;
}

.th-side {
  flex: 0 0 320px;
  /* 吸顶：长表单往下滚时进度仍在视野里 —— 这正是把它固定在左栏的目的 */
  position: sticky;
  top: 12px;
}

/* 进度栏自己滚：节点多（十几个）时不把整页撑长，吸顶才成立 */
.th-side-card :deep(.ant-card-body) {
  max-height: calc(100vh - 160px);
  overflow-y: auto;
}

/* 窄屏（分屏/小笔记本）堆叠：320px 固定栏会把表单挤到没法填 */
@media (max-width: 1100px) {
  .th-layout {
    flex-direction: column;
  }

  /* DOM 里进度在前（宽屏时它在左），堆叠后要把表单换回前面 ——
     办理才是本页的主任务，进度不该把表单挤到屏幕外 */
  .th-main {
    order: 1;
  }

  .th-side {
    order: 2;
    flex: 1 1 auto;
    width: 100%;
    position: static;
  }

  .th-side-card :deep(.ant-card-body) {
    max-height: none;
  }
}

.th-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

/* 页头现在要放「实体名+编码+版本 ｜ 流程 · 任务」，窄窗口一行放不下：
   允许换行（并给行间距），否则 flex 会把长编码压成省略号、信息直接丢掉 */
.th-head-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
  min-width: 0;
}

/* 实体编码：等宽字体，与 oid / 编号这类"要抄下来"的值同一视觉 */
.th-entity-code {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  color: #595959;
}

/* 「流程名 · 任务名」：交代"在办什么"，比纯辅助信息重要一档，但不该压过实体名 */
.th-flow-task {
  font-size: 13px;
  color: #595959;
}

.th-muted {
  font-size: 12px;
  color: #8c8c8c;
}

.th-back {
  padding-left: 0;
  color: #595959;
}

.th-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #262626;
}

.th-card {
  margin-bottom: 14px;
}
</style>
