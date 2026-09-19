package it.univr.riskmanagement.riskmeasures;

import java.util.Arrays;

import org.apache.commons.math3.distribution.NormalDistribution;

/*
 Parametric Gaussian VaR/ES. Assumes sample returns are Normal(mu, sigma)
 with mu and sigma estimated from the sample.

   VaR(alpha) = -(mu + sigma * Phi^{-1}(alpha))
   ES(beta)   = -(mu - sigma * phi(Phi^{-1}(beta)) / beta)

 Returns positive values under the profit sign convention.
 */
public class NormalRiskMeasure implements RiskMeasure {

	private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution();

	@Override
	public String getModelName() { return "Normal"; }

	/* Sample mean. */
	private double computeMean(double[] data) {
		double sum = 0.0;
		for (int i = 0; i < data.length; i++) {
			sum += data[i];
		}
		return sum / data.length;
	}

	/* Sample standard deviation with denominator n (no Bessel correction). */
	private double computeStdDev(double[] data, double mean) {
		double sumSquaredDev = 0.0;
		for (int i = 0; i < data.length; i++) {
			double deviation = data[i] - mean;
			sumSquaredDev += deviation * deviation;
		}
		double variance = sumSquaredDev / (data.length);
		return Math.sqrt(variance);
	}

	@Override
	public double computeVaR(double[] data, double alpha) {
		double mu = computeMean(data);
		double sigma = computeStdDev(data, mu);
		double zAlpha = STANDARD_NORMAL.inverseCumulativeProbability(alpha);
		return -(mu + sigma * zAlpha);
	}

	@Override
	public double computeES(double[] data, double beta) {
		double mu = computeMean(data);
		double sigma = computeStdDev(data, mu);
		double zBeta = STANDARD_NORMAL.inverseCumulativeProbability(beta);
		double phiZBeta = STANDARD_NORMAL.density(zBeta);
		return -(mu - sigma * phiZBeta / beta);
	}

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
