import { existsSync, readFileSync } from "node:fs"
import path from "node:path"
import { describe, expect, it } from "vitest"

/**
 * Web instalable (despliegue en Clever, T4): lo mínimo para que Chrome y Edge ofrezcan «Instalar Ganera».
 * Solo instalable: sin service worker, sin modo sin conexión ni caché de datos.
 */
const raiz = path.join(import.meta.dirname, "../../..")
const publico = path.join(raiz, "public")

const documento = new DOMParser().parseFromString(readFileSync(path.join(raiz, "index.html"), "utf-8"), "text/html")

type Icono = { src: string; sizes: string; type: string; purpose?: string }
type Manifiesto = {
  name: string
  short_name: string
  lang: string
  start_url: string
  scope: string
  display: string
  background_color: string
  theme_color: string
  icons: Icono[]
  [otro: string]: unknown
}
const manifiesto = JSON.parse(readFileSync(path.join(publico, "manifest.webmanifest"), "utf-8")) as Manifiesto

/** Ancho y alto de un PNG, leídos de su cabecera IHDR (bytes 16–23). */
function tamanoPng(ruta: string): { ancho: number; alto: number } {
  const bytes = readFileSync(ruta)
  expect(bytes.subarray(0, 8).toString("hex")).toBe("89504e470d0a1a0a")
  return { ancho: bytes.readUInt32BE(16), alto: bytes.readUInt32BE(20) }
}

describe("manifest.webmanifest", () => {
  it("se llama Ganera, en español, y abre la app a pantalla propia desde la raíz", () => {
    expect(manifiesto.name).toBe("Ganera")
    expect(manifiesto.short_name).toBe("Ganera")
    expect(manifiesto.lang).toBe("es")
    expect(manifiesto.start_url).toBe("/")
    expect(manifiesto.scope).toBe("/")
    expect(manifiesto.display).toBe("standalone")
  })

  it("usa los colores de marca de la app (Fondo de fondo; Superficie, la de la barra superior, en la barra de la ventana)", () => {
    expect(manifiesto.background_color).toBe("#f3f2f2")
    expect(manifiesto.theme_color).toBe("#eae9e9")
  })

  it("trae los iconos de 192 y 512 que piden Chrome y Edge, y uno maskable de 512", () => {
    const resumen = manifiesto.icons.map((i) => `${i.sizes} ${i.purpose ?? "any"}`)
    expect(resumen).toEqual(["192x192 any", "512x512 any", "512x512 maskable"])
  })

  it("cada icono existe en public/, es PNG y mide lo que declara", () => {
    for (const icono of manifiesto.icons) {
      expect(icono.type).toBe("image/png")
      expect(icono.src.startsWith("/")).toBe(true)
      const ruta = path.join(publico, icono.src)
      expect(existsSync(ruta), icono.src).toBe(true)
      const [ancho, alto] = icono.sizes.split("x").map(Number)
      expect(tamanoPng(ruta)).toEqual({ ancho, alto })
    }
  })

  it("no pide nada más (sin service worker, atajos ni capturas)", () => {
    expect(Object.keys(manifiesto).sort()).toEqual(
      ["background_color", "display", "icons", "lang", "name", "scope", "short_name", "start_url", "theme_color"].sort(),
    )
  })
})

describe("index.html enlaza la web instalable", () => {
  it("enlaza el manifiesto una sola vez", () => {
    const enlaces = documento.querySelectorAll('link[rel="manifest"]')
    expect(enlaces).toHaveLength(1)
    expect(enlaces[0].getAttribute("href")).toBe("/manifest.webmanifest")
  })

  it("declara el color de la barra igual que el manifiesto", () => {
    const meta = documento.querySelectorAll('meta[name="theme-color"]')
    expect(meta).toHaveLength(1)
    expect(meta[0].getAttribute("content")).toBe(manifiesto.theme_color)
  })

  it("trae el icono de 180 para «Añadir a pantalla de inicio» en iOS", () => {
    const enlaces = documento.querySelectorAll('link[rel="apple-touch-icon"]')
    expect(enlaces).toHaveLength(1)
    const href = enlaces[0].getAttribute("href") ?? ""
    expect(href).toBe("/apple-touch-icon.png")
    expect(tamanoPng(path.join(publico, href))).toEqual({ ancho: 180, alto: 180 })
  })

  it("no registra ningún service worker (solo instalable, sin modo sin conexión)", () => {
    const html = readFileSync(path.join(raiz, "index.html"), "utf-8")
    expect(html).not.toMatch(/serviceWorker/)
    expect(existsSync(path.join(publico, "sw.js"))).toBe(false)
  })
})
