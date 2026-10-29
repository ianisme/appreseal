package com.appreseal.repo;

import com.appreseal.domain.LibraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LibraryRepository extends JpaRepository<LibraryItem, String> {

    List<LibraryItem> findByUserIdOrderByAcquiredAtDesc(String userId);

    Optional<LibraryItem> findFirstByUserIdAndBundleIdOrderByAcquiredAtDesc(String userId, String bundleId);

    boolean existsByUserIdAndBundleId(String userId, String bundleId);

    List<LibraryItem> findByBundleId(String bundleId);

    List<LibraryItem> findByTaskId(String taskId);

    boolean existsByArtifactPath(String artifactPath);
}
