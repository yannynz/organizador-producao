export const environment = {
  production: true,
  apiUrl: '/api/orders',  // -> apenas caminho relativo, porque o nginx redireciona
  wsUrl: 'ws://' + window.location.host + '/ws/orders',  // -> WebSocket relativo ao host onde a página foi carregada
};

