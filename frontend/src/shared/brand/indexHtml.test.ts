import { readFileSync } from "node:fs"
import path from "node:path"
import { describe, expect, it } from "vitest"

/** index.html es la plantilla que Vite sirve y construye; se lee y se parsea con el DOMParser de jsdom. */
const documento = new DOMParser().parseFromString(
  readFileSync(path.join(import.meta.dirname, "../../../index.html"), "utf-8"),
  "text/html",
)

describe("index.html", () => {
  it("declara el idioma español", () => {
    expect(documento.documentElement.getAttribute("lang")).toBe("es")
  })

  it("titula la pestaña Ganera", () => {
    expect(documento.title).toBe("Ganera")
  })

  it("usa favicon-64.png como único favicon", () => {
    const iconos = documento.querySelectorAll('link[rel="icon"]')
    expect(iconos).toHaveLength(1)
    expect(iconos[0].getAttribute("type")).toBe("image/png")
    expect(iconos[0].getAttribute("href")).toBe("/favicon-64.png")
  })
})
