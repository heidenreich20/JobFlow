package com.pablo.jobflow.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    boolean existsByProjectId(Long projectId);
    void deleteByProjectId(Long projectId);
    Page<Task> findByProjectId(Long projectId, Pageable pageable);
}