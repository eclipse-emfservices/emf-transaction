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
 * Thread representing a long running read. This thread yields for other reads.
 * @author mgoyal
 *
 */
class LongRunningReadThread extends ReadThread {
	long timeYielded = 0L;
	
	/**
	 * Constructor
	 * @param waitObject
	 * @param notifyObject
	 */
	public LongRunningReadThread(TransactionalEditingDomain domain, Object waitObject, Object notifyObject) {
		super(domain, waitObject, notifyObject);
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			getDomain().runExclusive(new Runnable() {
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
					
					startTime = System.currentTimeMillis();
					for(int i = 0; i < 10; i++) {
						try {
							sleep(Constants.SLEEP_TIME);
							
							long startYield = System.currentTimeMillis();
							getDomain().yield();
							timeYielded += System.currentTimeMillis() - startYield;
						} catch (InterruptedException e) {
							// ignore this.
						}
					}
					isExecuted = true;
					endTime = System.currentTimeMillis();
				}
			});
		} catch(Exception e) {
			setFailed(e);
		}
	}
}
