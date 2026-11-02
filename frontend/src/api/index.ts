import { request } from './client'
import type {
  Difficulty,
  GradeView,
  LoginResponse,
  ModuleMeta,
  QuestionView,
  RecordPage,
  ReviewCard,
  ReviewStartView,
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

  grade: (questionId: string, answer: string, durationMs: number) =>
    request<GradeView>('/questions/' + encodeURIComponent(questionId) + '/grade', {
      method: 'POST',
      body: { answer, durationMs },
    }),
}

export const reviewApi = {
  /** 到期卡片：due=true 的那些。 */
  due: () => request<ReviewCard[]>('/review/due'),

  /** 全部卡片，含未到期，按到期时间升序。 */
  cards: () => request<ReviewCard[]>('/review/cards'),

  /** 用卡片存的难度配置重新出一道新题；卡不存在或不属于当前用户时后端报 2010。 */
  start: (cardId: number) =>
    request<ReviewStartView>('/review/' + encodeURIComponent(String(cardId)) + '/start', {
      method: 'POST',
    }),
}

export const recordApi = {
  list: (page: number, size: number) => request<RecordPage>('/records', { query: { page, size } }),
}

export { ApiError } from './client'
export type * from './types'
