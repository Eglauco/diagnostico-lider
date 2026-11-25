import { Component, computed, inject, input, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { Location, LowerCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { Curva } from '../compartilhado/curva';
import { baixarArquivo } from '../compartilhado/download';
import { estiloFaixa } from '../compartilhado/faixas';
import { dataCompleta, duracao, linkWhatsapp, quando, whatsappLegivel } from '../compartilhado/formatos';
import { API_ADMIN, Detalhe } from './admin-modelos';
import { SeloStatus } from './selos';

@Component({
  selector: 'app-admin-detalhe',
  imports: [Curva, LowerCasePipe, RouterLink, SeloStatus],
  templateUrl: './admin-detalhe.html',
  styleUrl: './admin.css',
  host: { class: 'block' },
})
export class AdminDetalhe {
  private readonly location = inject(Location);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);

  readonly id = input.required<string>();

  protected readonly detalhe = httpResource<Detalhe>(() => `${API_ADMIN}/diagnosticos/${this.id()}`);
  private readonly dados = computed(() => (this.detalhe.hasValue() ? this.detalhe.value() : undefined));

  protected readonly estilo = computed(() => estiloFaixa(this.dados()?.resultado?.faixa.codigo));
  protected readonly primeiroNome = computed(() => this.dados()?.lider.nome.split(' ')[0] ?? '');
  protected readonly respondidas = computed(() => this.dados()?.respostas.filter((r) => r.respondida).length ?? 0);
  protected readonly tempo = computed(() => duracao(this.dados()?.iniciadoEm, this.dados()?.concluidoEm));

  protected readonly baixando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly copiado = signal<string | null>(null);

  protected readonly quando = quando;
  protected readonly dataCompleta = dataCompleta;
  protected readonly whatsappLegivel = whatsappLegivel;
  protected readonly linkWhatsapp = linkWhatsapp;
  protected readonly letras = 'ABCDEFGHIJ';

  /** Volta para a lista preservando busca e filtros; se abriu direto, vai para o painel. */
  protected readonly confirmandoInativacao = signal(false);
  protected readonly mudandoAtivo = signal(false);

  /** Inativa (tira dos cálculos) ou reativa; o convite do líder, se houver, acompanha. */
  protected async mudarAtivo(d: Detalhe, acao: 'inativar' | 'reativar') {
    this.mudandoAtivo.set(true);
    try {
      await firstValueFrom(this.http.post(`${API_ADMIN}/diagnosticos/${d.id}/${acao}`, null));
      this.confirmandoInativacao.set(false);
      this.detalhe.reload();
    } catch {
      this.erro.set('Não foi possível concluir agora. Tente de novo.');
    } finally {
      this.mudandoAtivo.set(false);
    }
  }

  protected voltar() {
    const estado = this.location.getState() as { navigationId?: number } | null;
    if ((estado?.navigationId ?? 1) > 1) this.location.back();
    else void this.router.navigate(['/administrador']);
  }

  protected linkDaCampanha(codigo: string): string {
    return `${location.origin}${codigo === 'geral' ? '/' : `/c/${codigo}`}`;
  }

  protected async baixar(d: Detalhe) {
    if (!d.resultado || this.baixando()) return;
    this.baixando.set(true);
    this.erro.set(null);
    try {
      await baixarArquivo(`${API_ADMIN}/diagnosticos/${d.id}/relatorio.pdf`, 'diagnostico.pdf');
    } catch {
      this.erro.set('Não foi possível gerar o PDF agora. Tente de novo.');
    } finally {
      this.baixando.set(false);
    }
  }

  protected async copiar(texto: string, chave: string) {
    try {
      await navigator.clipboard.writeText(texto);
      this.copiado.set(chave);
      setTimeout(() => this.copiado.set(null), 2000);
    } catch {
      this.erro.set(`Copie manualmente: ${texto}`);
    }
  }
}
