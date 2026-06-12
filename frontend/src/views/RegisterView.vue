<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/http'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  displayName: '',
  email: '',
  password: '',
  confirmPassword: '',
})
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,50}$/, message: '4-50 位字母、数字或下划线', trigger: 'blur' },
  ],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '密码需同时包含字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [{
    validator: (_rule, value, callback) => {
      if (!value) callback(new Error('请再次输入密码'))
      else if (value !== form.password) callback(new Error('两次输入的密码不一致'))
      else callback()
    },
    trigger: 'blur',
  }],
}

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await auth.register({
      username: form.username,
      displayName: form.displayName,
      email: form.email,
      password: form.password,
    })
    ElMessage.success('注册成功')
    await router.replace('/')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-hero register-hero">
      <div class="auth-hero-content">
        <span class="eyebrow light">JOIN THE WORKFLOW</span>
        <h1>从发现问题开始<br />推动问题解决</h1>
        <p>注册后即可创建问题单，并持续查看处理进度与验证结果。</p>
      </div>
    </section>
    <section class="auth-panel">
      <div class="auth-card wide">
        <span class="eyebrow">CREATE ACCOUNT</span>
        <h2>注册新账号</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <div class="form-grid">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" size="large" />
            </el-form-item>
            <el-form-item label="显示名称" prop="displayName">
              <el-input v-model="form.displayName" size="large" />
            </el-form-item>
          </div>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" size="large" />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" size="large" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="form.confirmPassword" size="large" type="password" show-password />
            </el-form-item>
          </div>
          <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submit">
            创建账号
          </el-button>
        </el-form>
        <p class="auth-switch">已有账号？<router-link to="/login">返回登录</router-link></p>
      </div>
    </section>
  </div>
</template>

