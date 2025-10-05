import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { AdvancedFiltersComponent } from '../../../../components/advanced-filters/advanced-filters.component';
import { OrderDetailsModalComponent } from '../../../../components/order-details-modal/order-details-modal.component';
import { OrderService } from '../../../../services/orders.service';
import { WebsocketService } from '../../../../services/websocket.service';
import { orders } from '../../../../models/orders';
import { CursorPage } from '../../../../models/cursor-page';
import { OrderFilters } from '../../../../models/order-filters';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-delivered-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AdvancedFiltersComponent,
    OrderDetailsModalComponent,
  ],
  templateUrl: './delivered-list.component.html',
  styleUrls: ['./delivered-list.component.css'],
})
export class DeliveredListComponent implements OnInit, OnDestroy {
  // ===== labels / filtros =====
  statusDescriptions: { [key: number]: string } = {
    0: 'Em Produção',
    1: 'Cortada',
    7: 'Montada (corte)',
    8: 'Montada e vincada',
    2: 'Pronto para Entrega',
    3: 'Saiu para Entrega',
    4: 'Retirada',
    5: 'Entregue',
    6: 'Tirada',
  };
  statusList = Object.entries(this.statusDescriptions).map(([k, v]) => ({
    key: +k,
    label: v,
  }));

  // ===== estado de UI =====
  rows: orders[] = [];
  loading = false;

  baseFilters: OrderFilters = {
    status: [4, 5],
    sort: 'dataEntrega,desc,id,desc',
  };
  currentFilters: OrderFilters = { ...this.baseFilters };

  detailsOpen = false;
  selectedOrder: orders | null = null;

  private subs = new Subscription();

  // ===== paginação =====
  public pageSize = 50;

  // server-mode (cursor)
  private pageCursors: (string | null)[] = [null]; // page 1 começa com cursor null
  public pageCache: Record<number, orders[]> = {};
  public pageMeta: Record<
    number,
    { nextCursor: string | null; hasMore: boolean } | undefined
  > = {};
  currentPage = 1;
  pagesToShow: number[] = [1];
  public Math = Math;

  // fallback local
  public localMode = false;
  public localAll: orders[] = [];

  constructor(
    private orderService: OrderService,
    private websocket: WebsocketService,
  ) {}

  ngOnInit(): void {
    this.reload();

    // WebSocket: atualizações
    this.subs.add(
      this.websocket.watchOrders().subscribe((msg) => {
        let text = '';
        if (typeof msg?.body === 'string') text = msg.body;
        else if (msg?.body) {
          try {
            text = new TextDecoder('utf-8').decode(msg.body);
          } catch {
            text = String(msg.body);
          }
        }
        text = text
          .replace(/^\uFEFF/, '')
          .replace(/\u0000$/, '')
          .trim();
        if (!text || (text[0] !== '{' && text[0] !== '[')) return;

        let payload: any;
        try {
          payload = JSON.parse(text);
        } catch {
          return;
        }
        const o: orders = Array.isArray(payload) ? payload[0] : payload;

        // só interessam 4/5 e que passem no filtro atual
        const passes =
          (o.status === 4 || o.status === 5) &&
          this.applyFiltersLocal([o], this.currentFilters).length > 0;

        if (this.localMode) {
          // atualiza fonte completa + re-corta a página atual
          const idx = this.localAll.findIndex((x) => x.id === o.id);
          this.localAll =
            idx > -1
              ? this.localAll.map((x) => (x.id === o.id ? o : x))
              : passes
                ? [o, ...this.localAll]
                : this.localAll;
          if (!passes)
            this.localAll = this.localAll.filter((x) => x.id !== o.id);

          this.localAll = this.sortRows(this.localAll);

          const start = (this.currentPage - 1) * this.pageSize;
          this.rows = this.localAll.slice(start, start + this.pageSize);
          this.updatePagesList();
        } else {
          // server-mode: atualiza somente se o item estiver visível na página corrente
          const idx = this.rows.findIndex((x) => x.id === o.id);
          if (passes && idx > -1) {
            this.rows[idx] = o;
            this.rows = this.sortRows(this.rows);
            this.pageCache[this.currentPage] = this.rows;
          } else if (!passes && idx > -1) {
            this.rows.splice(idx, 1);
            this.pageCache[this.currentPage] = this.rows;
          }
          // para manter simples, não revalido as outras páginas em cache
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  // ===== ações =====
  reload() {
    // reseta paginação
    this.currentPage = 1;
    this.pageCursors = [null];
    this.pageCache = {};
    this.pageMeta = {};
    this.pagesToShow = [1];
    this.localMode = false;
    this.localAll = [];

    // tenta server-mode, cai para local se der erro
    this.gotoPage(1);
  }

  onApplyFilters(ev: { search: string; filters: OrderFilters }) {
    this.currentFilters = {
      ...(ev.filters || {}),
      status: [4, 5],
      sort: 'dataEntrega,desc,id,desc',
    };
    this.reload();
  }
  onClearFilters() {
    this.currentFilters = { ...this.baseFilters };
    this.reload();
  }

  openDetails(o: orders) {
    this.selectedOrder = o;
    this.detailsOpen = true;
  }
  closeDetails() {
    this.selectedOrder = null;
    this.detailsOpen = false;
  }

  handleUpdate(o: orders) {
    this.orderService.updateOrder(o.id, o).subscribe(() => {
      this.websocket.sendUpdateOrder(o);
      const i = this.rows.findIndex((r) => r.id === o.id);
      if (i > -1) this.rows[i] = o;
      this.closeDetails();
    });
  }

  handleDelete(id: number) {
    this.orderService.deleteOrder(id).subscribe(() => {
      this.websocket.sendDeleteOrder(id);
      this.rows = this.rows.filter((x) => x.id !== id);
      this.pageCache[this.currentPage] = this.rows;
      this.closeDetails();
    });
  }

  // ===== paginação numerada =====
  gotoPage(p: number) {
    if (p < 1) return;

    if (this.localMode) {
      // client-side
      const maxPages = Math.max(
        1,
        Math.ceil(this.localAll.length / this.pageSize),
      );
      if (p > maxPages) return;
      this.currentPage = p;
      const start = (p - 1) * this.pageSize;
      this.rows = this.localAll.slice(start, start + this.pageSize);
      this.updatePagesList();
      return;
    }

    // server-mode
    if (this.pageCache[p]) {
      this.currentPage = p;
      this.rows = this.pageCache[p];
      this.updatePagesList(); // usa length do pageCursors
      return;
    }

    const startCursor = this.pageCursors[p - 1] ?? null;
    this.loading = true;
    this.orderService
      .searchDeliveredCursor({
        limit: this.pageSize,
        cursor: startCursor,
        strategy: 'DATE_ID',
        filters: this.currentFilters,
      })
      .subscribe(
        (res: CursorPage<orders>) => {
          this.localMode = false;

          this.pageCache[p] = res.items;
          this.pageMeta[p] = {
            nextCursor: res.nextCursor,
            hasMore: res.hasMore,
          };

          // garante que o array de cursores tenha a posição p
          this.pageCursors[p] = res.nextCursor ?? null;

          this.currentPage = p;
          this.rows = res.items;
          this.loading = false;

          this.updatePagesList();
        },
        (_) => {
          // fallback local
          this.fetchLocalAndGo(p);
        },
      );
  }

  gotoPrev() {
    if (this.currentPage > 1) this.gotoPage(this.currentPage - 1);
  }
  gotoNext() {
    if (this.localMode) {
      const maxPages = Math.max(
        1,
        Math.ceil(this.localAll.length / this.pageSize),
      );
      if (this.currentPage < maxPages) this.gotoPage(this.currentPage + 1);
    } else {
      const meta = this.pageMeta[this.currentPage];
      if (meta?.hasMore) this.gotoPage(this.currentPage + 1);
    }
  }

  private updatePagesList() {
    if (this.localMode) {
      const n = Math.max(1, Math.ceil(this.localAll.length / this.pageSize));
      this.pagesToShow = Array.from({ length: n }, (_, i) => i + 1);
    } else {
      // número de páginas conhecidas até agora (1..N). Cresce conforme navegamos.
      const n = Math.max(1, this.pageCursors.length);
      this.pagesToShow = Array.from({ length: n }, (_, i) => i + 1);
      // Se a página atual tem hasMore, podemos opcionalmente exibir um fantasma p+1:
      // aqui mantive só as conhecidas para não confundir.
    }
  }

  private fetchLocalAndGo(p: number) {
    this.orderService.getOrders().subscribe(
      (all) => {
        const filtered = this.sortRows(
          this.applyFiltersLocal(all, this.currentFilters),
        );
        this.localMode = true;
        this.localAll = filtered;

        const maxPages = Math.max(
          1,
          Math.ceil(this.localAll.length / this.pageSize),
        );
        this.currentPage = Math.min(Math.max(1, p), maxPages);

        const start = (this.currentPage - 1) * this.pageSize;
        this.rows = this.localAll.slice(start, start + this.pageSize);
        this.loading = false;
        this.updatePagesList();
      },
      (_) => {
        this.loading = false;
      },
    );
  }

  // ===== utils =====
  private sortRows(list: orders[]) {
    return [...list].sort((a, b) => {
      const ta = a.dataEntrega ? new Date(a.dataEntrega).getTime() : 0;
      const tb = b.dataEntrega ? new Date(b.dataEntrega).getTime() : 0;
      if (tb !== ta) return tb - ta; // dataEntrega desc
      return (b.id || 0) - (a.id || 0); // id desc
    });
  }

  private applyFiltersLocal(list: orders[], f: OrderFilters) {
    const q = (f.q || '').toLowerCase();
    return list.filter((o) => {
      if (!(o.status === 4 || o.status === 5)) return false;
      if (f.id && o.id !== f.id) return false;
      if (f.nr && !o.nr.toLowerCase().includes(f.nr.toLowerCase()))
        return false;
      if (
        f.cliente &&
        !o.cliente.toLowerCase().includes(f.cliente.toLowerCase())
      )
        return false;
      if (f.prioridade && o.prioridade !== f.prioridade) return false;
      if (f.status?.length && !f.status.includes(o.status)) return false;
      if (
        f.entregador &&
        (o.entregador || '')
          .toLowerCase()
          .indexOf(f.entregador.toLowerCase()) === -1
      )
        return false;
      if (
        f.veiculo &&
        (o.veiculo || '').toLowerCase().indexOf(f.veiculo.toLowerCase()) === -1
      )
        return false;
      if (
        f.recebedor &&
        (o.recebedor || '').toLowerCase().indexOf(f.recebedor.toLowerCase()) ===
          -1
      )
        return false;
      if (
        f.montador &&
        (o.montador || '').toLowerCase().indexOf(f.montador.toLowerCase()) ===
          -1
      )
        return false;
      if (
        f.observacao &&
        (o.observacao || '')
          .toLowerCase()
          .indexOf(f.observacao.toLowerCase()) === -1
      )
        return false;

      const inRange = (d?: Date, from?: string, to?: string) => {
        if (!from && !to) return true;
        const t = d ? new Date(d).getTime() : NaN;
        const tf = from ? new Date(from).getTime() : -Infinity;
        const tt = to ? new Date(to).getTime() : +Infinity;
        return !isNaN(t) && t >= tf && t <= tt;
      };
      if (!inRange(o.dataH, f.dataHFrom, f.dataHTo)) return false;
      if (!inRange(o.dataEntrega, f.dataEntregaFrom, f.dataEntregaTo))
        return false;

      if (q) {
        const S = (x?: string) => (x || '').toLowerCase();
        const D = (d?: Date) =>
          d ? new Date(d).toLocaleDateString('pt-BR') : '';
        const hay =
          S(o.nr).includes(q) ||
          S(o.cliente).includes(q) ||
          S(o.prioridade).includes(q) ||
          S(this.statusDescriptions[o.status]).includes(q) ||
          S(o.entregador).includes(q) ||
          S(o.veiculo).includes(q) ||
          S(o.recebedor).includes(q) ||
          S(o.montador).includes(q) ||
          S(o.observacao).includes(q) ||
          D(o.dataH).includes(q) ||
          D(o.dataEntrega).includes(q);
        if (!hay) return false;
      }
      return true;
    });
  }

  getPriorityColor(p: string) {
    if (p === 'VERMELHO') return 'red';
    if (p === 'AMARELO') return 'goldenrod';
    if (p === 'AZUL') return 'dodgerblue';
    if (p === 'VERDE') return 'green';
    return 'black';
  }
}
