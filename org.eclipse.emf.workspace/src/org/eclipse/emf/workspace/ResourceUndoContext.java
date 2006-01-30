/**
 * <copyright>
 *
 * Copyright (c) 2005, 2006 IBM Corporation and others.
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
 * $Id: ResourceUndoContext.java,v 1.2 2006/01/30 19:48:00 cdamus Exp $
 */
package org.eclipse.emf.workspace;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
	}
	
	// Documentation copied from the interface
	public String getLabel() {
		return Messages.resCtxLabel;
	}

	/**
	 * Matches another <code>context</code> if it is a
	 * <code>ResourceUndoContext</code> having at least one resource in common
	 * with me.
	 */
	public boolean matches(IUndoContext context) {
		boolean result = false;
		
		if (context instanceof ResourceUndoContext) {
			result = getResource() == ((ResourceUndoContext) context).getResource();
		}
		
		return result;
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
	public static Set getAffectedResources(List notifications) {
		Set result;
		
		if (notifications.isEmpty()) {
			result = Collections.EMPTY_SET;
		} else {
			result = new java.util.HashSet();
			
			for (Iterator iter = notifications.iterator(); iter.hasNext();) {
				Notification next = (Notification) iter.next();
				Object notifier = next.getNotifier();
				
				if (notifier instanceof Resource) {
					result.add(notifier);
				} else if (notifier instanceof EObject) {
					EObject eobj = (EObject) notifier;
					
					result.add(eobj.eResource());
					
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
	public static Set getAffectedResources(IUndoableOperation operation) {
		Set result;
		IUndoContext[] contexts = operation.getContexts();
		
		if (contexts.length == 0) {
			result = Collections.EMPTY_SET;
		} else {
			result = new java.util.HashSet();
			
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
			Set resources,
			Notification notification) {
		
		Object oldValue = notification.getOldValue();
		Object newValue = notification.getNewValue();
		
		switch (notification.getEventType()) {
		case Notification.SET:
		case Notification.UNSET:
			if (oldValue != null) {
				resources.add(((EObject) oldValue).eResource());
			}
			if (newValue != null) {
				resources.add(((EObject) newValue).eResource());
			}
			break;
		case Notification.ADD:
			resources.add(((EObject) newValue).eResource());
			break;
		case Notification.ADD_MANY:
			for (Iterator iter = ((Collection) newValue).iterator(); iter.hasNext();) {
				resources.add(((EObject) iter.next()).eResource());
			}
			break;
		case Notification.REMOVE:
			resources.add(((EObject) oldValue).eResource());
			break;
		case Notification.REMOVE_MANY:
			for (Iterator iter = ((Collection) oldValue).iterator(); iter.hasNext();) {
				resources.add(((EObject) iter.next()).eResource());
			}
			break;
		}
	}
}
