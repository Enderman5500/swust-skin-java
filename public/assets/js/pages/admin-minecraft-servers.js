import { api } from '../api.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

function formatDuration(millis) {
  const totalMinutes = Math.max(0, Math.floor(Number(millis || 0) / 60000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (!hours) return `${minutes} 分钟`;
  return `${hours} 小时 ${minutes} 分`;
}

function statusPill(online, onlineCount = 0, maxPlayers = 0) {
  return `<span class="status-pill ${online ? 'online' : 'offline'}"><span class="status-dot"></span>${
    online ? `在线 · ${onlineCount}/${maxPlayers || '—'}` : '离线'
  }</span>`;
}

function detailId(ctx) {
  const parts = String(ctx.path || '').split('/').filter(Boolean);
  return parts[0] === 'admin' && parts[1] === 'minecraft-servers' ? parts[2] || '' : '';
}

export async function renderMinecraftServers(main, ctx) {
  main.innerHTML = ui.loadingBlock('正在读取 Minecraft 服务器…');
  let data;
  try {
    data = await api.get('/plugins/minecraft-server/admin/overview');
  } catch (error) {
    main.innerHTML = `<div class="empty-state"><span class="empty-icon">${icon('server')}</span><h3>服务器数据读取失败</h3><p>${ui.escapeHtml(
      error.message
    )}</p><button class="btn btn-primary" data-action="retry">重试</button></div>`;
    main.querySelector('[data-action="retry"]')?.addEventListener('click', () => renderMinecraftServers(main, ctx));
    return;
  }

  const servers = data.servers || [];
  const players = data.players || [];
  const totals = data.totals || {};
  const serverId = detailId(ctx);
  if (serverId) {
    renderDetail(main, ctx, servers, players, serverId);
    return;
  }
  renderList(main, ctx, servers, totals);
}

function serverById(servers, id) {
  return servers.find((server) => String(server.id) === String(id));
}

function openEditor(main, ctx, server = null) {
  const content = document.createElement('div');
  content.innerHTML = `
    <form class="form-stack">
      <label class="field"><span>服务器名称</span><input name="name" maxlength="60" required value="${ui.escapeHtml(
        server?.name || ''
      )}" placeholder="例如 星空社生存服" /></label>
      <div class="form-grid two">
        <label class="field"><span>地址</span><input name="host" maxlength="255" required value="${ui.escapeHtml(
          server?.host || ''
        )}" placeholder="play.example.com" /></label>
        <label class="field"><span>端口</span><input name="port" type="number" min="1" max="65535" required value="${
          server?.port || 25565
        }" /></label>
      </div>
      <div class="form-grid two">
        <label class="field"><span>排序值</span><input name="sortOrder" type="number" min="-999999" max="999999" step="1" value="${
          server?.sortOrder ?? 0
        }" /><small class="field-hint">数字越小越靠前。</small></label>
        <label class="field"><span>公开状态</span>
          <span class="switch-field compact-switch">
            <span><strong>在公开页面显示</strong><small>关闭后仍会继续 Ping 和统计。</small></span>
            <input type="checkbox" name="visible" ${server?.visible !== false ? 'checked' : ''} />
          </span>
        </label>
      </div>
      <label class="field"><span>描述</span><textarea name="description" rows="3" maxlength="300" placeholder="服务器玩法或说明">${ui.escapeHtml(
        server?.description || ''
      )}</textarea></label>
      <p class="field-hint">${
        server
          ? `服务器 ID：<code>${ui.escapeHtml(server.id)}</code>`
          : '保存后会自动生成服务器 ID，游戏端上报时需要填写。'
      }</p>
    </form>
  `;
  ui.openModal({
    title: server ? '编辑服务器' : '添加服务器',
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
          ui.setButtonLoading(button, true, '保存中…');
          try {
            const result = await api.post('/plugins/minecraft-server/admin/server', {
              id: server?.id || '',
              name: form.elements.name.value.trim(),
              host: form.elements.host.value.trim(),
              port: Number(form.elements.port.value),
              sortOrder: Number(form.elements.sortOrder.value || 0),
              description: form.elements.description.value.trim(),
              visible: form.elements.visible.checked,
            });
            ui.setButtonLoading(button, false);
            close();
            ui.toast(server ? '服务器已更新' : `服务器已创建：${result.server?.id || ''}`, 'success');
            await renderMinecraftServers(main, ctx);
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

function renderList(main, ctx, servers, totals) {
  const publicCount = servers.filter((server) => server.visible).length;
  const hiddenCount = servers.length - publicCount;
  main.innerHTML = `
    <section class="admin-page-head">
      <div>
        <p class="eyebrow">MINECRAFT SERVERS</p>
        <h2>Minecraft 服务器</h2>
        <p>管理服务器 ID、排序、在线人数与公开状态。点击服务器名查看玩家在线时长。</p>
      </div>
      <div class="admin-head-actions">
        <a class="btn btn-ghost" href="#/admin/api-keys">${icon('key')}API 密钥</a>
        <button class="btn btn-primary" data-action="add-server">${icon('plus')}添加服务器</button>
      </div>
    </section>

    <section class="stat-grid">
      <article class="stat-card"><span class="stat-icon">${icon('server')}</span><div><strong>${totals.servers || 0}</strong><span>服务器</span></div><small>${totals.onlineServers || 0} 个在线</small></article>
      <article class="stat-card"><span class="stat-icon">${icon('users')}</span><div><strong>${totals.onlinePlayers || 0}</strong><span>当前在线</span></div><small>来自 Ping / 心跳</small></article>
      <article class="stat-card"><span class="stat-icon">${icon('eye')}</span><div><strong data-stat="public">${publicCount}</strong><span>公开显示</span></div><small>出现在服务器页面</small></article>
      <article class="stat-card"><span class="stat-icon">${icon('eyeOff')}</span><div><strong data-stat="hidden">${hiddenCount}</strong><span>已隐藏</span></div><small>仅管理后台可见</small></article>
    </section>

    <div class="panel table-panel">
      ${
        servers.length
          ? `<table class="data-table mc-server-table">
              <thead><tr><th>服务器</th><th>服务器 ID</th><th>地址</th><th>状态</th><th>公开</th><th>端 / 版本</th><th>最后心跳/探测</th><th>操作</th></tr></thead>
              <tbody>
                ${servers
                  .map(
                    (server) => `
                  <tr>
                    <td data-label="服务器"><a class="server-name-link" href="#/admin/minecraft-servers/${ui.escapeHtml(
                      server.id
                    )}">${ui.escapeHtml(server.name)}</a><small class="field-hint">排序 ${
                      server.sortOrder ?? 0
                    }${server.description ? ` · ${ui.escapeHtml(server.description)}` : ''}</small></td>
                    <td data-label="服务器 ID"><code>${ui.escapeHtml(server.id)}</code><button class="icon-btn" data-action="copy-id" data-id="${ui.escapeHtml(
                      server.id
                    )}" title="复制服务器 ID">${icon('copy')}</button></td>
                    <td data-label="地址"><code>${ui.escapeHtml(server.address)}</code></td>
                    <td data-label="状态">${statusPill(server.online, server.onlineCount, server.maxPlayers)}</td>
                    <td data-label="公开">
                      <label class="table-switch" title="${server.visible ? '已在公开页面显示' : '已隐藏'}">
                        <input type="checkbox" data-action="toggle-visible" data-server="${ui.escapeHtml(server.id)}" aria-label="公开显示" ${
                          server.visible ? 'checked' : ''
                        } />
                        <span></span>
                      </label>
                    </td>
                    <td data-label="端和版本">${
                      server.loader
                        ? `${ui.escapeHtml(server.loader)}<small class="field-hint">${ui.escapeHtml(
                            server.version || ''
                          )}</small>`
                        : ui.escapeHtml(server.version || '—')
                    }</td>
                    <td data-label="最后心跳/探测">${
                      server.lastSeenAt
                        ? `心跳 ${ui.timeAgo(server.lastSeenAt)}`
                        : server.pingedAt
                          ? `探测 ${ui.timeAgo(server.pingedAt)}`
                          : '从未上报'
                    }</td>
                    <td data-label="操作" class="table-actions">
                      <button class="btn btn-ghost btn-sm" data-action="edit-server" data-server="${ui.escapeHtml(
                        server.id
                      )}">${icon('edit')}编辑</button>
                      <button class="btn btn-ghost btn-sm danger-text" data-action="delete-server" data-server="${ui.escapeHtml(
                        server.id
                      )}">${icon('trash')}删除</button>
                    </td>
                  </tr>
                `
                  )
                  .join('')}
              </tbody>
            </table>`
          : `<div class="empty-state compact"><span class="empty-icon">${icon('server')}</span><h3>还没有服务器</h3><p>添加服务器后，游戏端即可使用服务器 ID 上报在线情况。</p><button class="btn btn-primary" data-action="add-server">${icon(
              'plus'
            )}添加服务器</button></div>`
      }
    </div>

    <div class="panel api-key-help">
      <div class="panel-head"><div><p class="eyebrow">GAME INTEGRATION</p><h3>游戏端上报</h3></div></div>
      <p class="panel-lead">在游戏服务器配置中填写以下三项：</p>
      <pre class="code-block small">base-url = ${ui.escapeHtml(location.origin)}
server-id = 服务器 ID
api-key  = sk_...（需要 plugin:minecraft-server:use 权限）</pre>
      <p class="field-hint">在 <a href="#/admin/api-keys">API 密钥</a> 中创建一个包含“使用 Minecraft 服务器”权限的密钥。普通用户只能看到服务器在线人数和自己的时长。</p>
    </div>
  `;

  main.querySelectorAll('[data-action="add-server"]').forEach((button) => {
    button.addEventListener('click', () => openEditor(main, ctx));
  });
  main.querySelectorAll('[data-action="copy-id"]').forEach((button) => {
    button.addEventListener('click', () => ui.copyText(button.dataset.id, '服务器 ID 已复制'));
  });
  main.querySelectorAll('[data-action="toggle-visible"]').forEach((input) => {
    input.addEventListener('change', async () => {
      const visible = input.checked;
      input.disabled = true;
      try {
        await api.post('/plugins/minecraft-server/admin/server/visibility', {
          id: input.dataset.server,
          visible,
        });
        const server = serverById(servers, input.dataset.server);
        if (server) server.visible = visible;
        const shown = servers.filter((item) => item.visible).length;
        const publicStat = main.querySelector('[data-stat="public"]');
        const hiddenStat = main.querySelector('[data-stat="hidden"]');
        if (publicStat) publicStat.textContent = String(shown);
        if (hiddenStat) hiddenStat.textContent = String(servers.length - shown);
        const label = input.closest('.table-switch');
        if (label) label.title = visible ? '已在公开页面显示' : '已隐藏';
        input.disabled = false;
        ui.toast(visible ? '服务器已在公开页面显示' : '服务器已从公开页面隐藏', 'success');
      } catch (error) {
        input.checked = !visible;
        input.disabled = false;
        ui.toast(error.message, 'error');
      }
    });
  });
  main.querySelectorAll('[data-action="edit-server"]').forEach((button) => {
    button.addEventListener('click', () => openEditor(main, ctx, serverById(servers, button.dataset.server)));
  });
  main.querySelectorAll('[data-action="delete-server"]').forEach((button) => {
    button.addEventListener('click', async () => {
      const server = serverById(servers, button.dataset.server);
      if (!server) return;
      const confirmed = await ui.confirmDialog({
        title: '删除服务器',
        message: `删除「${server.name}」会同时清除该服务器的在线人数和玩家时长数据，确定继续吗？`,
        confirmText: '删除',
        danger: true,
      });
      if (!confirmed) return;
      try {
        await api.post('/plugins/minecraft-server/admin/server/delete', { id: server.id });
        ui.toast('服务器已删除', 'success');
        await renderMinecraftServers(main, ctx);
      } catch (error) {
        ui.toast(error.message, 'error');
      }
    });
  });
}

function renderDetail(main, ctx, servers, players, serverId) {
  const server = serverById(servers, serverId);
  if (!server) {
    main.innerHTML = `<div class="empty-state"><span class="empty-icon">${icon('server')}</span><h3>服务器不存在</h3><p>它可能已被删除。</p><a class="btn btn-primary" href="#/admin/minecraft-servers">返回服务器列表</a></div>`;
    return;
  }
  const rows = players.filter((player) => String(player.serverId) === String(serverId));
  const totalMillis = rows.reduce((sum, player) => sum + Number(player.totalMillis || 0), 0);
  const afkMillis = rows.reduce((sum, player) => sum + Number(player.afkMillis || 0), 0);
  const activeMillis = totalMillis - afkMillis;

  main.innerHTML = `
    <section class="admin-page-head">
      <div>
        <a class="text-link mc-back-link" href="#/admin/minecraft-servers">${icon('arrow')}返回服务器列表</a>
        <p class="eyebrow">SERVER DETAIL</p>
        <h2>${ui.escapeHtml(server.name)}</h2>
        <p><code>${ui.escapeHtml(server.id)}</code> · ${ui.escapeHtml(server.address)} · 排序 ${
          server.sortOrder ?? 0
        }</p>
      </div>
      <div class="admin-head-actions">
        ${statusPill(server.online, server.onlineCount, server.maxPlayers)}
        <button class="btn btn-ghost" data-action="edit-server">${icon('edit')}编辑服务器</button>
      </div>
    </section>

    <section class="stat-grid mc-stat-grid">
      <article class="stat-card"><span class="stat-icon">${icon('users')}</span><div><strong>${server.onlineCount || 0}</strong><span>当前在线</span></div><small>最大 ${server.maxPlayers || '—'} 人</small></article>
      <article class="stat-card"><span class="stat-icon">${icon('clock')}</span><div><strong>${formatDuration(totalMillis)}</strong><span>累计在线</span></div><small>${rows.length} 名玩家</small></article>
      <article class="stat-card"><span class="stat-icon">${icon('sparkles')}</span><div><strong>${formatDuration(activeMillis)}</strong><span>活跃时长</span></div><small>总在线减挂机</small></article>
      <article class="stat-card"><span class="stat-icon">${icon('moon')}</span><div><strong>${formatDuration(afkMillis)}</strong><span>挂机时长</span></div><small>无操作判定</small></article>
    </section>

    <div class="panel">
      <div class="panel-head"><div><p class="eyebrow">ONLINE NOW</p><h3>当前在线玩家</h3></div></div>
      <div class="mc-online-list">
        ${
          server.players?.length
            ? server.players
                .map(
                  (player) => `
              <div class="mc-online-row">
                <strong>${ui.escapeHtml(player.name)}</strong>
                ${player.afk ? '<span class="status-pill offline">挂机</span>' : '<span class="status-pill online">活跃</span>'}
                <small>${ui.escapeHtml(player.uuid)} · 会话开始 ${ui.formatDate(player.sessionStartedAt, {
                  time: true,
                })}</small>
              </div>
            `
                )
                .join('')
            : '<div class="empty-state compact"><p>当前没有在线玩家上报。Ping 能判断服务器在线，但玩家列表需要游戏端心跳。</p></div>'
        }
      </div>
    </div>

    <div class="panel">
      <div class="panel-head"><div><p class="eyebrow">PLAYTIME</p><h3>玩家在线时长</h3></div></div>
      ${
        rows.length
          ? `<div class="table-panel"><table class="data-table mc-player-table">
              <thead><tr><th>玩家</th><th>站点角色</th><th>总在线</th><th>活跃</th><th>挂机</th><th>最后在线</th></tr></thead>
              <tbody>
                ${rows
                  .map(
                    (player) => `
                  <tr>
                    <td data-label="玩家"><strong>${ui.escapeHtml(player.name)}</strong><small class="field-hint">${ui.escapeHtml(
                      player.uuid
                    )}</small></td>
                    <td data-label="站点角色">${
                      player.sitePlayerName
                        ? ui.escapeHtml(player.sitePlayerName)
                        : '<span class="muted-text">未匹配</span>'
                    }</td>
                    <td data-label="总在线">${formatDuration(player.totalMillis)}</td>
                    <td data-label="活跃">${formatDuration(player.activeMillis)}</td>
                    <td data-label="挂机">${formatDuration(player.afkMillis)}</td>
                    <td data-label="最后在线">${player.lastSeenAt ? ui.timeAgo(player.lastSeenAt) : '—'}</td>
                  </tr>
                `
                  )
                  .join('')}
              </tbody>
            </table></div>`
          : `<div class="empty-state compact"><p>还没有玩家在线时长数据。</p></div>`
      }
    </div>
  `;

  main.querySelector('[data-action="edit-server"]')?.addEventListener('click', () => openEditor(main, ctx, server));
}
