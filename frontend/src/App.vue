<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from './components/NavBar.vue'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()

// 刷新后恢复用户信息；token 失效由 401 处理链路兜底。
onMounted(() => {
  void auth.restore()
})

function handleLogout(): void {
  auth.logout()
  void router.push({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <NavBar v-if="auth.isLoggedIn" @logout="handleLogout" />
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-main {
  flex: 1;
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px 64px;
  box-sizing: border-box;
}
</style>
