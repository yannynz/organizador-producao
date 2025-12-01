export interface ClienteEndereco {
  id?: number;
  label?: string;
  uf?: string;
  cidade?: string;
  bairro?: string;
  logradouro?: string;
  cep?: string;
  horarioFuncionamento?: string;
  padraoEntrega?: string;
  isDefault: boolean;
  origin?: string;
  confidence?: string;
  manualLock?: boolean;
}

export interface Cliente {
  id: number;
  nomeOficial: string;
  nomeNormalizado?: string;
  apelidos?: string;
  padraoEntrega?: string;
  horarioFuncionamento?: string;
  cnpjCpf?: string;
  inscricaoEstadual?: string;
  telefone?: string;
  emailContato?: string;
  observacoes?: string;
  ativo: boolean;
  ultimoServicoEm?: string;
  origin?: string;
  manualLockMask?: number;
  transportadoraId?: number;
  transportadoraName?: string;
  enderecos?: ClienteEndereco[];
}
