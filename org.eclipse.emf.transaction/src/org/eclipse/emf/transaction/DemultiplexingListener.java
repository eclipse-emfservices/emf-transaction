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
 * $Id: DemultiplexingListener.java,v 1.1 2006/01/03 20:41:55 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import java.util.Iterator;

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
	 * @see #handleNotification(TXEditingDomain, Notification)
	 */
	public void resourceSetChanged(ResourceSetChangeEvent event) {
		for (Iterator iter = event.getNotifications().iterator(); iter.hasNext();) {
			Notification next = (Notification) iter.next();
			
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
	protected abstract void handleNotification(TXEditingDomain domain, Notification notification);

	/**
	 * I want only post-commit events, not pre-commit events.
	 */
	public boolean isPostcommitOnly() {
		return true;
	}
}
