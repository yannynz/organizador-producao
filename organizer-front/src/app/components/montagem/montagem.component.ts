import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { DateTime } from 'luxon';
import { WebsocketService } from '../../services/websocket.service';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';
import { OrderStatus } from '../../models/order-status.enum';
import { DxfAnalysisService } from '../../services/dxf-analysis.service';
import { DxfAnalysis } from '../../models/dxf-analysis';
import { AuthService } from '../../services/auth.service';
import { UserService } from '../../services/user.service';
import { AssignableUser, User } from '../../models/user.model';
import { UserSelectorComponent } from '../shared/user-selector/user-selector.component';
import { OpService } from '../../services/op.service';
import { environment } from '../../enviroment';

type AcaoMontagem = 'montar' | 'vincar' | 'montarVincar';
type ComplexidadeEstado = 'loading' | 'ready' | 'empty';

@Component({
  selector: 'app-montagem',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, UserSelectorComponent],
  templateUrl: './montagem.component.html',
})
export class MontagemComponent implements OnInit {
  loading = false;
  msg: { type: 'success' | 'danger' | null; text: string } = {
    type: null,
    text: '',
  };

  readonly statusVisiveis = new Set<number>([
    OrderStatus.Cortada,
    OrderStatus.Tirada,
    OrderStatus.MontadaCorte,
  ]);

  readonly priorities = ['VERMELHO', 'AMARELO', 'AZUL', 'VERDE'];

  readonly dxfStarIndices = [0, 1, 2, 3, 4];

  tiradas: orders[] = [];
  filteredTiradas: orders[] = [];
  searchTerm: string = '';
  users: AssignableUser[] = [];
  currentUser: User | null = null;
  pedidoEncontrado: orders | null = null;

  expandedNr: string | null = null;
  imageUrls: { [key: string]: string } = {};
  loadingImage: { [key: string]: boolean } = {};
  private readonly imagePublicBaseUrl = environment.imagePublicBaseUrl || '/facas-renders';

  // Materiais Modal
  showMateriaisModal = false;
  selectedNr: string | null = null;
  materiaisOp: string[] = [];
  dxfMetrics: any = null;
  loadingMateriais = false;
  private requestsPending = 0;

  // History Modal
  showHistoryModal = false;
  historyList: any[] = [];
  loadingHistory = false;

  private readonly complexidadePorNr: Record<string, number> = {};
  private readonly complexidadeEstadoPorNr: Record<string, ComplexidadeEstado> = {};

  form = this.fb.group({
    nr: [null as unknown as number],
    nome: ['', [Validators.required, Validators.minLength(2)]],
  });

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService,
    private ws: WebsocketService,
    private dxfAnalysisService: DxfAnalysisService,
    private authService: AuthService,
    private userService: UserService,
    private opService: OpService
  ) {}

  ngOnInit(): void {
    this.carregarTiradas();
    this.ouvirWebsocket();
    
    this.authService.user$.subscribe(user => {
        this.currentUser = user;
        this.loadUsersForRole(user);
        if (user) {
            const current = this.form.get('nome')?.value || '';
            if (!current) {
                this.form.patchValue({ nome: user.name });
            }
        }
    });
  }

  toggleImage(nr: string): void {
    if (!this.canViewDxfAnalysis()) { return; }

    if (this.expandedNr === nr) {
      this.expandedNr = null;
      return;
    }

    this.expandedNr = nr;
    if (this.imageUrls[nr]) {
      return; 
    }

    this.loadingImage[nr] = true;
    this.dxfAnalysisService.getLatestByOrder(nr).subscribe({
      next: (analysis) => {
        const resolved = analysis ? this.resolvePublicImageUrl(analysis) : null;
        this.imageUrls[nr] = resolved || '';
        this.loadingImage[nr] = false;
      },
      error: () => {
        this.imageUrls[nr] = '';
        this.loadingImage[nr] = false;
      }
    });
  }

  verMateriais(nr: string): void {
    if (!this.canViewDxfAnalysis()) { return; }
    this.selectedNr = nr;
    this.showMateriaisModal = true;
    this.loadingMateriais = true;
    this.materiaisOp = [];
    this.dxfMetrics = null;
    this.requestsPending = 2;

    // Carrega OP (materiais)
    this.opService.getOpByNr(nr).subscribe({
      next: (op) => {
        if (op && op.materiais && Array.isArray(op.materiais)) {
            // Se for lista de strings
            this.materiaisOp = op.materiais; 
            // Se for lista de objetos, precisaria adaptar
        }
      },
      error: (err) => console.error('Erro ao carregar OP', err)
    }).add(() => this.checkLoadingComplete());

    // Carrega DXF (medidas)
    this.dxfAnalysisService.getLatestByOrder(nr).subscribe({
      next: (analysis) => {
        if (analysis) {
          this.dxfMetrics = analysis.metrics || {};
          // Se tiver campos explícitos no analysis, adiciona também
          if (analysis.totalCutLengthMm) {
             this.dxfMetrics = { ...this.dxfMetrics, 'Comprimento de Corte (mm)': analysis.totalCutLengthMm };
          }
        }
      },
      error: (err) => console.error('Erro ao carregar DXF', err)
    }).add(() => this.checkLoadingComplete());
  }

  private checkLoadingComplete() {
     this.requestsPending--;
     if (this.requestsPending <= 0) {
        this.loadingMateriais = false;
     }
  }

  fecharModalMateriais(): void {
    this.showMateriaisModal = false;
    this.selectedNr = null;
    this.materiaisOp = [];
    this.dxfMetrics = null;
  }

  verHistorico(nr: string): void {
    const order = this.tiradas.find(o => o.nr === nr);
    if (!order) return;

    this.selectedNr = nr;
    this.showHistoryModal = true;
    this.loadingHistory = true;
    this.orderService.getHistory(order.id).subscribe({
      next: (list) => {
        this.historyList = list;
        this.loadingHistory = false;
      },
      error: () => {
        this.loadingHistory = false;
      }
    });
  }

  fecharHistorico(): void {
    this.showHistoryModal = false;
    this.historyList = [];
  }

  private loadUsersForRole(user: User | null) {
    if (!user) {
      this.users = [];
      return;
    }
    this.userService.getAssignableUsers().subscribe({
      next: users => {
        this.users = users;
      },
      error: () => {
        this.users = [user];
      }
    });
  }


  private carregarTiradas(): void {
    this.orderService.getOrders().subscribe({
      next: (lista) => {
        this.tiradas = this.ordenarLista(
          lista.filter((o) => this.statusVisiveis.has(o.status ?? -1)),
        );
        this.precarregarComplexidades(this.tiradas);
        this.filterOrders();
      },
      error: () => {
        this.msg = {
          type: 'danger',
          text: 'Falha ao carregar facas em montagem.',
        };
      },
    });
  }

  filterOrders(): void {
    if (!this.searchTerm) {
      this.filteredTiradas = [...this.tiradas];
    } else {
      const term = this.searchTerm.toLowerCase();
      this.filteredTiradas = this.tiradas.filter(o => 
        o.nr.toLowerCase().includes(term) || 
        (o.cliente && o.cliente.toLowerCase().includes(term))
      );
    }
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
      case OrderStatus.Cortada:
        return 0;
      case OrderStatus.Tirada:
        return 1;
      case OrderStatus.MontadaCorte:
        return 2;
      case OrderStatus.MontadaCompleta:
        return 3;
      default:
        return 4;
    }
  }

  private timestampRef(o: orders): number {
    const candidates = [
      o.dataVinco,
      o.dataMontagem,
      o.dataTirada,
      o.dataCortada,
      o.dataH,
    ];
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
        const vaiVinco = !!alvo.vaiVinco;
        const emborrachada = !!alvo.emborrachada;

        switch (acao) {
          case 'montar':
            atualizado.montador = nome;
            atualizado.dataMontagem = agora;
            atualizado.status = !emborrachada && !vaiVinco
              ? OrderStatus.ProntoEntrega
              : OrderStatus.MontadaCorte;
            break;
          case 'vincar':
            atualizado.vincador = nome;
            atualizado.dataVinco = agora;
            atualizado.status = emborrachada
              ? OrderStatus.MontadaCompleta
              : OrderStatus.ProntoEntrega;
            break;
          case 'montarVincar':
            atualizado.montador = nome;
            atualizado.vincador = nome;
            atualizado.dataMontagem = agora;
            atualizado.dataVinco = agora;
            atualizado.status = emborrachada
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
      ![OrderStatus.Cortada, OrderStatus.Tirada, OrderStatus.MontadaCorte].includes(
        status,
      )
    ) {
      this.msg = {
        type: 'danger',
        text: 'Somente facas cortadas, tiradas ou em corte podem ser montadas.',
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
      ![OrderStatus.Cortada, OrderStatus.Tirada, OrderStatus.MontadaCorte].includes(
        status,
      )
    ) {
      this.msg = {
        type: 'danger',
        text: 'Ação disponível apenas para facas cortadas, tiradas ou montadas.',
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
      this.recarregarComplexidade(order);
    } else if (idx !== -1) {
      const novaLista = [...this.tiradas];
      novaLista.splice(idx, 1);
      this.tiradas = novaLista;
    }
    this.filterOrders();
  }

  podeMontar(order: orders): boolean {
    return (
      !this.loading &&
      [OrderStatus.Cortada, OrderStatus.Tirada, OrderStatus.MontadaCorte].includes(
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
      [OrderStatus.Cortada, OrderStatus.Tirada, OrderStatus.MontadaCorte].includes(
        order.status ?? -1,
      )
    );
  }

  statusBadgeClass(status?: number): string {
    switch (status) {
      case OrderStatus.Cortada:
        return 'bg-info text-dark';
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
      case OrderStatus.Cortada:
        return 'Cortada';
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

  complexidadeEstado(nr?: string | number | null): ComplexidadeEstado | undefined {
    const chave = this.normalizarNr(nr);
    if (!chave) {
      return undefined;
    }
    return this.complexidadeEstadoPorNr[chave];
  }

  complexidadeValor(nr?: string | number | null): number {
    const chave = this.normalizarNr(nr);
    if (!chave) {
      return 0;
    }
    return this.complexidadePorNr[chave] ?? 0;
  }

  private precarregarComplexidades(lista: orders[]): void {
    lista.forEach((order) => this.carregarComplexidade(order));
  }

  private recarregarComplexidade(order: orders): void {
    const chave = this.normalizarNr(order.nr);
    if (!chave) {
      return;
    }
    const estado = this.complexidadeEstadoPorNr[chave];
    const precisaForcar = estado === 'empty';
    this.carregarComplexidade(order, precisaForcar);
  }

  private carregarComplexidade(order: orders, force = false): void {
    if (!this.canViewDxfAnalysis()) {
      const nr = this.normalizarNr(order.nr);
      if (nr) {
        delete this.complexidadePorNr[nr];
        this.complexidadeEstadoPorNr[nr] = 'empty';
      }
      return;
    }
    const nr = this.normalizarNr(order.nr);
    if (!nr) {
      return;
    }
    const estadoAtual = this.complexidadeEstadoPorNr[nr];
    if (estadoAtual === 'loading') {
      return;
    }
    if (!force && (estadoAtual === 'ready' || estadoAtual === 'empty')) {
      return;
    }

    this.complexidadeEstadoPorNr[nr] = 'loading';
    this.dxfAnalysisService.getLatestByOrder(nr).subscribe({
      next: (analysis) => {
        if (analysis) {
          this.complexidadePorNr[nr] = this.extrairPontuacao(analysis);
          this.complexidadeEstadoPorNr[nr] = 'ready';
        } else {
          delete this.complexidadePorNr[nr];
          this.complexidadeEstadoPorNr[nr] = 'empty';
        }
      },
      error: () => {
        delete this.complexidadePorNr[nr];
        this.complexidadeEstadoPorNr[nr] = 'empty';
      },
    });
  }

  private extrairPontuacao(analysis: DxfAnalysis): number {
    const base = analysis.scoreStars ?? analysis.score ?? 0;
    const ajustado = Math.max(0, Math.min(5, base));
    return Math.round(ajustado * 10) / 10;
  }

  updatePriority(order: orders, event: Event): void {
    const select = event.target as HTMLSelectElement;
    const newPriority = select.value;
    
    if (!newPriority || newPriority === order.prioridade) return;

    this.orderService.updatePriority(order.id, newPriority).subscribe({
      next: (updated) => {
        this.msg = { type: 'success', text: `Prioridade do pedido ${updated.nr} alterada para ${updated.prioridade}. O arquivo será renomeado.` };
        // A lista será atualizada via WebSocket, mas podemos forçar localmente para feedback instantâneo
        order.prioridade = updated.prioridade;
      },
      error: (err) => {
        this.msg = { type: 'danger', text: 'Falha ao atualizar prioridade.' };
        // Reverter select
        select.value = order.prioridade || '';
      }
    });
  }

  canViewHistory(): boolean {
    return !!this.currentUser && (this.currentUser.role === 'ADMIN' || this.currentUser.role === 'DESENHISTA');
  }

  canViewDxfAnalysis(): boolean {
    // Qualquer usuário autenticado pode visualizar a análise DXF.
    return true;
  }

  private resolvePublicImageUrl(analysis: DxfAnalysis): string | null {
    const candidates = [analysis.imageUrl, analysis.imageUri];
    for (const c of candidates) {
      if (c && c.trim()) {
        const trimmed = c.trim();
        if (trimmed.startsWith('data:') || trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
          return trimmed;
        }
        if (trimmed.startsWith('//')) {
          return `https:${trimmed}`;
        }
        return trimmed;
      }
    }

    if (this.imagePublicBaseUrl && analysis.imageKey) {
      const base = this.normalizeBaseUrl(this.imagePublicBaseUrl);
      const key = analysis.imageKey.startsWith('/') ? analysis.imageKey.substring(1) : analysis.imageKey;
      return `${base}/${key}`;
    }

    return null;
  }

  private normalizeBaseUrl(value: string): string {
    const trimmed = value.trim();
    return trimmed.endsWith('/') ? trimmed.slice(0, -1) : trimmed;
  }

}
