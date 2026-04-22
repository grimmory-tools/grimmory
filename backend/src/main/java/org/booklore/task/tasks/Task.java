package org.booklore.task.tasks;

import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;

public interface Task {

    TaskCreateResponse execute(TaskCreateRequest request);

    TaskType getTaskType();

    default String getMetadata() {
        return null;
    }

    void validatePermissions(BookLoreUser user, TaskCreateRequest request);
}
