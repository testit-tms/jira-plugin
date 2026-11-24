package testIT.jira;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import testIT.jira.model.IssueModel;
import testIT.jira.tms.TmsIssueContextService;
import testIT.jira.tms.TmsWorkItemUnlinkService;

@Component
public class IssueCreatedResolvedListener implements LifecycleAware, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(IssueCreatedResolvedListener.class);
    private static final String TEST_RESULT_FIELD = "testResultId";
    private static final Gson GSON = new Gson();

    private final PluginSettingsFactory pluginSettingsFactory;

    private final TmsIssueContextService issueContextService = new TmsIssueContextService();
    private final TmsWorkItemUnlinkService workItemUnlinkService = new TmsWorkItemUnlinkService();
    private volatile EventPublisher eventPublisher;
    private volatile boolean registered;

    @Inject
    public IssueCreatedResolvedListener(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public void onStart() {
        try {
            EventPublisher publisher = ComponentAccessor.getComponent(EventPublisher.class);
            if (publisher == null) {
                log.warn("EventPublisher is null; IssueCreatedResolvedListener not registered");
                return;
            }
            this.eventPublisher = publisher;
            publisher.register(this);
            registered = true;
        } catch (Exception e) {
            log.warn("Failed to register IssueCreatedResolvedListener; plugin stays enabled", e);
        }
    }

    @Override
    public void onStop() {
        unregisterQuietly();
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) throws IOException {
        Long eventTypeId = issueEvent.getEventTypeId();
        if (EventType.ISSUE_DELETED_ID.equals(eventTypeId)) {
            unlinkWorkItemsOnIssueDeleted(issueEvent.getIssue());
            return;
        }
        if (!EventType.ISSUE_CREATED_ID.equals(eventTypeId)) {
            return;
        }
        Issue issue = issueEvent.getIssue();
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
        CustomField testResultField = customFieldManager.getCustomFieldObjectByName(TEST_RESULT_FIELD);
        Object testResultId = issue.getCustomFieldValue(testResultField);
        if (testResultId == null) {
            return;
        }
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        String testItUrl = (String) settings.get(ConfigResource.Config.class.getName() + ".url");
        sendHttpRequestToTestIt(issue, testResultId.toString(), testItUrl);
    }

    private void unlinkWorkItemsOnIssueDeleted(Issue issue) {
        if (issue == null || issue.getKey() == null || issue.getKey().trim().isEmpty()) {
            return;
        }
        try {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            String url = (String) settings.get(ConfigResource.Config.class.getName() + ".url");
            String token = (String) settings.get(ConfigResource.Config.class.getName() + ".privateToken");
            if (url == null || url.trim().isEmpty() || token == null || token.trim().isEmpty()) {
                log.debug("Skip WI unlink on issue delete {}: Test IT url/token not configured", issue.getKey());
                return;
            }
            String issueKey = issue.getKey().trim();
            String jiraBaseUrl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl");
            String issueUrl = (jiraBaseUrl != null ? jiraBaseUrl : "") + "/browse/" + issueKey;
            List<JsonObject> linked = issueContextService.findLinkedWorkItems(
                    url.trim(), token.trim(), issueKey, issueUrl);
            for (JsonObject item : linked) {
                String workItemId = readWorkItemId(item);
                if (workItemId == null) {
                    continue;
                }
                try {
                    workItemUnlinkService.unlinkWorkItemFromIssue(
                            url.trim(), token.trim(), workItemId, issueKey, issueUrl);
                } catch (Exception e) {
                    log.warn("Failed to unlink TMS work item {} from deleted issue {}: {}",
                            workItemId, issueKey, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to unlink TMS work items for deleted issue {}: {}",
                    issue.getKey(), e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
    }

    private static String readWorkItemId(JsonObject item) {
        if (item == null || !item.has("id") || item.get("id").isJsonNull()) {
            return null;
        }
        try {
            return item.get("id").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private void sendHttpRequestToTestIt(Issue issue, String testResultId, String testItBaseUrl) throws IOException {
        if (issue == null || testItBaseUrl == null || testResultId == null) {
            return;
        }
        String requestUrl = buildUseLinkRequestUrl(testItBaseUrl, testResultId);
        String requestBody = buildIssuePayload(issue);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(requestUrl);
            request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = response.getEntity() != null
                        ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                        : "";
                if (statusCode == 200) {
                    log.debug("Linked Test IT test result {} to issue {}: {}", testResultId, issue.getKey(), responseBody);
                } else {
                    log.error("Failed to link Test IT test result {} to issue {}: status={}, body={}",
                            testResultId, issue.getKey(), statusCode, responseBody);
                }
            }
        }
    }

    private String buildUseLinkRequestUrl(String testItBaseUrl, String testResultId) {
        String normalizedBaseUrl = testItBaseUrl.endsWith("/") ? testItBaseUrl : testItBaseUrl + "/";
        return normalizedBaseUrl + "api/TestResults/linkRequests/" + testResultId + "/use";
    }

    private String buildIssuePayload(Issue issue) {
        String jiraBaseUrl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl");
        String issueTitle = issue.getKey() + ": " + issue.getSummary();
        String issueUrl = jiraBaseUrl + "/browse/" + issue.getKey();
        return GSON.toJson(new IssueModel(issueTitle, issueUrl));
    }

    @Override
    public void destroy() {
        unregisterQuietly();
    }

    private void unregisterQuietly() {
        EventPublisher publisher = eventPublisher;
        if (!registered || publisher == null) {
            return;
        }
        try {
            publisher.unregister(this);
        } catch (Exception e) {
            log.debug("EventPublisher unregister failed", e);
        } finally {
            registered = false;
        }
    }
}
