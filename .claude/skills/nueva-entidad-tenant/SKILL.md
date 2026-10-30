---
name: nueva-entidad-tenant
description: Procedimiento para añadir una entidad nueva multi-tenant en Ganera (que extiende GestoriaScopedEntity). Usa esto cuando el usuario pida crear una tabla/entidad nueva ligada a una Gestoría.
---

Al crear una entidad que pertenece a una Gestoría:
1. Extiende `GestoriaScopedEntity`, nunca añadas `gestoria_id` a mano.
2. Crea la migración Flyway correspondiente (nunca uses `ddl-auto` para producción).
3. Verifica que el filtro Hibernate `gestoriaFilter` se activa sobre la nueva
   entidad — no lo desactives ni lo bypasses bajo ningún concepto.
4. **Toda consulta lleva el `gestoriaId` EXPLÍCITO** (`findByIdAndGestoriaId`,
   `findByGestoriaId…`), nunca te fíes solo del filtro ambiente `gestoriaFilter`.
   Un `findById(id)` sobre una `GestoriaScopedEntity` con un id que venga de la
   ruta o del cuerpo (controlado por el cliente) es un **hallazgo bloqueante**:
   el filtro Hibernate NO se aplica a las cargas por clave primaria (footgun
   documentado en `CLAUDE.md`). Las únicas consultas sin `gestoriaId` permitidas
   son las deliberadamente sin tenant de los webhooks públicos (sin
   `Authentication`), y están enumeradas y justificadas en `CLAUDE.md`.
5. Añade el test correspondiente comprobando el aislamiento entre gestorías
   (una Gestoría no debe ver filas de otra).
6. **Cada escritura de la entidad deja su fila de auditoría** (`AuditoriaService`,
   paquete `shared/auditoria`) **y su entrada en el inventario cerrado**
   (`InventarioAuditoriaTest`): una ruta de escritura, `@Scheduled` o `@Modifying`
   nuevo sin declarar en el inventario pone la suite en rojo, y la guardia DML
   exige que toda escritura de negocio no exenta lleve su fila. Decide si la
   nueva escritura es auditada (con su test) o exenta (con su razón, p. ej.
   pre-tenant).