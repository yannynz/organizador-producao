import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';

import { OrderService } from '../../../../services/orders.service';
import { WebsocketService } from '../../../../services/websocket.service';
import { orders } from '../../../../models/orders';
import { AdvancedFiltersComponent } from '../../../../components/advanced-filters/advanced-filters.component';
import { OrderDetailsModalComponent } from '../../../../components/order-details-modal/order-details-modal.component';
import { OrderFilters } from '../../../../models/order-filters';

@Component({
  selector: 'app-pipeline-board',
  standalone: true,
  imports: [CommonModule, AdvancedFiltersComponent, OrderDetailsModalComponent],
  templateUrl: './pipeline-board.component.html',
  styleUrls: ['./pipeline-board.component.css']
})
export class PipelineBoardComponent implements OnInit, OnDestroy {
  statusDescriptions: { [key: number]: string } = {
    0: 'Em Produção', 1: 'Cortada', 7: 'Montada', 2: 'Pronto para Entrega',
    3: 'Saiu para Entrega', 6: 'Tirada', 4: 'Retirada', 5: 'Entregue'
  };
  statusList = Object.entries(this.statusDescriptions).map(([k,v]) => ({ key: +k, label: v }));

  cols = [
    { key: 0, label: 'Em Produção' },
    { key: 1, label: 'Cortada' },
    { key: 6, label: 'Tirada' },
    { key: 7, label: 'Montada' },
    { key: 2, label: 'Pronto p/ Entrega' },
    { key: 3, label: 'Saiu p/ Entrega' },
  ];

  data: Record<number, orders[]> = {};
  counts: Record<number, number> = {};
  loading = false;

  detailsOpen = false;
  selectedOrder: orders | null = null;

  currentFilters: OrderFilters = {};

  // mapa do último status conhecido (apenas para itens que passam no filtro atual)
  private knownStatus: Record<number, number> = {};
  private subs = new Subscription();

  constructor(private orderService: OrderService, private websocket: WebsocketService) {}

  ngOnInit(): void {
    this.reload();

    // WS: pedidos gerais
    this.subs.add(
      this.websocket.watchOrders().subscribe(msg => {
        const o: orders = JSON.parse(msg.body);
        this.applyRealtime(o);
      })
    );

    // WS: mudanças de prioridade (seu service já expõe esse tópico)
    this.subs.add(
      this.websocket.watchPriorities().subscribe(msg => {
        const o: orders = JSON.parse(msg.body);
        this.applyRealtime(o);
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  reload() {
    this.loading = true;
    this.orderService.getOrders().subscribe(all => {
      const filtered = this.applyFiltersLocal(all, this.currentFilters);

      this.data = {};
      this.counts = {};
      this.knownStatus = {};

      this.cols.forEach(c => {
        const colItems = filtered.filter(x => x.status === c.key);
        this.data[c.key] = colItems.slice(0, 50);
        this.counts[c.key] = colItems.length;
        // registra status conhecido somente para itens que passam no filtro atual
        colItems.forEach(it => { if (it.id != null) this.knownStatus[it.id] = c.key; });
      });

      this.loading = false;
    }, _ => this.loading = false);
  }

  onApplyFilters(ev: { search: string; filters: OrderFilters }) {
    this.currentFilters = ev.filters || {};
    this.reload();
  }
  onClearFilters() {
    this.currentFilters = {};
    this.reload();
  }

  private applyFiltersLocal(list: orders[], f: OrderFilters) {
    const q = (f.q || '').toLowerCase();
    return list.filter(o => {
      if (f.id && o.id !== f.id) return false;
      if (f.nr && !o.nr.toLowerCase().includes(f.nr.toLowerCase())) return false;
      if (f.cliente && !o.cliente.toLowerCase().includes(f.cliente.toLowerCase())) return false;
      if (f.prioridade && o.prioridade !== f.prioridade) return false;
      if (f.status?.length && !f.status.includes(o.status)) return false;
      if (f.entregador && (o.entregador||'').toLowerCase().indexOf(f.entregador.toLowerCase()) === -1) return false;
      if (f.veiculo && (o.veiculo||'').toLowerCase().indexOf(f.veiculo.toLowerCase()) === -1) return false;
      if (f.recebedor && (o.recebedor||'').toLowerCase().indexOf(f.recebedor.toLowerCase()) === -1) return false;
      if (f.montador && (o.montador||'').toLowerCase().indexOf(f.montador.toLowerCase()) === -1) return false;
      if (f.observacao && (o.observacao||'').toLowerCase().indexOf(f.observacao.toLowerCase()) === -1) return false;

      const inRange = (d?: Date, from?: string, to?: string) => {
        if (!from && !to) return true;
        const t = d ? new Date(d).getTime() : NaN;
        const tf = from ? new Date(from).getTime() : -Infinity;
        const tt = to ? new Date(to).getTime() : +Infinity;
        return !isNaN(t) && t >= tf && t <= tt;
      };
      if (!inRange(o.dataH, f.dataHFrom, f.dataHTo)) return false;
      if (!inRange(o.dataEntrega, f.dataEntregaFrom, f.dataEntregaTo)) return false;
      if (!inRange(o.dataHRetorno, f.dataHRetornoFrom, f.dataHRetornoTo)) return false;
      if (!inRange(o.dataMontagem, f.dataMontagemFrom, f.dataMontagemTo)) return false;

      if (q) {
        const S = (x?: string) => (x||'').toLowerCase();
        const D = (d?: Date) => d ? new Date(d).toLocaleDateString('pt-BR') : '';
        const hay =
          S(o.nr).includes(q) || S(o.cliente).includes(q) || S(o.prioridade).includes(q) ||
          S(this.statusDescriptions[o.status]).includes(q) || S(o.entregador).includes(q) ||
          S(o.observacao).includes(q) || S(o.veiculo).includes(q) || S(o.recebedor).includes(q) ||
          S(o.montador).includes(q) || D(o.dataH).includes(q) || D(o.dataEntrega).includes(q) ||
          D(o.dataHRetorno).includes(q) || D(o.dataMontagem).includes(q);
        if (!hay) return false;
      }
      return true;
    });
  }

  private matchesCurrentFilter(o: orders): boolean {
    // reaproveita a própria função de filtro
    return this.applyFiltersLocal([o], this.currentFilters).length > 0;
  }

  applyRealtime(o: orders) {
    if (o?.id == null) return;

    const passes = this.matchesCurrentFilter(o);
    const prev = this.knownStatus[o.id]; // status conhecido só para itens que PASSAVAM no filtro

    // 1) remover de qualquer coluna visível (para evitar duplicatas)
    const keys = this.cols.map(c => c.key);
    for (const k of keys) {
      const arr = this.data[k] || [];
      const idx = arr.findIndex(x => x.id === o.id);
      if (idx !== -1) { arr.splice(idx, 1); this.data[k] = arr; }
    }

    // 2) se passa no filtro atual, inserir/atualizar na coluna nova (top 50)
    if (passes && keys.includes(o.status)) {
      const arr = this.data[o.status] || [];
      const i = arr.findIndex(x => x.id === o.id);
      if (i !== -1) arr[i] = o; else arr.unshift(o);
      this.data[o.status] = arr.slice(0, 50);
    }

    // 3) ajustar contadores com base no status conhecido + filtro
    if (prev !== undefined) {
      // antes ele fazia parte do conjunto filtrado
      if (passes) {
        if (prev !== o.status) {
          if (this.counts[prev] != null) this.counts[prev] = Math.max(0, this.counts[prev] - 1);
          if (this.counts[o.status] != null) this.counts[o.status] = (this.counts[o.status] || 0) + 1;
        }
        this.knownStatus[o.id] = o.status; // continua no conjunto filtrado
      } else {
        // deixou de passar no filtro
        if (this.counts[prev] != null) this.counts[prev] = Math.max(0, this.counts[prev] - 1);
        delete this.knownStatus[o.id];
      }
    } else {
      // antes NÃO fazia parte do conjunto filtrado (ou nunca visto)
      if (passes) {
        if (this.counts[o.status] != null) this.counts[o.status] = (this.counts[o.status] || 0) + 1;
        this.knownStatus[o.id] = o.status;
      }
    }
  }

  open(o: orders) { this.selectedOrder = o; this.detailsOpen = true; }
  close() { this.selectedOrder = null; this.detailsOpen = false; }

  handleUpdate(o: orders) {
    this.orderService.updateOrder(o.id, o).subscribe(() => {
      // mantém seu publish para o backend (não mexi na service)
      this.websocket.sendUpdateOrder(o);
      this.close();
      // sem reload: o WS reflete a mudança
    });
  }

  handleDelete(id: number) {
    this.orderService.deleteOrder(id).subscribe(() => {
      this.websocket.sendDeleteOrder(id);
      this.close();
      // sem reload: se o backend publicar delete, trate aqui num handler próprio;
      // se não publicar, você pode remover localmente se quiser.
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

