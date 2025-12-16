import { Component, OnInit, ViewChild, TemplateRef } from '@angular/core';
import { FormBuilder, Validators, FormGroup, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { OrderService } from '../../services/orders.service';
import { WebsocketService } from '../../services/websocket.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { orders } from '../../models/orders';
import { CommonModule } from '@angular/common';
import { DateTime } from 'luxon';
import { forkJoin } from 'rxjs';
import { tap } from 'rxjs/operators';
import { OrderStatus } from '../../models/order-status.enum';
import { AuthService } from '../../services/auth.service';
import { UserService } from '../../services/user.service';
import { User, UserRole } from '../../models/user.model';
import { UserSelectorComponent } from '../shared/user-selector/user-selector.component';
import { DxfAnalysisService } from '../../services/dxf-analysis.service';

@Component({
  selector: 'app-delivery',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, UserSelectorComponent],
  templateUrl: './delivery.component.html',
  styleUrls: ['./delivery.component.css'],
})
export class DeliveryComponent implements OnInit {
  orders: orders[] = [];
  selectedOrders: orders[] = [];
  deliveryForm: FormGroup;
  filteredOrders: orders[] = [];
  searchTerm: string = '';
  adverseOutputForm: FormGroup;
  selectedIds = new Set<number>();
  users: User[] = [];
  currentUser: User | null = null;

  expandedNr: string | null = null;
  imageUrls: { [key: string]: string } = {};
  loadingImage: { [key: string]: boolean } = {};

  private readonly statusElegiveis = new Set<number>([
    OrderStatus.Cortada,
    OrderStatus.ProntoEntrega,
    OrderStatus.Tirada,
    OrderStatus.MontadaCorte,
    OrderStatus.MontadaCompleta,
  ]);

  @ViewChild('deliveryModal', { static: false }) deliveryModal!: TemplateRef<any>;
  @ViewChild('thankYouModal', { static: false }) thankYouModal!: TemplateRef<any>;
  @ViewChild('adverseOutputModal', { static: false }) adverseOutputModal!: TemplateRef<any>;

  constructor(
    private orderService: OrderService,
    private fb: FormBuilder,
    private websocketService: WebsocketService,
    private modalService: NgbModal,
    private authService: AuthService,
    private userService: UserService,
    private dxfAnalysisService: DxfAnalysisService
  ) {
    this.deliveryForm = this.fb.group({
      deliveryPerson: ['', Validators.required],
      notes: [''],
      deliveryType: ['', Validators.required],
      vehicleType: [''],
      customVehicle: [''],
      recebedor: [''],
    });
    this.adverseOutputForm = this.fb.group({
      adverseType: ['', Validators.required],
      customAdverseType: [''],
      cliente: ['', Validators.required],
      observacao: [''],
    });

  }

  ngOnInit(): void {
    this.loadOrders();
    this.listenForNewOrders();
    this.listenForNewOrdersPrioridades();
    
    this.authService.user$.subscribe(user => {
        this.currentUser = user;
        this.loadUsersForRole(user);
        if (user) {
            const current = this.deliveryForm.get('deliveryPerson')?.value || '';
            if (!current) {
                this.deliveryForm.patchValue({ deliveryPerson: user.name });
            }
        }
    });
  }

  toggleImage(nr: string): void {
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
        if (analysis && analysis.imageUrl) {
          this.imageUrls[nr] = analysis.imageUrl;
        } else {
          this.imageUrls[nr] = '';
        }
        this.loadingImage[nr] = false;
      },
      error: () => {
        this.imageUrls[nr] = '';
        this.loadingImage[nr] = false;
      }
    });
  }

  private loadUsersForRole(user: User | null) {
      if (!user) { return; }
      if (user.role === UserRole.ADMIN || user.role === UserRole.DESENHISTA) {
          this.userService.getAll().subscribe(users => {
              this.users = users;
          });
      } else {
          // Operador só vê a si mesmo, evitando chamada proibida ao backend.
          this.users = [user];
      }
  }


  loadOrders(): void {
  this.orderService.getOrders().subscribe({
    next: (orders) => {
      this.orders = orders
        .filter(order => this.statusElegiveis.has(order.status ?? -1))
        .sort((a, b) => this.comparePriorities(a.prioridade, b.prioridade));
      this.filteredOrders = [...this.orders];
    },
    error: (error) => {
      alert('Erro ao carregar os pedidos: ' + error.message);
    }
  });
}

listenForNewOrders(): void {
  this.websocketService.watchOrders().subscribe(msg => {
    const received: orders = JSON.parse(msg.body);
    console.log('[WS] watchOrders:', received);
    this.handleIncomingDeliveryOrder(received);
  });
}

listenForNewOrdersPrioridades(): void {
  this.websocketService.watchPriorities().subscribe(msg => {
    const received: orders = JSON.parse(msg.body);
    console.log('[WS] watchPriorities:', received);
    this.handleIncomingDeliveryOrder(received);
  });
}

private handleIncomingDeliveryOrder(received: orders) {
  if (!this.statusElegiveis.has(received.status ?? -1)) {
    this.orders = this.orders.filter(o => o.id !== received.id);
  } else {
    const idx = this.orders.findIndex(o => o.id === received.id);
    if (idx !== -1) this.orders[idx] = received;
    else this.orders = [...this.orders, received];
  }
  this.orders.sort((a, b) =>
    this.comparePriorities(a.prioridade, b.prioridade)
  );
  this.filteredOrders = [...this.orders];
}

  updateOrdersList(order: orders) {
    const index = this.orders.findIndex(o => o.id === order.id);
    if (index !== -1) {
      this.orders[index] = order;
    } else if (this.statusElegiveis.has(order.status ?? -1)) {
      this.orders.push(order);
    }
    this.orders = this.orders
      .filter(o => this.statusElegiveis.has(o.status ?? -1))
      .sort((a, b) => this.comparePriorities(a.prioridade, b.prioridade));
    this.selectedOrders = [];
  }

  onOrderSelectionChange(order: orders, checked: boolean): void {
  if (checked) this.selectedIds.add(order.id);
  else this.selectedIds.delete(order.id);
  this.selectedOrders = this.orders.filter(o => this.selectedIds.has(o.id));
}

  preConfirmDelivery(): void {
    this.deliveryForm.reset(); // Resetar formulário
    
    if (this.currentUser) {
        this.deliveryForm.patchValue({ deliveryPerson: this.currentUser.name });
    }

    this.modalService.open(this.deliveryModal); // Abrir o modal de entrega
  }

  addAdverseOutput(): void {
  if (this.adverseOutputForm.invalid) {
    alert('Preencha todos os campos obrigatórios.');
    return;
  }

  const selectedAdverseType =
    this.adverseOutputForm.get('adverseType')?.value === 'Outro'
      ? this.adverseOutputForm.get('customAdverseType')?.value
      : this.adverseOutputForm.get('adverseType')?.value;

  if (!selectedAdverseType) {
    alert('Informe o tipo de saída.');
    return;
  }

  const newAdverseOrder: orders = {
    id: 0, // Será gerado pelo backend
    nr: selectedAdverseType,
    cliente: this.adverseOutputForm.get('cliente')?.value,
    dataH: DateTime.now().setZone('America/Sao_Paulo').toJSDate(),
    prioridade: 'VERDE',
    status: 2,
    observacao: this.adverseOutputForm.get('observacao')?.value || 'Sem observações',
    isOpen: false,
  };

  this.orderService.createOrder(newAdverseOrder).subscribe(
    (savedOrder) => {
      this.websocketService.sendCreateOrder(savedOrder);
      this.loadOrders();
      this.modalService.dismissAll();
    },
    (error) => {
      alert('Erro ao salvar a saída adversa: ' + error.message);
    }
  );
}

  openAdverseOutputModal(): void {
    this.adverseOutputForm.reset(); // Resetar o formulário antes de abrir o modal
    this.modalService.open(this.adverseOutputModal); // Abrir o modal de saída adversa
  }


confirmDelivery(): void {
  if (this.deliveryForm.invalid || this.selectedOrders.length === 0) {
    alert('Preencha os campos obrigatórios e selecione ao menos 1 pedido.');
    return;
  }

  const deliveryPerson = this.deliveryForm.get('deliveryPerson')?.value
    ?.normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();

  const deliveryType = this.deliveryForm.get('deliveryType')?.value;
  const vehicleType = this.deliveryForm.get('vehicleType')?.value;
  const customVehicle = this.deliveryForm.get('customVehicle')?.value;
  const vehicle = vehicleType === 'Outro' ? customVehicle : vehicleType;

  const recebedor = this.deliveryForm.get('recebedor')?.value;
  const currentDateTime = DateTime.now().setZone('America/Sao_Paulo').toJSDate();

  let status: number;
  if (deliveryType === 'Sair para entrega') status = 3;
  else if (deliveryType === 'Retirada') status = 4;
  else {
    alert('Tipo de entrega inválido.');
    return;
  }

  const updates$ = this.selectedOrders.map(order => {
    const updatedOrder = {
      ...order,
      entregador: deliveryPerson,
      status,
      dataEntrega: currentDateTime,
      veiculo: vehicle,
      recebedor,
    };
    return this.orderService.updateOrder(order.id, updatedOrder).pipe(
      // Só envia o WS depois que o backend confirmou a atualização
      tap(() => this.websocketService.sendUpdateOrder(updatedOrder))
    );
  });

  forkJoin(updates$).subscribe({
    next: () => {
      this.loadOrders();            // atualiza a lista uma vez
      this.selectedOrders = [];     // limpa seleção
      this.modalService.dismissAll();
    },
    error: (err) => {
      alert('Erro ao atualizar pedidos: ' + (err?.message || err));
    }
  });
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

  removeFromSelection(order: orders): void {
    this.selectedOrders = this.selectedOrders.filter(o => o !== order);
  }

  highlightOrder(orderId: number) {
    const orderElement = document.getElementById(`order-${orderId}`);
    if (orderElement) {
      orderElement.classList.add('updated');
      setTimeout(() => orderElement.classList.remove('updated'), 2000);
    }
  }

  filterOrders(): void {
    const term = this.searchTerm.toLowerCase();
    this.filteredOrders = this.orders.filter(order =>
      order.id.toString().includes(term) ||
      order.nr.toLowerCase().includes(term) ||
      order.cliente.toLowerCase().includes(term) ||
      order.prioridade.toLowerCase().includes(term) ||
      (order.status !== undefined && order.status !== null && order.status.toString().includes(term)) ||
      this.getStatusDescription(order.status).toLowerCase().includes(term)
    );
  }

  getStatusDescription(status?: number | null): string {
    if (status === undefined || status === null) {
      return 'Desconhecido';
    }
    switch (status) {
      case OrderStatus.EmProducao:
        return 'Em produção';
      case OrderStatus.Cortada:
        return 'Cortada';
      case OrderStatus.ProntoEntrega:
        return 'Pronto para entrega';
      case OrderStatus.SaiuEntrega:
        return 'Saiu para entrega';
      case OrderStatus.Retirada:
        return 'Retirada';
      case OrderStatus.Entregue:
        return 'Entregue';
      case OrderStatus.Tirada:
        return 'Tirada';
      case OrderStatus.MontadaCorte:
        return 'Montada (corte)';
      case OrderStatus.MontadaCompleta:
        return 'Montada e vincada';
      default:
        return 'Desconhecido';
    }
  }
}
