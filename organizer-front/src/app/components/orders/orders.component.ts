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
  filteredPriority: string | null=null;


  constructor(
    private formBuilder: FormBuilder,
    private orderService: OrderService,
    private websocketService: WebsocketService
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
  this.loadOrders(); // Recarrega a lista de pedidos
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
    return order.status === 0 || order.status === 1;
  }

listenForWebSocketUpdates() {
  this.websocketService.watchOrders().subscribe((message: any) => {
    const receivedOrder = JSON.parse(message.body);
    console.log('Pedido recebido via WebSocket:', receivedOrder);

    const existingIndex = this.orders.findIndex(o => o.id === receivedOrder.id);

    if (existingIndex !== -1) {
      this.orders[existingIndex] = receivedOrder;
    } else if (this.shouldDisplayOrder(receivedOrder)) {
      this.orders.push(receivedOrder);
    }

    this.orders = this.orders.filter(order => this.shouldDisplayOrder(order));

    this.orders.sort((a, b) => this.comparePriorities(a.prioridade, b.prioridade));

    console.log('Lista de pedidos após atualização via WebSocket:', this.orders);
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


}
