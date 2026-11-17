let csrfToken = null;

export function setCsrf(token) {
  csrfToken = token || null;
}

export function getCsrf() {
  return csrfToken;
}

export function buildQuery(params = {}) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}

async function request(path, { method = 'GET', body, raw = false, headers = {} } = {}) {
  const url = path.startsWith('/api/') || path === '/api' ? path : `/api${path}`;
  const requestHeaders = { ...headers };
  if (csrfToken && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    requestHeaders['X-CSRF-Token'] = csrfToken;
  }
  let payload = body;
  if (body !== undefined && !raw) {
    requestHeaders['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(url, {
      method,
      headers: requestHeaders,
      body: payload,
      credentials: 'same-origin',
    });
  } catch {
    const error = new Error('网络连接失败，请检查服务是否在线');
    error.status = 0;
    throw error;
  }

  if (response.status === 204) return null;

  const type = response.headers.get('content-type') || '';
  let data;
  if (type.includes('application/json')) {
    data = await response.json().catch(() => null);
  } else {
    data = await response.text().catch(() => '');
  }

  if (!response.ok) {
    const message =
      (data && data.error && data.error.message) ||
      (typeof data === 'string' && data) ||
      `请求失败（${response.status}）`;
    const error = new Error(message);
    error.status = response.status;
    error.code = data?.error?.code;
    error.details = data?.error?.details;
    if (
      response.status === 401 &&
      url !== '/api/auth/login' &&
      url !== '/api/auth/register'
    ) {
      window.dispatchEvent(new CustomEvent('auth:expired'));
    }
    throw error;
  }

  return data && Object.prototype.hasOwnProperty.call(data, 'data') ? data.data : data;
}

export const api = {
  get(path, query) {
    return request(`${path}${query ? buildQuery(query) : ''}`);
  },
  post(path, body) {
    return request(path, { method: 'POST', body });
  },
  put(path, body) {
    return request(path, { method: 'PUT', body });
  },
  patch(path, body) {
    return request(path, { method: 'PATCH', body });
  },
  del(path) {
    return request(path, { method: 'DELETE' });
  },
  upload(path, buffer, contentType = 'application/octet-stream') {
    return request(path, {
      method: 'POST',
      raw: true,
      body: buffer,
      headers: { 'Content-Type': contentType },
    });
  },
  request,
};
