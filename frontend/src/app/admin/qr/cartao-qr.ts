import { geometriaCurva } from '../../compartilhado/curva-carreira';
import { TemaMarca } from '../../compartilhado/tema-marca';
import { TEMA_PADRAO } from '../cores/cores';
import { coresDoQr, desenharQr, GeometriaQr, LogoQr, novoCanvas, pngDoCanvas } from './qr-codigo';

/**
 * Cartão de divulgação (1080 × 1350, formato retrato de redes sociais): a empresa
 * convida, a curva da carreira dá o contexto e o QR Code leva direto ao diagnóstico.
 */

export const LARGURA_CARTAO = 1080;
export const ALTURA_CARTAO = 1350;

const MARGEM = 80;
const CONTEUDO = LARGURA_CARTAO - 2 * MARGEM;
const DISPLAY = '"Bricolage Grotesque Variable", system-ui, sans-serif';
const TEXTO = '"Instrument Sans Variable", system-ui, sans-serif';
const TITULO = 'Em que ponto da curva está a sua carreira?';

async function carregarFontes() {
  // Sem as fontes carregadas, o canvas desenharia com a fonte do sistema.
  await Promise.all([
    document.fonts.load(`700 78px ${DISPLAY}`, TITULO),
    document.fonts.load(`600 40px ${DISPLAY}`),
    document.fonts.load(`600 30px ${TEXTO}`),
    document.fonts.load(`500 26px ${TEXTO}`),
  ]).catch(() => undefined);
}

/** Diminui a fonte até o texto caber; no limite, corta com reticências. */
function caber(ctx: CanvasRenderingContext2D, texto: string, fonte: (px: number) => string, maximo: number, minimo: number) {
  for (let px = maximo; px >= minimo; px -= 2) {
    ctx.font = fonte(px);
    if (ctx.measureText(texto).width <= CONTEUDO) return texto;
  }
  let cortado = texto;
  while (cortado.length > 1 && ctx.measureText(`${cortado}…`).width > CONTEUDO) cortado = cortado.slice(0, -1);
  return `${cortado.trimEnd()}…`;
}

function quebrarLinhas(ctx: CanvasRenderingContext2D, texto: string): string[] {
  const linhas: string[] = [];
  let atual = '';
  for (const palavra of texto.split(' ')) {
    const tentativa = atual ? `${atual} ${palavra}` : palavra;
    if (atual && ctx.measureText(tentativa).width > CONTEUDO) {
      linhas.push(atual);
      atual = palavra;
    } else {
      atual = tentativa;
    }
  }
  if (atual) linhas.push(atual);
  return linhas;
}

/** Mesma cor com transparência ("#RRGGBB" + alfa). */
const comAlfa = (hex: string, alfa: number) => `${hex}${Math.round(alfa * 255).toString(16).padStart(2, '0')}`;

export async function pngDoCartao(
  geo: GeometriaQr,
  empresa: string,
  url: string,
  tema: TemaMarca = TEMA_PADRAO,
  logo: (LogoQr & { fonte: CanvasImageSource }) | null = null,
): Promise<Blob> {
  await carregarFontes();
  const { canvas, ctx } = novoCanvas(LARGURA_CARTAO, ALTURA_CARTAO);
  const BRASA = tema.destaqueSobreTinta;

  // Fundo na tinta da marca (escura) com uma luz suave vindo do alto.
  ctx.fillStyle = tema.tinta;
  ctx.fillRect(0, 0, LARGURA_CARTAO, ALTURA_CARTAO);
  const luz = ctx.createRadialGradient(220, -80, 0, 220, -80, 1100);
  luz.addColorStop(0, comAlfa(tema.tintaBrilho, 0.55));
  luz.addColorStop(1, comAlfa(tema.tinta, 0));
  ctx.fillStyle = luz;
  ctx.fillRect(0, 0, LARGURA_CARTAO, ALTURA_CARTAO);

  ctx.textBaseline = 'alphabetic';
  if (logo) {
    // Logomarca da empresa num cartão branco (funciona com qualquer cor de logo).
    const alturaMax = 60;
    const larguraMax = 360;
    const escala = Math.min(alturaMax / logo.altura, larguraMax / logo.largura);
    const [w, h] = [logo.largura * escala, logo.altura * escala];
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.roundRect(MARGEM, 64, w + 40, h + 28, 18);
    ctx.fill();
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(logo.fonte, MARGEM + 20, 64 + 14, w, h);
  } else {
    // Sem logo: a marca do Diagnóstico (discreta: quem convida é a empresa).
    ctx.fillStyle = 'rgb(255 255 255 / 0.12)';
    ctx.beginPath();
    ctx.roundRect(MARGEM, 76, 56, 56, 15);
    ctx.fill();
    ctx.save();
    ctx.translate(MARGEM, 76);
    ctx.scale(56 / 26, 56 / 26);
    ctx.strokeStyle = BRASA;
    ctx.lineWidth = 2.6;
    ctx.lineCap = 'round';
    ctx.stroke(new Path2D('M5 19 C 9 19, 9 8, 13 8 S 17 8, 21 12'));
    ctx.restore();
    ctx.fillStyle = '#FFFFFF';
    ctx.font = `600 30px ${TEXTO}`;
    ctx.fillText('Diagnóstico do Líder', MARGEM + 76, 114);
  }

  // Quem convida + a pergunta
  ctx.fillStyle = 'rgb(237 242 239 / 0.74)';
  const convite = caber(ctx, `${empresa} convida você`, (px) => `600 ${px}px ${TEXTO}`, 32, 22);
  ctx.fillText(convite, MARGEM, 232);

  // Títulos levemente condensados, como no site (font-stretch 84%).
  ctx.fillStyle = '#FFFFFF';
  ctx.font = `700 78px ${DISPLAY}`;
  ctx.fontStretch = 'semi-condensed';
  quebrarLinhas(ctx, TITULO).forEach((linha, i) => ctx.fillText(linha, MARGEM - 3, 314 + i * 82));
  ctx.fontStretch = 'normal';

  // A curva da carreira, a assinatura visual
  const curva = geometriaCurva(CONTEUDO, 170);
  ctx.save();
  ctx.translate(MARGEM, 440);
  const brilho = ctx.createLinearGradient(0, 0, 0, curva.base);
  brilho.addColorStop(0, comAlfa(BRASA, 0.22));
  brilho.addColorStop(1, comAlfa(BRASA, 0));
  ctx.fillStyle = brilho;
  ctx.fill(new Path2D(curva.area));
  ctx.strokeStyle = 'rgb(237 242 239 / 0.16)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(curva.margemX, curva.base);
  ctx.lineTo(curva.largura - curva.margemX, curva.base);
  ctx.stroke();
  ctx.strokeStyle = BRASA;
  ctx.lineWidth = 6;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.stroke(new Path2D(curva.curva));
  ctx.fillStyle = 'rgb(237 242 239 / 0.62)';
  ctx.font = `500 22px ${TEXTO}`;
  ctx.textAlign = 'center';
  curva.rotulos.forEach((r) => ctx.fillText(r.texto, r.x, curva.base + 28));
  ctx.restore();

  // O código, num cartão branco
  const lado = 480;
  const x = (LARGURA_CARTAO - lado) / 2;
  const y = 650;
  ctx.save();
  ctx.beginPath();
  ctx.roundRect(x, y, lado, lado, 36);
  ctx.clip();
  desenharQr(ctx, geo, x, y, lado, coresDoQr(tema), logo);
  ctx.restore();

  // Chamada
  ctx.textAlign = 'center';
  ctx.fillStyle = '#FFFFFF';
  ctx.font = `600 42px ${DISPLAY}`;
  ctx.fontStretch = 'semi-condensed';
  ctx.fillText('Aponte a câmera do celular', LARGURA_CARTAO / 2, y + lado + 76);
  ctx.fontStretch = 'normal';
  ctx.fillStyle = 'rgb(237 242 239 / 0.7)';
  const endereco = caber(ctx, `ou acesse ${url.replace(/^https?:\/\//, '')}`, (px) => `500 ${px}px ${TEXTO}`, 26, 18);
  ctx.fillText(endereco, LARGURA_CARTAO / 2, y + lado + 124);
  if (logo) {
    ctx.fillStyle = 'rgb(237 242 239 / 0.5)';
    ctx.font = `500 20px ${TEXTO}`;
    ctx.fillText('por Diagnóstico do Líder', LARGURA_CARTAO / 2, ALTURA_CARTAO - 34);
  }

  return pngDoCanvas(canvas);
}
