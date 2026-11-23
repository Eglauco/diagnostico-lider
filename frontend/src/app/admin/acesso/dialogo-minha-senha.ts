import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { form, FormField, minLength, required, validate } from '@angular/forms/signals';

import { problemaDe } from '../../api/diagnostico-api';
import { CampoSenha } from './campo-senha';
import { SessaoPainel } from './sessao';

type Campo = 'senhaAtual' | 'novaSenha';

/** "Trocar minha senha": pede a atual e a nova. As outras sessões da pessoa caem. */
@Component({
  selector: 'app-dialogo-minha-senha',
  imports: [CampoSenha, FormField],
  styleUrl: '../admin.css',
  template: `
    <dialog #dialogo class="dialogo" aria-labelledby="minha-senha-titulo" (close)="fechou()">
      <div class="dialogo-corpo">
        @if (!concluido()) {
          <form novalidate (submit)="trocar($event)">
            <div class="flex items-start justify-between gap-4">
              <h2 id="minha-senha-titulo" class="display text-[28px] leading-tight font-bold">Trocar minha senha</h2>
              <button type="button" class="-mt-1 -mr-2 grid size-11 shrink-0 place-items-center rounded-full text-abeto-2 hover:bg-nevoa hover:text-abeto" aria-label="Fechar" (click)="fechar()">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
              </button>
            </div>
            <!-- Ajuda o gerenciador de senhas a saber de qual conta é a senha. -->
            <input type="email" class="sr-only" autocomplete="username" tabindex="-1" aria-hidden="true" [value]="sessao.usuario()?.email ?? ''" readonly />

            <div class="mt-6 space-y-5">
              <div>
                <label for="minha-senha-atual" class="campo-rotulo">Senha atual</label>
                <app-campo-senha [formField]="formulario.senhaAtual" idCampo="minha-senha-atual" descricao="minha-senha-atual-ajuda" autocomplete="current-password" enterkeyhint="next" [mostrarErro]="!!erro('senhaAtual')" (input)="editou('senhaAtual')" />
                <p id="minha-senha-atual-ajuda" class="mt-1.5 min-h-5 text-[14px] font-medium text-travada" aria-live="polite">{{ erro('senhaAtual') }}</p>
              </div>
              <div>
                <label for="minha-senha-nova" class="campo-rotulo">Nova senha</label>
                <app-campo-senha [formField]="formulario.novaSenha" idCampo="minha-senha-nova" descricao="minha-senha-nova-ajuda" autocomplete="new-password" enterkeyhint="done" [mostrarErro]="!!erro('novaSenha')" (input)="editou('novaSenha')" />
                <p id="minha-senha-nova-ajuda" class="mt-1.5 min-h-5 text-[14px]" aria-live="polite">
                  @if (erro('novaSenha'); as mensagem) {
                    <span class="font-medium text-travada">{{ mensagem }}</span>
                  } @else {
                    <span class="text-abeto-2">Pelo menos 8 caracteres. Uma frase curta é fácil de lembrar e difícil de adivinhar.</span>
                  }
                </p>
              </div>
            </div>

            <p class="min-h-5 text-[14px] font-medium text-travada" aria-live="assertive">{{ erroGeral() }}</p>
            <div class="mt-3 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button type="button" class="botao-secundario" (click)="fechar()">Cancelar</button>
              <button type="submit" class="botao-primario" [attr.aria-busy]="enviando()" [disabled]="enviando()">
                {{ enviando() ? 'Trocando…' : 'Trocar senha' }}
              </button>
            </div>
          </form>
        } @else {
          <div class="py-2 text-center" role="status">
            <span class="mx-auto grid size-14 place-items-center rounded-full bg-ascensao-suave text-[#17714A]" aria-hidden="true">
              <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </span>
            <h2 id="minha-senha-titulo" class="display mt-4 text-[26px] font-bold">Senha trocada</h2>
            <p class="mx-auto mt-2 max-w-xs text-[15px] text-abeto-2">Se havia uma sessão aberta em outro aparelho, ela foi encerrada.</p>
            <button type="button" class="botao-primario mt-6 w-full" (click)="fechar()">Concluir</button>
          </div>
        }
      </div>
    </dialog>
  `,
})
export class DialogoMinhaSenha {
  protected readonly sessao = inject(SessaoPainel);
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

  protected readonly modelo = signal({ senhaAtual: '', novaSenha: '' });
  protected readonly formulario = form(this.modelo, (c) => {
    required(c.senhaAtual, { message: 'Escreva a sua senha atual.' });
    required(c.novaSenha, { message: 'Escreva a nova senha.' });
    minLength(c.novaSenha, 8, { message: 'Use pelo menos 8 caracteres.' });
    validate(c.novaSenha, ({ value, valueOf }) =>
      value() && value() === valueOf(c.senhaAtual) ? { kind: 'igual', message: 'A nova senha precisa ser diferente da atual.' } : null,
    );
  });

  protected readonly tentou = signal(false);
  protected readonly enviando = signal(false);
  protected readonly concluido = signal(false);
  protected readonly erroGeral = signal<string | null>(null);
  private readonly errosServidor = signal<Partial<Record<Campo, string>>>({});

  protected readonly erro = (campo: Campo) =>
    this.errosServidor()[campo] ?? (this.tentou() ? (this.formulario[campo]().errors()[0]?.message ?? null) : null);

  abrir() {
    this.modelo.set({ senhaAtual: '', novaSenha: '' });
    this.tentou.set(false);
    this.concluido.set(false);
    this.erroGeral.set(null);
    this.errosServidor.set({});
    this.dialogo().nativeElement.showModal();
  }

  protected fechar() {
    this.dialogo().nativeElement.close();
  }

  protected fechou() {
    this.modelo.set({ senhaAtual: '', novaSenha: '' });
  }

  protected editou(campo: Campo) {
    if (this.errosServidor()[campo]) this.errosServidor.update(({ [campo]: _, ...resto }) => resto);
    this.erroGeral.set(null);
  }

  protected async trocar(evento: Event) {
    evento.preventDefault();
    if (this.enviando()) return;
    this.tentou.set(true);
    if (this.formulario().invalid()) {
      document.getElementById(this.erro('senhaAtual') ? 'minha-senha-atual' : 'minha-senha-nova')?.focus();
      return;
    }
    this.enviando.set(true);
    try {
      const { senhaAtual, novaSenha } = this.modelo();
      await this.sessao.trocarSenha(senhaAtual, novaSenha);
      this.concluido.set(true);
    } catch (e) {
      const problema = problemaDe(e);
      const campos = (problema.campos ?? {}) as Partial<Record<Campo, string>>;
      if (campos.senhaAtual || campos.novaSenha) {
        this.errosServidor.set(campos);
        document.getElementById(campos.senhaAtual ? 'minha-senha-atual' : 'minha-senha-nova')?.focus();
      } else {
        this.erroGeral.set(problema.detail ?? 'Não foi possível trocar a senha agora.');
      }
    } finally {
      this.enviando.set(false);
    }
  }
}
