import { Component } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { DateTime } from 'luxon';
import { WebsocketService } from '../../services/websocket.service';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';

@Component({
  selector: 'app-montagem',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './montagem.component.html',
})
export class MontagemComponent {
  loading = false;
  msg: { type: 'success' | 'danger' | null; text: string } = { type: null, text: '' };
  pedidoEncontrado: orders | null = null;

  form = this.fb.group({
    nr: [null as unknown as number, [Validators.required]],
    montador: ['', [Validators.required, Validators.minLength(2)]],
  });

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService,
    private ws: WebsocketService
  ) {}

  limpar(): void {
    this.form.reset();
    this.msg = { type: null, text: '' };
    this.pedidoEncontrado = null;
  }

  getStatusDescription(status: number): string {
    switch (status) {
      case 1: return 'Em produção';
      case 2: return 'Cortada';
      case 3: return 'Pronta para entrega';
      case 4: return 'Saiu para entrega';
      case 5: return 'Entregue';
      case 6: return 'Tirada';
      case 7: return 'Montada';
      default: return 'Desconhecido';
    }
  } // <<< ESSA CHAVE FALTAVA

  confirmar(): void {
    if (this.form.invalid) {
      this.msg = { type: 'danger', text: 'Preencha o NR e o nome do montador.' };
      return;
    }

    this.loading = true;
    this.msg = { type: null, text: '' };
    this.pedidoEncontrado = null;

    const raw = this.form.getRawValue() as { nr: number; montador: string };
    const nrNum = Number(raw.nr);

    if (!Number.isFinite(nrNum)) {
      this.loading = false;
      this.msg = { type: 'danger', text: 'NR inválido.' };
      return;
    }

    this.orderService.getOrders().subscribe({
      next: (lista: orders[]) => {
        const alvo = lista.find(o => Number((o as any).nr) === nrNum);

        if (!alvo) {
          this.loading = false;
          this.msg = { type: 'danger', text: `Pedido não encontrado para NR: ${nrNum}` };
          return;
        }

        this.pedidoEncontrado = alvo;

        const atualizado: orders = {
          ...alvo,
          status: 7,
          montador: (raw.montador || '').trim(),
          // Date (não string) para bater com seu model: Date
          dataMontagem: DateTime.now().setZone('America/Sao_Paulo').toJSDate(),
        };

        this.orderService.updateOrder((alvo as any).id, atualizado).subscribe({
          next: (saved: orders) => {
            try { this.ws.sendUpdateOrder(saved); } catch {}
            this.loading = false;
            this.msg = { type: 'success', text: 'Montagem registrada com sucesso.' };
            this.form.reset();
            this.pedidoEncontrado = saved;
          },
          error: (err) => {
            this.loading = false;
            const txt = err?.error?.error || 'Falha ao atualizar o pedido.';
            this.msg = { type: 'danger', text: txt };
          }
        });
      },
      error: () => {
        this.loading = false;
        this.msg = { type: 'danger', text: 'Falha ao buscar pedidos.' };
      }
    });
  }
}

