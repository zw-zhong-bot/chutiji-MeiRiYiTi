<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { Difficulty, DifficultyAxis, ModuleMeta } from '../../api'
import AxisBool from './AxisBool.vue'
import AxisEnum from './AxisEnum.vue'
import AxisRange from './AxisRange.vue'

/**
 * 动态难度面板：完全由后端下发的 axes 驱动。
 * 这里不出现任何模块名或轴 key 的字面量，新增模块前端零改动。
 */
const props = defineProps<{ module: ModuleMeta }>()
const emit = defineEmits<{ change: [Difficulty] }>()

const values = reactive<Record<string, unknown>>({})

/** 按轴的 defaultValue 初始化；模块切换时整体重置。 */
function reset(): void {
  for (const key of Object.keys(values)) delete values[key]
  for (const axis of props.module.axes) {
    values[axis.key] = axis.defaultValue ?? defaultFor(axis)
  }
  emitValue()
}

function defaultFor(axis: DifficultyAxis): unknown {
  if (axis.kind === 'INT_RANGE') return axis.min ?? 0
  if (axis.kind === 'BOOLEAN') return false
  return axis.options?.[0]?.value ?? ''
}

function emitValue(): void {
  emit('change', { ...values })
}

watch(() => props.module, reset, { immediate: true })
</script>

<template>
  <div class="difficulty">
    <h2>难度设置</h2>
    <p v-if="module.axes.length === 0" class="muted">该模块没有可配置的难度项</p>

    <template v-for="axis in module.axes" :key="axis.key">
      <AxisRange
        v-if="axis.kind === 'INT_RANGE'"
        :axis="axis"
        :model-value="values[axis.key] as number"
        @update:model-value="
          (value) => {
            values[axis.key] = value
            emitValue()
          }
        "
      />
      <AxisEnum
        v-else-if="axis.kind === 'ENUM'"
        :axis="axis"
        :model-value="values[axis.key] as string"
        @update:model-value="
          (value) => {
            values[axis.key] = value
            emitValue()
          }
        "
      />
      <AxisBool
        v-else-if="axis.kind === 'BOOLEAN'"
        :axis="axis"
        :model-value="values[axis.key] as boolean"
        @update:model-value="
          (value) => {
            values[axis.key] = value
            emitValue()
          }
        "
      />
    </template>
  </div>
</template>

<style scoped>
.difficulty h2 {
  margin: 0 0 14px;
  font-size: 16px;
}
</style>
