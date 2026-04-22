package org.booklore.repository;

import org.booklore.model.entity.TaskCronConfigurationEntity;
import org.booklore.model.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskCronConfigurationRepository extends JpaRepository<TaskCronConfigurationEntity, Long> {

    Optional<TaskCronConfigurationEntity> findByTaskType(TaskType taskType);

    List<TaskCronConfigurationEntity> findByEnabledTrue();
}

