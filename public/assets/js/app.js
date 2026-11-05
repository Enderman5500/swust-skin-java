import { api, setCsrf } from './api.js';
import { icon } from './icons.js';
import { drawCapePreview, drawSkinCard, placeholderSkinCanvas } from './skin2d.js';
import * as ui from './ui.js';
import { render as renderHome } from './pages/home.js';
import { render as renderLogin } from './pages/login.js';
import { render as renderLibrary } from './pages/library.js';
import { render as renderPlayers } from './pages/players.js';
import { render as renderCloset } from './pages/closet.js';
import { render as renderUpload } from './pages/upload.js';
import { render as renderProfile } from './pages/profile.js';
import { render as renderAdmin } from './pages/admin.js';
import { render as renderPlugin } from './pages/plugin.js';

const state = {
  user: null,
  csrfToken: null,
  counts: null,
  settings: {
    site_name: '西南科技大学 Minecraft 星空社',
    site_short_name: '星空社',
    tagline: '在方块宇宙里，留下你的名字',
    announcement: '',
    footer_text: '西南科技大学 Minecraft 星空社 · 由社团技术组维护',
    icp_number: '',
    police_number: '',
    registrationOpen: true,
    yggdrasilEnabled: true,
  },
  plugins: [],
  home: null,
};

const headerEl = document.getElementById('site-header');
const footerEl = document.getElementById('site-footer');
const announcementEl = document.getElementById('announcement-root');
const mainEl = document.getElementById('main');
let currentCleanup = null;
let headerOutsideHandler = null;
let lastPath = null;

const progressEl = document.getElementById('route-progress');
let progressToken = 0;
let progressTimer = null;
let routeSequence = 0;
let routeAnimation = null;

function beginRouteProgress() {
  const token = ++progressToken;
  if (!progressEl) return token;
  clearTimeout(progressTimer);
  const bar = progressEl.firstElementChild;
  progressEl.hidden = false;
  progressEl.classList.add('is-active');
  progressEl.classList.remove('is-done');
  if (bar) {
    bar.style.transition = 'none';
    bar.style.width = '0%';
    void bar.offsetWidth;
    bar.style.transition = '';
    requestAnimationFrame(() => {
      if (token === progressToken) bar.style.width = '72%';
    });
  }
  return token;
}

function endRouteProgress(token) {
  if (token !== progressToken || !progressEl) return;
  const bar = progressEl.firstElementChild;
  progressEl.classList.add('is-done');
  if (bar) bar.style.width = '100%';
  clearTimeout(progressTimer);
  progressTimer = setTimeout(() => {
    if (token !== progressToken) return;
    progressEl.classList.remove('is-active', 'is-done');
    progressEl.hidden = true;
    if (bar) bar.style.width = '0%';
  }, 240);
}

function prefersReducedMotion() {
  return Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
}

const NAV_ITEMS = [
  { path: '/', label: '首页', icon: 'home' },
  { path: '/library', label: '皮肤库', icon: 'library' },
  { path: '/players', label: '我的角色', icon: 'user', auth: true },
  { path: '/closet', label: '衣柜', icon: 'closet', auth: true },
];

function parseHash() {
  const raw = location.hash.replace(/^#/, '') || '/';
  const [pathPart, queryPart] = raw.split('?');
  const path = pathPart || '/';
  const query = Object.fromEntries(new URLSearchParams(queryPart || ''));
  return { path, query, raw };
}

function navigate(hash) {
  const target = hash.startsWith('#') ? hash : `#${hash}`;
  if (location.hash === target) {
    handleRoute();
    return;
  }
  location.hash = target;
}

function navigationItems() {
  const items = NAV_ITEMS.filter((item) => !item.auth || state.user);
  for (const plugin of state.plugins) {
    if (!plugin.nav) continue;
    items.push({
      path: `/plugin/${plugin.id}`,
      label: plugin.nav.title,
      icon: plugin.nav.icon || 'sparkles',
      plugin: true,
    });
  }
  if (state.user?.isAdmin) {
    items.push({ path: '/admin', label: '管理后台', icon: 'admin' });
  }
  return items;
}

function isActive(itemPath, currentPath) {
  if (itemPath === '/') return currentPath === '/';
  if (itemPath === '/admin') return currentPath === '/admin' || currentPath.startsWith('/admin/');
  return currentPath === itemPath || currentPath.startsWith(`${itemPath}/`);
}

function updateHeaderActive() {
  const current = parseHash();
  headerEl.querySelectorAll('.main-nav .nav-link').forEach((link) => {
    const href = link.getAttribute('href') || '';
    const path = href.startsWith('#') ? href.slice(1) : href;
    link.classList.toggle('is-active', isActive(path, current.path));
  });
}

function displayName(user) {
  return user?.nickname || user?.username || '社员';
}

function renderHeader() {
  const current = parseHash();
  const items = navigationItems();
  const links = items
    .map(
      (item) => `
      <a class="nav-link ${isActive(item.path, current.path) ? 'is-active' : ''}" href="#${item.path}">
        ${icon(item.icon, 'nav-icon')}
        <span>${ui.escapeHtml(item.label)}</span>
      </a>
    `
    )
    .join('');

  const authArea = state.user
    ? `
      <details class="user-menu">
        <summary class="user-summary" aria-label="账号菜单">
          ${ui.avatarHtml(displayName(state.user), { size: 'sm', avatar: state.user.avatar })}
          <span class="user-name">${ui.escapeHtml(displayName(state.user))}</span>
          ${icon('chevron', 'icon user-chevron')}
        </summary>
        <div class="user-panel">
          <div class="user-panel-head">
            ${ui.avatarHtml(displayName(state.user), { size: 'md', avatar: state.user.avatar })}
            <div>
              <strong>${ui.escapeHtml(displayName(state.user))}</strong>
              <span>${ui.escapeHtml(state.user.email || state.user.username)}</span>
              <em>${ui.escapeHtml((state.user.roleNames || []).join(' / ') || '社员')}</em>
            </div>
          </div>
          <a href="#/profile">${icon('user')}个人中心</a>
          <a href="#/players">${icon('closet')}我的角色</a>
          <a href="#/upload">${icon('upload')}上传材质</a>
          ${state.user.isAdmin ? `<a href="#/admin">${icon('admin')}管理后台</a>` : ''}
          <button type="button" class="menu-danger" data-action="logout">${icon('logout')}退出登录</button>
        </div>
      </details>
    `
    : `
      <a class="btn btn-ghost btn-sm header-login" href="#/login">登录</a>
      <a class="btn btn-primary btn-sm" href="#/login?tab=register">注册</a>
    `;

  headerEl.innerHTML = `
    <div class="header-inner">
      <a class="brand" href="#/" aria-label="${ui.escapeHtml(state.settings.site_name)}">
        <img class="brand-badge" src="/assets/brand/badge.png" alt="" width="44" height="44" />
        <span class="brand-copy">
          <strong>${ui.escapeHtml(state.settings.site_short_name || '星空社')}</strong>
          <small>SWUST MINECRAFT · SKIN</small>
        </span>
      </a>
      <button class="icon-btn mobile-toggle" data-action="toggle-nav" aria-label="展开导航" aria-expanded="false">
        ${icon('layers')}
      </button>
      <nav class="main-nav" aria-label="主导航">${links}</nav>
      <div class="header-actions">${authArea}</div>
    </div>
  `;

  const toggle = headerEl.querySelector('[data-action="toggle-nav"]');
  const nav = headerEl.querySelector('.main-nav');
  toggle?.addEventListener('click', () => {
    const open = headerEl.classList.toggle('nav-open');
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? '收起导航' : '展开导航');
  });
  nav?.addEventListener('click', () => headerEl.classList.remove('nav-open'));

  headerEl.querySelector('[data-action="logout"]')?.addEventListener('click', async () => {
    try {
      await api.post('/auth/logout');
    } catch {
      // 忽略退出请求错误，本地状态仍然清理
    }
    setAuth(null, null);
    navigate('/');
    ui.toast('已退出登录，星空会记得你', 'info');
  });

  const menu = headerEl.querySelector('.user-menu');
  if (headerOutsideHandler) {
    document.removeEventListener('click', headerOutsideHandler);
    headerOutsideHandler = null;
  }
  if (menu) {
    headerOutsideHandler = (event) => {
      if (!menu.contains(event.target)) menu.removeAttribute('open');
    };
    document.addEventListener('click', headerOutsideHandler);
  }
}

function renderAnnouncement() {
  const text = String(state.settings.announcement || '').trim();
  if (!text || sessionStorage.getItem('swust-announcement-dismissed') === text) {
    announcementEl.innerHTML = '';
    return;
  }
  announcementEl.innerHTML = `
    <div class="announcement-bar">
      <div class="announcement-inner">
        <span class="announcement-icon">${icon('sparkles')}</span>
        <p>${ui.escapeHtml(text)}</p>
        <button class="icon-btn" data-action="dismiss-announcement" aria-label="关闭公告">${icon('close')}</button>
      </div>
    </div>
  `;
  announcementEl.querySelector('[data-action="dismiss-announcement"]')?.addEventListener('click', () => {
    sessionStorage.setItem('swust-announcement-dismissed', text);
    announcementEl.innerHTML = '';
  });
}

function renderFooter() {
  const year = new Date().getFullYear();
  const yggdrasil = state.home?.yggdrasilUrl || `${location.origin}/api/yggdrasil`;
  const beian = [
    state.settings.icp_number
      ? `<a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">${ui.escapeHtml(
          state.settings.icp_number
        )}</a>`
      : '',
    state.settings.police_number
      ? `<a href="https://beian.mps.gov.cn/" target="_blank" rel="noopener noreferrer">${ui.escapeHtml(
          state.settings.police_number
        )}</a>`
      : '',
  ]
    .filter(Boolean)
    .join(' · ');
  footerEl.innerHTML = `
    <div class="footer-inner">
      <div class="footer-brand">
        <img src="/assets/brand/badge.png" alt="" width="52" height="52" />
        <div>
          <strong>${ui.escapeHtml(state.settings.site_name)}</strong>
          <p>${ui.escapeHtml(state.settings.footer_text || '')}</p>
        </div>
      </div>
      <div class="footer-links">
        <a href="#/library">皮肤库</a>
        <a href="#/players">角色与衣柜</a>
        <a href="#/plugin/server-status">服务器状态</a>
        ${state.user?.isAdmin ? '<a href="#/admin">管理后台</a>' : ''}
      </div>
      <div class="footer-meta">
        <span>联机认证</span>
        <code>${ui.escapeHtml(yggdrasil)}</code>
        <button class="btn btn-ghost btn-sm" data-action="copy-ygg">复制</button>
      </div>
      <p class="footer-copy">© ${year} SWUST Minecraft 星空社 · 在方块宇宙里相遇${beian ? ` · ${beian}` : ''}</p>
    </div>
  `;
  footerEl.querySelector('[data-action="copy-ygg"]')?.addEventListener('click', () => {
    ui.copyText(yggdrasil, '认证服务器地址已复制');
  });
}

function setAuth(user, csrfToken) {
  state.user = user || null;
  state.csrfToken = csrfToken || null;
  setCsrf(state.csrfToken);
  renderHeader();
}

async function refreshAuth() {
  try {
    const data = await api.get('/auth/me');
    setAuth(data.user, data.csrfToken);
    state.counts = data.counts || null;
    return data.user;
  } catch {
    setAuth(null, null);
    return null;
  }
}

async function refreshPlugins() {
  try {
    const data = await api.get('/plugins');
    state.plugins = data.items;
    renderHeader();
  } catch {
    // 插件列表失败不影响核心页面
  }
}

async function refreshSite() {
  try {
    const home = await api.get('/home');
    state.home = home;
    state.settings = home.settings;
    renderHeader();
    renderAnnouncement();
    renderFooter();
    document.title = `${state.settings.site_name} · 皮肤站`;
  } catch {
    // ignore
  }
}

function pageContext({ path, query }) {
  return {
    state,
    api,
    ui,
    icon,
    path,
    query,
    navigate,
    setAuth,
    refreshAuth,
    refreshPlugins,
    refreshSite,
    skin2d: { drawSkinCard, drawCapePreview, placeholderSkinCanvas },
    user: state.user,
    settings: state.settings,
    plugins: state.plugins,
  };
}

function routeRequiresAuth(path) {
  return ['/players', '/closet', '/upload', '/profile'].includes(path);
}

function routeRequiresAdmin(path) {
  return path === '/admin' || path.startsWith('/admin/');
}

async function handleRoute() {
  const { path, query } = parseHash();
  const ctx = pageContext({ path, query });

  if (routeRequiresAuth(path) && !state.user) {
    navigate(`/login?next=${encodeURIComponent(path)}`);
    ui.toast('请先登录，再继续探索星空', 'info');
    return;
  }
  if (routeRequiresAdmin(path) && !state.user?.isAdmin) {
    navigate('/');
    ui.toast('管理后台仅对管理员开放', 'error');
    return;
  }

  const reducedMotion = prefersReducedMotion();
  const sequence = ++routeSequence;
  const progress = beginRouteProgress();
  const reuseAdminShell =
    routeRequiresAdmin(path) &&
    routeRequiresAdmin(lastPath || '') &&
    Boolean(mainEl.querySelector('.yd-admin'));

  let leavingAnimation = null;
  if (!reuseAdminShell) {
    mainEl.classList.remove('page-enter');
    routeAnimation?.cancel();
    routeAnimation = null;
    if (typeof mainEl.animate === 'function') {
      leavingAnimation = mainEl.animate(
        reducedMotion
          ? [{ opacity: 1 }, { opacity: 0 }]
          : [
              { opacity: 1, transform: 'none' },
              { opacity: 0, transform: 'translateY(-6px)' },
            ],
        {
          duration: reducedMotion ? 180 : 200,
          easing: 'ease',
          fill: 'forwards',
        }
      );
      routeAnimation = leavingAnimation;
      await leavingAnimation.finished.catch(() => {});
    }
  }
  if (sequence !== routeSequence) {
    leavingAnimation?.cancel();
    if (routeAnimation === leavingAnimation) routeAnimation = null;
    endRouteProgress(progress);
    return;
  }
  currentCleanup?.();
  currentCleanup = null;
  if (mainEl.contains(document.activeElement)) document.activeElement.blur();
  if (!reuseAdminShell) {
    mainEl.innerHTML = '';
    mainEl.classList.remove('page-enter');
  }

  let render = null;
  if (path === '/') render = renderHome;
  else if (path === '/login') render = renderLogin;
  else if (path === '/library') render = renderLibrary;
  else if (path === '/players') render = renderPlayers;
  else if (path === '/closet') render = renderCloset;
  else if (path === '/upload') render = renderUpload;
  else if (path === '/profile') render = renderProfile;
  else if (path === '/admin' || path.startsWith('/admin/')) render = renderAdmin;
  else if (path.startsWith('/plugin/')) render = renderPlugin;

  updateHeaderActive();
  document.title = `${state.settings.site_name} · 皮肤站`;
  document.body.classList.toggle('is-home', path === '/');
  document.body.classList.toggle('is-admin', routeRequiresAdmin(path));

  if (!render) {
    mainEl.innerHTML = `
      <section class="notfound">
        <span class="notfound-code">404</span>
        <h1>这颗星还没有被点亮</h1>
        <p>你访问的页面不存在，或者已经被星尘带走。</p>
        <a class="btn btn-primary" href="#/">回到首页</a>
      </section>
    `;
    mainEl.classList.add('page-enter');
    leavingAnimation?.cancel();
    if (routeAnimation === leavingAnimation) routeAnimation = null;
    endRouteProgress(progress);
    return;
  }

  try {
    const cleanup = await render(mainEl, ctx);
    if (typeof cleanup === 'function') currentCleanup = cleanup;
  } catch (error) {
    console.error(error);
    if (error.status === 401) {
      leavingAnimation?.cancel();
      if (routeAnimation === leavingAnimation) routeAnimation = null;
      await refreshAuth();
      navigate(`/login?next=${encodeURIComponent(path)}`);
      return;
    }
    mainEl.innerHTML = `
      <section class="page-error">
        <span class="empty-icon">${icon('moon')}</span>
        <h1>页面暂时没有响应</h1>
        <p>${ui.escapeHtml(error.message || '未知错误')}</p>
        <button class="btn btn-primary" data-action="retry">重新加载</button>
      </section>
    `;
    mainEl.querySelector('[data-action="retry"]')?.addEventListener('click', handleRoute);
  } finally {
    endRouteProgress(progress);
  }

  if (lastPath !== path) {
    window.scrollTo({ top: 0, behavior: 'instant' });
    lastPath = path;
  }
  if (!reuseAdminShell) {
    mainEl.classList.add('page-enter');
    if (reducedMotion && typeof mainEl.animate === 'function') {
      mainEl.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 320, easing: 'ease' });
    }
  }
  leavingAnimation?.cancel();
  if (routeAnimation === leavingAnimation) routeAnimation = null;
  mainEl.focus({ preventScroll: true });
}

async function bootstrap() {
  const boot = document.getElementById('boot');
  const app = document.getElementById('app');
  try {
    const [me, home, pluginData] = await Promise.all([
      api.get('/auth/me').catch(() => ({ user: null })),
      api.get('/home'),
      api.get('/plugins').catch(() => ({ items: [] })),
    ]);
    state.user = me.user;
    state.csrfToken = me.csrfToken;
    state.counts = me.counts || null;
    setCsrf(me.csrfToken);
    state.home = home;
    state.settings = home.settings;
    state.plugins = pluginData.items;

    document.title = `${state.settings.site_name} · 皮肤站`;
    renderHeader();
    renderAnnouncement();
    renderFooter();
    app.hidden = false;
    boot?.classList.add('is-done');
    setTimeout(() => boot?.remove(), 320);
    window.addEventListener('hashchange', handleRoute);
    window.addEventListener('auth:expired', async () => {
      if (!state.user) return;
      await refreshAuth();
      ui.toast('登录状态已过期，请重新登录', 'error');
      const { path } = parseHash();
      if (routeRequiresAuth(path) || routeRequiresAdmin(path)) {
        navigate(`/login?next=${encodeURIComponent(path)}`);
      }
    });
    await handleRoute();
  } catch (error) {
    console.error(error);
    if (boot) {
      boot.innerHTML = `
        <img src="/assets/brand/badge.png" alt="" class="boot-badge" />
        <p>星图加载失败：${ui.escapeHtml(error.message || '服务暂时不可用')}</p>
        <details class="boot-error-detail">
          <summary>技术细节</summary>
          <pre>${ui.escapeHtml(error.stack || String(error))}</pre>
        </details>
        <button class="btn btn-primary" data-retry>重新连接</button>
      `;
      boot.querySelector('[data-retry]')?.addEventListener('click', () => location.reload());
    }
  }
}

bootstrap();
