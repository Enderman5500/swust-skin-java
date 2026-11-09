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
  'minecraft:crafting_transmute': '染色转换',
  'minecraft:crafting_dye': '装备染色',
  'minecraft:crafting_imbue': '药箭',
  'minecraft:crafting_special_armordye': '盔甲染色',
  'minecraft:crafting_special_bannerduplicate': '旗帜复制',
  'minecraft:crafting_special_bookcloning': '成书复制',
  'minecraft:crafting_special_firework_rocket': '烟花火箭',
  'minecraft:crafting_special_firework_star': '烟火之星',
  'minecraft:crafting_special_firework_star_fade': '烟火之星（淡出）',
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
.mcw-card { overflow: hidden; }
.mcw-card strong { max-width: 100%; overflow-wrap: anywhere; line-height: 1.45; }
.mcw-card small { max-width: 100%; }
.mcw-icon-wrap { flex-shrink: 0; }
.mcw-result > div { min-width: 0; }
.mcw-result strong, .mcw-result small { overflow-wrap: anywhere; }
.mcw-dialog-head small { overflow-wrap: anywhere; }
.mcw-card-badge { position: absolute; top: 12px; right: 12px; padding: 2px 8px; border-radius: 999px; border: 1px solid rgba(44,76,102,.14); background: rgba(220,232,243,.55); color: var(--navy); font-size: 9.5px; letter-spacing: .04em; }
.mcw-card-icon { margin-bottom: 4px; }
.mcw-icon-wrap { position: relative; display: inline-flex; align-items: center; justify-content: center; width: 48px; height: 48px; }
.mcw-icon-wrap img { max-width: 100%; max-height: 100%; image-rendering: pixelated; }
.mcw-icon-wrap.is-empty::after { content: attr(data-initial); display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; border-radius: 10px; background: linear-gradient(140deg, var(--ice-soft), #fff); color: var(--navy); font-family: var(--font-display); font-size: 20px; }
.mcw-list { display: flex; flex-direction: column; gap: 12px; }
.mcw-recipe-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
@media (max-width: 860px) {
  .mcw-recipe-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
.mcw-recipe { display: grid; grid-template-columns: auto 28px auto; align-items: center; gap: 8px; padding: 10px 12px; border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.9); }
.mcw-recipe-grid .mcw-recipe { grid-template-columns: auto 18px minmax(0, 1fr); gap: 6px; }
.mcw-recipe-grid .mcw-result { min-width: 0; gap: 6px; }
.mcw-recipe-grid .mcw-result > div { min-width: 0; }
.mcw-recipe-grid .mcw-result small { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mcw-recipe-grid .mcw-result .mcw-icon-wrap { width: 34px; height: 34px; }
.mcw-recipe-grid .mcw-result strong { font-size: 12.5px; line-height: 1.35; }
.mcw-recipe-grid .mcw-result small { font-size: 9.5px; }
.mcw-recipe-grid .mcw-grid3 { grid-template-columns: repeat(3, 32px); grid-template-rows: repeat(3, 32px); gap: 3px; padding: 6px; }
.mcw-recipe-grid .mcw-arrow { font-size: 16px; }
.mcw-recipe footer { grid-column: 1 / -1; display: flex; justify-content: space-between; gap: 8px; padding-top: 6px; border-top: 1px dashed var(--line); font-size: 10.5px; color: var(--muted); }
.mcw-recipe footer code { flex: 1 1 auto; min-width: 0; text-align: right; font-family: var(--font-mono); font-size: 10px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
.mcw-pager { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 18px; font-size: 12px; color: var(--muted); flex-wrap: wrap; }
.mcw-page-jump { display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
.mcw-page-jump input { width: 58px; height: 30px; padding: 0 8px; border: 1px solid var(--line); border-radius: 8px; background: #fff; font-size: 12px; text-align: center; color: var(--text); appearance: textfield; -moz-appearance: textfield; }
.mcw-page-jump input::-webkit-outer-spin-button,
.mcw-page-jump input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.mcw-page-jump input:focus { outline: none; border-color: rgba(44, 76, 102, 0.35); box-shadow: 0 0 0 3px rgba(44, 76, 102, 0.08); }
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
.mcw-detail-name-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.mcw-cell.is-tag { border-color: rgba(44, 76, 102, 0.28); }
.mcw-tag-mark { position: absolute; top: 2px; right: 3px; font-family: var(--font-mono); font-size: 8px; color: var(--navy); opacity: 0.7; }
.mcw-hover-card.is-tag-card { display: block; width: min(360px, calc(100vw - 28px)); max-width: none; padding: 12px 14px; max-height: min(70vh, 380px); overflow-y: auto; pointer-events: auto; }
.mcw-tag-head { font-family: var(--font-mono); font-size: 11px; color: var(--navy); margin-bottom: 2px; word-break: break-all; }
.mcw-tag-count { font-size: 10px; color: var(--muted); margin-bottom: 8px; }
.mcw-hover-card.is-tag-card { padding-right: 22px; scrollbar-width: thin; scrollbar-color: rgba(44, 76, 102, 0.38) transparent; }
.mcw-hover-card.is-tag-card::-webkit-scrollbar { width: 12px; }
.mcw-hover-card.is-tag-card::-webkit-scrollbar-track { background: transparent; }
.mcw-hover-card.is-tag-card::-webkit-scrollbar-thumb { min-height: 32px; border: 4px solid transparent; border-radius: 999px; background: rgba(44, 76, 102, 0.38); background-clip: padding-box; }
.mcw-hover-card.is-tag-card::-webkit-scrollbar-thumb:hover { background: rgba(44, 76, 102, 0.55); background-clip: padding-box; }
.mcw-tag-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(112px, 1fr)); gap: 4px 10px; }
.mcw-tag-chip { display: flex; align-items: center; gap: 6px; min-width: 0; font-size: 11px; color: var(--text); }
.mcw-tag-chip .mcw-icon-wrap { width: 22px; height: 22px; flex: 0 0 auto; }
.mcw-tag-chip .mcw-icon-wrap.is-empty::after { font-size: 9px; border-radius: 4px; }
.mcw-tag-chip span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mcw-compare-versions { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: stretch; gap: 12px; margin-bottom: 14px; }
.mcw-version-card { display: flex; flex-direction: column; gap: 4px; padding: 16px 18px; border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.86); }
.mcw-version-card span { font-size: 9.5px; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); }
.mcw-version-card strong { font-family: var(--font-display); font-size: 21px; color: var(--navy); word-break: break-all; }
.mcw-version-card small { font-size: 11px; color: var(--muted); }
.mcw-version-card.is-target { border-color: rgba(44,76,102,.3); background: linear-gradient(140deg, rgba(220,232,243,.55), #fff); }
.mcw-compare-arrow { align-self: center; color: var(--muted); font-size: 18px; }
.mcw-compare-summary { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
.mcw-diff-list { display: flex; flex-direction: column; gap: 10px; }
.mcw-diff-list.is-items { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 10px; }
.mcw-diff-list.is-recipes { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 10px; }
.mcw-diff-card { position: relative; }
.mcw-diff-side { position: relative; }
.mcw-diff-side-badge { top: 10px; right: 10px; position: absolute; z-index: 1; }
.mcw-diff-side-head { padding-right: 64px; }
.mcw-compare-summary { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.mcw-compare-group { display: inline-flex; align-items: center; gap: 5px; padding: 4px 8px; border-radius: 999px; background: rgba(244,246,249,.75); border: 1px solid var(--line); }
.mcw-compare-group > span { font-size: 10px; color: var(--muted); margin-right: 2px; }
.mcw-compare-group button.mcw-badge { cursor: pointer; border: 1px solid transparent; }
.mcw-compare-group button.mcw-badge.is-active { border-color: var(--navy); box-shadow: 0 0 0 2px rgba(44,76,102,.14); }
.mcw-diff-side .mcw-grid3 { align-self: flex-start; }
.mcw-diff-card { border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.88); overflow: hidden; }
.mcw-diff-card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 6px 10px; border-bottom: 1px solid var(--line); background: var(--paper); font-size: 11px; color: var(--muted); }
.mcw-diff-card-head code { flex: 1 1 auto; min-width: 0; text-align: right; font-family: var(--font-mono); font-size: 10.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mcw-diff-body { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); gap: 6px; padding: 8px; align-items: stretch; }
.mcw-diff-body.is-single { grid-template-columns: minmax(0, 1fr); }
.mcw-compare-recipe { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.mcw-compare-recipe .mcw-grid3 { grid-template-columns: repeat(3, 26px); grid-template-rows: repeat(3, 26px); gap: 3px; padding: 5px; }
.mcw-compare-recipe .mcw-cell img { max-width: 22px; max-height: 22px; }
.mcw-compare-recipe .mcw-cell .mcw-icon-wrap { width: 22px; height: 22px; }
.mcw-compare-recipe .mcw-diff-side-head .mcw-icon-wrap { width: 24px; height: 24px; }
.mcw-compare-recipe-info .mcw-diff-side-head { gap: 4px; padding-right: 0; }
.mcw-compare-recipe-info .mcw-diff-side-head strong { flex: 1 1 0; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mcw-compare-recipe-info strong { font-size: 11px; line-height: 1.35; }
.mcw-compare-recipe-info small { display: block; font-size: 9px; line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mcw-compare-recipe-info { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.mcw-diff-side { display: flex; flex-direction: column; gap: 3px; min-width: 0; padding: 5px 6px; border-radius: 10px; border: 1px dashed var(--line-strong); }
.mcw-diff-side.is-present { border-style: solid; background: rgba(244,246,249,.55); }
.mcw-diff-side.is-target { border-color: rgba(60,122,91,.28); background: rgba(60,122,91,.05); }
.mcw-diff-side-label { font-size: 9.5px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); }
.mcw-diff-side-head { display: flex; align-items: center; gap: 6px; min-width: 0; }
.mcw-diff-side-head .mcw-icon-wrap { width: 30px; height: 30px; flex: 0 0 auto; }
.mcw-diff-side-head strong { font-size: 12px; color: var(--navy); overflow-wrap: anywhere; }
.mcw-diff-side small { display: block; font-size: 10.5px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mcw-diff-side-empty { color: var(--muted); font-size: 12px; }
.mcw-diff-arrow { align-self: center; color: var(--muted); font-size: 16px; }
@media (max-width: 720px) {
  .mcw-diff-list.is-items, .mcw-diff-list.is-recipes { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .mcw-compare-versions { grid-template-columns: 1fr; }
  .mcw-compare-arrow { justify-self: center; transform: rotate(90deg); }
  .mcw-diff-body { grid-template-columns: 1fr; }
  .mcw-diff-arrow { justify-self: center; transform: rotate(90deg); }
}
.mcw-compare-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.8); overflow: hidden; margin: 0 0 16px; }
.mcw-compare-stat { padding: 12px 16px; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); display: flex; flex-direction: column; gap: 2px; }
.mcw-compare-stat:nth-child(3n) { border-right: 0; }
.mcw-compare-stat:nth-child(n+4) { border-bottom: 0; }
.mcw-compare-stat span { font-size: 10px; letter-spacing: .06em; color: var(--muted); }
.mcw-compare-stat strong { font-family: var(--font-display); font-size: 20px; color: var(--navy); }
.mcw-diff { font-size: 11px; color: var(--muted); line-height: 1.6; }
.mcw-diff b { color: var(--navy); font-weight: 600; }
.mcw-diff .is-new { color: var(--success); }
.mcw-diff .is-old { color: var(--danger); }
@media (max-width: 900px) {
  .mcw-job-layout { grid-template-columns: 1fr; }
  .mcw-jobs-list { max-height: 260px; }
  .mcw-jobdetail { min-height: 420px; }
  .mcw-jobdetail .mcw-log { min-height: 260px; }
}
@media (max-width: 720px) {
  .mcw-recipe-grid { grid-template-columns: 1fr; }
}
.mcw-job-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 10px; border-radius: 9px; border: 1px solid var(--line); background: #fff; font-size: 11.5px; }
@keyframes mcw-view-in { from { opacity: 0.55; transform: translateY(6px); } to { opacity: 1; transform: none; } }
.mcw-view-enter { animation: mcw-view-in 0.45s var(--ease) both; }
.mcw-page-enter { animation: mcw-view-in 0.4s var(--ease) both; }
.mcw-vt-view { view-transition-name: mcw-view; }
::view-transition-old(mcw-view) { animation: mcw-vt-out 0.22s ease both; }
::view-transition-new(mcw-view) { animation: mcw-vt-in 0.3s var(--ease) both; }
.mcw-list-loading { opacity: 0.45; pointer-events: none; transition: opacity 0.22s ease; }
@keyframes mcw-vt-out { to { opacity: 0; transform: translateY(-4px); } }
@keyframes mcw-vt-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
.mcw-overlay { position: fixed; inset: 0; z-index: 120; display: flex; align-items: center; justify-content: center; padding: 24px; background: rgba(9,21,34,.45); backdrop-filter: blur(3px); opacity: 0; transition: opacity 0.3s ease, backdrop-filter 0.3s ease; }
.mcw-overlay.is-open { opacity: 1; }
.mcw-overlay.is-closing { pointer-events: none; opacity: 0; backdrop-filter: blur(0); }
.mcw-dialog { display: flex; flex-direction: column; width: min(720px, 100%); max-height: min(86vh, 900px); overflow: hidden; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-lg); transform: translateY(22px) scale(0.965); transform-origin: 50% 55%; transition: transform 0.45s cubic-bezier(0.2, 0.8, 0.2, 1); }
.mcw-overlay.is-open .mcw-dialog { transform: none; }
.mcw-overlay.is-closing .mcw-dialog { transform: translateY(14px) scale(0.975); }
.mcw-dialog-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; padding: 22px 24px 16px; border-bottom: 1px solid var(--line); position: sticky; top: 0; background: #fff; z-index: 1; }
.mcw-dialog-head h2 { font-family: var(--font-display); font-size: 24px; color: var(--navy); }
.mcw-dialog-head small { font-size: 11px; color: var(--muted); font-family: var(--font-mono); }
.mcw-dialog-body { padding: 20px 24px 26px; display: flex; flex-direction: column; gap: 18px; overflow-y: auto; min-height: 0; flex: 1 1 auto; }
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
.mcw-detail-name-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.mcw-cell.is-tag { border-color: rgba(44, 76, 102, 0.28); }
.mcw-tag-mark { position: absolute; top: 2px; right: 3px; font-family: var(--font-mono); font-size: 8px; color: var(--navy); opacity: 0.7; }
.mcw-hover-card.is-tag-card { display: block; width: min(360px, calc(100vw - 28px)); max-width: none; padding: 12px 14px; max-height: min(70vh, 380px); overflow-y: auto; pointer-events: auto; }
.mcw-tag-head { font-family: var(--font-mono); font-size: 11px; color: var(--navy); margin-bottom: 2px; word-break: break-all; }
.mcw-tag-count { font-size: 10px; color: var(--muted); margin-bottom: 8px; }
.mcw-hover-card.is-tag-card { padding-right: 22px; scrollbar-width: thin; scrollbar-color: rgba(44, 76, 102, 0.38) transparent; }
.mcw-hover-card.is-tag-card::-webkit-scrollbar { width: 12px; }
.mcw-hover-card.is-tag-card::-webkit-scrollbar-track { background: transparent; }
.mcw-hover-card.is-tag-card::-webkit-scrollbar-thumb { min-height: 32px; border: 4px solid transparent; border-radius: 999px; background: rgba(44, 76, 102, 0.38); background-clip: padding-box; }
.mcw-hover-card.is-tag-card::-webkit-scrollbar-thumb:hover { background: rgba(44, 76, 102, 0.55); background-clip: padding-box; }
.mcw-tag-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(112px, 1fr)); gap: 4px 10px; }
.mcw-tag-chip { display: flex; align-items: center; gap: 6px; min-width: 0; font-size: 11px; color: var(--text); }
.mcw-tag-chip .mcw-icon-wrap { width: 22px; height: 22px; flex: 0 0 auto; }
.mcw-tag-chip .mcw-icon-wrap.is-empty::after { font-size: 9px; border-radius: 4px; }
.mcw-tag-chip span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mcw-compare-versions { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: stretch; gap: 12px; margin-bottom: 14px; }
.mcw-version-card { display: flex; flex-direction: column; gap: 4px; padding: 16px 18px; border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.86); }
.mcw-version-card span { font-size: 9.5px; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); }
.mcw-version-card strong { font-family: var(--font-display); font-size: 21px; color: var(--navy); word-break: break-all; }
.mcw-version-card small { font-size: 11px; color: var(--muted); }
.mcw-version-card.is-target { border-color: rgba(44,76,102,.3); background: linear-gradient(140deg, rgba(220,232,243,.55), #fff); }
.mcw-compare-arrow { align-self: center; color: var(--muted); font-size: 18px; }
.mcw-compare-summary { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
.mcw-diff-list { display: flex; flex-direction: column; gap: 10px; }
.mcw-diff-list.is-items { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 10px; }
.mcw-diff-list.is-recipes { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 10px; }
.mcw-diff-card { position: relative; }
.mcw-diff-side { position: relative; }
.mcw-diff-side-badge { top: 10px; right: 10px; position: absolute; z-index: 1; }
.mcw-diff-side-head { padding-right: 64px; }
.mcw-compare-summary { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.mcw-compare-group { display: inline-flex; align-items: center; gap: 5px; padding: 4px 8px; border-radius: 999px; background: rgba(244,246,249,.75); border: 1px solid var(--line); }
.mcw-compare-group > span { font-size: 10px; color: var(--muted); margin-right: 2px; }
.mcw-compare-group button.mcw-badge { cursor: pointer; border: 1px solid transparent; }
.mcw-compare-group button.mcw-badge.is-active { border-color: var(--navy); box-shadow: 0 0 0 2px rgba(44,76,102,.14); }
.mcw-diff-side .mcw-grid3 { align-self: flex-start; }
.mcw-diff-card { border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.88); overflow: hidden; }
.mcw-diff-card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 6px 10px; border-bottom: 1px solid var(--line); background: var(--paper); font-size: 11px; color: var(--muted); }
.mcw-diff-card-head code { flex: 1 1 auto; min-width: 0; text-align: right; font-family: var(--font-mono); font-size: 10.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mcw-diff-body { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); gap: 6px; padding: 8px; align-items: stretch; }
.mcw-diff-body.is-single { grid-template-columns: minmax(0, 1fr); }
.mcw-compare-recipe { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.mcw-compare-recipe .mcw-grid3 { grid-template-columns: repeat(3, 26px); grid-template-rows: repeat(3, 26px); gap: 3px; padding: 5px; }
.mcw-compare-recipe .mcw-cell img { max-width: 22px; max-height: 22px; }
.mcw-compare-recipe .mcw-cell .mcw-icon-wrap { width: 22px; height: 22px; }
.mcw-compare-recipe .mcw-diff-side-head .mcw-icon-wrap { width: 24px; height: 24px; }
.mcw-compare-recipe-info .mcw-diff-side-head { gap: 4px; padding-right: 0; }
.mcw-compare-recipe-info .mcw-diff-side-head strong { flex: 1 1 0; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mcw-compare-recipe-info strong { font-size: 11px; line-height: 1.35; }
.mcw-compare-recipe-info small { display: block; font-size: 9px; line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mcw-compare-recipe-info { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.mcw-diff-side { display: flex; flex-direction: column; gap: 3px; min-width: 0; padding: 5px 6px; border-radius: 10px; border: 1px dashed var(--line-strong); }
.mcw-diff-side.is-present { border-style: solid; background: rgba(244,246,249,.55); }
.mcw-diff-side.is-target { border-color: rgba(60,122,91,.28); background: rgba(60,122,91,.05); }
.mcw-diff-side-label { font-size: 9.5px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); }
.mcw-diff-side-head { display: flex; align-items: center; gap: 6px; min-width: 0; }
.mcw-diff-side-head .mcw-icon-wrap { width: 30px; height: 30px; flex: 0 0 auto; }
.mcw-diff-side-head strong { font-size: 12px; color: var(--navy); overflow-wrap: anywhere; }
.mcw-diff-side small { display: block; font-size: 10.5px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mcw-diff-side-empty { color: var(--muted); font-size: 12px; }
.mcw-diff-arrow { align-self: center; color: var(--muted); font-size: 16px; }
@media (max-width: 720px) {
  .mcw-diff-list.is-items, .mcw-diff-list.is-recipes { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .mcw-compare-versions { grid-template-columns: 1fr; }
  .mcw-compare-arrow { justify-self: center; transform: rotate(90deg); }
  .mcw-diff-body { grid-template-columns: 1fr; }
  .mcw-diff-arrow { justify-self: center; transform: rotate(90deg); }
}
.mcw-compare-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border-radius: var(--radius); border: 1px solid var(--line); background: rgba(255,255,255,.8); overflow: hidden; margin: 0 0 16px; }
.mcw-compare-stat { padding: 12px 16px; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); display: flex; flex-direction: column; gap: 2px; }
.mcw-compare-stat:nth-child(3n) { border-right: 0; }
.mcw-compare-stat:nth-child(n+4) { border-bottom: 0; }
.mcw-compare-stat span { font-size: 10px; letter-spacing: .06em; color: var(--muted); }
.mcw-compare-stat strong { font-family: var(--font-display); font-size: 20px; color: var(--navy); }
.mcw-diff { font-size: 11px; color: var(--muted); line-height: 1.6; }
.mcw-diff b { color: var(--navy); font-weight: 600; }
.mcw-diff .is-new { color: var(--success); }
.mcw-diff .is-old { color: var(--danger); }
@media (max-width: 900px) {
  .mcw-grid { grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); }
  .mcw-detail-hero { flex-wrap: wrap; }
}
@media (max-width: 640px) {
  .mcw-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .mcw-card { padding: 13px; }
  .mcw-card strong { font-size: 12.5px; }
  .mcw-card-badge { top: 8px; right: 8px; padding: 1px 6px; font-size: 9px; }
  .mcw-toolbar-right { width: 100%; }
  .mcw-tabs { display: flex; width: 100%; max-width: 100%; flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none; padding: 3px; gap: 4px; }
  .mcw-tabs::-webkit-scrollbar { display: none; }
  .mcw-tabs .chip { flex: 0 0 auto; min-height: 32px; padding: 7px 9px; font-size: 11px; }
  .filter-chips { flex-wrap: nowrap; max-width: 100%; overflow-x: auto; scrollbar-width: none; padding-bottom: 2px; }
  .filter-chips::-webkit-scrollbar { display: none; }
  .filter-chips > * { flex: 0 0 auto; }
  .mcw-toolbar { gap: 10px; }
  .mcw-toolbar-right { width: 100%; flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none; padding-bottom: 2px; gap: 4px; }
  .mcw-toolbar-right::-webkit-scrollbar { display: none; }
  .mcw-toolbar-right > * { flex: 0 0 auto; }
  .mcw-toolbar .search-box { flex: 1 1 150px; min-width: 0; width: auto; max-width: none; }
  .mcw-toolbar .search-box input { min-width: 0; }
  .mcw-toolbar .select-box select { min-width: 88px; max-width: 130px; padding: 0 30px 0 10px; font-size: 11.5px; }
  .mcw-toolbar-right .mcw-compare-select select { min-width: 0; max-width: 130px; padding: 0 22px 0 8px; font-size: 11px; }
  .mcw-toolbar-right [data-compare-run] { min-height: 32px; padding: 0 12px; }
  .mcw-hide-mobile { display: none; }
  .mcw-compare-summary { flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none; padding-bottom: 2px; }
  .mcw-compare-summary::-webkit-scrollbar { display: none; }
  .mcw-compare-group { flex: 0 0 auto; }
  .mcw-page-jump, .mcw-jump-btn { display: none; }
  .mcw-pager { flex-wrap: nowrap; gap: 6px; font-size: 10.5px; }
  .mcw-pager .btn { min-height: 30px; padding: 0 10px; }
  .mcw-pager-total { display: none; }
  .mcw-dialog-body { padding: 16px; }
}
.mcw-diff-list.is-recipes { grid-template-columns: repeat(3, minmax(0, 1fr)); }
@media (max-width: 860px) {
  .mcw-diff-list.is-recipes { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 720px) {
  .mcw-diff-list.is-recipes { grid-template-columns: 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  .mcw-view-enter, .mcw-page-enter { animation: none; }
  ::view-transition-old(mcw-view), ::view-transition-new(mcw-view) { animation: none; }
  .mcw-overlay, .mcw-dialog { transition: none; }
}
@media (max-width: 720px) {
  .mcw-stats { grid-template-columns: repeat(2, 1fr); }
  .mcw-stat:nth-child(2) { border-right: 0; }
  .mcw-stat:nth-child(-n+2) { border-bottom: 1px solid var(--line); }
  .mcw-recipe, .mcw-recipe-grid .mcw-recipe { grid-template-columns: 1fr; justify-items: center; }
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
  const compactLayout = () => window.matchMedia?.('(max-width: 640px)').matches === true;
  let lastCompact = compactLayout();
  const narrowCards = () => window.matchMedia?.('(max-width: 720px)').matches === true;
  const viewWidth = () => view?.clientWidth || Math.max(320, (window.innerWidth || 0) - 48);
  const gridColumns = (minWidth, gap) => Math.max(1, Math.floor((viewWidth() + gap) / (minWidth + gap)));
  const itemColumns = () => {
    if (lastCompact) return 2;
    return gridColumns(window.matchMedia?.('(max-width: 900px)').matches ? 140 : 168, 12);
  };
  const cardColumns = () => (narrowCards() ? 1 : gridColumns(270, 10));
  const applyPageSizes = () => {
    const columns = itemColumns();
    itemView.size = lastCompact ? columns * 6 : columns * (columns >= 6 ? 3 : 4);
    const cards = cardColumns();
    recipeView.size = window.matchMedia?.('(min-width: 861px)').matches ? 12 : 8;
    adminView.size = lastCompact ? 10 : 15;
    compareView.size = narrowCards() ? 12 : cards * 5;
  };
  let itemView = { keyword: '', kind: '', page: 1, size: 18, loading: false };
  let recipeView = { keyword: '', page: 1, size: 12, loading: false };
  const adminView = { keyword: '', type: '', status: '', page: 1, size: 15, data: null, loading: false };
  const expandedGroups = new Set();
  const compareView = { from: '', to: '', data: null, kind: 'items', filter: 'added', itemKind: 'item', keyword: '', page: 1, size: 20, versions: [] };
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
            <button class="chip" data-tab="compare" role="tab">版本比对</button>
            ${isAdmin ? '<button class="chip" data-tab="admin" role="tab">版本管理</button>' : ''}
            ${isAdmin ? '<button class="chip" data-tab="jobs" role="tab">任务中心</button>' : ''}
          </div>
        </div>
      </header>
      <div data-view></div>
    </section>
  `;
  const view = root.querySelector('[data-view]');
  view.classList.add('mcw-vt-view');
  applyPageSizes();

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
  let hoverHideTimer = 0;

  function ensureHoverCard() {
    if (hoverCardEl) return hoverCardEl;
    hoverCardEl = document.createElement('div');
    hoverCardEl.className = 'mcw-hover-card';
    hoverCardEl.hidden = true;
    hoverCardEl.addEventListener('mouseenter', cancelHideHoverCard);
    hoverCardEl.addEventListener('mouseleave', scheduleHideHoverCard);
    document.body.append(hoverCardEl);
    return hoverCardEl;
  }

  function cancelHideHoverCard() {
    clearTimeout(hoverHideTimer);
    hoverHideTimer = 0;
  }

  function scheduleHideHoverCard() {
    cancelHideHoverCard();
    hoverHideTimer = setTimeout(hideHoverCard, 220);
  }

  function showHoverCard(cell) {
    const card = ensureHoverCard();
    card.classList.remove('is-tag-card');
    card.scrollTop = 0;
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
    positionHoverCard(cell);
  }

  function positionHoverCard(cell) {
    const card = hoverCardEl;
    if (!card || card.hidden) return;
    const rect = card.getBoundingClientRect();
    const anchor = cell.getBoundingClientRect();
    const pad = 14;
    const gap = 10;
    let x = anchor.left + (anchor.width - rect.width) / 2;
    let y = anchor.bottom + gap;
    if (y + rect.height > window.innerHeight - pad) y = anchor.top - rect.height - gap;
    x = Math.max(pad, Math.min(x, window.innerWidth - rect.width - pad));
    y = Math.max(pad, Math.min(y, window.innerHeight - rect.height - pad));
    card.style.left = `${Math.round(x)}px`;
    card.style.top = `${Math.round(y)}px`;
  }

  function hideHoverCard() {
    cancelHideHoverCard();
    hoverToken += 1;
    if (hoverCardEl) {
      hoverCardEl.hidden = true;
      hoverCardEl.classList.remove('is-tag-card');
    }
  }

  const tagCache = new Map();
  let hoverToken = 0;

  function bindRecipeHover(container) {
    container.querySelectorAll('[data-hover-id]').forEach((cell) => {
      cell.addEventListener('mouseenter', () => {
        cancelHideHoverCard();
        if (cell.dataset.hoverTag) showTagHoverCard(cell);
        else showHoverCard(cell);
      });
      cell.addEventListener('mouseleave', scheduleHideHoverCard);
      cell.addEventListener(
        'wheel',
        (event) => {
          if (!cell.dataset.hoverTag) return;
          const card = hoverCardEl;
          if (!card || card.hidden || !card.classList.contains('is-tag-card')) return;
          const max = card.scrollHeight - card.clientHeight;
          if (max <= 0) return;
          const delta = event.deltaMode === 1 ? event.deltaY * 16 : event.deltaY;
          const next = Math.max(0, Math.min(max, card.scrollTop + delta));
          if (next === card.scrollTop) return;
          event.preventDefault();
          card.scrollTop = next;
        },
        { passive: false }
      );
    });
  }

  function showTagHoverCard(cell) {
    const tag = cell.dataset.hoverTag;
    const tagVersion = cell.dataset.hoverVersion || version;
    const card = ensureHoverCard();
    card.classList.add('is-tag-card');
    card.scrollTop = 0;
    const token = ++hoverToken;
    const render = (data) => {
      if (token !== hoverToken) return;
      const members = data.members || [];
      card.innerHTML = `
        <div class="mcw-tag-head">#${esc(tag.replace(/^#/, ''))}</div>
        <div class="mcw-tag-count">${data.total || members.length} 个物品${
          data.total > members.length ? `（显示前 ${members.length} 个）` : ''
        }</div>
        <div class="mcw-tag-grid">${members
          .map(
            (item) => `
          <span class="mcw-tag-chip" title="${esc(item.namespacedId)}">${iconHtml(item)}<span>${esc(
              item.nameZh || item.nameEn || item.namespacedId
            )}</span></span>`
          )
          .join('')}</div>`;
      card.scrollTop = 0;
      bindIcons(card);
      card.hidden = false;
      positionHoverCard(cell);
    };
    const cacheKey = tagVersion + '|' + tag;
    const cached = tagCache.get(cacheKey);
    if (cached) {
      render(cached);
      return;
    }
    card.innerHTML = `<div class="mcw-tag-head">#${esc(tag.replace(/^#/, ''))}</div><div class="mcw-tag-count">加载标签成员…</div>`;
    card.hidden = false;
    positionHoverCard(cell);
    api.get(`${base}/tag`, { version: tagVersion, id: tag })
      .then((data) => {
        tagCache.set(cacheKey, data);
        render(data);
      })
      .catch(() => {
        if (token === hoverToken) {
          card.innerHTML = `<div class="mcw-tag-head">#${esc(tag)}</div><div class="mcw-tag-count">标签成员读取失败</div>`;
        }
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
    hideHoverCard();
    root.querySelectorAll('[data-tab]').forEach((button) => {
      button.classList.toggle('is-active', button.dataset.tab === tab);
    });
    if (tab === 'admin') return renderAdmin();
    if (tab === 'compare') return renderCompare();
    if (tab === 'jobs') return renderJobs();
    if (tab === 'recipes') return renderRecipes();
    return renderItems();
  }

  function animateElement(element, className = 'mcw-page-enter') {
    if (!element) return;
    element.classList.remove(className);
    void element.offsetWidth;
    element.classList.add(className);
  }

  let switching = false;

  /** 有原生 View Transitions 时先保留旧画面，等数据就绪后交叉淡化；否则回退为淡入。 */
  function runViewTransition(update) {
    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    view.classList.add('mcw-vt-view');
    if (switching || reduceMotion || typeof document.startViewTransition !== 'function') {
      return Promise.resolve(update()).finally(() => {
        if (!reduceMotion) animateElement(view, 'mcw-view-enter');
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

  async function loadItems(options = {}) {
    const body = view.querySelector('[data-item-body]');
    if (!body) return;
    if (!meta?.published) {
      body.innerHTML = renderEmptyNotice();
      bindEmptyActions();
      return;
    }
    const keep = Boolean(options.keep);
    if (keep) {
      body.classList.add('mcw-list-loading');
    } else {
      body.innerHTML = '<div class="plugin-loading">正在读取物品目录…</div>';
    }
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
        body.classList.remove('mcw-list-loading');
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
          <span class="mcw-pager-info">第 ${itemView.page} / ${totalPages} 页<span class="mcw-pager-total"> · 共 ${data.total} 个条目</span></span>
          <label class="mcw-page-jump">跳至 <input type="number" min="1" max="${totalPages}" data-item-jump-input aria-label="跳转页码" inputmode="numeric" /> 页</label>
          <button class="btn btn-ghost btn-sm mcw-jump-btn" data-item-jump disabled>跳转</button>
          <button class="btn btn-ghost btn-sm" data-item-page="${itemView.page + 1}" ${
        itemView.page >= totalPages ? 'disabled' : ''
      }>下一页</button>
        </div>
      `;
      body.classList.remove('mcw-list-loading');
      bindIcons(body);
      body.querySelectorAll('[data-item]').forEach((button) => {
        button.addEventListener('click', () => openItem(button.dataset.item));
      });
      body.querySelectorAll('[data-item-page]').forEach((button) => {
        button.addEventListener('click', () => {
          itemView.page = Number(button.dataset.itemPage);
          loadItems({ keep: true });
        });
      });
      const itemJump = body.querySelector('[data-item-jump-input]');
      const itemJumpButton = body.querySelector('[data-item-jump]');
      const jumpItems = () => {
        if (!itemJump.value.trim()) return;
        const value = Math.trunc(Number(itemJump.value));
        if (!Number.isFinite(value)) return;
        itemView.page = Math.min(totalPages, Math.max(1, value));
        loadItems({ keep: true });
      };
      itemJumpButton.addEventListener('click', jumpItems);
      itemJump.addEventListener('input', () => {
        itemJumpButton.disabled = !itemJump.value.trim();
      });
      itemJump.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') jumpItems();
      });
    } catch (error) {
      body.classList.remove('mcw-list-loading');
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
    void overlay.offsetWidth;
    overlay.classList.add('is-open');
    const body = overlay.querySelector('.mcw-dialog-body');
    let closing = false;
    const close = () => {
      if (closing) return;
      closing = true;
      document.removeEventListener('keydown', onKey);
      overlay.classList.remove('is-open');
      overlay.classList.add('is-closing');
      setTimeout(() => overlay.remove(), 420);
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
            <div class="mcw-detail-name-row">
              <h3>${esc(item.nameZh || item.nameEn || id)}</h3>
              ${
                item.icon
                  ? `<a class="btn btn-ghost btn-sm" href="${assetBase}${esc(item.icon)}" download="${esc(
                      item.namespacedId.replace(':', '_')
                    )}.png">下载图标</a>`
                  : ''
              }
            </div>
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

  async function loadRecipes(options = {}) {
    const body = view.querySelector('[data-recipe-body]');
    if (!body) return;
    if (!meta?.published) {
      body.innerHTML = renderEmptyNotice();
      bindEmptyActions();
      return;
    }
    const keep = Boolean(options.keep);
    if (keep) {
      body.classList.add('mcw-list-loading');
    } else {
      body.innerHTML = '<div class="plugin-loading">正在读取配方…</div>';
    }
    try {
      const data = await api.get(`${base}/recipes`, {
        version,
        keyword: recipeView.keyword,
        page: recipeView.page,
        size: recipeView.size,
      });
      if (disposed || tab !== 'recipes') return;
      if (!data.records.length) {
        body.classList.remove('mcw-list-loading');
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
          <span class="mcw-pager-info">第 ${recipeView.page} / ${totalPages} 页<span class="mcw-pager-total"> · 共 ${data.total} 个配方</span></span>
          <label class="mcw-page-jump">跳至 <input type="number" min="1" max="${totalPages}" data-recipe-jump-input aria-label="跳转页码" inputmode="numeric" /> 页</label>
          <button class="btn btn-ghost btn-sm mcw-jump-btn" data-recipe-jump disabled>跳转</button>
          <button class="btn btn-ghost btn-sm" data-recipe-page="${recipeView.page + 1}" ${
        recipeView.page >= totalPages ? 'disabled' : ''
      }>下一页</button>
        </div>
      `;
      bindIcons(body);
      body.classList.remove('mcw-list-loading');
      bindRecipeHover(body);
      body.querySelectorAll('[data-recipe-page]').forEach((button) => {
        button.addEventListener('click', () => {
          recipeView.page = Number(button.dataset.recipePage);
          loadRecipes({ keep: true });
        });
      });
      const recipeJump = body.querySelector('[data-recipe-jump-input]');
      const recipeJumpButton = body.querySelector('[data-recipe-jump]');
      const jumpRecipes = () => {
        if (!recipeJump.value.trim()) return;
        const value = Math.trunc(Number(recipeJump.value));
        if (!Number.isFinite(value)) return;
        recipeView.page = Math.min(totalPages, Math.max(1, value));
        loadRecipes({ keep: true });
      };
      recipeJumpButton.addEventListener('click', jumpRecipes);
      recipeJump.addEventListener('input', () => {
        recipeJumpButton.disabled = !recipeJump.value.trim();
      });
      recipeJump.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') jumpRecipes();
      });
    } catch (error) {
      body.classList.remove('mcw-list-loading');
      body.innerHTML = `<div class="mcw-empty"><h3>配方读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function recipeCardHtml(recipe, { compact = false } = {}) {
    const cells = Array.isArray(recipe.gridDetail) ? recipe.gridDetail.slice(0, 9) : [];
    while (cells.length < 9) cells.push(null);
    const grid = cells
      .map((cell) =>
        cell
          ? `<span class="mcw-cell ${cell.tag ? 'is-tag' : ''}" data-hover-id="${esc(cell.id)}" data-hover-name="${esc(
              cell.nameZh || ''
            )}" data-hover-en="${esc(cell.nameEn || '')}" data-hover-kind="${esc(
              cell.kind || 'item'
            )}" data-hover-icon="${esc(cell.icon || '')}" data-hover-tag="${esc(cell.tag || '')}">${detailIconHtml(
              cell.id,
              cell.nameZh,
              cell.icon
            )}${cell.tag ? '<span class="mcw-tag-mark">#</span>' : ''}</span>`
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

  async function loadAdmin(options = {}) {
    if (!isAdmin) return;
    if (options.keep) view.classList.add('mcw-list-loading');
    try {
      const data = await api.get(`${base}/admin/state`, {
        keyword: adminView.keyword,
        type: adminView.type,
        status: adminView.status,
        page: adminView.page,
        size: adminView.size,
      });
      adminView.data = data;
      if (disposed || tab !== 'admin') return;
      paintAdmin();
    } catch (error) {
      view.innerHTML = `<div class="mcw-empty"><h3>后台数据读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    } finally {
      view.classList.remove('mcw-list-loading');
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
            <p>导入会把该版本的物品、配方与方块状态写入本地；发布后前台可切换查看，已发布版本可设为默认展示。</p>
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
          <span class="mcw-pager-info">第 ${adminView.page} / ${totalPages} 页<span class="mcw-pager-total"> · 共 ${data.total} ${
      data.grouped ? `组 · ${data.snapshotCount} 个快照` : '个版本'
    }</span></span>
          <label class="mcw-page-jump">跳至 <input type="number" min="1" max="${totalPages}" data-admin-jump-input aria-label="跳转页码" inputmode="numeric" /> 页</label>
          <button class="btn btn-ghost btn-sm mcw-jump-btn" data-admin-jump disabled>跳转</button>
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
        loadAdmin().then(() => animateElement(view.querySelector('.mcw-table-wrap')));
      
      });
    });
    const adminJump = view.querySelector('[data-admin-jump-input]');
    const adminJumpButton = view.querySelector('[data-admin-jump]');
    const jumpAdmin = () => {
      if (!adminJump.value.trim()) return;
      const value = Math.trunc(Number(adminJump.value));
      if (!Number.isFinite(value)) return;
      adminView.page = Math.min(totalPages, Math.max(1, value));
      loadAdmin().then(() => animateElement(view.querySelector('.mcw-table-wrap')));
    };
    adminJumpButton.addEventListener('click', jumpAdmin);
    adminJump.addEventListener('input', () => {
      adminJumpButton.disabled = !adminJump.value.trim();
    });
    adminJump.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') jumpAdmin();
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
          ? `<button class="btn btn-ghost btn-sm" data-version-action="unpublish" data-version="${esc(row.id)}">取消发布</button>${
              row.current
                ? ''
                : `<button class="btn btn-ghost btn-sm" data-version-action="default" data-version="${esc(row.id)}">设为默认</button>`
            }`
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
      if (action === 'default') {
        await api.post(`${base}/admin/default`, { version: versionId });
        toast(`${versionId} 已设为默认展示版本`, 'success');
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

  // ---------------- 版本比对 ----------------

  async function renderCompare() {
    view.innerHTML = '<div class="plugin-loading">正在读取已发布版本…</div>';
    let versions = [];
    try {
      const data = await api.get(`${base}/versions`);
      versions = data.items || [];
    } catch (error) {
      view.innerHTML = `<div class="mcw-empty"><h3>版本读取失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
      return;
    }
    compareView.versions = versions;
    if (versions.length < 2) {
      view.innerHTML = `
        <div class="mcw-empty">
          <h3>至少需要两个已发布版本</h3>
          <p>请先在「版本管理」中发布两个 Minecraft 版本，再回到这里进行物品与配方比对。</p>
        </div>`;
      return;
    }
    if (!versions.some((row) => row.id === compareView.from)) compareView.from = '';
    if (!versions.some((row) => row.id === compareView.to)) compareView.to = '';
    paintCompareShell();
    if (compareView.data) paintCompareResults();
  }

  function compareOptions(selected) {
    return compareView.versions
      .map((row) => `<option value="${esc(row.id)}" ${row.id === selected ? 'selected' : ''}>${esc(row.id)}</option>`)
      .join('');
  }

  function versionsNewerThan(fromId) {
    const from = compareView.versions.find((row) => row.id === fromId);
    if (!from) return [];
    const time = String(from.releaseTime || '');
    return compareView.versions.filter((row) => String(row.releaseTime || '') > time);
  }

  function paintCompareShell() {
    const newer = versionsNewerThan(compareView.from);
    const toValue = newer.some((row) => row.id === compareView.to) ? compareView.to : '';
    compareView.to = toValue;
    view.innerHTML = `
      <div class="mcw-toolbar">
        <div class="filter-chips"><span class="mcw-muted">先选择前项版本，后项只能选择比它更新的版本</span></div>
        <div class="mcw-toolbar-right">
          <label class="select-box mcw-compare-select"><select data-compare-from>
            <option value="">选择前项版本</option>
            ${compareOptions(compareView.from)}
          </select></label>
          <span class="mcw-muted">→</span>
          <label class="select-box mcw-compare-select"><select data-compare-to ${compareView.from ? '' : 'disabled'}>
            <option value="">${compareView.from ? (newer.length ? '选择后项版本' : '前项已最新') : '先选前项版本'}</option>
            ${newer.map((row) => `<option value="${esc(row.id)}" ${row.id === toValue ? 'selected' : ''}>${esc(row.id)}</option>`).join('')}
          </select></label>
          <button class="btn btn-primary" data-compare-run ${
            compareView.from && toValue ? '' : 'disabled'
          }><span class="mcw-hide-mobile">开始</span>比对</button>
        </div>
      </div>
      <div data-compare-results>${compareView.data ? '' : '<div class="mcw-empty"><h3>先选前项，再选后项</h3><p>后项只能选择比前项更新的已导入版本。</p></div>'}</div>
    `;
    view.querySelector('[data-compare-from]').addEventListener('change', (event) => {
      compareView.from = event.target.value;
      compareView.to = '';
      compareView.data = null;
      paintCompareShell();
    });
    const toSelect = view.querySelector('[data-compare-to]');
    const runButton = view.querySelector('[data-compare-run]');
    toSelect.addEventListener('change', (event) => {
      compareView.to = event.target.value;
      compareView.data = null;
      runButton.disabled = !compareView.to;
    });
    view.querySelector('[data-compare-run]').addEventListener('click', runCompare);
  }

  async function runCompare() {
    if (!compareView.from) {
      toast('请先选择前项版本', 'error');
      return;
    }
    if (!compareView.to) {
      toast('请选择比前项更新的后项版本', 'error');
      return;
    }
    const host = view.querySelector('[data-compare-results]');
    host.innerHTML = '<div class="plugin-loading">正在比对版本数据…</div>';
    try {
      const data = await api.get(`${base}/compare`, { from: compareView.from, to: compareView.to });
      compareView.data = data;
      compareView.page = 1;
      paintCompareResults();
    } catch (error) {
      host.innerHTML = `<div class="mcw-empty"><h3>比对失败</h3><p>${esc(error.message || '未知错误')}</p></div>`;
    }
  }

  function paintCompareResults() {
    const host = view.querySelector('[data-compare-results]');
    const data = compareView.data;
    if (!host || !data) return;
    const counts = data.counts || {};
    const fromStats = data.fromStats || {};
    const toStats = data.toStats || {};
    host.innerHTML = `
      <div class="mcw-compare-versions">
        <article class="mcw-version-card">
          <span>对比起点</span>
          <strong>${esc(data.from)}</strong>
          <small>物品 ${fromStats.items || 0} · 配方 ${fromStats.recipes || 0}</small>
        </article>
        <span class="mcw-compare-arrow">→</span>
        <article class="mcw-version-card is-target">
          <span>对比目标</span>
          <strong>${esc(data.to)}</strong>
          <small>物品 ${toStats.items || 0} · 配方 ${toStats.recipes || 0}</small>
        </article>
      </div>
      <div class="mcw-compare-summary">${compareSummaryHtml()}</div>
      <div class="mcw-toolbar">
        <div class="filter-chips">
          ${[
            ['item', '物品'],
            ['block', '方块'],
            ['entity', '生物'],
            ['recipes', '配方'],
          ]
            .map(
              ([key, label]) =>
                `<button class="chip ${compareCategoryActive(key) ? 'is-active' : ''}" data-compare-cat="${key}">${label}</button>`
            )
            .join('')}
          <span class="chip-divider"></span>
          ${[
            ['added', '新增'],
            ['modified', '修改'],
            ['removed', '删除'],
          ]
            .map(
              ([key, label]) =>
                `<button class="chip ${compareView.filter === key ? 'is-active' : ''}" data-compare-filter="${key}">${label}</button>`
            )
            .join('')}
        </div>
        <div class="mcw-toolbar-right">
          <label class="search-box"><input type="search" data-compare-search placeholder="搜索名称或 id" value="${esc(
            compareView.keyword
          )}" /></label>
        </div>
      </div>
      <div data-compare-list></div>
    `;
    const applyTabChange = () => {
      compareView.page = 1;
      syncCompareTabs(host);
      paintCompareList();
    };
    host.querySelectorAll('[data-compare-cat]').forEach((button) => {
      button.addEventListener('click', () => {
        const key = button.dataset.compareCat;
        compareView.kind = key === 'recipes' ? 'recipes' : 'items';
        compareView.itemKind = key === 'recipes' ? '' : key;
        applyTabChange();
      });
    });
    host.querySelectorAll('[data-compare-filter]').forEach((button) => {
      button.addEventListener('click', () => {
        compareView.filter = button.dataset.compareFilter;
        applyTabChange();
      });
    });
    host.querySelectorAll('[data-summary-kind]').forEach((button) => {
      button.addEventListener('click', () => {
        compareView.kind = button.dataset.summaryKind;
        compareView.itemKind = button.dataset.summaryItemKind || '';
        compareView.filter = button.dataset.summaryFilter;
        applyTabChange();
      });
    });
    const search = host.querySelector('[data-compare-search]');
    search.addEventListener('input', () => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        compareView.keyword = search.value.trim();
        compareView.page = 1;
        paintCompareList();
      }, 250);
    });
    paintCompareList();
  }

  function syncCompareTabs(host) {
    host.querySelectorAll('[data-compare-cat]').forEach((button) => {
      button.classList.toggle('is-active', compareCategoryActive(button.dataset.compareCat));
    });
    host.querySelectorAll('[data-compare-filter]').forEach((button) => {
      button.classList.toggle('is-active', compareView.filter === button.dataset.compareFilter);
    });
    host.querySelectorAll('[data-summary-kind]').forEach((button) => {
      button.classList.toggle(
        'is-active',
        compareView.kind === button.dataset.summaryKind &&
          (compareView.itemKind || '') === (button.dataset.summaryItemKind || '') &&
          compareView.filter === button.dataset.summaryFilter
      );
    });
  }

  function compareSummaryHtml() {
    const data = compareView.data || {};
    const counts = data.counts || {};
    const kindCounts = data.itemKindCounts || {};
    const groups = [
      ['item', '物品', 'items'],
      ['block', '方块', 'items'],
      ['entity', '生物', 'items'],
    ].map(([kind, label, category]) => summaryGroupHtml(label, category, kind, kindCounts[kind] || {}))
      .join('');
    return groups + summaryGroupHtml('配方', 'recipes', '', {
      added: counts.recipesAdded,
      modified: counts.recipesModified,
      removed: counts.recipesRemoved,
    });
  }

  function summaryGroupHtml(label, kind, itemKind, counts) {
    return `<span class="mcw-compare-group"><span>${label}</span>${[
      ['added', '+', counts.added, 'is-passed'],
      ['modified', '~', counts.modified, 'is-pending'],
      ['removed', '-', counts.removed, 'is-failed'],
    ]
      .map(
        ([filter, sign, count, cls]) =>
          `<button class="mcw-badge ${cls} ${
            compareView.kind === kind && (compareView.itemKind || '') === (itemKind || '') &&
            compareView.filter === filter
              ? 'is-active'
              : ''
          }" data-summary-kind="${kind}" data-summary-item-kind="${itemKind}" data-summary-filter="${filter}">${sign}${
            count || 0
          }</button>`
      )
      .join('')}</span>`;
  }

  function compareCategoryActive(key) {
    if (key === 'recipes') return compareView.kind === 'recipes';
    return compareView.kind === 'items' && compareView.itemKind === key;
  }

  function paintCompareList({ animate = true } = {}) {
    const host = view.querySelector('[data-compare-list]');
    const data = compareView.data;
    if (!host || !data) return;
    if (compareView.kind === 'recipes') {
      compareView.size = window.matchMedia?.('(min-width: 861px)').matches ? 12 : 8;
    } else {
      compareView.size = narrowCards() ? 12 : Math.max(1, cardColumns()) * 5;
    }
    const block = data[compareView.kind] || {};
    let rows = block[compareView.filter] || [];
    if (compareView.kind === 'items' && compareView.itemKind) {
      rows = rows.filter((row) => (row.to?.kind || row.from?.kind) === compareView.itemKind);
    }
    const keyword = compareView.keyword.trim().toLowerCase();
    if (keyword) {
      rows = rows.filter((row) => {
        const haystack =
          compareView.kind === 'items'
            ? `${row.namespacedId} ${row.from?.nameZh} ${row.to?.nameZh} ${row.from?.nameEn} ${row.to?.nameEn}`
            : `${row.recipeId} ${row.resultNameZh} ${row.type}`;
        return haystack.toLowerCase().includes(keyword);
      });
    }
    if (!rows.length) {
      host.innerHTML = '<p class="mcw-muted" style="padding:20px 0">当前分类没有差异。</p>';
      if (animate) animateElement(host, 'mcw-page-enter');
      return;
    }
    const totalPages = Math.max(1, Math.ceil(rows.length / compareView.size));
    compareView.page = Math.min(compareView.page, totalPages);
    const start = (compareView.page - 1) * compareView.size;
    const pageRows = rows.slice(start, start + compareView.size);
    host.innerHTML = `<div class="mcw-diff-list ${
      compareView.kind === 'items' ? 'is-items' : 'is-recipes'
    }">${pageRows.map((row) => compareCardHtml(row)).join('')}</div>
      <div class="mcw-pager">
        <button class="btn btn-ghost btn-sm" data-compare-page="${compareView.page - 1}" ${
      compareView.page <= 1 ? 'disabled' : ''
    }>上一页</button>
        <span class="mcw-pager-info">第 ${compareView.page} / ${totalPages} 页<span class="mcw-pager-total"> · 共 ${rows.length} 项</span></span>
        <button class="btn btn-ghost btn-sm" data-compare-page="${compareView.page + 1}" ${
      compareView.page >= totalPages ? 'disabled' : ''
    }>下一页</button>
      </div>`;
    bindIcons(host);
    bindRecipeHover(host);
    if (animate) animateElement(host.querySelector('.mcw-diff-list') || host, 'mcw-page-enter');
    host.querySelectorAll('[data-compare-page]').forEach((button) => {
      button.addEventListener('click', () => {
        compareView.page = Number(button.dataset.comparePage);
        paintCompareList();
      });
    });
  }

  function compareCardHtml(row) {
    const data = compareView.data;
    const isItem = compareView.kind === 'items';
    const id = isItem ? row.namespacedId : row.recipeId;
    const label = { added: '新增', modified: '修改', removed: '删除' }[compareView.filter] || '';
    const badgeClass = { added: 'is-passed', modified: 'is-pending', removed: 'is-failed' }[compareView.filter] || '';
    const hasFrom = row.from && Object.keys(row.from).length > 0;
    const hasTo = row.to && Object.keys(row.to).length > 0;
    const fromContent = isItem ? itemCompareSide(row.from, row) : recipeCompareSide(row.from, data.from);
    const toContent = isItem ? itemCompareSide(row.to, row) : recipeCompareSide(row.to, data.to);
    const fromBlock = `<div class="mcw-diff-side ${hasFrom ? 'is-present' : ''}">
        <span class="mcw-diff-side-label">${esc(data.from)}</span>
        ${fromContent}
      </div>`;
    const toBlock = `<div class="mcw-diff-side is-target ${hasTo ? 'is-present' : ''}">
        <span class="mcw-diff-side-label">${esc(data.to)}</span>
        ${toContent}
      </div>`;
    const body =
      hasFrom && hasTo
        ? `<div class="mcw-diff-body">${fromBlock}<span class="mcw-diff-arrow">→</span>${toBlock}</div>`
        : `<div class="mcw-diff-body is-single">${hasTo ? toBlock : fromBlock}</div>`;
    return `
      <article class="mcw-diff-card">
        <div class="mcw-diff-card-head">
          <span class="mcw-badge ${badgeClass}">${label}</span>
          <code>${esc(id)}</code>
        </div>
        ${body}
      </article>`;
  }

  function itemCompareSide(item, row) {
    if (!item || Object.keys(item).length === 0) return '<span class="mcw-diff-side-empty">该版本中不存在</span>';
    return `
      <span class="mcw-card-badge mcw-diff-side-badge">${esc(kindLabel(item.kind || 'item'))}</span>
      <div class="mcw-diff-side-head">
        ${iconHtml({ namespacedId: item.namespacedId || row.namespacedId, nameZh: item.nameZh, icon: item.icon })}
        <strong>${esc(item.nameZh || item.nameEn || row.namespacedId)}</strong>
      </div>
      <small>${esc(item.nameEn || '')}</small>
      <small>${esc(row.namespacedId)}</small>`;
  }

  function recipeCompareSide(side, sideVersion) {
    if (!side || !side.resultId) return '<span class="mcw-diff-side-empty">该版本中不存在</span>';
    const cells = Array.isArray(side.gridDetail) ? side.gridDetail.slice(0, 9) : [];
    while (cells.length < 9) cells.push(null);
    const grid = cells
      .map((cell) =>
        cell
          ? `<span class="mcw-cell ${cell.tag ? 'is-tag' : ''}" data-hover-id="${esc(cell.id)}" data-hover-name="${esc(
              cell.nameZh || ''
            )}" data-hover-en="${esc(cell.nameEn || '')}" data-hover-kind="${esc(
              cell.kind || 'item'
            )}" data-hover-icon="${esc(cell.icon || '')}" data-hover-tag="${esc(
              cell.tag || ''
            )}" data-hover-version="${esc(sideVersion)}">${detailIconHtml(
              cell.id,
              cell.nameZh,
              cell.icon
            )}${cell.tag ? '<span class="mcw-tag-mark">#</span>' : ''}</span>`
          : '<span class="mcw-cell is-empty"></span>'
      )
      .join('');
    return `
      <div class="mcw-compare-recipe">
        <div class="mcw-grid3">${grid}</div>
        <div class="mcw-compare-recipe-info">
          <div class="mcw-diff-side-head">
            ${iconHtml({ namespacedId: side.resultId, nameZh: side.resultNameZh, icon: side.resultIcon })}
            <strong>${esc(side.resultNameZh || side.resultNameEn || side.resultId)}</strong>
          </div>
          <small>${esc(side.resultId)} × ${Number(side.resultCount) || 1}</small>
          <small>${esc(String(side.type || '').replace('minecraft:', ''))} · 原料 ${(
      side.ingredients || []
    ).length} 项</small>
        </div>
      </div>`;
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
      loadItems({ keep: true });
    }
  });

  view.addEventListener('change', (event) => {
    if (event.target.matches('[data-item-version]')) {
      version = event.target.value;
      itemView.page = 1;
      loadItems({ keep: true });
      loadMeta().then(() => {
        const select = view.querySelector('[data-item-version]');
        if (select) select.innerHTML = versionOptions(version);
        refreshKindChips();
      });
    } else if (event.target.matches('[data-recipe-version]')) {
      version = event.target.value;
      recipeView.page = 1;
      loadRecipes({ keep: true });
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
        loadItems({ keep: true });
      }, 320);
    } else if (event.target.matches('[data-recipe-search]')) {
      const input = event.target;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        recipeView.keyword = input.value.trim();
        recipeView.page = 1;
        loadRecipes({ keep: true });
      }, 320);
    }
  });

  async function switchTab(next) {
    if (next === 'admin' && !isAdmin) return;
    if (next === tab) return;
    stopPolling();
    await runViewTransition(async () => {
      tab = next;
      try {
        await loadMeta();
      } catch (error) {
        view.innerHTML = `<div class="mcw-empty"><h3>百科数据读取失败</h3><p>${esc(
          error.message || '未知错误'
        )}</p></div>`;
        return;
      }
      await renderView();
    });
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
      window.removeEventListener('scroll', hideHoverCard);
      hideHoverCard();
      if (hoverCardEl) hoverCardEl.remove();
      hoverCardEl = null;
    };
  }
  let resizeTimer = null;
  const onResize = () => {
    const next = compactLayout();
    const previousSize = itemView.size;
    const layoutChanged = next !== lastCompact;
    lastCompact = next;
    applyPageSizes();
    if (!layoutChanged && previousSize === itemView.size) return;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      if (disposed) return;
      if (tab === 'items') {
        itemView.page = 1;
        loadItems({ keep: true });
      } else if (tab === 'recipes') {
        recipeView.page = 1;
        loadRecipes({ keep: true });
      } else if (tab === 'admin') {
        adminView.page = 1;
        loadAdmin({ keep: true });
      } else if (tab === 'compare' && compareView.data) {
        compareView.page = 1;
        paintCompareList({ animate: false });
      }
    }, 250);
  };
  window.addEventListener('resize', onResize);
  window.addEventListener('scroll', hideHoverCard, { passive: true });

  renderView();

  return () => {
    disposed = true;
    window.removeEventListener('resize', onResize);
    window.removeEventListener('scroll', hideHoverCard);
    clearTimeout(resizeTimer);
    stopPolling();
    clearTimeout(searchTimer);
    hideHoverCard();
    if (hoverCardEl) hoverCardEl.remove();
    hoverCardEl = null;
  };
}
