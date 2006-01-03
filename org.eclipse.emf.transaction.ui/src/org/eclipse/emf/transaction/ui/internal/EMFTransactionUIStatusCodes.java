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
 * $Id: EMFTransactionUIStatusCodes.java,v 1.1 2006/01/03 20:44:14 cdamus Exp $
 */
package org.eclipse.emf.transaction.ui.internal;


/**
 * Error status codes for the EMF Transaction plug-in.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class EMFTransactionUIStatusCodes {

	public static final int CONTENT_PROVIDER_INTERRUPTED = 20;
	public static final int LABEL_PROVIDER_INTERRUPTED = 21;
	public static final int PROPERTY_SHEET_INTERRUPTED = 22;
	
	
	/** Not instantiable. */
	private EMFTransactionUIStatusCodes() {
		super();
	}
}
