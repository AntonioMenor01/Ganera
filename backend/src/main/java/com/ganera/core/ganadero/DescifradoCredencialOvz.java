package com.ganera.core.ganadero;

import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Descifra la contrasena OVZ de un Ganadero BAJO DEMANDA (M2), unicamente en la via de envio a OVZ (escalon 2,
 * todavia sin cablear): asi cargar un Ganadero no descifra nada y un ciphertext corrupto no rompe el detalle ni
 * {@code /ganaderos}. Cada descifrado deja una fila de auditoria {@code CREDENCIAL_OVZ_DESCIFRADA} (sin la
 * contrasena, nunca en {@code antes}/{@code despues}).
 *
 * <p>Orden deliberado dentro del metodo:
 * <ol>
 *   <li><b>Aislamiento</b>: la gestoria del Ganadero debe coincidir con la del actor autenticado (una sola
 *       fuente: el {@link GaneraUserPrincipal}). Si no, {@link CredencialOvzDeOtraGestoriaException} sin descifrar
 *       y sin dejar fila.</li>
 *   <li><b>Auditoria antes de descifrar</b> ({@link AuditoriaService#registrarAparte}, {@code REQUIRES_NEW}): la
 *       fila se confirma en su propia transaccion, asi que queda aunque el descifrado o el envio posterior fallen.
 *       Si registrar falla, la excepcion se propaga y NO se descifra ni se devuelve el plano.</li>
 *   <li><b>Descifrar</b>: un ciphertext corrupto (o sin credencial) da {@link DescifradoCredencialOvzException},
 *       con mensaje generico (nunca el valor); la fila ya quedo.</li>
 * </ol>
 */
@Service
public class DescifradoCredencialOvz {

    private final CifradorAesGcm cifrador;
    private final AuditoriaService auditoriaService;

    public DescifradoCredencialOvz(CifradorAesGcm cifrador, AuditoriaService auditoriaService) {
        this.cifrador = cifrador;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public CredencialOvzEnClaro descifrarPasswordParaEnvioOvz(Ganadero ganadero, GaneraUserPrincipal principal) {
        Objects.requireNonNull(ganadero, "ganadero");
        Objects.requireNonNull(principal, "principal");

        Long gestoriaGanadero = ganadero.getGestoria().getId();
        if (!gestoriaGanadero.equals(principal.gestoriaId())) {
            throw new CredencialOvzDeOtraGestoriaException();
        }

        // La fila ANTES de descifrar (REQUIRES_NEW): queda aunque lo de abajo falle.
        auditoriaService.registrarAparte(AccionAuditoria.CREDENCIAL_OVZ_DESCIFRADA, gestoriaGanadero,
                Actor.usuario(principal.usuarioId()), EntidadAuditada.GANADERO, ganadero.getId(), null, null);

        String plano;
        try {
            plano = cifrador.descifrar(ganadero.getOvzPasswordCifrada());
        } catch (RuntimeException e) {
            // Nunca el ciphertext ni el plano en el mensaje (no se encadena la causa del cifrador).
            throw new DescifradoCredencialOvzException();
        }
        if (plano == null) {
            throw new DescifradoCredencialOvzException();
        }
        return new CredencialOvzEnClaro(plano);
    }
}
