import { icon } from './icons.js';

export function pagerHtml(page, pages, total) {
  if (!total || pages <= 1) return '';
  const current = Math.max(1, Math.min(pages, page));
  return `
    <nav class="pagination admin-pager" data-pager aria-label="分页">
      <button class="pager-btn" data-page="${current - 1}" ${current <= 1 ? 'disabled' : ''}>${icon('chevron')}</button>
      <span class="pager-label">第 ${current} / ${pages} 页 · 共 ${total} 条</span>
      <button class="pager-btn pager-next" data-page="${current + 1}" ${current >= pages ? 'disabled' : ''}>${icon('chevron')}</button>
    </nav>
  `;
}

export function bindPager(host, handler) {
  host.querySelectorAll('.pagination').forEach((pager) => {
    pager.addEventListener('click', (event) => {
      const button = event.target.closest('[data-page]');
      if (!button || button.disabled) return;
      handler(Number(button.dataset.page));
    });
  });
}
