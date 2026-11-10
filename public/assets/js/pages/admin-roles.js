import { api } from '../api.js';
import { bindPager, pagerHtml } from '../admin-pagination.js';
import { can, roleLevelLabel, roleStatusLabel } from '../authz-client.js';
import { icon } from '../icons.js';
import * as ui from '../ui.js';

function statusPill(status) {
  return `<span class="status-pill ${status === 'ACTIVE' ? 'online' : 'offline'}"><span class="status-dot"></span>${roleStatusLabel(
    status
  )}</span>`;
}

function canManageRole(operator, role) {
  return Boolean(operator?.wildcard) || Number(operator?.levelWeight || 0) >= Number(role?.levelWeight || 0);
}

const LEVELS = [
  { value: 'SUPER_ADMIN', label: '超级管理员', weight: 100 },
  { value: 'ADMIN', label: '管理员', weight: 80 },
  { value: 'USER', label: '用户', weight: 50 },
  { value: 'GUEST', label: '访客', weight: 20 },
];

export async function renderRoles(main, ctx) {
  const state = { q: '', status: '', deptId: '', page: 1, pageSize: 10 };
  let rows = [];
  let permissions = [];
  let departments = [];

  main.innerHTML = `
    <section class="admin-page-head">
      <div><p class="eyebrow">ROLES</p><h2>角色管理</h2><p>角色按部门归属，拥有等级权重与权限集合；系统角色受保护，普通角色可停用后再次启用。</p></div>
      <div class="admin-head-actions">
        ${can(ctx.user, 'system:role:create') ? `<button class="btn btn-primary" data-action="create-role">${icon('plus')}新建角色</button>` : ''}
      </div>
    </section>
    <div class="toolbar">
      <label class="search-box">${icon('search')}<input type="search" placeholder="搜索角色名称或编码" data-search /></label>
      <div class="toolbar-right">
        <label class="select-box"><select data-status-filter>
          <option value="">全部状态</option>
          <option value="ACTIVE">启用</option>
          <option value="DEPRECATED">已停用</option>
        </select></label>
        <label class="select-box"><select data-dept-filter><option value="">全部部门</option></select></label>
      </div>
    </div>
    <div class="panel table-panel" data-table>${ui.loadingBlock('正在加载角色…')}</div>
    <div data-pager-host></div>
  `;

  const tableHost = main.querySelector('[data-table]');
  const pagerHost = main.querySelector('[data-pager-host]');
  const search = main.querySelector('[data-search]');
  const statusFilter = main.querySelector('[data-status-filter]');
  const deptFilter = main.querySelector('[data-dept-filter]');

  async function loadMeta() {
    const [permissionsRes, departmentsRes] = await Promise.all([
      api.get('/admin/roles/permissions').catch(() => ({ items: [] })),
      api.get('/admin/departments').catch(() => ({ items: [] })),
    ]);
    permissions = permissionsRes.items || [];
    departments = departmentsRes.items || [];
    deptFilter.innerHTML = `
      <option value="">全部部门</option>
      ${departments
        .map(
          (dept) =>
            `<option value="${ui.escapeHtml(dept.id)}">${ui.escapeHtml(dept.name)}</option>`
        )
        .join('')}
    `;
  }

  async function load() {
    tableHost.innerHTML = ui.loadingBlock('正在加载角色…');
    try {
      const data = await api.get('/admin/roles', {
        keyword: state.q,
        status: state.status,
        deptId: state.deptId,
        page: state.page,
        size: state.pageSize,
      });
      rows = data.items;
      tableHost.innerHTML = `
        <table class="data-table role-table">
          <thead><tr><th>角色</th><th>部门</th><th>等级</th><th>权限</th><th>用户</th><th>状态</th><th class="table-actions-col">操作</th></tr></thead>
          <tbody>
            ${data.items
              .map(
                (role) => `
                <tr data-role-id="${ui.escapeHtml(role.id)}">
                  <td data-label="角色">
                    <div class="role-name-cell">
                      <i class="role-dot" style="background:${ui.escapeHtml(role.color || '#64748b')}"></i>
                      <div>
                        <strong>${ui.escapeHtml(role.name)}</strong>
                        <span>${ui.escapeHtml(role.code)}${
                          role.systemRole ? ` · 系统角色 ${ui.escapeHtml(role.systemType || '')}` : ''
                        }</span>
                      </div>
                    </div>
                  </td>
                  <td data-label="部门">${ui.escapeHtml(role.deptName || '未分配')}</td>
                  <td data-label="等级"><span class="role-level">${roleLevelLabel(role.level)}</span><small class="role-weight">${role.levelWeight}</small></td>
                  <td data-label="权限"><span class="role-permission-count">${role.permissions.includes('*') ? '通配 *' : `${role.permissionCount} 项`}</span></td>
                  <td data-label="用户">${role.userCount ?? 0}</td>
                  <td data-label="状态">${statusPill(role.status)}</td>
                  <td data-label="操作" class="table-actions">
                    ${can(ctx.user, 'system:role:edit') && canManageRole(ctx.user, role) ? `<button class="btn btn-ghost btn-sm" data-action="edit-role" data-id="${ui.escapeHtml(role.id)}">${icon('edit')}编辑</button>` : ''}
                    ${
                      role.status === 'ACTIVE'
                        ? can(ctx.user, 'system:role:delete') && canManageRole(ctx.user, role) && !role.systemRole
                          ? `<button class="btn btn-ghost btn-sm danger-text" data-action="disable-role" data-id="${ui.escapeHtml(role.id)}">${icon('close')}停用</button>`
                          : ''
                        : can(ctx.user, 'system:role:edit') && canManageRole(ctx.user, role)
                        ? `<button class="btn btn-ghost btn-sm" data-action="enable-role" data-id="${ui.escapeHtml(role.id)}">${icon('check')}启用</button>`
                        : ''
                    }
                  </td>
                </tr>
              `
              )
              .join('')}
          </tbody>
        </table>
        ${data.items.length ? '' : `<div class="empty-state compact"><span class="empty-icon">${icon('shield')}</span><p>没有找到匹配的角色</p></div>`}
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

  function permissionGroups(selected = [], disabled = false) {
    const groups = new Map();
    for (const permission of permissions) {
      if (!groups.has(permission.module)) groups.set(permission.module, []);
      groups.get(permission.module).push(permission);
    }
    const known = new Set(permissions.map((permission) => permission.code));
    const unknown = selected.filter((code) => !known.has(code));
    return `
      ${[...groups.entries()]
        .map(
          ([module, items]) => `
          <section class="permission-group">
            <h4>${ui.escapeHtml(module)}</h4>
            <div class="permission-grid">
              ${items
                .map(
                  (permission) => `
                  <label class="permission-option">
                    <input type="checkbox" name="permissions" value="${ui.escapeHtml(permission.code)}" ${
                      selected.includes(permission.code) ? 'checked' : ''
                    } ${disabled ? 'disabled' : ''} />
                    <span><strong>${ui.escapeHtml(permission.name)}</strong><small>${ui.escapeHtml(
                      permission.code
                    )}</small></span>
                  </label>
                `
                )
                .join('')}
            </div>
          </section>
        `
        )
        .join('')}
      ${
        unknown.length
          ? `<section class="permission-group">
              <h4>其他已授权权限</h4>
              <div class="permission-grid">${unknown
                .map(
                  (code) => `<code class="plugin-chips">${ui.escapeHtml(code)}</code>`
                )
                .join('')}</div>
            </section>`
          : ''
      }
    `;
  }

  function openEditor(role = null) {
    const isEdit = Boolean(role);
    const isWildcard = role?.systemType === 'SUPER_ADMIN' || role?.permissions?.includes('*');
    const content = document.createElement('div');
    const availableLevels = LEVELS.filter(
      (level) => ctx.user.wildcard || Number(ctx.user.levelWeight || 0) >= level.weight
    );
    content.innerHTML = `
      <form class="form-stack" data-role-form>
        <div class="form-grid two">
          <label class="field"><span>角色名称</span><input name="name" required maxlength="64" value="${ui.escapeHtml(
            role?.name || ''
          )}" placeholder="例如 皮肤审核员" /></label>
          <label class="field"><span>角色编码</span><input name="code" required maxlength="64" pattern="[A-Za-z][A-Za-z0-9_:-]{1,63}" value="${ui.escapeHtml(
            role?.code || ''
          )}" ${role?.systemRole ? 'disabled' : ''} placeholder="例如 skin_reviewer" /></label>
        </div>
        <div class="form-grid two">
          <label class="field"><span>所属部门</span>
            <select name="deptId" required>
              ${departments
                .map(
                  (dept) =>
                    `<option value="${ui.escapeHtml(dept.id)}" ${
                      String(role?.deptId) === String(dept.id) ? 'selected' : ''
                    }>${ui.escapeHtml(dept.name)}</option>`
                )
                .join('')}
            </select>
          </label>
          <label class="field"><span>角色等级</span>
            <select name="level" required>
              ${availableLevels
                .map(
                  (level) =>
                    `<option value="${level.value}" ${(role?.level || 'USER') === level.value ? 'selected' : ''}>${level.label}（${level.weight}）</option>`
                )
                .join('')}
            </select>
          </label>
        </div>
        <div class="form-grid two">
          <label class="field"><span>代表色</span><input name="color" type="color" value="${ui.escapeHtml(
            role?.color || '#64748b'
          )}" /></label>
          <label class="field"><span>图标名称</span><input name="icon" maxlength="64" value="${ui.escapeHtml(
            role?.icon || ''
          )}" placeholder="Morphicons，如 morph:shield" /></label>
        </div>
        <div class="field">
          <span>权限</span>
          ${
            isWildcard
              ? `<div class="permission-wildcard">${icon('shield')}通配权限 <code>*</code>：拥有站点全部权限，不可拆分编辑。</div>`
              : permissionGroups(role?.permissions || [])
          }
        </div>
        <p class="field-hint">系统角色的编码不可修改且不可停用；角色仍需被用户使用时不能停用。</p>
      </form>
    `;
    const modal = ui.openModal({
      title: isEdit ? `编辑角色 · ${role.name}` : '新建角色',
      content,
      size: 'lg',
      actions: [
        { label: '取消', className: 'btn-ghost' },
        {
          label: isEdit ? '保存修改' : '创建角色',
          className: 'btn-primary',
          onClick: async ({ close, button }) => {
            const form = content.querySelector('form');
            if (!form.reportValidity()) return false;
            const rolePermissions = isWildcard
              ? ['*']
              : [...form.querySelectorAll('input[name="permissions"]:checked')].map(
                  (input) => input.value
                );
            const payload = {
              name: form.elements.name.value.trim(),
              code: form.elements.code.value.trim(),
              deptId: form.elements.deptId.value,
              level: form.elements.level.value,
              color: form.elements.color.value,
              icon: form.elements.icon.value.trim(),
              permissions: rolePermissions,
            };
            ui.setButtonLoading(button, true, '保存中…');
            try {
              if (isEdit) await api.put(`/admin/roles/${role.id}`, payload);
              else await api.post('/admin/roles', payload);
              ui.setButtonLoading(button, false);
              ui.toast(isEdit ? '角色已更新' : '角色已创建', 'success');
              close();
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
    setTimeout(() => content.querySelector('input[name="name"]')?.focus(), 40);
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
  deptFilter.addEventListener('change', () => {
    state.deptId = deptFilter.value;
    state.page = 1;
    load();
  });

  async function onMainClick(event) {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    if (button.dataset.action === 'create-role') {
      openEditor();
      return;
    }
    const role = rows.find((item) => String(item.id) === String(button.dataset.id));
    if (!role) return;
    if (button.dataset.action === 'edit-role') {
      openEditor(role);
    } else if (button.dataset.action === 'disable-role') {
      const confirmed = await ui.confirmDialog({
        title: '停用角色',
        message: `停用「${role.name}」前会检查是否仍有用户使用。停用后可将角色再次启用。`,
        confirmText: '停用角色',
        danger: true,
      });
      if (!confirmed) return;
      try {
        await api.del(`/admin/roles/${role.id}`);
        ui.toast('角色已停用', 'success');
        await load();
      } catch (error) {
        ui.toast(error.message, 'error');
      }
    } else if (button.dataset.action === 'enable-role') {
      try {
        await api.post(`/admin/roles/${role.id}/enable`);
        ui.toast('角色已启用', 'success');
        await load();
      } catch (error) {
        ui.toast(error.message, 'error');
      }
    }
  }
  main.addEventListener('click', onMainClick);

  await loadMeta();
  await load();

  return () => main.removeEventListener('click', onMainClick);
}
