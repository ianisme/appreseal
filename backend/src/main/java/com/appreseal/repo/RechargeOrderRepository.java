package com.appreseal.repo;

import com.appreseal.domain.RechargeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, String> {
    List<RechargeOrder> findByUserIdOrderByCreatedAtDesc(String userId);
}
