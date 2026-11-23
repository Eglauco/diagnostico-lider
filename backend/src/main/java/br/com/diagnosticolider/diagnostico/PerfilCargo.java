package br.com.diagnosticolider.diagnostico;

import br.com.diagnosticolider.empresa.Cargo;

/**
 * Cargo e tempo no cargo informados pelo líder no link da empresa. "Outro cargo": cargo nulo,
 * outro = true e, se ele escreveu, o nome do cargo.
 */
public record PerfilCargo(Cargo cargo, boolean outro, String outroNome, TempoCargo tempo) {

	/** Link geral ou empresa sem cargos cadastrados: nada a perguntar. */
	public static final PerfilCargo NENHUM = new PerfilCargo(null, false, null, null);

}
