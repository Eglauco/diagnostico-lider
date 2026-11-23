import { Component, input, linkedSignal, model, signal } from '@angular/core';
import { FormValueControl } from '@angular/forms/signals';

/**
 * Campo de senha do painel: mostrar/esconder e aviso de Caps Lock. Compatível com
 * Signal Forms via [formField].
 */
@Component({
  selector: 'app-campo-senha',
  styleUrl: '../admin.css',
  host: { class: 'block' },
  template: `
    <div class="campo flex items-center gap-1 pr-1.5">
      <input
        #campo
        class="h-14 min-w-0 flex-1 bg-transparent"
        [type]="visivel() ? 'text' : 'password'"
        [id]="idCampo()"
        [value]="value()"
        [attr.autocomplete]="autocomplete()"
        [attr.enterkeyhint]="enterkeyhint()"
        [attr.placeholder]="placeholder()"
        [attr.aria-invalid]="mostrarErro()"
        [attr.aria-describedby]="descricao()"
        autocapitalize="none"
        autocorrect="off"
        spellcheck="false"
        (input)="value.set(campo.value)"
        (keydown)="conferirCaps($event)"
        (keyup)="conferirCaps($event)"
        (blur)="caps.set(false)"
      />
      <button
        type="button"
        class="grid size-11 shrink-0 place-items-center rounded-xl text-abeto-2 transition-colors hover:bg-nevoa hover:text-abeto"
        [attr.aria-pressed]="visivel()"
        [attr.aria-controls]="idCampo()"
        [attr.aria-label]="visivel() ? 'Esconder senha' : 'Mostrar senha'"
        (click)="visivel.update((v) => !v)"
      >
        @if (visivel()) {
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M3 3l18 18M10.6 5.1A9.7 9.7 0 0 1 12 5c5 0 8.5 4.5 9.5 7a13 13 0 0 1-2.6 3.8M6.2 6.6C4.3 8 3 10 2.5 12c1 2.5 4.5 7 9.5 7 1.7 0 3.2-.5 4.5-1.2M9.9 9.9a3 3 0 0 0 4.2 4.2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        } @else {
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M2.5 12C3.5 9.5 7 5 12 5s8.5 4.5 9.5 7c-1 2.5-4.5 7-9.5 7s-8.5-4.5-9.5-7z" stroke-linejoin="round" /><circle cx="12" cy="12" r="3" />
          </svg>
        }
      </button>
    </div>
    @if (caps()) {
      <p class="aparecer mt-1.5 text-[13px] font-medium text-[#855300]">Caps Lock está ativado.</p>
    }
  `,
})
export class CampoSenha implements FormValueControl<string> {
  readonly value = model('');
  /** Preenchido pelo [formField]; o destaque visual usa mostrarErro (só depois de tentar enviar). */
  readonly invalid = input(false);
  readonly mostrarErro = input(false);
  readonly idCampo = input<string>();
  readonly descricao = input<string>();
  readonly placeholder = input<string>('');
  readonly autocomplete = input<'current-password' | 'new-password'>('current-password');
  readonly enterkeyhint = input('done');
  /** Senha gerada para outra pessoa: começa visível para poder conferir e copiar. */
  readonly comecarVisivel = input(false);

  protected readonly visivel = linkedSignal(() => this.comecarVisivel());
  protected readonly caps = signal(false);

  protected conferirCaps(evento: KeyboardEvent) {
    if (typeof evento.getModifierState === 'function') this.caps.set(evento.getModifierState('CapsLock'));
  }
}
