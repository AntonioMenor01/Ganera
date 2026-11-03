package com.ganera.core.facturacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria del checkout del alta publica (T4): al crear la Checkout Session con una cantidad estimada se crea la
 * suscripcion en TRIAL y se dejan SUSCRIPCION_CREADA y SUSCRIPCION_CANTIDAD_FIJADA (actor SISTEMA, via registrarAparte,
 * REQUIRES_NEW, porque el servicio no es @Transactional). La unica linea de red (crearSesionEnStripe) se sobreescribe
 * para devolver una Session con url, sin tocar Stripe (convencion test-sin-mocks-externos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StripeCheckoutServiceAuditoriaTest {

    @Autowired
    private SuscripcionService suscripcionService;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private AuditoriaService auditoriaService;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        suscripcionRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    /** StripeCheckoutService "configurado" (apiKey + priceId) pero sin red: crearSesionEnStripe devuelve una Session. */
    private StripeCheckoutService servicioSinRed() {
        return new StripeCheckoutService(suscripcionService, suscripcionRepository, auditoriaService,
                "sk_test_inventada", "price_inventado",
                "https://ganera.test/exito", "https://ganera.test/cancelado") {
            @Override
            Session crearSesionEnStripe(SessionCreateParams params) {
                Session session = new Session();
                session.setUrl("https://stripe.test/checkout/inventado");
                return session;
            }
        };
    }

    @Test
    void elCheckoutDejaSuscripcionCreadaYCantidadFijadaConActorSistemaEnLaGestoriaDelCheckout() throws Exception {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria checkout auditada"));

        servicioSinRed().crearSesionCheckoutConCantidadEstimada(gestoria.getId(), 15L);

        Suscripcion suscripcion = suscripcionRepository.findByGestoriaId(gestoria.getId()).orElseThrow();
        assertThat(suscripcion.getEstado()).isEqualTo(EstadoSuscripcion.TRIAL);
        assertThat(suscripcion.getExplotacionesContratadas()).isEqualTo(15);

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId());
        assertThat(filas).extracting(Auditoria::getAccion)
                .containsExactly(AccionAuditoria.SUSCRIPCION_CREADA, AccionAuditoria.SUSCRIPCION_CANTIDAD_FIJADA);
        filas.forEach(fila -> {
            assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.SISTEMA);
            assertThat(fila.getUsuarioId()).isNull();
            assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.SUSCRIPCION);
            assertThat(fila.getEntidadId()).isEqualTo(suscripcion.getId());
        });
        assertThat(filas.get(0).getDespues()).contains("\"estado\":\"TRIAL\"");
        assertThat(filas.get(1).getDespues()).contains("\"explotaciones_contratadas\":15");
    }
}
