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
 * $Id: TXEditingDomainImpl.java,v 1.1 2006/01/03 20:41:54 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.TXCommandStack;
import org.eclipse.emf.transaction.TXEditingDomain;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.internal.EMFTransactionDebugOptions;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;
import org.eclipse.emf.transaction.util.Lock;
import org.eclipse.osgi.util.NLS;

/**
 * The default implementation of the transactional editing domain. 
 *
 * @author Christian W. Damus (cdamus)
 */
public class TXEditingDomainImpl
	extends AdapterFactoryEditingDomain
	implements InternalTXEditingDomain {

	private String id;
	
	private TXChangeRecorder recorder;
	
	private volatile InternalTransaction activeTransaction;
	private TXValidator validator;
	
	private final Lock transactionLock = new Lock();
	private final Lock writeLock = new Lock();
	
	private final List precommitListeners = new java.util.ArrayList();
	private final List postcommitListeners = new java.util.ArrayList();
	
	/**
	 * Initializes me with my adapter factory, command stack, and resource set.
	 * 
	 * @param adapterFactory my adapter factory
	 * @param stack my command stack
	 * @param resourceSet my resource set
	 */
	public TXEditingDomainImpl(AdapterFactory adapterFactory, TXCommandStack stack, ResourceSet resourceSet) {
		super(adapterFactory, stack, resourceSet);
		
		initialize();
	}

	/**
	 * Initializes me with my adapter factory and a command stack, and a
	 * default resource set implementation.
	 * 
	 * @param adapterFactory my adapter factory
	 * @param stack my command stack
	 */
	public TXEditingDomainImpl(AdapterFactory adapterFactory, TXCommandStack stack) {
		super(adapterFactory, stack);
		
		initialize();
	}
	
	/**
	 * Initializes me with my adapter factory and a resource set, and a default
	 * command stack implementation.
	 * 
	 * @param adapterFactory my adapter factory
	 * @param resourceSet my resource set
	 */
	public TXEditingDomainImpl(AdapterFactory adapterFactory, ResourceSet resourceSet) {
		this(adapterFactory, new TXCommandStackImpl(), resourceSet);
	}

	/**
	 * Initializes me with my adapter factory and default implementations of
	 * a resource set and a command stack.
	 * 
	 * @param adapterFactory my adapter factory
	 */
	public TXEditingDomainImpl(AdapterFactory adapterFactory) {
		this(adapterFactory, new TXCommandStackImpl());
	}
	
	/**
	 * Initializes my state.
	 */
	private void initialize() {
		((InternalTXCommandStack) commandStack).setEditingDomain(this);
		recorder = new TXChangeRecorder(this, resourceSet);
		validator = TXValidator.NULL;
	}

	// Documentation copied from the inherited specification
	public synchronized final String getID() {
		return id;
	}
	
	// Documentation copied from the inherited specification
	public synchronized final void setID(String id) {
		boolean reregister = false;
		
		if ((id != this.id) && (this.id != null)) {
			// we are changing our ID, so we will have to reregister if we
			//    were registered under the old ID
			reregister = (TXEditingDomain.Registry.INSTANCE.remove(this.id) == this);
		}
		
		if (reregister && (id != null)) {
			TXEditingDomain.Registry.INSTANCE.add(id, this);
		}
		
		this.id = id;
	}
	
	/**
	 * Obtains an ID suitable for display in debug/trace messages.
	 * 
	 * @param domain the editing domain for which to get the debug ID
	 * 
	 * @return a debugging ID
	 */
	protected static String getDebugID(TXEditingDomain domain) {
		return (domain.getID() == null)? "<anonymous>" : domain.getID(); //$NON-NLS-1$
	}

	// Documentation copied from the inherited specification
	public void addResourceSetListener(ResourceSetListener l) {
		if (l.isPrecommitOnly() && l.isPostcommitOnly()) {
			throw new IllegalArgumentException(
					"conflicting isPrecommitOnly() and isPostcommitOnly()"); //$NON-NLS-1$
		}
		
		synchronized (precommitListeners) {
			synchronized (postcommitListeners) {
				// add the listener to the appropriate list only if it expects
				//    to receive the event type and is not already in the list
				
				if (!l.isPostcommitOnly() && !precommitListeners.contains(l)) {
					precommitListeners.add(l);
				}
				if (!l.isPrecommitOnly() && !postcommitListeners.contains(l)) {
					postcommitListeners.add(l);
				}
			}
		}
	}

	// Documentation copied from the inherited specification
	public void removeResourceSetListener(ResourceSetListener l) {
		synchronized (precommitListeners) {
			synchronized (postcommitListeners) {
				precommitListeners.remove(l);
				postcommitListeners.remove(l);
			}
		}
	}
	
	/**
	 * Obtains my command stack as the internal interface.
	 * 
	 * @return the internal view of my command stack
	 */
	protected InternalTXCommandStack getTXCommandStack() {
		return (InternalTXCommandStack) getCommandStack();
	}

	// Documentation copied from the inherited specification
	public Object runExclusive(Runnable read)
		throws InterruptedException {
		
		Transaction tx = startTransaction(true, null);
		
		final RunnableWithResult rwr = (read instanceof RunnableWithResult)?
			(RunnableWithResult) read : null;
		
		try {
			read.run();
		} finally {
			if ((tx != null) && (tx.isActive())) {
				// commit the transaction now
				try {
					tx.commit();
					
					if (rwr != null) {
						rwr.setStatus(Status.OK_STATUS);
					}
				} catch (RollbackException e) {
					Tracing.catching(TXEditingDomainImpl.class, "runExclusive", e); //$NON-NLS-1$
					EMFTransactionPlugin.INSTANCE.log(new MultiStatus(
						EMFTransactionPlugin.getPluginId(),
						EMFTransactionStatusCodes.READ_ROLLED_BACK,
						new IStatus[] {e.getStatus()},
						Messages.readTxRollback,
						null));
					
					if (rwr != null) {
						rwr.setStatus(e.getStatus());
					}
				}
			}
		}
		
		return (rwr != null)? rwr.getResult() : null;
	}

	// Documentation copied from the inherited specification
	public void yield() {
		final Thread current = Thread.currentThread();
		
		if (transactionLock.getOwner() != current) {
			IllegalStateException exc = new IllegalStateException("Only the active transaction may yield"); //$NON-NLS-1$
			Tracing.throwing(TXEditingDomainImpl.class, "yield", exc); //$NON-NLS-1$
			throw exc;
		}
		
		// we cannot yield in a read-write transaction context, even if we
		//    are in a read transaction nested in a write.  Otherwise,
		//    other threads could read uncommitted changes.  Ensure that
		//    we only yield if some other thread is waiting for the lock,
		//    otherwise nobody will resume us
		if ((writeLock.getOwner() == null) && transactionLock.yield()) {
			if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
				Tracing.trace(">>> Yielding " + getDebugID(activeTransaction) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
			InternalTransaction transactionToRestore = activeTransaction;
			TXValidator validatorToRestore = validator;
			
			activeTransaction = null;
			validator = TXValidator.NULL;
			
			int depth = transactionLock.getDepth();
			
			// unwind my read locks so that others may acquire
			for (int i = 0; i < depth; i++) {
				// notifies the next thread waiting for the lock
				transactionLock.release();
			}
			
			// re-acquire my locks to the depth that I had them
			for (int i = 0; i < depth; i++) {
				inner: for (;;) {
					try {
						transactionLock.uiSafeAcquire(false);
						break inner;
					} catch (InterruptedException e) {
						// must ignore this because we cannot afford to be
						//     interrupted:  we *must* restore the locks
						Tracing.catching(TXEditingDomainImpl.class, "yield", e); //$NON-NLS-1$
					}
				}
			}
			
			// I am no longer yielding: restore the active transaction
			activeTransaction = transactionToRestore;
			validator = validatorToRestore;
			
			assert activeTransaction != null;
			
			if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
				Tracing.trace(">>> Resuming " + getDebugID(activeTransaction) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	// Documentation copied from the inherited specification
	public InternalTransaction startTransaction(boolean readOnly, Map options) throws InterruptedException {
		InternalTransaction result;
		
		result = new TransactionImpl(this, readOnly, options);
		result.start();
		
		return result;
	}
	
	/**
	 * Obtains an ID suitable for display in debug/trace messages.
	 * 
	 * @param tx the transaction for which to get the debug ID
	 * 
	 * @return a debugging ID
	 */
	protected static String getDebugID(Transaction tx) {
		return getDebugID(tx.getEditingDomain()) + "::" //$NON-NLS-1$
			+ ((tx instanceof TransactionImpl)? Long.toString(((TransactionImpl) tx).id) : "<anonymous>"); //$NON-NLS-1$
	}
	
	// Documentation copied from the inherited specification
	public TXChangeRecorder getChangeRecorder() {
		return recorder;
	}
	
	// Documentation copied from the inherited specification
	public TXValidator getValidator() {
		return validator;
	}
	
	// Documentation copied from the inherited specification
	public InternalTransaction getActiveTransaction() {
		return activeTransaction;
	}
	
	// Documentation copied from the inherited specification
	public void activate(InternalTransaction tx) throws InterruptedException {
		assert tx != null : "Cannot activate a null transaction"; //$NON-NLS-1$
		
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
			Tracing.trace(">>> Activating   " + getDebugID(tx) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		acquire(tx);
		
		// tell this transaction what its parent is
		tx.setParent(activeTransaction);
		
		if (activeTransaction == null) {
			// activation of a root transaction creates a validator for it
			validator = tx.isReadOnly()
				? new ReadOnlyValidatorImpl()
				: new ReadWriteValidatorImpl();
		}
		
		activeTransaction = tx;
		validator.add(tx);
	}
	
	// Documentation copied from the inherited specification
	public void deactivate(InternalTransaction tx) {
		assert tx != null : "Cannot deactivate a null transaction"; //$NON-NLS-1$
		
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
			Tracing.trace(">>> Deactivating " + getDebugID(tx) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		synchronized (transactionLock) {
			if (activeTransaction != tx) {
				IllegalArgumentException exc = new IllegalArgumentException("Can only deactivate the active transaction"); //$NON-NLS-1$
				Tracing.throwing(TXEditingDomainImpl.class, "deactivate", exc); //$NON-NLS-1$
				throw exc;
			}
			
			activeTransaction = (InternalTransaction) tx.getParent();
			
			if (activeTransaction == null) {
				// deactivation of a root transaction generates post-commit event
				postcommit(tx);
				
				// and also clears the validator
				validator.dispose();
				validator = TXValidator.NULL;
			}
			
			release(tx);
		}
	}
	
	/**
	 * Acquires the appropriate locks for the specified transaction.
	 * 
	 * @param tx a transaction to be activated or resumed from a yield
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *     waiting for the lock
	 */
	private void acquire(InternalTransaction tx) throws InterruptedException {
		Thread current = Thread.currentThread();
		
		if ((transactionLock.getOwner() == current)
				&& (activeTransaction != null)
				&& (activeTransaction.getOwner() == current)
				&& activeTransaction.isReadOnly()
				&& !tx.isReadOnly()
				&& !TransactionImpl.isUnprotected(tx)) {

			throw new IllegalStateException(
				"Cannot activate read/write transaction in read-only transaction context"); //$NON-NLS-1$
		}
		
		transactionLock.uiSafeAcquire(!tx.isReadOnly());
		
		if (!tx.isReadOnly()) {
			// also acquire the write lock
			writeLock.acquire(false);
		}
	}
	
	/**
	 * Releases the lock currently held by the specified transaction.
	 * 
	 * @param tx a transaction
	 */
	private void release(InternalTransaction tx) {
		if (!tx.isReadOnly()) {
			writeLock.release();
		}
		
		transactionLock.release();
	}
	
	// Documentation copied from the inherited specification
	public void precommit(final InternalTransaction tx) throws RollbackException {
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
			Tracing.trace(">>> Precommitting " + getDebugID(tx) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		if (tx.getNotifications().isEmpty()) {
			// nobody to notify
			return;
		}
		
		final ResourceSetListener[] listeners;
		
		synchronized (precommitListeners) {
			listeners = (ResourceSetListener[]) precommitListeners.toArray(
				new ResourceSetListener[precommitListeners.size()]);
		}
		
		// we process only this transaction's own changes in the pre-commit
		final List notifications = tx.getNotifications();
		final List triggers = new java.util.ArrayList();
		
		try {
			final RollbackException[] rbe = new RollbackException[1];
			
			runExclusive(new Runnable() {
				public void run() {
					for (int i = 0; i < listeners.length; i++) {
						try {
							List filtered = FilterManager.getInstance().select(
									notifications,
									listeners[i].getFilter());
							
							if (!filtered.isEmpty()) {
								Command cmd = listeners[i].transactionAboutToCommit(
										new ResourceSetChangeEvent(
												TXEditingDomainImpl.this,
												tx,
												filtered));
								
								if (cmd != null) {
									triggers.add(cmd);
								}
							}
						} catch (RollbackException e) {
							rbe[0] = e;
							Tracing.catching(TXEditingDomainImpl.class, "precommit", e); //$NON-NLS-1$
							break;
						} catch (Exception e) {
							Tracing.catching(TXEditingDomainImpl.class, "precommit", e); //$NON-NLS-1$
							IStatus status = new Status(
								IStatus.ERROR,
								EMFTransactionPlugin.getPluginId(),
								EMFTransactionStatusCodes.PRECOMMIT_FAILED,
								Messages.precommitFailed,
								e);
							EMFTransactionPlugin.INSTANCE.log(status);
							
							// must roll back because we could not execute triggers
							rbe[0] = new RollbackException(status);
							break;
						}
					}
				}});
			
			if (rbe[0] != null) {
				Tracing.throwing(TXEditingDomainImpl.class, "precommit", rbe[0]); //$NON-NLS-1$
				throw rbe[0];
			}
			
			final Command command;
			if (tx instanceof EMFCommandTransaction) {
				command = ((EMFCommandTransaction) tx).getCommand();
			} else {
				command = null;
			}
			
			getTXCommandStack().executeTriggers(command, triggers, tx.getOptions());
		} catch (InterruptedException e) {
			Tracing.catching(TXEditingDomainImpl.class, "precommit", e); //$NON-NLS-1$
			IStatus status = new Status(
				IStatus.ERROR,
				EMFTransactionPlugin.getPluginId(),
				EMFTransactionStatusCodes.PRECOMMIT_INTERRUPTED,
				Messages.precommitInterrupted,
				e);
			EMFTransactionPlugin.INSTANCE.log(status);
			
			// must roll back because we could not execute triggers
			RollbackException exc = new RollbackException(status);
			Tracing.throwing(TXEditingDomainImpl.class, "precommit", exc); //$NON-NLS-1$
			throw exc;
		}
	}
	
	/**
	 * Performs post-commit processing of the specified transaction.  This
	 * consists of broadcasting the post-commit events to my resource set
	 * listeners.
	 * 
	 * @param tx the transaction that has committed
	 */
	protected void postcommit(final InternalTransaction tx) {
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
			Tracing.trace(">>> Postcommitting " + getDebugID(tx) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		if (validator.getNotifications().isEmpty()
				|| !TransactionImpl.isNotificationEnabled(tx)) {
			return;
		}
		
		// get the notifications from the validator and dispose it now,
		//     so that any further notifications gathered from the read
		//     transaction that we are about to initiate do not include those
		//     that we are sending around now
		final List notifications = validator.getNotifications();
		validator.dispose();
		
		final ResourceSetListener[] listeners;
		
		synchronized (postcommitListeners) {
			listeners = (ResourceSetListener[]) postcommitListeners.toArray(
				new ResourceSetListener[postcommitListeners.size()]);
		}
		
		try {
			runExclusive(new Runnable() {
				public void run() {
					for (int i = 0; i < listeners.length; i++) {
						try {
							List filtered = FilterManager.getInstance().select(
									notifications,
									listeners[i].getFilter());
							
							if (!filtered.isEmpty()) {
								listeners[i].resourceSetChanged(
										new ResourceSetChangeEvent(
												TXEditingDomainImpl.this,
												tx,
												filtered));
							}
						} catch (Exception e) {
							Tracing.catching(TXEditingDomainImpl.class, "postcommit", e); //$NON-NLS-1$
							IStatus status = new Status(
								IStatus.ERROR,
								EMFTransactionPlugin.getPluginId(),
								EMFTransactionStatusCodes.POSTCOMMIT_FAILED,
								Messages.postcommitFailed,
								e);
							EMFTransactionPlugin.INSTANCE.log(status);
						}
					}
				}});
		} catch (InterruptedException e) {
			Tracing.catching(TXEditingDomainImpl.class, "postcommit", e); //$NON-NLS-1$
			IStatus status = new Status(
				IStatus.ERROR,
				EMFTransactionPlugin.getPluginId(),
				EMFTransactionStatusCodes.POSTCOMMIT_INTERRUPTED,
				Messages.postcommitInterrupted,
				e);
			EMFTransactionPlugin.INSTANCE.log(status);
		}
	}
	
	// Documentation copied from the inherited specification
	public void broadcastUnbatched(Notification notification) {
		final ResourceSetListener[] listeners;
		
		synchronized (postcommitListeners) {
			listeners = (ResourceSetListener[]) postcommitListeners.toArray(
				new ResourceSetListener[postcommitListeners.size()]);
		}
		
		// TODO: Optimize with a reusable list and reusable event object
		final List notifications = Collections.singletonList(notification);
		
		try {
			runExclusive(new Runnable() {
				public void run() {
					for (int i = 0; i < listeners.length; i++) {
						try {
							List filtered = FilterManager.getInstance().selectUnbatched(
									notifications,
									listeners[i].getFilter());
							
							if (!filtered.isEmpty()) {
								listeners[i].resourceSetChanged(
										new ResourceSetChangeEvent(
												TXEditingDomainImpl.this,
												null,
												filtered));
							}
						} catch (Exception e) {
							Tracing.catching(TXEditingDomainImpl.class, "broadcastUnbatched", e); //$NON-NLS-1$
							IStatus status = new Status(
								IStatus.ERROR,
								EMFTransactionPlugin.getPluginId(),
								EMFTransactionStatusCodes.POSTCOMMIT_FAILED,
								Messages.postcommitFailed,
								e);
							EMFTransactionPlugin.INSTANCE.log(status);
						}
					}
				}});
		} catch (InterruptedException e) {
			Tracing.catching(TXEditingDomainImpl.class, "broadcastUnbatched", e); //$NON-NLS-1$
			IStatus status = new Status(
				IStatus.ERROR,
				EMFTransactionPlugin.getPluginId(),
				EMFTransactionStatusCodes.POSTCOMMIT_INTERRUPTED,
				Messages.postcommitInterrupted,
				e);
			EMFTransactionPlugin.INSTANCE.log(status);
		}
	}
	
	// Documentation copied from the inherited specification
	public void dispose() {
		// try this first, because it will fail if we are statically registered
		//    (in which case it is not permitted to dispose)
		setID(null);
		
		activeTransaction = null;
		
		recorder = null;
		validator = null;
		
		getTXCommandStack().dispose();
		commandStack = null;
	}
	
	/**
	 * Default implementation of a transaction editing domain factory.  This
	 * class creates {@link TXEditingDomainImpl}s and provides the mapping of
	 * resource sets to editing domain instances.
	 * <p>
	 * Clients that implement their own factory can plug in to the mapping
	 * of resource sets to editing domains using the static instance's
	 * {@link #mapResourceSet(TXEditingDomain)} and
	 * {@link #unmapResourceSet(TXEditingDomain)} methods by casting the
	 * {@link TXEditingDomain.Factory#INSTANCE} to the
	 * <code>TXEditingDomainImpl.FactoryImpl</code> type.
	 * </p>
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	public static class FactoryImpl implements TXEditingDomain.Factory {
		// Documentation copied from the inherited specification
		public synchronized TXEditingDomain createEditingDomain() {
			TXEditingDomain result = new TXEditingDomainImpl(
				new ComposedAdapterFactory(
					ComposedAdapterFactory.Descriptor.Registry.INSTANCE));
			
			mapResourceSet(result);
			
			return result;
		}

		// Documentation copied from the inherited specification
		public synchronized TXEditingDomain createEditingDomain(ResourceSet rset) {
			TXEditingDomain result = new TXEditingDomainImpl(
				new ComposedAdapterFactory(
					ComposedAdapterFactory.Descriptor.Registry.INSTANCE),
				rset);
			
			mapResourceSet(result);
			
			return result;
		}

		// Documentation copied from the inherited specification
		public TXEditingDomain getEditingDomain(ResourceSet rset) {
			TXEditingDomain result = null;
			
			ResourceSetDomainLink link = (ResourceSetDomainLink) EcoreUtil.getAdapter(
					rset.eAdapters(),
					ResourceSetDomainLink.class);
			
			if (link != null) {
				result = link.getDomain();
			}
			
			return result;
		}
		
		/**
		 * Adds the specified editing domain to the global reverse mapping
		 * of resource sets.
		 * 
		 * @param domain the editing domain to add to the resource set mapping
		 */
		public synchronized void mapResourceSet(TXEditingDomain domain) {
			domain.getResourceSet().eAdapters().add(
					new ResourceSetDomainLink(domain));
		}
		
		/**
		 * Removes the specified editing domain from the global reverse mapping
		 * of resource sets.
		 * 
		 * @param domain the editing domain to remove from the resource set mapping
		 */
		public synchronized void unmapResourceSet(TXEditingDomain domain) {
			for (Iterator iter = domain.getResourceSet().eAdapters().iterator(); iter.hasNext();) {
				Adapter next = (Adapter) iter.next();
				
				if (next.isAdapterForType(TXEditingDomain.class)) {
					iter.remove();
					
					// continue processing because maybe there are multiple
					//    links to the same domain
				}
			}
		}
		
		/**
		 * An adapter that attaches a weak reference to the editing domain
		 * onto the resource set that it manages.
		 *
		 * @author Christian W. Damus (cdamus)
		 */
		private static class ResourceSetDomainLink extends AdapterImpl {
			private final Reference domain;
			
			ResourceSetDomainLink(TXEditingDomain domain) {
				this.domain = new WeakReference(domain);
			}
			
			public boolean isAdapterForType(Object type) {
				return type == ResourceSetDomainLink.class;
			}
			
			final TXEditingDomain getDomain() {
				TXEditingDomain result = (TXEditingDomain) domain.get();
				
				if (result == null) {
					// no longer need the adapter
					getTarget().eAdapters().remove(this);
				}
				
				return result;
			}
		}
	}
	
	/**
	 * Implementation of the global editing domain registry.
	 * <p>
	 * This class is not intended to be used by clients.
	 * </p>
	 * 
	 * @author Christian W. Damus (cdamus)
	 */
	public final static class RegistryImpl implements TXEditingDomain.Registry {
		private final Map domains = new java.util.HashMap();
		
		// Documentation copied from the inherited specification
		public synchronized TXEditingDomain getEditingDomain(String id) {
			TXEditingDomain result = (TXEditingDomain) domains.get(id);
			
			if (result == null) {
				result = EditingDomainManager.getInstance().createEditingDomain(id);
				
				if (result != null) {
					addImpl(id, result);
				}
			}
			
			return result;
		}

		// Documentation copied from the inherited specification
		public synchronized void add(String id, TXEditingDomain domain) {
			// remove previously registered domain, if any (which applies the
			//    static registration constraint)
			remove(id);
			
			addImpl(id, domain);
		}

		/**
		 * Adds the specified domain into the registry under the given ID.  This
		 * method must only be invoked after it has been determined that this
		 * ID can be registered.
		 * 
		 * @param id the editing domain ID
		 * @param domain the domain to register
		 */
		void addImpl(String id, TXEditingDomain domain) {
			if (!id.equals(domain.getID())) {
				domain.setID(id); // ensure that the domain's id is set
			}
			
			domains.put(id, domain);
			
			EditingDomainManager.getInstance().configureListeners(id, domain);
		}

		// Documentation copied from the inherited specification
		public synchronized TXEditingDomain remove(String id) {
			if (EditingDomainManager.getInstance().isStaticallyRegistered(id)) {
				IllegalArgumentException exc = new IllegalArgumentException(
					NLS.bind(Messages.removeStaticDomain, id));
				Tracing.throwing(RegistryImpl.class, "remove", exc); //$NON-NLS-1$
				throw exc;
			}
			
			TXEditingDomain result = (TXEditingDomain) domains.remove(id);
			
			if (result != null) {
				EditingDomainManager.getInstance().deconfigureListeners(id, result);
			}
			
			return result;
		}
		
	}
}
