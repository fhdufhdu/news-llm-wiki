package com.newswiki.infrastructure.http;

import com.newswiki.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Component
public class ArticleFetcher {
    private final AppProperties properties;
    private final HttpClient httpClient;

    public ArticleFetcher(AppProperties properties) {
        this.properties = properties;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(properties.articleFetchTimeoutSeconds()));
        if (properties.articleFetchInsecureSsl()) {
            builder.sslContext(insecureSslContext());
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");
            builder.sslParameters(sslParameters);
        }
        this.httpClient = builder.build();
    }

    public FetchedArticle fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.articleFetchTimeoutSeconds()))
                .header("User-Agent", "NewsWiki/0.1")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String contentType = response.headers().firstValue("content-type").orElse("text/html");
            return new FetchedArticle(response.statusCode(), contentType, response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch article " + url + ": " + describe(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching article " + url + ": " + describe(e), e);
        }
    }

    private String describe(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return cursor.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : " - " + message);
    }

    private SSLContext insecureSslContext() {
        try {
            TrustManager[] trustManagers = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create insecure SSL context", e);
        }
    }

    public record FetchedArticle(int statusCode, String contentType, String html) {
    }
}
