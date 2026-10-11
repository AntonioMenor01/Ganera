# B2 reducido — commit 3: callback de estado de Twilio (solo backend)

**Estado:** aprobado por Antonio el 2026-10-11 (D1–D3 tal cual, con los ajustes de abajo). Base: `main` en `f816f6e`
(backend 796, frontend 912 en 43 ficheros). Plan del envío: `2026-10-11-promptB2-reducido-responder-whatsapp.md` (D10).

## Objetivo

Twilio avisa después de enviar si una respuesta se entregó, se leyó o falló. Ganera actualiza `RespuestaWhatsapp.estado`.
El frontend ya pinta «Entregado», «Leído» y «No ha llegado»: no cambia.

## Diseño

1. **Al enviar:** si `ganera.twilio.status-callback-url` (`TWILIO_STATUS_CALLBACK_URL`) no está vacía, el envío pide a
   Twilio que avise ahí (`StatusCallback`). Vacía → se envía igual que ahora y la respuesta se queda en `ENVIADO`.
2. **Endpoint `POST /webhooks/twilio/whatsapp/estado`**, sin JWT. Orden:
   - **Tamaño:** más de 8 KB → `413` antes de leer el cuerpo (filtro solo para esta ruta: `getParameterMap` ya consume
     el cuerpo); un cuerpo sin longitud declarada se lee con tope.
   - **Configuración:** sin `TWILIO_AUTH_TOKEN` o sin la URL de callback → `503`, nada se toca (fail-closed, D3).
   - **Firma `X-Twilio-Signature`:** con todos los parámetros y la URL **configurada**, nunca la de la petición;
     ausente o inválida → `403`. Mismo patrón que el webhook de entrada (`RequestValidator` por petición).
   - **Datos mínimos:** sin `MessageSid` o sin `MessageStatus` → `400`.
   - **Búsqueda solo por SID:** de la petición solo se leen `MessageSid`, `MessageStatus` y `ErrorCode`. SID desconocido
     → `200` sin cambios, log `INFO` con solo el SID.
3. **Transiciones (función pura):** `queued`/`accepted`/`sending`/`sent` → `ENVIADO`; `delivered` → `ENTREGADO`; `read` →
   `LEIDO`; `failed`/`undelivered` → `FALLIDO` con su código; otro valor → sin cambios.
   **Orden (D1):** `ENVIANDO`/`SIN_CONFIRMAR` < `ENVIADO` < `ENTREGADO` < `LEIDO`. `FALLIDO` gana a `ENVIANDO`,
   `SIN_CONFIRMAR` y `ENVIADO`, no a `ENTREGADO`/`LEIDO`. Un `delivered`/`read` posterior a un `FALLIDO` lo corrige **y
   vacía `codigo_error_twilio`**. **D2:** un `SIN_CONFIRMAR` con SID guardado se corrige con el callback.
4. **Idempotencia y concurrencia:** una sola sentencia `UPDATE … WHERE twilio_message_sid = :sid AND estado IN
   (:anteriores)`. Repetidos, desordenados o simultáneos nunca retroceden. Sin tabla de eventos.
5. **`ErrorCode`:** se guarda solo si es numérico y de longitud acotada (p. ej. 1–6 dígitos); si no, se ignora el
   código y el estado se aplica igual.
6. **Aislamiento:** el SID es `UNIQUE` en toda la tabla. Es una búsqueda sin gestoría a propósito (la petición no trae
   tenant), segunda excepción documentada junto a `ContactoRepository.findByTelefono`.
7. **Seguridad (ajuste de Antonio):** `SecurityConfig` deja de abrir `/webhooks/**`: solo rutas exactas `POST
   /webhooks/twilio/whatsapp`, `POST /webhooks/stripe` y `POST /webhooks/twilio/whatsapp/estado`. Una ruta inventada
   bajo `/webhooks/` sin JWT nunca da `200` anónimo (`401`, o `404` con JWT).
8. **Logs:** solo SID, estado y código; nunca teléfonos ni texto.

## Limitaciones aceptadas para el MVP

- **Callback antes de que el SID esté guardado** (Twilio puede avisar antes de que el paso 3 del envío persista el SID):
  `200` sin cambios y log `INFO` con el SID. Si pasa en el piloto, añadir una consulta de estado en Twilio por SID.
- Si el SID no llega a guardarse nunca (fallo de BD tras aceptar Twilio), el callback no puede reconciliar esa fila.

## Tests (TDD, Twilio simulado)

Funciones puras (conversión, orden, `ErrorCode`); E2E con dos gestorías (el SID de A cambia solo A aunque la petición
traiga parámetros de B); SID desconocido y SID aún no guardado → `200` sin cambios; repetido, desordenado y dos a la
vez; `delivered` tras `FALLIDO` vacía el código; `ErrorCode` no numérico o largo se ignora; `413`, `503`, `403`, `400`;
ruta inventada bajo `/webhooks/` sin JWT ≠ `200`; los webhooks de entrada y de Stripe siguen públicos; logs sin
teléfonos ni texto.

**Regresiones que tienen que salir en rojo:** sin validar la firma; sin tope de tamaño; `UPDATE` que permita
retroceder; localizar la fila por un dato de la petición distinto del SID; volver a abrir `/webhooks/**`.
