import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { DateTime } from 'luxon';
import { WebsocketService } from '../../services/websocket.service';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';

@Component({
  selector: 'app-rubber',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './rubber.component.html',
  styleUrls: ['./rubber.component.css']
})
export class RubberComponent implements OnInit {
  loading = false;
  msg: { type: 'success' | 'danger' | null, text: string } = { type: null, text: '' };

  // Lista de facas montadas que precisam ir para borracha (status 7 + observação contendo "emborrach")
  paraBorracha: orders[] = [];

  // Último pedido processado (apenas feedback)
  pedidoAtualizado: orders | null = null;

  form = this.fb.group({
    nr: [null as unknown as number, [Validators.required]],
    Emborrachador: ['', [Validators.required, Validators.minLength(2)]],
  });

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService,
    private ws: WebsocketService
  ) {}

  ngOnInit(): void {
    this.carregarParaBorracha();
    this.ouvirWebsocket();
  }

  private precisaDeBorracha(o: orders): boolean {
    const obs = (o as any).observacoes ?? (o as any).observacao ?? '';
    const t = String(obs).toLowerCase();
    // cobre "emborrachada", "emborrachado", "emborrachamento" etc. e menções claras a "borracha"
    return t.includes('emborrach');
  }

  private carregarParaBorracha(): void {
    this.orderService.getOrders().subscribe({
      next: (lista) => {
        this.paraBorracha = lista
          .filter(o => o.status === 7 && this.precisaDeBorracha(o))
          .sort((a, b) => {
            // prioriza mais recentes pela data de montagem (fallbacks para dataH)
            const ta = (a as any).dataMontagem ? new Date((a as any).dataMontagem).getTime() :
                      a.dataH ? new Date(a.dataH).getTime() : 0;
            const tb = (b as any).dataMontagem ? new Date((b as any).dataMontagem).getTime() :
                      b.dataH ? new Date(b.dataH).getTime() : 0;
            return tb - ta;
          });
      },
      error: () => {
        this.msg = { type: 'danger', text: 'Falha ao carregar facas para borracha.' };
      }
    });
  }

  private ouvirWebsocket(): void {
    this.ws.watchOrders().subscribe(msg => {
      let received: any;
      try { received = JSON.parse(msg.body); } catch { return; }
      const order: orders = (received && received.payload) ? received.payload : received;
      if (!order || typeof order !== 'object') return;

      const idx = this.paraBorracha.findIndex(o => o.id === order.id);
      const deveEntrar = order.status === 7 && this.precisaDeBorracha(order);

      if (deveEntrar) {
        if (idx === -1) this.paraBorracha = [order, ...this.paraBorracha];
        else this.paraBorracha[idx] = order;

        this.paraBorracha = [...this.paraBorracha].sort((a, b) => {
          const ta = (a as any).dataMontagem ? new Date((a as any).dataMontagem).getTime() :
                    a.dataH ? new Date(a.dataH).getTime() : 0;
          const tb = (b as any).dataMontagem ? new Date((b as any).dataMontagem).getTime() :
                    b.dataH ? new Date(b.dataH).getTime() : 0;
          return tb - ta;
        });
      } else if (idx !== -1) {
        // se mudou status ou tiraram a marcação de borracha, some da lista
        this.paraBorracha.splice(idx, 1);
        this.paraBorracha = [...this.paraBorracha];
      }
    });
  }

  limpar(): void {
    this.form.reset();
    this.msg = { type: null, text: '' };
    this.pedidoAtualizado = null;
  }

  // Ação rápida: usa o borrachador preenchido e confirma a linha
  borrachaRapido(nrValue: string | number): void {
    const Emborrachador = this.form.get('Emborrachador')?.value?.toString().trim();
    if (!Emborrachador) {
      this.msg = { type: 'danger', text: 'Informe o nome do emborrachador para dar baixa rapidamente.' };
      return;
    }
    const nrNum = Number(nrValue);
    this.form.patchValue({ nr: nrNum });
    this.confirmar();
  }

  confirmar(): void {
    if (this.form.invalid) {
      this.msg = { type: 'danger', text: 'Preencha o NR e o nome do emborrachador.' };
      return;
    }

    this.loading = true;
    this.msg = { type: null, text: '' };
    this.pedidoAtualizado = null;

    const raw = this.form.getRawValue() as { nr: number; Emborrachador: string };
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

        const atualizado: orders = {
          ...alvo,
          status: 2, // Pronta p/ entrega
          ...( { Emborrachador: raw.Emborrachador.trim() } as any ),
          ...( { dataEmborrachamento: DateTime.now().setZone('America/Sao_Paulo').toJSDate() } as any ),
        };

        this.orderService.updateOrder((alvo as any).id, atualizado).subscribe({
          next: (saved: orders) => {
            try { this.ws.sendUpdateOrder(saved); } catch {}
            this.loading = false;
            this.msg = { type: 'success', text: 'Emborrachamento registrado com sucesso.' };
            this.form.patchValue({ nr: null }); // mantém o nome para o próximo
            this.pedidoAtualizado = saved;

            // remove da lista
            this.paraBorracha = this.paraBorracha.filter(o => o.id !== saved.id);
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

  getPriorityColor(prioridade: string): string {
    switch (prioridade) {
      case 'VERMELHO': return 'red';
      case 'AMARELO':  return 'yellow';
      case 'AZUL':     return 'blue';
      case 'VERDE':    return 'green';
      default:         return 'black';
    }
  }
}

