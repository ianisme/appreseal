package com.appreseal.repo;

import com.appreseal.domain.AppItem;
import com.appreseal.domain.AppStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppRepository extends JpaRepository<AppItem, String> {

    Optional<AppItem> findByBundleId(String bundleId);

    List<AppItem> findByStatusOrderByUpdatedAtDesc(AppStatus status);

    @Query("""
            select a from AppItem a
            where a.status = com.appreseal.domain.AppStatus.ON_SALE
              and (lower(a.name) like lower(concat('%', :q, '%'))
                or lower(a.bundleId) like lower(concat('%', :q, '%'))
                or lower(a.developer) like lower(concat('%', :q, '%')))
            order by a.updatedAt desc
            """)
    List<AppItem> searchOnSale(@Param("q") String q);

    List<AppItem> findByStatusOrderByDownloadsDesc(AppStatus status);
}
