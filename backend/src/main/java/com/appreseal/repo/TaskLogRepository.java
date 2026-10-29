package com.appreseal.repo;

import com.appreseal.domain.TaskLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskLogRepository extends JpaRepository<TaskLog, Long> {

    List<TaskLog> findByTaskIdOrderBySeqAsc(String taskId);

    @Query("select coalesce(max(l.seq), 0) from TaskLog l where l.taskId = :taskId")
    int maxSeq(@Param("taskId") String taskId);

    @Modifying
    void deleteByTaskId(String taskId);
}
