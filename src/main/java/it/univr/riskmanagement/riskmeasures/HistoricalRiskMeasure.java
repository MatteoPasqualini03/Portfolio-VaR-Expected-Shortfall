package it.univr.riskmanagement.riskmeasures;

import java.util.Arrays;

/*
 Non-parametric Historical VaR/ES based on the empirical quantile of the
 sample. Both measures are returned as positive numbers under the profit
 sign convention.
 */
public class HistoricalRiskMeasure implements RiskMeasure {

	@Override
	public String getModelName() { return "Historical"; }

	/*
	 Historical VaR at level alpha:
	     VaR_alpha = -x_{floor(n*alpha)+1 : n}
	 with x_{k:n} the k-th smallest observation (1-indexed).
	 0-indexed in Java: sorted[floor(n*alpha)].
	 */
	@Override
	public double computeVaR(double[] data, double alpha) {
		int n = data.length;
		double[] sorted = Arrays.copyOf(data, n);
		Arrays.sort(sorted);
		int k = (int) Math.floor(alpha * n);
		return -sorted[k];
	}

	/*
	 Historical ES at level beta:
	     ES_beta = -(1/(n*beta)) * sum_{i=1..floor(n*beta)} x_{i:n}
	              -(1/beta) * (beta - floor(n*beta)/n) * x_{floor(n*beta)+1:n}
	 Weighted mean of the left tail (the two weights sum to 1).
	 */
	@Override
	public double computeES(double[] data, double beta) {
		int n = data.length;
		double[] sorted = Arrays.copyOf(data, n);
		Arrays.sort(sorted);
		int k = (int) Math.floor(beta * n);
		double sumTail = 0.0;
		for (int i = 0; i < k; i++) {
			sumTail += sorted[i];
		}
		double weightFull = 1.0 / (n * beta);
		double weightPartial = (beta - (double) k / n) / beta;
		return -(weightFull * sumTail + weightPartial * sorted[k]);
	}

	/* Rolling-window historical VaR. */
	@Override
	public double[] iterateVaR(double[] data, double alpha, int windowLength) {
		int n = data.length;
		if (windowLength >= n) {
			throw new IllegalArgumentException("windowLength must be less than the data length: " + n);
		}
		int resultLength = n - windowLength;
		double[] varSeries = new double[resultLength];
		for (int t = 0; t < resultLength; t++) {
			double[] window = Arrays.copyOfRange(data, t, t + windowLength);
			varSeries[t] = computeVaR(window, alpha);
		}
		return varSeries;
	}

	/* Rolling-window historical ES. */
	@Override
	public double[] iterateES(double[] data, double beta, int windowLength) {
		int n = data.length;
		if (windowLength >= n) {
			throw new IllegalArgumentException("windowLength must be less than the data length: " + n);
		}
		int resultLength = n - windowLength;
		double[] esSeries = new double[resultLength];
		for (int t = 0; t < resultLength; t++) {
			double[] window = Arrays.copyOfRange(data, t, t + windowLength);
			esSeries[t] = computeES(window, beta);
		}
		return esSeries;
	}

}
