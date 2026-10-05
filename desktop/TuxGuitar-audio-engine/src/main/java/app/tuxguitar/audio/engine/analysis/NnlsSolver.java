package app.tuxguitar.audio.engine.analysis;

import java.util.Arrays;

/**
 * Non-negative least squares by the Lawson-Hanson active-set method, on the normal equations:
 * minimise ||A x - d|| with x ≥ 0 given G = AᵀA and b = Aᵀd. Deterministic and allocation-free
 * after construction. Not thread-safe.
 */
final class NnlsSolver {

	private static final double EPSILON = 1e-12;
	private static final double RIDGE = 1e-9;

	private final int size;
	private final double[][] gram;
	private final double[] x;
	private final double[] gradient;
	private final boolean[] passive;
	private final int[] index;
	private final double[][] system;
	private final double[] z;

	NnlsSolver(double[][] gram) {
		this.size = gram.length;
		this.gram = gram;
		this.x = new double[this.size];
		this.gradient = new double[this.size];
		this.passive = new boolean[this.size];
		this.index = new int[this.size];
		this.system = new double[this.size][this.size + 1];
		this.z = new double[this.size];
	}

	/** Solves for {@code b} and returns the solution (owned by the solver, valid until the next call). */
	double[] solve(double[] b) {
		Arrays.fill(this.x, 0.0);
		Arrays.fill(this.passive, false);
		double scale = 0.0;
		for( double value : b ) {
			scale = Math.max(scale, Math.abs(value));
		}
		double tolerance = 1e-9 * Math.max(scale, 1e-12);
		for( int outer = 0 ; outer < 3 * this.size ; outer ++ ) {
			int best = -1;
			double bestGradient = tolerance;
			for( int i = 0 ; i < this.size ; i ++ ) {
				double g = b[i];
				for( int j = 0 ; j < this.size ; j ++ ) {
					g -= this.gram[i][j] * this.x[j];
				}
				this.gradient[i] = g;
				if( !this.passive[i] && g > bestGradient ) {
					bestGradient = g;
					best = i;
				}
			}
			if( best < 0 ) {
				break;
			}
			this.passive[best] = true;
			for( int inner = 0 ; inner < this.size ; inner ++ ) {
				int n = 0;
				for( int i = 0 ; i < this.size ; i ++ ) {
					if( this.passive[i] ) {
						this.index[n ++] = i;
					}
				}
				for( int r = 0 ; r < n ; r ++ ) {
					for( int c = 0 ; c < n ; c ++ ) {
						this.system[r][c] = this.gram[this.index[r]][this.index[c]] + (r == c ? RIDGE : 0.0);
					}
					this.system[r][n] = b[this.index[r]];
				}
				gauss(n);
				boolean allPositive = true;
				for( int r = 0 ; r < n ; r ++ ) {
					if( this.z[r] <= EPSILON ) {
						allPositive = false;
					}
				}
				if( allPositive ) {
					Arrays.fill(this.x, 0.0);
					for( int r = 0 ; r < n ; r ++ ) {
						this.x[this.index[r]] = this.z[r];
					}
					break;
				}
				double alpha = Double.MAX_VALUE;
				for( int r = 0 ; r < n ; r ++ ) {
					if( this.z[r] <= EPSILON ) {
						double current = this.x[this.index[r]];
						double step = current - this.z[r];
						if( step > 1e-15 ) {
							alpha = Math.min(alpha, current / step);
						}
					}
				}
				if( alpha == Double.MAX_VALUE ) {
					alpha = 0.0;
				}
				for( int r = 0 ; r < n ; r ++ ) {
					int i = this.index[r];
					this.x[i] = this.x[i] + alpha * (this.z[r] - this.x[i]);
				}
				for( int i = 0 ; i < this.size ; i ++ ) {
					if( this.passive[i] && this.x[i] <= EPSILON ) {
						this.passive[i] = false;
						this.x[i] = 0.0;
					}
				}
			}
		}
		return this.x;
	}

	/** Gaussian elimination with partial pivoting on the n x (n+1) system; the solution goes to z. */
	private void gauss(int n) {
		for( int i = 0 ; i < n ; i ++ ) {
			int pivot = i;
			for( int r = i + 1 ; r < n ; r ++ ) {
				if( Math.abs(this.system[r][i]) > Math.abs(this.system[pivot][i]) ) {
					pivot = r;
				}
			}
			double[] row = this.system[i];
			this.system[i] = this.system[pivot];
			this.system[pivot] = row;
			double value = this.system[i][i];
			if( Math.abs(value) < 1e-12 ) {
				value = 1e-12;
			}
			for( int r = i + 1 ; r < n ; r ++ ) {
				double factor = this.system[r][i] / value;
				for( int c = i ; c <= n ; c ++ ) {
					this.system[r][c] -= factor * this.system[i][c];
				}
			}
		}
		for( int i = n - 1 ; i >= 0 ; i -- ) {
			double sum = this.system[i][n];
			for( int c = i + 1 ; c < n ; c ++ ) {
				sum -= this.system[i][c] * this.z[c];
			}
			double value = this.system[i][i];
			if( Math.abs(value) < 1e-12 ) {
				value = 1e-12;
			}
			this.z[i] = sum / value;
		}
	}
}
