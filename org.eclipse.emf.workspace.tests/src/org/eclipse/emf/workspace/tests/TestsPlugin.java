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
 * $Id: TestsPlugin.java,v 1.2 2007/06/07 14:26:03 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * Main plug-in class, used only to gain access to the debug options.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TestsPlugin extends Plugin {
	public static TestsPlugin instance;
	
	public void start(BundleContext context) throws Exception {
		super.start(context);
		
		instance = this;
	}
	
	public void stop(BundleContext context) throws Exception {
		instance = null;
		
		super.stop(context);
	}
}
