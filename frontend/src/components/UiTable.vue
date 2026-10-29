<script setup lang="ts" generic="T extends Record<string, any>">
import AppIcon from './AppIcon.vue'
import type { TableColumn } from '@/types'

withDefaults(
  defineProps<{
    columns: TableColumn[]
    rows: T[]
    loading?: boolean
    emptyTitle?: string
    emptyDesc?: string
    skeletonRows?: number
  }>(),
  { loading: false, emptyTitle: '暂无数据', emptyDesc: '', skeletonRows: 5 }
)
</script>

<template>
  <div class="table-wrap">
    <table class="table">
      <thead>
        <tr>
          <th
            v-for="col in columns"
            :key="col.key"
            :style="{ width: col.width, textAlign: col.align || 'left' }"
          >
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody v-if="loading">
        <tr v-for="i in skeletonRows" :key="'sk' + i">
          <td v-for="col in columns" :key="col.key">
            <div class="skeleton" style="height: 12px; width: 70%" />
          </td>
        </tr>
      </tbody>
      <tbody v-else-if="rows.length">
        <tr v-for="(row, idx) in rows" :key="row.id ?? idx">
          <td
            v-for="col in columns"
            :key="col.key"
            :style="{ textAlign: col.align || 'left' }"
            :class="[{ num: col.mono }, col.mono && 'mono']"
          >
            <slot :name="`cell-${col.key}`" :row="row" :value="row[col.key]">
              {{ row[col.key] ?? '—' }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="!loading && !rows.length" class="empty">
      <div class="empty__art"><AppIcon name="box" :size="26" /></div>
      <div class="empty__title">{{ emptyTitle }}</div>
      <div v-if="emptyDesc" class="empty__desc">{{ emptyDesc }}</div>
      <slot name="empty-action" />
    </div>
  </div>
</template>
