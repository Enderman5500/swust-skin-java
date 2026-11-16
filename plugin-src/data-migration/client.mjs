const BASE = '/plugins/data-migration';
const MAX_UPLOAD = 32 * 1024 * 1024;

const STYLE = `
.dm-alert { display:flex; gap:10px; align-items:flex-start; padding:14px 16px; border-radius:14px; border:1px solid rgba(207,170,85,.42); background:rgba(232,201,122,.14); color:#7a5a1f; font-size:12.5px; line-height:1.75; }
.dm-alert strong { color:#5f4514; }
.dm-panel { margin-top:14px; }
.dm-file { display:flex; align-items:center; justify-content:space-between; gap:14px; padding:18px 20px; border:1px dashed var(--line-strong); border-radius:14px; background:var(--paper); cursor:pointer; transition:border-color .15s ease, background .15s ease; }
.dm-file:hover { border-color:var(--navy); background:rgba(156,195,230,.1); }
.dm-file-name { font-size:13px; color:var(--navy); font-weight:600; word-break:break-all; }
.dm-file-hint { font-size:11px; color:var(--muted); white-space:nowrap; }
.dm-actions { display:flex; align-items:center; gap:10px; flex-wrap:wrap; margin-top:14px; }
.dm-status { margin-top:10px; font-size:12px; color:var(--muted); min-height:18px; }
.dm-status.is-error { color:var(--danger); }
.dm-status.is-success { color:var(--success); }
.dm-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:10px; }
.dm-stat { padding:13px 15px; border-radius:13px; border:1px solid var(--line); background:rgba(255,255,255,.9); }
.dm-stat span { display:block; font-size:10.5px; letter-spacing:.05em; color:var(--muted); }
.dm-stat strong { display:block; margin-top:4px; font-family:var(--font-display); font-size:24px; line-height:1.1; color:var(--navy); }
.dm-stat small { display:block; margin-top:3px; font-size:10.5px; color:var(--muted); }
.dm-ok { padding:12px 14px; border-radius:12px; border:1px solid rgba(60,122,91,.3); background:rgba(60,122,91,.08); color:var(--success); font-size:12.5px; }
.dm-dup-group { margin-top:12px; border:1px solid var(--line); border-radius:13px; overflow:hidden; background:#fff; }
.dm-dup-head { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:11px 14px; background:var(--paper); border-bottom:1px solid var(--line); font-size:12.5px; }
.dm-dup-head strong { color:var(--navy); word-break:break-all; }
.dm-dup-head span { color:var(--muted); font-size:11px; white-space:nowrap; }
.dm-dup-row { display:flex; align-items:center; gap:12px; padding:11px 14px; border-top:1px solid var(--line); cursor:pointer; font-size:12.5px; }
.dm-dup-row:first-of-type { border-top:0; }
.dm-dup-row:hover { background:rgba(156,195,230,.08); }
.dm-dup-row input { accent-color:var(--navy); }
.dm-dup-main { flex:1 1 auto; min-width:0; }
.dm-dup-main b { color:var(--navy); font-weight:600; }
.dm-dup-main small { display:block; margin-top:2px; color:var(--muted); font-size:10.5px; }
.dm-dup-data { color:var(--muted); font-size:10.5px; white-space:nowrap; }
.dm-tag { display:inline-flex; align-items:center; padding:1px 7px; border-radius:999px; font-size:10px; border:1px solid rgba(60,122,91,.3); background:rgba(60,122,91,.08); color:var(--success); margin-left:6px; }
.dm-list { margin:8px 0 0; padding-left:18px; font-size:12px; line-height:1.8; color:var(--muted); }
.dm-list.is-issue { color:var(--danger); }
.dm-code { display:block; margin-top:8px; padding:9px 11px; border-radius:9px; background:var(--paper); border:1px solid var(--line); font-size:11px; color:var(--muted); word-break:break-all; }
.dm-busy { display:inline-flex; align-items:center; gap:6px; font-size:11px; color:var(--muted); }
.dm-busy-dot { width:7px; height:7px; border-radius:50%; background:var(--navy); animation:dm-pulse 1s ease-in-out infinite; }
@keyframes dm-pulse { 0%,100% { opacity:.25; } 50% { opacity:1; } }
.dm-sub { font-size:11.5px; color:var(--muted); line-height:1.8; }
.dm-panel h3 { margin:16px 0 8px; font-family:var(--font-display); font-size:16px; color:var(--navy); }
`;

function sizeText(bytes) {
  const value = Number(bytes) || 0;
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`;
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${value} B`;
}

function num(value) {
  return Number(value || 0).toLocaleString('zh-CN');
}

function friendlyError(error) {
  if (error?.code === 'payload_too_large') {
    return '文件超过应用上传上限（32MB），请拆分或压缩导出包后重试。';
  }
  if (error?.status === 413) {
    return '上传被反向代理拒绝（413 Request Entity Too Large）。请把 Nginx 的 client_max_body_size 调整为 64m 后重试，或改用服务器命令行迁移。';
  }
  return error?.message || '操作失败';
}

export async function mount(root, ctx) {
  const { api, user, toast, confirm, escapeHtml, formatDate } = ctx;

  if (!user?.isAdmin) {
    root.innerHTML = `
      <section class="plugin-shell">
        <div class="empty-state">
          <span class="empty-icon">${ctx.icon ? ctx.icon('plugin') : ''}</span>
          <h2>仅管理员可用</h2>
          <p>数据迁移会写入平台核心数据，只允许管理员操作。</p>
        </div>
      </section>
    `;
    return;
  }

  root.innerHTML = `
    <style>${STYLE}</style>
    <section class="plugin-shell dm">
      <header class="plugin-head">
        <div>
          <p class="eyebrow">PLUGIN · DATA MIGRATION</p>
          <h1>YuDream 数据迁移</h1>
          <p class="plugin-lead">上传 YuDream 导出的 ZIP，先分析再执行。迁移账号初始密码统一为 <code>swustmc123456</code>。</p>
        </div>
        <div class="plugin-head-actions">
          <span class="dm-busy" data-busy-chip hidden><span class="dm-busy-dot"></span>正在执行</span>
        </div>
      </header>

      <div class="dm-alert">
        <div>
          <strong>执行前请确认：</strong>
          迁移会写入用户、角色、部门、角色档案、材质、衣柜与钱包数据。同一邮箱存在多个账号时只会保留一个，请在分析结果中选择要保留的账号。重复邮箱的保留选择在首次导入后应保持一致；迁移不会删除平台上已存在的账号。已自行修改过密码的账号不会被重置。
        </div>
      </div>

      <section class="panel dm-panel">
        <div class="section-heading">
          <h2>1. 选择导出包</h2>
          <p>支持 <code>yudream-data-export-*.zip</code>，最大 ${sizeText(MAX_UPLOAD)}。建议先在导出平台确认导出任务已完成。若返回 413，请将 Nginx 的 <code>client_max_body_size</code> 调整为 64m。</p>
        </div>
        <label class="dm-file">
          <input type="file" accept=".zip,application/zip" data-file hidden>
          <span class="dm-file-name" data-file-label>点击选择 ZIP 导出包</span>
          <span class="dm-file-hint">仅 .zip</span>
        </label>
        <div class="dm-actions">
          <button class="btn btn-primary" type="button" data-action="analyze">分析导出包</button>
          <button class="btn btn-ghost" type="button" data-action="reset">清除</button>
        </div>
        <p class="dm-status" data-status></p>
      </section>

      <section class="panel dm-panel" data-analysis hidden>
        <div class="section-heading">
          <h2 data-phase-title>2. 确认迁移内容</h2>
          <p data-phase-lead>以下为预演结果，尚未写入数据库。确认后点击「开始迁移」。</p>
        </div>
        <div class="dm-grid" data-counts></div>
        <div data-duplicates></div>
        <div data-extra></div>
        <div class="dm-actions">
          <button class="btn btn-danger" type="button" data-action="run">开始迁移</button>
          <button class="btn btn-ghost" type="button" data-action="analyze-again">重新分析</button>
        </div>
      </section>

      <section class="panel dm-panel" data-last hidden>
        <div class="section-heading">
          <h2>最近一次迁移</h2>
          <p data-last-time></p>
        </div>
        <div data-last-body></div>
      </section>
    </section>
  `;

  const fileInput = root.querySelector('[data-file]');
  const fileLabel = root.querySelector('[data-file-label]');
  const analysisPanel = root.querySelector('[data-analysis]');
  const countsEl = root.querySelector('[data-counts]');
  const duplicatesEl = root.querySelector('[data-duplicates]');
  const extraEl = root.querySelector('[data-extra]');
  const statusEl = root.querySelector('[data-status]');
  const phaseTitle = root.querySelector('[data-phase-title]');
  const phaseLead = root.querySelector('[data-phase-lead]');
  const lastPanel = root.querySelector('[data-last]');
  const lastBody = root.querySelector('[data-last-body]');
  const lastTime = root.querySelector('[data-last-time]');

  let file = null;
  let analysis = null;
  let token = null;
  let keep = {};
  let phase = 'analysis';
  let busy = false;

  function setBusy(value) {
    busy = Boolean(value);
    refreshButtons();
    const chip = root.querySelector('[data-busy-chip]');
    if (chip) chip.hidden = !busy;
  }

  function refreshButtons() {
    root.querySelectorAll('[data-action]').forEach((button) => {
      const action = button.dataset.action;
      if (action === 'run') button.disabled = busy || !token;
      else button.disabled = busy;
    });
    fileInput.disabled = busy;
  }

  function showStatus(message, kind = '') {
    statusEl.textContent = message || '';
    statusEl.className = `dm-status${kind ? ` is-${kind}` : ''}`;
  }

  function setFile(next) {
    file = next;
    if (!file) {
      fileInput.value = '';
      fileLabel.textContent = '点击选择 ZIP 导出包';
    } else {
      fileLabel.textContent = `${file.name} · ${sizeText(file.size)}`;
    }
    analysis = null;
    token = null;
    keep = {};
    analysisPanel.hidden = true;
    showStatus('');
    refreshButtons();
  }

  function displayCount(result, key, verifiedKey) {
    const imported = result.importedCounts || {};
    if (verifiedKey && imported[verifiedKey] != null) return imported[verifiedKey];
    return imported[key] || 0;
  }

  function sourceCount(result, key, activeKey) {
    const counts = result.sourceCounts || {};
    return counts[activeKey] ?? counts[key] ?? 0;
  }

  function statCard(label, value, sub) {
    return `<div class="dm-stat"><span>${escapeHtml(label)}</span><strong>${num(value)}</strong><small>${escapeHtml(sub)}</small></div>`;
  }

  function renderCounts(result) {
    const runPhase = phase === 'run';
    const items = [
      ['用户', displayCount(result, 'users', 'verifiedUsers'), sourceCount(result, 'users', 'activeUsers')],
      ['角色档案', displayCount(result, 'skinPlayers', 'verifiedPlayers'), sourceCount(result, 'skinPlayers', 'activePlayers')],
      ['材质', displayCount(result, 'skinTextures', 'verifiedTextures'), sourceCount(result, 'skinTextures', 'parsedTextures')],
      ['衣柜', displayCount(result, 'skinClosetItems', 'verifiedClosetItems'), sourceCount(result, 'skinClosetItems', 'activeClosetItems')],
      ['钱包余额', displayCount(result, 'walletBalances', 'verifiedWalletBalances'), sourceCount(result, 'walletBalances')],
      ['钱包流水', displayCount(result, 'walletTransactions', 'verifiedWalletTransactions'), sourceCount(result, 'walletTransactions')],
    ];
    countsEl.innerHTML = items
      .map(([label, value, total]) =>
        statCard(label, value, runPhase ? `库中已校验 · 源 ${num(total)}` : `预演结果 · 源 ${num(total)}`)
      )
      .join('');
  }

  function renderDuplicates(result) {
    const groups = Array.isArray(result.duplicateEmails) ? result.duplicateEmails : [];
    if (!groups.length) {
      duplicatesEl.innerHTML = '<h3>同一邮箱账号</h3><div class="dm-ok">没有发现同一邮箱下的多个账号。</div>';
      return;
    }
    const cards = groups
      .map((group, index) => {
        const selected = keep[group.email] || group.keepUserId || group.recommendedUserId;
        const rows = (group.accounts || [])
          .map((account) => {
            const isRecommended = Boolean(account.recommended);
            const data = [
              `角色 ${num(account.playerCount)}`,
              `衣柜 ${num(account.closetCount)}`,
              `余额 ${num(account.walletBalanceCount)}`,
              `流水 ${num(account.walletTransactionCount)}`,
            ].join(' · ');
            return `
              <label class="dm-dup-row">
                <input type="radio" name="dm-keep-${index}" value="${escapeHtml(account.userId)}"
                  data-dup-email="${escapeHtml(group.email)}" ${account.userId === selected ? 'checked' : ''}>
                <span class="dm-dup-main">
                  <b>${escapeHtml(account.username || '未命名账号')}</b>
                  ${isRecommended ? '<span class="dm-tag">推荐</span>' : ''}
                  <small>${escapeHtml(account.nickname || '—')} · ${escapeHtml(account.userId)}</small>
                </span>
                <span class="dm-dup-data">${escapeHtml(data)}</span>
              </label>
            `;
          })
          .join('');
        return `
          <div class="dm-dup-group" data-email="${escapeHtml(group.email)}">
            <div class="dm-dup-head">
              <strong>${escapeHtml(group.email)}</strong>
              <span>共 ${group.accounts?.length || 0} 个账号，只保留一个</span>
            </div>
            ${rows}
          </div>
        `;
      })
      .join('');
    duplicatesEl.innerHTML = `
      <h3>同一邮箱账号</h3>
      <p class="dm-sub">每个邮箱只能保留一个账号。默认推荐关联数据最多的账号；也可以手动选择。未保留账号的角色、衣柜与角色分配不会导入，钱包记录按原用户 ID 保留。</p>
      ${cards}
    `;
    duplicatesEl.querySelectorAll('input[data-dup-email]').forEach((input) => {
      input.addEventListener('change', () => {
        if (input.checked) keep[input.dataset.dupEmail] = input.value;
      });
    });
  }

  function renderExtra(result) {
    const issues = Array.isArray(result.issues) ? result.issues : [];
    const notes = Array.isArray(result.notes) ? result.notes : [];
    const parts = [];
    if (issues.length) {
      parts.push(`<h3>需要关注</h3><ul class="dm-list is-issue">${issues
        .map((item) => `<li>${escapeHtml(item)}</li>`)
        .join('')}</ul>`);
    }
    if (notes.length) {
      parts.push(`<h3>说明</h3><ul class="dm-list">${notes
        .map((item) => `<li>${escapeHtml(item)}</li>`)
        .join('')}</ul>`);
    }
    if (result.reportFile) {
      parts.push(`<span class="dm-code">报告文件：${escapeHtml(result.reportFile)}</span>`);
    }
    extraEl.innerHTML = parts.join('');
  }

  function renderAnalysis(result, nextPhase = 'analysis') {
    analysis = result;
    phase = nextPhase;
    if (nextPhase === 'run') {
      phaseTitle.textContent = '迁移结果';
      phaseLead.textContent = '数据已写入平台。可以查看校验数量、问题与报告文件；如无问题，可关闭页面。';
    } else {
      phaseTitle.textContent = '2. 确认迁移内容';
      phaseLead.textContent = '以下为预演结果，尚未写入数据库。确认后点击「开始迁移」。';
    }
    analysisPanel.hidden = false;
    renderCounts(result);
    renderDuplicates(result);
    renderExtra(result);
    refreshButtons();
  }

  function renderLast(status) {
    const report = status?.lastReport;
    if (!report) {
      lastPanel.hidden = true;
      return;
    }
    lastPanel.hidden = false;
    lastTime.textContent = report.generatedAt
      ? `执行时间：${formatDate ? formatDate(report.generatedAt) : report.generatedAt}`
      : status.lastRunAt || '';
    const counts = [
      ['用户', displayCount(report, 'users', 'verifiedUsers')],
      ['角色档案', displayCount(report, 'skinPlayers', 'verifiedPlayers')],
      ['材质', displayCount(report, 'skinTextures', 'verifiedTextures')],
      ['衣柜', displayCount(report, 'skinClosetItems', 'verifiedClosetItems')],
      ['余额', displayCount(report, 'walletBalances', 'verifiedWalletBalances')],
      ['流水', displayCount(report, 'walletTransactions', 'verifiedWalletTransactions')],
    ];
    const issues = Array.isArray(report.issues) ? report.issues : [];
    lastBody.innerHTML = `
      <div class="dm-grid">${counts
        .map(([label, value]) => statCard(label, value, '最近一次迁移校验'))
        .join('')}</div>
      <p class="dm-sub" style="margin-top:12px">批次：${escapeHtml(report.batchId || '—')} · 导出 ID：${escapeHtml(report.exportId || '—')}</p>
      ${issues.length ? `<ul class="dm-list is-issue">${issues.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>` : ''}
      ${report.reportFile ? `<span class="dm-code">报告文件：${escapeHtml(report.reportFile)}</span>` : ''}
    `;
  }

  async function loadStatus() {
    try {
      const data = await api.get(`${BASE}/admin/status`);
      renderLast(data);
    } catch {
      // 状态读取失败不影响迁移
    }
  }

  async function analyze() {
    if (!file) {
      toast('请先选择导出包', 'error');
      return;
    }
    if (file.size > MAX_UPLOAD) {
      toast(`文件超过 ${sizeText(MAX_UPLOAD)}`, 'error');
      return;
    }
    setBusy(true);
    showStatus('正在解析导出包并预演迁移，请稍候…');
    try {
      const buffer = await file.arrayBuffer();
      const data = await api.upload(`${BASE}/admin/analyze`, buffer, 'application/zip');
      token = data.token || null;
      keep = {};
      for (const group of data.duplicateEmails || []) {
        keep[group.email] = group.keepUserId || group.recommendedUserId;
      }
      renderAnalysis(data, 'analysis');
      showStatus('分析完成，请确认迁移内容。', 'success');
    } catch (error) {
      analysis = null;
      token = null;
      analysisPanel.hidden = true;
      const message = friendlyError(error);
      showStatus(message, 'error');
      toast(message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function runMigration() {
    if (!token) {
      toast('请先分析导出包', 'error');
      return;
    }
    const duplicateCount = (analysis?.duplicateEmails || []).length;
    const ok = await confirm({
      title: '开始数据迁移',
      message: `即将写入用户、角色档案、材质、衣柜与钱包数据。迁移账号初始密码为 swustmc123456。${
        duplicateCount ? '同一邮箱的多个账号将按你的选择各保留一个。' : ''
      }此操作会修改平台数据库，确认继续？`,
      confirmText: '开始迁移',
      danger: true,
    });
    if (!ok) return;
    setBusy(true);
    showStatus('正在迁移，请勿关闭页面…');
    try {
      const result = await api.post(`${BASE}/admin/run`, { token, keep });
      token = null;
      renderAnalysis(result, 'run');
      showStatus('迁移完成。', 'success');
      toast('迁移完成', 'success');
      await loadStatus();
    } catch (error) {
      const message = friendlyError(error);
      showStatus(message, 'error');
      toast(message, 'error');
    } finally {
      setBusy(false);
    }
  }

  fileInput.addEventListener('change', () => {
    const next = fileInput.files?.[0] || null;
    if (next && !/\.zip$/i.test(next.name)) {
      toast('请选择 .zip 格式的导出包', 'error');
      setFile(null);
      return;
    }
    setFile(next);
  });

  root.querySelectorAll('[data-action]').forEach((button) => {
    button.addEventListener('click', () => {
      const action = button.dataset.action;
      if (action === 'analyze' || action === 'analyze-again') analyze();
      else if (action === 'run') runMigration();
      else if (action === 'reset') setFile(null);
    });
  });

  refreshButtons();
  await loadStatus();
}
