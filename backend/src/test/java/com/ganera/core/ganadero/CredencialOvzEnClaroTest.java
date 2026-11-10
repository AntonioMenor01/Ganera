package com.ganera.core.ganadero;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T3 de M2: el descifrado no devuelve un String desnudo, sino {@link CredencialOvzEnClaro}, cuyo
 * {@code toString()} esta redactado para que la contrasena no se cuele en un log, una traza o un mensaje de
 * error por accidente. El valor en claro solo se obtiene llamando explicitamente al getter.
 */
class CredencialOvzEnClaroTest {

    private static final String PLANO = "password-ovz-en-claro-inventada";

    @Test
    void elGetterDevuelveElValorEnClaro() {
        assertThat(new CredencialOvzEnClaro(PLANO).valor()).isEqualTo(PLANO);
    }

    @Test
    void toStringNoContieneElValorEnClaro() {
        CredencialOvzEnClaro credencial = new CredencialOvzEnClaro(PLANO);

        assertThat(credencial.toString()).doesNotContain(PLANO);
        assertThat(credencial.toString()).isEqualTo("CredencialOvzEnClaro[REDACTADO]");
    }
}
