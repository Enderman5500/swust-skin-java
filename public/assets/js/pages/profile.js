import { api } from '../api.js';
import {
  bindTextureCards,
  mountTextureCards,
  openTextureDetail,
  textureCardHtml,
} from '../components/texture.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

export async function render(root, ctx) {
  root.innerHTML = ui.loadingBlock('正在读取社员档案…');

  let me;
  let uploads = { items: [], total: 0 };
  try {
    [me, uploads] = await Promise.all([
      api.get('/auth/me'),
      api.get('/textures', { scope: 'mine', perPage: 12, page: 1 }),
    ]);
    ctx.state.counts = me.counts;
  } catch (error) {
    root.innerHTML = ui.emptyState('档案读取失败', error.message, '<button class="btn btn-primary" data-retry>重试</button>');
    root.querySelector('[data-retry]')?.addEventListener('click', () => render(root, ctx));
    return;
  }

  const user = me.user;
  const yggdrasil = ctx.state.home?.yggdrasilUrl || `${location.origin}/api/yggdrasil`;
  const displayName = user.nickname || user.username;
  const roleNames = user.roleNames?.length ? user.roleNames : ['星空社社员'];
  const roleLabel = roleNames.join(' / ');

  root.innerHTML = `
    <section class="profile-hero">
      <div class="profile-identity">
        <span data-profile-avatar>${ui.avatarHtml(displayName, { size: 'xl', avatar: user.avatar })}</span>
        <div>
          <p class="eyebrow">MEMBER PROFILE</p>
          <h1>${ui.escapeHtml(displayName)}</h1>
          <p class="profile-email">@${ui.escapeHtml(user.username)} · ${ui.escapeHtml(user.email)}</p>
          <div class="profile-tags">
            <span class="role-chip">${ui.escapeHtml(roleLabel)}</span>
            <span class="role-chip muted">加入于 ${ui.formatDate(user.createTime)}</span>
          </div>
        </div>
      </div>
      <div class="profile-actions">
        <button class="btn btn-ghost" data-action="edit-profile">${icon('user')}编辑资料</button>
        <button class="btn btn-ghost" data-action="edit-email">${icon('edit')}修改邮箱</button>
        <button class="btn btn-ghost danger-text" data-action="logout">${icon('logout')}退出登录</button>
      </div>
    </section>

    <section class="profile-stats">
      <div class="profile-stat"><strong>${me.counts.players}</strong><span>角色</span></div>
      <div class="profile-stat"><strong>${me.counts.textures}</strong><span>上传材质</span></div>
      <div class="profile-stat"><strong>${me.counts.closet}</strong><span>衣柜收藏</span></div>
      <div class="profile-stat"><strong>${user.lastLoginTime ? ui.formatDate(user.lastLoginTime, { time: true }) : '首次登录'}</strong><span>最近登录</span></div>
    </section>

    <section class="profile-grid">
      <div class="profile-main">
        <div class="panel">
          <div class="panel-head">
            <div><p class="eyebrow">MY UPLOADS</p><h2>我的材质</h2></div>
            <a class="text-link" href="#/upload">上传新材质 ${icon('arrow')}</a>
          </div>
          ${
            uploads.items.length
              ? `<div class="skin-grid profile-uploads">${uploads.items
                  .map((item) => textureCardHtml(item, { compact: true }))
                  .join('')}</div>`
              : ui.emptyState(
                  '还没有上传过材质',
                  '上传皮肤或披风，它们会显示在这里。',
                  '<a class="btn btn-primary" href="#/upload">上传材质</a>'
                )
          }
          ${
            uploads.total > uploads.items.length
              ? `<a class="btn btn-ghost btn-block" href="#/library?scope=mine">查看全部 ${uploads.total} 件材质</a>`
              : ''
          }
        </div>

        <div class="panel">
          <div class="panel-head">
            <div><p class="eyebrow">CONNECT</p><h2>联机认证</h2></div>
            <span class="type-pill muted">Yggdrasil</span>
          </div>
          <p class="panel-lead">在 HMCL / PCL2 的第三方登录设置中添加下面这个认证服务器，即可使用本站皮肤进入社团服务器。</p>
          <div class="copy-field">
            <code>${ui.escapeHtml(yggdrasil)}</code>
            <button class="btn btn-ghost btn-sm" data-action="copy-ygg">${icon('copy')}复制</button>
          </div>
          <ul class="panel-list">
            <li>${icon('check')}登录账号为本站用户名或邮箱</li>
            <li>${icon('check')}角色名需与服务器中的游戏 ID 一致</li>
            <li>${icon('check')}皮肤与披风属性由站点私钥签名</li>
          </ul>
        </div>
      </div>

      <aside class="profile-side">
        <div class="panel">
          <div class="panel-head">
            <div><p class="eyebrow">SECURITY</p><h2>修改密码</h2></div>
          </div>
          <form class="form-stack" data-password-form>
            <label class="field">
              <span>当前密码</span>
              <input type="password" name="current" autocomplete="current-password" required />
            </label>
            <label class="field">
              <span>新密码</span>
              <input type="password" name="next" autocomplete="new-password" required minlength="6" />
            </label>
            <label class="field">
              <span>确认新密码</span>
              <input type="password" name="confirm" autocomplete="new-password" required minlength="6" />
            </label>
            <div class="form-error" data-password-error hidden></div>
            <button class="btn btn-primary btn-block" type="submit">更新密码</button>
            <p class="field-hint">修改密码后，其他设备上的登录会失效。</p>
          </form>
        </div>

        <div class="panel compact-panel">
          <div class="panel-head">
            <div><p class="eyebrow">SHORTCUTS</p><h2>快捷入口</h2></div>
          </div>
          <div class="shortcut-list">
            <a href="#/players">${icon('user')}管理我的角色${icon('chevron')}</a>
            <a href="#/closet">${icon('closet')}打开衣柜${icon('chevron')}</a>
            <a href="#/library">${icon('library')}浏览皮肤库${icon('chevron')}</a>
            ${user.isAdmin ? `<a href="#/admin">${icon('admin')}进入管理后台${icon('chevron')}</a>` : ''}
          </div>
        </div>
      </aside>
    </section>
  `;

  root.querySelector('[data-action="copy-ygg"]')?.addEventListener('click', () => {
    ui.copyText(yggdrasil, '认证服务器地址已复制');
  });

  root.querySelector('[data-action="logout"]')?.addEventListener('click', async () => {
    try {
      await api.post('/auth/logout');
    } catch {
      // 忽略
    }
    ctx.setAuth(null, null);
    ctx.navigate('/');
    ui.toast('已退出登录', 'info');
  });

  root.querySelector('[data-action="edit-profile"]')?.addEventListener('click', () => {
    const content = document.createElement('div');
    content.innerHTML = `
      <form class="form-stack">
        <div class="avatar-edit-row">
          <span data-avatar-preview>${ui.avatarHtml(displayName, { size: 'xl', avatar: user.avatar })}</span>
          <div>
            <label class="btn btn-ghost btn-sm upload-button">${icon('upload')}更换头像<input type="file" accept="image/png,image/jpeg,image/webp" hidden data-avatar-file /></label>
            <p class="field-hint">支持 PNG / JPG / WebP，最大 2MB，建议正方形。</p>
          </div>
        </div>
        <label class="field">
          <span>昵称</span>
          <input name="nickname" maxlength="64" value="${ui.escapeHtml(user.nickname || '')}" placeholder="展示在站点里的名字" />
          <small class="field-hint">留空将使用用户名。</small>
        </label>
      </form>
    `;

    const preview = content.querySelector('[data-avatar-preview]');
    const fileInput = content.querySelector('[data-avatar-file]');
    fileInput.addEventListener('change', async () => {
      const file = fileInput.files?.[0];
      if (!file) return;
      if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
        ui.toast('仅支持 PNG、JPG 或 WebP 图片', 'error');
        fileInput.value = '';
        return;
      }
      if (file.size > 2 * 1024 * 1024) {
        ui.toast('头像不能超过 2MB', 'error');
        fileInput.value = '';
        return;
      }
      try {
        const data = await api.upload('/account/avatar', await file.arrayBuffer(), file.type);
        user.avatar = data.user.avatar;
        user.avatarFileId = data.user.avatarFileId;
        const html = ui.avatarHtml(displayName, { size: 'xl', avatar: user.avatar });
        preview.innerHTML = html;
        const heroAvatar = root.querySelector('[data-profile-avatar]');
        if (heroAvatar) heroAvatar.innerHTML = html;
        await ctx.refreshAuth();
        ui.toast('头像已更新', 'success');
      } catch (error) {
        ui.toast(error.message, 'error');
      }
      fileInput.value = '';
    });

    ui.openModal({
      title: '编辑个人资料',
      content,
      size: 'sm',
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: '保存',
          className: 'btn-primary',
          onClick: async ({ close, button }) => {
            const nickname = content.querySelector('input[name="nickname"]').value.trim();
            ui.setButtonLoading(button, true, '保存中…');
            try {
              const data = await api.patch('/account', { nickname });
              ui.setButtonLoading(button, false);
              user.nickname = data.user.nickname;
              await ctx.refreshAuth();
              close();
              ui.toast('个人资料已更新', 'success');
              ctx.navigate('/profile');
            } catch (error) {
              ui.setButtonLoading(button, false);
              ui.toast(error.message, 'error');
              return false;
            }
            return true;
          },
        },
      ],
    });
  });

  root.querySelector('[data-action="edit-email"]')?.addEventListener('click', () => {
    const content = document.createElement('div');
    content.innerHTML = `
      <form class="form-stack">
        <label class="field">
          <span>邮箱地址</span>
          <input type="email" name="email" required value="${ui.escapeHtml(user.email)}" />
          <small class="field-hint">邮箱可用于登录与账号识别。</small>
        </label>
      </form>
    `;
    ui.openModal({
      title: '修改邮箱',
      content,
      size: 'sm',
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: '保存',
          className: 'btn-primary',
          onClick: async ({ close, button }) => {
            const email = content.querySelector('input[name="email"]').value.trim();
            ui.setButtonLoading(button, true, '保存中…');
            try {
              const data = await api.patch('/account', { email });
              ui.setButtonLoading(button, false);
              ui.toast('邮箱已更新', 'success');
              user.email = data.user.email;
              root.querySelector('.profile-email').textContent = `@${user.username} · ${user.email}`;
              await ctx.refreshAuth();
              close();
            } catch (error) {
              ui.setButtonLoading(button, false);
              ui.toast(error.message, 'error');
              return false;
            }
            return true;
          },
        },
      ],
    });
  });

  const passwordForm = root.querySelector('[data-password-form]');
  const passwordError = root.querySelector('[data-password-error]');
  passwordForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    passwordError.hidden = true;
    const current = passwordForm.elements.current.value;
    const next = passwordForm.elements.next.value;
    const confirm = passwordForm.elements.confirm.value;
    if (next.length < 6) {
      passwordError.hidden = false;
      passwordError.textContent = '新密码长度不能少于 6 位';
      return;
    }
    const categories =
      Number(/[a-z]/.test(next)) + Number(/[A-Z]/.test(next)) + Number(/[0-9]/.test(next));
    if (categories < 2) {
      passwordError.hidden = false;
      passwordError.textContent = '密码必须包含小写字母、大写字母、数字中的至少两类';
      return;
    }
    if (next !== confirm) {
      passwordError.hidden = false;
      passwordError.textContent = '两次输入的新密码不一致';
      return;
    }
    const button = passwordForm.querySelector('button[type="submit"]');
    ui.setButtonLoading(button, true, '更新中…');
    try {
      await api.post('/account/password', { currentPassword: current, newPassword: next });
      ui.setButtonLoading(button, false);
      passwordForm.reset();
      ui.toast('密码已更新，其他设备已退出登录', 'success');
    } catch (error) {
      ui.setButtonLoading(button, false);
      passwordError.hidden = false;
      passwordError.textContent = error.message;
    }
  });

  const unbindCards = bindTextureCards(root, (id) => {
    openTextureDetail(ctx, id, { onChanged: () => ctx.navigate('/profile') });
  });
  await mountTextureCards(root);

  return () => unbindCards();
}
