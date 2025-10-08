import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { DateTime } from 'luxon';
import { WebsocketService } from '../../services/websocket.service';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';
import { OrderStatus } from '../../models/order-status.enum';

@Component({
  selector: 'app-rubber',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './rubber.component.html',
  styleUrls: ['./rubber.component.css'],
})
export class RubberComponent implements OnInit {
  loading = false;
  msg: { type: 'success' | 'danger' | null; text: string } = {
    type: null,
    text: '',
  };

  // Lista de facas montadas marcadas para borracha (status 7/8 + flag emborrachada true)
  paraBorracha: orders[] = [];

  private readonly statusElegiveis = new Set<number>([
    OrderStatus.MontadaCorte,
    OrderStatus.MontadaCompleta,
  ]);

  pedidoAtualizado: orders | null = null;

  form = this.fb.group({
    nr: [null as unknown as number, [Validators.required]],
    emborrachador: ['', [Validators.required, Validators.minLength(2)]],
  });

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService,
    private ws: WebsocketService,
  ) {}

  ngOnInit(): void {
    this.carregarParaBorracha();
    this.ouvirWebsocket();
  }

  private precisaDeBorracha(o: orders): boolean {
    return o.emborrachada === true;
  }

  private carregarParaBorracha(): void {
    this.orderService.getOrders().subscribe({
      next: (lista) => {
        this.paraBorracha = this.ordenarLista(
          lista.filter((o) => this.elegivelParaBorracha(o)),
        );
      },
      error: () => {
        this.msg = {
          type: 'danger',
          text: 'Falha ao carregar facas para borracha.',
        };
      },
    });
  }

  private ouvirWebsocket(): void {
    this.ws.watchOrders().subscribe((msg) => {
      let received: any;
      try {
        received = JSON.parse(msg.body);
      } catch {
        return;
      }
      const order: orders =
        received && received.payload ? received.payload : received;
      if (!order || typeof order !== 'object') return;

      this.atualizarLista(order);
    });
  }

  private elegivelParaBorracha(o: orders): boolean {
    return (
      this.statusElegiveis.has(o.status ?? -1) && this.precisaDeBorracha(o)
    );
  }

  private timestampRef(o: orders): number {
    const candidates = [o.dataVinco, o.dataMontagem, o.dataH];
    for (const value of candidates) {
      if (value) {
        const time = new Date(value).getTime();
        if (!Number.isNaN(time)) return time;
      }
    }
    return 0;
  }

  limpar(): void {
    this.form.reset();
    this.msg = { type: null, text: '' };
    this.pedidoAtualizado = null;
  }

  borrachaRapido(nrValue: string | number): void {
    const emborrachador = this.form
      .get('emborrachador')
      ?.value?.toString()
      .trim();
    if (!emborrachador) {
      this.msg = {
        type: 'danger',
        text: 'Informe o nome do emborrachador para dar baixa rapidamente.',
      };
      return;
    }
    const nrNum = Number(nrValue);
    this.form.patchValue({ nr: nrNum });
    this.confirmar();
  }

  confirmar(): void {
    if (this.form.invalid) {
      this.msg = {
        type: 'danger',
        text: 'Preencha o NR e o nome do emborrachador.',
      };
      return;
    }

    this.loading = true;
    this.msg = { type: null, text: '' };
    this.pedidoAtualizado = null;

    const raw = this.form.getRawValue() as {
      nr: number;
      emborrachador: string;
    };
    const nrNum = Number(raw.nr);

    if (!Number.isFinite(nrNum)) {
      this.loading = false;
      this.msg = { type: 'danger', text: 'NR inválido.' };
      return;
    }

    this.orderService.getOrders().subscribe({
      next: (lista: orders[]) => {
        const alvo = lista.find((o) => Number((o as any).nr) === nrNum);
        if (!alvo) {
          this.loading = false;
          this.msg = {
            type: 'danger',
            text: `Pedido não encontrado para NR: ${nrNum}`,
          };
          return;
        }

        const atualizado: orders = {
          ...alvo,
          status: 2, // Pronta p/ entrega
          ...({ emborrachador: raw.emborrachador.trim() } as any),
          ...({
            dataEmborrachamento: DateTime.now()
              .setZone('America/Sao_Paulo')
              .toJSDate(),
          } as any),
        };

        this.orderService.updateOrder((alvo as any).id, atualizado).subscribe({
          next: (saved: orders) => {
            try {
              this.ws.sendUpdateOrder(saved);
            } catch {}
            this.loading = false;
            this.msg = {
              type: 'success',
              text: 'Emborrachamento registrado com sucesso.',
            };
            this.form.patchValue({ nr: null }); // mantém o nome para o próximo
            this.pedidoAtualizado = saved;

            this.paraBorracha = this.paraBorracha.filter(
              (o) => o.id !== saved.id,
            );
          },
          error: (err) => {
            this.loading = false;
            const txt = err?.error?.error || 'Falha ao atualizar o pedido.';
            this.msg = { type: 'danger', text: txt };
          },
        });
      },
      error: () => {
        this.loading = false;
        this.msg = { type: 'danger', text: 'Falha ao buscar pedidos.' };
      },
    });
  }

  trackById = (_: number, o: orders) => o.id;

  statusBadgeClass(status?: number): string {
    switch (status) {
      case OrderStatus.MontadaCorte:
        return 'bg-warning text-dark';
      case OrderStatus.MontadaCompleta:
        return 'bg-primary';
      case OrderStatus.ProntoEntrega:
        return 'bg-success';
      default:
        return 'bg-light text-dark';
    }
  }

  getStatusLabel(status?: number): string {
    switch (status) {
      case OrderStatus.MontadaCorte:
        return 'Montada (corte)';
      case OrderStatus.MontadaCompleta:
        return 'Montada e vincada';
      case OrderStatus.ProntoEntrega:
        return 'Pronto p/ entrega';
      default:
        return '—';
    }
  }

  getPriorityColor(prioridade: string): string {
    switch (prioridade) {
      case 'VERMELHO':
        return 'red';
      case 'AMARELO':
        return 'yellow';
      case 'AZUL':
        return 'blue';
      case 'VERDE':
        return 'green';
      default:
        return 'black';
    }
  }

  private ordenarLista(lista: orders[]): orders[] {
    return [...lista].sort((a, b) => this.timestampRef(b) - this.timestampRef(a));
  }

  private atualizarLista(order: orders): void {
    const idx = this.paraBorracha.findIndex((o) => o.id === order.id);
    const deveEntrar = this.elegivelParaBorracha(order);

    if (deveEntrar) {
      const novaLista =
        idx === -1 ? [order, ...this.paraBorracha] : [...this.paraBorracha];
      if (idx !== -1) novaLista[idx] = order;
      this.paraBorracha = this.ordenarLista(novaLista);
    } else if (idx !== -1) {
      const novaLista = [...this.paraBorracha];
      novaLista.splice(idx, 1);
      this.paraBorracha = novaLista;
    }
  }
}
