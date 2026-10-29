<script setup lang="ts">
import AppLogo from '@/components/AppLogo.vue'
import AppIcon from '@/components/AppIcon.vue'
</script>

<template>
  <div class="nf">
    <AppLogo />
    <div class="nf__code">404</div>
    <h1 class="h2">这个页面不存在</h1>
    <p class="muted" style="margin-top: 10px">可能是链接过期，或者 App 已下架。</p>
    <div class="row gap-12" style="margin-top: 26px">
      <router-link to="/" class="btn btn--primary">回到首页</router-link>
      <router-link to="/market" class="btn btn--secondary">
        应用市场 <AppIcon name="arrow-right" :size="15" />
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.nf {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px;
}
.nf__code {
  font-size: 92px;
  font-weight: 800;
  letter-spacing: -0.06em;
  background: linear-gradient(135deg, #0071e3, #5e5ce6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin: 24px 0 6px;
  line-height: 1;
}
</style>
