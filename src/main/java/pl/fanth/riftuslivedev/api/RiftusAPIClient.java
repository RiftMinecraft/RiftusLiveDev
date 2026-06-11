package pl.fanth.riftuslivedev.api;

import com.google.gson.Gson;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import pl.fanth.riftuslivedev.RiftusLiveDev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RiftusAPIClient {
    private final String liveKey;
    private final OkHttpClient client;
    private static final Gson GSON = new Gson();

    public RiftusAPIClient(String liveKey) {
        this.liveKey = liveKey;
        this.client = new OkHttpClient.Builder()
            .addInterceptor(new Interceptor() {
                @Override
                public @NonNull Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
                    Request request = chain.request();
                    Request newRequest;

                    newRequest = request.newBuilder()
                        .addHeader("x-live-key", liveKey)
                        .build();
                    return chain.proceed(newRequest);
                }
            })
            .build();
    }

    /**
     * Gets project info
     */
    @Blocking
    public ProjectInfo getProjectInfo() {
        Request request = new Request.Builder()
            .url(getBaseUrl() + "/live/info")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body().string();
                throw new ApiException(response.code(), errorBody);
            }

            String responseBody = response.body().string();
            return GSON.fromJson(responseBody, ProjectInfo.class);
        } catch (IOException | ApiException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetches the artifact
     */
    @Blocking
    public void downloadArtifact(Path path) {
        Request request = new Request.Builder()
            .url(getBaseUrl() + "/live/artifact")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body().string();
                throw new ApiException(response.code(), errorBody);
            }

            Files.createDirectories(path.getParent());
            Files.deleteIfExists(path);
            Files.copy(response.body().byteStream(), path);
        } catch (IOException | ApiException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getBaseUrl() {
        if (!RiftusLiveDev.instance().pluginConfiguration().dev) {
            return "https://riftus-api.fanth.pl";
        } else {
            return "http://localhost:3000";
        }
    }

    public record ProjectInfo(
        String id,
        String name,
        boolean hasArtifact
    ) {
    }
}
