package com.ganera.core.tramite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elige el proveedor de IA activo para la extraccion y lo expone como el unico bean de
 * {@link TramiteExtractionService}. Se construye solo el proveedor elegido (el otro ni se instancia),
 * asi que:
 * <ul>
 *   <li>{@link ExtraccionTramiteService} sigue inyectando {@code TramiteExtractionService} por tipo,
 *       sin tocarlo;</li>
 *   <li>no hace falta {@code @Primary}, que chocaria con la IA falsa de los tests
 *       ({@code IaDePruebaConfig.IaProgramable}, tambien {@code @Primary}).</li>
 * </ul>
 *
 * <p>El proveedor se elige con la variable de entorno {@code AI_PROVIDER}
 * ({@code anthropic} | {@code mistral}); por defecto {@code anthropic}. Un valor ausente o no
 * reconocido usa {@code anthropic} y se registra en el log (ver {@link ProveedorIa#resolver}).
 */
@Configuration
public class ProveedorIaConfig {

    private static final Logger log = LoggerFactory.getLogger(ProveedorIaConfig.class);

    @Bean
    public TramiteExtractionService tramiteExtractionService(
            @Value("${ganera.ai.provider:anthropic}") String proveedorConfigurado,
            @Value("${ganera.anthropic.api-key:}") String anthropicApiKey,
            @Value("${ganera.anthropic.base-url:}") String anthropicBaseUrl,
            @Value("${ganera.anthropic.max-reintentos:2}") int anthropicMaxReintentos,
            @Value("${ganera.mistral.api-key:}") String mistralApiKey,
            @Value("${ganera.mistral.base-url:}") String mistralBaseUrl,
            @Value("${ganera.mistral.model:" + MistralTramiteExtractionService.MODELO_POR_DEFECTO + "}") String mistralModelo,
            @Value("${ganera.mistral.max-reintentos:2}") int mistralMaxReintentos) {

        ProveedorIa.Seleccion seleccion = ProveedorIa.resolver(proveedorConfigurado);
        if (!seleccion.reconocido()) {
            log.warn("AI_PROVIDER='{}' no es un proveedor de IA reconocido; se usa {}.",
                    seleccion.valorOriginal(), seleccion.proveedor().name().toLowerCase());
        }

        return switch (seleccion.proveedor()) {
            case MISTRAL -> {
                log.info("Proveedor de IA activo para la extracción: mistral (modelo {})", mistralModelo);
                yield new MistralTramiteExtractionService(mistralApiKey, mistralBaseUrl, mistralModelo, mistralMaxReintentos);
            }
            case ANTHROPIC -> {
                log.info("Proveedor de IA activo para la extracción: anthropic (modelo {})",
                        AnthropicTramiteExtractionService.MODELO);
                yield new AnthropicTramiteExtractionService(anthropicApiKey, anthropicBaseUrl, anthropicMaxReintentos);
            }
        };
    }
}
