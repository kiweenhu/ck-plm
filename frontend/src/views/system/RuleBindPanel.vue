<template>
  <div class="rule-bind-panel">
    <div class="rb-header">
      <div class="rb-header-left">
        <span v-if="hasBinding" class="rb-status-ok"><CheckCircleOutlined /> 已绑定: {{ getItemName(selectedItem) }}</span>
        <span v-else class="rb-status-none"><WarningOutlined /> 未绑定{{ title }}</span>
      </div>
      <a-button size="small" :type="hasBinding ? 'default' : 'primary'" @click="showBindModal">
        <template #icon><component :is="hasBinding ? SwapOutlined : LinkOutlined" /></template>
        {{ hasBinding ? '更换' : '绑定' }}
      </a-button>
    </div>
    <div class="rb-desc">{{ desc }}</div>

    <!-- 规则详情展开 -->
    <div v-if="hasBinding && selectedItem" class="rb-detail">
      <a-collapse :bordered="false" :ghost="true">
        <a-collapse-panel key="detail" header="查看规则详情">
          <slot name="detail" :item="selectedItem">
            <a-descriptions :column="1" size="small" bordered>
              <a-descriptions-item label="名称">{{ getItemName(selectedItem) }}</a-descriptions-item>
              <a-descriptions-item label="编码"><code>{{ selectedItem[itemCodeKey] || '-' }}</code></a-descriptions-item>
              <a-descriptions-item v-if="selectedItem[itemDescKey]" label="描述">{{ selectedItem[itemDescKey] }}</a-descriptions-item>
            </a-descriptions>
          </slot>
        </a-collapse-panel>
      </a-collapse>
    </div>

    <a-modal v-model:open="bindModalVisible" :title="'选择' + title" width="520px" :footer="null">
      <a-spin :spinning="loading" size="small">
        <a-empty v-if="!loading && items.length === 0" :description="emptyText" :image-style="{ height: '32px' }" />
        <div v-if="items.length > 0" class="rb-select-list">
          <div
            v-for="item in items" :key="getItemKey(item)"
            :class="['rb-select-item', {
              'rb-select-item-selected': getItemKey(item) === selectedCode,
              'rb-select-item-disabled': isDisabled(item)
            }]"
            @click="!isDisabled(item) && handleSelect(item)"
          >
            <div class="rb-select-item-title">
              {{ item[itemTitleKey] }}
              <a-tag v-if="isDisabled(item)" color="default" size="small" style="margin-left:6px">禁用</a-tag>
              <CheckCircleOutlined v-if="getItemKey(item) === selectedCode" style="color:#1677ff;margin-left:auto" />
            </div>
            <div class="rb-select-item-meta">
              <code>{{ item[itemCodeKey] || item.oid?.substring(0,8) }}</code>
              <span v-if="item[itemDescKey]" class="rb-select-item-desc">{{ item[itemDescKey] }}</span>
            </div>
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { CheckCircleOutlined, WarningOutlined, LinkOutlined, SwapOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  loading: Boolean,
  items: { type: Array, default: () => [] },
  selectedCode: { type: String, default: null },
  title: { type: String, default: '' },
  desc: { type: String, default: '' },
  emptyText: { type: String, default: '暂无可用的规则' },
  itemTitleKey: { type: String, default: 'name' },
  itemCodeKey: { type: String, default: 'code' },
  itemDescKey: { type: String, default: 'description' },
  disabledTest: { type: Function, default: () => false },
  getItemName: { type: Function, default: (item) => item?.name || '' },
})

const emit = defineEmits(['select'])
const bindModalVisible = ref(false)

const selectedItem = computed(() => props.items?.find(i => getItemKey(i) === props.selectedCode) || null)
const hasBinding = computed(() => !!props.selectedCode)

function getItemKey(item) { return item[props.itemCodeKey] || item.oid || '' }
function isDisabled(item) { return props.disabledTest(item) }

function showBindModal() { bindModalVisible.value = true }
function handleSelect(item) { emit('select', item); bindModalVisible.value = false }
</script>

<style scoped>
.rule-bind-panel { padding: 0; }
.rb-header { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; background: #fafafa; border-radius: 6px; margin-bottom: 8px; }
.rb-header-left { display: flex; align-items: center; gap: 6px; }
.rb-status-ok { font-size: 13px; color: #52c41a; font-weight: 500; }
.rb-status-none { font-size: 13px; color: #fa8c16; font-weight: 500; }
.rb-desc { font-size: 12px; color: #8c8c8c; margin-bottom: 8px; }

.rb-select-list { max-height: 360px; overflow-y: auto; }
.rb-select-item { padding: 10px 12px; border: 1px solid #f0f0f0; border-radius: 6px; margin-bottom: 6px; cursor: pointer; transition: all .15s; }
.rb-select-item:hover { border-color: #1677ff; background: #f0f5ff; }
.rb-select-item-selected { border-color: #1677ff; background: #e6f4ff; }
.rb-select-item-disabled { opacity: .5; cursor: not-allowed; }
.rb-select-item-disabled:hover { border-color: #f0f0f0; background: transparent; }
.rb-select-item-title { font-size: 13px; font-weight: 500; display: flex; align-items: center; }
.rb-select-item-meta { margin-top: 4px; display: flex; align-items: center; gap: 8px; font-size: 12px; color: #8c8c8c; }
.rb-select-item-desc { color: #bfbfbf; }
</style>
