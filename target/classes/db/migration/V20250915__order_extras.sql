-- Adiciona campos extras para materiais especiais no pedido
ALTER TABLE orders ADD COLUMN IF NOT EXISTS pertinax boolean DEFAULT false;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS poliester boolean DEFAULT false;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS papel_calibrado boolean DEFAULT false;
