<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { setAppLocale, type AppLocale, useAppI18n } from '@/i18n'

const route = useRoute()
const { locale, t } = useAppI18n()
const elementLocale = computed(() => locale.value === 'en' ? en : zhCn)

function toggleLocale() {
  setAppLocale((locale.value === 'en' ? 'zh-CN' : 'en') as AppLocale)
}

watch(
  [() => route.meta.titleKey, locale],
  () => {
    const pageTitle = route.meta.titleKey ? t(route.meta.titleKey) : t('app.workspace')
    document.title = `${pageTitle} - ${t('app.name')}`
  },
  { immediate: true },
)
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <el-button class="language-switcher" text @click="toggleLocale">
      {{ t('app.language') }}
    </el-button>
    <router-view />
  </el-config-provider>
</template>
