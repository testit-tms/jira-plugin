package testIT.jira;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import testIT.jira.testcase.TestCaseIssueType;

/**
 * On enable: ensure TestCase issue type icon (fail-safe).
 * Link type / custom fields / projects still applied from ConfigResource on Save.
 */
@Component
public class PluginEnabledHandler implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(PluginEnabledHandler.class);
    private static final String PLUGIN_KEY = "testit.testit";

    @JiraImport
    private final EventPublisher eventPublisher;

    @Inject
    public PluginEnabledHandler(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.eventPublisher.register(this);
    }

    @EventListener
    public void onPluginEnabled(PluginEnabledEvent event) {
        Plugin plugin = event.getPlugin();
        if (!PLUGIN_KEY.equals(plugin.getKey())) {
            return;
        }
        try {
            TestCaseIssueType.ensureTestCaseIcon();
        } catch (Exception e) {
            log.warn("TestCase icon ensure failed on enable; plugin stays enabled", e);
        }
    }

    @Override
    public void destroy() {
        eventPublisher.unregister(this);
    }
}
