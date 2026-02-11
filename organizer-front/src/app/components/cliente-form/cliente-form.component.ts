import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Cliente } from '../../models/cliente.model';
import { ClienteService } from '../../services/cliente.service';
import { TransportadoraService } from '../../services/transportadora.service';
import { Transportadora } from '../../models/transportadora.model';

@Component({
  selector: 'app-cliente-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cliente-form.component.html',
  styleUrls: ['./cliente-form.component.css']
})
export class ClienteFormComponent implements OnInit {
  @Input() cliente: Cliente = {
    id: 0,
    nomeOficial: '',
    ativo: true,
    origin: 'MANUAL'
  };
  @Output() save = new EventEmitter<Cliente>();
  @Output() cancel = new EventEmitter<void>();

  transportadoras: Transportadora[] = [];
  aliasSearchQuery = '';
  aliasResults: Cliente[] = [];
  aliasLoading = false;
  aliasMessage = '';
  aliasError = '';

  constructor(
    private service: ClienteService,
    private transportadoraService: TransportadoraService
  ) {}

  ngOnInit() {
    this.transportadoraService.listAll().subscribe(list => {
      this.transportadoras = list;
    });
    if (!this.cliente.enderecos) {
      this.cliente.enderecos = [];
    }
    if (this.cliente.defaultEmborrachada == null) {
      this.cliente.defaultEmborrachada = false;
    }
    if (this.cliente.defaultPertinax == null) {
      this.cliente.defaultPertinax = false;
    }
    if (this.cliente.defaultPoliester == null) {
      this.cliente.defaultPoliester = false;
    }
    if (this.cliente.defaultPapelCalibrado == null) {
      this.cliente.defaultPapelCalibrado = false;
    }
    if (this.cliente.defaultDestacador == null) {
      this.cliente.defaultDestacador = '';
    }

    if (this.cliente.id) {
      this.service.listEnderecos(this.cliente.id).subscribe(list => {
        this.cliente.enderecos = list || [];
      });
    }
  }

  addEndereco() {
    this.cliente.enderecos?.push({
      isDefault: this.cliente.enderecos.length === 0, // First one is default
      origin: 'MANUAL'
    });
  }

  removeEndereco(index: number) {
    this.cliente.enderecos?.splice(index, 1);
  }

  setDefault(index: number) {
    this.cliente.enderecos?.forEach((e, i) => e.isDefault = (i === index));
  }

  submit() {
    // Bind relationship if ID is set (manual binding might be needed if backend expects object)
    // Assuming backend accepts ID or object. If ID, we might need to adjust model.
    // Cliente model has `transportadoraId`.
    if (this.cliente.id) {
      this.service.update(this.cliente.id, this.cliente).subscribe(c => this.save.emit(c));
    } else {
      this.service.create(this.cliente).subscribe(c => this.save.emit(c));
    }
  }

  searchAlias() {
    this.aliasMessage = '';
    this.aliasError = '';
    const query = (this.aliasSearchQuery || '').trim();
    if (!query) {
      this.aliasResults = [];
      return;
    }
    this.aliasLoading = true;
    this.service.search(query, 0, 10).subscribe({
      next: res => {
        const content = (res && res.content) ? res.content as Cliente[] : [];
        this.aliasResults = content.filter(c => c.id !== this.cliente.id);
        this.aliasLoading = false;
      },
      error: () => {
        this.aliasLoading = false;
        this.aliasError = 'Falha ao buscar clientes.';
      }
    });
  }

  linkAlias(target: Cliente) {
    this.aliasMessage = '';
    this.aliasError = '';
    if (!this.cliente.id || this.cliente.id === 0) {
      this.aliasError = 'Salve o cliente antes de vincular apelidos.';
      return;
    }
    if (!target || !target.id) {
      this.aliasError = 'Cliente inválido para vincular.';
      return;
    }
    if (target.id === this.cliente.id) {
      this.aliasError = 'Não é possível vincular o cliente a ele mesmo.';
      return;
    }
    this.aliasLoading = true;
    this.service.linkAlias(this.cliente.id, target.id).subscribe({
      next: () => {
        this.aliasLoading = false;
        this.mergeAlias(target.nomeOficial);
        this.aliasMessage = `Vínculo criado: ${this.cliente.nomeOficial} <-> ${target.nomeOficial}`;
      },
      error: (err) => {
        this.aliasLoading = false;
        this.aliasError = err?.error?.message || 'Falha ao vincular apelido.';
      }
    });
  }

  private mergeAlias(alias: string) {
    if (!alias) {
      return;
    }
    const list = this.parseApelidos(this.cliente.apelidos);
    const normalized = this.normalizeAlias(alias);
    const exists = list.some(a => this.normalizeAlias(a) === normalized);
    if (!exists) {
      list.push(alias.trim());
      this.setApelidos(list);
    }
  }

  private parseApelidos(value: string | string[] | undefined): string[] {
    if (!value) {
      return [];
    }
    if (Array.isArray(value)) {
      return value
        .map(v => (v || '').toString().trim())
        .filter(v => !!v);
    }
    return value
      .split(',')
      .map(v => v.trim())
      .filter(v => !!v);
  }

  private setApelidos(list: string[]) {
    this.cliente.apelidos = list.join(', ');
  }

  private normalizeAlias(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toUpperCase()
      .trim();
  }
}
