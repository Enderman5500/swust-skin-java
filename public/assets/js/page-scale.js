/*
 * 大屏整体等比放大：按 1440px 设计宽度计算缩放比例，
 * 让宽屏表现接近浏览器页面缩放，而不是只把容器横向拉长。
 * 1440px 及以下保持 1:1，避免影响正常响应式布局。
 */
(function () {
  'use strict';

  var DESIGN_WIDTH = 1440;
  var MAX_SCALE = 2;
  var supportsZoom = !!(window.CSS && CSS.supports && CSS.supports('zoom', '1'));

  function apply() {
    var root = document.documentElement;
    var width = root.clientWidth || window.innerWidth || 0;
    var scale = supportsZoom && width > DESIGN_WIDTH ? Math.min(MAX_SCALE, width / DESIGN_WIDTH) : 1;
    scale = Math.round(scale * 1000) / 1000;
    if (scale > 1) {
      root.style.setProperty('--ui-scale', String(scale));
      root.style.zoom = String(scale);
    } else {
      root.style.removeProperty('--ui-scale');
      root.style.zoom = '';
    }
  }

  apply();

  var frame = 0;
  function schedule() {
    if (frame) return;
    frame = window.requestAnimationFrame(function () {
      frame = 0;
      apply();
    });
  }

  window.addEventListener('resize', schedule);
  window.addEventListener('orientationchange', schedule);
})();
