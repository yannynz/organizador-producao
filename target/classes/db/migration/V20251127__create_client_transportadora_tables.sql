CREATE TABLE clientes (
    id BIGSERIAL PRIMARY KEY,
    nome_oficial VARCHAR(180) NOT NULL,
    nome_normalizado VARCHAR(180) NOT NULL,
    apelidos TEXT, 
    padrao_entrega VARCHAR(20),
    observacoes TEXT,
    ativo BOOLEAN DEFAULT TRUE,
    transportadora_id BIGINT,
    ultimo_servico_em TIMESTAMPTZ,
    origin VARCHAR(20),
    manual_lock_mask SMALLINT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_clientes_nome_normalizado ON clientes(nome_normalizado);

CREATE TABLE transportadoras (
    id BIGSERIAL PRIMARY KEY,
    nome_oficial VARCHAR(180) NOT NULL,
    nome_normalizado VARCHAR(180) NOT NULL,
    apelidos TEXT,
    localizacao VARCHAR(255),
    horario_funcionamento VARCHAR(180),
    ultimo_servico_em TIMESTAMPTZ,
    padrao_entrega VARCHAR(20),
    observacoes TEXT,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_transportadoras_nome_normalizado ON transportadoras(nome_normalizado);

ALTER TABLE clientes ADD CONSTRAINT fk_clientes_transportadora FOREIGN KEY (transportadora_id) REFERENCES transportadoras(id);

CREATE TABLE cliente_enderecos (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES clientes(id),
    label VARCHAR(60),
    uf VARCHAR(2),
    cidade VARCHAR(120),
    bairro VARCHAR(120),
    logradouro VARCHAR(180),
    horario_funcionamento VARCHAR(180),
    padrao_entrega VARCHAR(20),
    is_default BOOLEAN DEFAULT FALSE,
    origin VARCHAR(20),
    confidence VARCHAR(10),
    manual_lock BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_cliente_enderecos_cliente_id ON cliente_enderecos(cliente_id);

-- Update orders table
ALTER TABLE orders ADD COLUMN cliente_id BIGINT REFERENCES clientes(id);
ALTER TABLE orders ADD COLUMN transportadora_id BIGINT REFERENCES transportadoras(id);
ALTER TABLE orders ADD COLUMN endereco_id BIGINT REFERENCES cliente_enderecos(id);
ALTER TABLE orders ADD COLUMN horario_func_aplicado VARCHAR(180);
ALTER TABLE orders ADD COLUMN fora_horario BOOLEAN;

-- Update op_import table
ALTER TABLE op_import ADD COLUMN cliente_id BIGINT;
ALTER TABLE op_import ADD COLUMN endereco_id BIGINT;
