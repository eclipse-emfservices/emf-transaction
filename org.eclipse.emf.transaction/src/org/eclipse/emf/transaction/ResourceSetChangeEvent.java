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
 * $Id: ResourceSetChangeEvent.java,v 1.4 2007/11/14 18:14:01 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import java.util.EventObject;
import java.util.List;

import org.eclipse.emf.common.notify.Notification;

/**
 * Event object describing the nature of changes in a resource set to
 * {@link ResourceSetListener}s.  Note that the event object is only valid
 * during the scope of the listener call-back invocation; in particular, the
 * editing domain is free to re-use event objects and/or notification lists
 * for performance purposes.  Therefore, if it is necessary to retain the
 * event or its list of notifications beyond the scope of the call-back, this
 * information must be copied by the client.
 * <p>
 * This class is not intended to be extended or instantiated by clients.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see ResourceSetListener
 */
public class ResourceSetChangeEvent
	extends EventObject {

	private static final long serialVersionUID = -6265603064286194469L;
	
	private final Transaction transaction;
	private final List<Notification> notifications;
	
	/**
	 * Initializes me with my source editing domain.
	 * 
	 * @param source my source (must not be <code>null</code>)
	 */
	public ResourceSetChangeEvent(TransactionalEditingDomain source) {
		this(source, null, null);
	}
	
	/**
	 * Initializes me with my source editing domain, command, and notifications.
	 * 
	 * @param source my source (must not be <code>null</code>)
	 * @param transaction the transaction that has made resource set changes
	 * @param notifications a list of events (as {@link Notification}s), in the
	 *     order in which they occurred
	 */
	public ResourceSetChangeEvent(TransactionalEditingDomain source,
			Transaction transaction, List<Notification> notifications) {
		super(source);
		
		this.transaction = transaction;
		this.notifications = notifications;
	}
	
	/**
	 * Obtains the editing domain whose resource set contents changed.
	 * 
	 * @return the editing domain
	 */
	public TransactionalEditingDomain getEditingDomain() {
		return (TransactionalEditingDomain) source;
	}
	
	/**
	 * Obtains the transaction in which resource set changes have occurred.
	 * This is the transaction that is either about to commit or that has
	 * committed.  Of particular interest in the transaction's
	 * {@link Transaction#getStatus() status} after it has committed.
	 * <p>
	 * <b>Note</b> that it is not permitted to attempt to commit or roll back
	 * the transaction during the listener call-back.  Any attempt to do so will
	 * result in an <code>IllegalStateException</code>.
	 * </p>
	 * 
	 * @return the transaction that is committing or committed.  This will
	 *     never be <code>null</code>
	 */
	public Transaction getTransaction() {
		return transaction;
	}
	
	/**
	 * Obtains the list of events (as {@link Notification}s), in the order in
	 * which they occurred, indicating the changes that occurred during the
	 * transaction.
	 * 
	 * @return the changes
	 * 
	 * @see Notification
	 */
	public List<Notification> getNotifications() {
		return notifications;
	}
}
