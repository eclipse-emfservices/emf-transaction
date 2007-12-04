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
 * $Id: EMFTransactionStatusCodes.java,v 1.4 2007/12/04 23:04:49 cdamus Exp $
 */
package org.eclipse.emf.transaction.internal;


/**
 * Error status codes for the EMF Transaction plug-in.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class EMFTransactionStatusCodes {

    public static final int TRANSACTION_ABORTED = 10;
    
	public static final int FACTORY_TYPE = 20;
	public static final int FACTORY_INITIALIZATION = 21;
	public static final int LISTENER_TYPE = 22;
	public static final int LISTENER_INITIALIZATION = 23;
	
	public static final int VALIDATION_FAILURE = 30;
	
	public static final int CONCURRENT_WRITE = 40;
	public static final int READ_ROLLED_BACK = 41;
	public static final int PRECOMMIT_INTERRUPTED = 42;
	public static final int PRECOMMIT_FAILED = 43;
	public static final int POSTCOMMIT_INTERRUPTED = 44;
	public static final int POSTCOMMIT_FAILED = 45;

	public static final int EXCEPTION_HANDLER_FAILED = 50;
	public static final int PRIVILEGED_RUNNABLE_FAILED = 51;
	
	public static final int RELOAD_DURING_UNLOAD = 100;
	
	/** Not instantiable. */
	private EMFTransactionStatusCodes() {
		super();
	}
}
