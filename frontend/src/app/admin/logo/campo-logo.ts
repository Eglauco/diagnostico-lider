import { Component, computed, DestroyRef, ElementRef, inject, input, model, output, signal, viewChild } from '@angular/core';

import { LogoEmpresa, TemaMarca } from '../../compartilhado/tema-marca';
import { LogoPreparada, prepararLogo } from './preparar-logo';

/** O que mudou na logomarca (vai para o servidor quando o formulário for salvo). */
export type MudancaLogo = { tipo: 'nova'; logo: LogoPreparada } | { tipo: 'remover' } | null;

/**
 * Logomarca da empresa: arrastar ou escolher o arquivo, ver como fica no fundo claro e no
 * escuro, tirar o fundo branco e aproveitar as cores do logo na paleta.
 */
@Component({
  selector: 'app-campo-logo',
  host: { class: 'block' },
  template: `
    <p class="text-[15px] font-semibold" id="logo-rotulo">Logomarca</p>
    <p class="mt-1 text-[14px] text-abeto-2">
      Aparece no questionário, no resultado, nos PDFs, no e-mail e no QR Code. PNG com fundo transparente ou SVG
      ficam melhores.
    </p>

    @if (exibida(); as url) {
      <div class="mt-4 grid grid-cols-2 gap-2">
        <figure class="grid h-28 place-items-center rounded-2xl p-4 ring-1 ring-linha" [style.background]="tema().fundo">
          <img [src]="url" class="max-h-full max-w-full object-contain" alt="Logomarca sobre o fundo claro do questionário" />
        </figure>
        <figure class="grid h-28 place-items-center rounded-2xl p-4" [style.background]="tema().tinta">
          <span class="grid max-h-full max-w-full place-items-center rounded-xl bg-white px-3 py-2">
            <img [src]="url" class="max-h-14 max-w-full object-contain" alt="Logomarca no cartão branco usado sobre fundos escuros" />
          </span>
        </figure>
      </div>
      <p class="mt-1.5 text-[12.5px] text-abeto-2">À esquerda, o questionário. À direita, a capa do relatório e o cartão do QR Code.</p>

      @if (preparada(); as p) {
        @if (p.opaca) {
          <label class="mt-3 flex min-h-11 cursor-pointer items-center gap-3 text-[14px]">
            <input type="checkbox" class="size-5 accent-[var(--color-abeto)]" [checked]="p.semFundo" (change)="alternarFundo($event)" />
            Remover o fundo branco da imagem
          </label>
        }
        @if (p.cores.length) {
          <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-2xl bg-nevoa-2 px-3.5 py-2.5 ring-1 ring-linha/70">
            <span class="text-[14px] text-abeto-2">Cores do logo:</span>
            <span class="flex items-center gap-1.5" aria-hidden="true">
              @for (cor of p.cores; track cor) {
                <i class="block size-6 rounded-full ring-2 ring-white" [style.background]="cor"></i>
              }
            </span>
            <span class="sr-only">{{ p.cores.join(' e ') }}</span>
            <button type="button" class="ml-auto min-h-10 rounded-full px-3 text-[14px] font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto" (click)="usarCores.emit(p.cores)">
              Usar estas cores
            </button>
          </div>
        }
      }

      <div class="mt-3 flex flex-wrap gap-2">
        <button type="button" class="botao-pequeno" [disabled]="processando()" (click)="escolher()">Trocar logomarca</button>
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
        class="zona-logo mt-4"
        [class.zona-logo-ativa]="arrastando()"
        aria-describedby="logo-rotulo"
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
            <rect x="3.5" y="4.5" width="17" height="15" rx="3" /><circle cx="9" cy="10" r="1.8" /><path d="M20.5 15.5l-4.5-4.5-8 8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span class="text-[15px] font-semibold">Enviar a logomarca</span>
          <span class="text-[13px] text-abeto-2">
            <span class="hidden sm:inline">Arraste o arquivo para cá ou clique para escolher · </span>PNG, SVG, JPG ou WebP
          </span>
        }
      </button>
    }

    <input #seletor type="file" class="sr-only" tabindex="-1" aria-hidden="true" accept="image/png,image/jpeg,image/webp,image/svg+xml,.svg" (change)="escolheu($event)" />
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
    .zona-logo {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 6px;
      width: 100%;
      min-height: 132px;
      padding: 18px;
      border: 1.5px dashed var(--color-abeto-3);
      border-radius: 20px;
      background: rgb(255 255 255 / 0.6);
      text-align: center;
      transition:
        background-color 160ms,
        border-color 160ms;
    }
    .zona-logo:hover,
    .zona-logo-ativa {
      border-color: var(--color-abeto);
      background: #fff;
    }
  `,
})
export class CampoLogo {
  /** Logo que já está salva (edição). */
  readonly atual = input<LogoEmpresa | null>(null);
  /** Paleta escolhida, para mostrar a logo sobre as cores de verdade. */
  readonly tema = input.required<TemaMarca>();
  readonly mudanca = model<MudancaLogo>(null);
  readonly usarCores = output<string[]>();

  private readonly seletor = viewChild.required<ElementRef<HTMLInputElement>>('seletor');
  private original: File | null = null;

  protected readonly preparada = computed(() => {
    const m = this.mudanca();
    return m?.tipo === 'nova' ? m.logo : null;
  });
  protected readonly exibida = computed(() => {
    const m = this.mudanca();
    if (m?.tipo === 'nova') return m.logo.url;
    return m?.tipo === 'remover' ? null : (this.atual()?.url ?? null);
  });
  protected readonly processando = signal(false);
  protected readonly arrastando = signal(false);
  protected readonly erro = signal<string | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.liberar());
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

  protected alternarFundo(evento: Event) {
    if (this.original) void this.processar(this.original, (evento.target as HTMLInputElement).checked);
  }

  protected remover() {
    this.liberar();
    this.original = null;
    this.erro.set(null);
    this.mudanca.set(this.atual() ? { tipo: 'remover' } : null);
  }

  private async processar(arquivo: File, removerFundo = true) {
    this.processando.set(true);
    this.erro.set(null);
    try {
      const logo = await prepararLogo(arquivo, removerFundo);
      this.liberar();
      this.original = arquivo;
      this.mudanca.set({ tipo: 'nova', logo });
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
