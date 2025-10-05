import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { orders } from '../models/orders';
import { CursorPage } from '../models/cursor-page';
import { OrderFilters } from '../models/order-filters';
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

  searchDeliveredCursor(req: {
    limit: number;
    cursor: string | null;
    strategy: 'ID' | 'DATE_ID';
    filters: OrderFilters; // seu tipo do front
  }) {
    let params = new HttpParams()
      .set('limit', String(req.limit))
      .set('strategy', req.strategy);

    // <<< IMPORTANTE: só setar se tiver valor de verdade >>>
    if (req.cursor && req.cursor.trim() !== '') {
      params = params.set('cursor', req.cursor);
    }

    // converter OrderFilters (front) -> OrderSearchDTO (backend)
    const body = this.toOrderSearchDTO(req.filters);

    return this.http.post<CursorPage<orders>>(
      `${this.baseUrl}/orders/search-cursor`,
      body, // @RequestBody OrderSearchDTO
      { params },
    );
  }

  // Converte para o DTO do backend
  private toOrderSearchDTO(f: OrderFilters | undefined): any {
    if (!f) return {};
    const dto: any = {};

    if (f.q) dto.q = f.q;
    if (f.nr) dto.nr = f.nr;
    if (f.cliente) dto.cliente = f.cliente;
    if (f.prioridade) dto.prioridade = f.prioridade;

    if (Array.isArray(f.status) && f.status.length) dto.statusIn = f.status;

    if (f.entregador) dto.entregador = f.entregador;
    if (f.observacao) dto.observacao = f.observacao;
    if (f.veiculo) dto.veiculo = f.veiculo;
    if (f.recebedor) dto.recebedor = f.recebedor;
    if (f.montador) dto.montador = f.montador;

    const rng = (from?: string, to?: string) =>
      from || to ? { from, to } : undefined;

    const rDataH = rng(f.dataHFrom, f.dataHTo);
    const rDataEntrega = rng(f.dataEntregaFrom, f.dataEntregaTo);
    const rDataHRetorno = rng(f.dataHRetornoFrom, f.dataHRetornoTo);
    const rDataMontagem = rng(f.dataMontagemFrom, f.dataMontagemTo);

    if (rDataH) dto.dataH = rDataH;
    if (rDataEntrega) dto.dataEntrega = rDataEntrega;
    if (rDataHRetorno) dto.dataHRetorno = rDataHRetorno;
    if (rDataMontagem) dto.dataMontagem = rDataMontagem;

    return dto;
  }
}
