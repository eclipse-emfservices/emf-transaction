/**
 * <copyright>
 *
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc., and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bug 177642
 *
 * </copyright>
 *
 * $Id: ResourceSetListenerImpl.java,v 1.5 2008/10/09 00:21:15 cdamus Exp $
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
	implements ResourceSetListener.Internal {

	private final NotificationFilter filter;
	
	private TransactionalEditingDomain target;
	
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

	/**
	 * Queries the transactional editing domain, if any, to which I am
	 * listening. Note the assumption of the most common case in which a
	 * listener is only attached to a single domain.
	 * 
	 * @return the editing domain that I listen to, or <code>null</code> if none
	 * @since 1.3
	 */
	protected TransactionalEditingDomain getTarget() {
		return target;
	}
	
	/**
	 * {@linkplain #getTarget() Remembers} the new editing domain that I am now
	 * listening to, if it is not <code>null</code>.
	 * 
	 * @since 1.3
	 */
	public void setTarget(TransactionalEditingDomain domain) {
		if (domain != null) {
			this.target = domain;
		}
	}

	/**
	 * If the specified domain is the one that I {@linkplain #getTarget()
	 * remembered}, then I forget it because I am no longer listening to it.
	 * 
	 * @since 1.3
	 */
	public void unsetTarget(TransactionalEditingDomain domain) {
		if (domain == target) {
			target = null;
		}
	}
}
