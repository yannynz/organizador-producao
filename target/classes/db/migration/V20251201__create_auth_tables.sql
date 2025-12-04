-- Criar tabela de usuários
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Inserir Admin Inicial (Senha: admin123)
INSERT INTO users (name, email, password, role) 
VALUES ('Administrador', 'workyann@hotmail.com', '$2b$12$UO9hVInjJPnksYQShbC/UOp0.C6tPIGN2XNtDINB3YpA76/9L2/mG', 'ADMIN');

-- Adicionar colunas de rastreabilidade nas tabelas principais
ALTER TABLE orders ADD COLUMN created_by_user_id BIGINT REFERENCES users(id);
ALTER TABLE orders ADD COLUMN updated_by_user_id BIGINT REFERENCES users(id);
