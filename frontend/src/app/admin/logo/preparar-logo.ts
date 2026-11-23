/**
 * Prepara a logomarca no navegador antes do envio: o backend recebe sempre um PNG pequeno e
 * limpo, venha o arquivo como vier (PNG, JPG, WebP ou SVG).
 *  1. desenha a imagem (SVG vira PNG aqui: nenhum script de SVG chega ao servidor);
 *  2. tira o fundo branco de imagens sem transparência (só o que encosta na borda);
 *  3. corta as margens vazias e reduz para no máximo 1000 px;
 *  4. sugere as cores da marca a partir dos pixels do logo.
 */

export interface LogoPreparada {
  png: Blob;
  largura: number;
  altura: number;
  /** Endereço local para pré-visualizar (URL.createObjectURL). */
  url: string;
  /** Cores sugeridas, a mais presente primeiro. */
  cores: string[];
  /** O fundo branco foi removido (dá para desfazer). */
  semFundo: boolean;
  /** A imagem original não tinha transparência (o fundo branco pode ser removido). */
  opaca: boolean;
}

export const TIPOS_ACEITOS = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'];
const TAMANHO_MAXIMO = 8 * 1024 * 1024;
const LADO_DESENHO = 1400;
const LADO_FINAL = 1000;

export async function prepararLogo(arquivo: File, removerFundo = true): Promise<LogoPreparada> {
  const svg = arquivo.type === 'image/svg+xml' || arquivo.name.toLowerCase().endsWith('.svg');
  if (!svg && !TIPOS_ACEITOS.includes(arquivo.type)) {
    throw new Error('Use uma imagem PNG, JPG, WebP ou SVG.');
  }
  if (arquivo.size > TAMANHO_MAXIMO) {
    throw new Error('A imagem passa de 8 MB. Use um arquivo menor.');
  }

  const imagem = svg ? await carregarSvg(arquivo) : await carregar(arquivo);
  // SVG é vetor: desenha grande. Imagem comum: só reduz, nunca amplia (ampliar só borra).
  const maior = Math.max(imagem.largura, imagem.altura);
  const fator = svg ? LADO_DESENHO / maior : Math.min(1, LADO_DESENHO / maior);
  const largura = Math.max(1, Math.round(imagem.largura * fator));
  const altura = Math.max(1, Math.round(imagem.altura * fator));

  const tela = document.createElement('canvas');
  tela.width = largura;
  tela.height = altura;
  const ctx = tela.getContext('2d', { willReadFrequently: true });
  if (!ctx) throw new Error('Seu navegador não conseguiu processar a imagem.');
  ctx.drawImage(imagem.fonte, 0, 0, largura, altura);
  imagem.liberar();

  const pixels = ctx.getImageData(0, 0, largura, altura);
  const opaca = semTransparencia(pixels);
  const semFundo = opaca && removerFundo && tirarFundoBranco(pixels);
  if (semFundo) ctx.putImageData(pixels, 0, 0);

  const caixa = conteudo(pixels, opaca && !semFundo);
  if (!caixa) throw new Error('A imagem parece estar vazia. Confira o arquivo.');

  // Corta as margens (com um respiro pequeno) e reduz para o tamanho final.
  const folga = Math.round(Math.max(caixa.largura, caixa.altura) * 0.02);
  const x = Math.max(0, caixa.x - folga);
  const y = Math.max(0, caixa.y - folga);
  const w = Math.min(largura - x, caixa.largura + folga * 2);
  const h = Math.min(altura - y, caixa.altura + folga * 2);
  const reducao = Math.min(1, LADO_FINAL / Math.max(w, h));
  const final = document.createElement('canvas');
  final.width = Math.max(8, Math.round(w * reducao));
  final.height = Math.max(8, Math.round(h * reducao));
  const ctxFinal = final.getContext('2d', { willReadFrequently: true })!;
  ctxFinal.imageSmoothingQuality = 'high';
  ctxFinal.drawImage(tela, x, y, w, h, 0, 0, final.width, final.height);

  const png = await new Promise<Blob>((resolver, rejeitar) =>
    final.toBlob((b) => (b ? resolver(b) : rejeitar(new Error('Não foi possível gerar a imagem.'))), 'image/png'),
  );
  return {
    png,
    largura: final.width,
    altura: final.height,
    url: URL.createObjectURL(png),
    cores: coresDaMarca(ctxFinal.getImageData(0, 0, final.width, final.height)),
    semFundo,
    opaca,
  };
}

interface Carregada {
  fonte: CanvasImageSource;
  largura: number;
  altura: number;
  liberar: () => void;
}

async function carregar(arquivo: Blob): Promise<Carregada> {
  try {
    const bitmap = await createImageBitmap(arquivo);
    return { fonte: bitmap, largura: bitmap.width, altura: bitmap.height, liberar: () => bitmap.close() };
  } catch {
    throw new Error('Não conseguimos abrir a imagem. Tente outro arquivo.');
  }
}

/** SVG sem largura/altura não desenha no canvas: usa o viewBox para dar um tamanho a ele. */
async function carregarSvg(arquivo: File): Promise<Carregada> {
  const texto = await arquivo.text();
  const doc = new DOMParser().parseFromString(texto, 'image/svg+xml');
  const raiz = doc.documentElement;
  if (raiz.nodeName.toLowerCase() !== 'svg' || doc.querySelector('parsererror')) {
    throw new Error('Esse SVG não pôde ser lido. Exporte de novo ou envie em PNG.');
  }
  const caixa = (raiz.getAttribute('viewBox') ?? '').split(/[\s,]+/).map(Number);
  let largura = parseFloat(raiz.getAttribute('width') ?? '');
  let altura = parseFloat(raiz.getAttribute('height') ?? '');
  if (!(largura > 0 && altura > 0)) {
    if (caixa.length === 4 && caixa[2] > 0 && caixa[3] > 0) {
      [largura, altura] = [caixa[2], caixa[3]];
    } else {
      [largura, altura] = [1000, 1000];
    }
  }
  raiz.setAttribute('width', String(largura));
  raiz.setAttribute('height', String(altura));
  const blob = new Blob([new XMLSerializer().serializeToString(doc)], { type: 'image/svg+xml' });
  const url = URL.createObjectURL(blob);
  const img = new Image();
  img.decoding = 'async';
  img.src = url;
  try {
    await img.decode();
  } catch {
    URL.revokeObjectURL(url);
    throw new Error('Esse SVG não pôde ser desenhado. Envie em PNG.');
  }
  return { fonte: img, largura, altura, liberar: () => URL.revokeObjectURL(url) };
}

function semTransparencia({ data }: ImageData): boolean {
  for (let i = 3; i < data.length; i += 4 * 7) if (data[i] < 250) return false;
  return true;
}

const quaseBranco = (data: Uint8ClampedArray, i: number) => Math.min(data[i], data[i + 1], data[i + 2]) >= 236;

/** Apaga o branco ligado às bordas (o miolo branco de um logo continua). */
function tirarFundoBranco(pixels: ImageData): boolean {
  const { width: w, height: h, data } = pixels;
  const visitado = new Uint8Array(w * h);
  const fila: number[] = [];
  const empilhar = (x: number, y: number) => {
    const p = y * w + x;
    if (!visitado[p] && quaseBranco(data, p * 4)) {
      visitado[p] = 1;
      fila.push(p);
    }
  };
  for (let x = 0; x < w; x++) {
    empilhar(x, 0);
    empilhar(x, h - 1);
  }
  for (let y = 0; y < h; y++) {
    empilhar(0, y);
    empilhar(w - 1, y);
  }
  let removidos = 0;
  while (fila.length) {
    const p = fila.pop()!;
    data[p * 4 + 3] = 0;
    removidos++;
    const x = p % w;
    const y = (p - x) / w;
    if (x > 0) empilhar(x - 1, y);
    if (x < w - 1) empilhar(x + 1, y);
    if (y > 0) empilhar(x, y - 1);
    if (y < h - 1) empilhar(x, y + 1);
  }
  // Suaviza a borda que encostava no branco (evita o "halo" claro em volta do logo).
  for (let p = 0; p < w * h; p++) {
    if (visitado[p]) continue;
    const i = p * 4;
    const claro = Math.min(data[i], data[i + 1], data[i + 2]);
    if (claro >= 200) {
      const x = p % w;
      const y = (p - x) / w;
      const vizinhoApagado =
        (x > 0 && visitado[p - 1]) || (x < w - 1 && visitado[p + 1]) || (y > 0 && visitado[p - w]) || (y < h - 1 && visitado[p + w]);
      if (vizinhoApagado) data[i + 3] = Math.round(255 * (1 - (claro - 200) / 56));
    }
  }
  return removidos > w * h * 0.02;
}

/** Retângulo onde está o desenho (ignora transparente, ou branco se a imagem for opaca). */
function conteudo({ width: w, height: h, data }: ImageData, ignorarBranco: boolean) {
  let [x0, y0, x1, y1] = [w, h, -1, -1];
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      const vazio = data[i + 3] < 12 || (ignorarBranco && quaseBranco(data, i));
      if (vazio) continue;
      if (x < x0) x0 = x;
      if (x > x1) x1 = x;
      if (y < y0) y0 = y;
      if (y > y1) y1 = y;
    }
  }
  return x1 < 0 ? null : { x: x0, y: y0, largura: x1 - x0 + 1, altura: y1 - y0 + 1 };
}

/**
 * Cores da marca: agrupa os pixels por matiz e devolve as mais presentes. Sem cor nenhuma
 * (logo preto, cinza), sugere o tom escuro predominante.
 */
export function coresDaMarca({ data }: ImageData): string[] {
  const grupos = new Map<number, { peso: number; r: number; g: number; b: number; h: number }>();
  let escuro = { peso: 0, r: 0, g: 0, b: 0 };
  for (let i = 0; i < data.length; i += 4 * 3) {
    if (data[i + 3] < 200) continue;
    const [r, g, b] = [data[i], data[i + 1], data[i + 2]];
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const luz = (max + min) / 510;
    const saturacao = max === min ? 0 : (max - min) / (255 - Math.abs(max + min - 255));
    if (saturacao < 0.25 || luz < 0.1 || luz > 0.92) {
      if (luz < 0.3) escuro = { peso: escuro.peso + 1, r: escuro.r + r, g: escuro.g + g, b: escuro.b + b };
      continue;
    }
    const matiz = matizDe(r, g, b);
    const chave = Math.round(matiz / 15) % 24;
    const grupo = grupos.get(chave) ?? { peso: 0, r: 0, g: 0, b: 0, h: matiz };
    grupos.set(chave, { peso: grupo.peso + 1, r: grupo.r + r, g: grupo.g + g, b: grupo.b + b, h: grupo.h });
  }
  const ordenados = [...grupos.values()].sort((a, b) => b.peso - a.peso);
  const cores: { hex: string; h: number }[] = [];
  for (const g of ordenados) {
    if (g.peso < 20) break;
    if (cores.some((c) => Math.abs(((c.h - g.h + 540) % 360) - 180) < 35)) continue;
    cores.push({ hex: hex(g.r / g.peso, g.g / g.peso, g.b / g.peso), h: g.h });
    if (cores.length === 2) break;
  }
  if (cores.length === 0 && escuro.peso > 20) {
    return [hex(escuro.r / escuro.peso, escuro.g / escuro.peso, escuro.b / escuro.peso)];
  }
  return cores.map((c) => c.hex);
}

function matizDe(r: number, g: number, b: number): number {
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  if (max === min) return 0;
  const d = max - min;
  const h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return (h * 60 + 360) % 360;
}

const hex = (r: number, g: number, b: number) =>
  '#' + [r, g, b].map((v) => Math.round(v).toString(16).padStart(2, '0')).join('').toUpperCase();
