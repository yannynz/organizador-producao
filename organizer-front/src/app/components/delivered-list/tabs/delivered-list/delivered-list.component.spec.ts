import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeliveredListComponent } from './delivered-list.component';

describe('DeliveredListComponent', () => {
  let component: DeliveredListComponent;
  let fixture: ComponentFixture<DeliveredListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeliveredListComponent]
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
