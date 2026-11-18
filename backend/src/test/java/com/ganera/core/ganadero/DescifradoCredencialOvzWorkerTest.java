package com.ganera.core.ganadero;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Descifrado de la contraseña OVZ para el WORKER (T4): actor SISTEMA, misma gestoría que el trabajo. */
@SpringBootTest
class DescifradoCredencialOvzWorkerTest {

    private static final String PLANO = "password-ovz-worker-inventada";
    private static final String CIFRADO_CORRUPTO = Base64.getEncoder().encodeToString(
            "ciphertext-corrupto-inventado-para-el-test-worker-0123".getBytes(StandardCharsets.UTF_8));

    @Autowired private DescifradoCredencialOvz servicio;
    @Autowired private CifradorAesGcm cifrador;
    @Autowired private GanaderoRepository ganaderoRepository;
    @Autowired private GestoriaRepository gestoriaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Ganadero ganaderoA;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria worker A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria worker B"));
        ganaderoA = new Ganadero();
        ganaderoA.setGestoria(gestoriaA);
        ganaderoA.setNombre("Titular worker A");
        ganaderoA.setNif("66666666K");
        ganaderoA.setOvzUsuario("usuario-ovz-worker");
        ganaderoA.setOvzPasswordCifrada(cifrador.cifrar(PLANO));
        ganaderoA = ganaderoRepository.save(ganaderoA);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        ganaderoRepository.deleteAllInBatch();
        gestoriaRepository.deleteAll();
    }

    @Test
    void descifraYDejaUnaFilaDeSistemaSinLaContrasena() {
        CredencialOvzEnClaro claro = servicio.descifrarPasswordParaWorker(ganaderoA, gestoriaA.getId());

        assertThat(claro.valor()).isEqualTo(PLANO);
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "select gestoria_id, actor_tipo, usuario_id, entidad_tipo, entidad_id, antes, despues "
                        + "from auditoria where accion = 'CREDENCIAL_OVZ_DESCIFRADA'");
        assertThat(fila.get("gestoria_id")).asString().isEqualTo(String.valueOf(gestoriaA.getId()));
        assertThat(fila.get("actor_tipo")).isEqualTo("SISTEMA");
        assertThat(fila.get("usuario_id")).isNull();
        assertThat(fila.get("entidad_tipo")).isEqualTo("GANADERO");
        assertThat(fila.get("entidad_id")).asString().isEqualTo(String.valueOf(ganaderoA.getId()));
        assertThat(fila.get("antes")).isNull();
        assertThat(fila.get("despues")).isNull();
    }

    @Test
    void deOtraGestoriaLanzaYNoDejaFila() {
        assertThatThrownBy(() -> servicio.descifrarPasswordParaWorker(ganaderoA, gestoriaB.getId()))
                .isInstanceOf(CredencialOvzDeOtraGestoriaException.class);
        assertThat(filas()).isZero();
    }

    @Test
    void conCifradoCorruptoLaFilaQuedaYLanzaSinElValor() {
        jdbcTemplate.update("update ganadero set ovz_password_cifrada = ? where id = ?",
                CIFRADO_CORRUPTO, ganaderoA.getId());
        Ganadero recargado = ganaderoRepository.findById(ganaderoA.getId()).orElseThrow();

        assertThatThrownBy(() -> servicio.descifrarPasswordParaWorker(recargado, gestoriaA.getId()))
                .isInstanceOf(DescifradoCredencialOvzException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CIFRADO_CORRUPTO));
        assertThat(filas()).isEqualTo(1);
    }

    private Integer filas() {
        return jdbcTemplate.queryForObject(
                "select count(*) from auditoria where accion = 'CREDENCIAL_OVZ_DESCIFRADA'", Integer.class);
    }
}
