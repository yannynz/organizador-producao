-- Defaults para pedidos: modalidade_entrega e booleans
ALTER TABLE orders ALTER COLUMN modalidade_entrega SET DEFAULT 'A ENTREGAR';
UPDATE orders SET modalidade_entrega = 'A ENTREGAR' WHERE modalidade_entrega IS NULL OR modalidade_entrega = '';

ALTER TABLE orders ALTER COLUMN pertinax SET DEFAULT false;
ALTER TABLE orders ALTER COLUMN poliester SET DEFAULT false;
ALTER TABLE orders ALTER COLUMN papel_calibrado SET DEFAULT false;
UPDATE orders SET pertinax = false WHERE pertinax IS NULL;
UPDATE orders SET poliester = false WHERE poliester IS NULL;
UPDATE orders SET papel_calibrado = false WHERE papel_calibrado IS NULL;
