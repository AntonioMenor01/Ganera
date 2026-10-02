import "@testing-library/jest-dom/vitest"
import { cleanup } from "@testing-library/react"
import { afterAll, afterEach, beforeAll } from "vitest"
import { setAuthToken, setUnauthorizedHandler } from "@/shared/api/authSession"
import { server } from "./server"

// Ninguna petición sale sin simular: una sin handler hace fallar el test.
beforeAll(() => server.listen({ onUnhandledRequest: "error" }))
afterEach(() => {
  server.resetHandlers()
  // Sin globals de Vitest, Testing Library no desmonta solo entre tests.
  cleanup()
  // Nada de sesión pasa de un test a otro: almacenamiento del navegador y estado de módulo.
  sessionStorage.clear()
  localStorage.clear()
  setAuthToken(null)
  setUnauthorizedHandler(null)
})
afterAll(() => server.close())
