<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ value: number; tone?: 'blue' | 'green' | 'red'; striped?: boolean; label?: string }>(),
  { tone: 'blue' }
)

const pct = computed(() => Math.max(0, Math.min(100, Math.round(props.value))))
</script>

<template>
  <div class="stack gap-6" style="width: 100%">
    <div v-if="label" class="spread tiny dim">
      <span>{{ label }}</span>
      <span class="mono">{{ pct }}%</span>
    </div>
    <div class="progress" role="progressbar" :aria-valuenow="pct" aria-valuemin="0" aria-valuemax="100">
      <div
        class="progress__bar"
        :class="[`progress__bar--${tone}`, striped && pct < 100 && 'progress__bar--striped']"
        :style="{ width: pct + '%' }"
      />
    </div>
  </div>
</template>
