ALTER TABLE ganadero ADD COLUMN nif VARCHAR(20);
ALTER TABLE ganadero ADD CONSTRAINT uq_ganadero_nif UNIQUE (nif);
