package com.onda.marketplace.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    boolean existsByCpfHash(String cpfHash);

    /** Filtro de autenticação: o token só vale enquanto a conta está ativa (não suspensa, não excluída). */
    boolean existsByIdAndAtivoTrue(UUID id);

    /**
     * Leitura por e-mail com trava de escrita na linha (limite de tentativas de senha no login): palpites simultâneos
     * para a mesma conta se enfileiram em vez de lerem o mesmo contador de erros e furarem o limite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailComTrava(@Param("email") String email);

    /** Métrica de estoque do dashboard (US23): contas ativas por papel, estado atual. */
    long countByRoleAndAtivoTrue(UserRole role);

    /**
     * Leitura com trava de escrita na linha (exclusão de conta, US36): um segundo pedido igual — o
     * toque duplo no botão — espera o primeiro terminar e então enxerga a conta já excluída, em vez
     * de repetir a limpeza e o e-mail de aviso.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdComTrava(@Param("id") UUID id);
}
