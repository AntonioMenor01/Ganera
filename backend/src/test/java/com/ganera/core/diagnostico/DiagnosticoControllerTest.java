package com.ganera.core.diagnostico;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** El controlador exige el X-Internal-Secret (fail-closed) antes de hacer nada. */
class DiagnosticoControllerTest {

    private static final DiagnosticoAlcanceResponse FIJA = DiagnosticoAlcanceResponse.conCodigo(200);

    private DiagnosticoController controlador(String secretoConfigurado) {
        DiagnosticoAlcanceService servicioFalso = new DiagnosticoAlcanceService("http://no-se-usa/", 1000) {
            @Override
            public DiagnosticoAlcanceResponse comprobar() {
                return FIJA;
            }
        };
        return new DiagnosticoController(servicioFalso, secretoConfigurado);
    }

    @Test
    void sinSecretoDevuelve401() {
        assertThat(controlador("s3cr3t").ovzAlcance(null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void conSecretoIncorrectoDevuelve401() {
        assertThat(controlador("s3cr3t").ovzAlcance("otro").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void sinSecretoConfiguradoFallaCerrado401() {
        assertThat(controlador("").ovzAlcance("loquesea").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void conSecretoCorrectoDevuelveElResultadoDelServicio() {
        ResponseEntity<DiagnosticoAlcanceResponse> resp = controlador("s3cr3t").ovzAlcance("s3cr3t");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(FIJA);
    }
}
