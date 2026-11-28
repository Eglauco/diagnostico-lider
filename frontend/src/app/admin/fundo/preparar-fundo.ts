/**
 * Prepara a foto de fundo no navegador antes do envio: o backend recebe sempre um JPEG de no
 * máximo 2400 px de largura, venha a foto como vier (JPG do celular de 12 MP, PNG, WebP). A
 * orientação da câmera (EXIF) já vem aplicada, e a foto nunca é ampliada (ampliar só borra).
 */

export interface FotoPreparada {
  jpeg: Blob;
  largura: number;
  altura: number;
  /** Endereço local para pré-visualizar (URL.createObjectURL). */
  url: string;
  /** Menor que o ideal para a metade de uma tela grande: pode ficar um pouco borrada. */
  pequena: boolean;
}

const TIPOS_ACEITOS = ['image/jpeg', 'image/png', 'image/webp', 'image/avif'];
const TAMANHO_MAXIMO = 25 * 1024 * 1024;
const LARGURA_FINAL = 2400;
const LADO_MAXIMO = 3200;
const LARGURA_MINIMA = 640;
const ALTURA_MINIMA = 400;
/** Abaixo disso, avisa (mas aceita): na metade de um monitor grande, a foto é esticada. */
const LARGURA_BOA = 1400;
/** O servidor aceita até 2,5 MB: comprime mais se passar. */
const BYTES_MAXIMO = 2_300_000;

export async function prepararFundo(arquivo: File): Promise<FotoPreparada> {
  if (!TIPOS_ACEITOS.includes(arquivo.type)) {
    throw new Error('Use uma foto em JPG, PNG ou WebP.');
  }
  if (arquivo.size > TAMANHO_MAXIMO) {
    throw new Error('A foto passa de 25 MB. Use um arquivo menor.');
  }

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(arquivo, { imageOrientation: 'from-image' });
  } catch {
    throw new Error('Não conseguimos abrir essa foto. Tente outro arquivo.');
  }
  if (bitmap.width < LARGURA_MINIMA || bitmap.height < ALTURA_MINIMA) {
    bitmap.close();
    throw new Error('A foto é pequena demais e ficaria borrada: use uma com pelo menos 640 × 400 pixels.');
  }

  const fator = Math.min(1, LARGURA_FINAL / bitmap.width, LADO_MAXIMO / bitmap.height);
  const largura = Math.round(bitmap.width * fator);
  const altura = Math.round(bitmap.height * fator);
  const tela = document.createElement('canvas');
  tela.width = largura;
  tela.height = altura;
  const ctx = tela.getContext('2d');
  if (!ctx) {
    bitmap.close();
    throw new Error('Seu navegador não conseguiu processar a foto.');
  }
  // Fundo branco: um PNG com transparência não vira preto no JPEG.
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, largura, altura);
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(bitmap, 0, 0, largura, altura);
  bitmap.close();

  let jpeg = await comprimir(tela, 0.84);
  for (const qualidade of [0.74, 0.64]) {
    if (jpeg.size <= BYTES_MAXIMO) break;
    jpeg = await comprimir(tela, qualidade);
  }
  if (jpeg.size > BYTES_MAXIMO) throw new Error('A foto ficou pesada demais. Tente outra.');

  return { jpeg, largura, altura, url: URL.createObjectURL(jpeg), pequena: largura < LARGURA_BOA };
}

function comprimir(tela: HTMLCanvasElement, qualidade: number): Promise<Blob> {
  return new Promise((ok, falha) =>
    tela.toBlob((b) => (b ? ok(b) : falha(new Error('Seu navegador não conseguiu processar a foto.'))), 'image/jpeg', qualidade),
  );
}
