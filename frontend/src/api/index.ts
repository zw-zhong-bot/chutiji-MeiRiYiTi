import { request } from './client'
import type { Difficulty, GradeView, LoginResponse, ModuleMeta, QuestionView, RecordPage, UserView } from './types'

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

export const recordApi = {
  list: (page: number, size: number) => request<RecordPage>('/records', { query: { page, size } }),
}

export { ApiError } from './client'
export type * from './types'
