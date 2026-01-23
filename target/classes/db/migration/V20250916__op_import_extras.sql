-- Garanta a existência da tabela base (para primeiro boot)
CREATE TABLE IF NOT EXISTS op_import (
  id                       BIGSERIAL PRIMARY KEY,
  numero_op                VARCHAR(255) NOT NULL UNIQUE,
  codigo_produto           VARCHAR(255),
  descricao_produto        VARCHAR(255),
  cliente                  VARCHAR(255),
  data_op                  TIMESTAMPTZ,
  emborrachada             BOOLEAN NOT NULL DEFAULT false,
  share_path               VARCHAR(1024),
  materiais                JSONB,
  destacador               VARCHAR(10),
  modalidade_entrega       VARCHAR(50),
  faca_id                  BIGINT,
  created_at               TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  data_requerida_entrega   TIMESTAMPTZ,
  usuario_importacao       VARCHAR(255)
);

-- Campos extras na OP importada: materiais especiais
ALTER TABLE op_import ADD COLUMN IF NOT EXISTS pertinax boolean;
ALTER TABLE op_import ADD COLUMN IF NOT EXISTS poliester boolean;
ALTER TABLE op_import ADD COLUMN IF NOT EXISTS papel_calibrado boolean;
