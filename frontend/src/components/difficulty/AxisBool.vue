<script setup lang="ts">
import type { DifficultyAxis } from '../../api'

/** kind=BOOLEAN：开关。 */
defineProps<{ axis: DifficultyAxis; modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
</script>

<template>
  <div class="axis">
    <label class="row">
      <span>{{ axis.label }}</span>
      <input
        :id="'axis-' + axis.key"
        type="checkbox"
        :checked="modelValue"
        @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
      />
    </label>
  </div>
</template>

<style scoped>
.axis {
  margin-bottom: 12px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  cursor: pointer;
}

input[type='checkbox'] {
  width: 18px;
  height: 18px;
  accent-color: var(--accent);
  cursor: pointer;
}
</style>
