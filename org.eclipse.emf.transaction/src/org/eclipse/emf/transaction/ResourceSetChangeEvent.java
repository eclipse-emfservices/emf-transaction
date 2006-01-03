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
 * $Id: ResourceSetChangeEvent.java,v 1.1 2006/01/03 20:41:55 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import java.util.EventObject;
import java.util.List;

import org.eclipse.emf.common.notify.Notification;

/**
 * Event object describing the nature of changes in a resource set to
 * {@link ResourceSetListener}s.
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
	private final List notifications;
	
	/**
	 * Initializes me with my source editing domain.
	 * 
	 * @param source my source (must not be <code>null</code>)
	 */
	public ResourceSetChangeEvent(TXEditingDomain source) {
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
	public ResourceSetChangeEvent(TXEditingDomain source, Transaction transaction, List notifications) {
		super(source);
		
		this.transaction = transaction;
		this.notifications = notifications;
	}
	
	/**
	 * Obtains the editing domain whose resource set contents changed.
	 * 
	 * @return the editing domain
	 */
	public TXEditingDomain getEditingDomain() {
		return (TXEditingDomain) source;
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
	public List getNotifications() {
		return notifications;
	}
}
