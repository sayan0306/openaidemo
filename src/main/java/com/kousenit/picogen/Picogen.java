package com.kousenit.picogen;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kousenit.picogen.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// See docs at https://picogen.io/docs
@SuppressWarnings("unused")
public class Picogen {
    private static final Logger logger = LoggerFactory.getLogger(Picogen.class);
    private static final String BASEURL = "https://api.picogen.io";
    private static final String COMPLETED_STATUS = "completed";
    private static final int SLEEP_TIME_MILLIS = 5000;

    private final HttpClient client = HttpClient.newHttpClient();

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public JobResponse doStabilityJob(StabilityRequest stabilityRequest) {
        return doJob(stabilityRequest);
    }

    public JobResponse doMidjourneyJob(MidjourneyRequest midjourneyRequest) {
        return doJob(midjourneyRequest);
    }

    private <T> JobResponse doJob(T requestObject) {
        String json = gson.toJson(requestObject);
        logger.info("Request: " + json);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASEURL + "/job/run"))
                .header("API-Token", System.getenv("PICOGEN_API_KEY"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String string = response.body();
            logger.info("Server response: " + string); // Keep this line for debugging
            List<JobResponse> jobResponses = gson.fromJson(string,
                    new TypeToken<List<JobResponse>>(){}.getType());
            JobResponse jobResponse = jobResponses.get(1);
            System.out.printf("ID: %s, cost: %d%n", jobResponse.id(), jobResponse.cost());
            return jobResponse;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getJobList() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASEURL + "/job/list/"))
                .header("API-Token", System.getenv("PICOGEN_API_KEY"))
                .header("Content-Type", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            String string = response.body();
            List<ListResponse> listResponses = gson.fromJson(string,
                    new TypeToken<List<ListResponse>>(){}.getType());
            return listResponses.stream()
                    .filter(Objects::nonNull)
                    .flatMap(listResponse -> listResponse.items().stream())
                    .filter(items -> items.status().equals("completed"))
                    .map(item -> item.result().get(0))
                    .collect(Collectors.toList());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Item getResponse(JobResponse jobResponse) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASEURL + "/job/get/" + jobResponse.id()))
                .header("API-Token", System.getenv("PICOGEN_API_KEY"))
                .header("Content-Type", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            String string = response.body();
            logger.info("Server response: " + string); // Keep this line for debugging

            // Parse the response as a List of Item (need the type for parsing)
            @SuppressWarnings("Convert2Diamond")
            List<Item> getRespons = gson.fromJson(string, new TypeToken<List<Item>>() {});

            // Get the first non-null response
            return getRespons.stream()
                    .filter(Objects::nonNull)
                    .findFirst().orElseThrow(() -> new RuntimeException("No valid response"));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Item waitForResponseCompletion(JobResponse jobResponse) {
        Item response = getResponse(jobResponse);
        if (response.status().equals("error")) {
            throw new RuntimeException("Error: " + response);
        }
        while (!response.status().equals(COMPLETED_STATUS)) {
            // logger.info(response.status() + "...");
            sleepInterruptibly();
            response = getResponse(jobResponse);
        }
        logger.info("Completed in " + (response.durationMs() / 1000) + " seconds");
        return response;
    }

    private void sleepInterruptibly() {
        try {
            Thread.sleep(SLEEP_TIME_MILLIS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public void midjourneyRequest(String prompt) {
        MidjourneyRequest jobRequest = RequestFactory.createMidjourneyJobRequest(
                prompt,
                "mj-5.2");
        JobResponse jobResponse = doMidjourneyJob(jobRequest);
        Item response = waitForResponseCompletion(jobResponse);
        response.result().forEach(System.out::println);
        long count = saveImagesToFiles(response.result());
        System.out.println("Saved " + count + " images to src/main/resources/images");
    }

    public void stabilityRequest(String prompt) {
        StabilityRequest jobRequest = RequestFactory.createStabilityJobRequest(
                prompt,
                "xl-v1.0");
        JobResponse jobResponse = doStabilityJob(jobRequest);
        Item response = waitForResponseCompletion(jobResponse);
        response.result().forEach(System.out::println);
        long count = saveImagesToFiles(response.result());
        System.out.println("Saved " + count + " images to src/main/resources/images");
    }

    private long saveImagesToFiles(List<String> urls) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        AtomicInteger count = new AtomicInteger(0);
        return urls.stream()
                .map(this::getImageBytes)
                .map(bytes -> {
                    String fileName = String.format("image_%s_%d.png", timestamp, count.getAndIncrement());
                    Path directory = Paths.get("src/main/resources/images");
                    Path filePath = directory.resolve(fileName);
                    writeFile(bytes, filePath);
                    return filePath;
                })
                .count();
    }

    private byte[] getImageBytes(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeFile(byte[] bytes, Path filePath) {
        try {
            Files.write(filePath, bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Picogen picogen = new Picogen();
        String prompt = """
                A realistic photo of a
                robot leaping into the air
                in joy after accomplishing a
                difficult task successfully.""";
        picogen.midjourneyRequest(prompt);
        // picogen.stabilityRequest(prompt);
    }
}
