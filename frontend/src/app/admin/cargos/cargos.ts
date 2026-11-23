/** Cargos de liderança da empresa, como o painel edita (a ordem é a hierarquia). */

export interface CargoEditavel {
  /** Identidade local da linha (o id só existe depois de salvo). */
  chave: string;
  id: number | null;
  nome: string;
  /** Diagnósticos que já escolheram este cargo (0 para os novos). */
  diagnosticos: number;
}

/** Igual ao CargoService.MAXIMO do backend: mais que isso fica longo para o líder escolher. */
export const MAXIMO_CARGOS = 12;

/** Ponto de partida no cadastro: o administrador edita, reordena ou remove. */
export const CARGOS_SUGERIDOS = ['Supervisor', 'Coordenador', 'Gerente', 'Diretor'];

let sequencia = 0;

export function novoCargo(nome = ''): CargoEditavel {
  return { chave: `cargo-${++sequencia}`, id: null, nome, diagnosticos: 0 };
}

export function cargosSugeridos(): CargoEditavel[] {
  return CARGOS_SUGERIDOS.map((nome) => novoCargo(nome));
}

export function cargosDaEmpresa(cargos: { id: number; nome: string; diagnosticos: number }[]): CargoEditavel[] {
  return cargos.map((c) => ({ chave: `cargo-${++sequencia}`, id: c.id, nome: c.nome, diagnosticos: c.diagnosticos }));
}

/** Linhas em branco não vão: são só espaço reservado que a pessoa não usou. */
export function cargosParaEnvio(cargos: CargoEditavel[]): { id: number | null; nome: string }[] {
  return cargos
    .map((c) => ({ id: c.id, nome: c.nome.trim().replace(/\s+/g, ' ') }))
    .filter((c) => c.nome.length > 0);
}
