/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
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
	@Override
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
