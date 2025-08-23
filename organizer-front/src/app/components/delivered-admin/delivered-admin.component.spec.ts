import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeliveredAdminComponent } from './delivered-admin.component';

describe('DeliveredAdminComponent', () => {
  let component: DeliveredAdminComponent;
  let fixture: ComponentFixture<DeliveredAdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeliveredAdminComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DeliveredAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
