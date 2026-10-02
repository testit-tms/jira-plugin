package testIT.jira.tms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TmsIssueContextService {
    private static final Logger log = LoggerFactory.getLogger(TmsIssueContextService.class);
    private static final int WORK_ITEM_PAGE_SIZE = 100;
    private static final int MAX_WORK_ITEM_PAGES = 2;
    private static final int TEST_RESULTS_HISTORY_TAKE = 20;

    private final TmsAdaptersClient client = new TmsAdaptersClient();

    public TmsIssueContextDto loadIssueContext(
            String tmsBaseUrl,
            String token,
            String issueKey,
            String issueUrl,
            String issueSummary)
            throws TmsClientException, IOException, URISyntaxException {
        TmsIssueContextDto context = new TmsIssueContextDto(issueKey);
        Map<String, Long> projectGlobalIdCache = new HashMap<>();
        List<JsonObject> linked = findLinkedWorkItems(tmsBaseUrl, token, issueKey, issueUrl);
        ensureIssueLinkTitles(tmsBaseUrl, token, linked, issueKey, issueUrl, issueSummary);
        for (JsonObject item : linked) {
            TmsWorkItemDto workItem = toWorkItemDto(tmsBaseUrl, token, item, projectGlobalIdCache);
            try {
                workItem.results = loadResultsForWorkItem(tmsBaseUrl, token, workItem, projectGlobalIdCache);
            } catch (TmsClientException | IOException | URISyntaxException e) {
                log.warn("TMS work item history failed for globalId={}: {}",
                        workItem.globalId, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                workItem.results = new ArrayList<>();
            }
            context.testCases.add(workItem);
        }
        return context;
    }

    public List<JsonObject> findLinkedWorkItems(String tmsBaseUrl, String token, String issueKey, String issueUrl)
            throws TmsClientException, IOException, URISyntaxException {
        List<JsonObject> matched = new ArrayList<>();
        Map<String, JsonObject> seen = new HashMap<>();
        String issueKeyUpper = issueKey != null ? issueKey.toUpperCase(Locale.ROOT) : "";
        String browseSuffix = "/browse/" + issueKeyUpper;

        for (int page = 0; page < MAX_WORK_ITEM_PAGES; page++) {
            int skip = page * WORK_ITEM_PAGE_SIZE;
            TmsPage<JsonObject> pageResult = client.searchWorkItems(
                    tmsBaseUrl, token, skip, WORK_ITEM_PAGE_SIZE, issueKey);
            boolean pageHadMatch = false;
            for (JsonObject item : pageResult.items) {
                if (!isLinkedToIssue(item, issueUrl, browseSuffix)) {
                    continue;
                }
                String id = TmsJson.getString(item, "id");
                if (id != null && !seen.containsKey(id)) {
                    seen.put(id, item);
                    matched.add(item);
                    pageHadMatch = true;
                }
            }
            if (pageHadMatch && pageResult.items.size() < WORK_ITEM_PAGE_SIZE) {
                break;
            }
            if (pageResult.items.size() < WORK_ITEM_PAGE_SIZE) {
                break;
            }
            if (pageResult.totalItems > 0 && skip + pageResult.items.size() >= pageResult.totalItems) {
                break;
            }
        }
        return matched;
    }

    private boolean isLinkedToIssue(JsonObject item, String issueUrl, String browseSuffix) {
        if (item == null || !item.has("links") || !item.get("links").isJsonArray()) {
            return false;
        }
        String normalizedIssueUrl = normalizeLinkUrl(issueUrl);
        for (JsonElement linkElement : item.getAsJsonArray("links")) {
            if (!linkElement.isJsonObject()) {
                continue;
            }
            JsonObject link = linkElement.getAsJsonObject();
            String type = TmsJson.getString(link, "type");
            String url = TmsJson.getString(link, "url");
            if (url == null) {
                continue;
            }
            String normalizedLinkUrl = normalizeLinkUrl(url);
            if ("Issue".equalsIgnoreCase(type)
                    && (normalizedLinkUrl.contains(browseSuffix)
                    || (normalizedIssueUrl != null && normalizedLinkUrl.equalsIgnoreCase(normalizedIssueUrl)))) {
                return true;
            }
        }
        return false;
    }

    private void ensureIssueLinkTitles(
            String tmsBaseUrl,
            String token,
            List<JsonObject> linked,
            String issueKey,
            String issueUrl,
            String issueSummary) {
        if (linked == null || linked.isEmpty() || issueKey == null || issueKey.trim().isEmpty()) {
            return;
        }
        String browseSuffix = "/browse/" + issueKey.trim().toUpperCase(Locale.ROOT);
        for (JsonObject item : linked) {
            if (!needsIssueLinkTitle(item, issueUrl, browseSuffix)) {
                continue;
            }
            String workItemId = TmsJson.getString(item, "id");
            if (workItemId == null || workItemId.trim().isEmpty()) {
                continue;
            }
            try {
                JsonObject workItem = client.getWorkItem(tmsBaseUrl, token, workItemId.trim());
                if (workItem == null || workItem.entrySet().isEmpty()) {
                    continue;
                }
                String title = resolveIssueLinkTitle(workItem, issueKey.trim(), issueSummary);
                if (!fillMissingIssueLinkTitles(workItem, issueUrl, browseSuffix, title)) {
                    continue;
                }
                client.updateWorkItem(tmsBaseUrl, token, TmsWorkItemUnlinkService.buildUpdatePayload(workItem));
                log.info("Ensured Issue link title for work item {} on issue {}", workItemId, issueKey);
            } catch (Exception e) {
                log.warn("Failed to ensure Issue link title for work item {} on issue {}: {}",
                        workItemId, issueKey, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            }
        }
    }

    private static boolean needsIssueLinkTitle(JsonObject item, String issueUrl, String browseSuffix) {
        if (item == null || !item.has("links") || !item.get("links").isJsonArray()) {
            return false;
        }
        String normalizedIssueUrl = normalizeLinkUrl(issueUrl);
        for (JsonElement linkElement : item.getAsJsonArray("links")) {
            if (!linkElement.isJsonObject()) {
                continue;
            }
            JsonObject link = linkElement.getAsJsonObject();
            if (!isMatchingIssueLink(link, browseSuffix, normalizedIssueUrl)) {
                continue;
            }
            if (isMissingLinkTitle(link)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fillMissingIssueLinkTitles(
            JsonObject workItem, String issueUrl, String browseSuffix, String title) {
        if (workItem == null || !workItem.has("links") || !workItem.get("links").isJsonArray()
                || title == null || title.trim().isEmpty()) {
            return false;
        }
        String normalizedIssueUrl = normalizeLinkUrl(issueUrl);
        boolean changed = false;
        for (JsonElement linkElement : workItem.getAsJsonArray("links")) {
            if (!linkElement.isJsonObject()) {
                continue;
            }
            JsonObject link = linkElement.getAsJsonObject();
            if (!isMatchingIssueLink(link, browseSuffix, normalizedIssueUrl)) {
                continue;
            }
            if (isMissingLinkTitle(link)) {
                link.addProperty("title", title.trim());
                if (link.has("Title")) {
                    link.remove("Title");
                }
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isMatchingIssueLink(JsonObject link, String browseSuffix, String normalizedIssueUrl) {
        String type = TmsJson.getString(link, "type");
        String url = TmsJson.getString(link, "url");
        if (!"Issue".equalsIgnoreCase(type) || url == null) {
            return false;
        }
        String normalizedLinkUrl = normalizeLinkUrl(url);
        return normalizedLinkUrl.contains(browseSuffix)
                || (normalizedIssueUrl != null && normalizedLinkUrl.equalsIgnoreCase(normalizedIssueUrl));
    }

    private static String resolveIssueLinkTitle(JsonObject workItem, String issueKey, String issueSummary) {
        String name = TmsJson.getString(workItem, "name");
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        String summary = issueSummary != null ? issueSummary.trim() : "";
        if (summary.isEmpty()) {
            return issueKey;
        }
        return issueKey + ": " + summary;
    }

    private static boolean isMissingLinkTitle(JsonObject link) {
        String title = readLinkTitle(link);
        if (title == null || title.trim().isEmpty()) {
            return true;
        }
        String url = TmsJson.getString(link, "url");
        return url != null && title.trim().equalsIgnoreCase(url.trim());
    }

    private static String readLinkTitle(JsonObject link) {
        String title = TmsJson.getString(link, "title");
        if (title != null) {
            return title;
        }
        return TmsJson.getString(link, "Title");
    }

    private static String normalizeLinkUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            trimmed = trimmed.substring(8);
        } else if (trimmed.regionMatches(true, 0, "http://", 0, 7)) {
            trimmed = trimmed.substring(7);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private TmsWorkItemDto toWorkItemDto(String tmsBaseUrl, String token, JsonObject item,
            Map<String, Long> projectGlobalIdCache) {
        String id = TmsJson.getString(item, "id");
        Long globalId = TmsJson.getLong(item, "globalId");
        String name = TmsJson.getString(item, "name");
        String projectId = TmsJson.getString(item, "projectId");
        Long projectGlobalId = resolveProjectGlobalId(tmsBaseUrl, token, projectId, projectGlobalIdCache);
        String url = TmsUrlHelper.workItemUrl(tmsBaseUrl, projectGlobalId, globalId);
        return new TmsWorkItemDto(id, globalId, name, projectId, url);
    }

    private Long resolveProjectGlobalId(String tmsBaseUrl, String token, String projectUuid,
            Map<String, Long> cache) {
        if (projectUuid == null || projectUuid.trim().isEmpty()) {
            return null;
        }
        String key = projectUuid.trim();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        try {
            Long globalId = client.getProjectGlobalId(tmsBaseUrl, token, key);
            cache.put(key, globalId);
            return globalId;
        } catch (TmsClientException | IOException | URISyntaxException e) {
            cache.put(key, null);
            return null;
        }
    }

    private List<TmsTestResultDto> loadResultsForWorkItem(String tmsBaseUrl, String token, TmsWorkItemDto workItem,
            Map<String, Long> projectGlobalIdCache)
            throws TmsClientException, IOException, URISyntaxException {
        if (workItem.globalId == null && (workItem.id == null || workItem.id.trim().isEmpty())) {
            return new ArrayList<>();
        }
        TmsAdaptersClient.WorkItemHistoryResult history = client.getWorkItemTestResultsHistory(
                tmsBaseUrl, token, workItem.globalId, workItem.id, 0, TEST_RESULTS_HISTORY_TAKE);
        if (history.items.isEmpty()) {
            log.warn("TMS work item history empty for historyId={}, globalId={}, lastStatus={}, bodyLength={}",
                    workItem.id, workItem.globalId, history.lastStatusCode, history.lastBodyLength);
        }
        List<TmsTestResultDto> results = new ArrayList<>();
        for (JsonObject raw : history.items) {
            if (!history.manualFilteredByApi && TmsJson.getBoolean(raw, "isAutomated")) {
                continue;
            }
            results.add(mapTestResult(raw, workItem, tmsBaseUrl, token, projectGlobalIdCache));
        }
        return results;
    }

    private TmsTestResultDto mapTestResult(JsonObject raw, TmsWorkItemDto workItem, String tmsBaseUrl, String token,
            Map<String, Long> projectGlobalIdCache) {
        TmsTestResultDto result = new TmsTestResultDto();
        result.id = TmsJson.getString(raw, "id");
        result.testPlanGlobalId = TmsJson.getLong(raw, "testPlanGlobalId");
        result.testPlanId = TmsJson.getString(raw, "testPlanId");
        result.date = TmsJson.extractResultDate(raw);
        if (result.date == null) {
            result.date = "—";
        }
        String outcome = TmsJson.getString(raw, "outcome");
        JsonObject status = raw.has("status") && raw.get("status").isJsonObject()
                ? raw.getAsJsonObject("status") : null;
        result.statusType = status != null ? TmsJson.getString(status, "type") : outcome;
        result.statusLabel = mapStatusLabel(outcome, result.statusType,
                status != null ? TmsJson.getString(status, "code") : null);
        result.testPlanName = resolveTestPlanName(result, raw);
        if (result.testPlanGlobalId != null) {
            Long projectGlobalId = resolveProjectGlobalId(tmsBaseUrl, token, workItem.projectId, projectGlobalIdCache);
            result.testPlanUrl = TmsUrlHelper.testPlanUrl(tmsBaseUrl, projectGlobalId, result.testPlanGlobalId);
        }
        return result;
    }

    private static String resolveTestPlanName(TmsTestResultDto result, JsonObject raw) {
        String testPlanName = TmsJson.getString(raw, "testPlanName");
        if (testPlanName != null && !testPlanName.isEmpty()) {
            return testPlanName;
        }
        if (result.testPlanGlobalId != null) {
            return "TP " + result.testPlanGlobalId;
        }
        if (result.testPlanId != null && !result.testPlanId.isEmpty()) {
            return result.testPlanId;
        }
        return null;
    }

    static String mapStatusLabel(String outcome, String statusType, String statusCode) {
        if (outcome != null && !outcome.isEmpty()) {
            if ("Passed".equalsIgnoreCase(outcome)) {
                return "PASSED";
            }
            if ("Failed".equalsIgnoreCase(outcome)) {
                return "FAILED";
            }
            return outcome.toUpperCase(Locale.ROOT);
        }
        if (statusType != null) {
            if ("Succeeded".equalsIgnoreCase(statusType) || "Passed".equalsIgnoreCase(statusType)) {
                return "PASSED";
            }
            if ("Failed".equalsIgnoreCase(statusType)) {
                return "FAILED";
            }
        }
        if (statusCode != null && !statusCode.isEmpty()) {
            String upperCode = statusCode.toUpperCase(Locale.ROOT);
            if (upperCode.contains("SUCCESS") || upperCode.contains("PASSED")
                    || upperCode.contains("УСПЕШ") || upperCode.contains("SUCCEEDED")) {
                return "PASSED";
            }
            if (upperCode.contains("FAIL") || upperCode.contains("НЕУД")) {
                return "FAILED";
            }
            return upperCode;
        }
        return statusType != null ? statusType.toUpperCase(Locale.ROOT) : "UNKNOWN";
    }
}
