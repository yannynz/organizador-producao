import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { NgbDatepickerModule, NgbDateStruct } from '@ng-bootstrap/ng-bootstrap';
import { OrderFilters } from '../../models/order-filters';

@Component({
  selector: 'app-advanced-filters',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, NgbDatepickerModule],
  templateUrl: './advanced-filters.component.html',
  styleUrls: ['./advanced-filters.component.css']
})
export class AdvancedFiltersComponent {
  @Input() statuses: { key: number; label: string }[] = [];
  @Input() compact = false;
  @Output() apply = new EventEmitter<{ search: string; filters: OrderFilters }>();
  @Output() clear = new EventEmitter<void>();

  open = false;

  form = this.fb.group({
    q: [''],
    id: [''],
    nr: [''],
    cliente: [''],
    prioridade: [''],
    status: [[] as number[]],
    entregador: [''],
    veiculo: [''],
    recebedor: [''],
    montador: [''],
    observacao: [''],
    isOpen: ['any'],
    dataHFrom: [null as NgbDateStruct | null],
    dataHTo: [null as NgbDateStruct | null],
    dataEntregaFrom: [null as NgbDateStruct | null],
    dataEntregaTo: [null as NgbDateStruct | null],
    dataHRetornoFrom: [null as NgbDateStruct | null],
    dataHRetornoTo: [null as NgbDateStruct | null],
    dataMontagemFrom: [null as NgbDateStruct | null],
    dataMontagemTo: [null as NgbDateStruct | null],
    sort: ['dataEntrega,desc,id,desc']
  });

  constructor(private fb: FormBuilder) { }

  private toIso(d: NgbDateStruct | null, end = false) {
    if (!d) return '';
    const dt = new Date(Date.UTC(d.year, d.month - 1, d.day, end ? 23 : 0, end ? 59 : 0, end ? 59 : 0, end ? 999 : 0));
    return dt.toISOString();
  }

  onApply() {
    const v = this.form.getRawValue(); // melhor que .value p/ pegar o valor “cru”
    const filters: OrderFilters = {};

    const set = <K extends keyof OrderFilters>(k: K, val: OrderFilters[K] | '' | null | undefined) => {
      if (val !== '' && val !== null && val !== undefined) (filters[k] as any) = val;
    };

    set('q', v.q?.trim());
    if (v.id) set('id', Number(v.id));
    set('nr', v.nr?.trim());
    set('cliente', v.cliente?.trim());
    set('prioridade', v.prioridade?.trim());

    if (Array.isArray(v.status) && v.status.length) {
      // garante números mesmo se o select entregar strings
      set('status', v.status.map((x: any) => Number(x)));
    }

    set('entregador', v.entregador?.trim());
    set('veiculo', v.veiculo?.trim());
    set('recebedor', v.recebedor?.trim());
    set('montador', v.montador?.trim());
    set('observacao', v.observacao?.trim());

    // boolean | null
    // boolean | null
    if (v.isOpen === 'any') set('isOpen', null);
    else set('isOpen', v.isOpen === 'true'); // 'true' -> true, 'false' -> false

    const dhf = this.toIso(v.dataHFrom); if (dhf) set('dataHFrom', dhf);
    const dht = this.toIso(v.dataHTo, true); if (dht) set('dataHTo', dht);

    const def = this.toIso(v.dataEntregaFrom); if (def) set('dataEntregaFrom', def);
    const det = this.toIso(v.dataEntregaTo, true); if (det) set('dataEntregaTo', det);

    const drf = this.toIso(v.dataHRetornoFrom); if (drf) set('dataHRetornoFrom', drf);
    const drt = this.toIso(v.dataHRetornoTo, true); if (drt) set('dataHRetornoTo', drt);

    const dmf = this.toIso(v.dataMontagemFrom); if (dmf) set('dataMontagemFrom', dmf);
    const dmt = this.toIso(v.dataMontagemTo, true); if (dmt) set('dataMontagemTo', dmt);

    set('sort', v.sort?.trim());

    this.apply.emit({ search: v.q?.trim() || '', filters });
  }

  onClear() {
    this.form.reset({
      q: '', id: '', nr: '', cliente: '', prioridade: '', status: [],
      entregador: '', veiculo: '', recebedor: '', montador: '', observacao: '',
      isOpen: 'any',
      dataHFrom: null, dataHTo: null,
      dataEntregaFrom: null, dataEntregaTo: null,
      dataHRetornoFrom: null, dataHRetornoTo: null,
      dataMontagemFrom: null, dataMontagemTo: null,
      sort: 'dataEntrega,desc,id,desc'
    });
    this.clear.emit();
  }
}

