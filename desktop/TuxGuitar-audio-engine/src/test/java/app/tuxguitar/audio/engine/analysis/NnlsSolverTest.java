package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class NnlsSolverTest {

	private static double[][] gram(double[][] columns) {
		int n = columns.length;
		double[][] g = new double[n][n];
		for( int i = 0 ; i < n ; i ++ ) {
			for( int j = 0 ; j < n ; j ++ ) {
				double sum = 0.0;
				for( int k = 0 ; k < columns[i].length ; k ++ ) {
					sum += columns[i][k] * columns[j][k];
				}
				g[i][j] = sum;
			}
		}
		return g;
	}

	private static double[] project(double[][] columns, double[] d) {
		double[] b = new double[columns.length];
		for( int i = 0 ; i < columns.length ; i ++ ) {
			for( int k = 0 ; k < d.length ; k ++ ) {
				b[i] += columns[i][k] * d[k];
			}
		}
		return b;
	}

	@Test
	public void recoversANonNegativeMixtureOfCorrelatedColumns() {
		double[][] columns = { { 1, 0, 0, 1 }, { 0, 1, 0, 1 }, { 0, 0, 1, 1 }, { 1, 1, 0, 0 } };
		double[] truth = { 2.0, 0.0, 3.0, 1.5 };
		double[] d = new double[4];
		for( int i = 0 ; i < 4 ; i ++ ) {
			for( int k = 0 ; k < 4 ; k ++ ) {
				d[k] += truth[i] * columns[i][k];
			}
		}
		double[] x = new NnlsSolver(gram(columns)).solve(project(columns, d));
		assertArrayEquals(truth, x, 1e-4);
	}

	@Test
	public void neverReturnsANegativeAmplitudeAndIsDeterministic() {
		double[][] columns = { { 1, 1, 0 }, { 1, 0, 1 }, { 0, 1, 1 } };
		double[] d = { 1.0, -2.0, 0.5 };
		NnlsSolver solver = new NnlsSolver(gram(columns));
		double[] first = solver.solve(project(columns, d)).clone();
		for( double value : first ) {
			assertTrue(value >= 0.0);
		}
		assertArrayEquals(first, solver.solve(project(columns, d)), 0.0);
	}

	@Test
	public void aZeroSignalGivesZeroEverywhere() {
		double[][] columns = { { 1, 0 }, { 0, 1 } };
		assertEquals(0.0, new NnlsSolver(gram(columns)).solve(new double[] { 0.0, 0.0 })[0], 0.0);
	}
}
