/**
 * A curva da carreira: sobe, estabiliza no platô e, se nada muda, começa a cair.
 * Mesmas funções do backend (CurvaCarreira.java). Se mudar aqui, mude lá também,
 * para a tela e o PDF mostrarem o mesmo desenho.
 */

const ALTURA_MAXIMA = 1.25;
const T_INDICE_100 = 0.28;
const T_INDICE_0 = 0.92;
const AMOSTRAS = 96;

export function alturaNaCurva(t: number): number {
  const subida = 1 / (1 + Math.exp(-11 * (t - 0.3)));
  const queda = t > 0.66 ? ((t - 0.66) / 0.34) ** 2 * 0.42 : 0;
  return subida - queda;
}

export function alturaNaProximaCurva(t: number): number {
  return 0.55 + 0.68 / (1 + Math.exp(-13 * (t - 0.8)));
}

/** Índice alto fica na subida; índice baixo, depois do platô, já perdendo altura. */
export function posicaoNaCurva(indice: number): number {
  return T_INDICE_0 - (indice / 100) * (T_INDICE_0 - T_INDICE_100);
}

export interface Ponto {
  x: number;
  y: number;
}

export interface GeometriaCurva {
  largura: number;
  altura: number;
  base: number;
  margemX: number;
  curva: string;
  proximaCurva: string;
  area: string;
  x: (t: number) => number;
  ponto: (t: number) => Ponto;
  rotulos: { texto: string; x: number }[];
}

export function geometriaCurva(largura: number, altura: number): GeometriaCurva {
  const margemX = 8;
  const margemTopo = 14;
  const margemBase = 34;
  const base = altura - margemBase;
  const x = (t: number) => margemX + t * (largura - 2 * margemX);
  const y = (v: number) => base - (v / ALTURA_MAXIMA) * (altura - margemTopo - margemBase);

  const caminho = (inicio: number, fim: number, f: (t: number) => number) =>
    Array.from({ length: AMOSTRAS + 1 }, (_, i) => {
      const t = inicio + ((fim - inicio) * i) / AMOSTRAS;
      return `${i === 0 ? 'M' : 'L'}${x(t).toFixed(1)} ${y(f(t)).toFixed(1)}`;
    }).join(' ');

  const curva = caminho(0, 1, alturaNaCurva);
  return {
    largura,
    altura,
    base,
    margemX,
    curva,
    proximaCurva: caminho(0.55, 1, alturaNaProximaCurva),
    area: `${curva} L${x(1).toFixed(1)} ${base} L${x(0).toFixed(1)} ${base} Z`,
    x,
    ponto: (t) => ({ x: x(t), y: y(alturaNaCurva(t)) }),
    rotulos: [
      { texto: 'Subida', x: x(0.24) },
      { texto: 'Platô', x: x(0.62) },
      { texto: 'Queda', x: x(0.91) },
    ],
  };
}
