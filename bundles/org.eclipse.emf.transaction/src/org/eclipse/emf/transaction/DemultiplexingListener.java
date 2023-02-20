/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: DemultiplexingListener.java,v 1.4 2007/11/14 18:14:01 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import org.eclipse.emf.common.notify.Notification;

/**
 * A convenient superclass for post-commit listeners to process
 * {@link Notification}s one at a time.  This effectively demultiplexes the
 * list of batched notifications.
 *
 * @author Christian W. Damus (cdamus)
 */
public abstract class DemultiplexingListener extends ResourceSetListenerImpl {

	/** Initializes me with the default filter. */
	public DemultiplexingListener() {
		super();
	}

	/**
	 * Initializes me with the specified filter.
	 * 
	 * @param filter my filter, or <code>null</code> to specify the default
	 */
	public DemultiplexingListener(NotificationFilter filter) {
		super(filter);
	}

	/**
	 * Implements the post-commit callback by processing the <code>event</code>'s
	 * notifications one by one, delegating to the {@link #handleNotification}
	 * method.
	 * 
	 * @see #handleNotification(TransactionalEditingDomain, Notification)
	 */
	@Override
	public void resourceSetChanged(ResourceSetChangeEvent event) {
		for (Notification next : event.getNotifications()) {
			handleNotification(event.getEditingDomain(), next);
		}
	}
	
	/**
	 * Implemented by subclasses to respond to each notification in serial order.
	 * 
	 * @param domain the editing domain from which the notification originated
	 * @param notification the notification describing a change in the model
	 * 
	 * @see #resourceSetChanged(ResourceSetChangeEvent)
	 */
	protected abstract void handleNotification(TransactionalEditingDomain domain, Notification notification);

	/**
	 * I want only post-commit events, not pre-commit events.
	 */
	@Override
	public boolean isPostcommitOnly() {
		return true;
	}
}
