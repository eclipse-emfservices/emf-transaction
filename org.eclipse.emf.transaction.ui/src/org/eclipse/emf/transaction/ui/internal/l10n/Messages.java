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
 * $Id: Messages.java,v 1.2 2007/06/07 14:26:08 cdamus Exp $
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
