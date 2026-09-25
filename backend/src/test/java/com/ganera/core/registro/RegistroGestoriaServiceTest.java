package com.ganera.core.registro;

import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class RegistroGestoriaServiceTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private RegistroGestoriaService nuevoServicio() {
        return new RegistroGestoriaService(gestoriaRepository, usuarioRepository, passwordEncoder);
    }

    @Test
    void creaGestoriaYUsuarioConPasswordHasheada() {
        RegistroGestoriaRequest request = new RegistroGestoriaRequest(
                "Gestoria Publica", "Primer Empleado", "publica@gestoria.com", "password123", RangoClientes.UNO_A_DIEZ);

        Long gestoriaId = nuevoServicio().crearGestoriaYUsuario(request);

        assertThat(gestoriaId).isNotNull();
        Usuario usuario = usuarioRepository.findByEmail("publica@gestoria.com").orElseThrow();
        assertThat(usuario.getGestoria().getId()).isEqualTo(gestoriaId);
        assertThat(usuario.getNombre()).isEqualTo("Primer Empleado");
        assertThat(usuario.isActivo()).isTrue();
        assertThat(usuario.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", usuario.getPasswordHash())).isTrue();
    }

    /**
     * No se comprueba aqui que el Gestoria de la segunda llamada quede huerfano leyendo la BD
     * en el mismo metodo: el saveAndFlush fallido dentro de un @DataJpaTest deja la Session de
     * Hibernate en un estado en el que cualquier query posterior (incluso un simple count())
     * puede disparar "AssertionFailure: don't flush the Session after an exception occurs" --
     * el mismo problema ya documentado en CLAUDE.md para ExplotacionImportFilaService. La
     * ausencia real de Gestoria huerfano se verifica en RegistroGestoriaEndToEndTest, donde cada
     * request HTTP usa su propia transaccion/sesion independiente.
     */
    @Test
    void emailDuplicadoLanzaDataIntegrityViolationException() {
        RegistroGestoriaService servicio = nuevoServicio();
        RegistroGestoriaRequest primero = new RegistroGestoriaRequest(
                "Gestoria Uno", "Empleado Uno", "duplicado@gestoria.com", "password123", RangoClientes.UNO_A_DIEZ);
        servicio.crearGestoriaYUsuario(primero);

        RegistroGestoriaRequest segundo = new RegistroGestoriaRequest(
                "Gestoria Dos", "Empleado Dos", "duplicado@gestoria.com", "password456", RangoClientes.ONCE_A_TREINTA);

        assertThatThrownBy(() -> servicio.crearGestoriaYUsuario(segundo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
