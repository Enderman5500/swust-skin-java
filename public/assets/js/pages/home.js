import { api } from '../api.js';
import {
  bindTextureCards,
  mountTextureCards,
  openTextureDetail,
  textureCardHtml,
} from '../components/texture.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

function number(value) {
  return new Intl.NumberFormat('zh-CN').format(Number(value) || 0);
}

function authCard(url) {
  return `
    <div class="hero-auth-card" data-auth-drag draggable="true" title="拖到 PCL2 / HMCL 窗口">
      <div class="hero-auth-top">
        <span class="live-dot"></span>
        <span>authlib-injector endpoint</span>
        <button class="icon-btn" type="button" data-action="copy-yggdrasil" aria-label="复制认证地址">${icon(
          'copy'
        )}</button>
      </div>
      <code class="hero-auth-url">${ui.escapeHtml(url)}</code>
      <div class="hero-auth-hint">
        ${icon('external')}
        <span>按住此卡片拖到 PCL2 / HMCL 窗口，自动添加皮肤站登录</span>
      </div>
    </div>
  `;
}

function bindAuthDrag(root, url) {
  root.querySelectorAll('[data-auth-drag]').forEach((card) => {
    card.addEventListener('dragstart', (event) => {
      const transfer = event.dataTransfer;
      if (!transfer) return;
      transfer.effectAllowed = 'copy';
      transfer.setData('text/plain', url);
      transfer.setData('text/uri-list', url);
      transfer.setData('text/html', `<a href="${url}">${url}</a>`);
      transfer.setData('application/x-authlib-injector', url);
      card.classList.add('is-dragging');
    });
    card.addEventListener('dragend', () => card.classList.remove('is-dragging'));
  });
}

function heroBannerHtml(urls) {
  if (!urls.length) {
    return `
      <figure class="hero-banner is-placeholder">
        <img src="/assets/brand/badge.png" alt="西南科技大学 Minecraft 星空社徽章" data-hero-banner />
      </figure>
    `;
  }
  if (urls.length === 1) {
    return `
      <figure class="hero-banner">
        <img src="${ui.escapeHtml(urls[0])}" alt="星空社首页主图" data-hero-banner />
      </figure>
    `;
  }
  return `
    <figure class="hero-banner hero-carousel" data-hero-carousel aria-roledescription="carousel" aria-label="首页主图">
      <div class="hero-carousel-track" data-hero-track>
        ${urls
          .map(
            (url, index) => `
          <div class="hero-carousel-slide" aria-hidden="${index === 0 ? 'false' : 'true'}">
            <img src="${ui.escapeHtml(url)}" alt="星空社首页主图 ${index + 1}" loading="${
              index === 0 ? 'eager' : 'lazy'
            }" data-hero-banner />
          </div>
        `
          )
          .join('')}
      </div>
      <button class="hero-carousel-arrow is-prev" type="button" data-carousel-prev aria-label="上一张主图">
        ${icon('chevron')}
      </button>
      <button class="hero-carousel-arrow is-next" type="button" data-carousel-next aria-label="下一张主图">
        ${icon('chevron')}
      </button>
      <div class="hero-carousel-dots" role="tablist" aria-label="切换首页主图">
        ${urls
          .map(
            (_, index) => `
          <button class="hero-carousel-dot${index === 0 ? ' is-active' : ''}" type="button"
            role="tab" aria-label="第 ${index + 1} 张" aria-selected="${index === 0}"
            data-slide-index="${index}"></button>
        `
          )
          .join('')}
      </div>
    </figure>
  `;
}

function mountCarousel(root, urls) {
  const carousel = root.querySelector('[data-hero-carousel]');
  if (!carousel || urls.length < 2) return null;
  const track = carousel.querySelector('[data-hero-track]');
  if (!track) return null;
  const dots = [...carousel.querySelectorAll('[data-slide-index]')];
  const previousButton = carousel.querySelector('[data-carousel-prev]');
  const nextButton = carousel.querySelector('[data-carousel-next]');
  const first = track.firstElementChild;
  const last = track.lastElementChild;
  if (!first || !last) return null;

  const cloneFirst = first.cloneNode(true);
  const cloneLast = last.cloneNode(true);
  for (const clone of [cloneFirst, cloneLast]) {
    clone.setAttribute('aria-hidden', 'true');
    clone.querySelectorAll('img').forEach((img) => img.setAttribute('loading', 'lazy'));
  }
  track.prepend(cloneLast);
  track.append(cloneFirst);

  let index = 1;
  let timer = null;
  let resetTimer = null;
  let paused = false;
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

  function activeDotIndex() {
    return ((index - 1) % urls.length + urls.length) % urls.length;
  }

  function updateDots() {
    const active = activeDotIndex();
    dots.forEach((dot, dotIndex) => {
      const isActive = dotIndex === active;
      dot.classList.toggle('is-active', isActive);
      dot.setAttribute('aria-selected', String(isActive));
    });
    [...track.children].forEach((slide, slideIndex) => {
      slide.setAttribute('aria-hidden', String(slideIndex !== index));
    });
  }

  function setTransform(next, animate = true) {
    track.style.transition = animate ? '' : 'none';
    track.style.transform = `translateX(-${next * 100}%)`;
    if (!animate) {
      void track.offsetWidth;
      track.style.transition = '';
    }
  }

  function goTo(next, animate = true) {
    index = next;
    setTransform(index, animate);
    updateDots();
  }

  function scheduleReset(target) {
    clearTimeout(resetTimer);
    resetTimer = setTimeout(() => goTo(target, false), 700);
  }

  function nextSlide(manual = false) {
    clearTimeout(resetTimer);
    if (index === 0) goTo(urls.length, false);
    else if (index === urls.length + 1) goTo(1, false);
    goTo(index + 1, true);
    if (index === urls.length + 1) scheduleReset(1);
    if (manual) start();
  }

  function previousSlide(manual = false) {
    clearTimeout(resetTimer);
    if (index === 0) goTo(urls.length, false);
    else if (index === urls.length + 1) goTo(1, false);
    goTo(index - 1, true);
    if (index === 0) scheduleReset(urls.length);
    if (manual) start();
  }

  function advance() {
    if (paused) return;
    nextSlide(false);
  }

  function stop() {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  function start() {
    if (reducedMotion) return;
    stop();
    timer = setInterval(advance, 4200);
  }

  dots.forEach((dot) => {
    dot.addEventListener('click', () => {
      const target = Number(dot.dataset.slideIndex);
      if (!Number.isFinite(target)) return;
      clearTimeout(resetTimer);
      if (index === 0) goTo(urls.length, false);
      else if (index === urls.length + 1) goTo(1, false);
      if (target + 1 !== index) goTo(target + 1, true);
      start();
    });
  });

  previousButton?.addEventListener('click', () => previousSlide(true));
  nextButton?.addEventListener('click', () => nextSlide(true));

  carousel.addEventListener('mouseenter', () => {
    paused = true;
    stop();
  });
  carousel.addEventListener('mouseleave', () => {
    paused = false;
    start();
  });

  goTo(1, false);
  start();
  return () => {
    stop();
    clearTimeout(resetTimer);
  };
}

export async function render(root, ctx) {
  root.innerHTML = `
    <div class="page-loading">
      ${ui.spinner()}
      <p>正在展开星图…</p>
    </div>
  `;

  let home;
  try {
    home = await api.get('/home');
    ctx.state.home = home;
    ctx.state.settings = home.settings;
  } catch (error) {
    home = ctx.state.home;
    if (!home) {
      root.innerHTML = `
        <section class="page-error">
          <span class="empty-icon">${icon('moon')}</span>
          <h1>星图暂时无法展开</h1>
          <p>${ui.escapeHtml(error.message)}</p>
          <button class="btn btn-primary" data-action="retry">重新加载</button>
        </section>
      `;
      root.querySelector('[data-action="retry"]')?.addEventListener('click', () => render(root, ctx));
      return;
    }
  }

  const { stats, recent, settings } = home;
  const tagline = String(settings.tagline || '').trim();
  const taglineLead = tagline
    ? `${ui.escapeHtml(tagline)}${/[。！？.!?]$/.test(tagline) ? '' : '。'}`
    : '';
  const signedIn = Boolean(ctx.user);
  const primaryHref = signedIn ? '#/players' : '#/login';
  const primaryLabel = signedIn ? '进入我的角色' : '登录';
  const primaryIcon = signedIn ? 'sparkles' : 'user';
  const secondaryAction = signedIn
    ? `<a class="btn btn-hero-ghost btn-lg" href="#/library">${icon('library')}浏览皮肤库</a>`
    : `<a class="btn btn-hero-ghost btn-lg" href="#/login?tab=register">${icon('sparkles')}注册</a>`;
  const homeBannerUrls = Array.isArray(settings.homeBannerUrls)
    ? settings.homeBannerUrls.filter(Boolean)
    : settings.homeBannerUrl
      ? [settings.homeBannerUrl]
      : [];
  const yggdrasilUrl = home.yggdrasilUrl;

  root.innerHTML = `
    <section class="hero">
      <div class="hero-stars" aria-hidden="true"></div>
      <div class="hero-orbit" aria-hidden="true"><span></span><span></span><span></span></div>
      <div class="hero-inner">
        <div class="hero-copy">
          <p class="eyebrow hero-eyebrow">
            <span class="eyebrow-dot"></span>
            SWUST · MINECRAFT 星空社
          </p>
          <h1>在方块宇宙里<br /><em>留下你的名字</em></h1>
          <p class="hero-lead">
            ${taglineLead}上传皮肤与披风，让每一次登录都带着星空的颜色。
          </p>
          <div class="hero-actions">
            <a class="btn btn-primary btn-lg" href="${primaryHref}">${icon(primaryIcon)}${primaryLabel}</a>
            ${secondaryAction}
          </div>
          ${
            signedIn
              ? authCard(yggdrasilUrl)
              : `<ul class="hero-points">
                  <li>${icon('check')}邮箱 / 用户名登录</li>
                  <li>${icon('check')}authlib-injector 联机</li>
                  <li>${icon('check')}3D 衣柜与皮肤库</li>
                </ul>`
          }
        </div>
        <div class="hero-visual">
          ${heroBannerHtml(homeBannerUrls)}
        </div>
      </div>
      <div class="hero-stats">
        <div class="hero-stat"><strong>${number(stats.users)}</strong><span>注册社员</span></div>
        <div class="hero-stat"><strong>${number(stats.players)}</strong><span>角色档案</span></div>
        <div class="hero-stat"><strong>${number(stats.textures)}</strong><span>公开材质</span></div>
        <div class="hero-stat"><strong>${number(stats.downloads)}</strong><span>材质下载</span></div>
      </div>
    </section>

    <section class="section">
      <div class="section-head">
        <div>
          <p class="eyebrow">FRESH FROM THE CONSTELLATION</p>
          <h2>新到星空</h2>
          <p>社团成员刚刚上传的皮肤与披风。</p>
        </div>
        <a class="text-link" href="#/library">浏览全部 ${icon('arrow')}</a>
      </div>
      ${
        recent.length
          ? `<div class="skin-grid home-skin-grid">${recent.map((item) => textureCardHtml(item)).join('')}</div>`
          : `<div class="empty-state">
              <span class="empty-icon">${icon('image')}</span>
              <h3>皮肤库还在等第一张作品</h3>
              <p>上传一张 64×64 的 PNG 皮肤，让星图亮起来。</p>
              <a class="btn btn-primary" href="#/upload">上传材质</a>
            </div>`
      }
    </section>

    <section class="section features-section">
      <div class="section-head">
        <div>
          <p class="eyebrow">WHY THIS SITE</p>
          <h2>为社团而生的皮肤站</h2>
          <p>不只是一个上传按钮，而是一整套联机外观方案。</p>
        </div>
      </div>
      <div class="feature-grid">
        <article class="feature-card">
          <span class="feature-icon">${icon('layers')}</span>
          <h3>实时 3D 衣橱</h3>
          <p>WebGL 渲染经典与纤细模型，支持披风、叠层与自由旋转，选购外观不再靠想象。</p>
          <span class="feature-tag">SKIN VIEWER</span>
        </article>
        <article class="feature-card">
          <span class="feature-icon">${icon('server')}</span>
          <h3>authlib-injector 联机</h3>
          <p>一次配置认证服务器，离线模式的社团服务器里也能看见每个人独一无二的皮肤。</p>
          <span class="feature-tag">YGGDRASIL</span>
        </article>
        <article class="feature-card">
          <span class="feature-icon">${icon('plugin')}</span>
          <h3>可扩展插件系统</h3>
          <p>服务器状态、随机皮肤只是开始。插件可以热加载、启停和独立扩展页面，让站点持续生长。</p>
          <span class="feature-tag">PLUGIN RUNTIME</span>
        </article>
      </div>
    </section>

    <section class="section join-section${signedIn ? ' is-single' : ''}">
      <div class="join-copy">
        <p class="eyebrow">JOIN THE CONSTELLATION</p>
        <h2>三步，把皮肤带进服务器</h2>
        <ol class="step-list">
          <li>
            <span class="step-number">01</span>
            <div><strong>注册星空社账号</strong><p>使用邮箱或用户名登录，创建属于你的角色名。</p></div>
          </li>
          <li>
            <span class="step-number">02</span>
            <div><strong>上传或收藏皮肤</strong><p>支持经典 / 纤细皮肤与披风，随时在衣柜里换装。</p></div>
          </li>
          <li>
            <span class="step-number">03</span>
            <div><strong>在启动器添加认证服务器</strong><p>${
              signedIn
                ? '把上方的认证卡片拖进启动器窗口，或手动填写认证地址。'
                : '把下面的地址粘贴到 HMCL / PCL2 的第三方登录设置中。'
            }</p></div>
          </li>
        </ol>
        <div class="join-actions">
          <a class="btn btn-primary" href="#/upload">${icon('upload')}上传第一张皮肤</a>
          <a class="btn btn-ghost" href="#/library">${icon('library')}先逛逛皮肤库</a>
        </div>
      </div>
      ${
        signedIn
          ? ''
          : `<div class="code-card">
              <div class="code-card-head">
                <span class="code-dots"><i></i><i></i><i></i></span>
                <span>authlib-injector endpoint</span>
                <button class="icon-btn" type="button" data-action="copy-yggdrasil" aria-label="复制认证地址">${icon(
                  'copy'
                )}</button>
              </div>
              <code class="code-block">${ui.escapeHtml(yggdrasilUrl)}</code>
              <p class="code-hint">
                HMCL：账户 → 添加认证服务器<br />
                PCL2：设置 → 登录方式 → 第三方登录
              </p>
              <div class="code-foot">
                <span>${icon('shield')}材质属性使用 RSA-SHA256 签名</span>
                <span>Yggdrasil API</span>
              </div>
            </div>`
      }
    </section>
  `;

  root.querySelectorAll('[data-action="copy-yggdrasil"]').forEach((button) => {
    button.addEventListener('click', () => ui.copyText(yggdrasilUrl, '认证服务器地址已复制'));
  });
  if (signedIn) bindAuthDrag(root, yggdrasilUrl);
  const stopCarousel = mountCarousel(root, homeBannerUrls);

  root.querySelectorAll('[data-hero-banner]').forEach((image) => {
    image.addEventListener('error', () => {
      if (image.dataset.fallbackApplied) return;
      image.dataset.fallbackApplied = '1';
      image.src = '/assets/brand/badge.png';
      image.alt = '西南科技大学 Minecraft 星空社徽章';
    });
  });


  const unbindCards = bindTextureCards(root, (id) => openTextureDetail(ctx, id, {
    onChanged: () => ctx.navigate('/'),
  }));
  await mountTextureCards(root);

  return () => {
    unbindCards();
    stopCarousel?.();
  };
}
