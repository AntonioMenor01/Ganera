package com.ganera.core.ovz;

import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.tramite.Tramite;
import org.springframework.stereotype.Service;

@Service
public class PlaywrightOvzAutomationService implements OvzAutomationService {

    @Override
    public void sincronizarInventarioInicial(Ganadero ganadero) {
        throw new UnsupportedOperationException(
                "Sincronizacion de inventario OVZ pendiente de implementar (Prompt 3a)");
    }

    @Override
    public void ejecutarTramite(Tramite tramite) {
        throw new UnsupportedOperationException(
                "Ejecucion de tramites OVZ pendiente de implementar (Prompt 3c)");
    }
}
