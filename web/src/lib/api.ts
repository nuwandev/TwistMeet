const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8080";

export class ApiError extends Error {
  code: string;
  status: number;

  constructor(code: string, message: string, status: number) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie
    .split("; ")
    .find((row) => row.startsWith(name + "="));
  return match ? decodeURIComponent(match.split("=")[1]) : null;
}

/**
 * The API issues an XSRF-TOKEN cookie on every request (see CsrfCookieFilter server-side); the
 * browser must echo it back as X-XSRF-TOKEN on any mutating call. A GET first guarantees the
 * cookie exists before a page ever needs to POST/PATCH anything.
 */
export async function ensureCsrfCookie(): Promise<void> {
  if (readCookie("XSRF-TOKEN")) return;
  await fetch(`${API_BASE_URL}/api/v1/me`, { credentials: "include" });
}

export async function apiFetch<T>(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<T> {
  const method = options.method ?? "GET";
  if (method !== "GET") {
    await ensureCsrfCookie();
  }
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const csrf = readCookie("XSRF-TOKEN");
  if (csrf && method !== "GET") {
    headers["X-XSRF-TOKEN"] = csrf;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    credentials: "include",
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const code = data?.code ?? "UNKNOWN_ERROR";
    const message = data?.message ?? `Request failed with status ${response.status}`;
    throw new ApiError(code, message, response.status);
  }

  return data as T;
}
