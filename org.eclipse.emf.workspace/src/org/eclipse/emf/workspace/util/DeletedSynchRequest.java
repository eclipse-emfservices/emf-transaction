/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
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
 * $Id: DeletedSynchRequest.java,v 1.1 2006/01/30 16:18:18 cdamus Exp $
 */
package org.eclipse.emf.workspace.util;

import org.eclipse.emf.ecore.resource.Resource;

/**
 * Concrete synchronization request for resource deletions.
 *
 * @author Christian W. Damus (cdamus)
 */
class DeletedSynchRequest extends SynchRequest {
	/**
	 * Initializes me with the synchronizer on whose behalf I perform a
	 * synchronization and the resource whose workspace partner is deleted.
	 * 
	 * @param synch the workspace synchronizer
	 * @param resource the resource that has been deleted
	 */
	DeletedSynchRequest(WorkspaceSynchronizer synch, Resource resource) {
		super(synch, resource);
	}
	
	protected void doPerform() {
		if (!synch.getDelegate().handleResourceDeleted(resource)) {
			// note that if our delegate is the default, it
			//     will always return true
			WorkspaceSynchronizer.defaultDelegate.handleResourceDeleted(resource);
		}
	}
}