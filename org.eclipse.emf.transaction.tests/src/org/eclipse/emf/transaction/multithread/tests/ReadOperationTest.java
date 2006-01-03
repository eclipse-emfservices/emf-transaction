/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
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
 * $Id: ReadOperationTest.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.multithread.tests;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * Testcase for testing scheduling of Read operation scenarios
 * @author mgoyal
 */
public class ReadOperationTest
	extends AbstractMultithreadTest {

	public static Test suite() {
		return new TestSuite(ReadOperationTest.class, "Reader Thread Tests"); //$NON-NLS-1$
	}

	/**
	 *  Tests scheduling of simple read operation
	 */
	public void testReadOperation() {
		ReadThread readThread1 = new ReadThread(getDomain());

		readThread1.start();

		boolean done = false;
		while (!done) {
			try {
				Thread.sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this exception
			}
			if (!readThread1.isAlive())
				done = true;
		}

		assertFalse(readThread1.isFailed());
		assertTrue(readThread1.isExecuted());
	}

	/**
	 *  Tests scheduling of simultaneous read operations.
	 */
	public void testSimultaneousRead() {
		Object notifier = new Object();
		ReadThread readThread1 = new ReadThread(getDomain(), null, notifier);
		ReadThread readThread2 = new ReadThread(getDomain(), null, notifier);

		synchronized (notifier) {
			try {
				readThread1.start();
				notifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}
		synchronized (notifier) {
			try {
				readThread2.start();
				notifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}

		boolean done = false;
		while (!done) {
			try {
				Thread.sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this exception
			}
			if (!readThread1.isAlive() && !readThread2.isAlive())
				done = true;
		}

		assertFalse(readThread1.isFailed());
		assertFalse(readThread2.isFailed());
		assertTrue(readThread1.isExecuted());
		assertTrue(readThread2.isExecuted());
		assertTrue(Constants.occurredBefore(readThread2, readThread1)
			|| Constants.occurredAfter(readThread2, readThread1));
	}

	/**
	 *  Tests scheduling of nested read operations.
	 */
	public void testNestedReads() {
		NestedReadThread readThread1 = new NestedReadThread(getDomain());
		readThread1.start();
		boolean done = false;
		while (!done) {
			try {
				Thread.sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this exception
			}
			if (!readThread1.isAlive())
				done = true;
		}

		assertFalse(readThread1.isInnerFailed());
		assertFalse(readThread1.isFailed());
		assertTrue(readThread1.isInnerExecuted());
		assertTrue(readThread1.isExecuted());
		assertTrue(Constants.occurredDuring(readThread1.getStartTime(),
			readThread1.getEndTime(), readThread1.getInnerStartTime(),
			readThread1.getInnerEndTime()));
	}

	/**
	 *  Tests scheduling of yielding read with other simultaneous read operations.
	 */
	public void testLongRunningYieldingRead() {
		Object runNotifier = new Object();
		LongRunningReadThread longReadThread = new LongRunningReadThread(
			getDomain(),
			null,
			runNotifier);
		ReadThread readThd1 = new ReadThread(getDomain(), null, runNotifier);
		ReadThread readThd2 = new ReadThread(getDomain(), null, runNotifier);
		ReadThread readThd3 = new ReadThread(getDomain(), null, runNotifier);

		synchronized (runNotifier) {
			try {
				longReadThread.start();
				runNotifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}
		synchronized (runNotifier) {
			try {
				readThd1.start();
				runNotifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}
		synchronized (runNotifier) {
			try {
				readThd2.start();
				runNotifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}
		synchronized (runNotifier) {
			try {
				readThd3.start();
				runNotifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}
		
		boolean done = false;
		while (!done) {
			try {
				Thread.sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this exception
			}
			if (!longReadThread.isAlive() && !readThd1.isAlive()
				&& !readThd2.isAlive() && !readThd3.isAlive())
				done = true;
		}

		assertFalse(longReadThread.isFailed());
		assertFalse(readThd1.isFailed());
		assertFalse(readThd2.isFailed());
		assertFalse(readThd3.isFailed());
		assertTrue(longReadThread.isExecuted());
		assertTrue(readThd1.isExecuted());
		assertTrue(readThd2.isExecuted());
		assertTrue(readThd3.isExecuted());
		// Verify that the start time of long running thread is before the start
		// time of other threads
		// Verify that the end time of other threads is before the end time of
		// long running thread.
		// Also verify that other threads didn't run simulatenously.
		assertTrue(Constants.occurredDuring(longReadThread, readThd1));
		assertTrue(Constants.occurredDuring(longReadThread, readThd2));
		assertTrue(Constants.occurredDuring(longReadThread, readThd3));
		assertTrue(!Constants.occurIntersect(readThd1, readThd2));
		assertTrue(!Constants.occurIntersect(readThd1, readThd3));
		assertTrue(!Constants.occurIntersect(readThd2, readThd3));
	}

	/**
	 *  Tests cooperative scheduling of multiple yielding readers.
	 */
	public void testMultipleLongRunningYieldingReads() {
		Object runNotifier = new Object();
		LongRunningReadThread longReadThread1 = new LongRunningReadThread(
			getDomain(),
			null,
			runNotifier);
		LongRunningReadThread longReadThread2 = new LongRunningReadThread(
			getDomain(),
			null,
			runNotifier);
		LongRunningReadThread longReadThread3 = new LongRunningReadThread(
			getDomain(),
			null,
			runNotifier);
		LongRunningReadThread longReadThread4 = new LongRunningReadThread(
			getDomain(),
			null,
			runNotifier);

		synchronized (runNotifier) {
			try {
				longReadThread1.start();
				runNotifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}
		synchronized (runNotifier) {
			try {
				longReadThread2.start();
				runNotifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}
		synchronized (runNotifier) {
			try {
				longReadThread3.start();
				runNotifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}
		synchronized (runNotifier) {
			// run this one directly in the UI thread to test that the UI-safe
			//   acquiring actually causes the "UI blocked" dialog
			longReadThread4.run();
		}
		
		boolean done = false;
		while (!done) {
			try {
				Thread.sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this exception
			}
			if (!longReadThread1.isAlive() && !longReadThread2.isAlive()
				&& !longReadThread3.isAlive() && !longReadThread4.isAlive())
				done = true;
		}

		assertFalse(longReadThread1.isFailed());
		assertFalse(longReadThread2.isFailed());
		assertFalse(longReadThread3.isFailed());
		assertFalse(longReadThread4.isFailed());
		assertTrue(longReadThread1.isExecuted());
		assertTrue(longReadThread2.isExecuted());
		assertTrue(longReadThread3.isExecuted());
		assertTrue(longReadThread4.isExecuted());
		
		// at least two of the threads should have yielded to other readers,
		//   which is indicated by having yielded for at least a sleep time
		int yielded = 0;
		if (longReadThread1.timeYielded >= Constants.SLEEP_TIME) {
			yielded++;
		}
		if (longReadThread2.timeYielded >= Constants.SLEEP_TIME) {
			yielded++;
		}
		if (longReadThread3.timeYielded >= Constants.SLEEP_TIME) {
			yielded++;
		}
		if (longReadThread4.timeYielded >= Constants.SLEEP_TIME) {
			yielded++;
		}
		assertTrue(yielded >= 2);
	}
}
