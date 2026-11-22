import { Component, computed, input, output } from '@angular/core';

/**
 * Topo do questionário: voltar + progresso em segmentos. Os segmentos são agrupados
 * ("Sobre você" e "Seu momento") porque essa divisão é real para o líder.
 */
@Component({
  selector: 'app-barra-progresso',
  host: { class: 'block bg-nevoa/85 backdrop-blur-md' },
  template: `
    <div class="flex h-16 items-center gap-3 px-2 sm:px-4">
      <button
        type="button"
        class="grid size-12 shrink-0 place-items-center rounded-full text-abeto transition-colors hover:bg-white/70"
        aria-label="Voltar"
        (click)="voltar.emit()"
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
          <path d="M15 5l-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>

      <div
        class="flex flex-1 gap-3"
        role="progressbar"
        aria-label="Progresso do diagnóstico"
        [attr.aria-valuenow]="atual()"
        aria-valuemin="1"
        [attr.aria-valuemax]="total()"
        [attr.aria-valuetext]="'Etapa ' + atual() + ' de ' + total()"
      >
        @for (grupo of segmentos(); track $index) {
          <div class="flex flex-1 gap-1" [style.flex-grow]="grupo.length">
            @for (s of grupo; track s.numero) {
              <span class="h-1.5 flex-1 overflow-hidden rounded-full bg-linha">
                <span
                  class="block h-full rounded-full bg-abeto transition-[width] duration-500 ease-[var(--ease-mola)]"
                  [style.width.%]="s.numero <= atual() ? 100 : 0"
                ></span>
              </span>
            }
          </div>
        }
      </div>

      <span class="w-14 shrink-0 pr-2 text-right text-[13px] font-semibold text-abeto-2 tabular-nums" aria-hidden="true">
        {{ atual() }}/{{ total() }}
      </span>
    </div>
  `,
})
export class BarraProgresso {
  /** Etapa atual, começando em 1. */
  readonly atual = input.required<number>();
  /** Tamanho de cada grupo, por exemplo [4, 3]. */
  readonly grupos = input.required<number[]>();
  readonly voltar = output();

  protected readonly total = computed(() => this.grupos().reduce((a, b) => a + b, 0));
  protected readonly segmentos = computed(() => {
    let numero = 0;
    return this.grupos().map((tamanho) => Array.from({ length: tamanho }, () => ({ numero: ++numero })));
  });
}
