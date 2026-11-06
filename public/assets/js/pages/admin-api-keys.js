import { api } from '../api.js';
import { can } from '../authz-client.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

function emptyState(text) {
  return `<div class="empty-state compact"><span class="empty-icon">${icon('key')}</span><p>${ui.escapeHtml(
    text
  )}</p></div>`;
}

function expiryLabel(value) {
  if (!value) return '永久有效';
  const expired = new Date(value).getTime() <= Date.now();
  return expired ? '已过期' : ui.formatDate(value);
}

function permissionGroupsHtml(items) {
  const groups = new Map();
  for (const item of items) {
    const module = item.module || '其他';
    if (!groups.has(module)) groups.set(module, []);
    groups.get(module).push(item);
  }
  return [...groups.entries()]
    .map(
      ([module, permissions]) => `
      <div class="permission-group">
        <h4>${ui.escapeHtml(module)}</h4>
        <div class="permission-grid">
          ${permissions
            .map(
              (permission) => `
            <label class="permission-option">
              <input type="checkbox" name="permission" value="${ui.escapeHtml(permission.code)}" />
              <span>
                <strong>${ui.escapeHtml(permission.name)}</strong>
                <small>${ui.escapeHtml(permission.code)}${
                  permission.description ? ` · ${ui.escapeHtml(permission.description)}` : ''
                }</small>
              </span>
            </label>
          `
            )
            .join('')}
        </div>
      </div>
    `
    )
    .join('');
}

export async function renderApiKeys(main, ctx) {
  main.innerHTML = ui.loadingBlock('正在读取 API 密钥…');
  let data;
  try {
    data = await api.get('/admin/api-keys');
  } catch (error) {
    main.innerHTML = `<div class="empty-state compact"><p>${ui.escapeHtml(error.message)}</p></div>`;
    return;
  }
  const canEdit = can(ctx.user, 'system:user:edit');
  main.innerHTML = `
    <section class="admin-page-head">
      <div><p class="eyebrow">API KEYS</p><h2>API 密钥</h2><p>为外部服务或插件签发受限密钥。密钥只在创建时显示一次，请妥善保存。</p></div>
      <div class="admin-head-actions">
        ${canEdit ? `<button class="btn btn-primary" data-action="create-key">${icon('plus')}新建 API 密钥</button>` : ''}
      </div>
    </section>
    <div class="panel table-panel">
      ${
        data.items.length
          ? `<table class="data-table api-key-table">
              <thead><tr><th>名称</th><th>所有者</th><th>密钥前缀</th><th>权限</th><th>最近使用</th><th>有效期</th><th>操作</th></tr></thead>
              <tbody>
                ${data.items
                  .map(
                    (item) => `
                  <tr>
                    <td data-label="名称"><strong>${ui.escapeHtml(item.name)}</strong></td>
                    <td data-label="所有者">${ui.escapeHtml(
                      item.owner?.nickname || item.owner?.username || '已注销用户'
                    )}<small class="field-hint"> @${ui.escapeHtml(item.owner?.username || '')}</small></td>
                    <td data-label="密钥前缀"><code>${ui.escapeHtml(item.prefix)}…</code></td>
                    <td data-label="权限">${item.permissions.length} 项</td>
                    <td data-label="最近使用">${item.lastUsedAt ? ui.formatDate(item.lastUsedAt, { time: true }) : '从未使用'}</td>
                    <td data-label="有效期">${ui.escapeHtml(expiryLabel(item.expiresAt))}</td>
                    <td data-label="操作" class="table-actions">
                      ${
                        canEdit
                          ? `<button class="btn btn-ghost btn-sm danger-text" data-revoke="${ui.escapeHtml(
                              item.id
                            )}">${icon('trash')}撤销</button>`
                          : ''
                      }
                    </td>
                  </tr>
                `
                  )
                  .join('')}
              </tbody>
            </table>`
          : emptyState('还没有 API 密钥')
      }
    </div>
    <div class="panel api-key-help">
      <div class="panel-head"><div><p class="eyebrow">USAGE</p><h3>如何使用</h3></div></div>
      <p class="panel-lead">请求时携带以下任一请求头：</p>
      <pre class="code-block small">Authorization: Bearer sk_xxxxxxxxx
X-API-Key: sk_xxxxxxxxx</pre>
      <p class="field-hint">API 密钥按创建时勾选的权限访问对应接口，包括管理接口与插件接口。插件安装后会自动出现新的插件权限；密钥不能访问账号安全设置或代表其他用户操作。</p>
    </div>
  `;

  async function openCreate() {
    let users = [];
    try {
      const result = await api.get('/admin/api-keys/users');
      users = result.items || [];
    } catch (error) {
      ui.toast(error.message, 'error');
      return;
    }
    if (!users.length) {
      ui.toast('没有可用的用户', 'error');
      return;
    }
    const content = document.createElement('div');
    content.innerHTML = `
      <form class="form-stack">
        <label class="field"><span>密钥名称</span><input name="name" maxlength="60" required placeholder="例如 社团机器人" /></label>
        <label class="field"><span>所属用户</span>
          <select name="userId" data-user-select required>
            ${users
              .map(
                (user) =>
                  `<option value="${ui.escapeHtml(user.id)}">${ui.escapeHtml(
                    user.nickname || user.username
                  )}（@${ui.escapeHtml(user.username)}）</option>`
              )
              .join('')}
          </select>
        </label>
        <label class="field"><span>有效期</span>
          <select name="expiresInDays">
            <option value="30">30 天</option>
            <option value="90" selected>90 天</option>
            <option value="365">365 天</option>
            <option value="0">永久</option>
          </select>
        </label>
        <div class="field">
          <span>权限</span>
          <div class="permission-picker">
            <div class="permission-picker-head">
              <strong data-permission-count>正在读取…</strong>
              <div class="permission-picker-actions">
                <button type="button" class="link-button" data-permission-all>全选</button>
                <button type="button" class="link-button" data-permission-none>清空</button>
              </div>
            </div>
            <div class="permission-picker-body" data-permission-groups>${ui.loadingBlock(
              '正在读取可用权限…'
            )}</div>
          </div>
          <small class="field-hint">权限会随插件安装自动更新；只能授予该用户当前拥有的权限。</small>
        </div>
        <p class="field-hint">密钥创建后只显示一次。外部服务只能使用这里勾选的权限。</p>
      </form>
    `;

    const groupsHost = content.querySelector('[data-permission-groups]');
    const countLabel = content.querySelector('[data-permission-count]');
    let permissionRequest = 0;

    function updatePermissionCount() {
      const boxes = [...groupsHost.querySelectorAll('input[name="permission"]')];
      const checked = boxes.filter((box) => box.checked).length;
      countLabel.textContent = boxes.length ? `已选 ${checked} / ${boxes.length} 项` : '没有可授予的权限';
    }

    async function loadPermissions(userId) {
      const token = ++permissionRequest;
      groupsHost.innerHTML = ui.loadingBlock('正在读取可用权限…');
      countLabel.textContent = '正在读取…';
      try {
        const data = await api.get('/admin/api-keys/permissions', { userId });
        if (token !== permissionRequest) return;
        const items = (data.items || []).filter((item) => item.granted);
        groupsHost.innerHTML = items.length
          ? permissionGroupsHtml(items)
          : `<div class="empty-state compact"><p>该用户当前没有可授予的权限</p></div>`;
        updatePermissionCount();
      } catch (error) {
        if (token !== permissionRequest) return;
        groupsHost.innerHTML = `<div class="empty-state compact"><p>${ui.escapeHtml(
          error.message
        )}</p></div>`;
        countLabel.textContent = '权限读取失败';
      }
    }

    groupsHost.addEventListener('change', updatePermissionCount);
    content.querySelector('[data-permission-all]')?.addEventListener('click', () => {
      groupsHost.querySelectorAll('input[name="permission"]').forEach((box) => {
        box.checked = true;
      });
      updatePermissionCount();
    });
    content.querySelector('[data-permission-none]')?.addEventListener('click', () => {
      groupsHost.querySelectorAll('input[name="permission"]').forEach((box) => {
        box.checked = false;
      });
      updatePermissionCount();
    });
    content.querySelector('[data-user-select]')?.addEventListener('change', (event) => {
      loadPermissions(event.target.value);
    });
    loadPermissions(users[0].id);

    ui.openModal({
      title: '新建 API 密钥',
      content,
      size: 'md',
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: '创建密钥',
          className: 'btn-primary',
          onClick: async ({ close, button }) => {
            const form = content.querySelector('form');
            if (!form.reportValidity()) return false;
            const permissions = [
              ...content.querySelectorAll('input[name="permission"]:checked'),
            ].map((input) => input.value);
            if (!permissions.length) {
              ui.toast('请至少选择一项权限', 'error');
              return false;
            }
            ui.setButtonLoading(button, true, '创建中…');
            try {
              const result = await api.post('/admin/api-keys', {
                name: form.elements.name.value.trim(),
                userId: form.elements.userId.value,
                scope: 'custom',
                permissions,
                expiresInDays: Number(form.elements.expiresInDays.value),
              });
              ui.setButtonLoading(button, false);
              close();
              showToken(result.token, result.apiKey);
              await renderApiKeys(main, ctx);
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
  }

  function showToken(token, key) {
    const content = document.createElement('div');
    content.innerHTML = `
      <div class="api-key-token">
        <p class="panel-lead">请立即复制并妥善保存。关闭窗口后，完整密钥将无法再次查看。</p>
        <div class="copy-field"><code>${ui.escapeHtml(token)}</code><button class="btn btn-ghost btn-sm" data-copy-token>${icon(
          'copy'
        )}复制</button></div>
        <p class="field-hint">名称：${ui.escapeHtml(key?.name || 'API 密钥')} · 权限 ${key?.permissions?.length || 0} 项</p>
      </div>
    `;
    ui.openModal({
      title: 'API 密钥已创建',
      content,
      size: 'md',
      actions: [{ label: '我已经保存', className: 'btn-primary' }],
    });
    content.querySelector('[data-copy-token]')?.addEventListener('click', () => {
      ui.copyText(token, 'API 密钥已复制');
    });
  }

  main.querySelector('[data-action="create-key"]')?.addEventListener('click', openCreate);
  main.querySelectorAll('[data-revoke]').forEach((button) => {
    button.addEventListener('click', async () => {
      const confirmed = await ui.confirmDialog({
        title: '撤销 API 密钥',
        message: '撤销后使用该密钥的外部服务会立即失效，确定继续吗？',
        confirmText: '撤销',
        danger: true,
      });
      if (!confirmed) return;
      try {
        await api.del(`/admin/api-keys/${encodeURIComponent(button.dataset.revoke)}`);
        ui.toast('API 密钥已撤销', 'success');
        await renderApiKeys(main, ctx);
      } catch (error) {
        ui.toast(error.message, 'error');
      }
    });
  });
}
