package com.ganera.core.shared.auditoria;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Auditoria base — T5. Unidad de {@code existsByIdAndGestoriaIdNot} (la consulta de seguridad: ¿el id existe en OTRA
 * gestoria?) en los cuatro repositorios, y de {@link AccesoAjeno} (que la usa para dejar la fila
 * {@code ACCESO_OTRA_GESTORIA} en la gestoria del actor, nunca en la victima).
 *
 * <p>Todo con {@code NOT_SUPPORTED}: {@code registrarAparte} es REQUIRES_NEW y CONFIRMA su fila en una transaccion
 * nueva; sin transaccion exterior, las entidades que crea cada test tambien se confirman (si no, la fila no veria su
 * gestoria por la FK). Se limpia por JDBC en {@code @AfterEach} (en H2 no hay trigger de inmutabilidad, V26 solo en
 * PostgreSQL; el repositorio de auditoria no tiene delete a proposito).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({AccesoAjeno.class, AuditoriaService.class, com.ganera.core.shared.tiempo.RelojConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccesoAjenoTest {

    @Autowired
    private AccesoAjeno accesoAjeno;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> gestoriasCreadas = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long id : gestoriasCreadas) {
            jdbc.update("delete from auditoria where gestoria_id = ?", id);
            jdbc.update("delete from tramite where gestoria_id = ?", id);
            jdbc.update("delete from contacto where gestoria_id = ?", id);
            jdbc.update("delete from explotacion where gestoria_id = ?", id);
            jdbc.update("delete from ganadero where gestoria_id = ?", id);
            jdbc.update("delete from usuario where gestoria_id = ?", id);
            jdbc.update("delete from gestoria where id = ?", id);
        }
        gestoriasCreadas.clear();
    }

    // --------------------------------------------- existsByIdAndGestoriaIdNot (los 4 repositorios)

    @Test
    void existsByIdAndGestoriaIdNotDistingueLaGestoriaEnLosCuatroRepositorios() {
        Gestoria a = gestoria("exists A");
        Gestoria b = gestoria("exists B");
        Explotacion explA = explotacion(a, "ES910000000001");
        Ganadero ganA = explA.getGanadero();
        Contacto conA = contacto(a, "+34600910001");
        Tramite tramA = tramite(a, conA);

        // Desde la propia gestoria: NO es ajeno. Desde la otra: SI. Inexistente: NO.
        assertThat(tramiteRepository.existsByIdAndGestoriaIdNot(tramA.getId(), a.getId())).isFalse();
        assertThat(tramiteRepository.existsByIdAndGestoriaIdNot(tramA.getId(), b.getId())).isTrue();
        assertThat(tramiteRepository.existsByIdAndGestoriaIdNot(999_999L, a.getId())).isFalse();

        assertThat(ganaderoRepository.existsByIdAndGestoriaIdNot(ganA.getId(), a.getId())).isFalse();
        assertThat(ganaderoRepository.existsByIdAndGestoriaIdNot(ganA.getId(), b.getId())).isTrue();
        assertThat(ganaderoRepository.existsByIdAndGestoriaIdNot(999_999L, a.getId())).isFalse();

        assertThat(explotacionRepository.existsByIdAndGestoriaIdNot(explA.getId(), a.getId())).isFalse();
        assertThat(explotacionRepository.existsByIdAndGestoriaIdNot(explA.getId(), b.getId())).isTrue();
        assertThat(explotacionRepository.existsByIdAndGestoriaIdNot(999_999L, a.getId())).isFalse();

        assertThat(contactoRepository.existsByIdAndGestoriaIdNot(conA.getId(), a.getId())).isFalse();
        assertThat(contactoRepository.existsByIdAndGestoriaIdNot(conA.getId(), b.getId())).isTrue();
        assertThat(contactoRepository.existsByIdAndGestoriaIdNot(999_999L, a.getId())).isFalse();
    }

    // --------------------------------------------- AccesoAjeno

    @Test
    void auditaUnRecursoAjenoEnLaGestoriaDelActorConLaEntidadYLaOperacion() {
        Gestoria a = gestoria("ajeno actor A");
        Gestoria b = gestoria("ajeno recurso B");
        Usuario usuarioA = usuario(a);
        Tramite tramB = tramite(b, contacto(b, "+34600900001"));

        boolean ajeno = accesoAjeno.tramite(usuarioA.getId(), a.getId(), tramB.getId(), OperacionAuditada.VER_TRAMITE);
        assertThat(ajeno).isTrue();

        List<Auditoria> enA = auditoriaRepository.findByGestoriaIdOrderByIdAsc(a.getId());
        assertThat(enA).hasSize(1);
        Auditoria fila = enA.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.ACCESO_OTRA_GESTORIA);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuarioA.getId());
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.TRAMITE);
        assertThat(fila.getEntidadId()).isEqualTo(tramB.getId());
        assertThat(fila.getDespues()).isEqualTo("{\"operacion\":\"VER_TRAMITE\"}");
        // NUNCA en la victima.
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(b.getId())).isEmpty();
    }

    @Test
    void noAuditaUnRecursoPropioNiUnoInexistente() {
        Gestoria a = gestoria("propio A");
        Usuario usuarioA = usuario(a);
        Tramite tramA = tramite(a, contacto(a, "+34600900002"));

        assertThat(accesoAjeno.tramite(usuarioA.getId(), a.getId(), tramA.getId(), OperacionAuditada.VER_TRAMITE))
                .isFalse();
        assertThat(accesoAjeno.tramite(usuarioA.getId(), a.getId(), 999_999L, OperacionAuditada.VER_TRAMITE))
                .isFalse();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(a.getId())).isEmpty();
    }

    /** Id en el cuerpo: tramite propio + explotacionId ajeno -> fila EXPLOTACION con EDITAR_TRAMITE. */
    @Test
    void patchTramitePropioConExplotacionIdAjenoAuditaLaExplotacion() {
        Gestoria a = gestoria("patch A");
        Gestoria b = gestoria("patch B");
        Usuario usuarioA = usuario(a);
        Tramite tramA = tramite(a, contacto(a, "+34600900003"));
        Explotacion explB = explotacion(b, "ES900000000001");

        accesoAjeno.patchTramite(usuarioA.getId(), a.getId(), tramA.getId(), explB.getId());

        List<Auditoria> enA = auditoriaRepository.findByGestoriaIdOrderByIdAsc(a.getId());
        assertThat(enA).hasSize(1);
        assertThat(enA.get(0).getEntidadTipo()).isEqualTo(EntidadAuditada.EXPLOTACION);
        assertThat(enA.get(0).getEntidadId()).isEqualTo(explB.getId());
        assertThat(enA.get(0).getDespues()).isEqualTo("{\"operacion\":\"EDITAR_TRAMITE\"}");
    }

    // --------------------------------------------- helpers

    private Gestoria gestoria(String nombre) {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria(nombre));
        gestoriasCreadas.add(gestoria.getId());
        return gestoria;
    }

    private Usuario usuario(Gestoria gestoria) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(UUID.randomUUID() + "@acceso.test");
        usuario.setPasswordHash("hash");
        usuario.setNombre("Usuaria");
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private Explotacion explotacion(Gestoria gestoria, String codigoRega) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + codigoRega);
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre("Finca " + codigoRega);
        return explotacionRepository.save(explotacion);
    }

    private Contacto contacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto acceso");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite tramite(Gestoria gestoria, Contacto contacto) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramiteRepository.save(tramite);
    }
}
