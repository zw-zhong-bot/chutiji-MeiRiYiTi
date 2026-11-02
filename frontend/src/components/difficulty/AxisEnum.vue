<script setup lang="ts">
import type { DifficultyAxis } from '../../api'

/** kind=ENUM：下拉。候选项全部来自后端下发。 */
defineProps<{ axis: DifficultyAxis; modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <div class="axis">
    <label :for="'axis-' + axis.key">{{ axis.label }}</label>
    <select
      :id="'axis-' + axis.key"
      :value="modelValue"
      @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <option v-for="option in axis.options ?? []" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  </div>
</template>

<style scoped>
.axis {
  margin-bottom: 16px;
}

label {
  display: block;
  font-size: 14px;
  margin-bottom: 6px;
}
</style>
