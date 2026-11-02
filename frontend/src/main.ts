import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { setUnauthorizedHandler } from './api/client'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 任何接口 401：client 已清 token，这里只负责跳登录页。
setUnauthorizedHandler(() => {
  const current = router.currentRoute.value
  if (current.meta.public) return
  void router.push({ name: 'login', query: { redirect: current.fullPath } })
})

app.mount('#app')
