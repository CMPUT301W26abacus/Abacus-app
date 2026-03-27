package com.example.abacus_app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CommentRepositoryTest {

    private static final String EVENT_ID = "event-test-comments-to-db";

    @Test
    public void addCommentTest() {

        CommentRepository repo = new CommentRepository();

        String userId = "fakeuser1";
        String content = "This is a great event!";

        repo.addComment(EVENT_ID, userId, content, new CommentRepository.VoidCallback() {
            @Override
            public void onComplete(Exception error) {
                // do nothing
            }
        });
    }
}
