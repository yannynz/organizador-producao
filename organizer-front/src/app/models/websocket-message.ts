export interface WebSocketMessage {
  type: 'create' | 'update';  // Tipo da operação: criação ou atualização
  order: any;  // O pedido (tipado conforme o modelo de pedidos que você tem)
}

