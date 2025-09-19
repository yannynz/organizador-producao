export class orders {
  id: number = 0;
  nr: string = '';
  cliente: string = '';
  dataH: Date | undefined;
  prioridade: string = '';
  status: number = 0;
  entregador?: string;
  dataEntrega?: Date;
  observacao?: string = '';
  isOpen: boolean = false;
  dataHRetorno?: Date;
  veiculo?: string;
  recebedor?: string;
  montador?: string;
  dataMontagem?: Date;
  emborrachador?: string;
  dataEmborrachamento?: Date;
  emborrachada?: boolean = false;
  dataCortada?: Date;
  dataTirada?: Date;
  destacador?: string;              // "M", "F", "MF"
  modalidadeEntrega?: string;       // "RETIRADA" | "A ENTREGAR"
  dataRequeridaEntrega?: Date;
  usuario?: string;
  usuarioImportacao?: string;
  pertinax?: boolean;
  poliester?: boolean;
  papelCalibrado?: boolean;
  }
