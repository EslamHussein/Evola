// Same-origin `/api` in production (Caddy proxies it to the Ktor server); overridable for local
// dev against a backend running on a different port.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

// Set once at app startup (see telegram/init.ts) — every request re-sends the raw initData string,
// same "tma <initData>" scheme the backend's TelegramInitDataValidator expects.
let rawInitData: string | undefined;

export function setRawInitData(value: string | undefined) {
  rawInitData = value;
}

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function request<TResponse>(path: string, body?: unknown): Promise<TResponse> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(rawInitData ? { Authorization: `tma ${rawInitData}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    const message = await response
      .json()
      .then((data: { error?: string }) => data.error)
      .catch(() => undefined);
    throw new ApiError(message ?? `Request to ${path} failed with ${response.status}`, response.status);
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }
  return (await response.json()) as TResponse;
}

export const api = {
  post: request,
};
