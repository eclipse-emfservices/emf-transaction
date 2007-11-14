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
 * $Id: EMFWorkbenchUIPlugin.java,v 1.3 2007/11/14 18:14:04 cdamus Exp $
 */
package org.eclipse.emf.workspace.ui.internal;

import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.util.ResourceLocator;
import org.osgi.framework.BundleContext;

/**
 * The main plugin class to be used in the desktop.
 */
public class EMFWorkbenchUIPlugin extends EMFPlugin {

	public static final EMFWorkbenchUIPlugin INSTANCE =
		new EMFWorkbenchUIPlugin();

	//The shared instance.
	private static Implementation plugin;
	
	/**
	 * The constructor.
	 */
	public EMFWorkbenchUIPlugin() {
		super(new ResourceLocator[]{});
	}

	// implements the inherited method
	@Override
    public ResourceLocator getPluginResourceLocator() {
		return plugin;
	}

	/**
	 * Obtains the Eclipse plug-in that I implement.
	 * 
	 * @return my Eclipse plug-in self
	 */
	public static Implementation getPlugin() {
		return plugin;
	}

	/**
	 * Obtains my plug-in identifier.
	 * 
	 * @return my plug-in unique ID
	 */
	public static String getPluginId() {
		return getPlugin().getBundle().getSymbolicName();
	}

	/**
	 * The definition of the Eclipse plug-in flavour of this EMF plug-in.
	 * 
	 * @author Christian W. Damus (cdamus)
	 */
	public static class Implementation extends EMFPlugin.EclipsePlugin {
		/**
		 * Initializes me with my Eclipse plug-in descriptor.
		 */
		public Implementation() {
			super();

			// Remember the static instance.
			//
			EMFWorkbenchUIPlugin.plugin = this;
		}

		/**
		 * This method is called upon plug-in activation
		 */
		@Override
        public void start(BundleContext context) throws Exception {
			super.start(context);
		}

		/**
		 * This method is called when the plug-in is stopped
		 */
		@Override
        public void stop(BundleContext context) throws Exception {
			super.stop(context);
			EMFWorkbenchUIPlugin.plugin = null;
		}
	}

}
