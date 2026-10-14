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
    expect(badge).toHaveClass("bg-neutral", "text-neutral-foreground")
    expect(badge).not.toHaveClass("bg-primary")
  })

  it("RECHAZADO con motivoRechazo SIN_TRAMITE: «Descartado», neutro (outline), nunca el rojo", () => {
    render(<BadgeEstadoTramite estado="RECHAZADO" motivoRechazo="SIN_TRAMITE" />)
    const badge = screen.getByText("Descartado")
    expect(badge).toHaveClass("bg-neutral", "text-neutral-foreground")
    expect(badge).not.toHaveClass("bg-danger")
    expect(screen.queryByText("Rechazado")).not.toBeInTheDocument()
  })

  it("RECHAZADO con un motivo desconocido o sin motivo: «Rechazado» en rojo, como siempre", () => {
    render(
      <>
        <BadgeEstadoTramite estado="RECHAZADO" motivoRechazo="OTRO" />
        <BadgeEstadoTramite estado="RECHAZADO" motivoRechazo={null} />
      </>,
    )
    for (const badge of screen.getAllByText("Rechazado")) expect(badge).toHaveClass("bg-danger")
    expect(screen.queryByText("Descartado")).not.toBeInTheDocument()
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
    expect(screen.getByText("No está en el inventario")).toHaveClass("bg-neutral", "text-neutral-foreground")
  })

  it("NO_ENCONTRADO sin completo (D5b) es neutro (outline)", () => {
    render(<BadgeResolucionCrotal crotal={{ resolucion: "NO_ENCONTRADO" }} />)
    expect(screen.getByText("No está en el inventario")).toHaveClass("bg-neutral", "text-neutral-foreground")
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
    expect(screen.getByText("DUPLICADO")).toHaveClass("bg-neutral", "text-neutral-foreground")
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
    expect(badge).toHaveClass("bg-neutral", "text-neutral-foreground")
    expect(badge).not.toHaveClass("bg-success")
  })
})

describe("BadgeEstadoTramite: icono a la izquierda (diseño de Álvaro, fase 1, T4)", () => {
  it.each([
    ["PENDIENTE_EXTRACCION", null, "Pendiente de extracción", "sparkles"],
    ["PENDIENTE_REVISION", null, "Pendiente de revisión", "eye"],
    ["APROBADO", null, "Aprobado · falta OVZ", "check"],
    ["HECHO_EN_OVZ", null, "Hecho en OVZ", "circle-check"],
    ["RECHAZADO", null, "Rechazado", "x"],
    ["RECHAZADO", "SIN_TRAMITE", "Descartado", "archive"],
  ])("%s (motivo %s): «%s» con el icono %s, decorativo y antes del texto", (estado, motivoRechazo, etiqueta, icono) => {
    render(<BadgeEstadoTramite estado={estado} motivoRechazo={motivoRechazo} />)
    const badge = screen.getByText(etiqueta)
    const svgs = badge.querySelectorAll("svg")
    expect(svgs).toHaveLength(1)
    expect(svgs[0]).toHaveAttribute("aria-hidden", "true")
    expect(svgs[0]).toHaveAttribute("data-icono", icono)
    expect(badge.firstChild).toBe(svgs[0])
    // El nombre accesible y el texto siguen siendo solo la etiqueta.
    expect(badge).toHaveTextContent(new RegExp(`^${etiqueta}$`))
  })

  it("el badge con icono no tiene nombre extra: un rol con nombre lo encuentra solo por la etiqueta", () => {
    render(
      <div role="group" aria-labelledby="b">
        <span id="b">
          <BadgeEstadoTramite estado="APROBADO" />
        </span>
      </div>,
    )
    expect(screen.getByRole("group")).toHaveAccessibleName("Aprobado · falta OVZ")
  })

  it.each(["EN_PROCESO", "EJECUTADO_OVZ", "ERROR_OVZ", "PAUSADO"])("%s no lleva icono", (estado) => {
    const { container } = render(<BadgeEstadoTramite estado={estado} />)
    expect(container.querySelector("svg")).toBeNull()
  })
})
