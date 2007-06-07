/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
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
 * $Id: WriteOperationTest.java,v 1.2 2007/06/07 14:26:17 cdamus Exp $
 */
package org.eclipse.emf.transaction.multithread.tests;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * Testcase for testing scheduling of Write Operation scenarios
 * @author mgoyal
 *  
 */
public class WriteOperationTest
	extends AbstractMultithreadTest {

	public static Test suite() {
		return new TestSuite(WriteOperationTest.class, "Writer Thread Tests"); //$NON-NLS-1$
	}

	/**
	 *  Tests scheduling of simple write operation.
	 */
	public void testWriteOperation() {
		WriteThread writeThread1 = new WriteThread(getDomain());

		writeThread1.start();
		boolean done = false;
		while (!done) {
			try {
				Thread.sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this exception
			}
			if (!writeThread1.isAlive())
				done = true;
		}

		assertFalse(writeThread1.isFailed());
		assertTrue(writeThread1.isExecuted());
	}

	/**
	 *  Tests scheduling of two simultaneous write operations.
	 */
	public void testSimultaneousWrites() {
		Object notifier = new Object();
		WriteThread writeThread1 = new WriteThread(getDomain(), null, notifier);
		WriteThread writeThread2 = new WriteThread(getDomain(), null, notifier);

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
			if (!writeThread1.isAlive() && !writeThread2.isAlive())
				done = true;
		}

		assertFalse(writeThread1.isFailed());
		assertFalse(writeThread2.isFailed());
		assertTrue(writeThread1.isExecuted());
		assertTrue(writeThread2.isExecuted());
		assertTrue(Constants.occurredAfter(writeThread1, writeThread2)
			|| Constants.occurredBefore(writeThread1, writeThread2));
	}

	/**
	 * Tests scheduling of Nested Write Operations.
	 */
	public void testNestedWrites() {
		NestedWriteThread writeThread1 = new NestedWriteThread(getDomain());
		writeThread1.start();
		boolean done = false;
		while (!done) {
			try {
				Thread.sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this exception
			}
			if (!writeThread1.isAlive())
				done = true;
		}

		assertFalse(writeThread1.isInnerFailed());
		assertFalse(writeThread1.isFailed());
		assertTrue(writeThread1.isInnerExecuted());
		assertTrue(writeThread1.isExecuted());
		assertTrue(Constants.occurredDuring(writeThread1.getStartTime(),
			writeThread1.getEndTime(), writeThread1.getInnerStartTime(),
			writeThread1.getInnerEndTime()));
	}

}
