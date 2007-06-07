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
 * $Id: ResourceSetListenerImpl.java,v 1.3 2007/06/07 14:25:59 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import org.eclipse.emf.common.command.Command;

/**
 * Default implementation of a resource-set listener, useful for extending to
 * implement only the callbacks of interest to the client.
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see ResourceSetChangeEvent
 */
public class ResourceSetListenerImpl
	implements ResourceSetListener {

	private final NotificationFilter filter;
	
	/** Initializes me with the default filter. */
	protected ResourceSetListenerImpl() {
		this(NotificationFilter.NOT_TOUCH);
	}
	
	/**
	 * Initializes me with the specified filter.
	 * 
	 * @param filter a filter, or <code>null</code> to request the default
	 */
	protected ResourceSetListenerImpl(NotificationFilter filter) {
		if (filter == null) {
			this.filter = NotificationFilter.NOT_TOUCH;
		} else {
			this.filter = filter;
		}
	}

	// Documentation copied from the interface
	public NotificationFilter getFilter() {
		return filter;
	}

	/**
	 * The default implementation of this method does nothing, returning
	 * no trigger command.
	 */
	public Command transactionAboutToCommit(ResourceSetChangeEvent event)
		throws RollbackException {
		
		return null;
	}

	/**
	 * The default implementation of this method does nothing.
	 */
	public void resourceSetChanged(ResourceSetChangeEvent event) {
		// do nothing
	}
	
	/**
	 * By default, assume that we want individual transaction pre-commit.
	 */
	public boolean isAggregatePrecommitListener() {
		return false;
	}
	
	/**
	 * By default, assume that we do not only want pre-commit events but also
	 * post-commit events.
	 */
	public boolean isPrecommitOnly() {
		return false;
	}
	
	/**
	 * By default, assume that we do not only want post-commit events but also
	 * pre-commit events.
	 */
	public boolean isPostcommitOnly() {
		return false;
	}
}
