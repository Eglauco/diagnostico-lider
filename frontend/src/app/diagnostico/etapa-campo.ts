import { afterNextRender, Component, computed, ElementRef, inject, input, output } from '@angular/core';
import { FieldTree, FormField } from '@angular/forms/signals';

import { EntradaTelefone } from '../compartilhado/entrada-telefone';
import { DadosRespondente } from '../api/modelos';

export type CampoId = keyof DadosRespondente;

interface ConfigCampo {
  tipo: string;
  inputmode: string;
  autocomplete: string;
  autocapitalize: string;
  placeholder: string;
}

const CONFIG: Record<Exclude<CampoId, 'whatsapp'>, ConfigCampo> = {
  nome: { tipo: 'text', inputmode: 'text', autocomplete: 'name', autocapitalize: 'words', placeholder: 'Nome e sobrenome' },
  email: { tipo: 'email', inputmode: 'email', autocomplete: 'email', autocapitalize: 'none', placeholder: 'voce@empresa.com' },
};

let sequencia = 0;

/**
 * Uma pergunta de identificação por tela: rótulo grande e campo grande com o botão de enviar
 * grudado nele, como no WhatsApp. Assim o botão nunca some atrás do teclado nem da barra do
 * Safari: o celular sempre mostra o campo em que a pessoa digita, e o botão vem junto.
 */
@Component({
  selector: 'app-etapa-campo',
  imports: [FormField, EntradaTelefone],
  host: { class: 'flex flex-1 flex-col' },
  template: `
    <form class="flex flex-1 flex-col" novalidate (submit)="enviar($event)">
      <div class="flex-1 px-5 pt-8 pb-10 sm:px-8 sm:pt-14">
        <p class="rotulo surgir text-abeto-2" style="--i: 0">{{ sobretitulo() }}</p>
        <label [for]="idCampo" class="display surgir mt-3 block text-[32px] leading-[1.06] font-semibold sm:text-[44px]" style="--i: 1">
          {{ rotulo() }}
        </label>

        <div class="surgir mt-8 flex items-center gap-3" style="--i: 2">
          <div class="min-w-0 flex-1">
            @if (campo() === 'whatsapp') {
              <app-entrada-telefone
                [formField]="controle()"
                [idCampo]="idCampo"
                [descricao]="idAjuda"
                [mostrarErro]="!!erro()"
                (input)="editou.emit()"
              />
            } @else {
              <input
                class="entrada-grande"
                [formField]="controle()"
                [id]="idCampo"
                [attr.type]="config()!.tipo"
                [attr.inputmode]="config()!.inputmode"
                [attr.autocomplete]="config()!.autocomplete"
                [attr.autocapitalize]="config()!.autocapitalize"
                [attr.placeholder]="config()!.placeholder"
                [attr.enterkeyhint]="ultimo() ? 'go' : 'next'"
                [attr.aria-invalid]="!!erro()"
                [attr.aria-describedby]="idAjuda"
                spellcheck="false"
                (input)="editou.emit()"
              />
            }
          </div>
          <button
            type="submit"
            class="botao-enviar"
            [class.botao-enviar-pronto]="preenchido()"
            [attr.aria-label]="ultimo() ? 'Ir para as perguntas' : 'Continuar'"
            [attr.aria-busy]="enviando()"
            [disabled]="enviando()"
          >
            @if (enviando()) {
              <span class="size-5 animate-spin rounded-full border-2 border-current/30 border-t-current" aria-hidden="true"></span>
            } @else {
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            }
          </button>
        </div>

        <div [id]="idAjuda" class="mt-3 min-h-6 text-[15px]" aria-live="polite">
          @if (erro(); as mensagem) {
            <p class="aparecer flex items-start gap-2 font-medium text-travada">
              <svg class="mt-0.5 shrink-0" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
                <circle cx="12" cy="12" r="9" /><path d="M12 7.5v5.5M12 16.5v.5" stroke-linecap="round" />
              </svg>
              {{ mensagem }}
            </p>
          } @else if (enviando()) {
            <p class="text-abeto-2">Salvando…</p>
          } @else if (ajuda()) {
            <p class="text-abeto-2">{{ ajuda() }}</p>
          }
        </div>

        @if (ultimo()) {
          <p class="mt-6 text-[13px] leading-snug text-abeto-2">
            Ao continuar, você concorda com o uso dos seus dados para gerar e enviar o seu diagnóstico.
          </p>
        } @else {
          <p class="mt-6 hidden text-[13px] text-abeto-2 sm:block">Você também pode pressionar <kbd class="font-sans font-semibold">Enter ↵</kbd></p>
        }
      </div>
    </form>
  `,
})
export class EtapaCampo {
  readonly campo = input.required<CampoId>();
  readonly controle = input.required<FieldTree<string>>();
  readonly sobretitulo = input.required<string>();
  readonly rotulo = input.required<string>();
  readonly ajuda = input<string | null>(null);
  readonly erro = input<string | null>(null);
  readonly ultimo = input(false);
  readonly enviando = input(false);

  readonly continuar = output();
  readonly editou = output();

  protected readonly idCampo = `campo-${++sequencia}`;
  protected readonly idAjuda = `${this.idCampo}-ajuda`;
  /** Com algo escrito, a seta ganha cor: mostra que dá para seguir. Vazia, continua tocável e explica o que falta. */
  protected readonly preenchido = computed(() => this.controle()().value().trim().length > 0);
  protected readonly config = computed(() => {
    const campo = this.campo();
    return campo === 'whatsapp' ? null : CONFIG[campo];
  });

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef);
    // Foco no campo quando a etapa entra (no celular, o teclado já sobe).
    afterNextRender(() => {
      setTimeout(() => host.nativeElement.querySelector('input')?.focus({ preventScroll: true }), 160);
    });
  }

  protected enviar(evento: Event) {
    evento.preventDefault();
    if (!this.enviando()) this.continuar.emit();
  }
}
