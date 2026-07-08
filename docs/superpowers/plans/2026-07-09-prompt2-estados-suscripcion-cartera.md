# Prompt 2 — Estados de Suscripción + Cartera Opcional — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar el modelo de datos que falta tras el Paso 1: los 6 estados de `Suscripcion`, el gate de aprobación `SuscripcionService.puedeAprobarTramites(gestoriaId)` (fail-closed), y el scaffolding inerte de cartera opcional (`Gestoria.modoCartera` + `UsuarioExplotacion`). Sin lógica de negocio conectada a ningún endpoint todavía — eso es Prompt 2.5 (login/onboarding) y Prompt 2.7 (Stripe).

**Architecture:** `Suscripcion` es una entidad nueva en el paquete `facturacion`, tenant-scoped (extiende `GestoriaScopedEntity`, una por Gestoría, `UNIQUE(gestoria_id)`). `SuscripcionService` es un servicio plano sin controlador. `UsuarioExplotacion` es scaffolding inerte que replica exactamente el patrón ya usado por `ContactoExplotacion` en el Paso 1 (entidad JPA simple, sin extender `GestoriaScopedEntity`, sin repositorio).

**Tech Stack:** Igual que el Paso 1 — Java 21, Spring Boot 3.4.x, Maven (vía `backend/mvnw`), PostgreSQL/Flyway, JUnit 5 + AssertJ, H2 en memoria para tests.

## Global Constraints

- Backend build tool: **Maven**, vía `backend/mvnw` (no `mvn` en el PATH del sistema). `JAVA_HOME` debe apuntar a `C:\Program Files\Java\jdk-23` solo para el comando en curso — nunca modificar la variable de entorno global (usada por otro proyecto no relacionado). Fallback si `mvnw` falla: Maven cacheado en `C:\Users\Antonio\.m2\wrapper\dists\apache-maven-3.9.9-bin\4nf9hui3q3djbarqar9g711ggc\apache-maven-3.9.9\bin`.
- Migraciones de esquema: **Flyway únicamente**, nunca `ddl-auto`. El repo ya tiene `V1`-`V9` (Paso 1) — las migraciones de este plan empiezan en **`V10`**.
- `EstadoSuscripcion` tiene **exactamente 6 valores**: `TRIAL, TRIAL_EXPIRADO_SIN_PAGO, ACTIVA, IMPAGO_GRACIA, SUSPENDIDA, CANCELADA`.
- `SuscripcionService.puedeAprobarTramites(gestoriaId)`: `false` si el estado es `TRIAL_EXPIRADO_SIN_PAGO` o `SUSPENDIDA`; `true` en cualquier otro estado; **`false` si no existe ninguna `Suscripcion` para esa Gestoría** (fail-closed — decisión ya cerrada, no es una ambigüedad a resolver).
- No conectar `puedeAprobarTramites` a ningún endpoint REST en este plan — es lógica de servicio pura, sin controlador.
- `Suscripcion` NO debe llevar todavía `stripeSubscriptionId` ni `explotacionesContratadas` — esos campos los añaden prompts futuros (2.5 y 2.7 respectivamente) vía sus propias migraciones. Añadirlos aquí sería construir por adelantado sin que este prompt lo pida.
- `UsuarioExplotacion` debe replicar el patrón de `ContactoExplotacion` (`backend/src/main/java/com/ganera/core/contacto/ContactoExplotacion.java`): entidad JPA plana, `@ManyToOne` a `Usuario` y `Explotacion`, **sin extender `GestoriaScopedEntity`**, **sin repositorio** — scaffolding inerte para una fase futura de filtrado por cartera, no lógica a completar ahora.
- No tocar `TenantFilterActivationInterceptor`, `JwtService`, `SecurityConfig`, ni crear `POST /auth/login` — eso es Prompt 2.5, explícitamente fuera de alcance aquí.
- Al crear una entidad multi-tenant (`Suscripcion`): extiende `GestoriaScopedEntity` (nunca añadas `gestoria_id` a mano), crea su migración Flyway, y añade un test de aislamiento entre gestorías (skill `nueva-entidad-tenant`).
- Verificación final: smoke test con H2 en memoria (skill `smoke-test-h2`), no solo compilación.

---

## Task 1: `EstadoSuscripcion` + `Suscripcion` + gate de aprobación

**Files:**
- Create: `backend/src/main/java/com/ganera/core/facturacion/EstadoSuscripcion.java`
- Create: `backend/src/main/java/com/ganera/core/facturacion/Suscripcion.java`
- Create: `backend/src/main/java/com/ganera/core/facturacion/SuscripcionRepository.java`
- Create: `backend/src/main/java/com/ganera/core/facturacion/SuscripcionService.java`
- Create: `backend/src/main/resources/db/migration/V10__create_suscripcion.sql`
- Test: `backend/src/test/java/com/ganera/core/facturacion/SuscripcionServiceTest.java`
- Test: `backend/src/test/java/com/ganera/core/facturacion/SuscripcionFilterIsolationTest.java`

**Interfaces:**
- Consumes: `GestoriaScopedEntity` (`backend/src/main/java/com/ganera/core/shared/tenant/GestoriaScopedEntity.java`, Paso 1 Task 3), `Gestoria`/`GestoriaRepository` (Paso 1 Task 3).
- Produces: `EstadoSuscripcion` (6 valores) y `SuscripcionRepository.findByGestoriaId(Long): Optional<Suscripcion>` — usados por Prompt 2.5 (onboarding manual crea una `Suscripcion` en `ACTIVA`) y Prompt 2.7 (Stripe). `SuscripcionService.puedeAprobarTramites(Long): boolean` — usado por un futuro endpoint de aprobación de trámites (no en este plan).

- [ ] **Step 1: `EstadoSuscripcion`**

```java
package com.ganera.core.facturacion;

public enum EstadoSuscripcion {
    TRIAL,
    TRIAL_EXPIRADO_SIN_PAGO,
    ACTIVA,
    IMPAGO_GRACIA,
    SUSPENDIDA,
    CANCELADA
}
```

- [ ] **Step 2: `Suscripcion` + repositorio**

```java
package com.ganera.core.facturacion;

import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una Suscripcion por Gestoria (UNIQUE(gestoria_id)). Sin stripeSubscriptionId
 * ni explotacionesContratadas todavia -- los añaden Prompt 2.5 y 2.7. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "suscripcion")
public class Suscripcion extends GestoriaScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSuscripcion estado;
}
```

```java
package com.ganera.core.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    Optional<Suscripcion> findByGestoriaId(Long gestoriaId);
}
```

- [ ] **Step 3: Migración Flyway**

`backend/src/main/resources/db/migration/V10__create_suscripcion.sql`:

```sql
CREATE TABLE suscripcion (
    id BIGSERIAL PRIMARY KEY,
    gestoria_id BIGINT NOT NULL UNIQUE REFERENCES gestoria(id),
    estado VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

> Nota: `gestoria_id` es `UNIQUE` a secas (no solo `NOT NULL`) — una Gestoría tiene como máximo una Suscripcion.

- [ ] **Step 4: Test que falla primero — los 7 casos**

```java
package com.ganera.core.facturacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(SuscripcionService.class)
class SuscripcionServiceTest {

    private static final Set<EstadoSuscripcion> ESTADOS_QUE_BLOQUEAN =
            EnumSet.of(EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO, EstadoSuscripcion.SUSPENDIDA);

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private SuscripcionService suscripcionService;

    @ParameterizedTest
    @EnumSource(EstadoSuscripcion.class)
    void puedeAprobarTramitesSegunElEstado(EstadoSuscripcion estado) {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba " + estado));

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(estado);
        suscripcionRepository.save(suscripcion);

        boolean esperado = !ESTADOS_QUE_BLOQUEAN.contains(estado);

        assertThat(suscripcionService.puedeAprobarTramites(gestoria.getId())).isEqualTo(esperado);
    }

    @Test
    void sinSuscripcionAsociadaNoPuedeAprobarTramitesFailClosed() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin suscripcion"));

        assertThat(suscripcionService.puedeAprobarTramites(gestoria.getId())).isFalse();
    }
}
```

> Este test cubre los 7 casos: uno por cada uno de los 6 valores de `EstadoSuscripcion` (vía `@ParameterizedTest`/`@EnumSource`) más el caso fail-closed sin `Suscripcion`.

- [ ] **Step 5: Ejecutar y verificar que falla**

Run: `cd backend && JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw -q test -Dtest=SuscripcionServiceTest`
Expected: FAIL — `EstadoSuscripcion`, `Suscripcion`, `SuscripcionRepository`, `SuscripcionService` no existen todavía.

- [ ] **Step 6: Implementar `SuscripcionService`**

```java
package com.ganera.core.facturacion;

import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class SuscripcionService {

    private static final Set<EstadoSuscripcion> ESTADOS_QUE_BLOQUEAN_APROBACION =
            EnumSet.of(EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO, EstadoSuscripcion.SUSPENDIDA);

    private final SuscripcionRepository suscripcionRepository;

    public SuscripcionService(SuscripcionRepository suscripcionRepository) {
        this.suscripcionRepository = suscripcionRepository;
    }

    public boolean puedeAprobarTramites(Long gestoriaId) {
        return suscripcionRepository.findByGestoriaId(gestoriaId)
                .map(suscripcion -> !ESTADOS_QUE_BLOQUEAN_APROBACION.contains(suscripcion.getEstado()))
                .orElse(false);
    }
}
```

- [ ] **Step 7: Ejecutar y verificar que pasa**

Run: `cd backend && JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw -q test -Dtest=SuscripcionServiceTest`
Expected: PASS (7 tests: 6 parametrizados + 1 fail-closed).

- [ ] **Step 8: Test de aislamiento entre gestorías (skill `nueva-entidad-tenant`)**

`Suscripcion` extiende `GestoriaScopedEntity`, así que necesita su propio test de aislamiento, con el mismo patrón que `GestoriaFilterIsolationTest` del Paso 1 (`backend/src/test/java/com/ganera/core/gestoria/GestoriaFilterIsolationTest.java`): habilitar el filtro `gestoriaFilter` a mano en la sesión y comprobar que una Gestoría no ve las `Suscripcion` de otra, y que sin filtro activo se ven todas.

```java
package com.ganera.core.facturacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
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
class SuscripcionFilterIsolationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    @Test
    void unaGestoriaNoVeSuscripcionesDeOtraCuandoElFiltroEstaActivo() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        suscripcionRepository.save(nuevaSuscripcion(gestoriaA, EstadoSuscripcion.ACTIVA));
        suscripcionRepository.save(nuevaSuscripcion(gestoriaB, EstadoSuscripcion.ACTIVA));

        entityManager.flush();
        entityManager.clear();

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("gestoriaFilter").setParameter("gestoriaId", gestoriaA.getId());

        List<Suscripcion> visibles = suscripcionRepository.findAll();

        assertThat(visibles).hasSize(1);
        assertThat(visibles.get(0).getGestoria().getId()).isEqualTo(gestoriaA.getId());
    }

    @Test
    void sinFiltroActivoSeVenTodasLasSuscripciones() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A2"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B2"));

        suscripcionRepository.save(nuevaSuscripcion(gestoriaA, EstadoSuscripcion.TRIAL));
        suscripcionRepository.save(nuevaSuscripcion(gestoriaB, EstadoSuscripcion.TRIAL));

        entityManager.flush();
        entityManager.clear();

        assertThat(suscripcionRepository.findAll()).hasSize(2);
    }

    private static Suscripcion nuevaSuscripcion(Gestoria gestoria, EstadoSuscripcion estado) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(estado);
        return suscripcion;
    }
}
```

- [ ] **Step 9: Ejecutar y verificar que el test de aislamiento pasa**

Run: `cd backend && JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw -q test -Dtest=SuscripcionFilterIsolationTest`
Expected: PASS (2 tests).

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/ganera/core/facturacion/EstadoSuscripcion.java backend/src/main/java/com/ganera/core/facturacion/Suscripcion.java backend/src/main/java/com/ganera/core/facturacion/SuscripcionRepository.java backend/src/main/java/com/ganera/core/facturacion/SuscripcionService.java backend/src/main/resources/db/migration/V10__create_suscripcion.sql backend/src/test/java/com/ganera/core/facturacion/SuscripcionServiceTest.java backend/src/test/java/com/ganera/core/facturacion/SuscripcionFilterIsolationTest.java
git commit -m "feat: EstadoSuscripcion (6 estados) y gate puedeAprobarTramites fail-closed"
```

---

## Task 2: `Gestoria.modoCartera` + `UsuarioExplotacion` (scaffolding inerte) + cierre

**Files:**
- Modify: `backend/src/main/java/com/ganera/core/gestoria/Gestoria.java`
- Create: `backend/src/main/java/com/ganera/core/gestoria/UsuarioExplotacion.java`
- Create: `backend/src/main/resources/db/migration/V11__add_modo_cartera_to_gestoria.sql`
- Create: `backend/src/main/resources/db/migration/V12__create_usuario_explotacion.sql`
- Test: `backend/src/test/java/com/ganera/core/gestoria/GestoriaModoCarteraTest.java`

**Interfaces:**
- Consumes: `Gestoria` (Paso 1 Task 3), `Usuario` (Paso 1 Task 3), `Explotacion` (Paso 1 Task 4).
- Produces: `Gestoria.isModoCartera()/setModoCartera(boolean)`, `UsuarioExplotacion` (entidad, sin repositorio) — ambos usados por una fase futura de filtrado por cartera, no en este plan.

- [ ] **Step 1: Test que falla primero**

```java
package com.ganera.core.gestoria;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class GestoriaModoCarteraTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void modoCarteraPorDefectoEsFalse() {
        Gestoria guardada = gestoriaRepository.save(new Gestoria("Gestoria por defecto"));

        entityManager.flush();
        entityManager.clear();

        Gestoria releida = gestoriaRepository.findById(guardada.getId()).orElseThrow();
        assertThat(releida.isModoCartera()).isFalse();
    }

    @Test
    void modoCarteraSePuedeActivarYPersiste() {
        Gestoria gestoria = new Gestoria("Gestoria con cartera");
        gestoria.setModoCartera(true);
        Gestoria guardada = gestoriaRepository.save(gestoria);

        entityManager.flush();
        entityManager.clear();

        Gestoria releida = gestoriaRepository.findById(guardada.getId()).orElseThrow();
        assertThat(releida.isModoCartera()).isTrue();
    }

    @Test
    void usuarioExplotacionSePersisteComoScaffoldingInerte() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria cartera"));

        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail("empleado@cartera.com");
        usuario.setPasswordHash("hash");
        usuario.setNombre("Empleado de cartera");
        usuarioRepository.save(usuario);

        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero de prueba");
        ganaderoRepository.save(ganadero);

        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega("ES-CARTERA-001");
        explotacion.setNombre("Explotacion de cartera");
        explotacionRepository.save(explotacion);

        UsuarioExplotacion vinculo = new UsuarioExplotacion();
        vinculo.setUsuario(usuario);
        vinculo.setExplotacion(explotacion);
        entityManager.persist(vinculo);

        entityManager.flush();
        entityManager.clear();

        UsuarioExplotacion releido = entityManager.find(UsuarioExplotacion.class, vinculo.getId());
        assertThat(releido.getUsuario().getId()).isEqualTo(usuario.getId());
        assertThat(releido.getExplotacion().getId()).isEqualTo(explotacion.getId());
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `cd backend && JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw -q test -Dtest=GestoriaModoCarteraTest`
Expected: FAIL — `Gestoria.isModoCartera()`/`setModoCartera(boolean)` y `UsuarioExplotacion` no existen todavía.

- [ ] **Step 3: Añadir `modoCartera` a `Gestoria`**

Modifica `backend/src/main/java/com/ganera/core/gestoria/Gestoria.java` añadiendo el campo (con Lombok `@Getter`/`@Setter` ya presentes en la clase, `isModoCartera()`/`setModoCartera` se generan solos):

```java
    @Column(name = "modo_cartera", nullable = false)
    private boolean modoCartera = false;
```

Añádelo justo después del campo `nombre` (antes de `createdAt`), manteniendo el resto de la clase intacto.

- [ ] **Step 4: `UsuarioExplotacion` (sin repositorio)**

```java
package com.ganera.core.gestoria;

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
 * Vinculo Usuario-Explotacion para la futura cartera opcional (modoCartera).
 * Entidad de scaffolding: sin repositorio propio todavia, igual que
 * ContactoExplotacion -- no construir logica de filtrado encima sin que el
 * paso correspondiente lo pida explicitamente.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario_explotacion")
public class UsuarioExplotacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "explotacion_id", nullable = false)
    private Explotacion explotacion;
}
```

- [ ] **Step 5: Migraciones Flyway**

`backend/src/main/resources/db/migration/V11__add_modo_cartera_to_gestoria.sql`:

```sql
ALTER TABLE gestoria ADD COLUMN modo_cartera BOOLEAN NOT NULL DEFAULT FALSE;
```

`backend/src/main/resources/db/migration/V12__create_usuario_explotacion.sql`:

```sql
CREATE TABLE usuario_explotacion (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    explotacion_id BIGINT NOT NULL REFERENCES explotacion(id),
    UNIQUE (usuario_id, explotacion_id)
);
```

- [ ] **Step 6: Ejecutar y verificar que pasa**

Run: `cd backend && JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw -q test -Dtest=GestoriaModoCarteraTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Ejecutar el backend completo**

Run: `cd backend && JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw -q test`
Expected: `BUILD SUCCESS`, todos los tests (Paso 1 + Task 1 + Task 2 de este plan) en verde.

- [ ] **Step 8: Smoke test H2 (skill `smoke-test-h2`)**

```bash
export JWT_SECRET="dev-secret-temporal-de-al-menos-32-bytes"
export ENCRYPTION_KEY="MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
export ONBOARDING_SECRET="dev-onboarding-secret"

cd backend
JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.arguments='--spring.datasource.url=jdbc:h2:mem:ganera;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.flyway.enabled=true --spring.jpa.hibernate.ddl-auto=none'
```

Expected: arranque limpio, las 12 migraciones (`V1`-`V12`) se aplican sin error contra H2 en modo PostgreSQL, sin excepciones de Hibernate/Spring Security al construir el contexto. Detener el proceso (`taskkill`/`Stop-Process` sobre el PID hijo `java.exe` en Windows — no basta con parar la shell).

- [ ] **Step 9: Actualizar `CLAUDE.md`**

En la sección "Current status", sustituye la frase "Steps 1, 2, 2.5, and 2.7 are complete" — si Prompt 2 ya estaba marcado como pendiente, actualízalo para reflejar que el modelo de `Suscripcion`/`EstadoSuscripcion`/`puedeAprobarTramites`/`modoCartera`/`UsuarioExplotacion` fue reconstruido y verificado en esta fecha, siguiendo `docs/superpowers/plans/2026-07-09-prompt2-estados-suscripcion-cartera.md`.

- [ ] **Step 10: Commit final**

```bash
git add backend/src/main/java/com/ganera/core/gestoria/Gestoria.java backend/src/main/java/com/ganera/core/gestoria/UsuarioExplotacion.java backend/src/main/resources/db/migration/V11__add_modo_cartera_to_gestoria.sql backend/src/main/resources/db/migration/V12__create_usuario_explotacion.sql backend/src/test/java/com/ganera/core/gestoria/GestoriaModoCarteraTest.java CLAUDE.md
git commit -m "feat: Gestoria.modoCartera y UsuarioExplotacion (scaffolding inerte) — cierre Prompt 2"
```

---

## Self-Review

**Cobertura del spec (Prompt 2 en `ganera-prompts.md` + referencias en `CLAUDE.md`):**
1. `EstadoSuscripcion` (6 valores exactos) → Task 1.
2. `SuscripcionService.puedeAprobarTramites(gestoriaId)`, fail-closed sin `Suscripcion`, sin conectar a ningún endpoint → Task 1.
3. `Gestoria.modoCartera` (default false) + `UsuarioExplotacion` (sin lógica de filtrado) → Task 2.

Sin huecos detectados.

**Escaneo de placeholders:** revisado — no quedan "TBD"/"implementar más tarde". `Suscripcion` deliberadamente NO incluye `stripeSubscriptionId`/`explotacionesContratadas` por diseño (ver Global Constraints), no es un placeholder olvidado.

**Consistencia de tipos:** `SuscripcionRepository.findByGestoriaId(Long): Optional<Suscripcion>` se define en Task 1 y se consume igual en `SuscripcionService`. `Gestoria.isModoCartera()/setModoCartera(boolean)` (generados por Lombok) se usan igual en el test de Task 2. `UsuarioExplotacion.setUsuario/setExplotacion` coinciden con los tipos `Usuario`/`Explotacion` ya existentes del Paso 1.
