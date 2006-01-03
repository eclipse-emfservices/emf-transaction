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
 * $Id: TXChangeRecorder.java,v 1.1 2006/01/03 20:41:54 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Collections;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;

/**
 * The change recorder for a {@link org.eclipse.emf.transaction.TXEditingDomain},
 * used by transactions to record rollback information and to detect changes that
 * violate the transaction protocol.  It also forwards notifications to the
 * domain's currently active transaction.
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see InternalTXEditingDomain#getChangeRecorder()
 * @see TXValidator
 * @see InternalTransaction#add(Notification)
 */
public class TXChangeRecorder
	extends ChangeRecorder {
	private final InternalTXEditingDomain domain;
	
	/**
	 * Initializes me with the editing domain that I assist and the resource
	 * set in which I will record changes.  Note that I do not begin recording
	 * until specifically asked to (unlike the superclass).
	 * 
	 * @param domain my editing domain
	 * @param rset my resource set
	 */
	public TXChangeRecorder(InternalTXEditingDomain domain, ResourceSet rset) {
		super(rset);
		this.domain = domain;
		
		// super already started recording
		endRecording();
		
		// tell the resource set manager about any resources that already exist
		ResourceSetManager.getInstance().observe(rset);
	}
	
	/**
	 * Obtains the editing domain that I assist.
	 * 
	 * @return my editing domain
	 */
	public final InternalTXEditingDomain getEditingDomain() {
		return domain;
	}
	
	/**
	 * Starts recording changes in my editing domain.
	 */
	public void beginRecording() {
		beginRecording(Collections.singleton(getEditingDomain().getResourceSet()));
	}
	
	/**
	 * Detects whether the change indicated by the specified notification
	 * violates the transaction protocol and/or how it changes the load state
	 * of a resource (if it all), in addition to recording the change (if I
	 * am currently recording) and passing it along to the domain's current
	 * transaction (if any).
	 */
	public void notifyChanged(Notification notification) {
		super.notifyChanged(notification);
		
		if (notification.getEventType() != Notification.REMOVING_ADAPTER) {
			// the removing adapter event is only sent to me when I am removed
			//    from an object.  Nobody else would find it useful
			
			Object notifier = notification.getNotifier();
			if (notifier instanceof ResourceSet) {
				processResourceSetNotification(notification);
			} else if (notifier instanceof Resource) {
				processResourceNotification(notification);
			} else {
				processObjectNotification(notification);
			}
		}
	}
	
	/**
	 * Analyzes a resource set notification for changes in the load state of
	 * resources and violations of the transaction protocol before passing it
	 * to the active transaction (if any).
	 * 
	 * @param notification a notification from the resource set
	 */
	protected void processResourceSetNotification(Notification notification) {
		ResourceSetManager.getInstance().observe(
				(ResourceSet) notification.getNotifier(),
				notification);
		
		appendNotification(notification);
	}
	
	/**
	 * Analyzes a resource notification for changes in its load state
	 * and violations of the transaction protocol before passing it
	 * to the active transaction (if any).
	 * 
	 * @param notification a notification from a resource
	 */
	protected void processResourceNotification(Notification notification) {
		ResourceSetManager.getInstance().observe(
				(Resource) notification.getNotifier(),
				notification);
		
		appendNotification(notification);
	}
	
	/**
	 * Analyzes an object notification for violations of the transaction
	 * protocol before passing it to the active transaction (if any).
	 * 
	 * @param notification a notification from a model element
	 */
	protected void processObjectNotification(Notification notification) {
		// nothing else special to do, yet, for object notifications
		
		appendNotification(notification);
	}
	
	/**
	 * Appends the specified notification to the batch for the active
	 * transaction, to be distributed when it commits.  If there is no
	 * active transaction, then it is sent immediately to post-commit
	 * listeners (unbatched).  This method applies the read/write transaction
	 * protocol check to this notification.
	 * 
	 * @param notification the notification to append
	 * 
	 * @throws IllegalStateException if the notification is not a result of
	 *     reading the resource set and no transaction is active or the
	 *     active transaction is read-only
	 */
	protected void appendNotification(Notification notification) {
		if (!NotificationFilter.READ.matches(notification)) {
			assertWriting();
		}
		
		InternalTransaction tx = getEditingDomain().getActiveTransaction();
		
		if (tx != null) {
			tx.add(notification);
		} else {
			// can't batch it
			getEditingDomain().broadcastUnbatched(notification);
		}
	}
	
	/**
	 * Implements the read/write transaction protocol check.
	 * 
	 * @throws IllegalStateException if no transaction is active or the
	 *     active transaction is read-only
	 */
	protected void assertWriting() {
		InternalTransaction tx = domain.getActiveTransaction();
		
		if ((tx == null) || tx.isReadOnly() || (tx.getOwner() != Thread.currentThread())) {
			synchronized (domain) {
				tx = domain.getActiveTransaction();

				// the transaction could be null now if it completed before we
				//    synchronized on the domain
				if (tx != null) {
					tx.abort(new Status(
						IStatus.ERROR,
						EMFTransactionPlugin.getPluginId(),
						EMFTransactionStatusCodes.CONCURRENT_WRITE,
						Messages.concurrentWrite,
						null));
				}
			}
			
			IllegalStateException ise = new IllegalStateException(
				Messages.noWriteTx);
			
			Tracing.throwing(TXChangeRecorder.class, "assertWriting", ise); //$NON-NLS-1$
			
			throw ise;
		}
	}

}
