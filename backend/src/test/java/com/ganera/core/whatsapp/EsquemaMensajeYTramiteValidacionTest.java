package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Las migraciones reales (Flyway hasta V19) frente a las entidades con ddl-auto=validate: si una
 * columna nueva de V19 no cuadra con su campo, o vuelve el @Lob de MensajeCampo.cuerpo (que en H2
 * no valida contra la columna TEXT), el contexto no levanta y este test falla.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = NONE)
class EsquemaMensajeYTramiteValidacionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void elEsquemaDeFlywayValidaContraLasEntidadesYV19EstaAplicada() {
        List<Map<String, Object>> v19 = jdbcTemplate.queryForList(
                "select \"success\" from \"flyway_schema_history\" where \"version\" = '19'");
        assertThat(v19).singleElement().satisfies(fila -> assertThat(fila.get("success")).isEqualTo(true));
    }

    @Test
    void lasFilasAntiguasQuedanConValoresPorDefectoEnLasColumnasNuevas() {
        Map<String, Object> columnas = jdbcTemplate.queryForMap("""
                select
                  (select column_default from information_schema.columns
                    where lower(table_name) = 'tramite' and lower(column_name) = 'intentos_extraccion') as intentos,
                  (select is_nullable from information_schema.columns
                    where lower(table_name) = 'tramite' and lower(column_name) = 'origen') as origen_nullable,
                  (select column_default from information_schema.columns
                    where lower(table_name) = 'mensaje_campo' and lower(column_name) = 'num_media') as num_media,
                  (select is_nullable from information_schema.columns
                    where lower(table_name) = 'mensaje_campo' and lower(column_name) = 'resultado') as resultado_nullable
                """);
        assertThat(String.valueOf(columnas.get("INTENTOS"))).isEqualTo("0");
        assertThat(columnas.get("ORIGEN_NULLABLE")).isEqualTo("YES");
        assertThat(String.valueOf(columnas.get("NUM_MEDIA"))).isEqualTo("0");
        assertThat(columnas.get("RESULTADO_NULLABLE")).isEqualTo("YES");
    }
}
