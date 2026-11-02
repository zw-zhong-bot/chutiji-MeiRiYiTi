import { request } from './client'
import type {
  DailyQuestionView,
  Difficulty,
  GradeRequest,
  GradeView,
  LoginResponse,
  ModuleMeta,
  PoolGenerateView,
  QuestionView,
  RecordPage,
  ReviewCard,
  ReviewStartView,
  ReviewStats,
  UserView,
} from './types'

export const authApi = {
  register: (body: { username: string; password: string; nickname: string }) =>
    request<UserView>('/auth/register', { method: 'POST', body }),

  login: (body: { username: string; password: string }) =>
    request<LoginResponse>('/auth/login', { method: 'POST', body }),

  me: () => request<UserView>('/auth/me'),
}

export const moduleApi = {
  list: () => request<ModuleMeta[]>('/modules'),
}

export const questionApi = {
  create: (moduleKey: string, difficulty: Difficulty) =>
    request<QuestionView>('/questions', { method: 'POST', body: { moduleKey, difficulty } }),

  /** ink 只有 FIGURE 题会带；undefined 时 JSON.stringify 直接丢掉这个键，请求形状与旧版一致。 */
  grade: (questionId: string, answer: string, durationMs: number, ink?: unknown) =>
    request<GradeView>('/questions/' + encodeURIComponent(questionId) + '/grade', {
      method: 'POST',
      body: { answer, durationMs, ink } as GradeRequest,
    }),
}

export const reviewApi = {
  /** 到期卡片：due=true 的那些。 */
  due: () => request<ReviewCard[]>('/review/due'),

  /** 全部卡片，含未到期，按到期时间升序。 */
  cards: () => request<ReviewCard[]>('/review/cards'),

  /** 复习统计：卡片类指标 + 最近 7 天复习量 + 按模块分布，只含当前用户的数据。 */
  stats: () => request<ReviewStats>('/review/stats'),

  /** 用卡片存的难度配置重新出一道新题；卡不存在或不属于当前用户时后端报 2010。 */
  start: (cardId: number) =>
    request<ReviewStartView>('/review/' + encodeURIComponent(String(cardId)) + '/start', {
      method: 'POST',
    }),
}

/** 每日一题：同一天多次调用返回同一道（后端按 user_id + 日期定死）。 */
export const dailyApi = {
  today: () => request<DailyQuestionView>('/daily'),
}

/** 题库跑批：登录即可，用于手动补池。 */
export const poolApi = {
  generate: (moduleKey: string, difficulty: Difficulty, count?: number) =>
    request<PoolGenerateView>('/pool/generate', { method: 'POST', body: { moduleKey, difficulty, count } }),
}

export const recordApi = {
  list: (page: number, size: number) => request<RecordPage>('/records', { query: { page, size } }),
}

export { ApiError } from './client'
export type * from './types'
