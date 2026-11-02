<script setup lang="ts">
import { computed } from 'vue'
import type { DifficultyAxis } from '../../api'

/** kind=INT_RANGE：滑块。上下限与步长全部来自后端下发。 */
const props = defineProps<{ axis: DifficultyAxis; modelValue: number }>()
const emit = defineEmits<{ 'update:modelValue': [number] }>()

const min = computed(() => props.axis.min ?? 0)
const max = computed(() => props.axis.max ?? 100)
const step = computed(() => props.axis.step ?? 1)

function update(value: number): void {
  emit('update:modelValue', Number(value))
}
</script>

<template>
  <div class="axis">
    <div class="axis-head">
      <label :for="'axis-' + axis.key">{{ axis.label }}</label>
      <span class="value">{{ modelValue }}</span>
    </div>
    <input
      :id="'axis-' + axis.key"
      type="range"
      :min="min"
      :max="max"
      :step="step"
      :value="modelValue"
      @input="update(($event.target as HTMLInputElement).valueAsNumber)"
    />
  </div>
</template>

<style scoped>
.axis {
  margin-bottom: 16px;
}

.axis-head {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  margin-bottom: 6px;
}

.value {
  color: var(--accent);
  font-variant-numeric: tabular-nums;
}

input[type='range'] {
  width: 100%;
}
</style>
