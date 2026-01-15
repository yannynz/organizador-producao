import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Subject, EMPTY, of } from 'rxjs';

import { OrderDetailsModalComponent } from './order-details-modal.component';
import { DxfAnalysisService } from '../../services/dxf-analysis.service';
import { WebsocketService } from '../../services/websocket.service';
import { orders } from '../../models/orders';
import { DxfAnalysis } from '../../models/dxf-analysis';
import { OpService } from '../../services/op.service';
import { environment } from '../../enviroment';

class MockOpService {
  openOpPdf(): void {}
}

class MockDxfAnalysisService {
  latest$ = of<DxfAnalysis | null>(null);
  history$ = of<DxfAnalysis[]>([]);
  requestedOrder: string | null = null;
  historyOrder: string | null = null;
  historyLimit: number | null = null;

  getLatestByOrder(orderNr: string) {
    this.requestedOrder = orderNr;
    return this.latest$;
  }

  listHistory(orderNr: string, limit: number) {
    this.historyOrder = orderNr;
    this.historyLimit = limit;
    return this.history$;
  }
}

class MockWebsocketService {
  watchDxfAnalysis() {
    return EMPTY;
  }
}

describe('OrderDetailsModalComponent', () => {
  let component: OrderDetailsModalComponent;
  let fixture: ComponentFixture<OrderDetailsModalComponent>;
  let mockService: MockDxfAnalysisService;

  beforeEach(async () => {
    environment.imagePublicBaseUrl = '';
    mockService = new MockDxfAnalysisService();

    await TestBed.configureTestingModule({
      imports: [OrderDetailsModalComponent, HttpClientTestingModule],
      providers: [
        { provide: DxfAnalysisService, useValue: mockService },
        { provide: WebsocketService, useClass: MockWebsocketService },
        { provide: OpService, useClass: MockOpService },
      ],
    }).compileComponents();
  });

  function makeOrder(nr: string): orders {
    const order = new orders();
    order.id = 1;
    order.nr = nr;
    order.cliente = 'Cliente Teste';
    order.prioridade = 'VERMELHO';
    order.status = 0;
    return order;
  }

  function makeAnalysis(overrides: Partial<DxfAnalysis> = {}): DxfAnalysis {
    return {
      analysisId: 'analysis-1',
      orderNr: '123',
      orderId: 1,
      score: 4.2,
      scoreLabel: 'ALTO',
      scoreStars: 4.0,
      totalCutLengthMm: 1200,
      curveCount: 10,
      intersectionCount: 2,
      minRadiusMm: 0.2,
      cacheHit: false,
      analyzedAt: '2025-10-14T10:00:00Z',
      fileName: 'NR123.DXF',
      fileHash: 'sha256:test',
      imagePath: 'render/path.png',
      imageUrl: 'http://cdn/path.png',
      imageBucket: 'facas',
      imageKey: 'renders/path.png',
      imageUri: 'http://cdn/path.png',
      imageChecksum: 'sha256:test',
      imageSizeBytes: 2048,
      imageContentType: 'image/png',
      imageUploadStatus: 'uploaded',
      imageUploadMessage: 'ok',
      imageUploadedAt: '2025-10-14T10:00:10Z',
      imageEtag: 'etag',
      imageWidth: 800,
      imageHeight: 600,
      metrics: {},
      explanations: ['cut_length'],
      ...overrides,
    };
  }

  function initComponent(): void {
    fixture = TestBed.createComponent(OrderDetailsModalComponent);
    component = fixture.componentInstance;
  }

  function openWithOrder(nr: string): void {
    fixture.componentRef.setInput('selectedOrder', makeOrder(nr));
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    fixture.detectChanges();
  }

  it('should create', () => {
    initComponent();
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('shows loading state while fetching DXF analysis', () => {
    const latestSubject = new Subject<DxfAnalysis | null>();
    mockService.latest$ = latestSubject.asObservable();
    mockService.history$ = of([]);

    initComponent();
    openWithOrder('123');

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Carregando análise DXF');

    latestSubject.next(null);
    latestSubject.complete();
    fixture.detectChanges();

    const updatedText = fixture.nativeElement.textContent;
    expect(updatedText).toContain('Sem análise DXF disponível.');
  });

  it('renders analysis details when available', () => {
    const analysis = makeAnalysis();
    mockService.latest$ = of(analysis);
    mockService.history$ = of([analysis]);

    initComponent();
    openWithOrder('123');

    const wrapper: HTMLElement = fixture.nativeElement;
    expect(wrapper.querySelector('.dxf-analysis-card')).not.toBeNull();
    expect(wrapper.textContent).toContain('Imagem da faca');
    const img: HTMLImageElement | null = wrapper.querySelector('.dxf-last-session img');
    expect(img?.src).toContain('http://cdn/path.png');
  });

  it('renders image from bucket/key when storage URI is missing', () => {
    environment.imagePublicBaseUrl = 'http://localhost:9000/facas-renders';
    const analysis = makeAnalysis({
      imageUri: null,
      imageUrl: null,
      imageBucket: 'facas-renders',
      imageKey: 'renders/sample.png',
    });
    mockService.latest$ = of(analysis);
    mockService.history$ = of([analysis]);

    initComponent();
    openWithOrder('123');

    const img: HTMLImageElement | null = fixture.nativeElement.querySelector('.dxf-last-session img');
    expect(img?.src).toContain('http://localhost:9000/facas-renders/renders/sample.png');
  });

  it('mostra fallback quando apenas caminho local está disponível', () => {
    const analysis = makeAnalysis({
      imageUri: null,
      imageUrl: null,
      imageBucket: null,
      imageKey: null,
      imagePath: 'legacy.png',
    });
    mockService.latest$ = of(analysis);
    mockService.history$ = of([analysis]);

    initComponent();
    openWithOrder('123');

    const img: HTMLImageElement | null = fixture.nativeElement.querySelector('.dxf-last-session img');
    expect(img).toBeNull();
    const fallback: HTMLElement | null = fixture.nativeElement.querySelector('.dxf-last-session .alert');
    expect(fallback?.textContent).toContain('Imagem não disponível no bucket configurado.');
  });

  it('shows upload warning when status is failure', () => {
    const analysis = makeAnalysis({
      imageUploadStatus: 'failed',
      imageUploadMessage: 'erro',
    });
    mockService.latest$ = of(analysis);
    mockService.history$ = of([analysis]);

    initComponent();
    openWithOrder('123');

    const warning: HTMLElement | null = fixture.nativeElement.querySelector('.alert-warning');
    expect(warning?.textContent).toContain('Upload pendente');
  });
});
