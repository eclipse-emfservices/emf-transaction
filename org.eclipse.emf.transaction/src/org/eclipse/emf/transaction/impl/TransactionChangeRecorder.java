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
 *   Zeligsoft - Bug 250498
 *
 * </copyright>
 *
 * $Id: TransactionChangeRecorder.java,v 1.8 2008/11/16 13:23:38 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Collections;
import java.util.Iterator;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.internal.EMFTransactionDebugOptions;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;
import org.eclipse.emf.transaction.util.ValidateEditSupport;

/**
 * The change recorder for a {@link org.eclipse.emf.transaction.TransactionalEditingDomain},
 * used by transactions to record rollback information and to detect changes that
 * violate the transaction protocol.  It also forwards notifications to the
 * domain's currently active transaction.
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see InternalTransactionalEditingDomain#getChangeRecorder()
 * @see TransactionValidator
 * @see InternalTransaction#add(Notification)
 */
public class TransactionChangeRecorder extends ChangeRecorder {
	
	private InternalTransactionalEditingDomain domain;
	
	private boolean paused;
	
	private boolean disposed;
	
	private ValidateEditSupport validateEdit;
	
	/**
	 * Initializes me with the editing domain that I assist and the resource
	 * set in which I will record changes.  Note that I do not begin recording
	 * until specifically asked to (unlike the superclass).
	 * 
	 * @param domain my editing domain
	 * @param rset my resource set
	 */
	public TransactionChangeRecorder(InternalTransactionalEditingDomain domain, ResourceSet rset) {
		super(rset);
		this.domain = domain;
		
		// super already started recording
		endRecording();
		
		setResolveProxies(false);
		
		// tell the resource set manager about any resources that already exist
		ResourceSetManager.getInstance().observe(rset);
	}
	
	/**
	 * Obtains the editing domain that I assist.
	 * 
	 * @return my editing domain
	 */
	public final InternalTransactionalEditingDomain getEditingDomain() {
		return domain;
	}
	
	/**
	 * Starts recording changes in my editing domain.
	 * 
	 * @throws IllegalStateException if I have been {@link #dispose() disposed}
	 */
	public void beginRecording() {
		if (disposed) {
			throw new IllegalStateException("cannot begin a disposed recorder"); //$NON-NLS-1$
		}
		
		beginRecording(Collections.singleton(getEditingDomain().getResourceSet()));
	}
	
	/**
	 * Extends the inherited implementation to clear the reference to the
	 * change description returned.
	 * 
	 * @throws IllegalStateException if I have been {@link #dispose() disposed}
	 */
	@Override
	public ChangeDescription endRecording() {
		if (disposed) {
			throw new IllegalStateException("cannot end a disposed recorder"); //$NON-NLS-1$
		}
		
		ChangeDescription result = super.endRecording();
		
		changeDescription = null;
		
		return result;
	}

	/**
	 * Overrides the superclass method to
	 * <ul>
	 *   <li>ignore the "originalTargetObjects" since we never resume recording
	 *       a paused change description</li>
	 *   <li>ignore the "targetObjects" because we will never find, upon
	 *       upon consolidating changes, that any target object is unexpectedly
	 *       orphaned (as we always listen to everything in the resource set,
	 *       so will always get the appropriate removal notifications).  Also,
	 *       because we manage an entire resource set on behalf of an editing
	 *       domain, disposal by removing ourselves from the adapters lists of
	 *       our targets is not an issue because we cannot cause a memory leak
	 *       outside of the scope of the editing domain and its resource set</li>
	 * </ul>
	 */
	@Override
	public void setTarget(Notifier target) {
		if (!disposed) {
			Iterator<? extends Notifier> contents = (target instanceof EObject) ? isResolveProxies() ? ((EObject) target)
				.eContents().iterator()
				: ((InternalEList<EObject>) ((EObject) target).eContents())
					.basicIterator()
				: target instanceof ResourceSet ? ((ResourceSet) target)
					.getResources().iterator()
					: target instanceof Resource ? ((Resource) target)
						.getContents().iterator()
						: null;

			if (contents != null) {
				while (contents.hasNext()) {
					Notifier notifier = contents.next();
					addAdapter(notifier);
				}
			}
		}
	}
	
	/**
	 * Detects whether the change indicated by the specified notification
	 * violates the transaction protocol and/or how it changes the load state of
	 * a resource (if it all), in addition to recording the change (if I am
	 * currently recording) and passing it along to the domain's current
	 * transaction (if any).
	 */
	@Override
	public void notifyChanged(Notification notification) {
		if (disposed) {
			// I am disposed, so I should no longer be responding to or even
			//    receiving events.  Moreover, I should not propagate myself
			//    to any elements that are later added to me!
			Object notifier = notification.getNotifier();
			if (notifier instanceof Notifier) {
				removeAdapter((Notifier) notifier);
			}
			
			return;
		}
		
		boolean record = true;
		
		switch (notification.getEventType()) {
		case Notification.SET:
		case Notification.UNSET:
		case Notification.ADD:
		case Notification.ADD_MANY:
		case Notification.REMOVE:
		case Notification.REMOVE_MANY:
			Resource sourceRes = null;
			Object notifier = notification.getNotifier();
			
			if (notifier instanceof Resource) {
				sourceRes = (Resource) notifier;
			} else if (notifier instanceof EObject) {
				sourceRes = ((EObject) notifier).eResource();
			}
			
			if (sourceRes != null) {
			    if (!ResourceSetManager.getInstance().isLoaded(sourceRes)) {
    				// resource load and unload are not undoable changes
    				record = false;
			    }
			    
			    if (!notification.isTouch() && (getValidateEditSupport() != null)) {
			        getValidateEditSupport().handleResourceChange(sourceRes,
			            notification);
			    }
			}
			
			break;
		}
		
		if ((record && !isPaused()) || !isRecording()) {
			super.notifyChanged(notification);
		} else {
			final boolean wasRecording = recording;
			
			try {
				recording = false;
				super.notifyChanged(notification);
			} finally {
				recording = wasRecording;
			}
		}
		
		if (notification.getEventType() != Notification.REMOVING_ADAPTER) {
			// the removing adapter event is only sent to me when I am removed
			//    from an object.  Nobody else would find it useful
			
			Object notifier = notification.getNotifier();
			if (notifier instanceof ResourceSet) {
				processResourceSetNotification(notification);
			} else if (notifier instanceof Resource) {
				processResourceNotification(notification);
			} else if (record) {
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
			if (tx.getOwner() == Thread.currentThread()) {
				tx.add(notification);
			} else if (Tracing
				.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
				
				Tracing.trace("Read notification from thread " //$NON-NLS-1$
					+ Thread.currentThread().getName() + " not appended to " //$NON-NLS-1$
					+ tx);
			}
		} else {
			// can't batch it because there is no transaction to collect a batch
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
			
			Tracing.throwing(TransactionChangeRecorder.class, "assertWriting", ise); //$NON-NLS-1$
			
			throw ise;
		}
	}

	/**
	 * Temporarily pauses the recording of the current change description.
	 * 
	 * @throws IllegalStateException if I am not currently recording
	 * 
	 * @see ChangeRecorder#isRecording()
	 * @see #isPaused()
	 * @see #resume()
	 */
	public void pause() {
		assert isRecording(): "Cannot pause when not recording"; //$NON-NLS-1$
		
		paused = true;
	}

	/**
	 * Queries whether I am currently paused in my recording.
	 * 
	 * @see ChangeRecorder#isRecording()
	 * @see #pause()
	 * @see #resume()
	 */
	public boolean isPaused() {
		return paused;
	}

	/**
	 * Resumes the paused recording of the current change description.
	 * 
	 * @throws IllegalStateException if I am not currently paused
	 * 
	 * @see ChangeRecorder#isRecording()
	 * @see #pause()
	 * @see #isPaused()
	 */
	public void resume() {
		assert isPaused(): "Cannot resume when not paused"; //$NON-NLS-1$
		
		paused = false;
	}
	
	/**
	 * Extends the inherited implementation to remove myself from all adapters
	 * that I can find in my editing domain.
	 * 
	 * @since 1.1
	 */
	@Override
	public void dispose() {
		if (!disposed) {
			super.dispose();
			
			this.disposed = true;
			
			ResourceSet rset = domain.getResourceSet();
			if (rset != null) {
				removeAdapter(rset);
				
				for (Iterator<Notifier> iter = EcoreUtil.getAllProperContents(rset, false);
						iter.hasNext();) {
					
					removeAdapter(iter.next());
				}
			}
			
			this.domain = null;
		}
	}
	
	public ValidateEditSupport getValidateEditSupport() {
	    return validateEdit;
	}
	
	/**
	 * Sets my validate-edit support, if applicable, for the duration of a
	 * root transaction.
	 * 
	 * @param validateEdit my validate-edit support, or <code>null</code>
	 * 
	 * @since 1.2
	 */
	public void setValidateEditSupport(ValidateEditSupport validateEdit) {
	    this.validateEdit = validateEdit;
	}
}
