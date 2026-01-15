import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { EMPTY } from 'rxjs';

import { OrdersComponent } from './orders.component';
import { WebsocketService } from '../../services/websocket.service';

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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersComponent, HttpClientTestingModule, RouterTestingModule],
      providers: [{ provide: WebsocketService, useClass: MockWebsocketService }],
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(OrdersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
