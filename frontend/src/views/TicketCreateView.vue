<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { http, errorMessage } from '@/api/http'
import type { TicketDetail, TicketPriority } from '@/types'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  title: '',
  description: '',
  category: '',
  priority: 'MEDIUM' as TicketPriority,
})
const rules: FormRules = {
  title: [{ required: true, message: '请输入问题标题', trigger: 'blur' }],
  description: [{ required: true, message: '请描述问题现象与影响', trigger: 'blur' }],
  category: [{ required: true, message: '请选择问题分类', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
}

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const { data } = await http.post<TicketDetail>('/api/tickets', form)
    ElMessage.success(`问题单 ${data.ticketNo} 已创建`)
    await router.replace(`/tickets/${data.id}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="panel form-panel">
    <div class="section-heading">
      <div>
        <span class="eyebrow">NEW ISSUE</span>
        <h2>描述需要解决的问题</h2>
      </div>
      <p>提供清晰的复现步骤、影响范围和期望结果，可以显著缩短处理时间。</p>
    </div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="问题标题" prop="title">
        <el-input v-model="form.title" size="large" maxlength="200" show-word-limit placeholder="用一句话概括问题" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="问题分类" prop="category">
          <el-select v-model="form.category" size="large" placeholder="请选择">
            <el-option label="功能异常" value="功能异常" />
            <el-option label="性能问题" value="性能问题" />
            <el-option label="数据问题" value="数据问题" />
            <el-option label="安全问题" value="安全问题" />
            <el-option label="使用咨询" value="使用咨询" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="form.priority" size="large">
            <el-radio-button value="LOW">低</el-radio-button>
            <el-radio-button value="MEDIUM">中</el-radio-button>
            <el-radio-button value="HIGH">高</el-radio-button>
            <el-radio-button value="CRITICAL">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </div>
      <el-form-item label="详细描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="12"
          maxlength="20000"
          show-word-limit
          placeholder="建议包括：问题现象、复现步骤、影响范围、期望结果"
        />
      </el-form-item>
      <div class="form-actions">
        <el-button size="large" @click="router.back()">取消</el-button>
        <el-button type="primary" size="large" :loading="loading" @click="submit">提交问题单</el-button>
      </div>
    </el-form>
  </section>
</template>

