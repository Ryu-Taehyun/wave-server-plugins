import { useEffect, useState } from "react";
import { api, type AlertRow, type Me, type NotifySettings } from "../lib/api";
import { ago, device } from "../lib/format";
import { currentSubscription, disablePush, enablePush, pushSupported } from "../lib/push";
import { Glass, fadeUp } from "../components/Glass";
import { Button, Toggle } from "../components/Controls";

export function NotifyCard({ me, toast }: { me: Me; toast: (ok: boolean, t: string) => void }) {
  const [s, setS] = useState<NotifySettings | null>(null);
  const [hook, setHook] = useState("");
  const [pushOn, setPushOn] = useState(false);
  const [recent, setRecent] = useState<AlertRow[]>([]);
  const admin = me.role === "admin";

  const load = () => {
    api.notify().then(setS).catch(() => {});
    api.alerts().then(setRecent).catch(() => {});
    currentSubscription().then((x) => setPushOn(!!x)).catch(() => {});
  };
  useEffect(load, []);
  const save = async (patch: Partial<NotifySettings> & { discord_webhook?: string }) => {
    try { const r = await api.saveNotify(patch); setS((cur) => (cur ? { ...cur, ...r } : cur)); toast(true, "저장했습니다"); }
    catch (e) { toast(false, (e as Error).message); }
  };
  if (!s) return null;

  return (
    <Glass className="card" {...fadeUp} style={{ gridColumn: "1 / -1" }}>
      <div className="card-h"><div className="card-t">알림</div><Button className="sm" onClick={async () => { await api.testNotify(); toast(true, "테스트 알림을 보냈습니다"); }}>테스트 보내기</Button></div>
      <div className="grid cols-2" style={{ gap: 18 }}>
        <div>
          <div className="list">
            <div className="list-item">
              <div className="grow"><b>이 기기로 푸시 알림</b>
                <div className="faint" style={{ fontSize: 12.5 }}>{pushSupported() ? "휴대폰은 홈 화면에 추가한 앱에서 켜세요" : "HTTPS 주소(또는 localhost)에서만 쓸 수 있습니다"}</div></div>
              <Toggle on={pushOn} disabled={!pushSupported() || !s.vapid_public} onChange={async (v) => {
                try { if (v) await enablePush(s.vapid_public!); else await disablePush(); setPushOn(v); toast(true, v ? "푸시 알림을 켰습니다" : "푸시 알림을 껐습니다"); load(); }
                catch (e) { toast(false, (e as Error).message); }
              }} />
            </div>
            {s.my_devices.length > 0 && <div className="faint" style={{ fontSize: 12.5, padding: "4px 4px 8px" }}>푸시 받는 내 기기: {s.my_devices.map((d) => device(d.user_agent)).join(", ")}</div>}
            <div className="list-item" style={{ display: "block" }}>
              <b>디스코드 채널</b> <span className="faint" style={{ fontSize: 12.5 }}>{s.discord_set ? `연결됨 (${s.discord_hint})` : "연결 안 됨"}</span>
              {admin && (
                <div className="row" style={{ marginTop: 8 }}>
                  <input className="field" style={{ height: 40 }} placeholder="웹훅 주소 (채널 설정 → 연동 → 웹후크)" value={hook} onChange={(e) => setHook(e.target.value)} />
                  <Button className="sm" onClick={() => { save({ discord_webhook: hook.trim() }); setHook(""); }}>{hook.trim() ? "저장" : "끊기"}</Button>
                </div>
              )}
            </div>
          </div>
          <div className="sep" />
          <b>기준</b>
          {([["tps_below", "TPS 가 이 아래면", ""], ["tps_minutes", "그 상태로 몇 분", "분"], ["down_minutes", "응답 없음 몇 분 뒤", "분"], ["disk_below_gb", "디스크가 이 아래면", "GB"]] as const).map(([k, l, u]) => (
            <div key={k} className="list-item" style={{ padding: "8px 4px" }}>
              <span className="grow">{l}</span>
              <input className="field tnum" style={{ width: 80, height: 36, textAlign: "right" }} defaultValue={s[k]} disabled={!admin}
                onBlur={(e) => { const v = Number(e.target.value); if (!Number.isNaN(v) && v !== s[k]) save({ [k]: v }); }} />
              <span className="faint" style={{ width: 24 }}>{u}</span>
            </div>
          ))}
        </div>
        <div>
          <b>보낼 알림</b>
          <div className="list">
            {Object.entries(s.kinds).map(([k, label]) => (
              <div key={k} className="list-item" style={{ padding: "8px 4px" }}>
                <span className="grow">{label}</span>
                <Toggle on={!!s.events[k]} disabled={!admin} onChange={(v) => save({ events: { ...s.events, [k]: v } })} />
              </div>
            ))}
          </div>
          {recent.length > 0 && (
            <>
              <div className="sep" />
              <b>최근 알림</b>
              {recent.slice(0, 5).map((a) => <div key={a.id} className="faint" style={{ fontSize: 12.5, padding: "3px 0" }}>{ago(a.ts)} · {a.title}</div>)}
            </>
          )}
        </div>
      </div>
    </Glass>
  );
}
