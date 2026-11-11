const STYLE = `
<style>
.ld-page { display:flex; flex-direction:column; gap:18px; }
.ld-hero { position:relative; overflow:hidden; display:flex; gap:18px; align-items:center; padding:26px; border-radius:22px; border:1px solid var(--line); background:linear-gradient(135deg, rgba(44,76,102,.10), rgba(156,195,230,.16)); }
.ld-hero img { width:72px; height:72px; border-radius:20px; object-fit:cover; border:1px solid rgba(255,255,255,.7); background:#fff; }
.ld-hero h1 { margin:2px 0 4px; font-family:var(--font-display); font-size:30px; line-height:1.2; }
.ld-hero p { margin:0; color:var(--muted); font-size:13px; }
.ld-badge { display:inline-flex; align-items:center; gap:6px; padding:3px 9px; border-radius:999px; background:rgba(44,76,102,.1); color:var(--navy); font-size:10px; font-weight:700; letter-spacing:.08em; }
.ld-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(320px,1fr)); gap:16px; }
.ld-card { padding:20px; border-radius:18px; border:1px solid var(--line); background:#fff; }
.ld-card h2 { margin:0 0 4px; font-size:18px; }
.ld-card > p { margin:0 0 14px; color:var(--muted); font-size:12px; }
.ld-drag { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:14px; border-radius:14px; border:1px dashed rgba(44,76,102,.28); background:rgba(156,195,230,.12); cursor:grab; user-select:none; }
.ld-drag code { font-size:11px; color:var(--navy); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.ld-actions { display:flex; flex-wrap:wrap; gap:8px; margin-top:12px; }
.ld-announcement { white-space:pre-wrap; font-size:13px; line-height:1.75; color:var(--text); }
.ld-server { display:flex; flex-direction:column; gap:8px; padding:16px; border-radius:14px; border:1px solid var(--line); background:var(--paper); }
.ld-server-top { display:flex; align-items:center; justify-content:space-between; gap:10px; }
.ld-server h3 { margin:0; font-size:15px; }
.ld-server code { font-size:11px; color:var(--navy); }
.ld-server p { margin:0; font-size:11.5px; color:var(--muted); }
.ld-server-meta { display:flex; flex-wrap:wrap; gap:6px 14px; font-size:10.5px; color:var(--muted-2); }
.ld-resource { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px 0; border-bottom:1px solid var(--line); }
.ld-resource:last-child { border-bottom:0; }
.ld-resource strong { display:block; font-size:13px; }
.ld-resource small { color:var(--muted); font-size:11px; }
.ld-admin-form { display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:14px; }
.ld-admin-form .field { margin:0; }
.ld-admin-form .field.full { grid-column:1/-1; }
.ld-section-head { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:12px; }
.ld-section-head h2 { margin:0; }
.ld-empty { padding:24px; text-align:center; color:var(--muted); font-size:12px; }
@media (max-width:700px){ .ld-hero{padding:20px; align-items:flex-start; flex-direction:column;} .ld-hero h1{font-size:24px;} }
</style>`;

const TYPE_LABEL = {
  modpack: '整合包',
  resourcepack: '资源包',
  shaderpack: '光影包',
  other: '其他',
};

function formatBytes(bytes) {
  const value = Number(bytes) || 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`;
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function absolute(url, origin) {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  return origin + (url.startsWith('/') ? url : `/${url}`);
}

export async function mount(root, ctx) {
  const { api, ui, icon, escapeHtml, user } = ctx;
  const adminMode = ctx.query.tab === 'admin';
  let disposed = false;

  root.innerHTML = `${STYLE}<section class="ld-page">${ui.loadingBlock('正在读取域信息…')}</section>`;

  function toast(message, type = 'success') {
    ui.toast(message, type);
  }

  function publicView(data) {
    const origin = location.origin;
    const base = (data.siteUrl || origin).replace(/\/$/, '');
    const invite = `swustmcl:domain:${encodeURIComponent(base)}`;
    const addUrl = `swustmcl://add-domain?url=${encodeURIComponent(base)}`;
    const iconUrl = data.icon ? absolute(data.icon, origin) : `${origin}/assets/brand/badge.png`;
    const servers = data.servers || [];
    const resources = data.resources || [];
    const packs = data.packs || [];
    const announcement = data.announcement?.text || '';
    root.innerHTML = `${STYLE}
      <section class="ld-page">
        <header class="ld-hero">
          <img src="${escapeHtml(iconUrl)}" alt="" />
          <div>
            <span class="ld-badge">SWUSTMCL DOMAIN</span>
            <h1>${escapeHtml(data.name || '社团域')}</h1>
            <p>${escapeHtml(data.description || '把域添加到星启启动器，直接获取官方服务器和资源。')}</p>
          </div>
        </header>

        <div class="ld-grid">
          <section class="ld-card">
            <h2>添加到启动器</h2>
            <p>在启动器侧边栏「域」卡片中添加，或复制邀请手动添加。</p>
            <div class="ld-drag" draggable="true" data-ld-drag>
              ${icon('layers')}
              <code>${escapeHtml(invite)}</code>
            </div>
            <div class="ld-actions">
              <button class="btn btn-primary btn-sm" data-ld-copy="${escapeHtml(invite)}">${icon('copy')}复制邀请</button>
              <button class="btn btn-ghost btn-sm" data-ld-copy="${escapeHtml(addUrl)}">${icon('external')}复制拉起链接</button>
              <a class="btn btn-ghost btn-sm" href="${escapeHtml(addUrl)}">${icon('external')}打开启动器</a>
            </div>
          </section>

          <section class="ld-card">
            <h2>域公告</h2>
            <p>${data.announcement?.updatedAt ? `更新于 ${escapeHtml(ctx.timeAgo(data.announcement.updatedAt))}` : '暂无公告'}</p>
            <div class="ld-announcement">${announcement ? escapeHtml(announcement) : '管理员还没有发布公告。'}</div>
          </section>
        </div>

        <section class="ld-card">
          <div class="ld-section-head"><h2>官方服务器</h2><span class="type-pill muted">${servers.length} 个</span></div>
          ${
            servers.length
              ? `<div class="ld-grid">${servers
                  .map((server) => {
                    const online = Boolean(server.online);
                    return `<article class="ld-server">
                      <div class="ld-server-top">
                        <h3>${escapeHtml(server.name)}</h3>
                        <span class="status-pill ${online ? 'online' : 'offline'}"><span class="status-dot"></span>${online ? '在线' : '未探测'}</span>
                      </div>
                      <code>${escapeHtml(server.host)}:${server.port}</code>
                      ${server.note ? `<p>${escapeHtml(server.note)}</p>` : ''}
                      ${server.pack ? `<p>整合包：${escapeHtml(server.pack.name)} · ${server.pack.policy === 'required' ? '必装' : '可选'} · v${server.pack.latestVersion}</p>` : ''}
                      <div class="ld-server-meta">
                        ${online ? `<span>${server.onlineCount || 0} / ${server.maxPlayers || '—'} 人</span>` : ''}
                        ${server.version ? `<span>${escapeHtml(server.version)}</span>` : ''}
                        ${server.lastSeenAt ? `<span>心跳 ${escapeHtml(ctx.timeAgo(server.lastSeenAt))}</span>` : ''}
                      </div>
                    </article>`;
                  })
                  .join('')}</div>`
              : '<div class="ld-empty">管理员还没有添加官方服务器。</div>'
          }
        </section>

        <section class="ld-card">
          <div class="ld-section-head"><h2>服务器整合包</h2><span class="type-pill muted">${packs.length} 个</span></div>
          ${
            packs.length
              ? packs
                  .map((pack) => {
                    const server = servers.find((item) => item.id === pack.serverId);
                    return `<div class="ld-resource">
                      <div>
                        <strong>${escapeHtml(pack.name)}${pack.policy === 'required' ? ' · 必装' : ' · 可选'}</strong>
                        <small>${escapeHtml(server ? server.name : '未绑定服务器')} · v${pack.latestVersion}${pack.totalSize ? ` · ${formatBytes(pack.totalSize)}` : ''}${pack.minJava ? ` · Java ${pack.minJava}+` : ''}</small>
                        ${pack.description ? `<small style="display:block;margin-top:2px">${escapeHtml(pack.description)}</small>` : ''}
                      </div>
                      <span class="type-pill muted">启动器安装</span>
                    </div>`;
                  })
                  .join('')
              : '<div class="ld-empty">管理员还没有发布服务器整合包。</div>'
          }
        </section>

        <section class="ld-card">
          <div class="ld-section-head"><h2>其他资源</h2><span class="type-pill muted">${resources.length} 个</span></div>
          ${
            resources.length
              ? resources
                  .map(
                    (resource) => `<div class="ld-resource">
                      <div>
                        <strong>${escapeHtml(resource.name)}</strong>
                        <small>${escapeHtml(TYPE_LABEL[resource.type] || resource.type)}${
                          resource.version ? ` · ${escapeHtml(resource.version)}` : ''
                        }${resource.size ? ` · ${formatBytes(resource.size)}` : ''}</small>
                        ${resource.description ? `<small style="display:block;margin-top:2px">${escapeHtml(resource.description)}</small>` : ''}
                      </div>
                      <a class="btn btn-ghost btn-sm" href="${escapeHtml(absolute(resource.url, origin))}" target="_blank" rel="noopener">${icon('download')}下载</a>
                    </div>`
                  )
                  .join('')
              : '<div class="ld-empty">管理员还没有发布资源。</div>'
          }
        </section>
      </section>`;

    const drag = root.querySelector('[data-ld-drag]');
    drag?.addEventListener('dragstart', (event) => {
      event.dataTransfer.effectAllowed = 'copy';
      event.dataTransfer.setData('text/plain', invite);
      event.dataTransfer.setData('text/uri-list', addUrl);
    });
    drag?.addEventListener('click', () => ui.copyText(invite, '启动器邀请已复制'));
    root.querySelectorAll('[data-ld-copy]').forEach((button) => {
      button.addEventListener('click', () => ui.copyText(button.dataset.ldCopy, '已复制'));
    });
  }

  function adminView(data) {
    const domain = data.domain || {};
    const servers = data.servers || [];
    const resources = data.resources || [];
    const packs = data.packs || [];
    const tokens = data.tokens || [];
    root.innerHTML = `${STYLE}
      <section class="ld-page">
        <header class="admin-page-head">
          <div><p class="eyebrow">LAUNCHER DOMAIN</p><h2>启动器域管理</h2><p>维护 SWUSTMCL 添加社团域后看到的公告、官方服务器与资源清单。</p></div>
        </header>

        <section class="panel">
          <div class="panel-head"><div><p class="eyebrow">DOMAIN</p><h3>域资料</h3></div><button class="btn btn-primary btn-sm" data-ld-save-domain>${icon('check')}保存域资料</button></div>
          <form class="ld-admin-form" data-ld-domain-form>
            <label class="field"><span>域名称</span><input name="name" maxlength="60" required value="${escapeHtml(domain.name || '')}" /></label>
            <label class="field"><span>图标地址</span><input name="icon" maxlength="500" placeholder="/assets/brand/badge.png" value="${escapeHtml(domain.icon || '')}" /></label>
            <label class="field"><span>站点地址</span><input name="siteUrl" maxlength="500" placeholder="留空则使用当前站点" value="${escapeHtml(domain.site_url || '')}" /></label>
            <label class="field"><span>认证地址</span><input name="yggdrasilUrl" maxlength="500" placeholder="/api/yggdrasil" value="${escapeHtml(domain.yggdrasil_url || '')}" /></label>
            <label class="field full"><span>CurseForge API Key（站点中转）</span>
              <input name="curseforgeApiKey" type="password" autocomplete="off" maxlength="200" placeholder="${
                domain.curseforge_configured ? '已配置，留空保持不变' : '粘贴 CurseForge API Key'
              }" />
              <small class="field-hint">${
                domain.curseforge_configured
                  ? `当前状态：${domain.curseforge_source === 'environment' ? '已通过站点环境变量配置' : '已配置'}。域内启动器搜索 CurseForge 时由本站中转，Key 不会下发。`
                  : '未配置。配置后，处于本站域的启动器可直接搜索 CurseForge，无需玩家填写 Key；也可用环境变量 CURSEFORGE_API_KEY 配置。'
              }</small>
            </label>
            <label class="field full"><span>域描述</span><input name="description" maxlength="300" value="${escapeHtml(domain.description || '')}" /></label>
            <label class="field full"><span>公告</span><textarea name="announcement" rows="5" maxlength="2000" placeholder="启动器域页面展示的公告">${escapeHtml(domain.announcement || '')}</textarea></label>
          </form>
        </section>

        <section class="panel">
          <div class="panel-head"><div><p class="eyebrow">PUBLISH TOKENS</p><h3>启动器发布令牌</h3></div><button class="btn btn-primary btn-sm" data-ld-add-token>${icon('plus')}生成令牌</button></div>
          <p class="field-hint">在启动器「版本管理 → 发布到域」中填写。令牌只显示一次，可随时吊销；持有令牌的人可以发布整合包版本。</p>
          ${
            tokens.length
              ? `<div class="table-panel"><table class="data-table">
                  <thead><tr><th>名称</th><th>前缀</th><th>有效期</th><th>最后使用</th><th>状态</th><th class="table-actions-col">操作</th></tr></thead>
                  <tbody>${tokens
                    .map(
                      (token) => `<tr>
                        <td data-label="名称"><strong>${escapeHtml(token.name)}</strong></td>
                        <td data-label="前缀"><code>${escapeHtml(token.prefix)}…</code></td>
                        <td data-label="有效期">${token.expiresAt ? escapeHtml(String(token.expiresAt).slice(0, 10)) : '永久'}</td>
                        <td data-label="最后使用">${token.lastUsedAt ? escapeHtml(String(token.lastUsedAt).slice(0, 16).replace('T', ' ')) : '—'}</td>
                        <td data-label="状态">${token.revoked ? '<span class="status-pill offline">已吊销</span>' : '<span class="status-pill online">可用</span>'}</td>
                        <td data-label="操作" class="table-actions">
                          <button class="btn btn-ghost btn-sm danger-text" data-ld-delete-token="${escapeHtml(token.id)}">${icon('trash')}吊销</button>
                        </td>
                      </tr>`
                    )
                    .join('')}</tbody>
                </table></div>`
              : '<div class="ld-empty">还没有发布令牌。生成后到启动器里填写即可发布整合包。</div>'
          }
        </section>

        <section class="panel">
          <div class="panel-head"><div><p class="eyebrow">INSTANCE PACKS</p><h3>服务器整合包</h3></div><button class="btn btn-ghost btn-sm" data-ld-refresh-packs>${icon('refresh')}刷新</button></div>
          <p class="field-hint">由管理员在启动器「版本管理 → 发布到域」上传。每个服务器最多绑定一个整合包；同一实例再次发布只会上传变化文件。</p>
          ${
            packs.length
              ? `<div class="table-panel"><table class="data-table">
                  <thead><tr><th>整合包</th><th>服务器</th><th>绑定方式</th><th>最低 Java</th><th>版本</th><th>大小</th><th>更新</th><th class="table-actions-col">操作</th></tr></thead>
                  <tbody>${packs
                    .map(
                      (pack) => `<tr>
                        <td data-label="整合包"><strong>${escapeHtml(pack.name)}</strong><small class="field-hint">${escapeHtml(pack.instance?.mcVersion || '')}${pack.instance?.loader ? ' · ' + escapeHtml(pack.instance.loader) : ''}</small></td>
                        <td data-label="服务器">${escapeHtml(pack.serverName || '未绑定')}</td>
                        <td data-label="绑定方式">${pack.policy === 'required' ? '<span class="status-pill online">必装</span>' : '<span class="status-pill offline">可选</span>'}</td>
                        <td data-label="最低 Java">${pack.minJava ? 'Java ' + pack.minJava + '+' : '不设置'}</td>
                        <td data-label="版本">${pack.latestVersion ? 'v' + pack.latestVersion + (pack.visible ? '' : ' · 隐藏') : '未发布'}</td>
                        <td data-label="大小">${formatBytes(pack.totalSize || 0)}</td>
                        <td data-label="更新">${escapeHtml(String(pack.latestCreatedAt || pack.updatedAt || '').slice(0, 10))}</td>
                        <td data-label="操作" class="table-actions">
                          <button class="btn btn-ghost btn-sm" data-ld-pack-versions="${escapeHtml(pack.id)}">${icon('layers')}版本</button>
                          <button class="btn btn-ghost btn-sm" data-ld-pack-visibility="${escapeHtml(pack.id)}" data-visible="${pack.visible ? '1' : '0'}">${pack.visible ? '隐藏' : '公开'}</button>
                          <button class="btn btn-ghost btn-sm danger-text" data-ld-pack-delete="${escapeHtml(pack.id)}">${icon('trash')}删除</button>
                        </td>
                      </tr>`
                    )
                    .join('')}</tbody>
                </table></div>`
              : '<div class="ld-empty">还没有整合包。请到启动器发布一个已有实例。</div>'
          }
        </section>

        <section class="panel">
          <div class="panel-head"><div><p class="eyebrow">SERVERS</p><h3>官方服务器</h3></div><button class="btn btn-primary btn-sm" data-ld-add-server>${icon('plus')}添加服务器</button></div>
          ${
            servers.length
              ? `<div class="table-panel"><table class="data-table">
                  <thead><tr><th>服务器</th><th>地址</th><th>关联统计</th><th>公开</th><th>排序</th><th class="table-actions-col">操作</th></tr></thead>
                  <tbody>${servers
                    .map(
                      (server) => `<tr>
                        <td data-label="服务器"><strong>${escapeHtml(server.name)}</strong>${
                          server.note ? `<small class="field-hint">${escapeHtml(server.note)}</small>` : ''
                        }</td>
                        <td data-label="地址"><code>${escapeHtml(server.host)}:${server.port}</code></td>
                        <td data-label="关联统计">${server.minecraft_server_id ? `<code>${escapeHtml(server.minecraft_server_id)}</code>` : '—'}</td>
                        <td data-label="公开">${server.visible ? '<span class="status-pill online">公开</span>' : '<span class="status-pill offline">隐藏</span>'}</td>
                        <td data-label="排序">${server.sort_order || 0}</td>
                        <td data-label="操作" class="table-actions">
                          <button class="btn btn-ghost btn-sm" data-ld-edit-server="${escapeHtml(server.id)}">${icon('edit')}编辑</button>
                          <button class="btn btn-ghost btn-sm danger-text" data-ld-delete-server="${escapeHtml(server.id)}">${icon('trash')}删除</button>
                        </td>
                      </tr>`
                    )
                    .join('')}</tbody>
                </table></div>`
              : '<div class="ld-empty">还没有官方服务器。</div>'
          }
        </section>

        <section class="panel">
          <div class="panel-head"><div><p class="eyebrow">LEGACY RESOURCES</p><h3>其他资源（旧版外链）</h3></div><button class="btn btn-ghost btn-sm" data-ld-add-resource>${icon('plus')}添加外链</button></div>
          <p class="field-hint">这里保留旧版资源包/光影外链；新整合包请用启动器发布。</p>
          ${
            resources.length
              ? `<div class="table-panel"><table class="data-table">
                  <thead><tr><th>资源</th><th>类型</th><th>版本</th><th>大小</th><th>公开</th><th class="table-actions-col">操作</th></tr></thead>
                  <tbody>${resources
                    .map(
                      (resource) => `<tr>
                        <td data-label="资源"><strong>${escapeHtml(resource.name)}</strong><small class="field-hint">${escapeHtml(resource.url)}</small></td>
                        <td data-label="类型">${escapeHtml(TYPE_LABEL[resource.type] || resource.type)}</td>
                        <td data-label="版本">${escapeHtml(resource.version || '—')}</td>
                        <td data-label="大小">${resource.size ? formatBytes(resource.size) : '—'}</td>
                        <td data-label="公开">${resource.visible ? '<span class="status-pill online">公开</span>' : '<span class="status-pill offline">隐藏</span>'}</td>
                        <td data-label="操作" class="table-actions">
                          <button class="btn btn-ghost btn-sm" data-ld-edit-resource="${escapeHtml(resource.id)}">${icon('edit')}编辑</button>
                          <button class="btn btn-ghost btn-sm danger-text" data-ld-delete-resource="${escapeHtml(resource.id)}">${icon('trash')}删除</button>
                        </td>
                      </tr>`
                    )
                    .join('')}</tbody>
                </table></div>`
              : '<div class="ld-empty">还没有资源。</div>'
          }
        </section>
      </section>`;

    const domainForm = root.querySelector('[data-ld-domain-form]');
    root.querySelector('[data-ld-save-domain]')?.addEventListener('click', async () => {
      if (!domainForm.reportValidity()) return;
      const form = new FormData(domainForm);
      try {
        const next = await api.post('/plugins/launcher-domain/admin/domain', Object.fromEntries(form.entries()));
        toast('域资料已保存');
        adminView(next);
      } catch (error) {
        toast(error.message, 'error');
      }
    });

    root.querySelector('[data-ld-add-server]')?.addEventListener('click', () => openServerEditor(null));
    root.querySelectorAll('[data-ld-edit-server]').forEach((button) => {
      button.addEventListener('click', () => {
        const server = servers.find((item) => item.id === button.dataset.ldEditServer);
        if (server) openServerEditor(server);
      });
    });
    root.querySelectorAll('[data-ld-delete-server]').forEach((button) => {
      button.addEventListener('click', async () => {
        const confirmed = await ui.confirmDialog({
          title: '删除服务器',
          message: '删除后启动器将不再显示该服务器，确定继续吗？',
          confirmText: '删除',
          danger: true,
        });
        if (!confirmed) return;
        try {
          const next = await api.post('/plugins/launcher-domain/admin/server/delete', { id: button.dataset.ldDeleteServer });
          toast('服务器已删除');
          adminView(next);
        } catch (error) {
          toast(error.message, 'error');
        }
      });
    });

    root.querySelector('[data-ld-add-resource]')?.addEventListener('click', () => openResourceEditor(null));
    root.querySelectorAll('[data-ld-edit-resource]').forEach((button) => {
      button.addEventListener('click', () => {
        const resource = resources.find((item) => item.id === button.dataset.ldEditResource);
        if (resource) openResourceEditor(resource);
      });
    });
    root.querySelectorAll('[data-ld-delete-resource]').forEach((button) => {
      button.addEventListener('click', async () => {
        const confirmed = await ui.confirmDialog({
          title: '删除资源',
          message: '删除后启动器将不再显示该资源，确定继续吗？',
          confirmText: '删除',
          danger: true,
        });
        if (!confirmed) return;
        try {
          const next = await api.post('/plugins/launcher-domain/admin/resource/delete', { id: button.dataset.ldDeleteResource });
          toast('资源已删除');
          adminView(next);
        } catch (error) {
          toast(error.message, 'error');
        }
      });
    });

    async function reloadAdmin() {
      try {
        adminView(await api.get('/plugins/launcher-domain/admin/overview'));
      } catch (error) {
        toast(error.message, 'error');
      }
    }

    root.querySelector('[data-ld-refresh-packs]')?.addEventListener('click', () => reloadAdmin());

    root.querySelector('[data-ld-add-token]')?.addEventListener('click', () => {
      const content = document.createElement('div');
      content.innerHTML = `
        <form class="form-stack">
          <label class="field"><span>令牌名称</span><input name="name" maxlength="40" value="启动器发布令牌" required /></label>
          <label class="field"><span>有效期</span>
            <select name="expiresInDays">
              <option value="30">30 天</option>
              <option value="90" selected>90 天</option>
              <option value="365">1 年</option>
              <option value="0">永久</option>
            </select>
          </label>
          <p class="field-hint">生成后只显示一次，请立即复制到启动器。</p>
        </form>`;
      ui.openModal({
        title: '生成发布令牌',
        content,
        size: 'sm',
        actions: [
          { label: '取消', className: 'btn-ghost' },
          {
            label: '生成',
            className: 'btn-primary',
            onClick: async ({ close, button }) => {
              const form = content.querySelector('form');
              if (!form.reportValidity()) return false;
              ui.setButtonLoading(button, true, '生成中…');
              try {
                const result = await api.post('/plugins/launcher-domain/admin/pack/token', {
                  name: form.elements.name.value.trim(),
                  expiresInDays: Number(form.elements.expiresInDays.value),
                });
                ui.setButtonLoading(button, false);
                close();
                reloadAdmin();
                const tokenContent = document.createElement('div');
                tokenContent.innerHTML = `
                  <p class="field-hint">把下面的令牌填写到启动器「版本管理 → 发布到域」中。关闭后无法再次查看。</p>
                  <div class="device-code-box" style="margin-top:10px"><code style="word-break:break-all">${escapeHtml(result.token)}</code></div>`;
                ui.openModal({
                  title: '发布令牌已生成',
                  content: tokenContent,
                  actions: [
                    { label: '关闭', className: 'btn-ghost' },
                    { label: '复制令牌', className: 'btn-primary', onClick: () => ui.copyText(result.token, '发布令牌已复制') },
                  ],
                });
              } catch (error) {
                ui.setButtonLoading(button, false);
                toast(error.message, 'error');
                return false;
              }
              return true;
            },
          },
        ],
      });
    });

    root.querySelectorAll('[data-ld-delete-token]').forEach((button) => {
      button.addEventListener('click', async () => {
        const confirmed = await ui.confirmDialog({
          title: '吊销发布令牌',
          message: '吊销后使用该令牌的启动器将无法继续发布，确定继续吗？',
          confirmText: '吊销',
          danger: true,
        });
        if (!confirmed) return;
        try {
          await api.post('/plugins/launcher-domain/admin/pack/token/delete', { id: button.dataset.ldDeleteToken });
          toast('发布令牌已吊销');
          reloadAdmin();
        } catch (error) {
          toast(error.message, 'error');
        }
      });
    });

    root.querySelectorAll('[data-ld-pack-versions]').forEach((button) => {
      button.addEventListener('click', async () => {
        const pack = packs.find((item) => item.id === button.dataset.ldPackVersions);
        if (!pack) return;
        try {
          const data = await api.get(`/plugins/launcher-domain/admin/pack/versions?id=${encodeURIComponent(pack.id)}`);
          const versions = data.versions || [];
          const content = document.createElement('div');
          content.innerHTML = versions.length
            ? `<div class="table-panel"><table class="data-table">
                <thead><tr><th>版本</th><th>文件</th><th>大小</th><th>说明</th><th>发布时间</th><th class="table-actions-col">操作</th></tr></thead>
                <tbody>${versions
                  .map(
                    (version) => `<tr>
                      <td data-label="版本"><strong>v${version.version_no}</strong>${version.version_no === pack.latestVersion ? ' <span class="status-pill online">当前</span>' : ''}</td>
                      <td data-label="文件">${version.file_count}</td>
                      <td data-label="大小">${formatBytes(version.total_size)}</td>
                      <td data-label="说明">${escapeHtml(version.notes || '—')}</td>
                      <td data-label="发布时间">${escapeHtml(String(version.created_at || '').slice(0, 16).replace('T', ' '))}</td>
                      <td data-label="操作" class="table-actions">
                        ${version.version_no === pack.latestVersion ? '' : `<button class="btn btn-ghost btn-sm" data-ld-rollback-version="${version.version_no}">回滚到此版本</button>`}
                      </td>
                    </tr>`
                  )
                  .join('')}</tbody>
              </table></div>`
            : '<div class="ld-empty">还没有发布版本。</div>';
          const modal = ui.openModal({
            title: `版本历史 · ${pack.name}`,
            content,
            size: 'lg',
            actions: [{ label: '关闭', className: 'btn-ghost' }],
          });
          content.querySelectorAll('[data-ld-rollback-version]').forEach((rollbackButton) => {
            rollbackButton.addEventListener('click', async () => {
              const version = Number(rollbackButton.dataset.ldRollbackVersion);
              const confirmed = await ui.confirmDialog({
                title: '回滚整合包',
                message: `将最新版本切换到 v${version}？玩家下次进服会更新到该版本。`,
                confirmText: '回滚',
              });
              if (!confirmed) return;
              try {
                await api.post('/plugins/launcher-domain/admin/pack/update', {
                  id: pack.id,
                  action: 'rollback',
                  version,
                });
                toast(`已回滚到 v${version}`);
                if (modal && modal.close) modal.close();
                reloadAdmin();
              } catch (error) {
                toast(error.message, 'error');
              }
            });
          });
        } catch (error) {
          toast(error.message, 'error');
        }
      });
    });

    root.querySelectorAll('[data-ld-pack-visibility]').forEach((button) => {
      button.addEventListener('click', async () => {
        const visible = button.dataset.visible === '1';
        try {
          await api.post('/plugins/launcher-domain/admin/pack/update', {
            id: button.dataset.ldPackVisibility,
            action: 'visibility',
            visible: !visible,
          });
          toast(visible ? '已隐藏整合包' : '已公开整合包');
          reloadAdmin();
        } catch (error) {
          toast(error.message, 'error');
        }
      });
    });

    root.querySelectorAll('[data-ld-pack-delete]').forEach((button) => {
      button.addEventListener('click', async () => {
        const confirmed = await ui.confirmDialog({
          title: '删除整合包',
          message: '删除后该服务器不再提供整合包，玩家无法再安装或更新。确定继续吗？',
          confirmText: '删除',
          danger: true,
        });
        if (!confirmed) return;
        try {
          await api.post('/plugins/launcher-domain/admin/pack/update', {
            id: button.dataset.ldPackDelete,
            action: 'delete',
          });
          toast('整合包已删除');
          reloadAdmin();
        } catch (error) {
          toast(error.message, 'error');
        }
      });
    });

    function openServerEditor(server) {
      const content = document.createElement('div');
      content.innerHTML = `
        <form class="form-stack">
          <input type="hidden" name="id" value="${escapeHtml(server?.id || '')}" />
          <label class="field"><span>名称</span><input name="name" maxlength="60" required value="${escapeHtml(server?.name || '')}" /></label>
          <div class="form-grid two">
            <label class="field"><span>地址</span><input name="host" maxlength="255" required placeholder="mc.example.com" value="${escapeHtml(server?.host || '')}" /></label>
            <label class="field"><span>端口</span><input name="port" type="number" min="1" max="65535" required value="${server?.port || 25565}" /></label>
          </div>
          <label class="field"><span>备注</span><input name="note" maxlength="200" value="${escapeHtml(server?.note || '')}" /></label>
          <div class="form-grid two">
            <label class="field"><span>图标地址</span><input name="icon" maxlength="500" placeholder="留空则不显示" value="${escapeHtml(server?.icon || '')}" /></label>
            <label class="field"><span>排序</span><input name="sortOrder" type="number" min="0" max="9999" value="${server?.sort_order || 0}" /></label>
          </div>
          <label class="field"><span>关联 minecraft-server 服务器 ID</span><input name="minecraftServerId" maxlength="80" placeholder="可选，用于网页显示心跳在线人数" value="${escapeHtml(server?.minecraft_server_id || '')}" /></label>
          <label class="switch-field"><span><strong>公开显示</strong><small>关闭后不进入启动器清单</small></span><input type="checkbox" name="visible" ${server?.visible !== false ? 'checked' : ''} /></label>
        </form>`;
      ui.openModal({
        title: server ? '编辑服务器' : '添加服务器',
        content,
        size: 'md',
        actions: [
          { label: '取消', className: 'btn-ghost' },
          {
            label: '保存',
            className: 'btn-primary',
            onClick: async ({ close, button }) => {
              const form = content.querySelector('form');
              if (!form.reportValidity()) return false;
              const body = Object.fromEntries(new FormData(form).entries());
              body.port = Number(body.port);
              body.sortOrder = Number(body.sortOrder);
              body.visible = form.elements.visible.checked;
              ui.setButtonLoading(button, true, '保存中…');
              try {
                const next = await api.post('/plugins/launcher-domain/admin/server', body);
                ui.setButtonLoading(button, false);
                toast('服务器已保存');
                close();
                adminView(next);
              } catch (error) {
                ui.setButtonLoading(button, false);
                toast(error.message, 'error');
                return false;
              }
              return true;
            },
          },
        ],
      });
    }

    function openResourceEditor(resource) {
      const content = document.createElement('div');
      content.innerHTML = `
        <form class="form-stack">
          <input type="hidden" name="id" value="${escapeHtml(resource?.id || '')}" />
          <label class="field"><span>名称</span><input name="name" maxlength="80" required value="${escapeHtml(resource?.name || '')}" /></label>
          <div class="form-grid two">
            <label class="field"><span>类型</span><select name="type">${Object.entries(TYPE_LABEL)
              .map(
                ([value, label]) =>
                  `<option value="${value}" ${(resource?.type || 'other') === value ? 'selected' : ''}>${label}</option>`
              )
              .join('')}</select></label>
            <label class="field"><span>版本</span><input name="version" maxlength="40" value="${escapeHtml(resource?.version || '')}" /></label>
          </div>
          <label class="field"><span>下载地址</span><input name="url" maxlength="1000" required placeholder="https://... 或 /path" value="${escapeHtml(resource?.url || '')}" /></label>
          <div class="form-grid two">
            <label class="field"><span>SHA-256</span><input name="sha256" maxlength="64" placeholder="可选" value="${escapeHtml(resource?.sha256 || '')}" /></label>
            <label class="field"><span>大小（字节）</span><input name="size" type="number" min="0" value="${resource?.size || 0}" /></label>
          </div>
          <div class="form-grid two">
            <label class="field"><span>图标地址</span><input name="icon" maxlength="500" value="${escapeHtml(resource?.icon || '')}" /></label>
            <label class="field"><span>排序</span><input name="sortOrder" type="number" min="0" max="9999" value="${resource?.sort_order || 0}" /></label>
          </div>
          <label class="field"><span>描述</span><textarea name="description" rows="3" maxlength="500">${escapeHtml(resource?.description || '')}</textarea></label>
          <label class="switch-field"><span><strong>公开显示</strong><small>关闭后不进入启动器清单</small></span><input type="checkbox" name="visible" ${resource?.visible !== false ? 'checked' : ''} /></label>
        </form>`;
      ui.openModal({
        title: resource ? '编辑资源' : '添加资源',
        content,
        size: 'md',
        actions: [
          { label: '取消', className: 'btn-ghost' },
          {
            label: '保存',
            className: 'btn-primary',
            onClick: async ({ close, button }) => {
              const form = content.querySelector('form');
              if (!form.reportValidity()) return false;
              const body = Object.fromEntries(new FormData(form).entries());
              body.size = Number(body.size);
              body.sortOrder = Number(body.sortOrder);
              body.visible = form.elements.visible.checked;
              ui.setButtonLoading(button, true, '保存中…');
              try {
                const next = await api.post('/plugins/launcher-domain/admin/resource', body);
                ui.setButtonLoading(button, false);
                toast('资源已保存');
                close();
                adminView(next);
              } catch (error) {
                ui.setButtonLoading(button, false);
                toast(error.message, 'error');
                return false;
              }
              return true;
            },
          },
        ],
      });
    }
  }

  async function load() {
    try {
      if (adminMode) {
        if (!user?.isAdmin) {
          root.innerHTML = `${STYLE}<div class="empty-state"><h3>需要管理员权限</h3><p>只有管理员可以维护启动器域。</p></div>`;
          return;
        }
        const data = await api.get('/plugins/launcher-domain/admin/overview');
        if (!disposed) adminView(data);
      } else {
        const data = await api.get('/plugins/launcher-domain/manifest');
        if (!disposed) publicView(data);
      }
    } catch (error) {
      if (!disposed) {
        root.innerHTML = `${STYLE}<div class="empty-state"><span class="empty-icon">${icon('layers')}</span><h3>域信息读取失败</h3><p>${escapeHtml(
          error.message
        )}</p></div>`;
      }
    }
  }

  await load();
  return () => {
    disposed = true;
  };
}
