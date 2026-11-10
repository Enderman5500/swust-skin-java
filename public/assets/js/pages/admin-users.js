import { api } from '../api.js';
import { bindPager, pagerHtml } from '../admin-pagination.js';
import { can, displayName, userStatusLabel } from '../authz-client.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

function roleChips(user) {
  if (!user.roles?.length) return '<span class="role-chip muted">无角色</span>';
  return user.roles
    .map(
      (role) => `
      <span class="role-chip" style="border-color:${ui.escapeHtml(role.color || '#64748b')}66;color:${ui.escapeHtml(
        role.color || '#64748b'
      )}">
        <i class="role-dot" style="background:${ui.escapeHtml(role.color || '#64748b')}"></i>
        ${ui.escapeHtml(role.name)}
      </span>
    `
    )
    .join('');
}

function statusPill(status) {
  const active = status === 'ACTIVE';
  return `<span class="status-pill ${active ? 'online' : 'offline'}"><span class="status-dot"></span>${userStatusLabel(
    status
  )}</span>`;
}

function canManageTarget(operator, target) {
  return Boolean(operator?.wildcard) || Number(operator?.levelWeight || 0) >= Number(target?.levelWeight || 0);
}

function passwordError(password) {
  if (String(password).length < 6) return '密码长度不能少于 6 位';
  const categories =
    Number(/[a-z]/.test(password)) + Number(/[A-Z]/.test(password)) + Number(/[0-9]/.test(password));
  if (categories < 2) return '密码必须包含小写字母、大写字母、数字中的至少两类';
  return null;
}

export async function renderUsers(main, ctx) {
  const state = { q: '', status: '', roleId: '', page: 1, pageSize: 10 };
  let roleOptions = [];
  let departments = [];
  let permissionCatalog = [];
  let rows = [];

  main.innerHTML = `
    <section class="admin-page-head">
      <div><p class="eyebrow">USERS</p><h2>用户管理</h2><p>按统一用户标准维护账号资料、角色、部门与登录状态。</p></div>
      <div class="admin-head-actions">
        ${can(ctx.user, 'system:user:create') ? `<button class="btn btn-primary" data-action="create-user">${icon('plus')}新建用户</button>` : ''}
      </div>
    </section>
    <div class="toolbar user-toolbar">
      <label class="search-box">${icon('search')}<input type="search" placeholder="搜索用户名、昵称、邮箱、手机或 QQ" data-search /></label>
      <div class="toolbar-right">
        <label class="select-box"><select data-status-filter>
          <option value="">全部状态</option>
          <option value="ACTIVE">正常</option>
          <option value="DISABLED">已停用</option>
        </select></label>
        <label class="select-box"><select data-role-filter><option value="">全部角色</option></select></label>
      </div>
    </div>
    <div class="panel table-panel" data-table>${ui.loadingBlock('正在加载用户…')}</div>
    <div data-pager-host></div>
  `;

  const tableHost = main.querySelector('[data-table]');
  const pagerHost = main.querySelector('[data-pager-host]');
  const search = main.querySelector('[data-search]');
  const statusFilter = main.querySelector('[data-status-filter]');
  const roleFilter = main.querySelector('[data-role-filter]');

  async function loadRoles() {
    try {
      const [rolesRes, deptsRes, permissionsRes] = await Promise.all([
        api.get('/admin/roles/options'),
        api.get('/admin/departments').catch(() => ({ items: [] })),
        api.get('/admin/permissions').catch(() => ({ items: [] })),
      ]);
      roleOptions = rolesRes.items || [];
      departments = deptsRes.items || [];
      permissionCatalog = permissionsRes.items || [];
      roleFilter.innerHTML = `
        <option value="">全部角色</option>
        ${roleOptions
          .map(
            (role) =>
              `<option value="${ui.escapeHtml(role.id)}">${ui.escapeHtml(role.name)}（${ui.escapeHtml(
                role.code
              )}）</option>`
          )
          .join('')}
      `;
    } catch {
      roleOptions = [];
      departments = [];
      permissionCatalog = [];
    }
  }

  async function load() {
    tableHost.innerHTML = ui.loadingBlock('正在加载用户…');
    try {
      const data = await api.get('/admin/users', {
        keyword: state.q,
        status: state.status,
        roleId: state.roleId,
        page: state.page,
        size: state.pageSize,
      });
      rows = data.items;
      tableHost.innerHTML = `
        <table class="data-table user-table">
          <thead><tr><th>用户</th><th>角色</th><th>部门</th><th>状态</th><th>角色额度</th><th>材质</th><th class="table-actions-col">操作</th></tr></thead>
          <tbody>
            ${data.items
              .map(
                (user) => `
                <tr data-user-id="${ui.escapeHtml(user.id)}">
                  <td data-label="用户">
                    <div class="user-cell">${ui.avatarHtml(displayName(user), {
                      size: 'sm',
                      avatar: user.avatar,
                    })}<div><strong>${ui.escapeHtml(displayName(user))}</strong><span>@${ui.escapeHtml(
                      user.username
                    )} · ${ui.escapeHtml(user.email || '未设置邮箱')}</span></div></div>
                  </td>
                  <td data-label="角色"><div class="role-chip-list">${roleChips(user)}</div></td>
                  <td data-label="部门">${ui.escapeHtml(user.deptNames?.join(' / ') || '未分配')}</td>
                  <td data-label="状态">${statusPill(user.status)}</td>
                  <td data-label="角色额度"><span class="num-pair">${user.playerCount} / ${user.playerLimit ?? 3}</span></td>
                  <td data-label="材质"><span class="num-pair">${user.textureCount}</span></td>
                  <td data-label="操作" class="table-actions">
                    ${
                      can(ctx.user, 'system:user:edit') && canManageTarget(ctx.user, user)
                        ? `<button class="btn btn-ghost btn-sm" data-action="edit-user" data-id="${ui.escapeHtml(
                            user.id
                          )}">${icon('edit')}编辑</button>`
                        : ''
                    }
                    ${
                      user.status === 'ACTIVE'
                        ? can(ctx.user, 'system:user:delete') && canManageTarget(ctx.user, user) && String(user.id) !== String(ctx.user.id)
                          ? `<button class="btn btn-ghost btn-sm danger-text" data-action="disable-user" data-id="${ui.escapeHtml(
                              user.id
                            )}">${icon('close')}停用</button>`
                          : ''
                        : can(ctx.user, 'system:user:edit') && canManageTarget(ctx.user, user)
                        ? `<button class="btn btn-ghost btn-sm" data-action="enable-user" data-id="${ui.escapeHtml(
                            user.id
                          )}">${icon('check')}启用</button>`
                        : ''
                    }
                    ${
                      can(ctx.user, 'system:user:purge') && canManageTarget(ctx.user, user) && String(user.id) !== String(ctx.user.id)
                        ? `<button class="btn btn-ghost btn-sm danger-text" data-action="purge-user" data-id="${ui.escapeHtml(
                            user.id
                          )}">${icon('trash')}永久删除</button>`
                        : ''
                    }
                  </td>
                </tr>
              `
              )
              .join('')}
          </tbody>
        </table>
        ${data.items.length ? '' : `<div class="empty-state compact"><span class="empty-icon">${icon(
          'users'
        )}</span><p>没有找到匹配的用户</p></div>`}
      `;
      pagerHost.innerHTML = pagerHtml(data.page, data.pages, data.total, state.pageSize);
      bindPager(
        pagerHost,
        (page) => {
          state.page = page;
          load();
        },
        (size) => {
          state.pageSize = size;
          state.page = 1;
          load();
        }
      );
    } catch (error) {
      tableHost.innerHTML = `<div class="empty-state compact"><p>${ui.escapeHtml(error.message)}</p></div>`;
    }
  }

  function roleCheckboxes(selected = [], { allowAll = false } = {}) {
    const available = roleOptions.filter(
      (role) => allowAll || Number(ctx.user.levelWeight || 0) >= Number(role.levelWeight || 0)
    );
    if (!available.length) {
      return '<p class="field-hint">没有可分配的角色，请先到「角色管理」创建。</p>';
    }
    return `
      <div class="role-check-list">
        ${available
          .map(
            (role) => `
            <label class="role-check">
              <input type="checkbox" name="roleIds" value="${ui.escapeHtml(role.id)}" ${
                selected.includes(String(role.id)) ? 'checked' : ''
              } />
              <span class="role-check-body">
                <i class="role-dot" style="background:${ui.escapeHtml(role.color || '#64748b')}"></i>
                <strong>${ui.escapeHtml(role.name)}</strong>
                <small>${ui.escapeHtml(role.code)} · ${ui.escapeHtml(role.deptName || '未分配部门')}${
                  role.systemType ? ' · 系统角色' : ''
                }</small>
              </span>
            </label>
          `
          )
          .join('')}
      </div>
    `;
  }

  function extraPermissionPicker(user) {
    if (user.wildcard) {
      return `<div class="permission-wildcard">${icon('shield')}超级管理员拥有通配权限，无需单独追加。</div>`;
    }
    if (!permissionCatalog.length) {
      return '<p class="field-hint">权限目录暂不可用。</p>';
    }
    const inherited = new Set((user.rolePermissions || []).map(String));
    const extra = new Set((user.extraPermissions || []).map(String));
    const operatorPermissions = new Set((ctx.user.permissions || []).map(String));
    const groups = new Map();
    for (const permission of permissionCatalog) {
      const code = String(permission.code);
      if (!inherited.has(code) && !extra.has(code) && !ctx.user.wildcard && !operatorPermissions.has(code)) {
        continue;
      }
      const module = permission.module || '其他';
      if (!groups.has(module)) groups.set(module, []);
      groups.get(module).push(permission);
    }
    return `
      <details class="permission-picker" ${extra.size ? 'open' : ''}>
        <summary>额外权限 <span>${extra.size} 项</span></summary>
        <p class="field-hint">在角色权限基础上单独追加。勾选后立即生效；角色继承的权限不可在这里取消。</p>
        ${[...groups.entries()]
          .map(
            ([module, items]) => `
          <div class="permission-group">
            <h4>${ui.escapeHtml(module)}</h4>
            <div class="permission-grid">
              ${items
                .map((permission) => {
                  const isInherited = inherited.has(String(permission.code));
                  const isExtra = extra.has(String(permission.code));
                  return `
                    <label class="permission-option${isInherited ? ' is-inherited' : ''}">
                      <input type="checkbox" name="extraPermissions" value="${ui.escapeHtml(permission.code)}"
                        ${isInherited || isExtra ? 'checked' : ''} ${isInherited ? 'disabled' : ''} />
                      <span>
                        <strong>${ui.escapeHtml(permission.name)}${
                          isInherited ? ' <em>角色继承</em>' : ''
                        }</strong>
                        <small>${ui.escapeHtml(permission.code)}</small>
                      </span>
                    </label>
                  `;
                })
                .join('')}
            </div>
          </div>
        `
          )
          .join('')}
      </details>
    `;
  }

  function openEditor(user = null) {
    const isEdit = Boolean(user);
    const content = document.createElement('div');
    const selectedRoles = user?.roleIds?.map(String) || [
      roleOptions.find((role) => role.code === 'user')?.id,
    ].filter(Boolean);
    content.innerHTML = `
      <form class="form-stack" data-user-form>
        <div class="form-grid two">
          <label class="field"><span>用户名</span><input name="username" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]{3,16}" value="${ui.escapeHtml(
            user?.username || ''
          )}" placeholder="3–16 位字母、数字或下划线" /></label>
          <label class="field"><span>昵称</span><input name="nickname" maxlength="64" value="${ui.escapeHtml(
            user?.nickname || ''
          )}" placeholder="展示名称" /></label>
        </div>
        <div class="form-grid two">
          <label class="field"><span>邮箱</span><input name="email" type="email" required value="${ui.escapeHtml(
            user?.email || ''
          )}" /></label>
          <label class="field"><span>手机号</span><input name="phone" maxlength="20" value="${ui.escapeHtml(
            user?.phone || ''
          )}" placeholder="可选，11 位手机号" /></label>
        </div>
        <div class="form-grid two">
          <label class="field"><span>QQ</span><input name="qq" maxlength="20" value="${ui.escapeHtml(
            user?.qq || ''
          )}" placeholder="可选" /></label>
          ${
            isEdit
              ? String(user.id) !== String(ctx.user.id)
                ? `<label class="field"><span>重置密码（可选）</span><input name="password" type="password" minlength="6" placeholder="留空则不修改，至少 6 位" /></label>`
                : ''
              : `<label class="field"><span>初始密码</span><input name="password" type="password" required minlength="6" placeholder="至少 6 位，字母数字组合" /></label>`
          }
        </div>
        <label class="switch-field">
          <span><strong>邮箱已验证</strong><small>对应用户字段 emailVerified。</small></span>
          <input type="checkbox" name="emailVerified" ${user?.emailVerified !== false ? 'checked' : ''} />
        </label>
        <label class="field">
          <span>可创建角色数</span>
          <input name="playerLimit" type="number" min="0" max="100" step="1" required value="${ui.escapeHtml(
            String(user?.playerLimit ?? 3)
          )}" />
          <small class="field-hint">默认 3，范围 0–100；设为 0 表示禁止创建新角色。</small>
        </label>
        <div class="field">
          <span>角色</span>
          ${roleCheckboxes(selectedRoles, { allowAll: ctx.user.wildcard })}
          <small class="field-hint">角色所需的部门会自动加入用户部门；当前部门：${ui.escapeHtml(
            user?.deptNames?.join(' / ') || '根部门（新建时）'
          )}</small>
        </div>
        ${isEdit ? extraPermissionPicker(user) : ''}
        <p class="field-hint">密码规则：至少 6 位，且包含小写字母、大写字母、数字中的至少两类。</p>
      </form>
    `;
    const modal = ui.openModal({
      title: isEdit ? `编辑用户 · ${displayName(user)}` : '新建用户',
      content,
      size: 'md',
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: isEdit ? '保存修改' : '创建用户',
          className: 'btn-primary',
          onClick: async ({ close, button }) => {
            const form = content.querySelector('form');
            if (!form.reportValidity()) return false;
            const password = form.elements.password?.value || '';
            if (!isEdit || password) {
              const pwdError = passwordError(password);
              if (pwdError) {
                ui.toast(pwdError, 'error');
                return false;
              }
            }
            const roleIds = [...form.querySelectorAll('input[name="roleIds"]:checked')].map(
              (input) => input.value
            );
            const payload = {
              username: form.elements.username.value.trim(),
              nickname: form.elements.nickname.value.trim(),
              email: form.elements.email.value.trim(),
              phone: form.elements.phone.value.trim(),
              qq: form.elements.qq.value.trim(),
              emailVerified: form.elements.emailVerified.checked,
              playerLimit: Number(form.elements.playerLimit.value),
            };
            ui.setButtonLoading(button, true, '保存中…');
            try {
              if (!isEdit) {
                await api.post('/admin/users', { ...payload, password, roleIds });
              } else {
                await api.patch(`/admin/users/${user.id}`, payload);
                const before = [...user.roleIds].map(String).sort().join(',');
                const after = [...roleIds].sort().join(',');
                if (before !== after) {
                  await api.put(`/admin/users/${user.id}/roles`, { roleIds });
                }
                if (password) {
                  await api.put(`/admin/users/${user.id}/password`, { password });
                }
                if (!user.wildcard && permissionCatalog.length) {
                  const extraPermissions = [
                    ...form.querySelectorAll('input[name="extraPermissions"]:checked:not(:disabled)'),
                  ].map((input) => input.value);
                  await api.put(`/admin/users/${user.id}/permissions`, {
                    permissions: extraPermissions,
                  });
                }
                if (String(user.id) === String(ctx.user.id)) await ctx.refreshAuth();
              }
              ui.setButtonLoading(button, false);
              ui.toast(isEdit ? '用户资料已更新' : '用户已创建', 'success');
              close();
              state.page = isEdit ? state.page : 1;
              await load();
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
    setTimeout(() => content.querySelector('input[name="username"]')?.focus(), 40);
    return modal;
  }


  search.addEventListener(
    'input',
    ui.debounce(() => {
      state.q = search.value.trim();
      state.page = 1;
      load();
    }, 300)
  );
  statusFilter.addEventListener('change', () => {
    state.status = statusFilter.value;
    state.page = 1;
    load();
  });
  roleFilter.addEventListener('change', () => {
    state.roleId = roleFilter.value;
    state.page = 1;
    load();
  });

  async function onMainClick(event) {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const action = button.dataset.action;
    if (action === 'create-user') {
      openEditor();
      return;
    }
    const user = rows.find((item) => String(item.id) === String(button.dataset.id));
    if (!user) return;
    if (action === 'edit-user') {
      openEditor(user);
    } else if (action === 'disable-user') {
      const confirmed = await ui.confirmDialog({
        title: '停用账号',
        message: `停用「${displayName(user)}」后，其网页会话与联机令牌会立即失效；账号数据保留，可再次启用。`,
        confirmText: '停用',
        danger: true,
      });
      if (!confirmed) return;
      try {
        await api.del(`/admin/users/${user.id}`);
        ui.toast('账号已停用', 'success');
        await load();
      } catch (error) {
        ui.toast(error.message, 'error');
      }
    } else if (action === 'enable-user') {
      try {
        await api.post(`/admin/users/${user.id}/enable`);
        ui.toast('账号已启用', 'success');
        await load();
      } catch (error) {
        ui.toast(error.message, 'error');
      }
    } else if (action === 'purge-user') {
      const confirmed = await ui.confirmDialog({
        title: '永久删除用户',
        message: `将永久删除「${displayName(user)}」及其角色、材质归属、衣柜与令牌，且不可恢复。`,
        confirmText: '永久删除',
        danger: true,
      });
      if (!confirmed) return;
      try {
        await api.del(`/admin/users/${user.id}/permanent`);
        ui.toast('用户已永久删除', 'success');
        await load();
      } catch (error) {
        ui.toast(error.message, 'error');
      }
    }
  }
  main.addEventListener('click', onMainClick);

  await loadRoles();
  await load();

  return () => main.removeEventListener('click', onMainClick);
}
