import { Component, computed, DestroyRef, ElementRef, inject, input, model, signal, viewChild } from '@angular/core';

import { posicaoDaFoto, UsoDaFoto, veuDaFoto } from '../../compartilhado/foto-fundo';
import { FundoEmpresa, LogoEmpresa, TemaMarca } from '../../compartilhado/tema-marca';
import { FotoPreparada, prepararFundo } from './preparar-fundo';

export interface Foco {
  x: number;
  y: number;
}

/** O que mudou na foto (vai para o servidor quando o formulário for salvo). */
export type MudancaFundo =
  | { tipo: 'nova'; foto: FotoPreparada; foco: Foco }
  | { tipo: 'foco'; foco: Foco }
  | { tipo: 'remover' }
  | null;

/** Qual foto da página do RH: a da entrada (em pé ou quadrada) ou a do topo (deitada). */
export type TipoFoto = 'fundo' | 'capa';

const PASSO_TECLADO = 4;

/**
 * O que cada foto pede: o tamanho ideal (para pedir ao cliente), o mínimo que não fica borrado e
 * o texto pronto do pedido. Os tamanhos saem dos cortes reais: a metade da entrada no computador
 * vai de 0,85 a 1,25 (quase quadrada); o topo vai de 6 por 1 (computador) a 2,4 por 1 (celular).
 */
const ESPEC: Record<
  TipoFoto,
  {
    titulo: string;
    descricao: string;
    ideal: string;
    formato: string;
    minimo: { largura: number; altura: number };
    rotuloMinimo: string;
    pedido: string;
    enviar: string;
    semOutra: string;
  }
> = {
  fundo: {
    titulo: 'Foto da entrada da página do RH',
    descricao:
      'Ocupa a metade esquerda da tela de entrada no computador: uma foto da empresa (fachada, equipe, uma obra) faz o gestor se sentir em casa.',
    ideal: '2000 × 2000 px',
    formato: 'quadrada (ou em pé, 1600 × 2000 px)',
    minimo: { largura: 1200, altura: 1200 },
    rotuloMinimo: '1200 × 1200 px',
    pedido:
      'Foto para a entrada da página de acompanhamento da avaliação: quadrada, 2000 × 2000 pixels (no mínimo 1200 × 1200), em JPG ou PNG. Pode ser a fachada, a equipe ou uma obra, com o principal no centro e sem textos por cima.',
    enviar: 'Enviar a foto da entrada',
    semOutra: 'Enquanto não houver imagem do topo, esta foto também aparece no topo da página e na entrada pelo celular.',
  },
  capa: {
    titulo: 'Imagem do topo da página do RH',
    descricao:
      'Aparece no topo da página depois que o gestor entra e na faixa da entrada pelo celular. Deitada, como um banner.',
    ideal: '2400 × 800 px',
    formato: 'deitada (3 por 1)',
    minimo: { largura: 1600, altura: 530 },
    rotuloMinimo: '1600 × 530 px',
    pedido:
      'Imagem para o topo da página de acompanhamento da avaliação: deitada, 2400 × 800 pixels (no mínimo 1600 × 530), em JPG ou PNG. O principal no centro, porque as laterais são cortadas no celular, e sem textos por cima.',
    enviar: 'Enviar a imagem do topo',
    semOutra: 'Enquanto não houver foto da entrada, esta imagem também aparece na entrada pelo computador.',
  },
};

/**
 * Uma foto da página do RH (a da entrada ou a do topo): o tamanho ideal para pedir ao cliente,
 * enviar a foto, tocar ou arrastar nela para escolher o ponto que fica sempre à vista (o foco) e
 * ver na hora como fica cortada em cada lugar onde aparece, já com o véu na cor da marca.
 */
@Component({
  selector: 'app-campo-fundo',
  host: { class: 'block' },
  template: `
    <p class="text-[15px] font-semibold" [id]="tipo() + '-rotulo'">{{ espec().titulo }}</p>
    <p class="mt-1 text-[14px] text-abeto-2">{{ espec().descricao }}</p>

    <!-- O tamanho ideal, para pedir ao cliente do jeito certo. -->
    <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-2xl bg-nevoa-2 px-3.5 py-2.5 ring-1 ring-linha/70">
      <svg class="shrink-0 text-abeto-2" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        @if (tipo() === 'capa') {
          <rect x="2.5" y="7" width="19" height="10" rx="2" />
        } @else {
          <rect x="5" y="4" width="14" height="16" rx="2" />
        }
      </svg>
      <p class="min-w-0 flex-1 text-[14px] leading-snug">
        Tamanho ideal: <strong class="font-semibold tabular-nums">{{ espec().ideal }}</strong>, {{ espec().formato }}.
        <span class="text-abeto-2">Mínimo {{ espec().rotuloMinimo }}. JPG ou PNG.</span>
      </p>
      <button type="button" class="min-h-10 rounded-full px-3 text-[14px] font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto" (click)="copiarPedido()">
        {{ pedidoCopiado() ? 'Pedido copiado' : 'Copiar o pedido para o cliente' }}
      </button>
    </div>

    @if (exibida(); as foto) {
      <div class="mt-3 rounded-[20px] bg-white p-3 ring-1 ring-linha sm:p-4">
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
            [attr.aria-describedby]="tipo() + '-dica'"
            (keydown)="tecla($event)"
            (pointerdown)="$event.stopPropagation(); apontar($event)"
          ></button>
        </div>
        <p [id]="tipo() + '-dica'" class="mt-2.5 flex items-center gap-2 text-[13px] leading-snug text-abeto-2">
          <svg class="shrink-0" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="7" /><circle cx="12" cy="12" r="1.5" fill="currentColor" /><path d="M12 2v3M12 19v3M2 12h3M19 12h3" stroke-linecap="round" /></svg>
          {{ computador ? 'Clique ou arraste' : 'Toque' }} na foto no que precisa ficar sempre à vista (um rosto, a fachada, a logo da obra).
        </p>

        <!-- Como fica cortada em cada lugar, com o véu na cor da marca. -->
        @if (tipo() === 'fundo') {
          <div class="mx-auto mt-4 grid max-w-[30rem] grid-cols-2 items-end gap-x-2.5 gap-y-3">
            @for (corte of cortesDaEntrada; track corte.rotulo) {
              <figure>
                <div class="miniatura" [style.aspect-ratio]="corte.proporcao">
                  <img class="foto" [src]="foto.url" alt="" [style.object-position]="posicao()" />
                  <i class="veu" [style.background]="veu('entrada')"></i>
                  <span class="absolute inset-x-[12%] top-[12%] block">
                    <span class="block h-1 w-[45%] rounded-full bg-white/60"></span>
                    <span class="mt-1.5 block h-1.5 w-[85%] rounded-full bg-white"></span>
                    <span class="mt-1 block h-1.5 w-[60%] rounded-full bg-white"></span>
                  </span>
                  <span class="absolute inset-x-[12%] top-[40%] bottom-[16%] block rounded-md bg-white/95 shadow-lg"></span>
                </div>
                <figcaption class="mt-1.5 text-[12px] text-abeto-2">{{ corte.rotulo }}</figcaption>
              </figure>
            }
          </div>
        } @else {
          <div class="mx-auto mt-4 grid max-w-[30rem] gap-3">
            <figure>
              <div class="miniatura" style="aspect-ratio: 6 / 1">
                <img class="foto" [src]="foto.url" alt="" [style.object-position]="posicao()" />
                <i class="veu" [style.background]="veu('capa')"></i>
                <span class="absolute inset-y-0 left-[4%] flex max-w-[78%] items-center gap-1.5">
                  <span class="grid size-5 shrink-0 place-items-center overflow-hidden rounded-md bg-white p-0.5 text-[7px] font-bold" [style.color]="tema().acao">@if (logo(); as lg) {<img class="block size-full object-contain" [src]="lg.url" alt="" />} @else {{{ sigla() }}}</span>
                  <span class="display line-clamp-1 text-[11px] font-bold text-white">{{ nome() || 'Nome da empresa' }}</span>
                </span>
              </div>
              <figcaption class="mt-1.5 text-[12px] text-abeto-2">Topo da página no computador</figcaption>
            </figure>
            <div class="grid grid-cols-[minmax(0,1.45fr)_minmax(0,1fr)] items-end gap-2.5">
              <figure>
                <div class="miniatura" style="aspect-ratio: 12 / 5">
                  <img class="foto" [src]="foto.url" alt="" [style.object-position]="posicao()" />
                  <i class="veu" [style.background]="veu('capa')"></i>
                  <span class="absolute bottom-[12%] left-[6%] flex max-w-[84%] items-center gap-1">
                    <span class="grid size-4 shrink-0 place-items-center overflow-hidden rounded bg-white p-px text-[6px] font-bold" [style.color]="tema().acao">@if (logo(); as lg) {<img class="block size-full object-contain" [src]="lg.url" alt="" />} @else {{{ sigla() }}}</span>
                    <span class="display line-clamp-1 text-[9px] font-bold text-white">{{ nome() || 'Nome da empresa' }}</span>
                  </span>
                </div>
                <figcaption class="mt-1.5 text-[12px] text-abeto-2">Topo da página no celular</figcaption>
              </figure>
              <!-- O celular: a faixa com a imagem no alto da tela de entrada. -->
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
            </div>
          </div>
        }

        @if (!outraExiste()) {
          <p class="mt-3 text-[13px] leading-snug text-abeto-2">{{ espec().semOutra }}</p>
        }
      </div>

      @if (abaixoDoIdeal(); as aviso) {
        <p class="mt-2 text-[13.5px] text-[#855300]">{{ aviso }}</p>
      }

      <div class="mt-3 flex flex-wrap gap-2">
        <button type="button" class="botao-pequeno" [disabled]="processando()" (click)="escolher()">Trocar</button>
        <button type="button" class="botao-pequeno" [disabled]="processando()" (click)="remover()">Remover</button>
      </div>
      @if (processando()) {
        <p class="mt-2 flex items-center gap-2 text-[14px] text-abeto-2" role="status">
          <span class="size-4 animate-spin rounded-full border-2 border-linha border-t-abeto" aria-hidden="true"></span>
          Preparando a imagem…
        </p>
      }
    } @else {
      <button
        type="button"
        class="zona-fundo mt-3"
        [class.zona-fundo-ativa]="arrastando()"
        [attr.aria-describedby]="tipo() + '-rotulo'"
        (click)="escolher()"
        (dragover)="arrastou($event)"
        (dragleave)="arrastando.set(false)"
        (drop)="soltou($event)"
      >
        @if (processando()) {
          <span class="size-6 animate-spin rounded-full border-[3px] border-linha border-t-abeto" aria-hidden="true"></span>
          <span class="text-[15px] font-semibold">Preparando a imagem…</span>
        } @else {
          <svg class="text-abeto-2" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path d="M3.5 8.5A2 2 0 0 1 5.5 6.5h2l1.5-2h6l1.5 2h2a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z" stroke-linejoin="round" /><circle cx="12" cy="13" r="3.5" />
          </svg>
          <span class="text-[15px] font-semibold">{{ espec().enviar }}</span>
          <span class="text-[13px] text-abeto-2">
            <span class="hidden sm:inline">Arraste o arquivo para cá ou clique para escolher · </span>JPG, PNG ou WebP
          </span>
          <span class="text-[13px] text-abeto-2">{{ outraExiste() ? 'Sem ela, usamos a outra foto da empresa.' : 'Sem foto, fica nas cores da marca.' }}</span>
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
  /** Qual foto: a da entrada ("fundo") ou a do topo ("capa"). */
  readonly tipo = input<TipoFoto>('fundo');
  /** A foto que já está salva (edição). */
  readonly atual = input<FundoEmpresa | null>(null);
  /** A empresa tem (ou vai ter) a outra foto: sem ela, esta aparece também no lugar da outra. */
  readonly outraExiste = input(false);
  /** A paleta escolhida: o véu da prévia é a cor da marca de verdade. */
  readonly tema = input.required<TemaMarca>();
  readonly nome = input('');
  readonly sigla = input('');
  readonly logo = input<LogoEmpresa | null>(null);
  readonly mudanca = model<MudancaFundo>(null);

  protected readonly computador = matchMedia('(pointer: fine)').matches;
  protected readonly espec = computed(() => ESPEC[this.tipo()]);
  /** A metade da entrada no computador: num notebook, quase quadrada; num monitor largo, mais deitada. */
  protected readonly cortesDaEntrada = [
    { rotulo: 'Entrada no notebook', proporcao: '9 / 10' },
    { rotulo: 'Entrada em monitor largo', proporcao: '5 / 4' },
  ];
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

  /** Uma foto nova abaixo do tamanho ideal: aceita, mas avisa. */
  protected readonly abaixoDoIdeal = computed(() => {
    const p = this.preparada();
    const min = this.espec().minimo;
    if (!p || (p.largura >= min.largura && p.altura >= min.altura)) return null;
    return `A imagem tem ${p.largura} × ${p.altura} px, abaixo de ${this.espec().rotuloMinimo}: em telas grandes pode ficar um pouco borrada.`;
  });

  protected readonly processando = signal(false);
  protected readonly arrastando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly pedidoCopiado = signal(false);

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

  /** O pedido pronto para mandar ao cliente (tamanho, formato e o que evitar). */
  protected async copiarPedido() {
    try {
      await navigator.clipboard.writeText(this.espec().pedido);
      this.pedidoCopiado.set(true);
      setTimeout(() => this.pedidoCopiado.set(false), 2200);
    } catch {
      this.erro.set('Não deu para copiar agora. O tamanho ideal está escrito acima.');
    }
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
      const foto = await prepararFundo(arquivo, this.tipo());
      this.liberar();
      this.mudanca.set({ tipo: 'nova', foto, foco: { x: 50, y: 50 } });
    } catch (e) {
      this.erro.set(e instanceof Error ? e.message : 'Não foi possível usar essa imagem.');
    } finally {
      this.processando.set(false);
    }
  }

  private liberar() {
    const p = this.preparada();
    if (p) URL.revokeObjectURL(p.url);
  }
}
