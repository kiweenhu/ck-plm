<template>
  <div class="template-library">
    <!-- 页头：与「编号规则」页保持同一套排版（标题 18/600 + 12px 灰副标题） -->
    <div class="lib-header">
      <div class="lib-header-left">
        <h3 class="lib-title">流程清单</h3>
        <span class="lib-subtitle">
          先建分组 → 选中分组 → 在组内设计流程；支持复制 / 停止部署 / 删除 / 移动分组
        </span>
      </div>
    </div>

    <!-- 统计栏：样式对齐「编号规则」页 -->
    <div class="lib-stats-bar">
      <div class="lib-stat-item">
        <ApartmentOutlined class="lib-stat-icon" />
        <span class="lib-stat-value">{{ allTemplates.length }}</span>
        <span class="lib-stat-label">流程总数</span>
      </div>
      <a-divider type="vertical" style="height: 24px" />
      <div class="lib-stat-item">
        <a-tag color="green" size="small">允许部署</a-tag>
        <span class="lib-stat-value">{{ enabledCount }}</span>
      </div>
      <div class="lib-stat-item">
        <a-tag color="default" size="small">停止部署</a-tag>
        <span class="lib-stat-value">{{ disabledCount }}</span>
      </div>
      <a-divider type="vertical" style="height: 24px" />
      <div class="lib-stat-item">
        <a-tag color="success" size="small">已部署</a-tag>
        <span class="lib-stat-value">{{ deployedCount }}</span>
      </div>
      <div class="lib-stat-item">
        <a-tag color="warning" size="small">有未部署改动</a-tag>
        <span class="lib-stat-value">{{ pendingDeployCount }}</span>
      </div>
    </div>

    <!-- 主从：左分组（含数量）/ 右该分组下的流程列表 -->
    <div class="lib-body">
      <aside class="library-categories">
        <div class="library-categories__head">
          <span class="library-categories__title">分组</span>
          <a class="category-add" @click="openCategoryCreate">新建分组</a>
        </div>
        <div
          v-for="item in categories"
          :key="String(item.value)"
          class="category-item"
          :class="{ 'is-active': item.value === activeCategory }"
          @click="pickCategory(item.value)"
        >
          <span class="category-item__label" :title="item.label">{{ item.label }}</span>
          <span class="category-item__count">{{ item.count }}</span>
          <!-- 只有字典里的真分组可改可删；「全部」「未分类」是筛选项，不是分组 -->
          <span v-if="item.group" class="category-item__ops">
            <a @click.stop="openCategoryRename(item)">改名</a>
            <a-popconfirm
              title="删除分组（组内必须没有流程）？"
              @confirm="removeCategory(item)"
            >
              <a class="danger" @click.stop>删除</a>
            </a-popconfirm>
          </span>
        </div>
        <div v-if="!hasGroups" class="category-empty muted">还没有分组，先「新建分组」再设计流程</div>
      </aside>

      <section class="library-main">
        <div class="library-toolbar">
          <a-input-search
            v-model:value="keyword"
            placeholder="搜索流程 key / 名称"
            style="width: 240px"
            allow-clear
          />
          <a-select v-model:value="enabledFilter" style="width: 130px" :options="enabledOptions" />
          <div class="library-toolbar__right">
            <a-button size="small" :loading="loading" @click="reload">刷新</a-button>
            <a-button size="small" type="primary" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新建流程
            </a-button>
          </div>
        </div>

        <div class="library-heading">
          {{ activeLabel }}
          <span class="muted">· {{ filtered.length }} 个流程</span>
        </div>

        <a-empty
          v-if="!loading && !filtered.length"
          :description="emptyHint"
          :image-style="{ height: '48px' }"
        >
          <a-space>
            <a-button v-if="!hasGroups" size="small" @click="openCategoryCreate">新建分组</a-button>
            <a-button size="small" type="primary" @click="openCreate">新建流程</a-button>
          </a-space>
        </a-empty>

        <a-table
          v-else
          :data-source="filtered"
          :columns="columns"
          :loading="loading"
          :pagination="false"
          row-key="oid"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <a class="link" @click="emit('open', record.oid)">{{ record.displayName || record.name }}</a>
              <div class="muted">{{ record.key }}</div>
            </template>
            <template v-else-if="column.key === 'category'">
              <!-- 显示名由分组字典解析：模板表里只存 categoryOid -->
              <a v-if="record.categoryOid" class="link" @click="pickCategory(record.categoryOid)">
                {{ groupNameOf(record) || '未知分组' }}
              </a>
              <span v-else class="muted">未分类</span>
            </template>
            <template v-else-if="column.key === 'version'">
              <span>v{{ record.latestVersion ?? 1 }}</span>
              <a-tag v-if="record.deployedVersion === record.latestVersion" color="success" style="margin-left: 6px">
                已部署
              </a-tag>
              <a-tag v-else-if="record.deployedVersion" color="warning" style="margin-left: 6px">
                有未部署改动
              </a-tag>
            </template>
            <template v-else-if="column.key === 'enabled'">
              <a-tag :color="record.enabled ? 'success' : 'default'">
                {{ record.enabled ? '允许部署' : '停止部署' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a @click="emit('open', record.oid)">打开</a>
                <a @click="openCopy(record)">复制</a>
                <a @click="openMove(record)">移动</a>
                <a @click="toggleEnabled(record)">{{ record.enabled ? '停止部署' : '允许部署' }}</a>
                <!-- 删除只能按版本进行：打开弹窗列出全部版本，已部署的置灰不可选 -->
                <a @click="openDeleteVersions(record)">删除版本</a>
              </a-space>
            </template>
          </template>
        </a-table>
      </section>
    </div>

    <!-- 新建：分组必选（下拉，只能选字典里的分组），key/name 即 DSL 的 meta -->
    <a-modal v-model:open="createVisible" title="新建流程模板" :confirm-loading="busy" @ok="doCreate">
      <a-form layout="vertical">
        <a-form-item label="流程 key" required>
          <a-input v-model:value="form.key" placeholder="如 part_release（字母开头，可含数字/下划线/连字符）" />
        </a-form-item>
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="如 零组件发布流程" />
        </a-form-item>
        <a-form-item label="分组" required>
          <!-- 只能选、不能输入：分组是清单页的导航骨架，自由输入会分裂出「研发 / 研发部」这类近义分组 -->
          <a-select
            v-model:value="form.categoryOid"
            :options="categoryOptions"
            placeholder="选择分组"
            :disabled="!hasGroups"
          />
          <div v-if="!hasGroups" class="muted">还没有分组，请先在左侧「新建分组」</div>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="copyVisible" title="复制 / 另存" :confirm-loading="busy" @ok="doCopy">
      <a-form layout="vertical">
        <a-form-item label="新流程 key" required>
          <a-input v-model:value="copyForm.key" placeholder="必须与原 key 不同" />
        </a-form-item>
        <a-form-item label="新名称">
          <a-input v-model:value="copyForm.name" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 分组：新建 / 改名（改名会同步组内模板，见后端 ProcessCategoryService#update） -->
    <a-modal
      v-model:open="categoryVisible"
      :title="categoryForm.oid ? '重命名分组' : '新建分组'"
      :confirm-loading="busy"
      @ok="doSaveCategory"
    >
      <a-form layout="vertical">
        <a-form-item label="分组名" required>
          <a-input v-model:value="categoryForm.name" placeholder="如 研发流程 / 变更流程" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="categoryForm.sortOrder" :min="0" style="width: 120px" />
          <span class="muted" style="margin-left: 8px">越小越靠前</span>
        </a-form-item>
        <a-form-item label="说明">
          <a-input v-model:value="categoryForm.description" placeholder="可空" />
        </a-form-item>
        <div v-if="categoryForm.oid" class="muted">改名会同步该分组下全部流程的分组名</div>
      </a-form>
    </a-modal>

    <!-- 移动到分组：分类的唯一修改入口（设计器里不再提供分类字段） -->
    <a-modal v-model:open="moveVisible" title="移动到分组" :confirm-loading="busy" @ok="doMove">
      <a-form layout="vertical">
        <a-form-item label="目标分组" required>
          <a-select v-model:value="moveForm.categoryOid" :options="categoryOptions" placeholder="选择分组" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!--
      删除版本：流程删除的唯一方式 —— 列出全部版本，已部署的置灰不可选，勾选后删除。
      已部署的版本不可删（引擎中的流程定义与历史实例仍引用它）；删完最后一个版本 → 流程整体消失。
    -->
    <a-modal
      v-model:open="versionsVisible"
      title="删除版本"
      :confirm-loading="busy"
      :ok-button-props="{ disabled: !selectedVersions.length }"
      ok-text="删除所选版本"
      @ok="doDeleteVersions"
    >
      <div class="muted versions-tip">
        流程删除只能按版本进行：<b>已部署的版本不能删除</b>
        （流程引擎中的流程定义与历史实例仍引用它），请勾选未部署的版本。
      </div>
      <a-table
        :data-source="versionRows"
        :columns="versionColumns"
        :loading="versionsLoading"
        :pagination="false"
        :row-selection="versionRowSelection"
        row-key="version"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'version'">
            <a-tag :color="record.version === currentLatestVersion ? 'blue' : 'default'">
              v{{ record.version }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'deployed'">
            <a-tag v-if="record.deployed" color="success">已部署</a-tag>
            <span v-else class="muted">未部署</span>
          </template>
        </template>
      </a-table>
      <a-alert
        v-if="!deletableVersions.length"
        type="info"
        show-icon
        message="该流程的版本都已部署，没有可删除的版本"
        description="如不再使用该流程，请把「允许部署」改为「停止部署」。"
      />
      <a-alert
        v-else-if="selectedVersions.length === versionRows.length"
        type="warning"
        show-icon
        message="所有版本都会被删除，该流程将一并消失"
        description="删除后不可恢复。如需保留该流程，请至少留一个版本。"
      />
      <div v-else-if="lockedVersions.length" class="muted versions-tip">
        另有 {{ lockedVersions.length }} 个已部署版本已被锁定（不可选）。
      </div>
    </a-modal>
  </div>
</template>

<script setup>
/**
 * 流程模板库（spec §4-H：CRUD / 版本 / 部署开关 / 复制另存）。
 *
 * <p>版式与「编号规则」页（`views/system/NumberRuleConfig.vue`）对齐：
 * <b>页头 → 统计栏 → 内容区</b>，统计栏用同一套取值（#fafafa 底、1px 边框、gap 20px，
 * 数字 700 粗体）。跨页统一比各自发明一套更省心。
 *
 * <p>内容区是主从结构：<b>左侧分组（含数量）→ 右侧该分组下的流程列表</b>。
 * 用法是「<b>先建分组 → 选中分组 → 在组内设计流程</b>」，因此：
 * <ul>
 *   <li>分组来自<b>字典</b>（{@link categoryRepository}），不是从模板聚合出来的名字 ——
 *       刚建好的空分组必须看得见，否则用户建完就找不到它；</li>
 *   <li>新建流程的「分组」是<b>必选下拉</b>，不再是自由输入（那正是
 *       「研发 / 研发部 / 研发中心」这类近义分裂的来源）；</li>
 *   <li>换组走行内「移动」—— 分类的唯一修改入口，设计器里不再提供分类字段
 *       （避免"设计器改了、保存却不生效"的第二处真相）。</li>
 * </ul>
 *
 * <p>只做「列表与入口」：真正的治理规则（校验拦截、key 不可变、分组必填）在
 * `template-repo` / `category-repo`，本组件不重复实现，只负责把仓库返回的
 * `{ ok, error }` 呈现出来。
 *
 * <p><b>筛选为什么在客户端</b>：左侧要显示各分组的数量，就必须知道「全量台账」，
 * 否则按分组过滤后，其他分组的数量无从得知。所以这里一次取全量，
 * 分组 / 关键字 / 部署开关都在本地筛选 —— 切换分组零延迟，数量也永远与列表一致。
 * 模板量上去需要分页时，把筛选移回服务端即可（`list({ category })` 接口已支持），
 * 改动只落在 {@link reload} 与筛选的 computed 上。
 *
 * <p>统计栏的数字一律取<b>全量台账</b>（不随左栏筛选变化）：它是「有多少流程」的
 * 稳定概览，跟着筛选跳动反而看不出全局。
 *
 * <p>当前分组同时回传给页面（`update:category`）写进 URL：从设计器返回列表时
 * 仍停在原分组，"在组内设计流程"的上下文不会丢。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { ApartmentOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { createEmptyDsl } from '@flow-dsl-core'
import { templateRepository } from './template-service'
import { UNCATEGORIZED, countTemplatesByCategoryOid } from './category-repo'
import { categoryRepository } from './category-service'
import { isVersionDeletable, splitDeletableVersions } from './template-repo'

const props = defineProps({
  /** 当前分组 oid（页面持有并写入 URL；null = 全部，UNCATEGORIZED = 未分类） */
  category: { type: String, default: null },
})

const emit = defineEmits(['open', 'update:category'])

/** 全量台账（不带筛选） */
const allTemplates = ref([])
/** 分组字典（后端 ck_process_category，已按 sort_order、name 排好） */
const groups = ref([])
const loading = ref(false)
const busy = ref(false)
const keyword = ref('')
const enabledFilter = ref(null)
/** null = 全部；UNCATEGORIZED = 未分类；其余为分组 <b>oid</b>（模板按 oid 引用分组） */
const activeCategory = ref(props.category ?? null)

// 页面是分组的持有者（写 URL），故外部变化要同步回来 —— 如浏览器前进后退
watch(
  () => props.category,
  (value) => {
    const next = value ?? null
    if (next !== activeCategory.value) {
      activeCategory.value = next
    }
  },
)

const columns = [
  { title: '名称 / key', key: 'name', width: 240 },
  { title: '分组', key: 'category', width: 120 },
  { title: '版本', key: 'version', width: 160 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '操作', key: 'action', width: 260 },
]

const enabledOptions = [
  { value: null, label: '全部' },
  { value: true, label: '允许部署' },
  { value: false, label: '停止部署' },
]

const createVisible = ref(false)
const copyVisible = ref(false)
const moveVisible = ref(false)
const categoryVisible = ref(false)
const form = ref({ key: '', name: '', categoryOid: '' })
const copyForm = ref({ oid: '', key: '', name: '' })
const moveForm = ref({ oid: '', categoryOid: '' })
const categoryForm = ref({ oid: '', name: '', sortOrder: 0, description: '' })

/** 删除版本弹窗：正在处理的模板、其版本行、勾选结果 */
const versionsVisible = ref(false)
const versionsLoading = ref(false)
const versionsFor = ref(null)
/** 打开弹窗时记下"当时的最新版"：标签高亮与"内容已回落"的提示都要用它 */
const currentLatestVersion = ref(null)
const versionRows = ref([])
const selectedVersions = ref([])

const versionColumns = [
  { title: '版本', key: 'version', width: 90 },
  { title: '变更说明', dataIndex: 'changeNote', key: 'changeNote' },
  { title: '部署', key: 'deployed', width: 90 },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
]

/** 可删 / 已锁定（已部署）的拆分：判定只有一处实现（见 template-repo 的 isVersionDeletable） */
const versionSplit = computed(() => splitDeletableVersions(versionRows.value))
const deletableVersions = computed(() => versionSplit.value.deletable)
const lockedVersions = computed(() => versionSplit.value.locked)

/** 行选择：已部署的勾不动（后端同样整批拒绝，这里只是别让用户白点） */
const versionRowSelection = computed(() => ({
  selectedRowKeys: selectedVersions.value,
  onChange: (keys) => {
    selectedVersions.value = keys
  },
  getCheckboxProps: (record) => ({ disabled: !isVersionDeletable(record) }),
}))

/**
 * 模板的分组显示名：由分组字典解析 oid。
 *
 * <p>模板表里不再存分组名（只存 oid）—— 显示名一律现查字典，于是"改分组名"
 * 不必触碰任何模板行，也不会出现"列表里还显示旧名"的残留。
 */
const groupNameOf = (record) => {
  const oid = (record.categoryOid ?? '').trim()
  if (!oid) {
    return ''
  }
  return groups.value.find((group) => group.oid === oid)?.name ?? ''
}

// ==================== 统计（全量口径）====================

const enabledCount = computed(() => allTemplates.value.filter((r) => r.enabled !== false).length)
const disabledCount = computed(() => allTemplates.value.filter((r) => r.enabled === false).length)
const deployedCount = computed(() => allTemplates.value.filter((r) => r.deployedVersion).length)
/** 「已部署过、但最新版本还没部署」—— 正是容易被忽略的那种状态 */
const pendingDeployCount = computed(
  () =>
    allTemplates.value.filter((r) => r.deployedVersion && r.deployedVersion !== r.latestVersion)
      .length,
)

/** 是否已有分组（没有分组时「新建流程」会先引导建分组） */
const hasGroups = computed(() => groups.value.length > 0)

/** 分组下拉项：只能选字典里的分组（**值是 oid**，名称只是给人看的标签） */
const categoryOptions = computed(() =>
  groups.value.map((group) => ({ value: group.oid, label: group.name })),
)

/** 当前选中的分组对象（「全部」「未分类」时为 null） */
const activeGroup = computed(
  () => groups.value.find((group) => group.oid === activeCategory.value) ?? null,
)

/**
 * 左栏分组清单 + 数量。
 *
 * <p>分组取自<b>字典</b>（不裁剪数量为 0 的分组），数量取自模板聚合（按 oid 计数）——
 * 「全部」永远第一；「未分类」只在确实存在时出现（分组必填是后加的规则，
 * 存量模板可能没有分组；它不是字典项，故不提供改名/删除）。
 */
const categories = computed(() => {
  const counts = countTemplatesByCategoryOid(allTemplates.value)
  const named = groups.value.map((group) => ({
    value: group.oid,
    label: group.name,
    count: counts.get(group.oid) ?? 0,
    group,
  }))
  const uncategorized = counts.get('') ?? 0
  return [
    { value: null, label: '全部', count: allTemplates.value.length, group: null },
    ...named,
    ...(uncategorized > 0
      ? [{ value: UNCATEGORIZED, label: '未分类', count: uncategorized, group: null }]
      : []),
  ]
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return allTemplates.value.filter((record) => {
    const oid = (record.categoryOid ?? '').trim()
    if (activeCategory.value === UNCATEGORIZED) {
      if (oid) {
        return false
      }
    } else if (activeCategory.value && oid !== activeCategory.value) {
      return false
    }
    if (enabledFilter.value !== null && enabledFilter.value !== undefined) {
      if (record.enabled !== enabledFilter.value) {
        return false
      }
    }
    if (kw) {
      const haystack = `${record.key ?? ''} ${record.name ?? ''} ${record.displayName ?? ''}`.toLowerCase()
      if (!haystack.includes(kw)) {
        return false
      }
    }
    return true
  })
})

const activeLabel = computed(
  () => categories.value.find((item) => item.value === activeCategory.value)?.label ?? '全部',
)

const emptyHint = computed(() => {
  if (keyword.value.trim()) {
    return `没有匹配「${keyword.value.trim()}」的流程`
  }
  if (!hasGroups.value) {
    return '先新建一个分组，再在组内设计流程'
  }
  return activeCategory.value === null ? '还没有流程模板' : `「${activeLabel.value}」下还没有流程`
})

function pickCategory(value) {
  activeCategory.value = value ?? null
  // 回传页面写进 URL：从设计器返回时仍停在原分组
  emit('update:category', activeCategory.value)
}

async function reload() {
  loading.value = true
  // 模板取全量（分组数量需要它，筛选在本地做）；分组字典与模板并行拉取
  const [templates, dict] = await Promise.all([
    templateRepository.list(),
    categoryRepository.list(),
  ])
  loading.value = false
  if (templates.ok) {
    allTemplates.value = templates.data ?? []
  }
  if (dict.ok) {
    groups.value = dict.data ?? []
    // 当前分组已被删掉时回落「全部」，避免停在一个不存在的分组上。
    // 兼容 URL 里存的是"分组名"的老链接：能按名字找到就顺手升级成 oid
    if (activeCategory.value && activeCategory.value !== UNCATEGORIZED) {
      if (!groups.value.some((group) => group.oid === activeCategory.value)) {
        const byName = groups.value.find((group) => group.name === activeCategory.value)
        pickCategory(byName ? byName.oid : null)
      }
    }
  }
  // 失败时不再弹提示：拦截器已按后端 message 提示过
}

onMounted(reload)

function openCreate() {
  // 没有分组时先引导建分组：流程必须落在某个分组下（后端同样会拒）
  if (!hasGroups.value) {
    message.info('先新建一个分组，再在组内新建流程')
    openCategoryCreate()
    return
  }
  // 在某个分组下点「新建」，默认就落在该分组 —— 省掉一次重复选择
  form.value = { key: '', name: '', categoryOid: activeGroup.value?.oid ?? '' }
  createVisible.value = true
}

async function doCreate() {
  if (!form.value.key.trim() || !form.value.name.trim()) {
    message.warning('流程 key 与名称必填')
    return
  }
  if (!form.value.categoryOid) {
    message.warning('请选择分组：流程必须挂在某个分组下')
    return
  }
  busy.value = true
  // 新建即产出一份「空白但合法」的 DSL（START → END 已连线），保证可立即保存。
  // 归属分组走 create 的入参（oid）；DSL 里的 meta.category 只留一个可读的名称快照
  const groupName = groups.value.find((group) => group.oid === form.value.categoryOid)?.name
  const dsl = createEmptyDsl({
    key: form.value.key.trim(),
    name: form.value.name.trim(),
    ...(groupName ? { category: groupName } : {}),
  })
  const result = await templateRepository.create(dsl, form.value.categoryOid)
  busy.value = false
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  createVisible.value = false
  message.success('已创建，正在打开设计器')
  emit('open', result.data.oid)
}

function openCopy(record) {
  copyForm.value = { oid: record.oid, key: `${record.key}_copy`, name: `${record.name} 副本` }
  copyVisible.value = true
}

async function doCopy() {
  if (!copyForm.value.key.trim()) {
    message.warning('新流程 key 必填')
    return
  }
  busy.value = true
  const result = await templateRepository.copy(
    copyForm.value.oid,
    copyForm.value.key.trim(),
    copyForm.value.name.trim() || undefined,
  )
  busy.value = false
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  copyVisible.value = false
  message.success('已复制')
  reload()
}

function openMove(record) {
  moveForm.value = { oid: record.oid, categoryOid: record.categoryOid ?? '' }
  moveVisible.value = true
}

async function doMove() {
  if (!moveForm.value.categoryOid) {
    message.warning('请选择目标分组')
    return
  }
  busy.value = true
  const result = await categoryRepository.moveTemplate(
    moveForm.value.oid,
    moveForm.value.categoryOid,
  )
  busy.value = false
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  moveVisible.value = false
  message.success('已移动分组')
  reload()
}

// ==================== 分组管理 ====================

function openCategoryCreate() {
  // 新分组排在最后：取现有最大序号 +10，留出插队空间
  const nextOrder = groups.value.reduce((max, group) => Math.max(max, group.sortOrder ?? 0), 0) + 10
  categoryForm.value = { oid: '', origin: '', name: '', sortOrder: nextOrder, description: '' }
  categoryVisible.value = true
}

function openCategoryRename(item) {
  const group = item.group
  categoryForm.value = {
    oid: group.oid,
    name: group.name,
    sortOrder: group.sortOrder ?? 0,
    description: group.description ?? '',
  }
  categoryVisible.value = true
}

async function doSaveCategory() {
  const draft = categoryForm.value
  busy.value = true
  const result = draft.oid
    ? await categoryRepository.rename(draft.oid, draft.name, draft.sortOrder, draft.description)
    : await categoryRepository.create(draft.name, draft.sortOrder, draft.description)
  busy.value = false
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  const renamed = Boolean(draft.oid)
  categoryVisible.value = false
  message.success(renamed ? '已重命名（模板按 oid 引用，无需同步）' : '分组已创建')
  reload()
}

async function removeCategory(item) {
  const result = await categoryRepository.remove(item.group.oid)
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  message.success('分组已删除')
  if (activeCategory.value === item.value) {
    pickCategory(null)
  }
  reload()
}

async function toggleEnabled(record) {
  const result = await templateRepository.setEnabled(record.oid, !record.enabled)
  if (result.ok) {
    message.success(record.enabled ? '已停止部署' : '已允许部署')
    reload()
  } else {
    message.warning(result.error)
  }
}

// ==================== 删除版本（流程删除的唯一方式）====================

/** 打开弹窗：现取版本列表（含每版是否已部署），默认一版都不勾 */
async function openDeleteVersions(record) {
  versionsFor.value = record
  currentLatestVersion.value = record.latestVersion ?? null
  versionRows.value = []
  selectedVersions.value = []
  versionsVisible.value = true
  versionsLoading.value = true
  const result = await templateRepository.versions(record.oid)
  versionsLoading.value = false
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  // 新版本在前，与版本历史抽屉一致
  versionRows.value = [...(result.data ?? [])].sort((a, b) => (b.version ?? 0) - (a.version ?? 0))
}

async function doDeleteVersions() {
  const versions = [...selectedVersions.value]
  busy.value = true
  const result = await templateRepository.removeVersions(versionsFor.value.oid, versions)
  busy.value = false
  if (!result.ok) {
    message.warning(result.error)
    return
  }
  versionsVisible.value = false
  const label = versions.map((v) => `v${v}`).join('、')
  if (result.data?.templateRemoved) {
    message.success(`已删除 ${label}；该流程已无版本，已一并删除`)
  } else if (versions.includes(currentLatestVersion.value)) {
    // 删掉的正是"最新版" → 流程内容回落到剩下的最新版，必须让用户知道内容变了
    message.success(`已删除 ${label}；流程内容已回落到 v${result.data?.latestVersion}`)
  } else {
    message.success(`已删除 ${label}`)
  }
  reload()
}

defineExpose({ reload })
</script>

<style scoped>
.template-library {
  display: flex;
  flex-direction: column;
}

/* 页头（与「编号规则」页同规格） */
.lib-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
}
.lib-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.lib-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}
.lib-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

/* 统计栏（与「编号规则」页同规格） */
.lib-stats-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
  padding: 8px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  margin-bottom: 10px;
}
.lib-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}
.lib-stat-icon {
  font-size: 14px;
  color: #1677ff;
}
.lib-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}
.lib-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

/* 主从内容区 */
.lib-body {
  display: flex;
  min-height: 420px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

/* 左：分组 */
.library-categories {
  width: 190px;
  flex-shrink: 0;
  padding: 10px 0;
  border-right: 1px solid #f0f0f0;
  overflow-y: auto;
}
.library-categories__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px 8px;
}
.library-categories__title {
  font-size: 12px;
  color: #8c8c8c;
}
.category-add {
  font-size: 12px;
}
.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 14px;
  font-size: 13px;
  color: #333;
  cursor: pointer;
}
.category-item:hover {
  background: #fafafa;
}
.category-item.is-active {
  background: #e6f4ff;
  color: #1677ff;
  font-weight: 500;
}
.category-item__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.category-item__count {
  flex-shrink: 0;
  font-size: 12px;
  color: #8c8c8c;
}
.category-item.is-active .category-item__count {
  color: #1677ff;
}
/* 分组操作默认不占位：常态只显示"名称 + 数量"，悬停才让出空间给「改名 / 删除」 */
.category-item__ops {
  display: none;
  flex-shrink: 0;
  gap: 8px;
  font-size: 12px;
  font-weight: 400;
}
.category-item:hover .category-item__ops {
  display: inline-flex;
}
.category-empty {
  padding: 4px 14px;
  line-height: 1.6;
}

/* 右：列表 */
.library-main {
  flex: 1;
  min-width: 0;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.library-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.library-toolbar__right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.library-heading {
  font-size: 13px;
  font-weight: 500;
}
.link {
  color: #1677ff;
}
.danger {
  color: #ff4d4f;
}
/* 删除版本弹窗里的说明文字（表格上下各一处） */
.versions-tip {
  margin: 6px 0;
  line-height: 1.6;
}
.muted {
  font-size: 12px;
  color: #8c8c8c;
  font-weight: 400;
}
</style>
