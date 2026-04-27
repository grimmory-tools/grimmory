/**
 * Derives the base URL from the document's base URI.
 * This correctly handles subpath deployments (e.g., /grimmory/).
 */
function getBaseUrl(): string {
  // document.baseURI includes the full URL with base href
  // e.g., "https://example.com/grimmory/" when <base href="/grimmory/">
  const baseUri = document.baseURI;
  // Remove trailing slash to get clean base URL
  return baseUri.replace(/\/$/, '');
}

/**
 * Derives the WebSocket broker URL from the current location.
 * Handles both http→ws and https→wss protocol upgrades.
 */
function getBrokerUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  // Get base path from document.baseURI, extract just the pathname
  const basePath = new URL(document.baseURI).pathname.replace(/\/$/, '');
  return `${protocol}//${window.location.host}${basePath}/ws`;
}

export const environment = {
  production: true,
  API_CONFIG: {
    BASE_URL: getBaseUrl(),
    BROKER_URL: getBrokerUrl(),
  },
};
