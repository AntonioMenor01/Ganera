package com.ganera.core.shared.auditoria;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.tramite.EstadoTramite;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * AuditoriaService sobre H2 con las migraciones reales (V25). "Ahora" sale de un Clock fijo con nanosegundos: la
 * fila tiene que llevarlo truncado a microsegundos (una implementacion con Instant.now() no daria ese valor).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({AuditoriaService.class, AuditoriaServiceTest.RelojFijo.class})
class AuditoriaServiceTest {

    private static final Instant AHORA = Instant.parse("2031-03-10T09:00:00.123456789Z");
    private static final Instant AHORA_EN_MICROS = Instant.parse("2031-03-10T09:00:00.123456Z");

    @TestConfiguration
    static class RelojFijo {
        @Bean
        Clock reloj() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @Autowired
    private AuditoriaService auditoria;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transacciones;

    @AfterEach
    void limpiar() {
        ContextoPeticion.limpiar();
        // Los tests sin transaccion (NOT_SUPPORTED) CONFIRMAN sus filas en la H2 compartida por toda la suite: una
        // fila de auditoria con su usuario haria fallar el deleteAll() de usuarios/gestorias de los demas tests (FK
        // sin ON DELETE). Se borran por JDBC (en H2 no hay trigger; el repositorio no tiene delete a proposito). En
        // los tests con transaccion esto va dentro de ella y se deshace con el resto.
        for (Long gestoriaId : gestoriasCreadas) {
            jdbc.update("delete from auditoria where gestoria_id = ?", gestoriaId);
            jdbc.update("delete from usuario where gestoria_id = ?", gestoriaId);
            jdbc.update("delete from gestoria where id = ?", gestoriaId);
        }
        gestoriasCreadas.clear();
    }

    private final List<Long> gestoriasCreadas = new java.util.ArrayList<>();

    private Gestoria gestoria(String nombre) {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria(nombre));
        gestoriasCreadas.add(gestoria.getId());
        return gestoria;
    }

    private Usuario usuario(Gestoria gestoria) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(UUID.randomUUID() + "@auditoria.test");
        usuario.setPasswordHash("hash");
        usuario.setNombre("Usuaria");
        return usuarioRepository.save(usuario);
    }

    private static DatosAuditoria antes() {
        return DatosAuditoria.con(Clave.ESTADO, EstadoTramite.PENDIENTE_REVISION).y(Clave.VERSION, 3);
    }

    private static DatosAuditoria despues() {
        return DatosAuditoria.con(Clave.ESTADO, EstadoTramite.APROBADO).y(Clave.VERSION, 4);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registrarSinTransaccionFallaYNoEscribeNada() {
        Gestoria gestoria = gestoria("Auditoria sin transaccion");

        assertThatThrownBy(() -> auditoria.registrar(AccionAuditoria.TRAMITE_APROBADO, gestoria.getId(),
                Actor.sistema(), EntidadAuditada.TRAMITE, 1L, antes(), despues()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId())).isEmpty();
    }

    @Test
    void conTransaccionEscribeLaFilaExacta() {
        Gestoria gestoria = gestoria("Auditoria fila exacta");
        Usuario usuario = usuario(gestoria);

        auditoria.registrar(AccionAuditoria.TRAMITE_APROBADO, gestoria.getId(), Actor.usuario(usuario.getId()),
                EntidadAuditada.TRAMITE, 77L, antes(), despues());
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> fila = jdbc.queryForMap("select * from auditoria where gestoria_id = ?", gestoria.getId());
        assertThat(fila).containsEntry("ACTOR_TIPO", "USUARIO")
                .containsEntry("USUARIO_ID", usuario.getId())
                .containsEntry("ACCION", "TRAMITE_APROBADO")
                .containsEntry("ENTIDAD_TIPO", "TRAMITE")
                .containsEntry("ENTIDAD_ID", 77L)
                .containsEntry("ANTES", "{\"estado\":\"PENDIENTE_REVISION\",\"version\":3}")
                .containsEntry("DESPUES", "{\"estado\":\"APROBADO\",\"version\":4}")
                .containsEntry("IP", null)
                .containsEntry("REQUEST_ID", null);
        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId());
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getCreatedAt()).isEqualTo(AHORA_EN_MICROS);
        assertThat(filas.get(0).getGestoria().getId()).isEqualTo(gestoria.getId());
    }

    @Test
    void sinDatosAntesNiDespuesLasColumnasQuedanNullYUnActorSinUsuarioNoLlevaUsuario() {
        Gestoria gestoria = gestoria("Auditoria sin datos");

        auditoria.registrar(AccionAuditoria.GESTORIA_CREADA, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.GESTORIA, gestoria.getId(), null, null);
        entityManager.flush();

        Auditoria fila = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId()).get(0);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.SISTEMA);
        assertThat(fila.getUsuarioId()).isNull();
        assertThat(fila.getAntes()).isNull();
        assertThat(fila.getDespues()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registrarAparteSobreviveAlRollbackDeLaTransaccionExterior() {
        Gestoria gestoria = gestoria("Auditoria aparte");
        Usuario usuario = usuario(gestoria);
        TransactionTemplate exterior = new TransactionTemplate(transacciones);

        exterior.executeWithoutResult(estado -> {
            auditoria.registrarAparte(AccionAuditoria.ACCESO_OTRA_GESTORIA, gestoria.getId(),
                    Actor.usuario(usuario.getId()), EntidadAuditada.TRAMITE, 5L, null, null);
            // Y una escritura normal en la misma transaccion exterior, que si se deshace.
            auditoria.registrar(AccionAuditoria.TRAMITE_EDITADO, gestoria.getId(), Actor.usuario(usuario.getId()),
                    EntidadAuditada.TRAMITE, 5L, null, null);
            estado.setRollbackOnly();
        });

        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId()))
                .extracting(Auditoria::getAccion).containsExactly(AccionAuditoria.ACCESO_OTRA_GESTORIA);
    }

    @Test
    void unUsuarioDeOtraGestoriaSeRechazaYNoEscribeNingunaFila() {
        Gestoria gestoriaA = gestoria("Auditoria A");
        Gestoria gestoriaB = gestoria("Auditoria B");
        Usuario deB = usuario(gestoriaB);

        assertThatThrownBy(() -> auditoria.registrar(AccionAuditoria.TRAMITE_APROBADO, gestoriaA.getId(),
                Actor.usuario(deB.getId()), EntidadAuditada.TRAMITE, 1L, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> auditoria.registrar(AccionAuditoria.TRAMITE_APROBADO, gestoriaA.getId(),
                Actor.usuario(Long.MAX_VALUE), EntidadAuditada.TRAMITE, 1L, null, null))
                .isInstanceOf(IllegalStateException.class);
        entityManager.flush();

        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaA.getId())).isEmpty();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaB.getId())).isEmpty();
    }

    /** La variante aparte (lecturas sensibles, accesos a otra gestoria) aplica la misma regla. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registrarAparteTambienRechazaUnUsuarioDeOtraGestoria() {
        Gestoria gestoriaA = gestoria("Auditoria aparte A");
        Gestoria gestoriaB = gestoria("Auditoria aparte B");
        Usuario deB = usuario(gestoriaB);

        assertThatThrownBy(() -> auditoria.registrarAparte(AccionAuditoria.ACCESO_OTRA_GESTORIA, gestoriaA.getId(),
                Actor.usuario(deB.getId()), EntidadAuditada.TRAMITE, 1L, null, null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaA.getId())).isEmpty();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaB.getId())).isEmpty();
    }

    @Test
    void sinGestoriaNiObligatoriosEsUnaExcepcion() {
        Gestoria gestoria = gestoria("Auditoria obligatorios");

        assertThatThrownBy(() -> auditoria.registrar(AccionAuditoria.GESTORIA_CREADA, null, Actor.sistema(),
                EntidadAuditada.GESTORIA, 1L, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditoria.registrar(null, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.GESTORIA, 1L, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditoria.registrar(AccionAuditoria.GESTORIA_CREADA, gestoria.getId(), null,
                EntidadAuditada.GESTORIA, 1L, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditoria.registrar(AccionAuditoria.GESTORIA_CREADA, gestoria.getId(),
                Actor.sistema(), null, 1L, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Actor.usuario(null)).isInstanceOf(NullPointerException.class);
        entityManager.flush();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId())).isEmpty();
    }

    @Test
    void ipYRequestIdSalenDelContextoDeLaPeticionSiLoHay() {
        Gestoria gestoria = gestoria("Auditoria contexto");
        String requestId = UUID.randomUUID().toString();

        auditoria.registrar(AccionAuditoria.LECTURA_CONTACTOS, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.CONTACTO, null, null, null);
        ContextoPeticion.establecer("203.0.113.7", requestId);
        auditoria.registrar(AccionAuditoria.LECTURA_TRAMITE, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.TRAMITE, 9L, null, null);
        entityManager.flush();

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId());
        assertThat(filas).extracting(Auditoria::getIp).containsExactly(null, "203.0.113.7");
        assertThat(filas).extracting(Auditoria::getRequestId).containsExactly(null, requestId);
        assertThat(filas).extracting(Auditoria::getEntidadId).containsExactly(null, 9L);
    }
}
