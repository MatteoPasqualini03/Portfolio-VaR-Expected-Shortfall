package it.univr.riskmanagement.riskmeasures;

import java.util.Arrays;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.MersenneTwister;

/*
 Monte Carlo VaR/ES with INDEPENDENT assets, generalized to n assets.
 Per-window pipeline:
   1. Estimate mu_a, sigma_a of the per-asset log returns on the window.
   2. Draw N independent log returns ~ Normal(mu_a, sigma_a) per asset.
   3. Convert to simple returns: simple_a = exp(log_a) - 1.
   4. Aggregate: portfolio_i = sum_a w_a * simple_a_i, with w_a fixed at construction.
   5. Compute VaR and ES on the simulated sample (same formulas as the historical estimator).

 RNG: Mersenne Twister, fixed seed for reproducibility.
 Template Method note: simulatePortfolioReturns is protected so subclasses
 can override only the simulation step and reuse the rest.
 */
public class MonteCarloRiskMeasure implements RiskMeasure {

	protected final int numSimulations;

	@Override
	public String getModelName() { return "Monte Carlo (N=" + numSimulations + ")"; }

	// Log returns of every asset, shape [numAssets][n-1].
	protected final double[][] logReturnsByAsset;

	// Fixed portfolio weights, length numAssets, summing to 1.
	protected final double[] weights;

	// Mersenne Twister generator.
	protected final MersenneTwister rng;

	/*
	 n-asset constructor. Weights are derived from the budgets vector so they
	 stay constant for the entire analysis.
	 */
	public MonteCarloRiskMeasure(double[][] logReturnsByAsset, double[] budgets,
								  int numSimulations) {
		int numAssets = logReturnsByAsset.length;
		if (budgets.length != numAssets) {
			throw new IllegalArgumentException(
				"budgets length (" + budgets.length + ") must equal number of assets ("
				+ numAssets + ")");
		}
		this.logReturnsByAsset = logReturnsByAsset;
		double totalBudget = 0.0;
		for (double b : budgets) totalBudget += b;
		this.weights = new double[numAssets];
		for (int a = 0; a < numAssets; a++) {
			this.weights[a] = budgets[a] / totalBudget;
		}
		this.numSimulations = numSimulations;
		this.rng = new MersenneTwister(42);
	}

	/* 2-asset constructor: delegates to the n-asset one. */
	public MonteCarloRiskMeasure(double[] logReturnsAsset1, double[] logReturnsAsset2,
								  double budget1, double budget2,
								  int numSimulations) {
		this(new double[][] { logReturnsAsset1, logReturnsAsset2 },
			 new double[] { budget1, budget2 },
			 numSimulations);
	}

	/* Sample mean on segment [from, to). */
	protected double computeMean(double[] data, int from, int to) {
		double sum = 0.0;
		for (int i = from; i < to; i++) {
			sum += data[i];
		}
		return sum / (to - from);
	}

	/* Sample standard deviation on segment [from, to), denominator n. */
	protected double computeStdDev(double[] data, int from, int to, double mean) {
		double sumSquaredDev = 0.0;
		for (int i = from; i < to; i++) {
			double deviation = data[i] - mean;
			sumSquaredDev += deviation * deviation;
		}
		double variance = sumSquaredDev / (to - from);
		return Math.sqrt(variance);
	}

	/*
	 One MC simulation for the window of LOG returns [from, to), INDEPENDENT assets.
	 Returns numSimulations draws of the portfolio relative return.
	 */
	protected double[] simulatePortfolioReturns(int from, int to) {
		int numAssets = logReturnsByAsset.length;

		double[] mu = new double[numAssets];
		double[] sigma = new double[numAssets];
		NormalDistribution[] dist = new NormalDistribution[numAssets];
		for (int a = 0; a < numAssets; a++) {
			mu[a] = computeMean(logReturnsByAsset[a], from, to);
			sigma[a] = computeStdDev(logReturnsByAsset[a], from, to, mu[a]);
			dist[a] = new NormalDistribution(rng, mu[a], sigma[a]);
		}

		double[] simulatedReturns = new double[numSimulations];
		for (int i = 0; i < numSimulations; i++) {
			double portfolioReturn = 0.0;
			for (int a = 0; a < numAssets; a++) {
				double logSim = dist[a].sample();
				double simple = Math.exp(logSim) - 1.0;
				portfolioReturn += weights[a] * simple;
			}
			simulatedReturns[i] = portfolioReturn;
		}
		return simulatedReturns;
	}

	@Override
	public double computeVaR(double[] data, double alpha) {
		double[] simulated = simulatePortfolioReturns(0, data.length);
		return new HistoricalRiskMeasure().computeVaR(simulated, alpha);
	}

	@Override
	public double computeES(double[] data, double beta) {
		double[] simulated = simulatePortfolioReturns(0, data.length);
		return new HistoricalRiskMeasure().computeES(simulated, beta);
	}

	/*
	 Rolling VaR: delegates to iterateVaRAndES so VaR and ES share the same
	 simulated sample per window.
	 */
	@Override
	public double[] iterateVaR(double[] data, double alpha, int windowLength) {
		return iterateVaRAndES(data, alpha, alpha, windowLength)[0];
	}

	/* Rolling ES: same delegation as iterateVaR. */
	@Override
	public double[] iterateES(double[] data, double beta, int windowLength) {
		return iterateVaRAndES(data, beta, beta, windowLength)[1];
	}

	/*
	 Single-pass rolling iterator: one MC simulation per window, both VaR (at alpha)
	 and ES (at beta) extracted from the same sorted sample.
	 Returns { varSeries, esSeries }, both of length (n - windowLength).
	 */
	public double[][] iterateVaRAndES(double[] data, double alpha, double beta, int windowLength) {
		int n = logReturnsByAsset[0].length;
		if (windowLength >= n) {
			throw new IllegalArgumentException("windowLength must be less than the data length");
		}
		int resultLength = n - windowLength;
		double[] varSeries = new double[resultLength];
		double[] esSeries = new double[resultLength];

		for (int t = 0; t < resultLength; t++) {
			double[] simulated = simulatePortfolioReturns(t, t + windowLength);
			Arrays.sort(simulated);

			int kAlpha = (int) Math.floor(alpha * numSimulations);
			varSeries[t] = -simulated[kAlpha];

			int kBeta = (int) Math.floor(beta * numSimulations);
			double sumTail = 0.0;
			for (int i = 0; i < kBeta; i++) {
				sumTail += simulated[i];
			}
			double weightFull = 1.0 / (numSimulations * beta);
			double weightPartial = (beta - (double) kBeta / numSimulations) / beta;
			esSeries[t] = -(weightFull * sumTail + weightPartial * simulated[kBeta]);
		}

		return new double[][] { varSeries, esSeries };
	}

}
