const BASE = '/plugins/feedback';

const TYPE_OPTIONS = [
  ['bug', '缺陷反馈'],
  ['suggestion', '功能建议'],
  ['question', '使用咨询'],
  ['other', '其他'],
];
const SEVERITY_OPTIONS = [
  ['low', '轻微'],
  ['normal', '一般'],
  ['high', '严重'],
  ['critical', '致命'],
];
const STATUS_OPTIONS = [
  ['pending', '待处理'],
  ['processing', '处理中'],
  ['resolved', '已解决'],
  ['rejected', '不予处理'],
  ['archived', '已归档'],
];
const TYPE_LABELS = Object.fromEntries(TYPE_OPTIONS);
const SEVERITY_LABELS = Object.fromEntries(SEVERITY_OPTIONS);
const STATUS_LABELS = Object.fromEntries(STATUS_OPTIONS);
const MAX_ATTACHMENTS = 6;
const MAX_DESCRIPTION = 5000;
const MAX_COMMENT = 2000;
const CALENDAR_ICON = '<svg class="fb-cal-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M8 3v4M16 3v4M3 10h18"/></svg>';

function pad2(value) {
  return String(value).padStart(2, '0');
}

function dateKeyOf(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

const STYLE = `
.fb-tabs { display: inline-flex; gap: 6px; padding: 4px; border-radius: 999px; background: var(--paper); border: 1px solid var(--line); }
.fb-tabs .chip { border: 0; background: transparent; }
.fb-tabs .chip.is-active { background: var(--navy); color: #fff; }
@keyframes fb-view-in { from { opacity: .55; transform: translateY(6px); } to { opacity: 1; transform: none; } }
@keyframes fb-vt-out { to { opacity: 0; transform: translateY(-4px); } }
@keyframes fb-vt-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
.fb-view-enter { animation: fb-view-in .4s var(--ease) both; }
.fb-list-loading { opacity: .45; pointer-events: none; transition: opacity .22s ease; }
@keyframes fb-list-in { from { opacity: .35; } to { opacity: 1; } }
.fb-list-enter { animation: fb-list-in .24s ease both; }
.fb-vt-view { view-transition-name: fb-view; }
::view-transition-old(fb-view) { animation: fb-vt-out .2s ease both; }
::view-transition-new(fb-view) { animation: fb-vt-in .28s var(--ease) both; }
.fb-toolbar { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.fb-filter-rows { display: flex; flex-direction: column; gap: 8px; flex: 1 1 auto; min-width: 0; }
.fb-filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.fb-filter-secondary { justify-content: space-between; }
.fb-filter-secondary .search-box { flex: 1 1 auto; max-width: none; min-width: 200px; }
.fb-filter-secondary .search-box input { width: 100%; }
.fb-filter-primary { justify-content: space-between; }
.fb-filter-group { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.fb-filter-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; flex: none; }
.fb-filters .search-box { min-width: 200px; }
.fb-filters .search-box input { min-width: 0; }
.fb-filters .select-box select { min-width: 108px; }
.fb-daterange { position: relative; display: inline-flex; align-items: center; gap: 6px; border: 1px solid var(--line); border-radius: 9px; background: #fff; padding: 3px 9px; }
.fb-daterange span { font-size: 10.5px; color: var(--muted); }
.fb-daterange input { width: 88px; border: 0; background: transparent; padding: 5px 2px; font-size: 11.5px; color: var(--text); outline: none; text-align: center; }
.fb-daterange input::placeholder { color: var(--muted); font-size: 11px; }
.fb-daterange:focus-within { border-color: var(--line-strong); }
.fb-date-pick { display: inline-flex; align-items: center; justify-content: center; border: 0; background: transparent; padding: 2px; color: var(--muted); cursor: pointer; }
.fb-date-pick:hover { color: var(--navy); }
.fb-cal-icon { width: 13px; height: 13px; }
.fb-date-pop { position: absolute; top: calc(100% + 8px); right: 0; z-index: 60; width: 272px; padding: 12px; border-radius: 12px; border: 1px solid var(--line-strong); background: #fff; box-shadow: var(--shadow-lg); }
.fb-date-pop-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-size: 12px; color: var(--navy); font-weight: 600; }
.fb-date-nav { width: 26px; height: 26px; border: 1px solid var(--line); border-radius: 7px; background: #fff; color: var(--navy); cursor: pointer; line-height: 1; }
.fb-date-nav:hover { border-color: var(--line-strong); }
.fb-date-week { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; margin: 9px 0 4px; font-size: 10px; color: var(--muted); text-align: center; }
.fb-date-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; }
.fb-date-day { height: 30px; border: 1px solid transparent; border-radius: 7px; background: transparent; font-size: 11.5px; color: var(--text); cursor: pointer; }
.fb-date-day:hover { background: rgba(156,195,230,.18); }
.fb-date-day.is-out { color: var(--muted); opacity: .45; }
.fb-date-day.is-between { background: rgba(156,195,230,.2); border-radius: 0; }
.fb-date-day.is-start, .fb-date-day.is-end { background: var(--navy); color: #fff; }
.fb-date-day.is-today { border-color: var(--navy); }
.fb-date-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 9px; }
.fb-date-hint { font-size: 10px; color: var(--muted); }
.fb-edit-assignee { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border: 1px solid var(--line); border-radius: 9px; background: #fff; font-size: 12.5px; color: var(--navy); }
.fb-edit-assignee > span { flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fb-toolbar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.fb-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.fb-stat { display: flex; align-items: center; gap: 11px; padding: 13px 14px; border-radius: 14px; border: 1px solid var(--line); background: rgba(255,255,255,.9); box-shadow: var(--shadow-sm); text-align: left; cursor: pointer; font: inherit; transition: border-color .15s ease, background .15s ease, transform .15s ease; }
.fb-stat:hover { border-color: var(--line-strong); transform: translateY(-1px); }
.fb-stat.is-static { cursor: default; }
.fb-stat.is-static:hover { border-color: var(--line); transform: none; }
.fb-stat.is-active { border-color: var(--navy); background: rgba(156,195,230,.12); box-shadow: 0 0 0 1px var(--navy) inset; }
.fb-stat-icon { width: 36px; height: 36px; border-radius: 10px; background: rgba(44,76,102,.08); color: var(--navy); display: inline-flex; align-items: center; justify-content: center; flex: none; }
.fb-stat-icon .icon { width: 17px; height: 17px; }
.fb-stat-body { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.fb-stat-label { font-size: 10.5px; letter-spacing: .06em; color: var(--muted); white-space: nowrap; }
.fb-stat strong { font-family: var(--font-display); font-size: 24px; line-height: 1.15; color: var(--navy); }
.fb-stat small { font-size: 10px; color: var(--muted-2, var(--muted)); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fb-stat-pending .fb-stat-icon { background: rgba(232,201,122,.22); color: #a1752c; }
.fb-stat-processing .fb-stat-icon { background: rgba(156,195,230,.24); color: #2f6d9e; }
.fb-stat-unassigned .fb-stat-icon { background: rgba(180,140,205,.18); color: #6f5390; }
.fb-stat-resolved .fb-stat-icon { background: rgba(60,122,91,.14); color: var(--success); }
.fb-stat-rejected .fb-stat-icon { background: rgba(180,75,75,.12); color: var(--danger); }
.fb-stat-archived .fb-stat-icon { background: rgba(44,76,102,.07); color: var(--muted); }
.fb-panel { padding: 20px; border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.86); }
.fb-panel + .fb-panel { margin-top: 14px; }
.fb-panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; flex-wrap: wrap; margin-bottom: 14px; }
.fb-panel-head h2 { font-family: var(--font-display); font-size: 22px; color: var(--navy); }
.fb-panel-head p { font-size: 11.5px; color: var(--muted); margin-top: 3px; line-height: 1.7; }
.fb-badge { display: inline-flex; align-items: center; gap: 5px; padding: 2px 9px; border-radius: 999px; border: 1px solid var(--line); background: #fff; color: var(--muted); font-size: 10px; white-space: nowrap; }
.fb-badge.is-pending { color: #a1752c; border-color: rgba(207,170,85,.4); background: rgba(232,201,122,.16); }
.fb-badge.is-processing { color: #2f6d9e; border-color: rgba(76,140,190,.35); background: rgba(156,195,230,.18); }
.fb-badge.is-resolved { color: var(--success); border-color: rgba(60,122,91,.3); background: rgba(60,122,91,.08); }
.fb-badge.is-rejected { color: var(--danger); border-color: rgba(180,75,75,.3); background: rgba(180,75,75,.08); }
.fb-badge.is-archived { color: var(--muted); background: var(--paper); }
.fb-badge.is-info { color: #2f6d9e; border-color: rgba(76,140,190,.35); background: rgba(156,195,230,.16); }
.fb-sev { font-size: 10.5px; font-weight: 700; padding: 2px 8px; border-radius: 999px; border: 1px solid var(--line); color: var(--muted); white-space: nowrap; }
.fb-sev.is-high { color: #a1752c; border-color: rgba(207,170,85,.45); background: rgba(232,201,122,.14); }
.fb-sev.is-critical { color: var(--danger); border-color: rgba(180,75,75,.35); background: rgba(180,75,75,.08); }
.fb-table-wrap { overflow: auto; border-radius: 12px; border: 1px solid var(--line); }
.fb-table { width: 100%; border-collapse: collapse; font-size: 12px; min-width: 960px; }
.fb-table th { text-align: left; padding: 10px 12px; background: var(--paper); color: var(--muted); font-size: 10px; letter-spacing: .08em; text-transform: uppercase; white-space: nowrap; }
.fb-table td { padding: 10px 12px; border-top: 1px solid var(--line); vertical-align: middle; }
.fb-table tr:hover td { background: rgba(156,195,230,.07); }
.fb-cell-title { display: flex; flex-direction: column; gap: 3px; max-width: 340px; }
.fb-cell-title strong { color: var(--navy); font-size: 12.5px; }
.fb-cell-title small { color: var(--muted); font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fb-cell-sub { font-size: 10.5px; color: var(--muted); margin-top: 2px; }
.fb-row-actions { display: flex; gap: 6px; flex-wrap: wrap; }
.fb-row-actions .btn { height: 28px; padding: 0 10px; font-size: 11px; }
.fb-pager { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 16px; font-size: 12px; color: var(--muted); }
.fb-empty { padding: 52px 24px; text-align: center; border-radius: var(--radius); border: 1px dashed var(--line-strong); background: rgba(255,255,255,.6); color: var(--muted); }
.fb-empty h3 { font-family: var(--font-display); font-size: 21px; color: var(--navy); margin-bottom: 8px; }
.fb-empty p { font-size: 12.5px; max-width: 520px; margin: 0 auto 14px; line-height: 1.8; }
.fb-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.fb-field { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.fb-field > span { font-size: 10.5px; font-weight: 700; color: var(--muted); }
.fb-field input, .fb-field textarea, .fb-field select { width: 100%; border: 1px solid var(--line); border-radius: 9px; background: #fff; padding: 9px 11px; font-size: 12.5px; color: var(--text); }
.fb-field textarea { min-height: 120px; resize: vertical; line-height: 1.7; }
.fb-field small { font-size: 10.5px; color: var(--muted); line-height: 1.7; }
.fb-field.is-wide { grid-column: 1 / -1; }
.fb-count { text-align: right; font-family: var(--font-mono); }
.fb-form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; flex-wrap: wrap; }
.fb-attachments { display: flex; gap: 8px; flex-wrap: wrap; }
.fb-attachment { position: relative; width: 92px; height: 92px; border-radius: 10px; overflow: hidden; border: 1px solid var(--line); background: var(--paper); }
.fb-attachment img { width: 100%; height: 100%; object-fit: cover; display: block; }
.fb-attachment button { position: absolute; top: 4px; right: 4px; width: 20px; height: 20px; border-radius: 999px; border: 0; background: rgba(9,21,34,.65); color: #fff; font-size: 12px; line-height: 1; cursor: pointer; }
.fb-upload { display: inline-flex; align-items: center; gap: 6px; width: fit-content; cursor: pointer; }
.fb-meta { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px 18px; font-size: 11.5px; color: var(--muted); }
.fb-meta strong { color: var(--navy); font-weight: 600; }
.fb-desc { font-size: 12.5px; line-height: 1.9; color: var(--text); white-space: pre-wrap; margin-top: 14px; }
.fb-gallery { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
.fb-gallery a { width: 120px; height: 120px; border-radius: 10px; overflow: hidden; border: 1px solid var(--line); background: var(--paper); }
.fb-gallery img { width: 100%; height: 100%; object-fit: cover; display: block; }
.fb-timeline { display: flex; flex-direction: column; gap: 10px; margin-top: 6px; }
.fb-event { display: flex; align-items: flex-start; gap: 9px; padding: 3px 2px; font-size: 11.5px; color: var(--muted); }
.fb-event-dot { width: 7px; height: 7px; margin-top: 5px; border-radius: 999px; background: var(--line-strong); flex: none; }
.fb-event strong { color: var(--navy); font-weight: 600; }
.fb-event time { margin-left: 6px; font-size: 10.5px; color: var(--muted); }
.fb-comment { padding: 11px 13px; border-radius: 11px; border: 1px solid var(--line); background: #fff; }
.fb-comment.is-admin { border-color: rgba(76,140,190,.32); background: rgba(156,195,230,.1); }
.fb-comment.is-internal { border-style: dashed; border-color: rgba(207,170,85,.5); background: rgba(232,201,122,.1); }
.fb-comment-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 11.5px; color: var(--navy); }
.fb-comment-head time { margin-left: auto; font-size: 10.5px; color: var(--muted); }
.fb-comment-body { margin-top: 5px; font-size: 12.5px; line-height: 1.85; color: var(--text); white-space: pre-wrap; }
.fb-layout { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(280px, 1fr); gap: 14px; align-items: start; }
.fb-side { display: flex; flex-direction: column; gap: 14px; }
.fb-side .fb-panel + .fb-panel { margin-top: 0; }
.fb-side h3 { font-family: var(--font-display); font-size: 17px; color: var(--navy); }
.fb-side p.hint { font-size: 10.5px; color: var(--muted); line-height: 1.7; margin-top: 3px; margin-bottom: 8px; }
.fb-side textarea, .fb-side input, .fb-side select { width: 100%; border: 1px solid var(--line); border-radius: 8px; padding: 8px 10px; font-size: 12px; background: #fff; }
.fb-side textarea { min-height: 84px; resize: vertical; }
.fb-side .fb-form-actions { margin-top: 8px; }
.fb-chips { display: flex; gap: 6px; flex-wrap: wrap; }
.fb-chip { display: inline-flex; align-items: center; gap: 5px; padding: 3px 9px; border-radius: 999px; border: 1px solid var(--line); background: #fff; font-size: 11px; color: var(--navy); }
.fb-overlay { position: fixed; inset: 0; z-index: 120; display: flex; align-items: center; justify-content: center; padding: 24px; background: rgba(9,21,34,.45); backdrop-filter: blur(3px); }
.fb-dialog { width: min(720px, 100%); max-height: min(88vh, 900px); display: flex; flex-direction: column; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-lg); overflow: hidden; }
.fb-dialog-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 18px 22px 12px; border-bottom: 1px solid var(--line); }
.fb-dialog-head h2 { font-family: var(--font-display); font-size: 22px; color: var(--navy); }
.fb-dialog-body { padding: 16px 22px 20px; overflow: auto; display: flex; flex-direction: column; gap: 12px; }
.fb-dialog-actions { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 22px 18px; border-top: 1px solid var(--line); }
.fb-picker-tools { display: flex; gap: 8px; }
.fb-picker-tools .search-box { flex: 1; }
.fb-user-list { display: flex; flex-direction: column; gap: 4px; max-height: 320px; overflow: auto; border: 1px solid var(--line); border-radius: 10px; padding: 6px; }
.fb-user-row { display: flex; align-items: center; gap: 10px; padding: 7px 9px; border-radius: 8px; cursor: pointer; font-size: 12px; }
.fb-user-row:hover { background: rgba(156,195,230,.1); }
.fb-user-row input { width: 15px; height: 15px; }
.fb-user-avatar { width: 30px; height: 30px; border-radius: 999px; background: var(--navy); color: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; flex: none; overflow: hidden; }
.fb-user-avatar img { width: 100%; height: 100%; object-fit: cover; }
.fb-user-row strong { display: block; color: var(--navy); font-size: 12px; }
.fb-user-row small { color: var(--muted); font-size: 10.5px; }
.fb-picker-manual { display: flex; gap: 8px; }
.fb-picker-manual input { flex: 1; border: 1px solid var(--line); border-radius: 8px; padding: 8px 10px; font-size: 12px; }
.fb-selected { display: flex; gap: 6px; flex-wrap: wrap; }
.fb-note { font-size: 11px; color: var(--muted); line-height: 1.7; }
.fb-category-table { min-width: 640px; }
.fb-category-name { color: var(--navy); font-weight: 600; font-size: 12.5px; }
.fb-empty-sm { padding: 20px; text-align: center; color: var(--muted); font-size: 12px; }
@media (max-width: 980px) {
  .fb-layout { grid-template-columns: 1fr; }
  .fb-form { grid-template-columns: 1fr; }
  .fb-meta { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .fb-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 720px) {
  .fb-meta { grid-template-columns: 1fr; }
}
`;

export async function mount(root, ctx) {
  const { api, user, toast, confirm, escapeHtml: esc, icon, navigate } = ctx;
  const isAdmin = Boolean(user?.isAdmin);

  let disposed = false;
  let tab = ctx.query?.tab === 'admin' && isAdmin ? 'admin'
    : ctx.query?.tab === 'categories' && isAdmin ? 'categories'
    : 'mine';

  const mine = { mode: 'list', id: null, filters: { keyword: '', status: '', type: '', severity: '', page: 1, size: 10 }, data: null, detail: null };
  const admin = {
    mode: 'list',
    id: null,
    filters: { keyword: '', status: '', type: '', severity: '', categoryId: '', assigneeId: '', assigneeName: '', authorId: '', authorName: '', unassigned: false, from: '', to: '', page: 1, size: 10 },
    data: null,
    detail: null,
    stats: null,
    categories: null,
  };
  const categoryAdmin = { data: null };
  let draft = null;

  injectStyle();

  root.innerHTML = `
    <section class="plugin-shell fb">
      <header class="plugin-head">
        <div>
          <p class="eyebrow">PLUGIN · FEEDBACK</p>
          <h1>问题反馈</h1>
          <p class="plugin-lead">提交缺陷、建议或使用咨询，跟进处理进度；管理员跨用户受理、回复与指派处理人。</p>
        </div>
        <div class="plugin-head-actions">
          <div class="fb-tabs" role="tablist">
            <button class="chip is-active" data-tab="mine" role="tab">我的反馈</button>
            ${isAdmin ? '<button class="chip" data-tab="admin" role="tab">反馈管理</button>' : ''}
            ${isAdmin ? '<button class="chip" data-tab="categories" role="tab">分类管理</button>' : ''}
          </div>
        </div>
      </header>
      <div class="fb-vt-view" data-view></div>
    </section>
  `;
  const view = root.querySelector('[data-view]');

  root.querySelectorAll('[data-tab]').forEach((button) => {
    button.addEventListener('click', () => switchTab(button.dataset.tab));
  });

  // ---------------------------------------------------------------- 通用渲染

  function render() {
    if (disposed) return Promise.resolve();
    if (!user) {
      view.innerHTML = loginPrompt();
      view.querySelector('[data-login]')?.addEventListener('click', () => {
        navigate?.(`/login?next=${encodeURIComponent('/plugin/feedback')}`);
      });
      return Promise.resolve();
    }
    return tab === 'admin' ? renderAdmin()
      : tab === 'categories' ? renderCategoryAdmin()
      : renderMine();
  }

  function animateElement(element, className = 'fb-view-enter') {
    if (!element) return;
    element.classList.remove(className);
    void element.offsetWidth;
    element.classList.add(className);
  }

  let switching = false;

  /**
   * 页签切换过渡：有原生 View Transitions 时先保留旧画面，
   * 等新页签数据就绪后再交叉淡化；否则回退为淡入，避免中间出现空白闪烁。
   */
  function runViewTransition(update) {
    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (switching || reduceMotion || typeof document.startViewTransition !== 'function') {
      return Promise.resolve(update()).finally(() => {
        if (!reduceMotion) animateElement(view);
      });
    }
    switching = true;
    try {
      const transition = document.startViewTransition(() => Promise.resolve(update()));
      transition.updateCallbackDone.catch(() => {}).finally(() => {
        switching = false;
      });
      return transition.updateCallbackDone;
    } catch (error) {
      switching = false;
      return Promise.resolve(update());
    }
  }

  async function switchTab(next) {
    if (next === tab) return;
    if ((next === 'admin' || next === 'categories') && !isAdmin) return;
    await runViewTransition(async () => {
      tab = next;
      root.querySelectorAll('[data-tab]').forEach((item) => {
        item.classList.toggle('is-active', item.dataset.tab === tab);
      });
      await render();
    });
  }

  function injectStyle() {
    let style = document.getElementById('fb-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'fb-style';
      document.head.append(style);
    }
    style.textContent = STYLE;
  }

  /** 把筛选控件恢复到指定值；下拉框显式设置 selectedIndex，避免不同浏览器显示不一致。 */
  function setControlValue(input, value) {
    if (!input) return;
    const text = value == null ? '' : String(value);
    if (input.type === 'checkbox') {
      input.checked = Boolean(value);
      return;
    }
    if (input.tagName === 'SELECT') {
      const index = [...input.options].findIndex((option) => option.value === text);
      input.selectedIndex = index < 0 ? 0 : index;
      // 宿主把 select 换成了自定义下拉，显示层只监听 change/input；值变更后需主动通知。
      input.dispatchEvent(new Event('input', { bubbles: true }));
      return;
    }
    input.value = text;
  }

  /** 支持 YYYY-MM-DD、YYYY/M/D、YYYY.M.D、YYYYMMDD 四种手输格式，统一为 YYYY-MM-DD。 */
  function normalizeDateInput(raw) {
    const text = String(raw == null ? '' : raw).trim();
    if (!text) return { valid: true, value: '' };
    let match = /^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/.exec(text);
    if (!match) match = /^(\d{4})(\d{2})(\d{2})$/.exec(text);
    if (!match) return { valid: false, value: '' };
    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);
    const date = new Date(year, month - 1, day);
    if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
      return { valid: false, value: '' };
    }
    const pad = (value) => String(value).padStart(2, '0');
    return { valid: true, value: `${year}-${pad(month)}-${pad(day)}` };
  }

  function loginPrompt() {
    return `
      <div class="fb-empty">
        <h3>登录后提交反馈</h3>
        <p>问题反馈需要登录账号，提交后可以在「我的反馈」中跟进处理进度。</p>
        <button class="btn btn-primary" data-login>去登录</button>
      </div>`;
  }

  function optionHtml(options, selected) {
    return options.map(([value, label]) => `<option value="${value}" ${value === selected ? 'selected' : ''}>${label}</option>`).join('');
  }

  async function ensureAdminCategories(force = false) {
    if (!force && admin.categories) return admin.categories;
    try {
      const data = await api.get(`${BASE}/admin/categories`);
      admin.categories = data.records || [];
    } catch (error) {
      admin.categories = [];
    }
    return admin.categories;
  }

  function categoryOptions(categories, selected, includeDisabled = false) {
    return (categories || [])
      .filter((category) => includeDisabled || category.enabled !== false || category.id === selected)
      .map((category) => `<option value="${esc(category.id)}" ${category.id === selected ? 'selected' : ''}>${esc(category.name)}${category.enabled === false ? '（已停用）' : ''}</option>`)
      .join('');
  }

  function statusBadge(item) {
    return `<span class="fb-badge is-${esc(item.status)}">${esc(item.statusLabel)}</span>`;
  }

  function severityBadge(item) {
    const tone = item.severity === 'critical' ? 'is-critical' : item.severity === 'high' ? 'is-high' : '';
    return `<span class="fb-sev ${tone}">${esc(item.severityLabel)}</span>`;
  }

  function assigneeText(item) {
    const names = (item.assignees || []).map((person) => person.name || person.id).filter(Boolean);
    return names.length ? esc(names.join('、')) : '<span class="fb-cell-sub">待指派</span>';
  }

  function attachGallery(urls) {
    if (!urls?.length) return '';
    return `<div class="fb-gallery">${urls.map((url) => `<a href="${esc(url)}" target="_blank" rel="noopener noreferrer"><img src="${esc(url)}" alt="附件" loading="lazy"></a>`).join('')}</div>`;
  }

  function timelineHtml(item) {
    const events = (item.activities || [])
      .filter((activity) => !['comment', 'reply', 'note'].includes(activity.action))
      .map((activity) => ({ ...activity, kind: 'event' }));
    const comments = (item.comments || []).map((comment) => ({ ...comment, kind: 'comment' }));
    const rows = [...events, ...comments].sort((left, right) => (left.createdAt || 0) - (right.createdAt || 0));
    if (!rows.length) return '<div class="fb-empty-sm">暂无往来记录</div>';
    return `<div class="fb-timeline">${rows.map((row) => {
      if (row.kind === 'event') {
        const detail = row.detail ? ` · ${esc(row.detail)}` : '';
        return `<div class="fb-event"><span class="fb-event-dot"></span><div><strong>${esc(row.actorName || '系统')}</strong> ${esc(row.actionLabel)}${detail}<time>${esc(row.createdAtLabel)}</time></div></div>`;
      }
      return `<div class="fb-comment ${row.adminReply ? 'is-admin' : ''} ${row.internal ? 'is-internal' : ''}">
        <div class="fb-comment-head">
          <strong>${esc(row.authorName || row.authorId || '用户')}</strong>
          ${row.adminReply && !row.internal ? '<span class="fb-badge is-info">官方回复</span>' : ''}
          ${row.internal ? '<span class="fb-badge">仅管理员可见</span>' : ''}
          <time>${esc(row.createdAtLabel)}</time>
        </div>
        <div class="fb-comment-body">${esc(row.content)}</div>
      </div>`;
    }).join('')}</div>`;
  }

  function pagerHtml(page, size, total, scope) {
    const pages = Math.max(1, Math.ceil(total / Math.max(1, size)));
    if (pages <= 1) return `<div class="fb-pager">共 ${total} 条</div>`;
    return `<div class="fb-pager">
      <button class="btn btn-ghost btn-sm" data-pager="${scope}" data-page="${Math.max(1, page - 1)}" ${page <= 1 ? 'disabled' : ''}>上一页</button>
      <span>第 ${page} / ${pages} 页 · 共 ${total} 条</span>
      <button class="btn btn-ghost btn-sm" data-pager="${scope}" data-page="${Math.min(pages, page + 1)}" ${page >= pages ? 'disabled' : ''}>下一页</button>
    </div>`;
  }

  // ---------------------------------------------------------------- 我的反馈

  function renderMine() {
    if (mine.mode === 'new' || mine.mode === 'edit') return renderForm(mine.mode);
    if (mine.mode === 'detail' && mine.id) return openMineDetail(mine.id);
    return renderMineList();
  }

  async function renderMineList() {
    const f = mine.filters;
    view.innerHTML = `
      <div class="fb-toolbar">
        <div class="fb-filter-rows">
          <div class="fb-filters fb-filter-primary">
            <div class="fb-filter-group">
              <label class="select-box"><select data-mine-filter="status">
                <option value="">全部状态</option>${optionHtml(STATUS_OPTIONS, f.status)}
              </select></label>
              <label class="select-box"><select data-mine-filter="type">
                <option value="">全部类型</option>${optionHtml(TYPE_OPTIONS, f.type)}
              </select></label>
              <label class="select-box"><select data-mine-filter="severity">
                <option value="">全部严重程度</option>${optionHtml(SEVERITY_OPTIONS, f.severity)}
              </select></label>
            </div>
            <button class="btn btn-primary" data-mine-new>${icon('plus')} 提交反馈</button>
          </div>
          <div class="fb-filters fb-filter-secondary">
            <label class="search-box"><input type="search" data-mine-filter="keyword" placeholder="搜索标题或描述，输入后自动搜索" value="${esc(f.keyword)}"></label>
            <div class="fb-filter-actions">
              <button class="btn btn-ghost btn-sm" data-mine-reset>重置</button>
              <button class="btn btn-ghost btn-sm" data-mine-query>查询</button>
            </div>
          </div>
        </div>
      </div>
      <div data-mine-list><div class="plugin-loading">正在读取反馈…</div></div>
    `;
    bindMineListControls();
    await loadMineList();
  }

  function bindMineListControls() {
    const f = mine.filters;
    view.querySelectorAll('[data-mine-filter]').forEach((input) => {
      if (input.tagName === 'SELECT') {
        input.addEventListener('change', () => {
          f[input.dataset.mineFilter] = input.value;
          f.page = 1;
          loadMineList({ keep: true });
        });
      } else {
        let timer = null;
        input.addEventListener('input', () => {
          f[input.dataset.mineFilter] = input.value;
          clearTimeout(timer);
          timer = setTimeout(() => {
            f.page = 1;
            loadMineList({ keep: true });
          }, 300);
        });
      }
    });
    view.querySelector('[data-mine-reset]')?.addEventListener('click', () => {
      Object.assign(f, { keyword: '', status: '', type: '', severity: '', page: 1 });
      view.querySelectorAll('[data-mine-filter]').forEach((input) => {
        setControlValue(input, f[input.dataset.mineFilter]);
      });
      loadMineList({ keep: true });
    });
    view.querySelector('[data-mine-query]')?.addEventListener('click', () => {
      f.page = 1;
      loadMineList({ keep: true });
    });
    view.querySelector('[data-mine-new]')?.addEventListener('click', () => {
      runViewTransition(() => {
        mine.mode = 'new';
        draft = { title: '', description: '', type: 'bug', severity: 'normal', categoryId: '', attachments: [] };
        return renderMine();
      });
    });
  }

  async function loadMineList(options = {}) {
    const host = view.querySelector('[data-mine-list]');
    if (!host) return;
    const keep = Boolean(options.keep);
    if (keep || host.querySelector('.fb-table-wrap, .fb-empty')) {
      host.classList.add('fb-list-loading');
    }
    const f = mine.filters;
    try {
      const data = await api.get(`${BASE}/me/feedbacks`, {
        keyword: f.keyword,
        status: f.status,
        type: f.type,
        severity: f.severity,
        page: f.page,
        size: f.size,
      });
      if (disposed) return;
      mine.data = data;
      paintMineList();
    } catch (error) {
      host.classList.remove('fb-list-loading');
      host.innerHTML = `<div class="fb-empty"><h3>反馈列表加载失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintMineList() {
    const host = view.querySelector('[data-mine-list]');
    if (!host || !mine.data) return;
    const { records, total, page, size } = mine.data;
    if (!records.length) {
      host.classList.remove('fb-list-loading');
      host.innerHTML = `<div class="fb-empty"><h3>还没有反馈</h3><p>点右上角「提交反馈」写下你的第一个问题。</p></div>`;
      animateElement(host, 'fb-list-enter');
      return;
    }
    host.innerHTML = `
      <div class="fb-table-wrap">
        <table class="fb-table">
          <thead><tr><th>反馈内容</th><th>类型</th><th>分类</th><th>严重程度</th><th>处理状态</th><th>处理人</th><th>往来</th><th>最近更新</th><th>操作</th></tr></thead>
          <tbody>${records.map((item) => `
            <tr>
              <td><div class="fb-cell-title"><strong>${esc(item.title)}</strong><small>${esc(item.description)}</small></div></td>
              <td>${esc(item.typeLabel)}</td>
              <td>${item.categoryName ? esc(item.categoryName) : '<span class="fb-cell-sub">未分类</span>'}</td>
              <td>${severityBadge(item)}</td>
              <td>${statusBadge(item)}</td>
              <td>${assigneeText(item)}</td>
              <td>${item.commentCount ? `${item.commentCount} 条<div class="fb-cell-sub">含官方回复与你的补充说明</div>` : '<span class="fb-cell-sub">暂无往来记录</span>'}</td>
              <td>${esc(item.updatedAtLabel)}</td>
              <td><div class="fb-row-actions">
                <button class="btn btn-ghost btn-sm" data-mine-open="${esc(item.id)}">查看</button>
                ${item.canArchive ? `<button class="btn btn-ghost btn-sm" data-mine-archive="${esc(item.id)}">归档</button>` : ''}
              </div></td>
            </tr>`).join('')}
          </tbody>
        </table>
      </div>
      ${pagerHtml(page, size, total, 'mine')}
    `;
    host.classList.remove('fb-list-loading');
    animateElement(host, 'fb-list-enter');
    host.querySelectorAll('[data-mine-open]').forEach((button) => {
      button.addEventListener('click', () => openMineDetail(button.dataset.mineOpen));
    });
    host.querySelectorAll('[data-mine-archive]').forEach((button) => {
      button.addEventListener('click', () => archiveMine(button.dataset.mineArchive));
    });
    host.querySelectorAll('[data-pager="mine"]').forEach((button) => {
      button.addEventListener('click', () => {
        mine.filters.page = Number(button.dataset.page) || 1;
        loadMineList({ keep: true });
      });
    });
  }

  async function archiveMine(id) {
    const ok = await confirm({
      title: '归档反馈',
      message: '归档后不再进入处理流程，但仍可追加归档说明。确认归档？',
      confirmText: '归档',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.post(`${BASE}/me/archive?id=${encodeURIComponent(id)}`);
      toast('反馈已归档', 'success');
      if (mine.mode === 'detail') openMineDetail(id);
      else loadMineList({ keep: true });
    } catch (error) {
      toast(error.message || '归档失败', 'error');
    }
  }

  async function openMineDetail(id) {
    try {
      const data = await api.get(`${BASE}/me/feedback`, { id });
      if (disposed) return;
      mine.mode = 'detail';
      mine.id = id;
      mine.detail = data;
      await runViewTransition(() => paintMineDetail());
    } catch (error) {
      mine.mode = 'detail';
      mine.id = id;
      await runViewTransition(() => {
        view.innerHTML = `<div class="fb-empty"><h3>反馈加载失败</h3><p>${esc(error.message || '未知错误')}</p><button class="btn btn-ghost" data-back>返回我的反馈</button></div>`;
        view.querySelector('[data-back]')?.addEventListener('click', backToMineList);
      });
    }
  }

  function paintMineDetail() {
    const item = mine.detail;
    if (!item) return;
    const archiveHint = item.status === 'resolved' || item.status === 'rejected'
      ? `<div class="fb-note">管理员已给出结论，可点击「归档」结束流程。${item.autoArchiveAtLabel ? `若未归档，将于 ${esc(item.autoArchiveAtLabel)} 自动归档。` : ''}</div>`
      : '';
    view.innerHTML = `
      <div class="fb-panel">
        <div class="fb-panel-head">
          <div>
            <div class="fb-chips">${statusBadge(item)}${severityBadge(item)}<span class="fb-badge">${esc(item.typeLabel)}</span>${item.categoryName ? `<span class="fb-badge">分类：${esc(item.categoryName)}</span>` : ''}</div>
            <h2 style="margin-top:8px">${esc(item.title)}</h2>
            <p>${esc(item.description)}</p>
          </div>
          <div class="fb-toolbar-right">
            <button class="btn btn-ghost btn-sm" data-back>返回列表</button>
            ${item.canEdit ? `<button class="btn btn-ghost btn-sm" data-edit>${icon('edit')} 修改内容</button>` : ''}
            ${item.canArchive ? '<button class="btn btn-primary btn-sm" data-archive>归档</button>' : ''}
            ${item.open ? '<button class="btn btn-ghost btn-sm" data-withdraw>撤回</button>' : ''}
          </div>
        </div>
        <div class="fb-meta">
          <div>提交人：<strong>${esc(item.authorName || '我')}</strong></div>
          <div>提交于：<strong>${esc(item.createdAtLabel)}</strong></div>
          <div>最近更新：<strong>${esc(item.updatedAtLabel)}</strong></div>
          <div>处理人：<strong>${(item.assignees || []).map((person) => esc(person.name || person.id)).join('、') || '尚未指派'}</strong></div>
          ${item.closedAtLabel ? `<div>${item.status === 'archived' ? '归档于' : '结论于'}：<strong>${esc(item.closedAtLabel)}</strong></div>` : ''}
          ${item.autoArchiveAtLabel ? `<div>自动归档：<strong>${esc(item.autoArchiveAtLabel)}</strong></div>` : ''}
        </div>
        <div class="fb-desc">${esc(item.description)}</div>
        ${attachGallery(item.attachments)}
        ${archiveHint}
      </div>
      <div class="fb-panel">
        <div class="fb-panel-head"><div><h2>处理记录</h2><p>官方回复、你的补充说明与状态变更按时间合并展示。</p></div></div>
        ${timelineHtml(item)}
        <div class="fb-field is-wide" style="margin-top:16px">
          <span>追加说明</span>
          <textarea data-comment maxlength="${MAX_COMMENT}" placeholder="补充复现步骤或新的信息"></textarea>
          <small class="fb-count" data-comment-count>还可以输入 ${MAX_COMMENT} 个字符</small>
        </div>
        <div class="fb-form-actions"><button class="btn btn-primary" data-comment-send>提交补充</button></div>
      </div>
    `;
    bindMineDetail();
  }

  function bindMineDetail() {
    const item = mine.detail;
    view.querySelector('[data-back]')?.addEventListener('click', backToMineList);
    view.querySelector('[data-archive]')?.addEventListener('click', () => archiveMine(item.id));
    view.querySelector('[data-edit]')?.addEventListener('click', () => {
      draft = {
        id: item.id,
        title: item.title,
        description: item.description,
        type: item.type,
        severity: item.severity,
        categoryId: item.categoryId || '',
        attachments: (item.attachments || []).map((url) => ({ url, name: '已上传图片' })),
      };
      runViewTransition(() => {
        mine.mode = 'edit';
        return renderMine();
      });
    });
    view.querySelector('[data-withdraw]')?.addEventListener('click', async () => {
      const ok = await confirm({ title: '撤回反馈', message: '撤回后反馈将被删除且无法恢复，确认撤回？', confirmText: '撤回', danger: true });
      if (!ok) return;
      try {
        await api.del(`${BASE}/me/feedback?id=${encodeURIComponent(item.id)}`);
        toast('反馈已撤回', 'success');
        backToMineList();
      } catch (error) {
        toast(error.message || '撤回失败', 'error');
      }
    });
    const textarea = view.querySelector('[data-comment]');
    const counter = view.querySelector('[data-comment-count]');
    textarea?.addEventListener('input', () => {
      counter.textContent = `还可以输入 ${Math.max(0, MAX_COMMENT - textarea.value.length)} 个字符`;
    });
    view.querySelector('[data-comment-send]')?.addEventListener('click', async () => {
      const content = textarea.value.trim();
      if (!content) {
        toast('请填写补充说明的内容', 'error');
        return;
      }
      try {
        await api.post(`${BASE}/me/comment?id=${encodeURIComponent(item.id)}`, { content });
        toast('补充说明已提交', 'success');
        openMineDetail(item.id);
      } catch (error) {
        toast(error.message || '提交失败，请稍后重试', 'error');
      }
    });
  }

  function backToMineList() {
    return runViewTransition(() => {
      mine.mode = 'list';
      mine.id = null;
      mine.detail = null;
      return renderMine();
    });
  }

  // ---------------------------------------------------------------- 提交 / 修改

  async function renderForm(mode) {
    if (!draft) draft = { title: '', description: '', type: 'bug', severity: 'normal', categoryId: '', attachments: [] };
    if (!draft.categories) {
      try {
        const data = await api.get(`${BASE}/categories`);
        draft.categories = data.records || [];
      } catch (error) {
        draft.categories = [];
      }
    }
    if (draft.categoryId && !draft.categories.some((category) => category.id === draft.categoryId)) {
      draft.categoryId = '';
    }
    const editing = mode === 'edit';
    view.innerHTML = `
      <div class="fb-panel">
        <div class="fb-panel-head">
          <div>
            <h2>${editing ? '修改反馈' : '提交反馈'}</h2>
            <p>${editing ? '仅未完结的反馈可以修改；修改不会改变当前处理进度。' : '描述你遇到的问题或想法，提交后可在「我的反馈」中跟进处理进度。'}</p>
          </div>
          <button class="btn btn-ghost btn-sm" data-back>返回我的反馈</button>
        </div>
        <div class="fb-form">
          <label class="fb-field is-wide"><span>反馈标题</span>
            <input data-field="title" maxlength="120" placeholder="一句话概括问题，例如「物料上传后缩略图不显示」" value="${esc(draft.title)}">
          </label>
          <label class="fb-field"><span>反馈类型</span>
            <select data-field="type">${optionHtml(TYPE_OPTIONS, draft.type)}</select>
            <small>便于管理员按类型分派，后期可修正</small>
          </label>
          <label class="fb-field"><span>严重程度</span>
            <select data-field="severity">${optionHtml(SEVERITY_OPTIONS, draft.severity)}</select>
            <small>影响使用的紧急程度，仅作受理参考</small>
          </label>
          <label class="fb-field"><span>反馈分类</span>
            <select data-field="categoryId"><option value="">未分类</option>${categoryOptions(draft.categories, draft.categoryId)}</select>
            <small>${draft.categories.length ? '由管理员维护，便于快速分派' : '管理员尚未配置分类，可先留空'}</small>
          </label>
          <label class="fb-field is-wide"><span>问题描述</span>
            <textarea data-field="description" maxlength="${MAX_DESCRIPTION}" placeholder="请说明操作步骤、预期结果与实际结果；有报错信息或复现路径时一并附上。">${esc(draft.description)}</textarea>
            <small class="fb-count" data-desc-count></small>
          </label>
          <div class="fb-field is-wide">
            <span>截图或附件</span>
            <div class="fb-attachments" data-attachments></div>
            <label class="btn btn-ghost btn-sm fb-upload">${icon('image')} 选择图片<input type="file" accept="image/*" multiple hidden data-file></label>
            <small>最多 ${MAX_ATTACHMENTS} 张，支持直接粘贴截图</small>
          </div>
        </div>
        <div class="fb-form-actions">
          <button class="btn btn-ghost" data-back>取消</button>
          <button class="btn btn-primary" data-save>${editing ? '保存修改' : '提交反馈'}</button>
        </div>
      </div>
    `;
    bindForm(mode);
  }

  function bindForm(mode) {
    const description = view.querySelector('[data-field="description"]');
    const counter = view.querySelector('[data-desc-count]');
    const refreshCounter = () => {
      counter.textContent = `${description.value.length} / ${MAX_DESCRIPTION}`;
    };
    refreshCounter();

    view.querySelectorAll('[data-field]').forEach((input) => {
      input.addEventListener('input', () => {
        draft[input.dataset.field] = input.value;
        if (input.dataset.field === 'description') refreshCounter();
      });
      if (input.tagName === 'SELECT') {
        input.addEventListener('change', () => {
          draft[input.dataset.field] = input.value;
        });
      }
    });

    const paintAttachments = () => {
      const host = view.querySelector('[data-attachments]');
      if (!host) return;
      host.innerHTML = draft.attachments.map((file, index) => `
        <div class="fb-attachment">
          <img src="${esc(file.url)}" alt="${esc(file.name)}">
          <button type="button" data-remove-attachment="${index}" aria-label="移除">×</button>
        </div>`).join('');
      host.querySelectorAll('[data-remove-attachment]').forEach((button) => {
        button.addEventListener('click', () => {
          draft.attachments.splice(Number(button.dataset.removeAttachment), 1);
          paintAttachments();
        });
      });
    };
    paintAttachments();

    const uploadFiles = async (files) => {
      for (const file of files) {
        if (!file.type.startsWith('image/')) {
          toast('仅支持图片附件', 'error');
          continue;
        }
        if (draft.attachments.length >= MAX_ATTACHMENTS) {
          toast(`最多上传 ${MAX_ATTACHMENTS} 张图片`, 'error');
          break;
        }
        try {
          const buffer = await file.arrayBuffer();
          const result = await api.upload(`${BASE}/attachment?name=${encodeURIComponent(file.name)}`, buffer, file.type);
          draft.attachments.push({ url: result.url, name: result.name || file.name });
          paintAttachments();
        } catch (error) {
          toast(error.message || '图片上传失败', 'error');
        }
      }
    };

    view.querySelector('[data-file]')?.addEventListener('change', (event) => {
      uploadFiles([...event.target.files]);
      event.target.value = '';
    });

    description?.addEventListener('paste', (event) => {
      const files = [...(event.clipboardData?.items || [])]
        .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
        .map((item) => item.getAsFile())
        .filter(Boolean);
      if (files.length) {
        event.preventDefault();
        uploadFiles(files);
      }
    });

    view.querySelectorAll('[data-back]').forEach((button) => {
      button.addEventListener('click', () => {
        if (mode === 'edit' && mine.id) openMineDetail(mine.id);
        else backToMineList();
      });
    });

    view.querySelector('[data-save]')?.addEventListener('click', async (event) => {
      const title = draft.title.trim();
      const body = draft.description.trim();
      if (!title) {
        toast('请填写反馈标题', 'error');
        return;
      }
      if (!body) {
        toast('请填写问题描述', 'error');
        return;
      }
      if (body.length > MAX_DESCRIPTION) {
        toast(`问题描述不能超过 ${MAX_DESCRIPTION} 个字符`, 'error');
        return;
      }
      const payload = {
        title,
        description: body,
        type: draft.type,
        severity: draft.severity,
        categoryId: draft.categoryId || '',
        attachments: draft.attachments.map((file) => file.url),
      };
      event.currentTarget.disabled = true;
      try {
        if (mode === 'edit') {
          const result = await api.put(`${BASE}/me/feedback?id=${encodeURIComponent(draft.id)}`, payload);
          toast('反馈内容已更新', 'success');
          mine.detail = result;
          mine.id = result.id;
          mine.mode = 'detail';
          await runViewTransition(() => paintMineDetail());
        } else {
          await api.post(`${BASE}/me/feedbacks`, payload);
          toast('反馈已提交，处理进度可在「我的反馈」中跟进', 'success');
          mine.filters.page = 1;
          backToMineList();
        }
        draft = null;
      } catch (error) {
        toast(error.message || '提交失败，请稍后重试', 'error');
        event.currentTarget.disabled = false;
      }
    });
  }

  // ---------------------------------------------------------------- 管理端

  function renderAdmin() {
    if (admin.mode === 'detail' && admin.id) return openAdminDetail(admin.id);
    return renderAdminList();
  }

  async function renderAdminList() {
    closeRangePicker();
    const f = admin.filters;
    const categories = await ensureAdminCategories();
    view.innerHTML = `
      <div data-admin-stats></div>
      <div class="fb-toolbar">
        <div class="fb-filter-rows">
          <div class="fb-filters">
            <label class="select-box"><select data-admin-filter="status"><option value="">全部状态</option>${optionHtml(STATUS_OPTIONS, f.status)}</select></label>
            <label class="select-box"><select data-admin-filter="type"><option value="">全部类型</option>${optionHtml(TYPE_OPTIONS, f.type)}</select></label>
            <label class="select-box"><select data-admin-filter="severity"><option value="">全部严重程度</option>${optionHtml(SEVERITY_OPTIONS, f.severity)}</select></label>
            <label class="select-box"><select data-admin-filter="categoryId"><option value="">全部分类</option>${categoryOptions(categories, f.categoryId, true)}</select></label>
            <button class="btn btn-ghost btn-sm" data-pick-assignee-filter>${f.assigneeName ? `处理人：${esc(f.assigneeName)}` : '按处理人筛选'}</button>
            <button class="btn btn-ghost btn-sm" data-pick-author-filter>${f.authorName ? `提交人：${esc(f.authorName)}` : '按提交人筛选'}</button>
            <div class="fb-daterange" title="提交时间范围，格式 YYYY-MM-DD">
              <input type="text" autocomplete="off" maxlength="10" data-admin-filter="from" value="${esc(f.from)}" placeholder="开始日期" aria-label="提交开始日期">
              <span>至</span>
              <input type="text" autocomplete="off" maxlength="10" data-admin-filter="to" value="${esc(f.to)}" placeholder="结束日期" aria-label="提交结束日期">
              <button type="button" class="fb-date-pick" data-range-pick title="选择日期范围">${CALENDAR_ICON}</button>
            </div>
          </div>
          <div class="fb-filters fb-filter-secondary">
            <label class="search-box"><input type="search" data-admin-filter="keyword" placeholder="搜索标题、描述或回复内容，输入后自动搜索" value="${esc(f.keyword)}"></label>
            <div class="fb-filter-actions">
              <button class="btn btn-ghost btn-sm" data-admin-reset>重置</button>
              <button class="btn btn-ghost btn-sm" data-admin-query>查询</button>
            </div>
          </div>
        </div>
      </div>
      <div data-admin-list><div class="plugin-loading">正在读取反馈…</div></div>
    `;
    bindAdminListControls();
    await Promise.all([loadAdminStats(), loadAdminList()]);
  }

  function syncAdminFilterControls() {
    const f = admin.filters;
    view.querySelectorAll('[data-admin-filter]').forEach((input) => {
      setControlValue(input, f[input.dataset.adminFilter]);
    });
    const assignee = view.querySelector('[data-pick-assignee-filter]');
    if (assignee) assignee.textContent = f.assigneeName ? `处理人：${f.assigneeName}` : '按处理人筛选';
    const author = view.querySelector('[data-pick-author-filter]');
    if (author) author.textContent = f.authorName ? `提交人：${f.authorName}` : '按提交人筛选';
  }

  function activeStatKey() {
    const f = admin.filters;
    if (f.unassigned) return 'unassigned';
    return f.status || 'total';
  }

  function syncAdminStatActive() {
    const key = activeStatKey();
    view.querySelectorAll('.fb-stat').forEach((card) => {
      card.classList.toggle('is-active', card.dataset.stat === key);
    });
  }

  /** 筛选条件变化时只更新控件与列表，不重建整页，避免闪烁。 */
  function applyAdminFilter(patch) {
    Object.assign(admin.filters, patch, { page: 1 });
    syncAdminFilterControls();
    syncAdminStatActive();
    loadAdminList({ keep: true });
  }

  function bindAdminListControls() {
    const f = admin.filters;
    view.querySelectorAll('[data-admin-filter]').forEach((input) => {
      if (input.type === 'checkbox') {
        input.addEventListener('change', () => {
          f[input.dataset.adminFilter] = input.checked;
          f.page = 1;
          syncAdminStatActive();
          loadAdminList({ keep: true });
        });
      } else if (input.dataset.adminFilter === 'from' || input.dataset.adminFilter === 'to') {
        const applyDate = () => {
          const key = input.dataset.adminFilter;
          const parsed = normalizeDateInput(input.value);
          if (!parsed.valid) {
            toast('日期格式应为 YYYY-MM-DD，例如 2026-01-01', 'error');
            input.value = f[key] || '';
            return;
          }
          const next = { from: f.from, to: f.to, [key]: parsed.value };
          if (next.from && next.to && next.from > next.to) {
            toast('开始日期不能晚于结束日期', 'error');
            input.value = f[key] || '';
            return;
          }
          input.value = parsed.value;
          if (parsed.value === f[key]) return;
          f[key] = parsed.value;
          f.page = 1;
          loadAdminList({ keep: true });
        };
        input.addEventListener('change', applyDate);
        input.addEventListener('keydown', (event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            input.blur();
          }
        });
      } else if (input.tagName === 'SELECT') {
        input.addEventListener('change', () => {
          f[input.dataset.adminFilter] = input.value;
          f.page = 1;
          syncAdminStatActive();
          loadAdminList({ keep: true });
        });
      } else {
        let timer = null;
        input.addEventListener('input', () => {
          f[input.dataset.adminFilter] = input.value;
          clearTimeout(timer);
          timer = setTimeout(() => {
            f.page = 1;
            loadAdminList({ keep: true });
          }, 300);
        });
      }
    });
    view.querySelector('[data-range-pick]')?.addEventListener('click', (event) => {
      openRangePicker(event.currentTarget);
    });
    view.querySelector('[data-admin-reset]')?.addEventListener('click', () => {
      Object.assign(f, {
        keyword: '', status: '', type: '', severity: '', categoryId: '', assigneeId: '', assigneeName: '',
        authorId: '', authorName: '', unassigned: false, from: '', to: '', page: 1,
      });
      syncAdminFilterControls();
      syncAdminStatActive();
      loadAdminList({ keep: true });
    });
    view.querySelector('[data-admin-query]')?.addEventListener('click', () => {
      const fromInput = view.querySelector('[data-admin-filter="from"]');
      const toInput = view.querySelector('[data-admin-filter="to"]');
      const from = normalizeDateInput(fromInput?.value);
      const to = normalizeDateInput(toInput?.value);
      if (!from.valid || !to.valid) {
        toast('日期格式应为 YYYY-MM-DD，例如 2026-01-01', 'error');
        return;
      }
      if (from.value && to.value && from.value > to.value) {
        toast('开始日期不能晚于结束日期', 'error');
        return;
      }
      f.from = from.value;
      f.to = to.value;
      if (fromInput) fromInput.value = from.value;
      if (toInput) toInput.value = to.value;
      f.page = 1;
      loadAdminList({ keep: true });
    });
    view.querySelector('[data-pick-assignee-filter]')?.addEventListener('click', () => {
      pickSingleUser('按处理人筛选', (person) => {
        applyAdminFilter({ assigneeId: person.id, assigneeName: person.name || person.id });
      });
    });
    view.querySelector('[data-pick-author-filter]')?.addEventListener('click', () => {
      pickSingleUser('按提交人筛选', (person) => {
        applyAdminFilter({ authorId: person.id, authorName: person.name || person.id });
      });
    });
  }

  let rangePop = null;

  function closeRangePicker() {
    if (!rangePop) return;
    rangePop.el.remove();
    document.removeEventListener('mousedown', rangePop.onDocClick);
    document.removeEventListener('keydown', rangePop.onKey);
    rangePop = null;
  }

  function applyAdminDateRange(start, end) {
    const fromInput = view.querySelector('[data-admin-filter="from"]');
    const toInput = view.querySelector('[data-admin-filter="to"]');
    if (fromInput) fromInput.value = start || '';
    if (toInput) toInput.value = end || '';
    admin.filters.from = start || '';
    admin.filters.to = end || '';
    admin.filters.page = 1;
    loadAdminList({ keep: true });
  }

  function rangeCalendarHtml(state) {
    const first = new Date(state.year, state.month, 1);
    const offset = (first.getDay() + 6) % 7;
    const gridStart = new Date(state.year, state.month, 1 - offset);
    const today = dateKeyOf(new Date());
    const cells = [];
    for (let index = 0; index < 42; index += 1) {
      const date = new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + index);
      const key = dateKeyOf(date);
      const classes = ['fb-date-day'];
      if (date.getMonth() !== state.month) classes.push('is-out');
      if (state.start && key === state.start) classes.push('is-start');
      if (state.end && key === state.end) classes.push('is-end');
      if (state.start && state.end && key > state.start && key < state.end) classes.push('is-between');
      if (key === today) classes.push('is-today');
      cells.push(`<button type="button" class="${classes.join(' ')}" data-date-key="${key}">${date.getDate()}</button>`);
    }
    return `
      <div class="fb-date-pop-head">
        <button type="button" class="fb-date-nav" data-range-prev aria-label="上个月">‹</button>
        <span>${state.year} 年 ${state.month + 1} 月</span>
        <button type="button" class="fb-date-nav" data-range-next aria-label="下个月">›</button>
      </div>
      <div class="fb-date-week">${['一', '二', '三', '四', '五', '六', '日'].map((day) => `<span>${day}</span>`).join('')}</div>
      <div class="fb-date-grid">${cells.join('')}</div>
      <div class="fb-date-foot">
        <span class="fb-date-hint">先点开始日期，再点结束日期</span>
        <button type="button" class="btn btn-ghost btn-sm" data-range-clear>清除</button>
      </div>
    `;
  }

  /** 单个弹窗里依次点击起止日期，第二次点击后自动应用。 */
  function openRangePicker(anchor) {
    closeRangePicker();
    const fromInput = view.querySelector('[data-admin-filter="from"]');
    const toInput = view.querySelector('[data-admin-filter="to"]');
    const from = normalizeDateInput(fromInput?.value).value;
    const to = normalizeDateInput(toInput?.value).value;
    const baseKey = from || dateKeyOf(new Date());
    const base = new Date(`${baseKey}T00:00:00`);
    const state = {
      start: from,
      end: to,
      year: base.getFullYear(),
      month: base.getMonth(),
    };
    const el = document.createElement('div');
    el.className = 'fb-date-pop';
    const paint = () => {
      el.innerHTML = rangeCalendarHtml(state);
    };
    paint();
    (anchor.closest('.fb-daterange') || anchor.parentNode).append(el);

    el.addEventListener('click', (event) => {
      if (event.target.closest('[data-range-prev]')) {
        state.month -= 1;
        if (state.month < 0) {
          state.month = 11;
          state.year -= 1;
        }
        paint();
        return;
      }
      if (event.target.closest('[data-range-next]')) {
        state.month += 1;
        if (state.month > 11) {
          state.month = 0;
          state.year += 1;
        }
        paint();
        return;
      }
      if (event.target.closest('[data-range-clear]')) {
        applyAdminDateRange('', '');
        closeRangePicker();
        return;
      }
      const day = event.target.closest('[data-date-key]');
      if (!day) return;
      const key = day.dataset.dateKey;
      if (!state.start || state.end) {
        state.start = key;
        state.end = '';
      } else if (key < state.start) {
        state.end = state.start;
        state.start = key;
      } else {
        state.end = key;
      }
      if (state.start && state.end) {
        applyAdminDateRange(state.start, state.end);
        closeRangePicker();
        return;
      }
      paint();
    });

    const onDocClick = (event) => {
      if (el.contains(event.target) || anchor.contains(event.target)) return;
      closeRangePicker();
    };
    const onKey = (event) => {
      if (event.key === 'Escape') closeRangePicker();
    };
    rangePop = { el, onDocClick, onKey };
    setTimeout(() => {
      document.addEventListener('mousedown', onDocClick);
      document.addEventListener('keydown', onKey);
    }, 0);
  }

  async function loadAdminStats() {
    try {
      admin.stats = await api.get(`${BASE}/admin/stats`);
      if (disposed) return;
      const host = view.querySelector('[data-admin-stats]');
      if (!host) return;
      const f = admin.filters;
      const cards = [
        ['total', '全部反馈', 'layers', '不含已归档', () => applyAdminFilter({ status: '', unassigned: false }), f.status === '' && !f.unassigned],
        ['pending', '待处理', 'clock', '等待受理', () => applyAdminFilter({ status: 'pending', unassigned: false }), f.status === 'pending'],
        ['processing', '处理中', 'refresh', '正在跟进', () => applyAdminFilter({ status: 'processing', unassigned: false }), f.status === 'processing'],
        ['open', '未完结合计', 'sparkles', '待处理 + 处理中（汇总，不支持筛选）', null, false],
        ['unassigned', '待指派', 'users', '还没有处理人', () => applyAdminFilter({ unassigned: true, status: '' }), f.unassigned],
        ['resolved', '已解决', 'check', '管理员已给结论', () => applyAdminFilter({ status: 'resolved', unassigned: false }), f.status === 'resolved'],
        ['rejected', '不予处理', 'close', '管理员已给结论', () => applyAdminFilter({ status: 'rejected', unassigned: false }), f.status === 'rejected'],
        ['archived', '已归档', 'panel', '已结束流程', () => applyAdminFilter({ status: 'archived', unassigned: false }), f.status === 'archived'],
      ];
      host.innerHTML = `<div class="fb-stats">${cards.map(([key, label, iconName, hint, action, active]) => {
        const tag = action ? 'button' : 'div';
        const interactive = action ? `type="button" data-stat="${key}"` : '';
        return `
        <${tag} class="fb-stat fb-stat-${key} ${active ? 'is-active' : ''}${action ? '' : ' is-static'}" ${interactive} title="${esc(hint)}">
          <span class="fb-stat-icon">${icon(iconName)}</span>
          <span class="fb-stat-body">
            <span class="fb-stat-label">${label}</span>
            <strong>${admin.stats[key] ?? 0}</strong>
            <small>${hint}</small>
          </span>
        </${tag}>
      `;
      }).join('')}</div>`;
      const actions = new Map(cards.map(([key, , , , action]) => [key, action]).filter(([, action]) => Boolean(action)));
      host.querySelectorAll('button[data-stat]').forEach((button) => {
        button.addEventListener('click', actions.get(button.dataset.stat));
      });
      syncAdminStatActive();
    } catch (error) {
      const host = view.querySelector('[data-admin-stats]');
      if (host) host.innerHTML = `<div class="fb-note">反馈统计加载失败：${esc(error.message || '未知错误')}</div>`;
    }
  }

  async function loadAdminList(options = {}) {
    const host = view.querySelector('[data-admin-list]');
    if (!host) return;
    const keep = Boolean(options.keep);
    if (keep || host.querySelector('.fb-table-wrap, .fb-empty')) {
      host.classList.add('fb-list-loading');
    }
    const f = admin.filters;
    try {
      const data = await api.get(`${BASE}/admin/feedbacks`, {
        keyword: f.keyword,
        status: f.status,
        type: f.type,
        severity: f.severity,
        categoryId: f.categoryId,
        assigneeId: f.assigneeId,
        authorId: f.authorId,
        unassigned: f.unassigned ? '1' : '',
        from: f.from ? new Date(`${f.from}T00:00:00`).getTime() : '',
        to: f.to ? new Date(`${f.to}T23:59:59.999`).getTime() : '',
        page: f.page,
        size: f.size,
      });
      if (disposed) return;
      admin.data = data;
      paintAdminList();
    } catch (error) {
      host.classList.remove('fb-list-loading');
      host.innerHTML = `<div class="fb-empty"><h3>反馈列表加载失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintAdminList() {
    const host = view.querySelector('[data-admin-list]');
    if (!host || !admin.data) return;
    const { records, total, page, size } = admin.data;
    if (!records.length) {
      host.classList.remove('fb-list-loading');
      host.innerHTML = `<div class="fb-empty"><h3>没有符合筛选条件的反馈</h3><p>换个关键词或清空筛选后再试。</p></div>`;
      animateElement(host, 'fb-list-enter');
      return;
    }
    host.innerHTML = `
      <div class="fb-table-wrap">
        <table class="fb-table">
          <thead><tr><th>反馈内容</th><th>类型</th><th>分类</th><th>严重程度</th><th>处理状态</th><th>提交人</th><th>处理人</th><th>往来</th><th>最近更新</th><th>操作</th></tr></thead>
          <tbody>${records.map((item) => `
            <tr>
              <td><div class="fb-cell-title"><strong>${esc(item.title)}</strong><small>${esc(item.description)}</small></div></td>
              <td>${esc(item.typeLabel)}</td>
              <td>${item.categoryName ? esc(item.categoryName) : '<span class="fb-cell-sub">未分类</span>'}</td>
              <td>${severityBadge(item)}</td>
              <td>${statusBadge(item)}</td>
              <td>${esc(item.authorName || item.authorId)}</td>
              <td>${assigneeText(item)}</td>
              <td>${item.commentCount ? `${item.commentCount} 条` : '<span class="fb-cell-sub">暂无往来记录</span>'}</td>
              <td>${esc(item.updatedAtLabel)}</td>
              <td><div class="fb-row-actions">
                <button class="btn btn-ghost btn-sm" data-admin-open="${esc(item.id)}">处理</button>
                ${item.status !== 'archived' ? `<button class="btn btn-ghost btn-sm" data-admin-archive="${esc(item.id)}">归档</button>` : ''}
                <button class="btn btn-ghost btn-sm" data-admin-delete="${esc(item.id)}">删除</button>
              </div></td>
            </tr>`).join('')}
          </tbody>
        </table>
      </div>
      ${pagerHtml(page, size, total, 'admin')}
    `;
    host.classList.remove('fb-list-loading');
    animateElement(host, 'fb-list-enter');
    host.querySelectorAll('[data-admin-open]').forEach((button) => {
      button.addEventListener('click', () => openAdminDetail(button.dataset.adminOpen));
    });
    host.querySelectorAll('[data-admin-archive]').forEach((button) => {
      button.addEventListener('click', () => archiveAdmin(button.dataset.adminArchive));
    });
    host.querySelectorAll('[data-admin-delete]').forEach((button) => {
      button.addEventListener('click', () => deleteAdmin(button.dataset.adminDelete));
    });
    host.querySelectorAll('[data-pager="admin"]').forEach((button) => {
      button.addEventListener('click', () => {
        admin.filters.page = Number(button.dataset.page) || 1;
        loadAdminList({ keep: true });
      });
    });
  }

  async function archiveAdmin(id) {
    const ok = await confirm({ title: '归档反馈', message: '归档后反馈将结束处理流程，确认归档？', confirmText: '归档', danger: true });
    if (!ok) return;
    try {
      await api.post(`${BASE}/admin/archive?id=${encodeURIComponent(id)}`);
      toast('反馈已归档', 'success');
      if (admin.mode === 'detail') openAdminDetail(id);
      else { loadAdminStats(); loadAdminList({ keep: true }); }
    } catch (error) {
      toast(error.message || '归档失败', 'error');
    }
  }

  async function deleteAdmin(id) {
    const ok = await confirm({ title: '删除反馈', message: '删除后反馈与往来记录将无法恢复，确认删除？', confirmText: '删除', danger: true });
    if (!ok) return;
    try {
      await api.del(`${BASE}/admin/feedback?id=${encodeURIComponent(id)}`);
      toast('反馈已删除', 'success');
      if (admin.mode === 'detail') backToAdminList();
      else { loadAdminStats(); loadAdminList({ keep: true }); }
    } catch (error) {
      toast(error.message || '删除失败', 'error');
    }
  }

  function backToAdminList() {
    return runViewTransition(() => {
      admin.mode = 'list';
      admin.id = null;
      admin.detail = null;
      return renderAdmin();
    });
  }

  async function openAdminDetail(id) {
    try {
      const data = await api.get(`${BASE}/admin/feedback`, { id });
      if (disposed) return;
      admin.mode = 'detail';
      admin.id = id;
      admin.detail = data;
      await runViewTransition(() => paintAdminDetail());
    } catch (error) {
      admin.mode = 'detail';
      admin.id = id;
      await runViewTransition(() => {
        view.innerHTML = `<div class="fb-empty"><h3>反馈加载失败</h3><p>${esc(error.message || '未知错误')}</p><button class="btn btn-ghost" data-back>返回反馈管理</button></div>`;
        view.querySelector('[data-back]')?.addEventListener('click', backToAdminList);
      });
    }
  }

  function paintAdminDetail() {
    const item = admin.detail;
    if (!item) return;
    view.innerHTML = `
      <div class="fb-toolbar">
        <div>
          <div class="fb-chips">${statusBadge(item)}${severityBadge(item)}<span class="fb-badge">${esc(item.typeLabel)}</span>${item.categoryName ? `<span class="fb-badge">分类：${esc(item.categoryName)}</span>` : ''}</div>
          <h2 style="font-family:var(--font-display);font-size:26px;color:var(--navy);margin-top:8px">${esc(item.title)}</h2>
          <p class="fb-note">提交人：${esc(item.authorName || item.authorId)} · 提交于 ${esc(item.createdAtLabel)} · 最近更新 ${esc(item.updatedAtLabel)}</p>
        </div>
        <div class="fb-toolbar-right">
          <button class="btn btn-ghost btn-sm" data-back>返回反馈管理</button>
          ${item.status !== 'archived' ? '<button class="btn btn-ghost btn-sm" data-admin-archive>归档</button>' : ''}
          <button class="btn btn-ghost btn-sm" data-admin-delete>删除</button>
        </div>
      </div>
      <div class="fb-layout">
        <div>
          <div class="fb-panel">
            <div class="fb-meta">
              <div>处理人：<strong>${(item.assignees || []).map((person) => esc(person.name || person.id)).join('、') || '尚未指派'}</strong></div>
              ${item.closedAtLabel ? `<div>${item.status === 'archived' ? '归档于' : '结论于'}：<strong>${esc(item.closedAtLabel)}</strong></div>` : ''}
              ${item.autoArchiveAtLabel ? `<div>自动归档：<strong>${esc(item.autoArchiveAtLabel)}</strong></div>` : ''}
            </div>
            <div class="fb-desc">${esc(item.description)}</div>
            ${attachGallery(item.attachments)}
          </div>
          <div class="fb-panel">
            <div class="fb-panel-head"><div><h2>处理记录</h2><p>同一次操作的系统留痕与内容合并展示在同一时间线上。</p></div></div>
            ${timelineHtml(item)}
          </div>
        </div>
        <div class="fb-side">
          <div class="fb-panel">
            <h3>官方回复</h3>
            <p class="hint">提交人可见；会出现在提交人的详情页里。</p>
            <textarea data-reply maxlength="${MAX_COMMENT}" placeholder="说明结论或下一步安排…"></textarea>
            <div class="fb-form-actions"><button class="btn btn-primary btn-sm" data-reply-send>发送回复</button></div>
          </div>
          <div class="fb-panel">
            <h3>内部备注</h3>
            <p class="hint">仅管理员可见，不会发给提交人。</p>
            <textarea data-note maxlength="${MAX_COMMENT}" placeholder="记录排查过程、临时结论或需要同事知悉的信息…"></textarea>
            <div class="fb-form-actions"><button class="btn btn-ghost btn-sm" data-note-send>记录备注</button></div>
          </div>
          <div class="fb-panel">
            <h3>状态流转</h3>
            <select data-status>${optionHtml(STATUS_OPTIONS, item.status)}</select>
            <input data-status-note placeholder="结论说明（可留空），会记入处理留痕" style="margin-top:8px">
            <div class="fb-form-actions"><button class="btn btn-primary btn-sm" data-status-send>提交状态变更</button></div>
          </div>
          <div class="fb-panel">
            <h3>处理人</h3>
            <div class="fb-chips" data-assignee-chips>${(item.assignees || []).map((person) => `<span class="fb-chip">${esc(person.name || person.id)}</span>`).join('') || '<span class="fb-note">尚未指派</span>'}</div>
            <div class="fb-form-actions"><button class="btn btn-ghost btn-sm" data-assign>指派处理人（可多选）</button></div>
          </div>
          <div class="fb-panel">
            <h3>严重程度</h3>
            <select data-severity>${optionHtml(SEVERITY_OPTIONS, item.severity)}</select>
            <div class="fb-form-actions"><button class="btn btn-ghost btn-sm" data-severity-send>保存严重程度</button></div>
          </div>
        </div>
      </div>
    `;
    bindAdminDetail(item);
  }

  function bindAdminDetail(item) {
    view.querySelector('[data-back]')?.addEventListener('click', backToAdminList);
    view.querySelector('[data-admin-archive]')?.addEventListener('click', () => archiveAdmin(item.id));
    view.querySelector('[data-admin-delete]')?.addEventListener('click', () => deleteAdmin(item.id));

    view.querySelector('[data-reply-send]')?.addEventListener('click', async () => {
      const content = view.querySelector('[data-reply]').value.trim();
      if (!content) {
        toast('请填写回复内容', 'error');
        return;
      }
      try {
        await api.post(`${BASE}/admin/reply?id=${encodeURIComponent(item.id)}`, { content });
        toast('回复已发送', 'success');
        openAdminDetail(item.id);
      } catch (error) {
        toast(error.message || '回复失败', 'error');
      }
    });

    view.querySelector('[data-note-send]')?.addEventListener('click', async () => {
      const content = view.querySelector('[data-note]').value.trim();
      if (!content) {
        toast('请填写备注内容', 'error');
        return;
      }
      try {
        await api.post(`${BASE}/admin/note?id=${encodeURIComponent(item.id)}`, { content });
        toast('备注已记录', 'success');
        openAdminDetail(item.id);
      } catch (error) {
        toast(error.message || '备注失败', 'error');
      }
    });

    view.querySelector('[data-status-send]')?.addEventListener('click', async () => {
      const status = view.querySelector('[data-status]').value;
      const note = view.querySelector('[data-status-note]').value;
      if (!status) {
        toast('请先选择要流转到的状态', 'error');
        return;
      }
      try {
        await api.put(`${BASE}/admin/status?id=${encodeURIComponent(item.id)}`, { status, note });
        toast('状态已更新', 'success');
        if (admin.mode === 'detail') openAdminDetail(item.id);
        else loadAdminList({ keep: true });
      } catch (error) {
        toast(error.message || '状态流转失败', 'error');
      }
    });

    view.querySelector('[data-severity-send]')?.addEventListener('click', async () => {
      const severity = view.querySelector('[data-severity]').value;
      try {
        await api.put(`${BASE}/admin/severity?id=${encodeURIComponent(item.id)}`, { severity });
        toast('严重程度已调整', 'success');
        openAdminDetail(item.id);
      } catch (error) {
        toast(error.message || '调整失败', 'error');
      }
    });

    view.querySelector('[data-assign]')?.addEventListener('click', () => {
      pickUsers({
        title: '指派处理人（可多选）',
        initial: item.assignees || [],
        onConfirm: async (people) => {
          try {
            await api.put(`${BASE}/admin/assignee?id=${encodeURIComponent(item.id)}`, { assignees: people });
            toast('处理人已更新', 'success');
            openAdminDetail(item.id);
          } catch (error) {
            toast(error.message || '处理人更新失败', 'error');
          }
        },
      });
    });
  }

  // ---------------------------------------------------------------- 分类管理

  function renderCategoryAdmin() {
    view.innerHTML = `
      <div class="fb-panel">
        <div class="fb-panel-head">
          <div>
            <h2>全部分类</h2>
            <p>分类会出现在用户提交表单中供用户选择；停用或删除后不再可选，历史反馈仍保留原分类名称。</p>
          </div>
          <button class="btn btn-primary btn-sm" data-category-create>${icon('plus')} 新增分类</button>
        </div>
        <div data-category-list><div class="plugin-loading">正在读取分类…</div></div>
      </div>
    `;
    view.querySelector('[data-category-create]')?.addEventListener('click', createCategory);
    return loadCategoryAdmin();
  }

  const assigneePickerBody = `
    <div class="fb-field is-wide"><span>默认处理人（可多选）</span>
      <div class="fb-edit-assignee">
        <span data-assignee-label></span>
        <button type="button" class="btn btn-ghost btn-sm" data-assignee-pick>选择成员</button>
        <button type="button" class="btn btn-ghost btn-sm" data-assignee-clear>清除</button>
      </div>
      <small>用户提交该分类的反馈时，会自动指派给这些成员；不影响已有反馈。</small>
    </div>
  `;

  /** 在弹窗里绑定可多选的默认处理人，返回可读取当前选择的 getter。 */
  function bindAssigneePicker(modal, initial) {
    let people = (initial || [])
      .map((person) => ({ id: String(person.id), name: person.name || String(person.id) }))
      .filter((person) => person.id);
    const label = modal.bodyEl.querySelector('[data-assignee-label]');
    const paint = () => {
      label.textContent = people.length ? people.map((person) => person.name).join('、') : '未设置';
    };
    paint();
    modal.bodyEl.querySelector('[data-assignee-pick]')?.addEventListener('click', () => {
      pickUsers({
        title: '选择默认处理人（可多选）',
        initial: people,
        onConfirm: (selected) => {
          people = selected.map((person) => ({ id: person.id, name: person.name || person.id }));
          paint();
        },
      });
    });
    modal.bodyEl.querySelector('[data-assignee-clear]')?.addEventListener('click', () => {
      people = [];
      paint();
    });
    return { get: () => people };
  }

  function createCategory() {
    const modal = openModal({
      title: '新增分类',
      body: `<div class="fb-form">
        <label class="fb-field is-wide"><span>分类名称</span>
          <input data-new-name maxlength="30" placeholder="例如「账号问题」">
        </label>
        ${assigneePickerBody}
      </div>`,
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: '保存',
          className: 'btn-primary',
          onClick: async () => {
            const input = modal.bodyEl.querySelector('[data-new-name]');
            const name = input?.value.trim() || '';
            if (!name) {
              toast('分类名称不能为空', 'error');
              input?.focus();
              return false;
            }
            try {
              await api.post(`${BASE}/admin/category/save`, {
                name,
                defaultAssignees: assignees.get(),
              });
              toast('分类已新增', 'success');
              admin.categories = null;
              loadCategoryAdmin({ keep: true });
              return true;
            } catch (error) {
              toast(error.message || '新增分类失败', 'error');
              return false;
            }
          },
        },
      ],
    });
    const assignees = bindAssigneePicker(modal, null);
    setTimeout(() => modal.bodyEl.querySelector('[data-new-name]')?.focus(), 40);
  }

  async function loadCategoryAdmin(options = {}) {
    const host = view.querySelector('[data-category-list]');
    if (!host) return;
    if (options.keep || host.querySelector('.fb-table-wrap')) host.classList.add('fb-list-loading');
    try {
      const data = await api.get(`${BASE}/admin/categories`);
      if (disposed || tab !== 'categories') return;
      categoryAdmin.data = data;
      paintCategoryList();
    } catch (error) {
      host.classList.remove('fb-list-loading');
      host.innerHTML = `<div class="fb-empty"><h3>分类加载失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintCategoryList() {
    const host = view.querySelector('[data-category-list]');
    if (!host) return;
    const records = categoryAdmin.data?.records || [];
    host.classList.remove('fb-list-loading');
    if (!records.length) {
      host.innerHTML = '<div class="fb-empty"><h3>还没有分类</h3><p>点击右上角「新增分类」创建第一个分类。</p></div>';
      animateElement(host, 'fb-list-enter');
      return;
    }
    host.innerHTML = `
      <div class="fb-table-wrap">
        <table class="fb-table fb-category-table">
          <thead><tr><th>分类名称</th><th>状态</th><th>默认处理人</th><th>使用中</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>${records.map((category) => `
            <tr>
              <td><span class="fb-category-name">${esc(category.name)}</span></td>
              <td>${category.enabled ? '<span class="fb-badge is-resolved">启用</span>' : '<span class="fb-badge is-archived">停用</span>'}</td>
              <td>${category.defaultAssigneeName ? esc(category.defaultAssigneeName) : '<span class="fb-cell-sub">未设置</span>'}</td>
              <td>${category.count} 条</td>
              <td>${esc(category.createdAtLabel || '—')}</td>
              <td><div class="fb-row-actions">
                <button class="btn btn-ghost btn-sm" data-category-edit="${esc(category.id)}">编辑</button>
                <button class="btn btn-ghost btn-sm" data-category-toggle="${esc(category.id)}">${category.enabled ? '停用' : '启用'}</button>
                <button class="btn btn-ghost btn-sm" data-category-delete="${esc(category.id)}">删除</button>
              </div></td>
            </tr>`).join('')}
          </tbody>
        </table>
      </div>
    `;
    animateElement(host, 'fb-list-enter');
    const find = (id) => records.find((category) => category.id === id);
    host.querySelectorAll('[data-category-edit]').forEach((button) => {
      button.addEventListener('click', () => editCategory(find(button.dataset.categoryEdit)));
    });
    host.querySelectorAll('[data-category-toggle]').forEach((button) => {
      button.addEventListener('click', () => toggleCategory(find(button.dataset.categoryToggle)));
    });
    host.querySelectorAll('[data-category-delete]').forEach((button) => {
      button.addEventListener('click', () => deleteCategory(find(button.dataset.categoryDelete)));
    });
  }

  function afterCategoryChange(message) {
    toast(message, 'success');
    admin.categories = null;
    loadCategoryAdmin({ keep: true });
  }

  function editCategory(category) {
    if (!category) return;
    const initialAssignees = category.defaultAssignees?.length
      ? category.defaultAssignees
      : category.defaultAssigneeId
        ? [{ id: category.defaultAssigneeId, name: category.defaultAssigneeName || category.defaultAssigneeId }]
        : [];
    const modal = openModal({
      title: '编辑分类',
      body: `
        <div class="fb-form">
          <label class="fb-field is-wide"><span>分类名称</span>
            <input data-edit-name maxlength="30" value="${esc(category.name)}">
          </label>
          ${assigneePickerBody}
        </div>
      `,
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: '保存',
          className: 'btn-primary',
          onClick: async () => {
            const input = modal.bodyEl.querySelector('[data-edit-name]');
            const name = input?.value.trim() || '';
            if (!name) {
              toast('分类名称不能为空', 'error');
              input?.focus();
              return false;
            }
            try {
              await api.post(`${BASE}/admin/category/save`, {
                id: category.id,
                name,
                enabled: category.enabled,
                defaultAssignees: assignees.get(),
              });
              afterCategoryChange('分类已更新');
              return true;
            } catch (error) {
              toast(error.message || '保存失败', 'error');
              return false;
            }
          },
        },
      ],
    });
    const assignees = bindAssigneePicker(modal, initialAssignees);
    setTimeout(() => {
      const input = modal.bodyEl.querySelector('[data-edit-name]');
      input?.focus();
      input?.select();
    }, 40);
  }

  async function toggleCategory(category) {
    if (!category) return;
    try {
      await api.post(`${BASE}/admin/category/save`, {
        id: category.id,
        name: category.name,
        enabled: !category.enabled,
      });
      afterCategoryChange(category.enabled ? '分类已停用' : '分类已启用');
    } catch (error) {
      toast(error.message || '操作失败', 'error');
    }
  }

  async function deleteCategory(category) {
    if (!category) return;
    const ok = await confirm({
      title: '删除分类',
      message: category.count
        ? `「${category.name}」已被 ${category.count} 条反馈使用。删除后这些反馈仍保留原分类名称，但不再能按该分类筛选。确认删除？`
        : `确认删除分类「${category.name}」？`,
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.post(`${BASE}/admin/category/delete`, { id: category.id });
      afterCategoryChange('分类已删除');
    } catch (error) {
      toast(error.message || '删除失败', 'error');
    }
  }

  // ---------------------------------------------------------------- 成员选择器

  function pickSingleUser(title, done) {
    pickUsers({ title, multiple: false, initial: [], onConfirm: (people) => done(people[0]) });
  }

  function pickUsers({ title, multiple = true, initial = [], onConfirm }) {
    const selected = new Map((initial || []).map((person) => [person.id, { id: person.id, name: person.name || person.id }]));
    const modal = openModal({
      title,
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: '完成',
          className: 'btn-primary',
          onClick: () => {
            if (!selected.size) {
              toast('请先选择成员', 'info');
              return false;
            }
            onConfirm([...selected.values()]);
          },
        },
      ],
      body: `
        <div class="fb-picker-tools">
          <label class="search-box"><input type="search" data-user-search placeholder="搜索姓名、用户名或邮箱"></label>
          <button class="btn btn-ghost btn-sm" data-user-search-go>搜索</button>
        </div>
        <div class="fb-user-list" data-user-list><div class="fb-empty-sm">正在加载成员…</div></div>
        <div class="fb-pager" data-user-pager></div>
        <div class="fb-picker-manual">
          <input data-manual placeholder="无法加载成员目录时，可手动输入用户名">
          <button class="btn btn-ghost btn-sm" data-manual-add>添加</button>
        </div>
        <div class="fb-note">处理人候选来自宿主账号目录；请确认所选成员可以访问反馈管理页。</div>
        <div class="fb-selected" data-selected></div>
      `,
    });
    const listHost = modal.bodyEl.querySelector('[data-user-list]');
    const pagerHost = modal.bodyEl.querySelector('[data-user-pager]');
    const selectedHost = modal.bodyEl.querySelector('[data-selected]');
    const search = modal.bodyEl.querySelector('[data-user-search]');
    let keyword = '';
    let page = 1;

    const paintSelected = () => {
      selectedHost.innerHTML = [...selected.values()]
        .map((person) => `<span class="fb-chip">${esc(person.name || person.id)}<button type="button" data-unpick="${esc(person.id)}" aria-label="移除" style="border:0;background:transparent;cursor:pointer">×</button></span>`)
        .join('');
      selectedHost.querySelectorAll('[data-unpick]').forEach((button) => {
        button.addEventListener('click', () => {
          selected.delete(button.dataset.unpick);
          paintSelected();
          paintList();
        });
      });
    };

    let rows = [];
    const paintList = () => {
      if (!rows.length) {
        listHost.innerHTML = '<div class="fb-empty-sm">没有匹配的成员，换个关键词试试</div>';
        return;
      }
      listHost.innerHTML = rows.map((person) => {
        const checked = selected.has(String(person.id));
        const name = person.nickname || person.username || person.id;
        const avatar = person.avatar
          ? `<span class="fb-user-avatar"><img src="${esc(person.avatar)}" alt=""></span>`
          : `<span class="fb-user-avatar">${esc(String(name).slice(0, 1).toUpperCase())}</span>`;
        return `<label class="fb-user-row">
          <input type="checkbox" data-pick="${esc(person.id)}" ${checked ? 'checked' : ''}>
          ${avatar}
          <div><strong>${esc(name)}</strong><small>@${esc(person.username || person.id)}${person.email ? ` · ${esc(person.email)}` : ''}</small></div>
        </label>`;
      }).join('');
      listHost.querySelectorAll('[data-pick]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => {
          const person = rows.find((row) => String(row.id) === checkbox.dataset.pick);
          if (!person) return;
          const id = String(person.id);
          if (checkbox.checked) {
            if (!multiple) selected.clear();
            selected.set(id, { id, name: person.nickname || person.username || id });
          } else {
            selected.delete(id);
          }
          if (!multiple) {
            paintList();
          }
          paintSelected();
        });
      });
    };

    const load = async () => {
      listHost.innerHTML = '<div class="fb-empty-sm">正在加载成员…</div>';
      try {
        const data = await api.get('/admin/users', { keyword, page, size: 20 });
        rows = data.items || [];
        const pages = data.pages || 1;
        if (page > pages) page = pages;
        paintList();
        pagerHost.innerHTML = pages > 1
          ? `<button class="btn btn-ghost btn-sm" data-user-page="${page - 1}" ${page <= 1 ? 'disabled' : ''}>上一页</button><span>第 ${page} / ${pages} 页</span><button class="btn btn-ghost btn-sm" data-user-page="${page + 1}" ${page >= pages ? 'disabled' : ''}>下一页</button>`
          : `<span>共 ${data.total || 0} 位成员</span>`;
        pagerHost.querySelectorAll('[data-user-page]').forEach((button) => {
          button.addEventListener('click', () => {
            page = Number(button.dataset.userPage) || 1;
            load();
          });
        });
      } catch (error) {
        listHost.innerHTML = `<div class="fb-empty-sm">当前无法加载成员目录：${esc(error.message || '未知错误')}<br>可在下方手动输入用户名添加。</div>`;
        pagerHost.innerHTML = '';
      }
    };

    search?.addEventListener('input', () => {
      clearTimeout(search._timer);
      search._timer = setTimeout(() => {
        keyword = search.value.trim();
        page = 1;
        load();
      }, 300);
    });
    modal.bodyEl.querySelector('[data-user-search-go]')?.addEventListener('click', () => {
      keyword = search.value.trim();
      page = 1;
      load();
    });
    modal.bodyEl.querySelector('[data-manual-add]')?.addEventListener('click', () => {
      const input = modal.bodyEl.querySelector('[data-manual]');
      const name = input.value.trim();
      if (!name) {
        toast('请输入用户名', 'info');
        return;
      }
      if (!multiple) selected.clear();
      selected.set(name, { id: name, name });
      input.value = '';
      paintSelected();
      paintList();
    });

    paintSelected();
    load();
  }

  // ---------------------------------------------------------------- 弹窗

  function openModal({ title, body, actions = [] }) {
    const overlay = document.createElement('div');
    overlay.className = 'fb-overlay';
    overlay.innerHTML = `
      <div class="fb-dialog" role="dialog" aria-modal="true" aria-label="${esc(title)}">
        <div class="fb-dialog-head"><h2>${esc(title)}</h2><button class="btn btn-ghost btn-sm" data-close>关闭</button></div>
        <div class="fb-dialog-body">${body}</div>
        ${actions.length ? '<div class="fb-dialog-actions"></div>' : ''}
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
    const footer = overlay.querySelector('.fb-dialog-actions');
    for (const action of actions) {
      const button = document.createElement('button');
      button.className = `btn ${action.className || 'btn-ghost'}`;
      button.textContent = action.label;
      button.addEventListener('click', async () => {
        const result = await action.onClick?.(close);
        if (result !== false) close();
      });
      footer?.append(button);
    }
    return { overlay, close, bodyEl: overlay.querySelector('.fb-dialog-body') };
  }

  render();

  return () => {
    disposed = true;
    closeRangePicker();
    document.querySelectorAll('.fb-overlay').forEach((overlay) => overlay.remove());
  };
}
