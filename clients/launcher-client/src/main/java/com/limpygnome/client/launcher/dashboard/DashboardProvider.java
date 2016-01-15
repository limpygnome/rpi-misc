package com.limpygnome.client.launcher.dashboard;

import com.limpygnome.daemon.api.Controller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

/**
 * A provider for building the URLS and maintaining a dashboard.
 */
public abstract class DashboardProvider
{
    private static final Logger LOG = LogManager.getLogger(DashboardProvider.class);

    /**
     * The time, or rather the lifespan, for which the page can live / be open until the next item
     * is used.
     */
    protected long time;

    /**
     * Creates a new instance.
     *
     * @param time The lifespan of this provider when used as current dashboard
     */
    protected DashboardProvider(long time)
    {
        this.time = time;
    }

    /**
     * Loads parameters for the provider from the provided JSON object.
     *
     * @param root Root element with parameters
     */
    public abstract void loadParams(JSONObject root);

    /**
     * Builds the URL for the dashboard, which is used to display the dashboard on the attached monitor.
     *
     * Can contain sensitive information.
     *
     * @return The dashboard URL
     */
    public abstract String fetchUrl();

    /**
     * Builds the URL to the dashbord which can be publicly shared.
     *
     * @return The public URL
     */
    public abstract String fetchPublicUrl();

    /**
     * Loads dashboard provider configured by the global settings.
     *
     * If no provider can be loaded, or incorrect settings are specified, a runtime exception will be thrown.
     *
     * @param controller The current controller
     * @return An instance of a provider
     */
    public static DashboardProvider parse(Controller controller, JSONObject dashboardProviderConfig)
    {
        DashboardProvider dashboardProvider;

        // Switch on provider setting
        String provider = (String) dashboardProviderConfig.get("provider");

        // If no provider specified, just use default...
        if (provider == null || provider.length() == 0 || provider.equals("default") || provider.equals("url"))
        {
            dashboardProvider = new DefaultDashbordProvider();
            LOG.debug("No dashboard provider specified, using default");
        }
        else
        {
            switch (provider)
            {
                case "jira":
                    dashboardProvider = new JiraDashboardProvider();
                    LOG.debug("Using JIRA dashbord provider");
                    break;
                default:
                    throw new RuntimeException("Dashboard provider '" + provider + "' not supported");
            }
        }

        // Load params into provider
        JSONObject dashboardParams = (JSONObject) dashboardProviderConfig.get("params");
        dashboardProvider.loadParams(dashboardParams);

        return dashboardProvider;
    }

}
