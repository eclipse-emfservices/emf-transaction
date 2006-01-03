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
 * $Id: LongRunningReadThread.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.multithread.tests;

import org.eclipse.emf.transaction.TXEditingDomain;

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
	public LongRunningReadThread(TXEditingDomain domain, Object waitObject, Object notifyObject) {
		super(domain, waitObject, notifyObject);
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
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