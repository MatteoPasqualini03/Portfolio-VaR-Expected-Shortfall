package it.univr.riskmanagement.backtesting;

import it.univr.riskmanagement.data.DataCollectionAndPlotting;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

/*
 Basel III "Traffic Light" backtesting of a VaR time series.

 Violation rule: a violation occurs at day t+1 when the realized loss
 exceeds the VaR forecast produced at the end of day t, i.e.
     actualReturns[t+1] < -varSeries[t].
 Under the profit sign convention used in the project, varSeries[t] is
 stored as a positive number (capital at risk).

 Alignment assumed by runAnnual / runRolling:
   - varSeries has length L = data.length - windowLength.
   - varSeries[t] is the forecast from the rolling window ending at
     index (windowLength - 1 + t) of the returns array, tested against
     actualReturns[windowLength + t].
   - Equivalently, callers pass actualReturnsAligned[t] = actualReturns[windowLength + t]
     and datesAligned[t] for the matching date.

 Basel III thresholds, expressed as violation percentages so that windows
 with fewer than 250 trading days remain comparable:
     GREEN  : rate <= 1.6%   (~ <= 4/250)
     YELLOW : 1.6% < rate < 3.6%
     RED    : rate >= 3.6%   (~ >= 10/250)
 */
public final class Backtesting {

	private static final String PLOTS_FOLDER = "plots";

	private static final double GREEN_MAX = 1.6;
	private static final double RED_MIN = 3.6;

	// Basel-standard rolling window for backtesting.
	private static final int ROLLING_BACKTEST_WINDOW = 250;

	private Backtesting() { }

	/* Traffic-light zones with associated colors. */
	public enum TrafficLight {
		GREEN(new Color(0x2E, 0xA0, 0x43)),
		YELLOW(new Color(0xE8, 0xB8, 0x00)),
		RED(new Color(0xC0, 0x2A, 0x2A));

		private final Color color;
		TrafficLight(Color color) { this.color = color; }
		public Color getColor() { return color; }
	}

	/* Annual backtest result: one entry per calendar year. */
	public record AnnualResult(int year, int violations, int days,
							   double violationPct, TrafficLight zone) {}

	/* Rolling backtest result: one entry per date after the bootstrap window. */
	public record RollingResult(LocalDate asOf, int violationsLast250,
								double violationPct, TrafficLight zone) {}

	/* Maps a violation percentage to a Basel III zone. */
	public static TrafficLight classify(double violationPct) {
		if (violationPct <= GREEN_MAX) return TrafficLight.GREEN;
		if (violationPct >= RED_MIN) return TrafficLight.RED;
		return TrafficLight.YELLOW;
	}

	/*
	 Annual backtest on [yearFrom, yearTo]. Years with no aligned data are skipped.
	 */
	public static List<AnnualResult> runAnnual(double[] varSeries,
											   double[] actualReturnsAligned,
											   LocalDate[] datesAligned,
											   int yearFrom, int yearTo) {
		int n = varSeries.length;
		List<AnnualResult> results = new ArrayList<>();

		for (int year = yearFrom; year <= yearTo; year++) {
			int violations = 0;
			int days = 0;
			for (int t = 0; t < n; t++) {
				if (datesAligned[t].getYear() != year) continue;
				days++;
				if (actualReturnsAligned[t] < -varSeries[t]) {
					violations++;
				}
			}
			if (days == 0) continue;
			double violationPct = 100.0 * violations / days;
			TrafficLight zone = classify(violationPct);
			results.add(new AnnualResult(year, violations, days, violationPct, zone));
		}
		return results;
	}

	/*
	 Rolling 250-day backtest over the full aligned series.
	 The first ROLLING_BACKTEST_WINDOW entries bootstrap the count; the first
	 output point is at index (ROLLING_BACKTEST_WINDOW - 1).
	 */
	public static List<RollingResult> runRolling(double[] varSeries,
												 double[] actualReturnsAligned,
												 LocalDate[] datesAligned) {
		int n = varSeries.length;
		List<RollingResult> results = new ArrayList<>();

		int[] indicator = new int[n];
		for (int t = 0; t < n; t++) {
			indicator[t] = (actualReturnsAligned[t] < -varSeries[t]) ? 1 : 0;
		}

		int runningCount = 0;
		for (int t = 0; t < ROLLING_BACKTEST_WINDOW && t < n; t++) {
			runningCount += indicator[t];
		}
		if (n >= ROLLING_BACKTEST_WINDOW) {
			double pct = 100.0 * runningCount / ROLLING_BACKTEST_WINDOW;
			results.add(new RollingResult(datesAligned[ROLLING_BACKTEST_WINDOW - 1],
					runningCount, pct, classify(pct)));

			for (int t = ROLLING_BACKTEST_WINDOW; t < n; t++) {
				runningCount += indicator[t];
				runningCount -= indicator[t - ROLLING_BACKTEST_WINDOW];
				double pctT = 100.0 * runningCount / ROLLING_BACKTEST_WINDOW;
				results.add(new RollingResult(datesAligned[t], runningCount, pctT, classify(pctT)));
			}
		}
		return results;
	}

	/* Prints the annual backtest report to stdout. */
	public static void printAnnualReport(String methodName, List<AnnualResult> results) {
		System.out.println();
		System.out.println("=== Basel III Traffic Light (Annual) - " + methodName + " ===");
		System.out.printf("%-6s | %-11s | %-5s | %-10s | %s%n",
				"Year", "Violations", "Days", "Viol. %", "Zone");
		System.out.println("-------|-------------|-------|------------|--------");
		for (AnnualResult r : results) {
			System.out.printf("%-6d | %-11d | %-5d | %-9.2f%% | %s%n",
					r.year(), r.violations(), r.days(), r.violationPct(), r.zone());
		}
	}

	/*
	 Bar chart of annual violation percentages, each bar colored by its
	 traffic-light zone.
	 */
	public static void plotAnnualReport(String methodName, List<AnnualResult> results) {
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		for (AnnualResult r : results) {
			dataset.addValue(r.violationPct(), "Violation %", Integer.toString(r.year()));
		}

		String title = "Basel III Backtest (Annual) - " + methodName;
		JFreeChart chart = ChartFactory.createBarChart(
				title, "Year", "Violation percentage (%)", dataset,
				PlotOrientation.VERTICAL, false, true, false);

		CategoryPlot plot = chart.getCategoryPlot();
		BarRenderer renderer = new BarRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public java.awt.Paint getItemPaint(int row, int column) {
				return results.get(column).zone().getColor();
			}
		};
		renderer.setShadowVisible(false);

		// always label each bar so zero-violation years remain readable
		renderer.setDefaultItemLabelGenerator(
				new StandardCategoryItemLabelGenerator("{2}%", new DecimalFormat("0.00")));
		renderer.setDefaultItemLabelsVisible(true);
		renderer.setDefaultPositiveItemLabelPosition(
				new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER));

		plot.setRenderer(renderer);

		NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
		yAxis.setLowerBound(0.0);
		double maxPct = 0.0;
		for (AnnualResult r : results) maxPct = Math.max(maxPct, r.violationPct());
		yAxis.setUpperBound(Math.max(maxPct + 0.6, RED_MIN + 0.6));

		saveChart(chart, title);
	}

	/*
	 Line chart of the rolling 250-day violation percentage with traffic-light
	 zone bands as horizontal markers.
	 */
	public static void plotRollingReport(String methodName, List<RollingResult> results) {
		TimeSeries series = new TimeSeries("Rolling 250-day Violation %");
		for (RollingResult r : results) {
			LocalDate d = r.asOf();
			series.add(new Day(d.getDayOfMonth(), d.getMonthValue(), d.getYear()),
					r.violationPct());
		}
		TimeSeriesCollection dataset = new TimeSeriesCollection(series);

		String title = "Basel III Backtest (Rolling 250d) - " + methodName;
		JFreeChart chart = ChartFactory.createTimeSeriesChart(
				title, "Date", "Violation percentage (%)", dataset,
				false, true, false);

		XYPlot plot = chart.getXYPlot();
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
		renderer.setSeriesPaint(0, Color.BLACK);
		renderer.setSeriesStroke(0, DataCollectionAndPlotting.STROKE_SOLID);
		plot.setRenderer(renderer);

		NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
		yAxis.setLowerBound(0.0);
		double upper = Math.max(yAxis.getUpperBound(), RED_MIN + 1.0);
		yAxis.setUpperBound(upper);

		IntervalMarker greenBand = new IntervalMarker(0.0, GREEN_MAX);
		greenBand.setPaint(new Color(TrafficLight.GREEN.getColor().getRed(),
				TrafficLight.GREEN.getColor().getGreen(),
				TrafficLight.GREEN.getColor().getBlue(), 60));
		plot.addRangeMarker(greenBand);

		IntervalMarker yellowBand = new IntervalMarker(GREEN_MAX, RED_MIN);
		yellowBand.setPaint(new Color(TrafficLight.YELLOW.getColor().getRed(),
				TrafficLight.YELLOW.getColor().getGreen(),
				TrafficLight.YELLOW.getColor().getBlue(), 60));
		plot.addRangeMarker(yellowBand);

		IntervalMarker redBand = new IntervalMarker(RED_MIN, upper);
		redBand.setPaint(new Color(TrafficLight.RED.getColor().getRed(),
				TrafficLight.RED.getColor().getGreen(),
				TrafficLight.RED.getColor().getBlue(), 60));
		plot.addRangeMarker(redBand);

		saveChart(chart, title);
	}

	/* Saves a chart as PNG under PLOTS_FOLDER. */
	private static void saveChart(JFreeChart chart, String title) {
		try {
			File plotsDir = new File(PLOTS_FOLDER);
			if (!plotsDir.exists()) plotsDir.mkdirs();
			String filename = title.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".png";
			File outputFile = new File(plotsDir, filename);
			ChartUtils.saveChartAsPNG(outputFile, chart, 800, 600);
			System.out.println("Plot saved: " + outputFile.getAbsolutePath());
		} catch (IOException e) {
			System.err.println("Error saving plot: " + e.getMessage());
		}
	}

}
