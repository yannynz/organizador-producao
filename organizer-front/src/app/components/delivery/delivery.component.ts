import { Component, OnInit, ViewChild, TemplateRef } from '@angular/core';
import { FormBuilder, Validators, FormGroup, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { OrderService } from '../../services/orders.service';
import { WebsocketService } from '../../services/websocket.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { orders } from '../../models/orders';
import { CommonModule } from '@angular/common';
import { DateTime } from 'luxon';

@Component({
  selector: 'app-delivery',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './delivery.component.html',
  styleUrls: ['./delivery.component.css'],
})
export class DeliveryComponent implements OnInit {
  orders: orders[] = [];
  selectedOrders: orders[] = [];
  deliveryForm: FormGroup;
  filteredOrders: orders[] = [];
  searchTerm: string = '';

  @ViewChild('deliveryModal', { static: false }) deliveryModal!: TemplateRef<any>;
  @ViewChild('thankYouModal', { static: false }) thankYouModal!: TemplateRef<any>;


  constructor(
    private orderService: OrderService,
    private fb: FormBuilder,
    private websocketService: WebsocketService,
    private modalService: NgbModal
  ) {
    this.deliveryForm = this.fb.group({
      deliveryPerson: ['', Validators.required],
      notes: [''],
      deliveryType: ['', Validators.required],
      vehicleType: [''],
      customVehicle: [''],
    });
  }

  ngOnInit(): void {
    this.loadOrders();
    this.listenForNewOrders();
  }

  loadOrders(): void {
    this.orderService.getOrders().subscribe((orders) => {
      this.orders = orders.filter(order => [1, 2].includes(order.status))
        .sort((a, b) => this.comparePriorities(a.prioridade, b.prioridade));
      this.filteredOrders = [...this.orders];
    });
  }

  listenForNewOrders(): void {
    this.websocketService.watchOrders().subscribe((message: any) => {
      const order = JSON.parse(message.body);
      this.updateOrdersList(order);
    });
  }

  updateOrdersList(order: orders) {
    const index = this.orders.findIndex(o => o.id === order.id);
    if (index !== -1) {
      this.orders[index] = order;
    } else {
      this.orders.push(order);
    }
    this.orders.sort((a, b) => this.comparePriorities(a.prioridade, b.prioridade));
    this.selectedOrders = [];
  }

  onOrderSelectionChange(order: orders, event: any): void {
    if (event.target.checked) {
      this.selectedOrders.push(order);
    } else {
      this.selectedOrders = this.selectedOrders.filter(o => o !== order);
    }
  }

  preConfirmDelivery(): void {
    if (this.selectedOrders.length === 0) {
      alert('Nenhum pedido selecionado');
    } else {
      this.deliveryForm.reset(); // Resetar formulário
      this.modalService.open(this.deliveryModal); // Abrir o modal de entrega
    }
  }

  confirmDelivery(): void {
    if (this.deliveryForm.invalid) {
      alert('Preencha todos os campos obrigatórios.');
      return;
    }

    const deliveryPerson = this.deliveryForm.get('deliveryPerson')?.value;
    const deliveryType = this.deliveryForm.get('deliveryType')?.value;
    const vehicleTypeControl = this.deliveryForm.get('vehicleType');
    const customVehicleControl = this.deliveryForm.get('customVehicle');
    const vehicle = vehicleTypeControl?.value === 'Outro' ? customVehicleControl?.value : vehicleTypeControl?.value;
    const currentDateTime = DateTime.now().setZone('America/Sao_Paulo').toJSDate();

    let status: number;
    if (deliveryType === 'Sair para entrega') {
      status = 3;
    } else if (deliveryType === 'Retirada') {
      status = 4;
    } else {
      alert('Tipo de entrega inválido.');
      return;
    }

    this.selectedOrders.forEach((order) => {
      const updatedOrder = {
        ...order,
        entregador: deliveryPerson,
        status: status,
        dataEntrega: currentDateTime,
        veiculo: vehicle
      };

      this.orderService.updateOrder(order.id, updatedOrder).subscribe(
        () => {
          this.websocketService.sendUpdateOrder(updatedOrder);

          this.loadOrders();
        },
        (error) => {
          alert(`Erro ao atualizar o pedido ${order.nr}: ${error}`);
        }
      );
    });

    this.modalService.dismissAll();
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
      order.status.toString().includes(term)
    );
  }
}

