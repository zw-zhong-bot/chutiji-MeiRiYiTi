/** 与后端 DTO 一一对应的类型定义。 */

export type AxisKind = 'INT_RANGE' | 'ENUM' | 'BOOLEAN'

export interface AxisOption {
  value: string
  label: string
}

/** 难度轴：完全由后端下发，前端不做任何模块相关的硬编码。 */
export interface DifficultyAxis {
  key: string
  label: string
  kind: AxisKind
  min?: number | null
  max?: number | null
  step?: number | null
  defaultValue?: unknown
  options?: AxisOption[] | null
}

export type QuestionType = 'FILL' | 'CHOICE' | 'BOARD' | 'FIGURE'

export interface ModuleMeta {
  key: string
  name: string
  description: string
  type: QuestionType
  axes: DifficultyAxis[]
  poolBased: boolean
  timeLimitSec: number
}

/** 难度取值：键来自 axis.key，值来自控件当前值。 */
export type Difficulty = Record<string, unknown>

export interface UserView {
  userId: number
  username: string
  nickname: string
  createdAt?: string
}

export interface LoginResponse {
  token: string
  expiresAt: string
  user: UserView
}

/** 出题响应。后端保证不下发 answer。 */
export interface QuestionView {
  questionId: string
  moduleKey: string
  type: QuestionType
  prompt: string
  payload: Record<string, unknown>
  timeLimitSec: number
  expiresAt: string
}

/** 判题请求体。ink 是手写笔迹（设计文档 7.4），只留档不参与判分，因此不限定形状。 */
export interface GradeRequest {
  answer: string
  durationMs: number
  ink?: unknown
}

export interface GradeView {
  correct: boolean
  correctAnswer: string
  explanation?: string
}

export interface RecordItem {
  id: number
  questionId: string
  moduleKey: string
  correct: boolean
  durationMs: number
  createdAt: string
}

export interface RecordPage {
  items: RecordItem[]
  page: number
  size: number
  total: number
  totalPages: number
}

/** 复习卡状态：FSRS 四态。 */
export type ReviewState = 'NEW' | 'LEARNING' | 'REVIEW' | 'RELEARNING'

/**
 * 复习卡：调度单元是「模块 + 难度配置」，不是原题。
 * difficulty 是建卡时存下的难度快照，到期时原样用于重新出题。
 */
export interface ReviewCard {
  cardId: number
  moduleKey: string
  difficulty: Difficulty
  knowledgeKey: string
  stability: number
  difficultyFsrs: number
  reps: number
  lapses: number
  state: ReviewState
  dueAt: string
  lastReviewAt?: string | null
  due: boolean
}

/** 按模块的复习分布：卡片数，以及这些卡片累计的复习次数与遗忘次数。 */
export interface ModuleStat {
  moduleKey: string
  cards: number
  reviews: number
  lapses: number
}

/** 单日复习量：correct 是当天 rating > 1 的条数（rating=1 是 Again 即答错）。 */
export interface DayStat {
  date: string
  reviews: number
  correct: number
}

/** 复习统计：全部按当前登录用户过滤，last7Days 固定 7 条（无数据的日期补 0）。 */
export interface ReviewStats {
  totalCards: number
  dueCards: number
  newCards: number
  reviewCards: number
  totalReviews: number
  totalLapses: number
  avgStability: number
  byModule: ModuleStat[]
  last7Days: DayStat[]
}

/**
 * 每日一题：同一天同一用户固定一道题，返回结构与 QuestionView 完全一致。
 * 后端保证不下发 answer。
 */
export type DailyQuestionView = QuestionView

/** 题库跑批结果：generated 是真正入库的条数，skipped 是生成失败被跳过的条数。 */
export interface PoolGenerateView {
  generated: number
  skipped: number
}

/** 开始复习：后端用卡片存的难度配置重新生成一道新题，题型与练习页一致。 */
export interface ReviewStartView {
  cardId: number
  moduleKey: string
  difficulty: Difficulty
  questionId: string
  prompt: string
  payload: Record<string, unknown>
  type: QuestionType
  timeLimitSec: number
  expiresAt: string
}
