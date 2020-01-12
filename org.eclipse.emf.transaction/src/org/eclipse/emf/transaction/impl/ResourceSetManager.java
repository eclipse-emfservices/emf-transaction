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
package org.eclipse.emf.transaction.impl;

import java.util.Collection;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.l10n.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * The resource set manager keeps track of the load state of {@link Resource}s
 * in a resource set managed by a transactional editing domain.  It assists in
 * the enforcement of write transaction semantics for certain kinds of changes
 * to the state of a resource (in particular, its contents).
 * <p>
 * The resource set manager is a singleton rather than a per-editing-domain
 * instance primarily because the notification filters need to be able to
 * distinguish events coming from loading/unloading resources, and filters do
 * not have any editing domain context.  This is not a problem, as the resources
 * are tracked by their object identity, so their states are absolute, not
 * relative to any particular editing domain.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 */
public final class ResourceSetManager {
	private static final ResourceSetManager INSTANCE = new ResourceSetManager();
	
	private final Map<Resource, Boolean> loadingResources = new java.util.WeakHashMap<Resource, Boolean>();
	private final Map<Resource, Boolean> loadedResources = new java.util.WeakHashMap<Resource, Boolean>();
	private final Map<Resource, Boolean> unloadingResources = new java.util.WeakHashMap<Resource, Boolean>();
	
	/**
	 * Not instantiable by clients.
	 */
	private ResourceSetManager() {
		super();
	}

	/**
	 * Obtains the singleton manager instance.
	 * 
	 * @return the singleton instance
	 */
	public static ResourceSetManager getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Observes the specified resource set, usually only when the editing domain
	 * is initialized.  This resource set may already contain resources in a
	 * variety of states, so I will examine each of them in turn and record
	 * their states.
	 * 
	 * @param rset a resource set
	 */
	public synchronized void observe(ResourceSet rset) {
		for (Resource next : rset.getResources()) {
			observe(next);
		}
	}
	
	/**
	 * Analyzes a notification from a resource set for any potential state
	 * changes in its resources.
	 * 
	 * @param rset a resource set sending a notification
	 * @param notification the notification from the resource set
	 */
	public synchronized void observe(ResourceSet rset, Notification notification) {
		if (notification.getFeatureID(null) == ResourceSet.RESOURCE_SET__RESOURCES) {
			Object newValue = notification.getNewValue();
			Object oldValue = notification.getOldValue();
			
			// what else would it be?
			switch (notification.getEventType()) {
			case Notification.SET:
			case Notification.UNSET:
				if (newValue != null) {
					// observe the new resource
					observe((Resource) newValue);
				}
				
				if (oldValue != null) {
					// the old resource is effectively unloaded as far as we are
					//    concerned because it is no longer in this resource set
					setUnloaded((Resource) oldValue);
				}
				break;
			case Notification.ADD:
				if (newValue != null) {
					// observe the new resource
					observe((Resource) newValue);
				}
				break;
			case Notification.ADD_MANY:
				if (newValue != null) {
					@SuppressWarnings("unchecked")
					Collection<Resource> resources = (Collection<Resource>) newValue;
					for (Resource next : resources) {
						// observe the new resource
						observe(next);
					}
				}
				break;
			case Notification.REMOVE:
				if (oldValue != null) {
					// the old resource is effectively unloaded as far as we are
					//    concerned because it is no longer in this resource set
					setUnloaded((Resource) oldValue);
				}
				break;
			case Notification.REMOVE_MANY:
				if (oldValue != null) {
					@SuppressWarnings("unchecked")
					Collection<Resource> resources = (Collection<Resource>) oldValue;
					for (Resource next : resources) {
						// the old resource is effectively unloaded as far as we are
						//    concerned because it is no longer in this resource set
						setUnloaded(next);
					}
				}
				break;
			}
		}
	}
	
	/**
	 * Analyzes the current state of a resource.
	 * 
	 * @param res a resource to analyze
	 */
	private synchronized void observe(Resource res) {
		if (res.isLoaded()) {
			setLoaded(res);
		} else {
			setUnloaded(res);  // just in case
		}
	}
	
	/**
	 * Analyzes a notification from a resource for any potential state change.
	 * 
	 * @param res a resource
	 * @param notification the notification from the resource
	 */
	public synchronized void observe(Resource res, Notification notification) {
		switch (notification.getFeatureID(null)) {
		case Resource.RESOURCE__IS_LOADED:
			if (notification.getNewBooleanValue()) {
				setLoaded(res);
			} else {
				// double-check that it hasn't been internally re-loaded by,
				// e.g., proxy resolution during clearing of adapters from
				// unloaded objects
				if (res.isLoaded()) {
					// whoops!  It's been reloaded
					setLoaded(res);
					
					EMFTransactionPlugin.getPlugin().log(new Status(
					    IStatus.WARNING,
                        EMFTransactionPlugin.getPluginId(),
                        EMFTransactionStatusCodes.RELOAD_DURING_UNLOAD,
                        NLS.bind(Messages.reloadDuringUnload, res.getURI()),
					    null));
				} else {
					setUnloaded(res);
				}
			}
			break;
		case Resource.RESOURCE__CONTENTS:
			if (!isLoaded(res) && res.isLoaded()) {
				// we must be in the process of loading this resource if we didn't
				//    think the resource is loaded yet, but it thinks it is
				setLoading(res);
			} else if (isLoaded(res) && !res.isLoaded()) {
				// we must be in the process of unloading this resource if we
				//    thought the resource was loaded yet, but it isn't loaded
				setUnloading(res);
			}
		}
	}
	
	/**
	 * Queries whether the specified resource is currently loaded.
	 * 
	 * @param res a resource
	 * @return <code>true</code> if the resource has completed loading and is
	 *     not now unloading; <code>false</code>, otherwise
	 */
	public synchronized boolean isLoaded(Resource res) {
		return loadedResources.containsKey(res);
	}
	
	/**
	 * Queries whether the specified resource is currently loading.
	 * 
	 * @param res a resource
	 * @return <code>true</code> if the resource is not yet loaded but is in
	 *     the process of loading; <code>false</code>, otherwise
	 */
	public synchronized boolean isLoading(Resource res) {
		return loadingResources.containsKey(res);
	}
	
	/**
	 * Queries whether the specified resource is currently unloading.
	 * 
	 * @param res a resource
	 * @return <code>true</code> if the resource is loaded but is in
	 *     the process of unloading; <code>false</code>, otherwise
	 */
	public synchronized boolean isUnloading(Resource res) {
		return unloadingResources.containsKey(res);
	}
	
	/**
	 * Queries whether the specified resource is currently unloaded.
	 * 
	 * @param res a resource
	 * @return <code>true</code> if the resource has completed unloading and is
	 *     not now loading; <code>false</code>, otherwise
	 */
	public synchronized boolean isUnloaded(Resource res) {
		return !(isLoaded(res) || isLoading(res) || isUnloading(res));
	}
	
	/**
	 * Records the specified resource's state as 'loaded'.
	 * 
	 * @param res a loaded resource
	 */
	private void setLoaded(Resource res) {
		loadedResources.put(res, Boolean.TRUE);
		loadingResources.remove(res);
		unloadingResources.remove(res);
	}
	
	/**
	 * Records the specified resource's state as 'loading'.
	 * 
	 * @param res a loading resource
	 */
	private void setLoading(Resource res) {
		loadingResources.put(res, Boolean.TRUE);
		loadedResources.remove(res);
		unloadingResources.remove(res);
	}
	
	/**
	 * Records the specified resource's state as 'unloading'.
	 * 
	 * @param res an unloading resource
	 */
	private void setUnloading(Resource res) {
		unloadingResources.put(res, Boolean.TRUE);
		loadedResources.remove(res);
		loadingResources.remove(res);
	}
	
	/**
	 * Records the specified resource's state as 'unloaded'.
	 * 
	 * @param res an unloaded resource
	 */
	private void setUnloaded(Resource res) {
		loadedResources.remove(res);
		loadingResources.remove(res);
		unloadingResources.remove(res);
	}
}
