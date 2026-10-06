package com.ganera.core.registro;

import com.ganera.core.facturacion.StripeCheckoutService;
import com.ganera.core.shared.web.MotivoErrorResponse;
import com.stripe.exception.StripeException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Alta publica de Gestoria, en paralelo a /internal/onboarding/gestoria (que se queda igual, para
 * soporte/casos manuales -- esta es una via adicional, no un reemplazo). Publico, sin JWT ni
 * secreto compartido: ver SecurityConfig.permitAll para esta ruta.
 *
 * <p><b>Cerrado por defecto (Prompt C, T2, D4).</b> Solo se llega aqui con
 * {@code ganera.registro.abierto=true}. Con {@code false} (el valor por defecto, fail-closed)
 * {@link RegistroCerradoFilter} contesta 404 sin cuerpo antes de Spring Security y antes de leer
 * el cuerpo, asi que este metodo no se ejecuta y no se crea nada. El permitAll de SecurityConfig se
 * queda: con el registro abierto la ruta tiene que seguir siendo publica, y con el cerrado el filtro
 * va antes que la seguridad (mismo 404 con o sin JWT). Llamar a este metodo directamente (como hacen
 * los tests unitarios) se salta el filtro.
 */
@RestController
public class RegistroGestoriaController {

    /** Mismo motivo para cualquier causa de fallo (email duplicado, password debil, email mal
     * formado, campo en blanco) -- no revela cual fue, para no permitir enumerar que emails ya
     * estan registrados (mismo principio que el 401 uniforme de AuthService.autenticar). Se
     * devuelve como {@code {"motivo": "..."}} (MotivoErrorResponse), el formato de error comun;
     * el 503 de Stripe sin configurar va sin cuerpo. */
    private static final String MOTIVO_REGISTRO_INVALIDO =
            "No se ha podido completar el registro con esos datos. Revisa el email y la contraseña e inténtalo de nuevo.";

    private final RegistroGestoriaService registroGestoriaService;
    private final StripeCheckoutService stripeCheckoutService;

    public RegistroGestoriaController(
            RegistroGestoriaService registroGestoriaService,
            StripeCheckoutService stripeCheckoutService) {
        this.registroGestoriaService = registroGestoriaService;
        this.stripeCheckoutService = stripeCheckoutService;
    }

    @PostMapping("/gestorias/registro")
    public ResponseEntity<?> registrar(@RequestBody RegistroGestoriaRequest request) throws StripeException {
        if (!RegistroGestoriaValidacion.solicitudValida(request)) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_REGISTRO_INVALIDO));
        }

        Long gestoriaId;
        try {
            gestoriaId = registroGestoriaService.crearGestoriaYUsuario(request);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_REGISTRO_INVALIDO));
        }

        long cantidad = request.rangoClientes().quantityExplotacionesEstimada();
        Optional<String> url = stripeCheckoutService.crearSesionCheckoutConCantidadEstimada(gestoriaId, cantidad);
        return url.map(u -> ResponseEntity.ok(new RegistroGestoriaResponse(u)))
                .orElseGet(() -> ResponseEntity.status(503).build());
    }
}
