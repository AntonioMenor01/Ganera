package com.ganera.core.explotacion;

import java.util.List;

/** contactos va a 0/0/0 si el Excel no trae la hoja opcional "Contactos". */
public record ImportResumenResponse(
        ImportHojaResumen explotaciones,
        ImportHojaResumen animales,
        ImportHojaResumen contactos,
        List<ImportErrorDto> errores) {
}
