import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MontagemComponent } from './montagem.component';
import { OrderService } from '../../services/orders.service';
import { WebsocketService } from '../../services/websocket.service';
import { DxfAnalysisService } from '../../services/dxf-analysis.service';
import { AuthService } from '../../services/auth.service';
import { UserService } from '../../services/user.service';
import { OpService } from '../../services/op.service';
import { of } from 'rxjs';

describe('MontagemComponent', () => {
  let component: MontagemComponent;
  let fixture: ComponentFixture<MontagemComponent>;
  
  // Mocks
  const orderServiceMock = {
    getOrders: jasmine.createSpy('getOrders').and.returnValue(of([])),
    getOrderByNr: jasmine.createSpy('getOrderByNr').and.returnValue(of(null))
  };
  const wsMock = {
    watchOrders: jasmine.createSpy('watchOrders').and.returnValue(of({})),
    sendUpdateOrder: jasmine.createSpy('sendUpdateOrder')
  };
  const dxfServiceMock = {
    getLatestByOrder: jasmine.createSpy('getLatestByOrder').and.returnValue(of(null))
  };
  const authServiceMock = {
    user$: of({ name: 'Tester', role: 'ADMIN' })
  };
  const userServiceMock = {
    getAll: jasmine.createSpy('getAll').and.returnValue(of([]))
  };
  const opServiceMock = {
    getOpByNr: jasmine.createSpy('getOpByNr').and.returnValue(of(null))
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MontagemComponent],
      providers: [
        { provide: OrderService, useValue: orderServiceMock },
        { provide: WebsocketService, useValue: wsMock },
        { provide: DxfAnalysisService, useValue: dxfServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: UserService, useValue: userServiceMock },
        { provide: OpService, useValue: opServiceMock }
      ]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(MontagemComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load materials and metrics when verMateriais is called', () => {
    // Arrange
    const nr = '120488';
    const mockOp = {
      materiais: ['MISTA 2PT 5,0 X 5,0 X 23,3 C 23,80', 'PICOTE 2PT TRAV ONDU']
    };
    const mockDxf = {
      totalCutLengthMm: 1234.56,
      metrics: { areaBorrachaMm2: 500 }
    };

    opServiceMock.getOpByNr.and.returnValue(of(mockOp));
    dxfServiceMock.getLatestByOrder.and.returnValue(of(mockDxf));

    // Act
    component.verMateriais(nr);

    // Assert
    expect(component.showMateriaisModal).toBeTrue();
    expect(component.selectedNr).toBe(nr);
    expect(component.materiaisOp).toEqual(mockOp.materiais);
    
    // Check if metrics merged correctly
    expect(component.dxfMetrics['areaBorrachaMm2']).toBe(500);
    expect(component.dxfMetrics['Comprimento de Corte (mm)']).toBe(1234.56);
  });
});