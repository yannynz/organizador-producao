export enum UserRole {
  ADMIN = 'ADMIN',
  DESENHISTA = 'DESENHISTA',
  OPERADOR = 'OPERADOR',
  ENTREGADOR = 'ENTREGADOR'
}

export interface User {
  id: number;
  name: string;
  email: string;
  role: UserRole;
}

export interface AuthResponse {
  token: string;
}
