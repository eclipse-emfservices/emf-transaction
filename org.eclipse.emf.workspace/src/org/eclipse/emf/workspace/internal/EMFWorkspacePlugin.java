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
 * $Id: EMFWorkspacePlugin.java,v 1.2 2007/06/07 14:25:44 cdamus Exp $
 */
package org.eclipse.emf.workspace.internal;

import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.util.ResourceLocator;
import org.osgi.framework.BundleContext;

/**
 * The main plugin class to be used in the desktop.
 */
public class EMFWorkspacePlugin extends EMFPlugin {

	public static final EMFWorkspacePlugin INSTANCE =
		new EMFWorkspacePlugin();

	//The shared instance.
	private static Implementation plugin;
	
	/**
	 * The constructor.
	 */
	public EMFWorkspacePlugin() {
		super(new ResourceLocator[]{});
	}

	// implements the inherited method
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
			EMFWorkspacePlugin.plugin = this;
		}

		/**
		 * This method is called upon plug-in activation
		 */
		public void start(BundleContext context) throws Exception {
			super.start(context);
		}

		/**
		 * This method is called when the plug-in is stopped
		 */
		public void stop(BundleContext context) throws Exception {
			super.stop(context);
			EMFWorkspacePlugin.plugin = null;
		}
	}

}
