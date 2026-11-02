export function can(user, code) {
  if (!user?.isAdmin) return false;
  return Boolean(user.wildcard || user.permissions?.includes('*') || user.permissions?.includes(code));
}

export function displayName(user) {
  return user?.nickname || user?.username || '社员';
}

export function roleLevelLabel(level) {
  return (
    {
      SUPER_ADMIN: '超级管理员',
      ADMIN: '管理员',
      USER: '用户',
      GUEST: '访客',
    }[level] || level
  );
}

export function userStatusLabel(status) {
  return status === 'ACTIVE' ? '正常' : status === 'DISABLED' ? '已停用' : status;
}

export function roleStatusLabel(status) {
  return status === 'ACTIVE' ? '启用' : status === 'DEPRECATED' ? '已停用' : status;
}
