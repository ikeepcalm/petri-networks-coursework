package dev.ua.ikeepcalm.app;

import dev.ua.ikeepcalm.petri.ServiceCentre;
import dev.ua.ikeepcalm.util.Charts;
import dev.ua.ikeepcalm.util.Statistics;
import org.knowm.xchart.*;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

public class Gui extends JFrame {

    private static final String[] TYPE_COLUMNS = {
            "Тип", "Частка", "Реєстрація, хв", "P(консультант)",
            "Консультація, хв", "Оформлення, хв", "P(повернення)"
    };

    private static final List<String> TIMES = List.of("W", "V", "Wq1", "Wq2", "Wq3");
    private static final List<String> SWEEP_SERIES = List.of("W, хв", "V, хв");

    private static final int KEPT_RUNS = 5;

    private final JTextField interarrival = new JTextField(6);
    private final JTextField admins = new JTextField(3);
    private final JTextField consultants = new JTextField(3);
    private final JTextField windows = new JTextField(3);
    private final JTextField warmup = new JTextField(7);
    private final JTextField duration = new JTextField(7);
    private final JTextField runs = new JTextField(4);
    private final JComboBox<Factor> factor = new JComboBox<>(Factor.values());
    private final JTextField from = new JTextField("3", 5);
    private final JTextField to = new JTextField("7", 5);
    private final JTextField step = new JTextField("1", 5);
    private final DefaultTableModel types = new DefaultTableModel(TYPE_COLUMNS, 0);
    private final DefaultTableModel results = new DefaultTableModel(new String[]{"Показник", "Значення"}, 0);
    private final JLabel status = new JLabel(" ");
    private final List<JButton> actions = new ArrayList<>();
    private final Deque<String> runNames = new ArrayDeque<>();

    private final CategoryChart load = categoryChart("Завантаженість вузлів", "Вузол", "U");
    private final CategoryChart times = categoryChart("Часи очікування та перебування", "Показник", "хв");

    private final XYChart sweep = new XYChartBuilder().title("Вплив параметра").xAxisTitle(" ").yAxisTitle("хв").build();
    private final List<XChartPanel<?>> chartPanels = new ArrayList<>();

    private int runCounter;

    public Gui() {
        super("Імітація мережі масового обслуговування з типізованими маркерами (мережа Петрі)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Charts.style(sweep.getStyler());
        sweep.getStyler().setLegendPosition(Styler.LegendPosition.OutsideS);
        JPanel charts = new JPanel(new GridLayout(2, 2, 6, 6));
        charts.add(resultsPanel());
        charts.add(chartPanel(load));
        charts.add(chartPanel(times));
        charts.add(chartPanel(sweep));
        add(controlsPanel(), BorderLayout.NORTH);
        add(charts, BorderLayout.CENTER);
        load(ServiceCentre.base());
        setSize(new Dimension(1500, 900));
        setLocationRelativeTo(null);
    }

    static void main() throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> new Gui().setVisible(true));
    }

    private static CategoryChart categoryChart(String title, String xTitle, String yTitle) {
        CategoryChart chart = new CategoryChartBuilder().title(title).xAxisTitle(xTitle).yAxisTitle(yTitle).build();
        Charts.style(chart.getStyler());
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideS);
        return chart;
    }

    private static JPanel titled(JPanel panel, String title) {
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private static void addField(JPanel panel, String label, JComponent field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private static boolean overloaded(ServiceCentre centre) {
        for (int node = 0; node < ServiceCentre.NODES.length; node++) {
            if (centre.offeredUtilisation(node) >= 1) {
                return true;
            }
        }
        return false;
    }

    private static String overloadWarning(ServiceCentre centre) {
        List<String> nodes = new ArrayList<>();
        for (int node = 0; node < ServiceCentre.NODES.length; node++) {
            if (centre.offeredUtilisation(node) >= 1) {
                nodes.add(ServiceCentre.NODES[node]);
            }
        }
        return nodes.isEmpty() ? "" : "<br>Перевантажено (ρ ≥ 1), стаціонарного режиму немає: " + String.join(", ", nodes);
    }

    private static double mean(List<ServiceCentre.Result> runResults, ToDoubleFunction<ServiceCentre.Result> response) {
        return Statistics.mean(Statistics.values(runResults, response));
    }

    private static double halfWidth(double[] values) {
        return values.length < 2 ? 0 : Statistics.halfWidth(values);
    }

    private static String estimate(List<ServiceCentre.Result> runResults, ToDoubleFunction<ServiceCentre.Result> response) {
        double[] values = Statistics.values(runResults, response);
        if (values.length < 2) {
            return format(values[0]);
        }
        return format(Statistics.mean(values)) + " ± " + format(Statistics.halfWidth(values));
    }

    private static double number(JTextField field, String name) {
        return parse(field.getText(), name);
    }

    private static int integer(JTextField field, String name) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": потрібне ціле число, отримано «" + field.getText() + "»");
        }
    }

    private static double parse(String text, String name) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": потрібне число, отримано «" + text + "»");
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private JComponent chartPanel(Chart<?, ?> chart) {
        XChartPanel<?> panel = new XChartPanel<>(chart);
        chartPanels.add(panel);
        return panel;
    }

    private JPanel controlsPanel() {
        JPanel fields = titled(new JPanel(new GridLayout(0, 2, 8, 4)), "Параметри мережі");
        addField(fields, "Середній інтервал надходження, хв", interarrival);
        addField(fields, "Адміністратори", admins);
        addField(fields, "Консультанти", consultants);
        addField(fields, "Вікна оформлення", windows);
        addField(fields, "Період розгону, хв", warmup);
        addField(fields, "Час моделювання, хв", duration);
        addField(fields, "Кількість прогонів", runs);

        JTable typeTable = new JTable(types);
        typeTable.setFillsViewportHeight(true);
        typeTable.setPreferredScrollableViewportSize(new Dimension(600, typeTable.getRowHeight() * 4));
        JPanel typeButtons = new JPanel(new GridLayout(1, 0, 6, 0));
        typeButtons.add(button("Додати тип", () ->
                types.addRow(new Object[]{types.getRowCount() + 1, "0.1", "1.0", "0.5", "5.0", "5.0", "0.1"})));
        typeButtons.add(button("Видалити останній тип", () -> {
            if (types.getRowCount() > 1) {
                types.removeRow(types.getRowCount() - 1);
            }
        }));
        typeButtons.add(button("Базові дані", () -> load(ServiceCentre.base())));
        JPanel typePanel = titled(new JPanel(new BorderLayout(0, 4)), "Типи заявок");
        typePanel.add(new JScrollPane(typeTable), BorderLayout.CENTER);
        typePanel.add(typeButtons, BorderLayout.SOUTH);

        JPanel study = titled(new JPanel(new GridLayout(0, 2, 8, 4)), "Дослідження");
        study.add(button("Моделювати", this::simulate));
        study.add(button("Очистити графіки", this::clearCharts));
        addField(study, "Параметр", factor);
        addField(study, "Від", from);
        addField(study, "До", to);
        addField(study, "Крок", step);
        study.add(new JLabel());
        study.add(button("Дослідити вплив", this::explore));

        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.add(fields, BorderLayout.WEST);
        panel.add(typePanel, BorderLayout.CENTER);
        panel.add(study, BorderLayout.EAST);
        return panel;
    }

    private JPanel resultsPanel() {
        JTable table = new JTable(results);
        table.setFillsViewportHeight(true);
        JPanel panel = titled(new JPanel(new BorderLayout(0, 4)), "Результати");
        panel.add(status, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        actions.add(button);
        return button;
    }

    private void load(ServiceCentre centre) {
        interarrival.setText(String.valueOf(centre.getInterarrivalMean()));
        admins.setText(String.valueOf(centre.getChannels()[ServiceCentre.REGISTRATION]));
        consultants.setText(String.valueOf(centre.getChannels()[ServiceCentre.CONSULTATION]));
        windows.setText(String.valueOf(centre.getChannels()[ServiceCentre.PROCESSING]));
        warmup.setText("5000");
        duration.setText("100000");
        runs.setText("10");
        types.setRowCount(0);
        for (int type = 1; type <= centre.types(); type++) {
            double[] means = centre.getServiceMeans()[type - 1];
            types.addRow(new Object[]{type, String.valueOf(centre.getTypeShares()[type - 1]),
                    String.valueOf(means[ServiceCentre.REGISTRATION]), String.valueOf(centre.getConsultProbability()[type - 1]),
                    String.valueOf(means[ServiceCentre.CONSULTATION]), String.valueOf(means[ServiceCentre.PROCESSING]),
                    String.valueOf(centre.getReturnProbability()[type - 1])});
        }
    }

    private ServiceCentre read() {
        int count = types.getRowCount();
        double[] shares = new double[count];
        double[][] means = new double[count][];
        double[] consult = new double[count];
        double[] ret = new double[count];
        for (int row = 0; row < count; row++) {
            shares[row] = cell(row, 1);
            means[row] = new double[]{cell(row, 2), cell(row, 4), cell(row, 5)};
            consult[row] = cell(row, 3);
            ret[row] = cell(row, 6);
        }
        int[] channels = {integer(admins, "Адміністратори"), integer(consultants, "Консультанти"),
                integer(windows, "Вікна оформлення")};
        ServiceCentre centre = new ServiceCentre(number(interarrival, "Середній інтервал надходження"),
                shares, channels, means, consult, ret);
        ServiceCentre.build(centre);
        return centre;
    }

    private Settings settings() {
        Settings settings = new Settings(read(), number(warmup, "Період розгону"), number(duration, "Час моделювання"),
                integer(runs, "Кількість прогонів"));
        if (settings.warmup() < 0 || settings.duration() <= 0 || settings.runs() < 1) {
            throw new IllegalArgumentException("Розгін має бути ≥ 0, час моделювання > 0, прогонів ≥ 1");
        }
        return settings;
    }

    private void simulate() {
        Settings settings;
        try {
            settings = settings();
        } catch (IllegalArgumentException e) {
            reject(e);
            return;
        }
        busy("Моделювання...");
        new SwingWorker<List<ServiceCentre.Result>, Void>() {
            @Override
            protected List<ServiceCentre.Result> doInBackground() {
                return ServiceCentre.replicate(settings.centre(), settings.runs(), settings.warmup(), settings.duration());
            }

            @Override
            protected void done() {
                idle();
                try {
                    show(settings, get());
                } catch (Exception e) {
                    status.setText("Помилка моделювання: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void show(Settings settings, List<ServiceCentre.Result> runResults) {
        ServiceCentre centre = settings.centre();
        status.setText(String.format(Locale.ROOT, "<html>Прогонів: %d, час моделювання %.0f хв%s</html>",
                runResults.size(), settings.duration(), overloadWarning(centre)));
        results.setRowCount(0);
        addResult("Час очікування W, хв", estimate(runResults, ServiceCentre.Result::getWaiting));
        addResult("Час перебування V, хв", estimate(runResults, ServiceCentre.Result::getSojourn));
        addResult("Пропускна здатність, заявок/хв", format(mean(runResults, ServiceCentre.Result::getThroughput)));
        for (int type = 1; type <= centre.types(); type++) {
            int t = type;
            addResult("Тип " + type + ": W, хв", estimate(runResults, r -> r.getWaitingByType().getOrDefault(t, Double.NaN)));
            addResult("Тип " + type + ": V, хв", estimate(runResults, r -> r.getSojournByType().getOrDefault(t, Double.NaN)));
        }
        double[] utilisation = new double[ServiceCentre.NODES.length];
        double[] timeValues = new double[TIMES.size()];
        timeValues[0] = mean(runResults, ServiceCentre.Result::getWaiting);
        timeValues[1] = mean(runResults, ServiceCentre.Result::getSojourn);
        for (int node = 0; node < ServiceCentre.NODES.length; node++) {
            int n = node;
            String name = ServiceCentre.NODES[node] + " (" + centre.getChannels()[node] + ")";
            utilisation[node] = mean(runResults, r -> r.getUtilisation()[n]);
            timeValues[2 + node] = mean(runResults, r -> r.getQueueWaiting()[n]);
            addResult(name + ": завантаженість U", format(utilisation[node]));
            addResult(name + ": середня черга L", format(mean(runResults, r -> r.getMeanQueue()[n])));
            addResult(name + ": очікування в черзі, хв", format(timeValues[2 + node]));
        }
        addRun(centre, utilisation, timeValues);
    }

    private void addRun(ServiceCentre centre, double[] utilisation, double[] timeValues) {
        String name = String.format(Locale.ROOT, "№%d: %s, %.2f хв", ++runCounter,
                Arrays.toString(centre.getChannels()), centre.getInterarrivalMean());
        if (runNames.size() == KEPT_RUNS) {
            String oldest = runNames.removeFirst();
            load.removeSeries(oldest);
            times.removeSeries(oldest);
        }
        runNames.addLast(name);
        load.addSeries(name, Arrays.asList(ServiceCentre.NODES), Arrays.stream(utilisation).boxed().toList());
        times.addSeries(name, TIMES, Arrays.stream(timeValues).boxed().toList());
        repaintCharts();
    }

    private void explore() {
        Settings settings;
        List<Double> levels = new ArrayList<>();
        Factor chosen = (Factor) factor.getSelectedItem();
        try {
            settings = settings();
            double first = number(from, "Від");
            double last = number(to, "До");
            double increment = number(step, "Крок");
            if (increment <= 0 || last < first || (last - first) / increment > 50) {
                throw new IllegalArgumentException("Потрібно: крок > 0, «До» ≥ «Від», не більше 50 точок");
            }
            for (double level = first; level <= last + 1e-9; level += increment) {
                levels.add(chosen.integer ? Math.round(level) : level);
            }
        } catch (IllegalArgumentException e) {
            reject(e);
            return;
        }
        SWEEP_SERIES.forEach(sweep::removeSeries);
        sweep.setXAxisTitle(chosen.label);
        busy("Дослідження впливу: " + chosen.label + "...");
        new SwingWorker<Void, double[]>() {
            private final List<double[]> points = new ArrayList<>();
            private final List<String> skipped = new ArrayList<>();

            @Override
            protected Void doInBackground() {
                for (double level : levels.stream().distinct().toList()) {
                    ServiceCentre centre = settings.centre().copy();
                    chosen.apply.accept(centre, level);
                    if (overloaded(centre)) {
                        skipped.add(format(level));
                        continue;
                    }
                    List<ServiceCentre.Result> runResults = ServiceCentre.replicate(centre, settings.runs(), settings.warmup(), settings.duration());
                    double[] waiting = Statistics.values(runResults, ServiceCentre.Result::getWaiting);
                    double[] sojourn = Statistics.values(runResults, ServiceCentre.Result::getSojourn);
                    publish(new double[]{level, Statistics.mean(waiting), halfWidth(waiting), Statistics.mean(sojourn), halfWidth(sojourn)});
                }
                return null;
            }

            @Override
            protected void process(List<double[]> chunk) {
                points.addAll(chunk);
                plotSweep(points);
            }

            @Override
            protected void done() {
                idle();
                try {
                    get();
                    status.setText(skipped.isEmpty() ? "Дослідження завершено"
                            : "Дослідження завершено; пропущено перевантажені значення (ρ ≥ 1): " + String.join(", ", skipped));
                } catch (Exception e) {
                    status.setText("Помилка моделювання: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void plotSweep(List<double[]> points) {
        for (int series = 0; series < SWEEP_SERIES.size(); series++) {
            int column = 1 + 2 * series;
            double[] x = points.stream().mapToDouble(p -> p[0]).toArray();
            double[] y = points.stream().mapToDouble(p -> p[column]).toArray();
            double[] error = points.stream().mapToDouble(p -> p[column + 1]).toArray();
            sweep.removeSeries(SWEEP_SERIES.get(series));
            sweep.addSeries(SWEEP_SERIES.get(series), x, y, error).setMarker(SeriesMarkers.CIRCLE);
        }
        repaintCharts();
    }

    private void clearCharts() {
        runNames.forEach(name -> {
            load.removeSeries(name);
            times.removeSeries(name);
        });
        runNames.clear();
        SWEEP_SERIES.forEach(sweep::removeSeries);
        repaintCharts();
    }

    private void repaintCharts() {
        chartPanels.forEach(panel -> {
            panel.revalidate();
            panel.repaint();
        });
    }

    private void busy(String text) {
        status.setText(text);
        actions.forEach(button -> button.setEnabled(false));
    }

    private void idle() {
        actions.forEach(button -> button.setEnabled(true));
    }

    private void reject(IllegalArgumentException e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), "Некоректні параметри", JOptionPane.ERROR_MESSAGE);
    }

    private void addResult(String name, String value) {
        results.addRow(new Object[]{name, value});
    }

    private double cell(int row, int column) {
        return parse(String.valueOf(types.getValueAt(row, column)), "Тип " + (row + 1) + ", «" + TYPE_COLUMNS[column] + "»");
    }

    private enum Factor {
        ADMINS("Адміністратори", true, (centre, value) -> centre.getChannels()[ServiceCentre.REGISTRATION] = (int) value),
        CONSULTANTS("Консультанти", true, (centre, value) -> centre.getChannels()[ServiceCentre.CONSULTATION] = (int) value),
        WINDOWS("Вікна оформлення", true, (centre, value) -> centre.getChannels()[ServiceCentre.PROCESSING] = (int) value),
        INTERARRIVAL("Середній інтервал надходження, хв", false, ServiceCentre::setInterarrivalMean);

        private final String label;
        private final boolean integer;
        private final ObjDoubleConsumer<ServiceCentre> apply;

        Factor(String label, boolean integer, ObjDoubleConsumer<ServiceCentre> apply) {
            this.label = label;
            this.integer = integer;
            this.apply = apply;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record Settings(ServiceCentre centre, double warmup, double duration, int runs) {
    }
}
