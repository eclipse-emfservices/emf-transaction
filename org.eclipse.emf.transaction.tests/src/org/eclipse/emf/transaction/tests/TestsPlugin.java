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
package org.eclipse.emf.transaction.tests;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * Main plug-in class, used only to gain access to the debug options.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TestsPlugin extends Plugin {
	public static TestsPlugin instance;
	
	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		
		instance = this;
	}
	
	@Override
	public void stop(BundleContext context) throws Exception {
		instance = null;
		
		super.stop(context);
	}
}
