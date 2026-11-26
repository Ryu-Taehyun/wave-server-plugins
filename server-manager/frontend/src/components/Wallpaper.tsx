// 뒤쪽 배경: 천천히 움직이는 색 덩어리 (유리가 비쳐 보이게)
import { motion } from "motion/react";

const BLOBS = [
  { c: "#2f6bff", s: 620, x: "-12%", y: "-18%", d: 26 },
  { c: "#8a3ffc", s: 520, x: "62%", y: "-10%", d: 31 },
  { c: "#00c2a8", s: 480, x: "70%", y: "58%", d: 29 },
  { c: "#ff5e7e", s: 380, x: "-6%", y: "62%", d: 34 },
  { c: "#ffb340", s: 300, x: "38%", y: "38%", d: 38 },
];

export function Wallpaper({ tint }: { tint?: string }) {
  return (
    <div className="wallpaper" aria-hidden>
      {BLOBS.map((b, i) => (
        <motion.div
          key={i}
          className="blob"
          style={{ width: b.s, height: b.s, left: b.x, top: b.y, background: i === 2 && tint ? tint : b.c }}
          animate={{ x: [0, 60, -40, 0], y: [0, -50, 30, 0], scale: [1, 1.08, 0.96, 1] }}
          transition={{ duration: b.d, repeat: Infinity, ease: "easeInOut" }}
        />
      ))}
      <div className="grain" />
    </div>
  );
}
