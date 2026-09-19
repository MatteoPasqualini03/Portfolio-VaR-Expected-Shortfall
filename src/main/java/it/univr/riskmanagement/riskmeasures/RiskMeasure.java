package it.univr.riskmanagement.riskmeasures;

import it.univr.riskmanagement.data.DataCollectionAndPlotting;

import java.awt.Color;
import java.awt.Stroke;
import java.time.LocalDate;
import java.util.Arrays;

/*
 Strategy contract for VaR/ES estimators. Each implementation provides:
  - point-wise VaR and ES on a sample
  - rolling-window iteration of both measures
  - default plot helpers built on getModelName()

 Sign convention: positive return = gain, negative = loss. VaR and ES are
 returned as positive numbers so that a larger value means more risk.

 Parameter convention: alpha is the VaR level (e.g. 0.01), beta the ES level
 (e.g. 0.025).
 */
public interface RiskMeasure {

	/* Short label for this model, used in plot titles. */
	String getModelName();

	/* VaR at level alpha on the given sample. */
	double computeVaR(double[] data, double alpha);

	/* ES at level beta on the given sample. */
	double computeES(double[] data, double beta);

	/*
	 Rolling-window VaR: for each t in [windowLength, n-1] computes VaR on
	 the window ending at t-1. Result has length n - windowLength.
	 */
	double[] iterateVaR(double[] data, double alpha, int windowLength);

	/*
	 Rolling-window ES: same alignment as iterateVaR.
	 */
	double[] iterateES(double[] data, double beta, int windowLength);

	/* Default plot of the rolling VaR series (black, solid). */
	default void plotIterateVaR(LocalDate[] dates, double[] data, double alpha, int windowLength) {
		double[] varSeries = iterateVaR(data, alpha, windowLength);
		LocalDate[] datesToPlot = Arrays.copyOfRange(dates, windowLength, dates.length);
		String title = getModelName() + " VaR (alpha=" + alpha + ")";
		DataCollectionAndPlotting.plotData(datesToPlot,
			new double[][] { varSeries },
			new String[] { title },
			new Color[] { Color.BLACK },
			new Stroke[] { DataCollectionAndPlotting.STROKE_SOLID },
			title, title);
	}

	/* Default plot of the rolling ES series (red, solid). */
	default void plotIterateES(LocalDate[] dates, double[] data, double beta, int windowLength) {
		double[] esSeries = iterateES(data, beta, windowLength);
		LocalDate[] datesToPlot = Arrays.copyOfRange(dates, windowLength, dates.length);
		String title = getModelName() + " ES (beta=" + beta + ")";
		DataCollectionAndPlotting.plotData(datesToPlot,
			new double[][] { esSeries },
			new String[] { title },
			new Color[] { Color.RED },
			new Stroke[] { DataCollectionAndPlotting.STROKE_SOLID },
			title, title);
	}

}
