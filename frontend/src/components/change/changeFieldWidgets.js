/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 *
 * 变更单据专属的表单控件 —— 供 RenderFields 按 uiComponent 渲染。
 *
 * <h3>为什么不复用 user-selector</h3>
 * 平台内置的 user-selector 取值是 user.oid，而变更单据的 owner / assignee 存的是
 * **username**（变更服务的既有口径：UserContext.get() 就是 username，审批人回写、
 * 时间线记录全按 username 比对）。改成 oid 会连带改状态机与审计字段，故单独做一套。
 *
 * <h3>为什么关联问题要单独做</h3>
 * 问题单可能上千条，平台没有"全量问题树"控件；这里按关键字服务端搜索（最多 20 条），
 * 并且**保留已选项的 label** —— 否则搜索一次之后，已选值就退化成裸 oid。
 */
import { computed, defineComponent, h, ref, onMounted, watch } from 'vue'
import { Select } from 'ant-design-vue'
import { getAllUsers, listChangeIssueOptions, listChangeObjectCandidates, listChangeRequests } from '@/api'

/** 三个控件共用的基础 props（RenderFields 统一传 value / disabled / placeholder） */
const baseProps = {
  value: { type: [String, Number, Array], default: undefined },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '' }
}

/**
 * 责任人 / 处理人选择（取值 = username）。
 *
 * <p>选项文案与变更面板原有口径一致：`显示名（账号）`，便于同名情况下确认是谁。
 */
export const ChangeUserSelect = defineComponent({
  name: 'ChangeUserSelect',
  props: baseProps,
  emits: ['update:value'],
  setup(props, { emit }) {
    const options = ref([])
    onMounted(async () => {
      try {
        const res = await getAllUsers()
        if (res.code === 200) {
          options.value = (res.data || []).map((u) => ({
            value: u.username,
            label: u.displayName ? `${u.displayName}（${u.username}）` : u.username
          }))
        }
      } catch {
        /* 拉不到用户就留空下拉，不阻塞其它字段 */
      }
    })
    return () => h(Select, {
      value: props.value,
      disabled: props.disabled,
      placeholder: props.placeholder || '默认为当前用户',
      options: options.value,
      showSearch: true,
      optionFilterProp: 'label',
      allowClear: true,
      style: { width: '100%' },
      'onUpdate:value': (val) => emit('update:value', val)
    })
  }
})

/**
 * 关联问题（多选，服务端搜索）。
 *
 * <p>`seedOptions` 由上层在编辑回填时传入（详情接口带回的 {oid, code, name}），
 * 解决"只有 oid、没有 label"的问题。
 */
export const ChangeIssueSelect = defineComponent({
  name: 'ChangeIssueSelect',
  props: { ...baseProps, seedOptions: { type: Array, default: () => [] } },
  emits: ['update:value'],
  setup(props, { emit }) {
    const options = ref([])

    const toOption = (issue) => ({ value: issue.oid, label: `${issue.code} ${issue.name}` })

    function merge(extra) {
      // 已选项优先：seed（编辑回填）→ 现有候选 → 本次搜索结果，按 oid 去重
      const merged = []
      const seen = new Set()
      for (const o of [...props.seedOptions.map(toOption), ...options.value, ...extra]) {
        if (!seen.has(o.value)) {
          seen.add(o.value)
          merged.push(o)
        }
      }
      options.value = merged
    }

    async function search(keyword) {
      try {
        const res = await listChangeIssueOptions({ keyword: keyword || undefined, limit: 20 })
        if (res.code !== 200) return
        merge((res.data || []).map(toOption))
      } catch {
        /* 搜索失败不影响已选内容 */
      }
    }

    onMounted(() => search(''))

    // 编辑回填的 label 晚于挂载到货（详情接口是异步的）：到货后并入候选，
    // 否则已选项在用户展开下拉前一直显示成裸 oid。
    watch(() => props.seedOptions, () => merge([]), { deep: true })

    return () => h(Select, {
      value: props.value,
      disabled: props.disabled,
      mode: 'multiple',
      placeholder: props.placeholder || '可选：输入编号或标题搜索',
      options: options.value,
      filterOption: false,
      showSearch: true,
      allowClear: true,
      style: { width: '100%' },
      onSearch: search,
      onFocus: () => search(''),
      'onUpdate:value': (val) => emit('update:value', val)
    })
  }
})

/**
 * 预估受影响对象（多选，服务端搜索）。
 *
 * <p>取值是对象 oid 数组：类型 / 编码 / 名称 / 所属容器一律由服务端按 oid 回查，
 * 前端只负责选择，不传快照（避免旧快照或伪造值写进库里）。
 *
 * <p>`seedOptions` 由上层在编辑回填时传（详情接口带回的 affectedObjects）。
 *
 * <p>`containerOid` 是这张单选的「变更系列/型号」：给了就只在它范围内检索
 * （服务端按系列子树 / 型号展开 container_oid）。换范围时把上一个范围的候选清掉，
 * 只保留已选项的 label —— 否则下拉里还挂着别的产品的零件，等于没做限定。
 *
 * <p>两类候选要置灰（2026-11-03 用户口径：*"处在变更中对象不能选，本通告已经选择过的对象不能选"*）：
 * <ul>
 *   <li><b>已在本变更里</b>（{@code excludeOids}，由上层拼：本单受影响对象 + 其余变更任务的范围）——
 *       同一张单换个任务又能把同一个零件挑一遍，服务端是幂等的不会报错，结果是"归哪张任务"最后一句话说了算；</li>
 *   <li><b>已被别的在途变更通告收录</b>（候选自带 {@code changeCode}）—— 一个对象同时只能被一份
 *       执行清单持有，与 {@code ck_change_affected_object.locked} 同一口径。</li>
 * </ul>
 * 但<b>当前已选的那条一律不置灰</b>（与 {@code ChangeRequestSelect} 同理）：编辑既有任务时，
 * 它自己的对象本来就在"本变更已挑过"里，一律禁掉会让用户连自己选过的都点不回来。
 */
export const ChangeAffectedObjectSelect = defineComponent({
  name: 'ChangeAffectedObjectSelect',
  props: {
    ...baseProps,
    seedOptions: { type: Array, default: () => [] },
    containerOid: { type: String, default: '' },
    /** 本变更单已经挑过的对象 oid（含其它任务的范围）：置灰并注明 */
    excludeOids: { type: Array, default: () => [] }
  },
  // options-change：把 oid → 展示文案抛给上层。变更通告表单要用它把"已选变更对象"
  // 做成变更任务的范围下拉 —— 对象 oid 只有这个控件认得，上层拿不到 label 就只能显示裸 uuid
  emits: ['update:value', 'options-change'],
  setup(props, { emit }) {
    /** 原始候选（value / label / changeCode）：置灰与加注在 options 里算，免得搜索反复改写原始文案 */
    const raw = ref([])

    const TYPE_LABEL = { PART: '零件', DOCUMENT: '文档', ENG_DOCUMENT: '工程文档' }

    const toOption = (obj) => ({
      value: obj.entityOid || obj.oid,
      label: `${TYPE_LABEL[obj.entityType] || obj.entityType || '对象'} · ${obj.entityCode || obj.code || ''} ${
        obj.entityName || obj.name || ''
      }`.trim(),
      changeCode: obj.changeCode || ''
    })

    const selectedKeys = computed(() => new Set((Array.isArray(props.value) ? props.value : []).map(String)))
    const excludeKeys = computed(() => new Set((props.excludeOids || []).map(String)))

    const options = computed(() => raw.value.map((o) => {
      const picked = selectedKeys.value.has(String(o.value))
      if (!picked && excludeKeys.value.has(String(o.value))) {
        return { ...o, label: `${o.label}（已在本变更中）`, disabled: true }
      }
      if (!picked && o.changeCode) {
        return { ...o, label: `${o.label}（已在 ${o.changeCode} 中）`, disabled: true }
      }
      return { ...o, disabled: false }
    }))

    function merge(extra) {
      const merged = []
      const seen = new Set()
      for (const o of [...props.seedOptions.map(toOption), ...raw.value, ...extra]) {
        if (o && o.value && !seen.has(o.value)) {
          seen.add(o.value)
          merged.push(o)
        }
      }
      raw.value = merged
    }

    // 抛给上层的必须是**没加注**的那份：它会被当成变更任务范围下拉的文案，
    // 带着「（已在本变更中）」会出现在与置灰无关的地方
    watch(raw, (list) => {
      emit('options-change', list.map(({ value, label }) => ({ value, label })))
    }, { deep: true, immediate: true })

    async function search(keyword) {
      try {
        const res = await listChangeObjectCandidates(keyword || undefined, 20, props.containerOid || undefined)
        if (res.code !== 200) return
        merge((res.data || []).map(toOption))
      } catch {
        /* 搜索失败不影响已选内容 */
      }
    }

    /** 只留下已选项：换产品范围后，旧候选不该继续出现在下拉里 */
    function keepSelectedOnly() {
      raw.value = raw.value.filter((o) => selectedKeys.value.has(String(o.value)))
    }

    onMounted(() => search(''))
    // 编辑回填的 label 晚于挂载到货：到货后并入候选，否则已选项显示成裸 oid
    watch(() => props.seedOptions, () => merge([]), { deep: true })
    // 换「变更系列/型号」= 换检索范围：清掉旧范围候选后按新范围重查
    watch(() => props.containerOid, () => {
      keepSelectedOnly()
      search('')
    })

    return () => h(Select, {
      value: props.value,
      disabled: props.disabled,
      mode: 'multiple',
      placeholder: props.placeholder || '可选：输入编号或名称搜索零件 / 文档',
      options: options.value,
      filterOption: false,
      showSearch: true,
      allowClear: true,
      style: { width: '100%' },
      onSearch: search,
      onFocus: () => search(''),
      'onUpdate:value': (val) => emit('update:value', val)
    })
  }
})

/**
 * 来源变更请求（单选，最近 200 条）。变更通告允许独立存在，故可清空。
 *
 * <p>已被变更通告绑定过的请求（列表接口带回 {@code noticeCode}）置灰不可选：
 * 同一张请求再派发一张通告，等于两份执行清单对着同一批受影响对象。
 * 当前已选的那条例外 —— 编辑既有通告时，它的来源请求本来就是"已绑定"状态，
 * 一律禁掉会让用户连自己的来源都改不了。
 */
export const ChangeRequestSelect = defineComponent({
  name: 'ChangeRequestSelect',
  props: baseProps,
  emits: ['update:value'],
  setup(props, { emit }) {
    const options = ref([])
    onMounted(async () => {
      try {
        const res = await listChangeRequests({ page: 1, pageSize: 200 })
        if (res.code === 200) {
          options.value = (res.data?.rows || []).map((r) => {
            const bound = r.noticeCode && r.oid !== props.value
            return {
              value: r.oid,
              label: bound ? `${r.code} ${r.name}（已绑定 ${r.noticeCode}）` : `${r.code} ${r.name}`,
              disabled: !!bound
            }
          })
        }
      } catch {
        /* 忽略 */
      }
    })
    return () => h(Select, {
      value: props.value,
      disabled: props.disabled,
      placeholder: props.placeholder || '可选 —— 变更通告允许独立存在',
      options: options.value,
      showSearch: true,
      optionFilterProp: 'label',
      allowClear: true,
      style: { width: '100%' },
      'onUpdate:value': (val) => emit('update:value', val)
    })
  }
})
