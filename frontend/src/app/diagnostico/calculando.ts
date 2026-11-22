import { Component, input } from '@angular/core';

import { Curva } from '../compartilhado/curva';

/** Pausa curta e intencional antes do resultado: o líder vê o ponto percorrendo a curva. */
@Component({
  selector: 'app-calculando',
  imports: [Curva],
  host: { class: 'flex min-h-dvh flex-col items-center justify-center px-6 text-center' },
  template: `
    <div class="w-full max-w-md" role="status" aria-live="polite">
      <app-curva modo="calculando" class="aparecer" />
      <p class="display aparecer mt-8 text-[28px] leading-tight font-semibold">
        Calculando o seu ponto na curva{{ nome() ? ', ' + nome() : '' }}…
      </p>
      <p class="aparecer mt-3 text-[16px] text-abeto-2" style="animation-delay: 300ms">
        Cruzando as suas respostas com direção, desenvolvimento e visibilidade.
      </p>
    </div>
  `,
})
export class Calculando {
  readonly nome = input('');
}
