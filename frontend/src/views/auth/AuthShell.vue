<script setup lang="ts">
import AppLogo from '@/components/AppLogo.vue'
import AppIcon from '@/components/AppIcon.vue'

defineProps<{ title: string; subtitle: string }>()
</script>

<template>
  <div class="auth">
    <aside class="auth__aside">
      <AppLogo dark />
      <div class="auth__pitch">
        <div class="eyebrow" style="color: #4da3ff">IOS IPA DECRYPTION</div>
        <h1>把加密包<br />变成明文。</h1>
        <p>多 Apple ID 池、离线 FairPlay 解密、cryptid 自动校验。</p>
      </div>

      <div class="mini-term">
        <div class="mini-term__row"><span class="p">$</span> appget get com.example.qingjian</div>
        <div class="mini-term__row dim">→ 下载 268 MB · id-01</div>
        <div class="mini-term__row ok">✔ cryptid 1 → 0 · 2m 41s</div>
      </div>

      <div class="auth__foot">
        <div class="auth__metric"><strong>团队</strong><span>自己或同事</span></div>
        <div class="auth__metric"><strong>不扣费</strong><span>提交和下载</span></div>
        <div class="auth__metric"><strong>虚构</strong><span>演示应用</span></div>
      </div>
    </aside>

    <main class="auth__main">
      <div class="auth__mobile-brand"><AppLogo /></div>
      <div class="auth__box">
        <h2 class="auth__title">{{ title }}</h2>
        <p class="auth__sub">{{ subtitle }}</p>
        <slot />
      </div>
      <div class="auth__legal">
        <AppIcon name="shield" :size="13" /> 仅供学习研究 · 团队自用
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #fff;
}
.auth__aside {
  position: relative;
  background: radial-gradient(120% 90% at 20% 0%, #1d2a3f 0%, #101114 55%, #0c0c0e 100%);
  color: #fff;
  padding: 40px 48px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.auth__aside::after {
  content: "";
  position: absolute;
  width: 620px;
  height: 620px;
  right: -240px;
  bottom: -300px;
  background: radial-gradient(circle, rgba(0, 113, 227, 0.28), transparent 62%);
}
.auth__pitch {
  margin-top: auto;
  position: relative;
}
.auth__pitch h1 {
  font-size: clamp(34px, 3.6vw, 48px);
  line-height: 1.1;
  letter-spacing: -0.035em;
  margin-top: 16px;
}
.auth__pitch p {
  margin-top: 16px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  max-width: 380px;
}
.mini-term {
  margin-top: 34px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 14px 16px;
  font-family: var(--mono);
  font-size: 11.5px;
  line-height: 1.95;
  color: #c7c7cc;
  position: relative;
}
.mini-term .p { color: #4da3ff; }
.mini-term .dim { color: #8e8e93; }
.mini-term .ok { color: #4ade80; }
.auth__foot {
  display: flex;
  gap: 34px;
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  position: relative;
}
.auth__metric {
  display: flex;
  flex-direction: column;
}
.auth__metric strong {
  font-size: 19px;
  letter-spacing: -0.03em;
}
.auth__metric span {
  font-size: 11.5px;
  color: rgba(255, 255, 255, 0.45);
}
.auth__main {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  position: relative;
}
.auth__box {
  width: 100%;
  max-width: 368px;
}
.auth__title {
  font-size: 28px;
  letter-spacing: -0.03em;
}
.auth__sub {
  margin-top: 8px;
  font-size: 13.5px;
  color: var(--text-2);
}
.auth__mobile-brand {
  display: none;
}
.auth__legal {
  position: absolute;
  bottom: 24px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--text-3);
}
@media (max-width: 900px) {
  .auth { grid-template-columns: 1fr; }
  .auth__aside { display: none; }
  .auth__mobile-brand {
    display: block;
    align-self: flex-start;
    margin-bottom: 40px;
  }
  .auth__main { justify-content: flex-start; padding-top: 28px; }
  .auth__legal { position: static; margin-top: 40px; }
}
</style>
