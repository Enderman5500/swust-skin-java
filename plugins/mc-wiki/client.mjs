const KIND_LABELS = { item: '物品', block: '方块', entity: '生物', misc: '其他' };

const RECIPE_LABELS = {
  'minecraft:crafting_shaped': '有序合成',
  'minecraft:crafting_shapeless': '无序合成',
  'minecraft:smelting': '熔炼',
  'minecraft:blasting': '高炉',
  'minecraft:smoking': '烟熏',
  'minecraft:campfire_cooking': '营火烹饪',
  'minecraft:stonecutting': '切石',
  'minecraft:smithing_transform': '锻造',
  'minecraft:smithing_trim': '锻造纹饰',
  'minecraft:crafting_special_armordye': '盔甲染色',
  'minecraft:crafting_special_bannerduplicate': '旗帜复制',
  'minecraft:crafting_special_bookcloning': '成书复制',
  'minecraft:crafting_special_firework_rocket': '烟花火箭',
  'minecraft:crafting_special_firework_star': '烟花之星',
  'minecraft:crafting_special_mapcloning': '地图复制',
  'minecraft:crafting_special_mapextending': '地图扩展',
  'minecraft:crafting_special_repairitem': '物品修复',
  'minecraft:crafting_special_shielddecoration': '盾牌纹饰',
  'minecraft:crafting_special_shulkerboxcoloring': '潜影盒染色',
  'minecraft:crafting_special_tippedarrow': '药箭',
  'minecraft:crafting_special_suspiciousstew': '可疑的炖汤',
  'minecraft:crafting_decorated_pot': '饰纹陶罐',
};

const STYLE = `
.mcw { --mcw-line: var(--line); }
.mcw-tabs { display: inline-flex; gap: 6px; padding: 4px; border-radius: 999px; background: var(--paper); border: 1px solid var(--line); }
.mcw-tabs .chip { border: 0; background: transparent; }
.mcw-tabs .chip.is-active { background: var(--navy); color: #fff; }
.mcw-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 22px; }
.mcw-toolbar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.mcw-toolbar .select-box select { min-width: 108px; }
.mcw-toolbar .search-box { min-width: 220px; max-width: 320px; }
.mcw-toolbar .search-box input { min-width: 0; }
.mcw-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(168px, 1fr)); gap: 12px; }
.mcw-card { position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 4px; padding: 16px; border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.86); text-align: left; transition: transform .18s var(--ease), box-shadow .18s var(--ease); }
.mcw-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-sm); border-color: var(--line-strong); }
.mcw-card strong { font-size: 13px; color: var(--navy); }
.mcw-card small { font-size: 10.5px; color: var(--muted-2, var(--muted)); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.mcw-card-badge { position: absolute; top: 12px; right: 12px; padding: 2px 8px; border-radius: 999px; border: 1px solid rgba(44,76,102,.14); background: rgba(220,232,243,.55); color: var(--navy); font-size: 9.5px; letter-spacing: .04em; }
.mcw-card-icon { margin-bottom: 4px; }
.mcw-icon-wrap { position: relative; display: inline-flex; align-items: center; justify-content: center; width: 48px; height: 48px; }
.mcw-icon-wrap img { max-width: 100%; max-height: 100%; image-rendering: pixelated; }
.mcw-icon-wrap.is-empty::after { content: attr(data-initial); display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; border-radius: 10px; background: linear-gradient(140deg, var(--ice-soft), #fff); color: var(--navy); font-family: var(--font-display); font-size: 20px; }
.mcw-list { display: flex; flex-direction: column; gap: 12px; }
.mcw-recipe-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.mcw-recipe { display: grid; grid-template-columns: auto 28px auto; align-items: center; gap: 14px; padding: 14px 16px; border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.9); }
.mcw-recipe-grid .mcw-recipe { grid-template-columns: auto 22px minmax(0, 1fr); gap: 12px; }
.mcw-recipe-grid .mcw-result { min-width: 0; }
.mcw-recipe footer { grid-column: 1 / -1; display: flex; justify-content: space-between; gap: 12px; padding-top: 8px; border-top: 1px dashed var(--line); font-size: 10.5px; color: var(--muted); }
.mcw-recipe footer code { font-family: var(--font-mono); font-size: 10px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mcw-grid3 { display: grid; grid-template-columns: repeat(3, 38px); grid-template-rows: repeat(3, 38px); gap: 4px; padding: 8px; border-radius: 12px; background: #fff; border: 1px solid var(--line-strong); }
.mcw-cell { display: flex; align-items: center; justify-content: center; border-radius: 7px; background: var(--paper); border: 1px solid rgba(19,43,64,.09); position: relative; }
.mcw-cell.is-empty { background: transparent; border: 1px dashed rgba(19,43,64,.2); }
.mcw-cell img { max-width: 30px; max-height: 30px; image-rendering: pixelated; }
.mcw-cell .mcw-icon-wrap { width: 30px; height: 30px; }
.mcw-cell .mcw-icon-wrap.is-empty::after { border-radius: 6px; font-size: 12px; background: transparent; }
.mcw-arrow { font-size: 18px; color: var(--muted-2, var(--muted)); text-align: center; }
.mcw-result { display: flex; align-items: center; gap: 10px; min-width: 150px; }
.mcw-result .mcw-icon-wrap { width: 42px; height: 42px; }
.mcw-result strong { display: block; font-size: 13.5px; color: var(--navy); }
.mcw-result small { font-size: 10.5px; color: var(--muted); }
.mcw-empty { padding: 64px 24px; text-align: center; border-radius: var(--radius); border: 1px dashed var(--line-strong); background: rgba(255,255,255,.6); color: var(--muted); }
.mcw-empty h3 { font-family: var(--font-display); font-size: 22px; color: var(--navy); margin-bottom: 8px; }
.mcw-empty p { font-size: 12.5px; max-width: 520px; margin: 0 auto 16px; line-height: 1.8; }
.mcw-empty .mcw-actions { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
.mcw-pager { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 18px; font-size: 12px; color: var(--muted); }
.mcw-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.8); overflow: hidden; }
.mcw-stat { padding: 16px 18px; border-right: 1px solid var(--line); display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.mcw-stat:last-child { border-right: 0; }
.mcw-stat span { font-size: 10px; letter-spacing: .1em; text-transform: uppercase; color: var(--muted-2, var(--muted)); }
.mcw-stat strong { font-family: var(--font-display); font-size: 22px; color: var(--navy); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mcw-stat small { font-size: 10px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mcw-panel { padding: 20px; border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.8); }
.mcw-panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; flex-wrap: wrap; margin-bottom: 14px; }
.mcw-panel-head h2 { font-family: var(--font-display); font-size: 22px; color: var(--navy); }
.mcw-panel-head p { font-size: 11.5px; color: var(--muted); margin-top: 3px; }
.mcw-table-wrap { overflow: auto; border-radius: 12px; border: 1px solid var(--line); }
.mcw-table { width: 100%; border-collapse: collapse; font-size: 12px; min-width: 780px; }
.mcw-table th { text-align: left; padding: 10px 12px; background: var(--paper); color: var(--muted); font-size: 10px; letter-spacing: .08em; text-transform: uppercase; white-space: nowrap; }
.mcw-table td { padding: 10px 12px; border-top: 1px solid var(--line); vertical-align: middle; }
.mcw-table tr:hover td { background: rgba(156,195,230,.07); }
.mcw-table code { font-family: var(--font-mono); font-size: 11px; }
.mcw-badge { display: inline-flex; align-items: center; gap: 5px; padding: 2px 8px; border-radius: 999px; font-size: 10px; border: 1px solid var(--line); color: var(--muted); background: #fff; white-space: nowrap; }
.mcw-badge.is-published { color: var(--success); border-color: rgba(60,122,91,.3); background: rgba(60,122,91,.08); }
.mcw-badge.is-imported { color: var(--navy); border-color: rgba(44,76,102,.25); background: rgba(44,76,102,.06); }
.mcw-badge.is-current { color: var(--gold-deep, #cfaa55); border-color: rgba(207,170,85,.4); background: rgba(232,201,122,.14); }
.mcw-row-actions { display: flex; gap: 6px; flex-wrap: wrap; }
.mcw-row-actions .btn { height: 28px; padding: 0 10px; font-size: 11px; }
.mcw-job { border: 1px solid rgba(44,76,102,.2); background: rgba(220,232,243,.35); }
.mcw-job-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.mcw-job-head strong { font-size: 13px; color: var(--navy); }
.mcw-progress { height: 8px; border-radius: 999px; background: rgba(44,76,102,.12); overflow: hidden; margin: 12px 0 8px; }
.mcw-progress i { display: block; height: 100%; width: 0; border-radius: 999px; background: linear-gradient(90deg, var(--navy), var(--ice)); transition: width .3s var(--ease); }
.mcw-job-meta { display: flex; justify-content: space-between; gap: 10px; font-size: 11px; color: var(--muted); flex-wrap: wrap; }
.mcw-log { margin-top: 12px; max-height: 220px; overflow: auto; padding: 12px; border-radius: 10px; background: #10202e; color: #cfe0ee; font-family: var(--font-mono); font-size: 10.5px; line-height: 1.7; white-space: pre-wrap; word-break: break-all; }
.mcw-jobdetail { display: flex; flex-direction: column; min-height: 560px; }
.mcw-jobdetail .mcw-log { flex: 1; max-height: none; min-height: 340px; }
.mcw-hover-card { position: fixed; z-index: 200; min-width: 168px; max-width: 260px; display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-radius: 12px; border: 1px solid var(--line-strong); background: #fff; box-shadow: var(--shadow-md); pointer-events: none; }
.mcw-hover-card[hidden] { display: none; }
.mcw-hover-card .mcw-icon-wrap { flex: 0 0 auto; width: 44px; height: 44px; }
.mcw-hover-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.mcw-hover-body strong { font-size: 12.5px; color: var(--navy); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mcw-hover-body small { font-size: 10.5px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mcw-dot { display: inline-block; width: 7px; height: 7px; margin-left: 6px; border-radius: 50%; background: var(--gold-deep, #cfaa55); box-shadow: 0 0 0 2px rgba(232, 201, 122, 0.22); vertical-align: middle; }
.mcw-log .is-warn { color: #f0cf8a; }
.mcw-log .is-error { color: #f1a3a3; }
.mcw-jobs-list { display: flex; flex-direction: column; gap: 6px; }
.mcw-job-layout { display: grid; grid-template-columns: minmax(280px, 360px) minmax(0, 1fr); gap: 16px; align-items: start; }
.mcw-joblist { min-height: 460px; }
.mcw-jobs-list { max-height: 520px; overflow: auto; padding-right: 4px; }
.mcw-job-item { display: flex; align-items: center; justify-content: space-between; gap: 10px; width: 100%; padding: 10px 12px; border-radius: 10px; border: 1px solid var(--line); background: #fff; text-align: left; cursor: pointer; transition: border-color .15s, background .15s; }
.mcw-job-item:hover { border-color: var(--line-strong); }
.mcw-job-item.is-active { border-color: rgba(44, 76, 102, 0.4); background: rgba(220, 232, 243, 0.35); }
.mcw-job-item-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.mcw-job-item-main strong { font-size: 12.5px; color: var(--navy); }
.mcw-job-item-main small { font-size: 10.5px; color: var(--muted); }
.mcw-job-head h2 { font-family: var(--font-display); font-size: 21px; color: var(--navy); }
.mcw-job-empty { display: flex; align-items: center; justify-content: center; min-height: 400px; color: var(--muted); font-size: 12.5px; }
.mcw-job-error { color: var(--danger); font-size: 12px; margin: 4px 0 8px; }
.mcw-badge.is-failed { color: var(--danger); border-color: rgba(180, 75, 75, 0.3); background: rgba(180, 75, 75, 0.08); }
.mcw-subrow td { background: rgba(156, 195, 230, 0.06); }
.mcw-subrow td:first-child { padding-left: 44px; }
.mcw-version-cell { white-space: nowrap; }
.mcw-toggle { display: inline-flex; align-items: center; justify-content: center; gap: 3px; width: 32px; height: 20px; margin-right: 5px; padding: 0; border: 1px solid var(--line); border-radius: 6px; background: #fff; color: var(--muted); font-size: 9.5px; cursor: pointer; vertical-align: middle; }
.mcw-toggle:hover { border-color: var(--line-strong); color: var(--navy); }
.mcw-toggle-caret { width: 0; height: 0; border-left: 4px solid currentColor; border-top: 3px solid transparent; border-bottom: 3px solid transparent; transition: transform .15s; }
.mcw-toggle.is-open .mcw-toggle-caret { transform: rotate(90deg); }
.mcw-toggle-space { display: inline-block; width: 37px; }
.mcw-sub-mark { color: var(--muted-2, var(--muted)); margin-right: 5px; }
.mcw-group-row td { background: rgba(244, 246, 249, 0.9); }
.mcw-group-title { font-weight: 700; color: var(--navy); font-size: 12px; }
@media (max-width: 900px) {
  .mcw-job-layout { grid-template-columns: 1fr; }
  .mcw-jobs-list { max-height: 260px; }
  .mcw-recipe-grid { grid-template-columns: 1fr; }
  .mcw-jobdetail { min-height: 420px; }
  .mcw-jobdetail .mcw-log { min-height: 260px; }
}
.mcw-job-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 10px; border-radius: 9px; border: 1px solid var(--line); background: #fff; font-size: 11.5px; }
.mcw-overlay { position: fixed; inset: 0; z-index: 120; display: flex; align-items: center; justify-content: center; padding: 24px; background: rgba(9,21,34,.45); backdrop-filter: blur(3px); }
.mcw-dialog { width: min(720px, 100%); max-height: min(86vh, 900px); overflow: auto; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-lg); }
.mcw-dialog-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; padding: 22px 24px 16px; border-bottom: 1px solid var(--line); position: sticky; top: 0; background: #fff; z-index: 1; }
.mcw-dialog-head h2 { font-family: var(--font-display); font-size: 24px; color: var(--navy); }
.mcw-dialog-head small { font-size: 11px; color: var(--muted); font-family: var(--font-mono); }
.mcw-dialog-body { padding: 20px 24px 26px; display: flex; flex-direction: column; gap: 18px; }
.mcw-detail-hero { display: flex; gap: 18px; align-items: center; }
.mcw-detail-hero .mcw-icon-wrap { width: 88px; height: 88px; }
.mcw-detail-hero .mcw-icon-wrap.is-empty::after { font-size: 34px; border-radius: 16px; }
.mcw-detail-hero h3 { font-family: var(--font-display); font-size: 22px; color: var(--navy); }
.mcw-detail-hero p { font-size: 12px; color: var(--muted); margin-top: 3px; }
.mcw-section-title { display: flex; align-items: center; justify-content: space-between; gap: 10px; font-size: 11px; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); }
.mcw-section-title strong { color: var(--navy); letter-spacing: 0; text-transform: none; font-size: 12px; }
.mcw-detail-recipes .mcw-recipe { padding: 10px 12px; }
.mcw-muted { color: var(--muted); font-size: 11.5px; }
.mcw-tag { font-family: var(--font-mono); font-size: 10px; color: var(--muted); }
@media (max-width: 720px) {
  .mcw-stats { grid-template-columns: repeat(2, 1fr); }
  .mcw-stat:nth-child(2) { border-right: 0; }
  .mcw-stat:nth-child(-n+2) { border-bottom: 1px solid var(--line); }
  .mcw-recipe { grid-template-columns: 1fr; justify-items: center; }
  .mcw-arrow { transform: rotate(90deg); }
  .mcw-toolbar .search-box { width: 100%; max-width: none; }
  .mcw-toolbar-right { width: 100%; }
}
`;

export async function mount(root, ctx) {
  const { api, user, toast, confirm, escapeHtml: esc, formatDate } = ctx;
  const pluginId = ctx.plugin?.id || 'mc-wiki';
  const base = `/plugins/${pluginId}`;
  const assetBase = `/plugin-assets/${pluginId}/`;
  const isAdmin = Boolean(user?.isAdmin);

  let disposed = false;
  let tab =
    ctx.query?.tab === 'admin' && isAdmin ? 'admin' : ctx.query?.tab === 'jobs' && isAdmin ? 'jobs' : 'items';
  let meta = null;
  let version = '';
  let itemView = { keyword: '', kind: '', page: 1, size: 24, loading: false };
  let recipeView = { keyword: '', page: 1, size: 12, loading: false };
  const adminView = { keyword: '', type: '', status: '', page: 1, data: null, loading: false };
  const expandedGroups = new Set();
  const jobView = {
    selectedId: null,
    job: null,
    lastSeq: 0,
    timer: null,
    listTimer: null,
    logs: [],
    jobs: [],
  };
  let searchTimer = null;

  injectStyle();
  root.innerHTML = `
    <section class="plugin-shell mcw">
      <header class="plugin-head">
        <div>
          <p class="eyebrow">PLUGIN · MC WIKI</p>
          <h1>Minecraft 百科</h1>
          <p class="plugin-lead">查阅已发布版本的物品、方块、生物与合成配方。数据由管理员从 Mojang 官方 JAR 导入。</p>
        </div>
        <div class="plugin-head-actions">
          <div class="mcw-tabs" role="tablist">
            <button class="chip is-active" data-tab="items" role="tab">物品图鉴</button>
            <button class="chip" data-tab="recipes" role="tab">合成配方</button>
            ${isAdmin ? '<button class="chip" data-tab="admin" role="tab">版本管理</button>' : ''}
            ${isAdmin ? '<button class="chip" data-tab="jobs" role="tab">任务中心</button>' : ''}
          </div>
        </div>
      </header>
      <div data-view></div>
    </section>
  `;
  const view = root.querySelector('[data-view]');

  if (ctx.plugin?.version) {
    root.querySelector('.plugin-lead').insertAdjacentHTML(
      'beforeend',
      ` <span class="mcw-tag">v${esc(ctx.plugin.version)}</span>`
    );
  }

  function injectStyle() {
    if (document.getElementById('mcw-style')) return;
    const style = document.createElement('style');
    style.id = 'mcw-style';
    style.textContent = STYLE;
    document.head.append(style);
  }

  function kindLabel(kind) {
    return KIND_LABELS[kind] || kind || '物品';
  }

  function recipeLabel(type) {
    const key = String(type || '').includes(':') ? String(type) : `minecraft:${type}`;
    return RECIPE_LABELS[key] || key.replace('minecraft:', '').replace(/_/g, ' ');
  }

  function initialOf(name, id) {
    const text = String(name || id || '?').trim();
    return esc(text.slice(0, 1).toUpperCase());
  }

  function iconHtml(item, extraClass = '') {
    const initial = initialOf(item.nameZh || item.nameEn, item.namespacedId || item.id);
    if (!item.icon) return `<span class="mcw-icon-wrap is-empty ${extraClass}" data-initial="${initial}"></span>`;
    return `<span class="mcw-icon-wrap ${extraClass}" data-initial="${initial}"><img src="${assetBase}${esc(
      item.icon
    )}" alt="" loading="lazy" data-icon /></span>`;
  }

  function detailIconHtml(id, nameZh, icon, extraClass = '') {
    return iconHtml({ namespacedId: id, nameZh, icon }, extraClass);
  }

  function bindIcons(container) {
    container.querySelectorAll('img[data-icon]').forEach((img) => {
      img.addEventListener('error', () => {
        const wrap = img.closest('.mcw-icon-wrap');
        if (wrap) wrap.classList.add('is-empty');
        img.remove();
      });
    });
  }

  let hoverCardEl = null;

  function ensureHoverCard() {
    if (hoverCardEl) return hoverCardEl;
    hoverCardEl = document.createElement('div');
    hoverCardEl.className = 'mcw-hover-card';
    hoverCardEl.hidden = true;
    document.body.append(hoverCardEl);
    return hoverCardEl;
  }

  function showHoverCard(cell, event) {
    const card = ensureHoverCard();
    card.innerHTML = `
      <span class="mcw-icon-wrap ${cell.dataset.hoverIcon ? '' : 'is-empty'}" data-initial="${initialOf(
      cell.dataset.hoverName,
      cell.dataset.hoverId
    )}">${cell.dataset.hoverIcon ? `<img src="${assetBase}${esc(cell.dataset.hoverIcon)}" alt="" />` : ''}</span>
      <span class="mcw-hover-body">
        <strong>${esc(cell.dataset.hoverName || cell.dataset.hoverId)}</strong>
        <small>${esc(cell.dataset.hoverEn || cell.dataset.hoverId)}</small>
      </span>`;
    bindIcons(card);
    card.hidden = false;
    positionHoverCard(event);
  }

  function positionHoverCard(event) {
    const card = hoverCardEl;
    if (!card || card.hidden) return;
    const rect = card.getBoundingClientRect();
    const pad = 14;
    let x = event.clientX + 16;
    let y = event.clientY + 16;
    if (x + rect.width > window.innerWidth - pad) x = event.clientX - rect.width - 16;
    if (y + rect.height > window.innerHeight - pad) y = event.clientY - rect.height - 16;
    card.style.left = `${Math.max(pad, x)}px`;
    card.style.top = `${Math.max(pad, y)}px`;
  }

  function hideHoverCard() {
    if (hoverCardEl) hoverCardEl.hidden = true;
  }

  function bindRecipeHover(container) {
    container.querySelectorAll('[data-hover-id]').forEach((cell) => {
      cell.addEventListener('mouseenter', (event) => showHoverCard(cell, event));
      cell.addEventListener('mousemove', positionHoverCard);
      cell.addEventListener('mouseleave', hideHoverCard);
    });
  }

  function day(value) {
    return value ? formatDate(value) : '—';
  }

  async function loadMeta() {
    meta = await api.get(`${base}/meta`, version ? { version } : undefined);
    if (!version) {
      version = meta.version || meta.versions?.[meta.versions.length - 1] || '';
    }
    return meta;
  }

  function refreshKindChips() {
    const host = view.querySelector('[data-kind-filter]');
    if (host) host.innerHTML = renderKindChips();
  }

  function versionOptions(selected) {
    const versions = meta?.versions || [];
    return versions
      .map((item) => `<option value="${esc(item)}" ${item === selected ? 'selected' : ''}>${esc(item)}</option>`)
      .join('');
  }

  function renderView() {
    if (disposed) return;
    root.querySelectorAll('[data-tab]').forEach((button) => {
      button.classList.toggle('is-active', button.dataset.tab === tab);
    });
    if (tab === 'admin') renderAdmin();
    else if (tab === 'jobs') renderJobs();
    else if (tab === 'recipes') renderRecipes();
    else renderItems();
  }

  // ---------------- 物品图鉴 ----------------

  async function renderItems() {
    view.innerHTML = `
      <div class="mcw-toolbar">
        <div class="filter-chips" data-kind-filter>
          ${renderKindChips()}
        </div>
        <div class="mcw-toolbar-right">
          <label class="select-box"><select data-item-version>${versionOptions(version)}</select></label>
          <label class="search-box"><input type="search" data-item-search placeholder="搜索中文名、英文名或 id" value="${esc(
            itemView.keyword
          )}" /></label>
        </div>
      </div>
      <div data-item-body><div class="plugin-loading">正在读取物品目录…</div></div>
    `;
    await loadItems();
  }

  function renderKindChips() {
    const kinds = meta?.kinds || {};
    const entries = [
      { key: '', label: '全部' },
      { key: 'item', label: `物品${kinds.item ? ` ${kinds.item}` : ''}` },
      { key: 'block', label: `方块${kinds.block ? ` ${kinds.block}` : ''}` },
      { key: 'entity', label: `生物${kinds.entity ? ` ${kinds.entity}` : ''}` },
    ];
    return entries
      .map(
        (entry) =>
          `<button class="chip ${itemView.kind === entry.key ? 'is-active' : ''}" data-kind="${entry.key}">${entry.label}</button>`
      )
      .join('');
  }

  async function loadItems() {
    const body = view.querySelector('[data-item-body]');
    if (!body) return;
    if (!meta?.published) {
      body.innerHTML = renderEmptyNotice();
      bindEmptyActions();
      return;
    }
    body.innerHTML = '<div class="plugin-loading">正在读取物品目录…</div>';
    try {
      const data = await api.get(`${base}/items`, {
        version,
        keyword: itemView.keyword,
        kind: itemView.kind,
        page: itemView.page,
        size: itemView.size,
      });
      if (disposed || tab !== 'items') return;
      if (!data.records.length) {
        body.innerHTML = `
          <div class="mcw-empty">
            <h3>没有匹配的物品</h3>
            <p>换个关键字或分类试试。数据来自 ${esc(version)} 的官方 JAR。</p>
          </div>`;
        return;
      }
      const totalPages = Math.max(1, Math.ceil(data.total / data.size));
      body.innerHTML = `
        <div class="mcw-grid" data-grid>${data.records.map(itemCardHtml).join('')}</div>
        <div class="mcw-pager">
          <button class="btn btn-ghost btn-sm" data-item-page="${itemView.page - 1}" ${
        itemView.page <= 1 ? 'disabled' : ''
      }>上一页</button>
          <span>第 ${itemView.page} / ${totalPages} 页 · 共 ${data.total} 个条目</span>
          <button class="btn btn-ghost btn-sm" data-item-page="${itemView.page + 1}" ${
        itemView.page >= totalPages ? 'disabled' : ''
      }>下一页</button>
        </div>
      `;
      bindIcons(body);
      body.querySelectorAll('[data-item]').forEach((button) => {
        button.addEventListener('click', () => openItem(button.dataset.item));
      });
      body.querySelectorAll('[data-item-page]').forEach((button) => {
        button.addEventListener('click', () => {
          itemView.page = Number(button.dataset.itemPage);
          loadItems();
        });
      });
    } catch (error) {
      body.innerHTML = `<div class="mcw-empty"><h3>物品读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function itemCardHtml(item) {
    return `
      <button class="mcw-card" data-item="${esc(item.namespacedId)}" title="${esc(item.namespacedId)}">
        <span class="mcw-card-badge">${esc(kindLabel(item.kind))}</span>
        ${iconHtml(item, 'mcw-card-icon')}
        <strong>${esc(item.nameZh || item.nameEn || item.namespacedId)}</strong>
        <small>${esc(item.nameEn || '')}</small>
      </button>`;
  }

  function renderEmptyNotice() {
    return `
      <div class="mcw-empty">
        <h3>百科还没有发布任何版本</h3>
        <p>管理员需要先在版本管理中刷新 Mojang 版本清单、导入一个版本并发布，物品图鉴才会在这里出现。</p>
        ${
          isAdmin
            ? '<div class="mcw-actions"><button class="btn btn-primary" data-go-admin>前往版本管理</button></div>'
            : ''
        }
      </div>`;
  }

  function bindEmptyActions() {
    view.querySelector('[data-go-admin]')?.addEventListener('click', () => switchTab('admin'));
  }

  async function openItem(id) {
    const overlay = document.createElement('div');
    overlay.className = 'mcw-overlay';
    overlay.innerHTML = `
      <div class="mcw-dialog" role="dialog" aria-modal="true">
        <div class="mcw-dialog-head">
          <div><h2>物品详情</h2><small>${esc(id)}</small></div>
          <button class="btn btn-ghost btn-sm" data-close>关闭</button>
        </div>
        <div class="mcw-dialog-body"><div class="plugin-loading">正在读取详情…</div></div>
      </div>`;
    document.body.append(overlay);
    const body = overlay.querySelector('.mcw-dialog-body');
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
    try {
      const data = await api.get(`${base}/item`, { version, id });
      if (!data) return close();
      const item = data.item;
      body.innerHTML = `
        <div class="mcw-detail-hero">
          ${iconHtml(item)}
          <div>
            <h3>${esc(item.nameZh || item.nameEn || id)}</h3>
            <p>${esc(item.nameEn || '')}</p>
            <p class="mcw-tag">${esc(item.namespacedId)} · ${esc(kindLabel(item.kind))}</p>
          </div>
        </div>
        <div>
          <div class="mcw-section-title"><span>合成产出</span><strong>${data.producing.length} 个配方</strong></div>
          <div class="mcw-list mcw-detail-recipes">${
            data.producing.length
              ? data.producing.map((recipe) => recipeCardHtml(recipe, { compact: true })).join('')
              : '<p class="mcw-muted">该物品没有直接合成配方。</p>'
          }</div>
        </div>
        <div>
          <div class="mcw-section-title"><span>作为原料</span><strong>${data.using.length} 个配方</strong></div>
          <div class="mcw-list mcw-detail-recipes">${
            data.using.length
              ? data.using.map((recipe) => recipeCardHtml(recipe, { compact: true })).join('')
              : '<p class="mcw-muted">该物品没有被其他配方使用。</p>'
          }</div>
        </div>
      `;
      bindIcons(body);
      bindRecipeHover(body);
    } catch (error) {
      body.innerHTML = `<div class="mcw-empty"><h3>详情读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  // ---------------- 合成配方 ----------------

  async function renderRecipes() {
    view.innerHTML = `
      <div class="mcw-toolbar">
        <div class="filter-chips"><span class="mcw-muted">按配方 id、产物或类型筛选</span></div>
        <div class="mcw-toolbar-right">
          <label class="select-box"><select data-recipe-version>${versionOptions(version)}</select></label>
          <label class="search-box"><input type="search" data-recipe-search placeholder="搜索配方或产物" value="${esc(
            recipeView.keyword
          )}" /></label>
        </div>
      </div>
      <div data-recipe-body><div class="plugin-loading">正在读取配方…</div></div>
    `;
    await loadRecipes();
  }

  async function loadRecipes() {
    const body = view.querySelector('[data-recipe-body]');
    if (!body) return;
    if (!meta?.published) {
      body.innerHTML = renderEmptyNotice();
      bindEmptyActions();
      return;
    }
    body.innerHTML = '<div class="plugin-loading">正在读取配方…</div>';
    try {
      const data = await api.get(`${base}/recipes`, {
        version,
        keyword: recipeView.keyword,
        page: recipeView.page,
        size: recipeView.size,
      });
      if (disposed || tab !== 'recipes') return;
      if (!data.records.length) {
        body.innerHTML = `<div class="mcw-empty"><h3>没有匹配的配方</h3><p>换个关键字试试，或确认该版本已导入配方数据。</p></div>`;
        return;
      }
      const totalPages = Math.max(1, Math.ceil(data.total / data.size));
      body.innerHTML = `
        <div class="mcw-recipe-grid">${data.records.map((recipe) => recipeCardHtml(recipe)).join('')}</div>
        <div class="mcw-pager">
          <button class="btn btn-ghost btn-sm" data-recipe-page="${recipeView.page - 1}" ${
        recipeView.page <= 1 ? 'disabled' : ''
      }>上一页</button>
          <span>第 ${recipeView.page} / ${totalPages} 页 · 共 ${data.total} 个配方</span>
          <button class="btn btn-ghost btn-sm" data-recipe-page="${recipeView.page + 1}" ${
        recipeView.page >= totalPages ? 'disabled' : ''
      }>下一页</button>
        </div>
      `;
      bindIcons(body);
      bindRecipeHover(body);
      body.querySelectorAll('[data-recipe-page]').forEach((button) => {
        button.addEventListener('click', () => {
          recipeView.page = Number(button.dataset.recipePage);
          loadRecipes();
        });
      });
    } catch (error) {
      body.innerHTML = `<div class="mcw-empty"><h3>配方读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function recipeCardHtml(recipe, { compact = false } = {}) {
    const cells = Array.isArray(recipe.gridDetail) ? recipe.gridDetail.slice(0, 9) : [];
    while (cells.length < 9) cells.push(null);
    const grid = cells
      .map((cell) =>
        cell
          ? `<span class="mcw-cell" data-hover-id="${esc(cell.id)}" data-hover-name="${esc(
              cell.nameZh || ''
            )}" data-hover-en="${esc(cell.nameEn || '')}" data-hover-kind="${esc(
              cell.kind || 'item'
            )}" data-hover-icon="${esc(cell.icon || '')}" title="${esc(cell.id)}">${detailIconHtml(
              cell.id,
              cell.nameZh,
              cell.icon
            )}</span>`
          : '<span class="mcw-cell is-empty"></span>'
      )
      .join('');
    const result = `
      <div class="mcw-result">
        ${detailIconHtml(recipe.resultId, recipe.resultNameZh, recipe.resultIcon)}
        <div>
          <strong>${esc(recipe.resultNameZh || recipe.resultNameEn || recipe.resultId)}</strong>
          <small>${esc(recipe.resultId)} × ${Number(recipe.resultCount) || 1}</small>
        </div>
      </div>`;
    return `
      <article class="mcw-recipe">
        <div class="mcw-grid3">${grid}</div>
        <div class="mcw-arrow">→</div>
        ${result}
        <footer>
          <span>${esc(recipeLabel(recipe.type))}</span>
          ${compact ? '' : `<code>${esc(recipe.recipeId || '')}</code>`}
        </footer>
      </article>`;
  }

  // ---------------- 版本管理 ----------------

  async function renderAdmin() {
    if (!isAdmin) return;
    view.innerHTML = '<div class="plugin-loading">正在读取版本清单…</div>';
    await loadAdmin();
  }

  async function loadAdmin() {
    if (!isAdmin) return;
    try {
      const data = await api.get(`${base}/admin/state`, {
        keyword: adminView.keyword,
        type: adminView.type,
        status: adminView.status,
        page: adminView.page,
        size: 20,
      });
      adminView.data = data;
      if (disposed || tab !== 'admin') return;
      paintAdmin();
    } catch (error) {
      view.innerHTML = `<div class="mcw-empty"><h3>后台数据读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintAdmin() {
    const data = adminView.data;
    if (!data) return;
    const renders = data.renders || {};
    const totalPages = Math.max(1, Math.ceil(data.total / data.size));
    view.innerHTML = `
      <div class="mcw-stats">
        <div class="mcw-stat"><span>版本清单</span><strong>${data.versionCount}</strong><small>来自 Mojang 官方清单</small></div>
        <div class="mcw-stat"><span>已导入</span><strong>${data.importedCount}</strong><small>物品与配方已落盘</small></div>
        <div class="mcw-stat"><span>已发布</span><strong>${(data.publishedVersions || []).length}</strong><small>公开百科可见</small></div>
        <div class="mcw-stat"><span>渲染资产</span><strong>${renders.updated ? renders.items : '未更新'}</strong><small>${
      renders.updated
        ? `物品图 ${renders.items} · 生物 ${renders.entitiesFlat + renders.entitiesIsometric}`
        : '更新后图鉴显示 3D 图标'
    }</small></div>
      </div>

      <section class="mcw-panel">
        <div class="mcw-panel-head">
          <div>
            <h2>百科版本</h2>
            <p>导入会把该版本的物品、配方与方块状态写入本地；发布后前台图鉴与配方页可切换查看。</p>
          </div>
          <div class="mcw-row-actions">
            <button class="btn btn-ghost" data-admin-action="jobs">任务中心</button>
            <button class="btn btn-ghost" data-admin-action="refresh">刷新版本清单</button>
            <button class="btn btn-primary" data-admin-action="renders">更新渲染资产</button>
          </div>
        </div>
        <div class="mcw-toolbar" style="margin-bottom:18px">
          <div class="filter-chips">
            <span class="mcw-muted">${
              renders.updated
                ? `渲染源 ${esc(renders.commit || '—')}${renders.gameVersion ? ` · Minecraft ${esc(renders.gameVersion)}` : ''}`
                : '渲染资产尚未更新，图鉴会以首字母占位'
            }</span>
          </div>
          <div class="mcw-toolbar-right">
            <label class="search-box"><input type="search" data-admin-search placeholder="搜索版本号" value="${esc(
              adminView.keyword
            )}" /></label>
            <label class="select-box"><select data-admin-type>${typeOptions(adminView.type)}</select></label>
            <label class="select-box"><select data-admin-status>${statusOptions(adminView.status)}</select></label>
          </div>
        </div>
        <div class="mcw-table-wrap">
          <table class="mcw-table">
            <thead><tr><th>版本</th><th>类型</th><th>发布时间</th><th>物品</th><th>配方</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              ${renderAdminRows(data)}
            </tbody>
          </table>
        </div>
        <div class="mcw-pager" style="margin-top:18px">
          <button class="btn btn-ghost btn-sm" data-admin-page="${adminView.page - 1}" ${
      adminView.page <= 1 ? 'disabled' : ''
    }>上一页</button>
          <span>第 ${adminView.page} / ${totalPages} 页 · 共 ${data.total} ${
      data.grouped ? `组 · ${data.snapshotCount} 个快照` : '个版本'
    }</span>
          <button class="btn btn-ghost btn-sm" data-admin-page="${adminView.page + 1}" ${
      adminView.page >= totalPages ? 'disabled' : ''
    }>下一页</button>
        </div>
      </section>
    `;

    view.querySelectorAll('[data-admin-action]').forEach((button) => {
      button.addEventListener('click', () => adminAction(button.dataset.adminAction));
    });
    view.querySelectorAll('[data-admin-page]').forEach((button) => {
      button.addEventListener('click', () => {
        adminView.page = Number(button.dataset.adminPage);
        loadAdmin();
      });
    });
    view.querySelectorAll('[data-version-action]').forEach((button) => {
      button.addEventListener('click', () => versionAction(button.dataset.versionAction, button.dataset.version));
    });
    view.querySelectorAll('[data-toggle-group]').forEach((button) => {
      button.addEventListener('click', () => {
        const key = button.dataset.toggleGroup;
        if (expandedGroups.has(key)) expandedGroups.delete(key);
        else expandedGroups.add(key);
        paintAdmin();
      });
    });
    const search = view.querySelector('[data-admin-search]');
    search?.addEventListener('input', () => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        adminView.keyword = search.value.trim();
        adminView.page = 1;
        loadAdmin();
      }, 300);
    });
    view.querySelector('[data-admin-type]')?.addEventListener('change', (event) => {
      adminView.type = event.target.value;
      adminView.page = 1;
      expandedGroups.clear();
      loadAdmin();
    });
    view.querySelector('[data-admin-status]')?.addEventListener('change', (event) => {
      adminView.status = event.target.value;
      adminView.page = 1;
      expandedGroups.clear();
      loadAdmin();
    });
  }

  function typeOptions(selected) {
    const types = [
      ['', '全部类型'],
      ['release', '正式版'],
      ['snapshot', '快照'],
      ['old_beta', '旧 Beta'],
      ['old_alpha', '旧 Alpha'],
    ];
    return types
      .map(([value, label]) => `<option value="${value}" ${selected === value ? 'selected' : ''}>${label}</option>`)
      .join('');
  }

  function statusOptions(selected) {
    const statuses = [
      ['', '全部状态'],
      ['imported', '已导入'],
      ['unimported', '未导入'],
      ['published', '已发布'],
    ];
    return statuses
      .map(([value, label]) => `<option value="${value}" ${selected === value ? 'selected' : ''}>${label}</option>`)
      .join('');
  }

  function renderAdminRows(data) {
    const empty = `<tr><td colspan="7"><span class="mcw-muted">${
      data.grouped
        ? '没有匹配的版本或快照。点击「刷新版本清单」从 Mojang 拉取。'
        : '没有匹配的版本。点击「刷新版本清单」从 Mojang 拉取。'
    }</span></td></tr>`;
    if (!data.grouped) {
      return data.records?.length ? data.records.map((row) => versionRowHtml(row)).join('') : empty;
    }
    const groups = data.groups || [];
    if (!groups.length) return empty;
    return groups
      .map((entry) => {
        if (entry.kind === 'single') return versionRowHtml(entry.release);
        const expanded = expandedGroups.has(entry.key) || adminView.type === 'snapshot';
        const head =
          entry.kind === 'upcoming'
            ? upcomingRowHtml(entry, expanded)
            : versionRowHtml(entry.release, {
                groupKey: entry.key,
                groupCount: entry.snapshotCount,
                expanded,
              });
        const children = expanded
          ? (entry.snapshots || []).map((row) => versionRowHtml(row, { sub: true })).join('')
          : '';
        return head + children;
      })
      .join('');
  }

  function groupToggleHtml(key, count, expanded) {
    return `
      <button class="mcw-toggle ${expanded ? 'is-open' : ''}" data-toggle-group="${esc(key)}"
        title="${expanded ? '收起' : '展开'} ${count} 个快照" aria-expanded="${expanded ? 'true' : 'false'}">
        <span class="mcw-toggle-caret"></span><span>${count}</span>
      </button>`;
  }

  function upcomingRowHtml(entry, expanded) {
    const label = entry.label || '未发布快照';
    return `
      <tr class="mcw-group-row">
        <td class="mcw-version-cell">
          ${groupToggleHtml(entry.key, entry.snapshotCount, expanded)}
          <code>${esc(label)}</code><span class="mcw-dot" title="该版本尚未正式发布"></span>
          ${entry.label ? '' : '<span class="mcw-muted"> · 暂无正式版</span>'}
        </td>
        <td>snapshot</td>
        <td>${day(entry.releaseTime)}</td>
        <td>—</td>
        <td>—</td>
        <td><span class="mcw-badge">未发布</span></td>
        <td></td>
      </tr>`;
  }

  function versionRowHtml(row, options = {}) {
    const badges = [
      row.published ? '<span class="mcw-badge is-published">已发布</span>' : '',
      row.current ? '<span class="mcw-badge is-current">默认</span>' : '',
      !row.published && row.imported ? '<span class="mcw-badge is-imported">已导入</span>' : '',
      !row.imported ? '<span class="mcw-badge">未导入</span>' : '',
    ]
      .filter(Boolean)
      .join(' ');
    const actions = [
      row.imported
        ? `<button class="btn btn-ghost btn-sm" data-version-action="reimport" data-version="${esc(row.id)}">重新导入</button>`
        : `<button class="btn btn-primary btn-sm" data-version-action="import" data-version="${esc(row.id)}">导入</button>`,
      row.imported
        ? row.published
          ? `<button class="btn btn-ghost btn-sm" data-version-action="unpublish" data-version="${esc(row.id)}">取消发布</button>`
          : `<button class="btn btn-primary btn-sm" data-version-action="publish" data-version="${esc(row.id)}">发布</button>`
        : '',
      row.imported
        ? `<button class="btn btn-ghost btn-sm" data-version-action="delete" data-version="${esc(row.id)}">删除数据</button>`
        : '',
    ]
      .filter(Boolean)
      .join('');
    const groupToggle = options.groupKey
      ? groupToggleHtml(options.groupKey, options.groupCount || 0, options.expanded)
      : '<span class="mcw-toggle-space"></span>';
    const prefix = options.sub ? '<span class="mcw-sub-mark">└</span>' : '';
    return `
      <tr class="${options.sub ? 'mcw-subrow' : ''}">
        <td class="mcw-version-cell">
          ${groupToggle}${prefix}<code>${esc(row.id)}</code>${
      row.latest ? ' <span class="mcw-badge">最新</span>' : ''
    }
        </td>
        <td>${esc(row.type || '—')}</td>
        <td>${day(row.releaseTime)}</td>
        <td>${row.imported ? row.items : '—'}</td>
        <td>${row.imported ? row.recipes : '—'}</td>
        <td>${badges}</td>
        <td><div class="mcw-row-actions">${actions}</div></td>
      </tr>`;
  }

  async function adminAction(action) {
    try {
      if (action === 'refresh') {
        const result = await api.post(`${base}/admin/refresh`, {});
        toast(result.message || '版本清单已刷新', 'success');
        await loadAdmin();
        return;
      }
      if (action === 'jobs') {
        switchTab('jobs');
        return;
      }
      if (action === 'renders') {
        if (jobView.jobs.some((job) => !isTerminal(job.status))) {
          toast('已有任务在执行，请到任务中心等待完成', 'error');
          return;
        }
        const result = await api.post(`${base}/admin/renders/update`, {});
        startJob(result.jobId);
        return;
      }
    } catch (error) {
      toast(error.message || '操作失败', 'error');
    }
  }

  async function versionAction(action, versionId) {
    try {
      if (action === 'import' || action === 'reimport') {
        if (jobView.jobs.some((job) => !isTerminal(job.status))) {
          toast('已有任务在执行，请到任务中心等待完成', 'error');
          return;
        }
        const result = await api.post(`${base}/admin/import`, { version: versionId });
        startJob(result.jobId);
        return;
      }
      if (action === 'publish') {
        await api.post(`${base}/admin/publish`, { version: versionId });
        toast(`${versionId} 已发布`, 'success');
        await loadMeta();
        await loadAdmin();
        return;
      }
      if (action === 'unpublish') {
        await api.post(`${base}/admin/unpublish`, { version: versionId });
        toast(`${versionId} 已取消发布`, 'success');
        await loadMeta();
        await loadAdmin();
        return;
      }
      if (action === 'delete') {
        const ok = await confirm({
          title: '删除版本数据',
          message: `将删除 ${versionId} 已导入的物品、配方与方块状态，并取消发布。此操作不可撤销。`,
          confirmText: '删除',
          danger: true,
        });
        if (!ok) return;
        await api.post(`${base}/admin/delete`, { version: versionId });
        toast('版本数据已删除', 'success');
        await loadMeta();
        await loadAdmin();
      }
    } catch (error) {
      toast(error.message || '操作失败', 'error');
    }
  }

  // ---------------- 任务中心 ----------------

  async function renderJobs() {
    if (!isAdmin) return;
    view.innerHTML = `
      <div class="mcw-job-layout">
        <section class="mcw-panel mcw-joblist">
          <div class="mcw-panel-head">
            <div>
              <h2>任务中心</h2>
              <p>导入与渲染资产更新都在单线程队列中执行，可暂停、继续或取消。</p>
            </div>
            <div class="mcw-row-actions">
              <button class="btn btn-ghost btn-sm" data-jobs-refresh>刷新</button>
              <button class="btn btn-ghost btn-sm" data-jobs-back>返回版本管理</button>
            </div>
          </div>
          <div class="mcw-jobs-list" data-jobs-list><div class="plugin-loading">正在读取任务…</div></div>
        </section>
        <section class="mcw-panel mcw-jobdetail" data-job-detail>
          <div class="mcw-job-empty">选择左侧任务查看进度与日志</div>
        </section>
      </div>`;

    view.querySelector('[data-jobs-refresh]')?.addEventListener('click', async () => {
      await loadJobsList();
      if (jobView.selectedId) await selectJob(jobView.selectedId, { force: true });
    });
    view.querySelector('[data-jobs-back]')?.addEventListener('click', () => switchTab('admin'));

    await loadJobsList();
    if (jobView.selectedId) await selectJob(jobView.selectedId, { force: true });
    else if (jobView.jobs[0]) await selectJob(jobView.jobs[0].jobId);
    startJobsListPolling();
  }

  function jobLabel(version) {
    return version === '__renders__' ? '渲染资产更新' : `导入 Minecraft ${version}`;
  }

  function statusBadgeClass(status) {
    if (status === 'DONE') return 'is-published';
    if (status === 'FAILED') return 'is-failed';
    if (status === 'PAUSED') return 'is-current';
    if (['RUNNING', 'PENDING', 'PAUSING', 'RESUMING', 'CANCELLING'].includes(status)) return 'is-imported';
    return '';
  }

  async function loadJobsList() {
    const host = view.querySelector('[data-jobs-list]');
    if (!host) return;
    try {
      const data = await api.get(`${base}/admin/jobs`);
      if (disposed) return;
      jobView.jobs = data.records || [];
      renderJobsList();
    } catch (error) {
      host.innerHTML = `<p class="mcw-muted">任务列表读取失败：${esc(error.message || '未知错误')}</p>`;
    }
  }

  function renderJobsList() {
    const host = view.querySelector('[data-jobs-list]');
    if (!host) return;
    if (!jobView.jobs.length) {
      host.innerHTML = '<p class="mcw-muted">还没有执行过任务。从版本管理里导入版本或更新渲染资产后，任务会显示在这里。</p>';
      return;
    }
    host.innerHTML = jobView.jobs
      .map(
        (job) => `
        <button class="mcw-job-item ${job.jobId === jobView.selectedId ? 'is-active' : ''}" data-select-job="${esc(
          job.jobId
        )}">
          <span class="mcw-job-item-main">
            <strong>${esc(jobLabel(job.version))}</strong>
            <small>${esc(job.phase || '')} · ${new Date(job.updatedAt || job.createdAt).toLocaleString('zh-CN')}</small>
          </span>
          <span class="mcw-badge ${statusBadgeClass(job.status)}">${esc(job.status)}</span>
        </button>`
      )
      .join('');
    host.querySelectorAll('[data-select-job]').forEach((button) => {
      button.addEventListener('click', () => selectJob(button.dataset.selectJob, { force: true }));
    });
  }

  async function selectJob(jobId, { force = false } = {}) {
    if (!force && jobView.selectedId === jobId && jobView.job) return;
    jobView.selectedId = jobId;
    stopJobPolling();
    jobView.lastSeq = 0;
    jobView.logs = [];
    jobView.job = jobView.jobs.find((job) => job.jobId === jobId) || null;
    renderJobsList();
    renderJobDetail();
    await pollOnce();
    if (jobView.job && !isTerminal(jobView.job.status)) {
      jobView.timer = setInterval(pollOnce, 900);
    }
  }

  function renderJobDetail() {
    const host = view.querySelector('[data-job-detail]');
    if (!host) return;
    const job = jobView.job;
    if (!job) {
      host.innerHTML = '<div class="mcw-job-empty">选择左侧任务查看进度与日志</div>';
      return;
    }
    const terminal = isTerminal(job.status);
    const percent = job.total > 0 ? Math.min(100, Math.round((job.done / job.total) * 100)) : 0;
    host.innerHTML = `
      <div class="mcw-job-head">
        <div>
          <h2>${esc(jobLabel(job.version))}</h2>
          <p class="mcw-muted">开始于 ${new Date(job.createdAt || Date.now()).toLocaleString('zh-CN')}</p>
        </div>
        <div class="mcw-row-actions">
          ${
            terminal
              ? '<button class="btn btn-ghost btn-sm" data-job-action="delete">删除记录</button>'
              : `<button class="btn btn-ghost btn-sm" data-job-action="${
                  job.status === 'PAUSED' ? 'resume' : 'pause'
                }">${job.status === 'PAUSED' ? '继续' : '暂停'}</button>
                 <button class="btn btn-ghost btn-sm" data-job-action="cancel">取消</button>`
          }
        </div>
      </div>
      <div class="mcw-progress"><i style="width:${percent}%"></i></div>
      <div class="mcw-job-meta">
        <span><span class="mcw-badge ${statusBadgeClass(job.status)}">${esc(job.status)}</span> · ${esc(
      job.phase || ''
    )}</span>
        <span>${job.done} / ${job.total || '?'}</span>
      </div>
      ${job.error ? `<p class="mcw-job-error">${esc(job.error)}</p>` : ''}
      <pre class="mcw-log" data-job-log></pre>`;
    renderJobLog();
    host.querySelectorAll('[data-job-action]').forEach((button) => {
      button.addEventListener('click', () => jobAction(button.dataset.jobAction));
    });
  }

  function renderJobLog() {
    const log = view.querySelector('[data-job-log]');
    if (!log) return;
    log.innerHTML = jobView.logs
      .map((entry) => `<div class="is-${esc(entry.level.toLowerCase())}">${esc(entry.line)}</div>`)
      .join('');
    log.scrollTop = log.scrollHeight;
  }

  async function jobAction(action) {
    const job = jobView.job;
    if (!job) return;
    try {
      if (action === 'pause') await api.post(`${base}/admin/job/pause`, { id: job.jobId });
      else if (action === 'resume') await api.post(`${base}/admin/job/resume`, { id: job.jobId });
      else if (action === 'cancel') await api.post(`${base}/admin/job/cancel`, { id: job.jobId });
      else if (action === 'delete') {
        const ok = await confirm({
          title: '删除任务记录',
          message: '只会删除任务记录与日志，不会影响已导入的版本数据。',
          confirmText: '删除',
          danger: true,
        });
        if (!ok) return;
        await api.post(`${base}/admin/job/delete`, { id: job.jobId });
        jobView.selectedId = null;
        jobView.job = null;
        jobView.logs = [];
        stopJobPolling();
        await loadJobsList();
        if (jobView.jobs[0]) await selectJob(jobView.jobs[0].jobId);
        else renderJobDetail();
        return;
      }
      await pollOnce();
    } catch (error) {
      toast(error.message || '任务操作失败', 'error');
    }
  }

  function isTerminal(status) {
    return ['DONE', 'FAILED', 'CANCELLED'].includes(String(status));
  }

  async function startJob(jobId) {
    jobView.selectedId = jobId;
    jobView.lastSeq = 0;
    jobView.logs = [];
    jobView.job = {
      jobId,
      version: '',
      status: 'PENDING',
      phase: 'QUEUE',
      done: 0,
      total: 0,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
    await switchTab('jobs');
    toast('任务已开始，可在任务中心查看进度', 'info');
  }

  function stopJobPolling() {
    if (jobView.timer) clearInterval(jobView.timer);
    jobView.timer = null;
  }

  function stopPolling() {
    stopJobPolling();
    if (jobView.listTimer) clearInterval(jobView.listTimer);
    jobView.listTimer = null;
  }

  function startJobsListPolling() {
    if (jobView.listTimer) clearInterval(jobView.listTimer);
    jobView.listTimer = setInterval(() => {
      if (tab === 'jobs' && !disposed) loadJobsList();
    }, 2500);
  }

  async function pollOnce() {
    const job = jobView.job;
    if (!job || disposed) return;
    try {
      const wasTerminal = isTerminal(job.status);
      const data = await api.get(`${base}/admin/job`, { id: job.jobId, afterSeq: jobView.lastSeq });
      jobView.job = data.job;
      for (const event of data.events || []) {
        jobView.lastSeq = Math.max(jobView.lastSeq, Number(event.seq) || 0);
        jobView.logs.push({
          level: event.level || 'INFO',
          line: `[${new Date(event.updatedAt || Date.now()).toLocaleTimeString('zh-CN', {
            hour12: false,
          })}] ${event.message || ''}`,
        });
      }
      if (jobView.logs.length > 500) jobView.logs = jobView.logs.slice(-500);
      const index = jobView.jobs.findIndex((item) => item.jobId === data.job.jobId);
      if (index >= 0) jobView.jobs[index] = data.job;
      else jobView.jobs.unshift(data.job);
      renderJobsList();
      renderJobDetail();
      if (isTerminal(data.job.status)) {
        stopJobPolling();
        // 只有在本次轮询期间从“进行中”变为结束时才提示；回看历史任务不弹窗
        if (!wasTerminal) {
          if (data.job.status === 'DONE') toast('任务完成', 'success');
          else if (data.job.status === 'FAILED') toast(data.job.error || '任务失败', 'error');
        }
        await loadJobsList();
      }
    } catch (error) {
      stopJobPolling();
      toast(error.message || '任务状态读取失败', 'error');
    }
  }

  // ---------------- 事件 ----------------

  root.addEventListener('click', async (event) => {
    const tabButton = event.target.closest('[data-tab]');
    if (tabButton) {
      switchTab(tabButton.dataset.tab);
      return;
    }
    const kindButton = event.target.closest('[data-kind]');
    if (kindButton) {
      itemView.kind = kindButton.dataset.kind;
      itemView.page = 1;
      view.querySelectorAll('[data-kind]').forEach((button) => {
        button.classList.toggle('is-active', button === kindButton);
      });
      loadItems();
    }
  });

  view.addEventListener('change', (event) => {
    if (event.target.matches('[data-item-version]')) {
      version = event.target.value;
      itemView.page = 1;
      loadItems();
      loadMeta().then(() => {
        const select = view.querySelector('[data-item-version]');
        if (select) select.innerHTML = versionOptions(version);
        refreshKindChips();
      });
    } else if (event.target.matches('[data-recipe-version]')) {
      version = event.target.value;
      recipeView.page = 1;
      loadRecipes();
      loadMeta().then(refreshKindChips);
    }
  });

  view.addEventListener('input', (event) => {
    if (event.target.matches('[data-item-search]')) {
      const input = event.target;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        itemView.keyword = input.value.trim();
        itemView.page = 1;
        loadItems();
      }, 320);
    } else if (event.target.matches('[data-recipe-search]')) {
      const input = event.target;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        recipeView.keyword = input.value.trim();
        recipeView.page = 1;
        loadRecipes();
      }, 320);
    }
  });

  async function switchTab(next) {
    if (next === 'admin' && !isAdmin) return;
    tab = next;
    stopPolling();
    try {
      await loadMeta();
    } catch (error) {
      view.innerHTML = `<div class="mcw-empty"><h3>百科数据读取失败</h3><p>${esc(
        error.message || '未知错误'
      )}</p></div>`;
      return;
    }
    renderView();
  }

  try {
    await loadMeta();
  } catch (error) {
    view.innerHTML = `<div class="mcw-empty"><h3>百科数据读取失败</h3><p>${esc(
      error.message || '未知错误'
    )}</p></div>`;
    return () => {
      disposed = true;
      stopPolling();
      hideHoverCard();
      if (hoverCardEl) hoverCardEl.remove();
      hoverCardEl = null;
    };
  }
  renderView();

  return () => {
    disposed = true;
    stopPolling();
    clearTimeout(searchTimer);
    hideHoverCard();
    if (hoverCardEl) hoverCardEl.remove();
    hoverCardEl = null;
  };
}
