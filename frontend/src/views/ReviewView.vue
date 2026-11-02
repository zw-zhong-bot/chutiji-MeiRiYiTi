<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError, moduleApi, questionApi, reviewApi } from '../api'
import type {
  GradeView,
  ModuleMeta,
  ReviewCard,
  ReviewStartView,
  ReviewStats,
  ReviewState,
} from '../api'
import QuestionRenderer from '../components/QuestionRenderer.vue'
import ResultPanel from '../components/ResultPanel.vue'

/** 列表态或答题态。 */
const phase = ref<'list' | 'review'>('list')
const tab = ref<'due' | 'all' | 'stats'>('due')

const modules = ref<ModuleMeta[]>([])
const dueCards = ref<ReviewCard[]>([])
const allCards = ref<ReviewCard[]>([])
const loading = ref(false)
const error = ref('')

const current = ref<ReviewStartView | null>(null)
const grade = ref<GradeView | null>(null)
const stats = ref<ReviewStats | null>(null)
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

/** 柱状图的纵轴上限：空数据时按 1 处理，否则除零会算出 NaN 高度。 */
const peakReviews = computed(() =>
  Math.max(1, ...(stats.value?.last7Days ?? []).map((day) => day.reviews)),
)

function barHeight(value: number): string {
  return (value / peakReviews.value) * 100 + '%'
}

/** 模块条形图按「卡片最多的那个模块」作满格，避免小模块被压成看不见。 */
const peakCards = computed(() => Math.max(1, ...(stats.value?.byModule ?? []).map((row) => row.cards)))

function cardWidth(cards: number): string {
  return (cards / peakCards.value) * 100 + '%'
}

/** 日期只显示 MM-DD，柱状图下方一行放得下。 */
function shortDate(date: string): string {
  return date.slice(5)
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

function fail(e: unknown, fallback: string): void {
  error.value = e instanceof ApiError ? e.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  // 统计 tab 是独立拉取的，切 tab 时才需要它，但列表页顶部也要显示出数字，
  // 所以这里和卡片一起并发取，失败不影响卡片展示。
  const statsTask = reviewApi.stats().catch(() => null)
  try {
    const [moduleList, due, all] = await Promise.all([
      moduleApi.list(),
      reviewApi.due(),
      reviewApi.cards(),
    ])
    modules.value = moduleList
    dueCards.value = due
    allCards.value = [...all].sort((a, b) => a.dueAt.localeCompare(b.dueAt))
    // 有到期卡才默认停在「待复习」，否则展示全部，避免新卡（次日才到期）看不到；
    // 但用户点了统计 tab 就别抢回去。
    if (tab.value !== 'stats') {
      tab.value = due.length > 0 ? 'due' : 'all'
    }
  } catch (e) {
    fail(e, '加载复习卡失败')
  } finally {
    stats.value = await statsTask
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
      // 只有画过才带 ink；null 转 undefined 后 JSON.stringify 会丢掉这个键。
      renderer.value?.collectInk() ?? undefined,
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
        <button
          type="button"
          class="tab"
          :class="{ active: tab === 'stats' }"
          @click="tab = 'stats'"
        >
          统计
        </button>
      </div>

      <p v-if="loading" class="muted">加载中…</p>

      <!-- 统计 tab：全部只读聚合，数据来自 GET /api/review/stats（按当前用户过滤） -->
      <template v-if="tab === 'stats'">
        <div v-if="!stats" class="card muted">统计加载失败，刷新可重试</div>
        <template v-else>
          <div class="stat-grid">
            <div class="card stat">
              <span class="stat-label">总卡片</span>
              <strong class="stat-value">{{ stats.totalCards }}</strong>
            </div>
            <div class="card stat">
              <span class="stat-label">待复习</span>
              <strong class="stat-value">{{ stats.dueCards }}</strong>
            </div>
            <div class="card stat">
              <span class="stat-label">累计复习</span>
              <strong class="stat-value">{{ stats.totalReviews }}</strong>
            </div>
            <div class="card stat">
              <span class="stat-label">累计遗忘</span>
              <strong class="stat-value">{{ stats.totalLapses }}</strong>
            </div>
          </div>

          <div class="card section">
            <h2>最近 7 天</h2>
            <p class="muted small">
              新卡 {{ stats.newCards }} 张 · 复习卡 {{ stats.reviewCards }} 张 · 平均稳定度
              {{ stats.avgStability }} 天
            </p>
            <!-- 柱状图：纯 div 高度百分比，不引图表库 -->
            <div class="chart" role="img" aria-label="最近 7 天复习量">
              <div
                v-for="day in stats.last7Days"
                :key="day.date"
                class="chart-col"
                :title="day.date + '：复习 ' + day.reviews + ' 次，答对 ' + day.correct + ' 次'"
              >
                <div class="chart-track">
                  <div class="chart-bar" :style="{ height: barHeight(day.reviews) }">
                    <!-- 数值贴在柱顶，而不是浮在整个图表顶部 -->
                    <span class="chart-value">{{ day.reviews }}</span>
                    <div class="chart-correct" :style="{ height: barHeight(day.correct) }" />
                  </div>
                </div>
                <span class="chart-x">{{ shortDate(day.date) }}</span>
              </div>
            </div>
            <p class="legend muted small">
              <i class="dot correct" /> 答对 {{ stats.last7Days.reduce((sum, d) => sum + d.correct, 0) }} 次
              <i class="dot total" />
              复习 {{ stats.last7Days.reduce((sum, d) => sum + d.reviews, 0) }} 次
            </p>
          </div>

          <div class="card section">
            <h2>按模块</h2>
            <p v-if="stats.byModule.length === 0" class="muted small">
              还没有卡片数据，做错题后会自动生成
            </p>
            <ul v-else class="module-list">
              <li v-for="row in stats.byModule" :key="row.moduleKey" class="module-row">
                <span class="module-name">{{ moduleName(row.moduleKey) }}</span>
                <span class="muted small">
                  {{ row.cards }} 张卡 · 复习 {{ row.reviews }} 次 · 遗忘 {{ row.lapses }} 次
                </span>
                <div class="module-bar">
                  <div
                    class="module-fill"
                    :style="{ width: cardWidth(row.cards) }"
                    :title="row.cards + ' 张卡'"
                  />
                </div>
              </li>
            </ul>
          </div>
        </template>
      </template>

      <template v-else>
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

/* ---------- 统计 tab ---------- */

.stat-grid {
  display: grid;
  /* 窄屏自动落成 2 列，不需要媒体查询 */
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
}

.stat-label {
  font-size: 13px;
  color: var(--muted);
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
}

.section {
  margin-bottom: 12px;
}

.section h2 {
  font-size: 15px;
  margin: 0 0 6px;
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 150px;
  /* 给柱顶的数值留出位置，否则最高那根的数字会被裁掉 */
  padding-top: 16px;
  margin: 10px 0 6px;
}

.chart-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  min-width: 0;
}

.chart-value {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 2px;
  font-size: 12px;
  line-height: 1;
  color: var(--muted);
}

/* 轨道撑满剩余高度，柱子按百分比从底部生长 */
.chart-track {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.chart-bar {
  position: relative;
  width: 70%;
  max-width: 34px;
  min-height: 2px;
  background: var(--accent-weak);
  border: 1px solid var(--accent);
  border-radius: 4px 4px 0 0;
  display: flex;
  align-items: flex-end;
}

/* 答对量叠在柱子底部：一眼看出「复习了但答错」的那截 */
.chart-correct {
  width: 100%;
  background: var(--accent);
  border-radius: 2px 2px 0 0;
}

.chart-x {
  font-size: 11px;
  color: var(--muted);
  margin-top: 4px;
}

.legend {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
}

.dot {
  display: inline-block;
  width: 9px;
  height: 9px;
  border-radius: 2px;
}

.dot.correct {
  background: var(--accent);
}

.dot.total {
  background: var(--accent-weak);
  border: 1px solid var(--accent);
}

.module-list {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
}

.module-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2px 8px;
  padding: 6px 0;
  border-top: 1px solid var(--border);
}

.module-row:first-child {
  border-top: none;
}

.module-name {
  font-size: 14px;
  font-weight: 600;
}

.module-bar {
  grid-column: 1 / -1;
  height: 6px;
  background: var(--accent-weak);
  border-radius: 3px;
  overflow: hidden;
}

.module-fill {
  height: 100%;
  background: var(--accent);
}
</style>