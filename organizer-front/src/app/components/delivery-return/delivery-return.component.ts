import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { WebsocketService } from '../../services/websocket.service';
import { DateTime } from 'luxon';

@Component({
  selector: 'app-delivery-return',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './delivery-return.component.html',
  styleUrls: ['./delivery-return.component.css']
})
export class DeliveryReturnComponent implements OnInit {
  orders: orders[] = [];
  selectedOrders: orders[] = [];
  returnForm: FormGroup;

  constructor(
    private orderService: OrderService,
    private fb: FormBuilder,
    private websocketService: WebsocketService

  ) {
    this.returnForm = this.fb.group({
      entregador: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadOrders();
 }

  loadOrders(): void {
    const entregadorName = this.returnForm.get('entregador')?.value?.toLowerCase();

    if (!entregadorName) {
      return;
    }

    this.orderService.getOrders().subscribe((orders) => {
      this.orders = orders.filter(order => order.status === 3 && order.entregador?.toLowerCase() === entregadorName);

      if (this.orders.length === 0) {
        alert('Nenhum pedido encontrado para este entregador.');
      }
    });
  }

  onOrderSelectionChange(order: orders, event: any): void {
    if (event.target.checked) {
      this.selectedOrders.push(order);
    } else {
      this.selectedOrders = this.selectedOrders.filter(o => o !== order);
    }
  }

  confirmReturn(): void {
  if (this.selectedOrders.length === 0) {
    alert('Nenhum pedido selecionado');
    return;
  }

  const dataHRetorno = DateTime.now().setZone('America/Sao_Paulo').toJSDate();
  const entregador = this.returnForm.get('entregador')?.value || '';

  this.selectedOrders.forEach((order) => {
    order.status = 5;
    order.dataHRetorno = dataHRetorno;

    this.orderService.updateOrder(order.id, order).subscribe(() => {
      this.websocketService.sendUpdateOrder(order);
    });
  });

  this.selectedOrders = [];
  alert('Obrigado por confirmar o retorno da rota');
  window.location.href = '/entrega';
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
  }}

