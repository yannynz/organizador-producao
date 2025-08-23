export interface OrderFilters {
  id?: number;
  nr?: string;
  cliente?: string;
  prioridade?: string;
  status?: number[];
  entregador?: string;
  veiculo?: string;
  recebedor?: string;
  montador?: string;
  observacao?: string;
  isOpen?: boolean | null;

  dataHFrom?: string;         dataHTo?: string;
  dataEntregaFrom?: string;   dataEntregaTo?: string;
  dataHRetornoFrom?: string;  dataHRetornoTo?: string;
  dataMontagemFrom?: string;  dataMontagemTo?: string;

  q?: string;
  sort?: string;
}

