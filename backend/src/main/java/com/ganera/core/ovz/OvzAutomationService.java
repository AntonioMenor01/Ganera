package com.ganera.core.ovz;

import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.tramite.Tramite;

/**
 * Automatizacion de OVZ.net via Playwright. Sin logica real todavia
 * (bloqueado hasta explorar la estructura real de OVZ.net, Prompt 3a/3c).
 */
public interface OvzAutomationService {
    void sincronizarInventarioInicial(Ganadero ganadero);

    void ejecutarTramite(Tramite tramite);
}
