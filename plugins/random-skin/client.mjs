export async function mount(root, ctx) {
  const { api, toast, escapeHtml, navigate, skinPreview } = ctx;
  let disposed = false;

  root.innerHTML = `
    <section class="plugin-shell">
      <header class="plugin-head">
        <div>
          <p class="eyebrow">PLUGIN · RANDOM SKIN</p>
          <h1>随机皮肤</h1>
          <p class="plugin-lead">从公开皮肤库随机抽取作品。点击卡片查看材质详情，或换一批继续探索。</p>
        </div>
        <div class="plugin-head-actions">
          <button class="btn btn-primary" data-action="refresh">换一批</button>
        </div>
      </header>
      <div class="skin-grid plugin-skin-grid" data-grid>
        <div class="plugin-loading">正在从星图里抽取皮肤…</div>
      </div>
      <p class="plugin-footnote">由 random-skin 插件提供 · 每次刷新都会从公开皮肤库重新抽样</p>
    </section>
  `;

  const grid = root.querySelector('[data-grid]');

  async function load() {
    grid.innerHTML = '<div class="plugin-loading">正在从星图里抽取皮肤…</div>';
    try {
      const data = await api.get('/textures?sort=random&type=skin&perPage=9');
      if (disposed) return;
      if (!data.items.length) {
        grid.innerHTML = '<div class="empty-state">皮肤库还是空的，去上传第一张作品吧。</div>';
        return;
      }
      grid.innerHTML = data.items
        .map(
          (texture) => `
          <article class="skin-card" data-open="${texture.id}" tabindex="0" role="button">
            <div class="skin-card-preview">
              <canvas class="skin-canvas" data-skin="${escapeHtml(texture.url)}"></canvas>
            </div>
            <div class="skin-card-body">
              <h3>${escapeHtml(texture.name)}</h3>
              <p>${escapeHtml(texture.uploader?.username || '未知社员')} · ${
                texture.model === 'slim' ? '纤细' : '经典'
              }</p>
            </div>
          </article>
        `
        )
        .join('');

      const canvases = [...grid.querySelectorAll('canvas[data-skin]')];
      await Promise.all(
        canvases.map((canvas) =>
          skinPreview.drawSkinCard(canvas, {
            url: canvas.dataset.skin,
            model: data.items.find((item) => item.url === canvas.dataset.skin)?.model,
          })
        )
      );
    } catch (error) {
      if (!disposed) {
        toast(error.message || '随机皮肤加载失败', 'error');
        grid.innerHTML = `<div class="empty-state">加载失败：${escapeHtml(
          error.message || '未知错误'
        )}</div>`;
      }
    }
  }

  root.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="refresh"]')) {
      load();
      return;
    }
    const card = event.target.closest('[data-open]');
    if (card) navigate(`#/library?texture=${card.dataset.open}`);
  });

  root.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const card = event.target.closest('[data-open]');
    if (!card) return;
    event.preventDefault();
    navigate(`#/library?texture=${card.dataset.open}`);
  });

  await load();

  return () => {
    disposed = true;
  };
}
