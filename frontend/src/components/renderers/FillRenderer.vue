<script setup lang="ts">
/**
 * FILL 题型：输入框作答。
 * 统一契约：对外暴露 submit()，返回该题型约定的答案结构；
 * 不认识 HTTP，也不认识模块。
 */
import { ref } from 'vue'

defineProps<{ payload: Record<string, unknown> }>()
defineEmits<{ 'submit-request': [] }>()

const answer = ref('')

function submit(): string {
  return answer.value.trim()
}

function reset(): void {
  answer.value = ''
}

defineExpose({ submit, reset })
</script>

<template>
  <div class="fill">
    <label class="field">
      <span>{{ (payload.hint as string) || '你的答案' }}</span>
      <input
        v-model="answer"
        type="text"
        placeholder="输入答案后提交"
        @keyup.enter="$emit('submit-request')"
      />
    </label>
  </div>
</template>

<style scoped>
.field {
  display: block;
}

.field span {
  display: block;
  font-size: 14px;
  color: var(--muted);
  margin-bottom: 6px;
}

input {
  font-size: 18px;
  padding: 10px 12px;
}
</style>