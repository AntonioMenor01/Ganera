import { afterEach, describe, expect, it, vi } from "vitest";
import { abrirFichaOvz, NOMBRE_VENTANA_FICHA, rutaFichaOvz } from "./abrirFichaOvz";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("abrirFichaOvz", () => {
  it("la ruta de la ficha es /tramites/{id}/ovz", () => {
    expect(rutaFichaOvz(128)).toBe("/tramites/128/ovz");
  });

  it("abre la ficha en la ventana con nombre fijo, sin noopener (D3: así hereda la sesión), y la enfoca", () => {
    const ventana = { focus: vi.fn() };
    const open = vi.spyOn(window, "open").mockReturnValue(ventana as unknown as Window);

    abrirFichaOvz(128);

    expect(open).toHaveBeenCalledTimes(1);
    const [url, nombre, caracteristicas] = open.mock.calls[0];
    expect(url).toBe("/tramites/128/ovz");
    expect(nombre).toBe(NOMBRE_VENTANA_FICHA);
    // Con noopener (o noreferrer) el navegador NO copia sessionStorage y la ficha caería al login.
    expect(String(caracteristicas ?? "")).not.toMatch(/noopener|noreferrer/i);
    expect(ventana.focus).toHaveBeenCalledTimes(1);
  });

  it("si el navegador bloquea la ventana, abre la ficha en la misma pestaña", () => {
    vi.spyOn(window, "open").mockReturnValue(null);
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, assign });

    abrirFichaOvz(7);

    expect(assign).toHaveBeenCalledWith("/tramites/7/ovz");
  });
});
