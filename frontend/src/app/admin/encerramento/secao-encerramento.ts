import { Component, computed, model, output } from '@angular/core';

import { prazoPorExtenso } from '../../compartilhado/formatos';

/** "2026-11-30T21:00:00Z" → { data: "2026-11-30", hora: "18:00" } no horário deste aparelho. */
function partes(iso: string | null): { data: string; hora: string } {
  if (!iso) return { data: '', hora: '18:00' };
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return { data: `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`, hora: `${p(d.getHours())}:${p(d.getMinutes())}` };
}

function iso(data: string, hora: string): string | null {
  if (!data) return null;
  const d = new Date(`${data}T${hora || '18:00'}`);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

/** Mesmo texto do lado do líder ("segunda-feira, 30 de novembro, às 18h"). */
export const encerramentoPorExtenso = prazoPorExtenso;

/**
 * "Encerramento da avaliação": data e hora em que os links fecham, os convites e lembretes
 * param e o relatório da empresa vira o resultado final. Atalhos de prazo para o caso comum.
 */
@Component({
  selector: 'app-secao-encerramento',
  host: { class: 'block' },
  styleUrl: '../admin.css',
  template: `
    <fieldset>
      <legend class="text-[15px] font-semibold">Encerramento da avaliação</legend>
      <p class="mt-1 text-[14px] text-abeto-2">
        Depois desta data, os links fecham, os convites e lembretes param e o relatório da empresa vira o resultado final.
      </p>

      @if (!valor()) {
        <div class="mt-4 flex flex-wrap gap-2">
          @for (prazo of prazos; track prazo.dias) {
            <button type="button" class="chip-prazo" (click)="emDias(prazo.dias)">{{ prazo.texto }}</button>
          }
          <button type="button" class="chip-prazo" (click)="emDias(14, true)">Escolher data</button>
        </div>
        <p class="mt-3 text-[14px] text-abeto-2">Sem data, a avaliação fica aberta até você encerrar.</p>
      } @else {
        <div class="mt-4 grid grid-cols-[minmax(0,1fr)_7.5rem] gap-3">
          <div>
            <label for="encerra-data" class="campo-rotulo">Data</label>
            <input id="encerra-data" type="date" class="campo" [value]="partes().data" (input)="mudar($any($event.target).value, partes().hora)" />
          </div>
          <div>
            <label for="encerra-hora" class="campo-rotulo">Hora</label>
            <input id="encerra-hora" type="time" step="900" class="campo" [value]="partes().hora" (input)="mudar(partes().data, $any($event.target).value)" />
          </div>
        </div>
        <div class="mt-3 flex items-start justify-between gap-3 text-[14px]" aria-live="polite">
          @if (passou()) {
            <p class="font-medium text-travada">Essa data já passou: ao salvar, a avaliação fica encerrada e o resultado fecha.</p>
          } @else {
            <p class="text-abeto-2">
              Encerra <strong class="font-semibold text-abeto">{{ porExtenso() }}</strong>{{ faltam() }}.
            </p>
          }
          <button type="button" class="-my-1 min-h-11 shrink-0 rounded-full px-2 font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto" (click)="limpar()">
            Tirar a data
          </button>
        </div>
      }
    </fieldset>
  `,
  styles: `
    .chip-prazo {
      min-height: 44px;
      padding: 0 16px;
      border-radius: 999px;
      border: 1.5px solid var(--color-linha);
      background: #fff;
      font-size: 15px;
      font-weight: 600;
      color: var(--color-abeto);
      transition: border-color 160ms;
    }
    .chip-prazo:hover {
      border-color: var(--color-abeto-3);
    }
  `,
})
export class SecaoEncerramento {
  /** Data e hora em ISO (null: sem data). */
  readonly valor = model<string | null>(null);
  readonly editou = output();

  protected readonly prazos = [
    { dias: 7, texto: 'Em 7 dias' },
    { dias: 15, texto: 'Em 15 dias' },
    { dias: 30, texto: 'Em 30 dias' },
  ];

  protected readonly partes = computed(() => partes(this.valor()));
  protected readonly porExtenso = computed(() => encerramentoPorExtenso(this.valor()));
  protected readonly passou = computed(() => {
    const v = this.valor();
    return !!v && new Date(v).getTime() <= Date.now();
  });
  protected readonly faltam = computed(() => {
    const v = this.valor();
    if (!v) return '';
    const dias = Math.ceil((new Date(v).getTime() - Date.now()) / 86_400_000);
    return dias <= 1 ? ' (amanhã ou antes)' : ` (faltam ${dias} dias)`;
  });

  /** Prazo em N dias, às 18h (o fim do expediente). */
  protected emDias(dias: number, focar = false) {
    const d = new Date();
    d.setDate(d.getDate() + dias);
    d.setHours(18, 0, 0, 0);
    this.valor.set(d.toISOString());
    this.editou.emit();
    if (focar) setTimeout(() => (document.getElementById('encerra-data') as HTMLInputElement | null)?.focus(), 0);
  }

  protected mudar(data: string, hora: string) {
    this.valor.set(iso(data, hora));
    this.editou.emit();
  }

  protected limpar() {
    this.valor.set(null);
    this.editou.emit();
  }
}
