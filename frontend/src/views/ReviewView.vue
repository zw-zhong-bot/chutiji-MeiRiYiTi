<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError, moduleApi, questionApi, reviewApi } from '../api'
import type { GradeView, ModuleMeta, ReviewCard, ReviewStartView, ReviewState } from '../api'
import QuestionRenderer from '../components/QuestionRenderer.vue'
import ResultPanel from '../components/ResultPanel.vue'

/** 列表态或答题态。 */
const phase = ref<'list' | 'review'>('list')
const tab = ref<'due' | 'all'>('due')

const modules = ref<ModuleMeta[]>([])
const dueCards = ref<ReviewCard[]>([])
const allCards = ref<ReviewCard[]>([])
const loading = ref(false)
const error = ref('')

const current = ref<ReviewStartView | null>(null)
const grade = ref<GradeView | null>(null)
const renderer = ref<InstanceType<typeof QuestionRenderer> | null>(null)
const starting = ref(false)
const grading = ref(false)
let startedAt = 0

const moduleByKey = computed(() => new Map(modules.value.map((m) => [m.key, m])))

/** 模块中文名来自 /api/modules，不硬编码。 */
function moduleName(key: string): string {
  return moduleByKey.value.get(key)?.name ?? key
}

/**
 * 难度描述：按模块下发的 axes 顺序，拼「axis.label + 当前值」。
 * 前端不认识任何具体字段名，模块新增轴无需改这里。
 */
function describeDifficulty(card: ReviewCard): string {
  const meta = moduleByKey.value.get(card.moduleKey)
  if (!meta) return ''
  const parts: string[] = []
  for (const axis of meta.axes) {
    if (!(axis.key in card.difficulty)) continue
    const value = card.difficulty[axis.key]
    if (axis.kind === 'BOOLEAN') {
      // 布尔轴也要写出取值：难度配置是复习卡的调度单元，描述必须能区分两张卡。
      parts.push(axis.label + ' ' + (value === true ? '是' : '否'))
      continue
    }
    if (axis.kind === 'ENUM') {
      const option = axis.options?.find((o) => o.value === String(value))
      parts.push(axis.label + ' ' + (option?.label ?? String(value)))
      continue
    }
    parts.push(axis.label + ' ' + String(value))
  }
  return parts.join(' · ')
}

const STATE_LABELS: Record<ReviewState, string> = {
  NEW: '新卡',
  LEARNING: '学习中',
  REVIEW: '复习中',
  RELEARNING: '重学中',
}

function stateLabel(state: ReviewState): string {
  return STATE_LABELS[state] ?? state
}

const shownCards = computed(() => (tab.value === 'due' ? dueCards.value : allCards.value))
const isEmpty = computed(() => !loading.value && shownCards.value.length === 0)

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

function fail(e: unknown, fallback: string): void {
  error.value = e instanceof ApiError ? e.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [moduleList, due, all] = await Promise.all([
      moduleApi.list(),
      reviewApi.due(),
      reviewApi.cards(),
    ])
    modules.value = moduleList
    dueCards.value = due
    allCards.value = [...all].sort((a, b) => a.dueAt.localeCompare(b.dueAt))
    // 有到期卡才默认停在「待复习」，否则直接展示全部，避免新卡（次日才到期）看不到。
    tab.value = due.length > 0 ? 'due' : 'all'
  } catch (e) {
    fail(e, '加载复习卡失败')
  } finally {
    loading.value = false
  }
}

async function startReview(card: ReviewCard): Promise<void> {
  starting.value = true
  error.value = ''
  try {
    current.value = await reviewApi.start(card.cardId)
    grade.value = null
    startedAt = Date.now()
    phase.value = 'review'
  } catch (e) {
    fail(e, '开始复习失败')
  } finally {
    starting.value = false
  }
}

async function submitAnswer(): Promise<void> {
  if (!current.value) return
  const answer = renderer.value?.collectAnswer()
  if (answer === null || answer === undefined || answer === '') {
    error.value = '请先作答'
    return
  }
  grading.value = true
  error.value = ''
  try {
    grade.value = await questionApi.grade(
      current.value.questionId,
      String(answer),
      Date.now() - startedAt,
    )
  } catch (e) {
    fail(e, '判题失败')
  } finally {
    grading.value = false
  }
}

async function backToList(): Promise<void> {
  phase.value = 'list'
  current.value = null
  grade.value = null
  await load()
}

void load()
</script>

<template>
  <section class="review">
    <h1>复习</h1>

    <p v-if="error" class="error-text banner">{{ error }}</p>

    <div v-if="phase === 'review' && current" class="card block">
      <p class="muted small">
        {{ moduleName(current.moduleKey) }} · 限时 {{ current.timeLimitSec }} 秒
      </p>
      <p class="prompt">{{ current.prompt }}</p>

      <QuestionRenderer
        ref="renderer"
        :type="current.type"
        :payload="current.payload"
        @submit-request="submitAnswer"
      />

      <div class="actions">
        <button type="button" :disabled="grading" @click="submitAnswer">
          {{ grading ? '判题中…' : '提交答案' }}
        </button>
        <button type="button" class="btn-ghost" @click="backToList">返回列表</button>
      </div>
    </div>

    <ResultPanel v-if="phase === 'review' && grade" :result="grade" />

    <template v-if="phase === 'list'">
      <p class="summary">
        今日待复习 <strong>{{ dueCards.length }}</strong> 张
      </p>

      <div class="tabs">
        <button
          type="button"
          class="tab"
          :class="{ active: tab === 'due' }"
          @click="tab = 'due'"
        >
          待复习 {{ dueCards.length }}
        </button>
        <button
          type="button"
          class="tab"
          :class="{ active: tab === 'all' }"
          @click="tab = 'all'"
        >
          全部卡片 {{ allCards.length }}
        </button>
      </div>

      <p v-if="loading" class="muted">加载中…</p>

      <div v-if="isEmpty && allCards.length === 0" class="card muted">
        还没有复习卡，做错题后会自动生成
      </div>
      <div v-else-if="isEmpty" class="card muted">今天没有到期卡片，可以去「全部卡片」里提前复习</div>

      <article v-for="card in shownCards" :key="card.cardId" class="card item">
        <div class="item-head">
          <strong>{{ moduleName(card.moduleKey) }}</strong>
          <span class="state">{{ card.state }}</span>
          <span class="muted small">{{ stateLabel(card.state) }}</span>
        </div>
        <p v-if="describeDifficulty(card)" class="difficulty">{{ describeDifficulty(card) }}</p>
        <p class="muted small">
          已复习 {{ card.reps }} 次 · 遗忘 {{ card.lapses }} 次 · 到期 {{ formatTime(card.dueAt) }}
        </p>
        <div class="item-actions">
          <button type="button" :disabled="starting" @click="startReview(card)">开始复习</button>
          <span v-if="!card.due" class="muted small">未到期，可提前复习</span>
        </div>
      </article>
    </template>
  </section>
</template>

<style scoped>
h1 {
  font-size: 20px;
  margin: 0 0 16px;
}

.block {
  margin-bottom: 16px;
}

.banner {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 10px 14px;
}

.summary {
  margin: 0 0 12px;
  font-size: 17px;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.tab {
  background: var(--surface);
  border-color: var(--border);
  color: var(--muted);
}

.tab.active {
  background: var(--accent-weak);
  border-color: var(--accent);
  color: var(--accent);
}

.item {
  margin-bottom: 12px;
}

.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.state {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--accent);
  background: var(--accent-weak);
  border-radius: 999px;
  padding: 1px 8px;
}

.difficulty {
  margin: 6px 0 4px;
  font-size: 14px;
}

.small {
  font-size: 13px;
  margin: 0;
}

.prompt {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 4px;
}

.actions {
  display: flex;
  gap: 10px;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
</style>