import "@testing-library/jest-dom/vitest"
import { cleanup } from "@testing-library/react"
import { afterAll, afterEach, beforeAll, beforeEach } from "vitest"
import { setAuthToken, setUnauthorizedHandler } from "@/shared/api/authSession"
import { configurarAutoAperturaPrimerosPasos } from "@/features/onboarding/primerosPasos"
import { server } from "./server"

// Ninguna petición sale sin simular: una sin handler hace fallar el test.
beforeAll(() => server.listen({ onUnhandledRequest: "error" }))

// La guía de primeros pasos se abre sola en producción, pero en los tests que montan el layout/app
// no debe aparecer el overlay encima. Se desactiva por defecto; el test de la autoapertura la activa.
beforeEach(() => configurarAutoAperturaPrimerosPasos(false))
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
