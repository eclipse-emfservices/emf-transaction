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
 * $Id: EMFTransactionPlugin.java,v 1.4 2009/08/11 09:25:19 bgruschko Exp $
 */
package org.eclipse.emf.transaction.internal;

import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.util.ResourceLocator;
import org.osgi.framework.BundleContext;

/**
 * The main plugin class to be used in the desktop.
 */
public class EMFTransactionPlugin extends EMFPlugin {

	public static final EMFTransactionPlugin INSTANCE =
		new EMFTransactionPlugin();

	//The shared instance.
	private static Implementation plugin;
	
	/**
	 * The constructor.
	 */
	public EMFTransactionPlugin() {
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
		if (!EMFPlugin.IS_ECLIPSE_RUNNING) {
			return "org.eclipse.emf.transaction"; //$NON-NLS-1$
		}
		else {
			return getPlugin().getBundle().getSymbolicName();
		}
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
			EMFTransactionPlugin.plugin = this;
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
			EMFTransactionPlugin.plugin = null;
		}
	}

}
