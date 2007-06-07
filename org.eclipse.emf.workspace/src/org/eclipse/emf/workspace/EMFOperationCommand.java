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
 * $Id: EMFOperationCommand.java,v 1.5 2007/06/07 14:25:44 cdamus Exp $
 */
package org.eclipse.emf.workspace;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IAdvancedUndoableOperation;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.util.ConditionalRedoCommand;
import org.eclipse.emf.workspace.impl.NonEMFTransaction;
import org.eclipse.emf.workspace.internal.EMFWorkspacePlugin;
import org.eclipse.emf.workspace.internal.EMFWorkspaceStatusCodes;
import org.eclipse.emf.workspace.internal.Tracing;
import org.eclipse.emf.workspace.internal.l10n.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * An implementation of the EMF {@link Command} API that wraps an
 * {@link IUndoableOperation}.  It is particularly useful for returning triggers
 * from a {@link ResourceSetListener} that perform non-EMF changes.  An
 * <code>EMFOperationCommand</code>, when executed, automatically inserts itself
 * into the change description of the active transaction to support the
 * inclusion of these non-EMF changes in:
 * <ul>
 *     <li>transaction rollback (in case of validation failure, etc.)</li>
 *     <li>undo/redo of {@link RecordingCommand}s</li>
 * </ul>
 *
 * @author Christian W. Damus (cdamus)
 */
public class EMFOperationCommand implements ConditionalRedoCommand {
	private final TransactionalEditingDomain domain;
	private IUndoableOperation operation;
	private Reference adaptable;
	
	/**
	 * Initializes me with the undoable operation that I wrap.
	 * 
	 * @param domain the editing domain in which I will be executed
	 * @param operation my operation
	 * 
	 * @throws IllegalArgumentException if either the domain or operation is
	 *      <code>null</code>
	 */
	public EMFOperationCommand(TransactionalEditingDomain domain, IUndoableOperation operation) {
		this(domain, operation, null);
	}
	
	/**
	 * Initializes me with the undoable operation that I wrap and an adaptable
	 * to pass to it when I execute/undo/redo.
	 * 
	 * @param domain the editing domain in which I will be executed
	 * @param operation my operation
	 * @param adaptable the adaptable to provide UI context to the operation
	 * 
	 * @throws IllegalArgumentException if either the domain or operation is
	 *      <code>null</code>
	 */
	public EMFOperationCommand(TransactionalEditingDomain domain,
			IUndoableOperation operation,
			IAdaptable adaptable) {
		if (domain == null) {
			throw new IllegalArgumentException("null domain"); //$NON-NLS-1$
		}
		if (operation == null) {
			throw new IllegalArgumentException("null operation"); //$NON-NLS-1$
		}
		
		this.domain = domain;
		this.operation = operation;
		
		if (adaptable != null) {
			this.adaptable = new WeakReference(adaptable);
		}
	}
	
	/**
	 * I can execute if my wrapped operation can execute.
	 */
	public boolean canExecute() {
		return operation.canExecute();
	}

	/**
	 * Executes my wrapped operation and inserts it into the active
	 * transaction's change description for rollback and undo/redo support.
	 * 
	 * @throws IllegalStateException if I am being executed outside of a
	 *     read/write transaction context
	 */
	public void execute() {
		InternalTransaction tx = getTransaction();
		
		if (tx == null) {
			throw new IllegalStateException(
					"attempt to execute without write transaction"); //$NON-NLS-1$
		}
		
		Transaction childTransaction = null;
		
		try {
			if (!(operation instanceof AbstractEMFOperation)) {
				// create a nested transaction on this operation's behalf,
				//    to record the non-EMF changes
				try {
					childTransaction = createNonEMFTransaction(
							operation,
							getAdaptable(),
							tx.getOptions());  // same options as current
				} catch (InterruptedException e) {
					Tracing.catching(EMFOperationCommand.class, "execute", e); //$NON-NLS-1$
					ExecutionException exc = new ExecutionException(Messages.executeInterrupted, e);
					Tracing.throwing(EMFOperationCommand.class, "execute", exc); //$NON-NLS-1$
					throw exc;
				}
			}
			
			operation.execute(new NullProgressMonitor(), getAdaptable());
		} catch (ExecutionException e) {
			EMFWorkspacePlugin.INSTANCE.log(new Status(
				IStatus.ERROR,
				EMFWorkspacePlugin.getPluginId(),
				EMFWorkspaceStatusCodes.ROLLBACK_FAILED,
				NLS.bind(Messages.rollbackFailed, operation.getLabel()),
				e));
		} finally {
			if ((childTransaction != null) && childTransaction.isActive()) {
				// we created a child transaction on the operation's behalf,
				//    so we must also commit it
				try {
					childTransaction.commit();
				} catch (RollbackException e) {
					Tracing.catching(EMFOperationCommand.class, "execute", e); //$NON-NLS-1$
					
					// rollback should not happen with non-EMF changes
					EMFWorkspacePlugin.INSTANCE.log(e.getStatus());
				}
			}
		}
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
			Map options)
			throws InterruptedException {
		
		InternalTransaction result = new NonEMFTransaction(
				domain, operation, info, options);
		
		result.start();
		
		return result;
	}

	/**
	 * I can undo if my wrapped operation can undo.
	 */
	public boolean canUndo() {
		return operation.canUndo();
	}

	/**
	 * I undo my wrapped operation.  If an adaptable was initially provided to
	 * me and it is still available, then it is passed along to the operation. 
	 * 
	 * @throws IllegalStateException if I am being undone outside of a
	 *     read/write transaction context
	 */
	public void undo() {
		InternalTransaction tx = getTransaction();
		
		if (tx == null) {
			throw new IllegalStateException(
					"attempt to undo without write transaction"); //$NON-NLS-1$
		}
		
		try {
			operation.undo(new NullProgressMonitor(), getAdaptable());
		} catch (ExecutionException e) {
			EMFWorkspacePlugin.INSTANCE.log(new Status(
				IStatus.ERROR,
				EMFWorkspacePlugin.getPluginId(),
				EMFWorkspaceStatusCodes.ROLLBACK_FAILED,
				NLS.bind(Messages.rollbackFailed, operation.getLabel()),
				e));
		}
	}

	/**
	 * I can redo if my wrapped operation can redo.
	 */
	public boolean canRedo() {
		return operation.canRedo();
	}

	/**
	 * I redo my wrapped operation.  If an adaptable was initially provided to
	 * me and it is still available, then it is passed along to the operation. 
	 * 
	 * @throws IllegalStateException if I am being redone outside of a
	 *     read/write transaction context
	 */
	public void redo() {
		InternalTransaction tx = getTransaction();
		
		if (tx == null) {
			throw new IllegalStateException(
					"attempt to redo without write transaction"); //$NON-NLS-1$
		}
		
		try {
			operation.redo(new NullProgressMonitor(), getAdaptable());
		} catch (ExecutionException e) {
			EMFWorkspacePlugin.INSTANCE.log(new Status(
				IStatus.ERROR,
				EMFWorkspacePlugin.getPluginId(),
				EMFWorkspaceStatusCodes.ROLLBACK_FAILED,
				NLS.bind(Messages.rollbackFailed, operation.getLabel()),
				e));
		}
	}

	// Documentation copied from the inherited specification
	public Collection getResult() {
		return null;
	}

	/**
	 * Obtains the affected objects from my wrapped operation, if it is an
	 * {@link IAdvancedUndoableOperation}. 
	 */
	public Collection getAffectedObjects() {
		Collection result = null;
		
		if (operation instanceof IAdvancedUndoableOperation) {
			Object[] affected = ((IAdvancedUndoableOperation) operation).getAffectedObjects();
			
			if (affected != null) {
				result = Arrays.asList(affected);
			}
		}
		
		return result;
	}

	/**
	 * My label is my wrapped operation's label. 
	 */
	public String getLabel() {
		return operation.getLabel();
	}

	/**
	 * My description is my wrapped operation's label. 
	 */
	public String getDescription() {
		return operation.getLabel();
	}

	/**
	 * Forgets my operation and the adaptable with which I was initialized,
	 * if any. 
	 */
	public void dispose() {
		operation.dispose();
		operation = null;
		
		if (adaptable != null) {
			adaptable.clear();
			adaptable = null;
		}
	}

	// Documentation copied from the inherited specification
	public Command chain(Command command) {
	    return new ConditionalRedoCommand.Compound().chain(this).chain(command);
	}

	/**
	 * Obtains my adaptable, if I was initialized with one and it is still
	 * available.
	 * 
	 * @return my adaptable, or <code>null</code> if not available
	 */
	private IAdaptable getAdaptable() {
		IAdaptable result = null;
		
		if (adaptable != null) {
			result = (IAdaptable) adaptable.get();
		}
		
		return result;
	}
	
	/**
	 * Obtains the currently active read/write transaction in my editing domain
	 * that is owned by the current thread.
	 * 
	 * @return the active transaction, or <code>null</code> if there is no
	 *    active transaction or the current thread does not own it or it is
	 *    read-only
	 */
	private InternalTransaction getTransaction() {
		InternalTransaction result = ((InternalTransactionalEditingDomain) domain).getActiveTransaction();
		
		if (result != null) {
			if (result.isReadOnly() || (result.getOwner() != Thread.currentThread())) {
				result = null;
			}
		}
		
		return result;
	}
	
	/**
	 * Obtains the undoable operation that this command is wrapping.
	 * 
	 * @return An undoable operation.
	 */
	IUndoableOperation getOperation() {
		return operation;
	}
}
