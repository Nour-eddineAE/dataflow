import type {Problem} from './types';

const UNAUTHORIZED = 401;

export class ApiError extends Error {
    constructor(message: string, readonly status: number, readonly problems?: Problem[]) {
        super(message);
    }
}

export const SIGN_IN = '/oauth2/authorization/keycloak';

export function csrfToken() {
    const match = document.cookie.split('; ').find((cookie) => cookie.startsWith('XSRF-TOKEN='));
    return match ? decodeURIComponent(match.slice('XSRF-TOKEN='.length)) : undefined;
}

export function signOut() {
    const form = document.createElement('form');
    form.method = 'post';
    form.action = '/logout';
    const token = csrfToken();
    if (token) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = '_csrf';
        input.value = token;
        form.appendChild(input);
    }
    document.body.appendChild(form);
    form.submit();
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const token = method === 'GET' ? undefined : csrfToken();
    if (token) headers['X-XSRF-TOKEN'] = token;
    const response = await fetch(`/api${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (response.status === UNAUTHORIZED) {
        window.location.assign(SIGN_IN);
        return new Promise<T>(() => undefined);
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : undefined;
    if (!response.ok) {
        throw new ApiError(data?.error ?? response.statusText, response.status, data?.problems);
    }
    return data as T;
}

async function upload<T>(path: string, file: File): Promise<T> {
    const body = new FormData();
    body.append('file', file);
    const token = csrfToken();
    const response = await fetch(`/api${path}`, {
        method: 'POST',
        headers: token ? {'X-XSRF-TOKEN': token} : {},
        body,
    });
    const text = await response.text();
    const data = text ? JSON.parse(text) : undefined;
    if (!response.ok) throw new ApiError(data?.error ?? response.statusText, response.status, data?.problems);
    return data as T;
}

export const api = {
    upload,
    get: <T>(path: string) => request<T>('GET', path),
    post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
    put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
    delete: <T>(path: string) => request<T>('DELETE', path),
};
