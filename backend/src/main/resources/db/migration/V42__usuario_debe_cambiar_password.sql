-- Forzar el cambio de la contrasena temporal en el primer login (rama feat/cambio-password-forzado).
-- El alta automatica (feat/alta-automatica-emails) crea el Usuario con una contrasena temporal y le pide por
-- email que la cambie, pero no habia donde hacerlo. Este flag lo pone a true el alta automatica (y al regenerar
-- la contrasena temporal en el reintento de la bienvenida); el frontend obliga a cambiarla antes de usar la app
-- y POST /auth/cambiar-password lo pone a false.
--
-- DEFAULT FALSE a proposito: los usuarios que ya existen (creados por el onboarding manual con su propia
-- contrasena) NO quedan forzados a cambiarla. Columna vendor-neutral (db/migration): H2 y PostgreSQL.
ALTER TABLE usuario ADD COLUMN debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE;
