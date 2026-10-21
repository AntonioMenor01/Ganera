package com.ganera.core.migracion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migracion a Supabase, T3: la CA de Supabase con la que el perfil clever valida el certificado del pooler
 * ({@code sslfactoryarg=classpath:supabase/ca-raiz.crt}) esta en src/main/resources y es de verdad. Se lee del DISCO:
 * en el classpath de los tests la tapa la CA de prueba (src/test/resources/supabase/ca-raiz.crt), asi que sin este
 * test podria faltar o ser la de prueba y ningun otro test lo veria; en Clever la app no arrancaria.
 */
class CaSupabaseTest {

    static final Path CA_SUPABASE = Path.of("src/main/resources/supabase/ca-raiz.crt");

    static final Path CA_PRUEBA = Path.of("src/test/resources/ssl-pruebas/ca-PRUEBA-solo-tests.crt");

    @Test
    void laCaDeSupabaseEsUnaCaVigenteDeSupabaseYNoLaDePrueba() throws Exception {
        comprobarCaDeSupabase(CA_SUPABASE);
    }

    /**
     * La comprobacion no es vacia: rechaza la CA de prueba (y su copia, la que tapa a la real en el classpath de los
     * tests). Sin esto, vaciar {@link #comprobarCaDeSupabase} dejaria el test de arriba en verde con cualquier fichero.
     */
    @Test
    void laComprobacionRechazaLaCaDePrueba() {
        for (Path deprueba : new Path[] {CA_PRUEBA, Path.of("src/test/resources/supabase/ca-raiz.crt")}) {
            assertThatThrownBy(() -> comprobarCaDeSupabase(deprueba)).as(deprueba.toString())
                    .isInstanceOf(AssertionError.class);
        }
    }

    /** La comprobacion, aparte para poder probarla con otro fichero. */
    static void comprobarCaDeSupabase(Path fichero) throws IOException, CertificateException {
        assertThat(fichero).as("La CA de Supabase (Database > SSL Configuration > Download certificate)").exists();
        X509Certificate ca = leer(fichero);

        assertThat(ca.getBasicConstraints()).as("es una CA (basicConstraints CA:TRUE)").isGreaterThanOrEqualTo(0);
        ca.checkValidity();
        assertThat(ca.getSubjectX500Principal().getName()).containsIgnoringCase("Supabase");
        assertThat(ca).as("no es la CA de prueba").isNotEqualTo(leer(CA_PRUEBA));
    }

    private static X509Certificate leer(Path fichero) throws IOException, CertificateException {
        try (InputStream entrada = Files.newInputStream(fichero)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(entrada);
        }
    }
}
