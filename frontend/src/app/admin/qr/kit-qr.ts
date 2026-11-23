import { Component, computed, DestroyRef, effect, inject, input, resource, signal, untracked } from '@angular/core';

import { salvarBlob } from '../../compartilhado/download';
import { LogoEmpresa, TemaMarca } from '../../compartilhado/tema-marca';
import { TEMA_PADRAO } from '../cores/cores';
import { pngDoCartao } from './cartao-qr';
import { coresDoQr, geometriaQr, pngDoQr, svgDoQr } from './qr-codigo';

/**
 * A logomarca pronta para desenhar no canvas e para embutir no SVG. Ela vem direto do bucket
 * (outra origem): "no-store" evita reaproveitar a cópia que a <img> da página guardou sem os
 * cabeçalhos de CORS, o que bloquearia a leitura.
 */
async function carregarLogo(logo: LogoEmpresa) {
  const resposta = await fetch(logo.url, { mode: 'cors', cache: 'no-store' });
  if (!resposta.ok) throw new Error(`Logomarca indisponível (${resposta.status})`);
  const blob = await resposta.blob();
  const fonte = await createImageBitmap(blob);
  const href = await new Promise<string>((resolver) => {
    const leitor = new FileReader();
    leitor.onload = () => resolver(String(leitor.result));
    leitor.readAsDataURL(blob);
  });
  return { fonte, href, largura: logo.largura, altura: logo.altura };
}

type Formato = 'codigo' | 'cartao';

/** Testa uma vez se o aparelho compartilha arquivos (celular: WhatsApp, e-mail, AirDrop…). */
function compartilhaArquivos(): boolean {
  try {
    return !!navigator.canShare?.({ files: [new File([''], 'teste.png', { type: 'image/png' })] });
  } catch {
    return false;
  }
}

/**
 * O QR Code do link em dois formatos: só o código (PNG ou SVG, para a empresa colocar
 * nos próprios materiais) e um cartão pronto para enviar. Copia, baixa ou compartilha.
 */
@Component({
  selector: 'app-kit-qr',
  styleUrl: '../admin.css',
  host: { class: 'block' },
  template: `
    <section class="cartao p-5 sm:p-6" aria-labelledby="titulo-qr">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <h2 id="titulo-qr" class="text-[17px] font-semibold">QR Code</h2>
        <div class="segmentado" role="group" aria-label="Formato da imagem">
          <button type="button" [attr.aria-pressed]="formato() === 'codigo'" (click)="formato.set('codigo')">Só o código</button>
          <button type="button" [attr.aria-pressed]="formato() === 'cartao'" (click)="formato.set('cartao')">Cartão</button>
        </div>
      </div>

      <div class="palco-qr mt-4" [class.palco-qr-cartao]="formato() === 'cartao'">
        @if (previa(); as src) {
          <img
            [src]="src"
            [alt]="descricao()"
            class="imagem-qr"
            [class.imagem-qr-cartao]="formato() === 'cartao'"
            [class.opacity-40]="imagem.isLoading()"
          />
        } @else {
          <div class="size-56 animate-pulse rounded-2xl bg-white/70" aria-hidden="true"></div>
        }
      </div>

      <div class="mt-4 grid grid-cols-2 gap-2">
        <button type="button" class="botao-primario col-span-2" [disabled]="!blob()" (click)="copiarImagem()">
          @if (aviso() === 'copiado') {
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
            Imagem copiada
          } @else {
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" aria-hidden="true"><rect x="8" y="8" width="12" height="12" rx="2.5" /><path d="M16 8V6.5A2.5 2.5 0 0 0 13.5 4h-7A2.5 2.5 0 0 0 4 6.5v7A2.5 2.5 0 0 0 6.5 16H8" /></svg>
            Copiar imagem
          }
        </button>
        @if (podeCompartilhar) {
          <button type="button" class="botao-secundario col-span-2" [disabled]="!blob()" (click)="compartilhar()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" aria-hidden="true"><path d="M12 3v12M7.5 7.5L12 3l4.5 4.5M5 13v5.5A2.5 2.5 0 0 0 7.5 21h9a2.5 2.5 0 0 0 2.5-2.5V13" stroke-linecap="round" stroke-linejoin="round" /></svg>
            Compartilhar imagem
          </button>
        }
        <button type="button" class="botao-secundario" [class.col-span-2]="formato() === 'cartao'" [disabled]="!blob()" (click)="baixarPng()">
          Baixar PNG
        </button>
        @if (formato() === 'codigo') {
          <button type="button" class="botao-secundario" (click)="baixarSvg()">Baixar SVG</button>
        }
      </div>

      <p class="mt-3 text-[13px] leading-snug text-abeto-2">
        @if (formato() === 'codigo') {
          PNG para e-mail, apresentações e redes. SVG para a gráfica: não perde qualidade em nenhum tamanho.
        } @else {
          Imagem de 1080 × 1350 px, pronta para WhatsApp, e-mail ou intranet.
        }
        No celular, você também pode tocar e segurar a imagem para salvar.
      </p>
      <p class="mt-2 min-h-5 text-[14px] font-medium text-travada" aria-live="polite">{{ erro() }}</p>
    </section>
  `,
})
export class KitQr {
  readonly url = input.required<string>();
  readonly codigo = input.required<string>();
  readonly empresa = input.required<string>();
  /** Cores da empresa: o código e o cartão saem com elas. */
  readonly tema = input<TemaMarca>(TEMA_PADRAO);
  /** Logomarca da empresa: no cartão e, se for quase quadrada, no centro do código. */
  readonly logo = input<LogoEmpresa | null>(null);

  private readonly logoCarregada = resource({
    params: () => this.logo() ?? undefined,
    loader: ({ params }) => carregarLogo(params),
  });

  protected readonly formato = signal<Formato>('codigo');
  protected readonly aviso = signal<'copiado' | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly podeCompartilhar = compartilhaArquivos();

  private readonly geo = computed(() => geometriaQr(this.url()));
  protected readonly imagem = resource({
    params: () => {
      // Com logo, espera ela carregar (sem logo quebrada, desenha sem ela).
      if (this.logo() && this.logoCarregada.isLoading()) return undefined;
      const logo = this.logoCarregada.hasValue() ? this.logoCarregada.value() : null;
      return { formato: this.formato(), geo: this.geo(), empresa: this.empresa(), url: this.url(), tema: this.tema(), logo };
    },
    loader: ({ params: p }) =>
      p.formato === 'cartao'
        ? pngDoCartao(p.geo, p.empresa, p.url, p.tema, p.logo)
        : pngDoQr(p.geo, 1200, coresDoQr(p.tema), p.logo),
  });
  protected readonly blob = computed(() => (this.imagem.hasValue() ? this.imagem.value() : undefined));

  /** URL da imagem exibida; a anterior continua na tela enquanto a nova é desenhada. */
  protected readonly previa = signal<string | null>(null);
  protected readonly descricao = computed(() =>
    this.formato() === 'cartao'
      ? `Cartão de divulgação de ${this.empresa()} com o QR Code do diagnóstico`
      : `QR Code que abre ${this.url()}`,
  );

  constructor() {
    effect(() => {
      const blob = this.blob();
      if (!blob) return;
      const anterior = untracked(this.previa);
      this.previa.set(URL.createObjectURL(blob));
      if (anterior) setTimeout(() => URL.revokeObjectURL(anterior), 1000);
    });
    effect(() => {
      this.formato();
      untracked(() => {
        this.erro.set(null);
        this.aviso.set(null);
      });
    });
    inject(DestroyRef).onDestroy(() => {
      const atual = this.previa();
      if (atual) URL.revokeObjectURL(atual);
    });
  }

  private nomeArquivo(extensao: 'png' | 'svg'): string {
    return this.formato() === 'cartao' ? `cartao-diagnostico-${this.codigo()}.png` : `qr-code-${this.codigo()}.${extensao}`;
  }

  protected async copiarImagem() {
    const blob = this.blob();
    if (!blob) return;
    this.erro.set(null);
    try {
      // Sem await antes: o Safari só deixa copiar dentro do clique.
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
      this.aviso.set('copiado');
      setTimeout(() => this.aviso.set(null), 2200);
    } catch {
      this.erro.set('O navegador não deixou copiar a imagem. Use “Baixar PNG”.');
    }
  }

  protected async compartilhar() {
    const blob = this.blob();
    if (!blob) return;
    this.erro.set(null);
    try {
      await navigator.share({
        files: [new File([blob], this.nomeArquivo('png'), { type: 'image/png' })],
        title: `Diagnóstico do Líder · ${this.empresa()}`,
      });
    } catch (e) {
      if ((e as DOMException)?.name !== 'AbortError') this.erro.set('Não foi possível compartilhar. Use “Baixar PNG”.');
    }
  }

  protected baixarPng() {
    const blob = this.blob();
    if (blob) salvarBlob(blob, this.nomeArquivo('png'));
  }

  protected baixarSvg() {
    const logo = this.logoCarregada.hasValue() ? this.logoCarregada.value() : null;
    salvarBlob(new Blob([svgDoQr(this.geo(), 1024, coresDoQr(this.tema()), logo)], { type: 'image/svg+xml' }), this.nomeArquivo('svg'));
  }
}
