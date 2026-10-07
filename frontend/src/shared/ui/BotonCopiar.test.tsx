import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BotonCopiar } from "./BotonCopiar";

const AVISO = "Selecciona el texto y cópialo (Ctrl+C o ⌘C)";

// `userEvent.setup()` instala su propio portapapeles en `navigator`: cada test pone el suyo DESPUÉS
// de `setup()` y aquí se deja `navigator.clipboard` como estaba antes del test. Los espías los
// restaura `restoreMocks: true` (vitest.config.ts).
let descriptorOriginal: PropertyDescriptor | undefined;

beforeEach(() => {
  descriptorOriginal = Object.getOwnPropertyDescriptor(navigator, "clipboard");
});

afterEach(() => {
  vi.useRealTimers();
  if (descriptorOriginal) {
    Object.defineProperty(navigator, "clipboard", descriptorOriginal);
  } else {
    delete (navigator as unknown as Record<string, unknown>).clipboard;
  }
});

function ponerPortapapeles(valor: unknown) {
  Object.defineProperty(navigator, "clipboard", { value: valor, configurable: true, writable: true });
}

function portapapelesQueFunciona() {
  const writeText = vi.fn<(texto: string) => Promise<void>>().mockResolvedValue(undefined);
  ponerPortapapeles({ writeText });
  return writeText;
}

function portapapelesPendiente() {
  let resolver: () => void = () => {};
  const writeText = vi.fn<(texto: string) => Promise<void>>().mockImplementation(
    () =>
      new Promise<void>((resolve) => {
        resolver = resolve;
      }),
  );
  ponerPortapapeles({ writeText });
  return { writeText, resolver: () => resolver() };
}

/** Tests con reloj falso: `fireEvent` dentro de `act`, sin `userEvent` (su espera interna de un
 * `setTimeout(0)` se colgaría con el reloj de Vitest). El `act` asíncrono deja correr la promesa de
 * `writeText`; el anuncio llega en el siguiente tick y no se avanza aquí. */
async function pulsarConRelojFalso(boton: HTMLElement) {
  await act(async () => {
    fireEvent.click(boton);
  });
}

function siguienteTick() {
  act(() => vi.advanceTimersByTime(0));
}

function region() {
  return screen.getByRole("status");
}

describe("BotonCopiar", () => {
  it("tiene el nombre accesible «Copiar {etiqueta}» y el texto visible «Copiar»", () => {
    render(<BotonCopiar texto="01/02/2026" etiqueta="Fecha de muerte" />);

    const boton = screen.getByRole("button", { name: "Copiar Fecha de muerte" });
    expect(boton).toHaveTextContent("Copiar");
  });

  it("monta la región aria-live desde el principio, vacía", () => {
    render(<BotonCopiar texto="x" etiqueta="NIF" />);

    expect(region()).toHaveAttribute("aria-live", "polite");
    expect(region()).toBeEmptyDOMElement();
  });

  it("copia exactamente el texto, con espacios y caracteres especiales", async () => {
    const user = userEvent.setup();
    const writeText = portapapelesQueFunciona();
    const texto = "  ES 0100  ñ<b>&\"'\t\nfin  ";
    render(<BotonCopiar texto={texto} etiqueta="Crotal" />);

    await user.click(screen.getByRole("button", { name: "Copiar Crotal" }));

    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText).toHaveBeenCalledWith(texto);
  });

  it("marca «copiado» durante 2 s, lo anuncia, vuelve al estado normal y vacía la región", async () => {
    vi.useFakeTimers();
    portapapelesQueFunciona();
    render(<BotonCopiar texto="ES010000001234" etiqueta="Crotal" />);
    const boton = screen.getByRole("button", { name: "Copiar Crotal" });

    await pulsarConRelojFalso(boton);
    siguienteTick();

    expect(boton).toHaveAttribute("data-copiado");
    // WCAG 2.5.3: el texto visible no cambia (el nombre accesible lo contiene siempre).
    expect(boton).toHaveTextContent(/^Copiar$/);
    expect(region()).toHaveTextContent("Copiado: Crotal");

    act(() => vi.advanceTimersByTime(1999));
    expect(boton).toHaveAttribute("data-copiado");

    act(() => vi.advanceTimersByTime(1));
    expect(boton).toHaveTextContent("Copiar");
    expect(boton).not.toHaveAttribute("data-copiado");
    expect(region()).toBeEmptyDOMElement();
    expect(screen.getByRole("button", { name: "Copiar Crotal" })).toBe(boton);
  });

  it("reinicia el plazo de 2 s si se pulsa otra vez mientras dice «Copiado»", async () => {
    vi.useFakeTimers();
    const writeText = portapapelesQueFunciona();
    render(<BotonCopiar texto="ES010000001234" etiqueta="Crotal" />);
    const boton = screen.getByRole("button", { name: "Copiar Crotal" });

    await pulsarConRelojFalso(boton);
    act(() => vi.advanceTimersByTime(1500));
    await pulsarConRelojFalso(boton);
    expect(writeText).toHaveBeenCalledTimes(2);

    // 1500 + 1500 = 3000 ms desde el primer clic: sin reinicio ya habría vuelto a «Copiar».
    act(() => vi.advanceTimersByTime(1500));
    expect(boton).toHaveAttribute("data-copiado");

    act(() => vi.advanceTimersByTime(500));
    expect(boton).toHaveTextContent("Copiar");
    expect(boton).not.toHaveAttribute("data-copiado");
  });

  it("copiar dos veces seguidas vacía la región y vuelve a anunciar", async () => {
    vi.useFakeTimers();
    portapapelesQueFunciona();
    render(<BotonCopiar texto="x" etiqueta="NIF" />);
    const boton = screen.getByRole("button", { name: "Copiar NIF" });

    await pulsarConRelojFalso(boton);
    siguienteTick();
    expect(region()).toHaveTextContent("Copiado: NIF");

    act(() => vi.advanceTimersByTime(500));
    await pulsarConRelojFalso(boton);
    expect(region()).toBeEmptyDOMElement();

    siguienteTick();
    expect(region()).toHaveTextContent("Copiado: NIF");
  });

  it("fallar dos veces seguidas vacía la región y vuelve a anunciar", async () => {
    vi.useFakeTimers();
    ponerPortapapeles(undefined);
    render(<BotonCopiar texto="x" etiqueta="NIF" />);
    const boton = screen.getByRole("button", { name: "Copiar NIF" });

    await pulsarConRelojFalso(boton);
    siguienteTick();
    expect(region()).toHaveTextContent(`No se ha podido copiar NIF. ${AVISO}`);

    await pulsarConRelojFalso(boton);
    expect(region()).toBeEmptyDOMElement();

    siguienteTick();
    expect(region()).toHaveTextContent(`No se ha podido copiar NIF. ${AVISO}`);
  });

  it("limpia el temporizador al desmontar", async () => {
    vi.useFakeTimers();
    portapapelesQueFunciona();
    const errorConsola = vi.spyOn(console, "error").mockImplementation(() => {});
    const { unmount } = render(<BotonCopiar texto="x" etiqueta="NIF" />);

    await pulsarConRelojFalso(screen.getByRole("button", { name: "Copiar NIF" }));
    siguienteTick();
    expect(vi.getTimerCount()).toBe(1);

    unmount();
    expect(vi.getTimerCount()).toBe(0);
    act(() => vi.advanceTimersByTime(5000));
    expect(errorConsola).not.toHaveBeenCalled();
  });

  it("desmontar con la copia pendiente no deja temporizadores vivos al resolverse", async () => {
    vi.useFakeTimers();
    const { resolver } = portapapelesPendiente();
    const errorConsola = vi.spyOn(console, "error").mockImplementation(() => {});
    const { unmount } = render(<BotonCopiar texto="x" etiqueta="NIF" />);

    await pulsarConRelojFalso(screen.getByRole("button", { name: "Copiar NIF" }));
    unmount();
    await act(async () => resolver());

    expect(vi.getTimerCount()).toBe(0);
    expect(errorConsola).not.toHaveBeenCalled();
  });

  it("sin Clipboard API muestra el texto seleccionado, con el foco, y avisa de copiarlo a mano", async () => {
    const user = userEvent.setup();
    ponerPortapapeles(undefined);
    render(<BotonCopiar texto="ES010000001234" etiqueta="Crotal" />);

    await user.click(screen.getByRole("button", { name: "Copiar Crotal" }));

    const campo = screen.getByRole<HTMLInputElement>("textbox", { name: "Crotal para copiar" });
    expect(campo).toHaveValue("ES010000001234");
    expect(campo).toHaveAttribute("readonly");
    expect(campo).toHaveAttribute("data-slot", "input");
    expect(campo).toHaveFocus();
    expect(campo.selectionStart).toBe(0);
    expect(campo.selectionEnd).toBe("ES010000001234".length);
    expect(campo).toHaveAccessibleDescription(AVISO);
    expect(screen.getByText(AVISO)).toBeVisible();
    await waitFor(() =>
      expect(region()).toHaveTextContent(`No se ha podido copiar Crotal. ${AVISO}`),
    );
    // Sin «Copiado» si no se ha copiado nada.
    expect(screen.getByRole("button", { name: "Copiar Crotal" })).not.toHaveAttribute("data-copiado");
  });

  it("si la promesa se rechaza, cae al texto seleccionado y al aviso", async () => {
    const user = userEvent.setup();
    const writeText = vi
      .fn<(texto: string) => Promise<void>>()
      .mockRejectedValue(new DOMException("denegado", "NotAllowedError"));
    ponerPortapapeles({ writeText });
    render(<BotonCopiar texto="12345678Z" etiqueta="NIF" />);

    await user.click(screen.getByRole("button", { name: "Copiar NIF" }));

    const campo = await screen.findByRole("textbox", { name: "NIF para copiar" });
    expect(campo).toHaveValue("12345678Z");
    expect(campo).toHaveFocus();
    await waitFor(() =>
      expect(region()).toHaveTextContent(`No se ha podido copiar NIF. ${AVISO}`),
    );
    expect(writeText).toHaveBeenCalledTimes(1);
  });

  it("si writeText lanza al llamarlo, también cae al texto seleccionado", async () => {
    const user = userEvent.setup();
    ponerPortapapeles({
      writeText: () => {
        throw new TypeError("no");
      },
    });
    render(<BotonCopiar texto="ES0101" etiqueta="Código REGA" />);

    await user.click(screen.getByRole("button", { name: "Copiar Código REGA" }));

    expect(await screen.findByRole("textbox", { name: "Código REGA para copiar" })).toHaveFocus();
  });

  it("al volver a pulsar tras un fallo, vuelve a seleccionar el texto", async () => {
    const user = userEvent.setup();
    ponerPortapapeles(undefined);
    render(<BotonCopiar texto="ES010000001234" etiqueta="Crotal" />);
    const boton = screen.getByRole("button", { name: "Copiar Crotal" });

    await user.click(boton);
    const campo = screen.getByRole<HTMLInputElement>("textbox", { name: "Crotal para copiar" });
    campo.setSelectionRange(0, 0);
    await user.click(boton);

    expect(campo).toHaveFocus();
    expect(campo.selectionEnd).toBe("ES010000001234".length);
  });

  it("si tras un fallo copia bien, quita el campo de copia manual y dice «Copiado»", async () => {
    const user = userEvent.setup();
    const writeText = vi
      .fn<(texto: string) => Promise<void>>()
      .mockRejectedValueOnce(new Error("no"))
      .mockResolvedValue(undefined);
    ponerPortapapeles({ writeText });
    render(<BotonCopiar texto="x" etiqueta="NIF" />);
    const boton = screen.getByRole("button", { name: "Copiar NIF" });

    await user.click(boton);
    expect(await screen.findByRole("textbox")).toBeInTheDocument();
    await user.click(boton);

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(boton).toHaveAttribute("data-copiado");
    await waitFor(() => expect(region()).toHaveTextContent("Copiado: NIF"));
  });

  it("ignora un segundo clic mientras la copia está pendiente", async () => {
    const user = userEvent.setup();
    const { writeText, resolver } = portapapelesPendiente();
    render(<BotonCopiar texto="x" etiqueta="NIF" />);
    const boton = screen.getByRole("button", { name: "Copiar NIF" });

    await user.click(boton);
    await user.click(boton);
    expect(writeText).toHaveBeenCalledTimes(1);

    await act(async () => resolver());
    expect(boton).toHaveAttribute("data-copiado");

    await user.click(boton);
    expect(writeText).toHaveBeenCalledTimes(2);
  });

  it("nunca escribe en la consola el texto copiado", async () => {
    const user = userEvent.setup();
    const espias = (["log", "info", "warn", "error", "debug"] as const).map((m) =>
      vi.spyOn(console, m).mockImplementation(() => {}),
    );
    ponerPortapapeles({ writeText: vi.fn().mockRejectedValue(new Error("no")) });
    render(<BotonCopiar texto="SECRETO-123" etiqueta="NIF" />);

    await user.click(screen.getByRole("button", { name: "Copiar NIF" }));
    await screen.findByRole("textbox");

    for (const espia of espias) {
      for (const llamada of espia.mock.calls) {
        expect(JSON.stringify(llamada.map(String))).not.toContain("SECRETO-123");
      }
    }
  });

  it("avisa con onCopiado solo cuando la copia sale bien", async () => {
    const user = userEvent.setup();
    portapapelesQueFunciona();
    const onCopiado = vi.fn();
    render(<BotonCopiar texto="ES010000001234" etiqueta="Crotal" onCopiado={onCopiado} />);

    await user.click(screen.getByRole("button", { name: "Copiar Crotal" }));

    expect(onCopiado).toHaveBeenCalledTimes(1);
  });

  it("no avisa con onCopiado si la copia falla", async () => {
    const user = userEvent.setup();
    ponerPortapapeles(undefined);
    const onCopiado = vi.fn();
    render(<BotonCopiar texto="ES010000001234" etiqueta="Crotal" onCopiado={onCopiado} />);

    await user.click(screen.getByRole("button", { name: "Copiar Crotal" }));

    expect(onCopiado).not.toHaveBeenCalled();
  });

  it("aplica className al botón", () => {
    render(<BotonCopiar texto="x" etiqueta="NIF" className="clase-de-prueba" />);

    expect(screen.getByRole("button", { name: "Copiar NIF" })).toHaveClass("clase-de-prueba");
  });
});
