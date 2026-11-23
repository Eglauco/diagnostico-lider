package br.com.diagnosticolider.questionario;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionarioRepository extends JpaRepository<Questionario, Long> {

	Optional<Questionario> findByCodigo(String codigo);

}
