package com.impygnome.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;

/**
 * Created by limpygnome on 26/08/15.
 */
public class MessageWindow extends JFrame
{
    private JPanel jPanel;
    private JLabel jLabelHeader;
    private JLabel jLabelText;

    private MessageWindowExpanderThread expander;
    private MessageWindowFlashThread flasher;

    public MessageWindow(String header, String text, long lifespanMs, int backgroundR, int backgroundG, int backgroundB)
    {
        Dimension screenSize = getScreenSize();
        double screenWidth = screenSize.getWidth();
        double screenHeight = screenSize.getHeight();
        boolean hasText = (text != null);

        // Set form properties
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Notification Client");

        // Disable window frame
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        // Create panel for controls
        jPanel = new JPanel();
        jPanel.setBackground(new Color(backgroundR, backgroundG, backgroundB));
        jPanel.setLayout(new GridLayout(hasText ? 2 : 1, 1));
        add(jPanel);

        // Add labels for text
        double minScreenDimension = Math.min(screenWidth, screenHeight);

        jLabelHeader = new JLabel();
        jLabelHeader.setText(prepareLabelText(header));
        jLabelHeader.setFont(new Font("Arial", Font.PLAIN, (int) (minScreenDimension * 0.1)));
        jLabelHeader.setHorizontalAlignment(SwingConstants.CENTER);
        jLabelHeader.setVerticalAlignment(hasText ? SwingConstants.BOTTOM : SwingConstants.CENTER);
        jLabelHeader.setForeground(Color.WHITE);
        jLabelHeader.setVisible(false);
        jPanel.add(jLabelHeader);

        if (hasText)
        {
            jLabelText = new JLabel();
            jLabelText.setText(prepareLabelText(text));
            jLabelText.setFont(new Font("Arial", Font.PLAIN, (int) (minScreenDimension * 0.05)));
            jLabelText.setHorizontalAlignment(SwingConstants.CENTER);
            jLabelText.setVerticalAlignment(SwingConstants.TOP);
            jLabelText.setForeground(Color.WHITE);
            jLabelText.setVisible(false);
            jPanel.add(jLabelText);
        }

        // Set initial size of form
        setSize(200, (int) (screenWidth * 0.1));

        // Show window
        centerOnScreen();
        setVisible(true);

        // Gradually expand window
        expander = new MessageWindowExpanderThread(this, 60);
        expander.start();

        flasher = new MessageWindowFlashThread(this, 100);
        flasher.start();

        // Setup timer to self-close window
        if (lifespanMs != 0)
        {
            new Timer((int) lifespanMs, new AbstractAction()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    dispatchEvent(new WindowEvent(MessageWindow.this, WindowEvent.WINDOW_CLOSING));
                }
            }).start();
        }
    }

    @Override
    public void dispose()
    {
        // Kill effect threads
        if (expander != null)
        {
            expander.kill();
        }
        if (flasher != null)
        {
            flasher.kill();
        }

        super.dispose();
    }

    private String prepareLabelText(String text)
    {
        return "<html><p class=\"text-align: center\">" + text.replace("\n", "<br />") + "</p></html>";
    }

    public void centerOnScreen()
    {
        Dimension screenSize = getScreenSize();

        double x = (screenSize.getWidth() / 2.0) - ((double) getWidth() / 2.0);
        double y = (screenSize.getHeight() / 2.0) - ((double) getHeight() / 2.0);

        setLocation((int) x, (int) y);
    }

    public Dimension getScreenSize()
    {
        GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] graphicsDevices = graphicsEnvironment.getScreenDevices();

        DisplayMode displayMode;
        for (GraphicsDevice graphicsDevice : graphicsDevices)
        {
            displayMode = graphicsDevice.getDisplayMode();
            return new Dimension(displayMode.getWidth(), displayMode.getHeight());
        }

        // Fallback...
        return Toolkit.getDefaultToolkit().getScreenSize();
    }

    public void showMessage()
    {
        jLabelHeader.setVisible(true);
        jLabelText.setVisible(true);
    }

}
