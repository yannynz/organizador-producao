export interface Transportadora {
  id: number;
  nomeOficial: string;
  nomeNormalizado?: string;
  apelidos?: string;
  localizacao?: string;
  horarioFuncionamento?: string;
  ultimoServicoEm?: string;
  padraoEntrega?: string;
  observacoes?: string;
  ativo: boolean;
}
