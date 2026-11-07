const paths = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M9 21v-6h6v6"/>',
  library:
    '<rect x="3" y="4" width="7" height="16" rx="1.5"/><rect x="14" y="4" width="7" height="16" rx="1.5"/><path d="M6.5 8h0M17.5 8h0"/>',
  user: '<circle cx="12" cy="8" r="3.5"/><path d="M4.5 21c.8-4 3.5-6 7.5-6s6.7 2 7.5 6"/>',
  closet:
    '<path d="M5 8h14l1 13H4L5 8Z"/><path d="M9 8V6a3 3 0 0 1 6 0v2"/><path d="M9.5 13h5"/>',
  upload: '<path d="M12 16V4"/><path d="m7 9 5-5 5 5"/><path d="M4 16v4h16v-4"/>',
  admin:
    '<path d="M12 3 4.5 6v6c0 4.2 3 7.2 7.5 9 4.5-1.8 7.5-4.8 7.5-9V6L12 3Z"/><path d="m9 12 2 2 4-4"/>',
  server:
    '<rect x="3" y="4" width="18" height="6" rx="1.5"/><rect x="3" y="14" width="18" height="6" rx="1.5"/><path d="M7 7h0M7 17h0M11 7h5M11 17h5"/>',
  terminal:
    '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="m7 9 3 3-3 3M13 15h4"/>',
  pause: '<path d="M9 5v14M15 5v14"/>',
  play: '<path d="m8 5 11 7-11 7Z"/>',
  sparkles:
    '<path d="m12 3 1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8L12 3Z"/><path d="m19 15 .8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15Z"/>',
  download: '<path d="M12 4v12"/><path d="m7 11 5 5 5-5"/><path d="M4 20h16"/>',
  heart: '<path d="M12 20s-7.5-4.5-7.5-10A4.5 4.5 0 0 1 12 7a4.5 4.5 0 0 1 7.5 3c0 5.5-7.5 10-7.5 10Z"/>',
  heartFilled: '<path fill="currentColor" stroke="none" d="M12 20s-7.5-4.5-7.5-10A4.5 4.5 0 0 1 12 7a4.5 4.5 0 0 1 7.5 3c0 5.5-7.5 10-7.5 10Z"/>',
  trash:
    '<path d="M4 7h16"/><path d="M9 7V4.5h6V7"/><path d="m6 7 1 13h10l1-13"/><path d="M10 11v5M14 11v5"/>',
  edit: '<path d="m4 20 4.5-1 10-10a2.1 2.1 0 0 0-3-3l-10 10L4 20Z"/><path d="m13.5 7.5 3 3"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  logout: '<path d="M14 4h5v16h-5"/><path d="M10 8l-4 4 4 4"/><path d="M6 12h9"/>',
  settings:
    '<circle cx="12" cy="12" r="3"/><path d="M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.4 1a7 7 0 0 0-1.7-1L14.5 3h-5l-.4 2.6a7 7 0 0 0-1.7 1l-2.4-1-2 3.4L5 10.5a7 7 0 0 0 0 3l-2 1.5 2 3.4 2.4-1a7 7 0 0 0 1.7 1l.4 2.6h5l.4-2.6a7 7 0 0 0 1.7-1l2.4 1 2-3.4-2-1.5c.1-.3.1-.7.1-1Z"/>',
  plugin:
    '<path d="M9 3v4H5v4H3v6h2v4h6v-2h2v2h6v-4h2v-6h-2V7h-4V3h-2v4h-2V3H9Z"/>',
  users:
    '<circle cx="9" cy="8" r="3"/><path d="M3.5 20c.6-3.5 2.8-5.5 5.5-5.5s4.9 2 5.5 5.5"/><circle cx="17" cy="9" r="2.5"/><path d="M15.5 14.5c2.3.2 4.2 2 4.8 5"/>',
  image:
    '<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="8.5" cy="10" r="1.5"/><path d="m4 18 6-6 4 4 2-2 4 4"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15H4V4h11v1"/>',
  check: '<path d="m5 12 4.5 4.5L19 7"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  refresh: '<path d="M20 11a8 8 0 1 0-2.3 6.7"/><path d="M20 4v7h-7"/>',
  arrow: '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
  layers:
    '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 13 9 5 9-5"/><path d="m3 17 9 5 9-5"/>',
  panel: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>',
  key: '<circle cx="8" cy="12" r="3.5"/><path d="M11.5 12H21"/><path d="M17 12v3M20 12v2"/>',
  shield:
    '<path d="M12 3 4.5 6v6c0 4.2 3 7.2 7.5 9 4.5-1.8 7.5-4.8 7.5-9V6L12 3Z"/><path d="M9 12h6"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
  external: '<path d="M14 4h6v6"/><path d="m20 4-9 9"/><path d="M18 14v5H5V6h5"/>',
  star: '<path d="m12 3 2.7 5.7 6.3.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.3-.9L12 3Z"/>',
  moon: '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z"/>',
  eye: '<path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6S2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="2.5"/>',
  eyeOff:
    '<path d="m3 3 18 18"/><path d="M10.6 6.2A10.8 10.8 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-3.3 3.8"/><path d="M6.2 7.4A17 17 0 0 0 2.5 12s3.5 6 9.5 6a10 10 0 0 0 4.1-.9"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>',
};

export function icon(name, className = 'icon') {
  const body = paths[name] || paths.star;
  return `<svg class="${className}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
}
