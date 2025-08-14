import { Component } from '@angular/core';
import { OnInit } from '@angular/core';
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
export class MontagemComponent implements OnInit {
  loading = false;
  msg: { type: 'success' | 'danger' | null, text: string } = { type: null, text: '' };

  // lista de facas tiradas (status 6)
  tiradas: orders[] = [];

  // último pedido salvo/atualizado (só pra feedback)
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

  ngOnInit(): void {
    this.carregarTiradas();
    this.ouvirWebsocket();
  }

  private carregarTiradas(): void {
    this.orderService.getOrders().subscribe({
      next: (lista) => {
        // status 6 = Tirada
        this.tiradas = lista
          .filter(o => o.status === 6)
          .sort((a, b) => {
            const ta = a.dataH ? new Date(a.dataH).getTime() : 0;
            const tb = b.dataH ? new Date(b.dataH).getTime() : 0;
            return tb - ta; // mais novas primeiro
          });
      },
      error: () => {
        this.msg = { type: 'danger', text: 'Falha ao carregar facas tiradas.' };
      }
    });
  }

  private ouvirWebsocket(): void {
    this.ws.watchOrders().subscribe(msg => {
      // suporta payload puro (Order) ou wrapper {type, payload}
      let received: any;
      try { received = JSON.parse(msg.body); } catch { return; }
      const order: orders = (received && received.payload) ? received.payload : received;

      if (!order || typeof order !== 'object') return;

      // Se virou status 6, coloca/atualiza na lista. Se saiu do 6, remove.
      const idx = this.tiradas.findIndex(o => o.id === order.id);
      if (order.status === 6) {
        if (idx === -1) this.tiradas = [order, ...this.tiradas];
        else this.tiradas[idx] = order;
        // mantém ordenação por data
        this.tiradas.sort((a, b) => {
          const ta = a.dataH ? new Date(a.dataH).getTime() : 0;
          const tb = b.dataH ? new Date(b.dataH).getTime() : 0;
          return tb - ta;
        });
      } else if (idx !== -1) {
        this.tiradas.splice(idx, 1);
        this.tiradas = [...this.tiradas];
      }
    });
  }

  limpar(): void {
    this.form.reset();
    this.msg = { type: null, text: '' };
    this.pedidoEncontrado = null;
  }

  // Ação rápida por linha: usa o montador preenchido e confirma o NR daquela linha
  montarRapido(nrValue: string | number): void {
    const montador = this.form.get('montador')?.value?.toString().trim();
    if (!montador) {
      this.msg = { type: 'danger', text: 'Informe o nome do montador para montar rapidamente.' };
      return;
    }
    // garante numérico
    const nrNum = Number(nrValue);
    this.form.patchValue({ nr: nrNum });
    this.confirmar();
  }

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

    // Busca, atualiza para status 7 e notifica WS
    this.orderService.getOrders().subscribe({
      next: (lista: orders[]) => {
        const alvo = lista.find(o => Number((o as any).nr) === nrNum);
        if (!alvo) {
          this.loading = false;
          this.msg = { type: 'danger', text: `Pedido não encontrado para NR: ${nrNum}` };
          return;
        }

        const atualizado: orders = {
          ...alvo,
          status: 7, // Montada
          montador: raw.montador.trim(),
          dataMontagem: DateTime.now().setZone('America/Sao_Paulo').toJSDate(),
        };

        this.orderService.updateOrder((alvo as any).id, atualizado).subscribe({
          next: (saved: orders) => {
            try { this.ws.sendUpdateOrder(saved); } catch {}
            this.loading = false;
            this.msg = { type: 'success', text: 'Montagem registrada com sucesso.' };
            this.form.patchValue({ nr: null }); // mantém o nome pro próximo
            this.pedidoEncontrado = saved;

            // saiu do status 6 → remove da lista
            this.tiradas = this.tiradas.filter(o => o.id !== saved.id);
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

  trackById = (_: number, o: orders) => o.id;
}

