import { Component, OnInit, OnDestroy } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { orders } from '../../models/orders';
import { Subscription, interval } from 'rxjs';
import { WebsocketService } from '../../services/websocket.service';
import { CommonModule } from '@angular/common';
import { OrderService } from '../../services/orders.service';
import { DxfAnalysisService } from '../../services/dxf-analysis.service';
import { AuthService } from '../../services/auth.service';
import { User, UserRole } from '../../models/user.model';
import { DxfAnalysis } from '../../models/dxf-analysis';
import { environment } from '../../enviroment';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.css'],
})
export class OrdersComponent implements OnInit, OnDestroy {
  orders: orders[] = [];
  createOrderForm: FormGroup;
  editOrderForm: FormGroup;
  editingOrder: orders | undefined;
  ordersSubscription: Subscription | undefined;
  selectedOrder: orders | null = null;
  filteredPriority: string | null = null;
  expandedNr: string | null = null;
  imageUrls: { [key: string]: string } = {};
  loadingImage: { [key: string]: boolean } = {};
  currentUser: User | null = null;
  
  private readonly imagePublicBaseUrl = environment.imagePublicBaseUrl || '/facas-renders';


  constructor(
    private formBuilder: FormBuilder,
    private orderService: OrderService,
    private websocketService: WebsocketService,
    private dxfAnalysisService: DxfAnalysisService,
    private authService: AuthService
  ) {
    this.createOrderForm = this.formBuilder.group({
      nr: ['', Validators.required],
      cliente: ['', Validators.required],
      prioridade: ['', Validators.required],
      dataEntrega: [new Date(), Validators.required],
      dataH: [''],
    });

    this.editOrderForm = this.formBuilder.group({
      id: [''],
      nr: ['', Validators.required],
      cliente: ['', Validators.required],
      prioridade: ['', Validators.required],
      dataEntrega: [new Date(), Validators.required],
      status: [''],
      entregador: [''],
      observacao: [''],
      dataH: ['']
    });
  }

  ngOnDestroy(): void {
    this.ordersSubscription?.unsubscribe();
  }

  ngOnInit() {
    this.loadOrders();
    this.listenForWebSocketUpdates();
    this.listenForWebSocketPrioridade();

    this.authService.user$.subscribe(user => {
      this.currentUser = user;
    });
  }

  filterByPriority(priority: string) {
    this.filteredPriority = priority;
    this.applyFilter();
  }

  clearPriorityFilter() {
    this.filteredPriority = null;
    this.applyFilter();
  }

  applyFilter() {
    this.loadOrders();
  }

  loadOrders() {
    this.orderService.getOrders().subscribe((orders: orders[]) => {
      let filteredOrders = orders.filter(order =>
        this.shouldDisplayOrder(order)
      );

      if (this.filteredPriority) {
        filteredOrders = filteredOrders.filter(
          order => order.prioridade === this.filteredPriority
        );
      }

      this.orders = filteredOrders.sort((a, b) =>
        this.comparePriorities(a.prioridade, b.prioridade)
      );
    });
  }

  shouldDisplayOrder(order: orders): boolean {
    return order.status === 0 || order.status === 1 || order.status === 6;
  }

 listenForWebSocketUpdates() {
  this.websocketService.watchOrders().subscribe((message: any) => {
    const received: orders = JSON.parse(message.body);
    const idx = this.orders.findIndex(o => o.id === received.id);

    if (this.shouldDisplayOrder(received)) {
      if (idx !== -1) {
        this.orders[idx] = received;
      } else {
        this.orders = [...this.orders, received];
      }
    } else {
      if (idx !== -1) {
        this.orders = this.orders.filter(o => o.id !== received.id);
      }
    }

    this.orders = [...this.orders].sort((a, b) =>
      this.comparePriorities(a.prioridade, b.prioridade)
    );
  });
}

//listenForWebSocketDelete() {
  //this.websocketService.watchDeleteOrders().subscribe((message: any) => {
    //const received: orders = JSON.parse(message.body);
    //this.orders = this.orders.filter(o => o.id !== received.id);
  //});


 listenForWebSocketPrioridade() {
  this.websocketService.watchPriorities().subscribe((message: any) => {
    const received: orders = JSON.parse(message.body);
    const idx = this.orders.findIndex(o => o.id === received.id);

    if (this.shouldDisplayOrder(received)) {
      if (idx !== -1) this.orders[idx] = received;
      else this.orders = [...this.orders, received];
    } else {
      if (idx !== -1) this.orders = this.orders.filter(o => o.id !== received.id);
    }

    this.orders = [...this.orders].sort((a, b) =>
      this.comparePriorities(a.prioridade, b.prioridade)
    );
  });
}

  openCreateOrderModal() {
    this.createOrderForm.reset();
    const modal = new (window as any).bootstrap.Modal(document.getElementById('createOrderModal')!);
    modal.show();
  }

  openEditOrderModal(order: orders) {
    this.selectedOrder = order;
    this.editOrderForm.patchValue({
      nr: order.nr,
      cliente: order.cliente,
      prioridade: order.prioridade,
      dataEntrega: order.dataEntrega
    });
    const modal = new (window as any).bootstrap.Modal(document.getElementById('editOrderModal')!);
    modal.show();
  }

  closeModal(modalId: string) {
    const modalElement = document.getElementById(modalId);
    if (modalElement) {
      const modalInstance = new (window as any).bootstrap.Modal(modalElement);
      modalInstance.hide();
    }
  }

  createOrder() {
    const newOrder = this.createOrderForm.value;
    this.orderService.createOrder(newOrder).subscribe(() => {
      this.createOrderForm.reset();
      this.closeModal('createOrderModal');
      this.loadOrders();
    });
  }

  updateOrder(): void {
    if (this.editOrderForm.valid) {
      const updatedOrder = { ...this.editingOrder, ...this.editOrderForm.value };

      this.orderService.updateOrder(updatedOrder.id, updatedOrder).subscribe({
        next: (response) => {
          console.log('Pedido atualizado com sucesso:', response);
          this.loadOrders();
          this.editOrderForm.reset();
          this.editingOrder = undefined;
          this.closeModal('editOrderModal');
        },
        error: (err) => {
          console.error('Erro ao atualizar o pedido:', err);
        }
      });
    }
  }

  delete(orderId: number) {
    if (confirm('Tem certeza que deseja excluir este pedido?')) {
      this.orderService.deleteOrder(orderId).subscribe(() => {
        this.orders = this.orders.filter(order => order.id !== orderId);
      });
    }
  }

  updateOrdersList(order: orders) {
    const index = this.orders.findIndex(o => o.id === order.id);

    if (index !== -1) {
      this.orders[index] = order;
    } else {
      if (order.status === 0 || order.status === 1) {
        this.orders.push(order);
      }
    }
    this.orders = this.orders.filter(o => o.status === 0 || o.status === 1);
    this.orders.sort((a, b) => this.comparePriorities(a.prioridade, b.prioridade));
    console.log('Lista de pedidos após atualização:', this.orders);
  }

  comparePriorities(priorityA: string, priorityB: string) {
    const priorities = ['VERMELHO', 'AMARELO', 'AZUL', 'VERDE'];
    return priorities.indexOf(priorityA) - priorities.indexOf(priorityB);
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

  getStatusDescription(status: number): string {
    switch (status) {
      case 0:
        return 'Em Produção';
      case 1:
        return 'Cortada';
      case 2:
        return 'Pronto para Entrega';
      case 3:
        return 'Saiu para Entrega';
      case 4:
        return 'Retirada';
      case 5:
        return 'Entregue';
      case 6:
        return 'Tirada';
      case 7:
        return 'Montada (corte)';
      case 8:
        return 'Montada e vincada';
      default:
        return 'Desconhecido';
    }
  }

  highlightOrder(orderId: number) {
    const orderElement = document.getElementById(`order-${orderId}`);
    if (orderElement) {
      orderElement.classList.add('updated');
      setTimeout(() => orderElement.classList.remove('updated'), 2000);
    }
  }

  // Permite a visualização das imagens de análise DXF para qualquer usuário autenticado.
  canViewDxfAnalysis(): boolean {
    return true;
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
        if (analysis) {
          const resolved = this.resolvePublicImageUrl(analysis);
          this.imageUrls[nr] = resolved || '';
        } else {
          this.imageUrls[nr] = ''; // Marca como sem imagem
        }
        this.loadingImage[nr] = false;
      },
      error: () => {
        this.imageUrls[nr] = '';
        this.loadingImage[nr] = false;
      }
    });
  }

  private resolvePublicImageUrl(analysis: DxfAnalysis): string | null {
    // 1. Tenta usar URLs diretas (imageUri ou imageUrl)
    const candidates = [analysis.imageUrl, analysis.imageUri];
    for (const c of candidates) {
      if (c && c.trim()) {
        const trimmed = c.trim();
        // Se for data URI ou http/https, usa direto
        if (trimmed.startsWith('data:') || trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
          return trimmed;
        }
        // Se começar com //, adiciona protocolo (assume https se não detectar window)
        if (trimmed.startsWith('//')) {
           return `https:${trimmed}`;
        }
        // Se for relativo/outro, retorna (pode ser relativo à raiz)
        return trimmed;
      }
    }

    // 2. Tenta construir a partir do bucket/key
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
