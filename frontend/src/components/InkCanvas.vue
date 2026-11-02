<script setup lang="ts">
/**
 * 手写笔迹层：Canvas 2D + PointerEvent（鼠标 / 触控笔 / 手指同一套事件）。
 *
 * 笔迹按「点数组」记录 [{x, y, pressure, t}]，只存不识别（设计文档 10.3 裁定 4）——
 * 它服务几何题里用户自己画辅助线对照答案的用法，因此这里不做任何笔迹识别或形状校正。
 * 坐标一律用 CSS 像素（相对画布左上角），与设备像素比无关，换设备重放也一致。
 */
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'

export interface InkPoint {
  x: number
  y: number
  pressure: number
  t: number
}

export interface InkStroke {
  width: number
  points: InkPoint[]
}

const props = withDefaults(defineProps<{ height?: number; disabled?: boolean }>(), {
  height: 200,
  disabled: false,
})

const canvasEl = ref<HTMLCanvasElement | null>(null)
const strokes = shallowRef<InkStroke[]>([])
const drawing = ref(false)
/** 三档线宽：细 / 中 / 粗。 */
const widths = [1.5, 3, 6]
const lineWidth = ref(widths[1]!)

let ctx: CanvasRenderingContext2D | null = null
let active: InkStroke | null = null
let resizeObserver: ResizeObserver | null = null

function resize(): void {
  const canvas = canvasEl.value
  if (!canvas) return
  const dpr = window.devicePixelRatio || 1
  const width = canvas.clientWidth || 320
  const height = canvas.clientHeight || props.height
  canvas.width = Math.round(width * dpr)
  canvas.height = Math.round(height * dpr)
  ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  redraw()
}

/** 重画全部笔迹：清屏 + 逐笔描边。撤销、清空、改线宽、改尺寸都走这里。 */
function redraw(): void {
  const canvas = canvasEl.value
  if (!canvas || !ctx) return
  const dpr = window.devicePixelRatio || 1
  ctx.clearRect(0, 0, canvas.width / dpr, canvas.height / dpr)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.strokeStyle = '#1f2430'
  for (const stroke of strokes.value) {
    if (stroke.points.length === 0) continue
    ctx.lineWidth = stroke.width
    ctx.beginPath()
    const first = stroke.points[0]!
    if (stroke.points.length === 1) {
      // 单击也要留下一个点
      ctx.arc(first.x, first.y, stroke.width / 2, 0, Math.PI * 2)
      ctx.fillStyle = '#1f2430'
      ctx.fill()
      continue
    }
    ctx.moveTo(first.x, first.y)
    for (let i = 1; i < stroke.points.length; i++) {
      const p = stroke.points[i]!
      ctx.lineTo(p.x, p.y)
    }
    ctx.stroke()
  }
}

function pointFrom(event: PointerEvent): InkPoint {
  const canvas = canvasEl.value!
  const rect = canvas.getBoundingClientRect()
  return {
    x: Math.round((event.clientX - rect.left) * 10) / 10,
    y: Math.round((event.clientY - rect.top) * 10) / 10,
    // 鼠标没有压感，统一给 0.5
    pressure: event.pressure > 0 ? Math.round(event.pressure * 100) / 100 : 0.5,
    t: Math.round(performance.now()),
  }
}

function onPointerDown(event: PointerEvent): void {
  if (props.disabled) return
  if (event.pointerType === 'mouse' && event.button !== 0) return
  const canvas = canvasEl.value
  if (!canvas) return
  event.preventDefault()
  canvas.setPointerCapture(event.pointerId)
  active = { width: lineWidth.value, points: [pointFrom(event)] }
  strokes.value = [...strokes.value, active]
  drawing.value = true
  redraw()
}

function onPointerMove(event: PointerEvent): void {
  if (!drawing.value || !active) return
  event.preventDefault()
  const events = typeof event.getCoalescedEvents === 'function' ? event.getCoalescedEvents() : []
  const samples = events.length > 0 ? events : [event]
  for (const sample of samples) {
    active.points.push(pointFrom(sample))
  }
  // shallowRef 下直接改内部数组不会触发重渲染，重画由我们手动驱动
  redraw()
}

function onPointerUp(event: PointerEvent): void {
  if (!drawing.value) return
  const canvas = canvasEl.value
  if (canvas && canvas.hasPointerCapture(event.pointerId)) {
    canvas.releasePointerCapture(event.pointerId)
  }
  drawing.value = false
  active = null
}

/** 撤销最后一笔。 */
function undo(): void {
  if (strokes.value.length === 0) return
  strokes.value = strokes.value.slice(0, -1)
  redraw()
}

/** 清空全部笔迹。 */
function clear(): void {
  strokes.value = []
  active = null
  drawing.value = false
  redraw()
}

/** 导出笔迹 JSON（点数组），随作答一起提交。 */
function getStrokes(): InkStroke[] {
  return strokes.value.map((stroke) => ({
    width: stroke.width,
    points: stroke.points.map((p) => ({ ...p })),
  }))
}

function setLineWidth(width: number): void {
  lineWidth.value = width
}

onMounted(() => {
  resize()
  if (typeof ResizeObserver !== 'undefined' && canvasEl.value) {
    resizeObserver = new ResizeObserver(() => resize())
    resizeObserver.observe(canvasEl.value)
  } else {
    window.addEventListener('resize', resize)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  window.removeEventListener('resize', resize)
})

defineExpose({ getStrokes, clear, undo, setLineWidth })
</script>

<template>
  <div class="ink">
    <div class="toolbar">
      <span class="hint">可在此画辅助线</span>
      <div class="widths" role="group" aria-label="线宽">
        <button
          v-for="(width, index) in widths"
          :key="width"
          type="button"
          class="swatch"
          :class="{ active: lineWidth === width }"
          :aria-label="'线宽 ' + (index + 1)"
          :aria-pressed="lineWidth === width"
          :title="'线宽 ' + (index + 1)"
          @click="setLineWidth(width)"
        >
          <i :style="{ width: 4 + index * 4 + 'px', height: 4 + index * 4 + 'px' }" />
        </button>
      </div>
      <button type="button" class="btn-ghost" :disabled="strokes.length === 0" @click="undo">撤销</button>
      <button type="button" class="btn-ghost" :disabled="strokes.length === 0" @click="clear">清除</button>
    </div>
    <canvas
      ref="canvasEl"
      class="surface"
      :style="{ height: height + 'px' }"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
      @pointerleave="onPointerUp"
    />
  </div>
</template>

<style scoped>
.ink {
  margin-top: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.toolbar .hint {
  font-size: 13px;
  color: var(--muted);
  margin-right: auto;
}

.toolbar button {
  padding: 4px 12px;
  font-size: 13px;
}

.widths {
  display: flex;
  gap: 4px;
}

.swatch {
  background: var(--surface);
  border: 1px solid var(--border);
  width: 30px;
  height: 26px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.swatch i {
  /* 按钮是 flex 容器，不加 flex: none 的话圆点会被压扁成一条竖线 */
  flex: none;
  display: block;
  background: var(--text);
  border-radius: 50%;
}

.swatch.active {
  border-color: var(--accent);
  background: var(--accent-weak);
  box-shadow: inset 0 0 0 1px var(--accent);
}

.surface {
  display: block;
  width: 100%;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: transparent;
  /* 移动端：禁止手指/pencil 拖动时滚页面，否则画不出线 */
  touch-action: none;
  cursor: crosshair;
}
</style>
