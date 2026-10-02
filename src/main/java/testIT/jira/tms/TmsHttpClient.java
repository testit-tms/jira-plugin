package testIT.jira.tms;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

final class TmsHttpClient {
    static final int CONNECT_TIMEOUT_MS = 10_000;
    static final int SOCKET_TIMEOUT_MS = 30_000;

    private TmsHttpClient() {
    }

    static HttpResponseData executeGet(URI uri, String token, String errorPrefix)
            throws TmsClientException, IOException {
        HttpGet request = new HttpGet(uri);
        request.setHeader("Authorization", authorizationHeader(token));
        request.setHeader("Accept", "application/json");
        return executeRequest(request, errorPrefix);
    }

    static HttpResponseData executePost(URI uri, String token, String jsonBody, String errorPrefix)
            throws TmsClientException, IOException {
        HttpPost request = new HttpPost(uri);
        request.setHeader("Authorization", authorizationHeader(token));
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(jsonBody != null ? jsonBody : "{}", ContentType.APPLICATION_JSON));
        return executeRequest(request, errorPrefix);
    }

    static HttpResponseData executePut(URI uri, String token, String jsonBody, String errorPrefix)
            throws TmsClientException, IOException {
        HttpPut request = new HttpPut(uri);
        request.setHeader("Authorization", authorizationHeader(token));
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(jsonBody != null ? jsonBody : "{}", ContentType.APPLICATION_JSON));
        return executeRequest(request, errorPrefix);
    }

    static HttpResponseData executeGetWithoutError(URI uri, String token) throws IOException {
        RequestConfig requestConfig = requestConfig();
        HttpGet request = new HttpGet(uri);
        request.setConfig(requestConfig);
        request.setHeader("Authorization", authorizationHeader(token));
        request.setHeader("Accept", "application/json");
        try (CloseableHttpClient client = httpClient(requestConfig);
                CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() != null
                    ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                    : "";
            return new HttpResponseData(status, body, 0, 0, 0, null);
        }
    }

    private static HttpResponseData executeRequest(HttpRequestBase request, String errorPrefix)
            throws TmsClientException, IOException {
        RequestConfig requestConfig = requestConfig();
        request.setConfig(requestConfig);
        try (CloseableHttpClient client = httpClient(requestConfig);
                CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() != null
                    ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                    : "";
            if (status < 200 || status >= 300) {
                throw new TmsClientException(status, truncate(errorPrefix + ": " + body));
            }
            return new HttpResponseData(status, body,
                    parseIntHeader(response, "Pagination-Skip", 0),
                    parseIntHeader(response, "Pagination-Take", 0),
                    parseIntHeader(response, "Pagination-Total-Items", 0),
                    null);
        }
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 800 ? message.substring(0, 800) + "..." : message;
    }

    private static RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();
    }

    private static CloseableHttpClient httpClient(RequestConfig requestConfig) {
        return HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
    }

    private static String authorizationHeader(String token) {
        String trimmedToken = token == null ? "" : token.trim();
        if (trimmedToken.regionMatches(true, 0, "PrivateToken ", 0, "PrivateToken ".length())
                || trimmedToken.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return trimmedToken;
        }
        return "PrivateToken " + trimmedToken;
    }

    private static int parseIntHeader(CloseableHttpResponse response, String name, int defaultValue) {
        Header header = response.getFirstHeader(name);
        if (header == null || header.getValue() == null || header.getValue().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(header.getValue().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static final class HttpResponseData {
        final int statusCode;
        final String body;
        final int skip;
        final int take;
        final int totalItems;
        final TmsClientException error;

        HttpResponseData(int statusCode, String body, int skip, int take, int totalItems, TmsClientException error) {
            this.statusCode = statusCode;
            this.body = body;
            this.skip = skip;
            this.take = take;
            this.totalItems = totalItems;
            this.error = error;
        }
    }
}
