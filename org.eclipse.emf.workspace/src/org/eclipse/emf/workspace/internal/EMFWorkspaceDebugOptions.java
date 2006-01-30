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
 * $Id: EMFWorkspaceDebugOptions.java,v 1.1 2006/01/30 19:48:00 cdamus Exp $
 */
package org.eclipse.emf.workspace.internal;


/**
 * The debug options available for this plug-in.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class EMFWorkspaceDebugOptions {
	/**
	 * This class should not be instantiated because it has only static
	 * features.
	 */
	private EMFWorkspaceDebugOptions() {
		super();
	}

	public static final String DEBUG = EMFWorkspacePlugin.getPluginId() + "/debug"; //$NON-NLS-1$

	public static final String EXCEPTIONS_CATCHING = DEBUG + "/exceptions/catching"; //$NON-NLS-1$
	public static final String EXCEPTIONS_THROWING = DEBUG + "/exceptions/throwing"; //$NON-NLS-1$

	public static final String METHODS_ENTERING = DEBUG + "/methods/entering"; //$NON-NLS-1$
	public static final String METHODS_EXITING = DEBUG + "/methods/exiting"; //$NON-NLS-1$
}
