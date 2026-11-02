/**
 * skinview3d 适配层。
 *
 * 站点不依赖构建工具，因此 skinview3d 以 UMD bundle 的形式在 index.html 中提前加载，
 * 暴露为 window.skinview3d。这里保持原有 SkinViewer 的调用接口不变。
 */
const library = () => window.skinview3d;

export function supportsWebGL() {
  try {
    if (!library()?.SkinViewer) return false;
    const canvas = document.createElement('canvas');
    return Boolean(
      window.WebGLRenderingContext &&
        (canvas.getContext('webgl') || canvas.getContext('experimental-webgl'))
    );
  } catch {
    return false;
  }
}

export class SkinViewer {
  constructor(canvas, {
    skinUrl = null,
    capeUrl = null,
    model = 'classic',
    autoRotate = true,
    angle = 0.55,
  } = {}) {
    const api = library();
    if (!api?.SkinViewer) throw new Error('skinview3d 未加载');

    this.canvas = canvas;
    this.initialAngle = angle;
    this.initialZoom = 0.86;
    this.viewer = new api.SkinViewer({
      canvas,
      width: Math.max(1, canvas.clientWidth || 320),
      height: Math.max(1, canvas.clientHeight || 400),
      skin: skinUrl || undefined,
      cape: capeUrl || undefined,
      model: model === 'slim' ? 'slim' : 'classic',
      autoRotate: false,
      enableRotate: true,
      enableZoom: true,
      enablePan: false,
      background: null,
      fov: 46,
      zoom: this.initialZoom,
    });

    this.viewer.autoRotateSpeed = 0.42;
    this.viewer.globalLight.intensity = 2.8;
    this.viewer.cameraLight.intensity = 2.0;
    this.viewer.playerWrapper.position.y = 2.6;
    this.viewer.controls.target.set(0, 4, 0);
    this.viewer.controls.update();
    this.viewer.playerWrapper.rotation.y = angle;
    this.viewer.autoRotate = Boolean(autoRotate);
    this.viewer.render();

    this.resizeObserver = window.ResizeObserver
      ? new ResizeObserver(() => this.resize())
      : null;
    this.resizeObserver?.observe(canvas);
    this._onWindowResize = () => this.resize();
    window.addEventListener('resize', this._onWindowResize);
    this._onDoubleClick = () => this.reset();
    canvas.addEventListener('dblclick', this._onDoubleClick);
    this.resize();
  }

  get autoRotate() {
    return Boolean(this.viewer?.autoRotate);
  }

  set autoRotate(value) {
    if (this.viewer) this.viewer.autoRotate = Boolean(value);
  }

  resize() {
    if (!this.viewer) return;
    const width = Math.max(1, Math.round(this.canvas.clientWidth || 320));
    const height = Math.max(1, Math.round(this.canvas.clientHeight || 400));
    if (this.viewer.width !== width) this.viewer.width = width;
    if (this.viewer.height !== height) this.viewer.height = height;
    this.viewer.render();
  }

  reset() {
    if (!this.viewer) return;
    this.viewer.playerWrapper.rotation.x = 0;
    this.viewer.playerWrapper.rotation.y = this.initialAngle;
    this.viewer.zoom = this.initialZoom;
    this.viewer.controls.target.set(0, 4, 0);
    this.viewer.controls.update();
    this.viewer.render();
  }

  dispose() {
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    window.removeEventListener('resize', this._onWindowResize);
    this.canvas.removeEventListener('dblclick', this._onDoubleClick);
    this.viewer?.dispose();
    this.viewer = null;
  }
}
