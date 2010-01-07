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
 *   Zeligsoft - Bugs 177642, 145877, 245446
 *
 * </copyright>
 *
 * $Id: TransactionalEditingDomainImpl.java,v 1.22 2010/01/07 15:29:51 bgruschko Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalCommandStack;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TransactionalEditingDomainEvent;
import org.eclipse.emf.transaction.TransactionalEditingDomainListener;
import org.eclipse.emf.transaction.internal.EMFTransactionDebugOptions;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.ITransactionLock;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;
import org.eclipse.emf.transaction.util.Adaptable;
import org.eclipse.emf.transaction.util.BasicTransactionOptionMetadataRegistry;
import org.eclipse.emf.transaction.util.EmptyLock;
import org.eclipse.emf.transaction.util.Lock;
import org.eclipse.emf.transaction.util.TransactionUtil;

/**
 * The default implementation of the transactional editing domain. 
 * <p>
 * Since 1.2, this class implements the {@link Adaptable} interface to adapt
 * to the following optional API:
 * </p>
 * <ul>
 *   <li>{@link TransactionalEditingDomain.DefaultOptions}</li>
 *   <li>{@link TransactionalEditingDomain.Lifecycle} (since 1.3)</li>
 *   <li>{@link Transaction.Option.Registry} (since 1.3)</li>
 * </ul>
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionalEditingDomainImpl
	extends AdapterFactoryEditingDomain
    implements InternalTransactionalEditingDomain, Adaptable,
    TransactionalEditingDomain.DefaultOptions {
	
	private String id;
	
	private TransactionChangeRecorder recorder;
	
	private volatile InternalTransaction activeTransaction;
	private TransactionValidator validator;
	
	private TransactionValidator.Factory validatorFactory = null;
	
	private final Map<Object, Object> defaultTransactionOptions =
		new java.util.HashMap<Object, Object>();
	private final Map<Object, Object> defaultTransactionOptionsRO = Collections
        .unmodifiableMap(defaultTransactionOptions);
	
	private ITransactionLock transactionLock = null;
	private ITransactionLock writeLock = null;
	
	private final List<ResourceSetListener> precommitListeners =
		new java.util.ArrayList<ResourceSetListener>();
	private final List<ResourceSetListener> aggregatePrecommitListeners =
		new java.util.ArrayList<ResourceSetListener>();
	private final List<ResourceSetListener> postcommitListeners =
		new java.util.ArrayList<ResourceSetListener>();
	
	// reusable notification list and event for unbatched change events
	private final List<Notification> unbatchedNotifications =
		new java.util.ArrayList<Notification>(1);
	private final ResourceSetChangeEvent unbatchedChangeEvent =
		new ResourceSetChangeEvent(this, null, unbatchedNotifications);
	
	// this is editable by clients for backwards compatibility with 1.1
	private final Map<Object, Object> undoRedoOptions = new java.util.HashMap<Object, Object>(
	    TransactionImpl.DEFAULT_UNDO_REDO_OPTIONS);
	
	private LifecycleImpl lifecycle;
	private Transaction.OptionMetadata.Registry optionMetadata;
	
	private boolean disposed	=	false;
	
	/**
	 * Initializes me with my adapter factory, command stack, and resource set.
	 * 
	 * @param adapterFactory my adapter factory
	 * @param stack my command stack
	 * @param resourceSet my resource set
	 */
	public TransactionalEditingDomainImpl(AdapterFactory adapterFactory, TransactionalCommandStack stack, ResourceSet resourceSet) {
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
	public TransactionalEditingDomainImpl(AdapterFactory adapterFactory, TransactionalCommandStack stack) {
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
	public TransactionalEditingDomainImpl(AdapterFactory adapterFactory, ResourceSet resourceSet) {
		this(adapterFactory, new TransactionalCommandStackImpl(), resourceSet);
	}

	/**
	 * Initializes me with my adapter factory and default implementations of
	 * a resource set and a command stack.
	 * 
	 * @param adapterFactory my adapter factory
	 */
	public TransactionalEditingDomainImpl(AdapterFactory adapterFactory) {
		this(adapterFactory, new TransactionalCommandStackImpl());
	}
	
	/**
	 * Initializes my state.
	 */
	private void initialize() {
		synchronized (this) {
			if ( EMFPlugin.IS_ECLIPSE_RUNNING ) {
				transactionLock = new Lock();
				writeLock = new Lock();
			} else {
				transactionLock = new EmptyLock();
				writeLock = new EmptyLock();
			}
		}
		
		((InternalTransactionalCommandStack) commandStack).setEditingDomain(this);
		recorder = createChangeRecorder(resourceSet);
		validator = TransactionValidator.NULL;
        
        // create a map for read-only-resource support.  Use a weak map
        //    to avoid retaining resources in this map
        resourceToReadOnlyMap = new java.util.WeakHashMap<Resource, Boolean>();
	}
	
	/**
	 * May be overridden by subclasses to create a custom change recorder
	 * implementation.  Just creates a change recorder on the specified resource
	 * set and returns it.
	 * 
	 * @param rset a resource set in which to record changes
	 * 
	 * @return the new change recorder
	 */
	protected TransactionChangeRecorder createChangeRecorder(ResourceSet rset) {
		return new TransactionChangeRecorder(this, rset);
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
			reregister = (TransactionalEditingDomain.Registry.INSTANCE.remove(this.id) == this);
		}
		
		if (reregister && (id != null)) {
			TransactionalEditingDomain.Registry.INSTANCE.add(id, this);
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
	protected static String getDebugID(TransactionalEditingDomain domain) {
		return (domain.getID() == null)? "<anonymous>" : domain.getID(); //$NON-NLS-1$
	}

	// Documentation copied from the inherited specification
	public void addResourceSetListener(ResourceSetListener l) {
		if (l.isPrecommitOnly() && l.isPostcommitOnly()) {
			throw new IllegalArgumentException(
					"conflicting isPrecommitOnly() and isPostcommitOnly()"); //$NON-NLS-1$
		}
		
		synchronized (precommitListeners) {
			synchronized (aggregatePrecommitListeners) {
				synchronized (postcommitListeners) {
					boolean wasAdded = false;
					
					// add the listener to the appropriate list only if it expects
					//    to receive the event type and is not already in the list
					
					if (!l.isPostcommitOnly()) {
						if (!l.isAggregatePrecommitListener()
								&& !precommitListeners.contains(l)) {
							wasAdded |= precommitListeners.add(l);
						} else if (l.isAggregatePrecommitListener()
								&& !aggregatePrecommitListeners.contains(l)) {
							wasAdded |= aggregatePrecommitListeners.add(l);
						}
					}
					
					if (!l.isPrecommitOnly() && !postcommitListeners.contains(l)) {
						wasAdded |= postcommitListeners.add(l);
					}
					
					if (wasAdded && (l instanceof ResourceSetListener.Internal)) {
						// welcome to the family
						((ResourceSetListener.Internal) l).setTarget(this);
					}
				}
			}
		}
	}

	// Documentation copied from the inherited specification
	public void removeResourceSetListener(ResourceSetListener l) {
		synchronized (precommitListeners) {
			synchronized (aggregatePrecommitListeners) {
				synchronized (postcommitListeners) {
					boolean wasRemoved = false;
					
					wasRemoved |= precommitListeners.remove(l);
					wasRemoved |= aggregatePrecommitListeners.remove(l);
					wasRemoved |= postcommitListeners.remove(l);
					
					if (wasRemoved
						&& (l instanceof ResourceSetListener.Internal)) {
						
						((ResourceSetListener.Internal) l).unsetTarget(this);
					}
				}
			}
		}
	}
	
	/**
	 * Obtains my command stack as the internal interface.
	 * 
	 * @return the internal view of my command stack
	 */
	protected InternalTransactionalCommandStack getTransactionalCommandStack() {
		return (InternalTransactionalCommandStack) getCommandStack();
	}

	// Documentation copied from the inherited specification
	public Object runExclusive(Runnable read)
		throws InterruptedException {
		
		Transaction active = getActiveTransaction();
		Transaction tx = null;
		
		if ((active == null)
				|| !(active.isActive() && active.isReadOnly()
						&& (active.getOwner() == Thread.currentThread()))) {
			
			// only need to start a new transaction if we don't already have
			//   exclusive read-only access
			tx = startTransaction(true, null);
		}
		
		final RunnableWithResult<?> rwr = (read instanceof RunnableWithResult)?
			(RunnableWithResult<?>) read : null;
		
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
					Tracing.catching(TransactionalEditingDomainImpl.class, "runExclusive", e); //$NON-NLS-1$
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
			Tracing.throwing(TransactionalEditingDomainImpl.class, "yield", exc); //$NON-NLS-1$
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
			TransactionValidator validatorToRestore = validator;
			
			activeTransaction = null;
			validator = TransactionValidator.NULL;
			
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
						Tracing.catching(TransactionalEditingDomainImpl.class, "yield", e); //$NON-NLS-1$
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
	public InternalTransaction startTransaction(boolean readOnly, Map<?, ?> options)
			throws InterruptedException {
		
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
	public TransactionChangeRecorder getChangeRecorder() {
		return recorder;
	}
	
	// Documentation copied from the inherited specification
	public TransactionValidator getValidator() {
		return validator;
	}
	
	protected void setValidator(TransactionValidator newValidator) {
		validator = newValidator;
	}
	
	/**
	 * Obtains a copy of my pre-commit listener list as an array, for safe
	 * iteration that allows concurrent updates to the original list.
	 * 
	 * @return my pre-commit listeners (as of the time of calling this method)
	 */
	protected final ResourceSetListener[] getPrecommitListeners() {
		synchronized (precommitListeners) {
			return precommitListeners.toArray(
				new ResourceSetListener[precommitListeners.size()]);
		}
	}
	
	/**
	 * Obtains a copy of my aggregate pre-commit listener list as an array, for
	 * safe iteration that allows concurrent updates to the original list.
	 * 
	 * @return my aggregate pre-commit listeners (as of the time of calling
	 *      this method)
	 */
	protected final ResourceSetListener[] getAggregatePrecommitListeners() {
		synchronized (aggregatePrecommitListeners) {
			return aggregatePrecommitListeners.toArray(
				new ResourceSetListener[aggregatePrecommitListeners.size()]);
		}
	}
	
	/**
	 * Obtains a copy of my post-commit listener list as an array, for safe
	 * iteration that allows concurrent updates to the original list.
	 * 
	 * @return my post-commit listeners (as of the time of calling this method)
	 */
	protected final ResourceSetListener[] getPostcommitListeners() {
		synchronized (postcommitListeners) {
			return postcommitListeners.toArray(
				new ResourceSetListener[postcommitListeners.size()]);
		}
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
				? getValidatorFactory().createReadOnlyValidator()
				: getValidatorFactory().createReadWriteValidator();
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
		
		if (activeTransaction != tx) {
			IllegalArgumentException exc = new IllegalArgumentException("Can only deactivate the active transaction"); //$NON-NLS-1$
			Tracing.throwing(TransactionalEditingDomainImpl.class, "deactivate", exc); //$NON-NLS-1$
			throw exc;
		}
		
		activeTransaction = (InternalTransaction) tx.getParent();
		
        try {
    		if (activeTransaction == null) {
    			// deactivation of a root transaction generates post-commit event
    			postcommit(tx);
    			
    			// and also clears the validator
    			validator.dispose();
    			validator = TransactionValidator.NULL;
    		} else {
                // ensure that the validator no longer retains this transaction in
                //     its map (if it's a read/write validator)
                validator.remove(tx);
            }
        } finally {		
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
			// also acquire the write lock.  Ignore interrupts because getting
			//    the write lock is trivial once we have the transaction lock,
			//    because the transaction lock is always acquired first
			for (;;) {
				try {
					writeLock.acquire(false);
					break;
				} catch (InterruptedException e) {
					Thread.interrupted();  // clear interrupt flag
				}
			}
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
		class PrecommitRunnable extends RunnableWithResult.Impl<List<Command>> {
			private final List<Notification> notifications;
			private final ResourceSetListener[] listeners;
			private RollbackException rollback;
			
			PrecommitRunnable(ResourceSetListener[] listeners,
					List<Notification> notifications) {
				
				this.listeners = listeners;
				this.notifications = notifications;
			}
			
			List<Command> runExclusive() throws InterruptedException {
				if ((listeners.length > 0) && !notifications.isEmpty()) {
					return TransactionUtil.runExclusive(
						TransactionalEditingDomainImpl.this, this);
				} else {
					return Collections.emptyList();
				}
			}
			
			RollbackException getRollback() {
				return rollback;
			}
			
			public void run() {
				List<Command> triggers = new java.util.ArrayList<Command>();
				setResult(triggers);
				
				ArrayList<Notification> cache = new ArrayList<Notification>(
						notifications.size());
				
				for (ResourceSetListener element : listeners) {
					try {
						List<Notification> filtered = FilterManager.getInstance().select(
								notifications,
								element.getFilter(),
								cache);
						
						if (!filtered.isEmpty()) {
							Command cmd = element.transactionAboutToCommit(
									new ResourceSetChangeEvent(
											TransactionalEditingDomainImpl.this,
											tx,
											filtered));
							
							if (cmd != null) {
								triggers.add(cmd);
							}
						}
					} catch (RollbackException e) {
						rollback = e;
						Tracing.catching(TransactionalEditingDomainImpl.class, "precommit", e); //$NON-NLS-1$
						break;
					} catch (Exception e) {
						Tracing.catching(TransactionalEditingDomainImpl.class, "precommit", e); //$NON-NLS-1$
						IStatus status = new Status(
							IStatus.ERROR,
							EMFTransactionPlugin.getPluginId(),
							EMFTransactionStatusCodes.PRECOMMIT_FAILED,
							Messages.precommitFailed,
							e);
						EMFTransactionPlugin.INSTANCE.log(status);
						
						// must roll back because we could not execute triggers
						rollback = new RollbackException(status);
						break;
					}
				}
			}}
		
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.TRANSACTIONS)) {
			Tracing.trace(">>> Precommitting " + getDebugID(tx) + " at " + Tracing.now()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		// we process only this transaction's own changes in the pre-commit
		PrecommitRunnable runnable = new PrecommitRunnable(
			getPrecommitListeners(),
			tx.getNotifications());
		
		// we will repeat the execution of aggregate listeners until there
		//    are no more notifications to send to them
		while (runnable != null) {
			try {
				List<Command> triggers = runnable.runExclusive();
				
				if (runnable.getRollback() != null) {
					Tracing.throwing(TransactionalEditingDomainImpl.class,
						"precommit", runnable.getRollback()); //$NON-NLS-1$
					throw runnable.getRollback();
				}
				
				final Command command;
				if (tx instanceof EMFCommandTransaction) {
					command = ((EMFCommandTransaction) tx).getCommand();
				} else {
					command = null;
				}
				
				if (!triggers.isEmpty()) {
					getTransactionalCommandStack().executeTriggers(
						command, triggers, tx.getOptions());
				}
				
				List<Notification> notifications = validator.getNotificationsForPrecommit(
					tx);
				
				if ((notifications == null) || notifications.isEmpty()) {
					runnable = null;
				} else {
					runnable = new PrecommitRunnable(
						getAggregatePrecommitListeners(),
						notifications);
				}
			} catch (InterruptedException e) {
				Tracing.catching(TransactionalEditingDomainImpl.class, "precommit", e); //$NON-NLS-1$
				IStatus status = new Status(
					IStatus.ERROR,
					EMFTransactionPlugin.getPluginId(),
					EMFTransactionStatusCodes.PRECOMMIT_INTERRUPTED,
					Messages.precommitInterrupted,
					e);
				EMFTransactionPlugin.INSTANCE.log(status);
				
				// must roll back because we could not execute triggers
				RollbackException exc = new RollbackException(status);
				Tracing.throwing(TransactionalEditingDomainImpl.class, "precommit", exc); //$NON-NLS-1$
				throw exc;
			}
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
		
		final List<Notification> notifications = validator.getNotificationsForPostcommit(
			tx);
		if ((notifications == null) || notifications.isEmpty()) {
			return;
		}
		final ArrayList<Notification> cache = new ArrayList<Notification>(
				notifications.size());
		
		// dispose the validator now because starting the read-only transaction
		//    below will replace it with a new validator
		validator.dispose();
		
		final ResourceSetListener[] listeners = getPostcommitListeners();
		
		try {
			runExclusive(new Runnable() {
				public void run() {
					for (ResourceSetListener element : listeners) {
						try {
							List<Notification> filtered = FilterManager.getInstance().select(
									notifications,
									element.getFilter(),
									cache);
							
							if (!filtered.isEmpty()) {
								element.resourceSetChanged(
										new ResourceSetChangeEvent(
												TransactionalEditingDomainImpl.this,
												tx,
												filtered));
							}
						} catch (Exception e) {
							Tracing.catching(TransactionalEditingDomainImpl.class, "postcommit", e); //$NON-NLS-1$
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
			Tracing.catching(TransactionalEditingDomainImpl.class, "postcommit", e); //$NON-NLS-1$
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
		final ResourceSetListener[] listeners = getPostcommitListeners();
		
		unbatchedNotifications.add(notification);
		
		try {
			runExclusive(new Runnable() {
				public void run() {
					for (ResourceSetListener element : listeners) {
						try {
							List<Notification> filtered = FilterManager.getInstance().selectUnbatched(
									unbatchedNotifications,
									element.getFilter());
							
							if (!filtered.isEmpty()) {
								element.resourceSetChanged(unbatchedChangeEvent);
							}
						} catch (Exception e) {
							Tracing.catching(TransactionalEditingDomainImpl.class, "broadcastUnbatched", e); //$NON-NLS-1$
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
			Tracing.catching(TransactionalEditingDomainImpl.class, "broadcastUnbatched", e); //$NON-NLS-1$
			IStatus status = new Status(
				IStatus.ERROR,
				EMFTransactionPlugin.getPluginId(),
				EMFTransactionStatusCodes.POSTCOMMIT_INTERRUPTED,
				Messages.postcommitInterrupted,
				e);
			EMFTransactionPlugin.INSTANCE.log(status);
		} finally {
			// remove the unbatched notification from our reusable cache
			unbatchedNotifications.remove(0);
		}
	}
	
	// Documentation copied from the inherited specification
	public final RunnableWithResult<Object> createPrivilegedRunnable(Runnable runnable) {
		InternalTransaction tx = getActiveTransaction();
		
		if ((tx == null) || (tx.getOwner() != Thread.currentThread())) {
			throw new IllegalStateException(
					"active transaction not owned by caller"); //$NON-NLS-1$
		}
		
		return new PrivilegedRunnable<Object>(tx, runnable);
	}
	
	// Documentation copied from the inherited specification
	public void startPrivileged(PrivilegedRunnable<?> runnable) {
		if (runnable.getTransaction().getEditingDomain() != this) {
			throw new IllegalArgumentException(
					"runnable has no privileges on this editing domain"); //$NON-NLS-1$
		}
		
		Thread current = Thread.currentThread();
			
		// transfer the locks to the current thread
		transactionLock.checkedTransfer(current);
		writeLock.checkedTransfer(current);
		}
		
	// Documentation copied from the inherited specification
	public void endPrivileged(PrivilegedRunnable<?> runnable) {
		if (runnable.getTransaction().getEditingDomain() != this) {
			throw new IllegalArgumentException(
					"runnable has no privileges on this editing domain"); //$NON-NLS-1$
		}
		
		Thread owner = runnable.getOwner();
		
		// transfer the locks to their previous owner
		transactionLock.checkedTransfer(owner);
		writeLock.checkedTransfer(owner);
		}
		
	// Documentation copied from the inherited specification
	public void dispose() {
		if ( !disposed ) {
			
			// this will fail if we are statically registered
			//    (in which case it is not permitted to dispose)
			if (getID() != null) {
				EditingDomainManager.getInstance().assertDynamicallyRegistered(
					getID());
			}
			
			disposed = true;
		
			getLifecycle().fireLifecycleEvent(
				TransactionalEditingDomainEvent.EDITING_DOMAIN_DISPOSING, null);
			
			// clear resource-set listeners (and notify them) on disposal
			Set<ResourceSetListener> rsetListeners = new java.util.HashSet<ResourceSetListener>();
			rsetListeners.addAll(aggregatePrecommitListeners);
			rsetListeners.addAll(precommitListeners);
			rsetListeners.addAll(postcommitListeners);
			
			for (ResourceSetListener next : rsetListeners) {
				if (next instanceof ResourceSetListener.Internal) {
					((ResourceSetListener.Internal) next).unsetTarget(this);
				}
			}
			
			// clear listeners after notification so that they cannot add themselves
			// back again during the call-back
			aggregatePrecommitListeners.clear();
			precommitListeners.clear();
			postcommitListeners.clear();
			getLifecycle().dispose();
			
			// only clear my ID after notifying listeners, because they may
			// need to key on it
			setID(null);
			
			activeTransaction = null;
			
			recorder.dispose();
			recorder = null;
			validator = null;
			
			getTransactionalCommandStack().dispose();
			commandStack = null;
			
			// disconnect the resource set from the editing domain
			((FactoryImpl) Factory.INSTANCE).unmapResourceSet(this);
		}
	}

	public Map<Object, Object> getUndoRedoOptions() {
		return undoRedoOptions;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getAdapter(Class<? extends T> adapterType) {
	    T result;
	    
	    if (adapterType == Transaction.OptionMetadata.Registry.class) {
	        result = (T) getOptionMetadata();
	    } else if (adapterType == DefaultOptions.class) {
	        result = (T) this;
	    } else if (adapterType == Lifecycle.class) {
	        result = (T) getLifecycle();
	    } else if (adapterType == InternalLifecycle.class) {
	        result = (T) getLifecycle();
	    } else {
	        result = null;
	    }
	    
	    return result;
	}
	
	public Map<?, ?> getDefaultTransactionOptions() {
	    return defaultTransactionOptionsRO;  // return the read-only view
	}
	
	public void setDefaultTransactionOptions(Map<?, ?> options) {
	    synchronized (defaultTransactionOptions) {
    	    defaultTransactionOptions.clear();
    	    defaultTransactionOptions.putAll(options);
	    }
	}
	
	/**
	 * Sets the factory to use when creating validators for transaction
	 * validation.
	 * 
	 * @since 1.1
	 * 
	 * @param validatorFactory the factory to set
	 */
	public void setValidatorFactory(TransactionValidator.Factory validatorFactory) {
		this.validatorFactory = validatorFactory;
	}
	
	/**
	 * Obtains the factory that this transactional editing domain uses
	 * to create validators for transaction validation.
	 * <p>
	 * If the validator factory has yet to be initialized, it is initialized
	 * using the default validator factory.
	 * </p>
	 * 
	 * @since 1.1
	 * 
	 * @return the requested validator factory
	 */
	public TransactionValidator.Factory getValidatorFactory() {
		if (this.validatorFactory == null) {
			return TransactionValidator.Factory.INSTANCE;
		}
        
		return this.validatorFactory;
	}
	
	/**
	 * Obtains my lazily-created lifecycle implementation.
	 * 
	 * @return my lifecycle
	 * 
	 * @since 1.3
	 */
	protected synchronized final LifecycleImpl getLifecycle() {
		if (lifecycle == null) {
			lifecycle = createLifecycle();
		}
		
		return lifecycle;
	}
	
	/**
	 * Creates a new lifecycle implementation.  Subclasses may override to
	 * create their own implementation.
	 * 
	 * @return a new lifecycle
	 * 
	 * @since 1.3
	 */
	protected LifecycleImpl createLifecycle() {
		return new LifecycleImpl();
	}
	
	/**
	 * Obtains my lazily-created transaction option metadata registry.
	 * 
	 * @return my option metadata registry
	 * 
	 * @since 1.3
	 */
	protected synchronized final Transaction.OptionMetadata.Registry getOptionMetadata() {
		if (optionMetadata == null) {
			optionMetadata = createOptionMetadataRegistry();
		}

		return optionMetadata;
	}

	/**
	 * Creates a new transaction option metadata registry.
	 * Subclasses may override to create their own implementation, although it
	 * would hardly seem interesting to do so.
	 * 
	 * @return a new option metadata registry
	 * 
	 * @since 1.3
	 */
	protected Transaction.OptionMetadata.Registry createOptionMetadataRegistry() {
		return new BasicTransactionOptionMetadataRegistry();
	}
	
	//
	// Nested classes
	//
	
	/**
	 * Default implementation of the validator factory
	 * 
	 * @since 1.1
	 * 
	 * @author David Cummings (dcummin)
	 */
	public static class ValidatorFactoryImpl implements TransactionValidator.Factory {
		/**
	     * {@inheritDoc}
	     */
		public TransactionValidator createReadOnlyValidator() {
			return new ReadOnlyValidatorImpl();
		}

		/**
	     * {@inheritDoc}
	     */
		public TransactionValidator createReadWriteValidator() {
			return new ReadWriteValidatorImpl();
		}
	}
	
	/**
	 * Default implementation of a transaction editing domain factory.  This
	 * class creates {@link TransactionalEditingDomainImpl}s and provides the mapping of
	 * resource sets to editing domain instances.
	 * <p>
	 * Clients that implement their own factory can plug in to the mapping
	 * of resource sets to editing domains using the static instance's
	 * {@link #mapResourceSet(TransactionalEditingDomain)} and
	 * {@link #unmapResourceSet(TransactionalEditingDomain)} methods by casting the
	 * {@link TransactionalEditingDomain.Factory#INSTANCE} to the
	 * <code>TransactionalEditingDomainImpl.FactoryImpl</code> type.
	 * </p>
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	public static class FactoryImpl implements TransactionalEditingDomain.Factory {
		// Documentation copied from the inherited specification
		public synchronized TransactionalEditingDomain createEditingDomain() {
			TransactionalEditingDomain result = new TransactionalEditingDomainImpl(
				new ComposedAdapterFactory(
					ComposedAdapterFactory.Descriptor.Registry.INSTANCE));
			
			mapResourceSet(result);
			
			return result;
		}

		// Documentation copied from the inherited specification
		public synchronized TransactionalEditingDomain createEditingDomain(ResourceSet rset) {
			TransactionalEditingDomain result = new TransactionalEditingDomainImpl(
				new ComposedAdapterFactory(
					ComposedAdapterFactory.Descriptor.Registry.INSTANCE),
				rset);
			
			mapResourceSet(result);
			
			return result;
		}

		// Documentation copied from the inherited specification
		public TransactionalEditingDomain getEditingDomain(ResourceSet rset) {
			TransactionalEditingDomain result = null;
			
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
		public synchronized void mapResourceSet(TransactionalEditingDomain domain) {
			domain.getResourceSet().eAdapters().add(
					new ResourceSetDomainLink(domain));
		}
		
		/**
		 * Removes the specified editing domain from the global reverse mapping
		 * of resource sets.
		 * 
		 * @param domain the editing domain to remove from the resource set mapping
		 */
		public synchronized void unmapResourceSet(TransactionalEditingDomain domain) {
			for (Iterator<Adapter> iter = domain.getResourceSet().eAdapters().iterator();
					iter.hasNext();) {
				
				Adapter next = iter.next();
				
				if (next.isAdapterForType(ResourceSetDomainLink.class)) {
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
		private static class ResourceSetDomainLink extends AdapterImpl
				implements IEditingDomainProvider {
			
			private final Reference<TransactionalEditingDomain> domain;
			
			ResourceSetDomainLink(TransactionalEditingDomain domain) {
				this.domain = new WeakReference<TransactionalEditingDomain>(domain);
			}
			
			@Override
			public boolean isAdapterForType(Object type) {
				return (type == ResourceSetDomainLink.class) ||
				    (type == IEditingDomainProvider.class);
			}
			
			final TransactionalEditingDomain getDomain() {
				TransactionalEditingDomain result = domain.get();
				
				if (result == null) {
					// no longer need the adapter
					getTarget().eAdapters().remove(this);
				}
				
				return result;
			}

			public final EditingDomain getEditingDomain() {
				return getDomain();
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
	public final static class RegistryImpl implements TransactionalEditingDomain.Registry {
		private final Map<String, TransactionalEditingDomain> domains =
			new java.util.HashMap<String, TransactionalEditingDomain>();
		
		// Documentation copied from the inherited specification
		public synchronized TransactionalEditingDomain getEditingDomain(String id) {
			TransactionalEditingDomain result = domains.get(id);
			
			if (result == null) {
				result = EditingDomainManager.getInstance().createEditingDomain(id);
				
				if (result != null) {
					addImpl(id, result);
				}
			}
			
			return result;
		}

		// Documentation copied from the inherited specification
		public synchronized void add(String id, TransactionalEditingDomain domain) {
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
		void addImpl(String id, TransactionalEditingDomain domain) {
			if (!id.equals(domain.getID())) {
				domain.setID(id); // ensure that the domain's id is set
			}
			
			domains.put(id, domain);
			
			EditingDomainManager.getInstance().configureListeners(id, domain);
		}

		// Documentation copied from the inherited specification
		public synchronized TransactionalEditingDomain remove(String id) {
			EditingDomainManager.getInstance().assertDynamicallyRegistered(id);
			
			TransactionalEditingDomain result = domains.remove(id);
			
			if (result != null) {
				EditingDomainManager.getInstance().deconfigureListeners(id, result);
			}
			
			return result;
		}
		
	}
	
	/**
	 * Default implementation of the {@link InternalLifecycle} protocol.
	 * May be subclassed by custom editing domain implementations.
	 * 
	 * @author Christian W. Damus (cdamus)
	 * 
	 * @since 1.3
	 */
	protected final class LifecycleImpl
			implements InternalLifecycle {

		private final List<TransactionalEditingDomainListener> lifecycleListeners = new java.util.ArrayList<TransactionalEditingDomainListener>();

		/**
		 * Initializes me.
		 */
		public LifecycleImpl() {
			super();
		}
		
		public void addTransactionalEditingDomainListener(
				TransactionalEditingDomainListener l) {

			synchronized (lifecycleListeners) {
				if (!lifecycleListeners.contains(l)) {
					lifecycleListeners.add(l);
				}
			}
		}

		public void removeTransactionalEditingDomainListener(
				TransactionalEditingDomainListener l) {

			synchronized (lifecycleListeners) {
				lifecycleListeners.remove(l);
			}
		}

		public void dispose() {
			lifecycleListeners.clear();
		}

		/**
		 * Obtains a copy of my life-cycle listener list as an array, for safe
		 * iteration that allows concurrent updates to the original list.
		 * 
		 * @return my life-cycle listeners (as of the time of calling this
		 *         method)
		 */
		protected final TransactionalEditingDomainListener[] getLifecycleListeners() {
			synchronized (lifecycleListeners) {
				return lifecycleListeners
					.toArray(new TransactionalEditingDomainListener[lifecycleListeners
						.size()]);
			}
		}

		/**
		 * Fires the specified life-cycle event to my listeners, if any.
		 * 
		 * @param type
		 *            one of the life-cycle event
		 *            {@linkplain TransactionalEditingDomainEvent#TRANSACTION_STARTING
		 *            types}
		 * @param transaction
		 *            the transaction that is the subject of the event, or
		 *            <code>null</code> if the event pertains to the editing
		 *            domain, itself
		 */
		protected void fireLifecycleEvent(int type, Transaction transaction) {
			if (lifecycleListeners.isEmpty()) {
				return;
			}

			TransactionalEditingDomainEvent event = new TransactionalEditingDomainEvent(
				TransactionalEditingDomainImpl.this, type, transaction);

			for (TransactionalEditingDomainListener next : getLifecycleListeners()) {
				try {
					switch (type) {
						case TransactionalEditingDomainEvent.TRANSACTION_STARTING :
							next.transactionStarting(event);
							break;
						case TransactionalEditingDomainEvent.TRANSACTION_INTERRUPTED :
							next.transactionInterrupted(event);
							break;
						case TransactionalEditingDomainEvent.TRANSACTION_STARTED :
							next.transactionStarted(event);
							break;
						case TransactionalEditingDomainEvent.TRANSACTION_CLOSING :
							next.transactionClosing(event);
							break;
						case TransactionalEditingDomainEvent.TRANSACTION_CLOSED :
							next.transactionClosed(event);
							break;
						case TransactionalEditingDomainEvent.EDITING_DOMAIN_DISPOSING :
							next.editingDomainDisposing(event);
							break;
					}
				} catch (Exception e) {
					Tracing.catching(TransactionalEditingDomainImpl.class,
						"fireLifecycleEvent", e); //$NON-NLS-1$

					EMFTransactionPlugin.getPlugin().getLog().log(
						new Status(IStatus.ERROR, EMFTransactionPlugin
							.getPluginId(), Messages.lifecycleListener, e));
				}
			}
		}

		public void transactionClosed(InternalTransaction transaction) {
			fireLifecycleEvent(
				TransactionalEditingDomainEvent.TRANSACTION_CLOSED, transaction);
		}

		public void transactionClosing(InternalTransaction transaction) {
			fireLifecycleEvent(
				TransactionalEditingDomainEvent.TRANSACTION_CLOSING,
				transaction);
		}

		public void transactionInterrupted(InternalTransaction transaction) {
			fireLifecycleEvent(
				TransactionalEditingDomainEvent.TRANSACTION_INTERRUPTED,
				transaction);
		}

		public void transactionStarted(InternalTransaction transaction) {
			fireLifecycleEvent(
				TransactionalEditingDomainEvent.TRANSACTION_STARTED,
				transaction);
		}

		public void transactionStarting(InternalTransaction transaction) {
			fireLifecycleEvent(
				TransactionalEditingDomainEvent.TRANSACTION_STARTING,
				transaction);
		}

	}
}
