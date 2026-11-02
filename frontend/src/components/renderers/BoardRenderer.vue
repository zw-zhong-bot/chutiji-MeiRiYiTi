<script setup lang="ts">
/**
 * BOARD 题型：9×9 数独棋盘。
 * 统一契约：对外暴露 submit()，返回该题型约定的答案结构；
 * 不认识 HTTP，也不认识模块 —— 只认 payload.size + payload.grid。
 * 提交格式与后端判题一致：按行拼接 81 个数字，空格为 0。
 */
import { computed, ref, watch } from 'vue'

const props = defineProps<{ payload: Record<string, unknown> }>()

type Cell = { value: number; fixed: boolean }

const size = computed(() => Number(props.payload.size) || 9)
const cells = ref<Cell[][]>(buildEmpty())
const cursor = ref<{ row: number; col: number } | null>(null)

function buildEmpty(): Cell[][] {
  return Array.from({ length: 9 }, () =>
    Array.from({ length: 9 }, () => ({ value: 0, fixed: false })),
  )
}

/** 出题响应到达（或换了一道题）时重建棋盘。 */
function load(): void {
  const raw = props.payload.grid as number[][] | undefined
  const next = buildEmpty()
  if (Array.isArray(raw)) {
    for (let row = 0; row < 9 && row < raw.length; row++) {
      const line = raw[row]
      if (!Array.isArray(line)) continue
      for (let col = 0; col < 9 && col < line.length; col++) {
        const value = Number(line[col]) || 0
        next[row][col] = { value, fixed: value !== 0 }
      }
    }
  }
  cells.value = next
  cursor.value = null
}

watch(() => props.payload, load, { immediate: true, deep: true })

function select(row: number, col: number): void {
  cursor.value = { row, col }
}

function move(rowDelta: number, colDelta: number): void {
  const current = cursor.value ?? { row: 0, col: 0 }
  const row = (current.row + rowDelta + size.value) % size.value
  const col = (current.col + colDelta + size.value) % size.value
  cursor.value = { row, col }
}

function type(digit: number): void {
  const current = cursor.value
  if (!current) return
  const cell = cells.value[current.row][current.col]
  if (cell.fixed) return
  cell.value = cell.value === digit ? 0 : digit
}

function clear(): void {
  const current = cursor.value
  if (!current) return
  const cell = cells.value[current.row][current.col]
  if (cell.fixed) return
  cell.value = 0
}

function onKeydown(event: KeyboardEvent): void {
  const key = event.key
  if (key >= '1' && key <= '9') {
    type(Number(key))
  } else if (key === 'Backspace' || key === 'Delete' || key === '0') {
    clear()
  } else if (key === 'ArrowUp') {
    move(-1, 0)
  } else if (key === 'ArrowDown') {
    move(1, 0)
  } else if (key === 'ArrowLeft') {
    move(0, -1)
  } else if (key === 'ArrowRight') {
    move(0, 1)
  } else {
    return
  }
  event.preventDefault()
}

function isActive(row: number, col: number): boolean {
  return cursor.value?.row === row && cursor.value?.col === col
}

/** 供样式使用：宫格粗线画在每 3 行 / 3 列的右边缘与下边缘。 */
function edge(row: number, col: number): string {
  const classes: string[] = []
  if (col % 3 === 2 && col !== 8) classes.push('edge-right')
  if (row % 3 === 2 && row !== 8) classes.push('edge-bottom')
  return classes.join(' ')
}

/** 统一契约：把当前网格拼成 81 位数字串交给调用方。 */
function submit(): string {
  const digits: string[] = []
  for (let row = 0; row < size.value; row++) {
    for (let col = 0; col < size.value; col++) {
      digits.push(String(cells.value[row][col].value))
    }
  }
  return digits.join('')
}

defineExpose({ submit })
</script>

<template>
  <div class="board" tabindex="0" @keydown="onKeydown">
    <div class="grid">
      <template v-for="(line, row) in cells" :key="row">
        <div
          v-for="(cell, col) in line"
          :key="row + '-' + col"
          class="cell"
          :class="[edge(row, col), { fixed: cell.fixed, active: isActive(row, col) }]"
          @click="select(row, col)"
        >
          {{ cell.value === 0 ? '' : cell.value }}
        </div>
      </template>
    </div>
    <p class="hint">点击格子后用数字键 1-9 填入，Backspace 清除，方向键移动</p>
  </div>
</template>

<style scoped>
.board {
  outline: none;
  display: inline-block;
}

.grid {
  display: grid;
  grid-template-columns: repeat(9, 44px);
  grid-template-rows: repeat(9, 44px);
  border: 2px solid var(--border);
  background: #fff;
}

.cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  font-size: 22px;
  color: var(--accent);
  cursor: pointer;
  user-select: none;
}

.cell.edge-right {
  border-right: 2px solid var(--border);
}

.cell.edge-bottom {
  border-bottom: 2px solid var(--border);
}

.cell.fixed {
  color: #111827;
  font-weight: 600;
  cursor: default;
}

.cell.active {
  background: #eef2ff;
  box-shadow: inset 0 0 0 2px var(--accent);
}

.board:focus .cell.active {
  background: #e0e7ff;
}

.hint {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--muted);
}
</style>
