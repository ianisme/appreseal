<script setup lang="ts">
import AppLogo from './AppLogo.vue'

const groups = [
  { title: '产品', links: [['成品库', '/market'], ['新建任务', '/app/new'], ['文档', '/docs']] },
  { title: '资源', links: [['使用文档', '/docs'], ['常见问题', '/docs#faq'], ['服务状态', '/docs#status']] },
  { title: '条款', links: [['使用说明', '/docs'], ['合规说明', '/docs#compliance']] },
  { title: '关于', links: [['联系支持', 'mailto:support@appreseal.com'], ['合规说明', '/docs#compliance']] }
]
</script>

<template>
  <footer class="footer">
    <div class="container">
      <div class="footer__grid">
        <div class="footer__brand">
          <AppLogo />
          <p>苹果应用脱壳工具。<br />自动下载，自动脱壳。仅供学习研究。</p>
        </div>
        <div v-for="g in groups" :key="g.title" class="footer__group">
          <div class="footer__title">{{ g.title }}</div>
          <router-link v-for="[label, to] in g.links" :key="label" :to="to" class="footer__link">{{ label }}</router-link>
        </div>
      </div>

      <div class="footer__bottom">
        <span>© 2026 AppReseal. 保留所有权利。</span>
        <span class="footer__note">所有脱壳产物仅用于安全研究，请在 24 小时内删除。</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  background: var(--bg-subtle);
  padding: 56px 0 32px;
  border-top: 1px solid var(--line-soft);
}
.footer__grid {
  display: grid;
  grid-template-columns: 1.6fr repeat(4, 1fr);
  gap: 32px;
}
.footer__brand p {
  margin-top: 14px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-3);
}
.footer__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 12px;
}
.footer__link {
  display: block;
  font-size: 12.5px;
  color: var(--text-2);
  padding: 4px 0;
}
.footer__link:hover {
  color: var(--blue);
}
.footer__bottom {
  margin-top: 44px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 11.5px;
  color: var(--text-3);
}
@media (max-width: 900px) {
  .footer__grid {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 560px) {
  .footer__bottom {
    flex-direction: column;
  }
}
</style>
