<template>
  <a-modal
    :visible="visible"
    title="设置生命周期状态"
    :confirm-loading="saving"
    :ok-button-props="{ disabled: !canSubmit }"
    ok-text="确定"
    cancel-text="取消"
    width="540px"
    @ok="confirm"
    @cancel="close"
  >
    <a-spin :spinning="loading">
      <!-- 类型没绑生命周期模板：说清原因，不给一个空下拉让人猜 -->
      <a-alert
        v-if="options?.reason"
        type="warning"
        show-icon
        :message="options.reason"
      />
      <template v-else>
        <!-- 对象正在流程中：候选照给，但这次不许设（流程里的「设置状态」节点会在该到的时候改它） -->
        <a-alert
          v-if="blocked"
          type="warning"
          show-icon
          class="slm-blocked"
          :message="options.blockedReason"
        />

        <!-- 对象仍绑着旧模板 / 旧子版本 / 未绑定：设置状态会在同一事务里先切到业务配置的模板。
             不再提供"保留旧模板"——那会把状态写进旧模板，与本次给候选的新模板对不上 -->
        <a-alert
          v-if="options.templateChanged || options.templateBindRequired"
          type="warning"
          show-icon
          class="slm-mismatch"
          message="本次会先切换到业务配置的生命周期模板"
        >
          <template #description>
            <template v-if="options.templateBindRequired">
              对象尚未绑定生命周期模板，将绑定为「{{ options.configuredTemplateName || options.configuredTemplateCode }}」。
            </template>
            <template v-else>
              对象仍绑定「{{ options.boundTemplateName || options.boundTemplateCode }}」，
              业务配置已改为「{{ options.configuredTemplateName || options.configuredTemplateCode }}」。
            </template>
            <span v-if="migrationNote" class="slm-switch-note">{{ migrationNote }}</span>
          </template>
        </a-alert>

        <div class="slm-head">
          <span class="slm-object">{{ record?.name || record?.code || record?.number || '该对象' }}</span>
          <span class="slm-meta">
            当前状态：<b>{{ currentName }}</b>
            <span class="slm-sep">·</span>
            生命周期模板：{{ options?.templateName || options?.templateCode || '—' }}
          </span>
        </div>

        <a-radio-group v-model:value="mode" class="slm-radio" :disabled="blocked">
          <a-radio value="SPECIFIED" :disabled="specifiedDisabled">设置对象到指定状态</a-radio>
          <a-radio value="INITIAL" :disabled="initialDisabled">
            退回初始状态（{{ options?.initial?.name || options?.initial?.code || '—' }}）
          </a-radio>
        </a-radio-group>

        <div v-if="noTargetsNote" class="slm-note">{{ noTargetsNote }}</div>

        <div v-if="mode === 'SPECIFIED' && !noTargets" class="slm-states">
          <a-select
            v-model:value="targetStateCode"
            style="width:100%"
            placeholder="选择目标状态"
            :options="stateOptions"
            :disabled="blocked"
          />
        </div>

        <div class="slm-hint">{{ hint }}</div>
      </template>
    </a-spin>
  </a-modal>
</template>

<script setup>
/**
 * 「设置生命周期状态」弹窗 —— 行操作下拉里的那一项。
 *
 * <p><b>两个选项，依据都是该类型绑定的生命周期模板</b>：
 * <ol>
 *   <li><b>设置对象到初始状态</b>：模板定义的初始状态（如「草稿」）。执行时<b>沿模板的回退规则
 *       逐跳退</b>（已发布 → 工作中 → 草稿），不是一步跳过去 —— 多跳在模板里本来就没有直接规则，
 *       允许跳跃等于把回退链当摆设；某一跳没有回退规则会明确失败并说清卡在哪。</li>
 *   <li><b>设置对象到指定状态</b>：从模板状态清单里挑一个，受迁移规则约束（当前状态能一步到它）。
 *       候选由后端按同一份模板给出，并标出"不到"的原因 —— 迁不过去的选项直接禁用，
 *       不让用户点了才知道。</li>
 * </ol>
 *
 * <p>候选、可达性都来自后端（{@code GET /{oid}/lifecycle-states}），与执行侧同一份模板，
 * 因此不会出现"界面让选、后端拒收"。
 *
 * <p><b>业务配置换过模板后，历史对象也要用新模板</b>：候选一律按业务配置的模板给；
 * 对象仍绑着旧模板 / 旧子版本 / 未绑定时，顶部明确提示"本次会先切换模板"，
 * 切换与设置状态在后端<b>同一个事务</b>里完成（状态按 code 保留，新模板没有同 code
 * 就落到新模板初始状态）。不再提供"保留旧模板"——那会把状态写进旧模板，
 * 与本次的候选、后续流程判定都对不上。
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getLifecycleStateOptions, setLifecycleState } from '@/api'

const props = defineProps({
  visible: { type: Boolean, default: false },
  /**
   * 业务对象（列表行）：{ oid, name, code/number, typeDefinitionCode, statusCode? }
   * 大版本不用传：服务端按"该大版本当前的最新小版本"解析。
   */
  record: { type: Object, default: null },
})
const emit = defineEmits(['update:visible', 'success'])

const loading = ref(false)
const saving = ref(false)
/** 候选项（一律按业务配置的模板） */
const options = ref(null)
/** 默认落在"指定状态"：进来就能看到可选状态，而不是先看到一次"退回初始" */
const mode = ref('SPECIFIED')
const targetStateCode = ref(undefined)
/** 请求序号：只让最后一次响应落到界面 */
let loadSeq = 0

const typeCode = computed(() =>
  props.record?.typeDefinitionCode || props.record?.typeCode || '')

const currentName = computed(() =>
  options.value?.current?.name || options.value?.current?.code || '（无状态）')

/**
 * 该对象正在流程中（同一对象 + 大版本已有流程实例在跑）—— 这次不允许手工改状态。
 *
 * <p>后端判的：流程里的「设置状态」服务节点会在该到的时候改它，人手工插一脚会让
 * "流程走到哪一步"与"对象是什么状态"对不上。这里只负责呈现与禁用；
 * 提交侧后端还会再挡一次（409），界面禁用不是唯一防线。
 */
const blocked = computed(() => !!options.value?.blockedReason)

/**
 * 提交时需要先切换模板：对象仍绑着旧模板 / 旧子版本，或根本没有绑定。
 *
 * <p>后端执行时还会再判一次（不能只靠界面传参），这里只决定提示文案。
 */
const migrating = computed(() =>
  !!options.value?.templateChanged || !!options.value?.templateBindRequired)

/** 切换模板会把当前状态带到哪：新模板里有同 code 就保留，没有就落到新模板初始状态 */
const migrationNote = computed(() => {
  const o = options.value
  if (!migrating.value || o.migrationKeepsStatus == null) return ''
  const current = o.current?.name || o.current?.code
  if (!current) return `绑定后状态为「${o.migrationStatusName || o.migrationStatusCode}」`
  if (o.migrationKeepsStatus) return `当前状态「${current}」在新模板中保留不变`
  return `当前状态「${current}」在新模板中不存在，将重置为「${o.migrationStatusName || o.migrationStatusCode}」`
})

/** 已在初始状态就没有"退回"可言 */
const initialDisabled = computed(() =>
  !!options.value?.initial?.code
  && options.value?.initial?.code === options.value?.current?.code)

/** 除当前状态外、可一步迁到的状态（下拉里真正有意义的选择） */
const reachableStates = computed(() =>
  (options.value?.states || []).filter(
    (s) => s.reachable && s.code !== options.value?.current?.code))

/**
 * 模板里没有其他可一步迁到的状态。
 *
 * <p>当前状态连一条升版/回退规则都没有时，两种模式都走不通：指定状态没有候选，
 * 退回初始也要沿回退规则逐跳走。所以这里不是"换个模式就好"，而是确实无路可走。
 */
const noTargets = computed(() =>
  !!options.value
  && !options.value.reason
  && !!options.value.states?.length
  && reachableStates.value.length === 0)

const specifiedDisabled = computed(() => noTargets.value)

/** 无路可走时把原因说清楚，而不是留一个灰掉的确定按钮让人猜 */
const noTargetsNote = computed(() => {
  if (!noTargets.value) return ''
  return initialDisabled.value
    ? `当前状态「${currentName.value}」就是模板的初始状态，没有可迁往的其他状态，无需设置。`
    : `当前状态「${currentName.value}」在模板里没有定义升版/回退规则，无法迁移到其他状态。`
})

/** 状态下拉：不可达的禁用，并把原因写进选项末尾（鼠标悬停也能看全） */
const stateOptions = computed(() =>
  (options.value?.states || []).map((s) => ({
    value: s.code,
    label: s.reachable ? s.name : `${s.name}（不可设置）`,
    title: s.reachable ? undefined : s.reason,
    disabled: !s.reachable,
  })))

const canSubmit = computed(() => {
  if (!props.visible || loading.value || options.value?.reason || blocked.value) return false
  // 没有可迁移目标时两种模式都执行不了，别让用户点了才知道
  if (noTargets.value) return false
  if (mode.value === 'INITIAL') return !initialDisabled.value
  return !!targetStateCode.value
})

const hint = computed(() => {
  // 被挡住时上面那条警示已经把原因说完了，再说一遍是噪声
  if (blocked.value) return ''
  // 无路可走时由 noTargetsNote 说明，底部再重复一遍也是噪声
  if (noTargets.value) return ''
  const target = options.value?.configuredTemplateName || options.value?.configuredTemplateCode
  const prefix = migrating.value
    ? `确定后会先把对象切换到「${target}」，再按该模板设置状态。`
    : ''
  if (mode.value === 'INITIAL') {
    return prefix + (initialDisabled.value
      ? '对象已经在初始状态，无需设置。'
      : `将沿模板的回退规则逐跳退回「${options.value?.initial?.name || ''}」；`
        + '若中途某个状态没有定义回退规则，会明确提示卡在哪一步。')
  }
  const picked = (options.value?.states || []).find((s) => s.code === targetStateCode.value)
  if (picked?.reason) return prefix + picked.reason
  // 无当前状态时后端只放行初始状态，别再用"能一步迁到"的泛化文案误导
  if (!options.value?.current?.code) {
    return prefix + `对象还没有生命周期状态，只能设到模板的初始状态「${options.value?.initial?.name || ''}」。`
  }
  return prefix + '只能选当前状态能一步迁到的状态（灰掉的项鼠标悬停可见原因）。'
})

watch(() => props.visible, (open) => {
  if (!open) return
  mode.value = 'SPECIFIED'
  targetStateCode.value = undefined
  options.value = null
  load()
})

async function load() {
  if (!props.record?.oid || !typeCode.value) {
    options.value = { reason: '缺少对象或类型信息，无法确定生命周期模板' }
    return
  }
  const seq = ++loadSeq
  loading.value = true
  try {
    const res = await getLifecycleStateOptions(props.record.oid, typeCode.value)
    if (seq !== loadSeq) return
    options.value = res?.code === 200
      ? (res.data || {})
      : { reason: res?.message || '读取生命周期状态失败' }
    // 预选第一个可迁移状态（跳过当前状态：重复设置是幂等空操作，预选它没有意义）
    if (options.value?.states?.length) {
      const current = options.value.current?.code
      const first = options.value.states.find((s) => s.reachable && s.code !== current)
      targetStateCode.value = first?.code
    }
  } catch {
    if (seq === loadSeq) {
      options.value = { reason: '读取生命周期状态失败，请稍后重试' }
    }
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

async function confirm() {
  if (!canSubmit.value) return
  saving.value = true
  try {
    const res = await setLifecycleState(props.record.oid, {
      typeDefinitionCode: typeCode.value,
      mode: mode.value,
      targetStateCode: mode.value === 'SPECIFIED' ? targetStateCode.value : undefined,
      // 迁移与设状态在后端同一个事务：要么模板和状态一起变，要么都不变
      migrateTemplate: migrating.value,
    })
    if (res?.code === 200) {
      const target = mode.value === 'INITIAL'
        ? (options.value?.initial?.name || options.value?.initial?.code)
        : (options.value?.states || []).find((s) => s.code === targetStateCode.value)?.name
      const templateName = options.value?.configuredTemplateName || options.value?.configuredTemplateCode
      const prefix = migrating.value ? `已切换到「${templateName}」并` : ''
      message.success(`${prefix}将「${props.record?.name || props.record?.code || '对象'}」设置为「${target}」`)
      close()
      emit('success')
    }
    // 失败原因（未绑模板 / 不允许的迁移 / 宿主不支持）由响应拦截器统一提示
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:visible', false)
}
</script>

<style scoped>
.slm-blocked {
  margin-bottom: 12px;
}

.slm-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 12px;
}

.slm-object {
  font-weight: 500;
}

.slm-meta {
  font-size: 12px;
  color: #8c8c8c;
}

.slm-sep {
  margin: 0 6px;
}

.slm-mismatch {
  margin-bottom: 10px;
}

/* 切换模板后状态会怎么变：跟在说明后面单独一行，避免和"旧→新"混成一段 */
.slm-switch-note {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.6;
}

.slm-radio {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slm-states {
  margin-top: 8px;
}

/* 无可迁移状态时的说明：与下拉同位置，避免单选下面突然空一块 */
.slm-note {
  margin-top: 8px;
  font-size: 12px;
  color: #8c8c8c;
}

.slm-hint {
  margin-top: 10px;
  padding: 6px 8px;
  border-radius: 4px;
  background: #fafafa;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.7;
}
</style>
