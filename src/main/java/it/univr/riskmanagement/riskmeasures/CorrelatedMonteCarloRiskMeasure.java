package it.univr.riskmanagement.riskmeasures;

import org.apache.commons.math3.distribution.NormalDistribution;

/*
 EXTRA — Monte Carlo VaR/ES that preserves the empirical correlation
 between two assets' log returns via Cholesky decomposition.

 Template Method: extends MonteCarloRiskMeasure and overrides only the
 simulation step. Rolling iteration, plotting and the rest are inherited.

 Per-window pipeline:
   1. Estimate mu_i, sigma_i on the window.
   2. Estimate sample Pearson rho on the window (clipped to [-0.999, 0.999]).
   3. Cholesky factor of [[1, rho],[rho, 1]] in closed form.
   4. Draw N pairs (z1, z2) iid Normal(0,1); set
        e1 = z1, e2 = rho*z1 + sqrt(1 - rho^2)*z2.
   5. log_i = mu_i + sigma_i * e_i; simple_i = exp(log_i) - 1.
   6. portfolio_i = w1 * simple1 + w2 * simple2 with fixed weights.

 Restricted to TWO assets.
 */
public class CorrelatedMonteCarloRiskMeasure extends MonteCarloRiskMeasure {

	public CorrelatedMonteCarloRiskMeasure(double[] logReturnsAsset1, double[] logReturnsAsset2,
										   double budget1, double budget2,
										   int numSimulations) {
		super(logReturnsAsset1, logReturnsAsset2, budget1, budget2, numSimulations);
	}

	/*
	 Sample Pearson correlation on equal-length segments. Means and stds
	 are passed in to avoid recomputation. Sample covariance uses (n-1).
	 */
	private double computeCorrelation(double[] x, double[] y, int from, int to,
									  double meanX, double meanY,
									  double stdX, double stdY) {
		double cov = 0.0;
		for (int i = from; i < to; i++) {
			cov += (x[i] - meanX) * (y[i] - meanY);
		}
		cov /= (to - from - 1);
		return cov / (stdX * stdY);
	}

	@Override
	protected double[] simulatePortfolioReturns(int from, int to) {
		double[] logR1 = logReturnsByAsset[0];
		double[] logR2 = logReturnsByAsset[1];

		double mu1    = computeMean(logR1, from, to);
		double sigma1 = computeStdDev(logR1, from, to, mu1);
		double mu2    = computeMean(logR2, from, to);
		double sigma2 = computeStdDev(logR2, from, to, mu2);

		double rho = computeCorrelation(logR1, logR2, from, to, mu1, mu2, sigma1, sigma2);
		if (rho > 0.999)  rho =  0.999;
		if (rho < -0.999) rho = -0.999;

		// Cholesky of [[1, rho],[rho, 1]]: L = [[1, 0],[rho, sqrt(1 - rho^2)]]
		double l21 = rho;
		double l22 = Math.sqrt(1.0 - rho * rho);

		double w1 = weights[0];
		double w2 = weights[1];

		NormalDistribution stdNormal = new NormalDistribution(rng, 0.0, 1.0);

		double[] simulatedReturns = new double[numSimulations];
		for (int i = 0; i < numSimulations; i++) {
			double z1 = stdNormal.sample();
			double z2 = stdNormal.sample();
			double e1 = z1;
			double e2 = l21 * z1 + l22 * z2;
			double logSim1 = mu1 + sigma1 * e1;
			double logSim2 = mu2 + sigma2 * e2;
			double simple1 = Math.exp(logSim1) - 1.0;
			double simple2 = Math.exp(logSim2) - 1.0;
			simulatedReturns[i] = w1 * simple1 + w2 * simple2;
		}
		return simulatedReturns;
	}

}
