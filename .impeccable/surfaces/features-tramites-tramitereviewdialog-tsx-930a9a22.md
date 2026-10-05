---
version: 1
slug: "features-tramites-tramitereviewdialog-tsx-930a9a22"
primary_target: "frontend/src/features/tramites/TramiteReviewDialog.tsx"
related_targets: []
---

# Revisión de trámite (modal)

Scope: `TramiteReviewDialog` (se abre desde la cola `/tramites`). Mode: Operate.

Audience and job: administrativos de la gestoría, varias veces al día, y el gestor a veces desde el
móvil. Abren un trámite, comparan el mensaje de WhatsApp con lo extraído, corrigen explotación, tipo
y crotales, guardan y aprueban o rechazan. Es el momento que el producto existe para proteger: el
humano aprueba siempre, y nunca se aprueba otra cosa que lo que se ve.

Content: detalle `{id, estado, tipoTramite, explotación (código REGA + nombre), mensajeOriginal
(hoy casi siempre null: 3b no existe), motivoError, crotales:[{crotalIndicado, crotal, resolucion}],
version}`. Edición solo en `PENDIENTE_REVISION`. Lógica y estados en `useRevisionTramite`
(Task 9a): guardar/aprobar/rechazar de uno en uno, 409 → datos frescos + motivo literal, 400
conserva la edición, "Guarda antes de aprobar" con cambios sin guardar.

Constraints: mundo visual fijo (DESIGN.md), solo tokens. Nada sugiere que algo ocurra en OVZ.net:
"Aprobar" solo cambia el estado. La regla de aprobación vive en el backend; el modal no la predice.
Los badges muestran lo guardado, nunca una predicción.

Decided with Antonio (2026-09-30): modal ancho de dos columnas (mensaje a la izquierda, datos
editables a la derecha; en móvil pantalla completa y columnas apiladas); una fila por crotal con
campo, crotal resuelto, badge y quitar, más "Añadir crotal"; confirmación de Rechazar en línea en
la barra de acciones, sin segundo modal.

## Direction contract

THESIS: una mesa de cotejo: a la izquierda lo que escribió el ganadero, a la derecha lo que Ganera
entendió, alineado para comprobarlo de un vistazo antes de firmar. Rechaza el formulario vertical
genérico y el modal estrecho con los datos apilados bajo el mensaje.

OWN-WORLD: el ledger de Ganera: panel blanco con anillo al 10 % sobre velo ligero; columna del
mensaje en Superficie como papel citado; campos de 32 px con borde Gris Input; badges en pares exactos;
Rojo Acción sólido solo en "Aprobar" (y "Guardar"), Rojo Enlace en enlaces y foco, Rojo Problema
teñido en "Sí, rechazar" y en los avisos con icono (marca nueva, 2026-10-05); cifras tabulares en crotales y REGA.

STORY: el empleado lee el mensaje, ve qué explotación, tipo y crotales se dedujeron y cómo se
resolvió cada crotal, corrige lo que falte, guarda, ve las resoluciones nuevas y aprueba; si el
backend objeta, lee su motivo exacto sobre los datos frescos.

FIRST VIEWPORT: cabecera "Trámite #N" con badge de estado; debajo dos columnas: izquierda "Mensaje
de WhatsApp" en bloque citado (o "Aún no hay mensaje: este trámite no llegó por WhatsApp"),
derecha los campos Explotación (combobox con búsqueda por REGA, nombre o ganadero), Tipo y la lista
de crotales con su badge; avisos entre la cabecera y las columnas; pie fijo con Rechazar a la
izquierda y Guardar + Aprobar a la derecha. Una fila de crotal editada o nueva muestra "Sin guardar"
en lugar de su badge antiguo.

FORM: extensión precisa dentro del mundo establecido, dirigida directamente sin concept-seed
(new-work.md: "Never run the script for a local extension or a precisely specified narrow
request"). La especifican las decisiones 7–13 y 24 del plan A2 y las tres respuestas de Antonio del
2026-09-30 citadas arriba. Seed key: none (no se tiró).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
