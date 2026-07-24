package testIT.jira.tms;

public final class TmsUrlHelper {
    private TmsUrlHelper() {
    }

    public static String workItemUrl(String tmsBase, Long projectGlobalId, Long workItemGlobalId) {
        if (projectGlobalId == null || workItemGlobalId == null) {
            return null;
        }
        return normalizeBase(tmsBase) + "/projects/" + projectGlobalId + "/tests/" + workItemGlobalId;
    }

    public static String testRunUrl(String tmsBase, Long projectGlobalId, String testRunId) {
        if (projectGlobalId == null || testRunId == null) {
            return null;
        }
        return normalizeBase(tmsBase) + "/projects/" + projectGlobalId + "/test-runs/" + testRunId;
    }

    public static String testPlanUrl(String tmsBase, Long projectGlobalId, Long testPlanGlobalId) {
        if (projectGlobalId == null || testPlanGlobalId == null) {
            return null;
        }
        return normalizeBase(tmsBase) + "/projects/" + projectGlobalId + "/test-plans/"
                + testPlanGlobalId + "/tests";
    }

    private static String normalizeBase(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
