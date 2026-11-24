package testIT.jira.duplication;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
public class DuplicationScheduler {
    private static final Logger log = LoggerFactory.getLogger(DuplicationScheduler.class);
    static final String JOB_KEY = "testit-duplication-tick";
    static final String JOB_DATA_KEY = "duplicationScheduler";
    private static final long TICK_MS = 60_000L;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private static volatile DuplicationScheduler INSTANCE;

    private volatile PluginScheduler pluginScheduler;
    private final DuplicationConfigStore configStore;
    private final DuplicationStatusStore statusStore;
    private final DuplicationSyncService syncService;
    private final ExecutorService executor;

    @Inject
    public DuplicationScheduler(
            DuplicationConfigStore configStore,
            DuplicationStatusStore statusStore,
            DuplicationSyncService syncService) {
        this.configStore = configStore;
        this.statusStore = statusStore;
        this.syncService = syncService;
        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "testit-duplication-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        INSTANCE = this;
    }

    public static synchronized DuplicationScheduler getOrCreate(PluginSettingsFactory pluginSettingsFactory) {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        if (pluginSettingsFactory == null) {
            throw new IllegalArgumentException("pluginSettingsFactory is required");
        }
        DuplicationConfigStore configStore = new DuplicationConfigStore(pluginSettingsFactory);
        DuplicationStatusStore statusStore = new DuplicationStatusStore(pluginSettingsFactory);
        DuplicationSyncService syncService = new DuplicationSyncService(
                configStore,
                statusStore,
                new JiraTestCaseIssueFactory(),
                pluginSettingsFactory);
        return new DuplicationScheduler(configStore, statusStore, syncService);
    }

    public static void ensureStarted() {
        DuplicationScheduler instance = INSTANCE;
        if (instance == null) {
            return;
        }
        try {
            instance.statusStore.clearStaleRunning();
            instance.ensureTickJobRegistered();
        } catch (Exception e) {
            log.warn("Duplication scheduler ensureStarted failed; plugin stays enabled", e);
        }
    }

    public void afterConfigSaved(DuplicationConfig config) {
        ensureTickJobRegistered();
        if (config == null || config.mappings == null) {
            return;
        }
        Instant now = Instant.now();
        int index = 0;
        for (DuplicationMapping mapping : config.mappings) {
            if (mapping == null || mapping.id == null || !Boolean.TRUE.equals(mapping.enabled)) {
                continue;
            }
            Instant eligible = now.plusSeconds(index * 30L);
            statusStore.setNextEligibleAt(mapping.id, eligible);
            index++;
        }
    }

    public void onTick() {
        DuplicationConfig config = configStore.load();
        if (config.mappings == null || config.mappings.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        for (DuplicationMapping mapping : config.mappings) {
            if (mapping == null || mapping.id == null || !Boolean.TRUE.equals(mapping.enabled)) {
                continue;
            }
            if (!isDue(mapping, statusStore.get(mapping.id), now, zone)) {
                continue;
            }
            final String mappingId = mapping.id;
            executor.submit(() -> syncService.syncMapping(mappingId));
        }
    }

    private boolean isDue(DuplicationMapping mapping, DuplicationMappingStats stats, Instant now, ZoneId zone) {
        if (stats.nextEligibleAt != null) {
            try {
                Instant eligible = Instant.parse(stats.nextEligibleAt);
                if (now.isBefore(eligible)) {
                    return false;
                }
                statusStore.setNextEligibleAt(mapping.id, null);
                if ("running".equalsIgnoreCase(stats.state)) {
                    statusStore.recordSkipOnceWhileRunning(mapping.id, "Previous sync still running");
                    return false;
                }
                return true;
            } catch (Exception ignored) {
                // fall through to schedule rules
            }
        }
        boolean scheduleDue = isScheduleDue(mapping, stats, now, zone);
        if (!scheduleDue) {
            return false;
        }
        if ("running".equalsIgnoreCase(stats.state)) {
            statusStore.recordSkipOnceWhileRunning(mapping.id, "Previous sync still running");
            return false;
        }
        return true;
    }

    private boolean isScheduleDue(
            DuplicationMapping mapping, DuplicationMappingStats stats, Instant now, ZoneId zone) {
        if ("DAILY".equalsIgnoreCase(mapping.scheduleMode)) {
            return isDailyDue(mapping, stats, now, zone);
        }
        long intervalMs = Math.max(15, mapping.intervalMinutes) * 60_000L;
        if (stats.lastFinishedAt == null && stats.lastStartedAt == null) {
            return true;
        }
        try {
            Instant last = stats.lastFinishedAt != null
                    ? Instant.parse(stats.lastFinishedAt)
                    : Instant.parse(stats.lastStartedAt);
            return now.toEpochMilli() - last.toEpochMilli() >= intervalMs;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isDailyDue(DuplicationMapping mapping, DuplicationMappingStats stats, Instant now, ZoneId zone) {
        LocalTime target;
        try {
            target = LocalTime.parse(mapping.dailyAt, HH_MM);
        } catch (Exception e) {
            target = LocalTime.of(2, 0);
        }
        ZonedDateTime zdt = now.atZone(zone);
        LocalDate today = zdt.toLocalDate();
        ZonedDateTime todayRun = ZonedDateTime.of(LocalDateTime.of(today, target), zone);
        if (zdt.isBefore(todayRun)) {
            return false;
        }
        if (stats.lastFinishedAt == null && stats.lastStartedAt == null) {
            return true;
        }
        try {
            Instant last = stats.lastFinishedAt != null
                    ? Instant.parse(stats.lastFinishedAt)
                    : Instant.parse(stats.lastStartedAt);
            ZonedDateTime lastZdt = last.atZone(zone);
            return lastZdt.isBefore(todayRun);
        } catch (Exception e) {
            return true;
        }
    }

    private void resolvePluginScheduler() {
        if (pluginScheduler != null) {
            return;
        }
        try {
            PluginScheduler scheduler = ComponentAccessor.getOSGiComponentInstanceOfType(PluginScheduler.class);
            if (scheduler == null) {
                scheduler = ComponentAccessor.getComponent(PluginScheduler.class);
            }
            if (scheduler == null) {
                log.warn("PluginScheduler unavailable; duplication tick job not registered");
                return;
            }
            pluginScheduler = scheduler;
        } catch (Exception e) {
            log.warn("Failed to resolve PluginScheduler; duplication tick job not registered", e);
        }
    }

    private void ensureTickJobRegistered() {
        resolvePluginScheduler();
        PluginScheduler scheduler = pluginScheduler;
        if (scheduler == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put(JOB_DATA_KEY, this);
        try {
            scheduler.unscheduleJob(JOB_KEY);
        } catch (Exception ignored) {
            // first registration
        }
        scheduler.scheduleJob(
                JOB_KEY,
                DuplicationTickJob.class,
                data,
                new Date(),
                TICK_MS);
        log.info("Registered duplication tick job every {} ms", TICK_MS);
    }
}
