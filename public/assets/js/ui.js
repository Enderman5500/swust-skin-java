import { icon } from './icons.js';

const modalStack = [];
let previousBodyOverflow = null;
const MAX_TOASTS = 5;
const activeToasts = [];

export function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function formatDate(value, { time = false } = {}) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  const options = time
    ? { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }
    : { year: 'numeric', month: '2-digit', day: '2-digit' };
  return new Intl.DateTimeFormat('zh-CN', options).format(date).replace(/\//g, '.');
}

export function timeAgo(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
  if (seconds < 60) return '刚刚';
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟前`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小时前`;
  if (seconds < 86400 * 30) return `${Math.floor(seconds / 86400)} 天前`;
  return formatDate(value);
}

export function debounce(fn, wait = 250) {
  let timer = null;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  };
}

function captureToastPositions() {
  const positions = new Map();
  for (const record of activeToasts) {
    if (!record.element.isConnected || record.element.classList.contains('is-leaving')) continue;
    positions.set(record.element, record.element.getBoundingClientRect().top);
  }
  return positions;
}

function animateToastShift(positions) {
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
  for (const [element, oldTop] of positions) {
    if (!element.isConnected || element.classList.contains('is-leaving')) continue;
    const delta = oldTop - element.getBoundingClientRect().top;
    if (Math.abs(delta) < 1) continue;
    element.animate(
      [{ transform: `translateY(${delta}px)` }, { transform: 'translateY(0)' }],
      { duration: 260, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)' }
    );
  }
}

export function toast(message, type = 'info', timeout = 3600) {
  const root = document.getElementById('toast-root');
  if (!root) return;
  const element = document.createElement('div');
  element.className = `toast toast-${type}`;
  const badge = document.createElement('span');
  badge.className = 'toast-icon';
  badge.innerHTML = icon(type === 'success' ? 'check' : type === 'error' ? 'close' : 'sparkles');
  const text = document.createElement('span');
  text.className = 'toast-text';
  text.textContent = String(message);
  element.append(badge, text);

  while (activeToasts.length >= MAX_TOASTS) {
    activeToasts.shift()?.dismiss();
  }

  const previousPositions = captureToastPositions();
  const record = { element, dismiss: null };
  activeToasts.push(record);
  root.append(element);
  animateToastShift(previousPositions);

  requestAnimationFrame(() => element.classList.add('is-visible'));
  let dismissed = false;
  const dismiss = () => {
    if (dismissed) return;
    dismissed = true;
    const index = activeToasts.indexOf(record);
    if (index !== -1) activeToasts.splice(index, 1);
    element.classList.add('is-leaving');
    element.classList.remove('is-visible');
    setTimeout(() => {
      const positions = captureToastPositions();
      element.remove();
      animateToastShift(positions);
    }, 260);
  };
  record.dismiss = dismiss;
  element.addEventListener('click', dismiss);
  setTimeout(dismiss, timeout);
}

export function openModal({ title, content, actions = [], size = 'md', onClose }) {
  const root = document.getElementById('modal-root');
  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.innerHTML = `
    <div class="modal modal-${size}" role="dialog" aria-modal="true" aria-label="${escapeHtml(
      title
    )}">
      <header class="modal-head">
        <h2>${escapeHtml(title)}</h2>
        <button class="icon-btn" data-modal-close aria-label="关闭">${icon('close')}</button>
      </header>
      <div class="modal-body"></div>
      <footer class="modal-foot"></footer>
    </div>
  `;
  const body = overlay.querySelector('.modal-body');
  if (content instanceof Node) body.append(content);
  else body.innerHTML = content || '';
  const footer = overlay.querySelector('.modal-foot');
  const buttons = [];

  let closed = false;
  let controller = null;
  const close = () => {
    if (closed) return;
    closed = true;
    overlay.classList.add('is-closing');
    overlay.classList.remove('is-open');
    document.removeEventListener('keydown', onKeyDown);
    setTimeout(() => overlay.remove(), 320);
    const index = modalStack.indexOf(controller);
    if (index !== -1) modalStack.splice(index, 1);
    if (modalStack.length === 0) {
      document.body.style.overflow = previousBodyOverflow ?? '';
      previousBodyOverflow = null;
    }
    onClose?.();
  };

  for (const action of actions) {
    const button = document.createElement('button');
    button.className = `btn ${action.className || 'btn-ghost'}`;
    button.textContent = action.label;
    button.type = 'button';
    button.addEventListener('click', async () => {
      if (!action.onClick) {
        close();
        return;
      }
      const result = await action.onClick({ close, button });
      if (result !== false) close();
    });
    footer.append(button);
    buttons.push(button);
  }
  if (!actions.length) footer.remove();

  const onKeyDown = (event) => {
    if (event.key !== 'Escape') return;
    if (modalStack[modalStack.length - 1]?.overlay !== overlay) return;
    close();
  };
  overlay.querySelector('[data-modal-close]').addEventListener('click', close);
  overlay.addEventListener('mousedown', (event) => {
    if (event.target === overlay) close();
  });
  document.addEventListener('keydown', onKeyDown);

  if (modalStack.length === 0) {
    previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
  }
  controller = { overlay, close };
  modalStack.push(controller);

  root.append(overlay);
  requestAnimationFrame(() => overlay.classList.add('is-open'));
  const focusTarget =
    body.querySelector('input, textarea, select, button') || buttons[0] || overlay;
  setTimeout(() => focusTarget?.focus?.(), 30);

  return { overlay, close, body, footer };
}

export function confirmDialog({
  title = '确认操作',
  message = '',
  confirmText = '确定',
  cancelText = '取消',
  danger = false,
} = {}) {
  return new Promise((resolve) => {
    let resolved = false;
    const done = (value) => {
      if (resolved) return;
      resolved = true;
      resolve(value);
    };
    openModal({
      title,
      content: `<p class="confirm-message">${escapeHtml(message)}</p>`,
      size: 'sm',
      onClose: () => done(false),
      actions: [
        { label: cancelText, className: 'btn-ghost', onClick: () => done(false) },
        {
          label: confirmText,
          className: danger ? 'btn-danger' : 'btn-primary',
          onClick: () => done(true),
        },
      ],
    });
  });
}

export async function copyText(value, successMessage = '已复制') {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(String(value));
      toast(successMessage, 'success');
      return true;
    }
  } catch {
    // fall through to legacy fallback
  }
  const textarea = document.createElement('textarea');
  textarea.value = String(value);
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.append(textarea);
  textarea.select();
  let ok = false;
  try {
    ok = document.execCommand('copy');
  } catch {
    ok = false;
  }
  textarea.remove();
  toast(ok ? successMessage : '复制失败，请手动选择', ok ? 'success' : 'error');
  return ok;
}

const AVATAR_COLORS = [
  ['#35597a', '#6b9fc9'],
  ['#3f5f4f', '#8ab08f'],
  ['#5b466f', '#a98fc4'],
  ['#6b4a3a', '#c99a78'],
  ['#4b5168', '#8f9bc2'],
  ['#6d5a2e', '#c9ad6a'],
];

export function avatarStyle(username) {
  const text = String(username || 'star');
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  const [from, to] = AVATAR_COLORS[hash % AVATAR_COLORS.length];
  return `background: linear-gradient(135deg, ${from}, ${to});`;
}

export function avatarHtml(username, { size = 'md', avatar = null } = {}) {
  if (avatar) {
    return `<span class="avatar avatar-${size} avatar-image" aria-hidden="true"><img src="${escapeHtml(
      avatar
    )}" alt="" loading="lazy" /></span>`;
  }
  const letter = String(username || '?').slice(0, 1).toUpperCase();
  return `<span class="avatar avatar-${size}" style="${avatarStyle(username)}" aria-hidden="true">${escapeHtml(
    letter
  )}</span>`;
}

export function spinner(label = '加载中') {
  return `<span class="spinner" role="status" aria-label="${escapeHtml(label)}"></span>`;
}

export function loadingBlock(label = '正在加载…') {
  return `<div class="loading-block">${spinner()}<span>${escapeHtml(label)}</span></div>`;
}

export function emptyState(title, text = '', actionHtml = '') {
  return `
    <div class="empty-state">
      <span class="empty-icon">${icon('moon')}</span>
      <h3>${escapeHtml(title)}</h3>
      ${text ? `<p>${escapeHtml(text)}</p>` : ''}
      ${actionHtml}
    </div>
  `;
}

export function setButtonLoading(button, loading, label = '处理中…') {
  if (!button) return;
  if (loading) {
    button.dataset.originalText = button.textContent;
    button.disabled = true;
    button.innerHTML = `${spinner()} ${escapeHtml(label)}`;
  } else {
    button.disabled = false;
    if (button.dataset.originalText) button.textContent = button.dataset.originalText;
  }
}

export function modelLabel(model) {
  return model === 'slim' ? '纤细' : '经典';
}

export function typeLabel(type) {
  return type === 'cape' ? '披风' : '皮肤';
}
