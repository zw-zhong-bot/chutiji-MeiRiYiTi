import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/token'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/practice' },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { public: true },
    },
    { path: '/practice', name: 'practice', component: () => import('../views/PracticeView.vue') },
    { path: '/records', name: 'records', component: () => import('../views/RecordsView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/practice' },
  ],
})

/** 未登录访问受保护页 → 登录页（带 redirect 回来）。 */
router.beforeEach((to) => {
  const loggedIn = getToken() !== null
  if (!to.meta.public && !loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && loggedIn) {
    return { name: 'practice' }
  }
  return true
})

export default router
