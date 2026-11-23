import { Component, computed, input } from '@angular/core';

import { Marca } from './marca';
import { logoPedeNome, MarcaEmpresa } from './tema-marca';

/**
 * Cabeçalho das telas do líder. No link de uma empresa, ela fica em destaque (logomarca, ou o
 * monograma com as iniciais quando não há logo); no link geral, a marca do Diagnóstico do Líder.
 */
@Component({
  selector: 'app-marca-empresa',
  imports: [Marca],
  host: { class: 'flex min-w-0 items-center' },
  template: `
    @if (empresa(); as e) {
      <span class="flex min-w-0 items-center gap-2.5">
        @if (e.logo; as logo) {
          <img
            class="block h-9 w-auto max-w-[180px] shrink-0 object-contain"
            [src]="logo.url"
            [attr.width]="logo.largura"
            [attr.height]="logo.altura"
            [alt]="mostrarNome() ? '' : e.nome"
          />
        } @else {
          <span class="grid size-8 shrink-0 place-items-center rounded-[9px] bg-acao text-[12.5px] font-bold tracking-[0.02em] text-sobre-acao" aria-hidden="true">
            {{ e.sigla }}
          </span>
        }
        @if (mostrarNome()) {
          <!-- Nome longo quebra em até 2 linhas em vez de sumir em reticências. -->
          <span class="line-clamp-2 text-[15px] leading-[1.2] font-semibold break-words text-abeto">{{ e.nome }}</span>
        }
      </span>
    } @else {
      <app-marca />
    }
  `,
})
export class MarcaEmpresaCabecalho {
  readonly empresa = input<MarcaEmpresa | null>(null);

  /** Sem logo, ou com logo só de símbolo: o nome aparece ao lado. */
  protected readonly mostrarNome = computed(() => {
    const logo = this.empresa()?.logo;
    return !logo || logoPedeNome(logo);
  });
}

/** "por Diagnóstico do Líder": a nossa marca discreta quando a empresa está em destaque. */
@Component({
  selector: 'app-credito-diagnostico',
  // Sem "display" no host: quem usa decide (ex.: hidden sm:inline-flex).
  host: { class: 'items-center gap-1.5 text-[12.5px] font-medium text-abeto-2' },
  template: `
    por
    <svg width="14" height="14" viewBox="0 0 26 26" aria-hidden="true" focusable="false">
      <rect width="26" height="26" rx="7" style="fill: var(--color-abeto)" />
      <path d="M5 19 C 9 19, 9 8, 13 8 S 17 8, 21 12" fill="none" style="stroke: var(--color-nevoa)" stroke-width="2.8" stroke-linecap="round" />
    </svg>
    <span class="font-semibold text-abeto">Diagnóstico do Líder</span>
  `,
})
export class CreditoDiagnostico {}
