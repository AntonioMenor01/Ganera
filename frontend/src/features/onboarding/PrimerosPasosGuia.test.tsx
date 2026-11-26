import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it } from "vitest"
import { AuthProvider } from "@/shared/auth/AuthContext"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { PrimerosPasosProvider } from "./PrimerosPasosContexto"
import { PrimerosPasosGuia } from "./PrimerosPasosGuia"
import { PillContinuar } from "./PillContinuar"
import { CoachmarkPaso } from "./CoachmarkPaso"
import { configurarAutoAperturaPrimerosPasos, guardarProgreso } from "./primerosPasos"

const USUARIO = { id: 42, email: "nuevo@gestoria.es", nombre: "Nuevo", gestoriaId: 3, activo: true }

function prefs(ovzHecho: boolean) {
  const base = {
    oficinaDi: null,
    oficinaMer: null,
    emisionDi: null,
    tipoIdentificador: null,
    telefonoMovimiento: null,
    medioTransporte: null,
    matricula: null,
    transportistaSirentra: null,
    tipoResponsable: null,
  }
  return ovzHecho ? { ...base, oficinaDi: "DI-01", tipoResponsable: "TITULAR" } : base
}

function paginaCount(n: number) {
  return HttpResponse.json({ content: [], totalElements: n, totalPages: n > 0 ? 1 : 0, number: 0, size: 1 })
}

function mockDeteccion({ ovz = false, ganaderos = 0, explotaciones = 0 } = {}) {
  server.use(
    http.get(apiUrl("/configuracion/preferencias-ovz"), () => HttpResponse.json(prefs(ovz))),
    http.get(apiUrl("/ganaderos"), () => paginaCount(ganaderos)),
    http.get(apiUrl("/explotaciones"), () => paginaCount(explotaciones)),
  )
}

function montar(url = "/") {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[url]}>
        <PrimerosPasosProvider>
          <PrimerosPasosGuia />
          <PillContinuar />
          <Routes>
            <Route path="/" element={<h1>INICIO</h1>} />
            <Route
              path="/configuracion"
              element={
                <>
                  <h1>CONFIG</h1>
                  <CoachmarkPaso pantalla="/configuracion" />
                </>
              }
            />
            <Route path="/ganaderos" element={<h1>GANADEROS</h1>} />
          </Routes>
        </PrimerosPasosProvider>
      </MemoryRouter>
    </AuthProvider>,
  )
}

beforeEach(() => {
  sessionStorage.setItem("ganera.token", "tok")
  server.use(http.get(apiUrl("/auth/me"), () => HttpResponse.json(USUARIO)))
})

afterEach(() => {
  sessionStorage.clear()
})

describe("Onboarding guiado", () => {
  it("se autoabre la primera vez y refleja el progreso detectado de datos reales", async () => {
    configurarAutoAperturaPrimerosPasos(true)
    mockDeteccion({ ovz: true, ganaderos: 1, explotaciones: 0 })
    montar()
    // Autoapertura tras cargar la sesión.
    expect(await screen.findByRole("dialog")).toBeInTheDocument()
    // ovz + ganadero detectados hechos = 2 de 8.
    await waitFor(() => expect(screen.getByText(/2 de 8 hechos/)).toBeInTheDocument())
  })

  it("la CTA de un paso navega a la pantalla y NO termina: queda la píldora para continuar", async () => {
    configurarAutoAperturaPrimerosPasos(true)
    mockDeteccion({ ovz: false })
    montar()
    await screen.findByRole("dialog")

    // Paso 1 (bienvenida) → Siguiente → paso ovz, que muestra su CTA.
    await userEvent.click(screen.getByRole("button", { name: /Siguiente/ }))
    await userEvent.click(await screen.findByRole("button", { name: /Ir a Configuración/ }))

    // Navegó a Configuración, la guía se cerró y aparece la píldora para continuar.
    expect(await screen.findByRole("heading", { name: "CONFIG" })).toBeInTheDocument()
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
    expect(await screen.findByRole("button", { name: /Continuar configuración/ })).toBeInTheDocument()
  })

  it("sin OVZ no deja terminar: «Empezar» deshabilitado y aviso", async () => {
    configurarAutoAperturaPrimerosPasos(true)
    mockDeteccion({ ovz: false, ganaderos: 1, explotaciones: 1 })
    montar()
    await screen.findByRole("dialog")
    // Avanzar hasta el último paso (7 «Siguiente»).
    for (let i = 0; i < 7; i++) await userEvent.click(screen.getByRole("button", { name: /Siguiente/ }))
    expect(screen.getByText(/Falta configurar tus datos de OVZ/)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Empezar" })).toBeDisabled()
  })

  it("con OVZ hecho, «Empezar» se habilita", async () => {
    configurarAutoAperturaPrimerosPasos(true)
    mockDeteccion({ ovz: true, ganaderos: 1, explotaciones: 1 })
    montar()
    await screen.findByRole("dialog")
    for (let i = 0; i < 7; i++) await userEvent.click(screen.getByRole("button", { name: /Siguiente/ }))
    expect(await screen.findByRole("button", { name: "Empezar" })).toBeEnabled()
  })

  it("el coachmark sale en su pantalla mientras el paso no esté hecho y se puede descartar", async () => {
    // Sin autoapertura (para no tapar con el overlay), pero con progreso → onboarding en curso.
    configurarAutoAperturaPrimerosPasos(false)
    guardarProgreso(USUARIO.id, { confirmados: [], coachDescartados: [], terminada: false })
    mockDeteccion({ ovz: false })
    montar("/configuracion")

    const coach = await screen.findByRole("complementary", { name: "Ayuda de primeros pasos" })
    expect(coach).toHaveTextContent("Datos de OVZ")
    await userEvent.click(within(coach).getByRole("button", { name: "Descartar esta ayuda" }))
    await waitFor(() =>
      expect(screen.queryByRole("complementary", { name: "Ayuda de primeros pasos" })).not.toBeInTheDocument(),
    )
  })

  it("la píldora reabre la guía por donde iba", async () => {
    configurarAutoAperturaPrimerosPasos(false)
    guardarProgreso(USUARIO.id, { confirmados: [], coachDescartados: [], terminada: false })
    mockDeteccion({ ovz: false })
    montar("/")

    const pill = await screen.findByRole("button", { name: /Continuar configuración/ })
    await userEvent.click(pill)
    expect(await screen.findByRole("dialog")).toBeInTheDocument()
  })
})
