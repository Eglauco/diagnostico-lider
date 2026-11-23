import { Component, computed, input } from '@angular/core';

import { estiloFaixa } from '../compartilhado/faixas';
import { StatusDiagnostico } from './admin-modelos';

/** "● 89 Ascensão": índice e faixa na cor da faixa. */
@Component({
  selector: 'app-selo-faixa',
  host: { class: 'inline-flex' },
  template: `
    @if (estilo(); as e) {
      <span
        class="inline-flex items-center gap-1.5 rounded-full py-1 pr-2.5 pl-2 text-[13px] font-semibold whitespace-nowrap"
        [style.background]="e.suave"
        [style.color]="e.texto"
      >
        <i class="block size-2 rounded-full" [style.background]="e.cor"></i>
        <span class="tabular-nums">{{ indice() }}</span>
        <span class="font-medium">{{ e.rotulo }}</span>
      </span>
    } @else {
      <span class="text-[13px] text-abeto-2">—</span>
    }
  `,
})
export class SeloFaixa {
  readonly faixa = input<string | null>(null);
  readonly indice = input<number | null>(null);
  protected readonly estilo = computed(() => estiloFaixa(this.faixa()));
}

/** Situação do diagnóstico; em andamento mostra quantas perguntas já foram respondidas. */
@Component({
  selector: 'app-selo-status',
  host: { class: 'inline-flex' },
  template: `
    @if (status() === 'CONCLUIDO') {
      <span class="inline-flex items-center gap-1.5 text-[13px] font-semibold whitespace-nowrap text-abeto">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true">
          <path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        Concluído
      </span>
    } @else {
      <span class="inline-flex items-center gap-2 text-[13px] font-semibold whitespace-nowrap text-abeto-2">
        <span class="relative grid size-3.5 place-items-center" aria-hidden="true">
          <svg viewBox="0 0 16 16" class="size-3.5 -rotate-90">
            <circle cx="8" cy="8" r="6" fill="none" stroke="#D2DDD8" stroke-width="2.5" />
            <circle cx="8" cy="8" r="6" fill="none" stroke="#7D918B" stroke-width="2.5" pathLength="1" stroke-dasharray="1" [attr.stroke-dashoffset]="1 - progresso()" />
          </svg>
        </span>
        Em andamento · {{ respondidas() }}/{{ total() }}
      </span>
    }
  `,
})
export class SeloStatus {
  readonly status = input.required<StatusDiagnostico>();
  readonly respondidas = input(0);
  readonly total = input(0);
  protected readonly progresso = computed(() => (this.total() ? this.respondidas() / this.total() : 0));
}
