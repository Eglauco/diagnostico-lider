import { Component, computed, effect, ElementRef, inject, input, linkedSignal, signal, untracked, viewChild } from '@angular/core';
import { httpResource, HttpResourceRef } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Curva } from '../compartilhado/curva';
import { baixarArquivo, buscarArquivo, compartilharArquivo, podeCompartilharArquivos } from '../compartilhado/download';
import { estiloFaixa, FAIXAS, ORDEM_FAIXAS } from '../compartilhado/faixas';
import { quando } from '../compartilhado/formatos';
import { MANTER_ROLAGEM } from '../compartilhado/rolagem';
import { API_ADMIN, ItemLista, Pagina, Painel } from './admin-modelos';
import { SeloFaixa, SeloStatus } from './selos';

const TAMANHO_PAGINA = 20;

type Filtros = Partial<Record<'busca' | 'status' | 'faixa' | 'campanha' | 'cargo' | 'pagina', string | number | null>>;

/** Mantém o último valor carregado enquanto um novo filtro carrega (sem piscar a tela). */
function ultimoValor<T>(recurso: HttpResourceRef<T | undefined>) {
  return linkedSignal<T | undefined, T | undefined>({
    source: () => (recurso.hasValue() ? recurso.value() : undefined),
    computation: (novo, anterior) => novo ?? anterior?.value,
  });
}

function semVazios(valores: Record<string, string | number | null | undefined>): Record<string, string | number> {
  return Object.fromEntries(Object.entries(valores).filter(([, v]) => v !== null && v !== undefined && v !== '')) as Record<
    string,
    string | number
  >;
}

/**
 * Painel: indicadores, curva com todos os líderes e a lista de diagnósticos.
 * Todos os filtros vivem na URL, então dá para compartilhar ou voltar para a mesma visão.
 */
@Component({
  selector: 'app-admin-lista',
  imports: [Curva, RouterLink, SeloFaixa, SeloStatus],
  templateUrl: './admin-lista.html',
  styleUrl: './admin.css',
  host: { class: 'block', '(document:keydown)': 'atalho($event)' },
})
export class AdminLista {
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);

  // Filtros vindos da URL (?busca=&status=&faixa=&campanha=&cargo=&pagina=)
  readonly busca = input<string>();
  readonly status = input<string>();
  readonly faixa = input<string>();
  readonly campanha = input<string>();
  /** Id do cargo ou "outro"; só vale junto com um link de empresa. */
  readonly cargo = input<string>();
  readonly pagina = input<string>();

  protected readonly paginaAtual = computed(() => Math.max(1, Number.parseInt(this.pagina() ?? '1', 10) || 1));

  protected readonly painel = httpResource<Painel>(() => ({
    url: `${API_ADMIN}/painel`,
    params: semVazios({ campanha: this.campanha(), cargo: this.campanha() ? this.cargo() : null }),
  }));
  /** Os filtros da tela como a API entende; a exportação usa os mesmos, sem paginar. */
  private readonly filtrosApi = computed(() =>
    semVazios({
      busca: this.busca(),
      // "Inativos" não é uma situação do diagnóstico: são os tirados dos cálculos.
      status: this.status() === 'INATIVO' ? null : this.status(),
      inativos: this.status() === 'INATIVO' ? 'true' : null,
      faixa: this.faixa(),
      campanha: this.campanha(),
      cargo: this.campanha() ? this.cargo() : null,
    }),
  );
  protected readonly lista = httpResource<Pagina<ItemLista>>(() => ({
    url: `${API_ADMIN}/diagnosticos`,
    params: { ...this.filtrosApi(), pagina: this.paginaAtual() - 1, tamanho: TAMANHO_PAGINA },
  }));
  protected readonly dadosPainel = ultimoValor(this.painel);
  protected readonly dadosLista = ultimoValor(this.lista);

  protected readonly termo = linkedSignal(() => this.busca() ?? '');
  private readonly campoBusca = viewChild<ElementRef<HTMLInputElement>>('campoBusca');
  protected readonly baixando = signal<string | null>(null);
  protected readonly exportando = signal<'pdf' | 'xlsx' | null>(null);
  /** No celular o PDF abre a folha de compartilhar (WhatsApp, e-mail); no computador, baixa. */
  protected readonly compartilhaPdf = podeCompartilharArquivos();
  /** PDF já gerado, guardado quando o navegador pediu um novo toque para abrir a folha. */
  private pdfPronto: { url: string; arquivo: File } | null = null;
  protected readonly erro = signal<string | null>(null);
  protected readonly copiado = signal<string | null>(null);

  protected readonly faixas = ORDEM_FAIXAS.map((codigo) => ({ codigo, ...FAIXAS[codigo] }));
  protected readonly quando = quando;

  protected readonly temFiltros = computed(() => !!(this.busca() || this.status() || this.faixa()));
  protected readonly descricaoFiltros = computed(() => {
    const partes: string[] = [];
    if (this.busca()) partes.push(`“${this.busca()}”`);
    if (this.status()) partes.push(this.status() === 'CONCLUIDO' ? 'concluídos' : 'em andamento');
    const faixa = estiloFaixa(this.faixa());
    if (faixa) partes.push(`faixa ${faixa.rotulo}`);
    return partes.join(' · ');
  });
  protected readonly totalTodosOsLinks = computed(() =>
    (this.dadosPainel()?.campanhas ?? []).reduce((soma, c) => soma + c.total, 0),
  );
  protected readonly campanhaSelecionada = computed(() =>
    this.dadosPainel()?.campanhas.find((c) => c.codigo === this.campanha()),
  );
  protected readonly cargoSelecionado = computed(() =>
    this.campanha() ? this.dadosPainel()?.cargos.find((c) => c.valor === this.cargo()) : undefined,
  );
  protected readonly taxaConclusao = computed(() => {
    const p = this.dadosPainel();
    return p?.total ? Math.round((p.concluidos / p.total) * 100) : 0;
  });
  protected readonly pontos = computed(() =>
    (this.dadosPainel()?.pontos ?? []).map((p) => ({ indice: p.indice, cor: estiloFaixa(p.faixa)?.cor ?? '#0F2B26' })),
  );
  protected readonly distribuicao = computed(() => {
    const p = this.dadosPainel();
    const total = ORDEM_FAIXAS.reduce((soma, f) => soma + (p?.porFaixa[f] ?? 0), 0);
    return this.faixas.map((f) => {
      const quantidade = p?.porFaixa[f.codigo] ?? 0;
      return { ...f, quantidade, percentual: total ? Math.round((quantidade / total) * 100) : 0 };
    });
  });
  protected readonly totalLista = computed(() => this.dadosLista()?.total ?? 0);
  /** "Exportar os 37 diagnósticos": deixa claro que vai a lista toda, não só a página aberta. */
  protected readonly rotuloExportacao = computed(() => {
    const total = this.totalLista();
    if (!total) return 'Exportar a lista';
    return total === 1 ? 'Exportar o diagnóstico da lista' : `Exportar os ${total.toLocaleString('pt-BR')} diagnósticos`;
  });
  protected readonly intervalo = computed(() => {
    const l = this.dadosLista();
    if (!l?.total) return null;
    const inicio = l.pagina * l.tamanho + 1;
    return { inicio, fim: inicio + l.itens.length - 1, total: l.total };
  });

  constructor() {
    // Busca enquanto digita, com uma pequena espera para não consultar a cada tecla.
    effect((onCleanup) => {
      const termo = this.termo().trim();
      if (termo === (untracked(this.busca) ?? '')) return;
      const espera = setTimeout(() => this.filtrar({ busca: termo || null, pagina: null }), 280);
      onCleanup(() => clearTimeout(espera));
    });
  }

  /**
   * Os filtros mudam a URL sem mexer na rolagem: a pessoa continua na lista. Só trocar de página
   * leva ao começo da lista (o título "Respostas"), e não ao topo da tela.
   */
  protected filtrar(mudancas: Filtros) {
    const trocouDePagina = mudancas.pagina != null;
    void this.router
      .navigate([], {
        relativeTo: this.rota,
        queryParams: mudancas,
        queryParamsHandling: 'merge',
        replaceUrl: true,
        state: MANTER_ROLAGEM,
      })
      .then(() => {
        if (trocouDePagina) document.getElementById('titulo-lista')?.scrollIntoView({ block: 'start', behavior: 'smooth' });
      });
  }

  protected limparFiltros() {
    this.termo.set('');
    this.filtrar({ busca: null, status: null, faixa: null, pagina: null });
  }

  protected rotuloCampanha(item: ItemLista): string {
    return item.campanhaDeEmpresa ? item.campanhaNome : 'Link geral';
  }

  protected async baixar(item: ItemLista) {
    if (item.status !== 'CONCLUIDO' || this.baixando()) return;
    this.baixando.set(item.id);
    this.erro.set(null);
    try {
      await baixarArquivo(`${API_ADMIN}/diagnosticos/${item.id}/relatorio.pdf`, 'diagnostico.pdf');
    } catch {
      this.erro.set(`Não foi possível gerar o PDF de ${item.nome}. Tente de novo.`);
    } finally {
      this.baixando.set(null);
    }
  }

  /** Exporta a lista inteira com os filtros da tela (não só a página aberta). */
  protected async exportar(tipo: 'pdf' | 'xlsx') {
    if (this.exportando() || !this.dadosLista()?.total) return;
    const url = `${API_ADMIN}/diagnosticos/exportacao.${tipo}?${new URLSearchParams(
      Object.entries(this.filtrosApi()).map(([chave, valor]) => [chave, String(valor)]),
    )}`;
    const nome = `diagnosticos.${tipo}`;
    this.exportando.set(tipo);
    this.erro.set(null);
    try {
      if (tipo === 'xlsx' || !this.compartilhaPdf) {
        await baixarArquivo(url, nome);
        return;
      }
      const arquivo = this.pdfPronto?.url === url ? this.pdfPronto.arquivo : await buscarArquivo(url, nome);
      const resultado = await compartilharArquivo(arquivo, 'Diagnósticos');
      this.pdfPronto = resultado === 'precisa-de-toque' ? { url, arquivo } : null;
      if (resultado === 'precisa-de-toque') this.erro.set('O PDF está pronto. Toque em "PDF" de novo para enviar.');
    } catch (e) {
      const motivo = e instanceof Error && !e.message.startsWith('Falha ao baixar') ? e.message : null;
      this.erro.set(motivo ?? `Não foi possível gerar ${tipo === 'pdf' ? 'o PDF' : 'a planilha'} agora. Tente de novo.`);
    } finally {
      this.exportando.set(null);
    }
  }

  protected async copiarLink(codigo: string) {
    const link = `${location.origin}${codigo === 'geral' ? '/' : `/c/${codigo}`}`;
    try {
      await navigator.clipboard.writeText(link);
      this.copiado.set(codigo);
      setTimeout(() => this.copiado.set(null), 2000);
    } catch {
      this.erro.set(`Copie o link manualmente: ${link}`);
    }
  }

  /** "/" leva direto para a busca, como em ferramentas de produtividade. */
  protected atalho(evento: KeyboardEvent) {
    const alvo = evento.target as HTMLElement | null;
    if (evento.key !== '/' || alvo?.closest('input, textarea, [contenteditable]')) return;
    evento.preventDefault();
    this.campoBusca()?.nativeElement.focus();
  }
}
