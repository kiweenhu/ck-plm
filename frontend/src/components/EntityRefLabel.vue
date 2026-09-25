<template>
  <!-- 回查到了：名称（可点进该大版本最新版本）+ 编码 + 版本 + 生命周期状态 -->
  <span v-if="label?.resolved" class="erl">
    <router-link v-if="label.path" class="erl-name" :to="label.path">{{ label.name }}</router-link>
    <span v-else class="erl-name erl-name--plain">{{ label.name }}</span>
    <span v-if="label.code" class="erl-code">{{ label.code }}</span>
    <a-tag v-if="label.version" size="small">版本 {{ label.version }}</a-tag>
    <a-tag v-if="label.status" size="small" color="green">{{ label.status }}</a-tag>
  </span>
  <!-- 没回查到（无权限 / 对象已删）不编造：退化为显示引用本身，至少能跟人核对 -->
  <span v-else-if="label" class="erl erl--raw" title="无法回查该对象的名称（可能已被删除或没有权限）">
    {{ label.fallback }}
  </span>
</template>

<script setup>
/**
 * 「业务标识」标签 —— 流程监控列表与流程详情共用。
 *
 * <p>视图模型由 {@code utils/entityRef.buildEntityLabel} 拼好传进来，本组件只负责渲染。
 * 做成组件而不是各页面各写一段：同一行字在两处长得不一样（列表有版本、详情没版本）
 * 是最容易发生、也最难被发现的漂移。
 */
defineProps({
  /** buildEntityLabel 的结果；null 表示该行没有关联业务实体 */
  label: { type: Object, default: null },
})
</script>

<style scoped>
.erl {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  line-height: 1.6;
}

.erl-name--plain {
  color: #434343;
}

.erl-code {
  color: #8c8c8c;
  font-family: 'Consolas', 'Monaco', monospace;
}

.erl--raw {
  color: #bfbfbf;
}
</style>
