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
 * $Id: SynchRequest.java,v 1.2 2007/06/07 14:25:44 cdamus Exp $
 */
package org.eclipse.emf.workspace.util;

import org.eclipse.emf.ecore.resource.Resource;

/**
 * Abstract definition of a single request to synchronize a workspace
 * resource change with the EMF resource representation in the editing
 * domain.
 *
 * @author Christian W. Damus (cdamus)
 */
abstract class SynchRequest {
	protected final WorkspaceSynchronizer synch;
	protected final Resource resource;
	
	/**
	 * Initializes me with the synchronizer on whose behalf I perform a
	 * synchronization and the resource whose workspace partner is changed.
	 * 
	 * @param synch the workspace synchronizer
	 * @param resource the resource that has changed
	 */
	SynchRequest(WorkspaceSynchronizer synch, Resource resource) {
		this.synch = synch;
		this.resource = resource;
	}
	
	/**
	 * Performs the synchronization on the synchronizer's behalf.
	 * 
	 * @throws InterruptedException if the job thread is interrupted while
	 *     attempting to start a read-only transaction in the editing domain
	 */
	public final void perform() throws InterruptedException {
		synch.getEditingDomain().runExclusive(new Runnable() {
			public void run() {
				doPerform();
			}});
	}
	
	/**
	 * Implemented by subclasses to actually perform their synchronization
	 * by delegation to the synchronizer's delegate.
	 */
	protected abstract void doPerform();
}
