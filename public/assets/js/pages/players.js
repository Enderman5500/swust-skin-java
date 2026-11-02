import { api } from '../api.js';
import { mountTextureCards, pickTexture } from '../components/texture.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

function playerPreviewHtml(player) {
  return `
    <div class="player-preview">
      <canvas class="player-skin-canvas"
        data-skin-url="${ui.escapeHtml(player.skin?.url || '')}"
        data-skin-model="${ui.escapeHtml(player.skin?.model || player.model)}"
        data-skin-type="skin"></canvas>
      ${
        player.cape
          ? `<canvas class="player-cape-canvas"
              data-skin-url="${ui.escapeHtml(player.cape.url)}"
              data-skin-type="cape"></canvas>`
          : ''
      }
      <span class="player-model-badge">${ui.modelLabel(player.model)}</span>
      ${!player.skin ? '<span class="player-empty-label">尚未装备皮肤</span>' : ''}
    </div>
  `;
}

function playerCardHtml(player) {
  return `
    <article class="player-card" data-player="${player.id}">
      ${playerPreviewHtml(player)}
      <div class="player-card-body">
        <div class="player-card-head">
          <div>
            <h3>${ui.escapeHtml(player.name)}</h3>
            <button class="uuid-copy" data-copy-uuid="${player.uuid}" title="复制 UUID">
              ${icon('copy')}${player.uuid.slice(0, 8)}…
            </button>
          </div>
          <span class="role-chip">角色</span>
        </div>
        <p class="player-meta">
          ${player.skin ? ui.escapeHtml(player.skin.name) : '默认外观'}
          <span class="dot-sep">·</span>
          ${player.cape ? `披风「${ui.escapeHtml(player.cape.name)}」` : '无披风'}
        </p>
        <div class="player-actions">
          <button class="btn btn-primary btn-sm" data-action="appearance" data-player-id="${player.id}">${icon(
            'closet'
          )}更换外观</button>
          <button class="btn btn-ghost btn-sm" data-action="rename" data-player-id="${player.id}">${icon(
            'edit'
          )}重命名</button>
          <button class="btn btn-ghost btn-sm danger-text" data-action="delete" data-player-id="${player.id}">${icon(
            'trash'
          )}删除</button>
        </div>
      </div>
    </article>
  `;
}

export async function render(root, ctx) {
  let players = [];
  let playersById = new Map();
  let limit = 10;

  root.innerHTML = `
    <section class="page-head">
      <div>
        <p class="eyebrow">MY CHARACTERS</p>
        <h1>我的角色</h1>
        <p>角色名就是你在 Minecraft 服务器中的名字。一个账号可以拥有多个角色。</p>
      </div>
      <div class="page-head-actions">
        <span class="count-badge" data-player-count>— / 10</span>
        <button class="btn btn-primary" data-action="create" disabled>${icon('plus')}创建角色</button>
      </div>
    </section>
    <div class="info-strip">
      ${icon('shield')}
      <p>角色 UUID 由本站命名空间与角色名生成（32 位十六进制）。重命名会保留原 UUID，便于服务器继续识别同一玩家。</p>
    </div>
    <section class="player-grid" data-players>
      ${ui.loadingBlock('正在读取角色档案…')}
    </section>
  `;

  const grid = root.querySelector('[data-players]');
  const countBadge = root.querySelector('[data-player-count]');
  const createButton = root.querySelector('[data-action="create"]');

  async function load() {
    grid.innerHTML = ui.loadingBlock('正在读取角色档案…');
    try {
      const data = await api.get('/players');
      players = data.items;
      playersById = new Map(players.map((player) => [player.id, player]));
      limit = data.limit || 10;
      countBadge.textContent = `${players.length} / ${limit}`;
      createButton.disabled = !data.canCreate;
      createButton.title = data.canCreate ? '' : `每位社员最多 ${limit} 个角色`;
      if (!players.length) {
        grid.innerHTML = `
          <div class="empty-state player-empty">
            <span class="empty-icon">${icon('user')}</span>
            <h3>还没有属于你的角色</h3>
            <p>创建一个角色，然后为它挑选皮肤和披风。角色名需要与服务器中的 ID 一致。</p>
            <button class="btn btn-primary" data-action="create">${icon('plus')}创建第一个角色</button>
          </div>
        `;
      } else {
        grid.innerHTML = players.map(playerCardHtml).join('');
        await mountTextureCards(grid);
      }
    } catch (error) {
      grid.innerHTML = ui.emptyState('角色加载失败', error.message, '<button class="btn btn-primary" data-action="retry">重试</button>');
    }
  }

  function openCreate() {
    const content = document.createElement('div');
    content.innerHTML = `
      <form class="form-stack" data-create-form>
        <label class="field">
          <span>角色名</span>
          <input type="text" name="name" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]{3,16}" placeholder="例如 Stargazer" autocomplete="off" />
          <small class="field-hint">3–16 位字母、数字或下划线，需与游戏内 ID 一致。</small>
        </label>
        <div class="field">
          <span>角色模型</span>
          <div class="model-options">
            <label class="model-option">
              <input type="radio" name="model" value="classic" checked />
              <span class="model-option-body">
                <strong>经典</strong>
                <small>Steve / Alex 身形，4 像素手臂</small>
              </span>
            </label>
            <label class="model-option">
              <input type="radio" name="model" value="slim" />
              <span class="model-option-body">
                <strong>纤细</strong>
                <small>Alex 身形，3 像素手臂</small>
              </span>
            </label>
          </div>
        </div>
      </form>
    `;
    const modal = ui.openModal({
      title: '创建角色',
      content,
      size: 'sm',
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: '创建角色',
          className: 'btn-primary',
          onClick: async ({ close, button }) => {
            const form = content.querySelector('form');
            if (!form.reportValidity()) return false;
            const name = form.elements.name.value.trim();
            const model = form.elements.model.value;
            ui.setButtonLoading(button, true, '创建中…');
            try {
              await api.post('/players', { name, model });
              ui.setButtonLoading(button, false);
              ui.toast(`角色 ${name} 已创建`, 'success');
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
    setTimeout(() => content.querySelector('input[name="name"]')?.focus(), 40);
    return modal;
  }

  function openRename(player) {
    const content = document.createElement('div');
    content.innerHTML = `
      <form class="form-stack">
        <label class="field">
          <span>新的角色名</span>
          <input type="text" name="name" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]{3,16}" value="${ui.escapeHtml(
            player.name
          )}" />
          <small class="field-hint">重命名会保留原 UUID，服务器的玩家数据不会因改名失效。</small>
        </label>
      </form>
    `;
    ui.openModal({
      title: '重命名角色',
      content,
      size: 'sm',
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: '保存',
          className: 'btn-primary',
          onClick: async ({ close, button }) => {
            const form = content.querySelector('form');
            if (!form.reportValidity()) return false;
            const name = form.elements.name.value.trim();
            ui.setButtonLoading(button, true, '保存中…');
            try {
              await api.patch(`/players/${player.id}`, { name });
              ui.setButtonLoading(button, false);
              ui.toast('角色名已更新', 'success');
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

  async function openAppearance(player) {
    let current = player;
    const content = document.createElement('div');
    content.className = 'appearance-dialog';

    const modal = ui.openModal({
      title: `${player.name} 的外观`,
      content,
      size: 'md',
      onClose: () => load(),
      actions: [{ label: '完成', className: 'btn-primary' }],
    });

    async function renderContent() {
      content.innerHTML = `
        <div class="appearance-row">
          <div class="appearance-preview">
            <canvas class="appearance-skin-canvas"
              data-skin-url="${ui.escapeHtml(current.skin?.url || '')}"
              data-skin-model="${ui.escapeHtml(current.skin?.model || current.model)}"
              data-skin-type="skin"></canvas>
          </div>
          <div class="appearance-info">
            <span class="eyebrow">SKIN</span>
            <h3>${current.skin ? ui.escapeHtml(current.skin.name) : '默认皮肤'}</h3>
            <p>${current.skin ? `${ui.modelLabel(current.skin.model)}模型` : '还没有为角色装备皮肤。'}</p>
            <div class="appearance-actions">
              <button class="btn btn-primary btn-sm" data-choose="skin">${icon('edit')}选择皮肤</button>
              ${current.skin ? '<button class="btn btn-ghost btn-sm" data-clear="skin">清除</button>' : ''}
            </div>
          </div>
        </div>
        <div class="appearance-row">
          <div class="appearance-preview cape-preview">
            ${
              current.cape
                ? `<canvas class="appearance-cape-canvas"
                     data-skin-url="${ui.escapeHtml(current.cape.url)}"
                     data-skin-type="cape"></canvas>`
                : `<span class="appearance-empty">${icon('closet')}</span>`
            }
          </div>
          <div class="appearance-info">
            <span class="eyebrow">CAPE</span>
            <h3>${current.cape ? ui.escapeHtml(current.cape.name) : '没有披风'}</h3>
            <p>${current.cape ? '已装备的披风会显示在角色背后。' : '为角色挑一件披风，让背影也有辨识度。'}</p>
            <div class="appearance-actions">
              <button class="btn btn-primary btn-sm" data-choose="cape">${icon('closet')}选择披风</button>
              ${current.cape ? '<button class="btn btn-ghost btn-sm" data-clear="cape">清除</button>' : ''}
            </div>
          </div>
        </div>
      `;
      await mountTextureCards(content);
    }

    content.addEventListener('click', async (event) => {
      const choose = event.target.closest('[data-choose]');
      const clear = event.target.closest('[data-clear]');
      if (choose) {
        const type = choose.dataset.choose;
        const picked = await pickTexture(ctx, {
          type,
          currentId: type === 'skin' ? current.skin?.id : current.cape?.id,
        });
        if (picked === undefined) return;
        try {
          const body =
            type === 'skin'
              ? { skinTextureId: picked ? picked.id : null }
              : { capeTextureId: picked ? picked.id : null };
          const data = await api.put(`/players/${current.id}/textures`, body);
          current = data.player;
          ui.toast(picked ? `已更换${type === 'skin' ? '皮肤' : '披风'}` : '已清除装备', 'success');
          await renderContent();
        } catch (error) {
          ui.toast(error.message, 'error');
        }
      } else if (clear) {
        const type = clear.dataset.clear;
        try {
          const body =
            type === 'skin' ? { skinTextureId: null } : { capeTextureId: null };
          const data = await api.put(`/players/${current.id}/textures`, body);
          current = data.player;
          ui.toast(type === 'skin' ? '已清除皮肤' : '已清除披风', 'info');
          await renderContent();
        } catch (error) {
          ui.toast(error.message, 'error');
        }
      }
    });

    await renderContent();
    return modal;
  }

  const handleRootClick = async (event) => {
    const actionButton = event.target.closest('[data-action]');
    if (actionButton) {
      const action = actionButton.dataset.action;
      if (action === 'create') {
        if (!createButton.disabled) openCreate();
        else ui.toast(`每位社员最多 ${limit} 个角色`, 'info');
        return;
      }
      if (action === 'retry') {
        load();
        return;
      }
      if (action === 'appearance') {
        const player = playersById.get(Number(actionButton.dataset.playerId));
        if (player) await openAppearance(player);
        return;
      }
      if (action === 'rename') {
        const player = playersById.get(Number(actionButton.dataset.playerId));
        if (player) openRename(player);
        return;
      }
      if (action === 'delete') {
        const player = playersById.get(Number(actionButton.dataset.playerId));
        if (!player) return;
        const confirmed = await ui.confirmDialog({
          title: '删除角色',
          message: `确定删除角色「${player.name}」吗？角色装备的外观不会从衣柜或皮肤库删除。`,
          confirmText: '删除角色',
          danger: true,
        });
        if (!confirmed) return;
        try {
          await api.del(`/players/${player.id}`);
          ui.toast('角色已删除', 'success');
          await load();
        } catch (error) {
          ui.toast(error.message, 'error');
        }
        return;
      }
    }
    const copyButton = event.target.closest('[data-copy-uuid]');
    if (copyButton) {
      ui.copyText(copyButton.dataset.copyUuid, 'UUID 已复制');
    }
  };
  root.addEventListener('click', handleRootClick);

  await load();

  return () => root.removeEventListener('click', handleRootClick);
}
