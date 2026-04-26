package org.grimmory.repository;

import org.grimmory.model.entity.TaskCronConfigurationEntity;
import org.grimmory.model.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskCronConfigurationRepository extends JpaRepository<TaskCronConfigurationEntity, Long> {

    Optional<TaskCronConfigurationEntity> findByTaskType(TaskType taskType);

    List<TaskCronConfigurationEntity> findByEnabledTrue();
}

