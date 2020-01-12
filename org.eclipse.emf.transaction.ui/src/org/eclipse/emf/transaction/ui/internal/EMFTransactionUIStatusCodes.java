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
