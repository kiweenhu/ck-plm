<template>
  <!-- 全局中文：不挂 ConfigProvider 时，AntD 自带的按钮/占位符一律是英文
       （Popconfirm 的 Cancel / OK、日期面板的月份、表格的空数据……），
       与整站中文文案混在一起，让人以为"确认按钮点不了" -->
  <a-config-provider :locale="zhCN">
    <router-view />
  </a-config-provider>
</template>

<script setup>
// App 根组件 —— 路由出口
import zhCN from 'ant-design-vue/es/locale/zh_CN'
</script>
