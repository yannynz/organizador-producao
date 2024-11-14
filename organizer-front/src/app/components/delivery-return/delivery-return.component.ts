import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

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
    private fb: FormBuilder
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

    const dataHRetorno = new Date();  // Obter a data e hora atuais para o retorno
    const entregador = this.returnForm.get('entregador')?.value || '';

    this.selectedOrders.forEach((order) => {
      order.status = 5;  // Definir status como entregue
      order.dataHRetorno = dataHRetorno;  // Definir a data e hora de retorno

      this.orderService.updateOrder(order.id, order).subscribe(() => {
        this.loadOrders();
      });
    });

    this.selectedOrders = [];
    alert('Obrigado por confirmar o retorno da rota');
    window.location.href = '/entrega';
  }

  getPriorityColor(prioridade: string): string {
    const colors: { [key: string]: string } = {
      'Vermelho': 'red',
      'Amarelo': 'orange',
      'Azul': 'blue',
      'Verde': 'green'
    };
    return colors[prioridade] || 'black';
  }

}

