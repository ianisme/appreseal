<script setup lang="ts">
import AppIcon from './AppIcon.vue'

defineProps<{
  modelValue: string
  label?: string
  placeholder?: string
  type?: string
  error?: string
  hint?: string
  icon?: string
  autocomplete?: string
  disabled?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <label class="field">
    <span v-if="label" class="field__label">{{ label }}</span>
    <div :class="['input-icon', error && 'is-error']">
      <span v-if="icon" class="input-icon__glyph"><AppIcon :name="icon" :size="17" /></span>
      <input
        :type="type || 'text'"
        :value="modelValue"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :disabled="disabled"
        class="input"
        :class="{ 'input--error': !!error, 'has-icon': !!icon }"
        :style="icon ? 'padding-left:40px' : ''"
        @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <span v-if="error" class="field__error">{{ error }}</span>
    <span v-else-if="hint" class="field__hint">{{ hint }}</span>
  </label>
</template>
