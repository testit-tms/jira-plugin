package testIT.jira;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.webfragment.contextproviders.AbstractJiraContextProvider;
import com.atlassian.jira.plugin.webfragment.model.JiraHelper;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.util.HashMap;
import java.util.Map;

public class TestCaseLink
extends AbstractJiraContextProvider {
    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;

    public TestCaseLink(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    public Map getContextMap(ApplicationUser applicationUser, JiraHelper jiraHelper) {
        Issue currIssue = (Issue)jiraHelper.getContextParams().get("issue");
        Map<String, Object> context = new HashMap<>();
        String issueKey = currIssue != null ? currIssue.getKey() : "";
        context.put("issueKey", issueKey);
        context.put("createDialogHref", "#testit-create-testcase");
        return context;
    }
}
