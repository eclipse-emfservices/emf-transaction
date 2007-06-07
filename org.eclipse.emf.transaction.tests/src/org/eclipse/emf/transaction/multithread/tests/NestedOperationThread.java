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
 * $Id: NestedOperationThread.java,v 1.3 2007/06/07 14:26:17 cdamus Exp $
 */
package org.eclipse.emf.transaction.multithread.tests;

import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * Thread representing nested Operations.
 * 
 * @author mgoyal
 */
class NestedOperationThread
	extends SimpleOperationThread {

	// Start time for inner operation
	protected long innerStartTime = -1;

	// end time for inner operation
	protected long innerEndTime = -1;

	// flag to determine if inner operation was successful
	protected boolean isInnerExecuted = false;

	// flag to determine if inner operation failed
	protected boolean isInnerFailed = false;

	/**
	 * Constructor
	 * 
	 * @param waitObject
	 * @param notifyObject
	 */
	public NestedOperationThread(TransactionalEditingDomain domain, Object waitObject, Object notifyObject) {
		super(domain, waitObject, notifyObject);
	}

	/**
	 * Returns the start time for the inner operation
	 * 
	 * @return innerStartTime
	 */
	public long getInnerStartTime() {
		return innerStartTime;
	}

	/**
	 * Returns the end time for the inner operation
	 * 
	 * @return innerEndTime
	 */
	public long getInnerEndTime() {
		return innerEndTime;
	}

	/**
	 * Returns true if the inner operation was successful
	 * 
	 * @return isInnerExecuted
	 */
	public boolean isInnerExecuted() {
		return isInnerExecuted;
	}

	/**
	 * Returns true if the inner operation failed.
	 * 
	 * @return isInnerFailed
	 */
	public boolean isInnerFailed() {
		return isInnerFailed;
	}
}
