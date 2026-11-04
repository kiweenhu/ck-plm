<template>
  <!--
    供货渠道（AVL）hover 面板 —— MPN 表「供货渠道」列的「N 家」。

    <p>为什么用 popover 而不是 tooltip：内容是多家供应商 × 多行结构化信息（料号 / 价格 /
    交期 / MOQ·SPQ / 首选），tooltip 排不开。

    <p>为什么抽成组件：「元器件认证申请」与「申请信息摘要卡」（审批 / 会签表用它）都要展示
    同一份渠道明细 —— 各写一遍必然漂移（2026-10-13 用户反馈：审批侧 hover 没有渠道内容，
    而申请侧有）。明细只有一处渲染，改口径不会漏。
  -->
  <a-popover placement="left" :title="`供货渠道（AVL）—— ${mpn || '—'}`">
    <template #content>
      <div v-if="avls.length" class="avl">
        <div v-for="avl in avls" :key="avl.oid" class="avl__row">
          <div class="avl__sup">
            <b>{{ supplierNameOf(avl.supplierOid) }}</b>
            <a-tag v-if="avl.preferred" color="gold" class="avl__tag">首选</a-tag>
          </div>
          <div class="avl__meta">供应商料号：{{ avl.supplierPartNo || '—' }}</div>
          <div class="avl__meta">
            单价：{{ avl.price == null ? '—' : avl.price + ' ' + (avl.currency || '') }}
            　交期：{{ avl.leadTimeDays == null ? '—' : avl.leadTimeDays + ' 天' }}
          </div>
          <div class="avl__meta">
            MOQ：{{ avl.moq == null ? '—' : avl.moq }}　SPQ：{{ avl.spq == null ? '—' : avl.spq }}
          </div>
        </div>
      </div>
      <div v-else class="avl__empty">尚无供货渠道（AVL）记录</div>
    </template>
    <a-tag :color="avls.length ? 'blue' : 'warning'" class="avl__trigger">{{ avls.length }} 家</a-tag>
  </a-popover>
</template>

<script setup>
/**
 * 供货渠道（AVL）明细 —— 鼠标移到「N 家」上展示。
 */
const props = defineProps({
  /** 该 MPN 的供货渠道列表（listPartAmlTree 返回节点里的 avls） */
  avls: { type: Array, default: () => [] },
  /** 制造商料号（面板标题用） */
  mpn: { type: String, default: '' },
  /** 供应商 oid → 名称（库里存的是 oid，界面要显示人名可读的名称） */
  supplierNames: { type: Object, default: () => ({}) },
})

function supplierNameOf(oid) {
  return props.supplierNames[oid] || oid || '—'
}
</script>

<style scoped>
/* 多家供应商分行，行间虚线分隔 */
.avl {
  max-width: 340px;
}

.avl__row + .avl__row {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #f0f0f0;
}

.avl__sup {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.avl__tag {
  margin-inline-end: 0;
}

.avl__meta {
  font-size: 12px;
  color: #666;
  line-height: 1.7;
}

.avl__empty {
  font-size: 12px;
  color: #999;
}

.avl__trigger {
  cursor: default;
}
</style>
