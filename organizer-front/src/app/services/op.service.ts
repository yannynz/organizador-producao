import { Injectable } from '@angular/core';
import { environment } from '../enviroment';

@Injectable({ providedIn: 'root' })
export class OpService {
  getOpUrl(numeroOp: string): string {
    const base = (environment.apiBase || '').replace(/\/$/, '');
    return `${base}/ops/${encodeURIComponent(numeroOp)}/arquivo`;
  }

  openInNewTab(numeroOp: string): void {
    const url = this.getOpUrl(numeroOp);
    const w = window.open(url, '_blank');
    if (w) { try { (w as any).opener = null; } catch { /* ignore */ } }
  }
}
