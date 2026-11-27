package br.com.diagnosticolider.rh;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AcessoRhRepository extends JpaRepository<AcessoRh, Long> {

	Optional<AcessoRh> findByTokenHash(String tokenHash);

}
