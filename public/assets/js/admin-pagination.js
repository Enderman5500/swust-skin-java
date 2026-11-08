import { icon } from './icons.js';

export const PAGE_SIZES = [10, 20, 50, 100];

export function pagerHtml(page, pages, total, pageSize = 10) {
  const totalPages = Math.max(1, Number(pages) || 1);
  const current = Math.max(1, Math.min(totalPages, Number(page) || 1));
  const size = PAGE_SIZES.includes(Number(pageSize)) ? Number(pageSize) : PAGE_SIZES[0];
  return `
    <nav class="pagination admin-pager" data-pager aria-label="分页">
      <button class="pager-btn" data-page="${current - 1}" ${current <= 1 ? 'disabled' : ''} aria-label="上一页">${icon('chevron')}</button>
      <span class="pager-label">第 ${current} / ${totalPages} 页 · 共 ${Number(total) || 0} 条</span>
      <button class="pager-btn pager-next" data-page="${current + 1}" ${current >= totalPages ? 'disabled' : ''} aria-label="下一页">${icon('chevron')}</button>
      <label class="pager-size">
        <span>每页</span>
        <select data-page-size aria-label="每页显示数量">
          ${PAGE_SIZES.map(
            (value) => `<option value="${value}" ${value === size ? 'selected' : ''}>${value}</option>`
          ).join('')}
        </select>
      </label>
    </nav>
  `;
}

export function bindPager(host, onPage, onSize) {
  host.querySelectorAll('.pagination').forEach((pager) => {
    pager.addEventListener('click', (event) => {
      const button = event.target.closest('[data-page]');
      if (!button || button.disabled) return;
      onPage(Number(button.dataset.page));
    });
    pager.querySelector('[data-page-size]')?.addEventListener('change', (event) => {
      const size = Number(event.target.value);
      if (PAGE_SIZES.includes(size)) onSize?.(size);
    });
  });
}
