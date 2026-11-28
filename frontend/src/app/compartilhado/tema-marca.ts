/**
 * Paleta da empresa do link (gerada no backend por PaletaMarca.java, que garante o contraste).
 * Aqui ela só vira variáveis de CSS: como as classes do Tailwind leem essas variáveis, a tela
 * inteira troca de cor sem mudar os componentes. As cores das faixas nunca mudam.
 */
export interface TemaMarca {
  acao: string;
  acaoForte: string;
  sobreAcao: string;
  tinta: string;
  tinta2: string;
  tinta3: string;
  fundo: string;
  fundo2: string;
  linha: string;
  destaque: string;
  destaqueTexto: string;
  sobreDestaque: string;
  destaqueSobreTinta: string;
  tintaBrilho: string;
}

/**
 * Logomarca enviada no cadastro. Com bucket, a url é direta dele (assinada, vale por dias);
 * em dev sem bucket, é a do backend. O endereço muda a cada nova versão.
 */
export interface LogoEmpresa {
  url: string;
  largura: number;
  altura: number;
}

/** Logo "quadrada" (só o símbolo) pede o nome ao lado; logo larga já traz o nome. */
export const logoPedeNome = (logo: LogoEmpresa) => logo.largura < logo.altura * 1.8;

/**
 * Foto de fundo da página do RH (a entrada e o topo da página). O foco (0 a 100, da esquerda e
 * do topo) é o ponto que fica sempre à vista quando a foto é cortada.
 */
export interface FundoEmpresa {
  url: string;
  largura: number;
  altura: number;
  focoX: number;
  focoY: number;
}

/** A empresa do link: nome em destaque, logo ou monograma (iniciais), a paleta e a foto de fundo. */
export interface MarcaEmpresa {
  nome: string;
  sigla: string;
  personalizada: boolean;
  tema: TemaMarca;
  logo: LogoEmpresa | null;
  fundo?: FundoEmpresa | null;
}

/** Token do tema → variável de CSS (os nomes antigos continuam: abeto = tinta, brasa = destaque). */
const VARIAVEIS: Partial<Record<keyof TemaMarca, string>> = {
  acao: '--color-acao',
  acaoForte: '--color-acao-forte',
  sobreAcao: '--color-sobre-acao',
  tinta: '--color-abeto',
  tinta2: '--color-abeto-2',
  tinta3: '--color-abeto-3',
  fundo: '--color-nevoa',
  fundo2: '--color-nevoa-2',
  linha: '--color-linha',
  destaque: '--color-brasa',
  destaqueTexto: '--color-brasa-escura',
  sobreDestaque: '--color-sobre-brasa',
};

const COR_PADRAO_NAVEGADOR = '#EDF2EF';

/**
 * Aplica (ou remove, com null) a paleta da empresa na página inteira, inclusive na barra do
 * navegador do celular. Devolve a função que desfaz.
 */
export function aplicarTema(tema: TemaMarca | null): () => void {
  const raiz = document.documentElement;
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (!tema) return () => undefined;
  for (const [chave, variavel] of Object.entries(VARIAVEIS)) {
    raiz.style.setProperty(variavel, tema[chave as keyof TemaMarca]);
  }
  meta?.setAttribute('content', tema.fundo);
  return () => {
    for (const variavel of Object.values(VARIAVEIS)) raiz.style.removeProperty(variavel);
    meta?.setAttribute('content', COR_PADRAO_NAVEGADOR);
  };
}
