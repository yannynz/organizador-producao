ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS emborrachada boolean NOT NULL DEFAULT false;

