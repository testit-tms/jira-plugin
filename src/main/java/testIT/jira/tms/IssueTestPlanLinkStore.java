package testIT.jira.tms;

import com.atlassian.jira.entity.property.EntityProperty;
import com.atlassian.jira.entity.property.JsonEntityPropertyManager;
import com.atlassian.jira.issue.Issue;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists linked test plan URLs on the Jira issue as an entity property (not Remote Issue Links).
 */
public class IssueTestPlanLinkStore {
    public static final String PROPERTY_KEY = "testit.testplans";
    public static final String ENTITY_NAME = "IssueProperty";

    private static final Logger log = LoggerFactory.getLogger(IssueTestPlanLinkStore.class);
    private static final Gson GSON = new Gson();

    private final JsonEntityPropertyManager propertyManager;

    public IssueTestPlanLinkStore(JsonEntityPropertyManager propertyManager) {
        this.propertyManager = propertyManager;
    }

    public List<String> getUrls(Issue issue) {
        if (issue == null || issue.getId() == null) {
            return new ArrayList<>();
        }
        EntityProperty property = propertyManager.get(ENTITY_NAME, issue.getId(), PROPERTY_KEY);
        if (property == null || property.getValue() == null) {
            log.debug("No {} property for issue {}", PROPERTY_KEY, issue.getKey());
            return new ArrayList<>();
        }
        List<String> urls = parseUrls(property.getValue());
        log.debug("Loaded {} test plan url(s) for issue {}", urls.size(), issue.getKey());
        return urls;
    }

    public void addUrl(Issue issue, String canonicalUrl) {
        if (canonicalUrl == null || canonicalUrl.trim().isEmpty()) {
            return;
        }
        if (issue == null || issue.getId() == null) {
            throw new IllegalStateException("Cannot save test plan links: issue is null");
        }
        String target = TmsTestPlanUrlHelper.normalizeLinkUrl(canonicalUrl);
        Set<String> urls = new LinkedHashSet<>(getUrls(issue));
        for (String existing : urls) {
            if (target.equalsIgnoreCase(TmsTestPlanUrlHelper.normalizeLinkUrl(existing))) {
                return;
            }
        }
        urls.add(canonicalUrl.trim());
        saveUrls(issue, urls);
    }

    public boolean removeUrl(Issue issue, String urlToRemove) {
        if (urlToRemove == null || urlToRemove.trim().isEmpty()) {
            return false;
        }
        if (issue == null || issue.getId() == null) {
            return false;
        }
        String target = TmsTestPlanUrlHelper.normalizeLinkUrl(urlToRemove);
        List<String> current = getUrls(issue);
        Set<String> remaining = new LinkedHashSet<>();
        boolean removed = false;
        for (String existing : current) {
            if (target.equalsIgnoreCase(TmsTestPlanUrlHelper.normalizeLinkUrl(existing))) {
                removed = true;
                continue;
            }
            remaining.add(existing);
        }
        if (removed) {
            saveUrls(issue, remaining);
        }
        return removed;
    }

    private void saveUrls(Issue issue, Set<String> urls) {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (String url : urls) {
            array.add(url);
        }
        root.add("urls", array);
        String json = GSON.toJson(root);
        try {
            propertyManager.put(ENTITY_NAME, issue.getId(), PROPERTY_KEY, json);
            log.info("Saved {} test plan url(s) for issue {}", urls.size(), issue.getKey());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot save test plan links: " + e.getMessage(), e);
        }
    }

    private static List<String> parseUrls(String json) {
        List<String> urls = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return urls;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return urls;
            }
            JsonObject object = root.getAsJsonObject();
            if (!object.has("urls") || !object.get("urls").isJsonArray()) {
                return urls;
            }
            for (JsonElement element : object.getAsJsonArray("urls")) {
                if (element != null && element.isJsonPrimitive()) {
                    String url = element.getAsString();
                    if (url != null && !url.trim().isEmpty()) {
                        urls.add(url.trim());
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Failed to parse {} property: {}", PROPERTY_KEY, e.getMessage());
        }
        return urls;
    }
}
