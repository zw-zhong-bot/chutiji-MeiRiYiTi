<script setup lang="ts">
import type { ModuleMeta } from '../api'

/** 模块卡片墙：数据全部来自 GET /api/modules。 */
defineProps<{ modules: ModuleMeta[]; selectedKey: string | null }>()
defineEmits<{ select: [ModuleMeta] }>()
</script>

<template>
  <div class="picker">
    <h2>选择模块</h2>
    <p v-if="modules.length === 0" class="muted">暂无可用模块</p>
    <ul class="grid">
      <li v-for="module in modules" :key="module.key">
        <button
          type="button"
          class="module-card"
          :class="{ active: module.key === selectedKey }"
          @click="$emit('select', module)"
        >
          <span class="name">{{ module.name }}</span>
          <span class="desc muted">{{ module.description }}</span>
          <span class="meta muted">{{ module.type }} · {{ module.timeLimitSec }} 秒</span>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.picker h2 {
  margin: 0 0 14px;
  font-size: 16px;
}

.grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.module-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--text);
  padding: 14px;
  text-align: left;
}

.module-card.active {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-weak);
}

.name {
  font-weight: 600;
}

.desc,
.meta {
  font-size: 13px;
}
</style>
