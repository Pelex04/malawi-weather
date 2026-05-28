package org.example.ui.components;

import org.example.model.WeatherData;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ForecastPanel extends JPanel {

    private static final Color CARD_BG     = new Color(255, 255, 255, 30);
    private static final Color TEXT_PRIMARY = Color.WHITE;
    private static final Color TEXT_MUTED   = new Color(200, 220, 255);
    private static final Font  DAY_FONT     = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font  ICON_FONT    = new Font("Segoe UI Emoji", Font.PLAIN, 22);
    private static final Font  TEMP_FONT    = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font  DETAIL_FONT  = new Font("Segoe UI", Font.PLAIN, 11);

    public ForecastPanel() {
        setOpaque(false);
        setLayout(new GridLayout(1, 7, 8, 0));
        setBorder(new EmptyBorder(10, 0, 0, 0));
    }

    public void updateForecast(List<WeatherData.DailyForecast> forecast) {
        removeAll();
        DateTimeFormatter inputFmt  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter outputFmt = DateTimeFormatter.ofPattern("EEE\ndd MMM");

        for (WeatherData.DailyForecast day : forecast) {
            JPanel card = createDayCard(day, inputFmt, outputFmt);
            add(card);
        }
        revalidate();
        repaint();
    }

    private JPanel createDayCard(WeatherData.DailyForecast day,
                                  DateTimeFormatter inputFmt,
                                  DateTimeFormatter outputFmt) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(8, 6, 8, 6));

        // Day label
        String dayLabel;
        try {
            LocalDate date = LocalDate.parse(day.getDate(), inputFmt);
            dayLabel = date.format(DateTimeFormatter.ofPattern("EEE"));
            String dateLabel = date.format(DateTimeFormatter.ofPattern("dd MMM"));
            JLabel dayLbl = styledLabel(dayLabel, DAY_FONT, TEXT_PRIMARY);
            JLabel dateLbl = styledLabel(dateLabel, DETAIL_FONT, TEXT_MUTED);
            card.add(dayLbl);
            card.add(dateLbl);
        } catch (Exception e) {
            card.add(styledLabel(day.getDate(), DAY_FONT, TEXT_PRIMARY));
        }

        card.add(Box.createVerticalStrut(6));

        // Weather icon
        JLabel icon = styledLabel(WeatherIconMapper.getIcon(0), ICON_FONT, TEXT_PRIMARY);
        card.add(icon);

        card.add(Box.createVerticalStrut(6));

        // Temps
        card.add(styledLabel(String.format("%.0f°", day.getTempMax()), TEMP_FONT, TEXT_PRIMARY));
        card.add(styledLabel(String.format("%.0f°", day.getTempMin()), DETAIL_FONT, TEXT_MUTED));

        card.add(Box.createVerticalStrut(4));

        // Rain prob
        if (day.getPrecipProbability() > 0) {
            card.add(styledLabel(
                String.format("💧 %.0f%%", day.getPrecipProbability()),
                DETAIL_FONT, new Color(150, 210, 255)));
        }

        // Wind
        card.add(styledLabel(
            String.format("💨 %.0f", day.getWindSpeedMax()),
            DETAIL_FONT, TEXT_MUTED));

        return card;
    }

    private JLabel styledLabel(String text, Font font, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(font);
        lbl.setForeground(color);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }
}
