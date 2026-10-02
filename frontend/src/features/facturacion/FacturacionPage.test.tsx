import { render, screen, within } from "@testing-library/react"
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom"
import { describe, expect, it } from "vitest"
import type { AppLayoutContext } from "@/shared/layout/AppLayout"
import { FacturacionPage } from "./FacturacionPage"
import type { EstadoSuscripcion, SuscripcionEstado } from "./types"

type Suscripcion = AppLayoutContext["suscripcion"]

function suscripcion(extra: Partial<Suscripcion> = {}): Suscripcion {
  return { estado: null, cargando: false, error: null, recargar: () => {}, ...extra }
}

function estado(valor: EstadoSuscripcion, puedeAprobarTramites: boolean): SuscripcionEstado {
  return { estado: valor, puedeAprobarTramites, explotacionesContratadas: 3 }
}

/** Monta la página con el contexto que le daría AppLayout (sin pedir nada al backend). */
function pantalla(contexto: Suscripcion) {
  return (
    <MemoryRouter>
      <Routes>
        <Route element={<Outlet context={{ suscripcion: contexto } satisfies AppLayoutContext} />}>
          <Route index element={<FacturacionPage />} />
        </Route>
      </Routes>
    </MemoryRouter>
  )
}

describe("FacturacionPage: estado de carga (Task 10, 4.1.3)", () => {
  it("«Cargando…» es un estado siempre montado: el mismo nodo se vacía al terminar", () => {
    const { rerender } = render(pantalla(suscripcion({ cargando: true })))
    const estadoCarga = screen.getByRole("status")
    expect(estadoCarga).toHaveTextContent("Cargando…")

    rerender(pantalla(suscripcion({ estado: estado("ACTIVA", true) })))
    expect(screen.getByRole("status")).toBe(estadoCarga)
    expect(estadoCarga).toBeEmptyDOMElement()
  })
})

describe("FacturacionPage: badge del estado con los pares de DESIGN.md (Task 10)", () => {
  const CASOS: [EstadoSuscripcion, boolean, string, string][] = [
    ["ACTIVA", true, "Activa", "bg-success"],
    ["TRIAL", true, "Periodo de prueba", "bg-success"],
    ["IMPAGO_GRACIA", true, "Pago fallido (periodo de gracia)", "bg-warning"],
    ["TRIAL_EXPIRADO_SIN_PAGO", false, "Prueba expirada, sin pago", "bg-danger"],
    ["SUSPENDIDA", false, "Suspendida", "bg-danger"],
    ["CANCELADA", false, "Cancelada", "bg-danger"],
  ]

  it.each(CASOS)("%s usa su par de estado", (valor, puede, etiqueta, clase) => {
    render(pantalla(suscripcion({ estado: estado(valor, puede) })))
    const badge = screen.getByText(etiqueta)
    expect(badge).toHaveClass(clase)
    expect(badge).not.toHaveClass("bg-secondary")
    expect(badge).not.toHaveClass("bg-destructive/10")
  })

  it("un estado desconocido sale tal cual, con el contorno neutro", () => {
    const raro = { estado: "OTRO", puedeAprobarTramites: true, explotacionesContratadas: null }
    render(pantalla(suscripcion({ estado: raro as unknown as SuscripcionEstado })))
    expect(screen.getByText("OTRO")).toHaveClass("border-border")
  })
})

describe("FacturacionPage: sin aviso repetido (Task 10)", () => {
  it("si no puede aprobar, la tarjeta lo dice en una línea sin role=alert (el banner ya avisa)", () => {
    render(pantalla(suscripcion({ estado: estado("SUSPENDIDA", false) })))
    const linea = screen.getByText(
      "Actualiza el pago de tu suscripción para poder volver a aprobar trámites.",
    )
    expect(linea.tagName).toBe("P")
    expect(linea.closest("[role='alert']")).toBeNull()
    const tarjeta = linea.closest("[data-slot='card']") as HTMLElement
    expect(within(tarjeta).queryByRole("alert")).not.toBeInTheDocument()
    expect(screen.queryByText("No puedes aprobar trámites ahora mismo")).not.toBeInTheDocument()
  })

  it("si puede aprobar, no aparece esa línea", () => {
    render(pantalla(suscripcion({ estado: estado("ACTIVA", true) })))
    expect(
      screen.queryByText("Actualiza el pago de tu suscripción para poder volver a aprobar trámites."),
    ).not.toBeInTheDocument()
  })
})
