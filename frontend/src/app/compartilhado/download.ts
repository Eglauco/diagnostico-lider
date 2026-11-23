/** Entrega um arquivo já gerado no navegador como download. */
export function salvarBlob(blob: Blob, nome: string): void {
  const objeto = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objeto;
  link.download = nome;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objeto), 30_000);
}

/**
 * Busca um arquivo gerado pelo servidor (fetch + blob), com o nome sugerido por ele quando
 * houver. Se der errado, o servidor explica em application/problem+json (ex.: poucas respostas).
 */
export async function buscarArquivo(url: string, nomePadrao: string): Promise<File> {
  const resposta = await fetch(url);
  if (!resposta.ok) {
    const problema = (await resposta.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(problema?.detail ?? `Falha ao baixar (${resposta.status})`);
  }
  const nome = resposta.headers.get('Content-Disposition')?.match(/filename="([^"]+)"/)?.[1] ?? nomePadrao;
  const blob = await resposta.blob();
  return new File([blob], nome, { type: blob.type || 'application/octet-stream' });
}

/**
 * Baixa um arquivo mantendo a pessoa na página, para dar feedback de "gerando…" enquanto o
 * PDF é montado.
 */
export async function baixarArquivo(url: string, nomePadrao: string): Promise<void> {
  const arquivo = await buscarArquivo(url, nomePadrao);
  salvarBlob(arquivo, arquivo.name);
}

/**
 * Celular com a folha de compartilhar do sistema aceitando arquivos (iPhone, Android): dá para
 * mandar o PDF direto para o WhatsApp, e-mail ou "Salvar em Arquivos". No computador o
 * principal continua sendo baixar, mesmo onde o navegador também compartilha.
 */
export function podeCompartilharArquivos(): boolean {
  if (typeof navigator === 'undefined' || typeof navigator.canShare !== 'function') return false;
  if (!matchMedia('(pointer: coarse)').matches) return false;
  try {
    return navigator.canShare({ files: [new File([''], 'teste.pdf', { type: 'application/pdf' })] });
  } catch {
    return false;
  }
}

/**
 * Abre a folha de compartilhar com o arquivo. O navegador só abre a folha logo depois de um
 * toque: se o arquivo demorou a ficar pronto, ele recusa ("precisa-de-toque") e basta tocar de
 * novo, agora com o arquivo em mãos.
 */
export async function compartilharArquivo(
  arquivo: File,
  titulo: string,
): Promise<'compartilhado' | 'cancelado' | 'precisa-de-toque'> {
  try {
    await navigator.share({ files: [arquivo], title: titulo });
    return 'compartilhado';
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') return 'cancelado';
    if (e instanceof DOMException && e.name === 'NotAllowedError') return 'precisa-de-toque';
    throw e;
  }
}
