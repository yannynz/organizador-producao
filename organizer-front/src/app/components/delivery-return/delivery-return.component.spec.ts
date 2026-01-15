import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { EMPTY } from 'rxjs';

import { DeliveryReturnComponent } from './delivery-return.component';
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

describe('DeliveryReturnComponent', () => {
  let component: DeliveryReturnComponent;
  let fixture: ComponentFixture<DeliveryReturnComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeliveryReturnComponent, HttpClientTestingModule, RouterTestingModule],
      providers: [{ provide: WebsocketService, useClass: MockWebsocketService }],
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DeliveryReturnComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
