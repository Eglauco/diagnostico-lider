import { Component, computed, input, model, output, signal } from '@angular/core';

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** O primeiro problema da lista (índice e mensagem), ou null. Linhas vazias são ignoradas. */
export function problemaGestores(emails: string[]): { indice: number; mensagem: string } | null {
  const vistos = new Set<string>();
  for (const [indice, bruto] of emails.entries()) {
    const email = bruto.trim().toLowerCase();
    if (!email) continue;
    if (email.length > 254 || !EMAIL.test(email)) return { indice, mensagem: 'Confira este e-mail: falta o @ ou o domínio.' };
    if (vistos.has(email)) return { indice, mensagem: 'Esse e-mail já está na lista.' };
    vistos.add(email);
  }
  return null;
}

/** Como vai para o servidor: sem espaços, em minúsculas e sem as linhas vazias. */
export function gestoresParaEnvio(emails: string[]): string[] {
  return emails.map((e) => e.trim().toLowerCase()).filter(Boolean);
}

/** Erros do servidor por linha: { "gestores.2": "…" } → { 2: "…" }. */
export function errosDeGestores(campos: Record<string, string> | undefined): Record<number, string> {
  const erros: Record<number, string> = {};
  for (const [chave, mensagem] of Object.entries(campos ?? {})) {
    const m = /^gestores\.(\d+)$/.exec(chave);
    if (m) erros[Number(m[1])] = mensagem;
  }
  return erros;
}

/**
 * "Gestores da empresa": quem recebe o relatório diário de participação (PDF e Excel) e o
 * resultado consolidado no fim. Pode ficar vazio, ter um ou vários, e muda a qualquer momento.
 */
@Component({
  selector: 'app-secao-gestores',
  host: { class: 'block' },
  styleUrl: '../admin.css',
  template: `
    <fieldset id="gestores-secao" tabindex="-1" class="scroll-mt-24 outline-none">
      <legend class="text-[15px] font-semibold">Gestores da empresa</legend>
      <p class="mt-1 text-[14px] text-abeto-2">
        Recebem todo dia útil, às 8h, o relatório de participação em PDF e Excel para cobrar quem falta. Quando todos
        responderem ou o prazo vencer, recebem o resultado consolidado.
      </p>

      @if (emails().length) {
        <ul class="mt-4 space-y-3">
          @for (email of emails(); track $index; let i = $index) {
            <li>
              <div class="flex items-center gap-2">
                <label [for]="'gestor-' + i" class="sr-only">E-mail do gestor {{ i + 1 }}</label>
                <input
                  [id]="'gestor-' + i"
                  class="campo min-w-0 flex-1"
                  type="email"
                  inputmode="email"
                  autocomplete="off"
                  autocapitalize="none"
                  spellcheck="false"
                  enterkeyhint="next"
                  placeholder="nome@empresa.com.br"
                  [value]="email"
                  [attr.aria-invalid]="!!erroDa(i)"
                  [attr.aria-describedby]="erroDa(i) ? 'gestor-erro-' + i : null"
                  (input)="mudar(i, $any($event.target).value)"
                  (blur)="tocar(i)"
                  (keydown.enter)="$event.preventDefault(); adicionar()"
                />
                <button
                  type="button"
                  class="grid size-12 shrink-0 place-items-center rounded-full text-abeto-2 transition-colors hover:bg-nevoa hover:text-travada"
                  [attr.aria-label]="'Remover ' + (email || 'este gestor')"
                  (click)="remover(i)"
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
                </button>
              </div>
              @if (erroDa(i); as mensagem) {
                <p [id]="'gestor-erro-' + i" class="aparecer mt-1.5 text-[14px] font-medium text-travada" aria-live="polite">{{ mensagem }}</p>
              }
            </li>
          }
        </ul>
      } @else {
        <p class="mt-4 rounded-2xl bg-nevoa-2 px-4 py-3 text-[14px] text-abeto-2 ring-1 ring-linha/70">
          Sem gestor, os relatórios automáticos não saem. Você pode cadastrar agora ou depois.
        </p>
      }

      <button type="button" class="botao-secundario mt-3" (click)="adicionar()">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
        {{ emails().length ? 'Adicionar outro gestor' : 'Adicionar gestor' }}
      </button>
    </fieldset>
  `,
})
export class SecaoGestores {
  readonly emails = model<string[]>([]);
  /** Erros do servidor por linha. */
  readonly erros = input<Record<number, string>>({});
  /** Mostra os erros de todas as linhas (tentou salvar). */
  readonly tentou = input(false);
  readonly editou = output();

  private readonly tocados = signal<Set<number>>(new Set());
  private readonly problema = computed(() => problemaGestores(this.emails()));

  protected erroDa(i: number): string | null {
    const servidor = this.erros()[i];
    if (servidor) return servidor;
    const p = this.problema();
    return p && p.indice === i && (this.tentou() || this.tocados().has(i)) ? p.mensagem : null;
  }

  protected mudar(i: number, valor: string) {
    this.emails.update((lista) => lista.map((e, j) => (j === i ? valor : e)));
    this.editou.emit();
  }

  protected tocar(i: number) {
    this.tocados.update((s) => new Set(s).add(i));
  }

  /** Nova linha vazia e o foco nela (o Enter num campo também chama aqui). */
  protected adicionar() {
    const lista = this.emails();
    const vazia = lista.findIndex((e) => !e.trim());
    if (vazia >= 0) return this.focar(vazia);
    this.emails.set([...lista, '']);
    this.editou.emit();
    this.focar(lista.length);
  }

  protected remover(i: number) {
    this.emails.update((lista) => lista.filter((_, j) => j !== i));
    this.tocados.set(new Set());
    this.editou.emit();
  }

  private focar(i: number) {
    setTimeout(() => (document.getElementById('gestor-' + i) as HTMLInputElement | null)?.focus(), 0);
  }
}
