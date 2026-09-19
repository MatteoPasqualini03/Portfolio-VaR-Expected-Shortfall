package it.univr.riskmanagement.twoassets;

import it.univr.riskmanagement.backtesting.Backtesting;
import it.univr.riskmanagement.riskmeasures.*;
import it.univr.riskmanagement.data.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/*
 ============================================================================
 PROJECT DRIVER -- end-to-end VaR/ES analysis on a portfolio of n assets.
 ============================================================================

 What the assignment requires (per Lecture 11 Java Project):
   - Two low-correlation assets, equal-weighted, 1-day horizon.
   - VaR at alpha = 1% (Basel II), ES at beta = 2.5% (Basel III).
   - Time window 2019-01-01 to 2025-12-31, rolling window n = 250.
   - Three estimators: Historical, Normal, Monte Carlo (independent) with
     100,000 simulations and Normal log-returns.
   - Use historical estimators for the Normal/MC parameters.

 What this driver adds on top:
   - Generalization to ANY number of assets for Historical / Normal /
     Independent MC. Just edit ASSET_RESOURCES / ASSET_NAMES / BUDGETS.
   - A CORRELATED Monte Carlo engine (closed-form 2x2 Cholesky),
     automatically skipped when n != 2.
   - Output in two scales: percentage of portfolio value (Pct) AND
     monetary value (USD).
   - Basel III "Traffic Light" backtesting: annual report (one bar per
     calendar year) and rolling 250-day violation series.
   - Cross-method comparison charts (all 4 methods on one plot) and
     Independent-vs-Correlated MC overlays.

 Demo portfolio used in this run:
   - SPY (S&P 500 ETF) and GLD (Gold ETF), USD-denominated, traded on US exchanges.
   - The SPY/GLD pair is chosen for its historically low (often negative)
     correlation, so that the diversification effect modeled by the
     Cholesky MC is visible in the results.

 Conventions used throughout the driver:
   - Sign: profit returns (positive = gain, negative = loss). Risk measures
     are returned as POSITIVE numbers, so larger value = more risk.
   - Color: VaR = BLACK, ES = RED, realized losses = GRAY.
   - Output: all charts are saved as PNG under the "plots/" folder. No
     popup windows are opened.

 Rolling-window math: given x return observations and a window W, the
 rolling risk series has length x - W. The first W observations build the
 first window, so the first estimate is available at day W+1. In this run,
 1763 returns - 250 = 1513 daily risk estimates.

 ============================================================================
 OBSERVED RESULTS (SPY 50% / GLD 50%, window 2019-2025) -- see plots/ folder
 ============================================================================

 1. PORTFOLIO VALUE AND RETURNS (Portfolio_Value_BuyAndHold.png)
    Buy-and-hold grows from 100 to ~315 USD over 7 years (+215%). Two
    adverse events stand out: the COVID crash (Feb-Mar 2020, sharp drawdown
    then full recovery by end-2020) and the 2022 drawdown (~160 -> ~130 USD,
    driven by aggressive Fed rate hikes and high inflation; recovery starts
    in 2023). From 2023 the portfolio enters a strong uptrend, closing 2025
    near 315 USD. Returns look more stable and stationary than prices, with
    two big spikes and small volatility clusters typical of equity and
    commodity markets.

 2. VaR AND ES DYNAMICS (Historical_VaR_and_ES.png, VaR_Comparison_*, ES_Comparison_*)
    All methods share the same qualitative pattern but differ in level and
    smoothness:
      - COVID spike (early 2020): Historical VaR ~3.8%, ES ~4.5% (largest
        shock in the sample). Normal/MC peak ~2.8% VaR -- smoother because
        parametric.
      - 2021 trough: once the COVID window rotates out of the 250-day
        lookback, Historical drops to ~2.2% VaR and Normal/MC to ~1.2%.
      - 2022 peak (~2.4-2.6% VaR): rate-hike / inflation regime. Historical
        reacts more slowly (staircase pattern); Normal/MC track the new
        volatility faster.
      - 2023-2024 trough (~1.2% VaR): calmest sub-period, both assets up,
        low realized volatility.
      - Shape: Historical VaR has a pronounced staircase pattern
        (non-parametric, rank-based); Normal and MC are smooth and track
        each other closely.

 3. INDEPENDENT vs. CORRELATED MC (Monte_Carlo_*_Independent_vs_Correlated_*)
    The two MC curves are close (< 5% spread at most dates), but Independent
    VaR is systematically higher than Correlated VaR at every point.
    Reason: SPY and GLD have a historically low or negative correlation
    (flight-to-safety hedge). The Cholesky model captures this
    diversification benefit and reports lower capital requirements; the
    independent model ignores it and overstates risk by ~5-10%.

 4. BASEL III ANNUAL TRAFFIC LIGHT (Basel_III_Backtest__Annual__*.png)
    Common finding: 2023 has 0 violations -> GREEN across ALL methods. The
    rolling VaR was still inflated by 2022's high volatility while 2023 was
    calm, so no realized daily loss exceeded the threshold.

    Historical (most conservative): never enters RED. Worst year 2022
      (~2.8% YELLOW). Sequence:
        2020 ~2.0% YELLOW | 2021 ~0.4% GREEN  | 2022 ~2.8% YELLOW
        2023 0.00% GREEN  | 2024 ~2.0% YELLOW | 2025 ~2.0% YELLOW.
      The staircase VaR absorbs shocks gradually; losses rarely exceed a
      lagged worst-case quantile (the known "ghost effect" of the method).

    Normal: RED in 2022 (~4.0%) and 2025 (~3.6%); border in 2020 (~3.6%
      YELLOW):
        2020 ~3.6% YELLOW | 2021 ~0.8% GREEN  | 2022 ~4.0% RED
        2023 0.00% GREEN  | 2024 ~2.4% YELLOW | 2025 ~3.6% RED.
      The Gaussian assumption underestimates fat-tail risk during crisis
      periods.

    MC Independent (least conservative): RED in 2022 (~4.8%, the worst
      across all methods) and 2025 (~3.6%):
        2020 ~3.2% YELLOW | 2021 ~1.6% GREEN  | 2022 ~4.8% RED
        2023 0.00% GREEN  | 2024 ~2.4% YELLOW | 2025 ~3.6% RED.
      Ignoring the negative SPY-GLD correlation leads to underestimated VaR
      and more violations during joint stress events.

    MC Correlated (Cholesky): nearly identical to Normal (same empirical
      covariance):
        2020 ~3.6% YELLOW | 2021 ~0.8% GREEN  | 2022 ~4.0% RED
        2023 0.00% GREEN  | 2024 ~2.4% YELLOW | 2025 ~3.6% RED.
      The difference with MC Independent mostly shows up when the hedge is
      active (equity sell-offs).

 5. BASEL III ROLLING 250-DAY (Basel_III_Backtest__Rolling_250d__*.png)
    The rolling profiles refine the annual picture:
      - Historical: never reaches RED. Peaks dic-2020 (~2.0%) and giu-2022
        (~2.8%); deep GREEN valley in 2023.
      - Normal and Correlated MC (nearly identical profiles): RED at
        dic-2020 (~3.6%), back to GREEN by mid-2021; second RED episode
        2022 peak ~4.4%, full GREEN recovery in 2023; third RED 2024-2025
        (~4.4% peak), end of sample in YELLOW/RED.
      - MC Independent: most volatile profile, peaks ~5.5% RED in late 2022
        -- the single worst reading across all methods and dates. Confirms
        that ignoring the SPY-GLD diversification systematically understates VaR.
 */
public class Test2Assets {

	// ---------- Analysis parameters --------------------------------------

	// Rolling-window length (250 trading days ~ 1 year).
	private static final int WINDOW_LENGTH = 250;
	// VaR significance level (Basel II).
	private static final double ALPHA = 0.01;
	// ES significance level (Basel III).
	private static final double BETA = 0.025;

	// ---------- Portfolio composition (n assets) -------------------------
	//
	// One entry per asset; lengths must match.
	//   ASSET_RESOURCES : classpath paths of the Excel files (leading '/').
	//   ASSET_NAMES     : human-readable names used in plot titles.
	//   BUDGETS         : USD invested in each asset at t=0; weights are
	//                     derived as BUDGETS[a] / sum(BUDGETS) and stay
	//                     constant for the entire analysis.
	//
	// To run the project on n > 2 assets, add entries here; the Correlated
	// MC step will be skipped automatically.
	private static final String[] ASSET_RESOURCES = { "/Asset1.xlsx", "/Asset2.xlsx" };
	private static final String[] ASSET_NAMES     = { "SPY",          "GLD"          };
	private static final double[] BUDGETS         = {  120.0,           50.0          };

	// ---------- Backtesting ----------------------------------------------

	// Calendar years covered by the Basel III annual report.
	private static final int YEAR_FROM = 2020;
	private static final int YEAR_TO = 2025;

	// ---------- Y-axis labels --------------------------------------------

	private static final String Y_LABEL_PERCENT = "Risk measure (%)";
	// Currency is USD because the demo portfolio (SPY, GLD) is USD-denominated.
	private static final String Y_LABEL_USD = "Capital (USD)";

	public static void main(String[] args) throws IOException {

		// Number of Monte Carlo simulations per rolling window.
		// Higher = more precision, slower runs. 100,000 gives good
		// convergence for a 1% quantile.
		int s = 100_000;

		// =================================================================
		// 1. LOAD DATA AND BUILD THE PORTFOLIO
		// =================================================================

		DataManagement tester = new DataManagement(ASSET_RESOURCES);
		int numAssets = tester.getNumAssets();

		// sanity-check plots of raw prices, one per asset
		plotRawPrices(tester);

		// price matrix consumed by the portfolio builder
		double[][] assetPrices = tester.getAllPrices();

		// fixed portfolio weights derived from BUDGETS
		double totalBudget = 0.0;
		for (double b : BUDGETS) totalBudget += b;
		double[] weights = new double[numAssets];
		for (int a = 0; a < numAssets; a++) {
			weights[a] = BUDGETS[a] / totalBudget;
		}

		// portfolio value V(t) (starts at totalBudget)
		tester.plotPortfolioValue(assetPrices, weights, totalBudget);

		// portfolio relative returns (V(t)/V(t-1) - 1) and log returns;
		// the helper also plots both series on a dual chart
		double[] portfolioRelativeReturns = tester.plotPortfolioRelativeReturns(assetPrices, weights);

		// per-asset log returns: only the Monte Carlo engines use them
		double[][] logReturnsByAsset = tester.getAllLogReturns();

		// dates aligned with returns (skip day 0) and with rolling risk series
		LocalDate[] allDates = tester.getDates();
		LocalDate[] returnDates = Arrays.copyOfRange(allDates, 1, allDates.length);
		LocalDate[] riskDates = RiskMeasureFacade.alignDates(returnDates, WINDOW_LENGTH);

		// =================================================================
		// 2. COMPUTE ALL RISK MEASURES ONCE (decimal scale).
		//    All series have length 1513 = 1763 returns - 250 window.
		// =================================================================

		System.out.println("Computing Historical VaR and ES...");
		double[] histVaRdec = RiskMeasureFacade.iterateHistoricalVaR(portfolioRelativeReturns, ALPHA, WINDOW_LENGTH);
		double[] histESdec  = RiskMeasureFacade.iterateHistoricalES (portfolioRelativeReturns, BETA,  WINDOW_LENGTH);

		System.out.println("Computing Normal parametric VaR and ES...");
		double[] normVaRdec = RiskMeasureFacade.iterateNormalVaR(portfolioRelativeReturns, ALPHA, WINDOW_LENGTH);
		double[] normESdec  = RiskMeasureFacade.iterateNormalES (portfolioRelativeReturns, BETA,  WINDOW_LENGTH);

		System.out.println("Computing Independent Monte Carlo VaR and ES ("
				+ s + " simulations per window, single-pass, "
				+ numAssets + " assets)...");
		double[][] mcResults = RiskMeasureFacade.iterateMonteCarloVaRAndES(portfolioRelativeReturns,
				logReturnsByAsset, BUDGETS,
				ALPHA, BETA, WINDOW_LENGTH, s);
		double[] mcVaRdec = mcResults[0];
		double[] mcESdec  = mcResults[1];

		// Correlated MC: closed-form 2x2 Cholesky -> only runs when n == 2.
		// For n > 2 the rest of the pipeline keeps working.
		double[] mcCorrVaRdec = null;
		double[] mcCorrESdec  = null;
		if (numAssets == 2) {
			System.out.println("Computing Correlated Monte Carlo VaR and ES (Cholesky)...");
			double[][] mcCorrResults = RiskMeasureFacade.iterateCorrelatedMonteCarloVaRAndES(portfolioRelativeReturns,
					logReturnsByAsset[0], logReturnsByAsset[1],
					BUDGETS[0], BUDGETS[1],
					ALPHA, BETA, WINDOW_LENGTH, s);
			mcCorrVaRdec = mcCorrResults[0];
			mcCorrESdec  = mcCorrResults[1];
		} else {
			System.out.println("Skipping Correlated Monte Carlo: implemented only for 2 assets ("
					+ numAssets + " given).");
		}

		// =================================================================
		// 3. REALIZED LOSSES aligned to the rolling risk series.
		//    losses[t] = max(0, -r[t]). Used as overlay on every plot.
		// =================================================================

		double[] realizedAligned  = Arrays.copyOfRange(portfolioRelativeReturns,
				WINDOW_LENGTH, portfolioRelativeReturns.length);
		double[] lossesDecAligned = tester.getPositiveLosses(realizedAligned);
		double[] lossesPctAligned = RiskMeasureFacade.toPercentage(lossesDecAligned);
		double[] lossesUsdAligned = RiskMeasureFacade.toMonetary  (lossesDecAligned, totalBudget);

		// =================================================================
		// 4. PLOTS -- both scales: monetary (USD) and percentage (Pct).
		//    Both demo assets are USD-denominated. Monetary scale uses
		//    absReturn ~= budget * relReturn (consistent across methods).
		// =================================================================

		// ---- monetary view ----
		double[] histVaRUsd   = RiskMeasureFacade.toMonetary(histVaRdec,  totalBudget);
		double[] histESUsd    = RiskMeasureFacade.toMonetary(histESdec,   totalBudget);
		double[] normVaRUsd   = RiskMeasureFacade.toMonetary(normVaRdec,  totalBudget);
		double[] normESUsd    = RiskMeasureFacade.toMonetary(normESdec,   totalBudget);
		double[] mcVaRUsd     = RiskMeasureFacade.toMonetary(mcVaRdec,    totalBudget);
		double[] mcESUsd      = RiskMeasureFacade.toMonetary(mcESdec,     totalBudget);
		double[] mcCorrVaRUsd = (mcCorrVaRdec != null) ? RiskMeasureFacade.toMonetary(mcCorrVaRdec, totalBudget) : null;
		double[] mcCorrESUsd  = (mcCorrESdec  != null) ? RiskMeasureFacade.toMonetary(mcCorrESdec,  totalBudget) : null;

		System.out.println("PlotIterateVaR [Historical] - Usd");
		RiskMeasureFacade.plotVaR(riskDates, histVaRUsd, lossesUsdAligned,
				"Historical VaR (alpha=" + ALPHA + ") Usd", Y_LABEL_USD);
		System.out.println("PlotIterateES [Historical] - Usd");
		RiskMeasureFacade.plotES(riskDates, histESUsd, lossesUsdAligned,
				"Historical ES (beta=" + BETA + ") Usd", Y_LABEL_USD);
		System.out.println("PlotCombined [Historical] - Usd");
		RiskMeasureFacade.plotCombined(riskDates, histVaRUsd, histESUsd, lossesUsdAligned,
				"Historical Usd", ALPHA, BETA, Y_LABEL_USD);

		System.out.println("PlotIterateVaR [Normal] - Usd");
		RiskMeasureFacade.plotVaR(riskDates, normVaRUsd, lossesUsdAligned,
				"Normal VaR (alpha=" + ALPHA + ") Usd", Y_LABEL_USD);
		System.out.println("PlotIterateES [Normal] - Usd");
		RiskMeasureFacade.plotES(riskDates, normESUsd, lossesUsdAligned,
				"Normal ES (beta=" + BETA + ") Usd", Y_LABEL_USD);
		System.out.println("PlotCombined [Normal] - Usd");
		RiskMeasureFacade.plotCombined(riskDates, normVaRUsd, normESUsd, lossesUsdAligned,
				"Normal Usd", ALPHA, BETA, Y_LABEL_USD);

		System.out.println("PlotIterateVaR [Monte Carlo Independent] - Usd");
		RiskMeasureFacade.plotVaR(riskDates, mcVaRUsd, lossesUsdAligned,
				"Monte Carlo VaR (alpha=" + ALPHA + ", N=" + s + ") Usd", Y_LABEL_USD);
		System.out.println("PlotIterateES [Monte Carlo Independent] - Usd");
		RiskMeasureFacade.plotES(riskDates, mcESUsd, lossesUsdAligned,
				"Monte Carlo ES (beta=" + BETA + ", N=" + s + ") Usd", Y_LABEL_USD);
		System.out.println("PlotCombined [Monte Carlo Independent] - Usd");
		RiskMeasureFacade.plotCombined(riskDates, mcVaRUsd, mcESUsd, lossesUsdAligned,
				"Monte Carlo Usd", ALPHA, BETA, Y_LABEL_USD);

		if (mcCorrVaRUsd != null) {
			System.out.println("PlotIterateVaR [Monte Carlo Correlated] - Usd");
			RiskMeasureFacade.plotVaR(riskDates, mcCorrVaRUsd, lossesUsdAligned,
					"MC Correlated VaR (alpha=" + ALPHA + ") Usd", Y_LABEL_USD);
			System.out.println("PlotIterateES [Monte Carlo Correlated] - Usd");
			RiskMeasureFacade.plotES(riskDates, mcCorrESUsd, lossesUsdAligned,
					"MC Correlated ES (beta=" + BETA + ") Usd", Y_LABEL_USD);
			System.out.println("PlotCombined [Monte Carlo Correlated] - Usd");
			RiskMeasureFacade.plotCombined(riskDates, mcCorrVaRUsd, mcCorrESUsd, lossesUsdAligned,
					"MC Correlated Usd", ALPHA, BETA, Y_LABEL_USD);

			System.out.println("PlotCompareVaR [All Methods] - Usd");
			RiskMeasureFacade.plotCompareVaR(riskDates,
					histVaRUsd, normVaRUsd, mcVaRUsd, mcCorrVaRUsd, lossesUsdAligned, ALPHA, Y_LABEL_USD);
			System.out.println("PlotCompareES [All Methods] - Usd");
			RiskMeasureFacade.plotCompareES(riskDates,
					histESUsd, normESUsd, mcESUsd, mcCorrESUsd, lossesUsdAligned, BETA, Y_LABEL_USD);

			System.out.println("PlotCompareMonteCarloVaR [Independent vs Correlated] - Usd");
			RiskMeasureFacade.plotCompareMonteCarloVaR(riskDates,
					mcVaRUsd, mcCorrVaRUsd, lossesUsdAligned, ALPHA, Y_LABEL_USD);
			System.out.println("PlotCompareMonteCarloES [Independent vs Correlated] - Usd");
			RiskMeasureFacade.plotCompareMonteCarloES(riskDates,
					mcESUsd, mcCorrESUsd, lossesUsdAligned, BETA, Y_LABEL_USD);
		}

		// ---- percentage view ----
		double[] histVaRPct   = RiskMeasureFacade.toPercentage(histVaRdec);
		double[] histESPct    = RiskMeasureFacade.toPercentage(histESdec);
		double[] normVaRPct   = RiskMeasureFacade.toPercentage(normVaRdec);
		double[] normESPct    = RiskMeasureFacade.toPercentage(normESdec);
		double[] mcVaRPct     = RiskMeasureFacade.toPercentage(mcVaRdec);
		double[] mcESPct      = RiskMeasureFacade.toPercentage(mcESdec);
		double[] mcCorrVaRPct = (mcCorrVaRdec != null) ? RiskMeasureFacade.toPercentage(mcCorrVaRdec) : null;
		double[] mcCorrESPct  = (mcCorrESdec  != null) ? RiskMeasureFacade.toPercentage(mcCorrESdec)  : null;

		System.out.println("PlotIterateVaR [Historical] - Pct");
		RiskMeasureFacade.plotVaR(riskDates, histVaRPct, lossesPctAligned,
				"Historical VaR (alpha=" + ALPHA + ") Pct", Y_LABEL_PERCENT);
		System.out.println("PlotIterateES [Historical] - Pct");
		RiskMeasureFacade.plotES(riskDates, histESPct, lossesPctAligned,
				"Historical ES (beta=" + BETA + ") Pct", Y_LABEL_PERCENT);
		System.out.println("PlotCombined [Historical] - Pct");
		RiskMeasureFacade.plotCombined(riskDates, histVaRPct, histESPct, lossesPctAligned,
				"Historical Pct", ALPHA, BETA, Y_LABEL_PERCENT);

		System.out.println("PlotIterateVaR [Normal] - Pct");
		RiskMeasureFacade.plotVaR(riskDates, normVaRPct, lossesPctAligned,
				"Normal VaR (alpha=" + ALPHA + ") Pct", Y_LABEL_PERCENT);
		System.out.println("PlotIterateES [Normal] - Pct");
		RiskMeasureFacade.plotES(riskDates, normESPct, lossesPctAligned,
				"Normal ES (beta=" + BETA + ") Pct", Y_LABEL_PERCENT);
		System.out.println("PlotCombined [Normal] - Pct");
		RiskMeasureFacade.plotCombined(riskDates, normVaRPct, normESPct, lossesPctAligned,
				"Normal Pct", ALPHA, BETA, Y_LABEL_PERCENT);

		System.out.println("PlotIterateVaR [Monte Carlo Independent] - Pct");
		RiskMeasureFacade.plotVaR(riskDates, mcVaRPct, lossesPctAligned,
				"Monte Carlo VaR (alpha=" + ALPHA + ", N=" + s + ") Pct", Y_LABEL_PERCENT);
		System.out.println("PlotIterateES [Monte Carlo Independent] - Pct");
		RiskMeasureFacade.plotES(riskDates, mcESPct, lossesPctAligned,
				"Monte Carlo ES (beta=" + BETA + ", N=" + s + ") Pct", Y_LABEL_PERCENT);
		System.out.println("PlotCombined [Monte Carlo Independent] - Pct");
		RiskMeasureFacade.plotCombined(riskDates, mcVaRPct, mcESPct, lossesPctAligned,
				"Monte Carlo Pct", ALPHA, BETA, Y_LABEL_PERCENT);

		if (mcCorrVaRPct != null) {
			System.out.println("PlotIterateVaR [Monte Carlo Correlated] - Pct");
			RiskMeasureFacade.plotVaR(riskDates, mcCorrVaRPct, lossesPctAligned,
					"MC Correlated VaR (alpha=" + ALPHA + ") Pct", Y_LABEL_PERCENT);
			System.out.println("PlotIterateES [Monte Carlo Correlated] - Pct");
			RiskMeasureFacade.plotES(riskDates, mcCorrESPct, lossesPctAligned,
					"MC Correlated ES (beta=" + BETA + ") Pct", Y_LABEL_PERCENT);
			System.out.println("PlotCombined [Monte Carlo Correlated] - Pct");
			RiskMeasureFacade.plotCombined(riskDates, mcCorrVaRPct, mcCorrESPct, lossesPctAligned,
					"MC Correlated Pct", ALPHA, BETA, Y_LABEL_PERCENT);

			System.out.println("PlotCompareVaR [All Methods] - Pct");
			RiskMeasureFacade.plotCompareVaR(riskDates,
					histVaRPct, normVaRPct, mcVaRPct, mcCorrVaRPct, lossesPctAligned, ALPHA, Y_LABEL_PERCENT);
			System.out.println("PlotCompareES [All Methods] - Pct");
			RiskMeasureFacade.plotCompareES(riskDates,
					histESPct, normESPct, mcESPct, mcCorrESPct, lossesPctAligned, BETA, Y_LABEL_PERCENT);

			System.out.println("PlotCompareMonteCarloVaR [Independent vs Correlated] - Pct");
			RiskMeasureFacade.plotCompareMonteCarloVaR(riskDates,
					mcVaRPct, mcCorrVaRPct, lossesPctAligned, ALPHA, Y_LABEL_PERCENT);
			System.out.println("PlotCompareMonteCarloES [Independent vs Correlated] - Pct");
			RiskMeasureFacade.plotCompareMonteCarloES(riskDates,
					mcESPct, mcCorrESPct, lossesPctAligned, BETA, Y_LABEL_PERCENT);
		}

		// =================================================================
		// 5. BASEL III TRAFFIC LIGHT BACKTESTING.
		//    Tested on DECIMAL series: varSeries[t] vs. realizedAligned[t+1].
		//
		//    Annual chart: one bar per calendar year, colored GREEN
		//    (<= 1.6%), YELLOW (1.6-3.6%) or RED (>= 3.6%). A bar at height
		//    0 means 0 violations that year (GREEN). This happens in 2023
		//    for ALL methods (calm year, still-inflated VaR from 2022).
		//
		//    Rolling chart: step function = fraction of violations in the
		//    trailing 250-day window at each date; color bands mark zones.
		//    Historical's step shape reflects its rank-based estimator;
		//    Normal and MC are smoother.
		// =================================================================

		System.out.println("Backtesting [Historical]");
		runBacktest("Historical", histVaRdec, realizedAligned, riskDates);

		System.out.println("Backtesting [Normal]");
		runBacktest("Normal", normVaRdec, realizedAligned, riskDates);

		System.out.println("Backtesting [Monte Carlo Independent]");
		runBacktest("Monte Carlo Independent", mcVaRdec, realizedAligned, riskDates);

		if (mcCorrVaRdec != null) {
			System.out.println("Backtesting [Monte Carlo Correlated]");
			runBacktest("Monte Carlo Correlated", mcCorrVaRdec, realizedAligned, riskDates);
		}

		System.out.println("All plots and reports have been generated successfully.");
	}

	// =====================================================================
	// PRIVATE HELPER METHODS
	// =====================================================================

	/* Plots the raw price series of every asset (sanity check). */
	private static void plotRawPrices(DataManagement tester) {
		for (int a = 0; a < tester.getNumAssets(); a++) {
			tester.plotPrices(tester.getPricesByAsset(a),
					"Prices Asset " + (a + 1) + " (" + ASSET_NAMES[a] + ")");
		}
	}

	/*
	 Runs annual + rolling Basel III backtesting for one method, prints the
	 annual report on stdout, and saves the two PNG charts.
	 */
	private static void runBacktest(String methodName, double[] varSeries,
			double[] realizedAligned, LocalDate[] datesAligned) {
		List<Backtesting.AnnualResult> annual = Backtesting.runAnnual(
				varSeries, realizedAligned, datesAligned, YEAR_FROM, YEAR_TO);
		Backtesting.printAnnualReport(methodName, annual);
		Backtesting.plotAnnualReport(methodName, annual);

		List<Backtesting.RollingResult> rolling = Backtesting.runRolling(
				varSeries, realizedAligned, datesAligned);
		Backtesting.plotRollingReport(methodName, rolling);
	}

}
