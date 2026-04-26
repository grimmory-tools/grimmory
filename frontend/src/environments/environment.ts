export const environment = {
  production: false,
  API_CONFIG: {
    BASE_URL: `http://${window.location.hostname}:6060`,
    BROKER_URL: `ws://${window.location.hostname}:6060/ws`,
  },
};
