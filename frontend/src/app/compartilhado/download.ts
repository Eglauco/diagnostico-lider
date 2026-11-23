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
 * Baixa um arquivo mantendo o líder na página (fetch + blob), para dar feedback de
 * "gerando…" enquanto o PDF é montado. Usa o nome sugerido pelo servidor quando houver.
 */
export async function baixarArquivo(url: string, nomePadrao: string): Promise<void> {
  const resposta = await fetch(url);
  if (!resposta.ok) {
    // O servidor explica o motivo em application/problem+json (ex.: poucas respostas).
    const problema = (await resposta.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(problema?.detail ?? `Falha ao baixar (${resposta.status})`);
  }
  const nome = resposta.headers.get('Content-Disposition')?.match(/filename="([^"]+)"/)?.[1] ?? nomePadrao;
  salvarBlob(await resposta.blob(), nome);
}
