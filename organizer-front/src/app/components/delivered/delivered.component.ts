import { Component, OnInit, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';
import { WebsocketService } from '../../services/websocket.service';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-delivered',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './delivered.component.html',
  styleUrls: ['./delivered.component.css']
})
export class DeliveredComponent implements OnInit, AfterViewInit {
  allOrders: orders[] = [];
  orders: orders[] = [];
  selectedOrder: orders | null = null;
  editOrderForm: FormGroup;
  returnForm: FormGroup; // Formulário de busca

  @ViewChild('orderDetailsModal') orderDetailsModal!: ElementRef;

  constructor(
    private orderService: OrderService,
    private fb: FormBuilder,
    private websocketService: WebsocketService,
    private modalService: NgbModal
  ) {
    this.editOrderForm = this.fb.group({
      id: [''],
      nr: ['', Validators.required],
      cliente: ['', Validators.required],
      prioridade: ['', Validators.required],
      status: ['', Validators.required],
      observacao: [''],
      dataH: [''],
      dataEntrega: [''],
      dataHRetorno: ['']
    });
    this.returnForm = this.fb.group({
      search: ['']
    });
  }

  ngOnInit(): void {
    this.loadOrders();
    this.setupWebSocket();
    console.log('Componente DeliveredComponent inicializado');
  }

  ngAfterViewInit(): void {
    if (this.orderDetailsModal) {
      console.log('Modal inicializado:', this.orderDetailsModal);
    }
  }

  loadOrders(): void {
    this.orderService.getOrders().subscribe((orders) => {
      this.allOrders = orders;
      this.orders = orders; // Inicia exibindo todos os pedidos
    });
  }

  filterOrders(): void {
    const searchTerm = this.returnForm.get('search')?.value?.toLowerCase() || '';

    if (searchTerm && !isNaN(+searchTerm)) {
      this.orderService.getOrderById(+searchTerm).subscribe((order) => {
        this.orders = order ? [order] : [];
      }, () => {
        this.orders = [];
      });
    } else {
      this.orders = this.allOrders.filter(order =>
        order.nr.toLowerCase().includes(searchTerm) ||
        order.cliente.toLowerCase().includes(searchTerm) ||
        order.prioridade.toLowerCase().includes(searchTerm)
      );
    }
  }

setupWebSocket(): void {
  this.websocketService.watchOrders().subscribe((message) => {
    const updatedOrder: orders = JSON.parse(message.body);

    const orderIndex = this.allOrders.findIndex(order => order.id === updatedOrder.id);
    if (orderIndex !== -1) {
      this.allOrders[orderIndex] = updatedOrder;
    } else {
      this.allOrders.push(updatedOrder);
    }

    const filteredIndex = this.orders.findIndex(order => order.id === updatedOrder.id);
    if (filteredIndex !== -1) {
      this.orders[filteredIndex] = updatedOrder;
    }
  });
}

  updateOrder(): void {
    if (this.editOrderForm.valid) {
      const updatedOrder: orders = { ...this.selectedOrder, ...this.editOrderForm.value };
      this.orderService.updateOrder(updatedOrder.id, updatedOrder).subscribe(() => {
        this.websocketService.sendUpdateOrder(updatedOrder);
        const index = this.orders.findIndex(order => order.id === updatedOrder.id);
        if (index !== -1) {
          this.orders[index] = updatedOrder; // Atualiza o pedido na lista atual
        }

        const allOrderIndex = this.allOrders.findIndex(order => order.id === updatedOrder.id);
        if (allOrderIndex !== -1) {
          this.allOrders[allOrderIndex] = updatedOrder; // Atualiza a lista completa
        }

        this.closeOrderDetails();
      });
    }
  }

  openOrderDetails(order: orders): void {
    this.selectedOrder = order;
    this.editOrderForm.patchValue({
      id: order.id,
      nr: order.nr,
      cliente: order.cliente,
      prioridade: order.prioridade,
      status: order.status,
      observacao: order.observacao,
      dataH: order.dataH,
      dataEntrega: order.dataEntrega,
      dataHRetorno: order.dataHRetorno
    });

    this.modalService.open(this.orderDetailsModal, { centered: true });
  }

  closeOrderDetails(): void {
    this.selectedOrder = null;
    this.modalService.dismissAll();
  }

  deleteOrder(order: orders): void {
    if (confirm('Tem certeza que deseja excluir este pedido?')) {
      this.orderService.deleteOrder(order.id).subscribe(() => {
        this.orders = this.orders.filter(o => o.id !== order.id);
        this.allOrders = this.allOrders.filter(o => o.id !== order.id);
        this.closeOrderDetails();
      });
    }
  }

  getPriorityColor(prioridade: string): string {
  switch (prioridade) {
    case 'Vermelho': return 'red';
    case 'Amarelo': return 'yellow';
    case 'Azul': return 'blue';
    case 'Verde': return 'green';
    default: return 'black';
  }
}

getStatusDescription(status: number): string {
  switch (status) {
    case 0: return 'Em Produção';
    case 1: return 'Cortada';
    case 2: return 'Pronto para Entrega';
    case 3: return 'Saiu para Entrega';
    case 4: return 'Retirada';
    case 5: return 'Entregue';
    default: return 'Desconhecido';
  }
}

filterOrdersByAnyAttribute(): void {
  const searchTerm = this.returnForm.get('search')?.value?.toLowerCase() || '';

  if (searchTerm) {
    this.orders = this.allOrders.filter(order =>
      Object.keys(order).some(key => {
        const value = (order as any)[key];
        return value?.toString().toLowerCase().includes(searchTerm);
      })
    );
  } else {
    this.orders = [...this.allOrders];  
  }
}


}

