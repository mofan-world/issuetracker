import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { http } from '@/api/http'
import type { ProjectView } from '@/types'

function storedProjectId() {
  const value = Number(localStorage.getItem('currentProjectId'))
  return Number.isSafeInteger(value) && value > 0 ? value : undefined
}

export const useProjectStore = defineStore('project', () => {
  const projects = ref<ProjectView[]>([])
  const currentProjectId = ref<number | undefined>(storedProjectId())
  const loading = ref(false)
  const loaded = ref(false)
  const currentProject = computed(() =>
    projects.value.find((project) => project.id === currentProjectId.value),
  )

  async function loadProjects(force = false) {
    if (loading.value || (loaded.value && !force)) return
    loading.value = true
    try {
      const { data } = await http.get<ProjectView[]>('/api/projects/my')
      projects.value = data
      const selected = data.some((project) => project.id === currentProjectId.value)
        ? currentProjectId.value
        : data[0]?.id
      setCurrentProject(selected)
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  function setCurrentProject(projectId?: number) {
    currentProjectId.value = projectId
    if (projectId) localStorage.setItem('currentProjectId', String(projectId))
    else localStorage.removeItem('currentProjectId')
  }

  function reset() {
    projects.value = []
    currentProjectId.value = undefined
    loaded.value = false
    localStorage.removeItem('currentProjectId')
  }

  return {
    projects,
    currentProjectId,
    currentProject,
    loading,
    loaded,
    loadProjects,
    setCurrentProject,
    reset,
  }
})
