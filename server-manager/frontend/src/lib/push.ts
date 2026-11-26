// 웹 푸시 구독 (홈 화면에 추가한 앱 · PC 브라우저). HTTPS 또는 localhost 에서만 동작
import { api } from "./api";

export const pushSupported = () =>
  "serviceWorker" in navigator && "PushManager" in window && "Notification" in window && window.isSecureContext;

function keyBytes(b64: string): Uint8Array {
  const pad = "=".repeat((4 - (b64.length % 4)) % 4);
  const raw = atob((b64 + pad).replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!pushSupported()) return null;
  const reg = await navigator.serviceWorker.getRegistration();
  return (await reg?.pushManager.getSubscription()) ?? null;
}

export async function enablePush(vapidPublic: string): Promise<void> {
  if (!pushSupported()) throw new Error("이 브라우저 · 주소에서는 푸시를 쓸 수 없습니다 (HTTPS 필요 · iPhone 은 홈 화면에 추가한 앱에서)");
  const perm = await Notification.requestPermission();
  if (perm !== "granted") throw new Error("알림 권한이 거부되었습니다 (브라우저 설정에서 허용하세요)");
  const reg = (await navigator.serviceWorker.getRegistration()) ?? (await navigator.serviceWorker.register("/sw.js"));
  await navigator.serviceWorker.ready;
  const sub = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(vapidPublic) as BufferSource }));
  await api.pushSubscribe(sub.toJSON());
}

export async function disablePush(): Promise<void> {
  const sub = await currentSubscription();
  if (!sub) return;
  await api.pushUnsubscribe(sub.toJSON()).catch(() => {});
  await sub.unsubscribe();
}
