package com.kousenit.openai;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kousenit.openai.json.ChatRequest;
import com.kousenit.openai.json.ChatResponse;
import com.kousenit.openai.json.Message;
import com.kousenit.openai.json.ModelList;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;

public class ChatGPT {
    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODELS_URL = "https://api.openai.com/v1/models";

    public final static String GPT_35_TURBO = "gpt-3.5-turbo";
    public final static String GPT_4 = "gpt-4";

    private final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final HttpClient client = HttpClient.newHttpClient();

    public List<ModelList.Model> listModels() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MODELS_URL))
                .header("Authorization",
                        "Bearer %s".formatted(System.getenv("OPENAI_API_KEY")))
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            ModelList modelList = gson.fromJson(response.body(), ModelList.class);
            return modelList.data().stream()
                    .sorted(Comparator.comparing(ModelList.Model::created).reversed())
                    .toList();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getResponse(String prompt, String model) {
        ChatRequest chatRequest = createChatRequest(prompt, model);
        ChatResponse chatResponse = createChatResponse(chatRequest);
        return chatResponse.choices().get(0).message().content();
    }

    public ChatRequest createChatRequest(String prompt, String model) {
        return new ChatRequest(model,
                List.of(new Message(Role.USER, prompt)),
                0.7);
    }

    public ChatResponse createChatResponse(ChatRequest chatRequest) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_URL))
                .header("Authorization",
                        "Bearer %s".formatted(System.getenv("OPENAI_API_KEY")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(chatRequest)))
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.statusCode() + ": " + response.body());
            return gson.fromJson(response.body(), ChatResponse.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}