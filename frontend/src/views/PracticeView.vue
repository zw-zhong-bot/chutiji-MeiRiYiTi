<script setup lang="ts">
import { ref } from 'vue'
import { ApiError, dailyApi, moduleApi, questionApi } from '../api'
import type { DailyQuestionView, Difficulty, GradeView, ModuleMeta, QuestionView } from '../api'
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

// 今日一题：进页面就取一次。后端按 user_id + 日期定死，所以当天怎么刷新都是同一道。
const daily = ref<DailyQuestionView | null>(null)
const dailyLoading = ref(false)
const dailyError = ref('')

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

/** 把今日一题载入答题区；判题、提交走的仍是下面那套，不另外开一条链路。 */
function openDaily(): void {
  if (!daily.value) return
  question.value = daily.value
  result.value = null
  error.value = ''
  dailyError.value = ''
  startedAt = Date.now()
}

async function loadDaily(): Promise<void> {
  dailyLoading.value = true
  dailyError.value = ''
  try {
    daily.value = await dailyApi.today()
  } catch (e) {
    dailyError.value = e instanceof ApiError ? e.message : '加载今日一题失败'
  } finally {
    dailyLoading.value = false
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
    result.value = await questionApi.grade(
      question.value.questionId,
      String(answer),
      Date.now() - startedAt,
      // 只有画过才带 ink；null 转 undefined 后 JSON.stringify 会丢掉这个键。
      renderer.value?.collectInk() ?? undefined,
    )
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '判题失败'
  } finally {
    grading.value = false
  }
}

void loadModules()
void loadDaily()
</script>

<template>
  <section class="practice">
    <p v-if="error" class="error-text banner">{{ error }}</p>

    <div class="card block daily">
      <div class="daily-head">
        <div>
          <h2 class="daily-title">今日一题</h2>
          <p class="muted small">
            每天一道，当天怎么刷新都是这同一道；点进去直接作答。
          </p>
        </div>
        <button type="button" class="daily-btn" :disabled="dailyLoading || !daily" @click="openDaily">
          {{ dailyLoading ? '加载中…' : '开始今日一题' }}
        </button>
      </div>
      <p v-if="dailyError" class="error-text">{{ dailyError }}</p>
      <p v-else-if="daily" class="muted small daily-meta">
        模块 {{ daily.moduleKey }} · 题型 {{ daily.type }} · 限时 {{ daily.timeLimitSec }} 秒
      </p>
    </div>

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

.daily {
  border-left: 4px solid #2563eb;
}

.daily-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.daily-title {
  font-size: 17px;
  margin: 0 0 4px;
}

.daily-btn {
  white-space: nowrap;
}

.daily-meta {
  margin-top: 10px;
}
</style>
