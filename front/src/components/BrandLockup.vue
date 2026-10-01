<script setup>
defineProps({ compact: { type: Boolean, default: false }, iconOnly: { type: Boolean, default: false } })
</script>

<template>
  <span class="sethub-brand" :class="{ compact, 'icon-only': iconOnly }">
    <span class="sethub-mark" aria-hidden="true">
      <svg viewBox="0 0 36 36" role="img">
        <path d="M11 10.5h8.2a4.3 4.3 0 0 1 0 8.6h-2.4a4.3 4.3 0 0 0 0 8.6H25" />
        <circle cx="10.5" cy="10.5" r="2.25" />
        <circle cx="25.5" cy="27.5" r="2.25" />
      </svg>
    </span>
    <span v-if="!iconOnly" class="sethub-wordmark"><b>人才管理</b><small>SetHub 旗下产品</small></span>
  </span>
</template>

<style scoped>
.sethub-brand{display:inline-flex;align-items:center;gap:11px;color:#172033}.sethub-mark{display:grid;place-items:center;width:40px;height:40px;flex:none;border-radius:12px;background:linear-gradient(145deg,#5d8dff 0%,#3370ff 55%,#2256d8 100%);box-shadow:0 8px 22px rgba(51,112,255,.26),inset 0 1px 0 rgba(255,255,255,.32)}.sethub-mark svg{width:30px;height:30px}.sethub-mark path{fill:none;stroke:#fff;stroke-width:3;stroke-linecap:round;stroke-linejoin:round}.sethub-mark circle{fill:#fff}.sethub-wordmark b,.sethub-wordmark small{display:block}.sethub-wordmark b{font-family:-apple-system,BlinkMacSystemFont,"SF Pro Display","Microsoft YaHei UI","PingFang SC",sans-serif;font-size:21px;line-height:1.1;letter-spacing:-.5px;font-weight:720}.sethub-wordmark small{margin-top:5px;color:#7d8998;font-size:9px;line-height:1;letter-spacing:1.1px}.sethub-brand.compact{gap:9px}.sethub-brand.compact .sethub-mark{width:35px;height:35px;border-radius:11px}.sethub-brand.compact .sethub-mark svg{width:26px;height:26px}.sethub-brand.compact .sethub-wordmark b{font-size:18px}.sethub-brand.compact .sethub-wordmark small{font-size:8px}
</style>
