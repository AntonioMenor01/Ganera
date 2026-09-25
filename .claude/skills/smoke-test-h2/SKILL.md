---
name: smoke-test-h2
description: Arranca el backend de Ganera con H2 en memoria para un smoke test manual cuando no hay Docker/Postgres disponible. Usa esto al final de cada paso para verificar end-to-end, no solo compilación.
---

Desde `backend/`, siempre con el Maven Wrapper (`mvn` no está en el PATH de esta máquina) y
`JAVA_HOME` apuntando a un JDK 21+ SOLO para este comando (el `JAVA_HOME` global es un JDK 17 de
otro proyecto — nunca lo cambies globalmente):

JAVA_HOME="/c/Program Files/Java/jdk-23" ./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.arguments='--spring.datasource.url=jdbc:h2:mem:ganera;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.flyway.enabled=true --spring.jpa.hibernate.ddl-auto=none'

- `useTestClasspath=true` es obligatorio: H2 es una dependencia de test-scope.
- Flyway activo + `ddl-auto=none`: se aplican las migraciones reales (`V1`...) contra H2, igual que
  en producción, en vez de dejar que Hibernate genere el esquema. Nunca uses `ddl-auto=update`.
- Antes de lanzarlo, exporta `JWT_SECRET`, `ENCRYPTION_KEY` (base64 de 32 bytes) y
  `ONBOARDING_SECRET`. Las variables de Stripe/Twilio pueden quedarse en blanco.
- Si hace falta sembrar datos desde otro proceso (p.ej. `org.h2.tools.RunScript`), usa una H2 en
  fichero con `AUTO_SERVER=TRUE` en vez de `mem:`.

Recuerda: esto valida la lógica de negocio y las migraciones contra H2 en modo PostgreSQL, pero NO
contra el motor real (Postgres). En Windows, matar el proceso requiere `taskkill`/`Stop-Process`
sobre el PID hijo de `java.exe`, no basta con parar la shell.
