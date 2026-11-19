package dev.ua.ikeepcalm.util;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.AxesChartStyler;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public class Charts {

    public static final Color[] PALETTE = {
            new Color(0x1F77B4),
            new Color(0xD62728),
            new Color(0x2CA02C),
            new Color(0x9467BD),
            new Color(0xFF7F0E)
    };

    private static final Font TEXT = new Font("Times New Roman", Font.PLAIN, 16);

    public static void style(AxesChartStyler styler) {
        styler.setLocale(Locale.forLanguageTag("uk"));
        styler.setChartBackgroundColor(Color.WHITE);
        styler.setPlotBackgroundColor(Color.WHITE);
        styler.setAxisTitleFont(TEXT);
        styler.setAxisTickLabelsFont(TEXT);
        styler.setLegendFont(TEXT);
        styler.setChartTitleFont(TEXT);
        styler.setSeriesColors(PALETTE);
    }

    public static XYChart line(String xTitle, String yTitle, String xPattern, Double xMin, Double xMax,
                               Map<String, double[][]> lines, Map<String, double[][]> points) {
        XYChart chart = new XYChartBuilder().width(1000).height(600).xAxisTitle(xTitle).yAxisTitle(yTitle).build();
        style(chart.getStyler());
        chart.getStyler().setXAxisDecimalPattern(xPattern);
        chart.getStyler().setXAxisMin(xMin);
        chart.getStyler().setXAxisMax(xMax);
        chart.getStyler().setLegendVisible(lines.size() + points.size() > 1);
        int index = 0;
        for (Map.Entry<String, double[][]> line : lines.entrySet()) {
            chart.addSeries(line.getKey(), line.getValue()[0], line.getValue()[1])
                    .setMarker(SeriesMarkers.NONE)
                    .setLineColor(PALETTE[index++ % PALETTE.length]);
        }
        index = 0;
        for (Map.Entry<String, double[][]> point : points.entrySet()) {
            chart.addSeries(point.getKey(), point.getValue()[0], point.getValue()[1])
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter)
                    .setMarker(SeriesMarkers.CIRCLE)
                    .setMarkerColor(PALETTE[index++ % PALETTE.length]);
        }
        return chart;
    }

    public static void save(Chart<?, ?> chart, Path file) {
        try {
            BitmapEncoder.saveBitmapWithDPI(chart, file.toString(), BitmapEncoder.BitmapFormat.PNG, 150);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
