import { api } from '../api.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

let codeTimer = null;
let codeCooldownUntil = 0;
let codeEmail = '';

function clearCodeTimer() {
  if (codeTimer) clearInterval(codeTimer);
  codeTimer = null;
}

function safeNext(value) {
  const next = String(value || '');
  return next.startsWith('/') && !next.startsWith('//') ? next : '/players';
}

function passwordField(name, label, autocomplete) {
  return `
    <label class="field">
      <span>${label}</span>
      <div class="input-with-action">
        <input type="password" name="${name}" ${autocomplete ? `autocomplete="${autocomplete}"` : ''} required minlength="6" placeholder="至少 6 位，字母数字组合" />
        <button type="button" class="field-action" data-toggle-password aria-label="显示密码">${icon('eye')}</button>
      </div>
    </label>
  `;
}

export async function render(root, ctx) {
  if (ctx.user) {
    ctx.navigate('/profile');
    return;
  }

  clearCodeTimer();
  let tab = ctx.query.tab === 'register' ? 'register' : 'login';
  const next = safeNext(ctx.query.next);
  const registrationOpen = ctx.settings.registrationOpen !== false;

  root.innerHTML = `
    <section class="auth-page">
      <div class="auth-visual">
        <div class="auth-stars" aria-hidden="true"></div>
        <div class="auth-badge-wrap">
          <img src="/assets/brand/badge.png" alt="星空社徽章" class="auth-badge" />
        </div>
        <div class="auth-quote">
          <p class="eyebrow">SWUST MINECRAFT</p>
          <h1>每一块方块<br />都能装下<span>一片星空</span></h1>
          <p>登录星空社皮肤站，创建你的角色、上传专属外观，让离线服务器里的每一次相遇都有辨识度。</p>
        </div>
        <ul class="auth-points">
          <li>${icon('check')}邮箱 / 用户名登录，无需第三方账号</li>
          <li>${icon('check')}角色、衣柜与皮肤库数据互通</li>
          <li>${icon('check')}authlib-injector 联机认证</li>
        </ul>
        <a class="auth-back" href="#/">${icon('arrow')}先回首页看看</a>
      </div>
      <div class="auth-panel">
        <div class="auth-card">
          <div class="auth-tabs" role="tablist">
            <button role="tab" class="${tab === 'login' ? 'is-active' : ''}" data-tab="login">登录</button>
            <button role="tab" class="${tab === 'register' ? 'is-active' : ''}" data-tab="register">注册</button>
          </div>
          <div class="auth-form" data-auth-form></div>
          <p class="auth-footnote">
            社团成员使用注册时的用户名或邮箱登录。忘记密码请联系社团管理员重置。
          </p>
        </div>
      </div>
    </section>
  `;

  const formHost = root.querySelector('[data-auth-form]');
  const tabButtons = [...root.querySelectorAll('[data-tab]')];

  function renderLoginForm() {
    clearCodeTimer();
    formHost.innerHTML = `
      <div class="auth-heading">
        <p class="eyebrow">WELCOME BACK</p>
        <h2>回到星空</h2>
        <p>使用用户名或邮箱继续。</p>
      </div>
      <form data-login-form novalidate>
        <label class="field">
          <span>用户名或邮箱</span>
          <input type="text" name="identifier" autocomplete="username" required placeholder="例如 Stargazer 或 you@swust.edu.cn" />
        </label>
        <label class="field">
          <span>密码</span>
          <div class="input-with-action">
            <input type="password" name="password" autocomplete="current-password" required placeholder="输入登录密码" />
            <button type="button" class="field-action" data-toggle-password aria-label="显示密码">${icon('eye')}</button>
          </div>
        </label>
        <div class="form-error" data-form-error hidden></div>
        <button class="btn btn-primary btn-block btn-lg" type="submit">${icon('sparkles')}登录星空社</button>
      </form>
      <p class="auth-switch">还没有账号？<button class="link-button" data-tab-link="register">注册一个</button></p>
    `;
    formHost.querySelector('form').addEventListener('submit', handleLogin);
  }

  function renderRegisterForm() {
    clearCodeTimer();
    if (!registrationOpen) {
      formHost.innerHTML = `
        <div class="auth-heading">
          <p class="eyebrow">REGISTRATION CLOSED</p>
          <h2>招新暂时告一段落</h2>
        </div>
        <div class="auth-closed">
          ${icon('moon')}
          <p>社团暂时关闭了注册通道。如果你是社员但还没有账号，请联系技术组或社长开通。</p>
        </div>
        <p class="auth-switch">已经有账号？<button class="link-button" data-tab-link="login">直接登录</button></p>
      `;
      return;
    }
    formHost.innerHTML = `
      <div class="auth-heading">
        <p class="eyebrow">JOIN THE CLUB</p>
        <h2>加入星空社</h2>
        <p>一个账号，连接你的所有角色与外观。</p>
      </div>
      <form data-register-form novalidate>
        <div class="form-grid two">
          <label class="field">
            <span>用户名</span>
            <input type="text" name="username" autocomplete="username" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]{3,16}" placeholder="3–16 位字母数字" />
          </label>
          <label class="field">
            <span>邮箱</span>
            <input type="email" name="email" autocomplete="email" required placeholder="you@example.com" />
          </label>
        </div>
        <div class="field">
          <span>邮箱验证码</span>
          <div class="input-with-button">
            <input type="text" name="code" aria-label="邮箱验证码" inputmode="numeric" autocomplete="one-time-code" required minlength="6" maxlength="6" pattern="[0-9]{6}" placeholder="6 位数字验证码" />
            <button type="button" class="btn btn-ghost btn-sm" data-send-code>发送验证码</button>
          </div>
          <small class="field-hint">验证码会发送到上面的邮箱，10 分钟内有效，120 秒后可重发。</small>
        </div>
        <label class="field">
          <span>昵称（可选）</span>
          <input type="text" name="nickname" maxlength="64" placeholder="展示在站点里的名字，默认与用户名相同" />
        </label>
        ${passwordField('password', '密码', 'new-password')}
        <label class="field">
          <span>确认密码</span>
          <input type="password" name="confirm" autocomplete="new-password" required minlength="6" placeholder="再次输入密码" />
        </label>
        <p class="field-hint">用户名为 3–16 位字母、数字或下划线；密码至少 6 位，且包含大小写字母、数字中的至少两类。</p>
        <div class="form-error" data-form-error hidden></div>
        <button class="btn btn-primary btn-block btn-lg" type="submit">${icon('star')}创建账号</button>
      </form>
      <p class="auth-switch">已经是社员？<button class="link-button" data-tab-link="login">直接登录</button></p>
    `;
    const form = formHost.querySelector('form');
    form.addEventListener('submit', handleRegister);
    const sendButton = form.querySelector('[data-send-code]');
    sendButton?.addEventListener('click', () => sendCode(form, sendButton));
    form.elements.email?.addEventListener('input', () => {
      const email = form.elements.email.value.trim().toLowerCase();
      if (email === codeEmail) return;
      codeCooldownUntil = 0;
      clearCodeTimer();
      if (sendButton) {
        sendButton.disabled = false;
        sendButton.textContent = '发送验证码';
      }
      form.elements.code.value = '';
    });
    if (Date.now() < codeCooldownUntil && sendButton) {
      startCodeCooldown(sendButton, Math.ceil((codeCooldownUntil - Date.now()) / 1000));
    }
  }

  function showError(message) {
    const errorBox = formHost.querySelector('[data-form-error]');
    if (!errorBox) return;
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  function startCodeCooldown(button, seconds) {
    if (!button || seconds <= 0) return;
    clearCodeTimer();
    codeCooldownUntil = Date.now() + seconds * 1000;
    const tick = () => {
      if (!button.isConnected) {
        clearCodeTimer();
        return;
      }
      const left = Math.ceil((codeCooldownUntil - Date.now()) / 1000);
      if (left <= 0) {
        clearCodeTimer();
        codeCooldownUntil = 0;
        button.disabled = false;
        button.textContent = '发送验证码';
        return;
      }
      button.disabled = true;
      button.textContent = `${left}s 后重发`;
    };
    tick();
    codeTimer = setInterval(tick, 250);
  }

  async function sendCode(form, button) {
    const email = form.elements.email.value.trim().toLowerCase();
    const errorBox = formHost.querySelector('[data-form-error]');
    errorBox.hidden = true;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
      showError('请先输入有效的邮箱地址');
      form.elements.email.focus();
      return;
    }
    ui.setButtonLoading(button, true, '发送中…');
    try {
      const data = await api.post('/auth/email-code', { email });
      ui.setButtonLoading(button, false);
      codeEmail = email;
      form.elements.code?.focus();
      startCodeCooldown(button, Number(data?.cooldown) || 120);
      ui.toast('验证码已发送，请查收邮箱', 'success');
    } catch (error) {
      ui.setButtonLoading(button, false);
      if (error.status === 429 && error.details?.retryAfter) {
        codeEmail = email;
        startCodeCooldown(button, Number(error.details.retryAfter) || 120);
      }
      showError(error.message);
    }
  }

  function setTab(nextTab) {
    tab = nextTab;
    for (const button of tabButtons) {
      button.classList.toggle('is-active', button.dataset.tab === tab);
      button.setAttribute('aria-selected', String(button.dataset.tab === tab));
    }
    if (tab === 'register') {
      renderRegisterForm();
      history.replaceState(null, '', `#/login?tab=register${next !== '/players' ? `&next=${encodeURIComponent(next)}` : ''}`);
    } else {
      renderLoginForm();
      history.replaceState(null, '', `#/login${next !== '/players' ? `?next=${encodeURIComponent(next)}` : ''}`);
    }
  }

  async function handleLogin(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const errorBox = formHost.querySelector('[data-form-error]');
    errorBox.hidden = true;
    const identifier = form.elements.identifier.value.trim();
    const password = form.elements.password.value;
    if (!identifier || !password) {
      showError('请输入用户名/邮箱和密码');
      return;
    }
    const button = form.querySelector('button[type="submit"]');
    ui.setButtonLoading(button, true, '正在点亮星图…');
    try {
      const data = await api.post('/auth/login', { identifier, password });
      ctx.setAuth(data.user, data.csrfToken);
      ui.toast(`欢迎回来，${data.user.nickname || data.user.username}`, 'success');
      ctx.navigate(next);
    } catch (error) {
      showError(error.message);
      ui.setButtonLoading(button, false);
    }
  }

  async function handleRegister(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const errorBox = formHost.querySelector('[data-form-error]');
    errorBox.hidden = true;
    const username = form.elements.username.value.trim();
    const nickname = form.elements.nickname?.value.trim() || '';
    const email = form.elements.email.value.trim();
    const code = form.elements.code.value.trim();
    const password = form.elements.password.value;
    const confirm = form.elements.confirm.value;
    if (!/^[A-Za-z0-9_]{3,16}$/.test(username)) {
      showError('用户名需为 3–16 位字母、数字或下划线');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
      showError('请输入有效的邮箱地址');
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      showError('请输入 6 位邮箱验证码');
      form.elements.code.focus();
      return;
    }
    if (password.length < 6) {
      showError('密码长度不能少于 6 位');
      return;
    }
    const categories =
      Number(/[a-z]/.test(password)) + Number(/[A-Z]/.test(password)) + Number(/[0-9]/.test(password));
    if (categories < 2) {
      showError('密码必须包含小写字母、大写字母、数字中的至少两类');
      return;
    }
    if (password !== confirm) {
      showError('两次输入的密码不一致');
      return;
    }
    const button = form.querySelector('button[type="submit"]');
    ui.setButtonLoading(button, true, '正在创建角色…');
    try {
      const data = await api.post('/auth/register', { username, nickname, email, password, code });
      ctx.setAuth(data.user, data.csrfToken);
      ui.toast('注册成功，欢迎加入星空社！', 'success');
      ctx.navigate(next === '/players' ? '/players' : next);
    } catch (error) {
      showError(error.message);
      ui.setButtonLoading(button, false);
    }
  }

  const handleRootClick = (event) => {
    const tabButton = event.target.closest('[data-tab]');
    if (tabButton) {
      setTab(tabButton.dataset.tab);
      return;
    }
    const tabLink = event.target.closest('[data-tab-link]');
    if (tabLink) {
      setTab(tabLink.dataset.tabLink);
      return;
    }
    const toggle = event.target.closest('[data-toggle-password]');
    if (toggle) {
      const input = toggle.closest('.input-with-action')?.querySelector('input');
      if (!input) return;
      const visible = input.type === 'text';
      input.type = visible ? 'password' : 'text';
      toggle.setAttribute('aria-label', visible ? '显示密码' : '隐藏密码');
      toggle.classList.toggle('is-active', !visible);
      toggle.innerHTML = icon(visible ? 'eye' : 'eyeOff');
    }
  };
  root.addEventListener('click', handleRootClick);

  setTab(tab);

  return () => {
    clearCodeTimer();
    root.removeEventListener('click', handleRootClick);
  };
}
