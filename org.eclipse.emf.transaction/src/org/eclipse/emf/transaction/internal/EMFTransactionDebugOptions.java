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
 * $Id: EMFTransactionDebugOptions.java,v 1.2 2007/06/07 14:25:59 cdamus Exp $
 */
package org.eclipse.emf.transaction.internal;


/**
 * The debug options available for this plug-in.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class EMFTransactionDebugOptions {
	/**
	 * This class should not be instantiated because it has only static
	 * features.
	 */
	private EMFTransactionDebugOptions() {
		super();
	}

	public static final String DEBUG = EMFTransactionPlugin.getPluginId() + "/debug"; //$NON-NLS-1$

	public static final String EXCEPTIONS_CATCHING = DEBUG + "/exceptions/catching"; //$NON-NLS-1$
	public static final String EXCEPTIONS_THROWING = DEBUG + "/exceptions/throwing"; //$NON-NLS-1$

	public static final String METHODS_ENTERING = DEBUG + "/methods/entering"; //$NON-NLS-1$
	public static final String METHODS_EXITING = DEBUG + "/methods/exiting"; //$NON-NLS-1$
	
	public static final String LOCKING = DEBUG + "/locking"; //$NON-NLS-1$
	public static final String TRANSACTIONS = DEBUG + "/transactions"; //$NON-NLS-1$
}
