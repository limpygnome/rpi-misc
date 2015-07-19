package com.limpygnome.daemon.ws281x.service;

import com.limpygnome.daemon.api.Controller;
import com.limpygnome.daemon.api.Service;
import com.limpygnome.daemon.ws281x.led.pattern.build.*;
import com.limpygnome.daemon.ws281x.led.pattern.daemon.*;
import com.limpygnome.daemon.ws281x.led.Pattern;
import com.limpygnome.daemon.ws281x.led.LedController;
import com.limpygnome.daemon.ws281x.led.LedRenderThread;
import com.limpygnome.daemon.ws281x.led.pattern.team.Standup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

/**
 * A service to control a NeoPixel LED strip.
 */
public class LedService implements Service
{
    private static final Logger LOG = LogManager.getLogger(LedService.class);

    private HashMap<String, Pattern> patterns;
    private LedController ledController;
    private LedRenderThread ledRenderThread;
    private String currentPatternName;

    public LedService()
    {
        this.patterns = new HashMap<>();
        this.ledController = new LedController();
        this.ledRenderThread = null;
    }

    @Override
    public synchronized void start(Controller controller)
    {
        // Register all the patterns!
        patterns.put("build-unknown", new BuildUnknown());
        patterns.put("build-ok", new BuildOkay());
        patterns.put("build-progress", new BuildProgress());
        patterns.put("build-unstable", new BuildUnstable());
        patterns.put("build-failure", new BuildFailure());
        patterns.put("jenkins-unavailable", new JenkinsUnavailable());

        patterns.put("shutdown", new Shutdown());
        patterns.put("startup", new Startup());

        patterns.put("standup", new Standup());


        // Set startup pattern, whilst another service changes it
        setPattern("startup");
    }

    @Override
    public synchronized void stop(Controller controller)
    {
        // Change pattern to shutdown
        setPattern("shutdown");

        // Stop render thread
        ledRenderThread.kill();

        // Wipe patterns
        patterns.clear();
    }

    public synchronized void setPattern(String patternName)
    {
        // Check if pattern already set
        if (currentPatternName == patternName)
        {
            LOG.debug("Ignoring pattern change request, already set - pattern: {}", patternName);
            return;
        }

        // Locate pattern
        Pattern pattern = patterns.get(patternName);

        if (pattern == null)
        {
            LOG.warn("LED pattern missing - pattern: {}", patternName);
        }
        else
        {
            // Kill current thread
            if (ledRenderThread != null)
            {
                ledRenderThread.kill();
            }

            // Build new thread
            ledRenderThread = new LedRenderThread(this, pattern);
            ledRenderThread.start();

            currentPatternName = patternName;

            LOG.debug("LED pattern changed - pattern: {}", patternName);
        }
    }

    public synchronized LedController getLedController()
    {
        return ledController;
    }

}
