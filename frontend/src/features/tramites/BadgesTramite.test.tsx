import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { BadgeEstadoTramite, BadgeResolucionCrotal, BadgeRolContacto } from "./BadgesTramite"

describe("BadgeEstadoTramite", () => {
  it("muestra la etiqueta, no el enum, con el par de color de su variante", () => {
    render(<BadgeEstadoTramite estado="ERROR_OVZ" />)
    const badge = screen.getByText("Error en OVZ.net")
    expect(badge).toHaveClass("bg-danger", "text-danger-foreground")
    expect(screen.queryByText("ERROR_OVZ")).not.toBeInTheDocument()
  })

  it("un estado desconocido sale tal cual en un badge neutro", () => {
    render(<BadgeEstadoTramite estado="PAUSADO" />)
    const badge = screen.getByText("PAUSADO")
    expect(badge).toHaveClass("border-border", "text-foreground")
    expect(badge).not.toHaveClass("bg-primary")
  })
})

describe("BadgeResolucionCrotal", () => {
  it.each([
    ["EN_INVENTARIO", "En inventario", "bg-success"],
    ["AMBIGUO", "Varios animales coinciden", "bg-warning"],
    ["SIN_EXPLOTACION", "Falta la explotación", "bg-warning"],
  ])("%s → %s", (resolucion, etiqueta, clase) => {
    render(<BadgeResolucionCrotal crotal={{ resolucion, completo: false }} />)
    expect(screen.getByText(etiqueta)).toHaveClass(clase)
  })

  it("NO_ENCONTRADO completo es neutro (outline)", () => {
    render(<BadgeResolucionCrotal crotal={{ resolucion: "NO_ENCONTRADO", completo: true }} />)
    expect(screen.getByText("No está en el inventario")).toHaveClass("border-border", "text-foreground")
  })

  it("NO_ENCONTRADO sin completo (D5b) es neutro (outline)", () => {
    render(<BadgeResolucionCrotal crotal={{ resolucion: "NO_ENCONTRADO" }} />)
    expect(screen.getByText("No está en el inventario")).toHaveClass("border-border", "text-foreground")
  })

  it("NO_ENCONTRADO incompleto es ámbar y lo dice en el texto, no solo con color", () => {
    render(<BadgeResolucionCrotal crotal={{ resolucion: "NO_ENCONTRADO", completo: false }} />)
    expect(screen.getByText("No está en el inventario · incompleto")).toHaveClass(
      "bg-warning",
      "text-warning-foreground",
    )
  })

  it("una resolución desconocida sale tal cual en un badge neutro", () => {
    render(<BadgeResolucionCrotal crotal={{ resolucion: "DUPLICADO" }} />)
    expect(screen.getByText("DUPLICADO")).toHaveClass("border-border")
  })
})

describe("BadgeRolContacto", () => {
  it("Titular con el par de éxito", () => {
    render(<BadgeRolContacto rol="TITULAR" />)
    expect(screen.getByText("Titular")).toHaveClass("bg-success", "text-success-foreground")
    expect(screen.queryByText("TITULAR")).not.toBeInTheDocument()
  })

  it("Empleado con el outline neutro", () => {
    render(<BadgeRolContacto rol="EMPLEADO" />)
    const badge = screen.getByText("Empleado")
    expect(badge).toHaveClass("border-border", "text-foreground")
    expect(badge).not.toHaveClass("bg-success")
  })
})
