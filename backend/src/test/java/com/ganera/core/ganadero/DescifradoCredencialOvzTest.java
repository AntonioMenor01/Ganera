package com.ganera.core.ganadero;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T7/T8/T9b de M2 (contexto real, para que la fila de auditoria y su ausencia sean de verdad). El descifrado es
 * bajo demanda y deja una fila {@code CREDENCIAL_OVZ_DESCIFRADA} SIN la contrasena; de otra gestoria lanza y no
 * deja fila; con un ciphertext corrupto la fila QUEDA (REQUIRES_NEW) y salta una excepcion sin el valor.
 *
 * <p>No es {@code @Transactional}: la fila de {@code registrarAparte} se confirma en su propia transaccion y se
 * lee por JDBC fuera de cualquier rollback (asi el caso corrupto no la oculta). Contrasena INVENTADA solo para
 * el test.
 */
@SpringBootTest
class DescifradoCredencialOvzTest {

    private static final String PLANO = "password-ovz-descifrado-inventada";
    /** Base64 valido (llega al descifrado GCM) que no descifra con ninguna clave. */
    private static final String CIFRADO_CORRUPTO = Base64.getEncoder().encodeToString(
            "ciphertext-corrupto-inventado-para-el-test-0123".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private DescifradoCredencialOvz servicio;
    @Autowired
    private CifradorAesGcm cifrador;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Usuario usuarioA;
    private Usuario usuarioB;
    private Ganadero ganaderoA;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria descifrado A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria descifrado B"));
        usuarioA = crearUsuario(gestoriaA, "descifradoA@test.com");
        usuarioB = crearUsuario(gestoriaB, "descifradoB@test.com");
        ganaderoA = crearGanadero(gestoriaA, "Titular descifrado A", "55555555K", PLANO);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        ganaderoRepository.deleteAllInBatch();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void descifraYDejaUnaFilaDeAuditoriaSinLaContrasena() {
        CredencialOvzEnClaro credencial = servicio.descifrarPasswordParaEnvioOvz(ganaderoA, principal(usuarioA, gestoriaA));

        assertThat(credencial.valor()).isEqualTo(PLANO);

        assertThat(contarFilasDescifrado()).isEqualTo(1);
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "select gestoria_id, actor_tipo, usuario_id, accion, entidad_tipo, entidad_id, antes, despues "
                        + "from auditoria where accion = 'CREDENCIAL_OVZ_DESCIFRADA'");
        assertThat(fila.get("gestoria_id")).asString().isEqualTo(String.valueOf(gestoriaA.getId()));
        assertThat(fila.get("actor_tipo")).isEqualTo("USUARIO");
        assertThat(fila.get("usuario_id")).asString().isEqualTo(String.valueOf(usuarioA.getId()));
        assertThat(fila.get("entidad_tipo")).isEqualTo("GANADERO");
        assertThat(fila.get("entidad_id")).asString().isEqualTo(String.valueOf(ganaderoA.getId()));
        assertThat(fila.get("antes")).isNull();
        assertThat(fila.get("despues")).isNull();
    }

    @Test
    void deOtraGestoriaLanzaYNoDescifraNiDejaFila() {
        assertThatThrownBy(() -> servicio.descifrarPasswordParaEnvioOvz(ganaderoA, principal(usuarioB, gestoriaB)))
                .isInstanceOf(CredencialOvzDeOtraGestoriaException.class);

        assertThat(contarFilasDescifrado()).isZero();
    }

    @Test
    void conUnCifradoCorruptoLaFilaQuedaYLanzaSinElValor() {
        jdbcTemplate.update("update ganadero set ovz_password_cifrada = ? where id = ?",
                CIFRADO_CORRUPTO, ganaderoA.getId());
        Ganadero recargado = ganaderoRepository.findById(ganaderoA.getId()).orElseThrow();

        assertThatThrownBy(() -> servicio.descifrarPasswordParaEnvioOvz(recargado, principal(usuarioA, gestoriaA)))
                .isInstanceOf(DescifradoCredencialOvzException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CIFRADO_CORRUPTO));

        // La fila quedo (REQUIRES_NEW), leida por JDBC fuera de cualquier transaccion con rollback.
        assertThat(contarFilasDescifrado()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private Integer contarFilasDescifrado() {
        return jdbcTemplate.queryForObject(
                "select count(*) from auditoria where accion = 'CREDENCIAL_OVZ_DESCIFRADA'", Integer.class);
    }

    private GaneraUserPrincipal principal(Usuario usuario, Gestoria gestoria) {
        return new GaneraUserPrincipal(usuario.getId(), gestoria.getId(), usuario.getEmail());
    }

    private Usuario crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario descifrado");
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private Ganadero crearGanadero(Gestoria gestoria, String nombre, String nif, String passwordOvz) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre(nombre);
        ganadero.setNif(nif);
        ganadero.setOvzUsuario("usuario-ovz-descifrado");
        ganadero.setOvzPasswordCifrada(cifrador.cifrar(passwordOvz));
        return ganaderoRepository.save(ganadero);
    }
}
