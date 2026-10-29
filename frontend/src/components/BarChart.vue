<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    data: { label: string; value: number }[]
    height?: number
    color?: string
    format?: (v: number) => string
  }>(),
  { height: 160, color: 'var(--blue)' }
)

const max = computed(() => Math.max(1, ...props.data.map((d) => d.value)))
const fmt = (v: number) => (props.format ? props.format(v) : String(v))
</script>

<template>
  <div class="chart">
    <div class="chart__bars" :style="{ height: height + 'px' }">
      <div v-for="(d, i) in data" :key="i" class="chart__col" :title="`${d.label}：${fmt(d.value)}`">
        <div
          class="chart__bar"
          :style="{
            height: Math.max(3, (d.value / max) * 100) + '%',
            background: color,
            opacity: i === data.length - 1 ? 1 : 0.55
          }"
        />
      </div>
    </div>
    <div class="chart__labels">
      <span v-for="(d, i) in data" :key="i" :class="{ 'is-last': i === data.length - 1 }">
        {{ i % 2 === 0 || i === data.length - 1 ? d.label : '' }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.chart {
  width: 100%;
}
.chart__bars {
  display: flex;
  align-items: flex-end;
  gap: 6px;
}
.chart__col {
  flex: 1;
  height: 100%;
  display: flex;
  align-items: flex-end;
  border-radius: 6px 6px 0 0;
  transition: background 0.2s;
}
.chart__col:hover .chart__bar {
  opacity: 1 !important;
  filter: brightness(0.92);
}
.chart__bar {
  width: 100%;
  border-radius: 6px 6px 3px 3px;
  transition: opacity 0.2s;
  min-height: 3px;
}
.chart__labels {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}
.chart__labels span {
  flex: 1;
  text-align: center;
  font-size: 10.5px;
  color: var(--text-3);
  white-space: nowrap;
}
.chart__labels span.is-last {
  color: var(--text);
  font-weight: 600;
}
</style>
