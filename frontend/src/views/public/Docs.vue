<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue'

const toc = [
  { id: 'start', label: '快速开始' },
  { id: 'upload', label: '上传自己的 IPA' },
  { id: 'billing', label: '没有付费' },
  { id: 'errors', label: '常见错误' },
  { id: 'compliance', label: '合规与安全' },
  { id: 'faq', label: 'FAQ' },
  { id: 'status', label: '服务状态' }
]

const errors = [
  { code: 'CREDENTIAL_MISMATCH', title: '凭证与包不匹配', desc: '这个包不是池里的账号下载的。换一个能解开它的凭证，或改从成品库下载。' },
  { code: 'DOWNLOAD_FAILED', title: '下载失败', desc: 'App Store 网络抖动或账号需要重新登录。系统会自动换账号重试，失败后可以再提交。' },
  { code: 'TIMEOUT', title: '任务超时', desc: '超时会自动终止。大包可以重试。' },
  { code: 'INVALID_IPA', title: '包结构异常', desc: '上传的文件不是有效 IPA，或缺 SC_Info / Info.plist。请确认是 App Store 下载的原始包。' }
]
</script>

<template>
  <div class="docs">
    <div class="container docs__grid">
      <aside class="toc">
        <div class="toc__title">文档目录</div>
        <a v-for="t in toc" :key="t.id" :href="`#${t.id}`" class="toc__link">{{ t.label }}</a>
        <div class="toc__card">
          <AppIcon name="help" :size="18" />
          <div>
            <strong>需要帮助？</strong>
            <a href="mailto:support@appreseal.com">support@appreseal.com</a>
          </div>
        </div>
      </aside>

      <article class="doc">
        <div class="eyebrow">DOCUMENTATION</div>
        <h1 class="h1" style="margin-top: 12px; font-size: 40px">使用文档</h1>
        <p class="lead" style="margin-top: 14px">苹果应用脱壳工具。选好应用后自动下载、自动脱壳，做好后下载明文包。</p>

        <section id="start" class="doc__section">
          <h2>快速开始</h2>
          <p>整个流程分三步：选苹果应用、自动下载并脱壳、下载明文包。</p>

          <div class="steps">
            <div class="step">
              <span class="step__n">1</span>
              <div>
                <strong>登录</strong>
                <p>用自己的账号或团队账号登录。不需要充值。</p>
              </div>
            </div>
            <div class="step">
              <span class="step__n">2</span>
              <div>
                <strong>新建脱壳任务</strong>
                <p>搜索 App，或从成品库下载已经整理好的包。</p>
              </div>
            </div>
            <div class="step">
              <span class="step__n">3</span>
              <div>
                <strong>下载产物</strong>
                <p>任务完成后在详情页下载。做好的包可以再下，不另外计费。</p>
              </div>
            </div>
          </div>

          <div class="callout">
            <AppIcon name="info" :size="18" />
            <div>
              <strong>不需要越狱设备</strong>
              <p>平台持有 Apple ID 池与 FairPlay 凭证，下载与解密都在服务端完成。</p>
            </div>
          </div>
        </section>

        <section id="upload" class="doc__section">
          <h2>上传自己的 IPA</h2>
          <p>如果你已经在自己的账号下下载了加密包，可以上传让平台解密：</p>
          <pre class="code"><code>1. 在「新建脱壳」选择「上传 IPA」
2. 拖入 .ipa 文件（≤ 2 GB），等待解析出 bundle id / 版本
3. 提交任务，进入队列
4. 完成后下载明文包</code></pre>
          <p class="dim small">上传的包必须由你有权分析、并且能被池里某个凭证解开。对不上会失败，但不会扣费。</p>
        </section>

        <section id="billing" class="doc__section">
          <h2>没有付费</h2>
          <p>提交任务、取消任务、下载成品都不扣费。没有钱包、充值套餐和订单。排队中和正在处理的任务都可以取消，取消只是停掉这次处理。</p>
        </section>

        <section id="errors" class="doc__section">
          <h2>常见错误</h2>
          <div class="err-list">
            <div v-for="e in errors" :key="e.code" class="err">
              <code class="err__code">{{ e.code }}</code>
              <div>
                <strong>{{ e.title }}</strong>
                <p>{{ e.desc }}</p>
              </div>
            </div>
          </div>
        </section>

        <section id="compliance" class="doc__section">
          <h2>合规与安全</h2>
          <ul class="bullets">
            <li>仅供学习研究。只处理你有权分析的安装包。</li>
            <li>不得用于重新分发、绕过付费或其他违法用途。</li>
            <li>下载链接 15 分钟有效；云端产物保留 30 天，到期自动清理。</li>
            <li>平台不存储你的 Apple ID 密码；账号池凭证与设备隔离保存。</li>
            <li>发现滥用行为（高频批量下载、转售）会冻结账号。</li>
          </ul>
        </section>

        <section id="faq" class="doc__section">
          <h2>FAQ</h2>
          <details class="qa">
            <summary>为什么解密只需要处理 4 KB？</summary>
            <p>App Store 的 IPA 在磁盘上只有 Mach-O 的第一页（cryptsize=4096）是加密的，其余段已由 Apple 处理为可读形式。解开这一页即可让 IDA / Hopper 正常解析符号与字符串。</p>
          </details>
          <details class="qa">
            <summary>产物能直接安装到非越狱 iPhone 吗？</summary>
            <p>不能直接安装。脱壳只移除 FairPlay 加密，签名仍需你自己的开发者证书重签；企业签名或 TrollStore 视设备版本而定。</p>
          </details>
          <details class="qa">
            <summary>可以指定历史版本吗？</summary>
            <p>可以。新建任务时展开「高级选项」填写 version id，平台会尝试下载该版本；Apple 不再提供的版本无法获取。</p>
          </details>
        </section>

        <section id="status" class="doc__section">
          <h2>服务状态</h2>
          <div class="status-card">
            <div class="row gap-10">
              <span class="pill pill--green pill--live"><span class="pill__dot" /> 全部系统正常</span>
              <span class="tiny dim">最后检查：刚刚</span>
            </div>
            <div class="status-grid">
              <div><span>API</span><strong class="ok">正常</strong></div>
              <div><span>任务队列</span><strong class="ok">正常 · 2 个任务</strong></div>
              <div><span>Apple ID 池</span><strong class="ok">3 / 4 可用</strong></div>
              <div><span>对象存储</span><strong class="ok">正常</strong></div>
            </div>
          </div>
        </section>
      </article>
    </div>
  </div>
</template>

<style scoped>
.docs {
  padding: 40px 0 90px;
}
.docs__grid {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 64px;
  align-items: start;
}
.toc {
  position: sticky;
  top: 88px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.toc__title {
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-3);
  margin-bottom: 10px;
}
.toc__link {
  padding: 6px 0;
  font-size: 13px;
  color: var(--text-2);
}
.toc__link:hover {
  color: var(--blue);
}
.toc__card {
  display: flex;
  gap: 10px;
  margin-top: 24px;
  padding: 14px;
  border-radius: var(--r-md);
  background: var(--bg-subtle);
  font-size: 12px;
  color: var(--text-2);
}
.toc__card strong {
  display: block;
  color: var(--text);
  font-size: 12.5px;
  margin-bottom: 2px;
}
.doc {
  max-width: 760px;
}
.doc__section {
  margin-top: 52px;
  scroll-margin-top: 90px;
}
.doc__section h2 {
  font-size: 24px;
  letter-spacing: -0.025em;
  margin-bottom: 14px;
}
.doc__section p {
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.8;
  margin-bottom: 12px;
}
.steps {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 22px 0;
}
.step {
  display: flex;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-md);
}
.step__n {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--blue-soft);
  color: var(--blue);
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  flex: none;
}
.step strong {
  font-size: 14px;
}
.step p {
  margin: 4px 0 0;
  font-size: 12.5px;
}
.callout {
  display: flex;
  gap: 12px;
  padding: 16px 18px;
  border-radius: var(--r-md);
  background: var(--blue-soft);
  color: var(--blue);
  margin-top: 20px;
}
.callout strong {
  font-size: 13.5px;
}
.callout p {
  margin: 3px 0 0;
  font-size: 12.5px;
  color: #2b6cb0;
}
.code {
  background: #141416;
  color: #d7d7dc;
  border-radius: var(--r-md);
  padding: 18px 20px;
  font-size: 12.5px;
  line-height: 1.9;
  overflow-x: auto;
  margin: 16px 0;
}
.err-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  border-top: 1px solid var(--line-soft);
  margin-top: 18px;
}
.err {
  display: grid;
  grid-template-columns: 190px 1fr;
  gap: 20px;
  padding: 16px 0;
  border-bottom: 1px solid var(--line-soft);
}
.err__code {
  font-size: 11.5px;
  color: var(--red);
  background: var(--red-soft);
  padding: 3px 8px;
  border-radius: 6px;
  align-self: start;
  width: fit-content;
}
.err strong {
  font-size: 13.5px;
}
.err p {
  margin: 4px 0 0;
  font-size: 12.5px;
}
.bullets {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}
.bullets li {
  position: relative;
  padding-left: 20px;
  font-size: 13.5px;
  color: var(--text-2);
  line-height: 1.7;
}
.bullets li::before {
  content: "";
  position: absolute;
  left: 4px;
  top: 9px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--blue);
}
.qa {
  border-bottom: 1px solid var(--line-soft);
}
.qa summary {
  padding: 15px 0;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  list-style: none;
}
.qa summary::-webkit-details-marker {
  display: none;
}
.qa p {
  padding-bottom: 14px;
  font-size: 13px;
  line-height: 1.75;
}
.status-card {
  padding: 20px;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-md);
  margin-top: 16px;
}
.status-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px 28px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--line-soft);
}
.status-grid div {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-2);
}
.status-grid .ok {
  color: var(--green);
  font-weight: 500;
}
@media (max-width: 900px) {
  .docs__grid { grid-template-columns: minmax(0, 1fr); gap: 24px; }
  .toc { position: static; flex-direction: row; flex-wrap: wrap; gap: 8px 16px; }
  .toc__title, .toc__card { display: none; }
  .err { grid-template-columns: minmax(0, 1fr); gap: 8px; }
}
</style>
