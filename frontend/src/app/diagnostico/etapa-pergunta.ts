import { Component, computed, input, output } from '@angular/core';

import { Opcao, Pergunta } from '../api/modelos';

let sequencia = 0;
const LETRAS = 'ABCDEFGHIJ';

/**
 * Pergunta de múltipla escolha: cartões grandes, um toque escolhe e avança sozinho.
 * No computador, as teclas A–D (ou 1–4) também escolhem.
 */
@Component({
  selector: 'app-etapa-pergunta',
  host: { class: 'block', '(document:keydown)': 'atalho($event)' },
  template: `
    <div class="px-5 pt-8 pb-12 sm:px-8 sm:pt-14">
      <p class="rotulo surgir text-abeto-2" style="--i: 0">
        @if (dimensao()) {
          <span class="text-brasa-escura">{{ dimensao() }}</span> ·
        }
        Pergunta {{ numero() }} de {{ total() }}
      </p>
      <h1 [id]="idTitulo" class="display surgir mt-3 text-[28px] leading-[1.12] font-semibold sm:text-[38px]" style="--i: 1">
        {{ pergunta().enunciado }}
      </h1>
      @if (pergunta().ajuda) {
        <p class="surgir mt-3 text-[15px] text-abeto-2" style="--i: 2">{{ pergunta().ajuda }}</p>
      }

      <div class="mt-8 grid gap-3" role="group" [attr.aria-labelledby]="idTitulo">
        @for (opcao of pergunta().opcoes; track opcao.id; let i = $index) {
          <button
            type="button"
            class="opcao surgir"
            [style.--i]="i + 3"
            [class.opcao-marcada]="marcada(opcao)"
            [class.opcao-apagada]="enviando() && algumaMarcada() && !marcada(opcao)"
            [attr.aria-pressed]="marcada(opcao)"
            [attr.aria-disabled]="enviando()"
            (click)="clicar(opcao)"
          >
            <span class="tecla" aria-hidden="true">
              @if (marcada(opcao)) {
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

      @if (algumaMarcada() && !enviando()) {
        <div class="aparecer mt-6 flex justify-end">
          <button type="button" class="botao-secundario" (click)="avancar.emit()">
            {{ ultima() ? 'Ver meu diagnóstico' : 'Próxima pergunta' }} <span aria-hidden="true">→</span>
          </button>
        </div>
      }
      <p class="mt-8 hidden text-[13px] text-abeto-2 sm:block">Dica: use as teclas {{ letrasDica() }} para responder.</p>
    </div>
  `,
  styles: `
    .opcao {
      display: flex;
      align-items: center;
      gap: 14px;
      width: 100%;
      min-height: 66px;
      padding: 14px 18px 14px 14px;
      border-radius: 20px;
      border: 1.5px solid var(--color-linha);
      background: #fff;
      color: var(--color-abeto);
      text-align: left;
      font-size: 17px;
      font-weight: 500;
      line-height: 1.35;
      box-shadow: 0 1px 0 rgb(15 43 38 / 0.04);
      transition:
        transform 160ms var(--ease-mola),
        background-color 220ms,
        border-color 220ms,
        color 220ms,
        box-shadow 220ms,
        opacity 260ms;
    }
    @media (hover: hover) {
      .opcao:not(.opcao-marcada):hover {
        border-color: var(--color-abeto-3);
        box-shadow: 0 10px 24px -18px rgb(15 43 38 / 0.6);
        transform: translateY(-1px);
      }
    }
    .opcao:active {
      transform: scale(0.985);
    }
    .tecla {
      display: grid;
      place-items: center;
      flex: none;
      width: 32px;
      height: 32px;
      border-radius: 10px;
      border: 1.5px solid var(--color-linha);
      color: var(--color-abeto-2);
      font-size: 13px;
      font-weight: 700;
      transition: all 220ms var(--ease-mola);
    }
    .opcao-marcada {
      background: var(--color-abeto);
      border-color: var(--color-abeto);
      color: #fff;
      box-shadow: 0 16px 30px -20px rgb(15 43 38 / 0.9);
    }
    .opcao-marcada .tecla {
      background: var(--color-brasa);
      border-color: var(--color-brasa);
      color: var(--color-abeto);
      transform: scale(1.06);
    }
    .opcao-apagada {
      opacity: 0.4;
    }
  `,
})
export class EtapaPergunta {
  readonly pergunta = input.required<Pergunta>();
  readonly numero = input.required<number>();
  readonly total = input.required<number>();
  readonly dimensao = input<string | null>(null);
  readonly selecionadas = input<number[]>([]);
  readonly enviando = input(false);
  readonly ultima = input(false);

  readonly escolher = output<Opcao>();
  readonly avancar = output();

  protected readonly idTitulo = `pergunta-${++sequencia}`;
  protected readonly algumaMarcada = computed(() => this.selecionadas().length > 0);
  protected readonly letrasDica = computed(() => {
    const letras = LETRAS.slice(0, this.pergunta().opcoes.length).split('');
    return `${letras.slice(0, -1).join(', ')} e ${letras.at(-1)}`;
  });

  protected marcada(opcao: Opcao): boolean {
    return this.selecionadas().includes(opcao.id);
  }

  protected letra(i: number): string {
    return LETRAS[i] ?? String(i + 1);
  }

  protected clicar(opcao: Opcao) {
    if (!this.enviando()) this.escolher.emit(opcao);
  }

  protected atalho(evento: KeyboardEvent) {
    if (evento.ctrlKey || evento.metaKey || evento.altKey || this.enviando()) return;
    const alvo = evento.target as HTMLElement | null;
    if (alvo?.closest('input, textarea, [contenteditable]')) return;
    const opcoes = this.pergunta().opcoes;
    const tecla = evento.key.toUpperCase();
    const indice = /^[1-9]$/.test(tecla) ? Number(tecla) - 1 : LETRAS.indexOf(tecla);
    if (indice >= 0 && indice < opcoes.length) {
      evento.preventDefault();
      this.escolher.emit(opcoes[indice]);
    }
  }
}
