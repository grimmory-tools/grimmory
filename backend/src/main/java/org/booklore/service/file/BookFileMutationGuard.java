package org.booklore.service.file;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class BookFileMutationGuard {
    public interface Participant {
        void beforeMutation();
    }

    private final List<Participant> participants;

    public BookFileMutationGuard(List<Participant> participants) {
        this.participants = List.copyOf(participants);
    }

    public void beforeMutation() {
        participants.forEach(Participant::beforeMutation);
    }
}
