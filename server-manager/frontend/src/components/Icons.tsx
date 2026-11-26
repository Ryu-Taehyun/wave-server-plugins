// SF Symbols 느낌의 선 아이콘 (직접 그림)
import type { SVGProps } from "react";

type P = SVGProps<SVGSVGElement> & { size?: number };
const base = (size = 22): SVGProps<SVGSVGElement> => ({
  width: size, height: size, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor",
  strokeWidth: 1.9, strokeLinecap: "round", strokeLinejoin: "round",
});

export const I = {
  home: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M3.5 10.5 12 3.8l8.5 6.7" /><path d="M5.5 9.2V19a1.5 1.5 0 0 0 1.5 1.5h3.5v-5.5h3V20.5H17a1.5 1.5 0 0 0 1.5-1.5V9.2" /></svg>,
  terminal: ({ size, ...p }: P) => <svg {...base(size)} {...p}><rect x="3" y="4.5" width="18" height="15" rx="3.5" /><path d="m7.5 9.5 3 2.5-3 2.5M12.5 15h4" /></svg>,
  people: ({ size, ...p }: P) => <svg {...base(size)} {...p}><circle cx="9" cy="8.5" r="3.3" /><path d="M3.5 19.5c.6-3.2 2.9-5 5.5-5s4.9 1.8 5.5 5" /><circle cx="17" cy="9.5" r="2.6" /><path d="M16.2 14.6c2.2.1 3.9 1.6 4.3 4.4" /></svg>,
  clock: ({ size, ...p }: P) => <svg {...base(size)} {...p}><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></svg>,
  gear: ({ size, ...p }: P) => <svg {...base(size)} {...p}><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z" /></svg>,
  shield: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M12 3.5 5 6v5.5c0 4.3 2.9 7.7 7 9 4.1-1.3 7-4.7 7-9V6l-7-2.5Z" /><path d="m9 12 2 2 4-4" /></svg>,
  power: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M12 3.5v8" /><path d="M7.1 6.6a7.5 7.5 0 1 0 9.8 0" /></svg>,
  play: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M8 5.5v13l10.5-6.5L8 5.5Z" fill="currentColor" stroke="none" /></svg>,
  stop: ({ size, ...p }: P) => <svg {...base(size)} {...p}><rect x="6.5" y="6.5" width="11" height="11" rx="2.5" fill="currentColor" stroke="none" /></svg>,
  restart: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M20 12a8 8 0 1 1-2.6-5.9" /><path d="M20 4.5v5h-5" /></svg>,
  send: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M12 19V5M6 11l6-6 6 6" /></svg>,
  plus: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M12 5v14M5 12h14" /></svg>,
  trash: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M4.5 7h15M9.5 7V5h5v2M6.5 7l.8 12a1.5 1.5 0 0 0 1.5 1.4h6.4a1.5 1.5 0 0 0 1.5-1.4l.8-12" /></svg>,
  logout: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M14.5 4.5H18a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1-1.5 1.5h-3.5" /><path d="M10 16.5 5.5 12 10 7.5M5.5 12h10" /></svg>,
  bolt: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M13 3 5 13.5h6L10 21l8-10.5h-6L13 3Z" /></svg>,
  wifi: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M3 9a13 13 0 0 1 18 0M6 12.5a8.5 8.5 0 0 1 12 0M9 16a4 4 0 0 1 6 0" /><circle cx="12" cy="19.2" r=".8" fill="currentColor" /></svg>,
  cpu: ({ size, ...p }: P) => <svg {...base(size)} {...p}><rect x="6" y="6" width="12" height="12" rx="2.5" /><path d="M9.5 2.5v3M14.5 2.5v3M9.5 18.5v3M14.5 18.5v3M2.5 9.5h3M2.5 14.5h3M18.5 9.5h3M18.5 14.5h3" /></svg>,
  memory: ({ size, ...p }: P) => <svg {...base(size)} {...p}><rect x="3" y="7" width="18" height="10" rx="2" /><path d="M7 10.5v3M11 10.5v3M15 10.5v3M7 17v2.5M12 17v2.5M17 17v2.5" /></svg>,
  timer: ({ size, ...p }: P) => <svg {...base(size)} {...p}><circle cx="12" cy="13" r="7.5" /><path d="M12 9v4l2.5 1.5M9.5 2.5h5" /></svg>,
  device: ({ size, ...p }: P) => <svg {...base(size)} {...p}><rect x="7" y="2.5" width="10" height="19" rx="2.8" /><path d="M10.5 18.5h3" /></svg>,
  key: ({ size, ...p }: P) => <svg {...base(size)} {...p}><circle cx="8" cy="15" r="4" /><path d="m11 12 8.5-8.5M16.5 6.5 19 9M14.5 8.5 16.5 10.5" /></svg>,
  search: ({ size, ...p }: P) => <svg {...base(size)} {...p}><circle cx="11" cy="11" r="6.5" /><path d="m20 20-4.2-4.2" /></svg>,
  check: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="m5 12.5 4.5 4.5L19 7.5" /></svg>,
  xmark: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M6 6l12 12M18 6 6 18" /></svg>,
  cube: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M12 2.8 20 7.2v9.6L12 21.2 4 16.8V7.2L12 2.8Z" /><path d="M4 7.2 12 11.6l8-4.4M12 11.6v9.6" /></svg>,
  megaphone: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M4 10v4a1 1 0 0 0 1 1h2l6 4V5L7 9H5a1 1 0 0 0-1 1Z" /><path d="M17 9a4 4 0 0 1 0 6" /></svg>,
  chat: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M20.5 11.5c0 4.1-3.8 7.5-8.5 7.5-1.2 0-2.3-.2-3.3-.6L4 19.5l1.2-3.6c-1.1-1.3-1.7-2.8-1.7-4.4 0-4.1 3.8-7.5 8.5-7.5s8.5 3.4 8.5 7.5Z" /></svg>,
  flag: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M5.5 21V4M5.5 4.5c4-2 7 2 11.5 0v9c-4.5 2-7.5-2-11.5 0" /></svg>,
  download: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M12 4v11M7 10.5l5 5 5-5M5 19.5h14" /></svg>,
  undo: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M9 14.5 4.5 10 9 5.5" /><path d="M4.5 10H14a5.5 5.5 0 0 1 0 11h-3" /></svg>,
  list: ({ size, ...p }: P) => <svg {...base(size)} {...p}><path d="M9 6.5h11M9 12h11M9 17.5h11" /><circle cx="4.5" cy="6.5" r="1" fill="currentColor" /><circle cx="4.5" cy="12" r="1" fill="currentColor" /><circle cx="4.5" cy="17.5" r="1" fill="currentColor" /></svg>,
};
