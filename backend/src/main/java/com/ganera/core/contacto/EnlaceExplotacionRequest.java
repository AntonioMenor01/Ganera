package com.ganera.core.contacto;

/** rol llega como String a proposito: un valor fuera del enum debe acabar en 400 con motivo, no
 * en el error de deserializacion generico de Jackson. Se parsea en ContactoController. */
public record EnlaceExplotacionRequest(Long explotacionId, String rol) {
}
