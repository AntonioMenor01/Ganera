-- Credenciales OVZ por ganadero (escalon 2, T1). Flag «la cuenta entra con certificado digital»: esas cuentas
-- NO se soportan en la presentacion automatica (D8), asi que el worker no las usara. Default FALSE para las
-- filas existentes. El usuario y la contrasena OVZ ya existian (ovz_usuario, ovz_password_cifrada).
ALTER TABLE ganadero ADD COLUMN usa_certificado BOOLEAN NOT NULL DEFAULT FALSE;
