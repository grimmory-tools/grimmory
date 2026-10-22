/** Browser navigation uses the frontend base, which may differ from the API origin in development. */
export function appUrl(path: string): string {
  return new URL(path.replace(/^\/+/, ''), document.baseURI).href;
}

export function authRedirectUrl(value: string): string {
  try {
    value = value.trim();
    const url = value.startsWith('/') ? new URL(appUrl(value)) : new URL(value, document.baseURI);
    if (url.protocol === 'http:' || url.protocol === 'https:') return url.href;
  } catch {
    // Invalid provider URLs return to the application's login page.
  }
  return appUrl('login');
}
