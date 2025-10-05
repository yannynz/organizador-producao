import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { DateTime } from 'luxon';
import { WebsocketService } from '../../services/websocket.service';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';
import { OrderStatus } from '../../models/order-status.enum';

type AcaoMontagem = 'montar' | 'vincar' | 'montarVincar';

@Component({
  selector: 'app-montagem',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './montagem.component.html',
})
export class MontagemComponent implements OnInit {
  loading = false;
  msg: { type: 'success' | 'danger' | null; text: string } = {
    type: null,
    text: '',
  };

  readonly statusVisiveis = new Set<number>([
    OrderStatus.Tirada,
    OrderStatus.MontadaCorte,
  ]);

  tiradas: orders[] = [];
  pedidoEncontrado: orders | null = null;

  form = this.fb.group({
    nr: [null as unknown as number],
    nome: ['', [Validators.required, Validators.minLength(2)]],
  });

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService,
    private ws: WebsocketService,
  ) {}

  ngOnInit(): void {
    this.carregarTiradas();
    this.ouvirWebsocket();
  }

  private carregarTiradas(): void {
    this.orderService.getOrders().subscribe({
      next: (lista) => {
        this.tiradas = this.ordenarLista(
          lista.filter((o) => this.statusVisiveis.has(o.status ?? -1)),
        );
      },
      error: () => {
        this.msg = {
          type: 'danger',
          text: 'Falha ao carregar facas em montagem.',
        };
      },
    });
  }

  private ordenarLista(lista: orders[]): orders[] {
    return [...lista].sort((a, b) => {
      const rankDiff = this.rankStatus(a.status) - this.rankStatus(b.status);
      if (rankDiff !== 0) return rankDiff;
      return this.timestampRef(b) - this.timestampRef(a);
    });
  }

  private rankStatus(status?: number): number {
    switch (status) {
      case OrderStatus.Tirada:
        return 0;
      case OrderStatus.MontadaCorte:
        return 1;
      case OrderStatus.MontadaCompleta:
        return 2;
      default:
        return 3;
    }
  }

  private timestampRef(o: orders): number {
    const candidates = [o.dataVinco, o.dataMontagem, o.dataTirada, o.dataH];
    for (const value of candidates) {
      if (value) {
        const time = new Date(value).getTime();
        if (!Number.isNaN(time)) return time;
      }
    }
    return 0;
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

  limpar(): void {
    this.form.reset({ nr: null, nome: '' });
    this.msg = { type: null, text: '' };
    this.pedidoEncontrado = null;
  }

  acaoRapida(nrValue: string | number, acao: AcaoMontagem): void {
    this.form.patchValue({ nr: Number(nrValue) });
    this.executarAcao(acao, nrValue);
  }

  confirmar(): void {
    this.executarAcao('montar');
  }

  private executarAcao(
    acao: AcaoMontagem,
    nrValor?: string | number | null,
  ): void {
    if (this.loading) return;

    const nrTexto = this.normalizarNr(nrValor ?? this.form.get('nr')?.value);
    if (nrTexto === null) {
      this.msg = {
        type: 'danger',
        text: 'Selecione um NR válido antes de continuar.',
      };
      return;
    }

    const nrNumero = Number(nrTexto);
    if (!Number.isFinite(nrNumero)) {
      this.msg = { type: 'danger', text: `NR inválido: ${nrTexto}` };
      return;
    }

    const nome = this.extrairNome();

    if (!this.validarNome(nome, acao)) {
      return;
    }

    this.loading = true;
    this.msg = { type: null, text: '' };
    this.pedidoEncontrado = null;

    this.orderService.getOrderByNr(nrTexto).subscribe({
      next: (alvo) => {
        if (!alvo) {
          this.loading = false;
          this.msg = {
            type: 'danger',
            text: `Pedido não encontrado para NR: ${nrTexto}`,
          };
          return;
        }

        if (!this.validarStatusAtual(alvo, acao)) {
          this.loading = false;
          return;
        }

        const orderId = Number((alvo as any).id);
        if (!Number.isFinite(orderId)) {
          this.loading = false;
          this.msg = {
            type: 'danger',
            text: 'Pedido inválido para atualização.',
          };
          return;
        }

        const agora = DateTime.now().setZone('America/Sao_Paulo').toJSDate();
        const atualizado: orders = { ...alvo, id: orderId };

        switch (acao) {
          case 'montar':
            atualizado.montador = nome;
            atualizado.dataMontagem = agora;
            atualizado.status = OrderStatus.MontadaCorte;
            break;
          case 'vincar':
            atualizado.vincador = nome;
            atualizado.dataVinco = agora;
            atualizado.status = atualizado.emborrachada
              ? OrderStatus.MontadaCompleta
              : OrderStatus.ProntoEntrega;
            break;
          case 'montarVincar':
            atualizado.montador = nome;
            atualizado.vincador = nome;
            atualizado.dataMontagem = agora;
            atualizado.dataVinco = agora;
            atualizado.status = atualizado.emborrachada
              ? OrderStatus.MontadaCompleta
              : OrderStatus.ProntoEntrega;
            break;
        }

        this.orderService.updateOrder(orderId, atualizado).subscribe({
          next: (saved) => {
            try {
              this.ws.sendUpdateOrder(saved);
            } catch {
              /* websocket best-effort */
            }
            this.loading = false;
            this.msg = { type: 'success', text: this.mensagemSucesso(acao) };

            this.form.patchValue({ nr: null, nome });
            this.pedidoEncontrado = saved;
            this.atualizarLista(saved);
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
        this.msg = { type: 'danger', text: 'Falha ao buscar pedido pelo NR.' };
      },
    });
  }

  private normalizarNr(valor: unknown): string | null {
    if (valor === null || valor === undefined) return null;
    const texto = String(valor).trim();
    return texto.length ? texto : null;
  }

  private extrairNome(): string {
    return this.form.get('nome')?.value?.toString().trim() ?? '';
  }

  private validarNome(valor: string, acao: AcaoMontagem): boolean {
    if (valor.length < 2) {
      this.msg = {
        type: 'danger',
        text: this.mensagemNomeObrigatorio(acao),
      };
      this.form.get('nome')?.markAsTouched();
      return false;
    }
    return true;
  }

  private validarStatusAtual(order: orders, acao: AcaoMontagem): boolean {
    const status = order.status ?? -1;
    if (
      acao === 'montar' &&
      ![OrderStatus.Tirada, OrderStatus.MontadaCorte].includes(status)
    ) {
      this.msg = {
        type: 'danger',
        text: 'Somente facas tiradas ou em corte podem ser montadas.',
      };
      return false;
    }
    if (acao === 'vincar' && status !== OrderStatus.MontadaCorte) {
      this.msg = {
        type: 'danger',
        text: 'Somente facas montadas podem ser vincadas.',
      };
      return false;
    }
    if (
      acao === 'montarVincar' &&
      ![OrderStatus.Tirada, OrderStatus.MontadaCorte].includes(status)
    ) {
      this.msg = {
        type: 'danger',
        text: 'Ação disponível apenas para facas tiradas ou montadas.',
      };
      return false;
    }
    return true;
  }

  private atualizarLista(order: orders): void {
    const idx = this.tiradas.findIndex((o) => o.id === order.id);
    if (this.statusVisiveis.has(order.status ?? -1)) {
      if (idx === -1) {
        this.tiradas = this.ordenarLista([order, ...this.tiradas]);
      } else {
        const novaLista = [...this.tiradas];
        novaLista[idx] = order;
        this.tiradas = this.ordenarLista(novaLista);
      }
    } else if (idx !== -1) {
      const novaLista = [...this.tiradas];
      novaLista.splice(idx, 1);
      this.tiradas = novaLista;
    }
  }

  podeMontar(order: orders): boolean {
    return (
      !this.loading &&
      [OrderStatus.Tirada, OrderStatus.MontadaCorte].includes(
        order.status ?? -1,
      )
    );
  }

  podeVincar(order: orders): boolean {
    return !this.loading && order.status === OrderStatus.MontadaCorte;
  }

  podeMontarVincar(order: orders): boolean {
    return (
      !this.loading &&
      [OrderStatus.Tirada, OrderStatus.MontadaCorte].includes(
        order.status ?? -1,
      )
    );
  }

  statusBadgeClass(status?: number): string {
    switch (status) {
      case OrderStatus.Tirada:
        return 'bg-secondary';
      case OrderStatus.MontadaCorte:
        return 'bg-warning text-dark';
      case OrderStatus.MontadaCompleta:
        return 'bg-primary';
      default:
        return 'bg-light text-dark';
    }
  }

  getStatusLabel(status?: number): string {
    switch (status) {
      case OrderStatus.Tirada:
        return 'Tirada';
      case OrderStatus.MontadaCorte:
        return 'Montada (corte)';
      case OrderStatus.MontadaCompleta:
        return 'Montada e vincada';
      default:
        return '—';
    }
  }

  private mensagemSucesso(acao: AcaoMontagem): string {
    switch (acao) {
      case 'montar':
        return 'Montagem registrada com sucesso.';
      case 'vincar':
        return 'Vinco registrado com sucesso.';
      case 'montarVincar':
        return 'Montagem e vinco registrados com sucesso.';
      default:
        return 'Ação concluída.';
    }
  }

  private mensagemNomeObrigatorio(acao: AcaoMontagem): string {
    switch (acao) {
      case 'montar':
        return 'Informe o nome do montador.';
      case 'vincar':
        return 'Informe o nome do vincador.';
      case 'montarVincar':
        return 'Informe o nome para montagem e vinco.';
      default:
        return 'Informe o nome do responsável.';
    }
  }

  trackById = (_: number, o: orders) => o.id;
}
