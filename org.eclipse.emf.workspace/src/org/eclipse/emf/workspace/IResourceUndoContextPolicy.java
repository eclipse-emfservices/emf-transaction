/**
 * <copyright>
 * 
 * Copyright (c) 2008 Zeligsoft Inc. and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Zeligsoft - Initial API and implementation
 * 
 * </copyright>
 *
 * $Id: IResourceUndoContextPolicy.java,v 1.1 2008/11/02 18:43:21 cdamus Exp $
 */

package org.eclipse.emf.workspace;

import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * <p>
 * A rule determining the resources for which an {@link IUndoableOperation}
 * should be tagged with {@link ResourceUndoContext}s. In general, these are the
 * resources that
 * </p>
 * <ul>
 * <li>are modified by the operation, such that they are become dirty, and/or</li>
 * <li>whose editors should show the operation in their Undo menu</li>
 * </ul>
 * <p>
 * Clients may implement this interface, but it is recommended to extend the
 * {@link AbstractResourceUndoContextPolicy} class whenever possible.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 * @since 1.3
 * 
 * @see AbstractResourceUndoContextPolicy
 */
public interface IResourceUndoContextPolicy {

	/**
	 * The default undo-context policy used by editing domains for which none is
	 * assigned by the client application.
	 */
	IResourceUndoContextPolicy DEFAULT = new AbstractResourceUndoContextPolicy() {
	};

	/**
	 * Determines the resources in the undo context of the specified
	 * <tt>operation</tt>, during which execution the changes indicated by the
	 * given <tt>notifications</tt> occurred. This operation may be called
	 * several times for the same operation, but always with different
	 * notifications.
	 * 
	 * @param operation
	 *            the operation. It may or may not have finished executing. Must
	 *            not be <code>null</code>
	 * @param notifications
	 *            a list of notifications of changes caused by the operation
	 *            during its execution, in the order in which they occurred.
	 *            This may be an empty list, but never <code>null</code>
	 * 
	 * @return the resources that are the undo context of this operation, or an
	 *         empty list if none. Never ruterns <code>null</code>
	 */
	Set<Resource> getContextResources(IUndoableOperation operation,
			List<? extends Notification> notifications);
}
