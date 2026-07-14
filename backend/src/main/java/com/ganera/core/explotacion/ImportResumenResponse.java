package com.ganera.core.explotacion;

import java.util.List;

public record ImportResumenResponse(
        ImportHojaResumen explotaciones,
        ImportHojaResumen animales,
        List<ImportErrorDto> errores) {
}
