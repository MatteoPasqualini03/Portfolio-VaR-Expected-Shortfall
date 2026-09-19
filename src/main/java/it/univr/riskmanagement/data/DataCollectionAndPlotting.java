package it.univr.riskmanagement.data;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

/*
 Excel I/O and chart rendering.
 Reads prices/dates from .xlsx (Apache POI) and saves charts as PNG
 (JFreeChart, headless, no popup windows). One- or multi-series plots
 with configurable colors and stroke styles.
 */
public class DataCollectionAndPlotting {

	// headless AWT: render PNGs without a display
	static {
		System.setProperty("java.awt.headless", "true");
	}

	// output folder for PNG charts
	private static final String PLOTS_FOLDER = "plots";

	// Line strokes for multi-series charts.
	public static final Stroke STROKE_SOLID = new BasicStroke(2.0f);
	public static final Stroke STROKE_DASHED = new BasicStroke(
		2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
		1.0f, new float[]{10.0f, 6.0f}, 0.0f);
	public static final Stroke STROKE_DOTTED = new BasicStroke(
		2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
		1.0f, new float[]{3.0f, 6.0f}, 0.0f);
	public static final Stroke STROKE_DASH_DOT = new BasicStroke(
		2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
		1.0f, new float[]{10.0f, 4.0f, 3.0f, 4.0f}, 0.0f);

	/* Prices of the first asset bundled in resources (Asset1.xlsx). */
	public static double[] getHistoricalPricesStock1() throws IOException {
		return getHistoricalPrices("/Asset1.xlsx");
	}

	/* Prices of the second asset bundled in resources (Asset2.xlsx). */
	public static double[] getHistoricalPricesStock2() throws IOException {
		return getHistoricalPrices("/Asset2.xlsx");
	}

	/* Dates from the first asset's resource. */
	public static LocalDate[] getDates() throws IOException {
		return getDates("/Asset1.xlsx");
	}

	/*
	 Plots one or more time series on a single chart and saves it as PNG.
	 Series share the same x-axis (dates); each has its own label, color and stroke.
	 */
	public static void plotData(LocalDate[] dates, double[][] dataSeries,
			String[] labels, Color[] colors, Stroke[] strokes,
			String title, String yAxisLabel) {

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		for (int s = 0; s < dataSeries.length; s++) {
			TimeSeries ts = new TimeSeries(labels[s]);
			for (int i = 0; i < dates.length; i++) {
				LocalDate date = dates[i];
				ts.add(new Day(date.getDayOfMonth(), date.getMonthValue(), date.getYear()),
					dataSeries[s][i]);
			}
			dataset.addSeries(ts);
		}

		JFreeChart chart = ChartFactory.createTimeSeriesChart(
				title, "Date", yAxisLabel, dataset,
				true, true, false);

		// apply per-series color and stroke
		XYPlot plot = chart.getXYPlot();
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
		for (int s = 0; s < dataSeries.length; s++) {
			renderer.setSeriesPaint(s, colors[s]);
			renderer.setSeriesStroke(s, strokes[s]);
		}
		plot.setRenderer(renderer);

		displayAndSaveChart(chart, title);
	}

	/*
	 Reads dates from the first column of an Excel resource (yyyy-MM-dd).
	 */
	public static LocalDate[] getDates(String resourcePath) throws IOException {
		List<LocalDate> datesList = new ArrayList<>();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		try (InputStream is = DataCollectionAndPlotting.class.getResourceAsStream(resourcePath);
		     Workbook workbook = new XSSFWorkbook(is)) {

			Sheet sheet = workbook.getSheetAt(0);

			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null) continue;

				Cell cell = row.getCell(0);
				if (cell != null) {
					try {
						String cellValue = cell.getStringCellValue().trim();
						LocalDate date = LocalDate.parse(cellValue, formatter);
						datesList.add(date);
					} catch (DateTimeParseException e) {
						System.out.println("Invalid date string at row " + (i + 1) + ": " + cell.getStringCellValue());
					}
				} else {
					System.out.println("Empty cell at row " + (i + 1));
				}
			}
		}
		return datesList.toArray(new LocalDate[0]);
	}

	/*
	 Reads prices from the second column (index 1) of an Excel resource.
	 */
	public static double[] getHistoricalPrices(String resourcePath) throws IOException {
		List<Double> pricesList = new ArrayList<>();

		try (InputStream is = DataCollectionAndPlotting.class.getResourceAsStream(resourcePath);
		     Workbook workbook = new XSSFWorkbook(is)) {

			Sheet sheet = workbook.getSheetAt(0);
			int columnIndex = 1;

			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null) continue;

				Cell cell = row.getCell(columnIndex);
				if (cell != null && cell.getCellType() == CellType.NUMERIC) {
					pricesList.add(cell.getNumericCellValue());
				} else if (cell != null && cell.getCellType() == CellType.STRING) {
					try {
						pricesList.add(Double.parseDouble(cell.getStringCellValue().replace("$", "")));
					} catch (NumberFormatException e) {
						System.out.println("Invalid value at row " + (i + 1));
					}
				}
			}
		}

		return pricesList.stream().mapToDouble(Double::doubleValue).toArray();
	}

	/*
	 Saves the chart as a PNG under PLOTS_FOLDER (title becomes the filename).
	 */
	private static void displayAndSaveChart(JFreeChart chart, String title) {
		try {
			File plotsDir = new File(PLOTS_FOLDER);
			if (!plotsDir.exists()) {
				plotsDir.mkdirs();
			}
			String filename = title.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".png";
			File outputFile = new File(plotsDir, filename);
			ChartUtils.saveChartAsPNG(outputFile, chart, 800, 600);
			System.out.println("Plot saved: " + outputFile.getAbsolutePath());
		} catch (IOException e) {
			System.err.println("Error saving plot: " + e.getMessage());
		}
	}
}
