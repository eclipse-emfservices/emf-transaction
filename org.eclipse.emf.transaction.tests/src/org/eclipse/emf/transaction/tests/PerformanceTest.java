/**
 * <copyright>
 *
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: PerformanceTest.java,v 1.1 2006/04/03 18:14:09 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.util.Collections;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * Regression tests for performance problems (time and memory).
 *
 * @author Christian W. Damus (cdamus)
 */
public class PerformanceTest extends AbstractTest {
	static final int RUNS = 10;
	static final int OUTLIERS = 2;  // room for 1 high and 1 low outlier
	
	private List timings;
	private long clock;
	
	public PerformanceTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(PerformanceTest.class, "Performance Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests the time required to create a deeply nested transaction tree and
	 * commit it, for regression in the performance of the
	 * <code>ReadWriteValidator.findTree(Transaction)</code> method.
	 */
	public void test_ReadWriteValidator_findTree_132590() {
		int count = RUNS + OUTLIERS;
		
		for (int i = 0; i < count; i++) {
			startClock();
			
			createDeeplyNestedTransaction(5, 10);
			
			long timing = stopClock();
			
			System.out.println("Raw timing: " + timing); //$NON-NLS-1$
		}
		
		removeOutliers();
		
		System.out.println("Mean time for " + RUNS + " runs: " + meanTiming()); //$NON-NLS-1$ //$NON-NLS-2$
		System.out.println("Standard deviation: " + stddevTiming()); //$NON-NLS-1$
	}
	
	//
	// Fixture methods
	//
	
	protected void doSetUp() throws Exception {
		super.doSetUp();
		
		timings = new java.util.ArrayList();
	}
	
	protected void doTearDown() throws Exception {
		timings = null;
		
		super.doTearDown();
	}
	
	/**
	 * Creates a deeply nested tree of transactions, having the specified
	 * <code>depth</code> and in which every non-leaf transaction has
	 * <code>breadth</code> children.
	 */
	protected void createDeeplyNestedTransaction(int depth, int breadth) {
		// stopping condition
		if (depth-- > 0) {
			startWriting();
			
			if (depth > 0) {
				for (int i = 0; i < breadth; i++) {
					createDeeplyNestedTransaction(depth, breadth);
				}
			}
			
			commit();
		}
	}
	
	final void startClock() {
		clock = System.currentTimeMillis();
	}
	
	final long stopClock() {
		long result = System.currentTimeMillis() - clock;
		
		timings.add(new Long(result));
		
		return result;
	}
	
	final void removeOutliers() {
		Collections.sort(timings);
		
		for (int i = 0; i < OUTLIERS / 2; i++) {
			timings.remove(0);
			timings.remove(timings.size() - 1);
		}
	}
	
	final double meanTiming() {
		double result = 0;
		int count = timings.size();
		
		for (int i = 0; i < count; i++) {
			result += ((Long) timings.get(i)).doubleValue();
		}
		
		result /= (double) count;
		
		return result;
	}
	
	final double stddevTiming() {
		double result = 0;
		double mean = meanTiming();
		double dev = 0;
		int count = timings.size();
		
		for (int i = 0; i < count; i++) {
			dev = ((Long) timings.get(i)).doubleValue() - mean;
			
			result += dev * dev;
		}
		
		result = Math.sqrt(result / (double) count);
		
		return result;
	}
}
