import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../enviroment';
import { orders } from '../models/orders';


@Injectable({ providedIn: 'root' })
export class OpService {
  private readonly ordersBase = environment.apiUrl.replace(/\/$/, '');
  private readonly opsBase    = (environment as any).opApiUrl?.replace(/\/$/, '') ?? '/api';

  constructor(private http: HttpClient) {}

  getOrders() {
    return this.http.get<orders[]>(`${this.ordersBase}`);
  }

  getOrderByNr(nr: string) {
    return this.http.get<orders>(`${this.ordersBase}/nr/${encodeURIComponent(nr)}`);
  }

  getOpPdfUrl(numeroOp: string): string {
    return `${this.opsBase}/ops/${encodeURIComponent(numeroOp)}/arquivo`;
  }

  openOpPdf(numeroOp: string) {
    const url = this.getOpPdfUrl(numeroOp);
    const w = window.open(url, '_blank');
    if (w) { try { (w as any).opener = null; } catch {} }
  }
}

