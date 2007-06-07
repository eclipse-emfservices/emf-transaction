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
 * $Id: Messages.java,v 1.2 2007/06/07 14:25:36 cdamus Exp $
 */
package org.eclipse.emf.workspace.examples.extlibrary.internal.l10n;

import org.eclipse.osgi.util.NLS;


/**
 * Localized messages for the EMF Workbench Example Library Editor plug-in.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class Messages
	extends NLS {

	private static final String BUNDLE_NAME = "org.eclipse.emf.workspace.examples.extlibrary.internal.l10n.Messages"; //$NON-NLS-1$

	public static String readConsole_title;
	public static String readJob_title;
	public static String readJob_msg; 
	public static String cmdFailed;
	public static String cmdRollback;
	public static String cmdException;
	
	static {
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

}
