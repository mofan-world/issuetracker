<script setup lang="ts">
import { computed } from 'vue'
import type { ProductVersionStatus, VersionOption } from '@/types'
import { buildVersionTree, type VersionTreeNode } from '@/utils/versionTree'

type SelectNode = VersionTreeNode<VersionOption> & {
  label: string
  disabled: boolean
}

const props = withDefaults(defineProps<{
  modelValue?: number
  options: VersionOption[]
  placeholder?: string
  clearable?: boolean
  size?: 'large' | 'default' | 'small'
  disabledIds?: number[]
  respectEnabled?: boolean
  excludeArchived?: boolean
  disabled?: boolean
}>(), {
  placeholder: '请选择版本',
  clearable: false,
  size: 'default',
  disabledIds: () => [],
  respectEnabled: true,
  excludeArchived: false,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  change: [value: number | undefined]
}>()

const statusLabels: Record<ProductVersionStatus, string> = {
  PLANNED: '计划中',
  ACTIVE: '开发中',
  RELEASED: '已发布',
  ARCHIVED: '已归档',
}

const treeData = computed<SelectNode[]>(() => {
  const disabledIds = new Set(props.disabledIds)
  const decorated = props.options.map((option) => ({
    ...option,
    label: `${option.versionNo} · ${option.name}`,
    disabled: disabledIds.has(option.id)
      || (props.respectEnabled && !option.enabled)
      || (props.excludeArchived && option.status === 'ARCHIVED'),
  }))
  return buildVersionTree(decorated) as SelectNode[]
})

const defaultExpandedKeys = computed(() => {
  if (!props.modelValue) return []
  const optionsById = new Map(props.options.map((item) => [item.id, item]))
  const keys: number[] = []
  let parentId = optionsById.get(props.modelValue)?.parentId
  while (parentId) {
    keys.unshift(parentId)
    parentId = optionsById.get(parentId)?.parentId
  }
  return keys
})

const treeProps = {
  value: 'id',
  label: 'label',
  children: 'children',
  disabled: 'disabled',
}

function filterNode(keyword: string, data: SelectNode) {
  const query = keyword.trim().toLowerCase()
  if (!query) return true
  return `${data.versionNo} ${data.name} ${data.pathLabel}`.toLowerCase().includes(query)
}

function updateValue(value: unknown) {
  const nextValue = typeof value === 'number' ? value : undefined
  emit('update:modelValue', nextValue)
  emit('change', nextValue)
}
</script>

<template>
  <el-tree-select
    class="full-width"
    :model-value="modelValue"
    :data="treeData"
    :props="treeProps"
    :size="size"
    :disabled="disabled"
    :placeholder="placeholder"
    :clearable="clearable"
    :default-expanded-keys="defaultExpandedKeys"
    node-key="id"
    check-strictly
    filterable
    :filter-node-method="filterNode"
    :render-after-expand="false"
    @update:model-value="updateValue"
  >
    <template #default="{ data }">
      <span class="version-tree-node">
        <strong>{{ data.versionNo }}</strong>
        <span>{{ data.name }}</span>
        <small>{{ statusLabels[data.status as ProductVersionStatus] }}</small>
      </span>
    </template>
  </el-tree-select>
</template>
