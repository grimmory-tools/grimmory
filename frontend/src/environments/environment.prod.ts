export const environment = {
  production: true,
  API_CONFIG: {
    BASE_URL: window.location.origin,
    BROKER_URL:
      window.location.protocol === 'https:'
        ? `wss://${window.location.host}/ws`
        : `ws://${window.location.host}/ws`,
  },
};
