/**
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bugs 234868, 245419, 245393, 250253
 */
package org.eclipse.emf.workspace;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionChangeDescription;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.TransactionImpl;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.emf.workspace.internal.EMFWorkspacePlugin;
import org.eclipse.emf.workspace.internal.EMFWorkspaceStatusCodes;
import org.eclipse.emf.workspace.internal.Tracing;
import org.eclipse.emf.workspace.internal.l10n.Messages;

/**
 * An abstract superclass for {@link IUndoableOperation}s that modify EMF
 * models.  The EMF operation provides a read/write transaction context for the
 * subclass implementation of the execution logic, with undo/redo support "for
 * free" (via recording of undo information).
 * <p>
 * The assumption is that an EMF operation will perform only changes to EMF
 * models that can be recorded.  If concomitant changes to non-EMF models are
 * also required, then they should be combined with the EMF operation via a
 * {@link CompositeEMFOperation}, unless ordering of EMF and non-EMF changes
 * is unimportant.  In such cases, it is sufficient to extend the
 * {@link #doUndo(IProgressMonitor, IAdaptable)} and
 * {@link #doRedo(IProgressMonitor, IAdaptable)} methods.
 * </p>
 * <p>
 * This class is meant to be extended by clients.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see CompositeEMFOperation
 */
public abstract class AbstractEMFOperation extends AbstractOperation {
	private final InternalTransactionalEditingDomain domain;
	private Map<Object, Object> txOptions;
	private boolean canSetOptions = true;
	
	private Transaction transaction;
	private TransactionChangeDescription change;
	
	private boolean reuseParentTransaction;
    boolean shouldDisposeChange;
	
	/**
	 * Initializes me with the editing domain in which I am making model changes
	 * and a label.
	 * 
	 * @param domain my editing domain
	 * @param label my user-readable label
	 */
	public AbstractEMFOperation(TransactionalEditingDomain domain, String label) {
		this(domain, label, null);
	}

	/**
	 * Initializes me with the editing domain, a label, and transaction options.
	 * 
	 * @param domain my editing domain
	 * @param label my user-readable label
	 * @param options for the transaction in which I execute myself, or
	 *     <code>null</code> for the default options
	 */
	public AbstractEMFOperation(TransactionalEditingDomain domain, String label,
			Map<?, ?> options) {
		
		super(label);
		
		this.domain = (InternalTransactionalEditingDomain) domain;
		internalSetOptions(options);
	}

	/**
	 * Implements the execution by delegating to the
	 * {@link #doExecute(IProgressMonitor, IAdaptable)} method within a
	 * read/write transaction.
	 * 
	 * @see #doExecute(IProgressMonitor, IAdaptable)
	 */
	@Override
	public final IStatus execute(IProgressMonitor monitor, IAdaptable info)
		throws ExecutionException {
		
		canSetOptions = false; // freeze my options
		
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }
        
		transaction = null;
		final List<IStatus> result = new java.util.ArrayList<IStatus>(2);
		
		try {
            // add to the transaction a reference to myself for the
            //   command stack's benefit, unless I would be inheriting
            //   a reference to a nesting operation
            Map<Object, Object> options = new java.util.HashMap<Object, Object>(
                    getOptions());
            Map<?, ?> inherited = inheritedOptions();
            if (inherited.containsKey(EMFWorkspacePlugin.OPTION_OWNING_OPERATION)) {
                options.put(EMFWorkspacePlugin.OPTION_OWNING_OPERATION,
                    inherited.get(EMFWorkspacePlugin.OPTION_OWNING_OPERATION));
            } else {
                options.put(EMFWorkspacePlugin.OPTION_OWNING_OPERATION, this);
            }
            
			if (!isReuseParentTransaction() || optionsDiffer(options)) {
				transaction = createTransaction(options);
			}
			
			IStatus status = doExecute(monitor, info);
			result.add(status);
			
			if (transaction != null) {
				if ((status == null) || (status.getSeverity() < IStatus.ERROR)) {
					transaction.commit();
					change = transaction.getChangeDescription();
	                
	                // ordinarily, we recursively dispose the root tx's change
	                shouldDisposeChange = transaction.getParent() == null;
	                
					didCommit(transaction);
				} else {
					((InternalTransaction) transaction).setStatus(status);
					transaction.rollback();
				}
			}
		} catch (InterruptedException e) {
			Tracing.catching(AbstractEMFOperation.class, "execute", e); //$NON-NLS-1$
			ExecutionException exc = new ExecutionException(Messages.executeInterrupted, e);
			Tracing.throwing(AbstractEMFOperation.class, "execute", exc); //$NON-NLS-1$
			throw exc;
		} catch (RollbackException e) {
			Tracing.catching(AbstractEMFOperation.class, "execute", e); //$NON-NLS-1$
			
			// rollback is a normal, anticipated condition
			result.add(e.getStatus());
		} catch (OperationCanceledException e) {
			// snuff the exception, because this is expected (user asked to
			//    cancel the model change).  We will rollback, below
			result.add(Status.CANCEL_STATUS);
		} finally {
			if ((transaction != null) && transaction.isActive()) {
				// we didn't commit it, so some RuntimeException or Error must
				//    have been thrown.  Roll back to protect data integrity
				//    but let the exception reach the caller.  Cannot return
				//    an error status because we're throwing
				rollback(transaction);
			}
			
			transaction = null;
		}
		
		return aggregateStatuses(result);
	}
	
	/**
	 * Queries whether the specified options differ from the currently active
     * transaction, if there is one.
	 * 
     * @param options the options to compare against the active transaction
	 * @return <code>false</code> if either there is an active transaction and it
	 *     has the same options as specified; <code>true</code>, otherwise
	 */
	private boolean optionsDiffer(Map<?, ?> options) {
		boolean result = true;

		Transaction active = ((InternalTransactionalEditingDomain) getEditingDomain())
			.getActiveTransaction();
		if ((active != null) && !active.isReadOnly()) {
			Transaction.OptionMetadata.Registry reg = TransactionUtil
				.getTransactionOptionRegistry(getEditingDomain());

			result = false; // let's look for a difference
			Map<?, ?> activeOptions = active.getOptions();

			// iterate the options passed in that would be applied to the
			// nested transaction, because if the active (to-be parent)
			// specifies more options, then they would be inherited, anyway
			for (Map.Entry<?, ?> next : options.entrySet()) {
				Object option = next.getKey();

				Transaction.OptionMetadata metadata = reg
					.getOptionMetadata(option);

				// tags don't force child transactions. Clients would have
				// to disable nesting if it really matters to them, or else
				// override the option meta-data in their editing domain's
				// local registry
				if (!metadata.isTag()
					&& !metadata.sameSetting(options, activeOptions)) {

					result = true;
					break;
				}
			}
		}

		return result;
	}
    
    /**
     * Obtains the options of the currently active transaction, or an empty map
     * if there is no active transaction.
     * 
     * @return options currently in effect that would be inherited by a new
     *     transaction that I might create
     */
    private Map<?, ?> inheritedOptions() {
        Map<?, ?> result = Collections.EMPTY_MAP;
        
        Transaction active =
            ((InternalTransactionalEditingDomain) getEditingDomain()).getActiveTransaction();
        if ((active != null) && !active.isReadOnly()) {
            result = active.getOptions();
        }
        
        return result;
    }
	
	/**
	 * Creates a suitable aggregate from these statuses.  If there are no
	 * statuses to aggregate, then an OK status is returned.  If there is a
	 * single status to aggregate, then it is returned.  Otherwise, a
	 * multi-status is returned with the provided statuses as children.
	 * 
	 * @param statuses the statuses to aggregate.  May have zero, one, or more
	 *    elements (all must be {@link IStatus}es)
	 * 
	 * @return the multi-status
	 */
	protected IStatus aggregateStatuses(List<? extends IStatus> statuses) {
		final IStatus result;
		
		if (statuses.isEmpty()) {
			result = Status.OK_STATUS;
		} else if (statuses.size() == 1) {
			result = statuses.get(0);
		} else {
			// find the most severe status, to use its plug-in, code, and message
			IStatus[] children = statuses.toArray(new IStatus[statuses.size()]);
			
			IStatus worst = children[0];
			for (int i = 1; i < children.length; i++) {
				if (children[i].getSeverity() > worst.getSeverity()) {
					worst = children[i];
				}
			}
			
			result = new MultiStatus(
					worst.getPlugin(),
					worst.getCode(),
					children,
					worst.getMessage(),
					null);  // the child status will have it (save on log space)
		}
		
		return result;
	}
	
	/**
	 * Obtains the change description that I recorded during execution of my
	 * transaction.
	 * 
	 * @return my change description, if I executed successfully;
	 *     <code>null</code>, otherwise
	 */
	protected final TransactionChangeDescription getChange() {
		return change;
	}

	/**
	 * <p>
	 * Hook for subclasses to learn that the specified <code>transaction</code>
	 * has been successfully committed and, if necessary, to extract information
	 * from it.
	 * </p>
	 * <p>
	 * Note: subclasses should call this super implementation to get some default
	 *  behaviours.
	 * </p>
	 * @param transaction a transaction that has committed, which has recorded
	 *     our changes
	 */
	protected void didCommit(Transaction transaction) {
		gatherUndoContextsFromTrigger(((InternalTransaction)transaction).getTriggers());
	}
	
	private void gatherUndoContextsFromTrigger(Command trigger) {
		if (trigger instanceof CompoundCommand) {
			for (Command next : ((CompoundCommand) trigger).getCommandList()) {
				gatherUndoContextsFromTrigger(next);
			}
		} else if (trigger instanceof EMFOperationCommand) {
			IUndoContext[] undoContextsToAdd = ((EMFOperationCommand)trigger).getOperation().getContexts();
			for (int j = 0; j<undoContextsToAdd.length; j++) {
				if (undoContextsToAdd[j] != null) {
					addContext(undoContextsToAdd[j]);
				}
			}
		}
	}

	/**
	 * Queries whether I can be undone.  I can generally be undone if I was
	 * successfully executed.  Subclasses would not usually need to override
	 * this method.
	 */
	@Override
	public boolean canUndo() {
		return (getChange() == null) || getChange().canApply();
	}

	/**
	 * Undoes me by inverting my recorded changes in a transaction.
	 */
	@Override
	public final IStatus undo(IProgressMonitor monitor, IAdaptable info)
		throws ExecutionException {
		
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }
        
		Transaction tx = null;
		IStatus result = null;
		
		try {
			tx = createTransaction(domain.getUndoRedoOptions());
			
			result = doUndo(monitor, info);
			
			tx.commit();
			
			didUndo(tx);
		} catch (InterruptedException e) {
			Tracing.catching(AbstractEMFOperation.class, "undo", e); //$NON-NLS-1$
			ExecutionException exc = new ExecutionException(Messages.undoInterrupted, e);
			Tracing.throwing(AbstractEMFOperation.class, "undo", exc); //$NON-NLS-1$
			throw exc;
		} catch (RollbackException e) {
			Tracing.catching(AbstractEMFOperation.class, "undo", e); //$NON-NLS-1$
			
			// rollback is a normal, anticipated condition
			result = e.getStatus();
		} catch (OperationCanceledException e) {
			// snuff the exception, because this is expected (user asked to
			//    cancel the model change).  We will rollback, below
			result = Status.CANCEL_STATUS;
		} catch (Exception e) {
			// the change description could contain non-EMF operations
			// that fail to undo, resulting in WrappedExceptions or
			// other exceptions
			Tracing.catching(AbstractEMFOperation.class, "undo", e); //$NON-NLS-1$
			
			Throwable t = e;
			if (t instanceof WrappedException) {
				// after tracing, unwrap
				t = ((WrappedException) e).getCause();
			}
			
			IStatus status = new Status(
				IStatus.ERROR,
				EMFWorkspacePlugin.getPluginId(),
				EMFWorkspaceStatusCodes.UNDO_ROLLED_BACK,
				Messages.undoRolledBack,
				t);
			EMFWorkspacePlugin.INSTANCE.log(status);
			
			throw new ExecutionException(status.getMessage(), t);
		} finally {
			if ((tx != null) && tx.isActive()) {
				// we didn't commit it, so some RuntimeException or Error must
				//    have been thrown.  Roll back to protect data integrity
				//    but let the exception reach the caller.  Cannot return
				//    an error status because we're throwing
				rollback(tx);
			}
		}
		
		return result;
	}

	/**
	 * Hook for subclasses to learn that the specified <code>transaction</code>
	 * has been successfully undone and, if necessary, to extract information
	 * from it.
	 * 
	 * @param tx a transaction that has been undone.
	 */
	protected void didUndo(Transaction tx) {
		// Method can be overriden by subclasses.
	}

	/**
	 * Queries whether I can be redone.  I can generally be redone if I was
	 * successfully executed.  Subclasses would not usually need to override
	 * this method.
	 */
	@Override
	public boolean canRedo() {
		return (getChange() == null) || getChange().canApply();
	}
	
	/**
	 * Redoes me by replaying my recorded changes in a transaction.
	 */
	@Override
	public final IStatus redo(IProgressMonitor monitor, IAdaptable info)
		throws ExecutionException {
		
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }
        
		Transaction tx = null;
		IStatus result = null;
		
		try {
			tx = createTransaction(domain.getUndoRedoOptions());
			
			result = doRedo(monitor, info);
			
			tx.commit();
			
			didRedo(tx);
		} catch (InterruptedException e) {
			Tracing.catching(AbstractEMFOperation.class, "redo", e); //$NON-NLS-1$
			ExecutionException exc = new ExecutionException(Messages.redoInterrupted, e);
			Tracing.throwing(AbstractEMFOperation.class, "redo", exc); //$NON-NLS-1$
			throw exc;
		} catch (RollbackException e) {
			Tracing.catching(AbstractEMFOperation.class, "redo", e); //$NON-NLS-1$
			
			// rollback is a normal, anticipated condition
			result = e.getStatus();
		} catch (OperationCanceledException e) {
			// snuff the exception, because this is expected (user asked to
			//    cancel the model change).  We will rollback, below
			result = Status.CANCEL_STATUS;
		} catch (Exception e) {
			// the change description could contain non-EMF operations
			// that fail to redo, resulting in WrappedExceptions or
			// other exceptions
			Tracing.catching(AbstractEMFOperation.class, "redo", e); //$NON-NLS-1$
			
			Throwable t = e;
			if (t instanceof WrappedException) {
				// after tracing, unwrap
				t = ((WrappedException) e).getCause();
			}
			
			IStatus status = new Status(
				IStatus.ERROR,
				EMFWorkspacePlugin.getPluginId(),
				EMFWorkspaceStatusCodes.REDO_ROLLED_BACK,
				Messages.redoRolledBack,
				t);
			EMFWorkspacePlugin.INSTANCE.log(status);
			
			throw new ExecutionException(status.getMessage(), t);
		} finally {
			if ((tx != null) && tx.isActive()) {
				// we didn't commit it, so some RuntimeException or Error must
				//    have been thrown.  Roll back to protect data integrity
				//    but let the exception reach the caller.  Cannot return
				//    an error status because we're throwing
				rollback(tx);
			}
		}
		
		return result;
	}
	
	/**
	 * Hook for subclasses to learn that the specified <code>transaction</code>
	 * has been successfully redone and, if necessary, to extract information
	 * from it.
	 * 
	 * @param tx a transaction that has been redone.
	 */
	protected void didRedo(Transaction tx) {
		// Subclasses may override this
	}

	/**
	 * Obtains my editing domain.
	 * 
	 * @return my editing domain
	 */
	public final TransactionalEditingDomain getEditingDomain() {
		return domain;
	}

	/**
	 * Obtains the transaction options that I use to create my transaction.
	 * 
	 * @return my options, or an empty map if none
	 */
	public final Map<?, ?> getOptions() {
		return txOptions;
	}

	/**
	 * <p>
	 * Replaces my options with a new set. This may only be done prior to my
	 * initial execution.
	 * </p>
	 * <p>
	 * <b>Note</b> that subclasses may override this method, but if they do so,
	 * then they must call the superclass implementation in order actually to
	 * effect any change to the operation's options. Thus, subclasses may
	 * override to disable this capability or to intercept the <tt>options</tt>
	 * argument and transform its values as required.
	 * </p>
	 * 
	 * @param options
	 *            my new options
	 * 
	 * @throws IllegalStateException
	 *             if I have {@linkplain #canSetOptions() already been executed}
	 * 
	 * @since 1.3
	 * 
	 * @see #canSetOptions()
	 */
	public void setOptions(Map<?, ?> options) {
		internalSetOptions(options);
	}

	/**
	 * Queries whether my options can be changed. That is, whether I have not
	 * yet been executed.
	 * 
	 * @return whether my options may be changed
	 * 
	 * @since 1.3
	 * 
	 * @see #setOptions(Map)
	 */
	public boolean canSetOptions() {
		return canSetOptions;
	}

	private void internalSetOptions(Map<?, ?> options) {
		if (!canSetOptions) {
			throw new IllegalStateException("operation has been executed"); //$NON-NLS-1$
		}
		
		if (options == null) {
			this.txOptions = Collections.<Object, Object> singletonMap(
				TransactionImpl.BLOCK_CHANGE_PROPAGATION, Boolean.TRUE);
		} else {
			// make a defensive copy to
			// - avoid modifying client's data
			// - guard against client modifying my map
			// - avoid exceptions on immutable maps
			Map<Object, Object> myOptions = new java.util.HashMap<Object, Object>(
				options);
			myOptions.put(TransactionImpl.BLOCK_CHANGE_PROPAGATION,
				Boolean.TRUE);
			this.txOptions = Collections.unmodifiableMap(myOptions);
		}
	}
	
	/**
	 * Creates a transaction, using the specified options, for me to execute
	 * in.
	 * 
	 * @param options the transaction options
	 * 
	 * @return the newly started transaction
	 * 
	 * @throws InterruptedException if the current thread was interrupted while
	 *     waiting for the transaction to start
	 */
	Transaction createTransaction(Map<?, ?> options) throws InterruptedException {
		return domain.startTransaction(false, options);
	}
	
	/**
	 * Ensures that the specified transaction is rolled back, first rolling
	 * back a nested transaction (if any).
	 * 
	 * @param tx a transaction to roll back
	 */
	void rollback(Transaction tx) {
		while (tx.isActive()) {
			Transaction active = domain.getActiveTransaction();
			
			active.rollback();
		}
	}
	
	/**
	 * Obtains the transaction in which I execute(d).
	 * 
	 * @return my transaction
	 */
	Transaction getTransaction() {
		return transaction;
	}
	
	/**
	 * Implemented by subclasses to perform the model changes.  These changes
	 * are applied by manipulation of the EMF metamodel's API, <em>not</em>
	 * by executing commands on the editing domain's command stack.
	 * 
	 * @param monitor the progress monitor provided by the operation history.
     *     Will never be <code>null</code> because the
     *     {@link #execute(IProgressMonitor, IAdaptable)} method would substitute
     *     a {@link NullProgressMonitor} in that case
	 * @param info the adaptable provided by the operation history
	 * 
	 * @return the status of the execution
	 * 
	 * @throws ExecutionException if, for some reason, I fail to complete
	 *     the operation
	 */
	protected abstract IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
		throws ExecutionException;
	
	/**
	 * Implements the undo behaviour by inverting my recorded changes.
	 * <p>
	 * <b>Note</b> that subclasses overriding this method <em>must</em> invoke
	 * the super implementation as well.
	 * </p>
	 * 
	 * @param monitor the progress monitor provided by the operation history
     *     Will never be <code>null</code> because the
     *     {@link #undo(IProgressMonitor, IAdaptable)} method would substitute
     *     a {@link NullProgressMonitor} in that case
	 * @param info the adaptable provided by the operation history
	 * 
	 * @return the status of the undo operation
	 * 
	 * @throws ExecutionException on failure to undo
	 */
	protected IStatus doUndo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		InternalTransaction active = domain.getActiveTransaction();
		
		if (change != null) {
			if ((active != null) && active.isRollingBack()) {
				// my changes are included in my parent's change description, and it
				//    is already applying them.  I am only being asked to undo
				//    because I am nested in some kind of non-EMF composite
				//    operation, and this is how non-EMF operations roll back.  So,
				//    I must simply apply() rather than applyAndReverse(), otherwise
				//    my parent transaction will find changes that can be applied
				//    again, effectively undoing my rollback
				getChange().apply();
			} else {
				getChange().applyAndReverse();
			}
		}
		
		return Status.OK_STATUS;
	}
	
	/**
	 * Implements the redo behaviour by replaying my recorded changes.
	 * <p>
	 * <b>Note</b> that subclasses overriding this method <em>must</em> invoke
	 * the super implementation as well.
	 * </p>
	 * 
	 * @param monitor the progress monitor provided by the operation history
     *     Will never be <code>null</code> because the
     *     {@link #redo(IProgressMonitor, IAdaptable)} method would substitute
     *     a {@link NullProgressMonitor} in that case
	 * @param info the adaptable provided by the operation history
	 * 
	 * @return the status of the redo operation
	 * 
	 * @throws ExecutionException on failure to redo
	 */
	protected IStatus doRedo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		if (change != null) {
			getChange().applyAndReverse();
		}
		
		return Status.OK_STATUS;
	}
	
	/**
	 * Forgets my transaction and its change description.
	 */
	@Override
	public void dispose() {
		super.dispose();
		
        disposeChange(false);
        
		transaction = null;  // just in case we didn't manage to forget, sooner
	}
    
    /**
     * Disposes my change description.
     * 
     * @param force whether to force disposal of the change
     */
    void disposeChange(boolean force) {
        // only dispose the root transaction's change, because it is a
        //   composite that will dispose all nested changes
        if ((change != null) && (shouldDisposeChange || force)) {
            TransactionUtil.dispose(change);
        }
        
        change = null;
    }

	/**
	 * Queries whether I reuse an existing read/write transaction when possible.
	 * It is not possible when either there is not any active transaction at the
	 * time of my execution or when the active transaction has different options
	 * from my options.
	 * 
	 * @return whether I reuse existing transactions
	 * 
	 * @since 1.3
	 * 
	 * @see #setReuseParentTransaction(boolean)
	 */
	public boolean isReuseParentTransaction() {
		return reuseParentTransaction;
	}

	/**
	 * Sets whether I reuse an existing read/write transaction when possible. It
	 * is not possible when either there is not any active transaction at the
	 * time of my execution or when the active transaction has different options
	 * from my options. This can be useful for performance of large nested
	 * operation structures, to eliminate the overhead of creating large numbers
	 * of small transactions with all of the data that they record.
	 * 
	 * @param reuseParentTransaction
	 *            whether to reuse parent transactions
	 * 
	 * @since 1.3
	 * 
	 * @see #isReuseParentTransaction()
	 */
	public void setReuseParentTransaction(boolean reuseParentTransaction) {
		this.reuseParentTransaction = reuseParentTransaction;
	}
}