import {
  Component,
  EventEmitter,
  Input,
  Output,
  OnChanges,
  SimpleChanges,
  OnDestroy,
  Inject,
  PLATFORM_ID,
  HostListener,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { orders } from '../../models/orders';
import { OpService } from '../../services/op.service';
import { DateTime } from 'luxon';

@Component({
  selector: 'app-order-details-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './order-details-modal.component.html',
  styleUrls: ['./order-details-modal.component.css'],
})
export class OrderDetailsModalComponent implements OnChanges, OnDestroy {
  @ViewChild('pickerContainer') pickerContainer?: ElementRef<HTMLElement>;

  @Input() statusDescriptions: { [key: number]: string } = {};
  @Input() selectedOrder: orders | null = null;
  @Input() open = false;

  @Output() close = new EventEmitter<void>();
  @Output() update = new EventEmitter<orders>();
  @Output() remove = new EventEmitter<number>();

  form: FormGroup;

  /** === Picker === */
  pickerOpen = false;
  pickerMonth = DateTime.now().toFormat('yyyy-LL');
  selectedDay: number | null = null;
  selectedHour = 0;
  selectedMinute = 0;
  selectingMinutes = false;

  weekDays = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'];
  monthCells: Array<number | null> = [];
  outerHours = Array.from({ length: 12 }, (_, i) => i); // 0–11
  innerHours = Array.from({ length: 12 }, (_, i) => i + 12); // 12–23
  minuteSteps = Array.from({ length: 12 }, (_, i) => i * 5);

  private readonly saoPauloZone = 'America/Sao_Paulo';
  private readonly displayFormatLuxon = 'dd/MM/yyyy HH:mm';
  private readonly isoLocalFormat = "yyyy-LL-dd'T'HH:mm";
  private readonly isBrowser: boolean;
  private readonly initialFormValue: any;
  private readonly outerHourRadius = 90;
  private readonly innerHourRadius = 55;
  private readonly minuteRadius = 90;
  private ignoreDocumentClick = false;

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
      vincador: [''],
      dataVinco: [''],
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

    const now = DateTime.now().setZone(this.saoPauloZone);
    this.syncPickerStateFromDate(now);
  }

  /** Ciclo Angular */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedOrder']) this.applySelectedOrder(this.selectedOrder);
    if (changes['open']?.currentValue) this.applySelectedOrder(this.selectedOrder);
    if (changes['open'] && !changes['open'].currentValue && !this.open) {
      this.form.reset(this.initialFormValue);
    }
  }

  ngOnDestroy(): void {}

  /** === Ações === */
  onClose(): void {
    this.close.emit();
  }

  onDelete(): void {
    if (this.selectedOrder?.id) this.remove.emit(this.selectedOrder.id);
  }

  onSave(): void {
    if (!this.selectedOrder) return;
    const value: any = { ...this.form.value };

    if (typeof value.dataRequeridaEntrega === 'string' && value.dataRequeridaEntrega.trim() !== '') {
      const normalized = this.toIsoWithZone(value.dataRequeridaEntrega);
      if (normalized) value.dataRequeridaEntrega = normalized;
      else delete value.dataRequeridaEntrega;
    } else delete value.dataRequeridaEntrega;

    this.update.emit({ ...this.selectedOrder, ...value });
  }

  onOpenOp(): void {
    const nr = this.form.get('nr')?.value as string;
    if (nr) this.opService.openOpPdf(nr);
  }

  /** === DateTime Picker === */
  togglePicker(event?: MouseEvent): void {
    if (!this.isBrowser) return;

    event?.preventDefault();
    event?.stopPropagation();

    if (this.pickerOpen) {
      this.closePicker();
      return;
    }

    this.openPicker();
  }

  generateMonthDays(): void {
    const month = this.monthDate;
    const daysInMonth = month.daysInMonth ?? 30;
    const firstWeekday = month.startOf('month').weekday % 7;
    const leadingNulls = Array.from({ length: firstWeekday }, () => null);
    const dayNumbers = Array.from({ length: Number(daysInMonth) }, (_, i) => i + 1);
    const totalCells = leadingNulls.length + dayNumbers.length;
    const trailingNullsCount = totalCells % 7 === 0 ? 0 : 7 - (totalCells % 7);
    const trailingNulls = Array.from({ length: trailingNullsCount }, () => null);

    this.monthCells = [...leadingNulls, ...dayNumbers, ...trailingNulls];
  }

  get monthDate(): DateTime {
    return DateTime.fromFormat(this.pickerMonth, 'yyyy-LL');
  }

  selectDay(day: number): void {
    this.selectedDay = day;
  }

  selectHour(h: number): void {
    this.selectedHour = h;
    this.selectingMinutes = true;
  }

  selectMinute(m: number): void {
    this.selectedMinute = m;
  }

  getClockNumberStyle(value: number, type: 'outer' | 'inner' | 'minute'): Record<string, string> {
    const radius = type === 'minute' ? this.minuteRadius : type === 'inner' ? this.innerHourRadius : this.outerHourRadius;
    const baseAngle = type === 'minute'
      ? value * 6
      : type === 'inner'
        ? (value - 12) * 30
        : (value % 12) * 30;
    const angle = baseAngle - 90;

    return {
      transform: `rotate(${angle}deg) translate(${radius}px) rotate(${-angle}deg)`
    };
  }

  clockHandStyle(): Record<string, string> {
    const baseAngle = this.selectingMinutes ? this.selectedMinute * 6 : (this.selectedHour % 12) * 30;
    const angle = baseAngle - 90;
    const length = this.selectingMinutes
      ? this.minuteRadius
      : this.selectedHour >= 12
        ? this.innerHourRadius
        : this.outerHourRadius;

    return {
      '--hand-angle': `${angle}deg`,
      '--hand-length': `${length}px`
    } as Record<string, string>;
  }

  applyDateTime(): void {
    if (!this.selectedDay) return;
    const [y, m] = this.monthDate.toFormat('yyyy-LL').split('-').map(Number);
    const d = this.selectedDay;
    const hh = this.selectedHour;
    const mm = this.selectedMinute;
    const dt = DateTime.fromObject({ year: y, month: m, day: d, hour: hh, minute: mm }, { zone: this.saoPauloZone });
    const formatted = dt.toFormat(this.displayFormatLuxon);

    const control = this.form.get('dataRequeridaEntrega');
    control?.setValue(formatted);
    control?.markAsDirty();
    control?.markAsTouched();

    this.closePicker();
  }

  /** Fecha clicando fora */
  @HostListener('document:click', ['$event'])
  onClickOutside(event: MouseEvent): void {
    if (!this.pickerOpen || !this.isBrowser) {
      return;
    }

    if (this.ignoreDocumentClick) {
      this.ignoreDocumentClick = false;
      return;
    }

    const target = event.target as Node;
    if (this.pickerContainer?.nativeElement.contains(target)) {
      return;
    }

    this.closePicker();
  }

  /** Utilitários */
  private applySelectedOrder(order: orders | null): void {
    if (!order) {
      this.form.reset(this.initialFormValue);
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
  }

  closePicker(): void {
    this.pickerOpen = false;
    this.selectingMinutes = false;
    this.ignoreDocumentClick = false;
  }

  private openPicker(): void {
    this.hydratePickerFromControl();
    this.generateMonthDays();
    this.selectingMinutes = false;
    this.pickerOpen = true;
    this.ignoreDocumentClick = true;

    if (this.isBrowser) {
      setTimeout(() => {
        this.ignoreDocumentClick = false;
      });
    }
  }

  private hydratePickerFromControl(): void {
    const controlValue = this.form.get('dataRequeridaEntrega')?.value;
    const parsed = this.parseIncomingDate(controlValue);
    const base = parsed ?? DateTime.now().setZone(this.saoPauloZone);
    this.syncPickerStateFromDate(base);
  }

  private syncPickerStateFromDate(date: DateTime): void {
    this.pickerMonth = date.toFormat('yyyy-LL');
    this.selectedDay = date.day;
    this.selectedHour = date.hour;
    const roundedMinute = Math.floor(date.minute / 5) * 5;
    this.selectedMinute = roundedMinute;
  }

  private parseIncomingDate(value: unknown): DateTime | null {
    if (value instanceof Date) return DateTime.fromJSDate(value).setZone(this.saoPauloZone);
    if (typeof value === 'string') {
      const cleaned = value.trim().replace(/\[.*\]$/, '');
      const attempts = [
        DateTime.fromISO(cleaned, { zone: this.saoPauloZone }),
        DateTime.fromFormat(cleaned, this.isoLocalFormat, { zone: this.saoPauloZone }),
        DateTime.fromFormat(cleaned, this.displayFormatLuxon, { zone: this.saoPauloZone }),
        DateTime.fromSQL(cleaned, { zone: this.saoPauloZone }),
      ];
      for (const c of attempts) if (c.isValid) return c;
    }
    return null;
  }

  private toIsoWithZone(raw: string): string | null {
    const cleaned = raw.trim().replace(/\[.*\]$/, '');
    const attempts = [
      DateTime.fromISO(cleaned, { zone: this.saoPauloZone }),
      DateTime.fromFormat(cleaned, this.isoLocalFormat, { zone: this.saoPauloZone }),
      DateTime.fromFormat(cleaned, this.displayFormatLuxon, { zone: this.saoPauloZone }),
      DateTime.fromSQL(cleaned, { zone: this.saoPauloZone }),
    ];
    for (const c of attempts) {
      if (c.isValid)
        return c.setZone(this.saoPauloZone).toISO({ includeOffset: true, suppressMilliseconds: true, suppressSeconds: false }) ?? null;
    }
    return null;
  }
}
