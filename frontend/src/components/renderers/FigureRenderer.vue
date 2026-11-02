<script setup lang="ts">
/**
 * FIGURE 题型：服务端下发的 SVG + 笔迹层 + 数值输入框。
 *
 * payload.svg 由服务端按题目参数生成（设计文档 10.3 裁定 3），前端只负责把它塞进 v-html，
 * 不认识任何几何知识；笔迹层服务「在图上画辅助线自己核对」，不参与判题。
 * 统一契约：对外暴露 submit()，返回输入框里的字符串。
 */
import { computed, ref, watch } from 'vue'
import InkCanvas from '../InkCanvas.vue'

const props = defineProps<{ payload: Record<string, unknown> }>()
defineEmits<{ 'submit-request': [] }>()

const answer = ref('')
const ink = ref<InstanceType<typeof InkCanvas> | null>(null)

const svg = computed(() => (typeof props.payload.svg === 'string' ? props.payload.svg : ''))
const hint = computed(() => (typeof props.payload.hint === 'string' ? props.payload.hint : '你的答案'))
const unit = computed(() => (typeof props.payload.unit === 'string' ? props.payload.unit : ''))

function submit(): string {
  return answer.value.trim()
}

function reset(): void {
  answer.value = ''
  ink.value?.clear()
}

/** 笔迹只做记录，随作答一起可取出（判题不使用）。 */
function getStrokes(): unknown {
  return ink.value?.getStrokes() ?? []
}

watch(svg, () => reset())

defineExpose({ submit, reset, getStrokes })
</script>

<template>
  <div class="figure">
    <p v-if="!svg" class="muted">该题缺少图形数据</p>
    <!-- svg 由服务端按题目参数生成，标签文字已做 XML 转义，是受信内容 -->
    <div v-else class="stage" v-html="svg" />

    <InkCanvas ref="ink" :height="200" />

    <label class="field">
      <span>{{ hint }}<template v-if="unit">（单位 {{ unit }}）</template></span>
      <input
        v-model="answer"
        type="text"
        inputmode="decimal"
        placeholder="输入答案后提交"
        @keyup.enter="$emit('submit-request')"
      />
    </label>
  </div>
</template>

<style scoped>
.stage {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 160px;
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  overflow: hidden;
}

.stage :deep(svg) {
  max-width: 100%;
  height: auto;
}

.field {
  display: block;
  margin-top: 14px;
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
