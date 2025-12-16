import {
  Component,
  EventEmitter,
  Input,
  Output,
  OnChanges,
  OnInit,
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
import { OrderHistory } from '../../models/order-history.model';
import { OpService } from '../../services/op.service';
import { DxfAnalysis } from '../../models/dxf-analysis';
import { DxfAnalysisService } from '../../services/dxf-analysis.service';
import { OrderHistoryService } from '../../services/order-history.service';
import { WebsocketService } from '../../services/websocket.service';
import { FilesizePipe } from '../../pipes/filesize.pipe';
import { environment } from '../../enviroment';
import { Subscription, filter } from 'rxjs';
import { DateTime } from 'luxon';

@Component({
  selector: 'app-order-details-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, FilesizePipe],
  templateUrl: './order-details-modal.component.html',
  styleUrls: ['./order-details-modal.component.css'],
})
export class OrderDetailsModalComponent implements OnInit, OnChanges, OnDestroy {
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
  private readonly imagePublicBaseUrl: string;
  private readonly apiBaseUrl: string;
  private readonly defaultProtocol: string;
  private ignoreDocumentClick = false;
  private dxfLatestSub?: Subscription;
  private dxfHistorySub?: Subscription;
  private dxfWebsocketSub?: Subscription;
  private orderHistorySub?: Subscription;

  dxfAnalysis: DxfAnalysis | null = null;
  dxfHistory: DxfAnalysis[] = [];
  dxfLoading = false;
  dxfError: string | null = null;

  orderHistory: OrderHistory[] = [];

  constructor(
    private fb: FormBuilder,
    private opService: OpService,
    private dxfAnalysisService: DxfAnalysisService,
    private orderHistoryService: OrderHistoryService,
    private websocketService: WebsocketService,
    @Inject(PLATFORM_ID) platformId: object
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
    this.imagePublicBaseUrl = this.normalizeBaseUrl(environment.imagePublicBaseUrl);
    this.apiBaseUrl = this.normalizeBaseUrl(environment.apiBaseUrl);
    this.defaultProtocol =
      this.isBrowser && typeof window !== 'undefined' && window.location?.protocol
        ? window.location.protocol
        : 'http:';
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
  ngOnInit(): void {
    this.ensureWebsocketSubscription();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedOrder']) {
      this.applySelectedOrder(this.selectedOrder);
      if (this.open) {
        this.refreshDxfAnalysis();
        this.refreshOrderHistory();
      }
    }
    if (changes['open']?.currentValue) {
      this.applySelectedOrder(this.selectedOrder);
      this.refreshDxfAnalysis();
      this.refreshOrderHistory();
    }
    if (changes['open'] && !changes['open'].currentValue && !this.open) {
      this.form.reset(this.initialFormValue);
      this.resetDxfState();
      this.orderHistory = [];
      this.orderHistorySub?.unsubscribe();
    }
  }

  ngOnDestroy(): void {
    this.dxfLatestSub?.unsubscribe();
    this.dxfHistorySub?.unsubscribe();
    this.dxfWebsocketSub?.unsubscribe();
    this.orderHistorySub?.unsubscribe();
  }

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

  refreshDxfAnalysis(): void {
    if (!this.open) {
      return;
    }

    const nr = this.selectedOrder?.nr;
    if (!nr) {
      this.resetDxfState();
      return;
    }

    this.dxfLoading = true;
    this.dxfError = null;

    this.dxfLatestSub?.unsubscribe();
    this.dxfLatestSub = this.dxfAnalysisService.getLatestByOrder(nr).subscribe({
      next: (analysis) => {
        this.dxfAnalysis = analysis;
        this.dxfLoading = false;
        if (analysis) {
          this.mergeHistoryWithLatest(analysis);
        } else {
          this.dxfHistory = [];
        }
      },
      error: (err) => {
        this.dxfLoading = false;
        if (err.status === 403) {
          this.dxfError = 'Você não tem permissão para visualizar a análise DXF.';
        } else {
          this.dxfError = 'Falha ao carregar análise DXF.';
        }
      },
    });

    this.loadDxfHistory(nr);
    this.ensureWebsocketSubscription();
  }

  starFillPercent(index: number, analysis: DxfAnalysis | null = this.dxfAnalysis): string {
    const value = this.ratingValue(analysis);
    const diff = value - index;
    let percent = 0;
    if (diff >= 1) {
      percent = 100;
    } else if (diff > 0) {
      percent = Math.round(diff * 100);
    }
    return `${percent}%`;
  }

  hasUploadWarning(analysis: DxfAnalysis | null = this.dxfAnalysis): boolean {
    if (!analysis?.imageUploadStatus) {
      return false;
    }
    const status = analysis.imageUploadStatus.toLowerCase();
    return status !== 'uploaded' && status !== 'exists';
  }

  dxfImageSource(analysis: DxfAnalysis | null = this.dxfAnalysis): string | null {
    if (!analysis) {
      return null;
    }
    return this.resolvePublicImageUrl(analysis);
  }

  dxfImageLink(analysis: DxfAnalysis | null = this.dxfAnalysis): string | null {
    return this.dxfImageSource(analysis);
  }

  formatAnalyzedAt(analysis: DxfAnalysis | null = this.dxfAnalysis): string | null {
    if (!analysis?.analyzedAt) {
      return null;
    }
    const parsed = DateTime.fromISO(analysis.analyzedAt);
    if (!parsed.isValid) {
      return analysis.analyzedAt;
    }
    return parsed.setZone(this.saoPauloZone).toFormat(this.displayFormatLuxon);
  }

  formatUploadedAt(analysis: DxfAnalysis | null = this.dxfAnalysis): string | null {
    if (!analysis?.imageUploadedAt) {
      return null;
    }
    const parsed = DateTime.fromISO(analysis.imageUploadedAt);
    if (!parsed.isValid) {
      return analysis.imageUploadedAt;
    }
    return parsed.setZone(this.saoPauloZone).toFormat(this.displayFormatLuxon);
  }

  dxfHistoryWithoutCurrent(): DxfAnalysis[] {
    if (!this.dxfAnalysis) {
      return this.dxfHistory;
    }
    return this.dxfHistory.filter((item) => item.analysisId !== this.dxfAnalysis?.analysisId);
  }

  formatIso(value?: string | null): string {
    if (!value) {
      return '-';
    }
    const parsed = DateTime.fromISO(value);
    if (!parsed.isValid) {
      return value;
    }
    return parsed.setZone(this.saoPauloZone).toFormat(this.displayFormatLuxon);
  }

  readonly dxfStarIndices = [0, 1, 2, 3, 4];

  ratingValue(analysis: DxfAnalysis | null = this.dxfAnalysis): number {
    if (!analysis) {
      return 0;
    }
    const base = analysis.scoreStars ?? analysis.score ?? 0;
    return Math.max(0, Math.min(5, base));
  }

  displayScore(analysis: DxfAnalysis | null = this.dxfAnalysis): number | null {
    if (!analysis) {
      return null;
    }
    const value = analysis.scoreStars ?? analysis.score;
    return value !== null && value !== undefined ? this.roundHalf(value) : null;
  }

  private roundHalf(value: number): number {
    return Math.round(value * 2) / 2;
  }

  private loadDxfHistory(orderNr: string): void {
    this.dxfHistorySub?.unsubscribe();
    this.dxfHistorySub = this.dxfAnalysisService.listHistory(orderNr, 5).subscribe({
      next: (history) => {
        this.dxfHistory = history ?? [];
        if (this.dxfAnalysis) {
          this.mergeHistoryWithLatest(this.dxfAnalysis);
        }
      },
      error: () => {
        this.dxfHistory = this.dxfAnalysis ? [this.dxfAnalysis] : [];
      },
    });
  }

  private mergeHistoryWithLatest(latest: DxfAnalysis): void {
    const filtered = this.dxfHistory.filter((item) => item.analysisId !== latest.analysisId);
    this.dxfHistory = [latest, ...filtered].slice(0, 5);
  }

  private ensureWebsocketSubscription(): void {
    if (this.dxfWebsocketSub || !this.isBrowser) {
      return;
    }
    this.dxfWebsocketSub = this.websocketService
      .watchDxfAnalysis()
      .pipe(filter((payload): payload is DxfAnalysis => !!payload))
      .subscribe((payload) => this.handleIncomingDxfAnalysis(payload));
  }

  private handleIncomingDxfAnalysis(payload: DxfAnalysis): void {
    if (!payload?.analysisId) {
      return;
    }

    const currentNr = this.selectedOrder?.nr;
    if (currentNr && payload.orderNr === currentNr) {
      this.dxfAnalysis = payload;
      this.dxfLoading = false;
      this.dxfError = null;
      this.mergeHistoryWithLatest(payload);
    }
  }

  private resolvePublicImageUrl(analysis: DxfAnalysis): string | null {
    const directCandidates = [analysis.imageUri, analysis.imageUrl].map((value) => this.pickHttpUrl(value));
    for (const candidate of directCandidates) {
      if (candidate) {
        return candidate;
      }
    }

    const baseBuilt = this.buildFromBase(analysis.imageKey);
    if (baseBuilt) {
      return baseBuilt;
    }
    return null;
  }

  private pickHttpUrl(value: string | null | undefined): string | null {
    if (!value) {
      return null;
    }
    const trimmed = value.trim();
    if (!trimmed) {
      return null;
    }
    if (trimmed.startsWith('data:')) {
      return trimmed;
    }
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      return trimmed;
    }
    if (trimmed.startsWith('//')) {
      return `${this.defaultProtocol}${trimmed}`;
    }
    return null;
  }

  private buildFromBase(key: string | null | undefined): string | null {
    if (!this.imagePublicBaseUrl || !key) {
      return null;
    }
    const trimmedKey = key.trim();
    if (!trimmedKey) {
      return null;
    }
    const normalizedKey = trimmedKey.startsWith('/') ? trimmedKey.substring(1) : trimmedKey;
    return `${this.imagePublicBaseUrl}/${normalizedKey}`;
  }

  private normalizeBaseUrl(value: string | undefined | null): string {
    if (!value) {
      return '';
    }
    const trimmed = value.trim();
    if (!trimmed) {
      return '';
    }
    return trimmed.endsWith('/') ? trimmed.slice(0, -1) : trimmed;
  }

  private resetDxfState(): void {
    this.dxfAnalysis = null;
    this.dxfHistory = [];
    this.dxfLoading = false;
    this.dxfError = null;
  }

  private refreshOrderHistory(): void {
    if (!this.open) {
      return;
    }

    const orderId = this.selectedOrder?.id;
    if (!orderId) {
      this.orderHistory = [];
      return;
    }

    this.orderHistorySub?.unsubscribe();
    this.orderHistorySub = this.orderHistoryService.getHistory(orderId).subscribe({
      next: (history) => {
        this.orderHistory = history;
      },
      error: (err) => {
        console.error('Failed to load order history', err);
        this.orderHistory = [];
      },
    });
  }

  formatHistoryTimestamp(timestamp: string): string {
    const parsed = DateTime.fromISO(timestamp);
    if (!parsed.isValid) {
      return timestamp;
    }
    return parsed.setZone(this.saoPauloZone).toFormat(this.displayFormatLuxon);
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
