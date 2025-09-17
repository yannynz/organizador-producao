import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges, OnDestroy, ViewChild, ElementRef, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { orders } from '../../models/orders';
import { OpService } from '../../services/op.service';
import { DateTime } from 'luxon';
import $ from 'jquery';
import moment from 'moment';
import 'moment/locale/pt-br';

@Component({
  selector: 'app-order-details-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './order-details-modal.component.html',
  styleUrls: ['./order-details-modal.component.css']
})
export class OrderDetailsModalComponent implements OnChanges, OnDestroy {
  @Input() statusDescriptions: { [key: number]: string } = {};
  @Input() selectedOrder: orders | null = null;
  @Input() open = false;

  @Output() close = new EventEmitter<void>();
  @Output() update = new EventEmitter<orders>();
  @Output() remove = new EventEmitter<number>();

  @ViewChild('dateTimeInput')
  set dateTimeInput(ref: ElementRef<HTMLInputElement> | undefined) {
    if (!this.isBrowser) {
      this.datePickerElement = undefined;
      return;
    }

    if (!ref) {
      this.teardownDatePicker();
      this.datePickerElement = undefined;
      return;
    }

    this.datePickerElement = $(ref.nativeElement) as JQuery<HTMLInputElement>;
    this.initializeDatePicker();
  }

  form: FormGroup;

  private readonly saoPauloZone = 'America/Sao_Paulo';
  private readonly displayFormatLuxon = 'dd/MM/yyyy HH:mm';
  private readonly displayFormatMoment = 'DD/MM/YYYY HH:mm';
  private readonly isoLocalFormat = "yyyy-LL-dd'T'HH:mm";
  private readonly isBrowser: boolean;
  private readonly initialFormValue: any;

  private datePickerElement?: JQuery<HTMLInputElement>;
  private pickerInitialized = false;
  private suppressPickerEvent = false;

  private readonly pickerChangeHandler = (_: JQuery.TriggeredEvent, date: moment.Moment | null) => {
    if (this.suppressPickerEvent) {
      return;
    }

    const control = this.form.get('dataRequeridaEntrega');
    if (!control) {
      return;
    }

    if (!date) {
      control.setValue('', { emitEvent: false });
      control.markAsDirty();
      control.markAsTouched();
      return;
    }

    const normalized = DateTime.fromJSDate(date.toDate()).setZone(this.saoPauloZone, { keepLocalTime: true });

    const displayValue = normalized.toFormat(this.displayFormatLuxon);
    control.setValue(displayValue, { emitEvent: false });
    control.markAsDirty();
    control.markAsTouched();
  };

  constructor(
    private fb: FormBuilder,
    private opService: OpService,
    @Inject(PLATFORM_ID) platformId: object
  ) {
    this.isBrowser = isPlatformBrowser(platformId);

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
      entregador: [''],
      emborrachador: [''],
      dataEmborrachamento: [''],
      dataCortada: [''],
      dataTirada: [''],
      emborrachada: [false],
      destacador: [''],
      modalidadeEntrega: [''],
      dataRequeridaEntrega: [''],
      usuarioImportacao: [''],
      pertinax: [false],
      poliester: [false],
      papelCalibrado: [false],
    });

    this.initialFormValue = this.form.getRawValue();
    moment.locale('pt-br');
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedOrder']) {
      this.applySelectedOrder(this.selectedOrder);
    }

    if (changes['open']?.currentValue) {
      this.applySelectedOrder(this.selectedOrder);
      if (this.pickerInitialized) {
        setTimeout(() => this.syncPickerWithForm(), 0);
      }
    }

    if (changes['open'] && !changes['open'].currentValue && !this.open) {
      this.form.reset(this.initialFormValue);
      this.syncPickerWithForm();
    }
  }

  ngOnDestroy(): void {
    this.teardownDatePicker();
  }

  onClose(): void {
    this.close.emit();
  }

  onDelete(): void {
    if (this.selectedOrder?.id) {
      this.remove.emit(this.selectedOrder.id);
    }
  }

  onSave(): void {
    if (!this.selectedOrder) {
      return;
    }

    const value: any = { ...this.form.value };

    if (typeof value.dataRequeridaEntrega === 'string' && value.dataRequeridaEntrega.trim() !== '') {
      const normalized = this.toIsoWithZone(value.dataRequeridaEntrega);
      if (normalized) {
        value.dataRequeridaEntrega = normalized;
      } else {
        delete value.dataRequeridaEntrega;
      }
    } else {
      delete value.dataRequeridaEntrega;
    }

    this.update.emit({ ...this.selectedOrder, ...value });
  }

  onOpenOp(): void {
    const nr = this.form.get('nr')?.value as string;
    if (nr) {
      this.opService.openOpPdf(nr);
    }
  }

  private applySelectedOrder(order: orders | null): void {
    if (!order) {
      this.form.reset(this.initialFormValue);
      this.syncPickerWithForm();
      return;
    }

    const patch: any = {
      ...this.initialFormValue,
      ...order,
      emborrachada: !!order.emborrachada,
      pertinax: !!order.pertinax,
      poliester: !!order.poliester,
      papelCalibrado: !!order.papelCalibrado,
      destacador: order.destacador ?? '',
      modalidadeEntrega: order.modalidadeEntrega ?? '',
      usuarioImportacao: order.usuarioImportacao ?? '',
    };

    const parsed = this.parseIncomingDate(order.dataRequeridaEntrega);
    patch.dataRequeridaEntrega = parsed ? parsed.toFormat(this.displayFormatLuxon) : '';

    this.form.reset(this.initialFormValue);
    this.form.patchValue(patch, { emitEvent: false });
    this.syncPickerWithForm();
  }

  private initializeDatePicker(): void {
    if (!this.datePickerElement) {
      return;
    }

    const picker: any = this.datePickerElement;
    if (typeof picker.bootstrapMaterialDatePicker !== 'function') {
      return;
    }

    picker.bootstrapMaterialDatePicker({
      format: this.displayFormatMoment,
      lang: 'pt-br',
      cancelText: 'Cancelar',
      okText: 'OK',
      clearButton: true,
      nowButton: true,
      nowText: 'Agora',
      time: true,
      shortTime: false,
      switchOnClick: true,
      weekStart: 0,
    });

    this.datePickerElement.on('change', this.pickerChangeHandler);
    this.pickerInitialized = true;
    this.syncPickerWithForm();
  }

  private teardownDatePicker(): void {
    if (!this.datePickerElement) {
      this.pickerInitialized = false;
      return;
    }

    this.datePickerElement.off('change', this.pickerChangeHandler);

    const picker: any = this.datePickerElement;
    if (typeof picker.bootstrapMaterialDatePicker === 'function') {
      picker.bootstrapMaterialDatePicker('destroy');
    }

    this.pickerInitialized = false;
  }

  private syncPickerWithForm(): void {
    if (!this.pickerInitialized || !this.datePickerElement) {
      return;
    }

    const picker: any = this.datePickerElement;
    if (typeof picker.bootstrapMaterialDatePicker !== 'function') {
      return;
    }
    const raw = this.form.get('dataRequeridaEntrega')?.value;
    const parsed = this.parseIncomingDate(raw);

    this.suppressPickerEvent = true;

    if (parsed) {
      picker.bootstrapMaterialDatePicker('setDate', moment(parsed.toJSDate()));
    } else {
      picker.bootstrapMaterialDatePicker('clear');
    }

    setTimeout(() => {
      this.suppressPickerEvent = false;
    });
  }

  private parseIncomingDate(value: unknown): DateTime | null {
    if (value instanceof Date) {
      return DateTime.fromJSDate(value).setZone(this.saoPauloZone);
    }

    if (typeof value === 'string') {
      const raw = value.trim();
      if (!raw) return null;

      const cleaned = raw.replace(/\[.*\]$/, '');
      const attempts = [
        DateTime.fromISO(cleaned, { setZone: true }),
        DateTime.fromISO(cleaned, { zone: this.saoPauloZone }),
        DateTime.fromFormat(cleaned, this.isoLocalFormat, { zone: this.saoPauloZone }),
        DateTime.fromFormat(cleaned, this.displayFormatLuxon, { zone: this.saoPauloZone }),
        DateTime.fromSQL(cleaned, { zone: this.saoPauloZone }),
      ];

      for (const candidate of attempts) {
        if (candidate.isValid) return candidate.setZone(this.saoPauloZone);
      }
    }

    return null;
  }

  private toIsoWithZone(raw: string): string | null {
    const trimmed = raw.trim();
    if (!trimmed) return null;

    const cleaned = trimmed.replace(/\[.*\]$/, '');
    const attempts = [
      DateTime.fromISO(cleaned, { zone: this.saoPauloZone }),
      DateTime.fromFormat(cleaned, this.isoLocalFormat, { zone: this.saoPauloZone }),
      DateTime.fromFormat(cleaned, this.displayFormatLuxon, { zone: this.saoPauloZone }),
      DateTime.fromSQL(cleaned, { zone: this.saoPauloZone }),
    ];

    for (const candidate of attempts) {
      if (candidate.isValid) {
        return candidate
          .setZone(this.saoPauloZone)
          .toISO({ includeOffset: true, suppressMilliseconds: true, suppressSeconds: false }) ?? null;
      }
    }

    return null;
  }
}
