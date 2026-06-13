<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ content: string }>()

type Block = { type: 'image'; alt: string; url: string } | { type: 'text'; text: string }

const blocks = computed<Block[]>(() => {
  const imagePattern = /^!\[([^\]]*)]\((\/api\/inline-images\/[A-Za-z0-9._-]+)\)$/
  return props.content.split('\n').map((line) => {
    const match = line.trim().match(imagePattern)
    return match
      ? { type: 'image', alt: match[1], url: match[2] }
      : { type: 'text', text: line }
  })
})
</script>

<template>
  <div class="safe-markdown">
    <template v-for="(block, index) in blocks" :key="index">
      <img
        v-if="block.type === 'image'"
        :src="block.url"
        :alt="block.alt"
        loading="lazy"
      />
      <p v-else :class="{ empty: !block.text }">{{ block.text || '\u00a0' }}</p>
    </template>
  </div>
</template>

