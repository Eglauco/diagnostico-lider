/**
 * Texto pronto para a empresa repassar aos líderes. "{Empresa} convida você" evita
 * errar o artigo (da Petrobras, do Grupo Alfa).
 */
export function mensagemConvite(empresa: string, url: string): string {
  return (
    `Olá! ${empresa} convida você para o Diagnóstico do Líder: algumas perguntas rápidas para ` +
    `descobrir em que ponto da curva está a sua carreira. O resultado sai na hora e chega em PDF no seu e-mail.\n\n` +
    `Comece por aqui: ${url}`
  );
}

export function linkWhatsappComTexto(texto: string): string {
  return `https://wa.me/?text=${encodeURIComponent(texto)}`;
}
