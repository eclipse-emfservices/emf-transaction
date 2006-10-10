/**
 * <copyright>
 *
 * Copyright (c) 2005, 2006 IBM Corporation and others.
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
 * $Id: SimpleOperationThread.java,v 1.3 2006/10/10 14:31:40 cdamus Exp $
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

	// flag to determing if execution succeeded.
	protected boolean isExecuted = false;

	// flag to determine if execution failed.
	private boolean isFailed = false;

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
	
	protected synchronized final void setFailed(Throwable t) {
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
