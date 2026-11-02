/**
 * 使用与详情页相同的 skinview3d 渲染正面缩略图，保证卡片外层与 3D 正面一致。
 */
let shared = null;
let queue = Promise.resolve();

function getShared() {
  const api = window.skinview3d;
  if (!api?.SkinViewer) throw new Error('skinview3d 未加载');
  if (shared) return shared;
  const canvas = document.createElement('canvas');
  const viewer = new api.SkinViewer({
    canvas,
    width: 240,
    height: 480,
    model: 'classic',
    autoRotate: false,
    enableRotate: false,
    enableZoom: false,
    enablePan: false,
    background: null,
    fov: 40,
    zoom: 0.9,
  });
  viewer.globalLight.intensity = 2.8;
  viewer.cameraLight.intensity = 2.0;
  viewer.playerWrapper.position.y = 2.6;
  viewer.controls.target.set(0, 4, 0);
  viewer.controls.update();
  shared = { canvas, viewer };
  return shared;
}

export function drawSkin3DCard(target, texture, { maxWidth = 220 } = {}) {
  if (!window.skinview3d?.SkinViewer || !texture?.url) return Promise.resolve(false);
  const task = queue.then(async () => {
    const { canvas, viewer } = getShared();
    viewer.width = 240;
    viewer.height = 480;
    await viewer.loadSkin(texture.url, {
      model: texture.model === 'slim' ? 'slim' : 'classic',
    });
    viewer.autoRotate = false;
    viewer.playerWrapper.position.y = 2.6;
    viewer.playerWrapper.rotation.set(0, 0, 0);
    viewer.controls.target.set(0, 4, 0);
    viewer.controls.update();
    viewer.zoom = 0.9;
    viewer.render();

    const width = Math.max(80, Math.min(maxWidth, target.clientWidth || maxWidth));
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const pixelWidth = Math.round(width * dpr);
    const pixelHeight = Math.round(width * 2 * dpr);
    if (target.width !== pixelWidth || target.height !== pixelHeight) {
      target.width = pixelWidth;
      target.height = pixelHeight;
    }
    const ctx = target.getContext('2d');
    ctx.clearRect(0, 0, target.width, target.height);
    ctx.drawImage(canvas, 0, 0, target.width, target.height);
    return true;
  });
  queue = task.catch(() => {});
  return task;
}
