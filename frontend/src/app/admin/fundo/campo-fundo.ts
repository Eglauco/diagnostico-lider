import { Component, computed, DestroyRef, ElementRef, inject, input, model, signal, viewChild } from '@angular/core';

import { posicaoDaFoto, UsoDaFoto, veuDaFoto } from '../../compartilhado/foto-fundo';
import { FundoEmpresa, LogoEmpresa, TemaMarca } from '../../compartilhado/tema-marca';
import { FotoPreparada, prepararFundo } from './preparar-fundo';

export interface Foco {
  x: number;
  y: number;
}

/** O que mudou na foto de fundo (vai para o servidor quando o formulário for salvo). */
export type MudancaFundo =
  | { tipo: 'nova'; foto: FotoPreparada; foco: Foco }
  | { tipo: 'foco'; foco: Foco }
  | { tipo: 'remover' }
  | null;

const PASSO_TECLADO = 4;

/**
 * Foto de fundo da página do RH: enviar a foto, tocar nela para escolher o ponto que fica sempre
 * à vista (o foco) e ver na hora como fica cortada nos três lugares onde aparece, já com o véu na
 * cor da marca.
 */
@Component({
  selector: 'app-campo-fundo',
  host: { class: 'block' },
  template: `
    <p class="text-[15px] font-semibold" id="fundo-rotulo">Foto de fundo da página do RH</p>
    <p class="mt-1 text-[14px] text-abeto-2">
      Aparece na entrada e no topo da página onde o RH acompanha a avaliação: uma foto da empresa (fachada, equipe,
      uma obra) faz o gestor se sentir em casa. Foto deitada, com 1600 pixels de largura ou mais, fica melhor.
    </p>

    @if (exibida(); as foto) {
      <div class="mt-4 rounded-[20px] bg-white p-3 ring-1 ring-linha sm:p-4">
        <!-- A foto inteira: tocar ou arrastar escolhe o ponto de foco. -->
        <div
          #area
          class="area-foco relative mx-auto overflow-hidden rounded-[14px]"
          [style.aspect-ratio]="foto.largura + ' / ' + foto.altura"
          [style.width]="'min(100%, ' + larguraMaxima(foto) + 'px)'"
          (pointerdown)="apontar($event)"
          (pointermove)="arrastar($event)"
          (pointerup)="soltar($event)"
          (pointercancel)="soltar($event)"
        >
          <img [src]="foto.url" class="pointer-events-none block size-full object-cover" alt="" draggable="false" />
          <button
            type="button"
            class="mira"
            [style.left.%]="foto.foco.x"
            [style.top.%]="foto.foco.y"
            [attr.aria-label]="'Ponto de foco: ' + foto.foco.x + '% da esquerda e ' + foto.foco.y + '% do topo. Use as setas para mover.'"
            aria-describedby="fundo-dica"
            (keydown)="tecla($event)"
            (pointerdown)="$event.stopPropagation(); apontar($event)"
          ></button>
        </div>
        <p id="fundo-dica" class="mt-2.5 flex items-center gap-2 text-[13px] leading-snug text-abeto-2">
          <svg class="shrink-0" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="7" /><circle cx="12" cy="12" r="1.5" fill="currentColor" /><path d="M12 2v3M12 19v3M2 12h3M19 12h3" stroke-linecap="round" /></svg>
          {{ computador ? 'Clique' : 'Toque' }} na foto no que precisa ficar sempre à vista (um rosto, a fachada, a logo da obra).
        </p>

        <!-- Como fica cortada em cada lugar, com o véu na cor da marca. -->
        <div class="mx-auto mt-4 grid max-w-[30rem] grid-cols-2 gap-x-2.5 gap-y-3">
          <figure>
            <div class="miniatura" style="aspect-ratio: 4 / 5">
              <img class="foto" [src]="foto.url" alt="" [style.object-position]="posicao()" />
              <i class="veu" [style.background]="veu('entrada')"></i>
              <span class="absolute inset-x-[12%] top-[12%] block">
                <span class="block h-1 w-[45%] rounded-full bg-white/60"></span>
                <span class="mt-1.5 block h-1.5 w-[85%] rounded-full bg-white"></span>
                <span class="mt-1 block h-1.5 w-[60%] rounded-full bg-white"></span>
              </span>
              <span class="absolute inset-x-[12%] top-[40%] bottom-[16%] block rounded-md bg-white/95 shadow-lg"></span>
            </div>
            <figcaption class="mt-1.5 text-[12px] text-abeto-2">Entrada no computador</figcaption>
          </figure>
          <!-- O celular: a faixa com a foto no alto da tela e a entrada embaixo. -->
          <figure>
            <div class="miniatura flex flex-col" style="aspect-ratio: 4 / 5" [style.background]="tema().fundo">
              <div class="relative h-[44%] shrink-0 overflow-hidden rounded-b-lg">
                <img class="foto" [src]="foto.url" alt="" [style.object-position]="posicao()" />
                <i class="veu" [style.background]="veu('celular')"></i>
                <span class="absolute bottom-[10%] left-[8%] flex max-w-[84%] items-center gap-1">
                  <span class="grid size-4 shrink-0 place-items-center overflow-hidden rounded bg-white p-px text-[6px] font-bold" [style.color]="tema().acao">@if (logo(); as lg) {<img class="block size-full object-contain" [src]="lg.url" alt="" />} @else {{{ sigla() }}}</span>
                  <span class="display line-clamp-1 text-[9px] font-bold text-white">{{ nome() || 'Nome da empresa' }}</span>
                </span>
              </div>
              <span class="mx-[8%] mt-[10%] block h-1 w-[40%] rounded-full" [style.background]="tema().destaque"></span>
              <span class="mx-[8%] mt-1.5 block h-1.5 w-[78%] rounded-full" [style.background]="tema().tinta"></span>
              <span class="mx-[8%] mt-1 block h-1.5 w-[55%] rounded-full" [style.background]="tema().tinta"></span>
              <span class="mx-[8%] mt-auto mb-[10%] block h-3 rounded-full" [style.background]="tema().acao"></span>
            </div>
            <figcaption class="mt-1.5 text-[12px] text-abeto-2">Entrada no celular</figcaption>
          </figure>
          <figure class="col-span-2">
            <div class="miniatura" style="aspect-ratio: 4 / 1">
              <img class="foto" [src]="foto.url" alt="" [style.object-position]="posicao()" />
              <i class="veu" [style.background]="veu('capa')"></i>
              <span class="absolute inset-y-0 left-[5%] flex max-w-[78%] items-center gap-1.5">
                <span class="grid size-5 shrink-0 place-items-center overflow-hidden rounded-md bg-white p-0.5 text-[7px] font-bold" [style.color]="tema().acao">@if (logo(); as lg) {<img class="block size-full object-contain" [src]="lg.url" alt="" />} @else {{{ sigla() }}}</span>
                <span class="display line-clamp-1 text-[11px] font-bold text-white">{{ nome() || 'Nome da empresa' }}</span>
              </span>
            </div>
            <figcaption class="mt-1.5 text-[12px] text-abeto-2">Topo da página do RH</figcaption>
          </figure>
        </div>
      </div>

      @if (preparada()?.pequena) {
        <p class="mt-2 text-[13.5px] text-[#855300]">
          A foto tem {{ preparada()!.largura }} pixels de largura: em telas grandes pode ficar um pouco borrada.
        </p>
      }

      <div class="mt-3 flex flex-wrap gap-2">
        <button type="button" class="botao-pequeno" [disabled]="processando()" (click)="escolher()">Trocar foto</button>
        <button type="button" class="botao-pequeno" [disabled]="processando()" (click)="remover()">Remover</button>
      </div>
      @if (processando()) {
        <p class="mt-2 flex items-center gap-2 text-[14px] text-abeto-2" role="status">
          <span class="size-4 animate-spin rounded-full border-2 border-linha border-t-abeto" aria-hidden="true"></span>
          Preparando a foto…
        </p>
      }
    } @else {
      <button
        type="button"
        class="zona-fundo mt-4"
        [class.zona-fundo-ativa]="arrastando()"
        aria-describedby="fundo-rotulo"
        (click)="escolher()"
        (dragover)="arrastou($event)"
        (dragleave)="arrastando.set(false)"
        (drop)="soltou($event)"
      >
        @if (processando()) {
          <span class="size-6 animate-spin rounded-full border-[3px] border-linha border-t-abeto" aria-hidden="true"></span>
          <span class="text-[15px] font-semibold">Preparando a foto…</span>
        } @else {
          <svg class="text-abeto-2" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path d="M3.5 8.5A2 2 0 0 1 5.5 6.5h2l1.5-2h6l1.5 2h2a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z" stroke-linejoin="round" /><circle cx="12" cy="13" r="3.5" />
          </svg>
          <span class="text-[15px] font-semibold">Enviar uma foto</span>
          <span class="text-[13px] text-abeto-2">
            <span class="hidden sm:inline">Arraste o arquivo para cá ou clique para escolher · </span>JPG, PNG ou WebP
          </span>
          <span class="text-[13px] text-abeto-2">Sem foto, o fundo fica nas cores da marca.</span>
        }
      </button>
    }

    <input #seletor type="file" class="sr-only" tabindex="-1" aria-hidden="true" accept="image/jpeg,image/png,image/webp,image/avif" (change)="escolheu($event)" />
    <p class="mt-2 min-h-5 text-[14px] font-medium text-travada" aria-live="polite">{{ erro() }}</p>
  `,
  styles: `
    .botao-pequeno {
      display: inline-flex;
      align-items: center;
      min-height: 40px;
      padding: 0 14px;
      border-radius: 999px;
      box-shadow: inset 0 0 0 1px var(--color-linha);
      background: rgb(255 255 255 / 0.6);
      color: var(--color-abeto);
      font-size: 14px;
      font-weight: 600;
    }
    .botao-pequeno:hover {
      background: #fff;
    }
    .zona-fundo {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 6px;
      width: 100%;
      min-height: 148px;
      padding: 18px;
      border: 1.5px dashed var(--color-abeto-3);
      border-radius: 20px;
      background: rgb(255 255 255 / 0.6);
      text-align: center;
      transition:
        background-color 160ms,
        border-color 160ms;
    }
    .zona-fundo:hover,
    .zona-fundo-ativa {
      border-color: var(--color-abeto);
      background: #fff;
    }

    .area-foco {
      max-height: 320px;
      cursor: crosshair;
      touch-action: none;
      user-select: none;
      background: var(--color-nevoa);
    }
    /* A mira do ponto de foco: um alvo branco com sombra, que se vê em qualquer foto. */
    .mira {
      position: absolute;
      width: 44px;
      height: 44px;
      margin: -22px 0 0 -22px;
      border-radius: 999px;
      border: 3px solid #fff;
      background: radial-gradient(circle, #fff 0 4px, transparent 5px);
      box-shadow:
        0 0 0 1.5px rgb(15 43 38 / 0.55),
        0 6px 18px rgb(0 0 0 / 0.35),
        inset 0 0 0 1.5px rgb(15 43 38 / 0.35);
      cursor: grab;
      transition: transform 160ms var(--ease-mola);
    }
    .mira:focus-visible {
      outline: 3px solid var(--color-acao);
      outline-offset: 3px;
    }
    .area-foco:active .mira {
      transform: scale(1.12);
      cursor: grabbing;
    }

    .miniatura {
      position: relative;
      overflow: hidden;
      border-radius: 12px;
      background: var(--color-nevoa);
    }
    .miniatura .foto {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: object-position 240ms var(--ease-mola);
    }
    .miniatura .veu {
      position: absolute;
      inset: 0;
    }
  `,
})
export class CampoFundo {
  /** A foto que já está salva (edição). */
  readonly atual = input<FundoEmpresa | null>(null);
  /** A paleta escolhida: o véu da prévia é a cor da marca de verdade. */
  readonly tema = input.required<TemaMarca>();
  readonly nome = input('');
  readonly sigla = input('');
  readonly logo = input<LogoEmpresa | null>(null);
  readonly mudanca = model<MudancaFundo>(null);

  protected readonly computador = matchMedia('(pointer: fine)').matches;
  private readonly seletor = viewChild.required<ElementRef<HTMLInputElement>>('seletor');
  private readonly area = viewChild<ElementRef<HTMLElement>>('area');
  private arrastandoFoco = false;

  protected readonly preparada = computed(() => {
    const m = this.mudanca();
    return m?.tipo === 'nova' ? m.foto : null;
  });

  /** A foto na tela: a nova (ainda não salva), a salva com o foco novo, ou nenhuma. */
  protected readonly exibida = computed(() => {
    const m = this.mudanca();
    if (m?.tipo === 'remover') return null;
    if (m?.tipo === 'nova') return { url: m.foto.url, largura: m.foto.largura, altura: m.foto.altura, foco: m.foco };
    const a = this.atual();
    if (!a) return null;
    return { url: a.url, largura: a.largura, altura: a.altura, foco: m?.tipo === 'foco' ? m.foco : { x: a.focoX, y: a.focoY } };
  });
  protected readonly posicao = computed(() => {
    const f = this.exibida()?.foco ?? { x: 50, y: 50 };
    return posicaoDaFoto({ focoX: f.x, focoY: f.y });
  });

  protected readonly processando = signal(false);
  protected readonly arrastando = signal(false);
  protected readonly erro = signal<string | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.liberar());
  }

  protected veu(uso: UsoDaFoto): string {
    return veuDaFoto(this.tema().tinta, uso);
  }

  /** A foto inteira cabe em até 320 px de altura (uma foto em pé não toma a tela). */
  protected larguraMaxima(foto: { largura: number; altura: number }): number {
    return Math.round((320 * foto.largura) / foto.altura);
  }

  protected escolher() {
    this.seletor().nativeElement.click();
  }

  protected escolheu(evento: Event) {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = '';
    if (arquivo) void this.processar(arquivo);
  }

  protected arrastou(evento: DragEvent) {
    evento.preventDefault();
    this.arrastando.set(true);
  }

  protected soltou(evento: DragEvent) {
    evento.preventDefault();
    this.arrastando.set(false);
    const arquivo = evento.dataTransfer?.files?.[0];
    if (arquivo) void this.processar(arquivo);
  }

  protected remover() {
    this.liberar();
    this.erro.set(null);
    this.mudanca.set(this.atual() ? { tipo: 'remover' } : null);
  }

  protected apontar(evento: PointerEvent) {
    const area = this.area()?.nativeElement;
    if (!area) return;
    this.arrastandoFoco = true;
    area.setPointerCapture?.(evento.pointerId);
    this.focarEm(evento, area);
  }

  protected arrastar(evento: PointerEvent) {
    const area = this.area()?.nativeElement;
    if (this.arrastandoFoco && area) this.focarEm(evento, area);
  }

  protected soltar(evento: PointerEvent) {
    this.arrastandoFoco = false;
    this.area()?.nativeElement.releasePointerCapture?.(evento.pointerId);
  }

  /** As setas movem o foco de 4 em 4 (com Shift, de 1 em 1). */
  protected tecla(evento: KeyboardEvent) {
    const passo = evento.shiftKey ? 1 : PASSO_TECLADO;
    const atual = this.exibida()?.foco;
    if (!atual) return;
    const mover: Record<string, Foco> = {
      ArrowLeft: { x: atual.x - passo, y: atual.y },
      ArrowRight: { x: atual.x + passo, y: atual.y },
      ArrowUp: { x: atual.x, y: atual.y - passo },
      ArrowDown: { x: atual.x, y: atual.y + passo },
    };
    const novo = mover[evento.key];
    if (!novo) return;
    evento.preventDefault();
    this.definirFoco(novo);
  }

  private focarEm(evento: PointerEvent, area: HTMLElement) {
    const caixa = area.getBoundingClientRect();
    this.definirFoco({
      x: ((evento.clientX - caixa.left) / caixa.width) * 100,
      y: ((evento.clientY - caixa.top) / caixa.height) * 100,
    });
  }

  private definirFoco(bruto: Foco) {
    const foco = { x: Math.round(Math.min(100, Math.max(0, bruto.x))), y: Math.round(Math.min(100, Math.max(0, bruto.y))) };
    const m = this.mudanca();
    if (m?.tipo === 'nova') {
      this.mudanca.set({ ...m, foco });
      return;
    }
    const a = this.atual();
    if (!a) return;
    // De volta ao foco que já está salvo: nada a mandar.
    this.mudanca.set(a.focoX === foco.x && a.focoY === foco.y ? null : { tipo: 'foco', foco });
  }

  private async processar(arquivo: File) {
    this.processando.set(true);
    this.erro.set(null);
    try {
      const foto = await prepararFundo(arquivo);
      this.liberar();
      this.mudanca.set({ tipo: 'nova', foto, foco: { x: 50, y: 50 } });
    } catch (e) {
      this.erro.set(e instanceof Error ? e.message : 'Não foi possível usar essa foto.');
    } finally {
      this.processando.set(false);
    }
  }

  private liberar() {
    const p = this.preparada();
    if (p) URL.revokeObjectURL(p.url);
  }
}
