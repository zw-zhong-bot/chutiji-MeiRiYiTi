<script setup lang="ts">
/**
 * 按 type 字段分发到具体渲染器。
 * 新增模块只要 type 落在四类之内，前端零改动。
 */
import { ref } from 'vue'
import type { QuestionType } from '../api'
import BoardRenderer from './renderers/BoardRenderer.vue'
import ChoiceRenderer from './renderers/ChoiceRenderer.vue'
import FigureRenderer from './renderers/FigureRenderer.vue'
import FillRenderer from './renderers/FillRenderer.vue'

defineProps<{ type: QuestionType; payload: Record<string, unknown> }>()
defineEmits<{ 'submit-request': [] }>()

const renderer = ref<{ submit: () => unknown } | null>(null)

/** 把当前渲染器的作答结果交出去，由 PracticeView 统一提交。 */
function collectAnswer(): unknown {
  return renderer.value?.submit() ?? null
}

defineExpose({ collectAnswer })
</script>

<template>
  <div class="renderer-host">
    <FillRenderer
      v-if="type === 'FILL'"
      ref="renderer"
      :payload="payload"
      @submit-request="$emit('submit-request')"
    />
    <ChoiceRenderer v-else-if="type === 'CHOICE'" ref="renderer" :payload="payload" />
    <BoardRenderer v-else-if="type === 'BOARD'" ref="renderer" :payload="payload" />
    <FigureRenderer v-else-if="type === 'FIGURE'" ref="renderer" :payload="payload" />
    <p v-else class="muted">未知题型：{{ type }}</p>
  </div>
</template>

<style scoped>
.renderer-host {
  margin: 16px 0;
}
</style>
