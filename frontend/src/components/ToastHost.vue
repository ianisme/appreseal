<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()
const icon = (type: string) => (type === 'success' ? 'check' : type === 'error' ? 'alert' : 'info')
</script>

<template>
  <div class="toast-stack" aria-live="polite">
    <div v-for="t in toast.items" :key="t.id" class="toast" :class="`toast--${t.type}`">
      <span class="toast__icon"><AppIcon :name="icon(t.type)" :size="17" /></span>
      <span>{{ t.message }}</span>
    </div>
  </div>
</template>
