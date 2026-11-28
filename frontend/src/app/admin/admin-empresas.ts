import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { quando } from '../compartilhado/formatos';
import { API_ADMIN, EmpresaResumo } from './admin-modelos';
import { prazoDaAvaliacao } from './empresa/prazo';

const tempo = (iso: string | null | undefined) => (iso ? new Date(iso).getTime() : 0);

/**
 * A carteira: todas as empresas juntas (avaliações abertas, líderes, quantos responderam, índice
 * médio) e um cartão por empresa com o progresso dela. As que encerram antes vêm primeiro; as
 * encerradas ficam num grupo à parte, no fim.
 */
@Component({
  selector: 'app-admin-empresas',
  imports: [NgTemplateOutlet, RouterLink],
  templateUrl: './admin-empresas.html',
  styleUrl: './admin.css',
  host: { class: 'block' },
})
export class AdminEmpresas {
  protected readonly empresas = httpResource<EmpresaResumo[]>(() => `${API_ADMIN}/empresas`);
  protected readonly copiado = signal<number | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly quando = quando;

  private readonly todas = computed(() => (this.empresas.hasValue() ? this.empresas.value() : []));

  /** Abertas: as com prazo, a que encerra antes primeiro; depois as sem prazo, a mais movimentada primeiro. */
  protected readonly abertas = computed(() =>
    this.todas()
      .filter((e) => !e.encerramento.encerrada)
      .sort((a, b) => {
        const pa = a.encerramento.em;
        const pb = b.encerramento.em;
        if (pa && pb) return tempo(pa) - tempo(pb);
        if (pa || pb) return pa ? -1 : 1;
        return (tempo(b.ultimoDiagnostico) || tempo(b.criadaEm)) - (tempo(a.ultimoDiagnostico) || tempo(a.criadaEm));
      }),
  );
  /** Encerradas: a que encerrou por último primeiro. */
  protected readonly encerradas = computed(() =>
    this.todas()
      .filter((e) => e.encerramento.encerrada)
      .sort((a, b) => tempo(b.encerramento.em) - tempo(a.encerramento.em)),
  );

  protected readonly totais = computed(() => {
    const todas = this.todas();
    const lideres = todas.reduce((soma, e) => soma + e.lideres, 0);
    const responderam = todas.reduce((soma, e) => soma + e.lideresConcluiram, 0);
    // A média da carteira pondera cada empresa pelos seus concluídos.
    const comMedia = todas.filter((e) => e.indiceMedio !== null && e.concluidos > 0);
    const concluidos = comMedia.reduce((soma, e) => soma + e.concluidos, 0);
    const indice = concluidos ? Math.round(comMedia.reduce((soma, e) => soma + (e.indiceMedio ?? 0) * e.concluidos, 0) / concluidos) : null;
    return {
      empresas: todas.length,
      abertas: this.abertas().length,
      lideres,
      responderam,
      percentual: lideres ? Math.round((responderam / lideres) * 100) : 0,
      indice,
      concluidos,
    };
  });

  protected prazo(e: EmpresaResumo) {
    return prazoDaAvaliacao(e.encerramento);
  }

  protected progresso(e: EmpresaResumo): number {
    return e.lideres ? Math.round((e.lideresConcluiram / e.lideres) * 100) : 0;
  }

  protected indice(e: EmpresaResumo): number | null {
    return e.indiceMedio === null ? null : Math.round(e.indiceMedio);
  }

  protected async copiarLink(empresa: EmpresaResumo) {
    if (!empresa.link) return;
    try {
      await navigator.clipboard.writeText(empresa.link.url);
      this.copiado.set(empresa.id);
      setTimeout(() => this.copiado.set(null), 2000);
    } catch {
      this.erro.set(`Copie o link manualmente: ${empresa.link.url}`);
    }
  }
}
