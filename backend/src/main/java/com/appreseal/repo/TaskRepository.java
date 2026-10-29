package com.appreseal.repo;

import com.appreseal.domain.Task;
import com.appreseal.domain.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, String> {

    List<Task> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Task> findAllByOrderByCreatedAtDesc();

    List<Task> findByStatusOrderByCreatedAtAsc(TaskStatus status);

    List<Task> findByStatusInOrderByCreatedAtAsc(Collection<TaskStatus> statuses);

    long countByStatus(TaskStatus status);

    long countByStatusIn(Collection<TaskStatus> statuses);

    long countByUserId(String userId);

    long countByCreatedAtAfter(Instant since);

    List<Task> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since);

    List<Task> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(String userId, Instant since, Pageable pageable);

    List<Task> findByStatusInAndFinishedAtBefore(Collection<TaskStatus> statuses, Instant before);

    List<Task> findByFinishedAtBefore(Instant before);

    boolean existsByArtifactPath(String artifactPath);

    /** 取消、成功、失败共用这把行锁，避免刚好完成时被取消覆盖，或反过来。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> lockById(@Param("id") String id);
}
