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
 * $Id: EditingDomainManager.java,v 1.5 2007/11/14 18:14:00 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * The editing domain manager initializes editing domain instances from the
 * <code>org.eclipse.emf.transaction.editingDomains</code> extension point.
 * It also configures listeners from the
 * <code>org.eclipse.emf.transaction.listeners</code> point.
 *
 * @author Christian W. Damus (cdamus)
 */
public class EditingDomainManager {
	private static final String EXT_POINT_DOMAINS = "editingDomains"; //$NON-NLS-1$
	private static final String E_DOMAIN = "editingDomain"; //$NON-NLS-1$
	private static final String A_ID = "id"; //$NON-NLS-1$
	private static final String A_FACTORY = "factory"; //$NON-NLS-1$

	private static final String EXT_POINT_LISTENERS = "listeners"; //$NON-NLS-1$
	private static final String E_LISTENER = "listener"; //$NON-NLS-1$
	private static final String A_CLASS = "class"; //$NON-NLS-1$

	private static final EditingDomainManager INSTANCE = new EditingDomainManager();
	
	private Collection<IConfigurationElement> universalListeners;
	private final Map<String, Reference<ResourceSetListener>> listeners =
		new java.util.HashMap<String, Reference<ResourceSetListener>>();
	
	/**
	 * Not instantiable by clients.
	 */
	private EditingDomainManager() {
		super();
	}
	
	/**
	 * Gets the singleton instance.
	 * 
	 * @return the instance
	 */
	public static EditingDomainManager getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Creates the editing domain registered on the extension point under the
	 * specified <code>id</code>, if an appropriate extension exists.
	 * 
	 * @param id the editing domain ID to initialize
	 * 
	 * @return the corresponding editing domain, or <code>null</code> if no
	 *     such extension was found
	 */
	public TransactionalEditingDomain createEditingDomain(String id) {
		TransactionalEditingDomain result = null;
		
		IConfigurationElement config = getDomainConfig(id);
		
		if (config != null) {
			String factoryClass = config.getAttribute(A_FACTORY);
			
			if ((factoryClass == null) || (factoryClass.trim().length() == 0)) {
				// default editing domain factory
				result = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain(); 
			} else {
				// client-specified domain factory
				try {
					Object factory = config.createExecutableExtension(A_FACTORY);
					
					if (factory instanceof TransactionalEditingDomain.Factory) {
						result = ((TransactionalEditingDomain.Factory) factory).createEditingDomain(); 
					} else {
						EMFTransactionPlugin.getPlugin().log(
								new Status(
										IStatus.ERROR,
										EMFTransactionPlugin.getPluginId(),
										EMFTransactionStatusCodes.FACTORY_TYPE,
										NLS.bind(
											Messages.factoryInterface,
											factory.getClass().getName(),
											id),
										null));
					}
				} catch (CoreException e) {
					Tracing.catching(EditingDomainManager.class, "createEditingDomain", e); //$NON-NLS-1$
					EMFTransactionPlugin.INSTANCE.log(new MultiStatus(
						EMFTransactionPlugin.getPluginId(),
						EMFTransactionStatusCodes.FACTORY_INITIALIZATION,
						new IStatus[] {e.getStatus()},
						NLS.bind(Messages.factoryInitialization, id),
						null));
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Queries whether the specified editing domain ID is statically registered
	 * on our extension point.
	 * 
	 * @param id the domain ID to query
	 * 
	 * @return whether it is statically registered
	 */
	public boolean isStaticallyRegistered(String id) {
		return getDomainConfig(id) != null;
	}
	
	/**
	 * Creates (if necessary) listeners registered against the specified
	 * editing domain ID and and adds them to it.  Note that this includes
	 * listeners registered against all domains (by not specifying an ID).
	 * <p>
	 * At most a single instance of any registered listener is created.
	 * </p>
	 * 
	 * @param id the editing domain ID
	 * @param domain the editing domain to which to add the listeners
	 */
	public void configureListeners(String id, TransactionalEditingDomain domain) {
		Collection<IConfigurationElement> configs = getListenerConfigs(id);
		
		for (IConfigurationElement next : configs) {
			ResourceSetListener listener = getListener(next, true);
			
			if (listener != null) {
				domain.addResourceSetListener(listener);
			}
		}
	}
	
	/**
	 * Removes from the editing domain the listeners that are registered against 
	 * its ID.  Note that this includes
	 * listeners registered against all domains (by not specifying an ID).
	 * 
	 * @param id the editing domain ID
	 * @param domain the editing domain from which to remove the listeners
	 */
	public void deconfigureListeners(String id, TransactionalEditingDomain domain) {
		Collection<IConfigurationElement> configs = getListenerConfigs(id);
		
		for (IConfigurationElement next : configs) {
			ResourceSetListener listener = getListener(next, false);
			
			if (listener != null) {
				domain.removeResourceSetListener(listener);
			}
		}
	}
	
	/**
	 * Retrieves the configuration element for the extension providing
	 * the specified domain ID.
	 * 
	 * @param id the domain ID to retrieve
	 * @return the corresponding configuration element, or <code>null</code> if this
	 *     ID is not registered on the extension point
	 */
	private IConfigurationElement getDomainConfig(String id) {
		IConfigurationElement result = null;
		
		IConfigurationElement[] configs = Platform.getExtensionRegistry().getConfigurationElementsFor(
				EMFTransactionPlugin.getPluginId(),
				EXT_POINT_DOMAINS);
		
		for (int i = 0; (result == null) && (i < configs.length); i++) {
			if (E_DOMAIN.equals(configs[i].getName()) && id.equals(configs[i].getAttribute(A_ID))) {
				result = configs[i];
			}
		}
		
		return result;
	}
	
	/**
	 * Retrieves the configuration elements for listeners registered on the specified
	 * editing domain ID.  This includes listeners that are registered against all
	 * editing domains.
	 * 
	 * @param id the domain ID to retrieve
	 * @return the configuration elements for listeners registered to this ID
	 */
	private Collection<IConfigurationElement> getListenerConfigs(String id) {
		Collection<IConfigurationElement> result =
			new java.util.ArrayList<IConfigurationElement>();
		
		IConfigurationElement[] configs = Platform.getExtensionRegistry().getConfigurationElementsFor(
				EMFTransactionPlugin.getPluginId(),
				EXT_POINT_LISTENERS);
		
		for (IConfigurationElement element : configs) {
			if (E_LISTENER.equals(element.getName())) {
				IConfigurationElement[] domains = element.getChildren(E_DOMAIN);
				
				for (IConfigurationElement element2 : domains) {
					if (id.equals(element2.getAttribute(A_ID))) {
						result.add(element);
						break;
					}
				}
			}
		}
		
		result.addAll(getUniversalListenerConfigs());
		
		return result;
	}
	
	/**
	 * Retrieves the configuration elements for listeners that are registered on all
	 * editing domains.
	 * 
	 * @return the configuration elements for universal listeners
	 */
	private Collection<IConfigurationElement> getUniversalListenerConfigs() {
		if (universalListeners == null) {
			universalListeners = new java.util.ArrayList<IConfigurationElement>();
			
			IConfigurationElement[] configs = Platform.getExtensionRegistry().getConfigurationElementsFor(
					EMFTransactionPlugin.getPluginId(),
					EXT_POINT_LISTENERS);
			
			for (IConfigurationElement element : configs) {
				if (E_LISTENER.equals(element.getName())) {
					IConfigurationElement[] domains = element.getChildren(E_DOMAIN);
					
					if (domains.length == 0) {
						universalListeners.add(element);
					}
				}
			}
		}
		
		return universalListeners;
	}
	
	/**
	 * Initializes a listener from the extension point configuration element.
	 * The <code>create</code> argument determines whether to lazily create the
	 * listener; it should be <code>true</code> when adding listeners to editing
	 * domains and <code>false</code> when removing them.
	 * 
	 * @param config the configuration element
	 * @param create if the listener does not exist yet, create it
	 * @return the listener, or <code>null</code> if either the configuration is
	 *     invalid or <code>create = false</code> and the listener does not yet exist
	 */
	private ResourceSetListener getListener(IConfigurationElement config, boolean create) {
		ResourceSetListener result = null;
		
		Reference<ResourceSetListener> ref = listeners.get(config.getAttribute(A_CLASS));
		if (ref != null) {
			result = ref.get();
		}
		
		if ((result == null) && create) {
			// initialize the listener because we are trying to add it
			try {
				Object listener = config.createExecutableExtension(A_CLASS);
				
				if (listener instanceof ResourceSetListener) {
					result = (ResourceSetListener) listener;
					ref = new WeakReference<ResourceSetListener>(result);
					listeners.put(result.getClass().getName(), ref);
				} else {
					EMFTransactionPlugin.getPlugin().log(
							new Status(
									IStatus.ERROR,
									EMFTransactionPlugin.getPluginId(),
									EMFTransactionStatusCodes.LISTENER_TYPE,
									NLS.bind(
										Messages.listenerInterface,
										listener.getClass().getName()),
									null));
				}
			} catch (CoreException e) {
				Tracing.catching(EditingDomainManager.class, "getListener", e); //$NON-NLS-1$
				EMFTransactionPlugin.INSTANCE.log(new MultiStatus(
					EMFTransactionPlugin.getPluginId(),
					EMFTransactionStatusCodes.LISTENER_INITIALIZATION,
					new IStatus[] {e.getStatus()},
					NLS.bind(Messages.listenerInitialization, config.getAttribute(A_CLASS)),
					null));
			}
		}
		
		return result;
	}
}
