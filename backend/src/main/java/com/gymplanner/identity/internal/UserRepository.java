package com.gymplanner.identity.internal;

import com.gymplanner.identity.api.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username) and (:excludeId is null or u.id <> :excludeId)")
    boolean existsUsername(@Param("username") String username, @Param("excludeId") UUID excludeId);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email) and (:excludeId is null or u.id <> :excludeId)")
    boolean existsEmail(@Param("email") String email, @Param("excludeId") UUID excludeId);

    boolean existsByRole(UserRole role);

    long countByRoleAndActiveTrue(UserRole role);

    @Query("""
            select u from User u
            where (lower(u.username) like :pattern escape '\\'
                or lower(u.email) like :pattern escape '\\'
                or lower(concat(u.firstName, ' ', u.lastName)) like :pattern escape '\\'
                or lower(concat(u.lastName, ' ', u.firstName)) like :pattern escape '\\')
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
            """)
    Page<User> search(@Param("pattern") String pattern, @Param("role") UserRole role,
            @Param("active") Boolean active, Pageable pageable);

    List<User> findByIdIn(Collection<UUID> ids);
}
