import { http, HttpResponse, type RequestHandler } from "msw"
import { apiUrl } from "./apiBaseUrl"

/**
 * Handlers compartidos por defecto. Casi vacío a propósito: cada test declara lo que necesita con
 * server.use(...), y cualquier petición sin handler falla (onUnhandledRequest: "error").
 *
 * Excepción (escalón 2, T5): la ficha del ganadero pide SIEMPRE sus documentos de OVZ al montarse
 * (`GET /ganaderos/{id}/documentos`). Para no obligar a cada test de la ficha a declararlo, aquí se
 * responde con una lista vacía por defecto (la sección queda oculta); un test que pruebe los documentos
 * lo sobreescribe con su propio `server.use(...)`, que tiene prioridad.
 */
export const handlers: RequestHandler[] = [
  http.get(apiUrl("/ganaderos/:id/documentos"), () => HttpResponse.json([])),
]
