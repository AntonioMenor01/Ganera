package com.ganera.core.tramite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El modo del worker es SEGURO por defecto: solo el texto «REAL» (sin importar mayúsculas) presenta. */
class ModoWorkerOvzTest {

    @Test
    void nuloVacioODesconocidoEsEnsayo() {
        assertThat(ModoWorkerOvz.desde(null)).isEqualTo(ModoWorkerOvz.ENSAYO);
        assertThat(ModoWorkerOvz.desde("")).isEqualTo(ModoWorkerOvz.ENSAYO);
        assertThat(ModoWorkerOvz.desde("   ")).isEqualTo(ModoWorkerOvz.ENSAYO);
        assertThat(ModoWorkerOvz.desde("xxx")).isEqualTo(ModoWorkerOvz.ENSAYO);
        assertThat(ModoWorkerOvz.desde("true")).isEqualTo(ModoWorkerOvz.ENSAYO);
    }

    @Test
    void soloRealEsReal() {
        assertThat(ModoWorkerOvz.desde("REAL")).isEqualTo(ModoWorkerOvz.REAL);
        assertThat(ModoWorkerOvz.desde("real")).isEqualTo(ModoWorkerOvz.REAL);
        assertThat(ModoWorkerOvz.desde("ENSAYO")).isEqualTo(ModoWorkerOvz.ENSAYO);
    }

    @Test
    void elApplicationYmlDeProduccionDejaElModoEnEnsayo() throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load("application.yml", new FileSystemResource("src/main/resources/application.yml"));
        assertThat(String.valueOf(fuentes.get(0).getProperty("ganera.ovz.worker.modo"))).isEqualTo("ENSAYO");
    }
}
