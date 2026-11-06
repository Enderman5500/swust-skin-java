CREATE TABLE IF NOT EXISTS departments (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT,
  leader_id TEXT,
  phone TEXT,
  avatar_file_id TEXT,
  color TEXT,
  parent_id TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  dept_type TEXT NOT NULL DEFAULT 'NORMAL',
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  version INTEGER NOT NULL DEFAULT 0,
  create_time TEXT NOT NULL,
  update_time TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_departments_parent ON departments(parent_id);

CREATE TABLE IF NOT EXISTS permissions (
  id TEXT PRIMARY KEY,
  code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  module TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  source TEXT NOT NULL DEFAULT 'ANNOTATION',
  version INTEGER NOT NULL DEFAULT 0,
  create_time TEXT NOT NULL,
  update_time TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_permissions_module ON permissions(module);

CREATE TABLE IF NOT EXISTS roles (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  dept_id TEXT,
  code TEXT NOT NULL UNIQUE,
  level TEXT NOT NULL DEFAULT 'USER',
  system_role INTEGER NOT NULL DEFAULT 0,
  system_type TEXT,
  icon TEXT,
  color TEXT,
  permissions TEXT NOT NULL DEFAULT '[]',
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  version INTEGER NOT NULL DEFAULT 0,
  create_time TEXT NOT NULL,
  update_time TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_roles_dept ON roles(dept_id);
CREATE INDEX IF NOT EXISTS idx_roles_status ON roles(status);

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  nickname TEXT,
  email TEXT UNIQUE,
  phone TEXT,
  qq TEXT,
  password TEXT NOT NULL,
  avatar_file_id TEXT,
  email_verified INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  tags TEXT NOT NULL DEFAULT '[]',
  fields TEXT NOT NULL DEFAULT '{}',
  version INTEGER NOT NULL DEFAULT 0,
  create_time TEXT NOT NULL,
  update_time TEXT NOT NULL,
  last_login_time TEXT,
  password_changed_time TEXT
);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_create_time ON users(create_time);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id TEXT NOT NULL,
  role_id TEXT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_id);

CREATE TABLE IF NOT EXISTS user_depts (
  user_id TEXT NOT NULL,
  dept_id TEXT NOT NULL,
  is_default INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, dept_id)
);
CREATE INDEX IF NOT EXISTS idx_user_depts_dept ON user_depts(dept_id);

CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  csrf TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  ip TEXT,
  user_agent TEXT
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS textures (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  hash TEXT NOT NULL UNIQUE,
  type TEXT NOT NULL,
  model TEXT NOT NULL DEFAULT 'classic',
  name TEXT NOT NULL,
  width INTEGER NOT NULL,
  height INTEGER NOT NULL,
  is_public INTEGER NOT NULL DEFAULT 1,
  uploaded_by TEXT,
  created_at TEXT NOT NULL,
  downloads INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_textures_public ON textures(is_public, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_textures_owner ON textures(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_textures_type ON textures(type, is_public);

CREATE TABLE IF NOT EXISTS players (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL,
  name TEXT NOT NULL UNIQUE,
  uuid TEXT NOT NULL UNIQUE,
  model TEXT NOT NULL DEFAULT 'classic',
  skin_texture_id INTEGER,
  cape_texture_id INTEGER,
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_players_user ON players(user_id);

CREATE TABLE IF NOT EXISTS closet_items (
  user_id TEXT NOT NULL,
  texture_id INTEGER NOT NULL,
  name TEXT,
  created_at TEXT NOT NULL,
  PRIMARY KEY (user_id, texture_id)
);
CREATE INDEX IF NOT EXISTS idx_closet_texture ON closet_items(texture_id);

CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS plugins (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  version TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  author TEXT NOT NULL DEFAULT '',
  enabled INTEGER NOT NULL DEFAULT 0,
  installed_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS plugin_data (
  plugin_id TEXT NOT NULL,
  key TEXT NOT NULL,
  value TEXT NOT NULL,
  PRIMARY KEY (plugin_id, key)
);

CREATE TABLE IF NOT EXISTS yggdrasil_tokens (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  player_id INTEGER,
  client_token TEXT,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ygg_tokens_user ON yggdrasil_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_ygg_tokens_expires ON yggdrasil_tokens(expires_at);

CREATE TABLE IF NOT EXISTS yggdrasil_joins (
  server_id TEXT PRIMARY KEY,
  player_id INTEGER NOT NULL,
  access_token_hash TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ygg_joins_created ON yggdrasil_joins(created_at);

CREATE TABLE IF NOT EXISTS user_permissions (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  permission TEXT NOT NULL,
  created_at TEXT NOT NULL,
  PRIMARY KEY (user_id, permission)
);
CREATE INDEX IF NOT EXISTS idx_user_permissions_user ON user_permissions(user_id);

CREATE TABLE IF NOT EXISTS api_keys (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  key_prefix TEXT NOT NULL,
  key_hash TEXT NOT NULL UNIQUE,
  permissions TEXT NOT NULL DEFAULT '[]',
  created_at TEXT NOT NULL,
  expires_at TEXT,
  last_used_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_api_keys_user ON api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_expires ON api_keys(expires_at);

CREATE TABLE IF NOT EXISTS email_verifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  ip TEXT,
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_email_verifications_email ON email_verifications(email, id);
CREATE INDEX IF NOT EXISTS idx_email_verifications_expires ON email_verifications(expires_at);
