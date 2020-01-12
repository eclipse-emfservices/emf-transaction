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
