import { api, buildQuery } from '../api.js';
import { DEFAULT_ROWS, ROW_SIZES, gridColumns, pageSizeForRows } from '../card-pagination.js';
import {
  bindTextureCards,
  mountTextureCards,
  openTextureDetail,
  textureCardHtml,
} from '../components/texture.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

export async function render(root, ctx) {
  const initialRows = Number.parseInt(ctx.query.rows, 10);
  const state = {
    type: ['skin', 'cape'].includes(ctx.query.type) ? ctx.query.type : 'all',
    q: ctx.query.q || '',
    sort: ['latest', 'popular', 'random'].includes(ctx.query.sort) ? ctx.query.sort : 'latest',
    scope: ctx.query.scope === 'mine' && ctx.user ? 'mine' : 'library',
    page: Math.max(1, Number.parseInt(ctx.query.page, 10) || 1),
    rows: ROW_SIZES.includes(initialRows) ? initialRows : DEFAULT_ROWS,
  };
  let lastData = null;
  let deepLinkOpened = false;
  let columns = 1;

  root.innerHTML = `
    <section class="page-head library-head">
      <div>
        <p class="eyebrow">SKIN LIBRARY</p>
        <h1>皮肤库</h1>
        <p>浏览社团成员公开分享的皮肤与披风。点击卡片，在 3D 中旋转查看。</p>
      </div>
      <div class="page-head-actions">
        <a class="btn btn-primary" href="#/upload">${icon('upload')}上传材质</a>
      </div>
    </section>
    <section class="library-layout">
      <div class="toolbar">
        <div class="filter-chips" data-type-filter>
          <button class="chip ${state.type === 'all' ? 'is-active' : ''}" data-type="all">全部</button>
          <button class="chip ${state.type === 'skin' ? 'is-active' : ''}" data-type="skin">皮肤</button>
          <button class="chip ${state.type === 'cape' ? 'is-active' : ''}" data-type="cape">披风</button>
          ${
            ctx.user
              ? `<span class="chip-divider"></span>
                 <button class="chip ${state.scope === 'mine' ? 'is-active' : ''}" data-scope="mine">我的材质</button>`
              : ''
          }
        </div>
        <div class="toolbar-right">
          <label class="search-box">
            ${icon('search')}
            <input type="search" placeholder="搜索材质或上传者" value="${ui.escapeHtml(state.q)}" data-search />
          </label>
          <label class="select-box">
            <select data-sort>
              <option value="latest" ${state.sort === 'latest' ? 'selected' : ''}>最新上传</option>
              <option value="popular" ${state.sort === 'popular' ? 'selected' : ''}>下载最多</option>
              <option value="random" ${state.sort === 'random' ? 'selected' : ''}>随机漫游</option>
            </select>
          </label>
        </div>
      </div>
      <div class="list-meta">
        <span data-count>正在读取星图…</span>
        <span class="list-meta-hint">${icon('sparkles')} 点击材质打开 3D 预览</span>
      </div>
      <div class="skin-grid library-grid" data-grid>
        ${ui.loadingBlock('正在加载皮肤库…')}
      </div>
      <nav class="pagination" data-pager aria-label="分页"></nav>
    </section>
  `;

  const grid = root.querySelector('[data-grid]');
  const countEl = root.querySelector('[data-count]');
  const pager = root.querySelector('[data-pager]');
  const searchInput = root.querySelector('[data-search]');
  const unbindCards = bindTextureCards(grid, (id) => openDetail(id));

  function syncUrl() {
    const query = {
      type: state.type === 'all' ? '' : state.type,
      q: state.q,
      sort: state.sort === 'latest' ? '' : state.sort,
      scope: state.scope === 'mine' ? 'mine' : '',
      page: state.page > 1 ? state.page : '',
      rows: state.rows !== DEFAULT_ROWS ? state.rows : '',
    };
    history.replaceState(null, '', `#/library${buildQuery(query)}`);
  }

  function renderPager(data) {
    const pages = [];
    const current = data.page;
    const total = Math.max(1, data.pages);
    const push = (page) => pages.push(page);
    push(1);
    if (current > 3) pages.push('…');
    for (let page = Math.max(2, current - 1); page <= Math.min(total - 1, current + 1); page += 1) {
      push(page);
    }
    if (current < total - 2) pages.push('…');
    if (total > 1) push(total);

    pager.innerHTML = `
      <button class="pager-btn" data-page="${current - 1}" ${current <= 1 ? 'disabled' : ''} aria-label="上一页">${icon('chevron')}</button>
      ${pages
        .map((page) =>
          page === '…'
            ? '<span class="pager-gap">…</span>'
            : `<button class="pager-btn ${page === current ? 'is-active' : ''}" data-page="${page}">${page}</button>`
        )
        .join('')}
      <button class="pager-btn pager-next" data-page="${current + 1}" ${current >= total ? 'disabled' : ''} aria-label="下一页">${icon('chevron')}</button>
      <label class="pager-size">
        <span>每页</span>
        <select data-page-size aria-label="每页显示行数">
          ${ROW_SIZES.map(
            (value) => `<option value="${value}" ${value === state.rows ? 'selected' : ''}>${value} 行</option>`
          ).join('')}
        </select>
      </label>
    `;
  }

  async function openDetail(id) {
    await openTextureDetail(ctx, id, {
      onChanged: () => load(),
    });
  }

  async function load({ scroll = false } = {}) {
    columns = gridColumns(grid);
    grid.innerHTML = ui.loadingBlock('正在加载皮肤库…');
    pager.innerHTML = '';
    try {
      const data = await api.get('/textures', {
        type: state.type === 'all' ? '' : state.type,
        q: state.q,
        sort: state.sort,
        scope: state.scope === 'mine' ? 'mine' : '',
        page: state.page,
        perPage: pageSizeForRows(state.rows, grid),
      });
      lastData = data;
      if (state.page > data.pages) {
        state.page = data.pages;
        syncUrl();
        return load({ scroll });
      }
      countEl.textContent =
        state.scope === 'mine'
          ? `我的材质共 ${data.total} 件`
          : `共 ${data.total} 件公开材质`;
      if (!data.items.length) {
        grid.innerHTML = ui.emptyState(
          state.scope === 'mine' ? '你还没有上传材质' : '这里还没有材质',
          state.scope === 'mine'
            ? '上传一张 PNG 皮肤或披风，它会出现在你的个人材质库里。'
            : '换一个关键词，或者成为第一个点亮星图的人。',
          '<a class="btn btn-primary" href="#/upload">上传材质</a>'
        );
      } else {
        grid.innerHTML = data.items.map((texture) => textureCardHtml(texture)).join('');
        await mountTextureCards(grid);
      }
      renderPager(data);
      if (scroll) grid.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (error) {
      grid.innerHTML = ui.emptyState('皮肤库加载失败', error.message, '<button class="btn btn-primary" data-retry>重新加载</button>');
      grid.querySelector('[data-retry]')?.addEventListener('click', () => load());
    }
  }

  root.querySelector('[data-type-filter]')?.addEventListener('click', (event) => {
    const typeButton = event.target.closest('[data-type]');
    const scopeButton = event.target.closest('[data-scope]');
    if (typeButton) {
      state.type = typeButton.dataset.type;
      state.page = 1;
      root.querySelectorAll('[data-type]').forEach((button) => {
        button.classList.toggle('is-active', button.dataset.type === state.type);
      });
    } else if (scopeButton) {
      state.scope = state.scope === 'mine' ? 'library' : 'mine';
      state.page = 1;
      scopeButton.classList.toggle('is-active', state.scope === 'mine');
    } else {
      return;
    }
    syncUrl();
    load();
  });

  root.querySelector('[data-sort]')?.addEventListener('change', (event) => {
    state.sort = event.target.value;
    state.page = 1;
    syncUrl();
    load();
  });

  const onSearch = ui.debounce(() => {
    state.q = searchInput.value.trim();
    state.page = 1;
    syncUrl();
    load();
  }, 320);
  searchInput?.addEventListener('input', onSearch);

  pager.addEventListener('click', (event) => {
    const button = event.target.closest('[data-page]');
    if (!button || button.disabled) return;
    const page = Number(button.dataset.page);
    if (!Number.isFinite(page) || page < 1 || (lastData && page > lastData.pages)) return;
    state.page = page;
    syncUrl();
    load({ scroll: true });
  });

  pager.addEventListener('change', (event) => {
    const select = event.target.closest('[data-page-size]');
    if (!select) return;
    const size = Number(select.value);
    if (!ROW_SIZES.includes(size)) return;
    state.rows = size;
    state.page = 1;
    syncUrl();
    load({ scroll: true });
  });

  const onResize = ui.debounce(() => {
    const next = gridColumns(grid);
    if (next === columns) return;
    columns = next;
    load();
  }, 180);
  window.addEventListener('resize', onResize);

  await load();

  const deepLink = Number.parseInt(ctx.query.texture, 10);
  if (deepLink && !deepLinkOpened) {
    deepLinkOpened = true;
    await openDetail(deepLink);
    const query = {
      type: state.type === 'all' ? '' : state.type,
      q: state.q,
      sort: state.sort === 'latest' ? '' : state.sort,
      scope: state.scope === 'mine' ? 'mine' : '',
      page: state.page > 1 ? state.page : '',
      rows: state.rows !== DEFAULT_ROWS ? state.rows : '',
    };
    history.replaceState(null, '', `#/library${buildQuery(query)}`);
  }

  return () => {
    unbindCards();
    window.removeEventListener('resize', onResize);
  };
}
