/**
 * Mantém o rodapé fixo (botão "Continuar", "Salvar"…) acima do teclado do celular.
 *
 * O `interactive-widget=resizes-content` do index.html resolve no Chrome do Android: lá a página
 * encolhe junto com o teclado. O Safari do iPhone (e alguns navegadores dentro de apps) ignora a
 * instrução: o teclado cobre o fim da página e o rodapé fica atrás dele. Aqui a Visual Viewport
 * API mede quanto o teclado cobre e publica no <html>:
 *  - `--teclado`: quanto subir o rodapé para ele encostar no teclado (0 quando a página já encolheu);
 *  - `data-teclado`: o teclado está aberto (o CSS compacta o topo e o rodapé, variante `teclado:`).
 * E, se o rodapé passar a cobrir o campo em que a pessoa digita, rola a página até o campo aparecer.
 */
export function acompanharTeclado(): void {
  const vv = window.visualViewport;
  if (!vv) return;

  const raiz = document.documentElement;
  // Altura sem teclado na orientação atual: base para saber se ele abriu (vale também no Android).
  let larguraBase = 0;
  let alturaSemTeclado = 0;
  let conferencia = 0;
  let aberto = false;
  let publicado = '';

  // Os eventos do Visual Viewport já chegam no ritmo dos quadros: medir é barato e roda direto.
  const medir = () => {
    if (vv.width !== larguraBase) {
      larguraBase = vv.width;
      alturaSemTeclado = 0;
    }
    const editando = editavel(document.activeElement);
    if (!editando) alturaSemTeclado = Math.max(alturaSemTeclado, vv.height);

    // Com zoom de pinça a área visível encolhe sem teclado nenhum: aí o rodapé fica onde está.
    const semZoom = vv.scale < 1.05;
    const cobertura = semZoom ? Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop)) : 0;
    const agora = editando && semZoom && (cobertura > 80 || alturaSemTeclado - vv.height > 120);

    // Rolar com o teclado aberto dispara um evento por quadro: só mexe no estilo quando muda.
    const valor = `${agora ? cobertura : 0}px`;
    if (valor !== publicado) raiz.style.setProperty('--teclado', (publicado = valor));
    if (agora !== aberto) raiz.toggleAttribute('data-teclado', agora);
    if (agora && !aberto) conferir(120);
    aberto = agora;
  };

  // Confere o campo depois que a tela assenta: o teclado terminou de subir (120 ms) ou a etapa
  // nova terminou de entrar com o teclado já aberto (400 ms).
  const conferir = (espera: number) => {
    clearTimeout(conferencia);
    conferencia = window.setTimeout(mostrarCampoAcimaDoRodape, espera);
  };

  vv.addEventListener('resize', medir);
  vv.addEventListener('scroll', medir);
  window.addEventListener('focusin', () => {
    medir();
    if (aberto) conferir(400);
  });
  // Ao sair do campo o teclado pode demorar a descer; o resize do viewport fecha a conta.
  window.addEventListener('focusout', () => setTimeout(medir, 50));
  medir();
}

function editavel(el: Element | null): el is HTMLElement {
  if (!(el instanceof HTMLElement)) return false;
  if (el.isContentEditable || el instanceof HTMLTextAreaElement) return true;
  if (el instanceof HTMLSelectElement) return false;
  return el instanceof HTMLInputElement && !/^(button|checkbox|color|file|hidden|image|radio|range|reset|submit)$/.test(el.type);
}

/**
 * Com o teclado aberto, o campo em foco e o botão que o acompanha têm de ficar à vista:
 *  - formulário com rodapé fixo: o rodapé sobe com o teclado; se passar a cobrir o campo (ou a
 *    ajuda/erro logo abaixo dele), rola até o campo aparecer acima do rodapé;
 *  - campo com o botão logo abaixo (`data-acao-do-campo`, como o "Outro cargo"): rola até o botão
 *    aparecer acima do teclado.
 * Nunca rola a ponto de esconder o campo atrás da barra do topo.
 */
function mostrarCampoAcimaDoRodape() {
  const campo = document.activeElement;
  if (!editavel(campo)) return;
  const caixa = campo.getBoundingClientRect();
  let sobra: number;

  const rodape = campo.closest('form')?.querySelector<HTMLElement>('.rodape-fixo');
  const acao = rodape ? null : campo.closest('section')?.querySelector<HTMLElement>('[data-acao-do-campo]');
  if (rodape) {
    const ajudas = (campo.getAttribute('aria-describedby') ?? '')
      .split(/\s+/)
      .map((id) => (id ? document.getElementById(id) : null))
      .filter((el) => el !== null);
    const base = Math.max(caixa.bottom, ...ajudas.map((el) => el.getBoundingClientRect().bottom));
    sobra = base + 12 - rodape.getBoundingClientRect().top;
  } else if (acao && window.visualViewport) {
    const vv = window.visualViewport;
    sobra = acao.getBoundingClientRect().bottom + 12 - (vv.offsetTop + vv.height);
  } else {
    return;
  }

  sobra = Math.min(sobra, caixa.top - 72);
  if (sobra > 0) window.scrollBy({ top: sobra, behavior: 'smooth' });
}
