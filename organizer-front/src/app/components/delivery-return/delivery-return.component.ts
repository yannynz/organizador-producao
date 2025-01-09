import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { WebsocketService } from '../../services/websocket.service';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable, forkJoin } from 'rxjs';
import { DateTime } from 'luxon';

@Component({
  selector: 'app-delivery-return',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './delivery-return.component.html',
  styleUrls: ['./delivery-return.component.css'],
})
export class DeliveryReturnComponent implements OnInit {
  orders: orders[] = [];
  selectedOrders: orders[] = [];
  groupedOrders: {
    cliente: string;
    pedidos: orders[];
    recebedor: string;
  }[] = [];
  returnForm: FormGroup;
  showModal = false;
  selectAll = false;

  constructor(
    private orderService: OrderService,
    private fb: FormBuilder,
    private websocketService: WebsocketService
  ) {
    this.returnForm = this.fb.group({
      entregador: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    const entregadorRaw = this.returnForm.get('entregador')?.value;
    const entregadorName = entregadorRaw
      ?.normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/\s+/g, ' ')
      .trim()
      .toLowerCase();

    if (!entregadorName) {
      return;
    }

    this.orderService.getOrders().subscribe((orders) => {
      this.orders = orders.filter(
        (order) =>
          order.status === 3 &&
          order.entregador
            ?.normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/\s+/g, ' ')
            .trim()
            .toLowerCase() === entregadorName
      );

      if (this.orders.length === 0) {
        alert('Nenhum pedido encontrado para este entregador.');
      }
    });
  }

  groupOrdersByClient(): void {
    this.groupedOrders = this.selectedOrders.reduce((groups, order) => {
      const group = groups.find((g) => g.cliente === order.cliente);
      if (group) {
        group.pedidos.push(order);
      } else {
        groups.push({
          cliente: order.cliente,
          pedidos: [order],
          recebedor: '',
        });
      }
      return groups;
    }, [] as { cliente: string; pedidos: orders[]; recebedor: string }[]);

    this.showModal = true;
  }

  onOrderSelectionChange(order: orders, event: any): void {
    if (event.target.checked) {
      this.selectedOrders.push(order);
    } else {
      this.selectedOrders = this.selectedOrders.filter((o) => o !== order);
      this.selectAll = false;
    }
  }

  // Valida se todos os grupos têm recebedor preenchido
  confirmGroupedReturn(): void {
    for (const group of this.groupedOrders) {
      if (!group.recebedor.trim()) {
        alert(`Por favor, preencha o recebedor para o cliente ${group.cliente}.`);
        return;
      }
    }
    const dataHRetorno = DateTime.now().setZone('America/Sao_Paulo').toJSDate();
    const entregador = this.returnForm.get('entregador')?.value || '';
    const updateRequests: Observable<orders>[] = [];
    this.groupedOrders.forEach((group) => {
      group.pedidos.forEach((order) => {
        order.dataHRetorno = dataHRetorno;
        order.status = 5;
        order.recebedor = group.recebedor;
        const update$ = this.orderService.updateOrder(order.id, order);
        updateRequests.push(update$);
      });
    });

    // Executa todas as atualizações em paralelo
    forkJoin(updateRequests).subscribe({
      next: () => {
        alert('Pedidos marcados como entregues com sucesso!');
        this.showModal = false;
        this.selectedOrders = [];
        this.groupedOrders = [];
        this.loadOrders();
      },
      error: (err) => {
        console.error('Erro ao processar atualizações:', err);
        alert('Ocorreu um erro ao processar as atualizações.');
      },
    });
  }

  updateRecebedor(cliente: string, recebedor: string): void {
    const group = this.groupedOrders.find((g) => g.cliente === cliente);
    if (group) {
      group.recebedor = recebedor.trim();
    }
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
  toggleSelectAll() {
    this.selectAll = !this.selectAll;
    if (this.selectAll) {
      this.selectedOrders = [...this.orders];
    } else {
      this.selectedOrders = [];
    }
  }

  cancelDelivery(order: orders): void {
    if (!confirm('Você tem certeza que deseja cancelar a saída deste pedido?')) {
      return;
    }
    const updatedOrder = { ...order };
    updatedOrder.status = 2;
    updatedOrder.dataEntrega = undefined;
    this.orderService.updateOrder(updatedOrder.id, updatedOrder).subscribe({
      next: () => {
        this.websocketService.sendUpdateOrder(updatedOrder);
        alert('Saída do pedido cancelada com sucesso.');
        this.loadOrders();
      },
      error: (err) => {
        console.error('Erro ao cancelar saída:', err);
        alert('Ocorreu um erro ao tentar cancelar a saída do pedido.');
      }
    });
  }

}

