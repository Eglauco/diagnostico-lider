import { Component, input, output } from '@angular/core';

import { Curva } from '../compartilhado/curva';
import { Marca } from '../compartilhado/marca';

export type Retomada =
  | { tipo: 'em-andamento'; primeiroNome: string; respondidas: number; total: number }
  | { tipo: 'concluido'; primeiroNome: string };

@Component({
  selector: 'app-boas-vindas',
  imports: [Curva, Marca],
  host: { class: 'flex min-h-dvh flex-col' },
  template: `
    <header class="flex flex-wrap items-center justify-between gap-3 px-5 pt-5 sm:px-8 sm:pt-8">
      <app-marca />
      @if (empresa(); as nome) {
        <span class="rounded-full bg-white/70 px-3 py-1.5 text-[13px] font-medium text-abeto-2 ring-1 ring-linha">
          Convite de <strong class="font-semibold text-abeto">{{ nome }}</strong>
        </span>
      }
    </header>

    <div class="px-5 pt-10 sm:px-8 sm:pt-16">
      <p class="rotulo surgir text-brasa-escura" style="--i: 0">Diagnóstico de carreira para líderes</p>
      <h1 class="display surgir mt-4 text-[44px] leading-[0.98] font-bold sm:text-[64px]" style="--i: 1">
        Em que ponto da curva está a sua carreira?
      </h1>
    </div>

    <app-curva class="surgir mt-7 px-3 sm:px-6" style="--i: 2" modo="hero" />

    <div class="px-5 sm:px-8">
      <p class="surgir mt-5 text-[18px] leading-relaxed text-abeto-2" style="--i: 3">
        Toda carreira sobe, chega a um platô e, se nada muda, começa a cair. Em {{ totalPerguntas() }} perguntas você
        descobre onde a sua está agora e o que fazer a seguir.
      </p>
      <ul class="surgir mt-6 flex flex-wrap gap-x-5 gap-y-2 text-[15px] font-medium text-abeto-2" style="--i: 4">
        <li class="inline-flex items-center gap-2">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" stroke-linecap="round" />
          </svg>
          2 minutos
        </li>
        <li class="inline-flex items-center gap-2">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M7 3h7l5 5v13H7z" stroke-linejoin="round" /><path d="M14 3v5h5" stroke-linejoin="round" />
          </svg>
          Resultado em PDF no seu e-mail
        </li>
      </ul>
    </div>

    <div class="rodape-fixo surgir mt-auto px-5 sm:px-8" style="--i: 5">
      @switch (retomada()?.tipo) {
        @case ('em-andamento') {
          <div class="rounded-3xl bg-white p-5 shadow-[0_18px_40px_-28px_rgb(15_43_38/0.6)] ring-1 ring-linha">
            <p class="text-[17px] font-semibold">Que bom ter você de volta, {{ retomada()!.primeiroNome }}.</p>
            <p class="mt-1 text-[15px] text-abeto-2">{{ textoProgresso() }}</p>
            <button type="button" class="botao-primario mt-4 w-full" (click)="continuar.emit()">
              Continuar de onde parei <span class="seta" aria-hidden="true">→</span>
            </button>
            <button type="button" class="mt-2 min-h-12 w-full text-[15px] font-semibold text-abeto-2 underline-offset-4 hover:underline" (click)="recomecar.emit()">
              Começar do zero
            </button>
          </div>
        }
        @case ('concluido') {
          <div class="rounded-3xl bg-white p-5 shadow-[0_18px_40px_-28px_rgb(15_43_38/0.6)] ring-1 ring-linha">
            <p class="text-[17px] font-semibold">{{ retomada()!.primeiroNome }}, você já concluiu este diagnóstico.</p>
            <button type="button" class="botao-primario mt-4 w-full" (click)="verResultado.emit()">
              Ver meu resultado <span class="seta" aria-hidden="true">→</span>
            </button>
            <button type="button" class="mt-2 min-h-12 w-full text-[15px] font-semibold text-abeto-2 underline-offset-4 hover:underline" (click)="recomecar.emit()">
              Fazer de novo
            </button>
          </div>
        }
        @default {
          <button type="button" class="botao-primario w-full" (click)="comecar.emit()">
            Começar diagnóstico <span class="seta" aria-hidden="true">→</span>
          </button>
          <p class="mt-3 text-center text-[13px] text-abeto-2">Usamos seus dados apenas para gerar e enviar o seu diagnóstico.</p>
        }
      }
    </div>
  `,
})
export class BoasVindas {
  readonly empresa = input<string | null>(null);
  readonly totalPerguntas = input.required<number>();
  readonly retomada = input<Retomada | null>(null);

  readonly comecar = output();
  readonly continuar = output();
  readonly recomecar = output();
  readonly verResultado = output();

  protected textoProgresso(): string {
    const r = this.retomada();
    if (r?.tipo !== 'em-andamento') return '';
    if (r.respondidas === 0) return 'Seus dados estão salvos. Faltam só as perguntas.';
    return `Você já respondeu ${r.respondidas} de ${r.total} perguntas.`;
  }
}
