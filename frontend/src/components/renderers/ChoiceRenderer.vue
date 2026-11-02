<script setup lang="ts">
/**
 * CHOICE 题型：单选项作答。
 * 统一契约：对外暴露 submit()，返回选中项的值（如 "A"）；
 * 不认识 HTTP，也不认识模块 —— 只认 payload.options。
 */
import { computed, ref } from 'vue'

const props = defineProps<{ payload: Record<string, unknown> }>()
const emit = defineEmits<{ 'submit-request': [] }>()

const selected = ref<number | null>(null)

/**
 * 后端下发的选项形如 ["A. 张三", "B. 李四", ...]。
 * 取首个字母作为提交值，与后端的 correctOption（"A"/"B"/...）对齐。
 */
const options = computed<string[]>(() => {
  const raw = props.payload.options
  return Array.isArray(raw) ? raw.map((item) => String(item)) : []
})

function optionKey(label: string): string {
  const match = /^\s*([A-Za-z])[.、．)）]/.exec(label)
  return match ? match[1].toUpperCase() : ''
}

function pick(index: number): void {
  selected.value = selected.value === index ? null : index
}

function submit(): string {
  if (selected.value === null) return ''
  return optionKey(options.value[selected.value] ?? '')
}

function reset(): void {
  selected.value = null
}

defineExpose({ submit, reset })
</script>

<template>
  <div class="choice">
    <p v-if="options.length === 0" class="muted">这道题没有提供选项</p>
    <ul v-else class="options">
      <li v-for="(label, index) in options" :key="index">
        <button
          type="button"
          class="option"
          :class="{ picked: selected === index }"
          :aria-pressed="selected === index"
          @click="pick(index)"
        >
          {{ label }}
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.options {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option {
  width: 100%;
  text-align: left;
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.option:hover {
  border-color: var(--accent);
}

.option.picked {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, #fff);
  font-weight: 600;
}
</style>
