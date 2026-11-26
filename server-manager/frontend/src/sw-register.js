// 홈 화면 앱(PWA) 등록. HTTPS 또는 localhost 에서만 동작 (그 외에는 조용히 건너뜀)
export function registerServiceWorker() {
  if (!("serviceWorker" in navigator) || !window.isSecureContext || import.meta.env.DEV) return;
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch(() => {});
  });
}
