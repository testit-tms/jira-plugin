package testIT.jira.tms;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TmsTestPlanUrlHelper {
    private static final Pattern TEST_PLAN_PATH = Pattern.compile(
            "/projects/(\\d+)/test-plans/(\\d+)(?:/tests)?/?$",
            Pattern.CASE_INSENSITIVE);

    private TmsTestPlanUrlHelper() {
    }

    public static ParsedTestPlanUrl parse(String rawUrl, String configuredTmsBaseUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Test plan URL is required");
        }
        String trimmed = rawUrl.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid test plan URL");
        }
        if (uri.getHost() == null || uri.getPath() == null) {
            throw new IllegalArgumentException("Invalid test plan URL");
        }
        validateHost(uri, configuredTmsBaseUrl);

        Matcher matcher = TEST_PLAN_PATH.matcher(uri.getPath());
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "URL must point to a Test IT test plan: .../projects/{id}/test-plans/{id}[/tests]");
        }
        long projectGlobalId = Long.parseLong(matcher.group(1));
        long testPlanGlobalId = Long.parseLong(matcher.group(2));
        String canonicalUrl = TmsUrlHelper.testPlanUrl(configuredTmsBaseUrl, projectGlobalId, testPlanGlobalId);
        return new ParsedTestPlanUrl(projectGlobalId, testPlanGlobalId, canonicalUrl, trimmed);
    }

    public static boolean matchesTestPlanUrl(String url, String configuredTmsBaseUrl) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        try {
            parse(url, configuredTmsBaseUrl);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String normalizeLinkUrl(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void validateHost(URI uri, String configuredTmsBaseUrl) {
        String configuredHost = hostFromBase(configuredTmsBaseUrl);
        String urlHost = uri.getHost();
        if (configuredHost == null || urlHost == null) {
            throw new IllegalArgumentException("Test plan URL host does not match configured Test IT URL");
        }
        if (!configuredHost.equalsIgnoreCase(urlHost)) {
            throw new IllegalArgumentException("Test plan URL host does not match configured Test IT URL");
        }
    }

    private static String hostFromBase(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return null;
        }
        try {
            URI base = new URI(TmsHttpClient.normalizeBaseUrl(baseUrl));
            return base.getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static final class ParsedTestPlanUrl {
        public final long projectGlobalId;
        public final long testPlanGlobalId;
        public final String canonicalUrl;
        public final String originalUrl;

        ParsedTestPlanUrl(long projectGlobalId, long testPlanGlobalId, String canonicalUrl, String originalUrl) {
            this.projectGlobalId = projectGlobalId;
            this.testPlanGlobalId = testPlanGlobalId;
            this.canonicalUrl = canonicalUrl;
            this.originalUrl = originalUrl;
        }
    }
}
