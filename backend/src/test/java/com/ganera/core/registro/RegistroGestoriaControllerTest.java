package com.ganera.core.registro;

import com.ganera.core.facturacion.StripeCheckoutService;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.web.MotivoErrorResponse;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class RegistroGestoriaControllerTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    /** Unico motivo para cualquier causa de fallo (sin oraculo de enumeracion de emails). */
    private static final MotivoErrorResponse MOTIVO_UNIFORME = new MotivoErrorResponse(
            "No se ha podido completar el registro con esos datos. Revisa el email y la contraseña e inténtalo de nuevo.");

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private RegistroGestoriaController nuevoController() {
        RegistroGestoriaService servicio = new RegistroGestoriaService(gestoriaRepository, usuarioRepository, passwordEncoder);
        StripeCheckoutService stripeCheckoutServiceSinConfigurar = new StripeCheckoutService(
                null, null, null, "", "", "", "");
        return new RegistroGestoriaController(servicio, stripeCheckoutServiceSinConfigurar);
    }

    @Test
    void registroValidoConStripeSinConfigurarDevuelve503PeroCreaGestoriaYUsuario() throws StripeException {
        RegistroGestoriaRequest request = new RegistroGestoriaRequest(
                "Gestoria Publica", "Primer Empleado", "publica@gestoria.com", "password123", RangoClientes.UNO_A_DIEZ);

        ResponseEntity<?> respuesta = nuevoController().registrar(request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        Usuario usuario = usuarioRepository.findByEmail("publica@gestoria.com").orElseThrow();
        assertThat(usuario.getGestoria()).isNotNull();
        assertThat(passwordEncoder.matches("password123", usuario.getPasswordHash())).isTrue();
    }

    /** No se hace ninguna consulta a la BD despues de la segunda llamada (el email duplicado):
     * el saveAndFlush fallido deja la Session de Hibernate en un estado en el que cualquier query
     * posterior podria disparar "AssertionFailure: don't flush the Session after an exception
     * occurs" (ver la misma nota en RegistroGestoriaServiceTest). Solo se comprueba la respuesta
     * HTTP, que ya es un objeto Java devuelto sin tocar la BD de nuevo. */
    @Test
    void emailDuplicadoDevuelve400ConMensajeGenerico() throws StripeException {
        RegistroGestoriaController controller = nuevoController();
        RegistroGestoriaRequest primero = new RegistroGestoriaRequest(
                "Gestoria Uno", "Empleado Uno", "duplicado@gestoria.com", "password123", RangoClientes.UNO_A_DIEZ);
        controller.registrar(primero);

        RegistroGestoriaRequest segundo = new RegistroGestoriaRequest(
                "Gestoria Dos", "Empleado Dos", "duplicado@gestoria.com", "password456", RangoClientes.ONCE_A_TREINTA);
        ResponseEntity<?> respuesta = controller.registrar(segundo);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(MOTIVO_UNIFORME);
    }

    @Test
    void passwordDebilDevuelve400ConMensajeGenericoYNoCreaNada() throws StripeException {
        RegistroGestoriaRequest request = new RegistroGestoriaRequest(
                "Gestoria Rechazada", "Empleado", "rechazado@gestoria.com", "corta", RangoClientes.UNO_A_DIEZ);

        ResponseEntity<?> respuesta = nuevoController().registrar(request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(MOTIVO_UNIFORME);
        assertThat(usuarioRepository.findByEmail("rechazado@gestoria.com")).isEmpty();
    }

    @Test
    void emailMalFormadoYDuplicadoDevuelvenElMismoMensaje() throws StripeException {
        RegistroGestoriaController controller = nuevoController();

        RegistroGestoriaRequest malFormado = new RegistroGestoriaRequest(
                "Gestoria", "Empleado", "no-es-un-email", "password123", RangoClientes.UNO_A_DIEZ);
        ResponseEntity<?> respuestaMalFormado = controller.registrar(malFormado);

        RegistroGestoriaRequest primero = new RegistroGestoriaRequest(
                "Gestoria Tres", "Empleado Tres", "otro-duplicado@gestoria.com", "password123", RangoClientes.UNO_A_DIEZ);
        controller.registrar(primero);
        RegistroGestoriaRequest segundo = new RegistroGestoriaRequest(
                "Gestoria Cuatro", "Empleado Cuatro", "otro-duplicado@gestoria.com", "password456", RangoClientes.UNO_A_DIEZ);
        ResponseEntity<?> respuestaDuplicado = controller.registrar(segundo);

        assertThat(respuestaMalFormado.getStatusCode().value()).isEqualTo(400);
        assertThat(respuestaDuplicado.getStatusCode().value()).isEqualTo(400);
        assertThat(respuestaMalFormado.getBody()).isEqualTo(MOTIVO_UNIFORME);
        assertThat(respuestaDuplicado.getBody()).isEqualTo(MOTIVO_UNIFORME);
    }
}
