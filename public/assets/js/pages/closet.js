import { api, buildQuery } from '../api.js';
import {
  mountTextureCards,
  openEquipDialog,
  openTextureDetail,
} from '../components/texture.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

export async function render(root, ctx) {
  const state = {
    type: ['skin', 'cape'].includes(ctx.query.type) ? ctx.query.type : 'all',
    q: ctx.query.q || '',
    page: Math.max(1, Number.parseInt(ctx.query.page, 10) || 1),
  };
  let items = [];
  let itemsById = new Map();

  root.innerHTML = `
    <section class="page-head">
      <div>
        <p class="eyebrow">MY CLOSET</p>
        <h1>我的衣柜</h1>
        <p>收藏的外观都在这里。给它们取个名字，随时装到角色身上。</p>
      </div>
      <div class="page-head-actions">
        <a class="btn btn-primary" href="#/library">${icon('library')}去皮肤库逛逛</a>
      </div>
    </section>
    <div class="toolbar closet-toolbar">
      <div class="filter-chips" data-type-filter>
        <button class="chip ${state.type === 'all' ? 'is-active' : ''}" data-type="all">全部</button>
        <button class="chip ${state.type === 'skin' ? 'is-active' : ''}" data-type="skin">皮肤</button>
        <button class="chip ${state.type === 'cape' ? 'is-active' : ''}" data-type="cape">披风</button>
      </div>
      <label class="search-box">
        ${icon('search')}
        <input type="search" placeholder="搜索衣柜" value="${ui.escapeHtml(state.q)}" data-search />
      </label>
    </div>
    <div class="list-meta"><span data-count>正在打开衣柜…</span></div>
    <section class="skin-grid closet-grid" data-grid>${ui.loadingBlock('正在打开衣柜…')}</section>
    <nav class="pagination" data-pager></nav>
  `;

  const grid = root.querySelector('[data-grid]');
  const countEl = root.querySelector('[data-count]');
  const pager = root.querySelector('[data-pager]');
  const search = root.querySelector('[data-search]');

  function syncUrl() {
    history.replaceState(
      null,
      '',
      `#/closet${buildQuery({
        type: state.type === 'all' ? '' : state.type,
        q: state.q,
        page: state.page > 1 ? state.page : '',
      })}`
    );
  }

  function cardHtml(texture) {
    return `
      <article class="skin-card closet-card" data-texture-id="${texture.id}">
        <button class="skin-card-preview ${texture.type === 'cape' ? 'is-cape' : ''}" data-open="${texture.id}" aria-label="查看材质">
          <canvas class="skin-canvas ${texture.type === 'cape' ? 'is-cape' : ''}"
            data-skin-url="${ui.escapeHtml(texture.url)}"
            data-skin-model="${ui.escapeHtml(texture.model)}"
            data-skin-type="${texture.type}"></canvas>
          ${texture.type === 'cape' ? '<span class="corner-badge">披风</span>' : ''}
          <span class="card-hover">查看材质</span>
        </button>
        <div class="skin-card-body">
          <div class="skin-card-title">
            <h3>${ui.escapeHtml(texture.closetName || texture.name)}</h3>
            <span class="type-tag">${ui.typeLabel(texture.type)}</span>
          </div>
          <p>
            ${texture.closetName ? `原名：${ui.escapeHtml(texture.name)}` : ui.escapeHtml(texture.uploader?.username || '')}
            <span class="dot-sep">·</span>
            ${ui.formatDate(texture.collectedAt || texture.createdAt)}
          </p>
          <div class="closet-actions">
            <button class="btn btn-primary btn-sm" data-action="equip" data-id="${texture.id}">${icon('user')}装备</button>
            <button class="btn btn-ghost btn-sm" data-action="rename" data-id="${texture.id}">${icon('edit')}重命名</button>
            <a class="btn btn-ghost btn-sm" href="${texture.downloadUrl}" download>${icon('download')}下载</a>
            <button class="btn btn-ghost btn-sm danger-text" data-action="remove" data-id="${texture.id}">${icon('trash')}移除</button>
          </div>
        </div>
      </article>
    `;
  }

  function renderPager(data) {
    if (data.pages <= 1) {
      pager.innerHTML = '';
      return;
    }
    pager.innerHTML = `
      <button class="pager-btn" data-page="${data.page - 1}" ${data.page <= 1 ? 'disabled' : ''}>${icon('chevron')}</button>
      <span class="pager-label">${data.page} / ${data.pages}</span>
      <button class="pager-btn pager-next" data-page="${data.page + 1}" ${data.page >= data.pages ? 'disabled' : ''}>${icon('chevron')}</button>
    `;
  }

  async function load() {
    grid.innerHTML = ui.loadingBlock('正在打开衣柜…');
    try {
      const data = await api.get('/closet', {
        type: state.type === 'all' ? '' : state.type,
        q: state.q,
        page: state.page,
        perPage: 24,
      });
      if (state.page > data.pages) {
        state.page = data.pages;
        syncUrl();
        return load();
      }
      items = data.items;
      itemsById = new Map(items.map((item) => [item.id, item]));
      countEl.textContent = `衣柜里共有 ${data.total} 件材质`;
      if (!items.length) {
        grid.innerHTML = ui.emptyState(
          '衣柜还是空的',
          '在皮肤库点击心形按钮，就能把喜欢的皮肤或披风收进衣柜。',
          '<a class="btn btn-primary" href="#/library">去皮肤库看看</a>'
        );
      } else {
        grid.innerHTML = items.map(cardHtml).join('');
        await mountTextureCards(grid);
      }
      renderPager(data);
    } catch (error) {
      grid.innerHTML = ui.emptyState('衣柜加载失败', error.message, '<button class="btn btn-primary" data-action="retry">重试</button>');
    }
  }

  async function openDetail(id) {
    await openTextureDetail(ctx, id, { onChanged: () => load() });
  }

  function openRename(texture) {
    const content = document.createElement('div');
    content.innerHTML = `
      <form class="form-stack">
        <label class="field">
          <span>衣柜里的名称</span>
          <input type="text" name="name" maxlength="48" value="${ui.escapeHtml(texture.closetName || texture.name)}" placeholder="例如 社团活动外观" />
          <small class="field-hint">留空会恢复使用材质原名称。</small>
        </label>
      </form>
    `;
    ui.openModal({
      title: '重命名收藏',
      content,
      size: 'sm',
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: '保存',
          className: 'btn-primary',
          onClick: async ({ close, button }) => {
            const name = content.querySelector('input[name="name"]').value.trim();
            ui.setButtonLoading(button, true, '保存中…');
            try {
              await api.patch(`/closet/${texture.id}`, { name });
              ui.setButtonLoading(button, false);
              ui.toast('衣柜名称已更新', 'success');
              close();
              await load();
            } catch (error) {
              ui.setButtonLoading(button, false);
              ui.toast(error.message, 'error');
              return false;
            }
            return true;
          },
        },
      ],
    });
  }

  root.querySelector('[data-type-filter]')?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-type]');
    if (!button) return;
    state.type = button.dataset.type;
    state.page = 1;
    root.querySelectorAll('[data-type]').forEach((item) => {
      item.classList.toggle('is-active', item.dataset.type === state.type);
    });
    syncUrl();
    load();
  });

  search?.addEventListener(
    'input',
    ui.debounce(() => {
      state.q = search.value.trim();
      state.page = 1;
      syncUrl();
      load();
    }, 320)
  );

  pager.addEventListener('click', (event) => {
    const button = event.target.closest('[data-page]');
    if (!button || button.disabled) return;
    state.page = Number(button.dataset.page);
    syncUrl();
    load();
  });

  grid.addEventListener('click', async (event) => {
    const actionButton = event.target.closest('[data-action]');
    if (actionButton) {
      event.stopPropagation();
      if (actionButton.dataset.action === 'retry') {
        load();
        return;
      }
      const texture = itemsById.get(Number(actionButton.dataset.id));
      if (!texture) return;
      if (actionButton.dataset.action === 'equip') {
        openEquipDialog(ctx, texture);
      } else if (actionButton.dataset.action === 'rename') {
        openRename(texture);
      } else if (actionButton.dataset.action === 'remove') {
        const confirmed = await ui.confirmDialog({
          title: '移出衣柜',
          message: `确定把「${texture.closetName || texture.name}」移出衣柜吗？材质本身不会被删除。`,
          confirmText: '移出衣柜',
          danger: true,
        });
        if (!confirmed) return;
        try {
          await api.del(`/closet/${texture.id}`);
          ui.toast('已移出衣柜', 'success');
          await load();
        } catch (error) {
          ui.toast(error.message, 'error');
        }
      } else if (actionButton.dataset.action === 'retry') {
        load();
      }
      return;
    }
    const preview = event.target.closest('[data-open]');
    if (preview) openDetail(Number(preview.dataset.open));
  });

  await load();
}
