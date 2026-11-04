import { api } from '../api.js';
import { icon } from '../icons.js';
import { drawCapePreview, drawSkinCard } from '../skin2d.js';
import { SkinViewer } from '../skin3d.js';
import * as ui from '../ui.js';

export function textureCardHtml(texture, { compact = false } = {}) {
  const isCape = texture.type === 'cape';
  return `
    <article class="skin-card ${compact ? 'is-compact' : ''}" data-texture-id="${texture.id}">
      <button class="skin-card-preview ${isCape ? 'is-cape' : ''}" data-open="${texture.id}" aria-label="查看材质：${ui.escapeHtml(
        texture.name
      )}">
        <canvas class="skin-canvas ${isCape ? 'is-cape' : ''}"
          data-skin-url="${ui.escapeHtml(texture.url)}"
          data-skin-model="${ui.escapeHtml(texture.model || 'classic')}"
          data-skin-type="${isCape ? 'cape' : 'skin'}"></canvas>
        ${isCape ? '<span class="corner-badge">披风</span>' : ''}
        <span class="card-hover">查看材质</span>
      </button>
      <div class="skin-card-body">
        <div class="skin-card-title">
          <h3>${ui.escapeHtml(texture.name)}</h3>
          ${texture.isPublic === false ? '<span class="private-badge">私有</span>' : ''}
        </div>
        <p>
          ${ui.escapeHtml(texture.uploader?.username || '未知社员')}
          <span class="dot-sep">·</span>
          ${ui.typeLabel(texture.type)}
          <span class="dot-sep">·</span>
          ${texture.downloads} 次下载
        </p>
      </div>
    </article>
  `;
}

export async function mountTextureCards(root) {
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  const canvases = [...root.querySelectorAll('canvas[data-skin-url]')];
  await Promise.all(
    canvases.map((canvas) => {
      const texture = {
        url: canvas.dataset.skinUrl,
        model: canvas.dataset.skinModel,
        type: canvas.dataset.skinType,
      };
      return texture.type === 'cape'
        ? drawCapePreview(canvas, texture.url)
        : drawSkinCard(canvas, texture);
    })
  );
}

export function bindTextureCards(root, onOpen) {
  const onClick = (event) => {
    const target = event.target.closest('[data-open]');
    if (!target) return;
    onOpen(Number(target.dataset.open));
  };
  const onKeyDown = (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const target = event.target.closest('.skin-card-preview');
    if (!target) return;
    event.preventDefault();
    onOpen(Number(target.dataset.open));
  };
  root.addEventListener('click', onClick);
  root.addEventListener('keydown', onKeyDown);
  return () => {
    root.removeEventListener('click', onClick);
    root.removeEventListener('keydown', onKeyDown);
  };
}

export async function openTextureDetail(ctx, textureId, { onChanged } = {}) {
  let texture;
  try {
    const data = await api.get(`/textures/${textureId}`);
    texture = data.texture;
  } catch (error) {
    ui.toast(error.message, 'error');
    return;
  }

  const container = document.createElement('div');
  container.className = 'texture-detail';
  const isCape = texture.type === 'cape';
  container.innerHTML = `
    <div class="detail-view">
      <canvas class="viewer-canvas" data-viewer></canvas>
      <div class="viewer-hint">拖动旋转 · 滚轮缩放 · 双击复位</div>
      <div class="viewer-tools">
        <button class="btn btn-ghost btn-sm" data-action="reset">复位视角</button>
        <button class="btn btn-ghost btn-sm" data-action="autorotate">暂停旋转</button>
      </div>
    </div>
    <div class="detail-info">
      <div class="detail-eyebrow">
        <span class="type-pill ${isCape ? 'cape' : 'skin'}">${isCape ? '披风' : '皮肤'}材质</span>
        ${texture.isPublic ? '<span class="type-pill muted">公开</span>' : '<span class="type-pill muted">私有</span>'}
      </div>
      <h2>${ui.escapeHtml(texture.name)}</h2>
      <p class="detail-owner">
        ${ui.avatarHtml(texture.uploader?.username || '?', { size: 'xs' })}
        ${ui.escapeHtml(texture.uploader?.username || '未知社员')}
        <span>上传于 ${ui.formatDate(texture.createdAt)}</span>
      </p>
      <dl class="detail-meta">
        <div><dt>模型</dt><dd>${ui.modelLabel(texture.model)}</dd></div>
        <div><dt>尺寸</dt><dd>${texture.width} × ${texture.height}</dd></div>
        <div><dt>下载</dt><dd>${texture.downloads} 次</dd></div>
        <div><dt>材质编号</dt><dd>#${texture.id}</dd></div>
      </dl>
      <div class="detail-actions">
        <button class="btn btn-primary" data-action="equip">${icon('user')}装备到角色</button>
        <button class="btn btn-ghost" data-action="closet">${icon(texture.inCloset ? 'heartFilled' : 'heart')}${
          texture.inCloset ? '已在衣柜' : '收藏到衣柜'
        }</button>
        <a class="btn btn-ghost" href="${texture.downloadUrl}" download data-action="download">${icon(
          'download'
        )}下载 PNG</a>
      </div>
      ${
        texture.isMine || ctx.user?.isAdmin
          ? `<div class="detail-manage">
              <button class="btn btn-ghost btn-sm" data-action="edit">${icon('edit')}编辑信息</button>
              <button class="btn btn-ghost btn-sm danger-text" data-action="delete">${icon(
                'trash'
              )}删除材质</button>
            </div>`
          : ''
      }
    </div>
  `;

  let viewer = null;
  const modal = ui.openModal({
    title: '材质详情',
    content: container,
    size: 'lg',
    onClose: () => viewer?.dispose(),
  });

  const canvas = container.querySelector('[data-viewer]');
  try {
    viewer = new SkinViewer(canvas, {
      skinUrl: isCape ? null : texture.url,
      capeUrl: isCape ? texture.url : null,
      model: texture.model,
      autoRotate: true,
      angle: isCape ? 2.8 : 0.55,
    });
  } catch {
    const fallback = document.createElement('canvas');
    fallback.className = 'viewer-fallback';
    canvas.replaceWith(fallback);
    if (isCape) drawCapePreview(fallback, texture.url);
    else drawSkinCard(fallback, texture);
    fallback.insertAdjacentHTML('afterend', '<p class="viewer-fallback-note">当前浏览器不支持 WebGL，已切换为 2D 预览。</p>');
  }

  container.querySelector('[data-action="reset"]')?.addEventListener('click', () => viewer?.reset());
  const rotateButton = container.querySelector('[data-action="autorotate"]');
  rotateButton?.addEventListener('click', () => {
    if (!viewer) return;
    viewer.autoRotate = !viewer.autoRotate;
    rotateButton.textContent = viewer.autoRotate ? '暂停旋转' : '继续旋转';
  });

  container.querySelector('[data-action="closet"]')?.addEventListener('click', async (event) => {
    if (!ctx.user) {
      ui.toast('请先登录，再收藏材质', 'info');
      modal.close();
      ctx.navigate(`/login?next=${encodeURIComponent('/library')}`);
      return;
    }
    const button = event.currentTarget;
    try {
      if (texture.inCloset) {
        await api.del(`/closet/${texture.id}`);
        texture.inCloset = false;
        button.innerHTML = `${icon('heart')}收藏到衣柜`;
        ui.toast('已从衣柜移除', 'info');
      } else {
        await api.post(`/closet/${texture.id}`);
        texture.inCloset = true;
        button.innerHTML = `${icon('heartFilled')}已在衣柜`;
        ui.toast('已收藏到衣柜', 'success');
      }
    } catch (error) {
      ui.toast(error.message, 'error');
    }
  });

  container.querySelector('[data-action="equip"]')?.addEventListener('click', () => {
    if (!ctx.user) {
      ui.toast('请先登录，再装备材质', 'info');
      modal.close();
      ctx.navigate(`/login?next=${encodeURIComponent('/players')}`);
      return;
    }
    openEquipDialog(ctx, texture);
  });

  container.querySelector('[data-action="edit"]')?.addEventListener('click', () => {
    openEditTextureDialog(ctx, texture, {
      onSaved: (updated) => {
        texture = { ...texture, ...updated };
        container.querySelector('.detail-info h2').textContent = texture.name;
        const visibility = container.querySelector('.detail-eyebrow .type-pill.muted');
        if (visibility) visibility.textContent = texture.isPublic ? '公开' : '私有';
        onChanged?.();
      },
    });
  });

  container.querySelector('[data-action="delete"]')?.addEventListener('click', async () => {
    const confirmed = await ui.confirmDialog({
      title: '删除材质',
      message: `确定删除「${texture.name}」吗？已装备该材质的角色会恢复为默认外观。`,
      confirmText: '删除',
      danger: true,
    });
    if (!confirmed) return;
    try {
      await api.del(`/textures/${texture.id}`);
      ui.toast('材质已删除', 'success');
      modal.close();
      onChanged?.();
    } catch (error) {
      ui.toast(error.message, 'error');
    }
  });

  container.querySelector('[data-action="download"]')?.addEventListener('click', () => {
    ui.toast('开始下载材质', 'info');
  });

  return { modal, texture };
}

export async function openEquipDialog(ctx, texture) {
  let players;
  try {
    const data = await api.get('/players');
    players = data.items;
  } catch (error) {
    ui.toast(error.message, 'error');
    return;
  }

  if (!players.length) {
    const modal = ui.openModal({
      title: '还没有角色',
      content: `
        <div class="empty-state compact">
          <span class="empty-icon">${icon('user')}</span>
          <h3>先创建一个游戏角色</h3>
          <p>角色名会作为你在 Minecraft 服务器中的名字，并据此生成 32 位角色 UUID。</p>
          <a class="btn btn-primary" href="#/players" data-close-link>去创建角色</a>
        </div>
      `,
      size: 'sm',
      actions: [],
    });
    modal.body.querySelector('[data-close-link]')?.addEventListener('click', () => modal.close());
    return;
  }

  const content = document.createElement('div');
  content.className = 'player-pick-list';
  content.innerHTML = players
    .map(
      (player) => `
      <button class="player-pick" data-player="${player.id}">
        <span class="player-pick-avatar">${ui.avatarHtml(player.name, { size: 'sm' })}</span>
        <span class="player-pick-info">
          <strong>${ui.escapeHtml(player.name)}</strong>
          <small>${ui.modelLabel(player.model)}模型 · ${player.skin ? '已装备皮肤' : '尚未装备皮肤'}</small>
        </span>
        ${icon('chevron')}
      </button>
    `
    )
    .join('');

  const modal = ui.openModal({
    title: `装备「${texture.name}」`,
    content,
    size: 'sm',
    actions: [],
  });

  content.addEventListener('click', async (event) => {
    const target = event.target.closest('[data-player]');
    if (!target) return;
    const playerId = Number(target.dataset.player);
    try {
      const body =
        texture.type === 'cape'
          ? { capeTextureId: texture.id }
          : { skinTextureId: texture.id };
      const data = await api.put(`/players/${playerId}/textures`, body);
      ui.toast(`已装备到 ${data.player.name}`, 'success');
      modal.close();
    } catch (error) {
      ui.toast(error.message, 'error');
    }
  });
}

export function openEditTextureDialog(ctx, texture, { onSaved } = {}) {
  const content = document.createElement('div');
  content.innerHTML = `
    <form class="form-stack" data-edit-form>
      <label class="field">
        <span>材质名称</span>
        <input type="text" name="name" maxlength="48" value="${ui.escapeHtml(texture.name)}" required />
      </label>
      <label class="switch-field">
        <span>
          <strong>公开到皮肤库</strong>
          <small>私有材质仅你自己和管理员可见，但仍可用于自己的角色。</small>
        </span>
        <input type="checkbox" name="isPublic" ${texture.isPublic ? 'checked' : ''} />
      </label>
    </form>
  `;
  const modal = ui.openModal({
    title: '编辑材质信息',
    content,
    size: 'sm',
    actions: [
      { label: '取消', className: 'btn-ghost' },
      {
        label: '保存修改',
        className: 'btn-primary',
        onClick: async ({ close, button }) => {
          const form = content.querySelector('form');
          if (!form.reportValidity()) return false;
          const name = form.elements.name.value.trim();
          const isPublic = form.elements.isPublic.checked;
          ui.setButtonLoading(button, true, '保存中…');
          try {
            const data = await api.patch(`/textures/${texture.id}`, { name, isPublic });
            ui.setButtonLoading(button, false);
            ui.toast('材质信息已更新', 'success');
            onSaved?.(data.texture);
            close();
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
  return modal;
}

export async function pickTexture(ctx, { type, currentId = null } = {}) {
  return new Promise(async (resolve) => {
    let resolved = false;
    const finish = (value) => {
      if (resolved) return;
      resolved = true;
      resolve(value);
    };
    const content = document.createElement('div');
    content.className = 'texture-picker';
    content.innerHTML = ui.loadingBlock('正在加载材质库…');
    const modal = ui.openModal({
      title: type === 'cape' ? '选择披风' : '选择皮肤',
      content,
      size: 'lg',
      onClose: () => finish(undefined),
      actions: [
        { label: '取消', className: 'btn-ghost' },
        ...(currentId
          ? [
              {
                label: '清除装备',
                className: 'btn-ghost danger-text',
                onClick: () => finish(null),
              },
            ]
          : []),
      ],
    });

    try {
      const [mine, library] = await Promise.all([
        api.get('/textures', { scope: 'mine', type, perPage: 60, page: 1 }).catch(() => ({ items: [] })),
        api.get('/textures', { type, perPage: 60, page: 1 }).catch(() => ({ items: [] })),
      ]);
      const map = new Map();
      for (const item of [...mine.items, ...library.items]) map.set(item.id, item);
      const items = [...map.values()];

      if (!items.length) {
        content.innerHTML = `
          <div class="empty-state compact">
            <span class="empty-icon">${icon('image')}</span>
            <h3>还没有可用的${type === 'cape' ? '披风' : '皮肤'}</h3>
            <p>上传一张 PNG 材质，或者先去皮肤库看看。</p>
            <a class="btn btn-primary" href="#/upload">上传材质</a>
          </div>
        `;
        content.querySelector('a')?.addEventListener('click', () => modal.close());
        return;
      }

      content.innerHTML = `
        <div class="picker-search">
          ${icon('search')}
          <input type="search" placeholder="搜索材质名称或上传者" data-picker-search />
          <span class="picker-count">${items.length} 件材质</span>
        </div>
        <div class="picker-grid">
          ${items
            .map(
              (texture) => `
              <button class="picker-card ${texture.id === currentId ? 'is-selected' : ''}" data-pick="${texture.id}">
                <canvas data-skin-url="${ui.escapeHtml(texture.url)}"
                  data-skin-model="${ui.escapeHtml(texture.model)}"
                  data-skin-type="${texture.type}"></canvas>
                <span>${ui.escapeHtml(texture.name)}</span>
                <small>${ui.escapeHtml(texture.uploader?.username || '')}${
                  texture.isMine ? ' · 我的' : ''
                }</small>
              </button>
            `
            )
            .join('')}
        </div>
      `;
      await mountTextureCards(content);

      const search = content.querySelector('[data-picker-search]');
      search?.addEventListener('input', () => {
        const value = search.value.trim().toLowerCase();
        for (const card of content.querySelectorAll('.picker-card')) {
          const text = card.textContent.toLowerCase();
          card.hidden = Boolean(value) && !text.includes(value);
        }
      });

      content.addEventListener('click', (event) => {
        const target = event.target.closest('[data-pick]');
        if (!target) return;
        const texture = items.find((item) => item.id === Number(target.dataset.pick));
        finish(texture || null);
        modal.close();
      });
    } catch (error) {
      content.innerHTML = `<div class="empty-state">${ui.escapeHtml(error.message)}</div>`;
    }
  });
}
