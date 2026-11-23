import { correction, generate } from 'lean-qr';

import { TemaMarca } from '../../compartilhado/tema-marca';

/**
 * QR Code com a identidade do Diagnóstico: módulos em abeto, marcadores de canto
 * arredondados e o símbolo da marca no centro. A mesma geometria vira SVG (para a
 * gráfica, sem perder qualidade) e PNG (desenhado em canvas).
 */

export const COR_MODULO = '#0F2B26';
const COR_CURVA = '#FF5B37';

/** Cores do código: módulos escuros (leitura) e o símbolo central nas cores da marca. */
export interface CoresQr {
  modulo: string;
  simbolo: string;
  curva: string;
}

export const CORES_PADRAO: CoresQr = { modulo: COR_MODULO, simbolo: COR_MODULO, curva: COR_CURVA };

function luminancia(hex: string): number {
  const canal = (i: number) => {
    const v = parseInt(hex.slice(i, i + 2), 16) / 255;
    return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * canal(1) + 0.7152 * canal(3) + 0.0722 * canal(5);
}

function contraste(a: string, b: string): number {
  const [la, lb] = [luminancia(a), luminancia(b)];
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/**
 * Módulos na tinta da marca (escura, então a leitura não muda); o símbolo no centro com a cor
 * principal e a curva na secundária (ou na cor do texto do botão, se as duas se confundirem).
 */
export function coresDoQr(tema: TemaMarca): CoresQr {
  return {
    modulo: tema.tinta,
    simbolo: tema.acao,
    curva: contraste(tema.destaque, tema.acao) >= 3 ? tema.destaque : tema.sobreAcao,
  };
}

/** O símbolo de compartilhado/marca.ts, numa caixa de 26 × 26. */
const MARCA = { lado: 26, raio: 7, curva: 'M5 19 C 9 19, 9 8, 13 8 S 17 8, 21 12', traco: 2.6 };

/** Fração da largura do código ocupada pelo símbolo. Com correção H (30%), fica folgado. */
const PROPORCAO_LOGO = 0.2;

export interface GeometriaQr {
  /** Largura total em módulos, já com a margem branca ao redor. */
  lado: number;
  /** Módulos de dados, como um único caminho (sem emendas entre quadrados). */
  modulos: string;
  /** Os três marcadores de canto (preencher com fill-rule evenodd). */
  marcadores: string;
  /** Onde fica o símbolo da marca, em módulos. */
  logo: { x: number; y: number; lado: number; escala: number } | null;
}

/** Quadrado arredondado como caminho SVG. */
function quadrado(x: number, y: number, lado: number, r: number): string {
  const f = (n: number) => +n.toFixed(3);
  return (
    `M${f(x + r)} ${f(y)}H${f(x + lado - r)}A${r} ${r} 0 0 1 ${f(x + lado)} ${f(y + r)}` +
    `V${f(y + lado - r)}A${r} ${r} 0 0 1 ${f(x + lado - r)} ${f(y + lado)}` +
    `H${f(x + r)}A${r} ${r} 0 0 1 ${f(x)} ${f(y + lado - r)}V${f(y + r)}A${r} ${r} 0 0 1 ${f(x + r)} ${f(y)}Z`
  );
}

/** Anel 7 × 7 com o miolo 3 × 3: o desenho que a câmera procura para achar o código. */
function marcador(x: number, y: number): string {
  return quadrado(x, y, 7, 2.2) + quadrado(x + 1, y + 1, 5, 1.4) + quadrado(x + 2, y + 2, 3, 0.9);
}

export function geometriaQr(texto: string, { margem = 4, logo = true } = {}): GeometriaQr {
  const qr = generate(texto, { minCorrectionLevel: correction.H });
  const n = qr.size;

  // Área livre no centro, sempre com número ímpar de módulos para ficar centralizada.
  let ladoLogo = logo ? Math.round(n * PROPORCAO_LOGO) : 0;
  if (ladoLogo > 0 && ladoLogo % 2 === 0) ladoLogo += 1;
  const inicioLogo = (n - ladoLogo) / 2;
  const reservado = (x: number, y: number) =>
    (x < 7 && y < 7) ||
    (x >= n - 7 && y < 7) ||
    (x < 7 && y >= n - 7) ||
    (ladoLogo > 0 && x >= inicioLogo && x < inicioLogo + ladoLogo && y >= inicioLogo && y < inicioLogo + ladoLogo);

  // Junta os módulos vizinhos de cada linha num retângulo só: caminho menor.
  const partes: string[] = [];
  for (let y = 0; y < n; y++) {
    let x = 0;
    while (x < n) {
      if (!qr.get(x, y) || reservado(x, y)) {
        x++;
        continue;
      }
      const inicio = x;
      while (x < n && qr.get(x, y) && !reservado(x, y)) x++;
      partes.push(`M${inicio + margem} ${y + margem}h${x - inicio}v1h${inicio - x}z`);
    }
  }

  const marcadores = [
    [0, 0],
    [n - 7, 0],
    [0, n - 7],
  ]
    .map(([x, y]) => marcador(x + margem, y + margem))
    .join('');

  // O símbolo fica meio módulo para dentro da área livre, com respiro branco.
  const ladoSimbolo = ladoLogo - 1;
  return {
    lado: n + 2 * margem,
    modulos: partes.join(''),
    marcadores,
    logo:
      ladoLogo > 0
        ? {
            x: inicioLogo + margem + 0.5,
            y: inicioLogo + margem + 0.5,
            lado: ladoSimbolo,
            escala: ladoSimbolo / MARCA.lado,
          }
        : null,
  };
}

/** Arquivo SVG completo, com fundo branco. */
/** Logomarca no centro do código (só a de proporção quase quadrada; a larga ficaria minúscula). */
export interface LogoQr {
  largura: number;
  altura: number;
}

export const logoCabeNoQr = (logo: LogoQr | null | undefined): logo is LogoQr =>
  !!logo && logo.largura <= logo.altura * 1.8 && logo.altura <= logo.largura * 1.8;

/** Onde a logo fica dentro da área livre do centro: cartão branco e a imagem com respiro. */
export function caixaDaLogo(area: NonNullable<GeometriaQr['logo']>, logo: LogoQr) {
  const respiro = area.lado * 0.12;
  const livre = area.lado - respiro * 2;
  const escala = Math.min(livre / logo.largura, livre / logo.altura);
  const largura = logo.largura * escala;
  const altura = logo.altura * escala;
  return { x: area.x + (area.lado - largura) / 2, y: area.y + (area.lado - altura) / 2, largura, altura };
}

export function svgDoQr(
  geo: GeometriaQr,
  tamanhoPx = 1024,
  cores: CoresQr = CORES_PADRAO,
  logo: (LogoQr & { href: string }) | null = null,
): string {
  const caixa = geo.logo && logoCabeNoQr(logo) ? caixaDaLogo(geo.logo, logo) : null;
  const simbolo = caixa && geo.logo && logo
    ? `<rect x="${geo.logo.x}" y="${geo.logo.y}" width="${geo.logo.lado}" height="${geo.logo.lado}" rx="${geo.logo.lado * 0.22}" fill="#FFFFFF"/>` +
      `<image href="${logo.href}" x="${+caixa.x.toFixed(3)}" y="${+caixa.y.toFixed(3)}" width="${+caixa.largura.toFixed(3)}" height="${+caixa.altura.toFixed(3)}" preserveAspectRatio="xMidYMid meet"/>`
    : geo.logo
    ? `<g transform="translate(${geo.logo.x} ${geo.logo.y}) scale(${+geo.logo.escala.toFixed(5)})">` +
      `<rect width="${MARCA.lado}" height="${MARCA.lado}" rx="${MARCA.raio}" fill="${cores.simbolo}"/>` +
      `<path d="${MARCA.curva}" fill="none" stroke="${cores.curva}" stroke-width="${MARCA.traco}" stroke-linecap="round"/></g>`
    : '';
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${geo.lado} ${geo.lado}" width="${tamanhoPx}" height="${tamanhoPx}">` +
    `<rect width="${geo.lado}" height="${geo.lado}" fill="#FFFFFF"/>` +
    `<path d="${geo.modulos}" fill="${cores.modulo}" shape-rendering="crispEdges"/>` +
    `<path d="${geo.marcadores}" fill="${cores.modulo}" fill-rule="evenodd"/>` +
    simbolo +
    `</svg>`
  );
}

/** Desenha o código (com o fundo branco e a margem) num quadrado de `tamanho` px. */
export function desenharQr(
  ctx: CanvasRenderingContext2D,
  geo: GeometriaQr,
  x: number,
  y: number,
  tamanho: number,
  cores: CoresQr = CORES_PADRAO,
  logo: (LogoQr & { fonte: CanvasImageSource }) | null = null,
) {
  const escala = tamanho / geo.lado;
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(escala, escala);
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, geo.lado, geo.lado);
  ctx.fillStyle = cores.modulo;
  ctx.fill(new Path2D(geo.modulos));
  ctx.fill(new Path2D(geo.marcadores), 'evenodd');
  if (geo.logo && logoCabeNoQr(logo)) {
    const caixa = caixaDaLogo(geo.logo, logo);
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(geo.logo.x, geo.logo.y, geo.logo.lado, geo.logo.lado, geo.logo.lado * 0.22);
    ctx.fill();
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(logo.fonte, caixa.x, caixa.y, caixa.largura, caixa.altura);
  } else if (geo.logo) {
    ctx.translate(geo.logo.x, geo.logo.y);
    ctx.scale(geo.logo.escala, geo.logo.escala);
    ctx.fillStyle = cores.simbolo;
    ctx.fill(new Path2D(quadrado(0, 0, MARCA.lado, MARCA.raio)));
    ctx.strokeStyle = cores.curva;
    ctx.lineWidth = MARCA.traco;
    ctx.lineCap = 'round';
    ctx.stroke(new Path2D(MARCA.curva));
  }
  ctx.restore();
}

export function novoCanvas(largura: number, altura: number): { canvas: HTMLCanvasElement; ctx: CanvasRenderingContext2D } {
  const canvas = document.createElement('canvas');
  canvas.width = largura;
  canvas.height = altura;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas indisponível');
  return { canvas, ctx };
}

export function pngDoCanvas(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolver, rejeitar) =>
    canvas.toBlob((blob) => (blob ? resolver(blob) : rejeitar(new Error('Falha ao gerar a imagem'))), 'image/png'),
  );
}

/** Só o código, em PNG quadrado. */
export function pngDoQr(
  geo: GeometriaQr,
  tamanho = 1200,
  cores: CoresQr = CORES_PADRAO,
  logo: (LogoQr & { fonte: CanvasImageSource }) | null = null,
): Promise<Blob> {
  const { canvas, ctx } = novoCanvas(tamanho, tamanho);
  desenharQr(ctx, geo, 0, 0, tamanho, cores, logo);
  return pngDoCanvas(canvas);
}
