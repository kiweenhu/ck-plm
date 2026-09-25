<template>
  <div class="ftn-node" :style="{ paddingLeft: depth * 28 + 'px' }">
    <div
      class="ftn-row"
      :class="{
        'ftn-row--selected': selectedFolderOid === node.oid,
        'ftn-row--inherited': node._inherited,
      }"
      @click="$emit('select', node)"
    >
      <span class="ftn-expand" @click.stop="$emit('toggle', node)">
        <CaretDownOutlined v-if="hasChildren" :class="{ rotated: !expandedFolders[node.oid] }" />
        <span v-else class="ftn-dot" />
      </span>
      <FolderOutlined class="ftn-icon" :class="{ 'ftn-icon--inherited': node._inherited }" />
      <span class="ftn-name" :class="{ 'ftn-name--inherited': node._inherited }">
        {{ node.name }}
        <a-tag v-if="node._inherited" color="default" size="small" class="ftn-inherited-tag">继承</a-tag>
      </span>
      <!-- 继承节点：克隆按钮 -->
      <span v-if="node._inherited" class="ftn-actions">
        <a-button type="link" size="small" @click.stop="$emit('clone-inherited', node)" title="克隆到本产品">
          <CopyOutlined />
        </a-button>
      </span>
      <!-- 自有节点：编辑操作 -->
      <span v-else class="ftn-actions">
        <a-button type="link" size="small" @click.stop="$emit('create-sub', node.oid)">
          <FolderAddOutlined />
        </a-button>
        <a-button type="link" size="small" @click.stop="$emit('rename', node)">
          <EditOutlined />
        </a-button>
        <a-popconfirm v-if="node.type !== 'SYSTEM'" title="确定删除此文件夹及其子文件夹？"
          @confirm="$emit('delete', node.oid)">
          <a-button type="link" size="small" danger @click.stop>
            <DeleteOutlined />
          </a-button>
        </a-popconfirm>
      </span>
    </div>
    <FolderTreeNode
      v-if="hasChildren && expandedFolders[node.oid] !== false"
      v-for="child in node.children"
      :key="child.oid"
      :node="child"
      :depth="depth + 1"
      :selected-folder-oid="selectedFolderOid"
      :expanded-folders="expandedFolders"
      @select="$emit('select', $event)"
      @toggle="$emit('toggle', $event)"
      @create-sub="$emit('create-sub', $event)"
      @rename="$emit('rename', $event)"
      @delete="$emit('delete', $event)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CaretDownOutlined, FolderOutlined, FolderAddOutlined, EditOutlined, DeleteOutlined, CopyOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  node: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  selectedFolderOid: String,
  expandedFolders: { type: Object, default: () => ({}) },
})

defineEmits(['select', 'toggle', 'create-sub', 'rename', 'delete', 'clone-inherited'])

const hasChildren = computed(() => props.node.children?.length > 0)
</script>

<style scoped>
.ftn-node { padding: 1px 0; }

.ftn-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background .15s;
}
/*
 * 注意：--pl-* 变量原先只在「产品线/阶段」页面的 .pl-dashboard 作用域内定义，
 * 而 FolderTreeNode 是跨页面复用组件（企业资源 / 封装·图符 / 产品线 …）。
 * 在未定义这些变量的页面里 var() 会解析失败 → 选中态与 hover 完全没有颜色。
 * 因此此处为所有变量补上兜底值，使组件自带配色、不依赖宿主页面。
 */
.ftn-row:hover { background: var(--pl-primary-bg, #f0f5ff); }
.ftn-row--selected,
.ftn-row--selected:hover {
  background: var(--pl-primary-light, #e6f4ff);
  border-left: 3px solid var(--pl-primary, #1677ff);
  padding-left: 9px;
}
/* 选中态文字也用主题色 + 加粗，进一步强化区分度 */
.ftn-row--selected .ftn-name {
  color: var(--pl-primary, #1677ff);
  font-weight: 500;
}
.ftn-row--inherited { opacity: .78; }
.ftn-row:hover .ftn-actions { opacity: 1; }

.ftn-expand {
  width: 16px; height: 16px;
  display: inline-flex;
  align-items: center; justify-content: center;
  cursor: pointer;
  color: var(--pl-text-muted, #8c8c8c);
  font-size: 12px;
  flex-shrink: 0;
}
.ftn-expand .rotated { transform: rotate(-90deg); transition: transform .15s; }

.ftn-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: #d9d9d9;
  display: inline-block;
}

.ftn-icon { font-size: 15px; color: #faad14; flex-shrink: 0; }
.ftn-icon--inherited { color: #d9b26b; }

.ftn-name {
  font-size: 13px;
  color: var(--pl-text, #1a1a2e);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 6px;
}
.ftn-name--inherited { color: var(--pl-text-muted, #8c8c8c); }

.ftn-inherited-tag { font-size: 10px; padding: 0 4px; line-height: 16px; }

.ftn-actions {
  opacity: 0;
  transition: opacity .15s;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.ftn-actions :deep(.ant-btn-link) { padding: 0 4px; font-size: 13px; }
</style>
