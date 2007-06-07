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
 * $Id: WriteThread.java,v 1.3 2007/06/07 14:26:17 cdamus Exp $
 */
package org.eclipse.emf.transaction.multithread.tests;

import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.fixtures.TestCommand;


/**
 * Thread representing a simple Write Operation.
 * @author mgoyal
 *
 */
class WriteThread extends SimpleOperationThread {
	/**
	 * Constructor
	 * @param waitObject
	 * @param notifyObject
	 */
	public WriteThread(TransactionalEditingDomain domain, Object waitObject, Object notifyObject) {
		super(domain, waitObject, notifyObject);
	}
	
	/**
	 * Constructor 
	 */
	public WriteThread(TransactionalEditingDomain domain) {
		this(domain, null, null);
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		if(notifyObject != null) {
			synchronized(notifyObject) {
				notifyObject.notify();
			}
		}

		if(waitObject != null) {
			synchronized(waitObject) {
				try {
					waitObject.wait();
				} catch(InterruptedException e) {
					// Nothing..
				}
			}
		}

		try {
			getCommandStack().execute(new TestCommand() {
					public void execute() {
						startTime = System.currentTimeMillis();
						try {
							sleep(Constants.SLEEP_TIME);
						} catch (InterruptedException e) {
							// ignore this.
						}
						isExecuted = true;
						endTime = System.currentTimeMillis();
					}
				}, null);
		} catch(Exception e) {
			setFailed(e);
		}
	}
}
