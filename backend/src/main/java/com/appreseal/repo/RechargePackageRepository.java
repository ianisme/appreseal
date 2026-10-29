package com.appreseal.repo;

import com.appreseal.domain.RechargePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RechargePackageRepository extends JpaRepository<RechargePackage, String> {
    List<RechargePackage> findAllByOrderBySortOrderAsc();
}
