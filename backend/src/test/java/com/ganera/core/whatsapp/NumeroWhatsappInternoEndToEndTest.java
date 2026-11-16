package com.ganera.core.whatsapp;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.GuardiaDmlExtension;
import com.ganera.core.shared.tiempo.RelojConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Alta interna de un número DEDICADO (plan 2026-11-16, T4). Como el OnboardingControllerTest: se construye el
 * controlador a mano sobre repositorios reales (H2 + Flyway) y la transacción la pone {@code @DataJpaTest};
 * {@link GuardiaDmlExtension} exige que la escritura de negocio del 201 deje su fila de auditoría.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({AuditoriaService.class, RelojConfig.class})
@ExtendWith(GuardiaDmlExtension.class)
class NumeroWhatsappInternoEndToEndTest {

    private static final String SECRETO = "secreto-numeros";
    private static final String COMPARTIDO = "whatsapp:+14155238886";

    @Autowired
    private NumeroWhatsappRepository numeroWhatsappRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private AuditoriaService auditoriaService;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private Clock reloj;

    private NumeroWhatsappController controller() {
        return new NumeroWhatsappController(numeroWhatsappRepository, gestoriaRepository, auditoriaService,
                reloj, SECRETO, COMPARTIDO);
    }

    @Test
    void secretoValidoSoloConElCorrecto() {
        NumeroWhatsappController c = controller();
        assertThat(c.secretoValido(SECRETO)).isTrue();
        assertThat(c.secretoValido("otro")).isFalse();
        assertThat(c.secretoValido(null)).isFalse();
    }

    @Test
    void altaCorrectaDevuelve201PersisteElNumeroYDejaFilaDeAuditoriaSinTelefono() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria numero propio"));

        ResponseEntity<?> respuesta = controller().registrar(SECRETO,
                new NumeroWhatsappRequest("whatsapp:+34 910 000 088", gestoria.getId()));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        NumeroWhatsappResponse cuerpo = (NumeroWhatsappResponse) respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.gestoriaId()).isEqualTo(gestoria.getId());

        NumeroWhatsapp guardado = numeroWhatsappRepository.findById(cuerpo.id()).orElseThrow();
        assertThat(guardado.getNumero()).isEqualTo("+34910000088"); // normalizado a E.164
        assertThat(guardado.getGestoria().getId()).isEqualTo(gestoria.getId());
        assertThat(guardado.isActivo()).isTrue();

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId());
        assertThat(filas).hasSize(1);
        Auditoria fila = filas.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.NUMERO_WHATSAPP_REGISTRADO);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.SISTEMA);
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.NUMERO_WHATSAPP);
        assertThat(fila.getEntidadId()).isEqualTo(cuerpo.id());
        // El telefono NUNCA en antes/despues.
        assertThat(fila.getAntes()).isNull();
        assertThat(fila.getDespues()).isNull();
        assertThat(fila.getUsuarioId()).isNull();
    }

    @Test
    void unNumeroIgualAlCompartidoEs400SinCrearNiFila() {
        // El rechazo del compartido va ANTES de mirar la gestoria: no hace falta crearla (y así la guardia DML no
        // ve una escritura de setup sin auditar). gestoriaId ficticio.
        ResponseEntity<?> respuesta = controller().registrar(SECRETO,
                new NumeroWhatsappRequest("whatsapp:+14155238886", 1L));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(numeroWhatsappRepository.count()).isZero();
    }

    @Test
    void unNumeroYaRegistradoEs409() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria duplicado num"));
        controller().registrar(SECRETO, new NumeroWhatsappRequest("+34910000099", gestoria.getId()));

        ResponseEntity<?> repetido = controller().registrar(SECRETO,
                new NumeroWhatsappRequest("whatsapp:+34910000099", gestoria.getId()));

        assertThat(repetido.getStatusCode().value()).isEqualTo(409);
        assertThat(numeroWhatsappRepository.count()).isEqualTo(1);
    }

    @Test
    void unaGestoriaInexistenteEs404() {
        ResponseEntity<?> respuesta = controller().registrar(SECRETO,
                new NumeroWhatsappRequest("+34910000111", 999_999L));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(numeroWhatsappRepository.count()).isZero();
    }

    @Test
    void unNumeroInvalidoEs400() {
        // El numero invalido se rechaza antes de mirar la gestoria: gestoriaId ficticio, sin crearla.
        ResponseEntity<?> respuesta = controller().registrar(SECRETO,
                new NumeroWhatsappRequest("no-es-un-numero", 1L));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(numeroWhatsappRepository.count()).isZero();
    }

    @Test
    void sinGestoriaEnElCuerpoEs400() {
        ResponseEntity<?> respuesta = controller().registrar(SECRETO,
                new NumeroWhatsappRequest("+34910000122", null));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(numeroWhatsappRepository.count()).isZero();
    }

    @Test
    void sinSecretoEs401SinCrearNada() {
        // El secreto se comprueba lo primero: gestoriaId ficticio, sin crearla.
        ResponseEntity<?> respuesta = controller().registrar("incorrecto",
                new NumeroWhatsappRequest("+34910000133", 1L));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(401);
        assertThat(numeroWhatsappRepository.count()).isZero();
    }
}
