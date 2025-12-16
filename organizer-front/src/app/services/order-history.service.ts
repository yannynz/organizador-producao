import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { OrderHistory } from '../models/order-history.model';
import { environment } from '../enviroment';

@Injectable({
  providedIn: 'root'
})
export class OrderHistoryService {
  private apiUrl = `${environment.apiBaseUrl}/order-history`;

  constructor(private http: HttpClient) { }

  getHistory(orderId: number): Observable<OrderHistory[]> {
    // In a real application, you would make an HTTP call here:
    // return this.http.get<OrderHistory[]>(`${this.apiUrl}/${orderId}`);

    // For now, returning mock data
    return of([
      {
        id: 1,
        orderId: orderId,
        timestamp: new Date().toISOString(),
        userId: 'user123',
        userName: 'John Doe',
        changes: [
          { field: 'status', oldValue: 0, newValue: 1 },
          { field: 'observacao', oldValue: '', newValue: 'Initial observation' },
        ],
      },
      {
        id: 2,
        orderId: orderId,
        timestamp: new Date(Date.now() - 3600000).toISOString(), // 1 hour ago
        userId: 'user456',
        userName: 'Jane Smith',
        changes: [
          { field: 'prioridade', oldValue: 'Baixa', newValue: 'Alta' },
        ],
      },
    ]);
  }
}
