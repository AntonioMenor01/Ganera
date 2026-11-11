/**
 * Forma canónica de un teléfono SOLO para comparar en el cliente si un número ya es contacto de la
 * gestoría. Los teléfonos del listado llegan en E.164 (TelefonoNormalizador del backend, p. ej.
 * "+34612345678"); lo que la persona teclea puede venir con espacios, puntos, guiones o sin el
 * prefijo 34. Esto NO valida ni normaliza para enviar (la fuente de verdad es el backend): solo
 * reduce a dígitos y deja los móviles/fijos españoles de 9 cifras con su "34" delante, de modo que
 * "612 345 678", "+34612345678" y "0034 612345678" coincidan.
 */
export function telefonoCanonico(valor: string): string {
  let digitos = valor.replace(/\D+/g, "");
  if (digitos === "") return "";
  // 0034… (prefijo internacional con 00) → 34…
  if (digitos.startsWith("0034")) digitos = digitos.slice(2);
  // Número español de 9 cifras sin prefijo de país → se le antepone el 34.
  if (digitos.length === 9 && /^[6789]/.test(digitos)) digitos = `34${digitos}`;
  return digitos;
}

/** true si dos teléfonos son el mismo número a efectos de comparación en el cliente. */
export function mismoTelefono(a: string, b: string): boolean {
  const ca = telefonoCanonico(a);
  return ca !== "" && ca === telefonoCanonico(b);
}
