import { Component, computed, ElementRef, inject, input, model, output, signal } from '@angular/core';

import { CargoEditavel, cargosSugeridos, MAXIMO_CARGOS, novoCargo } from './cargos';

/**
 * "Cargos dos líderes": a lista que o líder vê no link da empresa, na ordem da hierarquia.
 * Aparece como uma escada: o nível mais alto em cima e o primeiro nível de liderança embaixo.
 * Os dados seguem do primeiro nível para o mais alto (índice 0 = primeiro nível); só a tela
 * inverte. Setas reordenam (toque e teclado); remover um cargo que já tem respostas avisa que
 * ele continua no relatório e dá para desfazer.
 */
@Component({
  selector: 'app-secao-cargos',
  host: { class: 'block' },
  template: `
    <fieldset id="cargos-secao" tabindex="-1" class="scroll-mt-24 outline-none">
      <legend class="text-[15px] font-semibold">Cargos dos líderes</legend>
      <p class="mt-1 text-[14px] text-abeto-2">
        No link da empresa, o líder escolhe o cargo dele e o relatório mostra em que nível a carreira mais trava.
        Monte a escada: o nível mais alto em cima e o primeiro nível de liderança embaixo.
      </p>

      @if (cargos().length) {
        <p class="mt-4 flex items-center gap-2 text-[12px] font-semibold tracking-[0.1em] text-abeto-2 uppercase" aria-hidden="true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M12 19V5m0 0l-5 5m5-5l5 5" stroke-linecap="round" stroke-linejoin="round" /></svg>
          Nível mais alto
        </p>
        <ol class="mt-2 space-y-2" reversed>
          @for (linha of visiveis(); track linha.cargo.chave; let primeiro = $first, ultimo = $last) {
            @let cargo = linha.cargo;
            @let i = linha.indice;
            <li class="aparecer flex items-center gap-1 rounded-2xl bg-white py-1 pr-1 pl-1 ring-1 ring-linha">
              <input
                class="h-12 min-w-0 flex-1 rounded-xl bg-transparent px-3 text-[16px] text-abeto outline-none placeholder:text-abeto-3 focus:ring-2 focus:ring-abeto"
                [id]="cargo.chave"
                type="text"
                maxlength="80"
                autocapitalize="words"
                autocomplete="off"
                spellcheck="false"
                placeholder="Nome do cargo"
                [attr.aria-label]="'Cargo do nível ' + (i + 1) + ' de ' + cargos().length + (i === 0 ? ' (primeiro nível)' : i === cargos().length - 1 ? ' (nível mais alto)' : '')"
                [attr.aria-describedby]="cargo.diagnosticos ? cargo.chave + '-uso' : null"
                [value]="cargo.nome"
                (input)="renomear(i, $any($event.target).value)"
                (keydown.enter)="$event.preventDefault(); adicionarAbaixo(i)"
              />
              <button type="button" class="botao-icone" [attr.aria-disabled]="primeiro" [attr.aria-label]="'Subir ' + (cargo.nome || 'cargo') + ' um nível'" (click)="mover(i, 1)">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" aria-hidden="true"><path d="M6 15l6-6 6 6" stroke-linecap="round" stroke-linejoin="round" /></svg>
              </button>
              <button type="button" class="botao-icone" [attr.aria-disabled]="ultimo" [attr.aria-label]="'Descer ' + (cargo.nome || 'cargo') + ' um nível'" (click)="mover(i, -1)">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" aria-hidden="true"><path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
              </button>
              <button type="button" class="botao-icone hover:text-travada" [attr.aria-label]="'Remover ' + (cargo.nome || 'cargo')" (click)="remover(i)">
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
              </button>
              @if (cargo.diagnosticos) {
                <span [id]="cargo.chave + '-uso'" class="sr-only">{{ rotuloUso(cargo.diagnosticos) }}</span>
              }
            </li>
          }
        </ol>
        <p class="mt-2 flex items-center gap-2 text-[12px] font-semibold tracking-[0.1em] text-abeto-2 uppercase" aria-hidden="true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M5 19h14" stroke-linecap="round" /></svg>
          Primeiro nível de liderança
        </p>

        @if (cargos().length < maximo) {
          <button type="button" class="botao-tracejado mt-3" (click)="adicionar()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
            Adicionar cargo
          </button>
        } @else {
          <p class="mt-3 text-[14px] text-abeto-2">Você chegou ao limite de {{ maximo }} cargos.</p>
        }
      } @else {
        <div class="mt-4 rounded-2xl border-[1.5px] border-dashed border-abeto-3 px-4 py-5">
          <p class="text-[15px] font-semibold">Sem cargos, o líder não informa o cargo.</p>
          <p class="mt-1 text-[14px] text-abeto-2">E o relatório da empresa fica sem a análise por cargo e por tempo no cargo.</p>
          <div class="mt-4 flex flex-col gap-2 sm:flex-row">
            <button type="button" class="botao-secundario" (click)="usarSugeridos()">Usar a lista sugerida</button>
            <button type="button" class="botao-tracejado sm:w-auto" (click)="adicionar()">Adicionar cargo</button>
          </div>
        </div>
      }

      @if (removidos().length) {
        <ul class="mt-3 space-y-2" aria-live="polite">
          @for (r of removidos(); track r.cargo.chave) {
            <li class="aparecer flex items-start gap-3 rounded-2xl bg-nevoa-2 px-3.5 py-2.5 text-[14px] ring-1 ring-linha/70">
              <p class="flex-1 text-abeto-2">
                <strong class="font-semibold text-abeto">{{ r.cargo.nome }}</strong> sai da lista do líder.
                {{ r.cargo.diagnosticos === 1 ? 'O diagnóstico que já tem continua' : 'Os ' + r.cargo.diagnosticos + ' diagnósticos que já tem continuam' }} no relatório.
              </p>
              <button type="button" class="-my-1 min-h-11 shrink-0 rounded-full px-3 font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto" (click)="desfazer(r.cargo)">
                Desfazer
              </button>
            </li>
          }
        </ul>
      }

      <div class="mt-2 min-h-5 text-[14px]" aria-live="polite">
        @if (erro(); as mensagem) {
          <p class="aparecer font-medium text-travada">{{ mensagem }}</p>
        }
        <span class="sr-only">{{ anuncio() }}</span>
      </div>
    </fieldset>
  `,
  styles: `
    .botao-icone {
      display: grid;
      place-items: center;
      flex: none;
      width: 48px;
      height: 48px;
      border-radius: 14px;
      color: var(--color-abeto-2);
      transition: background-color 160ms, color 160ms;
    }
    .botao-icone:hover {
      background: var(--color-nevoa-2);
      color: var(--color-abeto);
    }
    .botao-icone[aria-disabled='true'] {
      opacity: 0.3;
      cursor: default;
      background: none;
    }
    .botao-tracejado {
      display: inline-flex;
      min-height: 52px;
      width: 100%;
      align-items: center;
      justify-content: center;
      gap: 8px;
      border-radius: 16px;
      border: 1.5px dashed var(--color-abeto-3);
      padding: 0 16px;
      font-size: 15px;
      font-weight: 600;
      color: var(--color-abeto);
    }
    .botao-tracejado:hover {
      background: #fff;
    }
  `,
})
export class SecaoCargos {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly cargos = model<CargoEditavel[]>([]);
  readonly erro = input<string | null>(null);
  readonly editou = output();

  protected readonly maximo = MAXIMO_CARGOS;
  /** Cargos com respostas removidos nesta edição (com o lugar onde estavam, para desfazer). */
  protected readonly removidos = signal<{ cargo: CargoEditavel; posicao: number }[]>([]);
  protected readonly anuncio = signal('');

  /** A escada na tela: do nível mais alto (em cima) ao primeiro nível (embaixo). */
  protected readonly visiveis = computed(() =>
    this.cargos()
      .map((cargo, indice) => ({ cargo, indice }))
      .reverse(),
  );

  protected rotuloUso(n: number): string {
    return n === 1 ? '1 líder já escolheu este cargo' : `${n} líderes já escolheram este cargo`;
  }

  protected renomear(i: number, nome: string) {
    this.cargos.update((lista) => lista.map((c, j) => (j === i ? { ...c, nome } : c)));
    this.editou.emit();
  }

  /** delta +1 sobe um nível (na tela, vai para cima); -1 desce. */
  protected mover(i: number, delta: -1 | 1) {
    const lista = [...this.cargos()];
    const j = i + delta;
    if (j < 0 || j >= lista.length) return;
    [lista[i], lista[j]] = [lista[j], lista[i]];
    this.cargos.set(lista);
    this.anuncio.set(`${lista[j].nome || 'Cargo'} agora é o nível ${j + 1} de ${lista.length}.`);
    this.editou.emit();
  }

  protected remover(i: number) {
    const cargo = this.cargos()[i];
    this.cargos.update((lista) => lista.filter((_, j) => j !== i));
    if (cargo.id !== null && cargo.diagnosticos > 0) {
      this.removidos.update((r) => [...r, { cargo, posicao: i }]);
    }
    this.anuncio.set(`${cargo.nome || 'Cargo'} removido.`);
    this.editou.emit();
  }

  protected desfazer(cargo: CargoEditavel) {
    const removido = this.removidos().find((r) => r.cargo.chave === cargo.chave);
    if (!removido) return;
    this.removidos.update((r) => r.filter((x) => x !== removido));
    this.cargos.update((lista) => {
      const nova = [...lista];
      nova.splice(Math.min(removido.posicao, nova.length), 0, cargo);
      return nova;
    });
    this.anuncio.set(`${cargo.nome} voltou para a lista.`);
    this.editou.emit();
  }

  /** O botão fica embaixo da escada: o cargo novo entra embaixo (primeiro nível) e as setas o levam ao lugar. */
  protected adicionar() {
    this.adicionarAbaixo(0);
  }

  /** Enter no nome cria a linha logo abaixo na tela (um nível abaixo), como numa lista de verdade. */
  protected adicionarAbaixo(i: number) {
    if (this.cargos().length >= MAXIMO_CARGOS) return;
    const novo = novoCargo();
    this.cargos.update((lista) => {
      const nova = [...lista];
      nova.splice(i, 0, novo);
      return nova;
    });
    this.editou.emit();
    this.focar(novo.chave);
  }

  protected usarSugeridos() {
    const sugeridos = cargosSugeridos();
    this.cargos.set(sugeridos);
    this.editou.emit();
    this.focar(sugeridos[sugeridos.length - 1].chave);
  }

  private focar(chave: string) {
    setTimeout(() => (this.host.nativeElement.querySelector(`#${chave}`) as HTMLInputElement | null)?.focus(), 0);
  }
}
