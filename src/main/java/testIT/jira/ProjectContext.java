package testIT.jira;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.webfragment.conditions.AbstractIssueCondition;
import com.atlassian.jira.plugin.webfragment.model.JiraHelper;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;

public class ProjectContext extends AbstractIssueCondition {
    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public ProjectContext(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public boolean shouldDisplay(ApplicationUser user, Issue issue, JiraHelper jiraHelper) {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        String allowedProjects = (String) settings.get(ConfigResource.Config.class.getName() + ".projects");
        if (allowedProjects == null || allowedProjects.isEmpty()) {
            return false;
        }
        List<String> projectKeys = Arrays.asList(allowedProjects.split(","));
        return projectKeys.contains(jiraHelper.getProject().getKey());
    }
}
