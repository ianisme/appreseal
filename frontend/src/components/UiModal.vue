<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(
  defineProps<{ open: boolean; title?: string; subtitle?: string; width?: number; closable?: boolean }>(),
  { width: 480, closable: true }
)
const emit = defineEmits<{ close: [] }>()

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.closable) emit('close')
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      document.addEventListener('keydown', onKey)
      document.body.style.overflow = 'hidden'
    } else {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="modal-mask" @click.self="closable && emit('close')">
        <div class="modal" :style="{ maxWidth: width + 'px' }" role="dialog" aria-modal="true">
          <div class="modal__head">
            <div>
              <div class="modal__title">{{ title }}</div>
              <div v-if="subtitle" class="modal__sub">{{ subtitle }}</div>
            </div>
            <button v-if="closable" class="icon-btn" aria-label="关闭" @click="emit('close')">
              <AppIcon name="x" :size="18" />
            </button>
          </div>
          <div class="modal__body"><slot /></div>
          <div v-if="$slots.footer" class="modal__foot"><slot name="footer" /></div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
