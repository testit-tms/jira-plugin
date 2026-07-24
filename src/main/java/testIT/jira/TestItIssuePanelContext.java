package testIT.jira;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.webfragment.contextproviders.AbstractJiraContextProvider;
import com.atlassian.jira.plugin.webfragment.model.JiraHelper;
import com.atlassian.jira.user.ApplicationUser;
import java.util.HashMap;
import java.util.Map;

public class TestItIssuePanelContext extends AbstractJiraContextProvider {
    public Map getContextMap(ApplicationUser applicationUser, JiraHelper jiraHelper) {
        Issue issue = (Issue) jiraHelper.getContextParams().get("issue");
        Map<String, Object> context = new HashMap<>();
        context.put("issueKey", issue != null ? issue.getKey() : "");
        return context;
    }
}
