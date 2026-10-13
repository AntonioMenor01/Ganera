const LETRA = /\p{L}/u;

function primeraLetra(texto: string): string | undefined {
  return texto.match(LETRA)?.[0];
}

/**
 * Iniciales del usuario para el cuadro de la barra: primera letra de la primera palabra y de la
 * última (si hay más de una), en mayúsculas y con tildes. Las palabras sin letras («-», «2») no
 * cuentan. Se normaliza a NFC para no perder una tilde que llegue descompuesta. Sin nombre utilizable, la primera letra del email; sin nada, «?».
 */
export function iniciales(nombre: string, email: string): string {
  const letras = nombre
    .normalize("NFC")
    .split(/\s+/u)
    .map(primeraLetra)
    .filter((letra): letra is string => letra !== undefined);
  const resultado =
    letras.length === 0
      ? (primeraLetra(email.normalize("NFC")) ?? "?")
      : letras.length === 1
        ? letras[0]
        : letras[0] + letras[letras.length - 1];
  return resultado.toLocaleUpperCase("es");
}
