package com.appreseal.repo;

import com.appreseal.domain.AppleAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppleAccountRepository extends JpaRepository<AppleAccount, String> {
    List<AppleAccount> findAllByOrderByPriorityAsc();

    Optional<AppleAccount> findByEmailIgnoreCase(String email);
}
