import { api } from '../api.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

const MAX_LINES = 3000;

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

export async function renderLogs(main, ctx) {
  main.innerHTML = `
    <section class="admin-page-head">
      <div>
        <p class="eyebrow">SYSTEM LOGS</p>
        <h2>系统日志</h2>
        <p>实时查看服务终端输出，下载当前或历史存档日志。存档默认保留 30 天。</p>
      </div>
      <div class="admin-head-actions">
        <button class="btn btn-ghost" data-action="refresh-logs">${icon('refresh')}刷新存档</button>
        <a class="btn btn-primary" data-action="download-current" href="#" download>${icon('download')}下载当前日志</a>
      </div>
    </section>

    <section class="panel log-terminal-panel">
      <div class="log-terminal-head">
        <div class="log-window-dots" aria-hidden="true"><span></span><span></span><span></span></div>
        <div class="log-terminal-title">
          <strong>实时终端</strong>
          <span class="log-connection" data-log-connection><i></i>正在连接…</span>
        </div>
        <div class="log-terminal-actions">
          <button class="btn btn-ghost btn-sm" data-action="toggle-scroll">${icon('pause')}暂停滚动</button>
          <button class="btn btn-ghost btn-sm" data-action="clear-log">${icon('trash')}清屏</button>
        </div>
      </div>
      <div class="log-output" data-log-output tabindex="0" aria-label="实时终端日志">
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
  const archiveHost = main.querySelector('[data-log-archives]');
  const countLabel = main.querySelector('[data-log-count]');
  const downloadCurrent = main.querySelector('[data-action="download-current"]');
  const toggleButton = main.querySelector('[data-action="toggle-scroll"]');
  let source = null;
  let autoScroll = true;
  let lineCount = 0;

  const setConnection = (state, text) => {
    connection.className = `log-connection ${state}`;
    connection.innerHTML = `<i></i>${ui.escapeHtml(text)}`;
  };

  const clearPlaceholder = () => {
    output.querySelector('.log-placeholder')?.remove();
  };

  const scrollToBottom = () => {
    if (autoScroll) output.scrollTop = output.scrollHeight;
  };

  const append = (text) => {
    clearPlaceholder();
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

  const renderArchives = (data) => {
    const current = data?.current;
    if (current?.name) {
      downloadCurrent.href = `/api/admin/logs/download?file=${encodeURIComponent(current.name)}`;
      downloadCurrent.classList.remove('is-disabled');
      downloadCurrent.removeAttribute('aria-disabled');
    } else {
      downloadCurrent.href = '#';
      downloadCurrent.classList.add('is-disabled');
      downloadCurrent.setAttribute('aria-disabled', 'true');
    }

    const rows = [];
    if (current?.name) {
      rows.push(`
        <tr>
          <td data-label="文件"><strong>${ui.escapeHtml(current.name)}</strong><small class="field-hint">当前运行日志</small></td>
          <td data-label="类型"><span class="status-pill online">当前</span></td>
          <td data-label="大小">${formatBytes(current.size)}</td>
          <td data-label="更新时间">${current.modifiedAt ? ui.timeAgo(current.modifiedAt) : '—'}</td>
          <td data-label="操作" class="table-actions">
            <a class="btn btn-ghost btn-sm" href="/api/admin/logs/download?file=${encodeURIComponent(
              current.name
            )}" download>${icon('download')}下载</a>
          </td>
        </tr>
      `);
    }
    for (const archive of data?.archives || []) {
      rows.push(`
        <tr>
          <td data-label="文件"><code>${ui.escapeHtml(archive.name)}</code></td>
          <td data-label="类型"><span class="status-pill offline">存档</span></td>
          <td data-label="大小">${formatBytes(archive.size)}</td>
          <td data-label="更新时间">${archive.modifiedAt ? ui.formatDate(archive.modifiedAt, { time: true }) : '—'}</td>
          <td data-label="操作" class="table-actions">
            <a class="btn btn-ghost btn-sm" href="/api/admin/logs/download?file=${encodeURIComponent(
              archive.name
            )}" download>${icon('download')}下载</a>
          </td>
        </tr>
      `);
    }

    countLabel.textContent = `${data?.archives?.length || 0} 个存档`;
    archiveHost.innerHTML = rows.length
      ? `<table class="data-table log-archive-table">
          <thead><tr><th>文件</th><th>类型</th><th>大小</th><th>更新时间</th><th>操作</th></tr></thead>
          <tbody>${rows.join('')}</tbody>
        </table>`
      : '<div class="empty-state compact"><p>还没有历史日志存档。</p></div>';
  };

  const loadArchives = async (notify = false) => {
    try {
      const data = await api.get('/admin/logs');
      renderArchives(data);
      if (notify) ui.toast('日志存档已刷新', 'success');
    } catch (error) {
      archiveHost.innerHTML = `<div class="empty-state compact"><p>${ui.escapeHtml(
        error.message
      )}</p><button class="btn btn-ghost btn-sm" data-action="refresh-logs">重试</button></div>`;
      archiveHost.querySelector('[data-action="refresh-logs"]')?.addEventListener('click', () => loadArchives());
    }
  };

  const connect = () => {
    source?.close();
    source = new EventSource('/api/admin/logs/stream?tail=600');
    source.addEventListener('open', () => {
      setConnection('online', '实时连接');
      output.replaceChildren();
      lineCount = 0;
      const placeholder = document.createElement('div');
      placeholder.className = 'log-placeholder';
      placeholder.textContent = '正在接收日志…';
      output.appendChild(placeholder);
    });
    source.addEventListener('log', (event) => {
      append(event.data);
    });
    source.addEventListener('error', () => {
      setConnection('error', '连接中断，正在重连…');
    });
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
  main.querySelector('[data-action="refresh-logs"]').addEventListener('click', () => loadArchives(true));

  await loadArchives();
  connect();

  return () => {
    source?.close();
    source = null;
  };
}
