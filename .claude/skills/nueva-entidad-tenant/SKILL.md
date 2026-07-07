---
name: nueva-entidad-tenant
description: Procedimiento para añadir una entidad nueva multi-tenant en Ganera (que extiende GestoriaScopedEntity). Usa esto cuando el usuario pida crear una tabla/entidad nueva ligada a una Gestoría.
---

Al crear una entidad que pertenece a una Gestoría:
1. Extiende `GestoriaScopedEntity`, nunca añadas `gestoria_id` a mano.
2. Crea la migración Flyway correspondiente (nunca uses `ddl-auto` para producción).
3. Verifica que el filtro Hibernate `gestoriaFilter` se activa sobre la nueva
   entidad — no lo desactives ni lo bypasses bajo ningún concepto.
4. El repositorio no necesita añadir `gestoriaId` a las queries manualmente:
   el filtro ya lo hace. Si ves una query que lo añade a mano, pregunta si es
   necesario (probablemente sea un endpoint público sin Authentication, como
   los webhooks).
5. Añade el test correspondiente comprobando el aislamiento entre gestorías
   (una Gestoría no debe ver filas de otra).