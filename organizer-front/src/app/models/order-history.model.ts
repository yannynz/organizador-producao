export interface OrderChange {
  field: string;
  oldValue: any;
  newValue: any;
}

export interface OrderHistory {
  id: number;
  orderId: number;
  timestamp: string; // ISO 8601 string
  userId: string;
  userName: string;
  changes: OrderChange[];
}
