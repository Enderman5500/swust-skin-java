import { api, buildQuery } from '../api.js';
import { icon } from '../icons.js';
import { drawCapePreview, drawSkinCard } from '../skin2d.js';
import * as ui from '../ui.js';

function validateDimensions(width, height, type) {
  if (type === 'skin') {
    if ([64, 128, 256].includes(width) && (height === width || height * 2 === width)) return null;
    return '皮肤尺寸应为 64×64、64×32（或同比例高清尺寸）';
  }
  if (width === 22 && height === 17) return null;
  if ([64, 128, 256].includes(width) && height * 2 === width) return null;
  return '披风尺寸应为 64×32（或同比例高清尺寸）';
}

export async function render(root, ctx) {
  let previewUrl = null;

  root.innerHTML = `
    <section class="page-head">
      <div>
        <p class="eyebrow">UPLOAD TEXTURE</p>
        <h1>上传材质</h1>
        <p>支持 8 位 PNG 皮肤与披风，最大 4 MB。上传后即可在 3D 中预览并装备给角色。</p>
      </div>
      <div class="page-head-actions">
        <a class="btn btn-ghost" href="#/library">${icon('library')}去皮肤库</a>
      </div>
    </section>
    <section class="upload-layout">
      <div class="upload-main">
        <div class="dropzone" data-dropzone tabindex="0" role="button" aria-label="选择 PNG 文件">
          <input type="file" accept="image/png,.png" hidden data-file />
          <div class="dropzone-inner" data-dropzone-inner>
            <span class="dropzone-icon">${icon('upload')}</span>
            <h2>拖入一张 PNG，或者点击选择</h2>
            <p>皮肤 64×64 / 64×32 · 披风 64×32 · 支持 128×128 与 256×256 高清</p>
            <span class="btn btn-primary">${icon('image')}选择 PNG 文件</span>
          </div>
        </div>
        <div class="upload-preview-meta" data-preview-meta hidden></div>
        <div class="upload-guidelines">
          <article>
            <span>${icon('layers')}</span>
            <div><strong>经典与纤细</strong><p>皮肤上传时可选择模型；画面中的 3D 预览会立即反映。</p></div>
          </article>
          <article>
            <span>${icon('shield')}</span>
            <div><strong>尺寸校验</strong><p>服务端会校验 PNG 结构与尺寸，不合法文件不会入库。</p></div>
          </article>
          <article>
            <span>${icon('heart')}</span>
            <div><strong>公开或私有</strong><p>公开材质会出现在皮肤库；私有材质只有你和管理员可见。</p></div>
          </article>
        </div>
      </div>
      <aside class="upload-form-card">
        <form class="form-stack" data-upload-form>
          <div class="upload-form-head">
            <p class="eyebrow">DETAILS</p>
            <h2>材质信息</h2>
          </div>
          <label class="field">
            <span>材质名称</span>
            <input type="text" name="name" maxlength="48" placeholder="例如 星河旅人" required />
          </label>
          <div class="field">
            <span>材质类型</span>
            <div class="segmented" data-type-segmented>
              <button type="button" class="is-active" data-type="skin">皮肤</button>
              <button type="button" data-type="cape">披风</button>
            </div>
          </div>
          <div class="field" data-model-field>
            <span>角色模型</span>
            <div class="segmented" data-model-segmented>
              <button type="button" class="is-active" data-model="classic">经典</button>
              <button type="button" data-model="slim">纤细</button>
            </div>
            <small class="field-hint">纤细模型为 3 像素手臂，只影响皮肤。</small>
          </div>
          <label class="switch-field">
            <span>
              <strong>公开到皮肤库</strong>
              <small>关闭后仅自己可见，仍可装备到角色。</small>
            </span>
            <input type="checkbox" name="isPublic" checked />
          </label>
          <div class="form-error" data-form-error hidden></div>
          <button class="btn btn-primary btn-block btn-lg" type="submit" data-submit disabled>
            ${icon('upload')}上传材质
          </button>
          <p class="field-hint upload-server-hint">${icon('shield')} 上传即表示你拥有该材质的分享权利。</p>
        </form>
      </aside>
    </section>
  `;

  const dropzone = root.querySelector('[data-dropzone]');
  const inner = root.querySelector('[data-dropzone-inner]');
  const fileInput = root.querySelector('[data-file]');
  const form = root.querySelector('[data-upload-form]');
  const errorBox = root.querySelector('[data-form-error]');
  const submitButton = root.querySelector('[data-submit]');
  const modelField = root.querySelector('[data-model-field]');
  const previewMeta = root.querySelector('[data-preview-meta]');
  const state = {
    file: null,
    type: 'skin',
    model: 'classic',
    width: 0,
    height: 0,
    previewNode: null,
  };

  function setError(message) {
    if (!message) {
      errorBox.hidden = true;
      errorBox.textContent = '';
      return;
    }
    errorBox.hidden = false;
    errorBox.textContent = message;
  }

  function refreshSubmit() {
    const dimensionError = state.file
      ? validateDimensions(state.width, state.height, state.type)
      : '请选择一张 PNG 材质';
    const nameOk = form.elements.name.value.trim().length > 0;
    setError(state.file && dimensionError ? dimensionError : '');
    submitButton.disabled = !state.file || Boolean(dimensionError) || !nameOk;
  }

  async function inspectFile(file) {
    if (!file) return;
    if (file.size > 4 * 1024 * 1024) {
      setError('文件超过 4 MB 限制');
      return;
    }
    const header = new Uint8Array(await file.slice(0, 8).arrayBuffer());
    const signature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
    if (!signature.every((value, index) => header[index] === value)) {
      setError('请选择 PNG 格式的图片');
      return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onload = async () => {
      state.file = file;
      state.width = image.naturalWidth;
      state.height = image.naturalHeight;
      if (image.naturalWidth === 22 && image.naturalHeight === 17) {
        state.type = 'cape';
        setSegmented('type', 'cape');
      } else if (image.naturalWidth === image.naturalHeight) {
        state.type = 'skin';
        setSegmented('type', 'skin');
      } else {
        state.type = 'cape';
        setSegmented('type', 'cape');
      }
      if (!form.elements.name.value.trim()) {
        form.elements.name.value = file.name.replace(/\.png$/i, '').slice(0, 48);
      }
      await renderPreview();
      refreshSubmit();
    };
    image.onerror = () => setError('无法读取这张 PNG，文件可能已损坏');
    image.src = previewUrl;
  }

  function setSegmented(group, value) {
    const selector = group === 'type' ? '[data-type-segmented] [data-type]' : '[data-model-segmented] [data-model]';
    root.querySelectorAll(selector).forEach((button) => {
      const key = group === 'type' ? button.dataset.type : button.dataset.model;
      button.classList.toggle('is-active', key === value);
    });
  }

  async function renderPreview() {
    if (!state.file || !previewUrl) {
      inner.innerHTML = `
        <span class="dropzone-icon">${icon('upload')}</span>
        <h2>拖入一张 PNG，或者点击选择</h2>
        <p>皮肤 64×64 / 64×32 · 披风 64×32 · 支持 128×128 与 256×256 高清</p>
        <span class="btn btn-primary">${icon('image')}选择 PNG 文件</span>
      `;
      previewMeta.hidden = true;
      dropzone.classList.remove('has-file');
      return;
    }
    inner.innerHTML = `
      <div class="upload-preview-stage">
        <canvas class="upload-preview-canvas ${state.type === 'cape' ? 'is-cape' : ''}" data-preview></canvas>
      </div>
      <div class="upload-preview-copy">
        <strong>${ui.escapeHtml(state.file.name)}</strong>
        <span>${state.width} × ${state.height} · ${(state.file.size / 1024).toFixed(1)} KB</span>
        <span class="text-link">点击或拖入另一张图片替换</span>
      </div>
    `;
    dropzone.classList.add('has-file');
    const canvas = inner.querySelector('[data-preview]');
    if (state.type === 'cape') await drawCapePreview(canvas, previewUrl);
    else await drawSkinCard(canvas, { url: previewUrl, model: state.model });
    modelField.hidden = state.type !== 'skin';
    previewMeta.hidden = false;
    previewMeta.innerHTML = `
      <span>${icon('check')}已识别 ${state.type === 'cape' ? '披风' : '皮肤'}材质</span>
      <span>${state.width} × ${state.height}</span>
      <button class="link-button" data-action="clear-file">移除文件</button>
    `;
  }

  dropzone.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="clear-file"]')) return;
    fileInput.click();
  });
  dropzone.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      fileInput.click();
    }
  });
  fileInput.addEventListener('change', () => inspectFile(fileInput.files[0]));

  for (const type of ['dragenter', 'dragover']) {
    dropzone.addEventListener(type, (event) => {
      event.preventDefault();
      dropzone.classList.add('is-dragging');
    });
  }
  for (const type of ['dragleave', 'drop']) {
    dropzone.addEventListener(type, (event) => {
      event.preventDefault();
      dropzone.classList.remove('is-dragging');
    });
  }
  dropzone.addEventListener('drop', (event) => {
    const file = event.dataTransfer?.files?.[0];
    if (file) inspectFile(file);
  });

  root.querySelector('[data-type-segmented]').addEventListener('click', async (event) => {
    const button = event.target.closest('[data-type]');
    if (!button) return;
    state.type = button.dataset.type;
    setSegmented('type', state.type);
    modelField.hidden = state.type !== 'skin';
    if (state.file) await renderPreview();
    refreshSubmit();
  });

  root.querySelector('[data-model-segmented]').addEventListener('click', async (event) => {
    const button = event.target.closest('[data-model]');
    if (!button) return;
    state.model = button.dataset.model;
    setSegmented('model', state.model);
    if (state.file) await renderPreview();
  });

  form.elements.name.addEventListener('input', refreshSubmit);

  previewMeta.addEventListener('click', (event) => {
    if (!event.target.closest('[data-action="clear-file"]')) return;
    state.file = null;
    state.width = 0;
    state.height = 0;
    fileInput.value = '';
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
    renderPreview();
    refreshSubmit();
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!state.file) {
      setError('请选择一张 PNG 材质');
      return;
    }
    const name = form.elements.name.value.trim();
    if (!name) {
      setError('请填写材质名称');
      return;
    }
    const dimensionError = validateDimensions(state.width, state.height, state.type);
    if (dimensionError) {
      setError(dimensionError);
      return;
    }
    setError('');
    ui.setButtonLoading(submitButton, true, '上传中…');
    try {
      const query = buildQuery({
        type: state.type,
        name,
        model: state.type === 'skin' ? state.model : '',
        public: form.elements.isPublic.checked ? '1' : '0',
      });
      const data = await api.upload(`/api/textures${query}`, state.file, 'image/png');
      ui.toast(
        data.created ? '材质上传成功，正在打开 3D 预览' : '材质已存在，已为你加入衣柜',
        'success'
      );
      ctx.refreshSite();
      ctx.navigate(`/library?texture=${data.texture.id}`);
    } catch (error) {
      setError(error.message);
      ui.setButtonLoading(submitButton, false);
    }
  });

  return () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
  };
}
