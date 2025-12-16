import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { orders } from '../models/orders';
import { environment } from '../enviroment';
import { HttpParams } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class OrderService {
  private baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getOrders(): Observable<orders[]> {
    return this.http.get<orders[]>(`${this.baseUrl}`);
  }

  getOrderById(id: number): Observable<orders | null> {
    return this.http.get<orders | null>(`${this.baseUrl}/${id}`);
  }

  getOrderByNr(nr: string | number): Observable<orders> {
    const nrParam = encodeURIComponent(String(nr));
    return this.http.get<orders>(`${this.baseUrl}/nr/${nrParam}`);
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
  updateOrderStatus(
    id: number,
    status: number,
    entregador: string,
    observacao: string,
  ): Observable<orders> {
    const url = `${this.baseUrl}/${id}/status`;
    const params = new URLSearchParams();
    params.append('status', status.toString());
    params.append('entregador', entregador);
    params.append('observacao', observacao);
    return this.http.put<orders>(`${url}?${params.toString()}`, null);
  }
  updateOrderAdm(
    id: number,
    order: orders,
    adminPassword: string,
  ): Observable<orders> {
    const url = `${this.baseUrl}/updateAdm/${id}`;
    const params = new HttpParams().set('adminPassword', adminPassword);
    return this.http.put<orders>(url, order, { params });
  }

  deleteOrderAdm(id: number, adminPassword: string): Observable<void> {
    const url = `${this.baseUrl}/deleteAdm/${id}`;
    const params = new HttpParams().set('adminPassword', adminPassword);
    return this.http.delete<void>(url, { params });
  }

  updatePriority(id: number, priority: string): Observable<orders> {
    return this.http.patch<orders>(`${this.baseUrl}/${id}/priority`, { priority });
  }

  getHistory(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/${id}/history`);
  }
}
