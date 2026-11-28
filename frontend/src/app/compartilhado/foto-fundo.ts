/**
 * Como a foto de fundo da empresa aparece: cortada para caber (object-fit: cover) com o ponto de
 * foco sempre à vista, e um véu na cor da marca por cima, para o texto branco continuar legível
 * com qualquer foto. A mesma conta serve às telas do RH e à prévia do painel.
 */

/** Onde a foto aparece: a metade da entrada no computador, o topo da página do RH e a faixa do celular. */
export type UsoDaFoto = 'entrada' | 'capa' | 'celular';

const mistura = (cor: string, quanto: number) => `color-mix(in srgb, ${cor} ${quanto}%, transparent)`;

/** O véu na cor da marca (tinta: a cor escura do tema, que já passa no contraste com branco). */
export function veuDaFoto(tinta: string, uso: UsoDaFoto): string {
  switch (uso) {
    case 'entrada':
      // Escuro o bastante para o título e a legenda saltarem; a foto ainda aparece, como textura.
      return `linear-gradient(180deg, ${mistura(tinta, 76)} 0%, ${mistura(tinta, 84)} 42%, ${mistura(tinta, 95)} 100%)`;
    case 'capa':
      // O nome fica à esquerda: ali o véu é mais forte; à direita, a foto respira.
      return `linear-gradient(90deg, ${mistura(tinta, 92)} 0%, ${mistura(tinta, 74)} 48%, ${mistura(tinta, 38)} 100%)`;
    case 'celular':
      return `linear-gradient(180deg, ${mistura(tinta, 50)} 0%, ${mistura(tinta, 68)} 45%, ${mistura(tinta, 92)} 100%)`;
  }
}

/** "30% 45%": o ponto de foco como object-position. */
export function posicaoDaFoto(f: { focoX: number; focoY: number }): string {
  return `${f.focoX}% ${f.focoY}%`;
}
