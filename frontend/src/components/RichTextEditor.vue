<template>
  <div class="rte-container" :class="{ 'rte-disabled': disabled }">
    <!-- 工具栏 -->
    <div class="rte-toolbar" v-if="!disabled">
      <!-- 撤销/重做 -->
      <a-button-group size="small">
        <a-button :disabled="!editor?.can().undo()" @click="editor?.chain().focus().undo().run()">
          <UndoOutlined />
        </a-button>
        <a-button :disabled="!editor?.can().redo()" @click="editor?.chain().focus().redo().run()">
          <RedoOutlined />
        </a-button>
      </a-button-group>

      <a-divider type="vertical" />

      <!-- 段落样式 -->
      <a-select
        :value="currentHeading"
        size="small"
        style="width: 100px"
        :options="headingOptions"
        @change="setHeading"
      />

      <a-divider type="vertical" />

      <!-- 文字样式 -->
      <a-button-group size="small">
        <a-button
          @click="editor?.chain().focus().toggleBold().run()"
          :type="editor?.isActive('bold') ? 'primary' : 'default'"
        ><BoldOutlined /></a-button>
        <a-button
          @click="editor?.chain().focus().toggleItalic().run()"
          :type="editor?.isActive('italic') ? 'primary' : 'default'"
        ><ItalicOutlined /></a-button>
        <a-button
          @click="editor?.chain().focus().toggleUnderline().run()"
          :type="editor?.isActive('underline') ? 'primary' : 'default'"
        ><UnderlineOutlined /></a-button>
        <a-button
          @click="editor?.chain().focus().toggleStrike().run()"
          :type="editor?.isActive('strike') ? 'primary' : 'default'"
        ><StrikethroughOutlined /></a-button>
        <a-button
          @click="editor?.chain().focus().toggleHighlight().run()"
          :type="editor?.isActive('highlight') ? 'primary' : 'default'"
        ><HighlightOutlined /></a-button>
      </a-button-group>

      <a-divider type="vertical" />

      <!-- 文字颜色 -->
      <a-tooltip title="文字颜色">
        <div class="rte-color-picker">
          <span class="rte-color-label"><FontColorsOutlined /></span>
          <input type="color" class="rte-color-input" :value="currentTextColor" @input="setTextColor" />
        </div>
      </a-tooltip>

      <a-divider type="vertical" />

      <!-- 对齐 -->
      <a-button-group size="small">
        <a-button @click="editor?.chain().focus().setTextAlign('left').run()"
          :type="editor?.isActive({ textAlign: 'left' }) ? 'primary' : 'default'"
        ><AlignLeftOutlined /></a-button>
        <a-button @click="editor?.chain().focus().setTextAlign('center').run()"
          :type="editor?.isActive({ textAlign: 'center' }) ? 'primary' : 'default'"
        ><AlignCenterOutlined /></a-button>
        <a-button @click="editor?.chain().focus().setTextAlign('right').run()"
          :type="editor?.isActive({ textAlign: 'right' }) ? 'primary' : 'default'"
        ><AlignRightOutlined /></a-button>
        <a-button @click="editor?.chain().focus().setTextAlign('justify').run()"
          :type="editor?.isActive({ textAlign: 'justify' }) ? 'primary' : 'default'"
        ><AlignLeftOutlined /></a-button>
      </a-button-group>

      <a-divider type="vertical" />

      <!-- 列表 -->
      <a-button-group size="small">
        <a-button @click="editor?.chain().focus().toggleBulletList().run()"
          :type="editor?.isActive('bulletList') ? 'primary' : 'default'"
        ><UnorderedListOutlined /></a-button>
        <a-button @click="editor?.chain().focus().toggleOrderedList().run()"
          :type="editor?.isActive('orderedList') ? 'primary' : 'default'"
        ><OrderedListOutlined /></a-button>
        <a-button @click="editor?.chain().focus().toggleTaskList().run()"
          :type="editor?.isActive('taskList') ? 'primary' : 'default'"
        ><CheckSquareOutlined /></a-button>
      </a-button-group>

      <a-divider type="vertical" />

      <!-- 引用 / 代码 / 分割线 -->
      <a-button-group size="small">
        <a-button @click="editor?.chain().focus().toggleBlockquote().run()"
          :type="editor?.isActive('blockquote') ? 'primary' : 'default'"
        ><BlockOutlined /></a-button>
        <a-button @click="editor?.chain().focus().toggleCodeBlock().run()"
          :type="editor?.isActive('codeBlock') ? 'primary' : 'default'"
        ><CodeOutlined /></a-button>
        <a-button @click="editor?.chain().focus().setHorizontalRule().run()"
        ><MinusOutlined /></a-button>
      </a-button-group>

      <a-divider type="vertical" />

      <!-- 表格 -->
      <a-tooltip title="插入表格">
        <a-button size="small" @click="insertTable"
        ><TableOutlined /></a-button>
      </a-tooltip>

      <!-- 链接 -->
      <a-tooltip title="插入链接">
        <a-button size="small" @click="promptLink"
          :type="editor?.isActive('link') ? 'primary' : 'default'"
        ><LinkOutlined /></a-button>
      </a-tooltip>

      <!-- 图片上传 -->
      <a-tooltip title="插入图片">
        <a-upload
          :show-upload-list="false"
          accept="image/*"
          :before-upload="handleImageUpload"
        >
          <a-button size="small"><FileImageOutlined /></a-button>
        </a-upload>
      </a-tooltip>

      <a-divider type="vertical" />

      <!-- 清除格式 -->
      <a-button size="small" @click="editor?.chain().focus().clearNodes().unsetAllMarks().run()">
        清除格式
      </a-button>
    </div>

    <!-- 编辑器内容区 -->
    <div class="rte-content">
      <editor-content :editor="editor" />
    </div>

    <!-- 链接弹窗 -->
    <a-modal
      v-model:open="linkModalVisible"
      title="插入链接"
      :width="400"
      :mask-closable="false"
      @ok="applyLink"
    >
      <a-input v-model:value="linkUrl" placeholder="请输入链接地址 (https://...)" />
    </a-modal>
  </div>
</template>

<script setup>
import { ref, watch, computed, onBeforeUnmount, shallowRef } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import TextAlign from '@tiptap/extension-text-align'
import Placeholder from '@tiptap/extension-placeholder'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import Highlight from '@tiptap/extension-highlight'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import { FontFamily } from '@tiptap/extension-font-family'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import {
  UndoOutlined, RedoOutlined, BoldOutlined, ItalicOutlined,
  UnderlineOutlined, StrikethroughOutlined, AlignLeftOutlined,
  AlignCenterOutlined, AlignRightOutlined, UnorderedListOutlined,
  OrderedListOutlined, HighlightOutlined, BlockOutlined, CodeOutlined,
  MinusOutlined, TableOutlined, LinkOutlined, FileImageOutlined,
  FontColorsOutlined, CheckSquareOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '请输入内容...' }
})

const emit = defineEmits(['update:modelValue', 'focus', 'blur'])

// ========== 编辑器初始化 ==========
const editor = useEditor({
  content: props.modelValue || '',
  editable: !props.disabled,
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3, 4] }
    }),
    Underline,
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    Placeholder.configure({ placeholder: props.placeholder }),
    Table.configure({ resizable: true }),
    TableRow,
    TableCell,
    TableHeader,
    Image.configure({ inline: true, allowBase64: true }),
    Link.configure({ openOnClick: false, autolink: true }),
    Highlight.configure({ multicolor: true }),
    TextStyle,
    Color,
    FontFamily,
    TaskList,
    TaskItem.configure({ nested: true })
  ],
  onUpdate: ({ editor }) => {
    const html = editor.getHTML()
    // 过滤掉空的 placeholder data 属性
    const val = (html === '<p></p>' || html === '') ? '' : html
    emit('update:modelValue', val)
  },
  onFocus: () => emit('focus'),
  onBlur: () => emit('blur')
})

// ========== 内外值同步 ==========
// 外部 modelValue 变化 → 更新编辑器内容
const isInternalUpdate = ref(false)
watch(() => props.modelValue, (newVal) => {
  if (!editor.value) return
  const currentHtml = editor.value.getHTML()
  const normCurrent = (currentHtml === '<p></p>' || currentHtml === '') ? '' : currentHtml
  const normNew = newVal || ''
  if (normCurrent !== normNew) {
    isInternalUpdate.value = true
    editor.value.commands.setContent(normNew)
    isInternalUpdate.value = false
  }
})

// disabled 变化
watch(() => props.disabled, (val) => {
  if (editor.value) editor.value.setEditable(!val)
})

// ========== 段落 ==========
const headingOptions = [
  { value: 'paragraph', label: '正文' },
  { value: 'heading-1', label: '标题1' },
  { value: 'heading-2', label: '标题2' },
  { value: 'heading-3', label: '标题3' },
  { value: 'heading-4', label: '标题4' }
]

const currentHeading = computed(() => {
  if (!editor.value) return 'paragraph'
  for (let i = 4; i >= 1; i--) {
    if (editor.value.isActive('heading', { level: i })) return `heading-${i}`
  }
  return 'paragraph'
})

function setHeading(val) {
  if (!editor.value) return
  if (val === 'paragraph') {
    editor.value.chain().focus().setParagraph().run()
  } else {
    const level = parseInt(val.split('-')[1])
    editor.value.chain().focus().toggleHeading({ level }).run()
  }
}

// ========== 文字颜色 ==========
const currentTextColor = computed(() => {
  return editor.value?.getAttributes('textStyle').color || '#262626'
})

function setTextColor(e) {
  if (!editor.value) return
  const color = e.target.value
  if (color === '#262626' || color === '#000000') {
    editor.value.chain().focus().unsetColor().run()
  } else {
    editor.value.chain().focus().setColor(color).run()
  }
}

// ========== 表格 ==========
function insertTable() {
  editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
}

// ========== 链接 ==========
const linkModalVisible = ref(false)
const linkUrl = ref('')

function promptLink() {
  if (!editor.value) return
  const prev = editor.value.getAttributes('link').href || ''
  linkUrl.value = prev
  linkModalVisible.value = true
}

function applyLink() {
  if (!editor.value) return
  linkModalVisible.value = false
  const url = linkUrl.value.trim()
  if (!url) {
    editor.value.chain().focus().unsetLink().run()
    return
  }
  // 自动补全协议
  const href = /^https?:\/\//i.test(url) ? url : `https://${url}`
  editor.value.chain().focus().extendMarkRange('link').setLink({ href }).run()
}

// ========== 图片上传 ==========
function handleImageUpload(file) {
  const reader = new FileReader()
  reader.onload = (e) => {
    editor.value?.chain().focus().setImage({ src: e.target.result }).run()
    message.success('图片已插入')
  }
  reader.readAsDataURL(file)
  return false // 阻止默认上传
}

// ========== 生命周期 ==========
onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>

<script>
// Options API 兼容导出
export default { name: 'RichTextEditor' }
</script>

<style scoped>
.rte-container {
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #fff;
}

.rte-container:focus-within {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.1);
}

.rte-container.rte-disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

/* ===== 工具栏 ===== */
.rte-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px 8px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
}

.rte-toolbar .ant-divider-vertical {
  height: 20px;
  margin: 0 2px;
}

/* ===== 颜色选择器 ===== */
.rte-color-picker {
  position: relative;
  width: 28px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
}

.rte-color-picker:hover {
  border-color: #1677ff;
}

.rte-color-label {
  font-size: 14px;
  color: #595959;
  pointer-events: none;
}

.rte-color-input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

/* ===== 编辑器内容区 ===== */
.rte-content {
  max-height: 420px;
  overflow-y: auto;
}

/* Tiptap 编辑器样式 */
.rte-content :deep(.tiptap) {
  min-height: 120px;
  padding: 10px 14px;
  outline: none;
  font-size: 14px;
  line-height: 1.8;
  color: #262626;
  word-break: break-word;
}

/* placeholder */
.rte-content :deep(.tiptap p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  color: #bfbfbf;
  pointer-events: none;
  height: 0;
}

/* 标题样式 */
.rte-content :deep(.tiptap h1) { font-size: 1.5em; line-height: 1.4; margin: 0.6em 0 0.3em; }
.rte-content :deep(.tiptap h2) { font-size: 1.35em; line-height: 1.4; margin: 0.5em 0 0.25em; }
.rte-content :deep(.tiptap h3) { font-size: 1.18em; line-height: 1.4; margin: 0.4em 0 0.2em; }
.rte-content :deep(.tiptap h4) { font-size: 1.05em; line-height: 1.4; margin: 0.3em 0 0.15em; }

/* 列表 */
.rte-content :deep(.tiptap ul),
.rte-content :deep(.tiptap ol) {
  padding-left: 1.6em;
  margin: 4px 0;
}

.rte-content :deep(.tiptap ul[data-type="taskList"]) {
  list-style: none;
  padding-left: 0.5em;
}

.rte-content :deep(.tiptap ul[data-type="taskList"] li) {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.rte-content :deep(.tiptap ul[data-type="taskList"] li > label) {
  margin-top: 3px;
  user-select: none;
}

/* 引用块 */
.rte-content :deep(.tiptap blockquote) {
  border-left: 3px solid #1677ff;
  padding: 8px 16px;
  margin: 8px 0;
  background: #f0f5ff;
  color: #595959;
  border-radius: 0 4px 4px 0;
}

/* 代码块 */
.rte-content :deep(.tiptap pre) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px 16px;
  border-radius: 6px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  margin: 8px 0;
}

.rte-content :deep(.tiptap pre code) {
  background: none;
  padding: 0;
  color: inherit;
  font-size: inherit;
}

/* 行内代码 */
.rte-content :deep(.tiptap code) {
  background: #f5f2f0;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 0.9em;
  color: #c41d7f;
}

/* 分割线 */
.rte-content :deep(.tiptap hr) {
  border: none;
  border-top: 2px solid #e8e8e8;
  margin: 16px 0;
}

/* 表格 */
.rte-content :deep(.tiptap table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
}

.rte-content :deep(.tiptap table th),
.rte-content :deep(.tiptap table td) {
  border: 1px solid #d9d9d9;
  padding: 6px 12px;
  min-width: 60px;
  text-align: left;
}

.rte-content :deep(.tiptap table th) {
  background: #fafafa;
  font-weight: 600;
}

/* 图片 */
.rte-content :deep(.tiptap img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 8px 0;
}

.rte-content :deep(.tiptap img.ProseMirror-selectednode) {
  outline: 2px solid #1677ff;
}

/* 链接 */
.rte-content :deep(.tiptap a) {
  color: #1677ff;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

/* 高亮 */
.rte-content :deep(.tiptap mark) {
  background: #ffd666;
  border-radius: 2px;
  padding: 0 2px;
}

/* 禁用态 */
.rte-disabled :deep(.tiptap) {
  cursor: not-allowed;
  opacity: 0.85;
}

/* table 选中态（ProseMirror 默认） */
.rte-content :deep(.selectedCell) {
  background: rgba(22, 119, 255, 0.1);
}
</style>
