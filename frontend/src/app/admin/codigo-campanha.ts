/**
 * O trecho final do link (/c/{codigo}). Mesma regra de CodigoCampanha.java: mude as duas
 * juntas. O servidor tem a palavra final; aqui é para a prévia aparecer enquanto digita.
 */

export const CODIGO_MINIMO = 3;
export const CODIGO_MAXIMO = 60;

const semAcento = (texto: string) => texto.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();

/** "Grupo Ação & Cia." → "grupo-acao-cia". */
export function normalizarCodigo(texto: string): string {
  return semAcento(texto)
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, CODIGO_MAXIMO)
    .replace(/-+$/, '');
}

/** Enquanto digita: a mesma regra, mas deixa o hífen do fim ("grupo-" vira "grupo-alfa"). */
export function codigoDigitando(texto: string): string {
  return semAcento(texto)
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+/, '')
    .slice(0, CODIGO_MAXIMO);
}
