export const endpoints = JSON.parse(document.getElementById('app-config').textContent);

export class ApiError extends Error {
    constructor(status, message, errors = {}) {
        super(message);
        this.status = status;
        this.errors = errors;
    }
}

export async function request(url, { method = 'GET', body, query, signal } = {}) {
    const target = new URL(url, window.location.origin);
    Object.entries(query ?? {}).forEach(([key, value]) => {
        if (value !== null && value !== undefined && value !== '') {
            target.searchParams.set(key, String(value));
        }
    });

    const headers = { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (method !== 'GET') {
        const cookie = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='));
        if (cookie) headers['X-XSRF-TOKEN'] = decodeURIComponent(cookie.slice('XSRF-TOKEN='.length));
    }

    let response;
    try {
        response = await fetch(target, {
            method, headers, credentials: 'same-origin', signal,
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch (error) {
        if (error.name === 'AbortError') throw error;
        throw new ApiError(0, 'Não foi possível conectar. Verifique sua conexão e tente novamente.');
    }

    const data = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
        const messages = {
            401: 'Sua sessão terminou. Entre novamente para continuar.',
            403: 'Você não tem permissão para esta ação.',
            404: 'Este item não está mais disponível. Atualize a lista.',
            419: 'Sua sessão expirou. Atualize a página e tente novamente.',
            422: 'Confira os campos e tente novamente.',
            429: 'Muitas tentativas. Aguarde um minuto e tente novamente.',
        };
        throw new ApiError(response.status, messages[response.status] ?? 'Não foi possível concluir a ação. Tente novamente.', data?.errors);
    }

    return data;
}

export function initializeCsrf() {
    return request(endpoints.csrf);
}
