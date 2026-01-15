import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { EMPTY } from 'rxjs';

import { DeliveredListComponent } from './delivered-list.component';
import { WebsocketService } from '../../../../services/websocket.service';

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

describe('DeliveredListComponent', () => {
  let component: DeliveredListComponent;
  let fixture: ComponentFixture<DeliveredListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeliveredListComponent, HttpClientTestingModule],
      providers: [{ provide: WebsocketService, useClass: MockWebsocketService }],
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DeliveredListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
