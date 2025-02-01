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
  adverseOutputForm: FormGroup;

  @ViewChild('deliveryModal', { static: false }) deliveryModal!: TemplateRef<any>;
  @ViewChild('thankYouModal', { static: false }) thankYouModal!: TemplateRef<any>;
  @ViewChild('adverseOutputModal', { static: false }) adverseOutputModal!: TemplateRef<any>;

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
  }

  loadOrders(): void {
    this.orderService.getOrders().subscribe((orders) => {
      this.orders = orders.filter(order => [1, 2].includes(order.status))
        .sort((a, b) => this.comparePriorities(a.prioridade, b.prioridade));
      this.filteredOrders = [...this.orders];
    });
    (error: any) => {
      alert('Erro ao carregar os pedidos: ' + error.message);
    }
  }

  listenForNewOrders(): void {
  this.websocketService.watchOrders().subscribe((message: any) => {
    const receivedOrder = JSON.parse(message.body);
    console.log('Pedido recebido via WebSocket:', receivedOrder);
    this.orders.sort((a, b) => this.comparePriorities(a.prioridade, b.prioridade));
    console.log('Lista de pedidos após atualização via WebSocket:', this.orders);
    window.location.reload();
  });
}

  listenForNewOrdersPrioridades(): void {
  this.websocketService.watchPriorities().subscribe((message: any) => {
    const receivedOrder = JSON.parse(message.body);
    console.log('Pedido recebido via WebSocket:', receivedOrder);
    this.orders.sort((a, b) => this.comparePriorities(a.prioridade, b.prioridade));
    console.log('Lista de pedidos após atualização via WebSocket:', this.orders);
    window.location.reload();
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
    this.deliveryForm.reset(); // Resetar formulário
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
  if (this.deliveryForm.invalid) {
    alert('Preencha todos os campos obrigatórios.');
    return;
  }

  const deliveryPersonRaw = this.deliveryForm.get('deliveryPerson')?.value;
  const deliveryPerson = deliveryPersonRaw
    ?.normalize("NFD") // Decompor caracteres acentuados
    .replace(/[\u0300-\u036f]/g, "") // Remover marcas diacríticas
    .replace(/\s+/g, " ") // Substituir múltiplos espaços por um único espaço
    .trim() // Remover espaços no início e no final
    .toLowerCase(); // Converter para letras minúsculas

  const deliveryType = this.deliveryForm.get('deliveryType')?.value;
  const vehicleTypeControl = this.deliveryForm.get('vehicleType');
  const customVehicleControl = this.deliveryForm.get('customVehicle');
  const vehicle = vehicleTypeControl?.value === 'Outro' ? customVehicleControl?.value : vehicleTypeControl?.value;
  const currentDateTime = DateTime.now().setZone('America/Sao_Paulo').toJSDate();
  const recebedor = this.deliveryForm.get('recebedor')?.value;

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
      veiculo: vehicle,
      recebedor: recebedor,
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
  console.log(deliveryPerson);
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

