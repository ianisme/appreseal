<script setup lang="ts">
defineProps<{ modelValue: string; items: { value: string; label: string }[] }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <div class="segmented" role="tablist">
    <button
      v-for="item in items"
      :key="item.value"
      role="tab"
      class="segmented__item"
      :class="{ 'is-active': modelValue === item.value }"
      :aria-selected="modelValue === item.value"
      @click="emit('update:modelValue', item.value)"
    >
      {{ item.label }}
    </button>
  </div>
</template>
