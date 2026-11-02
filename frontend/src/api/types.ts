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
