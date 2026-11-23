import { Component, effect, input, linkedSignal, model, output, untracked } from '@angular/core';

import { CampoCor } from './campo-cor';

/** "Cores da marca": principal (botões e destaques) e secundária opcional (a curva da carreira). */
@Component({
  selector: 'app-secao-cores',
  imports: [CampoCor],
  host: { class: 'block' },
  template: `
    <fieldset>
      <legend class="text-[15px] font-semibold">Cores da marca</legend>
      <p class="mt-1 text-[14px] text-abeto-2">
        Usadas no questionário, no resultado do líder, nos PDFs, no e-mail e no cartão do QR Code. Sem cor, vale a
        identidade do Diagnóstico do Líder.
      </p>

      <div class="mt-4 grid gap-x-4 gap-y-2 sm:grid-cols-2">
        <app-campo-cor rotulo="Cor principal" ajuda="Botões, monograma e destaques." idCampo="cor-primaria" [(valor)]="primaria" [erro]="erroPrimaria()" (editou)="editou.emit()" />
        @if (mostrarSecundaria()) {
          <app-campo-cor rotulo="Cor secundária" ajuda="A curva da carreira. Opcional." idCampo="cor-secundaria" [(valor)]="secundaria" [erro]="erroSecundaria()" (editou)="editou.emit()" />
        } @else {
          <div class="flex items-start sm:pt-[30px]">
            <button
              type="button"
              class="inline-flex min-h-14 w-full items-center justify-center gap-2 rounded-2xl border-[1.5px] border-dashed border-abeto-3 px-4 text-[15px] font-semibold text-abeto hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
              [disabled]="!primaria()"
              [title]="primaria() ? '' : 'Escolha a cor principal primeiro'"
              (click)="mostrarSecundaria.set(true)"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
              Adicionar cor secundária
            </button>
          </div>
        }
      </div>

      @if (ajustes().length) {
        <ul class="aparecer mt-2 space-y-2" aria-live="polite">
          @for (a of ajustes(); track a) {
            <li class="flex items-start gap-2.5 rounded-2xl bg-plato-suave px-3.5 py-2.5 text-[14px] text-[#855300]">
              <svg class="mt-0.5 shrink-0" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 11v5.5M12 7.5v.5" stroke-linecap="round" /></svg>
              {{ a }}
            </li>
          }
        </ul>
      }
    </fieldset>
  `,
})
export class SecaoCores {
  readonly primaria = model('');
  readonly secundaria = model('');
  readonly ajustes = input<string[]>([]);
  readonly erroPrimaria = input<string | null>(null);
  readonly erroSecundaria = input<string | null>(null);
  readonly editou = output();

  /** Aberta quando já existe secundária (edição) ou quando a pessoa pede. */
  protected readonly mostrarSecundaria = linkedSignal(() => !!this.secundaria());

  constructor() {
    // Secundária depende da principal: sem principal, ela sai junto.
    effect(() => {
      if (!this.primaria() && untracked(this.secundaria)) this.secundaria.set('');
    });
  }
}
