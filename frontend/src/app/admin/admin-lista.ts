import { Component, computed, effect, ElementRef, inject, input, linkedSignal, signal, untracked, viewChild } from '@angular/core';
import { httpResource, HttpResourceRef } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Curva } from '../compartilhado/curva';
import { baixarArquivo, buscarArquivo, compartilharArquivo, podeCompartilharArquivos } from '../compartilhado/download';
import { estiloFaixa, FAIXAS, ORDEM_FAIXAS } from '../compartilhado/faixas';
import { quando } from '../compartilhado/formatos';
import { MANTER_ROLAGEM } from '../compartilhado/rolagem';
import { API_ADMIN, EncontradoEmEmpresa, ItemLista, OrigemLider, Pagina, Painel } from './admin-modelos';
import { EmpresaAtual } from './empresa/empresa-atual';
import { SeloFaixa, SeloStatus } from './selos';

const TAMANHO_PAGINA = 20;

type Filtros = Partial<Record<'busca' | 'status' | 'faixa' | 'campanha' | 'cargo' | 'pagina', string | number | null>>;

const ROTULO_ORIGEM: Record<OrigemLider, string> = {
  PLANILHA: 'Planilha',
  MANUAL: 'Cadastro manual',
  LINK: 'Link da empresa',
};

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
 * Indicadores, curva com os líderes e a lista de diagnósticos, em dois lugares:
 * - Diagnóstico Geral (/administrador/geral): só quem respondeu pelo link geral, sem empresa;
 * - aba Diagnóstico da empresa (/administrador/empresas/:id/diagnostico): o link dela, com cargos,
 *   a origem de cada líder e o relatório consolidado.
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
  private readonly empresaAtual = inject(EmpresaAtual, { optional: true });

  /** Vem do "data" da rota: o Diagnóstico Geral ou a aba de uma empresa. */
  readonly modo = input<'geral' | 'empresa'>('geral');

  // Filtros vindos da URL (?busca=&status=&faixa=&cargo=&pagina=)
  readonly busca = input<string>();
  readonly status = input<string>();
  readonly faixa = input<string>();
  /** Só em links antigos (?campanha=): o de uma empresa leva à aba Diagnóstico dela. */
  readonly campanha = input<string>();
  /** Id do cargo ou "outro"; só na aba da empresa. */
  readonly cargo = input<string>();
  readonly pagina = input<string>();

  protected readonly geral = computed(() => this.modo() !== 'empresa');
  protected readonly empresa = computed(() =>
    !this.geral() && this.empresaAtual?.empresa.hasValue() ? this.empresaAtual.empresa.value() : undefined,
  );
  /** O link cujos diagnósticos aparecem: o geral ou o da empresa (undefined enquanto ela carrega). */
  private readonly codigo = computed(() => (this.geral() ? 'geral' : this.empresa()?.link?.codigo));
  private readonly cargoAtivo = computed(() => (this.geral() ? null : this.cargo()));

  protected readonly paginaAtual = computed(() => Math.max(1, Number.parseInt(this.pagina() ?? '1', 10) || 1));

  protected readonly painel = httpResource<Painel>(() => {
    const codigo = this.codigo();
    return codigo ? { url: `${API_ADMIN}/painel`, params: semVazios({ campanha: codigo, cargo: this.cargoAtivo() }) } : undefined;
  });
  /** Os filtros da tela como a API entende; a exportação usa os mesmos, sem paginar. */
  private readonly filtrosApi = computed(() => {
    const codigo = this.codigo();
    if (!codigo) return null;
    return semVazios({
      busca: this.busca(),
      // "Inativos" não é uma situação do diagnóstico: são os tirados dos cálculos.
      status: this.status() === 'INATIVO' ? null : this.status(),
      inativos: this.status() === 'INATIVO' ? 'true' : null,
      faixa: this.faixa(),
      campanha: codigo,
      cargo: this.cargoAtivo(),
    });
  });
  protected readonly lista = httpResource<Pagina<ItemLista>>(() => {
    const filtros = this.filtrosApi();
    return filtros
      ? { url: `${API_ADMIN}/diagnosticos`, params: { ...filtros, pagina: this.paginaAtual() - 1, tamanho: TAMANHO_PAGINA } }
      : undefined;
  });
  /** Busca no Diagnóstico Geral: em quais empresas a pessoa também aparece. */
  protected readonly nasEmpresas = httpResource<EncontradoEmEmpresa[]>(() => {
    const busca = this.busca()?.trim();
    return this.geral() && busca ? { url: `${API_ADMIN}/diagnosticos/nas-empresas`, params: { busca } } : undefined;
  });
  protected readonly dadosPainel = ultimoValor(this.painel);
  protected readonly dadosLista = ultimoValor(this.lista);
  protected readonly encontrados = computed(() =>
    this.busca() && this.nasEmpresas.hasValue() ? (this.nasEmpresas.value() ?? []) : [],
  );

  protected readonly termo = linkedSignal(() => this.busca() ?? '');
  private readonly campoBusca = viewChild<ElementRef<HTMLInputElement>>('campoBusca');
  protected readonly baixando = signal<string | null>(null);
  protected readonly exportando = signal<'pdf' | 'xlsx' | null>(null);
  /** No celular o PDF abre a folha de compartilhar (WhatsApp, e-mail); no computador, baixa. */
  protected readonly compartilhaPdf = podeCompartilharArquivos();
  /** PDF já gerado, guardado quando o navegador pediu um novo toque para abrir a folha. */
  private pdfPronto: { url: string; arquivo: File } | null = null;
  protected readonly erro = signal<string | null>(null);
  protected readonly copiado = signal(false);

  protected readonly faixas = ORDEM_FAIXAS.map((codigo) => ({ codigo, ...FAIXAS[codigo] }));
  protected readonly quando = quando;

  protected readonly temFiltros = computed(() => !!(this.busca() || this.status() || this.faixa()));
  protected readonly descricaoFiltros = computed(() => {
    const partes: string[] = [];
    if (this.busca()) partes.push(`“${this.busca()}”`);
    if (this.status()) partes.push(this.status() === 'CONCLUIDO' ? 'concluídos' : this.status() === 'INATIVO' ? 'inativos' : 'em andamento');
    const faixa = estiloFaixa(this.faixa());
    if (faixa) partes.push(`faixa ${faixa.rotulo}`);
    return partes.join(' · ');
  });
  protected readonly cargoSelecionado = computed(() =>
    this.geral() ? undefined : this.dadosPainel()?.cargos.find((c) => c.valor === this.cargo()),
  );
  protected readonly taxaConclusao = computed(() => {
    const p = this.dadosPainel();
    return p?.total ? Math.round((p.concluidos / p.total) * 100) : 0;
  });
  /** "2 pontos acima da média geral (52)", só na empresa. */
  protected readonly comparacao = computed(() => {
    const p = this.dadosPainel();
    if (this.geral() || !p?.referencia || p.indiceMedio === null) return null;
    const diferenca = p.indiceMedio - p.referencia.media;
    if (diferenca === 0) return `na média geral (${p.referencia.media})`;
    const pontos = Math.abs(diferenca);
    return `${pontos} ${pontos === 1 ? 'ponto' : 'pontos'} ${diferenca > 0 ? 'acima' : 'abaixo'} da média geral (${p.referencia.media})`;
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

  // Relatório consolidado da empresa: só com respostas suficientes para ninguém ser identificado.
  protected readonly faltamParaRelatorio = computed(() => {
    const e = this.empresa();
    return e ? Math.max(0, e.minimoRelatorio - e.indicadores.concluidos) : 0;
  });
  protected readonly urlRelatorio = computed(() => `${API_ADMIN}/empresas/${this.empresa()?.id}/relatorio.pdf`);
  protected readonly gerandoRelatorio = signal(false);

  constructor() {
    // Busca enquanto digita, com uma pequena espera para não consultar a cada tecla.
    effect((onCleanup) => {
      const termo = this.termo().trim();
      if (termo === (untracked(this.busca) ?? '')) return;
      const espera = setTimeout(() => this.filtrar({ busca: termo || null, pagina: null }), 280);
      onCleanup(() => clearTimeout(espera));
    });

    // Link antigo (/administrador?campanha=demo&cargo=3): a lista de uma empresa mora na tela dela.
    effect(() => {
      const codigo = this.campanha();
      const campanhas = this.dadosPainel()?.campanhas;
      if (!this.geral() || !codigo || !campanhas) return;
      untracked(() => {
        const destino = campanhas.find((c) => c.codigo === codigo)?.empresaId;
        if (destino) {
          void this.router.navigate(['/administrador/empresas', destino, 'diagnostico'], {
            queryParams: semVazios({ busca: this.busca(), status: this.status(), faixa: this.faixa(), cargo: this.cargo() }),
            replaceUrl: true,
          });
        } else {
          this.filtrar({ campanha: null, cargo: null });
        }
      });
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

  /**
   * Fora do cadastro de líderes e em andamento, só pode ser quem já tinha respondido e começou de
   * novo: vira a resposta dele quando terminar.
   */
  protected origem(item: ItemLista): string {
    if (item.origem) return ROTULO_ORIGEM[item.origem];
    return item.status === 'EM_ANDAMENTO' ? 'Nova tentativa' : '—';
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
    const filtros = this.filtrosApi();
    if (this.exportando() || !filtros || !this.dadosLista()?.total) return;
    const url = `${API_ADMIN}/diagnosticos/exportacao.${tipo}?${new URLSearchParams(
      Object.entries(filtros).map(([chave, valor]) => [chave, String(valor)]),
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
      const resultado = await compartilharArquivo(arquivo, this.geral() ? 'Diagnóstico Geral' : `Diagnóstico · ${this.empresa()?.nome ?? ''}`);
      this.pdfPronto = resultado === 'precisa-de-toque' ? { url, arquivo } : null;
      if (resultado === 'precisa-de-toque') this.erro.set('O PDF está pronto. Toque em "PDF" de novo para enviar.');
    } catch (e) {
      const motivo = e instanceof Error && !e.message.startsWith('Falha ao baixar') ? e.message : null;
      this.erro.set(motivo ?? `Não foi possível gerar ${tipo === 'pdf' ? 'o PDF' : 'a planilha'} agora. Tente de novo.`);
    } finally {
      this.exportando.set(null);
    }
  }

  protected async baixarRelatorio() {
    if (this.gerandoRelatorio() || !this.empresa()) return;
    this.gerandoRelatorio.set(true);
    this.erro.set(null);
    try {
      await baixarArquivo(this.urlRelatorio(), 'relatorio-consolidado.pdf');
    } catch (e) {
      this.erro.set(e instanceof Error && !e.message.startsWith('Falha') ? e.message : 'Não foi possível gerar o relatório agora. Tente de novo.');
    } finally {
      this.gerandoRelatorio.set(false);
    }
  }

  /** O link geral (no Diagnóstico Geral) ou o da empresa. */
  protected async copiarLink() {
    const link = this.geral() ? `${location.origin}/` : this.empresa()?.link?.url;
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link);
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2000);
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
