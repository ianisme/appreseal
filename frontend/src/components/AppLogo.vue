<script setup lang="ts">
withDefaults(defineProps<{ dark?: boolean; compact?: boolean; to?: string }>(), { to: '/' })
</script>

<template>
  <router-link :to="to" class="logo" :class="{ 'logo--dark': dark }" aria-label="AppReseal 首页">
    <span class="logo__mark">
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
        <rect x="4.5" y="10" width="15" height="10.5" rx="2.6" />
        <path d="M8.2 10V7.6a3.8 3.8 0 0 1 7.4-1.2" />
        <circle cx="12" cy="15.2" r="1.15" fill="#fff" stroke="none" />
      </svg>
    </span>
    <span v-if="!compact" class="logo__text">AppReseal</span>
  </router-link>
</template>

<style scoped>
.logo {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--text);
}
.logo:hover {
  color: var(--text);
}
.logo__mark {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: linear-gradient(145deg, #2b8cff 0%, #0062c4 100%);
  box-shadow: 0 4px 12px rgba(0, 113, 227, 0.32), inset 0 1px 0 rgba(255, 255, 255, 0.28);
}
.logo__text {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.025em;
}
.logo--dark {
  color: #fff;
}
.logo--dark:hover {
  color: #fff;
}
</style>
