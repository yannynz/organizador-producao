import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { OrderService } from '../../services/orders.service';
import { orders } from '../../models/orders';
import { WebsocketService } from '../../services/websocket.service';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DateTime } from 'luxon';
import { format } from 'date-fns';
import { debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-delivered',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './delivered.component.html',
  styleUrls: ['./delivered.component.css']
})
export class DeliveredComponent implements OnInit {

  @ViewChild('adverseOutputModal') adverseOutputModal!: ElementRef;
  adverseOutputForm: FormGroup;
  allOrders: orders[] = [];
  orders: orders[] = [];
  selectedOrder: orders | null = null;
  editOrderForm: FormGroup;
  returnForm: FormGroup; // Formulário de busca
  currentPage: number = 0;
  pageSize: number = 20;
  totalPages: number = 0;
  visiblePages: number[] = [];


  statusDescriptions: { [key: number]: string } = {
    0: 'Em Produção',
    1: 'Cortada',
    2: 'Pronto para Entrega',
    3: 'Saiu para Entrega',
    4: 'Retirada',
    5: 'Entregue',

  };

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
      dataHRetorno: [''],
      veiculo: [''],
      recebedor: [''],
    });
    this.returnForm = this.fb.group({
      search: ['']
    });
    this.adverseOutputForm = this.fb.group({
      adverseType: ['', Validators.required],
      customAdverseType: [''],
      cliente: ['', Validators.required],
      prioridade: ['VERDE', Validators.required],
      observacao: ['']
    });
  }

  ngOnInit(): void {
    this.loadOrders();
    this.setupWebSocket();
    this.returnForm.get('search')?.valueChanges.pipe(debounceTime(300)).subscribe(() => {
      this.filterOrdersByAnyAttribute();
    });
  }

  loadOrders(): void {
    this.orderService.getOrders().subscribe((orders) => {
      this.allOrders = orders.sort((a, b) => {
        const dateA = a.dataH ? new Date(a.dataH).getTime() : 0;
        const dateB = b.dataH ? new Date(b.dataH).getTime() : 0;
        return dateB - dateA; // Ordena do mais novo para o mais antigo
      });
      this.totalPages = Math.ceil(this.allOrders.length / this.pageSize);
      this.filterOrdersByAnyAttribute();
      this.updateOrdersForCurrentPage();
      this.updateVisiblePages();
    });
  }

  updateOrdersForCurrentPage(): void {
    const startIndex = this.currentPage * this.pageSize;
    const endIndex = startIndex + this.pageSize;
    this.orders = this.allOrders.slice(startIndex, endIndex);
  }

  updateVisiblePages(): void {
    const pages = [];
    const startPage = Math.max(0, this.currentPage - 2); // Pega a página inicial com 2 antes da página atual
    const endPage = Math.min(this.totalPages - 1, startPage + 4); // Mostra até 5 páginas no máximo

    for (let i = startPage; i <= endPage; i++) {
      pages.push(i);
    }

    this.visiblePages = pages;
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.updateOrdersForCurrentPage();
      this.updateVisiblePages();
    }
  }

  filterOrders(): void {
    this.filterOrdersByAnyAttribute(); // Filtro chamado ao submeter o formulário de busca
  }

  filterOrdersByAnyAttribute(): void {
    const searchTerm = this.returnForm.get('search')?.value?.toLowerCase() || '';

    if (searchTerm) {
      this.orders = this.allOrders.filter(order => {
        const creationDate = order.dataH ? format(new Date(order.dataH), 'dd/MM/yyyy') : '';
        const deliveryDate = order.dataEntrega ? format(new Date(order.dataEntrega), 'dd/MM/yyyy') : '';
        const statusDescription = this.statusDescriptions[order.status]?.toLowerCase() || '';

        return (
          order.nr.toLowerCase().includes(searchTerm) || // Número do pedido
          order.cliente.toLowerCase().includes(searchTerm) || // Nome do cliente
          order.prioridade.toLowerCase().includes(searchTerm) || // Prioridade
          statusDescription.includes(searchTerm) || // Descrição do status
          (order.entregador && order.entregador.toLowerCase().includes(searchTerm)) || // Entregador
          (order.observacao && order.observacao.toLowerCase().includes(searchTerm)) || // Observação
          (order.veiculo && order.veiculo.toLowerCase().includes(searchTerm)) || // Veículo
          creationDate.includes(searchTerm) || // Data de criação
          deliveryDate.includes(searchTerm) // Data de entrega
        );
      });
      this.totalPages = Math.ceil(this.orders.length / this.pageSize); // Atualiza número de páginas após o filtro
      this.updateVisiblePages(); // Atualiza as páginas visíveis
    } else {
      this.orders = [...this.allOrders]; // Se não houver termo de pesquisa, mostrar todos os pedidos
      this.totalPages = Math.ceil(this.orders.length / this.pageSize);
      this.updateVisiblePages();
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
      this.totalPages = Math.ceil(this.allOrders.length / this.pageSize);
      this.updateOrdersForCurrentPage();
      this.updateVisiblePages();
    });
  }

updateOrder(): void {
  if (this.editOrderForm.valid) {
    // Cria uma cópia do pedido com os novos dados
    const updatedOrder: orders = { ...this.selectedOrder, ...this.editOrderForm.value };
    const currentSearchTerm = this.returnForm.get('search')?.value?.toLowerCase(); // Armazena o termo de pesquisa
    const currentPage = this.currentPage; // Armazena a página atual

    // Chama o serviço para atualizar o pedido no banco de dados
    this.orderService.updateOrder(updatedOrder.id, updatedOrder).subscribe(
      () => {
        // Atualiza a lista de pedidos locais
        const orderIndex = this.allOrders.findIndex(order => order.id === updatedOrder.id);
        if (orderIndex !== -1) {
          this.allOrders[orderIndex] = updatedOrder;
        } else {
          this.allOrders.push(updatedOrder);
        }

        // Realiza a filtragem novamente com o termo de pesquisa atual
        this.returnForm.get('search')?.setValue(currentSearchTerm); // Define o termo de pesquisa de volta
        this.filterOrdersByAnyAttribute(); // Aplica o filtro para o termo de pesquisa

        // Atualiza a paginação
        this.totalPages = Math.ceil(this.orders.length / this.pageSize); // Recalcula o número de páginas
        this.updateVisiblePages(); // Atualiza as páginas visíveis

        // Atualiza os pedidos para a página atual
        this.updateOrdersForCurrentPage();

        // Garante que a página atual seja preservada após a atualização
        this.currentPage = currentPage;

        // Fecha o modal de edição
        this.closeOrderDetails();
      },
      (error) => {
        console.error('Erro ao atualizar o pedido:', error);
        alert('Erro ao atualizar o pedido.');
      }
    );
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
      dataHRetorno: order.dataHRetorno,
      veiculo: order.veiculo,
      recebedor: order.recebedor,
    });

    this.modalService.open(this.orderDetailsModal, { centered: true });
  }

  closeOrderDetails(): void {
    this.selectedOrder = null;
    this.modalService.dismissAll();
  }

  deleteOrder(): void {
    if (!this.selectedOrder) {
      alert('Nenhum pedido selecionado para exclusão.');
      return;
    }

    const orderId = this.selectedOrder?.id; // Utiliza o operador opcional para evitar erro
    if (
      confirm(
        `Tem certeza que deseja excluir o pedido de ID ${orderId}? Esta ação não pode ser desfeita.`
      )
    ) {
      this.orderService.deleteOrder(orderId!).subscribe(
        () => {
          // Atualiza a lista de pedidos e remove o pedido excluído
          this.orders = this.orders.filter((o) => o.id !== orderId);
          this.allOrders = this.allOrders.filter((o) => o.id !== orderId);

          // Fecha o modal
          this.closeOrderDetails();

          // Limpa o pedido selecionado
          this.selectedOrder = null;
        },
        (error) => {
          console.error('Erro ao deletar pedido:', error);
          alert('Não foi possível excluir o pedido. Tente novamente.');
        }
      );
    }
  }

  getPriorityColor(prioridade: string): string {
    switch (prioridade) {
      case 'VERMELHO': return 'red';
      case 'AMARELO': return 'yellow';
      case 'AZUL': return 'blue';
      case 'VERDE': return 'green';
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

  getStatusKeys(): number[] {
    return Object.keys(this.statusDescriptions).map(key => +key);
  }

  openAdverseOutputModal(): void {
    this.modalService.open(this.adverseOutputModal, { centered: true });
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
        this.orders.push(savedOrder);
        this.loadOrders();
        this.modalService.dismissAll();
      },
      (error) => {
        alert('Erro ao salvar a saída adversa: ' + error.message);
      }
    );
  }
}

