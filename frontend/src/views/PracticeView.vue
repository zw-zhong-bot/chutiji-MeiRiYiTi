<script setup lang="ts">
import { ref } from 'vue'
import { ApiError, moduleApi, questionApi } from '../api'
import type { Difficulty, GradeView, ModuleMeta, QuestionView } from '../api'
import DifficultyPanel from '../components/difficulty/DifficultyPanel.vue'
import ModulePicker from '../components/ModulePicker.vue'
import QuestionRenderer from '../components/QuestionRenderer.vue'
import ResultPanel from '../components/ResultPanel.vue'

const modules = ref<ModuleMeta[]>([])
const selected = ref<ModuleMeta | null>(null)
const difficulty = ref<Difficulty>({})
const question = ref<QuestionView | null>(null)
const result = ref<GradeView | null>(null)
const error = ref('')
const loadingModules = ref(false)
const creating = ref(false)
const grading = ref(false)

const renderer = ref<InstanceType<typeof QuestionRenderer> | null>(null)
let startedAt = 0

async function loadModules(): Promise<void> {
  loadingModules.value = true
  error.value = ''
  try {
    modules.value = await moduleApi.list()
    if (modules.value.length > 0) selectModule(modules.value[0])
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '加载模块失败'
  } finally {
    loadingModules.value = false
  }
}

function selectModule(module: ModuleMeta): void {
  selected.value = module
  question.value = null
  result.value = null
  error.value = ''
}

function onDifficultyChange(value: Difficulty): void {
  difficulty.value = value
}

async function createQuestion(): Promise<void> {
  if (!selected.value) return
  creating.value = true
  error.value = ''
  result.value = null
  try {
    question.value = await questionApi.create(selected.value.key, difficulty.value)
    startedAt = Date.now()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '出题失败'
  } finally {
    creating.value = false
  }
}

async function submitAnswer(): Promise<void> {
  if (!question.value) return
  const answer = renderer.value?.collectAnswer()
  if (answer === null || answer === undefined || answer === '') {
    error.value = '请先作答'
    return
  }
  grading.value = true
  error.value = ''
  try {
    result.value = await questionApi.grade(question.value.questionId, String(answer), Date.now() - startedAt)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '判题失败'
  } finally {
    grading.value = false
  }
}

void loadModules()
</script>

<template>
  <section class="practice">
    <p v-if="error" class="error-text banner">{{ error }}</p>

    <div class="card block">
      <ModulePicker
        :modules="modules"
        :selected-key="selected?.key ?? null"
        @select="selectModule"
      />
      <p v-if="loadingModules" class="muted">加载模块中…</p>
    </div>

    <div v-if="selected" class="card block">
      <DifficultyPanel :module="selected" @change="onDifficultyChange" />
      <button type="button" :disabled="creating" @click="createQuestion">
        {{ creating ? '出题中…' : '出题' }}
      </button>
    </div>

    <div v-if="question" class="card block">
      <p class="prompt">{{ question.prompt }}</p>
      <p class="muted small">限时 {{ question.timeLimitSec }} 秒</p>

      <QuestionRenderer
        ref="renderer"
        :type="question.type"
        :payload="question.payload"
        @submit-request="submitAnswer"
      />

      <button type="button" :disabled="grading" @click="submitAnswer">
        {{ grading ? '判题中…' : '提交答案' }}
      </button>
    </div>

    <ResultPanel v-if="result" :result="result" />
  </section>
</template>

<style scoped>
.block {
  margin-bottom: 16px;
}

.banner {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 10px 14px;
}

.prompt {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 4px;
}

.small {
  font-size: 13px;
  margin: 0;
}
</style>
