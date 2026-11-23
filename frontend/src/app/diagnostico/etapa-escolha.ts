import { Component, input, output } from '@angular/core';

import { OpcaoPerfil } from '../api/modelos';

let sequencia = 0;
const LETRAS = 'ABCDEFGHIJKLMN';

/**
 * Escolha de identificação (cargo, tempo no cargo): os mesmos cartões das perguntas, um toque
 * escolhe e a tela anda sozinha. O conteúdo projetado aparece abaixo das opções (ex.: o nome do
 * cargo quando ele não está na lista).
 */
@Component({
  selector: 'app-etapa-escolha',
  host: { class: 'block', '(document:keydown)': 'atalho($event)' },
  template: `
    <div class="px-5 pt-8 pb-12 sm:px-8 sm:pt-14">
      <p class="rotulo surgir text-abeto-2" style="--i: 0">{{ sobretitulo() }}</p>
      <h1 [id]="idTitulo" class="display surgir mt-3 text-[32px] leading-[1.06] font-semibold sm:text-[44px]" style="--i: 1">
        {{ titulo() }}
      </h1>
      @if (ajuda()) {
        <p [id]="idAjuda" class="surgir mt-3 text-[15px] text-abeto-2" style="--i: 2">{{ ajuda() }}</p>
      }

      <div
        class="mt-8 grid gap-3"
        role="group"
        [attr.aria-labelledby]="idTitulo"
        [attr.aria-describedby]="ajuda() ? idAjuda : null"
      >
        @for (opcao of opcoes(); track opcao.valor; let i = $index) {
          <button
            type="button"
            class="opcao surgir"
            [style.--i]="i + 3"
            [class.opcao-marcada]="opcao.valor === selecionada()"
            [class.opcao-apagada]="enviando() && !!selecionada() && opcao.valor !== selecionada()"
            [attr.aria-pressed]="opcao.valor === selecionada()"
            [attr.aria-disabled]="enviando()"
            (click)="clicar(opcao.valor)"
          >
            <span class="tecla" aria-hidden="true">
              @if (opcao.valor === selecionada()) {
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.2">
                  <path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              } @else {
                {{ letra(i) }}
              }
            </span>
            <span class="flex-1">{{ opcao.texto }}</span>
          </button>
        }
      </div>

      <ng-content />

      <div class="mt-3 min-h-6 text-[15px]" aria-live="polite">
        @if (erro(); as mensagem) {
          <p class="aparecer flex items-start gap-2 font-medium text-travada">
            <svg class="mt-0.5 shrink-0" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
              <circle cx="12" cy="12" r="9" /><path d="M12 7.5v5.5M12 16.5v.5" stroke-linecap="round" />
            </svg>
            {{ mensagem }}
          </p>
        }
      </div>

      @if (selecionada() && !enviando()) {
        <div class="aparecer mt-3 flex justify-end" data-acao-do-campo>
          <button type="button" class="botao-secundario" (click)="avancar.emit()">
            {{ ultimo() ? 'Ir para as perguntas' : 'Continuar' }} <span aria-hidden="true">→</span>
          </button>
        </div>
      }
      @if (ultimo()) {
        <p class="mt-6 text-[13px] leading-snug text-abeto-2">
          Ao continuar, você concorda com o uso dos seus dados para gerar e enviar o seu diagnóstico.
        </p>
      }
    </div>
  `,
})
export class EtapaEscolha {
  readonly sobretitulo = input.required<string>();
  readonly titulo = input.required<string>();
  readonly ajuda = input<string | null>(null);
  readonly opcoes = input.required<OpcaoPerfil[]>();
  readonly selecionada = input<string | null>(null);
  readonly erro = input<string | null>(null);
  readonly enviando = input(false);
  /** Última etapa de identificação: avançar cria o diagnóstico. */
  readonly ultimo = input(false);

  readonly escolher = output<string>();
  readonly avancar = output();

  protected readonly idTitulo = `escolha-${++sequencia}`;
  protected readonly idAjuda = `${this.idTitulo}-ajuda`;

  protected letra(i: number): string {
    return LETRAS[i] ?? String(i + 1);
  }

  protected clicar(valor: string) {
    if (!this.enviando()) this.escolher.emit(valor);
  }

  /** Teclas A, B, C… (ou 1–9) escolhem, como nas perguntas. */
  protected atalho(evento: KeyboardEvent) {
    if (evento.ctrlKey || evento.metaKey || evento.altKey || this.enviando()) return;
    const alvo = evento.target;
    if (alvo instanceof Element && alvo.closest('input, textarea, [contenteditable]')) return;
    const opcoes = this.opcoes();
    const tecla = evento.key.toUpperCase();
    const indice = /^[1-9]$/.test(tecla) ? Number(tecla) - 1 : LETRAS.indexOf(tecla);
    if (indice >= 0 && indice < opcoes.length) {
      evento.preventDefault();
      this.escolher.emit(opcoes[indice].valor);
    }
  }
}
