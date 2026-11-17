# Mapa de OVZ

Documentación de la estructura interna de los formularios de OVZnet / CAÑADA (Oficina Veterinaria Virtual de Extremadura, Junta de Extremadura) para vacuno. Se ha generado leyendo las páginas con una sesión de ganadero, **sin enviar, grabar ni presentar nada**.

Convenciones:
- Los datos reales se sustituyen por marcadores: `<CROTAL>`, `<NIF>`, `<NOMBRE>`, `<CODIGO_REGA>`, `<FECHA>`, `<TELEFONO>`, `<CLAVE_INTERNA>`, `<N>`.
- `P_` es la forma corta del prefijo de id indicado en cada sección. El `name` se obtiene cambiando `_` por `$` en el id.
- Los valores de los catálogos se dan como `Texto [value]`.

---

## Entrada y menú

### 1. Inicio de sesión
- Tipos: **usuario y contraseña**, o **certificado digital** (botón «Entrar con Certificado»). En la pantalla de entrada no aparece Cl@ve.
- Al entrar con usuario y contraseña no se pide doble factor.
- Tras el login se llega a `/Presentacion.aspx`.

### 2. Sesión
- Caduca tras **20 minutos sin actividad**, según el aviso de la propia web.
- ⚠️ **Detector de «varias instancias»:** si se carga una página **escribiendo su URL**, se pulsa **Atrás** o hay **dos pestañas** abiertas con OVZnet, la aplicación redirige a `/AvisoSesion.aspx` («Varias instancias de la aplicación abierta, pueden ocasionarle pérdidas de datos.») y la sesión deja de valer. **Hay que navegar siempre pulsando los enlaces y menús de la aplicación.**
- Botón «Salir» (`ctl00_ucBarraUsuario1_btnSalir`), arriba a la derecha: cierra la sesión.

### 3. Explotaciones
- Desplegable **«Lista de explotaciones:»**: id `ctl00_ucMenu1_ddlDelegado`, name `ctl00$ucMenu1$ddlDelegado`.
  - Valor inicial: «Seleccione un elemento...».
  - Opciones (son datos del ganadero, así que no se copian): `<CODIGO_REGA> - <ESPECIE>`, **una entrada por cada especie** de cada explotación. El vacuno aparece como «Bóvidos».
  - Al cambiar de valor hace postback.
- La cuenta documentada tiene **una sola explotación (un REGA) con varias especies**.
- El menú principal **solo aparece tras elegir una explotación**.
- Cada opción de trámite abre primero una tabla «Especie | Provincia | Municipio» con las explotaciones a las que se tiene acceso. **Hay que pulsar el enlace de la especie («Bóvidos»)** para entrar en el trámite. Ese enlace lleva `?ClaveExplotacion=<CLAVE_INTERNA>&ClaveEspecie=1`.
- Junto al desplegable aparece «Actua en nombre de: `<NOMBRE>`».

### 4. Menú principal
Barra horizontal con submenús que se abren **al pasar el ratón**. Los enlaces no tienen id; es más fiable localizarlos por su `href`.

| Menú | Submenú | href |
|---|---|---|
| Explotaciones | Búsqueda de explotaciones | – |
| Animales | Búsqueda de animales | `/Animales/wgAnimales.aspx` |
| | Buscar animal | – |
| | Identificación Electrónica de Bovinos | – |
| Trámites | Trámites | `/Explotaciones/wgTramites.aspx` |
| | Solicitud DVR | – |
| Movimientos | Confirmación de Movimientos | `/Movimientos/wgMovimientos.aspx` |
| | Solicitud de Movimientos | `/Movimientos/wgMovimientos.aspx` |
| | Anulación de guías | `/Movimientos/wgSolicitudAnulacionGuias.aspx` |
| | Solicitud rechazo animales en origen | – |
| Trámites OVZ | Tareas OVZ | `/Tareas/wgTareas.aspx` |
| Documentos | Documentos | `/Explotaciones/wgDocumentos.aspx` |
| | Resúmenes | `/Explotaciones/wgResumen.aspx` |
| Identificadores | Solicitud de Identificadores · Busqueda Ficheros Identificadores · Busqueda Solicitud Identificadores · Subida de fichero TP3 · Solicitud Duplicados · Implantación Duplicados | – |
| Tratamientos | Consulta de Prescripciones por Explotación | – |
| Descargas | Descargas | `/Descargas/wgDescargas.aspx` |
| Comunicación de información | Datos de Contacto | – |

«Confirmación de Movimientos» y «Solicitud de Movimientos» comparten URL. La pantalla que se muestra depende de la opción pulsada (se guarda en la sesión).

### Datos técnicos generales
- Aplicación ASP.NET WebForms, con un único formulario `aspnetForm`. **No hay iframes** en ninguna de las pantallas revisadas.
- Las pestañas y los botones son enlaces `javascript:WebForm_DoPostBackWithOptions(...)` o `__doPostBack(...)`. **La URL no cambia** al pasar de una pestaña o pantalla a otra.
- Los id `ctl00_…` los genera el servidor y son estables, pero llevan **un índice de pestaña** (`ctl02`, `ctl05`…) que depende de la pestaña. Selector recomendado: `[id$="_<Sufijo>"]` dentro del prefijo de su pestaña.
- Los botones finales («Aceptar», «Solicitar») **no piden confirmación en el cliente**: un solo clic presenta el trámite.

---

## A. Baja por muerte en campo

**Ruta:** elegir explotación «… - Bóvidos» → **Trámites › Trámites** → «Bóvidos» → pestaña **«Baja de Bóvidos»**.
Pestañas de la página: «Alta Individual» · «Baja de Bóvidos» · «Declaración de Censo» · «Animales sin DI».
URL: `/Explotaciones/wgTramites.aspx?ClaveExplotacion=<CLAVE_INTERNA>&ClaveEspecie=1`. No cambia entre pantallas.

**Prefijo:** `P_` = `ctl00_ContentPlaceHolder1_ucPestanasTramites_ctl05_`

**Pantallas:** 2.
1. Búsqueda del animal: se pasa a la siguiente con «Buscar».
2. «Baja de Bovino» (subtítulo: «Baja de Bovino por muerte en campo.»), con los botones «Aceptar» y «Volver».

### Pantalla 1 – Búsqueda
| Orden | Etiqueta | Tipo | Obligatorio | Formato/valores | Depende de | id |
|---|---|---|---|---|---|---|
| 1 | *(sin etiqueta: crotal)* | texto | sí (para buscar) | `<CROTAL>` (ES + 12 dígitos); sin maxlength | – | `P_TxtCrotal` |
| – | Buscar | enlace con postback | – | busca, no guarda | 1 | `P_LinkBuscar` |

### Pantalla 2 – Formulario
Leyenda «(*) Datos Obligatorios». No hay validadores de cliente.

| Orden | Etiqueta | Tipo | Obligatorio | Formato/valores | Depende de | id |
|---|---|---|---|---|---|---|
| 1 | Código RIIA | texto, solo lectura | – | `<CROTAL>` | crotal buscado | `P_txtCodigoRIIA` |
| 2 | Fecha nacimiento | texto, solo lectura | – | `<FECHA>` | ídem | `P_txtFechaNacimiento` |
| 3 | Explotación de nacimiento | texto, solo lectura | – | `<CODIGO_REGA>` | ídem | `P_txtExplotacionNacimiento` |
| 4 | Explotación de ubicacion | texto, solo lectura | – | `<CODIGO_REGA>` | ídem | `P_txtExplotacionUbicacion` |
| 5 | Código RIIA madre | texto, solo lectura | – | `<CROTAL>` | ídem | `P_txtCodigoRIIAMadre` |
| 6 | País de nacimiento | texto, solo lectura | – | – | ídem | `P_txtPaisNacimiento` |
| 7 | Especie | texto, solo lectura | – | – | ídem | `P_txtEspecie` |
| 8 | Raza | texto, solo lectura | – | – | ídem | `P_txtRaza` |
| 9 | Sexo | texto, solo lectura | – | – | ídem | `P_txtSexo` |
| 10 | Identificación de lidia | texto, solo lectura | – | – | ídem | `P_txtIdemLidia` |
| 11 | **Fecha de muerte** | texto de fecha + calendario | **sí (\*)** | maxlength 10; `dd/mm/aaaa` (PENDIENTE). Plazo: máximo 7 días desde la muerte | – | `P_TextFechaMuerte`, calendario `P_imgIni` |
| 12 | Nº Documento MER | texto | no | maxlength 80 | – | `P_TxtMer` |
| 13 | Mostrar/Ocultar Documentos | botón | no | muestra u oculta los adjuntos en el cliente, sin postback | – | `P_btnAddDocumentos` |
| 13a | *(adjunto MER)* | selector de archivo | no | «Puede adjuntar el documento MER(en formato pdf) cuyo tamaño no supere los 5 MB.» | 13 | `P_FUpload`, dentro de `P_divAdjuntos` |
| 13b | Añadir | enlace con postback | – | **sube el adjunto**: no pulsar | 13a | `P_BtnGrabar` |
| 13c | *(tabla de adjuntos)* | tabla | – | – | 13b | `P_gvDocumentos` |
| 14 | Oficina Veterinaria de Zona | desplegable **desactivado** | – | AZUAGA · BADAJOZ · CACERES · CASTUERA · CORIA · DON BENITO · HERRERA DEL DUQUE · JEREZ DE LOS CABALLEROS · MERIDA · NAVALMORAL DE LA MATA · PLASENCIA · TRUJILLO · VALENCIA DE ALCANTARA · ZAFRA · ZORITA | viene preseleccionada (de la explotación, PENDIENTE) | `P_DDLOVZ` |

**Avisos fijos:**
- Plazo de notificación: máximo 7 días desde la muerte.
- Hay que entregar una copia del ejemplar MER en 15 días en la OVZ indicada.
- Si el MER no identifica el animal o no se lee bien, hay que aportar también el justificante de la baja de la aplicación.

**Botones (no pulsados):**
- **«Aceptar»** (`P_btn_Aceptar`): postback sin confirmación. **Presenta la baja.**
- **«Volver»** (`P_btnVolver`): vuelve a la búsqueda.

**Datos técnicos:** no hay iframes; la URL no cambia; mensajes de error devueltos por el servidor (PENDIENTE).

**Documento generado:** el justificante de la baja. Aparece en **Documentos › Documentos** con «Tipo Trámite: Baja Animal», como un enlace en la columna «Documento» (ver E). Formato probable: PDF (PENDIENTE).

**Incidencias:** ninguna aparte de las generales.

---

## B. Alta por nacimiento

**Ruta:** elegir explotación «… - Bóvidos» → **Trámites › Trámites** → «Bóvidos» → pestaña **«Alta Individual»** (la que se abre por defecto).
URL: igual que en A. No cambia.

**Prefijo:** `P_` = `ctl00_ContentPlaceHolder1_ucPestanasTramites_ctl02_`

**Pantallas:** 2.
1. «Alta de Bóvidos» (subtítulo: «Alta de Bóvidos por nacimiento en explotación.»): tabla de crotales repartidos. **Se pasa a la pantalla 2 pulsando un crotal.** Elegirlo no cambia su estado: comprobado que sigue «Libre» tras «Volver».
2. Formulario «DATOS DEL ANIMAL», con los botones «Aceptar» y «Volver».

### Pantalla 1
| Orden | Etiqueta | Tipo | Obligatorio | Formato/valores | Depende de | id |
|---|---|---|---|---|---|---|
| 1 | Trashumante | casilla | no | – | – | `P_chkTrashumante` (**postback**) |
| 2 | CROTALES REPARTIDOS EN LA EXPLOTACIÓN `<CODIGO_REGA>` | tabla | – | Columnas: Crotal · Estado · Fecha Reparto. Datos del ganadero (no se copian). Pie: «`<N>` crotales repartidos encontrado en …» | – | `P_gvCrotalesRepartidos` |
| 2a | *(crotal de cada fila)* | enlace con postback | sí | elige el crotal del alta | – | enlace dentro de cada fila |
| – | *(errores)* | texto | – | – | – | `P_lblError` |

### Pantalla 2 – Formulario
Leyenda «(*) Datos Obligatorios». Hay 2 validadores de cliente (los de formato de fecha).

| Orden | Etiqueta | Tipo | Obligatorio | Formato/valores | Depende de | id |
|---|---|---|---|---|---|---|
| 0 | *(crotal del animal)* | texto fijo | – | `<CROTAL>` | pantalla 1 | `P_lblcrotal` |
| 1 | Crotal de la Madre: | texto | **sí (\*)** | `<CROTAL>` | – | `P_TxtMadre` |
| 2 | Fecha Nacimiento: | fecha + calendario | **sí (\*)** | `dd/mm/aaaa`; mensaje «Indique la fecha con el formato: dd/mm/aaaa» | – | `P_TxtFNac`, `P_imgIni`, `P_RegularExpressionValidator1` |
| 3 | Sexo: | desplegable | **sí (\*)** | Seleccione un elemento... [xx] · Hembra [H] · Macho [M] | – | `P_DDLSexo` |
| 4 | Tipo Identificador: | desplegable | **sí (\*)** | Seleccione un elemento... [00] · Crotal Electrónico [04] | – | `P_DDLTipoIdentificador` |
| 5 | Explotación Nacimiento: | texto | no | maxlength 14 (`<CODIGO_REGA>`) | **postback al cambiar** | `P_txtExplotNacimiento` |
| 6 | Identificador de Lidia: | texto | marcado \* (¿solo con raza LIDIA?, PENDIENTE) | – | ¿Raza? | `P_TxtLidia` |
| 7 | Fecha Identificación: | fecha + calendario | **sí (\*)** | `dd/mm/aaaa` | – | `P_TxtFId`, `P_imgId`, `P_RegularExpressionValidator2` |
| 8 | Raza: | desplegable | **sí (\*)** | catálogo abajo (134 razas) | – | `P_DDLRazas` |
| 9 | Emisión del DI | opciones | sí (una viene marcada) | «Prefiero que mis DI's sean emitidos en la OVZ» [OVZ] (**por defecto**) · «Imprimiré los DI's desde la opción de animales sin DI» [OVZNET] | – | `P_rblImpDI_0`, `P_rblImpDI_1` (onclick en el cliente) |
| 10 | Envío o recogida del DI | desplegable | sí (viene elegido) | CORREO [0] · AZUAGA [601] · BADAJOZ [602] · CACERES [1001] · CASTUERA [603] · CORIA [1002] · DON BENITO [604] · HERRERA DEL DUQUE [605] · JEREZ DE LOS CABALLEROS [606] · MERIDA [607] · NAVALMORAL DE LA MATA [1004] · PLASENCIA [1005] · TRUJILLO [1006] · VALENCIA DE ALCANTARA [1007] · ZAFRA [608] · ZORITA [1003] | preseleccionada la OVZ de la explotación (PENDIENTE); se aplica con la opción 9 = OVZ | `P_DDLOVZ` |

**Catálogo de razas (`P_DDLRazas`):**
ASTURIANA [1] · RUBIA GALLEGA [2] · SANTA GERTRUDIS [3] · BLANCA CACEREÑA [4] · LIDIA [5] · CRUZADA [6] · HEREFORD [7] · FLECKVIEH [8] · MORUCHA [9] · LIMOUSINE [11] · CHAROLAISE [12] · AVILEÑA [13] · RETINTA [14] · FRISONA [15] · OTRAS [36] · BLONDE AQUITANI [64] · BNEGRO [65] · BCOLORADO [66] · MONTPELLIER [67] · SALERS [68] · MERTOLENGA [69] · PIRENAICA [70] · ALISTANA-SANABRESA [73] · BETIZU [74] · BRUNA DE LOS PIRINEOS [75] · LEVANTINA [76] · MALLORQUINA [77] · MONCHINA [78] · MOSTRENCA [79] · SERRANA NEGRA [80] · PARDA [81] · JERSEY [82] · ANGUS [83] · ROJA DANESA [84] · ASTURIANA DEL VALLE [85] · MRY [86] · NORMANDA [87] · TUDANCA [88] · CARDENA ANDALUZA [89] · MURCIANA [90] · CANARIA [91] · PALMERA [92] · MENORQUINA [93] · NEGRA ANDALUZA [94] · PAJUNA [95] · BLANCA BELGA [96] · PIAMONTESA [97] · CACHENA [98] · CALDELANA [99] · FRIEIRESA [100] · LIMIANA [101] · VIANESA [102] · SAYAGUESA [103] · TERREÑA [104] · AYRSHIRE [105] · BUFALO [106] · CHIANINA [107] · GUERNSEY [108] · MONTBELIARD [109] · BISONTE [110] · BRAHMAN [111] · CEBU [112] · HOLSTEIN [113] · SHORTHORN [114] · SIMMENTAL [115] · ANGLER [116] · BORDELAISE [117] · GALLOWAY [118] · BRETTONNE PIE NOIRE [119] · CASTA (AURE Y ST. GIRONS) [120] · ARMORICAINE [121] · AUROCHS RECONSTITUE [122] · BAZADAISE [123] · BEARNAISE [124] · BLUE DU NORD [125] · COOPELSO [126] · CORSE [127] · CREOLE [128] · FERRANDAISE [129] · FROMENT DU LEON [130] · GELBVIEH [131] · HERENS [132] · INRA [133] · LOURDAISE [134] · MARAICHINE [135] · MARCHIGIANA [136] · MIRANDAISE [137] · N. DAMA [138] · NANTAISE [139] · PIE ROUGE DES PLAINES [140] · CAMARGUE [141] · ROUGE FLAMANDE [142] · SAOSNOISE [143] · TARENTAISE [144] · VILLARD DE LANS [145] · VOSGIENNE [146] · ABONDANCE [147] · ROTBUNTE [148] · SOUTH DEVON [149] · IRISH MAOL / DROIMEANN [151] · MAINE ANJOU [152] · PARTENAISE [153] · DEXTER [154] · AUBRAC [155] · HIGHLAND [156] · KERRY [157] · MURRAY GREY [158] · ROMAGNOLA [159] · PASIEGA [160] · SERRANA DE TERUEL [161] · DESCONOCIDA [901] · DESCONOCIDA [955] · ALBERA [956] · MURCIANA-LEVANTINA [957] · PINZGAUER [958] · BALTATA ROMANESCA [959] · ROJA LETONA [960] · ALENTEJANA [1015] · PARDE DE MONTAÑA [1088] · WAGYU [1089] · WATUSI [1090] · KOBE [1091] · SWEDISH RED AND WHITE [1092] · AROUQUESA [1093] · PRETA [1114] · Valdostana [1119] · Vaca de la reina [1125] · Gascona del Pirineo [1204] · Barrosa [1212] · Dansk Holstein [1217] · Prim Holstein [1256] · Mantequera leonesa [1319] · Braunvieh [1321] · PARDA DE MONTAÑA [1322]

> Conviene mapear las razas **por valor**, no por texto: hay dos «DESCONOCIDA» (901 y 955) y dos casi iguales, «PARDE DE MONTAÑA» [1088] y «PARDA DE MONTAÑA» [1322]. En este formulario no hay campo de «aptitud».

**Textos y avisos:**
- Los DI se pueden imprimir desde Trámites › pestaña «Animales sin DI», o pedir que los emita la OVZ.
- Si los emite la OVZ hay que pagar tasas con el modelo M 50: 2,95 € por el servicio y 0,14 € por cada DI.
- El envío por correo con acuse de recibo cuesta 5,90 € más, y hay que remitir el M 50 a la OVZ.
- Enlace «PLAZO DE NOTIFICACIÓN DE ALTAS POR NACIMIENTOS» (`P_Plazos`): se abre en una pestaña nueva (`target=_blank`).

**Botones (no pulsados):**
- **«Aceptar»** (`P_btnAceptar`): postback sin confirmación. **Presenta el alta.**
- **«Volver»** (`P_btnVolver`): vuelve a la tabla de crotales.

**Datos técnicos:** no hay iframes; la URL no cambia. Varios sufijos (`_DDLOVZ`, `_imgIni`) se repiten en la pestaña de baja (`ctl05`), así que hay que filtrar por `_ctl02_`.

**Documento generado:** el DI, que emite la OVZ o se imprime desde «Animales sin DI». En «Documentos» existe el tipo de trámite «Alta Bovino». Formato del justificante: PENDIENTE.

**Incidencias:** el postback al cambiar «Explotación Nacimiento» recarga la página; hay que esperar antes de seguir rellenando.

---

## C. Solicitud de movimiento (venta y salida a matadero)

**Ruta:** elegir explotación «… - Bóvidos» → **Movimientos › Solicitud de Movimientos** → «Bóvidos» → pestaña **«Solicitud Movimiento»**. La otra pestaña es «Consulta Solicitudes» (ver E).
URL: `/Movimientos/wgMovimientos.aspx`. No cambia.

**Prefijo:** `P_` = `ctl00_ContentPlaceHolder1_ucPestanasMovimientos_ctl05_`

**Pantallas:** una sola pantalla larga con bloques: Explotación Origen → Explotación Destino → Datos de la guía → Animales → «Solicitar».
- Arriba: «Limpiar Formulario» (`P_BtnLimpiar`), que vacía el formulario.
- Cabecera «DATOS DE LA SOLICITUD DE MOVIMIENTO ( )», con `P_LblSolicitud` y `P_LblEstado`.
- Aviso fijo: «Las solicitudes de movimientos intracomunitarios y exportaciones a terceros países deberán realizarse presencialmente en la OVZ correspondiente, no admitiéndose su tramitación a través de la aplicación OVZnet (CAÑADA)».

### Explotación Origen (se rellena sola)
| Orden | Etiqueta | Tipo | Obligatorio | Formato/valores | Depende de | id |
|---|---|---|---|---|---|---|
| 1 | Nombre Propietario | texto, solo lectura | – | `<NOMBRE>` | explotación activa | `P_TxtPropOrigen` |
| 2 | Nif Propietario | texto, solo lectura | – | `<NIF>` | ídem | `P_TxtNifPropOrigen` |
| 3 | Código REGA | texto, solo lectura | – | `<CODIGO_REGA>` | ídem | `P_txtCodigoREGAOrigen` |
| 4 | Nº Registro | texto, solo lectura | – | – | ídem | `P_TxtRegistroOrigen` |
| 5 | Nombre Finca | texto, solo lectura | – | – | ídem | `P_txtNombreFincaOrigen` |
| 6 | Sistema Productivo | texto, solo lectura | – | – | ídem | `P_TxtSOOrigen` |
| 7 | Tipo Explotación | texto, solo lectura | – | – | ídem | `P_TxtTipoExplOrigen` |
| 8 | Municipio | texto, solo lectura | – | – | ídem | `P_TxtMunicipioOrigen` |
| 9 | Provincia | texto, solo lectura | – | – | ídem | `P_TxtProvinciaOrigen` |
| 10 | Teléfono | texto | **sí (\*)** | viene relleno (`<TELEFONO>`) | titular | `P_TxtTelefono` |
| 11 | «Autorizo envío de SMS a este número de teléfono, cuando el movimiento sea validado.» | casilla | no | desmarcada | – | `P_ckSms` |
| 12 | Último Saneamiento | 2 textos, solo lectura | – | `<FECHA>` y diagnóstico | ídem | `P_TxtFechaSaneamiento`, `P_TxtDiag` |
| 13 | Clasificación Zootécnica | texto, solo lectura | – | – | ídem | `P_txtClasifZootecnicaOrigen` |
| 14 | Calificaciones | tabla, solo lectura | – | Columnas: Enfermedad · Calificación · Fecha Obtención. Filas: Tuberculosis, Brucelosis, Leucosis, Perineumonia, IBR, Lengua Azul, Riesgo zoosanitario | ídem | – |
| 15 | Restricciones de Salida : | texto | – | – | ídem | – |

### Explotación Destino
| Orden | Etiqueta | Tipo | Obligatorio | Formato/valores | Depende de | id |
|---|---|---|---|---|---|---|
| 16 | Pais | desplegable **desactivado** | – | fijo en España [111]; catálogo de 227 países (UE 101–127, resto desde 201) | – | `P_DDLPais` (postback) |
| 17 | Comunidad Autónoma | desplegable | PENDIENTE | C.A. de Andalucía [1] · C.A. de Aragón [2] · C.A. de Asturias [3] · C.A. de Baleares [4] · C.A. de Canarias [5] · C.A. de Cantabria [6] · C.A. de Castilla la Mancha [7] · C.A. de Castilla y León [8] · C.A. de Cataluña [9] · C.A. de Extremadura [10] (**por defecto**) · C.A. de Galicia [11] · C.A. de Madrid [12] · C.A. de Murcia [13] · C.A. de Navarra [14] · C.A. del País Vasco [15] · C.A. de la Rioja [16] · C.A. Valenciana [17] · Ceuta [18] · Melilla [19] | – | `P_DDLCA` (**postback**: recarga Provincia) |
| 18 | **Código REGA** | texto | **sí (\*)** | maxlength 14 (`<CODIGO_REGA>`) | – | `P_TxtExplotacion` (**postback al cambiar**: busca el destino) |
| 19 | Nº Registro | texto, solo lectura | – | – | REGA destino | `P_TxtRegistroDestino` |
| 20 | Nombre Finca | texto, solo lectura | – | – | REGA destino | `P_TxtNombreFincaDestino` |
| 21 | Nombre Propietario / Nif Propietario | textos, solo lectura | – | `<NOMBRE>` / `<NIF>` | REGA destino | `P_TxtPropDestino`, `P_TxtNifPropDestino` |
| 22 | **Consignatario** | texto | **sí (\*)** | libre | – | `P_TxtConsignatario` |
| 23 | Municipio | desplegable **desactivado** | – | municipios de la provincia (Badajoz: 165) | Provincia / REGA destino | `P_DDLMunicipio` |
| 24 | Provincia | desplegable **desactivado** | – | BADAJOZ [6] · CACERES [10] (los de la C.A. elegida) | C.A. / REGA destino | `P_DDLProvincia` (postback) |
| 25 | Sistema Productivo | texto, solo lectura | – | – | REGA destino | `P_TxtSisProductivoDestino` |
| 26 | Tipo Explotación | desplegable **desactivado** | – | catálogo abajo | REGA destino | `P_DDLTipoExplotacion` (postback) |
| 27 | Clasificación Zootécnica | texto, solo lectura | – | – | REGA destino | `P_txtClasifZootecnicaDestino` |
| 28 | Restricciones de Entrada : / Calificaciones | texto y tabla | – | – | REGA destino | – |

**Catálogo «Tipo Explotación» (`P_DDLTipoExplotacion`):** GANADERA [1] · FERIA O MERCADO PERMANENTE [4] · MATADERO [5] · PLAZA DE TOROS PERMANENTE [6] · NUCLEO ZOOLOGICO [7] · MATANZAS DOMICILIARIAS [8] · CONCENTRACION EQUINA [10] · PASTOS [11] · TRATANTES (CON EXPLOTACIÓN) [12] · FERIA O MERCADO NO PERMANENTE [13] · PLAZA DE TOROS NO PERMANENTE [14] · CENTRO DE TESTAJE, SELECCIÓN Y REPRODUCCIÓN ANIMAL [15] · CENTRO DE AGRUPAMIENTO REPROD. PORCINOS DESVIEJE [16] · CENTRO DE TIPIFICACION DE OVINO O BOVINO [17] · OTROS CENTROS DE CONCENTRACION ANIMAL [18] · CENTRO DE INSPECCIÓN [19] · CENTRO DE CUARENTENA [20] · PUNTO DE PARADA [21] · OTRAS EXPLOTACIONES DE OCIO [22] · CENTRO DE ENSEÑANZA [23] · CENTROS DE ANIMALES DE EXPERIMENTACIÓN [24] · EXPLOTACIÓN PARA LA PRÁCTICA ECUESTRE [25] · CENTRO DE MUESTREO PARA LA LENGUA AZUL [26] · ESTABLECIMIENTOS DE TRANSFORMACIÓN AUTORIZADOS [27] · CENTROS DE DEPURACIÓN/EXPEDICIÓN O SIMILARES [28] · ESPACIOS NATURALES ACOTADOS [29] · DEPÓSITOS SEMENTALES EQUINOS Y CENTROS REPROD [30] · CONCENTRACIONES ÉQUIDOS CARÁCTER LÚDICO/CULTURAL [31] · EXPLOTACIONES NO COMERCIALES [32] · CENTROS DE ACOGIDA/RECOGIDA DE ÉQUIDOS ABANDONADOS [33] · EXPLOTACIONES DE CABALLOS SILVESTRES [34] · CENTRO DE SACRIFICIO DE URGENCIA [35] · ESPACIOS CINEGÉTICOS [37] · CENTRO DE CONCENTRACIÓN DE LIDIA [38] · EXPLOTACIÓN DE CABESTROS [39]

### Datos de la guía
| Orden | Etiqueta | Tipo | Obligatorio | Formato/valores | Depende de | id |
|---|---|---|---|---|---|---|
| 29 | **Fecha de Salida** | fecha + calendario + hora | **sí (\*)** | «Formato: dd/mm/aaaa». La hora viene rellena en formato `HH:MM:SS` | – | `P_txtFechaSalida` (postback), `P_imgIni`, `P_txtHoraSalida` (postback) |
| 30 | **Fecha de Llegada** | fecha + calendario + hora | **sí (\*)** | ídem | – | `P_txtFechaLlegada2` (postback), `P_ImgLlegada`, `P_txtHoraLlegada` (postback) |
| 31 | Medio de Transporte | desplegable | PENDIENTE | Camión [1] (**por defecto**) · Barco [2] · Tren [3] · Avión [4] · Conducción a pie [5] · Otros [99] | – | `P_DDLMedioTransporte` |
| 32 | Identificador del medio de transporte (Matrícula) | texto | PENDIENTE | libre | – | `P_txtMatricula` |
| 33 | Transportista (Código SIRENTRA) | texto | PENDIENTE | libre | – | `P_TxtTransportista` |
| 34 | Tipo Responsable del Movimiento | desplegable | PENDIENTE | Desconocido [0] · Titular de la Explotación [1] (**por defecto**) · Tratante-Comerciante-Operador [2] · Transportista [3] · Otros [99] | – | `P_DDLResponsable` |
| 35 | Aptitud del Movimiento | desplegable | PENDIENTE | SACRIFICIO [0] · VIDA [1] · REPRODUCC [2] (**por defecto**) · CEBO [3] | ¿tipo de destino? (PENDIENTE) | `P_DDLAptitud` (**postback**) |
| 36 | Guía Temporal | casilla | no | desmarcada | – | `P_ckTemporal` (**postback**) |
| 37 | Observaciones | área de texto | no | libre | – | `P_txtObservaciones` |

### Animales
| Orden | Etiqueta | Tipo | Obligatorio | Formato/valores | Depende de | id |
|---|---|---|---|---|---|---|
| 38 | Animales Solicitados / «Animales añadidos a la solicitud: `<N>`» | contador y listas | sí, al menos 1 (PENDIENTE) | Contenedores vacíos sin destino: `P_ucAnimales_PropiedadAnimales`, `P_ucAnimales_tbvalidados`, `P_ucAnimales_AnimalesaddSolicitud`, `P_ucAnimales_TblAnimales`. Las listas con los animales del ganadero no se copian | PENDIENTE (probablemente destino y fechas) | `P_ucAnimales_lblNumAnimalesAñadidos` |
| 39 | Entrada de Crotal por Lector: | texto | – | maxlength 15 (crotal electrónico) | – | `P_ucAnimales_txtLector` (**postback al cambiar**: añade el animal) |
| 40 | Añadiré documentación asociada | casilla | no | desmarcada | – | `P_chkAdjuntarDocumentos` |

**Campos ocultos técnicos** (4 px de ancho): `P_txtNuevaGuia`, `P_txtAyudaPorcina`, `P_txtIdPlanAyuda`, `P_txtQuiereAyuda`.

**Botones (no pulsados):**
- **«Solicitar»** (`P_BtnSolicitar`, submit con postback): **presenta la solicitud.**
- «Limpiar Formulario» (`P_BtnLimpiar`): vacía el formulario. Se ha usado al terminar.

**Variantes:**
- **Venta a otra explotación:** se pone un REGA de destino ganadero (Tipo Explotación GANADERA [1]). **PENDIENTE DE CONFIRMAR:** cómo se cargan y seleccionan los animales, y qué aptitud aplica.
- **Salida a matadero:** se pone un REGA de matadero (Tipo Explotación MATADERO [5]). **PENDIENTE DE CONFIRMAR:** si la aptitud pasa sola a SACRIFICIO, si aparecen campos nuevos y si cambian los obligatorios.

**Datos técnicos:** no hay iframes; la URL no cambia; muchos campos hacen postback (CA, REGA destino, fechas, horas, aptitud, guía temporal, lector). Hay que esperar a que termine cada recarga antes de seguir rellenando.

**Documento generado:** la **guía sanitaria de movimiento**. Se ve en **Documentos › Documentos** con «Tipo Trámite: Guías» (tabla `gvGuiasFirmadas`, botón de imagen «Mostrar Guía Firmada en PDF»). Es un **PDF firmado**. En qué momento queda disponible tras la solicitud (¿al validarla la OVZ?): PENDIENTE.

**Incidencias:** los movimientos intracomunitarios y las exportaciones no se pueden tramitar aquí.

---

## D. Confirmación de entrada

**Ruta:** elegir explotación «… - Bóvidos» → **Movimientos › Confirmación de Movimientos** → «Bóvidos».
URL: `/Movimientos/wgMovimientos.aspx` (la misma que la solicitud).
Pestañas:
- «Movimientos Internos»: «Esta página muestra los movimientos dentro de la CCAA.»; prefijo `…ucPestanasMovimientos_ctl02_`.
- «Movimientos Externos»: «… fuera de la CCAA.»; prefijo `…ucPestanasMovimientos_ctl05_`.

### Pantalla de búsqueda (igual en las dos pestañas)
| Orden | Etiqueta | Tipo | Obligatorio | Formato/valores | Depende de | id (sufijo) |
|---|---|---|---|---|---|---|
| 1 | Fecha Inicio: | fecha + calendario + limpiar | PENDIENTE | `dd/mm/aaaa`; por defecto, hace un año | – | `_cldInicio_txtFecha_cldInicio`, `_cldInicio_imgCalendariocldInicio` («Seleccionar fecha»), `_cldInicio_imgLimpiarcldInicio` («Lipiar campo») |
| 2 | Fecha Fin: / Fecha fin: | fecha + calendario + limpiar | PENDIENTE | `dd/mm/aaaa`; por defecto, hoy | – | `_cldFin_txtFecha_cldFin`, `_cldFin_imgCalendariocldFin`, `_cldFin_imgLimpiarcldFin` |
| – | Buscar | enlace con postback | – | solo busca | – | `_btnLinkBuscar` |

- Mensajes de la pestaña de internos, cuando no hay nada: «No existen Movimientos de entrada pendientes de Confirmar.» y «No existen Movimientos de salida pendientes de Confirmar.»
- En la pestaña de externos: «`<N>` movimientos pendientes de confirmar encontrados en … ms».
- Aviso fijo: el plazo para confirmar la entrada de animales es de **como máximo 7 días** desde la fecha de entrada.
- Contenedores: `div_filtro`, `div_guias`, `PnlAyuda`.

**Formulario de confirmación:** la cuenta **no tenía movimientos pendientes**, así que no se pudo ver. **PENDIENTE DE CONFIRMAR:** columnas de la lista de pendientes, cómo se elige el movimiento, campos (fecha real de llegada, animales recibidos o rechazados…), botones finales y documento generado. En «Documentos» existe el tipo «Confirmacion de Movimiento» [5].

---

## E. Consultas (solo lectura)

### E1. Animales de la explotación
**Ruta:** **Animales › Búsqueda de animales**. URL `/Animales/wgAnimales.aspx`. Prefijo `…ucPestanasAnimales_ctl02_`.
- Pestañas: «Animales del ganadero» · «Animales temporales en explotación».
- Filtros: «Código explotación» (`_ComboExplotacionesAdm`), «Especie» (`_ComboEspeciesAdm`), «Fecha:» (`_txtFechaBusqueda` + calendario `_imgB`), botón «Buscar» (`_btnLinkBuscarAdm`).
- Descarga en XML: «Animales pertenecientes a la explotación en el periodo:» `_txtFechaInicio` – `_txtFechaFin` (con calendarios `_imgIni`, `_imgFin`) y enlace `_descargaXML` (postback). Según la página, el XML contiene lo necesario para rellenar el **Libro de Registro Bovino**. Incluye instrucciones para importarlo en Microsoft Office Excel y LibreOffice Calc; se aconseja dar formato de texto a todas las celdas antes de importar.
- Las columnas del resultado de búsqueda quedan PENDIENTE (no se lanzó la búsqueda).

### E2. Trámites presentados y su estado
- **Trámites OVZ › Tareas OVZ** → «Bóvidos». URL `/Tareas/wgTareas.aspx`. Prefijo `ctl00_ContentPlaceHolder1_ucTareas1_`.
  - «Tipo de tarea» `_ddlTipoTarea` (**postback**): Impresión DI [1] · Solicitud de Anulacion [11] · Solicitud de Rechazo en Origen [12].
  - «Estado:» `_ddlEstados`: (vacío) [0] · PENDIENTE DE TRAMITACIÓN [1] · EN CURSO [2] · FINALIZADO [3] · RECHAZADO [4] · ANULADO [5].
  - «Fecha Inicio:» `_txtFechaIni` (+ `_imgIni`), «Fecha Fin:» `_txtFechaFin` (+ `_imgFin`), «Buscar» `_btnLinkBuscar`.
- **Movimientos › Solicitud de Movimientos** → «Bóvidos» → pestaña **«Consulta Solicitudes»** («Consulta de Solicitud de Movimiento. Esta página permite ver el estado de las solicitudes de movimiento.»). Prefijo `…ucPestanasMovimientos_ctl02_`.
  - «Estado:» `_DDLEstados`: (vacío) [0] · PENDIENTE DE TRAMITACIÓN [1] · EN CURSO [2] · FINALIZADO [3] · RECHAZADO [4] · ANULADO [5].
  - «Fecha Inicio Salida:» `_txtFechaIni` (+ `_imgIni`), «Fecha Fin Salida:» `_txtFechaFin` (+ `_imgFin`), «Buscar» `_btnLinkBuscar`.
  - Resultado en `_gvSolicitudes`. Sin datos muestra «No hay solicitudes.». Las columnas quedan PENDIENTE (no había solicitudes en el periodo).

### E3. Justificantes y guías
- **Documentos › Documentos** («Documentos Generados. Esta página nos muestra la lista de documetos que ha generado el usuario.»). URL `/Explotaciones/wgDocumentos.aspx`. Prefijo `ctl00_ContentPlaceHolder1_`.
  - «Tipo Trámite:» `DDLTipoTramite` (**obligatorio**: sin él sale «Es necesario informar un tipo de trámite»): Seleccione un elemento... [xx] · Alta Bovino [1] · Alta No Bovino [2] · Baja Animal [3] · Declaracion de Censo [4] · Confirmacion de Movimiento [5] · Solicitud de Movimiento [6] · Mod Declaracion de Censo [10] · Certificado de Vacunación [16] · Solicitud Anulacion Guias [21] · Solicitud rechazo animales en origen [22] · Traslado de colmenas para trashumancia [23] · Demora Crotalización [25] · Autorizacion datos para informacion telematica [26] · Plan de Reestructuración Porcino Ibérico [27] · Comunicación Resultados Saneamiento [31] · Certificado Ferias [32] · Declaración Responsable Veterinaria [33] · ACTUACION SANITARIA [35] · Guías [9].
  - «Fecha Inicio:» `cldInicio_txtFecha_cldInicio`, «Fecha Fin:» `cldFin_txtFecha_cldFin`, «Buscar» `btnLinkBuscar`.
  - Resultado para los tipos de trámite: tabla `GVDocumentos` con las columnas **Documento · Tipo · Fecha Creacion**. «Documento» es un enlace con postback que **descarga el justificante** (formato PENDIENTE; probablemente PDF). Pie `LblPieGrid`: «`<N>` documentos encontrados». Hay paginación.
  - Resultado para «Guías»: tabla `gvGuiasFirmadas` con las columnas **Solicitud · Fecha Salida · Fecha Llegada · Explotación Destino · Nº Guia · Guía**. La columna «Guía» tiene un botón de imagen (`filetype_pdf.png`) con el título **«Mostrar Guía Firmada en PDF»** (postback). Hay paginación.
- **Documentos › Resúmenes** («Esta página le permite generar los resúmenes de trámites generados por el usuario.»). URL `/Explotaciones/wgResumen.aspx`.
  - «Tipo Tramite:» `DDLTipoTramite`: Alta Bovino · Alta No Bovino · Baja Animal · Declaracion de Censo · Solicitud de Movimiento · Guías.
  - «Explotación:» `DDLExplotacion` (datos del ganadero), fechas `cldInicio…`/`cldFin…`.
  - Botón «Generar Resumen» (`btnLinkBuscar`). Formato del resumen: PENDIENTE.
- **Descargas › Descargas** (`/Descargas/wgDescargas.aspx`): documentos generales, no del ganadero. Entre ellos: teléfonos y correos de las OVZ para pedir cita previa, guía rápida de trazabilidad ovino-caprino, nota informativa sobre brucelosis ovina-caprina, **manual de OVZnet en PDF** («Ultimo Manual OVZNET.pdf»), formato del fichero de altas masivas de pequeños rumiantes, formulario ICA y una nota sobre tuberculosis bovina. Todos son enlaces directos a archivos.

---

## F. Anular o corregir un trámite presentado (solo descripción; no se ha pulsado nada)

- **Anular una guía o solicitud de movimiento:** **Movimientos › Anulación de guías** (`/Movimientos/wgSolicitudAnulacionGuias.aspx`, título «Solicitud de anulación de guías»).
  - Muestra una lista de guías anulables y su pie: «`<N>` guías encontradas en … ms». En esta cuenta salían 0, así que **no se ve ni el formulario ni el botón**.
  - Por el nombre, se presenta una *solicitud* de anulación que tramita la OVZ, no una anulación inmediata.
  - El seguimiento se hace en **Trámites OVZ › Tareas OVZ** con el tipo «Solicitud de Anulacion» [11] y el filtro de estado. El justificante aparece en **Documentos** con el tipo «Solicitud Anulacion Guias» [21].
- **Rechazar animales recibidos:** **Movimientos › Solicitud rechazo animales en origen** (no se ha abierto). Seguimiento en Tareas OVZ, tipo «Solicitud de Rechazo en Origen» [12]; documentos con el tipo [22].
- **Corregir o anular una baja o un alta ya presentada:** en las pantallas revisadas **no hay ninguna opción para ello** (ni en Trámites ni en Documentos). **PENDIENTE DE CONFIRMAR** si solo se puede hacer en la OVZ, de forma presencial. Puede consultarse el manual (Descargas).
- **Corregir antes de presentar:** en movimientos, «Limpiar Formulario»; en altas y bajas, «Volver».

---

## PENDIENTE DE CONFIRMAR

**General**
1. Si los índices de pestaña de los id (`ctl02`, `ctl05`) son siempre los mismos, también con otras especies.
2. Si el valor `ClaveEspecie=1` corresponde siempre a bovino.
3. Mensajes de error que devuelve el servidor al presentar con datos incompletos o erróneos.
4. Si los iconos de calendario abren una ventana emergente.

**A. Baja por muerte**
5. Si la casilla del crotal es obligatoria según OVZ y cómo lo indica.
6. Formato exacto de «Fecha de muerte» (se supone `dd/mm/aaaa`).
7. De dónde sale la OVZ preseleccionada (desplegable desactivado).
8. Formato del justificante de la baja (enlace en Documentos › «Baja Animal»; probablemente PDF) y si aparece algo en pantalla tras «Aceptar».

**B. Alta por nacimiento**
9. Qué cambia al marcar «Trashumante».
10. Si «Identificador de Lidia» solo es obligatorio con raza LIDIA.
11. Qué hace el postback de «Explotación Nacimiento».
12. Si las opciones de emisión del DI muestran u ocultan el desplegable de OVZ y los avisos de tasas.
13. De dónde sale la OVZ preseleccionada.
14. Justificante del alta (formato y descarga) y contenido del enlace de plazos.

**C. Solicitud de movimiento**
15. **Venta:** cómo se cargan y seleccionan los animales de la explotación (listas de `ucAnimales`), y qué aptitud aplica.
16. **Matadero:** cambios al poner un REGA de matadero (aptitud SACRIFICIO automática, campos nuevos u obligatorios distintos).
17. Si son obligatorios «Comunidad Autónoma», Medio de Transporte, Matrícula, Transportista, Tipo Responsable y Aptitud.
18. Qué hace «Guía Temporal».
19. En qué momento queda disponible la guía en PDF (al solicitar o al validar la OVZ).

**D. Confirmación de entrada**
20. Lista de movimientos pendientes, formulario de confirmación, campos, botones y documento generado (no había movimientos pendientes).

**E. Consultas**
21. Columnas del resultado de «Búsqueda de animales».
22. Columnas de `gvSolicitudes` en «Consulta Solicitudes».
23. Formato del resumen de «Resúmenes».
24. Formato del justificante en `GVDocumentos`.

**F. Anular o corregir**
25. Formulario y botones de «Anulación de guías» (no había guías anulables).
26. Contenido de «Solicitud rechazo animales en origen».
27. Si existe en OVZnet alguna vía para corregir o anular altas y bajas, o si solo se hace en la OVZ.

---

*Revisión final: este documento no contiene nombres, NIF, teléfonos, direcciones, crotales, códigos REGA, claves internas, usuarios, contraseñas, certificados ni capturas. Los valores de los catálogos son listas genéricas de OVZnet.*
