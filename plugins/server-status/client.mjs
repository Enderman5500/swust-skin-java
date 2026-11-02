export async function mount(root, ctx) {
  const { api, user, toast, escapeHtml, formatDate } = ctx;
  let timer = null;
  let disposed = false;
  let editing = false;
  let latest = null;

  root.innerHTML = `
    <section class="plugin-shell">
      <header class="plugin-head">
        <div>
          <p class="eyebrow">PLUGIN · SERVER STATUS</p>
          <h1>社团服务器</h1>
          <p class="plugin-lead">实时探测社团 Minecraft 服务器的在线状态与连接延迟。地址由技术组维护。</p>
        </div>
        <div class="plugin-head-actions">
          ${user?.isAdmin ? '<button class="btn btn-ghost" data-action="edit">管理服务器</button>' : ''}
          <button class="btn btn-primary" data-action="refresh">刷新状态</button>
        </div>
      </header>
      <div class="server-summary" data-summary>
        <div class="server-summary-item"><span>服务器</span><strong data-count="total">—</strong></div>
        <div class="server-summary-item"><span>在线</span><strong data-count="online">—</strong></div>
        <div class="server-summary-item"><span>本站材质上传</span><strong data-count="uploads">—</strong></div>
        <div class="server-summary-item"><span>最近检测</span><strong data-count="checked">—</strong></div>
      </div>
      <div class="server-grid" data-server-list>
        <div class="plugin-loading">正在连接服务器…</div>
      </div>
      <div class="server-editor" data-editor hidden>
        <div class="section-heading">
          <h2>服务器列表</h2>
          <p>最多 8 台，端口通常为 25565。修改后立即生效。</p>
        </div>
        <div class="server-editor-rows" data-editor-rows></div>
        <div class="server-editor-actions">
          <button class="btn btn-ghost" data-action="add-row">添加服务器</button>
          <button class="btn btn-primary" data-action="save">保存配置</button>
        </div>
      </div>
      <p class="plugin-footnote" data-footnote></p>
    </section>
  `;

  const listEl = root.querySelector('[data-server-list]');
  const editorEl = root.querySelector('[data-editor]');
  const rowsEl = root.querySelector('[data-editor-rows]');
  const summaryEl = root.querySelector('[data-summary]');
  const footnoteEl = root.querySelector('[data-footnote]');

  function copyText(text) {
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(
        () => toast('已复制服务器地址', 'success'),
        () => toast('复制失败，请手动选择地址', 'error')
      );
    } else {
      toast(`服务器地址：${text}`, 'info');
    }
  }

  function renderServers(servers) {
    if (!servers.length) {
      listEl.innerHTML = '<div class="empty-state">还没有配置服务器。</div>';
      return;
    }
    listEl.innerHTML = servers
      .map(
        (server) => `
        <article class="server-card ${server.online ? 'is-online' : ''}">
          <div class="server-card-top">
            <span class="status-pill ${server.online ? 'online' : 'offline'}">
              <span class="status-dot"></span>${server.online ? '在线' : '离线'}
            </span>
            <span class="server-latency">${server.online ? `${server.latency} ms` : '—'}</span>
          </div>
          <h3>${escapeHtml(server.name)}</h3>
          <div class="server-address">
            <code>${escapeHtml(server.host)}:${server.port}</code>
            <button class="btn btn-ghost btn-sm" data-copy="${escapeHtml(`${server.host}:${server.port}`)}">复制</button>
          </div>
        </article>
      `
      )
      .join('');
  }

  function renderSummary(status) {
    summaryEl.querySelector('[data-count="total"]').textContent = status.servers.length;
    summaryEl.querySelector('[data-count="online"]').textContent = status.online;
    summaryEl.querySelector('[data-count="uploads"]').textContent = status.uploadedTextures;
    summaryEl.querySelector('[data-count="checked"]').textContent = status.checkedAt
      ? formatDate(status.checkedAt, { time: true })
      : '—';
    if (status.lastUpload?.name) {
      footnoteEl.textContent = `最近通过本插件记录的上传：${status.lastUpload.name}`;
    }
  }

  async function load(silent = false) {
    if (disposed) return;
    try {
      const status = await api.get('/plugins/server-status/status');
      if (disposed) return;
      latest = status;
      renderSummary(status);
      renderServers(status.servers);
    } catch (error) {
      if (!silent) toast(error.message || '服务器状态获取失败', 'error');
      listEl.innerHTML = `<div class="empty-state">状态获取失败：${escapeHtml(
        error.message || '未知错误'
      )}</div>`;
    }
  }

  function renderEditor() {
    const servers = latest?.servers?.length
      ? latest.servers
      : [{ name: '', host: '', port: 25565 }];
    rowsEl.innerHTML = servers
      .map(
        (server, index) => `
        <div class="server-editor-row" data-row="${index}">
          <label><span>名称</span><input type="text" data-field="name" maxlength="32" value="${escapeHtml(
            server.name
          )}" placeholder="星空社生存服"></label>
          <label><span>地址</span><input type="text" data-field="host" maxlength="128" value="${escapeHtml(
            server.host
          )}" placeholder="play.example.com"></label>
          <label class="server-editor-port"><span>端口</span><input type="number" data-field="port" min="1" max="65535" value="${Number(
            server.port
          ) || 25565}"></label>
          <button class="btn btn-ghost btn-sm" data-action="remove-row" title="移除">移除</button>
        </div>
      `
      )
      .join('');
  }

  async function save() {
    const rows = [...rowsEl.querySelectorAll('[data-row]')];
    const servers = rows.map((row) => ({
      name: row.querySelector('[data-field="name"]').value.trim(),
      host: row.querySelector('[data-field="host"]').value.trim(),
      port: Number.parseInt(row.querySelector('[data-field="port"]').value, 10),
    }));
    try {
      await api.post('/plugins/server-status/config', { servers });
      toast('服务器配置已保存', 'success');
      editing = false;
      editorEl.hidden = true;
      await load();
    } catch (error) {
      toast(error.message || '保存失败', 'error');
    }
  }

  root.addEventListener('click', (event) => {
    const target = event.target.closest('button');
    if (!target) return;
    const action = target.dataset.action;
    if (target.dataset.copy) {
      copyText(target.dataset.copy);
      return;
    }
    if (action === 'refresh') load();
    else if (action === 'edit') {
      editing = !editing;
      editorEl.hidden = !editing;
      if (editing) renderEditor();
    } else if (action === 'add-row') {
      if (rowsEl.children.length >= 8) {
        toast('最多 8 台服务器', 'error');
        return;
      }
      const wrapper = document.createElement('div');
      wrapper.innerHTML = `
        <div class="server-editor-row" data-row="${rowsEl.children.length}">
          <label><span>名称</span><input type="text" data-field="name" maxlength="32" placeholder="新服务器"></label>
          <label><span>地址</span><input type="text" data-field="host" maxlength="128" placeholder="play.example.com"></label>
          <label class="server-editor-port"><span>端口</span><input type="number" data-field="port" min="1" max="65535" value="25565"></label>
          <button class="btn btn-ghost btn-sm" data-action="remove-row" title="移除">移除</button>
        </div>`;
      rowsEl.append(wrapper.firstElementChild);
    } else if (action === 'remove-row') {
      target.closest('[data-row]')?.remove();
    } else if (action === 'save') {
      save();
    }
  });

  timer = setInterval(() => {
    if (!editing) load(true);
  }, 30000);

  await load();

  return () => {
    disposed = true;
    if (timer) clearInterval(timer);
  };
}
