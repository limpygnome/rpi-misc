package com.limpygnome.daemon.buildtv.model;

import java.awt.*;

/**
 * Represents a notification.
 */
public class Notification
{
    private String header;
    private String text;
    private long lifespan;
    private Color background;
    private int priority;

    private long timeStamp;

    public Notification(String header, String text, long lifespan, Color background, int priority)
    {
        this.header = header;
        this.text = text;
        this.lifespan = lifespan;
        this.background = background;
        this.priority = priority;

        this.timeStamp = System.currentTimeMillis();
    }

    public String getHeader()
    {
        return header;
    }

    public String getText()
    {
        return text;
    }

    public long getLifespan()
    {
        return lifespan;
    }

    public Color getBackground()
    {
        return background;
    }

    public int getPriority()
    {
        return priority;
    }

    public long getTimeStamp()
    {
        return timeStamp;
    }

}
