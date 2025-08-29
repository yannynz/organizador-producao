import { TestBed } from '@angular/core/testing';

import { OpService } from './op.service';

describe('OpService', () => {
  let service: OpService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(OpService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
