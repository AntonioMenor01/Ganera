# Trámites de bovino en OVZNET: catálogo, campos y origen de cada dato

Fecha: 2026-10-06. Base para la ficha OVZ (escalón 1), para el B2 (qué extrae la IA) y para el modo asistido.

**Fuente:** *Manual de la Oficina Veterinaria Virtual* (CAÑADA / OVZNET), Servicio de Sanidad Animal,
Junta de Extremadura. La portada dice Rev. 13.02 (04/12/2025) y las cabeceras, Rev. 13.01 (01/10/2025).
Tiene 69 páginas. SHA-256 del PDF:
`92ee96ea6ca21d64260041b10ebdc4edf65bb26c7b4fe231f03491228d9e2c22`. Portal: https://ovznet.juntaex.es.
El PDF no se sube al repo (`docs/referencias/*.pdf` está en `.gitignore`): cada uno lo guarda en
`docs/referencias/Ultimo Manual OVZNET.pdf`. Las páginas citadas abajo son las del PDF.

## Cautelas antes de usarlo

- **Es el OVZNET de Extremadura.** Otras comunidades tienen su propio sistema. Si una gestoría trabaja
  fuera de Extremadura, este catálogo no vale tal cual.
- **Es el manual del portal del ganadero** (se entra con las claves ARADO o con certificado). El manual
  avisa de que los menús cambian según el perfil. Hay que confirmar con la gestoría piloto que ve las
  mismas pantallas.
- Los campos salen de las **capturas** (imágenes 29, 32, 33–34, 44 y 63–75). El orden es el **visual**:
  filas de arriba abajo y, en cada fila, de izquierda a derecha. El orden de tabulación real hay que
  comprobarlo en OVZ.
- El manual **no da las listas de valores** de Sexo, Tipo de identificador, Raza, Aptitud del movimiento
  ni Categoría del censo. Solo da las de Medio de transporte y Tipo de responsable. Las demás hay que
  sacarlas de OVZ real.

## 1. Catálogo de trámites de bovino

| # | Trámite en OVZ | Menú (págs.) | Qué es | Tipo actual en Ganera |
|---|---|---|---|---|
| 1 | Alta individual ("Alta de Bóvidos por nacimiento en explotación") | Trámites › Alta (22–24) | Nacimiento de un ternero | `ALTA` |
| 2 | Baja de bovino ("por muerte en campo") | Trámites › Baja (24–25) | Muerte en la explotación | `BAJA` |
| 3 | Solicitud de movimiento (guía) | Movimientos › Solicitud (41–54) | Salida: venta a otra explotación, matadero, cebadero, feria, pastos | `MOVIMIENTO` |
| 4 | Confirmación de movimientos | Movimientos › Confirmación (37–41) | Entrada de animales que vienen de otra explotación | `MOVIMIENTO` o `ALTA` (según el prompt actual, "entrada") |
| 5 | Declaración de censo (y "Modificar último censo" en 24 h) | Trámites › Censo (26–27) | Recuento por categoría y raza | `CENSO` |
| 6 | Animales con demora | Trámites › Demora (27) | Fecha de identificación de animales dados de alta con demora de crotalización | `DEMORA` |
| 7 | Anulación de guías | Movimientos (55) | Anular una guía firmada | — |
| 8 | Solicitud de rechazo de animales en origen | Movimientos (55–57) | Devolver animales de una guía a otra CA o a matadero | — |

Otros, que no son declaraciones de un hecho: Animales sin DI (imprimir el DI, p. 27), Identificación
electrónica (recrotalizar con un ES27, pp. 20–21), modificar los datos de un animal en las 72 h siguientes
al alta (p. 19), solicitud de identificadores y de duplicados, implantación de duplicados (pp. 61–66).

**En OVZ no existe la "baja por venta" ni la "baja por matadero".** La venta y el matadero son una
solicitud de movimiento (al matadero, con aptitud SACRIFICIO). Coincide con la regla que ya sigue el
prompt de la IA: la venta y el matadero son movimientos, y la baja es la muerte o el sacrificio en la
explotación.

## 2. Campos de cada trámite, en orden

Leyenda de la columna Obl.: `*` = obligatorio según OVZ; `sí` = obligatorio según el texto del manual,
sin asterisco en la captura.

### 2.1 Alta individual (pp. 22–24)

Antes del formulario: se elige la explotación, luego Trámites › Bóvidos › pestaña Alta, y en la lista
"Crotales repartidos en la explotación" (estado Libre) se elige el crotal que llevará el ternero.

| Orden | Campo en OVZ | Obl. | Notas |
|---|---|---|---|
| 0 | Explotación | sí | Selector inicial |
| 0 | Crotal del ternero | sí | Se elige de la lista; luego sale como "Número Identificación" |
| 1 | Crotal de la Madre | sí | Opcional solo para reses nacidas antes de 1998 |
| 2 | Fecha Nacimiento | `*` | dd/mm/aaaa |
| 3 | Identificador de Lidia | no | Solo ganado de lidia |
| 4 | Sexo | `*` | Lista |
| 5 | Fecha Identificación | `*` | No se pide en explotaciones autorizadas a demorar la crotalización |
| 6 | Tipo Identificador | `*` | Lista; con crotal electrónico sale "Crotal electrónico" por defecto |
| 7 | Raza | `*` | Lista |
| 8 | Emisión del DI | sí | "Prefiero que mis DI's sean emitidos en la OVZ" o "Imprimiré los DI's desde la opción de animales sin DI" (con tasas) |
| 9 | Oficina de recogida o envío por correo | sí | Lista (p. ej. MERIDA) |

Variante **Trashumante** (casilla): añade el Código REGA de la explotación de pertenencia (de otra CA),
que muestra el nombre y el NIF del propietario, y el crotal del ternero.

### 2.2 Baja de bovino (pp. 24–25)

Antes del formulario: se elige la explotación, luego la pestaña Baja, se escribe el crotal y se pulsa
Buscar (o Buscar sin crotal y se elige de la lista).

OVZ rellena solo, en modo lectura: Código RIIA, Especie, Fecha nacimiento, Raza, Explotación de
nacimiento, Sexo, Explotación de ubicación, Identificación de lidia, Código RIIA madre y País de
nacimiento.

| Orden | Campo en OVZ | Obl. | Notas |
|---|---|---|---|
| 1 | Crotal (en Buscar) | sí | |
| 2 | Fecha de muerte | `*` | |
| 3 | Nº Documento MER | sí | Documento de retirada de cadáveres; el texto lo pide, la captura no lleva asterisco |
| 4 | Oficina para entregar la copia del ejemplar MER | sí | Lista (p. ej. BADAJOZ); plazo de 15 días |

Plazo: 7 días desde la muerte. **Una baja por animal.**

### 2.3 Solicitud de movimiento (pp. 45–54)

Antes del formulario: se elige la explotación, luego Movimientos › Solicitud de Movimientos › Bóvidos ›
pestaña Solicitud de Movimiento. OVZ no deja pedir guías si hay entradas sin confirmar desde hace más
de 13 días.

| Orden | Bloque | Campo en OVZ | Obl. | Notas |
|---|---|---|---|---|
| 1 | Origen | Teléfono | `*` | Es lo único editable del bloque; el resto lo rellena OVZ, con las calificaciones y las restricciones de salida |
| 2 | Origen | Autorizo envío de SMS | no | Casilla |
| 3 | Destino | País | | Por defecto España |
| 4 | Destino | Comunidad Autónoma | | |
| 5 | Destino | Código REGA | `*` | Con él, OVZ rellena Nº Registro, finca, nombre y NIF del propietario, municipio, provincia, sistema productivo, tipo de explotación y clasificación zootécnica |
| 6 | Destino | Consignatario | `*` | Nombre de quien recibe los animales |
| 7 | Guía | Fecha de Salida (y hora) | `*` | |
| 8 | Guía | Fecha de Llegada (y hora) | `*` | |
| 9 | Guía | Medio de Transporte | | Camión, Barco, Tren, Avión, Conducción a pie, Otros |
| 10 | Guía | Identificador del medio de transporte (Matrícula) | | |
| 11 | Guía | Transportista (Código SIRENTRA) | | |
| 12 | Guía | Tipo Responsable del Movimiento | | Desconocido, Titular de la Explotación, Tratante-Comerciante-Operador, Transportista, Otros |
| 13 | Guía | Aptitud del Movimiento | | Lista; la captura muestra SACRIFICIO |
| 14 | Guía | Guía Temporal | | Casilla (trashumancia o regreso a la CA de origen) |
| 15 | Animales | Crotal de cada animal | sí | Con "Añadir" desde la lista, o escribiéndolo y pulsando "validar". No se pueden mezclar animales en "Reg. Pertenencia" y en "Reg. Ubicación Temporal" |
| 16 | Animales | Propiedad de los animales: N.I.F., Nombre y apellidos, Explotación, Finca | | Solo para animales en ubicación temporal; vale el REGA de pertenencia o el NIF del propietario; Finca es opcional |

Avisos: con menos de 48 h de antelación puede que la OVZ no la tramite; una guía firmada y no emitida
caduca a los 5 días.

### 2.4 Confirmación de movimientos (pp. 37–41)

Antes: Movimientos › Confirmación, pestaña Internos (origen en Extremadura; se abre por Guía o por
Código REMO) o Externos (origen en otra CA; por Código REMO).

OVZ enseña el origen, el destino y los animales de la guía. El gestor marca **Confirmar** o **Rechazar**
en cada animal y elige la oficina donde recoger los DI nuevos. En la captura parecen editables también la
Fecha de llegada, el Transportista y el Medio de transporte (hay que comprobarlo). Plazo: 7 días desde la
entrada.

### 2.5 Declaración de censo (pp. 26–27)

Una línea por cada combinación de Categoría `*` (lista), Raza `*` (lista) y Cantidad `*`, con el botón
"Añadir". También se puede pulsar "Cargar última declaración" y corregir las cantidades. Se termina con
"Terminar declaración". "Modificar último censo" solo está disponible en las 24 h siguientes: es el
mismo formulario, no un trámite aparte.

### 2.6 Animales con demora (p. 27)

OVZ lista los animales pendientes de fecha de identificación (crotal, fecha de nacimiento, fecha de
reparto). Se marcan los animales, se pone la Fecha de Identificación `*` y se pulsa Actualizar.

### 2.7 Anulación de guía y rechazo en origen (pp. 55–57)

En los dos se elige la guía y se escribe un Motivo. La anulación se puede pedir hasta 10 días después de
la firma, y una sola vez. El rechazo en origen solo vale si el destino es otra CA o un matadero, y en las
48 h siguientes a la salida; también deja modificar o rechazar lotes.

## 3. De dónde sale cada dato

| Clave | Significado |
|---|---|
| **G** | Ganera ya lo tiene |
| **IA** | La IA puede sacarlo del mensaje (B2) |
| **D** | Ganera puede deducirlo (con una regla o con el inventario) |
| **P** | Preferencia fija de la gestoría o del ganadero (se podría guardar una vez) |
| **M** | Lo rellena el gestor |
| **OVZ** | OVZ lo rellena solo; no hace falta en la ficha |

| Trámite | Campo | Origen | Nota |
|---|---|---|---|
| Todos | Explotación (código REGA) | G | `Explotacion.codigoRega` |
| Todos | Titular, para saber con qué cuenta entrar | G | Nombre y NIF del `Ganadero`. `ovzUsuario` no se enseña nunca |
| Alta | Crotal del ternero | IA / M | Solo si el ganadero lo escribe. Ganera no conoce los crotales repartidos (llegarían con la sincronización 3a) y el ternero no está en el inventario |
| Alta | Crotal de la Madre | IA → D | La IA saca los dígitos y el servidor completa el crotal con el inventario, igual que hoy. Hace falta distinguir la madre de los demás crotales |
| Alta | Fecha Nacimiento | IA | "Ha parido hoy" o "ayer": la IA necesita la fecha de recepción del mensaje como referencia |
| Alta | Identificador de Lidia | M | Raro |
| Alta | Sexo | IA | "Macho", "hembra", "becerra", "ternero"… |
| Alta | Fecha Identificación | IA / M | Muchas veces es el día del mensaje ("le he puesto el crotal") |
| Alta | Tipo Identificador | P / M | Casi siempre el mismo |
| Alta | Raza | IA / M | Hace falta la lista de OVZ; "cruzada" es frecuente |
| Alta | Emisión del DI, oficina | P | |
| Baja | Crotal | G | |
| Baja | Fecha de muerte | IA | |
| Baja | Nº Documento MER | M | La IA, solo si viene escrito; suele llegar en papel o en foto |
| Baja | Oficina para la copia del MER | P | |
| Baja | Datos del animal (10 campos) | OVZ | |
| Movimiento | Teléfono | P | Teléfono de contacto de la gestoría o del titular |
| Movimiento | País, Comunidad Autónoma | D | Probablemente salen del REGA de destino (ES + código de provincia); hay que confirmarlo |
| Movimiento | Código REGA de destino | IA / M | Lo normal es que el ganadero diga "al matadero de X" o "a Fulano", no el REGA. Más adelante, una agenda de destinos habituales por ganadero |
| Movimiento | Consignatario | IA / M | |
| Movimiento | Fecha y hora de salida | IA | |
| Movimiento | Fecha y hora de llegada | D / M | Por defecto, el mismo día |
| Movimiento | Medio de transporte | P | Normalmente camión |
| Movimiento | Matrícula, transportista (SIRENTRA) | P / M | El transportista habitual del ganadero |
| Movimiento | Tipo de responsable | P / M | |
| Movimiento | Aptitud del movimiento | IA → D | Si es matadero, SACRIFICIO; el resto de valores hay que confirmarlos |
| Movimiento | Guía temporal | M | |
| Movimiento | Crotales | G | |
| Movimiento | Propiedad de los animales (ubicación temporal) | G / M | NIF, nombre y REGA del titular |
| Confirmación | Guía o Código REMO | M | La IA, si viene escrito |
| Confirmación | Animales que llegaron | G | Los crotales del trámite |
| Confirmación | Animales que no llegaron | IA / M | |
| Confirmación | Oficina para los DI | P | |
| Censo | Categoría, Raza | M | Listas de OVZ |
| Censo | Cantidad | IA / M | |
| Demora | Crotales | G | |
| Demora | Fecha de Identificación | IA | |

## 4. Propuesta de MVP

Orden por frecuencia (es una suposición; hay que confirmarla con la gestoría piloto):

1. baja por muerte;
2. alta por nacimiento;
3. solicitud de movimiento (venta a otra explotación o matadero);
4. confirmación de entrada;
5. después, censo, demora, anulación y rechazo en origen.

**Tipos.** Sustituyen al enum actual, siguiendo la decisión 18 de A1 con dos ajustes. `ALTA_BOVINO`
pasa a llamarse `ALTA_NACIMIENTO`, porque el alta de OVZ es solo por nacimiento (la entrada es una
confirmación). `MOD_DECLARACION_CENSO` desaparece, porque es el mismo formulario que la declaración,
dentro de las 24 h.

| Tipo | Formulario de OVZ |
|---|---|
| `ALTA_NACIMIENTO` | Alta individual |
| `BAJA_MUERTE` | Baja de bovino |
| `SOLICITUD_MOVIMIENTO` | Solicitud de movimiento |
| `CONFIRMACION_MOVIMIENTO` | Confirmación de movimientos |
| `DECLARACION_CENSO` | Declaración de censo |
| `DEMORA_CROTALIZACION` | Animales con demora |

La anulación de guías y el rechazo en origen quedan fuera.

**Campos del MVP** (lo que la IA tendrá que sacar en el B2 y la ficha tendrá que enseñar):

- **Baja:** crotales, fecha de muerte y nº MER (si viene escrito).
- **Alta:** crotal del ternero (si viene escrito), crotal de la madre, fecha de nacimiento, sexo, raza
  (texto libre hasta tener la lista de OVZ) y fecha de identificación.
- **Movimiento:** crotales, destino en texto (y su REGA si viene escrito), consignatario, fecha de salida
  y aptitud, si se puede deducir (matadero).
- **Para todos:** la IA recibe la fecha de recepción del mensaje y devuelve fechas absolutas.

**Consecuencias para el B2:**

1. **Varios animales.** En OVZ cada alta y cada baja es un formulario por animal, y una guía lleva
   varios. Un trámite de Ganera con tres crotales de baja son tres bajas en OVZ. Propuesta: los datos
   (por ejemplo, la fecha de muerte) van por trámite. Si las fechas no coinciden, la IA parte el mensaje
   en varios trámites ("varios trámites por mensaje" ya está en el B2).
2. **Crotal de la madre.** Hace falta un rol en los crotales del trámite (animal o madre) o un campo
   aparte con su propia resolución contra el inventario.
3. **Listas de valores de OVZ** (sexo, raza, tipo de identificador, aptitud y categoría): hay que
   sacarlas de OVZ real antes del B2.
4. **Preferencias de la gestoría** (oficina, emisión del DI, tipo de identificador, medio de transporte y
   teléfono): hace falta un sitio donde guardarlas. Quedan fuera del MVP; mientras tanto, la ficha las
   marca como "Falta".

## 5. Plazos que da el manual

Sirven para los avisos que tenga la cola en el futuro.

| Hecho | Plazo |
|---|---|
| Baja por muerte | 7 días desde la muerte |
| Copia del ejemplar MER a la OVZ | 15 días |
| Confirmación de una entrada | 7 días desde la entrada |
| Entradas sin confirmar | Pasados 13 días, OVZ no deja pedir más guías |
| Solicitud de guía | Al menos 48 h de antelación (aviso, no bloqueo) |
| Guía firmada y no emitida | Caduca a los 5 días |
| Anulación de una guía | Hasta 10 días desde la firma, una sola vez |
| Rechazo en origen | 48 h desde la salida |
| Modificar los datos de un animal | 72 h desde el alta |
| Modificar el último censo | 24 h |
| Alta por nacimiento | El manual remite a un enlace ("Plazo de notificación de altas por nacimientos") que no incluye |
