function formatDuration(millis) {
  const totalMinutes = Math.max(0, Math.floor(Number(millis || 0) / 60000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (!hours) return `${minutes} 分钟`;
  return `${hours} 小时 ${minutes} 分`;
}

function renderServers(servers, tools) {
  const { icon, escapeHtml, timeAgo } = tools;
  if (!servers.length) {
    return `<div class="empty-state compact"><span class="empty-icon">${icon('server')}</span><h3>还没有服务器</h3><p>管理员添加服务器后，这里会显示在线人数。</p></div>`;
  }
  return `
    <div class="server-grid">
      ${servers
        .map(
          (server) => `
        <article class="server-card ${server.online ? 'is-online' : ''}">
          <div class="server-card-top">
            <span class="status-pill ${server.online ? 'online' : 'offline'}">
              <span class="status-dot"></span>${server.online ? '在线' : '离线'}
            </span>
            <span class="server-latency">${server.online ? `${server.onlineCount} / ${server.maxPlayers || '—'} 人` : '未上报'}</span>
          </div>
          <h3>${escapeHtml(server.name)}</h3>
          <p class="server-address"><code>${escapeHtml(server.address)}</code></p>
          ${server.description ? `<p class="mc-server-desc">${escapeHtml(server.description)}</p>` : ''}
          <div class="mc-server-meta">
            <span>${escapeHtml(
              server.loader ? `${server.loader} ${server.version || ''}` : server.version || '未知版本'
            )}</span>
            <span>${
              server.lastSeenAt
                ? `最后心跳 ${timeAgo(server.lastSeenAt)}`
                : server.pingedAt
                  ? `最后探测 ${timeAgo(server.pingedAt)}`
                  : '从未上报'
            }</span>
          </div>
        </article>
      `
        )
        .join('')}
    </div>
  `;
}

function renderMyTime(data, tools) {
  const { escapeHtml, timeAgo } = tools;
  if (!data || !data.servers?.length) {
    return `<div class="empty-state compact"><p>还没有匹配到你的游戏角色在线记录。</p></div>`;
  }
  return `
    <div class="mc-time-summary">
      <div><strong>${formatDuration(data.totalMillis)}</strong><span>总在线</span></div>
      <div><strong>${formatDuration(data.activeMillis)}</strong><span>活跃</span></div>
      <div><strong>${formatDuration(data.afkMillis)}</strong><span>挂机</span></div>
    </div>
    <div class="table-panel mc-time-table">
      <table class="data-table">
        <thead><tr><th>服务器</th><th>总在线</th><th>活跃</th><th>挂机</th><th>最后在线</th></tr></thead>
        <tbody>
          ${data.servers
            .map(
              (server) => `
            <tr>
              <td data-label="服务器"><strong>${escapeHtml(server.name)}</strong>${server.online ? ' <span class="status-pill online"><span class="status-dot"></span>在线</span>' : ''}</td>
              <td data-label="总在线">${formatDuration(server.totalMillis)}</td>
              <td data-label="活跃">${formatDuration(server.activeMillis)}</td>
              <td data-label="挂机">${formatDuration(server.afkMillis)}</td>
              <td data-label="最后在线">${server.lastSeenAt ? timeAgo(server.lastSeenAt) : '—'}</td>
            </tr>
          `
            )
            .join('')}
        </tbody>
      </table>
    </div>
  `;
}

export async function mount(root, ctx) {
  let disposed = false;
  let timer = null;
  let loading = false;
  const { api, user, icon, escapeHtml, timeAgo } = ctx;

  root.innerHTML = `<section class="plugin-shell"><div class="plugin-loading">${ctx.ui.loadingBlock('正在读取服务器状态…')}</div></section>`;

  async function load() {
    if (loading || disposed) return;
    loading = true;
    try {
      const [status, mine] = await Promise.all([
        api.get('/plugins/minecraft-server/status'),
        user ? api.get('/plugins/minecraft-server/me').catch(() => null) : Promise.resolve(null),
      ]);
      if (disposed) return;
      const servers = status.servers || [];
      const onlineServers = servers.filter((server) => server.online).length;
      const onlinePlayers = servers.reduce((sum, server) => sum + Number(server.onlineCount || 0), 0);
      root.innerHTML = `
        <section class="plugin-shell mc-server-page">
          <header class="plugin-head">
            <div>
              <p class="eyebrow">MINECRAFT SERVERS</p>
              <h1>社团服务器</h1>
              <p>查看服务器在线状态。登录后可以查看自己的总在线、活跃和挂机时长。</p>
            </div>
            <span class="type-pill muted">${servers.length} 个服务器</span>
          </header>
          <div class="server-summary">
            <div class="server-summary-item"><strong>${servers.length}</strong><span>服务器</span></div>
            <div class="server-summary-item"><strong>${onlineServers}</strong><span>在线服务器</span></div>
            <div class="server-summary-item"><strong>${onlinePlayers}</strong><span>当前在线玩家</span></div>
          </div>
          ${renderServers(servers, ctx)}
          ${
            user
              ? `<section class="panel mc-my-time"><div class="panel-head"><div><p class="eyebrow">MY PLAYTIME</p><h2>我的在线时长</h2></div></div>${renderMyTime(mine, ctx)}</section>`
              : `<div class="panel mc-login-hint"><p class="panel-lead">登录后可以查看自己的在线、活跃和挂机时长；这里不会显示其他玩家的数据。</p><a class="btn btn-primary" href="#/login">登录查看</a></div>`
          }
        </section>
      `;
    } catch (error) {
      if (!disposed) {
        root.innerHTML = `<div class="empty-state"><span class="empty-icon">${icon('server')}</span><h3>服务器状态读取失败</h3><p>${escapeHtml(error.message)}</p></div>`;
      }
    } finally {
      loading = false;
    }
  }

  await load();
  timer = setInterval(load, 30000);
  return () => {
    disposed = true;
    if (timer) clearInterval(timer);
  };
}
