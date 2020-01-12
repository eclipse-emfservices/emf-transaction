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
package org.eclipse.emf.transaction.ui.internal.l10n;

import org.eclipse.osgi.util.NLS;


/**
 * Localized messages for the EMF Transaction UI plug-in.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class Messages
	extends NLS {

	private static final String BUNDLE_NAME = "org.eclipse.emf.transaction.ui.internal.l10n.Messages"; //$NON-NLS-1$

	public static String contentInterrupt;
	public static String labelInterrupt;
	public static String propertyInterrupt;
	
	static {
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

}
