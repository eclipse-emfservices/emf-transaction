/**
 * <copyright>
 *
 * Copyright (c) 2005, 2009 IBM Corporation, Christian W. Damus, and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Christian W. Damus - Bug 264220
 *
 * </copyright>
 *
 * $Id: ResourceUndoContext.java,v 1.6.2.1 2009/02/10 04:17:35 cdamus Exp $
 */
package org.eclipse.emf.workspace;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.internal.l10n.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * An {@link IUndoContext} that tags an EMF operation with a resource affected by it.
 * Two resource contexts match if and only if they reference the same {@link Resource}
 * instance.  An operation may have any number of distinct resource contexts.
 * <p>
 * The determination of which resource is affected by any atomic EMF change is
 * obvious, except for the case of cross-resource reference changes.  When a
 * cross-resource reference is added or removed, then both the referencing and
 * referenced resources are considered to be affected, even if the reference is
 * unidirectional.  The assumption is that even without the back-reference, there
 * is an implicit dependency in that direction.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 */
public final class ResourceUndoContext
	implements IUndoContext {

	private final TransactionalEditingDomain editingDomain;
	
	private final Resource resource;
	private final String label;

	/**
	 * Initializes me with the editing domain that owns a resource and the
	 * resource that I represent.
	 * 
	 * @param domain the editing domain
	 * @param resource the resource that I represent
	 */
	public ResourceUndoContext(TransactionalEditingDomain domain, Resource resource) {
		this.editingDomain = domain;
		this.resource = resource;
		this.label = NLS.bind(Messages.resCtxLabel, resource.getURI());
	}
	
	// Documentation copied from the interface
	public String getLabel() {
		return label;
	}

	/**
	 * I match another <code>context</code> if it is a
	 * <code>ResourceUndoContext</code> representing the same resource as I.
	 */
	public boolean matches(IUndoContext context) {
		return this.equals(context);
	}
	
	/**
	 * I am equal to other <code>ResourceUndoContexts</code> on the same
	 * resource as mine.
	 */
	@Override
	public boolean equals(Object o) {
		boolean result = false;
		
		if (o instanceof ResourceUndoContext) {
			result = getResource() == ((ResourceUndoContext) o).getResource();
		}
		
		return result;
	}

	// Redefines the inherited method
	@Override
	public int hashCode() {
		return resource == null ? 0 : resource.hashCode();
	}
	
	/**
	 * Obtains the resource that I represent.
	 * 
	 * @return my resource
	 */
	public Resource getResource() {
		return resource;
	}
	
	/**
	 * Obtains the editing domain that manages my resource.
	 * 
	 * @return my editing domain
	 */
	public final TransactionalEditingDomain getEditingDomain() {
		return editingDomain;
	}

	/**
	 * Analyzes a list of notifications to extract the set of {@link Resource}s
	 * affected by the changes.
	 * 
	 * @param notifications a list of {@link Notification}s indicating changes
	 *     in a resource set
	 *     
	 * @return the resources affected by the specified notifications.
	 *     The resulting set should be treated as unmodifiable
	 */
	public static Set<Resource> getAffectedResources(
			List<? extends Notification> notifications) {
		
		Set<Resource> result;
		
		if (notifications.isEmpty()) {
			result = Collections.emptySet();
		} else {
			result = new java.util.HashSet<Resource>();
			
			for (Notification next : notifications) {
				Object notifier = next.getNotifier();
				
				if (notifier instanceof Resource) {
					result.add((Resource) notifier);
				} else if (notifier instanceof EObject) {
					EObject eobj = (EObject) notifier;
                    Resource resource = eobj.eResource();
                    
                    if (resource != null) {
                        result.add(resource);
                    }
					
					// if the reference has an opposite, then we will get the
					//   notification from the other end, anyway
					final Object feature = next.getFeature();
					if ((feature instanceof EReference)
							&& (((EReference) feature).getEOpposite() == null)) {
						handleCrossResourceReference(result, next);
					}
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Extracts the set of EMF {@link Resource}s affected by the specified
	 * operation, from the <code>ResourceUndoContext</code>s attached to it.
	 * 
	 * @param operation an undoable operation
	 * 
	 * @return the {@link Resource}s that it affects, or an empty set if none.
	 *     The resulting set should be treated as unmodifiable
	 */
	public static Set<Resource> getAffectedResources(IUndoableOperation operation) {
		Set<Resource> result;
		IUndoContext[] contexts = operation.getContexts();
		
		if (contexts.length == 0) {
			result = Collections.emptySet();
		} else {
			result = new java.util.HashSet<Resource>();
			
			for (int i = 0; i < contexts.length; i++) {
				if (contexts[i] instanceof ResourceUndoContext) {
					result.add(((ResourceUndoContext) contexts[i]).getResource());
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Handles notifications that can potentially represent cross-resource
	 * references.  Helper to the {@link #getAffectedResources(List)} method.
	 * 
	 * @param resources collects the affected resources
	 * @param notification a potential cross-resource reference change notification
	 */
	private static void handleCrossResourceReference(
			Set<Resource> resources,
			Notification notification) {
		
		Object oldValue = notification.getOldValue();
		Object newValue = notification.getNewValue();
        Resource resource;
		
		switch (notification.getEventType()) {
		case Notification.SET:
		case Notification.UNSET:
			// bug 264220: in case of UNSET of a multi-valued reference,
			// the old and new values could be Booleans, for the extra
			// notification of change to the is-set state of the reference
			// (a previous REMOVE_MANY indicated the clearing of the list)
			if (oldValue instanceof EObject) {
                resource = ((EObject) oldValue).eResource();
                
                if (resource != null) {
                    resources.add(resource);
                }
			}
			if (newValue instanceof EObject) {
                resource = ((EObject) newValue).eResource();
                
                if (resource != null) {
                    resources.add(resource);
                }
			}
			break;
		case Notification.ADD:
            resource = ((EObject) newValue).eResource();
            
            if (resource != null) {
                resources.add(resource);
            }
			break;
		case Notification.ADD_MANY: {
		    @SuppressWarnings("unchecked")
		    Collection<EObject> newReferences = (Collection<EObject>) newValue;
			for (EObject next : newReferences) {
                resource = next.eResource();
                
                if (resource != null) {
                    resources.add(resource);
                }
			}
			break;}
		case Notification.REMOVE:
            resource = ((EObject) oldValue).eResource();
            
            if (resource != null) {
                resources.add(resource);
            }
			break;
		case Notification.REMOVE_MANY: {
            @SuppressWarnings("unchecked")
            Collection<EObject> oldReferences = (Collection<EObject>) oldValue;
            for (EObject next : oldReferences) {
                resource = next.eResource();
                
                if (resource != null) {
                    resources.add(resource);
                }
			}
			break;}
		}
	}
	
	@Override
	public String toString() {
	    return getLabel();
	}
}
