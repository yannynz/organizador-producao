import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { EMPTY, of, throwError } from 'rxjs';
import { FormBuilder } from '@angular/forms';

import { OrdersComponent } from './orders.component';
import { WebsocketService } from '../../services/websocket.service';
import { OrderService } from '../../services/orders.service';
import { AuthService } from '../../services/auth.service';
import { DxfAnalysisService } from '../../services/dxf-analysis.service';
import { orders } from '../../models/orders';

class MockWebsocketService {
  watchOrders() {
    return EMPTY;
  }

  watchPriorities() {
    return EMPTY;
  }

  watchDxfAnalysis() {
    return EMPTY;
  }
}

describe('OrdersComponent', () => {
  let component: OrdersComponent;
  let fixture: ComponentFixture<OrdersComponent>;
  let orderService: jasmine.SpyObj<OrderService>;

  beforeEach(async () => {
    orderService = jasmine.createSpyObj<OrderService>('OrderService', [
      'getOrders',
      'createOrder',
      'updateOrder',
      'deleteOrder',
    ]);
    orderService.getOrders.and.returnValue(of([
      { id: 1, nr: '120430', prioridade: 'VERDE', status: 0 } as orders,
      { id: 2, nr: '120431', prioridade: 'AZUL', status: 5 } as orders,
      { id: 3, nr: '120432', prioridade: 'AMARELO', status: 6 } as orders,
      { id: 4, nr: '120433', prioridade: 'VERMELHO', status: '1' as unknown as number } as orders,
      { id: 5, nr: '120434', prioridade: 'VERMELHO', status: 7 } as orders,
      { id: 6, nr: '120435', prioridade: 'VERMELHO', status: '8' as unknown as number } as orders,
    ]));

    await TestBed.configureTestingModule({
      imports: [OrdersComponent, RouterTestingModule],
      providers: [
        { provide: WebsocketService, useClass: MockWebsocketService },
        { provide: OrderService, useValue: orderService },
        { provide: AuthService, useValue: { user$: of(null) } },
        { provide: DxfAnalysisService, useValue: jasmine.createSpyObj<DxfAnalysisService>('DxfAnalysisService', ['getLatestByOrder']) },
      ],
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(OrdersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads only visible production orders', () => {
    expect(component.orders.map(order => order.nr)).toEqual(['120433', '120432', '120430']);
    expect(component.loadingOrders).toBeFalse();
    expect(component.ordersLoadError).toBeNull();
  });

  it('shows an error state when orders fail to load', () => {
    orderService.getOrders.and.returnValue(throwError(() => new Error('network')));

    component.loadOrders();
    fixture.detectChanges();

    expect(component.orders).toEqual([]);
    expect(component.loadingOrders).toBeFalse();
    expect(component.ordersLoadError).toBe('Não foi possível carregar pedidos.');
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar pedidos.');
  });

  it('keeps status 6 orders when updating the list', () => {
    component.orders = [];

    component.updateOrdersList({ id: 3, nr: '120432', prioridade: 'AMARELO', status: 6 } as orders);

    expect(component.orders.length).toBe(1);
    expect(component.orders[0].status).toBe(6);
  });

  it('shows only production, cut, and pulled orders', () => {
    expect(component.shouldDisplayOrder({ status: 0 } as orders)).toBeTrue();
    expect(component.shouldDisplayOrder({ status: '1' as unknown as number } as orders)).toBeTrue();
    expect(component.shouldDisplayOrder({ status: 6 } as orders)).toBeTrue();
    expect(component.shouldDisplayOrder({ status: 7 } as orders)).toBeFalse();
    expect(component.shouldDisplayOrder({ status: 8 } as orders)).toBeFalse();
    expect(component.shouldDisplayOrder({ status: 5 } as orders)).toBeFalse();
  });

  it('does not call the orders API while prerendering on the server', () => {
    orderService.getOrders.calls.reset();
    const serverComponent = new OrdersComponent(
      new FormBuilder(),
      orderService,
      new MockWebsocketService() as unknown as WebsocketService,
      jasmine.createSpyObj<DxfAnalysisService>('DxfAnalysisService', ['getLatestByOrder']),
      { user$: of(null) } as AuthService,
      'server',
    );

    serverComponent.ngOnInit();

    expect(orderService.getOrders).not.toHaveBeenCalled();
  });
});
