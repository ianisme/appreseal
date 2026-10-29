import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ToastItem {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

let seq = 0

export const useToastStore = defineStore('toast', () => {
  const items = ref<ToastItem[]>([])

  function push(message: string, type: ToastItem['type'] = 'info', ms = 3200) {
    const id = ++seq
    items.value.push({ id, type, message })
    window.setTimeout(() => {
      items.value = items.value.filter((t) => t.id !== id)
    }, ms)
  }

  return {
    items,
    success: (m: string) => push(m, 'success'),
    error: (m: string) => push(m, 'error', 4200),
    info: (m: string) => push(m, 'info'),
    push
  }
})
