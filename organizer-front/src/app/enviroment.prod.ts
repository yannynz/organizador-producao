const websocketUrl =
  typeof window !== 'undefined'
    ? `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/orders`
    : '';

export const environment = {
  production: true,
  apiBaseUrl: '/api',
  apiUrl: '/api/orders',
  wsUrl: websocketUrl,
  imagePublicBaseUrl: '',
};
