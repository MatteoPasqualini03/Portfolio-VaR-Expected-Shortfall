package it.univr.riskmanagement.riskmeasures;

import it.univr.riskmanagement.data.DataCollectionAndPlotting;

import java.awt.Color;
import java.awt.Stroke;
import java.time.LocalDate;
import java.util.Arrays;

/*
 Facade over the concrete risk-measure implementations. Three layers:
   1. COMPUTE — return rolling-window series in DECIMAL scale (e.g. 0.025 = 2.5%).
   2. SCALE   — toPercentage(series) and toMonetary(series, portfolioValue).
   3. PLOT    — pure plotting helpers (per-method, comparison, etc.).

 Color convention: VaR = BLACK, ES = RED.
 */
public class RiskMeasureFacade {

	private static final HistoricalRiskMeasure HISTORICAL = new HistoricalRiskMeasure();
	private static final NormalRiskMeasure NORMAL = new NormalRiskMeasure();

	// ===== COMPUTE — scalar on the full sample (ad-hoc use) ============

	public static double computeHistoricalVaR(double[] data, double alpha) {
		return HISTORICAL.computeVaR(data, alpha);
	}

	public static double computeHistoricalES(double[] data, double beta) {
		return HISTORICAL.computeES(data, beta);
	}

	public static double computeNormalVaR(double[] data, double alpha) {
		return NORMAL.computeVaR(data, alpha);
	}

	public static double computeNormalES(double[] data, double beta) {
		return NORMAL.computeES(data, beta);
	}

	// ===== COMPUTE — rolling-window series (decimal scale) =============

	public static double[] iterateHistoricalVaR(double[] data, double alpha, int windowLength) {
		return HISTORICAL.iterateVaR(data, alpha, windowLength);
	}

	public static double[] iterateHistoricalES(double[] data, double beta, int windowLength) {
		return HISTORICAL.iterateES(data, beta, windowLength);
	}

	public static double[] iterateNormalVaR(double[] data, double alpha, int windowLength) {
		return NORMAL.iterateVaR(data, alpha, windowLength);
	}

	public static double[] iterateNormalES(double[] data, double beta, int windowLength) {
		return NORMAL.iterateES(data, beta, windowLength);
	}

	/*
	 Rolling-window Monte Carlo VaR and ES (INDEPENDENT assets, n-asset)
	 in single-pass mode: one MC simulation per window yields both measures.
	 Returns { varSeries, esSeries } in decimal scale.
	 */
	public static double[][] iterateMonteCarloVaRAndES(double[] portfolioReturns,
			double[][] logReturnsByAsset, double[] budgets,
			double alpha, double beta, int windowLength, int numSimulations) {
		MonteCarloRiskMeasure mc = new MonteCarloRiskMeasure(
				logReturnsByAsset, budgets, numSimulations);
		return mc.iterateVaRAndES(portfolioReturns, alpha, beta, windowLength);
	}

	/* 2-asset overload of iterateMonteCarloVaRAndES. */
	public static double[][] iterateMonteCarloVaRAndES(double[] portfolioReturns,
			double[] logReturnsAsset1, double[] logReturnsAsset2,
			double budget1, double budget2,
			double alpha, double beta, int windowLength, int numSimulations) {
		return iterateMonteCarloVaRAndES(portfolioReturns,
				new double[][] { logReturnsAsset1, logReturnsAsset2 },
				new double[] { budget1, budget2 },
				alpha, beta, windowLength, numSimulations);
	}

	/*
	 Rolling-window Monte Carlo VaR/ES with CORRELATED assets (Cholesky).
	 Restricted to TWO assets. Returns { varSeries, esSeries }.
	 */
	public static double[][] iterateCorrelatedMonteCarloVaRAndES(double[] portfolioReturns,
			double[] logReturnsAsset1, double[] logReturnsAsset2,
			double budget1, double budget2,
			double alpha, double beta, int windowLength, int numSimulations) {
		CorrelatedMonteCarloRiskMeasure mc = new CorrelatedMonteCarloRiskMeasure(
				logReturnsAsset1, logReturnsAsset2,
				budget1, budget2, numSimulations);
		return mc.iterateVaRAndES(portfolioReturns, alpha, beta, windowLength);
	}

	// ===== SCALE — decimal -> percentage or monetary ===================

	/* Decimal series -> percentage points (multiply by 100). */
	public static double[] toPercentage(double[] series) {
		double[] scaled = new double[series.length];
		for (int i = 0; i < series.length; i++) {
			scaled[i] = series[i] * 100.0;
		}
		return scaled;
	}

	/* Decimal series -> monetary terms (multiply by portfolio value). */
	public static double[] toMonetary(double[] series, double portfolioValue) {
		double[] scaled = new double[series.length];
		for (int i = 0; i < series.length; i++) {
			scaled[i] = series[i] * portfolioValue;
		}
		return scaled;
	}

	// ===== PLOT — pre-computed, pre-scaled series ======================
	// 4th comparison color and losses color.
		private static final Color COLOR_DARK_GREEN = new Color(0, 150, 0);
		private static final Color COLOR_LOSSES = Color.GRAY;
		
	/* VaR (black) overlaid with realized losses (gray). */
	public static void plotVaR(LocalDate[] dates, double[] varSeries, double[] losses,
			String title, String yAxisLabel) {
		DataCollectionAndPlotting.plotData(dates,
			new double[][] { varSeries, losses },
			new String[] { title, "Realized Losses" },
			new Color[] { Color.BLACK, COLOR_LOSSES },
			new Stroke[] {
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID
			},
			title, yAxisLabel);
	}

	/* ES (red) overlaid with realized losses (gray). */
	public static void plotES(LocalDate[] dates, double[] esSeries, double[] losses,
			String title, String yAxisLabel) {
		DataCollectionAndPlotting.plotData(dates,
			new double[][] { esSeries, losses },
			new String[] { title, "Realized Losses" },
			new Color[] { Color.RED, COLOR_LOSSES },
			new Stroke[] {
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID
			},
			title, yAxisLabel);
	}

	/* VaR (black) and ES (red) from the same method on one chart. */
	public static void plotCombined(LocalDate[] dates,
			double[] varSeries, double[] esSeries, double[] losses,
			String methodName, double alpha, double beta, String yAxisLabel) {
		String title = methodName + " VaR and ES";
		DataCollectionAndPlotting.plotData(dates,
			new double[][] { varSeries, esSeries, losses },
			new String[] {
				methodName + " VaR (alpha=" + alpha + ")",
				methodName + " ES (beta=" + beta + ")",
				"Realized Losses"
			},
			new Color[] { Color.BLACK, Color.RED, COLOR_LOSSES },
			new Stroke[] {
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID
			},
			title, yAxisLabel);
	}

	/*
	 VaR comparison across all 4 methods:
	   Historical=BLUE, Normal=BLACK, MC Independent=RED, MC Correlated=DARK GREEN.
	 */
	public static void plotCompareVaR(LocalDate[] dates,
			double[] histVaR, double[] normVaR, double[] mcVaR, double[] mcCorrVaR,
			double[] losses, double alpha, String yAxisLabel) {
		String title = "VaR Comparison - All Methods (alpha=" + alpha + ")";
		DataCollectionAndPlotting.plotData(dates,
			new double[][] { histVaR, normVaR, mcVaR, mcCorrVaR, losses },
			new String[] { "Historical VaR", "Normal VaR", "MC Independent VaR", "MC Correlated VaR", "Realized Losses" },
			new Color[] { Color.BLUE, Color.BLACK, Color.RED, COLOR_DARK_GREEN, COLOR_LOSSES },
			new Stroke[] {
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID
			},
			title, yAxisLabel);
	}

	/* ES comparison across all 4 methods (same color convention as plotCompareVaR). */
	public static void plotCompareES(LocalDate[] dates,
			double[] histES, double[] normES, double[] mcES, double[] mcCorrES,
			double[] losses, double beta, String yAxisLabel) {
		String title = "ES Comparison - All Methods (beta=" + beta + ")";
		DataCollectionAndPlotting.plotData(dates,
			new double[][] { histES, normES, mcES, mcCorrES, losses },
			new String[] { "Historical ES", "Normal ES", "MC Independent ES", "MC Correlated ES", "Realized Losses" },
			new Color[] { Color.BLUE, Color.BLACK, Color.RED, COLOR_DARK_GREEN, COLOR_LOSSES },
			new Stroke[] {
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID
			},
			title, yAxisLabel);
	}

	/* Independent vs. Correlated MC VaR overlay (black, solid vs. dashed). */
	public static void plotCompareMonteCarloVaR(LocalDate[] dates,
			double[] independentVaR, double[] correlatedVaR, double[] losses,
			double alpha, String yAxisLabel) {
		String title = "Monte Carlo VaR - Independent vs Correlated (alpha=" + alpha + ")";
		DataCollectionAndPlotting.plotData(dates,
			new double[][] { independentVaR, correlatedVaR, losses },
			new String[] { "MC Independent VaR", "MC Correlated VaR", "Realized Losses" },
			new Color[] { Color.BLACK, Color.BLACK, COLOR_LOSSES },
			new Stroke[] {
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_DASHED,
				DataCollectionAndPlotting.STROKE_SOLID
			},
			title, yAxisLabel);
	}

	/* Independent vs. Correlated MC ES overlay (red, solid vs. dashed). */
	public static void plotCompareMonteCarloES(LocalDate[] dates,
			double[] independentES, double[] correlatedES, double[] losses,
			double beta, String yAxisLabel) {
		String title = "Monte Carlo ES - Independent vs Correlated (beta=" + beta + ")";
		DataCollectionAndPlotting.plotData(dates,
			new double[][] { independentES, correlatedES, losses },
			new String[] { "MC Independent ES", "MC Correlated ES", "Realized Losses" },
			new Color[] { Color.RED, Color.RED, COLOR_LOSSES },
			new Stroke[] {
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_DASHED,
				DataCollectionAndPlotting.STROKE_SOLID
			},
			title, yAxisLabel);
	}

	// ===== HELPERS =====================================================

	/* Date subset aligned with a rolling-window series of length (n - windowLength). */
	public static LocalDate[] alignDates(LocalDate[] dates, int windowLength) {
		return Arrays.copyOfRange(dates, windowLength, dates.length);
	}

}
