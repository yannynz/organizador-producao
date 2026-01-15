import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { EMPTY } from 'rxjs';

import { PipelineBoardComponent } from './pipeline-board.component';
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

describe('PipelineBoardComponent', () => {
  let component: PipelineBoardComponent;
  let fixture: ComponentFixture<PipelineBoardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineBoardComponent, HttpClientTestingModule],
      providers: [{ provide: WebsocketService, useClass: MockWebsocketService }],
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(PipelineBoardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
