package com.ganera.core.ganadero;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T8/T9 de M2 a nivel unitario (Mockito): fija el ORDEN (registrarAparte antes de descifrar), que un fallo de la
 * fila impide descifrar, y que de otra gestoria no se registra ni se descifra. Son las guardas de las mutaciones
 * B (quitar la comprobacion de gestoria) y C (quitar registrarAparte).
 */
class DescifradoCredencialOvzUnitTest {

    private static final Long GESTORIA_GANADERO = 7L;
    private static final Long GANADERO_ID = 42L;
    private static final String CIPHERTEXT = "ciphertext-en-reposo";

    private final CifradorAesGcm cifrador = mock(CifradorAesGcm.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final DescifradoCredencialOvz servicio = new DescifradoCredencialOvz(cifrador, auditoriaService);

    private Ganadero ganaderoDeLaGestoria(Long gestoriaId) {
        Ganadero ganadero = mock(Ganadero.class);
        Gestoria gestoria = mock(Gestoria.class);
        when(ganadero.getGestoria()).thenReturn(gestoria);
        when(gestoria.getId()).thenReturn(gestoriaId);
        when(ganadero.getId()).thenReturn(GANADERO_ID);
        when(ganadero.getOvzPasswordCifrada()).thenReturn(CIPHERTEXT);
        return ganadero;
    }

    private GaneraUserPrincipal principal(Long gestoriaId) {
        return new GaneraUserPrincipal(5L, gestoriaId, "u@test.com");
    }

    @Test
    void registraLaFilaAntesDeDescifrar() {
        Ganadero ganadero = ganaderoDeLaGestoria(GESTORIA_GANADERO);
        when(cifrador.descifrar(CIPHERTEXT)).thenReturn("plano");

        CredencialOvzEnClaro credencial = servicio.descifrarPasswordParaEnvioOvz(ganadero, principal(GESTORIA_GANADERO));

        assertThat(credencial.valor()).isEqualTo("plano");
        InOrder orden = inOrder(auditoriaService, cifrador);
        orden.verify(auditoriaService).registrarAparte(eq(AccionAuditoria.CREDENCIAL_OVZ_DESCIFRADA),
                eq(GESTORIA_GANADERO), any(Actor.class), eq(EntidadAuditada.GANADERO), eq(GANADERO_ID),
                isNull(), isNull());
        orden.verify(cifrador).descifrar(CIPHERTEXT);
    }

    @Test
    void siFallaLaFilaNoSeDescifraNiSeDevuelvePlano() {
        Ganadero ganadero = ganaderoDeLaGestoria(GESTORIA_GANADERO);
        doThrow(new RuntimeException("fallo al auditar")).when(auditoriaService).registrarAparte(
                any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> servicio.descifrarPasswordParaEnvioOvz(ganadero, principal(GESTORIA_GANADERO)))
                .isInstanceOf(RuntimeException.class);

        verify(cifrador, never()).descifrar(any());
    }

    @Test
    void deOtraGestoriaNoRegistraNiDescifra() {
        Ganadero ganadero = ganaderoDeLaGestoria(GESTORIA_GANADERO);

        assertThatThrownBy(() -> servicio.descifrarPasswordParaEnvioOvz(ganadero, principal(999L)))
                .isInstanceOf(CredencialOvzDeOtraGestoriaException.class);

        verifyNoInteractions(auditoriaService);
        verifyNoInteractions(cifrador);
    }
}
