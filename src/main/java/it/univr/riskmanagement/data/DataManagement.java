package it.univr.riskmanagement.data;

import java.awt.Color;
import java.awt.Stroke;
import java.io.IOException;
import java.util.Arrays;

import java.time.LocalDate;

/*
 Portfolio data layer: loads prices/dates for n assets and derives the
 series used by the rest of the project (per-asset log returns, portfolio
 value V(t), portfolio relative returns, portfolio log returns, realized losses).

 Sign convention: profit returns (positive = gain, negative = loss).
 Date alignment: dates are read from the first resource and assumed shared
 across all assets (same trading calendar).
 */

public class DataManagement {

	// Historical prices, one row per asset.
	private final double[][] pricesByAsset;
	// Trading-day calendar shared by all assets.
	private final LocalDate[] dates;

	/* Default constructor: two assets from /Asset1.xlsx and /Asset2.xlsx. */
	public DataManagement() throws IOException {
		this(new String[] { "/Asset1.xlsx", "/Asset2.xlsx" });
	}

	/*
	 Loads n assets from the given classpath resources.
	 Dates taken from the first resource and assumed aligned across all assets.
	 */
	public DataManagement(String[] assetResources) throws IOException {
		int numAssets = assetResources.length;
		this.pricesByAsset = new double[numAssets][];
		for (int a = 0; a < numAssets; a++) {
			pricesByAsset[a] = DataCollectionAndPlotting.getHistoricalPrices(assetResources[a]);
		}
		this.dates = DataCollectionAndPlotting.getDates(assetResources[0]);
	}

	public LocalDate[] getDates() { return dates; }

	public int getNumAssets() { return pricesByAsset.length; }

	/* Prices of asset at index a (0-based). */
	public double[] getPricesByAsset(int a) { return pricesByAsset[a]; }

	/* Full price matrix, one row per asset. */
	public double[][] getAllPrices() { return pricesByAsset; }

	/* Prices of asset 1 (= index 0). */
	public double[] getPricesAsset1() { return pricesByAsset[0]; }

	/* Prices of asset 2 (= index 1). */
	public double[] getPricesAsset2() { return pricesByAsset[1]; }

	/*
	 Log returns of a single price series: r_log(t) = ln(P(t+1)/P(t)),
	 t = 0, ..., n-2. Length n-1.
	 */
	public double[] getLogReturns(double[] prices) {
		int n = prices.length;
		double[] logReturns = new double[n - 1];
		for (int t = 0; t < n - 1; t++) {
			logReturns[t] = Math.log(prices[t + 1] / prices[t]);
		}
		return logReturns;
	}

	/* Per-asset log returns: n-asset version of getLogReturns. */
	public double[][] getAllLogReturns() {
		double[][] result = new double[pricesByAsset.length][];
		for (int a = 0; a < pricesByAsset.length; a++) {
			result[a] = getLogReturns(pricesByAsset[a]);
		}
		return result;
	}

	/*
	 Portfolio value V(t) = sum_i n_i * P_i(t), with units n_i fixed at t=0
	 by n_i = weights[i] * totalBudget / P_i(0). Length = number of dates.
	 */
	public double[] getPortfolioValue(double[][] assetPrices, double[] weights, double totalBudget) {
		int numAssets = assetPrices.length;
		int n = assetPrices[0].length;

		double[] units = new double[numAssets];
		for (int i = 0; i < numAssets; i++) {
			units[i] = weights[i] * totalBudget / assetPrices[i][0];
		}

		double[] portfolioValue = new double[n];
		for (int t = 0; t < n; t++) {
			double vt = 0.0;
			for (int i = 0; i < numAssets; i++) {
				vt += units[i] * assetPrices[i][t];
			}
			portfolioValue[t] = vt;
		}
		return portfolioValue;
	}

	/*
	 Portfolio relative returns: r_rel(t) = V(t+1)/V(t) - 1, length n-1.
	 Scale-invariant in totalBudget, so V(t) is built with budget = 1.
	 */
	public double[] getPortfolioRelativeReturns(double[][] assetPrices, double[] weights) {
		double[] portfolioValue = getPortfolioValue(assetPrices, weights, 1.0);
		int n = portfolioValue.length;
		double[] relativeReturns = new double[n - 1];
		for (int t = 0; t < n - 1; t++) {
			relativeReturns[t] = portfolioValue[t + 1] / portfolioValue[t] - 1.0;
		}
		return relativeReturns;
	}

	/* Portfolio log returns from relative returns: log(1 + r_rel(t)). */
	public double[] getPortfolioLogReturns(double[] relativeReturns) {
		double[] logReturns = new double[relativeReturns.length];
		for (int t = 0; t < relativeReturns.length; t++)
			logReturns[t] = Math.log(1.0 + relativeReturns[t]);
		return logReturns;
	}

	/* Realized losses: max(0, -r). Gains map to 0. */
	public double[] getPositiveLosses(double[] returns) {
		double[] losses = new double[returns.length];
		for (int i = 0; i < returns.length; i++)
			losses[i] = Math.max(0.0, -returns[i]);
		return losses;
	}

	/* Plots the prices of one asset. */
	public void plotPrices(double[] prices, String label) {
		DataCollectionAndPlotting.plotData(dates,
			new double[][] { prices },
			new String[] { label },
			new Color[] { Color.BLACK },
			new Stroke[] { DataCollectionAndPlotting.STROKE_SOLID },
			label, label);
	}

	/* Plots the portfolio value V(t) (starts at totalBudget). */
	public void plotPortfolioValue(double[][] assetPrices, double[] weights, double totalBudget) {
		double[] portfolioValue = getPortfolioValue(assetPrices, weights, totalBudget);
		DataCollectionAndPlotting.plotData(dates,
			new double[][] { portfolioValue },
			new String[] { "Portfolio Value BuyAndHold" },
			new Color[] { Color.BLACK },
			new Stroke[] { DataCollectionAndPlotting.STROKE_SOLID },
			"Portfolio Value BuyAndHold", "Value (USD)");
	}

	/*
	 Plots portfolio returns (black) and log-returns (red) on the same chart.
	 Returns the relative-return series so the caller can reuse it.
	 */
	public double[] plotPortfolioRelativeReturns(double[][] assetPrices, double[] weights) {
		double[] relativeReturns = getPortfolioRelativeReturns(assetPrices, weights);
		double[] logReturns = getPortfolioLogReturns(relativeReturns);
		LocalDate[] datesToPlot = Arrays.copyOfRange(dates, 1, dates.length);
		DataCollectionAndPlotting.plotData(datesToPlot,
			new double[][] { relativeReturns, logReturns },
			new String[] { "Portfolio Returns", "Portfolio Log Returns" },
			new Color[] { Color.BLACK, Color.RED },
			new Stroke[] {
				DataCollectionAndPlotting.STROKE_SOLID,
				DataCollectionAndPlotting.STROKE_SOLID
			},
			"Portfolio Returns and Log Returns", "Return");
		return relativeReturns;
	}

}
