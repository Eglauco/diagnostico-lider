import { Component, computed, input, model, OnInit, output, signal } from '@angular/core';
import { FieldTree, FormField } from '@angular/forms/signals';

import { CARGO_OUTRO, OpcaoPerfil } from '../api/modelos';
import { EntradaTelefone } from '../compartilhado/entrada-telefone';
import { CreditoDiagnostico, MarcaEmpresaCabecalho } from '../compartilhado/marca-empresa';
import { MarcaEmpresa } from '../compartilhado/tema-marca';

export interface ErrosConfirmacao {
  nome: string | null;
  email: string | null;
  whatsapp: string | null;
  cargo: string | null;
}

/**
 * Primeira tela do link pessoal: os dados que o RH mandou, para o líder confirmar com um toque
 * ("Está tudo certo") ou corrigir ali mesmo. Substitui as telas de nome, e-mail, WhatsApp e
 * cargo do link aberto.
 */
@Component({
  selector: 'app-confirmar-dados',
  imports: [CreditoDiagnostico, EntradaTelefone, FormField, MarcaEmpresaCabecalho],
  host: { class: 'flex min-h-dvh flex-col' },
  template: `
    <header class="flex items-center justify-between gap-3 px-5 pt-5 sm:px-8 sm:pt-8">
      <app-marca-empresa [empresa]="empresa()" />
      <app-credito-diagnostico class="hidden shrink-0 sm:inline-flex" />
    </header>

    <form class="flex flex-1 flex-col" novalidate (submit)="enviar($event)">
      <div class="flex-1 px-5 pt-9 pb-8 sm:px-8 sm:pt-14">
        <p class="rotulo surgir text-brasa-escura" style="--i: 0">Convite para você</p>
        <h1 class="display surgir mt-3 text-[38px] leading-[1.02] font-bold sm:text-[52px]" style="--i: 1">
          {{ primeiroNome() ? 'Olá, ' + primeiroNome() + '!' : 'Olá!' }} Confira seus dados
        </h1>
        <p class="surgir mt-3 text-[17px] leading-relaxed text-abeto-2" style="--i: 2">
          {{ empresa()?.nome ?? 'A empresa' }} enviou estes dados para o seu diagnóstico de carreira. Se algo estiver errado, corrija aqui.
        </p>

        <div class="surgir mt-6 rounded-3xl bg-white p-1.5 ring-1 ring-linha" style="--i: 3">
          @if (!editando()) {
            <dl class="divide-y divide-linha">
              <div class="dado"><dt>Nome</dt><dd>{{ controles().nome().value() || '—' }}</dd></div>
              <div class="dado"><dt>E-mail</dt><dd class="break-all">{{ controles().email().value() || '—' }}</dd></div>
              @if (whatsappNaLinha()) {
                <!-- Não veio da planilha: o campo já aparece aberto, sem precisar de "Corrigir". -->
                <div class="px-3.5 py-3">
                  <label for="confirmar-whatsapp-linha" class="rotulo-campo">WhatsApp <span class="font-normal text-abeto-2">(só falta este)</span></label>
                  <app-entrada-telefone class="campo-dado-telefone" [formField]="controles().whatsapp" idCampo="confirmar-whatsapp-linha" descricao="confirmar-whatsapp-linha-erro" [mostrarErro]="!!erros().whatsapp" (input)="editou.emit('whatsapp')" />
                  <p id="confirmar-whatsapp-linha-erro" class="erro-campo" aria-live="polite">{{ erros().whatsapp }}</p>
                </div>
              } @else {
                <div class="dado"><dt>WhatsApp</dt><dd>{{ controles().whatsapp().value() }}</dd></div>
              }
              @if (opcoesCargo().length) {
                <div class="dado"><dt>Cargo</dt><dd [class.text-travada]="!cargo()">{{ nomeCargo() || 'Falta informar' }}</dd></div>
              }
            </dl>
          } @else {
            <div class="space-y-4 p-3.5">
              <div>
                <label for="confirmar-nome" class="rotulo-campo">Nome completo</label>
                <input id="confirmar-nome" class="campo-dado" [formField]="controles().nome" autocomplete="name" autocapitalize="words" enterkeyhint="next" [attr.aria-invalid]="!!erros().nome" aria-describedby="confirmar-nome-erro" (input)="editou.emit('nome')" />
                <p id="confirmar-nome-erro" class="erro-campo" aria-live="polite">{{ erros().nome }}</p>
              </div>
              <div>
                <label for="confirmar-email" class="rotulo-campo">E-mail</label>
                <input id="confirmar-email" class="campo-dado" type="email" inputmode="email" autocomplete="email" autocapitalize="none" spellcheck="false" enterkeyhint="next" [formField]="controles().email" [attr.aria-invalid]="!!erros().email" aria-describedby="confirmar-email-erro" (input)="editou.emit('email')" />
                <p id="confirmar-email-erro" class="erro-campo" aria-live="polite">{{ erros().email }}</p>
              </div>
              <div>
                <label for="confirmar-whatsapp" class="rotulo-campo">WhatsApp</label>
                <app-entrada-telefone class="campo-dado-telefone" [formField]="controles().whatsapp" idCampo="confirmar-whatsapp" descricao="confirmar-whatsapp-erro" [mostrarErro]="!!erros().whatsapp" (input)="editou.emit('whatsapp')" />
                <p id="confirmar-whatsapp-erro" class="erro-campo" aria-live="polite">{{ erros().whatsapp }}</p>
              </div>
              @if (opcoesCargo().length) {
                <div>
                  <label for="confirmar-cargo" class="rotulo-campo">Cargo</label>
                  <select id="confirmar-cargo" class="campo-dado" [attr.aria-invalid]="!!erros().cargo" aria-describedby="confirmar-cargo-erro" (change)="escolherCargo.emit($any($event.target).value || null)">
                    <option value="" [selected]="!cargo()">Escolha o seu cargo</option>
                    @for (o of opcoesCargo(); track o.valor) {
                      <option [value]="o.valor" [selected]="cargo() === o.valor">{{ o.texto }}</option>
                    }
                  </select>
                  <p id="confirmar-cargo-erro" class="erro-campo" aria-live="polite">{{ erros().cargo }}</p>
                  @if (cargo() === OUTRO) {
                    <label for="confirmar-cargo-outro" class="rotulo-campo mt-2">Qual é o seu cargo? <span class="font-normal text-abeto-2">(opcional)</span></label>
                    <input id="confirmar-cargo-outro" class="campo-dado" maxlength="80" autocapitalize="words" placeholder="Ex.: Head de vendas" [value]="outroNome()" (input)="escreverOutro.emit($any($event.target).value)" />
                  }
                </div>
              }
            </div>
          }
        </div>

        @if (!editando()) {
          <button type="button" class="surgir mt-3 inline-flex min-h-12 items-center gap-2 rounded-full px-1 text-[16px] font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto" style="--i: 4" (click)="editando.set(true)">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" aria-hidden="true"><path d="M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4" stroke-linecap="round" stroke-linejoin="round" /></svg>
            Corrigir algum dado
          </button>
        }

        @if (prazo(); as p) {
          <p class="surgir mt-5 flex items-center gap-2 text-[15px] font-medium text-abeto" style="--i: 5">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" stroke-linecap="round" /></svg>
            Responda até {{ p }}.
          </p>
        }
      </div>

      <div class="rodape-fixo px-5 sm:px-8">
        <button type="submit" class="botao-primario w-full" [attr.aria-busy]="enviando()" [disabled]="enviando()">
          @if (enviando()) {
            <span class="size-5 animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden="true"></span>
            Salvando…
          } @else {
            {{ editando() ? 'Salvar e continuar' : 'Está tudo certo' }} <span class="seta" aria-hidden="true">→</span>
          }
        </button>
        <p class="mt-3 text-center text-[13px] leading-snug text-abeto-2">
          @if (ultimo()) {
            Ao continuar, você concorda com o uso dos seus dados para gerar e enviar o seu diagnóstico.
          } @else {
            Suas respostas são suas: a empresa vê só os números do grupo.
          }
        </p>
      </div>
    </form>
  `,
  styles: `
    .dado {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      padding: 14px 14px;
      font-size: 16px;
    }
    .dado dt {
      flex: none;
      color: var(--color-abeto-2);
    }
    .dado dd {
      min-width: 0;
      text-align: right;
      font-weight: 600;
    }
    .rotulo-campo {
      display: block;
      margin-bottom: 6px;
      font-size: 15px;
      font-weight: 600;
    }
    .campo-dado,
    .campo-dado-telefone ::ng-deep input {
      width: 100%;
      min-height: 52px;
      padding: 0 16px;
      border: 0;
      border-radius: 14px;
      background: var(--color-nevoa-2);
      box-shadow: inset 0 0 0 1px var(--color-linha);
      color: var(--color-abeto);
      font-size: 17px;
      font-weight: 500;
      outline: none;
    }
    .campo-dado:focus,
    .campo-dado-telefone ::ng-deep input:focus {
      box-shadow: inset 0 0 0 2px var(--color-abeto);
      background: #fff;
    }
    .campo-dado[aria-invalid='true'],
    .campo-dado-telefone ::ng-deep input[aria-invalid='true'] {
      box-shadow: inset 0 0 0 2px var(--color-travada);
    }
    .erro-campo {
      min-height: 0;
      margin-top: 4px;
      font-size: 14px;
      font-weight: 500;
      color: var(--color-travada);
    }
    .erro-campo:empty {
      display: none;
    }
  `,
})
export class ConfirmarDados implements OnInit {
  readonly empresa = input<MarcaEmpresa | null>(null);
  readonly primeiroNome = input('');
  readonly controles = input.required<{ nome: FieldTree<string>; email: FieldTree<string>; whatsapp: FieldTree<string> }>();
  readonly opcoesCargo = input<OpcaoPerfil[]>([]);
  readonly cargo = input<string | null>(null);
  readonly outroNome = input('');
  readonly erros = input<ErrosConfirmacao>({ nome: null, email: null, whatsapp: null, cargo: null });
  readonly prazo = input<string | null>(null);
  readonly ultimo = input(false);
  readonly enviando = input(false);
  /** A pessoa abriu os campos para corrigir (ou a página abriu, porque falta algo). */
  readonly editando = model(false);

  readonly confirmar = output();
  readonly editou = output<'nome' | 'email' | 'whatsapp'>();
  readonly escolherCargo = output<string | null>();
  readonly escreverOutro = output<string>();

  protected readonly OUTRO = CARGO_OUTRO;
  /** O WhatsApp não veio da planilha: decidido ao abrir, para o campo não sumir enquanto a pessoa digita. */
  protected readonly whatsappNaLinha = signal(false);

  ngOnInit() {
    this.whatsappNaLinha.set(!this.controles().whatsapp().value());
  }
  protected readonly nomeCargo = computed(() => {
    const c = this.cargo();
    if (c === CARGO_OUTRO) return this.outroNome().trim() || 'Outro cargo';
    return this.opcoesCargo().find((o) => o.valor === c)?.texto ?? '';
  });

  protected enviar(evento: Event) {
    evento.preventDefault();
    if (!this.enviando()) this.confirmar.emit();
  }
}
