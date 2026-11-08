import { api } from '../api.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

const MAX_LINES = 3000;
const SOURCE_KEY = 'swust.admin.log.source';

const SOURCES = [
  { id: 'journal-system', label: '全部系统日志', kind: 'journal', title: '全部系统日志' },
  { id: 'journal-service', label: '本站服务', kind: 'journal', title: '本站服务日志' },
  { id: 'app', label: '应用日志', kind: 'file', group: 'app', title: '应用日志' },
  { id: 'nginx-access', label: 'Nginx 访问', kind: 'file', group: 'nginxAccess', title: 'Nginx 访问日志' },
  { id: 'nginx-error', label: 'Nginx 错误', kind: 'file', group: 'nginxError', title: 'Nginx 错误日志' },
];

function formatBytes(bytes) {
  const value = Number(bytes) || 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function severity(line) {
  if (line.includes(' ERROR ')) return 'is-error';
  if (line.includes(' WARN ')) return 'is-warn';
  return '';
}

function sourceDef(id) {
  return SOURCES.find((item) => item.id === id) || SOURCES[0];
}

export async function renderLogs(main, ctx) {
  main.innerHTML = `
    <section class="admin-page-head">
      <div>
        <p class="eyebrow">SYSTEM LOGS</p>
        <h2>系统日志</h2>
        <p>实时查看应用、systemd journald 与 Nginx 日志，并下载历史文件或按时间导出。</p>
        <p class="field-hint">游戏端心跳只记录玩家上线 / 离线，不记录每次上报的玩家明细。</p>
      </div>
      <div class="admin-head-actions">
        <button class="btn btn-ghost" data-action="refresh-logs">${icon('refresh')}刷新</button>
        <a class="btn btn-primary" data-action="download-current" href="#" download>${icon('download')}下载当前日志</a>
      </div>
    </section>

    <div class="log-source-bar" data-log-sources role="tablist" aria-label="日志来源"></div>

    <section class="panel log-terminal-panel">
      <div class="log-terminal-head">
        <div class="log-window-dots" aria-hidden="true"><span></span><span></span><span></span></div>
        <div class="log-terminal-title">
          <strong data-log-title>全部系统日志</strong>
          <span class="log-connection" data-log-connection><i></i>正在连接…</span>
        </div>
        <div class="log-terminal-actions">
          <button class="btn btn-ghost btn-sm" data-action="toggle-scroll">${icon('pause')}暂停滚动</button>
          <button class="btn btn-ghost btn-sm" data-action="clear-log">${icon('trash')}清屏</button>
        </div>
      </div>
      <div class="log-output" data-log-output tabindex="0" aria-label="实时系统日志">
        <div class="log-placeholder">正在连接实时日志…</div>
      </div>
    </section>

    <section class="panel log-archive-panel">
      <div class="panel-head">
        <div><p class="eyebrow">ARCHIVES</p><h3>历史日志</h3></div>
        <span class="field-hint" data-log-count></span>
      </div>
      <div class="table-panel" data-log-archives>
        ${ui.loadingBlock('正在读取日志存档…')}
      </div>
    </section>
  `;

  const output = main.querySelector('[data-log-output]');
  const connection = main.querySelector('[data-log-connection]');
  const sourceBar = main.querySelector('[data-log-sources]');
  const title = main.querySelector('[data-log-title]');
  const archiveHost = main.querySelector('[data-log-archives]');
  const countLabel = main.querySelector('[data-log-count]');
  const downloadCurrent = main.querySelector('[data-action="download-current"]');
  const toggleButton = main.querySelector('[data-action="toggle-scroll"]');
  let data = null;
  let source = SOURCES[0].id;
  let eventSource = null;
  let autoScroll = true;
  let lineCount = 0;

  const setConnection = (state, text) => {
    connection.className = `log-connection ${state}`;
    connection.innerHTML = `<i></i>${ui.escapeHtml(text)}`;
  };

  const scrollToBottom = () => {
    if (autoScroll) output.scrollTop = output.scrollHeight;
  };

  const append = (text) => {
    output.querySelector('.log-placeholder')?.remove();
    const line = document.createElement('div');
    line.className = `log-line ${severity(text)}`.trim();
    line.textContent = text || ' ';
    output.appendChild(line);
    lineCount++;
    while (lineCount > MAX_LINES && output.firstElementChild) {
      output.firstElementChild.remove();
      lineCount--;
    }
    scrollToBottom();
  };

  const journalAvailable = () => data?.journal?.available !== false;
  const available = (id) => {
    if (!id.startsWith('journal')) return true;
    return journalAvailable();
  };

  const renderSources = () => {
    sourceBar.innerHTML = SOURCES.map((item) => {
      const enabled = available(item.id);
      return `<button type="button" class="log-source-tab${item.id === source ? ' is-active' : ''}" data-source="${
        item.id
      }" role="tab" aria-selected="${item.id === source}" ${enabled ? '' : 'disabled title="当前服务账号没有 journald 读取权限"'}>${ui.escapeHtml(
        item.label
      )}</button>`;
    }).join('');
    sourceBar.querySelectorAll('[data-source]').forEach((button) => {
      button.addEventListener('click', () => switchSource(button.dataset.source));
    });
  };

  const groupFor = (def) => {
    if (!data) return { current: null, archives: [] };
    if (def.kind === 'journal') return { current: null, archives: [] };
    return data[def.group] || { current: null, archives: [] };
  };

  const updateDownload = () => {
    const def = sourceDef(source);
    if (def.kind === 'journal') {
      downloadCurrent.href = `/api/admin/logs/download?source=${encodeURIComponent(source)}&hours=24`;
      downloadCurrent.innerHTML = `${icon('download')}导出最近 24 小时`;
      downloadCurrent.classList.remove('is-disabled');
      downloadCurrent.removeAttribute('aria-disabled');
      return;
    }
    const current = groupFor(def).current;
    if (current?.name) {
      downloadCurrent.href = `/api/admin/logs/download?source=${encodeURIComponent(
        source
      )}&file=${encodeURIComponent(current.name)}`;
      downloadCurrent.innerHTML = `${icon('download')}下载当前日志`;
      downloadCurrent.classList.remove('is-disabled');
      downloadCurrent.removeAttribute('aria-disabled');
    } else {
      downloadCurrent.href = '#';
      downloadCurrent.innerHTML = `${icon('download')}下载当前日志`;
      downloadCurrent.classList.add('is-disabled');
      downloadCurrent.setAttribute('aria-disabled', 'true');
    }
  };

  const renderJournals = () => {
    countLabel.textContent = 'journald 持续记录';
    archiveHost.innerHTML = `
      <div class="log-journal-note">
        <p>系统日志由 systemd journald 持续保存，可按时间范围导出。应用日志和 Nginx 轮转文件请切换到对应来源下载。</p>
        <div class="log-export-actions">
          ${[
            [1, '最近 1 小时'],
            [24, '最近 24 小时'],
            [168, '最近 7 天'],
          ]
            .map(
              ([hours, label]) => `
              <a class="btn btn-ghost btn-sm" href="/api/admin/logs/download?source=${encodeURIComponent(
                source
              )}&hours=${hours}" download>${icon('download')}${label}</a>
            `
            )
            .join('')}
        </div>
      </div>
    `;
  };

  const renderArchives = () => {
    const def = sourceDef(source);
    title.textContent = def.title;
    updateDownload();
    if (def.kind === 'journal') {
      renderJournals();
      return;
    }
    const group = groupFor(def);
    const rows = [];
    if (group.current?.name) {
      rows.push(`
        <tr>
          <td data-label="文件"><strong>${ui.escapeHtml(group.current.name)}</strong><small class="field-hint">当前文件</small></td>
          <td data-label="类型"><span class="status-pill online">当前</span></td>
          <td data-label="大小">${formatBytes(group.current.size)}</td>
          <td data-label="更新时间">${group.current.modifiedAt ? ui.timeAgo(group.current.modifiedAt) : '—'}</td>
          <td data-label="操作" class="table-actions">
            <a class="btn btn-ghost btn-sm" href="/api/admin/logs/download?source=${encodeURIComponent(
              source
            )}&file=${encodeURIComponent(group.current.name)}" download>${icon('download')}下载</a>
          </td>
        </tr>
      `);
    }
    for (const archive of group.archives || []) {
      rows.push(`
        <tr>
          <td data-label="文件"><code>${ui.escapeHtml(archive.name)}</code></td>
          <td data-label="类型"><span class="status-pill offline">存档</span></td>
          <td data-label="大小">${formatBytes(archive.size)}</td>
          <td data-label="更新时间">${archive.modifiedAt ? ui.formatDate(archive.modifiedAt, { time: true }) : '—'}</td>
          <td data-label="操作" class="table-actions">
            <a class="btn btn-ghost btn-sm" href="/api/admin/logs/download?source=${encodeURIComponent(
              source
            )}&file=${encodeURIComponent(archive.name)}" download>${icon('download')}下载</a>
          </td>
        </tr>
      `);
    }

    countLabel.textContent = `${group.archives?.length || 0} 个存档`;
    archiveHost.innerHTML = rows.length
      ? `<table class="data-table log-archive-table">
          <thead><tr><th>文件</th><th>类型</th><th>大小</th><th>更新时间</th><th>操作</th></tr></thead>
          <tbody>${rows.join('')}</tbody>
        </table>`
      : '<div class="empty-state compact"><p>当前来源还没有可下载的历史文件。</p></div>';
  };

  const connect = () => {
    eventSource?.close();
    eventSource = new EventSource(
      `/api/admin/logs/stream?source=${encodeURIComponent(source)}&tail=600`
    );
    eventSource.addEventListener('open', () => {
      setConnection('online', '实时连接');
      output.replaceChildren();
      lineCount = 0;
      const placeholder = document.createElement('div');
      placeholder.className = 'log-placeholder';
      placeholder.textContent = '正在接收日志…';
      output.appendChild(placeholder);
    });
    eventSource.addEventListener('log', (event) => append(event.data));
    eventSource.addEventListener('error', () => {
      setConnection('error', '连接中断，正在重连…');
    });
  };

  const switchSource = (next) => {
    if (!available(next) || next === source) return;
    source = next;
    try {
      localStorage.setItem(SOURCE_KEY, source);
    } catch {
      // 忽略无法写入本地存储的情况
    }
    renderSources();
    renderArchives();
    output.replaceChildren();
    lineCount = 0;
    const placeholder = document.createElement('div');
    placeholder.className = 'log-placeholder';
    placeholder.textContent = '正在连接实时日志…';
    output.appendChild(placeholder);
    setConnection('', '正在连接…');
    connect();
  };

  const loadData = async (notify = false) => {
    try {
      data = await api.get('/admin/logs');
      if (!available(source)) source = 'app';
      renderSources();
      renderArchives();
      if (notify) ui.toast('日志列表已刷新', 'success');
    } catch (error) {
      archiveHost.innerHTML = `<div class="empty-state compact"><p>${ui.escapeHtml(
        error.message
      )}</p><button class="btn btn-ghost btn-sm" data-action="refresh-logs">重试</button></div>`;
      archiveHost.querySelector('[data-action="refresh-logs"]')?.addEventListener('click', () => loadData());
      throw error;
    }
  };

  toggleButton.addEventListener('click', () => {
    autoScroll = !autoScroll;
    toggleButton.innerHTML = autoScroll
      ? `${icon('pause')}暂停滚动`
      : `${icon('play')}继续滚动`;
    toggleButton.classList.toggle('is-active', !autoScroll);
    scrollToBottom();
  });
  main.querySelector('[data-action="clear-log"]').addEventListener('click', () => {
    output.replaceChildren();
    lineCount = 0;
    scrollToBottom();
  });
  main.querySelector('[data-action="refresh-logs"]').addEventListener('click', () => loadData(true));

  const stored = localStorage.getItem(SOURCE_KEY);
  if (SOURCES.some((item) => item.id === stored)) source = stored;
  await loadData();
  connect();

  return () => {
    eventSource?.close();
    eventSource = null;
  };
}
