package com.ganera.core.ganadero;

import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.springframework.stereotype.Service;

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
 *
 * <p><b>Deliberadamente NO es {@code @Transactional}</b> (escalon 2, T0, pendiente de M2): la unica escritura es la
 * auditoria, que ya va en su propia transaccion ({@code registrarAparte}, {@code REQUIRES_NEW}), y el descifrado es
 * puro. Si el metodo fuera {@code @Transactional (REQUIRED)}, el worker que lo llame dentro de la transaccion de su
 * trabajo y capture su excepcion dejaria esa transaccion {@code rollback-only} y el commit del trabajo fallaria con
 * {@code UnexpectedRollbackException}. Sin la anotacion, lanzar aqui no contamina la transaccion del que llama. Lo
 * fija {@code DescifradoCredencialOvzTest.llamadoDentroDeOtraTransaccionNoLaMarcaRollbackOnly}.
 */
@Service
public class DescifradoCredencialOvz {

    private final CifradorAesGcm cifrador;
    private final AuditoriaService auditoriaService;

    public DescifradoCredencialOvz(CifradorAesGcm cifrador, AuditoriaService auditoriaService) {
        this.cifrador = cifrador;
        this.auditoriaService = auditoriaService;
    }

    public CredencialOvzEnClaro descifrarPasswordParaEnvioOvz(Ganadero ganadero, GaneraUserPrincipal principal) {
        Objects.requireNonNull(ganadero, "ganadero");
        Objects.requireNonNull(principal, "principal");

        return descifrar(ganadero, principal.gestoriaId(), Actor.usuario(principal.usuarioId()));
    }

    /**
     * Variante del WORKER de OVZ (T4, escalon 2): sin principal HTTP, actor SISTEMA; el aislamiento se comprueba contra
     * la gestoria del TRABAJO. Mismo orden y mismas garantias que la via del usuario (aislamiento, fila ANTES de
     * descifrar, mensaje generico). Tampoco es {@code @Transactional}: el worker la llama FUERA de toda transaccion.
     */
    public CredencialOvzEnClaro descifrarPasswordParaWorker(Ganadero ganadero, Long gestoriaIdDelTrabajo) {
        Objects.requireNonNull(ganadero, "ganadero");
        Objects.requireNonNull(gestoriaIdDelTrabajo, "gestoriaIdDelTrabajo");
        return descifrar(ganadero, gestoriaIdDelTrabajo, Actor.sistema());
    }

    private CredencialOvzEnClaro descifrar(Ganadero ganadero, Long gestoriaEsperada, Actor actor) {
        Long gestoriaGanadero = ganadero.getGestoria().getId();
        if (!gestoriaGanadero.equals(gestoriaEsperada)) {
            throw new CredencialOvzDeOtraGestoriaException();
        }

        // La fila ANTES de descifrar (REQUIRES_NEW): queda aunque lo de abajo falle.
        auditoriaService.registrarAparte(AccionAuditoria.CREDENCIAL_OVZ_DESCIFRADA, gestoriaGanadero,
                actor, EntidadAuditada.GANADERO, ganadero.getId(), null, null);

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
