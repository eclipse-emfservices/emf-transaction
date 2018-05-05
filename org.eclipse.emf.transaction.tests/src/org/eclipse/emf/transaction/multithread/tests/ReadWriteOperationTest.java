/**
 * Copyright (c) 2005, 2018 IBM Corporation, Christian W. Damus, and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Christian W. Damus - bug 149982
 */
package org.eclipse.emf.transaction.multithread.tests;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * Testcase for testing read and write operation scheduling scenarios
 * 
 * @author mgoyal
 */
public class ReadWriteOperationTest
	extends AbstractMultithreadTest {

	public static Test suite() {
		return new TestSuite(ReadWriteOperationTest.class, "Concurrent Reader and Writer Thread Tests"); //$NON-NLS-1$
	}

	/**
	 *  Tests scheduling of complex Read write scenarios.
	 */
	public void testComplexSimultaneousReadsWrites() {
		Object notifier = new Object();
		NestedReadInWriteThread readInWriteThread1 = new NestedReadInWriteThread(
			getDomain(), null, notifier);
		NestedReadInWriteThread readInWriteThread2 = new NestedReadInWriteThread(
			getDomain(), null, notifier);

		synchronized (notifier) {
			try {
				readInWriteThread1.start();
				notifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}
		synchronized (notifier) {
			try {
				readInWriteThread2.start();
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
			if (!readInWriteThread1.isAlive()
				&& !readInWriteThread2.isAlive())
				done = true;
		}

		assertFalse(readInWriteThread1.isFailed());
		assertFalse(readInWriteThread2.isFailed());
		assertTrue(readInWriteThread1.isExecuted());
		assertTrue(readInWriteThread2.isExecuted());
		assertFalse(readInWriteThread1.isInnerFailed());
		assertFalse(readInWriteThread2.isInnerFailed());
		assertTrue(readInWriteThread1.isInnerExecuted());
		assertTrue(readInWriteThread2.isInnerExecuted());
	}

	/**
	 * Tests scheduling of simultaneous read and write operation  
	 */
	public void testSimultaneousReadsWrites() {
		Object notifier = new Object();
		WriteThread writeThread1 = new WriteThread(getDomain(), null, notifier);
		WriteThread writeThread2 = new WriteThread(getDomain(), null, notifier);
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
				writeThread1.start();
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

		synchronized (notifier) {
			try {
				writeThread2.start();
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
			if (!writeThread1.isAlive() && !writeThread2.isAlive()
				&& !readThread1.isAlive() && !readThread2.isAlive())
				done = true;
		}

		assertFalse(readThread1.isFailed());
		assertFalse(readThread2.isFailed());
		assertFalse(writeThread1.isFailed());
		assertFalse(writeThread2.isFailed());
		assertTrue(readThread1.isExecuted());
		assertTrue(readThread2.isExecuted());
		assertTrue(writeThread1.isExecuted());
		assertTrue(writeThread2.isExecuted());
		assertTrue(Constants.occurredBefore(writeThread2, writeThread1)
			|| Constants.occurredAfter(writeThread2, writeThread1));
		assertTrue(Constants.occurredBefore(writeThread2, readThread2)
			|| Constants.occurredAfter(writeThread2, readThread2));
		assertTrue(Constants.occurredBefore(writeThread2, readThread1)
			|| Constants.occurredAfter(writeThread2, readThread1));
		assertTrue(Constants.occurredBefore(readThread2, readThread1)
			|| Constants.occurredAfter(readThread2, readThread1));
		assertTrue(Constants.occurredBefore(readThread2, writeThread1)
			|| Constants.occurredAfter(readThread2, writeThread1));
		assertTrue(Constants.occurredBefore(readThread1, writeThread1)
			|| Constants.occurredAfter(readThread1, writeThread1));
	}

	/**
	 *  Tests Scheduling of Nested Read in Write Operation
	 */
	public void testNestedReadInWrite() {
		NestedReadInWriteThread readInWriteThd = new NestedReadInWriteThread(getDomain());
		readInWriteThd.start();

		boolean done = false;
		while (!done) {
			try {
				Thread.sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this exception
			}
			if (!readInWriteThd.isAlive())
				done = true;
		}

		assertFalse(readInWriteThd.isInnerFailed());
		assertFalse(readInWriteThd.isFailed());
		assertTrue(readInWriteThd.isInnerExecuted());
		assertTrue(readInWriteThd.isExecuted());
		
		// CWD: Cannot assert that the elapsed time was >= Constants.SLEEP_TIME
		//    because the J9 VM always sleeps to short when SLEEP_TIME < 500
		assertTrue((readInWriteThd.getEndTime() - readInWriteThd
			.getInnerEndTime()) > 0);
	}

	/**
	 *  Tests scheduling of long running read with write operation.
	 */
	public void testLongRunningReadWithWrites() {
		Object notifier = new Object();
		LongRunningReadThread longReadThread = new LongRunningReadThread(
			getDomain(),
			null,
			notifier);
		ReadThread readThd1 = new ReadThread(getDomain(), null, notifier);
		ReadThread readThd2 = new ReadThread(getDomain(), null, notifier);
		WriteThread writeThd1 = new WriteThread(getDomain(), null, notifier);
		ReadThread readThd3 = new ReadThread(getDomain(), null, notifier);

		synchronized (notifier) {
			try {
				longReadThread.start();
				notifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}

		synchronized (notifier) {
			try {
				readThd1.start();
				notifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}

		synchronized (notifier) {
			try {
				readThd2.start();
				notifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}

		synchronized (notifier) {
			try {
				writeThd1.start();
				notifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}

		synchronized (notifier) {
			try {
				readThd3.start();
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
			if (!longReadThread.isAlive() && !readThd1.isAlive()
				&& !readThd2.isAlive() && !readThd3.isAlive()
				&& !writeThd1.isAlive())
				done = true;
		}

		assertFalse(longReadThread.isFailed());
		assertFalse(readThd1.isFailed());
		assertFalse(readThd2.isFailed());
		assertFalse(readThd3.isFailed());
		assertFalse(writeThd1.isFailed());
		assertTrue(longReadThread.isExecuted());
		assertTrue(readThd1.isExecuted());
		assertTrue(readThd2.isExecuted());
		assertTrue(readThd3.isExecuted());
		assertTrue(writeThd1.isExecuted());
		// The start time of long running thread is before the start time of
		// readThd1 and readThd2
		// The end time of long running thread is before the start time of
		// writeThd1 and readThd3
		// The end time of readThd1 and readThd2 is before the end time of long
		// running thread.
		// Verify readThd1 and readThd2 didn't execute simultaneously
		// Verify writeThd1 and readThd3 didn't execute simulatenously
		//		System.out.println((readThd1.getStartTime() -
		// longReadThread.getStartTime()));
		//		System.out.println(readThd2.getStartTime() -
		// longReadThread.getStartTime());
		//		System.out.println(longReadThread.getEndTime() -
		// readThd1.getEndTime());
		//		System.out.println(longReadThread.getEndTime() -
		// readThd2.getEndTime());
		//		System.out.println(readThd1.getStartTime() -
		// readThd2.getStartTime());
		//		System.out.println(writeThd1.getStartTime() -
		// longReadThread.getEndTime());
		//		System.out.println(readThd3.getStartTime() -
		// longReadThread.getEndTime());
		//		System.out.println(readThd3.getStartTime() -
		// writeThd1.getStartTime());
		assertTrue(!Constants.occurIntersect(longReadThread, readThd1));
		assertTrue(!Constants.occurIntersect(longReadThread, readThd2));
		assertTrue(Constants.occurredAfter(readThd1, readThd2)
			|| Constants.occurredBefore(readThd1, readThd2));
		assertTrue("Read yielded to a write", //$NON-NLS-1$
			(Constants.occurredBefore(longReadThread, writeThd1) || Constants
			.occurredAfter(longReadThread, writeThd1))
			&& !Constants.occurredDuring(longReadThread, writeThd1));
		assertTrue(!Constants.occurIntersect(longReadThread, readThd3));
	}

	/**
	 *  Tests that the lock implementation does not allow interruption of the
	 *  UI thread during timed waits.
	 */
	public void test_interruptionOfUIThread_149982() {
		Object notifier = new Object();
		LongRunningReadThread longReadThread = new LongRunningReadThread(
			getDomain(),
			null,
			notifier);
		WriteThread writeThd1 = new WriteThread(getDomain());
		
		synchronized (notifier) {
			try {
				longReadThread.start();
				notifier.wait();
			} catch (InterruptedException e) {
				// nothing
			}
		}

		final Thread uiThread = Thread.currentThread();
		class Interrupter implements Runnable {
			private volatile boolean dead;
			
			public void run() {
				while (!dead) {
					uiThread.interrupt();
					
					try {
						Thread.sleep(50L);
					} catch (InterruptedException e) {
						// don't care.  Just interrupt the UI again!
					}
				}
			}
			
			void die() {
				dead = true;
			}};
			
		Interrupter interrupter = new Interrupter();
		Thread interrupterThread = new Thread(interrupter);
		interrupterThread.setDaemon(true);
		interrupterThread.start();
		
		try {
			// for good measure, interrupt the "UI thread" now
			uiThread.interrupt();
			
			// run this one on the UI thread.  It will have to wait, and while it is
			//   waiting, the interrupter thread will try to interrupt it
			writeThd1.run();
			
			// if the thread failed, assert that it was  because it was interrupted not
			// in the timed wait phase but in the beginning of the special job rule
			if (writeThd1.isFailed()) {
				assertTrue(writeThd1.failedIn("org.eclipse.core.internal.jobs.JobManager"));
			} else {
				assertTrue(writeThd1.isExecuted());
			}
		} finally {			
			interrupter.die();
			Thread.interrupted();  // don't interfere with the following tests
		}
	}
}
