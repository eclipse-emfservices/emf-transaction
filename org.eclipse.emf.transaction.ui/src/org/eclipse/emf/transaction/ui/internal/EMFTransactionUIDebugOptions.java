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
 * $Id: EMFTransactionUIDebugOptions.java,v 1.1 2006/01/03 20:44:14 cdamus Exp $
 */
package org.eclipse.emf.transaction.ui.internal;


/**
 * The debug options available for this plug-in.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class EMFTransactionUIDebugOptions {
	/**
	 * This class should not be instantiated because it has only static
	 * features.
	 */
	private EMFTransactionUIDebugOptions() {
		super();
	}

	public static final String DEBUG = EMFTransactionUIPlugin.getPluginId() + "/debug"; //$NON-NLS-1$

	public static final String EXCEPTIONS_CATCHING = DEBUG + "/exceptions/catching"; //$NON-NLS-1$
	public static final String EXCEPTIONS_THROWING = DEBUG + "/exceptions/throwing"; //$NON-NLS-1$

	public static final String METHODS_ENTERING = DEBUG + "/methods/entering"; //$NON-NLS-1$
	public static final String METHODS_EXITING = DEBUG + "/methods/exiting"; //$NON-NLS-1$
}
