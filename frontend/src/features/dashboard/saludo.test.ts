import { describe, expect, it } from "vitest";
import { fechaLargaMadrid, horaEnMadrid, nombrePila, saludo, saludoPorHora } from "./saludo";

describe("saludoPorHora", () => {
  it.each([
    [6, "Buenos días"],
    [9, "Buenos días"],
    [13, "Buenos días"],
    [14, "Buenas tardes"],
    [18, "Buenas tardes"],
    [20, "Buenas tardes"],
    [21, "Buenas noches"],
    [23, "Buenas noches"],
    [0, "Buenas noches"],
    [5, "Buenas noches"],
  ])("a las %i da «%s»", (hora, esperado) => {
    expect(saludoPorHora(hora)).toBe(esperado);
  });
});

describe("horaEnMadrid", () => {
  it("convierte a la hora de pared de Madrid, no a la del equipo", () => {
    // 2026-01-15 12:30 UTC = 13:30 en Madrid (invierno, UTC+1).
    expect(horaEnMadrid(new Date("2026-01-15T12:30:00Z"))).toBe(13);
    // 2026-07-15 12:30 UTC = 14:30 en Madrid (verano, UTC+2).
    expect(horaEnMadrid(new Date("2026-07-15T12:30:00Z"))).toBe(14);
  });
});

describe("saludo", () => {
  it("combina franja y hora de Madrid", () => {
    // 18:00 UTC en verano = 20:00 en Madrid → tarde.
    expect(saludo(new Date("2026-07-30T18:00:00Z"))).toBe("Buenas tardes");
    // 23:00 UTC en verano = 01:00 del día siguiente en Madrid → noche.
    expect(saludo(new Date("2026-07-30T23:00:00Z"))).toBe("Buenas noches");
  });
});

describe("nombrePila", () => {
  it("toma la primera palabra", () => {
    expect(nombrePila("Lucía Martín")).toBe("Lucía");
    expect(nombrePila("  Ana  ")).toBe("Ana");
  });
  it("vacío si no hay nombre utilizable", () => {
    expect(nombrePila("")).toBe("");
    expect(nombrePila("   ")).toBe("");
    expect(nombrePila(null)).toBe("");
    expect(nombrePila(undefined)).toBe("");
  });
});

describe("fechaLargaMadrid", () => {
  it("da el día de la semana, el día, el mes y el año, con mayúscula inicial", () => {
    // 2026-10-30 es viernes.
    expect(fechaLargaMadrid(new Date("2026-10-30T10:00:00Z"))).toBe("Viernes, 30 de octubre de 2026");
  });
  it("usa la fecha de Madrid: un instante de madrugada no se va al día anterior del equipo", () => {
    // 2026-10-30 23:30 UTC = 2026-10-31 00:30 en Madrid → sábado 31.
    expect(fechaLargaMadrid(new Date("2026-10-30T23:30:00Z"))).toBe("Sábado, 31 de octubre de 2026");
  });
});
