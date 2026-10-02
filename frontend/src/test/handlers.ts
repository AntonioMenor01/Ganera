import type { RequestHandler } from "msw"

/**
 * Handlers compartidos por defecto. Vacío a propósito: cada test declara lo que necesita con
 * server.use(...), y cualquier petición sin handler falla (onUnhandledRequest: "error").
 */
export const handlers: RequestHandler[] = []
