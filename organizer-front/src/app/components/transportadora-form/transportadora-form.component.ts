import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Transportadora } from '../../models/transportadora.model';
import { TransportadoraService } from '../../services/transportadora.service';

@Component({
  selector: 'app-transportadora-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './transportadora-form.component.html',
  styleUrls: ['./transportadora-form.component.css']
})
export class TransportadoraFormComponent {
  @Input() transportadora: Transportadora = {
    id: 0,
    nomeOficial: '',
    ativo: true
  };
  @Output() save = new EventEmitter<Transportadora>();
  @Output() cancel = new EventEmitter<void>();

  constructor(private service: TransportadoraService) {}

  submit() {
    if (this.transportadora.id) {
      this.service.update(this.transportadora.id, this.transportadora).subscribe(t => this.save.emit(t));
    } else {
      this.service.create(this.transportadora).subscribe(t => this.save.emit(t));
    }
  }
}
