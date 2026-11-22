package br.com.diagnosticolider.questionario;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionarioVersaoRepository extends JpaRepository<QuestionarioVersao, Long> {

	Optional<QuestionarioVersao> findFirstByQuestionarioIdAndStatus(Long questionarioId, QuestionarioVersao.Status status);

}
