<script setup lang="ts">
import { useAuthStore } from '../stores/auth'

defineEmits<{ logout: [] }>()

const auth = useAuthStore()
</script>

<template>
  <header class="navbar">
    <div class="navbar-inner">
      <RouterLink to="/practice" class="brand">出题机</RouterLink>
      <nav class="links">
        <RouterLink to="/practice" class="link">练习</RouterLink>
        <RouterLink to="/records" class="link">记录</RouterLink>
      </nav>
      <div class="right">
        <span class="who">{{ auth.user?.nickname || auth.user?.username || '已登录' }}</span>
        <button class="btn-ghost" type="button" @click="$emit('logout')">退出</button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  border-bottom: 1px solid var(--border);
  background: var(--surface);
}

.navbar-inner {
  max-width: 960px;
  margin: 0 auto;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand {
  font-weight: 700;
  color: var(--text);
  text-decoration: none;
}

.links {
  display: flex;
  gap: 12px;
}

.link {
  color: var(--muted);
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 6px;
}

.link.router-link-active {
  color: var(--accent);
  background: var(--accent-weak);
}

.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.who {
  color: var(--muted);
  font-size: 14px;
}
</style>
