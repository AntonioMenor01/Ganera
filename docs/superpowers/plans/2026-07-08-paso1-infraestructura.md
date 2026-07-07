# Paso 1 — Infraestructura del Monorepo Ganera — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconstruir el andamiaje completo de Ganera (monorepo `/backend` + `/frontend` + `docker-compose.yml`) tal como lo describe `ganera-prompts.md` (Prompt 1, líneas 62-116) y `CLAUDE.md`, con las correcciones ya cerradas históricamente (Contacto `UNIQUE(telefono)` global, credenciales OVZ en `Ganadero`, cifrado real desde el día uno, git inicializado). Sin lógica de negocio (extracción IA, resolución de trámites, ejecución OVZ) — solo estructura, esqueleto compilable, y los mecanismos transversales (multi-tenancy, cifrado, JWT) que si no se ponen ahora, se vuelven muy caros de retrofitear después.

**Architecture:** Monolito modular Spring Boot (paquetes por contexto: `gestoria`, `ganadero`, `explotacion`, `contacto`, `tramite`, `facturacion`, `whatsapp`, `ovz`, `shared/{tenant,security,crypto}`) + SPA React separada. Multi-tenancy row-level vía `gestoria_id` + Hibernate Filters, activados por un interceptor que lee el `Authentication` ya puesto en contexto por un filtro JWT. Todas las migraciones vía Flyway, nunca `ddl-auto` en real (solo se permite en el smoke test H2 puntual).

**Tech Stack:** Java 21, Spring Boot 3.4.x, Maven, PostgreSQL, Flyway, Spring Security + `jjwt` 0.12.x, Spring AI (`spring-ai-anthropic` 1.0.3), Twilio SDK, Stripe SDK (`stripe-java`), Playwright (Java), Lombok; Vite + React 19 + TypeScript + Tailwind v4 (`@tailwindcss/vite`) + shadcn/ui (preset "nova").

## Global Constraints

- Backend build tool: **Maven**, no Gradle.
- Java 21, Spring Boot **3.4+**. Modelo IA fijado al snapshot exacto `claude-haiku-4-5-20251001` (nunca el alias flotante).
- Multi-tenancy: row-level `gestoria_id` + Hibernate Filters (`GestoriaScopedEntity`, filtro `gestoriaFilter`) — nunca bypassed para entidades que extiendan `GestoriaScopedEntity`.
- Migraciones de esquema: **Flyway únicamente**, nunca `ddl-auto` fuera del smoke test H2 puntual.
- `Contacto.telefono` es **`UNIQUE` a secas** (no `UNIQUE(gestoria_id, telefono)`) — un único número de WhatsApp de Twilio compartido entre gestorías, así que el teléfono no puede depender de saber ya el `gestoria_id`.
- Credenciales OVZ.net (`ovzUsuario`, `ovzPasswordCifrada`) viven en **`Ganadero`**, no en `Explotacion` — un Ganadero tiene un único login que cubre todas sus explotaciones.
- `ovzPasswordCifrada` se cifra con AES-256-GCM vía `AttributeConverter` (`EncryptedStringConverter`); la clave viene de `ENCRYPTION_KEY` — **nunca hardcodeada, nunca un fallback inseguro**: el arranque debe fallar explícitamente si falta o tiene el tamaño incorrecto.
- `Tramite.estado` (`EstadoTramite`) tiene exactamente 7 valores: `PENDIENTE_EXTRACCION, PENDIENTE_REVISION, APROBADO, EN_PROCESO, EJECUTADO_OVZ, ERROR_OVZ, RECHAZADO`. No existe un estado separado para "explotación ambigua" — ese caso es `PENDIENTE_REVISION` con `explotacion = null`.
- `MensajeCampo` **no** extiende `GestoriaScopedEntity` — al recibir el webhook no se conoce aún la Gestoría. `gestoria_id`/`contacto`/`tramite` son nullable.
- `MensajeCampo.message_sid` tiene constraint `UNIQUE` — idempotencia del webhook de Twilio.
- Ningún trámite se ejecuta contra OVZ.net sin aprobación humana explícita — no aplica todavía en este paso (no hay lógica de negocio), pero ningún esqueleto debe insinuar un atajo.
- No mockees la deserialización de SDKs externos (Stripe `Event`/Session, Twilio `RequestValidator`) en tests — separa la lógica de decisión pura en métodos package-private testeables con tipos simples (skill `test-sin-mocks-externos`).
- Al crear una entidad multi-tenant: extiende `GestoriaScopedEntity` (nunca añadas `gestoria_id` a mano), crea su migración Flyway, verifica que `gestoriaFilter` se activa sobre ella, y añade un test de aislamiento entre gestorías (skill `nueva-entidad-tenant`).
- Verificación final de cada paso: smoke test con H2 en memoria (skill `smoke-test-h2`), no solo compilación.
- Lombok: declarar `annotationProcessorPaths` explícito para Lombok en `maven-compiler-plugin` — la combinación JDK/Maven de este entorno rompe el descubrimiento implícito por classpath (footgun ya conocido, ver `CLAUDE.md`).
- Cualquier cliente de integración opcional (Twilio, Stripe) debe tolerar credencial en blanco en el arranque — constrúyelo por request/lazy, nunca en un constructor/campo estático que se ejecute al boot (footgun de Twilio ya conocido y corregido una vez; no repetirlo).

---

## Task 1: Esqueleto Maven + docker-compose + configuración base

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/ganera/core/GaneraApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/resources/application.yml`
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `.gitignore`

**Interfaces:**
- Produces: proyecto Maven que compila (`mvn -f backend compile`), con todas las dependencias que las tareas siguientes usarán (Spring Web/Data JPA/Security/Validation, PostgreSQL driver, Flyway, `jjwt`, `spring-ai-anthropic`, Twilio SDK, Stripe SDK, Playwright, Lombok, H2/`spring-security-test` en test scope).

- [ ] **Step 1: Crear `backend/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <groupId>com.ganera</groupId>
    <artifactId>ganera-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>ganera-core</name>
    <description>Backend de Ganera</description>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.0.3</spring-ai.version>
        <jjwt.version>0.12.6</jjwt.version>
        <twilio.version>10.6.4</twilio.version>
        <stripe.version>28.2.0</stripe.version>
        <playwright.version>1.49.0</playwright.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-anthropic</artifactId>
        </dependency>

        <dependency>
            <groupId>com.twilio.sdk</groupId>
            <artifactId>twilio</artifactId>
            <version>${twilio.version}</version>
        </dependency>

        <dependency>
            <groupId>com.stripe</groupId>
            <artifactId>stripe-java</artifactId>
            <version>${stripe.version}</version>
        </dependency>

        <dependency>
            <groupId>com.microsoft.playwright</groupId>
            <artifactId>playwright</artifactId>
            <version>${playwright.version}</version>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <release>21</release>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.36</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Crear la clase principal**

`backend/src/main/java/com/ganera/core/GaneraApplication.java`:

```java
package com.ganera.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GaneraApplication {
    public static void main(String[] args) {
        SpringApplication.run(GaneraApplication.class, args);
    }
}
```

- [ ] **Step 3: Configuración base `application.yml`**

`backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: ganera-core
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/ganera}
    username: ${DATABASE_USERNAME:ganera}
    password: ${DATABASE_PASSWORD:ganera}
  jpa:
    open-in-view: true
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      chat:
        options:
          model: claude-haiku-4-5-20251001

ganera:
  jwt:
    secret: ${JWT_SECRET:}
    expiration-minutes: 480
  encryption:
    key: ${ENCRYPTION_KEY:}
  onboarding:
    secret: ${ONBOARDING_SECRET:}

twilio:
  account-sid: ${TWILIO_ACCOUNT_SID:}
  auth-token: ${TWILIO_AUTH_TOKEN:}

stripe:
  api-key: ${STRIPE_API_KEY:}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET:}
```

- [ ] **Step 4: Configuración de test**

`backend/src/test/resources/application.yml` (H2 en modo PostgreSQL, Flyway activo contra H2 para validar la forma de las migraciones en cada test, JWT/encryption/onboarding con valores dummy fijos para que los tests no dependan de variables de entorno del shell):

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:ganera_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    open-in-view: true
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
    locations: classpath:db/migration

ganera:
  jwt:
    secret: test-jwt-secret-de-al-menos-32-bytes-de-longitud
    expiration-minutes: 480
  encryption:
    key: 0123456789abcdef0123456789abcdef
  onboarding:
    secret: test-onboarding-secret

twilio:
  account-sid: ""
  auth-token: ""

stripe:
  api-key: ""
  webhook-secret: ""
```

- [ ] **Step 5: `docker-compose.yml` en la raíz**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ${DATABASE_NAME:-ganera}
      POSTGRES_USER: ${DATABASE_USERNAME:-ganera}
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD:-ganera}
    ports:
      - "5432:5432"
    volumes:
      - ganera_postgres_data:/var/lib/postgresql/data

volumes:
  ganera_postgres_data:
```

- [ ] **Step 6: `.env.example` en la raíz**

```
DATABASE_URL=jdbc:postgresql://localhost:5432/ganera
DATABASE_NAME=ganera
DATABASE_USERNAME=ganera
DATABASE_PASSWORD=ganera

JWT_SECRET=
ENCRYPTION_KEY=
ONBOARDING_SECRET=

ANTHROPIC_API_KEY=

TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=

STRIPE_API_KEY=
STRIPE_WEBHOOK_SECRET=
```

- [ ] **Step 7: `.gitignore` en la raíz**

```
target/
node_modules/
dist/
.env
*.iml
.idea/
.vscode/
*.log
.claude/settings.local.json
```

- [ ] **Step 8: Verificar que compila**

Run: `mvn -f backend -q compile`
Expected: `BUILD SUCCESS` (o sin salida, que es el modo `-q` en éxito).

- [ ] **Step 9: Commit**

```bash
git init
git add backend/pom.xml backend/src/main/java/com/ganera/core/GaneraApplication.java backend/src/main/resources/application.yml backend/src/test/resources/application.yml docker-compose.yml .env.example .gitignore CLAUDE.md ganera-prompts.md .claude docs
git commit -m "chore: esqueleto Maven inicial de ganera-core"
```

---

## Task 2: Cifrado de credenciales OVZ (`shared/crypto`)

**Files:**
- Create: `backend/src/main/java/com/ganera/core/shared/crypto/EncryptedStringConverter.java`
- Create: `backend/src/main/java/com/ganera/core/shared/crypto/EncryptionKeyHolder.java`
- Test: `backend/src/test/java/com/ganera/core/shared/crypto/EncryptedStringConverterTest.java`

**Interfaces:**
- Produces: `EncryptedStringConverter implements AttributeConverter<String, String>` — usable vía `@Convert(converter = EncryptedStringConverter.class)` en cualquier campo `String` que deba cifrarse en reposo. `EncryptionKeyHolder.requireKey(): SecretKey` — lanza `IllegalStateException` si `ganera.encryption.key` falta o no decodifica a 32 bytes.

- [ ] **Step 1: Escribir el test que falla**

```java
package com.ganera.core.shared.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    private static final String VALID_KEY_BASE64 =
            java.util.Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());

    @Test
    void cifraYDescifraDevuelveElValorOriginal() {
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> VALID_KEY_BASE64);

        String original = "mi-password-secreto-de-ovz";
        String cifrado = converter.convertToDatabaseColumn(original);

        assertThat(cifrado).isNotNull().isNotEqualTo(original);
        assertThat(converter.convertToEntityAttribute(cifrado)).isEqualTo(original);
    }

    @Test
    void cadaLlamadaProduceUnCifradoDistintoPorElIvAleatorio() {
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> VALID_KEY_BASE64);

        String cifrado1 = converter.convertToDatabaseColumn("mismo-valor");
        String cifrado2 = converter.convertToDatabaseColumn("mismo-valor");

        assertThat(cifrado1).isNotEqualTo(cifrado2);
    }

    @Test
    void nullSeMantieneNull() {
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> VALID_KEY_BASE64);

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void claveAusenteFallaExplicitamenteAlUsarla() {
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> "");

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("valor"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void claveDeTamanoIncorrectoFallaExplicitamente() {
        String claveCorta = java.util.Base64.getEncoder().encodeToString("demasiado-corta".getBytes());
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> claveCorta);

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("valor"))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -f backend -q test -Dtest=EncryptedStringConverterTest`
Expected: FAIL — no compila (`EncryptedStringConverter`, `EncryptionKeyHolder` no existen todavía).

- [ ] **Step 3: Implementar `EncryptionKeyHolder`**

```java
package com.ganera.core.shared.crypto;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;
import java.util.function.Supplier;

/** Deriva la SecretKey AES-256 desde la variable de entorno ganera.encryption.key en base64. */
public class EncryptionKeyHolder {

    private final Supplier<String> keySource;

    public EncryptionKeyHolder(Supplier<String> keySource) {
        this.keySource = keySource;
    }

    public Key requireKey() {
        String raw = keySource.get();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY no está configurada. La aplicación no puede arrancar sin ella.");
        }
        byte[] bytes = Base64.getDecoder().decode(raw);
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY debe decodificar a 32 bytes (AES-256), tiene " + bytes.length + ".");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
```

- [ ] **Step 4: Implementar `EncryptedStringConverter`**

```java
package com.ganera.core.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * Cifra/descifra de forma transparente un campo String con AES-256-GCM.
 * IV aleatorio de 12 bytes por valor, prefijado al texto cifrado antes de codificar en base64.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final EncryptionKeyHolder keyHolder;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedStringConverter(@Value("${ganera.encryption.key:}") String encryptionKey) {
        this.keyHolder = new EncryptionKeyHolder(() -> encryptionKey);
    }

    EncryptedStringConverter(Supplier<String> keySource) {
        this.keyHolder = new EncryptionKeyHolder(keySource);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            Key key = keyHolder.requireKey();
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCipherText = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, ivAndCipherText, 0, iv.length);
            System.arraycopy(cipherText, 0, ivAndCipherText, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(ivAndCipherText);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el valor", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            Key key = keyHolder.requireKey();
            byte[] ivAndCipherText = Base64.getDecoder().decode(dbData);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(ivAndCipherText, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] cipherText = new byte[ivAndCipherText.length - IV_LENGTH_BYTES];
            System.arraycopy(ivAndCipherText, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el valor", e);
        }
    }
}
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

Run: `mvn -f backend -q test -Dtest=EncryptedStringConverterTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/ganera/core/shared/crypto backend/src/test/java/com/ganera/core/shared/crypto
git commit -m "feat: cifrado AES-256-GCM real para campos sensibles (ENCRYPTION_KEY obligatoria)"
```

---

## Task 3: Multi-tenancy base — `Gestoria` + `GestoriaScopedEntity` + `Usuario`

**Files:**
- Create: `backend/src/main/java/com/ganera/core/shared/tenant/GestoriaScopedEntity.java`
- Create: `backend/src/main/java/com/ganera/core/gestoria/Gestoria.java`
- Create: `backend/src/main/java/com/ganera/core/gestoria/GestoriaRepository.java`
- Create: `backend/src/main/java/com/ganera/core/gestoria/Usuario.java`
- Create: `backend/src/main/java/com/ganera/core/gestoria/UsuarioRepository.java`
- Create: `backend/src/main/resources/db/migration/V1__create_gestoria.sql`
- Create: `backend/src/main/resources/db/migration/V2__create_usuario.sql`
- Test: `backend/src/test/java/com/ganera/core/gestoria/GestoriaFilterIsolationTest.java`

**Interfaces:**
- Consumes: nada de tareas anteriores (es la base del dominio).
- Produces: `GestoriaScopedEntity` (con `getId()`, `getGestoria()`, `setGestoria(Gestoria)`, `getCreatedAt()`) que las tareas 4, 6 extenderán. Filtro Hibernate `gestoriaFilter` con parámetro `gestoriaId` (Long), que la Tarea 8 activará vía interceptor.

- [ ] **Step 1: `GestoriaScopedEntity`**

```java
package com.ganera.core.shared.tenant;

import com.ganera.core.gestoria.Gestoria;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@FilterDef(name = "gestoriaFilter", parameters = @ParamDef(name = "gestoriaId", type = Long.class))
@Filter(name = "gestoriaFilter", condition = "gestoria_id = :gestoriaId")
public abstract class GestoriaScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gestoria_id", nullable = false, updatable = false)
    private Gestoria gestoria;

    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 2: `Gestoria` + repositorio**

```java
package com.ganera.core.gestoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "gestoria")
public class Gestoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private Instant createdAt = Instant.now();

    public Gestoria(String nombre) {
        this.nombre = nombre;
    }
}
```

```java
package com.ganera.core.gestoria;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GestoriaRepository extends JpaRepository<Gestoria, Long> {
}
```

- [ ] **Step 3: `Usuario` + repositorio**

```java
package com.ganera.core.gestoria;

import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Empleado de una Gestoria. Único que autentica en el sistema (ganaderos y contactos nunca lo hacen). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario")
public class Usuario extends GestoriaScopedEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;
}
```

```java
package com.ganera.core.gestoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
}
```

- [ ] **Step 4: Migraciones Flyway**

`backend/src/main/resources/db/migration/V1__create_gestoria.sql`:

```sql
CREATE TABLE gestoria (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

`backend/src/main/resources/db/migration/V2__create_usuario.sql`:

```sql
CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

> Nota: `email` es `UNIQUE` global (no compuesto con `gestoria_id`) — `POST /auth/login` (Prompt 2.5) solo recibe email+password sin indicar gestoría, así que dos gestorías no pueden tener un Usuario con el mismo email.

- [ ] **Step 5: Test de aislamiento (falla primero)**

```java
package com.ganera.core.gestoria;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class GestoriaFilterIsolationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    @Test
    void unaGestoriaNoVeUsuariosDeOtraCuandoElFiltroEstaActivo() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        usuarioRepository.save(nuevoUsuario(gestoriaA, "a@gestoriaA.com"));
        usuarioRepository.save(nuevoUsuario(gestoriaB, "b@gestoriaB.com"));

        entityManager.flush();
        entityManager.clear();

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("gestoriaFilter").setParameter("gestoriaId", gestoriaA.getId());

        List<Usuario> visibles = usuarioRepository.findAll();

        assertThat(visibles).hasSize(1);
        assertThat(visibles.get(0).getEmail()).isEqualTo("a@gestoriaA.com");
    }

    @Test
    void sinFiltroActivoSeVenTodasLasFilas() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        usuarioRepository.save(nuevoUsuario(gestoriaA, "a2@gestoriaA.com"));
        usuarioRepository.save(nuevoUsuario(gestoriaB, "b2@gestoriaB.com"));

        entityManager.flush();
        entityManager.clear();

        assertThat(usuarioRepository.findAll()).hasSize(2);
    }

    private static Usuario nuevoUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash("hash");
        usuario.setNombre("Usuario de prueba");
        return usuario;
    }
}
```

- [ ] **Step 6: Ejecutar y verificar que falla, luego pasa**

Run: `mvn -f backend -q test -Dtest=GestoriaFilterIsolationTest`
Expected primero: FAIL (clases no existen). Tras los steps 1-4: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/ganera/core/shared/tenant backend/src/main/java/com/ganera/core/gestoria backend/src/main/resources/db/migration/V1__create_gestoria.sql backend/src/main/resources/db/migration/V2__create_usuario.sql backend/src/test/java/com/ganera/core/gestoria
git commit -m "feat: Gestoria, Usuario y filtro Hibernate multi-tenant base"
```

---

## Task 4: `Ganadero` (con credenciales OVZ cifradas), `Explotacion`, `Animal`

**Files:**
- Create: `backend/src/main/java/com/ganera/core/ganadero/Ganadero.java`
- Create: `backend/src/main/java/com/ganera/core/ganadero/GanaderoRepository.java`
- Create: `backend/src/main/java/com/ganera/core/explotacion/Explotacion.java`
- Create: `backend/src/main/java/com/ganera/core/explotacion/ExplotacionRepository.java`
- Create: `backend/src/main/java/com/ganera/core/explotacion/Animal.java`
- Create: `backend/src/main/java/com/ganera/core/explotacion/AnimalRepository.java`
- Create: `backend/src/main/resources/db/migration/V3__create_ganadero.sql`
- Create: `backend/src/main/resources/db/migration/V4__create_explotacion.sql`
- Create: `backend/src/main/resources/db/migration/V5__create_animal.sql`
- Test: `backend/src/test/java/com/ganera/core/ganadero/GanaderoRepositoryTest.java`

**Interfaces:**
- Consumes: `GestoriaScopedEntity` (Task 3), `EncryptedStringConverter` (Task 2).
- Produces: `Ganadero.getOvzUsuario()/getOvzPasswordCifrada()` (texto plano en memoria, cifrado en columna); `Explotacion.getCodigoRega()`; `Animal.getCrotal()/getCrotalUltimosDigitos()` — usados por la Tarea 6 (Tramite referencia Explotacion) y por trabajo futuro de resolución de crotales.

- [ ] **Step 1: `Ganadero` + repositorio**

```java
package com.ganera.core.ganadero;

import com.ganera.core.shared.crypto.EncryptedStringConverter;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cliente de una Gestoria. Nunca autentica; su único canal es WhatsApp vía Contacto. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ganadero")
public class Ganadero extends GestoriaScopedEntity {

    @Column(nullable = false)
    private String nombre;

    @Column(name = "ovz_usuario")
    private String ovzUsuario;

    @Column(name = "ovz_password_cifrada")
    @Convert(converter = EncryptedStringConverter.class)
    private String ovzPasswordCifrada;
}
```

```java
package com.ganera.core.ganadero;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GanaderoRepository extends JpaRepository<Ganadero, Long> {
}
```

- [ ] **Step 2: `Explotacion` + repositorio**

```java
package com.ganera.core.explotacion;

import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "explotacion")
public class Explotacion extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ganadero_id", nullable = false)
    private Ganadero ganadero;

    @Column(name = "codigo_rega", nullable = false, unique = true)
    private String codigoRega;

    @Column(nullable = false)
    private String nombre;
}
```

```java
package com.ganera.core.explotacion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExplotacionRepository extends JpaRepository<Explotacion, Long> {
}
```

- [ ] **Step 3: `Animal` + repositorio**

```java
package com.ganera.core.explotacion;

import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "animal")
public class Animal extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "explotacion_id", nullable = false)
    private Explotacion explotacion;

    /** Crotal completo, formato ES123456789012. */
    @Column(nullable = false, unique = true)
    private String crotal;

    /** Últimos dígitos, indexados aparte porque es lo que el Contacto escribe por WhatsApp. */
    @Column(name = "crotal_ultimos_digitos", nullable = false)
    private String crotalUltimosDigitos;
}
```

```java
package com.ganera.core.explotacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnimalRepository extends JpaRepository<Animal, Long> {
    List<Animal> findByExplotacionIdAndCrotalUltimosDigitos(Long explotacionId, String crotalUltimosDigitos);
}
```

- [ ] **Step 4: Migraciones**

`backend/src/main/resources/db/migration/V3__create_ganadero.sql`:

```sql
CREATE TABLE ganadero (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    nombre VARCHAR(255) NOT NULL,
    ovz_usuario VARCHAR(255),
    ovz_password_cifrada VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_ganadero_gestoria_id ON ganadero(gestoria_id);
```

`backend/src/main/resources/db/migration/V4__create_explotacion.sql`:

```sql
CREATE TABLE explotacion (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    ganadero_id BIGINT NOT NULL REFERENCES ganadero(id),
    codigo_rega VARCHAR(50) NOT NULL UNIQUE,
    nombre VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_explotacion_gestoria_id ON explotacion(gestoria_id);
CREATE INDEX idx_explotacion_ganadero_id ON explotacion(ganadero_id);
```

`backend/src/main/resources/db/migration/V5__create_animal.sql`:

```sql
CREATE TABLE animal (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    explotacion_id BIGINT NOT NULL REFERENCES explotacion(id),
    crotal VARCHAR(20) NOT NULL UNIQUE,
    crotal_ultimos_digitos VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_animal_gestoria_id ON animal(gestoria_id);
CREATE INDEX idx_animal_explotacion_crotal_digitos ON animal(explotacion_id, crotal_ultimos_digitos);
```

- [ ] **Step 5: Test que falla primero (round-trip de cifrado a través de JPA + aislamiento)**

```java
package com.ganera.core.ganadero;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class GanaderoRepositoryTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void laPasswordDeOvzSeAlmacenaCifradaEnColumnaPeroSeLeeEnClaro() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba"));

        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero de prueba");
        ganadero.setOvzUsuario("usuario.ovz");
        ganadero.setOvzPasswordCifrada("password-en-claro");
        Ganadero guardado = ganaderoRepository.save(ganadero);

        entityManager.flush();
        entityManager.clear();

        String columnaCruda = jdbcTemplate.queryForObject(
                "SELECT ovz_password_cifrada FROM ganadero WHERE id = ?",
                String.class, guardado.getId());
        assertThat(columnaCruda).isNotEqualTo("password-en-claro");

        Ganadero releido = ganaderoRepository.findById(guardado.getId()).orElseThrow();
        assertThat(releido.getOvzPasswordCifrada()).isEqualTo("password-en-claro");
    }
}
```

- [ ] **Step 6: Ejecutar y verificar que falla, luego pasa**

Run: `mvn -f backend -q test -Dtest=GanaderoRepositoryTest`
Expected primero: FAIL. Tras steps 1-4: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/ganera/core/ganadero backend/src/main/java/com/ganera/core/explotacion backend/src/main/resources/db/migration/V3__create_ganadero.sql backend/src/main/resources/db/migration/V4__create_explotacion.sql backend/src/main/resources/db/migration/V5__create_animal.sql backend/src/test/java/com/ganera/core/ganadero
git commit -m "feat: Ganadero (credenciales OVZ cifradas), Explotacion y Animal"
```

---

## Task 5: `Contacto` (tenant-unscoped) + `ContactoExplotacion`

**Files:**
- Create: `backend/src/main/java/com/ganera/core/contacto/TipoContacto.java`
- Create: `backend/src/main/java/com/ganera/core/contacto/Contacto.java`
- Create: `backend/src/main/java/com/ganera/core/contacto/ContactoRepository.java`
- Create: `backend/src/main/java/com/ganera/core/contacto/ContactoExplotacion.java`
- Create: `backend/src/main/resources/db/migration/V6__create_contacto.sql`
- Create: `backend/src/main/resources/db/migration/V7__create_contacto_explotacion.sql`
- Test: `backend/src/test/java/com/ganera/core/contacto/ContactoRepositoryTest.java`

**Interfaces:**
- Consumes: `Explotacion` (Task 4).
- Produces: `ContactoRepository.findByTelefono(String): Optional<Contacto>` — lookup deliberadamente **sin** filtro de tenant (`Contacto` no extiende `GestoriaScopedEntity`). `ContactoExplotacion` queda como entidad sin repositorio (scaffolding inerte, igual que `UsuarioExplotacion` en un paso futuro) — no crear su repositorio en este paso.

- [ ] **Step 1: `TipoContacto`**

```java
package com.ganera.core.contacto;

public enum TipoContacto {
    TITULAR,
    TRABAJADOR
}
```

- [ ] **Step 2: `Contacto` + repositorio**

```java
package com.ganera.core.contacto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Quien escribe por WhatsApp. Deliberadamente NO extiende GestoriaScopedEntity:
 * al llegar un mensaje solo se conoce el teléfono, no la Gestoria — por eso
 * telefono es UNIQUE a secas (un único número de Twilio compartido entre
 * todas las gestorías) y la búsqueda por teléfono es la única forma de
 * resolver a qué Gestoria pertenece un mensaje entrante.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contacto")
public class Contacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String telefono;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoContacto tipo;

    private Instant createdAt = Instant.now();
}
```

```java
package com.ganera.core.contacto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContactoRepository extends JpaRepository<Contacto, Long> {
    Optional<Contacto> findByTelefono(String telefono);
}
```

- [ ] **Step 3: `ContactoExplotacion` (sin repositorio todavía)**

```java
package com.ganera.core.contacto;

import com.ganera.core.explotacion.Explotacion;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vínculo Contacto-Explotacion. Un titular puede tener varias filas (una
 * gestoría de varias explotaciones); un trabajador, exactamente una.
 * Entidad de scaffolding: sin repositorio propio todavía, igual que
 * UsuarioExplotacion — no construir lógica de filtrado encima sin que el
 * paso correspondiente lo pida explícitamente.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contacto_explotacion")
public class ContactoExplotacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "explotacion_id", nullable = false)
    private Explotacion explotacion;
}
```

- [ ] **Step 4: Migraciones**

`backend/src/main/resources/db/migration/V6__create_contacto.sql`:

```sql
CREATE TABLE contacto (
    id BIGSERIAL PRIMARY KEY,
    telefono VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(255) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

`backend/src/main/resources/db/migration/V7__create_contacto_explotacion.sql`:

```sql
CREATE TABLE contacto_explotacion (
    id BIGSERIAL PRIMARY KEY,
    contacto_id BIGINT NOT NULL REFERENCES contacto(id),
    explotacion_id BIGINT NOT NULL REFERENCES explotacion(id),
    UNIQUE (contacto_id, explotacion_id)
);
```

- [ ] **Step 5: Test que falla primero**

```java
package com.ganera.core.contacto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ContactoRepositoryTest {

    @Autowired
    private ContactoRepository contactoRepository;

    @Test
    void buscaPorTelefonoSinNecesidadDeConocerLaGestoriaDeAntemano() {
        Contacto contacto = new Contacto();
        contacto.setTelefono("+34600111222");
        contacto.setNombre("Titular de prueba");
        contacto.setTipo(TipoContacto.TITULAR);
        contactoRepository.save(contacto);

        Optional<Contacto> encontrado = contactoRepository.findByTelefono("+34600111222");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getTipo()).isEqualTo(TipoContacto.TITULAR);
    }

    @Test
    void telefonoEsUnicoGlobalmenteNoPorGestoria() {
        Contacto contacto = new Contacto();
        contacto.setTelefono("+34600333444");
        contacto.setNombre("Otro contacto");
        contacto.setTipo(TipoContacto.TRABAJADOR);
        contactoRepository.saveAndFlush(contacto);

        Contacto duplicado = new Contacto();
        duplicado.setTelefono("+34600333444");
        duplicado.setNombre("Intento duplicado");
        duplicado.setTipo(TipoContacto.TRABAJADOR);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> contactoRepository.saveAndFlush(duplicado));
    }
}
```

- [ ] **Step 6: Ejecutar y verificar que falla, luego pasa**

Run: `mvn -f backend -q test -Dtest=ContactoRepositoryTest`
Expected primero: FAIL. Tras steps 1-4: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/ganera/core/contacto backend/src/main/resources/db/migration/V6__create_contacto.sql backend/src/main/resources/db/migration/V7__create_contacto_explotacion.sql backend/src/test/java/com/ganera/core/contacto
git commit -m "feat: Contacto (unico por telefono, sin tenant) y ContactoExplotacion"
```

---

## Task 6: `Tramite` (7 estados) + `MensajeCampo` (idempotente por `message_sid`)

**Files:**
- Create: `backend/src/main/java/com/ganera/core/tramite/EstadoTramite.java`
- Create: `backend/src/main/java/com/ganera/core/tramite/TipoTramite.java`
- Create: `backend/src/main/java/com/ganera/core/tramite/Tramite.java`
- Create: `backend/src/main/java/com/ganera/core/tramite/TramiteRepository.java`
- Create: `backend/src/main/java/com/ganera/core/whatsapp/MensajeCampo.java`
- Create: `backend/src/main/java/com/ganera/core/whatsapp/MensajeCampoRepository.java`
- Create: `backend/src/main/resources/db/migration/V8__create_tramite.sql`
- Create: `backend/src/main/resources/db/migration/V9__create_mensaje_campo.sql`
- Test: `backend/src/test/java/com/ganera/core/tramite/TramiteRepositoryTest.java`
- Test: `backend/src/test/java/com/ganera/core/whatsapp/MensajeCampoRepositoryTest.java`

**Interfaces:**
- Consumes: `GestoriaScopedEntity`, `Explotacion`, `Contacto` (Tasks 3-5).
- Produces: `EstadoTramite` (7 valores exactos) y `Tramite.getExplotacion()` nullable — usados por toda la lógica de negocio futura (Prompt 3b/3c). `MensajeCampoRepository.existsByMessageSid(String): boolean` — usado por el webhook de Twilio en la Tarea 9.

- [ ] **Step 1: `EstadoTramite` y `TipoTramite`**

```java
package com.ganera.core.tramite;

public enum EstadoTramite {
    PENDIENTE_EXTRACCION,
    PENDIENTE_REVISION,
    APROBADO,
    EN_PROCESO,
    EJECUTADO_OVZ,
    ERROR_OVZ,
    RECHAZADO
}
```

```java
package com.ganera.core.tramite;

public enum TipoTramite {
    ALTA,
    BAJA,
    CENSO,
    MOVIMIENTO,
    DEMORA
}
```

- [ ] **Step 2: `Tramite` + repositorio**

```java
package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tramite")
public class Tramite extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    /** Null si el contacto tiene varias explotaciones y el mensaje no desambigua. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "explotacion_id")
    private Explotacion explotacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tramite")
    private TipoTramite tipoTramite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTramite estado = EstadoTramite.PENDIENTE_EXTRACCION;

    @Column(name = "motivo_error")
    private String motivoError;
}
```

```java
package com.ganera.core.tramite;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TramiteRepository extends JpaRepository<Tramite, Long> {
}
```

- [ ] **Step 3: `MensajeCampo` + repositorio**

```java
package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.tramite.Tramite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Mensaje crudo entrante de Twilio. NO extiende GestoriaScopedEntity: al
 * recibir el webhook aún no se conoce la Gestoria — gestoria/contacto/tramite
 * se rellenan una vez resuelto el Contacto por teléfono.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mensaje_campo")
public class MensajeCampo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_sid", nullable = false, unique = true)
    private String messageSid;

    @Column(name = "telefono_origen", nullable = false)
    private String telefonoOrigen;

    @Lob
    @Column(nullable = false)
    private String cuerpo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestoria_id")
    private Gestoria gestoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contacto_id")
    private Contacto contacto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tramite_id")
    private Tramite tramite;

    private Instant createdAt = Instant.now();
}
```

```java
package com.ganera.core.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeCampoRepository extends JpaRepository<MensajeCampo, Long> {
    boolean existsByMessageSid(String messageSid);
}
```

- [ ] **Step 4: Migraciones**

`backend/src/main/resources/db/migration/V8__create_tramite.sql`:

```sql
CREATE TABLE tramite (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL REFERENCES gestoria(id),
    contacto_id BIGINT NOT NULL REFERENCES contacto(id),
    explotacion_id BIGINT REFERENCES explotacion(id),
    tipo_tramite VARCHAR(20),
    estado VARCHAR(30) NOT NULL,
    motivo_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tramite_gestoria_id ON tramite(gestoria_id);
CREATE INDEX idx_tramite_estado ON tramite(estado);
```

`backend/src/main/resources/db/migration/V9__create_mensaje_campo.sql`:

```sql
CREATE TABLE mensaje_campo (
    id BIGSERIAL PRIMARY KEY,
    message_sid VARCHAR(100) NOT NULL UNIQUE,
    telefono_origen VARCHAR(30) NOT NULL,
    cuerpo TEXT NOT NULL,
    gestoria_id BIGINT REFERENCES gestoria(id),
    contacto_id BIGINT REFERENCES contacto(id),
    tramite_id BIGINT REFERENCES tramite(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

- [ ] **Step 5: Tests que fallan primero**

```java
package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.TipoContacto;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TramiteRepositoryTest {

    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private ContactoRepository contactoRepository;

    @Test
    void unTramitePuedeGuardarseSinExplotacionAsignadaAunSabiendoLaGestoria() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba"));

        Contacto contacto = new Contacto();
        contacto.setTelefono("+34600555666");
        contacto.setNombre("Titular ambiguo");
        contacto.setTipo(TipoContacto.TITULAR);
        contactoRepository.save(contacto);

        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        Tramite guardado = tramiteRepository.save(tramite);

        Tramite releido = tramiteRepository.findById(guardado.getId()).orElseThrow();
        assertThat(releido.getExplotacion()).isNull();
        assertThat(releido.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }
}
```

```java
package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class MensajeCampoRepositoryTest {

    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;

    @Test
    void unMessageSidDuplicadoNuncaCreaUnSegundoRegistro() {
        MensajeCampo primero = new MensajeCampo();
        primero.setMessageSid("SM123");
        primero.setTelefonoOrigen("+34600777888");
        primero.setCuerpo("Alta de 3 becerros crotal 1234");
        mensajeCampoRepository.saveAndFlush(primero);

        assertThat(mensajeCampoRepository.existsByMessageSid("SM123")).isTrue();

        MensajeCampo duplicado = new MensajeCampo();
        duplicado.setMessageSid("SM123");
        duplicado.setTelefonoOrigen("+34600777888");
        duplicado.setCuerpo("reintento de Twilio");

        assertThrows(DataIntegrityViolationException.class,
                () -> mensajeCampoRepository.saveAndFlush(duplicado));
    }
}
```

- [ ] **Step 6: Ejecutar y verificar que fallan, luego pasan**

Run: `mvn -f backend -q test -Dtest=TramiteRepositoryTest,MensajeCampoRepositoryTest`
Expected primero: FAIL. Tras steps 1-4: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/ganera/core/tramite backend/src/main/java/com/ganera/core/whatsapp backend/src/main/resources/db/migration/V8__create_tramite.sql backend/src/main/resources/db/migration/V9__create_mensaje_campo.sql backend/src/test/java/com/ganera/core/tramite backend/src/test/java/com/ganera/core/whatsapp
git commit -m "feat: Tramite (7 estados exactos) y MensajeCampo idempotente por message_sid"
```

---

## Task 7: JWT (`shared/security`)

**Files:**
- Create: `backend/src/main/java/com/ganera/core/shared/security/GaneraUserPrincipal.java`
- Create: `backend/src/main/java/com/ganera/core/shared/security/JwtService.java`
- Create: `backend/src/main/java/com/ganera/core/shared/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/ganera/core/shared/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/ganera/core/shared/security/JwtServiceTest.java`

**Interfaces:**
- Consumes: nada de tareas de dominio (es infraestructura transversal).
- Produces: `GaneraUserPrincipal(Long usuarioId, Long gestoriaId, String email)`; `JwtService.generarToken(GaneraUserPrincipal): String` y `JwtService.parsearToken(String): GaneraUserPrincipal` — usados por la Tarea 8 (`TenantFilterActivationInterceptor`) y por el futuro `POST /auth/login` (Prompt 2.5, no en este paso).

- [ ] **Step 1: `GaneraUserPrincipal`**

```java
package com.ganera.core.shared.security;

public record GaneraUserPrincipal(Long usuarioId, Long gestoriaId, String email) {
}
```

- [ ] **Step 2: Test de `JwtService` que falla primero**

```java
package com.ganera.core.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-de-al-menos-32-bytes-de-longitud";

    @Test
    void generaYParseaUnTokenValidoConservandoLosClaims() {
        JwtService jwtService = new JwtService(SECRET, 480);
        GaneraUserPrincipal principal = new GaneraUserPrincipal(1L, 2L, "empleado@gestoria.com");

        String token = jwtService.generarToken(principal);
        GaneraUserPrincipal parseado = jwtService.parsearToken(token);

        assertThat(parseado.usuarioId()).isEqualTo(1L);
        assertThat(parseado.gestoriaId()).isEqualTo(2L);
        assertThat(parseado.email()).isEqualTo("empleado@gestoria.com");
    }

    @Test
    void unTokenManipuladoSeRechaza() {
        JwtService jwtService = new JwtService(SECRET, 480);
        String token = jwtService.generarToken(new GaneraUserPrincipal(1L, 2L, "a@b.com"));
        String tokenManipulado = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parsearToken(tokenManipulado)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unTokenFirmadoConOtroSecretSeRechaza() {
        JwtService emisor = new JwtService(SECRET, 480);
        JwtService verificador = new JwtService("otro-secreto-completamente-distinto-de-32-bytes", 480);

        String token = emisor.generarToken(new GaneraUserPrincipal(1L, 2L, "a@b.com"));

        assertThatThrownBy(() -> verificador.parsearToken(token)).isInstanceOf(RuntimeException.class);
    }
}
```

- [ ] **Step 3: Ejecutar y verificar que falla**

Run: `mvn -f backend -q test -Dtest=JwtServiceTest`
Expected: FAIL (`JwtService` no existe).

- [ ] **Step 4: Implementar `JwtService`**

```java
package com.ganera.core.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${ganera.jwt.secret}") String secret,
            @Value("${ganera.jwt.expiration-minutes}") long expirationMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET no está configurada. La aplicación no puede arrancar sin ella.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generarToken(GaneraUserPrincipal principal) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(principal.usuarioId()))
                .claim("gestoriaId", principal.gestoriaId())
                .claim("email", principal.email())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(Duration.ofMinutes(expirationMinutes))))
                .signWith(signingKey)
                .compact();
    }

    public GaneraUserPrincipal parsearToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new GaneraUserPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get("gestoriaId", Long.class),
                claims.get("email", String.class));
    }
}
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

Run: `mvn -f backend -q test -Dtest=JwtServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 6: `JwtAuthenticationFilter`**

```java
package com.ganera.core.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                GaneraUserPrincipal principal = jwtService.parsearToken(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 7: `SecurityConfig`**

```java
package com.ganera.core.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(401)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

> Nota: `/auth/login` y `/internal/**` se declaran públicos aquí a nivel de Spring Security desde ya (así lo hicieron los pasos históricos), aunque los controladores concretos (`POST /auth/login`, `POST /internal/onboarding/gestoria`) se implementan en el Prompt 2.5, no en este paso.

- [ ] **Step 8: Verificar que el módulo compila junto al resto**

Run: `mvn -f backend -q compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/ganera/core/shared/security backend/src/test/java/com/ganera/core/shared/security
git commit -m "feat: infraestructura JWT (GaneraUserPrincipal, JwtService, filtro, SecurityConfig)"
```

---

## Task 8: Activación del filtro multi-tenant por request (`TenantFilterActivationInterceptor`)

**Files:**
- Create: `backend/src/main/java/com/ganera/core/shared/tenant/TenantFilterActivationInterceptor.java`
- Create: `backend/src/main/java/com/ganera/core/shared/tenant/WebMvcTenantConfig.java`
- Test: `backend/src/test/java/com/ganera/core/shared/tenant/TenantFilterActivationInterceptorTest.java`

**Interfaces:**
- Consumes: `GaneraUserPrincipal` (Task 7), `gestoriaFilter` (Task 3).
- Produces: activación automática del filtro Hibernate en cada request autenticada; ninguna activación en requests sin `Authentication` (webhooks) — comportamiento intencional, no un hueco de seguridad.

- [ ] **Step 1: Test que falla primero**

Verifica el interceptor de forma aislada (sin levantar todo Spring MVC): dado un `Session` de Hibernate y una `Authentication` en el `SecurityContext`, el filtro se activa con el `gestoriaId` correcto; sin `Authentication`, no se activa (no lanza excepción).

```java
package com.ganera.core.shared.tenant;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TenantFilterActivationInterceptorTest {

    @Autowired
    private EntityManager entityManager;

    private final TenantFilterActivationInterceptor interceptor = new TenantFilterActivationInterceptor();

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activaElFiltroConElGestoriaIdDelPrincipalAutenticado() throws Exception {
        GaneraUserPrincipal principal = new GaneraUserPrincipal(1L, 42L, "empleado@gestoria.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        interceptor.preHandle(null, null, null);

        Session session = entityManager.unwrap(Session.class);
        assertThat(session.getEnabledFilter("gestoriaFilter")).isNotNull();
        assertThat(session.getEnabledFilter("gestoriaFilter").getParameter("gestoriaId")).isEqualTo(42L);
    }

    @Test
    void noActivaNadaSinAutenticacionYNoLanzaExcepcion() throws Exception {
        interceptor.preHandle(null, null, null);

        Session session = entityManager.unwrap(Session.class);
        assertThat(session.getEnabledFilter("gestoriaFilter")).isNull();
    }
}
```

Esta prueba necesita acceso al `EntityManager`/`Session` desde el interceptor — inyéctalo vía `EntityManager` con `@PersistenceContext`.

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -f backend -q test -Dtest=TenantFilterActivationInterceptorTest`
Expected: FAIL (`TenantFilterActivationInterceptor` no existe).

- [ ] **Step 3: Implementar `TenantFilterActivationInterceptor`**

```java
package com.ganera.core.shared.tenant;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantFilterActivationInterceptor implements HandlerInterceptor {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof GaneraUserPrincipal principal) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("gestoriaFilter").setParameter("gestoriaId", principal.gestoriaId());
        }

        return true;
    }
}
```

- [ ] **Step 4: Registrar el interceptor en Spring MVC**

```java
package com.ganera.core.shared.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcTenantConfig implements WebMvcConfigurer {

    private final TenantFilterActivationInterceptor tenantFilterActivationInterceptor;

    public WebMvcTenantConfig(TenantFilterActivationInterceptor tenantFilterActivationInterceptor) {
        this.tenantFilterActivationInterceptor = tenantFilterActivationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterActivationInterceptor);
    }
}
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

Run: `mvn -f backend -q test -Dtest=TenantFilterActivationInterceptorTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/ganera/core/shared/tenant backend/src/test/java/com/ganera/core/shared/tenant
git commit -m "feat: activacion automatica del filtro gestoriaFilter por request autenticada"
```

---

## Task 9: Esqueletos de integraciones (IA, Twilio, Stripe, Playwright) + frontend + cierre

**Files:**
- Create: `backend/src/main/java/com/ganera/core/tramite/TramiteExtractionService.java`
- Create: `backend/src/main/java/com/ganera/core/tramite/AnthropicTramiteExtractionService.java`
- Create: `backend/src/main/java/com/ganera/core/whatsapp/TwilioWebhookController.java`
- Test: `backend/src/test/java/com/ganera/core/whatsapp/TwilioWebhookIdempotencyTest.java`
- Create: `backend/src/main/java/com/ganera/core/facturacion/StripeWebhookController.java`
- Test: `backend/src/test/java/com/ganera/core/facturacion/StripeWebhookControllerTest.java`
- Create: `backend/src/main/java/com/ganera/core/ovz/OvzAutomationService.java`
- Create: `backend/src/main/java/com/ganera/core/ovz/PlaywrightOvzAutomationService.java`
- Create: `frontend/` (scaffold Vite completo, ver steps)
- Modify: `docs/superpowers/plans/2026-07-08-paso1-infraestructura.md` (marcar tareas completadas, esto mismo)

**Interfaces:**
- Consumes: `MensajeCampoRepository` (Task 6), `Ganadero`/`Tramite` (Tasks 4, 6) para las firmas de `OvzAutomationService`.
- Produces: cierre completo del Paso 1 — nada que otras tareas de este plan consuman (es la última).

- [ ] **Step 1: `TramiteExtractionService` (interfaz) + implementación esqueleto**

```java
package com.ganera.core.tramite;

/**
 * Abstrae el proveedor de IA usado para extraer {tipoTramite, ultimosDigitosCrotales}
 * de un mensaje de WhatsApp en lenguaje natural. Sin lógica de prompt todavía
 * (Prompt 3b) — solo la interfaz y un esqueleto que compila.
 */
public interface TramiteExtractionService {
    TramiteExtraido extraer(String mensajeOriginal);

    record TramiteExtraido(TipoTramite tipoTramite, java.util.List<String> ultimosDigitosCrotales) {
    }
}
```

```java
package com.ganera.core.tramite;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AnthropicTramiteExtractionService implements TramiteExtractionService {

    private final ChatClient chatClient;

    public AnthropicTramiteExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public TramiteExtraido extraer(String mensajeOriginal) {
        throw new UnsupportedOperationException(
                "Extraccion de tramites via IA pendiente de implementar (Prompt 3b)");
    }
}
```

- [ ] **Step 2: `TwilioWebhookController` (validación de firma per-request, guardado idempotente)**

Separamos la decisión de idempotencia (pura, testeable) de la parte que toca el SDK de Twilio (`RequestValidator`), siguiendo la skill `test-sin-mocks-externos`.

```java
package com.ganera.core.whatsapp;

import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

@RestController
public class TwilioWebhookController {

    private final String authToken;
    private final MensajeCampoRepository mensajeCampoRepository;

    public TwilioWebhookController(
            @Value("${twilio.auth-token:}") String authToken,
            MensajeCampoRepository mensajeCampoRepository) {
        this.authToken = authToken;
        this.mensajeCampoRepository = mensajeCampoRepository;
    }

    @PostMapping(value = "/webhooks/twilio/whatsapp", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> recibirMensaje(
            HttpServletRequest request,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String firma,
            @RequestParam("MessageSid") String messageSid,
            @RequestParam("From") String from,
            @RequestParam("Body") String body) {

        // RequestValidator se construye por request (no en el constructor / campo estático):
        // con TWILIO_AUTH_TOKEN en blanco (aun sin cuenta de Twilio configurada), el SDK
        // lanza IllegalArgumentException si se construye al arrancar la app.
        if (!authToken.isBlank()) {
            RequestValidator validator = new RequestValidator(authToken);
            Map<String, String> params = new TreeMap<>();
            params.put("MessageSid", messageSid);
            params.put("From", from);
            params.put("Body", body);
            boolean firmaValida = firma != null
                    && validator.validate(request.getRequestURL().toString(), params, firma);
            if (!firmaValida) {
                return ResponseEntity.status(403).build();
            }
        }

        if (debeGuardarNuevoMensaje(messageSid)) {
            MensajeCampo mensaje = new MensajeCampo();
            mensaje.setMessageSid(messageSid);
            mensaje.setTelefonoOrigen(from);
            mensaje.setCuerpo(body);
            mensajeCampoRepository.save(mensaje);
        }

        return ResponseEntity.ok().build();
    }

    /** Lógica de idempotencia pura, sin tocar el SDK de Twilio: testeable sin mocks. */
    boolean debeGuardarNuevoMensaje(String messageSid) {
        return !mensajeCampoRepository.existsByMessageSid(messageSid);
    }
}
```

- [ ] **Step 3: Test de idempotencia (sin mockear Twilio)**

```java
package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TwilioWebhookIdempotencyTest {

    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;

    @Test
    void unMessageSidYaGuardadoNoDebeVolverAGuardarse() {
        TwilioWebhookController controller = new TwilioWebhookController("", mensajeCampoRepository);

        assertThat(controller.debeGuardarNuevoMensaje("SM999")).isTrue();

        MensajeCampo existente = new MensajeCampo();
        existente.setMessageSid("SM999");
        existente.setTelefonoOrigen("+34600000000");
        existente.setCuerpo("mensaje original");
        mensajeCampoRepository.saveAndFlush(existente);

        assertThat(controller.debeGuardarNuevoMensaje("SM999")).isFalse();
    }
}
```

> Nota: este test usa `@DataJpaTest`, que no expone un `ApplicationContext` web completo — es intencional, ya que solo probamos `debeGuardarNuevoMensaje`. La validación de firma y el flujo HTTP completo (`RequestValidator`) se cubren en el smoke test H2 del Step 10, no aquí, siguiendo la skill `test-sin-mocks-externos`.

- [ ] **Step 4: `StripeWebhookController` (validación de firma, sin lógica de negocio)**

```java
package com.ganera.core.facturacion;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private final String webhookSecret;

    public StripeWebhookController(@Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Void> recibirEvento(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String firma) {

        Event evento;
        try {
            evento = Webhook.constructEvent(payload, firma, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().build();
        }

        // Sin logica de negocio de facturacion todavia (Prompt 2.7): solo se
        // confirma la recepcion del evento verificado.
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 5: Test de verificación de firma (llamando al SDK real, sin mocks)**

```java
package com.ganera.core.facturacion;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StripeWebhookControllerTest {

    @Test
    void unaFirmaInvalidaEsRechazadaPorElSdkRealDeStripe() {
        String payload = "{\"id\":\"evt_test\"}";
        String secreto = "whsec_test_secret";
        String firmaInvalida = "t=1,v1=firma-que-no-encaja";

        assertThrows(SignatureVerificationException.class,
                () -> Webhook.constructEvent(payload, firmaInvalida, secreto));
    }
}
```

- [ ] **Step 6: `OvzAutomationService` (interfaz) + esqueleto Playwright**

```java
package com.ganera.core.ovz;

import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.tramite.Tramite;

/**
 * Automatizacion de OVZ.net via Playwright. Sin logica real todavia
 * (bloqueado hasta explorar la estructura real de OVZ.net, Prompt 3a/3c).
 */
public interface OvzAutomationService {
    void sincronizarInventarioInicial(Ganadero ganadero);

    void ejecutarTramite(Tramite tramite);
}
```

```java
package com.ganera.core.ovz;

import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.tramite.Tramite;
import org.springframework.stereotype.Service;

@Service
public class PlaywrightOvzAutomationService implements OvzAutomationService {

    @Override
    public void sincronizarInventarioInicial(Ganadero ganadero) {
        throw new UnsupportedOperationException(
                "Sincronizacion de inventario OVZ pendiente de implementar (Prompt 3a)");
    }

    @Override
    public void ejecutarTramite(Tramite tramite) {
        throw new UnsupportedOperationException(
                "Ejecucion de tramites OVZ pendiente de implementar (Prompt 3c)");
    }
}
```

- [ ] **Step 7: Verificar que el backend completo compila y los tests pasan**

Run: `mvn -f backend -q test`
Expected: `BUILD SUCCESS`, todos los tests de las Tareas 2-9 en verde.

- [ ] **Step 8: Scaffold del frontend**

```bash
cd frontend-scaffold-tmp 2>/dev/null || true
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npx shadcn@latest init -t vite -p nova -y
npm install react-router-dom axios
npm install -D tailwindcss @tailwindcss/vite
```

Crear/editar los archivos de estructura por feature:

`frontend/src/shared/api/httpClient.ts`:

```typescript
import axios from "axios";

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
});

httpClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("ganera_jwt");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

`frontend/src/shared/layout/AppLayout.tsx`:

```tsx
import { Outlet } from "react-router-dom";

export function AppLayout() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b px-6 py-4">
        <span className="font-semibold">Ganera</span>
      </header>
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  );
}
```

`frontend/src/router.tsx`:

```tsx
import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "@/shared/layout/AppLayout";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/tramites" replace /> },
      { path: "tramites", element: <div>Cola de tramites (pendiente)</div> },
      { path: "ganaderos", element: <div>Ganaderos (pendiente)</div> },
      { path: "explotaciones", element: <div>Explotaciones (pendiente)</div> },
      { path: "facturacion", element: <div>Facturacion (pendiente)</div> },
    ],
  },
]);
```

`frontend/src/main.tsx` (reemplazar el generado por defecto):

```tsx
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { router } from "./router";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
);
```

Crear las carpetas vacías de feature (con un `.gitkeep` para que git las trackee):

```bash
mkdir -p frontend/src/features/tramites frontend/src/features/ganaderos frontend/src/features/explotaciones frontend/src/features/facturacion
touch frontend/src/features/tramites/.gitkeep frontend/src/features/ganaderos/.gitkeep frontend/src/features/explotaciones/.gitkeep frontend/src/features/facturacion/.gitkeep
```

- [ ] **Step 9: Verificar que el frontend compila**

Run: `cd frontend && npm run build`
Expected: build de Vite exitoso (`dist/` generado sin errores de TypeScript).

- [ ] **Step 10: Smoke test H2 (skill `smoke-test-h2`)**

```bash
export JWT_SECRET="dev-secret-temporal-de-al-menos-32-bytes"
export ENCRYPTION_KEY="MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
export ONBOARDING_SECRET="dev-onboarding-secret"

mvn -f backend spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.arguments='--spring.datasource.url=jdbc:h2:mem:ganera;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.flyway.enabled=true --spring.jpa.hibernate.ddl-auto=none'
```

Expected: arranque limpio, las 9 migraciones (`V1`-`V9`) se aplican sin error contra H2 en modo PostgreSQL, sin excepciones de Hibernate ni de Spring Security al construir el contexto. Detener el proceso (`taskkill` sobre el PID hijo `java.exe` en Windows, según la skill).

- [ ] **Step 11: Actualizar `ganera-prompts.md`**

Añadir bajo "Prompt 1 — Infraestructura" una nota de estado: reconstrucción completada en la fecha de hoy, sin backend/frontend previos que reutilizar, siguiendo este plan (`docs/superpowers/plans/2026-07-08-paso1-infraestructura.md`).

- [ ] **Step 12: Commit final**

```bash
git add frontend docs/superpowers/plans/2026-07-08-paso1-infraestructura.md ganera-prompts.md backend/src/main/java/com/ganera/core/tramite/TramiteExtractionService.java backend/src/main/java/com/ganera/core/tramite/AnthropicTramiteExtractionService.java backend/src/main/java/com/ganera/core/whatsapp/TwilioWebhookController.java backend/src/test/java/com/ganera/core/whatsapp/TwilioWebhookIdempotencyTest.java backend/src/main/java/com/ganera/core/facturacion backend/src/test/java/com/ganera/core/facturacion backend/src/main/java/com/ganera/core/ovz
git commit -m "feat: esqueletos IA/Twilio/Stripe/Playwright + scaffold frontend — cierre Paso 1"
```

---

## Self-Review

**Cobertura del spec (Prompt 1, líneas 62-116 de `ganera-prompts.md`, más correcciones):**
1. Estructura monorepo (`/backend`, `/frontend`, paquetes por contexto) → Tasks 1, 3-9.
2. BD + multi-tenancy (Flyway, `gestoria_id`, Hibernate Filters, 7 estados de `Tramite`, `motivo_error`, `message_sid` UNIQUE) → Tasks 3, 4, 5, 6, 8.
3. Seguridad JWT esqueleto → Task 7.
4. Spring AI Anthropic + `TramiteExtractionService` → Task 1 (dependencia/config), Task 9 (interfaz+esqueleto).
5. Twilio (dependencia, controlador, validación de firma, guardado idempotente) → Task 1, Task 9.
6. Stripe (dependencia, controlador, validación de firma) → Task 1, Task 9.
7. Playwright (`ovz`, `OvzAutomationService` esqueleto) → Task 1, Task 9.
8. Frontend (Vite/React/TS/Tailwind/shadcn, estructura por feature, httpClient, sin pantallas) → Task 9.
9. `docker-compose.yml` con Postgres + `.env` → Task 1.
10. Correcciones históricas: `UNIQUE(telefono)` a secas → Task 5; credenciales OVZ en `Ganadero` → Task 4; cifrado AES-256-GCM real desde el inicio → Task 2; git init + primer commit → Task 1 (init) y commits en cada tarea.

Sin huecos detectados.

**Escaneo de placeholders:** revisado — no quedan "TBD"/"implementar más tarde" salvo los `UnsupportedOperationException` de `TramiteExtractionService`/`OvzAutomationService`, que son exactamente lo que el propio Prompt 1 pide ("solo el esqueleto... sin lógica de prompt/ejecución todavía"), no placeholders de plan.

**Consistencia de tipos:** `GaneraUserPrincipal(usuarioId, gestoriaId, email)` se usa igual en `JwtService` (Task 7), `JwtAuthenticationFilter` (Task 7) y `TenantFilterActivationInterceptor` (Task 8). `EstadoTramite`/`TipoTramite` se definen una vez (Task 6) y no se redefinen en ninguna tarea posterior. `MensajeCampoRepository.existsByMessageSid` se define en Task 6 y se consume tal cual en Task 9 sin cambiar de nombre.
