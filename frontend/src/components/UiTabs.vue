<script setup lang="ts">
defineProps<{ modelValue: string; items: { value: string; label: string; count?: number }[] }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <div class="tabs" role="tablist">
    <button
      v-for="item in items"
      :key="item.value"
      role="tab"
      class="tabs__item"
      :class="{ 'is-active': modelValue === item.value }"
      :aria-selected="modelValue === item.value"
      @click="emit('update:modelValue', item.value)"
    >
      {{ item.label }}
      <span v-if="item.count !== undefined" class="tabs__count">{{ item.count }}</span>
    </button>
  </div>
</template>
