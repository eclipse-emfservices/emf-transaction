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
 *   Zeligsoft - Bug 245393
 *
 * </copyright>
 *
 * $Id: CompositeEMFOperation.java,v 1.6 2008/09/21 21:18:28 cdamus Exp $
 */
package org.eclipse.emf.workspace;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.ICompositeOperation;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.TriggeredOperations;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.workspace.impl.NonEMFTransaction;
import org.eclipse.emf.workspace.internal.EMFWorkspacePlugin;
import org.eclipse.emf.workspace.internal.EMFWorkspaceStatusCodes;
import org.eclipse.emf.workspace.internal.Tracing;
import org.eclipse.emf.workspace.internal.l10n.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * An implementation of a composite undoable operation for composition of
 * operations which may include {@link AbstractEMFOperation}s.  The composite
 * ensures that all of the nested operations are executed in a single
 * transaction context.  The composite can combine EMF and non-EMF operations
 * freely, and even include nested <code>CompositeEMFOperation</code>s.
 * <p>
 * Although a <code>CompositeEMFOperation</code> provides a single root
 * transaction context for all of its children, these children open nested
 * transactions of their own, by default.  This can be disabled by turning off
 * the {@link #isTransactionNestingEnabled() transactionNestingEnabled} property.
 * This is a hint that child operations should just execute in the transaction
 * that is already open, unless they require a nested transaction.  In
 * consequence, child operations would not be able to depend on changes being
 * {@linkplain ResourceSetListener#transactionAboutToCommit triggered} by
 * previous operations, as triggers will be deferred to the end of the composite
 * (when it commits).
 * </p>
 * <p>
 * Note that this kind of a composite is different from the
 * {@link IOperationHistory}'s notion of a {@link TriggeredOperations}, because
 * the children of a composite are not "triggered" by any primary operation.
 * Rather, it is assumed that the children are explicitly composed by a client.
 * The undo contexts of the composite are a union of the undo contexts of its
 * children.
 * </p>
 * <p>
 * <b>Note:</b>  This class cannot be used with the
 * {@link IOperationHistory#openOperation(ICompositeOperation, int)} API
 * because it does not implement the <code>ICompositeOperation</code> interface.
 * This prevents the possibility of open-ended transactions on the operation
 * history that any listener can contribute additional changes to, on the
 * same editing domain or a different editing domain.  The transaction API
 * provides a tightly-regulated triggered change mechanism via the
 * {@link ResourceSetListener} interface.
 * </p>
 * <p>
 * This class is intended to be instantiated by clients and supports a limited
 * form of subclassing.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 */
public class CompositeEMFOperation extends AbstractEMFOperation {

	private final List<IUndoableOperation> children;
	private boolean transactionNestingEnabled = true;
	
	/**
	 * Initializes me with the editing domain in which I am making model changes
	 * and a label.
	 * 
	 * @param domain my editing domain
	 * @param label my user-readable label
	 */
	public CompositeEMFOperation(TransactionalEditingDomain domain, String label) {
		this(domain, label, null, null);
	}
	
	/**
	 * Initializes me with the editing domain, a label, and transaction options.
	 * 
	 * @param domain my editing domain
	 * @param label my user-readable label
	 * @param options for the transaction in which I execute myself, or
	 *     <code>null</code> for the default options
	 */
	public CompositeEMFOperation(TransactionalEditingDomain domain, String label,
			Map<?, ?> options) {
		this(domain, label, null, options);
	}
	
	/**
	 * Initializes me with the editing domain, a label, and child operations.
	 * 
	 * @param domain my editing domain
	 * @param label my user-readable label
	 * @param children a list of operations to compose
	 */
	public CompositeEMFOperation(TransactionalEditingDomain domain, String label,
			List<? extends IUndoableOperation> children) {
		this(domain, label, children, null);
	}
	
	/**
	 * Initializes me with the editing domain, a label, and child operations,
	 * and transaction options.
	 * 
	 * @param domain my editing domain
	 * @param label my user-readable label
	 * @param children a list of operations to compose
	 * @param options for the transaction in which I execute myself, or
	 *     <code>null</code> for the default options
	 */
	public CompositeEMFOperation(TransactionalEditingDomain domain, String label,
			List<? extends IUndoableOperation> children, Map<?, ?> options) {
		super(domain, label, options);
		
		if (children != null) {
			this.children = new java.util.ArrayList<IUndoableOperation>(children);
		} else {
			this.children = new java.util.ArrayList<IUndoableOperation>();
		}
	}
	
	/**
	 * Implements the execution logic by sequential execution of my children.
	 * Non-EMF operations are captured in the transaction's recorded changes
	 * so that they may be correctly rolled back (in sequence) in the event
	 * of rollback, undo, or redo.
	 */
	@Override
	protected final IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		final List<IStatus> result = new java.util.ArrayList<IStatus>(size());
		
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		
		monitor.beginTask(getLabel(), size());
		
		try {
			for (ListIterator<IUndoableOperation> iter = listIterator(); iter.hasNext();) {
				if (monitor.isCanceled()) {
					// abort the current transaction so that it will rollback
					//   any changes already committed by child transactions
					((InternalTransaction) getTransaction()).abort(new Status(
						IStatus.CANCEL,
						EMFWorkspacePlugin.getPluginId(),
						1,
						Messages.executeInterrupted,
						null));
					break;
				}
				
				Transaction childTransaction = null;
				IUndoableOperation next = iter.next();
				IStatus status = null;
				
				if (!(next instanceof AbstractEMFOperation)) {
					// create a nested transaction on this operation's behalf,
					//    to record the non-EMF changes
					try {
						childTransaction = createNonEMFTransaction(
								next,
								info,
								getOptions());
					} catch (InterruptedException e) {
						Tracing.catching(CompositeEMFOperation.class, "execute", e); //$NON-NLS-1$
						ExecutionException exc = new ExecutionException(Messages.executeInterrupted, e);
						Tracing.throwing(CompositeEMFOperation.class, "execute", exc); //$NON-NLS-1$
						throw exc;
					}
				} else {
					AbstractEMFOperation emf = (AbstractEMFOperation) next;
					if (emf.isReuseParentTransaction() == isTransactionNestingEnabled()) {
						// tell the child operation to use my transaction, if
						//    appropriate.  This will also propagate transaction
						//    nesting disablement state to nested composites
						emf.setReuseParentTransaction(!isTransactionNestingEnabled());
					}
				}
				
				try {
					status = next.execute(new SubProgressMonitor(monitor, 1), info);
					result.add(status);
					
					if (status.matches(IStatus.ERROR | IStatus.CANCEL)) {
						if (childTransaction != null) {
							childTransaction.rollback();
						}
						
						// abort the current transaction so that it will rollback
						//   any changes already committed by child transactions
						((InternalTransaction) getTransaction()).abort(status);
						
						break;
					}
				} finally {					
					if ((childTransaction != null) && childTransaction.isActive()) {
						// we created a child transaction on the operation's behalf,
						//    so we must also commit it
						try {
							childTransaction.commit();
						} catch (RollbackException e) {
							Tracing.catching(CompositeEMFOperation.class, "execute", e); //$NON-NLS-1$
							
							// rollback is a normal, anticipated condition
							result.add(e.getStatus());
						}
					}
				}
			}
		} finally {
			monitor.done();
		}
		
		return aggregateStatuses(result);
	}
	
	/**
	 * Creates a transaction for recording non-EMF changes, using the specified
	 * options.
	 * 
	 * @param operation the non-EMF changes to capture in this transaction
	 * @param info the adaptable object provided to the operation when it is
	 *     executed
	 * @param options the transaction options
	 * 
	 * @return the newly started transaction
	 * 
	 * @throws InterruptedException if the current thread was interrupted while
	 *     waiting for the transaction to start
	 */
	private Transaction createNonEMFTransaction(
			IUndoableOperation operation,
			IAdaptable info,
			Map<?, ?> options)
			throws InterruptedException {
		
		InternalTransaction result = new NonEMFTransaction(
				getEditingDomain(), operation, info, options);
		
		result.start();
		
		return result;
	}
	
	@Override
	protected void didCommit(Transaction transaction) {
		super.didCommit(transaction);
		
		final Command triggers = ((InternalTransaction) transaction).getTriggers();
		if (triggers != null) {
			// append a child operation for the triggers
			getChildren().add(new AbstractOperation("") { //$NON-NLS-1$
				@Override
				public boolean canUndo() {
					return triggers.canUndo();
				}

				@Override
				public IStatus execute(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
					// this method will never be called
					triggers.execute();
					return Status.OK_STATUS;
				}

				@Override
				public IStatus redo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
					triggers.redo();
					return Status.OK_STATUS;
				}

				@Override
				public IStatus undo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
					triggers.undo();
					return Status.OK_STATUS;
				}
			});
		}
	}
	
	/**
	 * I can execute if all of my children can execute.
	 */
	@Override
	public boolean canExecute() {
		boolean result = super.canExecute();
		
		if (result) {
			for (Iterator<IUndoableOperation> iter = iterator(); result && iter.hasNext();) {
				result = iter.next().canExecute();
			}
		}
		
		return result;
	}
	
	/**
	 * I can undo if my transaction successfully completed with changes recorded
	 * and my children can all be undone.
	 */
	@Override
	public boolean canUndo() {
		boolean result = (getChange() != null);
		
		if (result && isTransactionNestingEnabled()) {
			for (Iterator<IUndoableOperation> iter = iterator(); result && iter.hasNext();) {
				result = iter.next().canUndo();
			}
		}
		
		return result;
	}
	
	/**
	 * I undo by asking my children to undo, in reverse order.
	 */
	@Override
	protected final IStatus doUndo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		if (!isTransactionNestingEnabled()) {
			// if we are not nesting transactions, then our change description
			//    has all of the changes required for undo/redo and children
			//    will not know for themselves what their changes are
			return super.doUndo(monitor, info);
		}
		
		final List<IStatus> result = new java.util.ArrayList<IStatus>(size());
		
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		
		ExecutionException caughtException = null;
		monitor.beginTask(getLabel(), size());
		
		for (ListIterator<IUndoableOperation> iter = listIterator(size());
				iter.hasPrevious();) {
			
			IUndoableOperation prev = iter.previous();
			IStatus status = null;
			
			try {
				status = prev.undo(new SubProgressMonitor(monitor, 1), info);
			} catch (ExecutionException e) {
				Tracing.catching(CompositeEMFOperation.class, "doUndo", e); //$NON-NLS-1$
				caughtException = e;
			}
			
			if (status != null) {
				result.add(status);
			}
			
			boolean childFailed = (caughtException != null)
				|| status.matches(IStatus.ERROR | IStatus.CANCEL);
			
			// monitor cancellation doesn't matter if this was the first child
			if (childFailed || (monitor.isCanceled() && iter.hasPrevious())) {
				if (childFailed) {
					// back-track over the operation that failed, assuming that it
					//    already rolled itself back by whatever means available
					iter.next();
				} else {
					// monitor was canceled
					result.add(new Status(
						IStatus.CANCEL,
						EMFWorkspacePlugin.getPluginId(),
						1,
						Messages.undoInterrupted,
						null));
				}
				
				while (iter.hasNext()) {
					// unwind the child operations
					IUndoableOperation next = iter.next();
					if (!next.canRedo()) {
						// oops!  Can't continue unwinding.  Oh, well
						EMFWorkspacePlugin.INSTANCE.log(new Status(
							IStatus.ERROR,
							EMFWorkspacePlugin.getPluginId(),
							EMFWorkspaceStatusCodes.UNDO_RECOVERY_FAILED,
							NLS.bind(Messages.undoRecoveryFailed, Messages.cannotRedo),
							null));
						break;
					}
					
					try {
						next.redo(new NullProgressMonitor(), info);
					} catch (ExecutionException inner) {
						Tracing.catching(CompositeEMFOperation.class, "doUndo", inner); //$NON-NLS-1$
						
						EMFWorkspacePlugin.INSTANCE.log(new Status(
							IStatus.ERROR,
							EMFWorkspacePlugin.getPluginId(),
							EMFWorkspaceStatusCodes.UNDO_RECOVERY_FAILED,
							NLS.bind(Messages.undoRecoveryFailed, inner.getLocalizedMessage()),
							inner));
						break;
					}
				}
				
				if (caughtException != null) {
					Tracing.throwing(CompositeEMFOperation.class, "doUndo", caughtException); //$NON-NLS-1$
					throw caughtException;
				}
				
				break; // don't go through the list again
			}
		}
		
		return aggregateStatuses(result);
	}
	
	/**
	 * I can redo if my transaction successfully completed with changes recorded
	 * and my children can all be redone.
	 */
	@Override
	public boolean canRedo() {
		boolean result = (getChange() != null);
		
		if (result && isTransactionNestingEnabled()) {
			for (Iterator<IUndoableOperation> iter = iterator(); result && iter.hasNext();) {
				result = iter.next().canRedo();
			}
		}
		
		return result;
	}
	
	/**
	 * I undo by asking my children to redo, in forward order.
	 */
	@Override
	protected final IStatus doRedo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		if (!isTransactionNestingEnabled()) {
			// if we are not nesting transactions, then our change description
			//    has all of the changes required for undo/redo and children
			//    will not know for themselves what their changes are
			return super.doRedo(monitor, info);
		}
		
		final List<IStatus> result = new java.util.ArrayList<IStatus>(size());
		
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		
		ExecutionException caughtException = null;
		monitor.beginTask(getLabel(), size());
		
		for (ListIterator<IUndoableOperation> iter = listIterator(); iter.hasNext();) {
			IUndoableOperation next = iter.next();
			IStatus status = null;
			
			try {
				status = next.redo(new SubProgressMonitor(monitor, 1), info);
			} catch (ExecutionException e) {
				Tracing.catching(CompositeEMFOperation.class, "doUndo", e); //$NON-NLS-1$
				caughtException = e;
			}
			
			if (status != null) {
				result.add(status);
			}
			
			boolean childFailed = (caughtException != null)
				|| status.matches(IStatus.ERROR | IStatus.CANCEL);
			
			// monitor cancellation doesn't matter if this was the last child
			if (childFailed || (monitor.isCanceled() && iter.hasNext())) {
				if (childFailed) {
					// back-track over the operation that failed, assuming that it
					//    already rolled itself back by whatever means available
					iter.previous();
				} else {
					// monitor was canceled
					result.add(new Status(
						IStatus.CANCEL,
						EMFWorkspacePlugin.getPluginId(),
						1,
						Messages.redoInterrupted,
						null));
				}
				
				while (iter.hasPrevious()) {
					// unwind the child operations
					IUndoableOperation prev = iter.previous();
					if (!prev.canUndo()) {
						// oops!  Can't continue unwinding.  Oh, well
						EMFWorkspacePlugin.INSTANCE.log(new Status(
							IStatus.ERROR,
							EMFWorkspacePlugin.getPluginId(),
							EMFWorkspaceStatusCodes.REDO_RECOVERY_FAILED,
							NLS.bind(Messages.redoRecoveryFailed, Messages.cannotUndo),
							null));
						break;
					}
					
					try {
						prev.undo(new NullProgressMonitor(), info);
					} catch (ExecutionException inner) {
						EMFWorkspacePlugin.INSTANCE.log(new Status(
							IStatus.ERROR,
							EMFWorkspacePlugin.getPluginId(),
							EMFWorkspaceStatusCodes.REDO_RECOVERY_FAILED,
							NLS.bind(Messages.redoRecoveryFailed, inner.getLocalizedMessage()),
							inner));
						break;
					}
				}
				
				if (caughtException != null) {
					Tracing.throwing(CompositeEMFOperation.class, "doRedo", caughtException); //$NON-NLS-1$
					throw caughtException;
				}
				
				break; // don't go through the list again
			}
		}
		
		return aggregateStatuses(result);
	}

	/**
	 * Removes all of my children and disposes them.
	 */
	@Override
	public void dispose() {
		super.dispose();
		
		for (ListIterator<IUndoableOperation> iter = listIterator(size());
				iter.hasPrevious();) {
			
			iter.previous().dispose();
			iter.remove();
		}
	}
	
	/**
	 * Obtains my nested operations.  Note that the return result is mutable and is
	 * identical to my child-operation storage, so subclasses should be careful
	 * of adding or removing contents.  This should ordinarily be done only via
	 * the {@link #add(IUndoableOperation)} and {@link #remove(IUndoableOperation)}
	 * methods because these maintain the undo contexts (or, equivalently, using
	 * the iterators).
	 * 
	 * @return my list of children
	 * 
	 * @see #add(IUndoableOperation)
	 * @see #remove(IUndoableOperation)
	 * @see #iterator()
	 * @see #listIterator(int)
	 */
	protected List<IUndoableOperation> getChildren() {
		return children;
	}
	
	/**
	 * Adds a child operation to me.  This should only be done before I am
	 * executed.  Has no effect if I already contain this operation as a child.
	 * 
	 * @param operation a new child operation
	 * 
	 * @throws IllegalStateException if I have already been successfully
	 *     executed
	 */
	public void add(IUndoableOperation operation) {
		assertNotExecuted();
		
		if (!getChildren().contains(operation)) {
			getChildren().add(operation);
			didAdd(operation);
		}
	}
	
	/**
	 * Asserts that I have not yet been executed.  Changes to my children are
	 * not permitted after I have been executed.
	 */
	protected final void assertNotExecuted() {
		if (getChange() != null) {
			IllegalStateException exc = new IllegalStateException("Operation already executed"); //$NON-NLS-1$
			Tracing.throwing(CompositeEMFOperation.class, "assertNotExecuted", exc); //$NON-NLS-1$
			throw exc;
		}
	}
	
	/**
	 * Updates my undo contexts for the addition of a new child operation.
	 * 
	 * @param operation a new child operation
	 */
	private void didAdd(IUndoableOperation operation) {
		IUndoContext[] childContexts = operation.getContexts();
		for (int i = 0; i < childContexts.length; i++) {
			if (!hasContext(childContexts[i])) {
				addContext(childContexts[i]);
			}
		}
	}

	/**
	 * Removes a child operation from me.  This should only be done before I am
	 * executed.  Has no effect if I do not contain this operation as a child.
	 * <p>
	 * <b>Note</b> that, unlike the {@link ICompositeOperation} interface (which
	 * I do not implement), I do not dispose an operation when it is removed
	 * from me.  This would not be correct, as I did not create that operation.
	 * </p>
	 * 
	 * @param operation a child operation to remove
	 * 
	 * @throws IllegalStateException if I have already been successfully
	 *     executed
	 */
	public void remove(IUndoableOperation operation) {
		assertNotExecuted();
		
		if (getChildren().remove(operation)) {
			didRemove(operation);
		}
	}
	
	/**
	 * Updates my undo contexts for the removal of a child operation.
	 * 
	 * @param operation an erstwhile child operation
	 */
	private void didRemove(IUndoableOperation operation) {
		IUndoContext[] childContexts = operation.getContexts();
		
		for (int i = 0; i < childContexts.length; i++) {
			if (!anyChildHasContext(childContexts[i])) {
				removeContext(childContexts[i]);
			}
		}
	}
	
	/**
	 * Queries whether any of my children has the specified context.
	 * 
	 * @param ctx a context
	 * 
	 * @return <code>false</code> if none of my children has the specified
	 *     context; <code>true</code>, otherwise
	 */
	private boolean anyChildHasContext(IUndoContext ctx) {
		boolean result = false;
		
		for (Iterator<IUndoableOperation> iter = iterator(); !result && iter.hasNext();) {
			result = iter.next().hasContext(ctx);
		}
		
		return result;
	}
	
	/**
	 * Extends the inherited method to toggle my
	 * {@link #setTransactionNestingDisabled(boolean) transaction nesting}
	 * state accordingly.
	 * 
	 * @since 1.3
	 * 
	 * @see #isTransactionNestingEnabled()
	 */
	@Override
	public void setReuseParentTransaction(boolean reuseParentTransaction) {
		super.setReuseParentTransaction(reuseParentTransaction);
		
		setTransactionNestingEnabled(!reuseParentTransaction);
	}
	
	/**
	 * Queries whether nesting of transactions is enabled, meaning that all
	 * child operations (recursively) execute in their own (nested) transactions.
	 * Nested transactions are enabled by default.
	 * 
	 * @return <code>false</code> if child operations execute in a single (flat)
	 *    transaction where possible; <code>false</code>, otherwise
	 *    
	 * @see #setTransactionNestingEnabled(boolean)
	 */
	public boolean isTransactionNestingEnabled() {
		return transactionNestingEnabled;
	}
	
	/**
	 * Sets whether nesting of transactions is enabled.  Turning this off can
	 * improve performance considerably by avoiding the book-keeping overhead
	 * of large numbers of small transactions, where a composite operation is
	 * a large nested structure.  This property is propagated to child
	 * composite operations (recursively).
	 * <p>
	 * <b>Note</b> that this is really only a hint:  there are exceptions where
	 * child operation will still create nested transactions.  These are:
	 * <ul>
	 *   <li>when the child is not an {@link AbstractEMFOperation}.  Non-EMF
	 *       operations are given special transactions that capture their changes
	 *       as change descriptions in the parent transaction</li>
	 *   <li>when the child has different
	 *       {@link AbstractEMFOperation#getOptions() transaction options} than
	 *       the parent composite operation</li>
	 * </ul>
	 * </p>
	 *  
	 * @param enable whether to force all child operations to create nested
	 *     transactions
	 *     
	 * @throws IllegalStateException if I have already been
	 *     {@link #execute(IProgressMonitor, IAdaptable) executed}, after which
	 *     time transaction nesting cannot be changed
	 */
	public void setTransactionNestingEnabled(boolean enable) {
		assertNotExecuted();
		
		transactionNestingEnabled = enable;
	}
	
	/**
	 * Queries the number of child operations that I contain.
	 * 
	 * @return my size
	 */
	public int size() {
		return getChildren().size();
	}
	
	/**
	 * Obtains an iterator to traverse my child operations.
	 * Removing children via this iterator correctly maintains my undo contexts.
	 * 
	 * @return an iterator of my children
	 */
	public Iterator<IUndoableOperation> iterator() {
		return new ChildIterator();
	}
	
	/**
	 * Obtains an iterator to traverse my child operations in either direction.
	 * Adding and removing children via this iterator correctly maintains my
	 * undo contexts.
	 * <p>
	 * <b>Note</b> that, unlike list iterators generally, this implementation
	 * does not permit the addition of an operation that I already contain
	 * (the composite does not permit duplicates).  Moreover, only
	 * {@link IUndoableOperation}s may be added, otherwise
	 * <code>ClassCastException</code>s will result.
	 * </p>
	 * 
	 * @return an iterator of my children
	 */
	public ListIterator<IUndoableOperation> listIterator() {
		return new ChildListIterator(0);
	}
	
	/**
	 * Obtains an iterator to traverse my child operations in either direction,
	 * starting from the specified <code>index</code>.
	 * Adding and removing children via this iterator correctly maintains my
	 * undo contexts.
	 * <p>
	 * <b>Note</b> that, unlike list iterators generally, this implementation
	 * does not permit the addition of an operation that I already contain
	 * (the composite does not permit duplicates).  Moreover, only
	 * {@link IUndoableOperation}s may be added, otherwise
	 * <code>ClassCastException</code>s will result.
	 * </p>
	 * 
	 * @param index the index in my children at which to start iterating
	 * 
	 * @return an iterator of my children
	 */
	public ListIterator<IUndoableOperation> listIterator(int index) {
		return new ChildListIterator(index);
	}
	
	/**
	 * Custom iterator implementation that maintains my undo contexts
	 * correctly when elements are removed.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	private class ChildIterator implements Iterator<IUndoableOperation> {
		protected IUndoableOperation last;
		protected final ListIterator<IUndoableOperation> iter;
	
		ChildIterator() {
			this(0);
		}
		
		ChildIterator(int index) {
			iter = getChildren().listIterator(index);
		}
		
		public void remove() {
			assertNotExecuted();
			
			iter.remove();
			didRemove(last);
			last = null;
		}
	
		public IUndoableOperation next() {
			last = iter.next();
			return last;
		}
	
		public boolean hasNext() {
			return iter.hasNext();
		}
	}
	
	/**
	 * Custom list-iterator implementation that maintains my undo contexts
	 * correctly, as well as uniqueness of the list contents.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	private class ChildListIterator extends ChildIterator
			implements ListIterator<IUndoableOperation> {
		
		ChildListIterator(int index) {
			super(index);
		}
		
		public void add(IUndoableOperation o) {
			assertNotExecuted();
			
			if (!getChildren().contains(o)) {
				iter.add(o);
				didAdd(o);
			}
		}
	
		public void set(IUndoableOperation o) {
			assertNotExecuted();
			
			if (!getChildren().contains(o)) {
				didRemove(last);
				iter.set(o);
				last = o;
				didAdd(o);
			}
		}
	
		public int previousIndex() {
			return iter.previousIndex();
		}
	
		public int nextIndex() {
			return iter.nextIndex();
		}
	
		public IUndoableOperation previous() {
			last = iter.previous();
			return last;
		}
	
		public boolean hasPrevious() {
			return iter.hasPrevious();
		}
	}
}
