package pl.fanth.riftuslivedev.api;

import pl.fanth.riftuslivedev.RiftusLiveDev;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class RiftusAPI {
    private static final String BASE_URL = "http://localhost:3000";

    public static void downloadArtifact() throws ApiException {
        Path artifactPath = RiftusLiveDev.instance().getArtifactPath();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE_URL + "/live/artifact"))
                .header("x-live-key", RiftusLiveDev.instance().pluginConfiguration().liveKey)
                .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = new String(response.body());
                throw new ApiException(response.statusCode(), errorBody);
            }

            Files.createDirectories(artifactPath.getParent());
            Files.write(artifactPath, response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
