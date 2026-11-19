package com.ganera.core.ovz.spike;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * SPIKE de ALCANCE (NO producción): ¿responde OVZnet desde la IP que ejecuta este test, con SOLO la página de
 * entrada y SIN credenciales? Pensado para correrlo DESDE CLEVER (la instancia del backend) y ver si OVZ no
 * bloquea su IP de París, antes de montar nada. También sirve en local como comprobación de humo.
 *
 * <p>No inicia sesión, no navega, no escribe credenciales. Solo abre {@code OVZ_URL}, comprueba que responde
 * HTTP 2xx/3xx y que hay un campo de contraseña en la página de entrada (señal de que cargó la pantalla de
 * login, no un error del proxy). No toma capturas. Logs sin datos.
 *
 * <p>Desactivado por defecto: corre con {@code OVZ_SPIKE_ALCANCE=true} y {@code OVZ_URL} definida.
 * Ejecutar: {@code OVZ_SPIKE_ALCANCE=true OVZ_URL=... ./mvnw test -Dtest=OvzAlcanceLoginTest}
 * (en Clever, con Chromium disponible; si no lo está, este test mismo dirá que Playwright no pudo arrancar —
 * ver el documento del spike para la alternativa del worker en Docker).
 */
@EnabledIfEnvironmentVariable(named = "OVZ_SPIKE_ALCANCE", matches = "true")
class OvzAlcanceLoginTest {

    @Test
    void laPaginaDeLoginRespondeSinCredenciales() {
        String url = System.getenv("OVZ_URL");
        if (url == null || url.isBlank()) {
            fail("Falta OVZ_URL (la URL de entrada de OVZnet).");
        }

        try (Playwright playwright = Playwright.create()) {
            Browser navegador = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = navegador.newContext().newPage();

            var respuesta = page.navigate(url, new Page.NavigateOptions().setTimeout(20_000));
            int estado = respuesta == null ? -1 : respuesta.status();
            boolean hayCampoPassword = page.locator("input[type=password]").count() > 0;

            System.out.println("ALCANCE OVZ: HTTP " + estado + ", campo de contraseña presente: " + hayCampoPassword);
            if (estado < 200 || estado >= 400) {
                fail("OVZ no respondió correctamente desde esta IP (HTTP " + estado + ").");
            }
            if (!hayCampoPassword) {
                fail("La página respondió pero no parece la de login (sin campo de contraseña): revisar IP/proxy/URL.");
            }
            navegador.close();
        } catch (RuntimeException e) {
            // Si Playwright/Chromium no arranca en el entorno (p. ej. Clever sin dependencias), se ve aquí.
            fail("No se pudo comprobar el alcance: " + e.getClass().getSimpleName()
                    + " — " + (e.getMessage() == null ? "" : e.getMessage().split("\\R", 2)[0]));
        }
    }
}
