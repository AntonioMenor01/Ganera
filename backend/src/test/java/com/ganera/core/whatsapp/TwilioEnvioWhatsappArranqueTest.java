package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque con las propiedades de la suite (Twilio todo vacio): la app arranca (el cliente de Twilio es
 * perezoso, nunca se crea en el constructor) y el envio real es el unico EnvioWhatsapp, sin configurar.
 * Asi ningun test que no importe EnvioWhatsappFalso puede llegar a llamar a Twilio: el endpoint
 * responde 503 antes de crear nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TwilioEnvioWhatsappArranqueTest {

    @Autowired
    private EnvioWhatsapp envioWhatsapp;
    @Autowired
    private org.springframework.boot.web.servlet.FilterRegistrationBean<LimiteCuerpoCallbackEstadoFilter> limiteCallback;

    @Test
    void conTwilioVacioArrancaYElEnvioRealNoEstaConfigurado() {
        assertThat(envioWhatsapp).isInstanceOf(TwilioEnvioWhatsapp.class);
        assertThat(envioWhatsapp.configurado()).isFalse();
        assertThat(((TwilioEnvioWhatsapp) envioWhatsapp).clienteCreado()).isFalse();
        // Sin TWILIO_STATUS_CALLBACK_URL el envio no pide callback (la respuesta se queda en ENVIADO).
        assertThat(((TwilioEnvioWhatsapp) envioWhatsapp).statusCallback()).isNull();
    }

    @Test
    void conUrlDeCallbackElEnvioLaPideYSinElClienteCreado() {
        TwilioEnvioWhatsapp envio = new TwilioEnvioWhatsapp("AC1", "token", "whatsapp:+14155238886",
                " https://ganera.example.test/webhooks/twilio/whatsapp/estado ");

        assertThat(envio.statusCallback())
                .isEqualTo(java.net.URI.create("https://ganera.example.test/webhooks/twilio/whatsapp/estado"));
        assertThat(envio.clienteCreado()).isFalse();
        assertThat(new TwilioEnvioWhatsapp("AC1", "token", "whatsapp:+14155238886", "no es una url").statusCallback())
                .isNull();
    }

    /** El tope de tamano del callback va antes de Spring Security y solo en su ruta exacta. */
    @Test
    void elFiltroDeTamanoDelCallbackVaAntesDeSpringSecurityYSoloEnSuRuta() {
        assertThat(limiteCallback.getOrder())
                .isLessThan(org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER);
        assertThat(limiteCallback.getUrlPatterns()).containsExactly("/webhooks/twilio/whatsapp/estado");
        assertThat(limiteCallback.getFilter()).isInstanceOf(LimiteCuerpoCallbackEstadoFilter.class);
    }

    @Test
    void elApplicationYmlDeProduccionLeeElRemitenteDeSuVariableSinValorPorDefecto() throws Exception {
        Path clases = Path.of(TwilioEnvioWhatsappArranqueTest.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        Path modulo = clases.getParent().getParent();
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(modulo.resolve("src/main/resources/application.yml")));
        Properties produccion = yaml.getObject();

        assertThat(produccion.getProperty("ganera.twilio.whatsapp-from")).isEqualTo("${TWILIO_WHATSAPP_FROM:}");
        assertThat(produccion.getProperty("twilio.account-sid")).isEqualTo("${TWILIO_ACCOUNT_SID:}");
        assertThat(produccion.getProperty("twilio.auth-token")).isEqualTo("${TWILIO_AUTH_TOKEN:}");
        assertThat(produccion.getProperty("ganera.twilio.status-callback-url"))
                .isEqualTo("${TWILIO_STATUS_CALLBACK_URL:}");
    }
}
