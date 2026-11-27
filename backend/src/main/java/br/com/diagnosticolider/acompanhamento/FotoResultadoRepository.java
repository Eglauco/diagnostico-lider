package br.com.diagnosticolider.acompanhamento;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface FotoResultadoRepository extends JpaRepository<FotoResultado, Long> {

	Optional<FotoResultado> findFirstByEmpresaIdOrderByTiradaEmDescIdDesc(Long empresaId);

}
