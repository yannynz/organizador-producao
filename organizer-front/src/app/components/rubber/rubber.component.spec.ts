import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RubberComponent } from './rubber.component';

describe('RubberComponent', () => {
  let component: RubberComponent;
  let fixture: ComponentFixture<RubberComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RubberComponent]
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
