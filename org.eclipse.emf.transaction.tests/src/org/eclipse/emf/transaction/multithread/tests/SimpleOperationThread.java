/**
 * Copyright (c) 2005, 2018 IBM Corporation, Christian W. Damus, and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Christian W. Damus - bug 149982
 */
package org.eclipse.emf.transaction.multithread.tests;

import org.eclipse.emf.transaction.TransactionalCommandStack;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * Thread representing a simple model operation.
 * 
 * @author mgoyal
 */
class SimpleOperationThread
		extends Thread {

	// start time.
	protected long startTime = -1;

	// end time
	protected long endTime = -1;

	// flag to determine if execution succeeded.
	protected boolean isExecuted = false;

	// flag to determine if execution failed.
	private boolean isFailed = false;

	// the cause of failure, if it was a throwable
	private Throwable causeOfFailure;

	// Notify Object
	Object notifyObject = null;

	// Wait Object.
	Object waitObject = null;

	private final TransactionalEditingDomain domain;

	/**
	 * Constructor
	 * 
	 * @param waitObject
	 *            Object to wait on
	 * @param notifyObject
	 *            Object to notify
	 */
	public SimpleOperationThread(TransactionalEditingDomain domain, Object waitObject, Object notifyObject) {
		this.domain = domain;
		this.notifyObject = notifyObject;
		this.waitObject = waitObject;
	}

	/**
	 * Returns the start time of this operation
	 * 
	 * @return startTime
	 */
	public long getStartTime() {
		return startTime;
	}

	/**
	 * Returns the end time of this operation.
	 * 
	 * @return endTime
	 */
	public long getEndTime() {
		return endTime;
	}

	/**
	 * Returns true if the execution succeeded
	 * 
	 * @return isExecuted
	 */
	public boolean isExecuted() {
		return isExecuted;
	}

	/**
	 * Returns true if the execution failed.
	 * 
	 * @return isFailed
	 */
	public synchronized boolean isFailed() {
		return isFailed;
	}

	/**
	 * Queries whether the thread failed due to an exception in the given class.
	 * 
	 * @param className
	 *            a class name
	 * @return {@code true} if execution failed in some method of the named class;
	 *         {@code false} otherwise
	 */
	public synchronized boolean failedIn(String className) {
		boolean result = false;

		// Check the causes, too (exp. for the case of InterruptedException caused
		// by an exception in the Lock.uiSafeAcquire() method)
		out: for (Throwable t = causeOfFailure; t != null; t = t.getCause() == t ? null : t.getCause()) {
			for (StackTraceElement next : t.getStackTrace()) {
				if (className.equals(next.getClassName())) {
					result = true;
					break out;
				}
			}
		}

		return result;
	}

	protected synchronized final void setFailed(Throwable t) {
		causeOfFailure = t;
		isFailed = true;
		t.printStackTrace();
	}

	protected final TransactionalEditingDomain getDomain() {
		return domain;
	}

	protected final TransactionalCommandStack getCommandStack() {
		return (TransactionalCommandStack) domain.getCommandStack();
	}
}
