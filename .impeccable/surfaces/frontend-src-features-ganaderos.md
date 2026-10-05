---
version: 1
slug: "frontend-src-features-ganaderos"
primary_target: "frontend/src/features/ganaderos"
related_targets: []
---

# Ganaderos (listado y detalle)

Scope: `/ganaderos` (listado paginado) y `/ganaderos/:id` (detalle). Mode: Operate.

Audience and job: administrativos de la gestoría (escritorio) y el gestor (a veces desde el móvil).
Buscan a un ganadero para saber qué explotaciones tiene, quién escribe por WhatsApp en cada una y
con qué rol, y consultar su inventario de animales. Solo lectura: Ganaderos, Explotaciones y
Animales solo se crean con el importador Excel (no hay formularios de alta).

Content: listado `{nombre, nif, numeroExplotaciones}`, ordenable solo por nombre y NIF. Detalle
`{nombre, nif, explotaciones:[{codigoRega, nombre, contactos:[{nombre, telefono, rol}]}]}`,
contactos activos solamente. Animales por explotación vía `GET /explotaciones/{id}/animales`
(Task 8). 404 = otra gestoría o inexistente, indistinguibles a propósito.

Constraints: mundo visual fijo (DESIGN.md), tokens solamente, sin tarjetas anidadas, sin métrica
héroe, sin kicker. Errores siempre visibles con Reintentar.

Decided with Antonio (2026-09-29): secciones apiladas por explotación; si hay más de 3
explotaciones, un índice compacto arriba con los códigos REGA (y nombre) como enlaces a cada
sección; animales plegados por defecto; teléfonos como enlace `tel:`.

## Direction contract

THESIS: la ficha de un cliente de la gestoría, leída de arriba abajo como un expediente: quién es,
qué explotaciones tiene y quién habla por cada una. Rechaza el panel de tarjetas iguales con
métricas y el maestro-detalle con drawers.

OWN-WORLD: el ledger de Ganera sin cambios: lienzo Fondo, un único contenedor blanco con
anillo al 10 % por bloque, filas separadas por Gris Borde, Rojo Enlace solo para enlaces y foco,
badges de rol en par exacto (Titular = éxito, Empleado = outline neutro), cifras tabulares.

STORY: el empleado encuentra al ganadero en una lista ordenable, abre su ficha, identifica cada
explotación por su código REGA, ve y llama a sus contactos, y despliega los animales solo cuando
los necesita.

FIRST VIEWPORT: enlace "← Ganaderos" pequeño arriba; título = nombre (20px/800) con NIF tabular
en Gris Texto al lado; subtítulo con "N explotaciones"; con más de 3, índice en línea de enlaces
REGA·nombre; debajo, la primera sección de explotación: cabecera REGA + nombre, lista de
contactos (nombre, teléfono tel:, badge de rol), pie "Ver animales" plegado.

FORM: extensión precisa dentro del mundo establecido, dirigida directamente sin concept-seed
(new-work.md: "Never run the script for a local extension or a precisely specified narrow
request"). La especifican la decisión 14 del plan A2 ("`/ganaderos/:id`: cabecera (nombre, NIF) y
una tarjeta por explotación (código REGA, nombre, contactos con teléfono y badge de rol, y el panel
'Ver animales')") y las respuestas de Antonio del 2026-09-29 ("Secciones apiladas. Si el ganadero
tiene más de 3 explotaciones, añade arriba un índice compacto con los códigos REGA (y nombre) como
enlaces a cada sección. Los animales siguen plegados por defecto." y "Enlace para llamar").
Seed key: none (no se tiró).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
