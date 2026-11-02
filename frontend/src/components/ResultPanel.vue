<script setup lang="ts">
import type { GradeView } from '../api'

/** 判题结果：对错、正确答案、解析。 */
defineProps<{ result: GradeView }>()
</script>

<template>
  <div class="result card" :class="result.correct ? 'ok' : 'bad'">
    <p class="verdict">{{ result.correct ? '✓ 答对了' : '✗ 答错了' }}</p>
    <p class="line"><span class="label">正确答案</span>{{ result.correctAnswer }}</p>
    <p v-if="result.explanation" class="line">
      <span class="label">解析</span>{{ result.explanation }}
    </p>
  </div>
</template>

<style scoped>
.result {
  margin-top: 16px;
}

.result.ok {
  border-color: var(--success);
}

.result.bad {
  border-color: var(--danger);
}

.verdict {
  margin: 0 0 8px;
  font-weight: 600;
}

.result.ok .verdict {
  color: var(--success);
}

.result.bad .verdict {
  color: var(--danger);
}

.line {
  margin: 4px 0;
}

.label {
  display: inline-block;
  min-width: 72px;
  color: var(--muted);
  font-size: 14px;
}
</style>
