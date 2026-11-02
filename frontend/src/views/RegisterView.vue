<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')
const loading = ref(false)
const notice = ref('')

async function submit(): Promise<void> {
  error.value = ''
  notice.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = '请填写用户名和密码'
    return
  }
  loading.value = true
  try {
    await auth.register(username.value.trim(), password.value, nickname.value.trim())
    notice.value = '注册成功，正在跳转登录页…'
    await router.replace({ name: 'login' })
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="card auth-card">
      <h1>注册账号</h1>
      <p class="muted">用户名 3-64 位，密码至少 6 位</p>

      <form @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <input v-model="username" type="text" autocomplete="username" placeholder="3-64 位" />
        </label>
        <label class="field">
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="new-password"
            placeholder="至少 6 位"
          />
        </label>
        <label class="field">
          <span>昵称（可选）</span>
          <input v-model="nickname" type="text" placeholder="不填则用用户名" />
        </label>

        <p v-if="error" class="error-text">{{ error }}</p>
        <p v-if="notice" class="ok-text">{{ notice }}</p>

        <button type="submit" :disabled="loading">{{ loading ? '注册中…' : '注册' }}</button>
      </form>

      <p class="switch muted">
        已有账号？<RouterLink to="/login">去登录</RouterLink>
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

.ok-text {
  color: var(--success);
  margin: 8px 0 0;
}
</style>
