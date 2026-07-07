---
name: smoke-test-h2
description: Arranca el backend de Ganera con H2 en memoria para un smoke test manual cuando no hay Docker/Postgres disponible. Usa esto al final de cada paso para verificar end-to-end, no solo compilación.
---

Comando exacto (requiere useTestClasspath=true porque H2 es test-scope):

mvn spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.arguments='--spring.datasource.url=jdbc:h2:mem:ganera;MODE=PostgreSQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=update'

Antes de lanzarlo, exporta JWT_SECRET, ENCRYPTION_KEY y ONBOARDING_SECRET.
Recuerda: esto valida la lógica de negocio pero NO las migraciones de Flyway
contra el motor real (Postgres). En Windows, matar el proceso requiere
taskkill/Stop-Process sobre el PID hijo de java.exe, no basta con parar la shell.