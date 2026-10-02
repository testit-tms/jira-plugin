package testIT.jira;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserManager;
import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import testIT.jira.customfields.HiddenStepsCustomField;
import testIT.jira.customfields.TestResultIdCustomField;
import testIT.jira.testcase.TestCaseIssueType;

@Path("/")
@Named
public class ConfigResource {
    private static final Logger log = LoggerFactory.getLogger(ConfigResource.class);
    private static final Gson GSON = new Gson();

    @ComponentImport
    private final UserManager userManager;
    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;
    @ComponentImport
    private final TransactionTemplate transactionTemplate;

    @Inject
    public ConfigResource(UserManager userManager, PluginSettingsFactory pluginSettingsFactory,
            TransactionTemplate transactionTemplate) {
        this.userManager = userManager;
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.transactionTemplate = transactionTemplate;
    }

    @GET
    @Path("/projects")
    @Produces("application/json")
    public Response getProjects(@Context HttpServletRequest request) {
        if (!isSystemAdmin(request)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            ProjectManager projectManager = ComponentAccessor.getProjectManager();
            Map<String, String> projects = new HashMap<>();
            for (Project project : projectManager.getProjectObjects()) {
                projects.put(project.getKey(), project.getName());
            }
            return Response.ok(GSON.toJson(projects)).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            log.error("Failed to load Jira projects list", e);
            return serverError(e);
        }
    }

    @GET
    @Produces("application/json")
    public Response get(@Context HttpServletRequest request) {
        if (!isSystemAdmin(request)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            Config config = transactionTemplate.execute(new TransactionCallback<Config>() {
                @Override
                public Config doInTransaction() {
                    return loadConfig();
                }
            });
            return Response.ok(config).build();
        } catch (Exception e) {
            log.error("Failed to load Test IT config", e);
            return serverError(e);
        }
    }

    @PUT
    @Consumes("application/json")
    public Response put(final Config config, @Context HttpServletRequest request) {
        if (!isSystemAdmin(request)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        final String url = config == null ? null : config.getUrl();
        final String privateToken = config == null || config.getPrivateToken() == null ? "" : config.getPrivateToken();
        final String[] projects = config == null || config.getProjects() == null ? new String[0] : config.getProjects();
        log.info("Saving Test IT config: url={}, privateTokenSet={}, projects={}",
                url, !privateToken.isEmpty(), projects.length);
        try {
            transactionTemplate.execute((TransactionCallback<Void>) () -> {
                saveConfig(url, privateToken, projects);
                applyProjectChanges(projects);
                return null;
            });
            return Response.noContent().build();
        } catch (Exception e) {
            log.error("Failed to save Test IT config", e);
            return serverError(e);
        }
    }

    private Config loadConfig() {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        Config config = new Config();
        config.setUrl(readSetting(settings, ".url"));
        config.setPrivateToken(readSetting(settings, ".privateToken"));
        String projectsSetting = readSetting(settings, ".projects");
        config.setProjects(projectsSetting.isEmpty() ? new String[0] : projectsSetting.split(","));
        return config;
    }

    private void saveConfig(String url, String privateToken, String[] projects) {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        settings.put(Config.class.getName() + ".url", url);
        settings.put(Config.class.getName() + ".privateToken", privateToken);
        settings.put(Config.class.getName() + ".projects", String.join(",", projects));
    }

    private void applyProjectChanges(String[] projects) {
        if (projects.length == 0) {
            return;
        }
        TestCaseIssueType.addTestedByIssueLinkType();
        TestCaseIssueType.addTestCaseToProjects(projects);
        HiddenStepsCustomField.changeCustomField(projects);
        TestResultIdCustomField.changeCustomField(projects);
    }

    private String readSetting(PluginSettings settings, String suffix) {
        String value = (String) settings.get(Config.class.getName() + suffix);
        return value != null ? value : "";
    }

    private boolean isSystemAdmin(HttpServletRequest request) {
        String username = userManager.getRemoteUsername(request);
        return username != null && userManager.isSystemAdmin(username);
    }

    private Response serverError(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        return Response.serverError()
                .entity(Collections.singletonMap("message", message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Config {
        @XmlElement
        private String url;
        @XmlElement
        private String privateToken;
        @XmlElement
        private String[] projects;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPrivateToken() {
            return privateToken;
        }

        public void setPrivateToken(String privateToken) {
            this.privateToken = privateToken;
        }

        public String[] getProjects() {
            return projects;
        }

        public void setProjects(String[] projects) {
            this.projects = projects;
        }
    }
}
