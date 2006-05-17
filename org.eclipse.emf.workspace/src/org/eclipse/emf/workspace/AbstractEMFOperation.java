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
 * $Id: AbstractEMFOperation.java,v 1.10 2006/05/17 21:32:32 cmcgee Exp $
 */
package org.eclipse.emf.workspace;

import java.util.Collections;
import java.util.Iterator;
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
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionChangeDescription;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
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
	private final Map txOptions;
	
	private Transaction transaction;
	private TransactionChangeDescription change;
	
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
	public AbstractEMFOperation(TransactionalEditingDomain domain, String label, Map options) {
		super(label);
		
		this.domain = (InternalTransactionalEditingDomain) domain;
		if (options == null) {
			this.txOptions = Collections.EMPTY_MAP;
		} else {
			// make a defensive copy to
			//  - avoid modifying client's data
			//  - guard against client modifying my map
			//  - avoid exceptions on immutable maps
			this.txOptions = new java.util.HashMap(options);
		}
	}

	/**
	 * Implements the execution by delegating to the
	 * {@link #doExecute(IProgressMonitor, IAdaptable)} method within a
	 * read/write transaction.
	 * 
	 * @see #doExecute(IProgressMonitor, IAdaptable)
	 */
	public final IStatus execute(IProgressMonitor monitor, IAdaptable info)
		throws ExecutionException {
		
		transaction = null;
		final List result = new java.util.ArrayList(2);
		
		try {
			transaction = createTransaction(getOptions());
			
			result.add(doExecute(monitor, info));
			
			transaction.commit();
			change = transaction.getChangeDescription();
			
			didCommit(transaction);
		} catch (InterruptedException e) {
			Tracing.catching(AbstractEMFOperation.class, "execute", e); //$NON-NLS-1$
			ExecutionException exc = new ExecutionException(Messages.executeInterrupted, e);
			Tracing.throwing(AbstractEMFOperation.class, "execute", exc); //$NON-NLS-1$
			throw exc;
		} catch (RollbackException e) {
			Tracing.catching(AbstractEMFOperation.class, "execute", e); //$NON-NLS-1$
			
			// rollback is a normal, anticipated condition
			result.add(e.getStatus());
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
	protected IStatus aggregateStatuses(List statuses) {
		final IStatus result;
		
		if (statuses.isEmpty()) {
			result = Status.OK_STATUS;
		} else if (statuses.size() == 1) {
			result = ((IStatus) statuses.get(0));
		} else {
			// find the most severe status, to use its plug-in, code, and message
			IStatus[] children = (IStatus[]) statuses.toArray(
					new IStatus[statuses.size()]);
			
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
			for (Iterator i = ((CompoundCommand) trigger).getCommandList()
				.iterator(); i.hasNext();) {
				gatherUndoContextsFromTrigger((Command)i.next());
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
	public boolean canUndo() {
		return (getChange() == null) || getChange().canApply();
	}

	/**
	 * Undoes me by inverting my recorded changes in a transaction.
	 */
	public final IStatus undo(IProgressMonitor monitor, IAdaptable info)
		throws ExecutionException {
		
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
	 * @param transaction a transaction that has been undone.
	 */
	protected void didUndo(Transaction tx) {
		// Method can be overriden by subclasses.
	}

	/**
	 * Queries whether I can be redone.  I can generally be redone if I was
	 * successfully executed.  Subclasses would not usually need to override
	 * this method.
	 */
	public boolean canRedo() {
		return (getChange() == null) || getChange().canApply();
	}
	
	/**
	 * Redoes me by replaying my recorded changes in a transaction.
	 */
	public final IStatus redo(IProgressMonitor monitor, IAdaptable info)
		throws ExecutionException {
		
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
	 * @param transaction a transaction that has been redone.
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
	public final Map getOptions() {
		return txOptions;
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
	Transaction createTransaction(Map options) throws InterruptedException {
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
	 * @param monitor the progress monitor provided by the operation history
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
	public void dispose() {
		super.dispose();
		
		transaction = null;
		change = null;
	}
}
