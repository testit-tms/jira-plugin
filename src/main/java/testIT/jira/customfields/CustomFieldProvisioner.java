package testIT.jira.customfields;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.context.ProjectContext;
import com.atlassian.jira.issue.customfields.CustomFieldSearcher;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.fields.ConfigurableField;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigSchemeManager;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenLayoutItem;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreenSchemeItem;
import com.atlassian.jira.issue.fields.screen.FieldScreenTab;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeEntity;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CustomFieldProvisioner {
    private static final Logger log = LoggerFactory.getLogger(CustomFieldProvisioner.class);
    private static final String TEXT_SEARCHER = "com.atlassian.jira.plugin.system.customfieldtypes:textsearcher";
    private static final String TEXT_AREA_TYPE = "com.atlassian.jira.plugin.system.customfieldtypes:textarea";

    private CustomFieldProvisioner() {
    }

    static List<Project> projectsFromSettings(PluginSettingsFactory factory, String projectsSettingKey) {
        if (factory == null) {
            return Collections.emptyList();
        }
        PluginSettings settings = factory.createGlobalSettings();
        String allowedProjects = (String) settings.get(projectsSettingKey);
        if (allowedProjects == null || allowedProjects.isEmpty()) {
            return Collections.emptyList();
        }
        return projectsByKeys(allowedProjects.split(","));
    }

    static List<Project> projectsByKeys(String[] projectKeys) {
        if (projectKeys == null || projectKeys.length == 0) {
            return Collections.emptyList();
        }
        Set<String> keys = new HashSet<>();
        for (String key : projectKeys) {
            if (key == null) {
                continue;
            }
            String trimmed = key.trim();
            if (!trimmed.isEmpty()) {
                keys.add(trimmed);
            }
        }
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<Project> result = new ArrayList<>();
        for (Project project : ComponentAccessor.getProjectManager().getProjectObjects()) {
            if (keys.contains(project.getKey())) {
                result.add(project);
            }
        }
        return result;
    }

    static List<JiraContextNode> projectContexts(List<Project> projects) {
        List<JiraContextNode> contexts = new ArrayList<>();
        for (Project project : projects) {
            contexts.add(new ProjectContext(project.getId()));
        }
        return contexts;
    }

    static CustomField ensureTextAreaField(String fieldName, String fieldDescription,
            IssueType issueType, List<Project> projects) {
        List<JiraContextNode> contexts = projectContexts(projects);
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
        CustomField field = customFieldManager.getCustomFieldObjectByName(fieldName);
        if (field == null) {
            field = createTextAreaField(customFieldManager, fieldName, fieldDescription, issueType, contexts);
        } else {
            updateFieldContexts(field, contexts);
        }
        syncFieldScreens(field, projects);
        return field;
    }

    private static CustomField createTextAreaField(CustomFieldManager customFieldManager, String fieldName,
            String fieldDescription, IssueType issueType, List<JiraContextNode> contexts) {
        try {
            CustomFieldSearcher searcher = customFieldManager.getCustomFieldSearcher(TEXT_SEARCHER);
            CustomFieldType fieldType = customFieldManager.getCustomFieldType(TEXT_AREA_TYPE);
            return customFieldManager.createCustomField(
                    fieldName,
                    fieldDescription,
                    fieldType,
                    searcher,
                    contexts,
                    Collections.singletonList(issueType));
        } catch (Exception e) {
            log.error("Failed to create custom field {}", fieldName, e);
            return null;
        }
    }

    private static void updateFieldContexts(CustomField field, List<JiraContextNode> contexts) {
        FieldConfigSchemeManager schemeManager = ComponentAccessor.getFieldConfigSchemeManager();
        List<FieldConfigScheme> schemes = field.getConfigurationSchemes();
        if (schemes.isEmpty()) {
            FieldConfigScheme defaultScheme = schemeManager.createDefaultScheme(field, contexts);
            schemeManager.updateFieldConfigScheme(defaultScheme, contexts, field);
            return;
        }
        schemeManager.updateFieldConfigScheme(schemes.get(0), contexts, field);
    }

    static void syncFieldScreens(CustomField field, List<Project> projects) {
        if (field == null) {
            return;
        }
        Set<Long> allowedScreenIds = screenIdsForProjects(projects);
        FieldScreenManager screenManager = ComponentAccessor.getFieldScreenManager();
        for (FieldScreen screen : screenManager.getFieldScreens()) {
            FieldScreenTab tab = screen.getTab(0);
            if (allowedScreenIds.contains(screen.getId())) {
                if (!tab.isContainsField(field.getId())) {
                    tab.addFieldScreenLayoutItem(field.getId());
                }
                continue;
            }
            if (!tab.isContainsField(field.getId())) {
                continue;
            }
            FieldScreenLayoutItem layoutItem = tab.getFieldScreenLayoutItem(field.getId());
            if (layoutItem != null) {
                tab.removeFieldScreenLayoutItem(layoutItem.getPosition());
            }
        }
    }

    private static Set<Long> screenIdsForProjects(List<Project> projects) {
        Set<Long> screenIds = new HashSet<>();
        IssueTypeScreenSchemeManager schemeManager = ComponentAccessor.getIssueTypeScreenSchemeManager();
        for (Project project : projects) {
            IssueTypeScreenScheme issueTypeScheme = schemeManager.getIssueTypeScreenScheme(project);
            for (IssueTypeScreenSchemeEntity entity : issueTypeScheme.getEntities()) {
                FieldScreenScheme fieldScreenScheme = entity.getFieldScreenScheme();
                for (FieldScreenSchemeItem item : fieldScreenScheme.getFieldScreenSchemeItems()) {
                    screenIds.add(item.getFieldScreen().getId());
                }
            }
        }
        return screenIds;
    }
}
