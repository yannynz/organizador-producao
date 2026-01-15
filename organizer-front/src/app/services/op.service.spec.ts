import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { OpService } from './op.service';

describe('OpService', () => {
  let service: OpService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(OpService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
