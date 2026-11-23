import { Component, input } from '@angular/core';

@Component({
  selector: 'app-marca',
  host: { class: 'inline-flex items-center gap-2.5 text-[15px] font-semibold tracking-[0.005em] text-abeto' },
  template: `
    <svg width="28" height="28" viewBox="0 0 26 26" aria-hidden="true" focusable="false">
      <rect width="26" height="26" rx="7" fill="#0F2B26" />
      <path
        d="M5 19 C 9 19, 9 8, 13 8 S 17 8, 21 12"
        fill="none"
        stroke="#FF5B37"
        stroke-width="2.6"
        stroke-linecap="round"
      />
    </svg>
    <span [class]="compacta() ? 'sr-only sm:not-sr-only' : ''">Diagnóstico do Líder</span>
  `,
})
export class Marca {
  /** No celular mostra só o símbolo (o nome continua disponível para leitores de tela). */
  readonly compacta = input(false);
}
