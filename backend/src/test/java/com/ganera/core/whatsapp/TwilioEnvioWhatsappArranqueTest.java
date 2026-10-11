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

    @Test
    void conTwilioVacioArrancaYElEnvioRealNoEstaConfigurado() {
        assertThat(envioWhatsapp).isInstanceOf(TwilioEnvioWhatsapp.class);
        assertThat(envioWhatsapp.configurado()).isFalse();
        assertThat(((TwilioEnvioWhatsapp) envioWhatsapp).clienteCreado()).isFalse();
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
    }
}
