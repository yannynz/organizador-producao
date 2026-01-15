import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { EMPTY, of } from 'rxjs';

import { OpComponent } from './op.component';
import { OpService } from '../../services/op.service';
import { WebsocketService } from '../../services/websocket.service';

class MockOpService {
  getOrders() {
    return of([]);
  }

  openOpPdf() {}
}

class MockWebsocketService {
  watchOrders() {
    return EMPTY;
  }
}

describe('OpComponent', () => {
  let component: OpComponent;
  let fixture: ComponentFixture<OpComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommonModule],
      declarations: [OpComponent],
      providers: [
        { provide: OpService, useClass: MockOpService },
        { provide: WebsocketService, useClass: MockWebsocketService },
      ],
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(OpComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
