// 유리 카드: 커서(손가락)를 따라 반사광이 움직임
import { motion, type HTMLMotionProps } from "motion/react";
import { forwardRef, type PointerEvent } from "react";

type Props = HTMLMotionProps<"div"> & { strong?: boolean; interactive?: boolean };

function track(e: PointerEvent<HTMLDivElement>) {
  const el = e.currentTarget;
  const r = el.getBoundingClientRect();
  el.style.setProperty("--mx", `${((e.clientX - r.left) / r.width) * 100}%`);
  el.style.setProperty("--my", `${((e.clientY - r.top) / r.height) * 100}%`);
}

export const Glass = forwardRef<HTMLDivElement, Props>(function Glass({ strong, interactive, className = "", onPointerMove, ...rest }, ref) {
  return (
    <motion.div
      ref={ref}
      className={`glass ${strong ? "strong" : ""} ${className}`}
      onPointerMove={(e) => { track(e); onPointerMove?.(e); }}
      whileHover={interactive ? { y: -2 } : undefined}
      whileTap={interactive ? { scale: 0.985 } : undefined}
      transition={{ type: "spring", stiffness: 420, damping: 30 }}
      {...rest}
    />
  );
});

export const fadeUp = {
  initial: { opacity: 0, y: 14, filter: "blur(6px)" },
  animate: { opacity: 1, y: 0, filter: "blur(0px)" },
  transition: { type: "spring" as const, stiffness: 260, damping: 28 },
};
