import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { orders } from '../models/orders';
<<<<<<< HEAD
=======
import { environment } from '../../environment';
>>>>>>> 61128281 (beta1)

@Injectable({
  providedIn: 'root'
})
<<<<<<< HEAD
export class OrderService {
  private baseUrl = 'http://localhost:8080/api/orders'; // Ajuste com sua URL
=======

export class OrderService {
    private baseUrl = environment.apiUrl;
>>>>>>> 61128281 (beta1)

  constructor(private http: HttpClient) { }

  getOrders(): Observable<orders[]> {
    return this.http.get<orders[]>(`${this.baseUrl}`);
  }

  getOrderById(id: number): Observable<orders | null> {
    return this.http.get<orders | null>(`${this.baseUrl}/${id}`);
  }

  createOrder(order: orders): Observable<orders> {
    return this.http.post<orders>(`${this.baseUrl}/create`, order);
  }

  updateOrder(id: number, order: orders): Observable<orders> {
    return this.http.put<orders>(`${this.baseUrl}/update/${id}`, order);
  }

  deleteOrder(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/delete/${id}`);
  }
  updateOrderStatus(id: number, status: number, entregador: string, observacao: string): Observable<orders> {
    const url = `${this.baseUrl}/${id}/status`;
    const params = new URLSearchParams();
    params.append('status', status.toString());
    params.append('entregador', entregador);
    params.append('observacao', observacao);
    return this.http.put<orders>(`${url}?${params.toString()}`, null);
  }
}

