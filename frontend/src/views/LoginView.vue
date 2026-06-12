<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/http'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await auth.login(form)
    ElMessage.success('登录成功')
    await router.replace((route.query.redirect as string) || '/')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-hero">
      <div class="auth-hero-content">
        <span class="eyebrow light">ISSUE OPERATIONS</span>
        <h1>让每一个问题<br />都有清晰的归处</h1>
        <p>从提交、分派、处理到验证关闭，全流程可追踪、可审计。</p>
        <div class="hero-metrics">
          <div><strong>全流程</strong><span>状态可见</span></div>
          <div><strong>RBAC</strong><span>权限隔离</span></div>
          <div><strong>可扩展</strong><span>面向 20 万用户</span></div>
        </div>
      </div>
    </section>
    <section class="auth-panel">
      <div class="auth-card">
        <span class="eyebrow">WELCOME BACK</span>
        <h2>登录工作台</h2>
        <p class="muted">使用你的组织账号继续</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form>
        <p class="auth-switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
      </div>
    </section>
  </div>
</template>

