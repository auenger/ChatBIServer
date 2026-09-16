/**
 * 统一请求封装（借鉴 Chat2DB service/base 的思想：统一前缀、统一错误、401 处理）。
 * 阶段 1 用 fetch；token 存 localStorage（内网工具场景）。
 */
const TOKEN_KEY = 'meper_token';
const USERNAME_KEY = 'meper_username';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function saveSession(token: string, username: string) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USERNAME_KEY, username);
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
}

export function savedUsername(): string | null {
  return localStorage.getItem(USERNAME_KEY);
}

export class ApiError extends Error {
  code: string;
  status: number;

  constructor(code: string, message: string, status: number) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

export async function request<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined),
  };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch('/api' + path, { ...init, headers });

  if (response.status === 401 && path !== '/auth/login') {
    clearSession();
    window.location.href = '/login';
    throw new ApiError('UNAUTHORIZED', '未登录或会话已过期', 401);
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(data.code ?? 'ERROR', data.message ?? response.statusText, response.status);
  }
  return data as T;
}

export function get<T = unknown>(path: string): Promise<T> {
  return request<T>(path, { method: 'GET' });
}

export function post<T = unknown>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
}

export function del<T = unknown>(path: string): Promise<T> {
  return request<T>(path, { method: 'DELETE' });
}
