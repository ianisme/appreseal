<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(defineProps<{ icon?: string; title: string; desc?: string }>(), { icon: 'box' })
</script>

<template>
  <div class="empty">
    <div class="empty__art"><AppIcon :name="icon" :size="26" /></div>
    <div class="empty__title">{{ title }}</div>
    <div v-if="desc" class="empty__desc">{{ desc }}</div>
    <div v-if="$slots.default" style="margin-top: 10px"><slot /></div>
  </div>
</template>
