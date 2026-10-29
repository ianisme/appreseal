<script setup lang="ts">
import { useRouter } from 'vue-router'

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'dark' | 'danger'
    size?: 'sm' | 'md' | 'lg'
    loading?: boolean
    disabled?: boolean
    block?: boolean
    type?: 'button' | 'submit'
    /** 传入后渲染成真正的链接，不要再包进 router-link。 */
    to?: string
  }>(),
  { variant: 'secondary', size: 'md', loading: false, disabled: false, block: false, type: 'button' }
)

const router = useRouter()

function follow(e: MouseEvent) {
  if (!props.to) return
  if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
  e.preventDefault()
  router.push(props.to).then((failure) => {
    if (failure) window.location.assign(props.to!)
  }).catch(() => {
    window.location.assign(props.to!)
  })
}
</script>

<template>
  <a
    v-if="to"
    v-bind="$attrs"
    :href="to"
    class="btn"
    :class="[`btn--${props.variant}`, props.size !== 'md' && `btn--${props.size}`, props.block && 'btn--block']"
    @click="follow"
  >
    <slot />
  </a>
  <button
    v-else
    v-bind="$attrs"
    :type="type"
    class="btn"
    :class="[`btn--${variant}`, size !== 'md' && `btn--${size}`, block && 'btn--block']"
    :disabled="disabled || loading"
  >
    <span v-if="loading" class="btn__spinner" />
    <slot />
  </button>
</template>
