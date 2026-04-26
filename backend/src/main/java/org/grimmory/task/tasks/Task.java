package org.grimmory.task.tasks;

import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.request.TaskCreateRequest;
import org.grimmory.model.dto.response.TaskCreateResponse;
import org.grimmory.model.enums.TaskType;

public interface Task {

    TaskCreateResponse execute(TaskCreateRequest request);

    TaskType getTaskType();

    default String getMetadata() {
        return null;
    }

    void validatePermissions(BookLoreUser user, TaskCreateRequest request);
}
