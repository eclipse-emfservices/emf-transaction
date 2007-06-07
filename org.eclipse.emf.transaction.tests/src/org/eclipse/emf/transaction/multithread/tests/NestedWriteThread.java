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
 * $Id: NestedWriteThread.java,v 1.3 2007/06/07 14:26:17 cdamus Exp $
 */
package org.eclipse.emf.transaction.multithread.tests;

import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.fixtures.TestCommand;

/**
 * Thread representing write operation nested in a write operation.
 * 
 * @author mgoyal
 */
class NestedWriteThread
	extends NestedOperationThread {

	/**
	 * Constructor
	 * 
	 * @param waitObject
	 * @param notifyObject
	 */
	public NestedWriteThread(TransactionalEditingDomain domain, Object waitObject, Object notifyObject) {
		super(domain, waitObject, notifyObject);
	}

	/**
	 * Default Constructor 
	 */
	public NestedWriteThread(TransactionalEditingDomain domain) {
		this(domain, null, null);
	}

	/** 
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		if (notifyObject != null) {
			synchronized (notifyObject) {
				notifyObject.notify();
			}
		}
		if (waitObject != null) {
			synchronized (waitObject) {
				try {
					waitObject.wait();
				} catch (InterruptedException e) {
					// Nothing..
				}
			}
		}

		Transaction tx = null;

		try {
			tx = ((InternalTransactionalEditingDomain) getDomain()).startTransaction(
				false, null);
			
			startTime = System.currentTimeMillis();
			try {
				sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this.
			}
			final boolean bWriting = true;
			
			try {
				getCommandStack().execute(new TestCommand() {
				
					public void execute() {
						innerStartTime = System.currentTimeMillis();
						
						try {
							sleep(Constants.SLEEP_TIME);
						} catch (InterruptedException e) {
							// ignore this.
						}
						
						if (bWriting && !isExecuted)
							isInnerExecuted = true;
						innerEndTime = System.currentTimeMillis();
					}
				
				});
			} catch (Exception e1) {
				isInnerFailed = true;
			}
			
			try {
				sleep(Constants.SLEEP_TIME);
			} catch (InterruptedException e) {
				// ignore this.
			}
			endTime = System.currentTimeMillis();
			isExecuted = true;
		} catch (Exception e) {
			setFailed(e);
		} finally {
			if (tx != null) {
				try {
					tx.commit();
				} catch (Exception e) {
					setFailed(e);
				}
			}
		}
	}
}
