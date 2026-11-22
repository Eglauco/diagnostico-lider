import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Marca } from './compartilhado/marca';

@Component({
  selector: 'app-nao-encontrado',
  imports: [Marca, RouterLink],
  host: { class: 'mx-auto flex min-h-dvh max-w-xl flex-col px-5 pt-5 pb-10 sm:px-8 sm:pt-8' },
  template: `
    <app-marca />
    <div class="flex flex-1 flex-col justify-center">
      <p class="rotulo text-brasa-escura">Fora da curva</p>
      <h1 class="display mt-3 text-[40px] leading-[1.02] font-bold">{{ titulo() }}</h1>
      <p class="mt-4 text-[18px] text-abeto-2">{{ mensagem() }}</p>
      <a routerLink="/" class="botao-primario mt-8 self-start">Ir para o diagnóstico <span class="seta" aria-hidden="true">→</span></a>
    </div>
  `,
})
export class NaoEncontrado {
  readonly titulo = input('Não encontramos esta página');
  readonly mensagem = input('O endereço pode estar incompleto. Confira o link que você recebeu.');
}
