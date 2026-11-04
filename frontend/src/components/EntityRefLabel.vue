<template>
  <!-- 回查到了：名称 + 编码（可点进详情）+ 版本 + 生命周期状态 -->
  <span v-if="label?.resolved" class="erl">
    <!-- 链接挂在**编码**上：编码是这条对象的身份证，一眼能认、也便于复制核对；
         名称是描述性文案，把它做成链接反而让整行看起来都像入口（2026-11-04 用户要求调换） -->
    <span class="erl-name erl-name--plain">{{ label.name }}</span>
    <router-link v-if="label.code && label.path" class="erl-code erl-code--link" :to="label.path">
      {{ label.code }}
    </router-link>
    <span v-else-if="label.code" class="erl-code">{{ label.code }}</span>
    <!-- 没有编码但有入口（极少见）：退一步让名称可点，总比一条都点不进去强 -->
    <router-link v-else-if="label.path" class="erl-name erl-name--link" :to="label.path">
      {{ label.name }}
    </router-link>
    <a-tag v-if="label.version" size="small">版本 {{ label.version }}</a-tag>
    <a-tag v-if="label.status" size="small" :color="label.statusColor || 'green'">{{ label.status }}</a-tag>
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

.erl-name--link {
  color: #1677ff;
  text-decoration: none;
}

.erl-name--link:hover {
  text-decoration: underline;
}

.erl-code {
  color: #8c8c8c;
  font-family: 'Consolas', 'Monaco', monospace;
}

/* 编码是入口：给足可点击的观感（蓝 + 悬停下划线），但不开新窗口、不改字号 */
.erl-code--link,
.erl-code--link:hover {
  color: #1677ff;
  text-decoration: none;
}

.erl-code--link:hover {
  text-decoration: underline;
}

.erl--raw {
  color: #bfbfbf;
}
</style>
