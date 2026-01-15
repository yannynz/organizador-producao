import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { EMPTY } from 'rxjs';

import { RubberComponent } from './rubber.component';
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

describe('RubberComponent', () => {
  let component: RubberComponent;
  let fixture: ComponentFixture<RubberComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RubberComponent, HttpClientTestingModule, RouterTestingModule],
      providers: [{ provide: WebsocketService, useClass: MockWebsocketService }],
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(RubberComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
