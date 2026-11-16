import { api } from '../api.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

const STYLE = `
.ob-page { max-width: 760px; margin: 36px auto 70px; padding: 0 20px; }
.ob-card { padding: 30px; border: 1px solid var(--line); border-radius: var(--radius); background: rgba(255,255,255,.92); box-shadow: var(--shadow-sm); }
.ob-head h1 { margin: 6px 0 8px; font-family: var(--font-display); font-size: 30px; color: var(--navy); }
.ob-head p { color: var(--muted); font-size: 13px; line-height: 1.8; }
.ob-progress { display: flex; gap: 10px; flex-wrap: wrap; margin: 20px 0 22px; }
.ob-step { display: inline-flex; align-items: center; gap: 6px; padding: 5px 11px; border: 1px solid var(--line); border-radius: 999px; background: #fff; color: var(--muted); font-size: 11px; }
.ob-step span { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 50%; background: var(--paper); color: var(--muted); font-size: 10px; }
.ob-step.is-done { border-color: rgba(60,122,91,.3); background: rgba(60,122,91,.08); color: var(--success); }
.ob-step.is-done span { background: var(--success); color: #fff; }
.ob-block { padding: 18px 0; border-top: 1px solid var(--line); }
.ob-block:first-of-type { border-top: 0; padding-top: 0; }
.ob-block.is-done { opacity: .78; }
.ob-block-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.ob-block-head h2 { font-family: var(--font-display); font-size: 19px; color: var(--navy); }
.ob-done { color: var(--success); font-size: 11px; white-space: nowrap; }
.ob-block p { color: var(--muted); font-size: 12.5px; line-height: 1.8; }
.ob-inline { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 8px 0 12px; }
.ob-hint { color: var(--muted); font-size: 11px; }
.ob-success { display: flex; align-items: center; justify-content: space-between; gap: 14px; flex-wrap: wrap; margin-top: 18px; padding: 16px; border: 1px solid rgba(60,122,91,.3); border-radius: 14px; background: rgba(60,122,91,.08); }
.ob-success strong { color: var(--success); }
.ob-foot { margin-top: 16px; color: var(--muted); font-size: 11px; line-height: 1.8; }
`;

function safeNext(value) {
  const next = String(value || '');
  return next.startsWith('/') && !next.startsWith('//') && next !== '/onboarding'
    ? next
    : '/players';
}

export async function render(root, ctx) {
  if (!ctx.user) {
    ctx.navigate(`/login?next=${encodeURIComponent('/onboarding')}`);
    return;
  }

  const next = safeNext(ctx.query.next);
  let cooldownTimer = null;
  let disposed = false;

  root.innerHTML = `<style>${STYLE}</style><section class="ob-page"><div class="ob-card" data-card></div></section>`;
  const card = root.querySelector('[data-card]');

  function currentUser() {
    return ctx.state?.user || ctx.user;
  }

  function clearCooldown() {
    if (cooldownTimer) clearInterval(cooldownTimer);
    cooldownTimer = null;
  }

  function startCooldown(button, seconds) {
    clearCooldown();
    let remaining = Math.max(1, Number(seconds) || 120);
    const label = button.dataset.originalText || button.textContent;
    button.disabled = true;
    const tick = () => {
      if (disposed) return;
      if (remaining <= 0) {
        clearCooldown();
        button.disabled = false;
        button.textContent = label;
        return;
      }
      button.textContent = `${remaining}s 后重发`;
      remaining -= 1;
    };
    tick();
    cooldownTimer = setInterval(tick, 1000);
  }

  function renderPage() {
    clearCooldown();
    const user = currentUser();
    if (!user) {
      ctx.navigate(`/login?next=${encodeURIComponent('/onboarding')}`);
      return;
    }
    if (!user.onboardingRequired) {
      ctx.navigate(next);
      return;
    }
    const emailDone = Boolean(user.emailVerified);
    const passwordDone = !user.mustChangePassword;
    card.innerHTML = `
      <header class="ob-head">
        <p class="eyebrow">ACCOUNT ACTIVATION</p>
        <h1>完成账号激活</h1>
        <p>只要使用初始密码登录，都必须先验证邮箱并修改密码。完成后即可正常使用站点与联机认证。</p>
      </header>
      <div class="ob-progress">
        <div class="ob-step ${emailDone ? 'is-done' : ''}"><span>${emailDone ? '✓' : '1'}</span>邮箱验证</div>
        <div class="ob-step ${passwordDone ? 'is-done' : ''}"><span>${passwordDone ? '✓' : '2'}</span>修改密码</div>
      </div>
      <section class="ob-block ${emailDone ? 'is-done' : ''}">
        <div class="ob-block-head">
          <h2>邮箱验证</h2>
          ${emailDone ? '<span class="ob-done">已完成</span>' : ''}
        </div>
        ${
          emailDone
            ? `<p>当前邮箱 <strong>${ui.escapeHtml(user.email || '')}</strong> 已验证。</p>`
            : `
              <label class="field">
                <span>邮箱</span>
                <input type="email" data-email value="${ui.escapeHtml(user.email || '')}" placeholder="you@example.com" autocomplete="email" />
              </label>
              <div class="ob-inline">
                <button class="btn btn-ghost btn-sm" type="button" data-send-code>发送验证码</button>
                <span class="ob-hint">验证码 10 分钟内有效，120 秒后可重发</span>
              </div>
              <label class="field">
                <span>邮箱验证码</span>
                <div class="input-with-button">
                  <input type="text" data-code inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" placeholder="6 位数字验证码" />
                  <button class="btn btn-primary btn-sm" type="button" data-verify-email>验证邮箱</button>
                </div>
              </label>
              <p class="field-hint">如果一直收不到验证码，请联系管理员确认邮件服务是否启用。</p>
            `
        }
      </section>
      <section class="ob-block ${passwordDone ? 'is-done' : ''}">
        <div class="ob-block-head">
          <h2>修改初始密码</h2>
          ${passwordDone ? '<span class="ob-done">已完成</span>' : ''}
        </div>
        ${
          passwordDone
            ? '<p>初始密码已修改，账号已启用。</p>'
            : `
              <label class="field">
                <span>当前密码</span>
                <input type="password" data-current autocomplete="current-password" placeholder="请输入初始密码" />
              </label>
              <label class="field">
                <span>新密码</span>
                <input type="password" data-new autocomplete="new-password" placeholder="至少 6 位，包含大小写字母、数字中的至少两类" />
              </label>
              <label class="field">
                <span>确认新密码</span>
                <input type="password" data-confirm autocomplete="new-password" placeholder="再次输入新密码" />
              </label>
              <button class="btn btn-primary" type="button" data-change-password>修改密码</button>
            `
        }
      </section>
      ${
        emailDone && passwordDone
          ? `<div class="ob-success">
              <strong>${icon('check')} 账号激活完成</strong>
              <button class="btn btn-primary" type="button" data-finish>进入星空社</button>
            </div>`
          : ''
      }
      <p class="ob-foot">为保障账号安全，完成前只能访问邮箱验证与修改密码接口；启动器联机认证也会暂时拒绝该账号。</p>
    `;
    bind();
  }

  function bind() {
    const sendButton = card.querySelector('[data-send-code]');
    const verifyButton = card.querySelector('[data-verify-email]');
    const changeButton = card.querySelector('[data-change-password]');
    const finishButton = card.querySelector('[data-finish]');
    const emailInput = card.querySelector('[data-email]');
    const codeInput = card.querySelector('[data-code]');

    sendButton?.addEventListener('click', async () => {
      const email = (emailInput?.value || '').trim().toLowerCase();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
        ui.toast('请输入有效的邮箱地址', 'error');
        emailInput?.focus();
        return;
      }
      ui.setButtonLoading(sendButton, true, '发送中…');
      try {
        const data = await api.post('/account/email-code', { email });
        ui.setButtonLoading(sendButton, false);
        startCooldown(sendButton, Number(data?.cooldown) || 120);
        ui.toast('验证码已发送，请查收邮箱', 'success');
        codeInput?.focus();
      } catch (error) {
        ui.setButtonLoading(sendButton, false);
        if (error.status === 429 && error.details?.retryAfter) {
          startCooldown(sendButton, Number(error.details.retryAfter) || 120);
        }
        ui.toast(error.message || '验证码发送失败', 'error');
      }
    });

    verifyButton?.addEventListener('click', async () => {
      const email = (emailInput?.value || '').trim().toLowerCase();
      const code = (codeInput?.value || '').trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
        ui.toast('请输入有效的邮箱地址', 'error');
        return;
      }
      if (!/^\d{6}$/.test(code)) {
        ui.toast('请输入 6 位数字验证码', 'error');
        return;
      }
      ui.setButtonLoading(verifyButton, true, '验证中…');
      try {
        await api.post('/account/email-verify', { email, code });
        await ctx.refreshAuth();
        ui.toast('邮箱验证成功', 'success');
        renderPage();
      } catch (error) {
        ui.setButtonLoading(verifyButton, false);
        ui.toast(error.message || '邮箱验证失败', 'error');
      }
    });

    changeButton?.addEventListener('click', async () => {
      const current = card.querySelector('[data-current]')?.value || '';
      const nextPassword = card.querySelector('[data-new]')?.value || '';
      const confirmPassword = card.querySelector('[data-confirm]')?.value || '';
      if (!current) {
        ui.toast('请输入当前密码', 'error');
        return;
      }
      if (nextPassword.length < 6) {
        ui.toast('新密码不能少于 6 位', 'error');
        return;
      }
      if (nextPassword !== confirmPassword) {
        ui.toast('两次输入的新密码不一致', 'error');
        return;
      }
      if (nextPassword === current) {
        ui.toast('新密码不能与初始密码相同', 'error');
        return;
      }
      ui.setButtonLoading(changeButton, true, '修改中…');
      try {
        await api.post('/account/password', {
          currentPassword: current,
          newPassword: nextPassword,
        });
        await ctx.refreshAuth();
        ui.toast('密码已修改', 'success');
        renderPage();
      } catch (error) {
        ui.setButtonLoading(changeButton, false);
        ui.toast(error.message || '密码修改失败', 'error');
      }
    });

    finishButton?.addEventListener('click', () => ctx.navigate(next));
  }

  renderPage();

  return () => {
    disposed = true;
    clearCooldown();
  };
}
