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
 * $Id: NestedReadThread.java,v 1.2 2006/01/30 19:47:50 cdamus Exp $
 */
package org.eclipse.emf.transaction.multithread.tests;

import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * Thread representing read operation nested in a read operation.
 * 
 * @author mgoyal
 */
class NestedReadThread
	extends NestedOperationThread {

	/**
	 * Constructor
	 * 
	 * @param waitObject
	 * @param notifyObject
	 */
	public NestedReadThread(TransactionalEditingDomain domain, Object waitObject, Object notifyObject) {
		super(domain, waitObject, notifyObject);
	}

	/**
	 * Default Constructor
	 */
	public NestedReadThread(TransactionalEditingDomain domain) {
		this(domain, null, null);
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		try {
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

			getDomain().runExclusive(new Runnable() {
				public void run() {
					startTime = System.currentTimeMillis();
					final boolean bReading = true;
					try {
						sleep(Constants.SLEEP_TIME);
					} catch (InterruptedException e) {
						// ignore this.
					}
					try {
						getDomain().runExclusive(new Runnable() {
							public void run() {
								innerStartTime = System.currentTimeMillis();
								try {
									sleep(Constants.SLEEP_TIME);
								} catch (InterruptedException e) {
									// ignore this.
								}
								if (bReading && !isExecuted)
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
					isExecuted = true;
					endTime = System.currentTimeMillis();
				}
			});
		} catch (Exception e) {
			setFailed(e);
		}
	}
}