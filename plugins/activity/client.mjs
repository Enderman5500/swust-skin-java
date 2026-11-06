const STATUS_LABELS = { DRAFT: '草稿', OPEN: '开放报名', CLOSED: '已结束' };
const STATUS_CLASS = { DRAFT: '', OPEN: 'is-open', CLOSED: 'is-closed' };
const VERIFY_LABELS = { PENDING: '待核验', PASSED: '已通过', REJECTED: '未通过' };
const VERIFY_CLASS = { PENDING: 'is-pending', PASSED: 'is-passed', REJECTED: 'is-rejected' };
const SOURCE_LABELS = { SELF: '自助报名', MANUAL: '管理员添加', SERVER: '服务器同步' };
const ELIGIBILITY_LABELS = {
  ELIGIBLE: '可生成证明',
  SENIOR: '大四',
  GRADUATED: '已毕业',
  INVALID: '学号无效',
  NONE: '未填写',
};

const STYLE = `
.acv-tabs { display: inline-flex; gap: 6px; padding: 4px; border-radius: 999px; background: var(--paper); border: 1px solid var(--line); }
.acv-tabs .chip { border: 0; background: transparent; }
.acv-tabs .chip.is-active { background: var(--navy); color: #fff; }
.acv-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 20px; }
.acv-toolbar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.acv-toolbar .search-box { min-width: 220px; max-width: 320px; }
.acv-toolbar .search-box input { min-width: 0; }
.acv-toolbar .select-box select { min-width: 108px; }
.acv-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
.acv-card { display: flex; flex-direction: column; gap: 10px; padding: 20px; border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.88); }
.acv-card-top { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.acv-card h3 { font-family: var(--font-display); font-size: 21px; color: var(--navy); line-height: 1.35; }
.acv-card p { font-size: 12px; color: var(--muted); line-height: 1.75; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.acv-card-meta { display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--muted); }
.acv-card-meta span { display: flex; align-items: center; gap: 6px; }
.acv-card-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: auto; padding-top: 6px; }
.acv-badge { display: inline-flex; align-items: center; gap: 5px; padding: 2px 9px; border-radius: 999px; border: 1px solid var(--line); background: #fff; color: var(--muted); font-size: 10px; white-space: nowrap; }
.acv-badge.is-open { color: var(--success); border-color: rgba(60,122,91,.3); background: rgba(60,122,91,.08); }
.acv-badge.is-closed { color: var(--muted); }
.acv-badge.is-pending { color: #a1752c; border-color: rgba(207,170,85,.4); background: rgba(232,201,122,.14); }
.acv-badge.is-passed { color: var(--success); border-color: rgba(60,122,91,.3); background: rgba(60,122,91,.08); }
.acv-badge.is-rejected { color: var(--danger); border-color: rgba(180,75,75,.3); background: rgba(180,75,75,.08); }
.acv-panel { padding: 20px; border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.82); }
.acv-panel + .acv-panel { margin-top: 16px; }
.acv-panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; flex-wrap: wrap; margin-bottom: 16px; }
.acv-panel-head h2 { font-family: var(--font-display); font-size: 22px; color: var(--navy); }
.acv-panel-head p { font-size: 11.5px; color: var(--muted); margin-top: 3px; }
.acv-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.acv-field { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.acv-field span { font-size: 10.5px; font-weight: 700; color: var(--muted); }
.acv-field input, .acv-field textarea, .acv-field select { width: 100%; border: 1px solid var(--line); border-radius: 9px; background: #fff; padding: 9px 11px; font-size: 12.5px; color: var(--text); }
.acv-field textarea { min-height: 92px; resize: vertical; }
.acv-field input:disabled { background: var(--paper); color: var(--muted); }
.acv-field.is-wide { grid-column: 1 / -1; }
.acv-switch { display: inline-flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--navy); font-weight: 600; }
.acv-switch input { width: 16px; height: 16px; }
.btn[hidden] { display: none !important; }
.acv-identity-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-top: 14px; }
.acv-identity-footer .acv-elig { flex: 1; min-width: 260px; margin-top: 0; }
.acv-elig { margin-top: 12px; padding: 10px 12px; border-radius: 10px; border: 1px dashed var(--line-strong); background: var(--paper); font-size: 11.5px; color: var(--muted); }
.acv-elig.is-warn { border-color: rgba(180,75,75,.3); background: rgba(180,75,75,.06); color: var(--danger); }
.acv-elig.is-ok { border-color: rgba(60,122,91,.3); background: rgba(60,122,91,.06); color: var(--success); }
.acv-table-wrap { overflow: auto; border-radius: 12px; border: 1px solid var(--line); }
.acv-table { width: 100%; border-collapse: collapse; font-size: 12px; min-width: 720px; }
.acv-table th { text-align: left; padding: 10px 12px; background: var(--paper); color: var(--muted); font-size: 10px; letter-spacing: .08em; text-transform: uppercase; white-space: nowrap; }
.acv-table td { padding: 10px 12px; border-top: 1px solid var(--line); vertical-align: middle; }
.acv-table tr:hover td { background: rgba(156,195,230,.07); }
.acv-table code { font-family: var(--font-mono); font-size: 11px; }
.acv-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.8); overflow: hidden; margin-bottom: 16px; }
.acv-stat { padding: 14px 16px; border-right: 1px solid var(--line); display: flex; flex-direction: column; gap: 2px; }
.acv-stat:last-child { border-right: 0; }
.acv-stat span { font-size: 10px; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); }
.acv-stat strong { font-family: var(--font-display); font-size: 22px; color: var(--navy); }
.acv-pager { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 18px; font-size: 12px; color: var(--muted); }
.acv-empty { padding: 56px 24px; text-align: center; border-radius: var(--radius); border: 1px dashed var(--line-strong); background: rgba(255,255,255,.6); color: var(--muted); }
.acv-empty h3 { font-family: var(--font-display); font-size: 22px; color: var(--navy); margin-bottom: 8px; }
.acv-empty p { font-size: 12.5px; max-width: 520px; margin: 0 auto 16px; line-height: 1.8; }
.acv-subtabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 18px; }
.acv-overlay { position: fixed; inset: 0; z-index: 120; display: flex; align-items: center; justify-content: center; padding: 24px; background: rgba(9,21,34,.45); backdrop-filter: blur(3px); }
.acv-dialog { width: min(760px, 100%); max-height: min(88vh, 920px); overflow: auto; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-lg); }
.acv-dialog.is-wide { width: min(980px, 100%); }
.acv-dialog-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; padding: 20px 24px 14px; border-bottom: 1px solid var(--line); position: sticky; top: 0; background: #fff; z-index: 1; }
.acv-dialog-head h2 { font-family: var(--font-display); font-size: 24px; color: var(--navy); }
.acv-dialog-head small { font-size: 11px; color: var(--muted); }
.acv-dialog-body { padding: 20px 24px 26px; display: flex; flex-direction: column; gap: 16px; }
.acv-dialog-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 4px; flex-wrap: wrap; }
.acv-detail-meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; font-size: 11.5px; color: var(--muted); }
.acv-detail-meta strong { color: var(--navy); font-weight: 600; }
.acv-detail-desc { font-size: 12.5px; line-height: 1.9; color: var(--text); white-space: pre-wrap; }
.acv-check-row { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border: 1px solid var(--line); border-radius: 9px; background: #fff; font-size: 12px; }
.acv-check-row input { width: 15px; height: 15px; }
.acv-activity-picker { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 8px; max-height: 260px; overflow: auto; }
.acv-preview-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.acv-note { font-size: 11px; color: var(--muted); line-height: 1.75; }
.acv-note code { font-family: var(--font-mono); font-size: 10px; }
.acv-row-actions { display: flex; gap: 6px; flex-wrap: wrap; }
.acv-row-actions .btn { height: 28px; padding: 0 10px; font-size: 11px; }
.acv-inline-input { width: 100%; min-width: 90px; border: 1px solid var(--line); border-radius: 7px; padding: 6px 8px; font-size: 12px; background: #fff; }
.acv-picker-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 40px; padding: 5px 10px; border: 1px solid var(--line); border-radius: 9px; background: #fff; font-size: 12.5px; color: var(--muted); }
.acv-picker-row > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.acv-file-picker { display: flex; align-items: center; gap: 10px; min-height: 40px; padding: 4px 6px; border: 1px solid var(--line); border-radius: 9px; background: #fff; cursor: pointer; }
.acv-file-picker:hover { border-color: var(--line-strong); }
.acv-file-name { flex: 1; min-width: 0; font-size: 12.5px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.acv-paragraphs { display: flex; flex-direction: column; gap: 10px; }
.acv-paragraphs textarea { min-height: 58px; font-size: 12.5px; line-height: 1.6; }
.acv-template { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 12px 14px; border-radius: 10px; border: 1px solid var(--line); background: var(--paper); font-size: 12px; color: var(--muted); }
.acv-template strong { color: var(--navy); }
@media (max-width: 860px) {
  .acv-form-grid, .acv-preview-grid, .acv-detail-meta { grid-template-columns: 1fr; }
  .acv-stats { grid-template-columns: repeat(2, 1fr); }
  .acv-stat:nth-child(2) { border-right: 0; }
  .acv-stat:nth-child(-n+2) { border-bottom: 1px solid var(--line); }
  .acv-toolbar .search-box { width: 100%; max-width: none; }
}
`;

export async function mount(root, ctx) {
  const { api, user, toast, confirm, escapeHtml: esc, formatDate, navigate } = ctx;
  const isAdmin = Boolean(user?.isAdmin);
  const base = '/plugins/activity';

  let disposed = false;
  let tab = ctx.query?.tab === 'admin' && isAdmin ? 'admin' : ctx.query?.tab === 'mine' && user ? 'mine' : 'square';
  let searchTimer = null;

  const square = { keyword: '', status: '', items: [] };
  const mine = { data: null, stamped: { items: [] }, continueSelected: false, sub: 'identity' };
  const admin = {
    sub: 'activities',
    activities: { keyword: '', status: '', page: 1, size: 20, data: null },
    verify: { activityId: '', keyword: '', verify: '', page: 1, data: null, selected: new Set(), options: [] },
    proof: { settings: null, exports: null, fixed: [], stamped: [], selected: new Set(), mode: 'any', preview: null, activities: [] },
    identities: { keyword: '', eligibility: '', page: 1, data: null },
  };

  injectStyle();
  root.innerHTML = `
    <section class="plugin-shell acv">
      <header class="plugin-head">
        <div>
          <p class="eyebrow">PLUGIN · ACTIVITY</p>
          <h1>社团活动</h1>
          <p class="plugin-lead">报名社团 Minecraft 活动，管理员核验参与后可按模板生成参与证明。</p>
        </div>
        <div class="plugin-head-actions">
          <div class="acv-tabs" role="tablist">
            <button class="chip is-active" data-tab="square" role="tab">活动广场</button>
            ${user ? '<button class="chip" data-tab="mine" role="tab">我的活动</button>' : ''}
            ${isAdmin ? '<button class="chip" data-tab="admin" role="tab">活动管理</button>' : ''}
          </div>
        </div>
      </header>
      <div data-view></div>
    </section>
  `;
  const view = root.querySelector('[data-view]');

  function injectStyle() {
    if (document.getElementById('acv-style')) return;
    const style = document.createElement('style');
    style.id = 'acv-style';
    style.textContent = STYLE;
    document.head.append(style);
  }

  function timeText(value) {
    if (!value) return '待定';
    return formatDate(value, { time: true });
  }

  function todayLabel() {
    const now = new Date();
    return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`;
  }

  function statusBadge(status) {
    return `<span class="acv-badge ${STATUS_CLASS[status] || ''}">${esc(STATUS_LABELS[status] || status)}</span>`;
  }

  function verifyBadge(verify) {
    return `<span class="acv-badge ${VERIFY_CLASS[verify] || ''}">${esc(VERIFY_LABELS[verify] || verify)}</span>`;
  }

  function eligibilityOf(studentNo, graduationYear) {
    const value = String(studentNo || '').trim();
    if (!value) return { status: 'NONE', label: '未填写学号', allowed: false, grade: 0, enrollmentYear: 0, graduationYear: 0 };
    if (!/^51\d{8}$/.test(value)) {
      return { status: 'INVALID', label: '学号格式无效', allowed: false, grade: 0, enrollmentYear: 0, graduationYear: 0 };
    }
    const enrollmentYear = Number(value.slice(2, 6));
    const now = new Date();
    const academic = now.getMonth() + 1 >= 9 ? now.getFullYear() : now.getFullYear() - 1;
    let grad = Number(graduationYear) || 0;
    if (grad < enrollmentYear || grad > enrollmentYear + 10) grad = enrollmentYear + 4;
    const grade = academic - enrollmentYear + 1;
    if (enrollmentYear < 2000 || grade < 1) {
      return { status: 'INVALID', label: '学号年份无效', allowed: false, grade: 0, enrollmentYear, graduationYear: grad };
    }
    const cutoff = new Date(grad, 7, 31, 23, 59, 59);
    if (now > cutoff) {
      return { status: 'GRADUATED', label: '已毕业', allowed: false, grade, enrollmentYear, graduationYear: grad };
    }
    if (grade >= 4) {
      return { status: 'SENIOR', label: '大四', allowed: false, grade, enrollmentYear, graduationYear: grad };
    }
    return {
      status: 'ELIGIBLE',
      label: grade === 1 ? '大一' : grade === 2 ? '大二' : '大三',
      allowed: true,
      grade,
      enrollmentYear,
      graduationYear: grad,
    };
  }

  function identityHint(proofOptIn, eligibility, continueSelected) {
    const expired = eligibility.status === 'SENIOR' || eligibility.status === 'GRADUATED';
    if (expired) {
      if (continueSelected) return '已选择继续参与活动证明，保存后仍会进入证明名单。';
      return `已过默认参与时间段（${eligibility.label}），默认不生成活动证明；可点击「继续参与」手动加入。`;
    }
    if (!proofOptIn) return '当前未参与活动证明，不会进入证明名单。';
    const endYear = eligibility.graduationYear ? eligibility.graduationYear - 1 : 0;
    return endYear
      ? `默认参与活动证明，预计于 ${endYear} 年 8 月 31 日后不再默认参与。`
      : '默认参与活动证明，填写学号后显示停止参与时间。';
  }

  function requireLogin() {
    toast('请先登录后再报名活动', 'info');
    if (navigate) navigate('/login?next=' + encodeURIComponent('/plugin/activity'));
  }

  function openModal({ title, subtitle = '', body, wide = false, actions = [] }) {
    const overlay = document.createElement('div');
    overlay.className = 'acv-overlay';
    overlay.innerHTML = `
      <div class="acv-dialog ${wide ? 'is-wide' : ''}" role="dialog" aria-modal="true">
        <div class="acv-dialog-head">
          <div><h2>${esc(title)}</h2>${subtitle ? `<small>${esc(subtitle)}</small>` : ''}</div>
          <button class="btn btn-ghost btn-sm" data-close>关闭</button>
        </div>
        <div class="acv-dialog-body">${body}</div>
      </div>`;
    document.body.append(overlay);
    const close = () => {
      document.removeEventListener('keydown', onKey);
      overlay.remove();
    };
    const onKey = (event) => {
      if (event.key === 'Escape') close();
    };
    document.addEventListener('keydown', onKey);
    overlay.addEventListener('click', (event) => {
      if (event.target === overlay || event.target.closest('[data-close]')) close();
    });
    const actionHost = overlay.querySelector('.acv-dialog-actions');
    if (!actionHost && actions.length) {
      const host = document.createElement('div');
      host.className = 'acv-dialog-actions';
      overlay.querySelector('.acv-dialog-body').append(host);
    }
    for (const action of actions) {
      const button = document.createElement('button');
      button.className = `btn ${action.className || 'btn-primary'}`;
      button.textContent = action.label;
      button.addEventListener('click', () => action.onClick?.(close));
      overlay.querySelector('.acv-dialog-actions').append(button);
    }
    return { overlay, close };
  }

  async function download(responsePromise, fallbackName) {
    const response = await responsePromise;
    const blob = await response.blob();
    const disposition = response.headers.get('content-disposition') || '';
    let filename = fallbackName;
    const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (encoded) {
      try {
        filename = decodeURIComponent(encoded[1]);
      } catch {
        filename = fallbackName;
      }
    } else {
      const plain = disposition.match(/filename="([^"]+)"/i);
      if (plain) filename = plain[1];
    }
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function renderView() {
    if (disposed) return;
    root.querySelectorAll('[data-tab]').forEach((button) => {
      button.classList.toggle('is-active', button.dataset.tab === tab);
    });
    if (tab === 'admin') renderAdmin();
    else if (tab === 'mine') renderMine();
    else renderSquare();
  }

  // ================================================================ 活动广场

  async function renderSquare() {
    view.innerHTML = `
      <div class="acv-toolbar">
        <div class="filter-chips">
          ${['', 'OPEN', 'CLOSED']
            .map(
              (value) =>
                `<button class="chip ${square.status === value ? 'is-active' : ''}" data-square-status="${value}">${
                  value ? STATUS_LABELS[value] : '全部活动'
                }</button>`
            )
            .join('')}
        </div>
        <div class="acv-toolbar-right">
          <label class="search-box"><input type="search" data-square-search placeholder="搜索活动名称" value="${esc(
            square.keyword
          )}" /></label>
        </div>
      </div>
      <div data-square-list><div class="plugin-loading">正在读取活动…</div></div>
    `;
    await loadSquare();
  }

  async function loadSquare() {
    const host = view.querySelector('[data-square-list]');
    if (!host) return;
    try {
      const data = await api.get(`${base}/activities`);
      if (disposed) return;
      square.items = data.items || [];
      paintSquare();
    } catch (error) {
      host.innerHTML = `<div class="acv-empty"><h3>活动读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintSquare() {
    const host = view.querySelector('[data-square-list]');
    if (!host) return;
    const keyword = square.keyword.trim().toLowerCase();
    const items = square.items.filter((item) => {
      if (square.status && item.status !== square.status) return false;
      if (keyword && !String(item.title || '').toLowerCase().includes(keyword)) return false;
      return true;
    });
    if (!items.length) {
      host.innerHTML = `<div class="acv-empty"><h3>暂无活动</h3><p>新的社团活动发布后会出现在这里。</p></div>`;
      return;
    }
    host.innerHTML = `<div class="acv-grid">${items.map(activityCard).join('')}</div>`;
    host.querySelectorAll('[data-open-activity]').forEach((button) => {
      button.addEventListener('click', () => openActivity(button.dataset.openActivity));
    });
    host.querySelectorAll('[data-signup]').forEach((button) => {
      button.addEventListener('click', () => signup(button.dataset.signup));
    });
    host.querySelectorAll('[data-cancel]').forEach((button) => {
      button.addEventListener('click', () => cancelSignup(button.dataset.cancel));
    });
    host.querySelectorAll('[data-login]').forEach((button) => {
      button.addEventListener('click', () => requireLogin());
    });
  }

  function activityCard(item) {
    const mine = item.mine;
    const joined = mine && mine.status === 'JOINED';
    const actions = [
      `<button class="btn btn-ghost btn-sm" data-open-activity="${esc(item.id)}">详情</button>`,
      user
        ? joined
          ? `<button class="btn btn-ghost btn-sm" data-cancel="${esc(item.id)}">取消报名</button>`
          : item.signupOpen
            ? `<button class="btn btn-primary btn-sm" data-signup="${esc(item.id)}">立即报名</button>`
            : `<span class="acv-badge">报名未开放</span>`
        : `<button class="btn btn-primary btn-sm" data-login>登录后报名</button>`,
    ].join('');
    return `
      <article class="acv-card">
        <div class="acv-card-top">
          ${statusBadge(item.status)}
          <span class="acv-badge">${item.joinedCount || 0} 人报名${
            item.spotsLeft >= 0 ? ` · 余 ${item.spotsLeft}` : ''
          }</span>
        </div>
        <h3>${esc(item.title)}</h3>
        <p>${esc(item.summary || '社团 Minecraft 活动，点击详情查看说明。')}</p>
        <div class="acv-card-meta">
          <span>时间：${esc(timeText(item.startTime))}${item.endTime ? ` – ${esc(timeText(item.endTime))}` : ''}</span>
          <span>地点：${esc(item.location || '线上 / 待通知')}</span>
          ${joined ? `<span>我的状态：${verifyBadge(mine.verify)}</span>` : ''}
        </div>
        <div class="acv-card-actions">${actions}</div>
      </article>`;
  }

  async function openActivity(id) {
    try {
      const data = await api.get(`${base}/activity`, { id });
      if (disposed) return;
      const activity = data.activity;
      const stats = data.stats;
      const mineRow = data.mine;
      const joined = mineRow && mineRow.status === 'JOINED';
      const body = `
        <div class="acv-detail-meta">
          <div>状态：${statusBadge(activity.status)}</div>
          <div>报名：<strong>${stats.joined}</strong> 人 · 已核验 <strong>${stats.passed}</strong> 人</div>
          <div>时间：<strong>${esc(timeText(activity.startTime))} – ${esc(timeText(activity.endTime))}</strong></div>
          <div>地点：<strong>${esc(activity.location || '线上 / 待通知')}</strong></div>
          <div>报名截止：<strong>${activity.signupEnd ? esc(timeText(activity.signupEnd)) : '活动开始前'}</strong></div>
          <div>名额：<strong>${activity.capacity > 0 ? activity.capacity : '不限'}</strong></div>
        </div>
        <div class="acv-detail-desc">${esc(activity.description || activity.summary || '暂无详细说明。')}</div>
        ${
          joined
            ? `<div class="acv-elig">你已报名，核验状态：${verifyBadge(mineRow.verify)}</div>`
            : ''
        }`;
      const dialog = openModal({
        title: activity.title,
        subtitle: '活动详情',
        body,
      });
      const actionsHost = dialog.overlay.querySelector('.acv-dialog-body');
      const row = document.createElement('div');
      row.className = 'acv-dialog-actions';
      row.innerHTML = `
        ${user
          ? joined
            ? `<button class="btn btn-ghost" data-detail-cancel>取消报名</button>`
            : activity.signupOpen
              ? `<button class="btn btn-primary" data-detail-signup>立即报名</button>`
              : `<span class="acv-badge">报名未开放</span>`
          : `<button class="btn btn-primary" data-detail-login>登录后报名</button>`}
      `;
      actionsHost.append(row);
      row.querySelector('[data-detail-login]')?.addEventListener('click', () => {
        dialog.close();
        requireLogin();
      });
      row.querySelector('[data-detail-signup]')?.addEventListener('click', async () => {
        await signup(activity.id);
        dialog.close();
      });
      row.querySelector('[data-detail-cancel]')?.addEventListener('click', async () => {
        await cancelSignup(activity.id);
        dialog.close();
      });
    } catch (error) {
      toast(error.message || '活动详情读取失败', 'error');
    }
  }

  async function signup(activityId) {
    if (!user) return requireLogin();
    try {
      await api.post(`${base}/signup`, { activityId });
      toast('报名成功，等待管理员核验', 'success');
      await loadSquare();
      if (tab === 'mine') await renderMine();
    } catch (error) {
      toast(error.message || '报名失败', 'error');
    }
  }

  async function cancelSignup(activityId) {
    if (!user) return requireLogin();
    try {
      await api.post(`${base}/cancel`, { activityId });
      toast('已取消报名', 'success');
      await loadSquare();
      if (tab === 'mine') await renderMine();
    } catch (error) {
      toast(error.message || '取消报名失败', 'error');
    }
  }

  // ================================================================ 我的活动

  async function renderMine() {
    if (!user) {
      tab = 'square';
      return renderView();
    }
    view.innerHTML = '<div class="plugin-loading">正在读取我的活动…</div>';
    try {
      const [me, stamped] = await Promise.all([
        api.get(`${base}/me`),
        api.get(`${base}/my/stamped`).catch(() => ({ items: [] })),
      ]);
      mine.data = me;
      mine.stamped = stamped;
      if (disposed || tab !== 'mine') return;
      paintMine();
    } catch (error) {
      view.innerHTML = `<div class="acv-empty"><h3>读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintMine() {
    const data = mine.data;
    const identity = data.identity || {};
    const eligibility = eligibilityOf(identity.studentNo, identity.graduationYear);
    const expired = eligibility.status === 'SENIOR' || eligibility.status === 'GRADUATED';
    mine.continueSelected = Boolean(identity.continueParticipate);
    const effectiveAllowed =
      Boolean(identity.proofOptIn) && (eligibility.allowed || (expired && mine.continueSelected));
    const activities = data.activities || [];
    const proofable = activities.filter((row) => row.verify === 'PASSED' && effectiveAllowed);
    view.innerHTML = `
      <div class="acv-subtabs">
        ${[
          ['identity', '身份信息'],
          ['activities', `我的参与${activities.length ? ` ${activities.length}` : ''}`],
          ['proofs', '活动证明'],
        ]
          .map(
            ([key, label]) =>
              `<button class="chip ${mine.sub === key ? 'is-active' : ''}" data-mine-tab="${key}">${label}</button>`
          )
          .join('')}
      </div>
      <div data-mine-view></div>
    `;
    view.querySelectorAll('[data-mine-tab]').forEach((button) => {
      button.addEventListener('click', () => {
        mine.sub = button.dataset.mineTab;
        paintMine();
      });
    });
    const host = view.querySelector('[data-mine-view]');
    if (mine.sub === 'activities') {
      host.innerHTML = activitiesPanelHtml(activities, proofable);
      bindMineActivities();
    } else if (mine.sub === 'proofs') {
      host.innerHTML = proofsPanelHtml();
      bindMineProofs();
    } else {
      host.innerHTML = identityPanelHtml(identity, eligibility, expired, effectiveAllowed);
      bindMineIdentity();
    }
  }

  function identityPanelHtml(identity, eligibility, expired, effectiveAllowed) {
    return `
      <section class="acv-panel">
        <div class="acv-panel-head">
          <div>
            <h2>活动证明身份</h2>
            <p>只有开启活动证明并填写真实信息，核验通过后才会进入证明名单；默认参与至大三结束，大四及已毕业可手动继续参与。</p>
          </div>
          <span class="acv-badge ${identity.proofOptIn ? 'is-passed' : ''}" data-proof-state>${
            identity.proofOptIn ? '已开启' : '未开启'
          }</span>
        </div>
        <label class="acv-switch"><input type="checkbox" data-proof-optin ${
          identity.proofOptIn ? 'checked' : ''
        } /> 参与活动证明</label>
        <div class="acv-form-grid" style="margin-top:14px">
          <label class="acv-field"><span>姓名 *</span><input type="text" data-field="realName" maxlength="30" value="${esc(
            identity.realName || ''
          )}" placeholder="用于证明文件署名" /></label>
          <label class="acv-field"><span>学号 *</span><input type="text" data-field="studentNo" maxlength="10" value="${esc(
            identity.studentNo || ''
          )}" placeholder="5120241234" /></label>
          <label class="acv-field"><span>预计毕业年份</span><input type="number" data-field="graduationYear" min="2000" max="2100" value="${esc(
            identity.graduationYear || ''
          )}" placeholder="根据学号自动推算，可修改" /></label>
          <label class="acv-field"><span>专业班级 *</span><input type="text" data-field="className" maxlength="60" value="${esc(
            identity.className || ''
          )}" placeholder="例如 软件2301" /></label>
          <label class="acv-field"><span>学院</span><input type="text" data-field="college" maxlength="60" value="${esc(
            identity.college || ''
          )}" placeholder="例如 计算机科学与技术学院" /></label>
          <label class="acv-field"><span>专业</span><input type="text" data-field="major" maxlength="60" value="${esc(
            identity.major || ''
          )}" placeholder="例如 软件工程" /></label>
        </div>
        <div class="acv-identity-footer">
          <div class="acv-elig ${
            expired ? (mine.continueSelected ? 'is-ok' : 'is-warn') : effectiveAllowed ? 'is-ok' : ''
          }" data-eligibility>
            ${esc(identityHint(identity.proofOptIn, eligibility, mine.continueSelected))}
          </div>
          <div class="acv-row-actions">
            <button class="btn btn-ghost" data-continue-proof ${
              expired && !mine.continueSelected ? '' : 'hidden'
            }>继续参与</button>
            <button class="btn btn-primary" data-save-identity>保存身份信息</button>
          </div>
        </div>
      </section>`;
  }

  function activitiesPanelHtml(activities, proofable) {
    return `
      <section class="acv-panel">
        <div class="acv-panel-head">
          <div><h2>我的参与</h2><p>管理员核验通过的活动可以生成参与证明。</p></div>
          ${
            proofable.length > 1
              ? `<button class="btn btn-ghost" data-proof-all>生成综合证明（${proofable.length} 项）</button>`
              : ''
          }
        </div>
        ${
          activities.length
            ? `<div class="acv-table-wrap"><table class="acv-table">
                 <thead><tr><th>活动</th><th>时间</th><th>报名时间</th><th>核验状态</th><th>操作</th></tr></thead>
                 <tbody>${activities.map(mineRowHtml).join('')}</tbody>
               </table></div>`
            : `<div class="acv-empty"><h3>还没有报名活动</h3><p>去「活动广场」看看最近有什么活动吧。</p></div>`
        }
      </section>`;
  }

  function proofsPanelHtml() {
    return `
      <section class="acv-panel">
        <div class="acv-panel-head">
          <div><h2>活动证明</h2><p>管理员上传盖章文件后，名单内的参与者可以在这里下载。</p></div>
        </div>
        ${
          mine.stamped?.items?.length
            ? `<div class="acv-table-wrap"><table class="acv-table">
                 <thead><tr><th>证明</th><th>来源</th><th>上传时间</th><th>操作</th></tr></thead>
                 <tbody>${mine.stamped.items
                   .map(
                     (row) => `
                   <tr>
                     <td><strong>${esc(row.title || row.filename)}</strong><br /><span class="acv-note">${esc(
                       row.filename || ''
                     )}</span></td>
                     <td>${row.source === 'EXPORT' ? '活动导出' : '管理员上传'}</td>
                     <td>${row.uploadedAt ? esc(formatDate(row.uploadedAt, { time: true })) : '—'}</td>
                     <td><button class="btn btn-primary btn-sm" data-download-stamped="${esc(
                       row.id
                     )}">下载盖章证明</button></td>
                   </tr>`
                   )
                   .join('')}</tbody>
               </table></div>`
            : '<p class="acv-note">暂无可下载的已盖章证明。</p>'
        }
      </section>`;
  }

  function mineRowHtml(row) {
    const identity = mine.data.identity || {};
    const eligibility = eligibilityOf(identity.studentNo, identity.graduationYear);
    const expired = eligibility.status === 'SENIOR' || eligibility.status === 'GRADUATED';
    const canProof =
      row.verify === 'PASSED' &&
      identity.proofOptIn &&
      (eligibility.allowed || (expired && mine.continueSelected));
    return `
      <tr>
        <td><strong>${esc(row.title)}</strong><br /><span class="acv-note">${esc(row.location || '')}</span></td>
        <td>${esc(timeText(row.startTime))}</td>
        <td>${row.joinedAt ? esc(formatDate(row.joinedAt, { time: true })) : '—'}</td>
        <td>${verifyBadge(row.verify)}${row.verifyNote ? `<br /><span class="acv-note">${esc(row.verifyNote)}</span>` : ''}</td>
        <td><div class="acv-row-actions">
          ${
            canProof
              ? `<button class="btn btn-primary btn-sm" data-proof-one="${esc(row.activityId)}">生成证明</button>`
              : row.verify === 'PASSED'
                ? `<span class="acv-note">需开启证明并填写有效学号</span>`
                : ''
          }
          <button class="btn btn-ghost btn-sm" data-mine-cancel="${esc(row.activityId)}">取消报名</button>
        </div></td>
      </tr>`;
  }

  function bindMineIdentity() {
    const host = view;
    const optIn = host.querySelector('[data-proof-optin]');
    const studentNo = host.querySelector('[data-field="studentNo"]');
    const graduationYear = host.querySelector('[data-field="graduationYear"]');
    const eligibilityHost = host.querySelector('[data-eligibility]');
    const continueButton = host.querySelector('[data-continue-proof]');
    const stateBadge = host.querySelector('[data-proof-state]');
    const syncGraduationRange = () => {
      const value = studentNo.value.trim();
      if (!/^51\d{8}$/.test(value)) {
        graduationYear.removeAttribute('min');
        graduationYear.removeAttribute('max');
        return 0;
      }
      const enroll = Number(value.slice(2, 6));
      graduationYear.min = String(enroll);
      graduationYear.max = String(enroll + 10);
      if (graduationYear.dataset.manual !== '1') graduationYear.value = String(enroll + 4);
      let grad = Number(graduationYear.value) || enroll + 4;
      if (grad < enroll || grad > enroll + 10) {
        grad = enroll + 4;
        graduationYear.value = String(grad);
      }
      return enroll;
    };
    const syncState = (eligibility) => {
      const expired = eligibility.status === 'SENIOR' || eligibility.status === 'GRADUATED';
      if (continueButton) continueButton.hidden = !(expired && !mine.continueSelected);
      const effectiveAllowed =
        optIn.checked && (eligibility.allowed || (expired && mine.continueSelected));
      eligibilityHost.className = `acv-elig ${
        expired ? (mine.continueSelected ? 'is-ok' : 'is-warn') : effectiveAllowed ? 'is-ok' : ''
      }`;
      eligibilityHost.textContent = identityHint(optIn.checked, eligibility, mine.continueSelected);
      if (stateBadge) {
        stateBadge.textContent = effectiveAllowed ? '已开启' : '未开启';
        stateBadge.classList.toggle('is-passed', effectiveAllowed);
      }
    };
    const refreshEligibility = () => syncState(eligibilityOf(studentNo.value.trim(), graduationYear.value));
    studentNo.addEventListener('input', () => {
      syncGraduationRange();
      refreshEligibility();
    });
    graduationYear.addEventListener('input', () => {
      graduationYear.dataset.manual = '1';
      refreshEligibility();
    });
    graduationYear.addEventListener('change', () => {
      const enroll = syncGraduationRange();
      if (enroll) {
        const min = enroll;
        const max = enroll + 10;
        let value = Number(graduationYear.value);
        if (!Number.isFinite(value) || value < min) value = min;
        if (value > max) value = max;
        graduationYear.value = String(value);
      }
      refreshEligibility();
    });
    optIn.addEventListener('change', refreshEligibility);
    continueButton?.addEventListener('click', () => {
      mine.continueSelected = true;
      optIn.checked = true;
      refreshEligibility();
      toast('已选择继续参与活动证明，请保存后生效', 'info');
    });
    syncGraduationRange();
    refreshEligibility();
    host.querySelector('[data-save-identity]').addEventListener('click', async () => {
      const body = { proofOptIn: optIn.checked };
      host.querySelectorAll('[data-field]').forEach((input) => {
        body[input.dataset.field] = input.value.trim();
      });
      body.continueParticipate = Boolean(mine.continueSelected);
      if (body.graduationYear) body.graduationYear = Number(body.graduationYear);
      if (body.proofOptIn && (!body.realName || !body.studentNo || !body.className)) {
        toast('开启活动证明需要填写姓名、学号和专业班级', 'error');
        return;
      }
      if (/^51\d{8}$/.test(body.studentNo || '')) {
        const enroll = Number(body.studentNo.slice(2, 6));
        if (!body.graduationYear) body.graduationYear = enroll + 4;
        if (body.graduationYear < enroll || body.graduationYear > enroll + 10) {
          toast('预计毕业年份应在入学年份至入学年份 + 10 年之间', 'error');
          return;
        }
      }
      try {
        await api.post(`${base}/identity`, body);
        toast('身份信息已保存', 'success');
        await renderMine();
      } catch (error) {
        toast(error.message || '保存失败', 'error');
      }
    });
  }

  function bindMineActivities() {
    const host = view;
    host.querySelectorAll('[data-proof-one]').forEach((button) => {
      button.addEventListener('click', () => generateMyProof([button.dataset.proofOne]));
    });
    host.querySelector('[data-proof-all]')?.addEventListener('click', () => {
      const ids = (mine.data.activities || [])
        .filter((row) => row.verify === 'PASSED')
        .map((row) => row.activityId);
      generateMyProof(ids);
    });
    host.querySelectorAll('[data-mine-cancel]').forEach((button) => {
      button.addEventListener('click', () => cancelSignup(button.dataset.mineCancel));
    });
  }

  function bindMineProofs() {
    view.querySelectorAll('[data-download-stamped]').forEach((button) => {
      button.addEventListener('click', async () => {
        try {
          await download(
            api.raw(`${base}/my/stamped/download?id=${encodeURIComponent(button.dataset.downloadStamped)}`),
            '盖章证明.pdf'
          );
        } catch (error) {
          toast(error.message || '下载失败', 'error');
        }
      });
    });
  }

  async function generateMyProof(activityIds) {
    try {
      await download(
        api.raw(`${base}/my/proof`, { method: 'POST', body: { activityIds } }),
        '活动证明.docx'
      );
      toast('证明文件已开始下载', 'success');
    } catch (error) {
      toast(error.message || '证明生成失败', 'error');
    }
  }

  // ================================================================ 管理端

  async function renderAdmin() {
    if (!isAdmin) {
      tab = 'square';
      return renderView();
    }
    view.innerHTML = `
      <div class="acv-subtabs">
        ${[
          ['activities', '活动管理'],
          ['verify', '参与核验'],
          ['proof', '证明导出'],
          ['identities', '身份名单'],
        ]
          .map(
            ([key, label]) =>
              `<button class="chip ${admin.sub === key ? 'is-active' : ''}" data-admin-tab="${key}">${label}</button>`
          )
          .join('')}
      </div>
      <div data-admin-view></div>
    `;
    view.querySelectorAll('[data-admin-tab]').forEach((button) => {
      button.addEventListener('click', () => {
        admin.sub = button.dataset.adminTab;
        renderAdmin();
      });
    });
    if (admin.sub === 'activities') await renderAdminActivities();
    else if (admin.sub === 'verify') await renderAdminVerify();
    else if (admin.sub === 'proof') await renderAdminProof();
    else await renderAdminIdentities();
  }

  // ---------------- 活动管理 ----------------

  async function renderAdminActivities() {
    const host = view.querySelector('[data-admin-view]');
    host.innerHTML = `
      <div class="acv-toolbar">
        <div class="filter-chips">
          ${['', 'DRAFT', 'OPEN', 'CLOSED']
            .map(
              (value) =>
                `<button class="chip ${admin.activities.status === value ? 'is-active' : ''}" data-admin-status="${value}">${
                  value ? STATUS_LABELS[value] : '全部状态'
                }</button>`
            )
            .join('')}
        </div>
        <div class="acv-toolbar-right">
          <label class="search-box"><input type="search" data-admin-search placeholder="搜索活动名称" value="${esc(
            admin.activities.keyword
          )}" /></label>
          <button class="btn btn-primary" data-new-activity>新建活动</button>
        </div>
      </div>
      <div data-admin-activity-body><div class="plugin-loading">正在读取活动…</div></div>
    `;
    host.querySelector('[data-new-activity]').addEventListener('click', () => openActivityEditor(null));
    host.querySelectorAll('[data-admin-status]').forEach((button) => {
      button.addEventListener('click', () => {
        admin.activities.status = button.dataset.adminStatus;
        admin.activities.page = 1;
        renderAdminActivities();
      });
    });
    host.querySelector('[data-admin-search]').addEventListener('input', (event) => {
      const input = event.target;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        admin.activities.keyword = input.value.trim();
        admin.activities.page = 1;
        renderAdminActivities();
      }, 300);
    });
    await loadAdminActivities();
  }

  async function loadAdminActivities() {
    const host = view.querySelector('[data-admin-activity-body]');
    if (!host) return;
    try {
      const data = await api.get(`${base}/admin/activities`, {
        keyword: admin.activities.keyword,
        status: admin.activities.status,
        page: admin.activities.page,
        size: admin.activities.size,
      });
      admin.activities.data = data;
      paintAdminActivities();
    } catch (error) {
      host.innerHTML = `<div class="acv-empty"><h3>活动读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintAdminActivities() {
    const host = view.querySelector('[data-admin-activity-body]');
    const data = admin.activities.data;
    if (!host || !data) return;
    const totalPages = Math.max(1, Math.ceil(data.total / data.size));
    host.innerHTML = `
      <div class="acv-table-wrap"><table class="acv-table">
        <thead><tr><th>活动</th><th>状态</th><th>活动时间</th><th>报名 / 通过</th><th>操作</th></tr></thead>
        <tbody>
          ${
            data.records.length
              ? data.records
                  .map(
                    (row) => `
              <tr>
                <td><strong>${esc(row.title)}</strong><br /><span class="acv-note">${esc(row.location || '')}</span></td>
                <td>${statusBadge(row.status)}</td>
                <td>${esc(timeText(row.startTime))}</td>
                <td>${row.stats?.joined || 0} / ${row.stats?.passed || 0}</td>
                <td><div class="acv-row-actions">
                  <button class="btn btn-ghost btn-sm" data-edit-activity="${esc(row.id)}">编辑</button>
                  ${
                    row.status === 'DRAFT'
                      ? `<button class="btn btn-primary btn-sm" data-set-status="${esc(row.id)}" data-status="OPEN">发布</button>`
                      : row.status === 'OPEN'
                        ? `<button class="btn btn-ghost btn-sm" data-set-status="${esc(row.id)}" data-status="CLOSED">结束</button>`
                        : `<button class="btn btn-ghost btn-sm" data-set-status="${esc(row.id)}" data-status="OPEN">重新开放</button>`
                  }
                  <button class="btn btn-ghost btn-sm" data-delete-activity="${esc(row.id)}">删除</button>
                </div></td>
              </tr>`
                  )
                  .join('')
              : '<tr><td colspan="5"><span class="acv-note">还没有活动，点击右上角新建。</span></td></tr>'
          }
        </tbody>
      </table></div>
      <div class="acv-pager">
        <button class="btn btn-ghost btn-sm" data-activity-page="${admin.activities.page - 1}" ${
      admin.activities.page <= 1 ? 'disabled' : ''
    }>上一页</button>
        <span>第 ${admin.activities.page} / ${totalPages} 页 · 共 ${data.total} 个活动</span>
        <button class="btn btn-ghost btn-sm" data-activity-page="${admin.activities.page + 1}" ${
      admin.activities.page >= totalPages ? 'disabled' : ''
    }>下一页</button>
      </div>
    `;
    host.querySelectorAll('[data-activity-page]').forEach((button) => {
      button.addEventListener('click', () => {
        admin.activities.page = Number(button.dataset.activityPage);
        loadAdminActivities();
      });
    });
    host.querySelectorAll('[data-edit-activity]').forEach((button) => {
      button.addEventListener('click', () => openActivityEditor(button.dataset.editActivity));
    });
    host.querySelectorAll('[data-set-status]').forEach((button) => {
      button.addEventListener('click', async () => {
        try {
          await api.post(`${base}/admin/activity/status`, {
            id: button.dataset.setStatus,
            status: button.dataset.status,
          });
          toast('活动状态已更新', 'success');
          await loadAdminActivities();
        } catch (error) {
          toast(error.message || '更新失败', 'error');
        }
      });
    });
    host.querySelectorAll('[data-delete-activity]').forEach((button) => {
      button.addEventListener('click', async () => {
        const ok = await confirm({
          title: '删除活动',
          message: '删除活动会同时移除所有报名与核验记录，已导出的证明文件不会删除。',
          confirmText: '删除',
          danger: true,
        });
        if (!ok) return;
        try {
          await api.post(`${base}/admin/activity/delete`, { id: button.dataset.deleteActivity });
          toast('活动已删除', 'success');
          await loadAdminActivities();
        } catch (error) {
          toast(error.message || '删除失败', 'error');
        }
      });
    });
  }

  async function openActivityEditor(id) {
    let activity = null;
    if (id) {
      try {
        const data = await api.get(`${base}/activity`, { id });
        activity = data.activity;
      } catch (error) {
        toast(error.message || '活动读取失败', 'error');
        return;
      }
    }
    const value = (key) => esc(activity?.[key] ?? '');
    const body = `
      <div class="acv-form-grid">
        <label class="acv-field is-wide"><span>活动名称 *</span><input type="text" data-edit="title" maxlength="80" value="${value(
          'title'
        )}" placeholder="例如 星空社秋季建筑大赛" /></label>
        <label class="acv-field is-wide"><span>一句话简介</span><input type="text" data-edit="summary" maxlength="200" value="${value(
          'summary'
        )}" placeholder="展示在活动卡片上的简介" /></label>
        <label class="acv-field is-wide"><span>详细说明</span><textarea data-edit="description" maxlength="5000" placeholder="活动内容、参与方式、注意事项">${value(
          'description'
        )}</textarea></label>
        <label class="acv-field"><span>地点</span><input type="text" data-edit="location" maxlength="100" value="${value(
          'location'
        )}" placeholder="例如 线上 / 东区机房" /></label>
        <label class="acv-field"><span>名额（0 为不限）</span><input type="number" data-edit="capacity" min="0" max="9999" value="${
          activity?.capacity ?? 0
        }" /></label>
        <label class="acv-field"><span>开始时间</span><input type="datetime-local" data-edit="startTime" value="${value(
          'startTime'
        )}" /></label>
        <label class="acv-field"><span>结束时间</span><input type="datetime-local" data-edit="endTime" value="${value(
          'endTime'
        )}" /></label>
        <label class="acv-field"><span>报名开始</span><input type="datetime-local" data-edit="signupStart" value="${value(
          'signupStart'
        )}" /></label>
        <label class="acv-field"><span>报名截止</span><input type="datetime-local" data-edit="signupEnd" value="${value(
          'signupEnd'
        )}" /></label>
        <label class="acv-check-row"><input type="checkbox" data-edit="proofEnabled" ${
          activity?.proofEnabled === false ? '' : 'checked'
        } /> 允许生成参与证明</label>
        <label class="acv-check-row"><input type="checkbox" data-edit="autoSignup" ${
          activity?.autoSignup ? 'checked' : ''
        } /> 服务器在线自动报名（待接入服务器插件）</label>
        <p class="acv-note is-wide">自动报名需要服务器插件提供在线玩家数据，当前版本暂未接入，可先使用自助报名。</p>
      </div>
    `;
    const dialog = openModal({
      title: id ? '编辑活动' : '新建活动',
      subtitle: '保存后活动处于草稿状态，发布后才会出现在活动广场',
      body,
      wide: true,
      actions: [
        {
          label: id ? '保存修改' : '创建活动',
          className: 'btn-primary',
          onClick: async (close) => {
            const bodyData = {};
            dialog.overlay.querySelectorAll('[data-edit]').forEach((input) => {
              bodyData[input.dataset.edit] =
                input.type === 'checkbox' ? input.checked : input.type === 'number' ? Number(input.value) : input.value;
            });
            if (id) bodyData.id = id;
            try {
              await api.post(`${base}/admin/activity/save`, bodyData);
              toast(id ? '活动已更新' : '活动已创建', 'success');
              close();
              await loadAdminActivities();
            } catch (error) {
              toast(error.message || '保存失败', 'error');
            }
          },
        },
      ],
    });
    dialog.overlay.querySelector('[data-edit="title"]')?.focus();
  }

  // ---------------- 参与核验 ----------------

  async function renderAdminVerify() {
    const host = view.querySelector('[data-admin-view]');
    host.innerHTML = '<div class="plugin-loading">正在读取活动与报名…</div>';
    try {
      const data = await api.get(`${base}/admin/activities`, { page: 1, size: 200 });
      admin.verify.options = data.records || [];
      if (!admin.verify.activityId && admin.verify.options.length) {
        admin.verify.activityId = admin.verify.options[0].id;
      }
    } catch (error) {
      host.innerHTML = `<div class="acv-empty"><h3>活动读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
      return;
    }
    paintAdminVerify();
  }

  function paintAdminVerify() {
    const host = view.querySelector('[data-admin-view]');
    if (!admin.verify.options.length) {
      host.innerHTML = `<div class="acv-empty"><h3>还没有活动</h3><p>先在「活动管理」中创建并发布活动。</p></div>`;
      return;
    }
    host.innerHTML = `
      <div class="acv-toolbar">
        <div class="acv-toolbar-right">
          <label class="select-box"><select data-verify-activity>${admin.verify.options
            .map(
              (item) =>
                `<option value="${esc(item.id)}" ${item.id === admin.verify.activityId ? 'selected' : ''}>${esc(
                  item.title
                )}（${STATUS_LABELS[item.status] || item.status}）</option>`
            )
            .join('')}</select></label>
          <label class="search-box"><input type="search" data-verify-search placeholder="搜索用户 / 姓名 / 学号" value="${esc(
            admin.verify.keyword
          )}" /></label>
          <label class="select-box"><select data-verify-status>
            ${[
              ['', '全部核验状态'],
              ['PENDING', '待核验'],
              ['PASSED', '已通过'],
              ['REJECTED', '未通过'],
            ]
              .map(
                ([value, label]) =>
                  `<option value="${value}" ${admin.verify.verify === value ? 'selected' : ''}>${label}</option>`
              )
              .join('')}
          </select></label>
        </div>
        <div class="acv-row-actions">
          <button class="btn btn-ghost" data-sync-server>服务器自动报名</button>
          <button class="btn btn-ghost" data-bulk="PASSED">批量通过</button>
          <button class="btn btn-ghost" data-bulk="REJECTED">批量拒绝</button>
        </div>
      </div>
      <div data-verify-body><div class="plugin-loading">正在读取报名…</div></div>
    `;
    host.querySelector('[data-verify-activity]').addEventListener('change', (event) => {
      admin.verify.activityId = event.target.value;
      admin.verify.page = 1;
      admin.verify.selected.clear();
      loadAdminVerify();
    });
    host.querySelector('[data-verify-status]').addEventListener('change', (event) => {
      admin.verify.verify = event.target.value;
      admin.verify.page = 1;
      admin.verify.selected.clear();
      loadAdminVerify();
    });
    host.querySelector('[data-verify-search]').addEventListener('input', (event) => {
      const input = event.target;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        admin.verify.keyword = input.value.trim();
        admin.verify.page = 1;
        loadAdminVerify();
      }, 300);
    });
    host.querySelector('[data-sync-server]').addEventListener('click', async () => {
      try {
        const result = await api.post(`${base}/admin/sync`, {});
        toast(result.message || '暂未接入服务器同步', 'info');
      } catch (error) {
        toast(error.message || '暂未接入服务器同步', 'error');
      }
    });
    host.querySelectorAll('[data-bulk]').forEach((button) => {
      button.addEventListener('click', () => {
        const ids = [...admin.verify.selected];
        if (!ids.length) {
          toast('请先勾选参与者', 'info');
          return;
        }
        verifyUsers(ids, button.dataset.bulk);
      });
    });
    loadAdminVerify();
  }

  async function loadAdminVerify() {
    const host = view.querySelector('[data-verify-body]');
    if (!host || !admin.verify.activityId) return;
    try {
      const data = await api.get(`${base}/admin/activity`, {
        id: admin.verify.activityId,
        keyword: admin.verify.keyword,
        verify: admin.verify.verify,
        page: admin.verify.page,
        size: 20,
      });
      admin.verify.data = data;
      paintAdminVerifyBody();
    } catch (error) {
      host.innerHTML = `<div class="acv-empty"><h3>报名读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintAdminVerifyBody() {
    const host = view.querySelector('[data-verify-body]');
    const data = admin.verify.data;
    if (!host || !data) return;
    const stats = data.stats || {};
    const totalPages = Math.max(1, Math.ceil(data.total / data.size));
    host.innerHTML = `
      <div class="acv-stats">
        <div class="acv-stat"><span>已报名</span><strong>${stats.joined || 0}</strong></div>
        <div class="acv-stat"><span>待核验</span><strong>${stats.pending || 0}</strong></div>
        <div class="acv-stat"><span>已通过</span><strong>${stats.passed || 0}</strong></div>
        <div class="acv-stat"><span>未通过</span><strong>${stats.rejected || 0}</strong></div>
      </div>
      <div class="acv-table-wrap"><table class="acv-table">
        <thead><tr>
          <th><input type="checkbox" data-check-all /></th>
          <th>用户</th><th>姓名 / 学号</th><th>报名时间</th><th>来源</th><th>核验</th><th>操作</th>
        </tr></thead>
        <tbody>
          ${
            data.records.length
              ? data.records.map(verifyRowHtml).join('')
              : '<tr><td colspan="7"><span class="acv-note">没有匹配的报名记录。</span></td></tr>'
          }
        </tbody>
      </table></div>
      <div class="acv-pager">
        <button class="btn btn-ghost btn-sm" data-verify-page="${admin.verify.page - 1}" ${
      admin.verify.page <= 1 ? 'disabled' : ''
    }>上一页</button>
        <span>第 ${admin.verify.page} / ${totalPages} 页 · 共 ${data.total} 人</span>
        <button class="btn btn-ghost btn-sm" data-verify-page="${admin.verify.page + 1}" ${
      admin.verify.page >= totalPages ? 'disabled' : ''
    }>下一页</button>
      </div>
    `;
    host.querySelectorAll('[data-check-user]').forEach((input) => {
      input.checked = admin.verify.selected.has(input.dataset.checkUser);
      input.addEventListener('change', () => {
        if (input.checked) admin.verify.selected.add(input.dataset.checkUser);
        else admin.verify.selected.delete(input.dataset.checkUser);
      });
    });
    host.querySelector('[data-check-all]')?.addEventListener('change', (event) => {
      const checked = event.target.checked;
      host.querySelectorAll('[data-check-user]').forEach((input) => {
        input.checked = checked;
        if (checked) admin.verify.selected.add(input.dataset.checkUser);
        else admin.verify.selected.delete(input.dataset.checkUser);
      });
    });
    host.querySelectorAll('[data-verify-user]').forEach((button) => {
      button.addEventListener('click', () => verifyUsers([button.dataset.verifyUser], button.dataset.verifyValue));
    });
    host.querySelectorAll('[data-verify-page]').forEach((button) => {
      button.addEventListener('click', () => {
        admin.verify.page = Number(button.dataset.verifyPage);
        loadAdminVerify();
      });
    });
  }

  function verifyRowHtml(row) {
    return `
      <tr>
        <td><input type="checkbox" data-check-user="${esc(row.userId)}" /></td>
        <td><strong>${esc(row.nickname || row.username)}</strong><br /><span class="acv-note">@${esc(
          row.username
        )}</span></td>
        <td>${esc(row.realName || '—')}<br /><span class="acv-note">${esc(row.studentNo || '未填写学号')}</span></td>
        <td>${row.joinedAt ? esc(formatDate(row.joinedAt, { time: true })) : '—'}</td>
        <td>${esc(SOURCE_LABELS[row.source] || row.source || '自助报名')}</td>
        <td>${verifyBadge(row.verify)}${row.verifyNote ? `<br /><span class="acv-note">${esc(row.verifyNote)}</span>` : ''}</td>
        <td><div class="acv-row-actions">
          <button class="btn btn-primary btn-sm" data-verify-user="${esc(row.userId)}" data-verify-value="PASSED">通过</button>
          <button class="btn btn-ghost btn-sm" data-verify-user="${esc(row.userId)}" data-verify-value="REJECTED">拒绝</button>
          <button class="btn btn-ghost btn-sm" data-verify-user="${esc(row.userId)}" data-verify-value="PENDING">待定</button>
        </div></td>
      </tr>`;
  }

  async function verifyUsers(userIds, verify) {
    try {
      const result = await api.post(`${base}/admin/verify`, {
        activityId: admin.verify.activityId,
        userIds,
        verify,
      });
      toast(`已更新 ${result.count || userIds.length} 条核验记录`, 'success');
      admin.verify.selected.clear();
      await loadAdminVerify();
    } catch (error) {
      toast(error.message || '核验失败', 'error');
    }
  }

  // ---------------- 证明导出 ----------------

  async function renderAdminProof() {
    const host = view.querySelector('[data-admin-view]');
    host.innerHTML = '<div class="plugin-loading">正在读取模板与导出记录…</div>';
    try {
      const [settings, exportsData, activities, fixed, stamped] = await Promise.all([
        api.get(`${base}/admin/settings`),
        api.get(`${base}/admin/exports`),
        api.get(`${base}/admin/activities`, { page: 1, size: 200 }),
        api.get(`${base}/admin/fixed`),
        api.get(`${base}/admin/stamped`),
      ]);
      admin.proof.settings = settings;
      admin.proof.exports = exportsData;
      admin.proof.activities = activities.records || [];
      admin.proof.fixed = fixed.items || [];
      admin.proof.stamped = stamped.items || [];
      if (!admin.proof.selected.size) {
        admin.proof.activities
          .filter((item) => item.stats?.passed > 0)
          .forEach((item) => admin.proof.selected.add(item.id));
      }
      paintAdminProof();
    } catch (error) {
      host.innerHTML = `<div class="acv-empty"><h3>读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintAdminProof() {
    const host = view.querySelector('[data-admin-view]');
    const settings = admin.proof.settings || {};
    const exportsData = admin.proof.exports || { items: [] };
    const activities = admin.proof.activities || [];
    const fixed = admin.proof.fixed || [];
    host.innerHTML = `
      <section class="acv-panel">
        <div class="acv-panel-head">
          <div><h2>证明模板</h2><p>上传 DOCX 模板后可在线修改正文文字；导出时自动替换占位符并重建附录表格中的参与者名单。</p></div>
          <div class="acv-row-actions">
            ${
              settings.templateUploaded
                ? `<button class="btn btn-ghost" data-edit-template>在线编辑模板</button>
                   <button class="btn btn-ghost" data-download-template>下载当前模板</button>`
                : ''
            }
            <button class="btn btn-primary" data-upload-template>上传模板</button>
            <input type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" data-template-file hidden />
          </div>
        </div>
        <div class="acv-template">
          <span>${
            settings.templateUploaded
              ? `当前模板：<strong>${esc(settings.templateName)}</strong> · ${(
                  Number(settings.templateSize || 0) / 1024
                ).toFixed(0)} KB · ${esc(formatDate(settings.templateUploadedAt, { time: true }))}`
              : '尚未上传模板，请先上传活动证明 DOCX。'
          }</span>
          <span class="acv-note">占位符：{{proofNo}} {{issueDate}} {{issuer}} {{activities}} {{activityCount}} {{generatedAt}} {{name}} {{studentNo}} {{college}} {{className}} {{major}} {{enrollmentYear}} {{graduationYear}}</span>
        </div>
      </section>

      <section class="acv-panel">
        <div class="acv-panel-head"><div><h2>导出设置</h2><p>默认签发单位与编号前缀，可在生成时覆盖。</p></div></div>
        <div class="acv-form-grid">
          <label class="acv-field"><span>默认签发单位</span><input type="text" data-setting="defaultIssuer" maxlength="60" value="${esc(
            settings.defaultIssuer || ''
          )}" placeholder="例如 共青团西南科技大学计算机科学与技术学院分委员会" /></label>
          <label class="acv-field"><span>默认编号前缀</span><input type="text" data-setting="defaultProofPrefix" maxlength="30" value="${esc(
            settings.defaultProofPrefix || 'SWUSTMC'
          )}" /></label>
        </div>
        <div class="acv-dialog-actions"><button class="btn btn-ghost" data-save-settings>保存设置</button></div>
      </section>

      <section class="acv-panel">
        <div class="acv-panel-head">
          <div><h2>固定参与人员</h2><p>不通过网站报名、但需要出现在证明名单中的人员（如工作人员、外校同学），会随每次导出加入。</p></div>
          <div class="acv-row-actions">
            <button class="btn btn-ghost" data-import-fixed>从现有用户导入</button>
            <button class="btn btn-primary" data-add-fixed>添加固定人员</button>
          </div>
        </div>
        ${
          fixed.length
            ? `<div class="acv-table-wrap"><table class="acv-table" style="min-width:0">
                <thead><tr><th>姓名</th><th>专业班级</th><th>学号</th><th>预计毕业</th><th>操作</th></tr></thead>
                <tbody>${fixed
                  .map(
                    (row) => `
                  <tr>
                    <td>${esc(row.name)}</td>
                    <td>${esc(row.className)}</td>
                    <td><code>${esc(row.studentNo)}</code></td>
                    <td>${esc(row.graduationYear || '—')}</td>
                    <td><div class="acv-row-actions">
                      <button class="btn btn-ghost btn-sm" data-edit-fixed="${esc(row.id)}">编辑</button>
                      <button class="btn btn-ghost btn-sm" data-delete-fixed="${esc(row.id)}">删除</button>
                    </div></td>
                  </tr>`
                  )
                  .join('')}</tbody>
              </table></div>`
            : '<p class="acv-note">还没有固定参与人员。</p>'
        }
      </section>

      <section class="acv-panel">
        <div class="acv-panel-head">
          <div><h2>生成活动证明</h2><p>选择活动后预览名单，可在生成前手动修改人员与正文信息。</p></div>
          <button class="btn btn-primary" data-preview-proof>预览并生成</button>
        </div>
        <div class="acv-form-grid">
          <div class="acv-field is-wide">
            <span>活动（可多选，联动生成）</span>
            <div class="acv-activity-picker">
              ${
                activities.length
                  ? activities
                      .map(
                        (item) => `
                <label class="acv-check-row">
                  <input type="checkbox" data-proof-activity="${esc(item.id)}" ${
                          admin.proof.selected.has(item.id) ? 'checked' : ''
                        } />
                  <span>${esc(item.title)} <span class="acv-note">· ${STATUS_LABELS[item.status] || item.status} · 通过 ${
                          item.stats?.passed || 0
                        } 人</span></span>
                </label>`
                      )
                      .join('')
                  : '<span class="acv-note">还没有活动。</span>'
              }
            </div>
          </div>
          <label class="acv-field"><span>多活动匹配方式</span>
            <select data-proof-mode>
              <option value="any" ${admin.proof.mode === 'any' ? 'selected' : ''}>任一活动通过即可</option>
              <option value="all" ${admin.proof.mode === 'all' ? 'selected' : ''}>需全部活动通过</option>
            </select>
          </label>
        </div>
      </section>

      <section class="acv-panel">
        <div class="acv-panel-head"><div><h2>导出记录</h2><p>最近 100 次导出；文件保存在插件数据目录中。</p></div></div>
        ${
          exportsData.items?.length
            ? `<div class="acv-table-wrap"><table class="acv-table">
                <thead><tr><th>时间</th><th>活动</th><th>人数</th><th>文件</th><th>操作</th></tr></thead>
                <tbody>${exportsData.items
                  .map(
                    (row) => `
                  <tr>
                    <td>${esc(formatDate(row.createdAt, { time: true }))}</td>
                    <td>${esc((row.activityTitles || []).join('、'))}</td>
                    <td>${row.count}${row.excludedCount ? ` <span class="acv-note">（排除 ${row.excludedCount}）</span>` : ''}</td>
                    <td><code>${esc(row.filename)}</code></td>
                    <td><div class="acv-row-actions">
                      <button class="btn btn-ghost btn-sm" data-download-export="${esc(row.id)}">下载</button>
                      <button class="btn btn-ghost btn-sm" data-delete-export="${esc(row.id)}">删除</button>
                    </div></td>
                  </tr>`
                  )
                  .join('')}</tbody>
              </table></div>`
            : '<p class="acv-note">还没有导出记录。</p>'
        }
      </section>

      <section class="acv-panel">
        <div class="acv-panel-head">
          <div><h2>已盖章 PDF</h2><p>上传盖章扫描件：可按导出记录自动匹配参与名单，或独立上传时手动选择名单。名单内的参与者可下载。</p></div>
          <button class="btn btn-primary" data-upload-stamped>上传盖章 PDF</button>
        </div>
        ${
          admin.proof.stamped?.length
            ? `<div class="acv-table-wrap"><table class="acv-table">
                <thead><tr><th>证明</th><th>来源</th><th>人数</th><th>上传时间</th><th>操作</th></tr></thead>
                <tbody>${admin.proof.stamped
                  .map(
                    (row) => `
                  <tr>
                    <td><strong>${esc(row.title || row.filename)}</strong><br /><span class="acv-note">${esc(
                      row.filename || ''
                    )}</span></td>
                    <td>${row.source === 'EXPORT' ? '导出记录' : '手动选择'}</td>
                    <td>${row.participantCount || 0}</td>
                    <td>${row.uploadedAt ? esc(formatDate(row.uploadedAt, { time: true })) : '—'}</td>
                    <td><div class="acv-row-actions">
                      <button class="btn btn-ghost btn-sm" data-download-stamped-admin="${esc(row.id)}">下载</button>
                      <button class="btn btn-ghost btn-sm" data-delete-stamped="${esc(row.id)}">删除</button>
                    </div></td>
                  </tr>`
                  )
                  .join('')}</tbody>
              </table></div>`
            : '<p class="acv-note">还没有上传盖章文件。</p>'
        }
      </section>
    `;
    bindAdminProof();
  }

  function bindAdminProof() {
    const host = view.querySelector('[data-admin-view]');
    const fileInput = host.querySelector('[data-template-file]');
    host.querySelector('[data-upload-template]').addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', async () => {
      const file = fileInput.files?.[0];
      if (!file) return;
      try {
        await api.upload(
          `${base}/admin/template?name=${encodeURIComponent(file.name)}`,
          file,
          'application/octet-stream'
        );
        toast('模板已上传', 'success');
        await renderAdminProof();
      } catch (error) {
        toast(error.message || '模板上传失败', 'error');
      } finally {
        fileInput.value = '';
      }
    });
    host.querySelector('[data-download-template]')?.addEventListener('click', async () => {
      try {
        await download(api.raw(`${base}/admin/template/download`), 'template.docx');
      } catch (error) {
        toast(error.message || '模板下载失败', 'error');
      }
    });
    host.querySelector('[data-edit-template]')?.addEventListener('click', () => openTemplateEditor());
    host.querySelector('[data-save-settings]').addEventListener('click', async () => {
      const body = {};
      host.querySelectorAll('[data-setting]').forEach((input) => {
        body[input.dataset.setting] = input.value.trim();
      });
      try {
        await api.post(`${base}/admin/settings`, body);
        toast('设置已保存', 'success');
        admin.proof.settings = await api.get(`${base}/admin/settings`);
      } catch (error) {
        toast(error.message || '保存失败', 'error');
      }
    });
    host.querySelector('[data-add-fixed]').addEventListener('click', () => openFixedEditor(null));
    host.querySelector('[data-upload-stamped]').addEventListener('click', () => openStampedUpload());
    host.querySelectorAll('[data-download-stamped-admin]').forEach((button) => {
      button.addEventListener('click', async () => {
        try {
          await download(
            api.raw(`${base}/admin/stamped/download?id=${encodeURIComponent(button.dataset.downloadStampedAdmin)}`),
            '盖章证明.pdf'
          );
        } catch (error) {
          toast(error.message || '下载失败', 'error');
        }
      });
    });
    host.querySelectorAll('[data-delete-stamped]').forEach((button) => {
      button.addEventListener('click', async () => {
        const ok = await confirm({
          title: '删除盖章文件',
          message: '删除后参与者将无法再下载，操作不可撤销。',
          confirmText: '删除',
          danger: true,
        });
        if (!ok) return;
        try {
          await api.post(`${base}/admin/stamped/delete`, { id: button.dataset.deleteStamped });
          toast('盖章文件已删除', 'success');
          await renderAdminProof();
        } catch (error) {
          toast(error.message || '删除失败', 'error');
        }
      });
    });
    host.querySelector('[data-import-fixed]').addEventListener('click', () => openFixedImport());
    host.querySelectorAll('[data-edit-fixed]').forEach((button) => {
      button.addEventListener('click', () => {
        const row = (admin.proof.fixed || []).find((item) => item.id === button.dataset.editFixed);
        openFixedEditor(row || null);
      });
    });
    host.querySelectorAll('[data-delete-fixed]').forEach((button) => {
      button.addEventListener('click', async () => {
        const ok = await confirm({
          title: '删除固定参与人员',
          message: '删除后此人将不再出现在后续导出的证明名单中。',
          confirmText: '删除',
          danger: true,
        });
        if (!ok) return;
        try {
          await api.post(`${base}/admin/fixed/delete`, { id: button.dataset.deleteFixed });
          toast('已删除', 'success');
          await renderAdminProof();
        } catch (error) {
          toast(error.message || '删除失败', 'error');
        }
      });
    });
    host.querySelectorAll('[data-proof-activity]').forEach((input) => {
      input.addEventListener('change', () => {
        if (input.checked) admin.proof.selected.add(input.dataset.proofActivity);
        else admin.proof.selected.delete(input.dataset.proofActivity);
      });
    });
    host.querySelector('[data-proof-mode]').addEventListener('change', (event) => {
      admin.proof.mode = event.target.value;
    });
    host.querySelector('[data-preview-proof]').addEventListener('click', async () => {
      const ids = [...admin.proof.selected];
      if (!ids.length) return toast('请先选择活动', 'info');
      if (!admin.proof.settings?.templateUploaded) return toast('请先上传证明模板', 'error');
      try {
        const preview = await api.get(`${base}/admin/export/preview`, {
          activityIds: ids.join(','),
          mode: admin.proof.mode,
        });
        openProofEditor(preview);
      } catch (error) {
        toast(error.message || '预览失败', 'error');
      }
    });
    host.querySelectorAll('[data-delete-export]').forEach((button) => {
      button.addEventListener('click', async () => {
        const ok = await confirm({
          title: '删除导出记录',
          message: '将同时删除服务器上的证明文件，操作不可撤销。',
          confirmText: '删除',
          danger: true,
        });
        if (!ok) return;
        try {
          await api.post(`${base}/admin/export/delete`, { id: button.dataset.deleteExport });
          toast('导出记录已删除', 'success');
          await renderAdminProof();
        } catch (error) {
          toast(error.message || '删除失败', 'error');
        }
      });
    });
    host.querySelectorAll('[data-download-export]').forEach((button) => {
      button.addEventListener('click', async () => {
        try {
          await download(
            api.raw(`${base}/admin/export/download?id=${encodeURIComponent(button.dataset.downloadExport)}`),
            '活动证明.docx'
          );
        } catch (error) {
          toast(error.message || '下载失败', 'error');
        }
      });
    });
  }

  // ---------------- 在线模板编辑 ----------------

  async function openTemplateEditor() {
    let paragraphs = [];
    try {
      paragraphs = await api.get(`${base}/admin/template/paragraphs`);
    } catch (error) {
      toast(error.message || '模板读取失败', 'error');
      return;
    }
    const body = `
      <p class="acv-note">直接修改正文段落文字；占位符保留原样即可在导出时替换。表格中的参与者名单由系统自动生成。</p>
      <div class="acv-paragraphs">
        ${paragraphs
          .map(
            (row) => `
          <label class="acv-field">
            <span>段落 ${row.index + 1}</span>
            <textarea data-paragraph="${row.index}" rows="2">${esc(row.text)}</textarea>
          </label>`
          )
          .join('')}
      </div>
    `;
    const dialog = openModal({
      title: '在线编辑模板',
      subtitle: '保存后会立即替换当前模板的正文文字',
      body,
      wide: true,
      actions: [
        {
          label: '保存模板',
          className: 'btn-primary',
          onClick: async (close) => {
            const rows = [...dialog.overlay.querySelectorAll('[data-paragraph]')].map((input) => ({
              index: Number(input.dataset.paragraph),
              text: input.value,
            }));
            try {
              await api.post(`${base}/admin/template/paragraphs`, { paragraphs: rows });
              toast('模板已更新', 'success');
              close();
            } catch (error) {
              toast(error.message || '模板保存失败', 'error');
            }
          },
        },
      ],
    });
  }

  // ---------------- 固定参与人员编辑 ----------------

  function openFixedEditor(row) {
    const value = (key) => esc(row?.[key] ?? '');
    const body = `
      <div class="acv-form-grid">
        <label class="acv-field"><span>姓名 *</span><input type="text" data-fixed="name" maxlength="40" value="${value(
          'name'
        )}" /></label>
        <label class="acv-field"><span>专业班级 *</span><input type="text" data-fixed="className" maxlength="80" value="${value(
          'className'
        )}" placeholder="例如 计科2301" /></label>
        <label class="acv-field"><span>学号 *（51 + 4 位入学年份 + 4 位数字）</span><input type="text" data-fixed="studentNo" maxlength="10" value="${value(
          'studentNo'
        )}" placeholder="例如 5120241234" /></label>
        <label class="acv-field"><span>预计毕业年份</span><input type="number" data-fixed="graduationYear" min="2000" max="2100" value="${value(
          'graduationYear'
        )}" /></label>
        <label class="acv-field"><span>学院</span><input type="text" data-fixed="college" maxlength="80" value="${value(
          'college'
        )}" /></label>
        <label class="acv-field"><span>专业</span><input type="text" data-fixed="major" maxlength="80" value="${value(
          'major'
        )}" /></label>
      </div>
      <p class="acv-note">固定人员会随每次导出加入名单；大四及已毕业同学仍会被自动排除。</p>
    `;
    const dialog = openModal({
      title: row ? '编辑固定参与人员' : '添加固定参与人员',
      body,
      wide: true,
      actions: [
        {
          label: '保存',
          className: 'btn-primary',
          onClick: async (close) => {
            const data = {};
            dialog.overlay.querySelectorAll('[data-fixed]').forEach((input) => {
              data[input.dataset.fixed] =
                input.type === 'number' ? Number(input.value) : input.value.trim();
            });
            if (row) data.id = row.id;
            try {
              await api.post(`${base}/admin/fixed/save`, data);
              toast('固定人员已保存', 'success');
              close();
              await renderAdminProof();
            } catch (error) {
              toast(error.message || '保存失败', 'error');
            }
          },
        },
      ],
    });
  }

  function openStampedUpload() {
    const exportsItems = admin.proof.exports?.items || [];
    let users = [];
    const selectedUsers = new Set();
    const body = `
      <div class="acv-form-grid">
        <label class="acv-field is-wide"><span>匹配方式</span>
          <select data-stamped-source>
            <option value="EXPORT">根据导出记录，自动匹配参与名单</option>
            <option value="MANUAL">独立上传，手动选择参与名单</option>
          </select>
        </label>
        <label class="acv-field is-wide" data-stamped-export-box><span>导出记录</span>
          <select data-stamped-export>
            ${
              exportsItems.length
                ? exportsItems
                    .map(
                      (row) =>
                        `<option value="${esc(row.id)}">${esc(
                          (row.activityTitles || []).join('、') || row.filename
                        )} · ${row.count} 人 · ${esc(formatDate(row.createdAt, { time: true }))}</option>`
                    )
                    .join('')
                : '<option value="">还没有导出记录</option>'
            }
          </select>
        </label>
        <div class="acv-field is-wide" data-stamped-manual-box hidden>
          <span>参与名单</span>
          <div class="acv-picker-row">
            <span data-stamped-selection>尚未选择参与人员</span>
            <button type="button" class="btn btn-ghost btn-sm" data-pick-users>选择参与名单</button>
          </div>
        </div>
        <label class="acv-field"><span>证明标题（可选）</span><input type="text" data-stamped-title maxlength="100" placeholder="例如：星空社秋季建筑大赛活动证明" /></label>
        <div class="acv-field"><span>盖章 PDF *</span>
          <span class="acv-file-picker" data-file-picker>
            <button type="button" class="btn btn-ghost btn-sm" data-pick-file>选择 PDF</button>
            <span class="acv-file-name" data-file-name>未选择文件</span>
            <input type="file" accept=".pdf,application/pdf" data-stamped-file hidden />
          </span>
        </div>
      </div>
      <p class="acv-note">建议上传扫描或盖章后的 PDF；参与名单内的用户登录后在「我的活动 → 已盖章证明」中下载。</p>
    `;
    const dialog = openModal({
      title: '上传盖章 PDF',
      subtitle: '按导出记录自动匹配，或手动选择参与名单',
      body,
      wide: true,
      actions: [
        {
          label: '上传',
          className: 'btn-primary',
          onClick: async (close) => {
            const fileInput = dialog.overlay.querySelector('[data-stamped-file]');
            const file = fileInput.files?.[0];
            if (!file) return toast('请选择 PDF 文件', 'error');
            const source = dialog.overlay.querySelector('[data-stamped-source]').value;
            const title = dialog.overlay.querySelector('[data-stamped-title]').value.trim();
            const payload = { source, title };
            if (source === 'EXPORT') {
              const exportId = dialog.overlay.querySelector('[data-stamped-export]').value;
              if (!exportId) return toast('请选择导出记录', 'error');
              payload.exportId = exportId;
            } else {
              if (!selectedUsers.size) return toast('请选择参与名单', 'error');
              payload.userIds = [...selectedUsers];
            }
            try {
              const prepared = await api.post(`${base}/admin/stamped/prepare`, payload);
              await api.upload(
                `${base}/admin/stamped/upload?id=${encodeURIComponent(prepared.id)}&name=${encodeURIComponent(
                  file.name
                )}`,
                file,
                'application/pdf'
              );
              toast('盖章文件已上传', 'success');
              close();
              await renderAdminProof();
            } catch (error) {
              toast(error.message || '上传失败', 'error');
            }
          },
        },
      ],
    });
    const sourceSelect = dialog.overlay.querySelector('[data-stamped-source]');
    const exportBox = dialog.overlay.querySelector('[data-stamped-export-box]');
    const manualBox = dialog.overlay.querySelector('[data-stamped-manual-box]');
    const selectionText = dialog.overlay.querySelector('[data-stamped-selection]');
    const fileInput = dialog.overlay.querySelector('[data-stamped-file]');
    const filePicker = dialog.overlay.querySelector('[data-file-picker]');
    const fileName = dialog.overlay.querySelector('[data-file-name]');

    const updateSelection = () => {
      const chosen = users.filter((row) => selectedUsers.has(row.userId));
      selectionText.textContent = chosen.length
        ? `已选择 ${chosen.length} 人：${chosen
            .slice(0, 3)
            .map((row) => row.realName || row.username)
            .join('、')}${chosen.length > 3 ? ' 等' : ''}`
        : '尚未选择参与人员';
    };
    const loadUsers = async () => {
      if (users.length) return;
      const data = await api.get(`${base}/admin/identities`, { page: 1, size: 200 });
      users = (data.records || []).filter((row) => row.studentNo && row.realName);
    };
    const openUserPicker = async () => {
      try {
        await loadUsers();
      } catch (error) {
        toast(error.message || '用户读取失败', 'error');
        return;
      }
      const temp = new Set(selectedUsers);
      const pickerBody = `
        <label class="search-box" style="width:100%;max-width:none;margin-bottom:10px">
          <input type="search" data-picker-search placeholder="搜索姓名 / 用户名 / 学号" />
        </label>
        <div data-picker-list style="max-height:320px;overflow:auto"></div>
      `;
      const picker = openModal({
        title: '选择参与名单',
        subtitle: '可多选，确定后返回上传窗口',
        body: pickerBody,
        wide: true,
        actions: [
          {
            label: '确定',
            className: 'btn-primary',
            onClick: (close) => {
              selectedUsers.clear();
              temp.forEach((id) => selectedUsers.add(id));
              updateSelection();
              close();
            },
          },
        ],
      });
      const listHost = picker.overlay.querySelector('[data-picker-list]');
      const search = picker.overlay.querySelector('[data-picker-search]');
      const paint = () => {
        const keyword = search.value.trim().toLowerCase();
        const filtered = users.filter((row) =>
          !keyword
            ? true
            : `${row.realName} ${row.username} ${row.studentNo} ${row.className}`
                .toLowerCase()
                .includes(keyword)
        );
        listHost.innerHTML = filtered.length
          ? filtered
              .map(
                (row) => `
            <label class="acv-check-row" style="margin-bottom:6px">
              <input type="checkbox" data-picker-user="${esc(row.userId)}" ${
                  temp.has(row.userId) ? 'checked' : ''
                } />
              <span>${esc(row.realName)} · ${esc(row.className || '—')} · ${esc(
                  row.studentNo
                )} <span class="acv-note">@${esc(row.username)}</span></span>
            </label>`
              )
              .join('')
          : '<p class="acv-note">没有匹配的用户。</p>';
        listHost.querySelectorAll('[data-picker-user]').forEach((input) => {
          input.addEventListener('change', () => {
            if (input.checked) temp.add(input.dataset.pickerUser);
            else temp.delete(input.dataset.pickerUser);
          });
        });
      };
      search.addEventListener('input', paint);
      paint();
    };

    sourceSelect.addEventListener('change', () => {
      const manual = sourceSelect.value === 'MANUAL';
      exportBox.hidden = manual;
      manualBox.hidden = !manual;
      if (manual && !users.length) {
        loadUsers()
          .then(updateSelection)
          .catch((error) => toast(error.message || '用户读取失败', 'error'));
      }
    });
    manualBox.querySelector('[data-pick-users]').addEventListener('click', openUserPicker);
    filePicker.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', () => {
      const file = fileInput.files?.[0];
      fileName.textContent = file ? file.name : '未选择文件';
    });
  }

  async function openFixedImport() {
    const body = `
      <p class="acv-note">从已填写活动身份信息的用户中选择，导入后会出现在固定参与人员名单中。</p>
      <label class="search-box" style="width:100%;max-width:none;margin-bottom:10px">
        <input type="search" data-import-search placeholder="搜索用户名 / 姓名 / 学号 / 班级" />
      </label>
      <div data-import-list><div class="plugin-loading">正在读取用户…</div></div>
    `;
    const dialog = openModal({
      title: '从现有用户导入',
      subtitle: '仅列出已填写姓名与学号的用户',
      body,
      wide: true,
    });
    const listHost = dialog.overlay.querySelector('[data-import-list]');
    const search = dialog.overlay.querySelector('[data-import-search]');
    let timer = null;
    const load = async () => {
      listHost.innerHTML = '<div class="plugin-loading">正在读取用户…</div>';
      try {
        const data = await api.get(`${base}/admin/fixed/candidates`, { keyword: search.value.trim() });
        if (!data.items.length) {
          listHost.innerHTML = '<p class="acv-note">没有可导入的用户（可能已全部加入固定名单）。</p>';
          return;
        }
        listHost.innerHTML = `<div class="acv-table-wrap"><table class="acv-table" style="min-width:0">
          <thead><tr><th>用户</th><th>姓名</th><th>专业班级</th><th>学号</th><th>操作</th></tr></thead>
          <tbody>${data.items
            .map(
              (row) => `
            <tr>
              <td>@${esc(row.username)}</td>
              <td>${esc(row.realName)}</td>
              <td>${esc(row.className || '—')}</td>
              <td><code>${esc(row.studentNo)}</code></td>
              <td><button class="btn btn-primary btn-sm" data-import-user="${esc(row.studentNo)}">导入</button></td>
            </tr>`
            )
            .join('')}</tbody>
        </table></div>`;
        listHost.querySelectorAll('[data-import-user]').forEach((button) => {
          button.addEventListener('click', async () => {
            const row = data.items.find((item) => item.studentNo === button.dataset.importUser);
            if (!row) return;
            try {
              await api.post(`${base}/admin/fixed/save`, {
                name: row.realName,
                className: row.className,
                studentNo: row.studentNo,
                college: row.college,
                major: row.major,
                graduationYear: row.graduationYear,
              });
              toast('已加入固定参与人员', 'success');
              dialog.close();
              await renderAdminProof();
            } catch (error) {
              toast(error.message || '导入失败', 'error');
            }
          });
        });
      } catch (error) {
        listHost.innerHTML = `<p class="acv-note">用户读取失败：${esc(error.message || '未知错误')}</p>`;
      }
    };
    search.addEventListener('input', () => {
      clearTimeout(timer);
      timer = setTimeout(load, 300);
    });
    await load();
  }

  // ---------------- 证明预览与手动修改 ----------------

  function openProofEditor(preview) {
    const defaults = preview.defaults || {};
    const rows = (preview.eligible || []).map((row) => ({
      realName: row.realName || '',
      className: row.className || '',
      studentNo: row.studentNo || '',
      college: row.college || '',
      major: row.major || '',
      graduationYear: row.graduationYear || '',
    }));
    const body = `
      <div class="acv-form-grid">
        <label class="acv-field"><span>证明编号</span><input type="text" data-doc="proofNo" maxlength="40" value="${esc(
          defaults.proofNo || ''
        )}" /></label>
        <label class="acv-field"><span>签发日期</span><input type="text" data-doc="issueDate" maxlength="30" value="${esc(
          defaults.issueDate || ''
        )}" /></label>
        <label class="acv-field is-wide"><span>签发单位</span><input type="text" data-doc="issuer" maxlength="60" value="${esc(
          defaults.issuer || ''
        )}" /></label>
        <label class="acv-field is-wide"><span>正文中的活动名称（多活动用顿号分隔）</span><input type="text" data-doc="activityText" maxlength="300" value="${esc(
          defaults.activityText || ''
        )}" /></label>
      </div>
      <div class="acv-panel-head" style="margin:6px 0 8px">
        <div><h3 class="acv-note">参与者名单（可直接修改、删除或在末尾添加）</h3></div>
        <div class="acv-row-actions">
          <span class="acv-note">共 ${rows.length} 人</span>
          <button class="btn btn-ghost btn-sm" data-add-proof-row>添加一行</button>
        </div>
      </div>
      <div class="acv-table-wrap"><table class="acv-table" style="min-width:0">
        <thead><tr><th>姓名</th><th>专业班级</th><th>学号</th><th>操作</th></tr></thead>
        <tbody data-proof-rows>${rows.map(proofRowEditor).join('')}</tbody>
      </table></div>
      ${
        preview.excluded?.length
          ? `<p class="acv-note">已自动排除 ${preview.excluded.length} 人：${preview.excluded
              .map((row) => `${esc(row.realName || row.username)}（${esc(row.reason)}）`)
              .join('、')}</p>`
          : ''
      }
    `;
    const dialog = openModal({
      title: '预览并修改证明',
      subtitle: '确认后生成一份包含全部参与者的 DOCX',
      body,
      wide: true,
      actions: [
        {
          label: '确认生成并下载',
          className: 'btn-primary',
          onClick: async (close) => {
            const doc = {};
            dialog.overlay.querySelectorAll('[data-doc]').forEach((input) => {
              doc[input.dataset.doc] = input.value.trim();
            });
            const editedRows = [...dialog.overlay.querySelectorAll('[data-proof-row]')].map((tr) => ({
              realName: tr.querySelector('[data-row-field="realName"]').value.trim(),
              className: tr.querySelector('[data-row-field="className"]').value.trim(),
              studentNo: tr.querySelector('[data-row-field="studentNo"]').value.trim(),
            }));
            if (!editedRows.length) {
              toast('名单不能为空', 'error');
              return;
            }
            try {
              const result = await api.post(`${base}/admin/export`, {
                activityIds: [...admin.proof.selected],
                mode: admin.proof.mode,
                issuer: doc.issuer,
                issueDate: doc.issueDate,
                proofNo: doc.proofNo,
                activityText: doc.activityText,
                rows: editedRows,
              });
              toast(`已生成包含 ${result.count} 人的证明`, 'success');
              close();
              await download(api.raw(`${base}/admin/export/download?id=${encodeURIComponent(result.id)}`), result.filename);
              await renderAdminProof();
            } catch (error) {
              toast(error.message || '生成失败', 'error');
            }
          },
        },
      ],
    });
    const rowsHost = dialog.overlay.querySelector('[data-proof-rows]');
    dialog.overlay.querySelector('[data-add-proof-row]').addEventListener('click', () => {
      const wrapper = document.createElement('tbody');
      wrapper.innerHTML = proofRowEditor({ realName: '', className: '', studentNo: '' });
      rowsHost.append(wrapper.firstElementChild);
    });
    rowsHost.addEventListener('click', (event) => {
      const button = event.target.closest('[data-remove-proof-row]');
      if (button) button.closest('[data-proof-row]')?.remove();
    });
  }

  function proofRowEditor(row) {
    return `
      <tr data-proof-row>
        <td><input class="acv-inline-input" data-row-field="realName" maxlength="40" value="${esc(row.realName || '')}" /></td>
        <td><input class="acv-inline-input" data-row-field="className" maxlength="80" value="${esc(
          row.className || ''
        )}" /></td>
        <td><input class="acv-inline-input" data-row-field="studentNo" maxlength="10" value="${esc(
          row.studentNo || ''
        )}" /></td>
        <td><button class="btn btn-ghost btn-sm" data-remove-proof-row>移除</button></td>
      </tr>`;
  }

  // ---------------- 身份名单 ----------------

  async function renderAdminIdentities() {
    const host = view.querySelector('[data-admin-view]');
    host.innerHTML = `
      <div class="acv-toolbar">
        <div class="filter-chips"><span class="acv-note">学号前四位为入学年份；大四及已毕业同学不再进入后续证明名单。</span></div>
        <div class="acv-toolbar-right">
          <label class="search-box"><input type="search" data-identity-search placeholder="搜索用户名 / 姓名 / 学号" value="${esc(
            admin.identities.keyword
          )}" /></label>
          <label class="select-box"><select data-identity-eligibility>${[
            ['', '全部状态'],
            ['ELIGIBLE', '可生成证明'],
            ['SENIOR', '大四'],
            ['GRADUATED', '已毕业'],
            ['INVALID', '学号无效'],
            ['NONE', '未填写'],
          ]
            .map(
              ([value, label]) =>
                `<option value="${value}" ${admin.identities.eligibility === value ? 'selected' : ''}>${label}</option>`
            )
            .join('')}</select></label>
        </div>
      </div>
      <div data-identity-body><div class="plugin-loading">正在读取身份名单…</div></div>
    `;
    host.querySelector('[data-identity-search]').addEventListener('input', (event) => {
      const input = event.target;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        admin.identities.keyword = input.value.trim();
        admin.identities.page = 1;
        loadAdminIdentities();
      }, 300);
    });
    host.querySelector('[data-identity-eligibility]').addEventListener('change', (event) => {
      admin.identities.eligibility = event.target.value;
      admin.identities.page = 1;
      loadAdminIdentities();
    });
    await loadAdminIdentities();
  }

  async function loadAdminIdentities() {
    const host = view.querySelector('[data-identity-body]');
    if (!host) return;
    try {
      const data = await api.get(`${base}/admin/identities`, {
        keyword: admin.identities.keyword,
        eligibility: admin.identities.eligibility,
        page: admin.identities.page,
        size: 20,
      });
      admin.identities.data = data;
      paintAdminIdentities();
    } catch (error) {
      host.innerHTML = `<div class="acv-empty"><h3>身份名单读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintAdminIdentities() {
    const host = view.querySelector('[data-identity-body]');
    const data = admin.identities.data;
    if (!host || !data) return;
    const totalPages = Math.max(1, Math.ceil(data.total / data.size));
    host.innerHTML = `
      <div class="acv-table-wrap"><table class="acv-table">
        <thead><tr><th>用户</th><th>姓名</th><th>学号</th><th>学院 / 班级</th><th>年级</th><th>证明状态</th><th>更新时间</th></tr></thead>
        <tbody>${
          data.records.length
            ? data.records
                .map(
                  (row) => `
              <tr>
                <td>@${esc(row.username)}</td>
                <td>${esc(row.realName || '—')}</td>
                <td>${esc(row.studentNo || '—')}</td>
                <td>${esc(row.college || '—')}<br /><span class="acv-note">${esc(row.className || '')}</span></td>
                <td>${esc(row.gradeLabel)}</td>
                <td>${
                  row.proofOptIn
                    ? row.proofAllowed
                      ? '<span class="acv-badge is-passed">可生成</span>'
                      : `<span class="acv-badge is-rejected">${esc(ELIGIBILITY_LABELS[row.eligibility] || row.gradeLabel)}</span>`
                    : '<span class="acv-badge">未开启</span>'
                }</td>
                <td>${row.updatedAt ? esc(formatDate(row.updatedAt, { time: true })) : '—'}</td>
              </tr>`
                )
                .join('')
            : '<tr><td colspan="7"><span class="acv-note">还没有用户填写活动身份信息。</span></td></tr>'
        }</tbody>
      </table></div>
      <div class="acv-pager">
        <button class="btn btn-ghost btn-sm" data-identity-page="${admin.identities.page - 1}" ${
      admin.identities.page <= 1 ? 'disabled' : ''
    }>上一页</button>
        <span>第 ${admin.identities.page} / ${totalPages} 页 · 共 ${data.total} 人</span>
        <button class="btn btn-ghost btn-sm" data-identity-page="${admin.identities.page + 1}" ${
      admin.identities.page >= totalPages ? 'disabled' : ''
    }>下一页</button>
      </div>
    `;
    host.querySelectorAll('[data-identity-page]').forEach((button) => {
      button.addEventListener('click', () => {
        admin.identities.page = Number(button.dataset.identityPage);
        loadAdminIdentities();
      });
    });
  }

  // ================================================================ 事件

  root.addEventListener('click', (event) => {
    const tabButton = event.target.closest('[data-tab]');
    if (tabButton) {
      tab = tabButton.dataset.tab;
      renderView();
      return;
    }
    const statusButton = event.target.closest('[data-square-status]');
    if (statusButton) {
      square.status = statusButton.dataset.squareStatus;
      renderSquare();
    }
  });

  view.addEventListener('input', (event) => {
    if (!event.target.matches('[data-square-search]')) return;
    const input = event.target;
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      square.keyword = input.value.trim();
      paintSquare();
    }, 250);
  });

  renderView();

  return () => {
    disposed = true;
    clearTimeout(searchTimer);
  };
}
