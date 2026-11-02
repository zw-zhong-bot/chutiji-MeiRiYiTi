<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit(): Promise<void> {
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = '请填写用户名和密码'
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/practice'
    await router.replace(redirect)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="card auth-card">
      <h1>登录出题机</h1>
      <p class="muted">登录后开始练习</p>

      <form @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <input v-model="username" type="text" autocomplete="username" placeholder="请输入用户名" />
        </label>
        <label class="field">
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </label>

        <p v-if="error" class="error-text">{{ error }}</p>

        <button type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
      </form>

      <p class="switch muted">
        还没有账号？<RouterLink to="/register">去注册</RouterLink>
      </p>
    </div>
  </section>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding-top: 32px;
}

.auth-card {
  width: 100%;
  max-width: 380px;
  padding: 24px;
}

.auth-card h1 {
  margin: 0 0 4px;
  font-size: 22px;
}

.field {
  display: block;
  margin-bottom: 14px;
}

.field span {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: var(--muted);
}

form button {
  width: 100%;
  margin-top: 8px;
}

.switch {
  margin: 16px 0 0;
  font-size: 14px;
  text-align: center;
}

.switch a {
  color: var(--accent);
}
</style>
