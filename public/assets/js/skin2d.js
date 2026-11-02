import { drawSkin3DCard } from './skin-thumb.js';

const imageCache = new Map();

export function loadTexture(url) {
  if (!url) return Promise.reject(new Error('缺少材质地址'));
  if (!imageCache.has(url)) {
    imageCache.set(
      url,
      new Promise((resolve, reject) => {
        const image = new Image();
        image.decoding = 'async';
        image.onload = () => resolve(image);
        image.onerror = () => reject(new Error('材质加载失败'));
        image.src = url;
      })
    );
  }
  return imageCache.get(url);
}

const SKIN = {
  head: { x: 8, y: 8, w: 8, h: 8, overlay: [40, 8] },
  body: { x: 20, y: 20, w: 8, h: 12, overlay: [20, 36] },
  armRight: { x: 44, y: 20, w: 4, h: 12, overlay: [44, 36] },
  armLeft: { x: 36, y: 52, w: 4, h: 12, overlay: [52, 52] },
  legRight: { x: 4, y: 20, w: 4, h: 12, overlay: [4, 36] },
  legLeft: { x: 20, y: 52, w: 4, h: 12, overlay: [4, 52] },
};

const SLIM = {
  armRight: { x: 44, y: 20, w: 3, h: 12, overlay: [44, 36] },
  armLeft: { x: 36, y: 52, w: 3, h: 12, overlay: [52, 52] },
};

function drawPart(ctx, image, part, dx, dy, unit, scale, mirror = false) {
  const sx = part.x * scale;
  const sy = part.y * scale;
  const sw = part.w * scale;
  const sh = part.h * scale;
  const dw = part.w * unit;
  const dh = part.h * unit;
  if (mirror) {
    ctx.save();
    ctx.translate(dx + dw, dy);
    ctx.scale(-1, 1);
    ctx.drawImage(image, sx, sy, sw, sh, 0, 0, dw, dh);
    ctx.restore();
  } else {
    ctx.drawImage(image, sx, sy, sw, sh, dx, dy, dw, dh);
  }
}

function paintPlaceholder(ctx, unit) {
  const rect = (x, y, w, h, color) => {
    ctx.fillStyle = color;
    ctx.fillRect(x * unit, y * unit, w * unit, h * unit);
  };
  rect(4, 0, 8, 8, '#e5bd95');
  rect(4, 0, 8, 2, '#7b92a6');
  rect(6, 3, 1, 1, '#1f2d3d');
  rect(9, 3, 1, 1, '#1f2d3d');
  rect(4, 8, 8, 12, '#7b92a6');
  rect(0, 8, 4, 8, '#7b92a6');
  rect(12, 8, 4, 8, '#7b92a6');
  rect(0, 16, 4, 4, '#e5bd95');
  rect(12, 16, 4, 4, '#e5bd95');
  rect(4, 20, 4, 12, '#394754');
  rect(8, 20, 4, 12, '#394754');
  rect(4, 30, 4, 2, '#222a32');
  rect(8, 30, 4, 2, '#222a32');
}

export async function drawSkinCard(canvas, texture, { maxWidth = 220 } = {}) {
  const width = Math.max(80, Math.min(maxWidth, canvas.clientWidth || 160));
  const unit = Math.max(2, Math.round(width / 16));
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  canvas.width = 16 * unit * dpr;
  canvas.height = 32 * unit * dpr;
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.imageSmoothingEnabled = false;
  ctx.clearRect(0, 0, 16 * unit, 32 * unit);

  if (!texture || texture.type === 'cape') {
    if (texture?.url) await drawCapePreview(canvas, texture.url, { maxWidth });
    else paintPlaceholder(ctx, unit);
    return;
  }

  let image;
  try {
    image = await loadTexture(texture.url);
  } catch {
    paintPlaceholder(ctx, unit);
    return;
  }

  const scale = image.width / 64;
  const slim = texture.model === 'slim';
  const legacy = image.height === 32;
  const arms = slim ? SLIM : SKIN;
  const armOffset = slim ? 1 : 0;
  const armWidth = slim ? 3 : 4;

  // 基础层
  drawPart(ctx, image, SKIN.head, 4 * unit, 0, unit, scale);
  drawPart(ctx, image, SKIN.body, 4 * unit, 8 * unit, unit, scale);
  drawPart(
    ctx,
    image,
    arms.armRight,
    armOffset * unit,
    8 * unit,
    unit,
    scale
  );
  drawPart(
    ctx,
    image,
    legacy ? SKIN.armRight : arms.armLeft,
    (12 + (4 - armWidth - armOffset)) * unit,
    8 * unit,
    unit,
    scale,
    legacy
  );
  drawPart(ctx, image, SKIN.legRight, 4 * unit, 20 * unit, unit, scale);
  drawPart(
    ctx,
    image,
    legacy ? SKIN.legRight : SKIN.legLeft,
    8 * unit,
    20 * unit,
    unit,
    scale,
    legacy
  );

  // 叠层：与 3D 正面使用相同的前脸贴图和透明度，不额外加阴影。
  if (!legacy) {
    ctx.save();
    drawPart(ctx, image, { x: 40, y: 8, w: 8, h: 8 }, 4 * unit, 0, unit, scale);
    drawPart(ctx, image, { x: 20, y: 36, w: 8, h: 12 }, 4 * unit, 8 * unit, unit, scale);
    drawPart(
      ctx,
      image,
      { x: 44, y: 36, w: armWidth, h: 12 },
      armOffset * unit,
      8 * unit,
      unit,
      scale
    );
    drawPart(
      ctx,
      image,
      { x: 52, y: 52, w: armWidth, h: 12 },
      (12 + (4 - armWidth - armOffset)) * unit,
      8 * unit,
      unit,
      scale
    );
    drawPart(ctx, image, { x: 4, y: 36, w: 4, h: 12 }, 4 * unit, 20 * unit, unit, scale);
    drawPart(ctx, image, { x: 4, y: 52, w: 4, h: 12 }, 8 * unit, 20 * unit, unit, scale);
    ctx.restore();
  }

  // 先用 2D 立即显示，再异步替换为同一 skinview3d 渲染的正面缩略图。
  drawSkin3DCard(canvas, texture, { maxWidth }).catch(() => {});
}

export async function drawCapePreview(canvas, url, { maxWidth = 220 } = {}) {
  const width = Math.max(60, Math.min(maxWidth, canvas.clientWidth || 160));
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  let image;
  try {
    image = await loadTexture(url);
  } catch {
    return;
  }
  const modern = image.width >= 64;
  const sourceW = modern ? 10 : image.width;
  const sourceH = modern ? 16 : image.height;
  const unit = Math.max(2, Math.round(width / sourceW));
  canvas.width = sourceW * unit * dpr;
  canvas.height = sourceH * unit * dpr;
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.imageSmoothingEnabled = false;
  ctx.clearRect(0, 0, sourceW * unit, sourceH * unit);
  const sx = modern ? image.width / 64 : 0;
  ctx.drawImage(
    image,
    (modern ? 1 : 0) * sx,
    (modern ? 1 : 0) * sx,
    sourceW * sx,
    sourceH * sx,
    0,
    0,
    sourceW * unit,
    sourceH * unit
  );
}

export function placeholderSkinCanvas() {
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 64;
  const ctx = canvas.getContext('2d');
  const unit = 1;
  paintPlaceholder(ctx, unit);
  // 补全 3D 需要的后脑、侧面与腿部背面
  const fill = (x, y, w, h, color) => {
    ctx.fillStyle = color;
    ctx.fillRect(x, y, w, h);
  };
  fill(0, 8, 8, 8, '#e5bd95');
  fill(16, 8, 8, 8, '#e5bd95');
  fill(24, 8, 8, 8, '#e5bd95');
  fill(0, 8, 8, 2, '#7b92a6');
  fill(16, 8, 8, 2, '#7b92a6');
  fill(24, 8, 8, 5, '#7b92a6');
  fill(8, 0, 8, 8, '#7b92a6');
  fill(16, 0, 8, 8, '#e5bd95');
  fill(16, 20, 4, 12, '#7b92a6');
  fill(28, 20, 4, 12, '#7b92a6');
  fill(32, 20, 8, 12, '#7b92a6');
  fill(20, 16, 8, 4, '#7b92a6');
  fill(28, 16, 8, 4, '#394754');
  fill(0, 20, 4, 12, '#7b92a6');
  fill(8, 20, 4, 12, '#7b92a6');
  fill(12, 20, 4, 12, '#7b92a6');
  fill(4, 16, 4, 4, '#7b92a6');
  fill(8, 16, 4, 4, '#222a32');
  fill(16, 52, 4, 12, '#394754');
  fill(24, 52, 4, 12, '#394754');
  fill(28, 52, 4, 12, '#394754');
  fill(20, 48, 4, 4, '#394754');
  fill(24, 48, 4, 4, '#222a32');
  fill(36, 52, 4, 12, '#7b92a6');
  fill(44, 52, 4, 12, '#7b92a6');
  fill(32, 52, 4, 12, '#7b92a6');
  fill(40, 52, 4, 12, '#7b92a6');
  fill(36, 48, 4, 4, '#7b92a6');
  fill(40, 48, 4, 4, '#e5bd95');
  fill(40, 20, 4, 12, '#7b92a6');
  fill(48, 20, 4, 12, '#7b92a6');
  fill(52, 20, 4, 12, '#7b92a6');
  fill(56, 20, 4, 12, '#7b92a6');
  fill(44, 16, 4, 4, '#7b92a6');
  fill(48, 16, 4, 4, '#e5bd95');
  return canvas;
}
