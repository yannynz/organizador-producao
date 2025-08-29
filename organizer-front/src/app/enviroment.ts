export const environment = {
  production: true,
  apiUrl: '/api/orders',
  wsUrl: typeof window !== 'undefined' ? 'ws://' + window.location.host + '/ws/orders' : '',  // proteção
  apiBaseUrl: '/api',
};

