package com.appreseal.repo;

import com.appreseal.domain.Role;
import com.appreseal.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByLastLoginAtAfter(Instant since);

    long countByCreatedAtAfter(Instant since);

    long countByRole(Role role);

    @Query("""
            select u from User u
            where lower(u.email) like lower(concat('%', :q, '%'))
               or lower(u.nickname) like lower(concat('%', :q, '%'))
            order by u.createdAt asc
            """)
    List<User> search(@Param("q") String q);
}
