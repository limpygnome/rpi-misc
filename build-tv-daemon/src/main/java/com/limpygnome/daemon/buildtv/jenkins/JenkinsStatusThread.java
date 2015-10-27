package com.limpygnome.daemon.buildtv.jenkins;

import com.limpygnome.daemon.api.Controller;
import com.limpygnome.daemon.buildtv.led.pattern.LedPattern;
import com.limpygnome.daemon.buildtv.led.pattern.source.PatternSource;
import com.limpygnome.daemon.buildtv.model.JenkinsHostUpdateResult;
import com.limpygnome.daemon.buildtv.model.Notification;
import com.limpygnome.daemon.buildtv.service.LedTimeService;
import com.limpygnome.daemon.buildtv.service.NotificationService;
import com.limpygnome.daemon.common.ExtendedThread;
import com.limpygnome.daemon.util.RestClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;

/**
 * Checks build status against list of projects on Jenkins.
 */
public class JenkinsStatusThread extends ExtendedThread
{
    private static final Logger LOG = LogManager.getLogger(JenkinsStatusThread.class);

    /* The global configuration file for Jenkins. */
    private static final String JENKINS_SETTINGS_FILENAME = "jenkins.json";

    /* The HTTP user agent for any requests made by the build TV to Jenkins. */
    private static final String USER_AGENT = "Build TV";

    /* The source name for notifications sent to the notification service. */
    private static final String NOTIFICATION_SOURCE_NAME = "build-tv-jenkins";

    private LedTimeService ledTimeService;
    private NotificationService notificationService;
    private long pollRateMs;
    private int bufferSizeBytes;
    private PatternSource patternSource;
    private LedPattern lastLedPattern;

    private JenkinsHost[] jenkinsHosts;
    private RestClient restClient;

    public JenkinsStatusThread(Controller controller)
    {
        // Load configuration from file
        loadConfigurationFromFile(controller);

        // Setup REST client
        this.restClient = new RestClient(USER_AGENT, bufferSizeBytes);

        // Setup a new LED pattern source for this thread
        this.patternSource = new PatternSource("Jenkins Status", LedPattern.BUILD_UNKNOWN, 1);

        // Fetch LED time service for later
        this.ledTimeService = (LedTimeService) controller.getServiceByName("led-time");

        // Fetch notifications service
        this.notificationService = (NotificationService) controller.getServiceByName(NotificationService.SERVICE_NAME);

        // Set last pattern to null; will be used to track last led pattern for deciding if to update notification
        this.lastLedPattern = null;
    }

    private void loadConfigurationFromFile(Controller controller)
    {
        // Retrieve file instance of Jenkins file
        File file = controller.findConfigFile(JENKINS_SETTINGS_FILENAME);

        try
        {
            // Load JSON from file
            JSONParser jsonParser = new JSONParser();
            JSONObject root = (JSONObject) jsonParser.parse(new FileReader(file));

            // Load mandatory configuration
            this.pollRateMs = (long) root.get("poll-rate-ms");
            this.bufferSizeBytes = (int) (long) root.get("max-buffer-bytes");

            LOG.debug("Poll rate: {}, max buffer bytes: {}", pollRateMs, bufferSizeBytes);

            // Parse JSON into hosts
            JSONArray rawHosts = (JSONArray) root.get("hosts");
            this.jenkinsHosts = parseHosts(rawHosts);
        }
        catch (Exception e)
        {
            String absPath = file.getAbsolutePath();
            LOG.error("Failed to load Jenkins hosts file - path: {}", absPath, e);
            throw new RuntimeException("Failed to load Jenkins hosts file - path: " + absPath);
        }
    }

    private JenkinsHost[] parseHosts(JSONArray rawHosts)
    {
        JenkinsHost[] jenkinsHosts = new JenkinsHost[rawHosts.size()];

        JSONObject rawHost;
        JSONArray rawHostJobs;
        JenkinsHost jenkinsHost;
        LinkedList<String> jobs;

        for (int i = 0; i < rawHosts.size(); i++)
        {
            rawHost = (JSONObject) rawHosts.get(i);

            // Parse jobs
            jobs = new LinkedList<>();

            if (rawHost.containsKey("jobs"))
            {
                rawHostJobs = (JSONArray) rawHost.get("jobs");

                for (int j = 0; j < rawHostJobs.size(); j++)
                {
                    jobs.add((String) rawHostJobs.get(j));
                }
            }

            // Parse host
            jenkinsHost = new JenkinsHost(
                    (String) rawHost.get("name"),
                    (String) rawHost.get("url"),
                    jobs
            );

            jenkinsHosts[i] = jenkinsHost;
        }

        return jenkinsHosts;
    }

    @Override
    public void run()
    {
        Thread.currentThread().setName("Jenkins Status");

        // Add our pattern to LED time service
        ledTimeService.addPatternSource(patternSource);

        // Run until thread exits, polling Jenkins for status and updating pattern source
        JenkinsHostUpdateResult hostsResult;
        Notification notification;

        while (!isExit())
        {
            try
            {
                // Poll Jenkins
                hostsResult = pollHosts();

                // Set LED pattern to highest found from hosts
                patternSource.setCurrentLedPattern(hostsResult.getLedPattern());

                // Display notification for certain LED patterns
                updateNotificationFromJenkinsResult(hostsResult);

                // Wait a while...
                Thread.sleep(pollRateMs);
            }
            catch (InterruptedException e)
            {
                LOG.error("Exception during Jenkins status thread", e);
            }
        }

        // Remove our pattern from LED time service
        ledTimeService.removePatternSource(patternSource);
    }

    private void updateNotificationFromJenkinsResult(JenkinsHostUpdateResult hostsResult)
    {
        // TODO: probably shouldn't have magic values here, clean it up eventually using config with fallback default values
        LedPattern currentLedPattern = hostsResult.getLedPattern();

        if (this.lastLedPattern == null || this.lastLedPattern != currentLedPattern)
        {
            // Update last led pattern to avoid sending multiple notifications
            this.lastLedPattern = currentLedPattern;

            // Build text showing affected jobs
            String text;
            List<String> affectedJobs = hostsResult.getAffectedJobs();

            if (affectedJobs.size() > 4)
            {
                text = affectedJobs.size() + " jobs affected";
            }
            else if (!affectedJobs.isEmpty())
            {
                StringBuilder buffer = new StringBuilder();

                for (String job : affectedJobs)
                {
                    buffer.append(job).append("\n");
                }
                buffer.delete(buffer.length()-1, buffer.length());

                text = buffer.toString();
            }
            else
            {
                text = null;
            }

            // Build notification
            Notification notification;

            switch (hostsResult.getLedPattern())
            {
                case BUILD_FAILURE:
                    notification = new Notification("build failure", text, 60000, Color.decode("#CC3300"), 10);
                    break;
                case BUILD_OK:
                    notification = new Notification("build success", text, 10000, Color.decode("#339933"), 10);
                    break;
                case BUILD_PROGRESS:
                    notification = new Notification("build in progress...", text, 10000, Color.decode("#003D99"), 10);
                    break;
                case BUILD_UNSTABLE:
                    notification = new Notification("build unstable", text, 60000, Color.decode("#FF9933"), 10);
                    break;
                case JENKINS_UNAVAILABLE:
                    notification = new Notification("Jenkins offline", text, 0, Color.decode("#CC0000"), 10);
                    break;
                default:
                    notification = null;
                    break;
            }

            // Update via service
            notificationService.updateCurrentNotification(NOTIFICATION_SOURCE_NAME, notification);
        }
    }

    private JenkinsHostUpdateResult pollHosts()
    {
        JenkinsHostUpdateResult hostsResult = new JenkinsHostUpdateResult();
        LedPattern highestLedPattern = LedPattern.BUILD_UNKNOWN;

        JenkinsHostUpdateResult result;
        LedPattern resultLedPattern;

        // Get status of each job and keep highest priority LED pattern
        for (JenkinsHost jenkinsHost : jenkinsHosts)
        {
            result = jenkinsHost.update(restClient);
            resultLedPattern = result.getLedPattern();

            // Determine if host's pattern has highest priority
            if (resultLedPattern.PRIORITY > highestLedPattern.PRIORITY)
            {
                highestLedPattern = resultLedPattern;
            }

            // Merge affected jobs into overall result
            hostsResult.mergeAffectedJobs(result);
        }

        // Set overall LED pattern to highest found
        hostsResult.setLedPattern(highestLedPattern);

        return hostsResult;
    }

}
