# Ideas de retención / dependencia del producto (gestoría + ganadero)

Cinco ideas para que gestorías y ganaderos dependan de Ganera. Dos ya están **en producción**; las otras
tres son **backend** (de Clever/Antonio: no se despliegan desde el frontend de Vercel) y aquí quedan
diseñadas y listas para implementar. Cada una pensada para los **dos clientes**: la **gestoría** (único
que entra en la app) y el **ganadero** (nunca entra; su canal es WhatsApp a través de la gestoría).

Fecha: 2026-11-27.

---

## ✅ #2 — Avisos siempre visibles para la gestoría (HECHO Y DESPLEGADO)

**Campana de novedades** en la cabecera (`features/novedades`), visible desde cualquier pantalla: plazos
legales que vencen y trámites atascados. Se compone de `GET /alertas` (sin backend nuevo). Badge con el
número, panel con la lista, cada aviso enlaza a su trámite o al calendario, estado «al día» honesto.

- Cliente: **gestoría**. PR #56, fusionado a `main`, **en producción** (Vercel).
- Pendiente opcional (backend): el **canal email** del mismo aviso (resumen diario al gestor). Ver abajo.

## ✅ #5 — Recordatorio de valor para la gestoría (HECHO Y DESPLEGADO)

Tarjeta **«Tu actividad en Ganera»** en el Inicio: hechos en OVZ, trámites gestionados y ganaderos. **Solo
datos reales** de endpoints existentes; nunca estima «horas ahorradas» (regla de honestidad).

- Cliente: **gestoría**. PR #57, fusionado a `main`, **en producción** (Vercel).

---

## #2-bis — Resumen diario por email al gestor (backend, SIN bloqueo; listo para implementar)

Complemento del canal del aviso: un correo diario a los usuarios de cada gestoría con lo accionable
(N pendientes de revisión, plazos que vencen pronto). Tira de la gestoría al producto cada mañana.

**Diseño**
- `@Scheduled` diario (p. ej. 07:00 `Europe/Madrid`), **trabajo cross-tenant** como `SuscripcionSyncScheduler`
  (sin `gestoriaFilter`; cada consulta con su `gestoriaId` explícito).
- Reutiliza `shared/email/EmailService` (ya existe, **fail-closed**, apagado por defecto) y la lógica de
  `tramite/AlertaService` (hacerla invocable por `gestoriaId` sin JWT).
- Flag nuevo `ganera.notificaciones.resumen-diario.activo` (default `false`, env propio). Sin SMTP configurado
  no envía nada (ya lo garantiza `EmailService`).
- Solo envía si hay algo accionable (no spamear con correos vacíos). Nunca datos personales de más en el cuerpo.
- **Auditoría**: es lectura + email (no escribe tablas de negocio) → declarar el `@Scheduled` como **exento**
  en `InventarioAuditoriaTest` con su motivo (igual que otros planificadores de solo lectura).
- Tests: lógica de armado del resumen (pura) + planificador con un `EmailService` falso (patrón `test-sin-mocks-externos`).

**Sin bloqueo de infraestructura.** Es la idea backend más barata y de mayor valor inmediato.

---

## #1 — Confirmación automática al ganadero cuando su trámite está hecho (backend; BLOQUEO WhatsApp)

Cuando un trámite del ganadero queda `HECHO_EN_OVZ` (o presentado automático), avisarle por WhatsApp
—«Tu baja del crotal … ya está hecha»— **con el justificante**. El ganadero ve que su gestoría (con Ganera)
responde: dependencia pura.

**Qué ya existe**
- El gestor **ya puede** enviar esa confirmación a mano desde el diálogo de revisión (B2 reducido,
  `SeccionWhatsapp`): texto propuesto por estado, y la plantilla de `HECHO_EN_OVZ` ya dice «ya está hecho en OVZ».

**Qué falta (y su bloqueo)**
- **Envío automático** (sin clic del gestor) al pasar a `HECHO_EN_OVZ`/`EJECUTADO_OVZ`: evento/encolado +
  `EnvioWhatsapp` (existe), flag fail-closed, idempotencia, fila de auditoría, declaración en el inventario.
- **Justificante adjunto** (PDF): B2 solo manda texto; adjuntar media es backend adicional.
- **⚠️ BLOQUEO REAL — plantillas de WhatsApp Business.** Un mensaje **iniciado por el negocio fuera de la
  ventana de 24 h** del último mensaje del ganadero necesita una **plantilla aprobada por Meta** y un número
  verificado propio (lo anota B2/Prompt C: «Templates for outside the window need an own verified number»).
  La confirmación suele llegar días después → fuera de ventana → **hoy no se puede entregar sin plantilla**.
- **Camino recomendado:** (1) tramitar número propio + plantillas con Meta/Twilio (Prompt C); (2) entonces
  implementar el auto-envío detrás del flag. Hasta (1), el valor se cubre con el envío manual existente.

---

## #3 — Recordatorios de plazo al ganadero (backend; MISMO BLOQUEO WhatsApp)

Antes de que venza un plazo legal, recordar al ganadero por WhatsApp lo que falta («Recuerda: el plazo de
la baja vence el DD/MM»). Convierte a Ganera en quien evita multas, no solo quien tramita.

**Qué ya existe**
- La **campana (#2)** ya le muestra al gestor qué plazos vencen; desde el trámite puede enviar el recordatorio
  con `SeccionWhatsapp`. El bucle gestor→ganadero ya es posible a mano.

**Qué falta (y su bloqueo)**
- **Programación proactiva** (recordatorio automático X días antes): `@Scheduled` que recorre plazos y envía.
- **⚠️ MISMO BLOQUEO** que #1: un recordatorio proactivo está casi siempre **fuera de la ventana de 24 h** →
  necesita **plantilla de WhatsApp Business**. Sin ella, solo el gestor puede enviarlo a mano (y ya puede).
- **Camino recomendado:** igual que #1 — plantillas Meta primero; luego el planificador detrás de un flag.

---

## #4 — Facturación de la gestoría a sus ganaderos (backend; sin bloqueo de infra, pero diseño grande)

Que la gestoría cobre a sus ganaderos **desde Ganera** (por trámite o cuota) encadena su operativa de cobro
al producto: el coste de irse se dispara.

**Qué falta (no hay nada hoy)**
- Modelo: tarifa por tipo de trámite / cuota por ganadero; conceptos facturables (un trámite `HECHO_EN_OVZ`
  genera un cargo).
- Documento de factura (numeración, PDF) y su almacenamiento en la ficha del ganadero.
- ¿Cobro real? Stripe de Ganera es **suscripción gestoría→Ganera**; cobrar ganadero→gestoría es **otro flujo**
  (Stripe Connect o solo factura + registro de pago manual). **Decisión de producto de Antonio.**
- Multi-tenant estricto, auditoría e inventario como todo lo demás.
- **Sin bloqueo de infraestructura**, pero es la idea **más grande** y necesita decisiones de diseño/negocio
  antes de implementar. Candidata a un spike propio.

---

## Resumen para decidir

| Idea | Cliente | Estado | Bloqueo |
|---|---|---|---|
| #2 campana | gestoría | ✅ en producción | — |
| #5 resumen de valor | gestoría | ✅ en producción | — |
| #2-bis email diario | gestoría | backend, listo | ninguno |
| #1 confirmación auto al ganadero | ganadero | backend, parcial manual ya | plantillas WhatsApp Business |
| #3 recordatorios al ganadero | ganadero | backend, parcial manual ya | plantillas WhatsApp Business |
| #4 facturación a ganaderos | ambos | backend, por diseñar | decisión de negocio (Stripe Connect) |

**Orden sugerido para Antonio:** #2-bis (barato, sin bloqueo) → tramitar plantillas WhatsApp Business →
#1 y #3 (auto) → #4 (spike de diseño).
