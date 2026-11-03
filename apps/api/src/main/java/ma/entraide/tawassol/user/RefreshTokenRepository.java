package ma.entraide.tawassol.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revoque = true where r.famille = :famille")
    int revokeFamily(@Param("famille") String famille);

    @Modifying
    @Query("update RefreshToken r set r.revoque = true where r.utilisateurId = :userId")
    int revokeAllForUser(@Param("userId") String userId);
}
