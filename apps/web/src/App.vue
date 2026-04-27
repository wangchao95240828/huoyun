<script setup lang="ts">
import { AlertTriangle, FileSpreadsheet, ReceiptText, ShieldCheck, WalletCards } from "lucide-vue-next";

const modules = [
  { icon: WalletCards, title: "费率引擎", text: "规则版本、低消、分抛、燃油、附加费叠加/取大。" },
  { icon: ReceiptText, title: "应收账单", text: "客户模板、币种、账单版本、门户下载与核销。" },
  { icon: FileSpreadsheet, title: "成本对账", text: "渠道账单映射、子单匹配、差异队列和申诉状态。" },
  { icon: ShieldCheck, title: "保险", text: "投保 API、电子保单、保费自动入应付。" },
  { icon: AlertTriangle, title: "风险预警", text: "卡派转快递前提示超长/超重和预计附加费。" }
];
</script>

<template>
  <main class="shell">
    <aside class="sidebar">
      <div class="brand">XQT SaaS</div>
      <nav>
        <button class="active">财务驾驶舱</button>
        <button>计费规则</button>
        <button>渠道账单</button>
        <button>客户账单</button>
        <button>利润与提成</button>
      </nav>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div>
          <p>财务模块</p>
          <h1>对账从人工逐票，改成系统比对异常</h1>
        </div>
        <button class="primary">新建账单导入</button>
      </header>

      <section class="metrics">
        <article><span>待对账</span><strong>128</strong></article>
        <article><span>差异金额</span><strong>¥18,420</strong></article>
        <article><span>预估毛利</span><strong>23.6%</strong></article>
        <article><span>异常处理</span><strong>42</strong></article>
      </section>

      <section class="module-grid">
        <article v-for="item in modules" :key="item.title">
          <component :is="item.icon" :size="22" />
          <h2>{{ item.title }}</h2>
          <p>{{ item.text }}</p>
        </article>
      </section>
    </section>
  </main>
</template>
