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
 * $Id: ReadThread.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.multithread.tests;

import org.eclipse.emf.transaction.TXEditingDomain;


/**
 * Thread for a simple read operation
 * 
 * @author mgoyal
 */
class ReadThread extends SimpleOperationThread {
	/**
	 * Constructor
	 * @param waitObject
	 * @param notifyObject
	 */
	public ReadThread(TXEditingDomain domain, Object waitObject, Object notifyObject) {
		super(domain, waitObject, notifyObject);
	}
	
	/**
	 * Default constructor 
	 */
	public ReadThread(TXEditingDomain domain) {
		this(domain, null, null);
	}
	
	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		try {
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

			getDomain().runExclusive(new Runnable() {
				public void run() {
					startTime = System.currentTimeMillis();
					try {
						sleep(Constants.SLEEP_TIME);
					} catch (InterruptedException e) {
						// ignore this.
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