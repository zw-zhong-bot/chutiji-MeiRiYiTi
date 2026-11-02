<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiError, recordApi } from '../api'
import type { RecordPage } from '../api'

const pageSize = 20
const data = ref<RecordPage | null>(null)
const page = ref(0)
const error = ref('')
const loading = ref(false)

const items = computed(() => data.value?.items ?? [])
const totalPages = computed(() => data.value?.totalPages ?? 0)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    data.value = await recordApi.list(page.value, pageSize)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '加载记录失败'
  } finally {
    loading.value = false
  }
}

function go(delta: number): void {
  const next = page.value + delta
  if (next < 0 || (totalPages.value > 0 && next >= totalPages.value)) return
  page.value = next
}

watch(page, () => void load(), { immediate: true })

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <section class="records">
    <h1>做题记录</h1>
    <p v-if="error" class="error-text">{{ error }}</p>
    <p v-if="loading" class="muted">加载中…</p>

    <div v-if="!loading && items.length === 0" class="card muted">还没有做题记录</div>

    <div v-if="items.length > 0" class="card">
      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>模块</th>
            <th>结果</th>
            <th>用时</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td>{{ formatTime(item.createdAt) }}</td>
            <td>{{ item.moduleKey }}</td>
            <td :class="item.correct ? 'ok' : 'bad'">{{ item.correct ? '正确' : '错误' }}</td>
            <td>{{ (item.durationMs / 1000).toFixed(1) }}s</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="totalPages > 1" class="pager">
      <button type="button" class="btn-ghost" :disabled="page <= 0" @click="go(-1)">上一页</button>
      <span class="muted">第 {{ page + 1 }} / {{ totalPages }} 页 · 共 {{ data?.total ?? 0 }} 条</span>
      <button
        type="button"
        class="btn-ghost"
        :disabled="page + 1 >= totalPages"
        @click="go(1)"
      >
        下一页
      </button>
    </div>
  </section>
</template>

<style scoped>
h1 {
  font-size: 20px;
  margin: 0 0 16px;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  text-align: left;
  padding: 8px 6px;
  border-bottom: 1px solid var(--border);
}

th {
  color: var(--muted);
  font-weight: 500;
}

.ok {
  color: var(--success);
}

.bad {
  color: var(--danger);
}

.pager {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}
</style>
