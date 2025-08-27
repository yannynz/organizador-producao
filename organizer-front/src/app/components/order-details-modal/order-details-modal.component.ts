import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { OpService } from '../../services/op.service';
import { orders } from '../../models/orders';


@Component({
  selector: 'app-order-details-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './order-details-modal.component.html',
  styleUrls: ['./order-details-modal.component.css']
})
export class OrderDetailsModalComponent {
  @Input() statusDescriptions: { [key: number]: string } = {};
  @Input() selectedOrder: orders | null = null;
  @Input() open = false;

  @Output() close = new EventEmitter<void>();
  @Output() update = new EventEmitter<orders>();
  @Output() remove = new EventEmitter<number>();

  form: FormGroup;

  constructor(private fb: FormBuilder, private opService: OpService) {
    this.form = this.fb.group({
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
      montador: [''],
      dataMontagem: [''],
      entregador: ['']
    });
  }

  ngOnChanges() { if (this.selectedOrder) this.form.patchValue(this.selectedOrder); }
  onClose() { this.close.emit(); }
  onDelete() { if (this.selectedOrder?.id) this.remove.emit(this.selectedOrder.id); }
  onSave() { this.update.emit({ ...this.selectedOrder!, ...this.form.value }); }
  get opUrl(): string {
    const nr = this.form.get('nr')?.value as string | null;
    return nr ? this.opService.getOpUrl(nr) : '#';
  }
}

