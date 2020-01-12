/**
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc., and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bug 240775
 */
package org.eclipse.emf.workspace;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.emf.common.notify.Notification;
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
	 * @param notifications
	 *            a list of {@link Notification}s indicating changes in a
	 *            resource set
	 * 
	 * @return the resources affected by the specified notifications. The
	 *         resulting set should be treated as unmodifiable
	 * 
	 * @deprecated Since the 1.3 release, use the
	 *             {@link IResourceUndoContextPolicy#getContextResources(IUndoableOperation, List)}
	 *             method of the editing domain's resource undo-context policy,
	 *             instead
	 */
	public static Set<Resource> getAffectedResources(
			List<? extends Notification> notifications) {

		// the default implementation never considers the operation, so a
		// null value will not hurt it
		return IResourceUndoContextPolicy.DEFAULT.getContextResources(null,
			notifications);
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
	
	@Override
	public String toString() {
	    return getLabel();
	}
}
