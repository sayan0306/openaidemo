package com.kousenit.openai.quiz;

import com.kousenit.openai.ChatGPT;
import com.kousenit.openai.Role;
import com.kousenit.openai.json.ChatRequest;
import com.kousenit.openai.json.ChatResponse;
import com.kousenit.openai.json.Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class GenerateQuiz {
    private final ChatGPT chat = new ChatGPT();

    private final Message systemMessage = new Message(Role.SYSTEM, """
            Create a multiple-choice quiz about the Java programming langauge topic in the
            next message. The quiz should have between 3 and 6 questions, each
            with four possible answers, with only one correct answer per question.
            Label the answers A through D, and identify which answer is correct.
                            
            After each question, add a section labeled [Rationales] that explains
            each of the potential answers, again labeled A through D to identify which
            rationale goes with which answer.
            """);

    void createQuizWithMultipleTopics() {
        List.of("Abstract Classes",
                        "Exception Handling",
                        "Collections",
                        "Generic types",
                        "Implementing Interfaces",
                        "Static and default methods in interfaces",
                        "Overriding toString, equals, and hashCode",
                        "File Manipulation",
                        "Threads, Runnables, and the Executor Framework",
                        "Callables and Futures",
                        "Locks and Latches",
                        "The java.net Package",
                        "Working with URLs",
                        "Socket and Server Socket",
                        "Static and Inner Classes",
                        "Lambda Expressions",
                        "Streams",
                        "Method References",
                        "Concurrent Collections",
                        "Traditional JDBC Classes",
                        "The java.time Package")
                .parallelStream()
                .forEach(this::sendMessage);
    }

    private void sendMessage(String topic) {
        List<Message> allMessages = List.of(systemMessage, new Message(Role.USER, topic));
        ChatRequest request = new ChatRequest("gpt-3.5-turbo", allMessages, 0.7);
        ChatResponse response = chat.createChatResponse(request);
        System.out.printf("%s: %s (%s)%n", topic, response.usage(), Thread.currentThread().getName());
        try {
            Files.writeString(Path.of("build/resources/main/chat-%s.txt".formatted(topic)),
                    response.choices().get(0).message().content(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) {
        GenerateQuiz quiz = new GenerateQuiz();
        quiz.createQuizWithMultipleTopics();
    }
}
