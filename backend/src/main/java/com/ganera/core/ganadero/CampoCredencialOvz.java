package com.ganera.core.ganadero;

/**
 * Campo de las credenciales OVZ de un Ganadero que puede cambiar al guardarlas (clave {@code CAMPOS_CAMBIADOS} de
 * las filas {@code CREDENCIAL_OVZ_GUARDADA}/{@code CREDENCIAL_OVZ_MODIFICADA}, escalon 2, T1). Se guarda el CODIGO,
 * nunca el valor: ni el usuario ni la contrasena llegan jamas a la auditoria.
 */
public enum CampoCredencialOvz {
    USUARIO,
    PASSWORD,
    USA_CERTIFICADO
}
