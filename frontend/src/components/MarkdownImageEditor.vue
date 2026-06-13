<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type InputInstance } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { errorMessage, http } from '@/api/http'

const props = withDefaults(defineProps<{
  modelValue: string
  rows?: number
  placeholder?: string
  maxlength?: number
}>(), {
  rows: 10,
  placeholder: '',
  maxlength: 20000,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const inputRef = ref<InputInstance>()
const fileInput = ref<HTMLInputElement>()
const uploading = ref(false)

async function uploadImage(file: File) {
  if (!file.type.startsWith('image/')) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const body = new FormData()
    body.append('file', file, file.name || `pasted-${Date.now()}.png`)
    const { data } = await http.post<{ originalName: string; url: string }>('/api/inline-images', body)
    insertMarkdown(`\n![${escapeAlt(data.originalName)}](${data.url})\n`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    uploading.value = false
  }
}

async function handlePaste(event: ClipboardEvent) {
  const imageItem = Array.from(event.clipboardData?.items || [])
    .find((item) => item.type.startsWith('image/'))
  const file = imageItem?.getAsFile()
  if (!file) return
  event.preventDefault()
  await uploadImage(file)
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) await uploadImage(file)
  target.value = ''
}

function insertMarkdown(markdown: string) {
  const textarea = inputRef.value?.textarea
  const start = textarea?.selectionStart ?? props.modelValue.length
  const end = textarea?.selectionEnd ?? start
  const nextValue = props.modelValue.slice(0, start) + markdown + props.modelValue.slice(end)
  emit('update:modelValue', nextValue)
  requestAnimationFrame(() => {
    const cursor = start + markdown.length
    textarea?.focus()
    textarea?.setSelectionRange(cursor, cursor)
  })
}

function escapeAlt(value: string) {
  return value.replace(/[[\]]/g, '')
}
</script>

<template>
  <div class="markdown-image-editor">
    <div class="editor-toolbar">
      <el-button :icon="Picture" :loading="uploading" @click="fileInput?.click()">
        {{ uploading ? '图片上传中' : '插入图片' }}
      </el-button>
      <span>也可以直接粘贴剪贴板中的图片</span>
      <input
        ref="fileInput"
        class="hidden-file-input"
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        @change="handleFileChange"
      />
    </div>
    <el-input
      ref="inputRef"
      :model-value="modelValue"
      type="textarea"
      :rows="rows"
      :maxlength="maxlength"
      show-word-limit
      :placeholder="placeholder"
      @update:model-value="emit('update:modelValue', $event)"
      @paste="handlePaste"
    />
  </div>
</template>

