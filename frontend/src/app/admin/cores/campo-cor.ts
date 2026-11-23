import { Component, computed, input, model, output } from '@angular/core';

import { normalizarHex } from './cores';

/**
 * Uma cor da marca: a bolinha abre o seletor do sistema e o campo aceita o código do manual da
 * marca (ex.: #0057B8). Vazio = sem cor.
 */
@Component({
  selector: 'app-campo-cor',
  styleUrl: '../admin.css',
  host: { class: 'block' },
  template: `
    <label [for]="idCampo()" class="campo-rotulo">{{ rotulo() }}</label>
    <div class="campo flex items-center gap-3 pr-1.5 pl-2">
      <span class="relative grid size-10 shrink-0 place-items-center rounded-full" [class.amostra-vazia]="!cor()" [style.background]="cor() ?? null">
        @if (!cor()) {
          <svg class="text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
        }
        <input
          type="color"
          class="absolute inset-0 size-full cursor-pointer rounded-full opacity-0"
          [value]="cor() ?? '#0F2B26'"
          [attr.aria-label]="'Escolher ' + rotulo().toLowerCase() + ' no seletor de cores'"
          (input)="escolheu($event)"
        />
      </span>
      <input
        [id]="idCampo()"
        class="h-14 min-w-0 flex-1 bg-transparent font-mono text-[16px] uppercase tracking-wide"
        type="text"
        inputmode="text"
        autocomplete="off"
        autocapitalize="characters"
        spellcheck="false"
        maxlength="7"
        placeholder="#0057B8"
        [value]="valor()"
        [attr.aria-invalid]="!!erro() || (!!valor() && !cor())"
        [attr.aria-describedby]="idCampo() + '-ajuda'"
        (input)="digitou($event)"
        (blur)="ajustar()"
      />
      @if (valor()) {
        <button type="button" class="grid size-10 shrink-0 place-items-center rounded-full text-abeto-2 hover:bg-nevoa hover:text-abeto" [attr.aria-label]="'Remover a ' + rotulo().toLowerCase()" (click)="limpar()">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
        </button>
      }
    </div>
    <p [id]="idCampo() + '-ajuda'" class="mt-1.5 min-h-5 text-[13px]" aria-live="polite">
      @if (erro(); as mensagem) {
        <span class="font-medium text-travada">{{ mensagem }}</span>
      } @else if (valor() && !cor()) {
        <span class="font-medium text-travada">Use um código de cor como #0057B8.</span>
      } @else {
        <span class="text-abeto-2">{{ ajuda() }}</span>
      }
    </p>
  `,
  styles: `
    .amostra-vazia {
      border: 1.5px dashed var(--color-abeto-3);
    }
  `,
})
export class CampoCor {
  readonly rotulo = input.required<string>();
  readonly ajuda = input('');
  readonly idCampo = input.required<string>();
  readonly erro = input<string | null>(null);
  /** O que está no campo (pode estar no meio da digitação). */
  readonly valor = model('');
  readonly editou = output();

  protected readonly cor = computed(() => normalizarHex(this.valor()));

  protected escolheu(evento: Event) {
    this.valor.set((evento.target as HTMLInputElement).value.toUpperCase());
    this.editou.emit();
  }

  protected digitou(evento: Event) {
    const texto = (evento.target as HTMLInputElement).value.trim();
    this.valor.set(texto && !texto.startsWith('#') ? `#${texto}` : texto);
    this.editou.emit();
  }

  protected ajustar() {
    const cor = this.cor();
    if (cor && cor !== this.valor()) this.valor.set(cor);
  }

  protected limpar() {
    this.valor.set('');
    this.editou.emit();
  }
}
