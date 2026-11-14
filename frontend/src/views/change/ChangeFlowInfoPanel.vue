<template>
  <!--
    「提交审批 / 发起评审」弹窗的公共头部：单据结论条 + 阻断提示 + 流程信息网格。

    <p>为什么抽出来：ECN 的「提交审批」与 ECR 的「发起评审」拉起的是同一种东西（一条已部署的
    流程），用户要判断的也是同一件事 —— 走哪条流程、部署没部署、本单当前什么状态。
    两处各写一套的结果是：一边能看到版本与部署状态，另一边只有一句"将发起评审流程"，
    流程没部署还得点完确定才吃报错。

    <p>组件只管展示，查流程与判定能不能提交留在各自面板（配置项不同：ECN 读审批流程 key，
    ECR 读评审流程 key）。
  -->
  <div class="ck-summary">
    <div class="ck-summary__object">
      <span class="ck-summary__name">{{ record?.code || '' }}</span>
      <span class="ck-summary__code">{{ record?.name || '' }}</span>
      <a-tag v-if="statusLabel" size="small" :color="statusColor || 'default'">{{ statusLabel }}</a-tag>
      <a-tag v-for="tag in tags" :key="tag.text" size="small" :color="tag.color || 'default'">
        {{ tag.text }}
      </a-tag>
    </div>
    <!-- 部署状态与最新版本靠右：这是"能不能提交"的两个直接判据，扫一眼就要看到 -->
    <div v-if="!flow.loading && flow.found" class="cf-state">
      <a-tag v-if="flow.deployedVersion" color="green" size="small">
        已部署 v{{ flow.deployedVersion }}
      </a-tag>
      <a-tag v-else color="orange" size="small">尚未部署</a-tag>
      <a-tag color="blue" size="small">v{{ flow.latestVersion ?? '-' }}</a-tag>
    </div>
  </div>

  <a-alert
    v-if="blocked"
    type="warning"
    show-icon
    class="cf-hint"
    :message="blockReason"
    :description="blockHint"
  />
  <a-alert v-else-if="infoMessage" type="info" show-icon class="cf-hint" :message="infoMessage" />

  <!-- 流程信息：与「发起流程」弹框同一套字段，便于把两处当成同一件事来读 -->
  <div v-if="flow.loaded && !flow.loading && flow.key" class="ck-section">
    <div class="ck-section__title">流程信息</div>
    <div class="cf-info">
      <div class="cf-info__item">
        <span class="cf-info__label">流程名称</span>
        <span class="cf-info__value">{{ flow.name || '（未在流程清单里找到）' }}</span>
      </div>
      <div class="cf-info__item">
        <span class="cf-info__label">流程 key</span>
        <code class="cf-info__value cf-info__code">{{ flow.key }}</code>
      </div>
      <div class="cf-info__item">
        <span class="cf-info__label">最新版本</span>
        <span class="cf-info__value cf-info__value--tags">
          <a-tag color="blue" size="small">v{{ flow.latestVersion ?? '-' }}</a-tag>
          <span v-if="flow.latestCreatedAt" class="cf-info__muted">{{ flow.latestCreatedAt }}</span>
        </span>
      </div>
      <div class="cf-info__item">
        <span class="cf-info__label">部署状态</span>
        <span class="cf-info__value cf-info__value--tags">
          <a-tag v-if="flow.deployedVersion" color="green" size="small">
            已部署 v{{ flow.deployedVersion }}
          </a-tag>
          <a-tag v-else color="orange" size="small">尚未部署</a-tag>
        </span>
      </div>
      <div v-if="flow.changeNote" class="cf-info__item cf-info__item--full">
        <span class="cf-info__label">变更说明</span>
        <div class="cf-info__value cf-info__value--text">{{ flow.changeNote }}</div>
      </div>
      <div v-if="flow.description" class="cf-info__item cf-info__item--full">
        <span class="cf-info__label">流程说明</span>
        <div class="cf-info__value cf-info__value--text">{{ flow.description }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 变更域「提交审批 / 发起评审」弹窗的流程信息块（展示组件，无请求）。
 *
 * <p>`flow` 约定的字段（各面板自己查、自己清）：
 * `{ loading, loaded, found, key, name, description, latestVersion, latestCreatedAt,
 *    deployedVersion, changeNote }`。
 */
defineProps({
  /** 本次流转的单据（弹窗里要显示"提交哪一张"：编号 / 标题 / 当前状态） */
  record: { type: Object, default: null },
  /** 状态标签：文案与配色按单据域给（ECN 与 ECR 的状态字典不同） */
  statusLabel: { type: String, default: '' },
  statusColor: { type: String, default: 'default' },
  /** 额外标签（如 ECR 的变更级别）；空则整组不渲染 */
  tags: { type: Array, default: () => [] },
  /** 审批流程信息（见上方字段约定） */
  flow: { type: Object, required: true },
  /** true = 现在提交必然被退回（显示 warning 并应由父级禁用确定按钮） */
  blocked: { type: Boolean, default: false },
  blockReason: { type: String, default: '' },
  blockHint: { type: String, default: '' },
  /** 未阻断时的一句说明（走哪条流程、去哪儿办） */
  infoMessage: { type: String, default: '' },
})
</script>

<style scoped>
/* 结论条与下方提示之间留一口气：原 ECN 弹窗就是 12px，抽组件后不能丢 */
.ck-summary {
  margin-bottom: 12px;
}
.cf-hint {
  margin-bottom: 12px;
}
.cf-state {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  white-space: nowrap;
}
/* 两列网格：标签窄、值占满剩余宽度；长文本（变更/流程说明）整行铺开 */
.cf-info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  background: #f0f0f0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
}
.cf-info__item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  padding: 6px 10px;
  background: #fff;
}
.cf-info__item--full {
  grid-column: 1 / -1;
}
.cf-info__label {
  flex: 0 0 auto;
  font-size: 12px;
  line-height: 20px;
  color: #8c8c8c;
  white-space: nowrap;
}
.cf-info__value {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 13px;
  line-height: 20px;
  color: rgba(0, 0, 0, 0.88);
  word-break: break-word;
}
.cf-info__value--tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.cf-info__value--text {
  line-height: 1.65;
  white-space: pre-wrap;
}
.cf-info__code {
  font-family: monospace;
  font-size: 12px;
  color: #595959;
}
.cf-info__muted {
  font-size: 12px;
  color: #8c8c8c;
}
</style>
