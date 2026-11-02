<script setup lang="ts">
/** 选择题（type=CHOICE）占位：分发机制已完整，具体交互留待后续里程碑。 */
defineProps<{ payload: Record<string, unknown> }>()

const displayType = 'CHOICE'

function submit(): string {
  return ''
}

defineExpose({ submit })
</script>

<template>
  <div class="placeholder">
    <p>该题型开发中（{{ displayType }}）</p>
  </div>
</template>

<style scoped>
.placeholder {
  padding: 24px;
  border: 1px dashed var(--border);
  border-radius: var(--radius);
  text-align: center;
  color: var(--muted);
}

.placeholder p {
  margin: 0;
}
</style>
