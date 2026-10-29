<script setup lang="ts">
defineProps<{ page: number; pages: number; total: number }>()
const emit = defineEmits<{ 'update:page': [number] }>()
</script>

<template>
  <div v-if="pages > 1" class="pager">
    <button class="btn btn--secondary btn--sm" type="button" :disabled="page <= 1" @click="emit('update:page', page - 1)">
      上一页
    </button>
    <span class="tiny dim">第 {{ page }} / {{ pages }} 页 · {{ total }} 条</span>
    <button class="btn btn--secondary btn--sm" type="button" :disabled="page >= pages" @click="emit('update:page', page + 1)">
      下一页
    </button>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 18px 16px;
}
</style>
