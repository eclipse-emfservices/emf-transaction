/**
 * <copyright>
 *
 * Copyright (c) 2005, 2006 IBM Corporation and others.
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
 * $Id: TransactionalCommandStackImpl.java,v 1.6 2006/10/10 14:31:47 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.transaction.ExceptionHandler;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;
import org.eclipse.emf.transaction.util.ConditionalRedoCommand;
import org.eclipse.emf.transaction.util.TriggerCommand;

/**
 * The default implementation of the transactional editing domain command stack.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionalCommandStackImpl
	extends BasicCommandStack
	implements InternalTransactionalCommandStack {

	private InternalTransactionalEditingDomain domain;
	
	private ExceptionHandler exceptionHandler;
	
	/**
	 * Initializes me.
	 */
	public TransactionalCommandStackImpl() {
		super();
	}

	// Documentation copied from the inherited specification
	public InternalTransactionalEditingDomain getDomain() {
		return domain;
	}
	
	// Documentation copied from the inherited specification
	public void setEditingDomain(InternalTransactionalEditingDomain domain) {
		this.domain = domain;
	}
	
	// Documentation copied from the inherited specification
	public void execute(Command command, Map options) throws InterruptedException, RollbackException {
		if ((command != null) && command.canExecute()) {
			InternalTransaction tx = createTransaction(command, options);
			
			try {
				super.execute(command);
				
				// commit the transaction now
				tx.commit();
			} catch (OperationCanceledException e) {
				// snuff the exception, because this is expected (user asked to
				//    cancel the model change).  We will rollback, below
			} finally {
				if ((tx != null) && (tx.isActive())) {
					// roll back (some exception, possibly being thrown now or
					//    an operation cancel, has occurred)
					rollback(tx);
				} else {
					// the transaction has already incorporated the triggers
					//    into its change description, so the recording command
					//    doesn't need them again
					if (!(command instanceof RecordingCommand)) {
						Command triggerCommand = tx.getTriggers();
						
						if (triggerCommand != null) {
							// replace the executed command by a compound of the
							//    original and the trigger commands
							CompoundCommand compound = new ConditionalRedoCommand.Compound();
							compound.append(mostRecentCommand);
							compound.append(triggerCommand);
							mostRecentCommand = compound;
					        commandList.set(top, mostRecentCommand);
						}
					}
				}
			}
		} else if (command != null) {
			command.dispose();
		}
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

	// Documentation copied from the inherited specification
	public void setExceptionHandler(ExceptionHandler handler) {
		this.exceptionHandler = handler;
	}
	
	// Documentation copied from the inherited specification
	public ExceptionHandler getExceptionHandler() {
		return exceptionHandler;
	}
	
	/**
	 * Extends the inherited method by first rolling back the active
	 * transaction (if any) and passing the exception along to
	 * the registered exception handler (if any).
	 */
	protected void handleError(Exception exception) {
		Transaction active = getDomain().getActiveTransaction();
		
		if (active != null) {
			active.rollback();
		}
		
		if (!isCancelException(exception)) {
			if (exceptionHandler != null) {
				try {
					exceptionHandler.handleException(exception);
				} catch (Exception e) {
					Tracing.catching(TransactionalCommandStackImpl.class, "handleError", e); //$NON-NLS-1$
					EMFTransactionPlugin.INSTANCE.log(new Status(
							IStatus.WARNING,
							EMFTransactionPlugin.getPluginId(),
							EMFTransactionStatusCodes.EXCEPTION_HANDLER_FAILED,
							Messages.exceptionHandlerFailed,
							e));
				}
			}
			
			super.handleError(exception);
		}
	}
	
	/**
	 * Does the specified exception indicate that the user canceled execution,
	 * undo, or redo of a command?
	 * 
	 * @param exception an exception
	 * @return <code>true</code> if it is an {@link OperationCanceledException}
	 *     or a {@link RollbackException} that was caused by operation cancel
	 */
	private boolean isCancelException(Throwable exception) {
		boolean result;
		
		if (exception instanceof OperationCanceledException) {
			result = true;
		} else if (exception instanceof RollbackException) {
			IStatus status = ((RollbackException) exception).getStatus();
			result = (status != null) &&
				((status.getSeverity() == IStatus.CANCEL)
						|| isCancelException(status.getException()));
		} else {
			result = false;
		}
		
		return result;
	}
	
	/**
	 * Redefines the inherited method by forwarding to the
	 * {@link TransactionalEditingDomain#execute(Command, Map)} method.  Any checked
	 * exception thrown by that method is handled by
	 * {@link #handleError(Exception)} but is not propagated.
	 */
	public void execute(Command command) {
		try {
			execute(command, null);
		} catch (InterruptedException e) {
			// just log it.  Note that the transaction is already rolled back,
			//    so handleError() will not find an active transaction
			Tracing.catching(TransactionalCommandStackImpl.class, "execute", e); //$NON-NLS-1$
			handleError(e);
		} catch (RollbackException e) {
			// just log it.  Note that the transaction is already rolled back,
			//    so handleError() will not find an active transaction
			Tracing.catching(TransactionalCommandStackImpl.class, "execute", e); //$NON-NLS-1$
			handleError(e);
		}
	}

	/**
	 * Extends the inherited implementation by invoking it within the context
	 * of an undo transaction (a read/write transaction with the
	 * {@link #getUndoRedoOptions() undo/redo options}).
	 */
	public void undo() {
		if (canUndo()) {
			try {
				Transaction tx = createTransaction(getUndoCommand(), getUndoRedoOptions());
			
				super.undo();
				
				tx.commit();
			} catch (Exception e) {
				// just log it and roll back if necessary
				Tracing.catching(TransactionalCommandStackImpl.class, "undo", e); //$NON-NLS-1$
				handleError(e);
			}
		}
	}

	private Map getUndoRedoOptions() {
		return domain.getUndoRedoOptions();
	}

	/**
	 * Extends the inherited implementation to consider the redoability of
	 * {@link ConditionalRedoCommand}s.
	 */
	public boolean canRedo() {
		boolean result = super.canRedo();
		
		if (result) {
			// I know that this is a valid index if super returned true
			Object nextRedo = commandList.get(top + 1);
			
			if (nextRedo instanceof ConditionalRedoCommand) {
				result = ((ConditionalRedoCommand) nextRedo).canRedo();
			}
		}
		
		return result;
	}
	
	/**
	 * Extends the inherited implementation by invoking it within the context
	 * of a redo transaction (a read/write transaction with the
	 * {@link #getUndoRedoOptions() undo/redo options}).
	 */
	public void redo() {
		if (canRedo()) {
			try {
				Transaction tx = createTransaction(getRedoCommand(), getUndoRedoOptions());
			
				super.redo();
				
				tx.commit();
			} catch (Exception e) {
				// just log it and roll back if necessary
				Tracing.catching(TransactionalCommandStackImpl.class, "redo", e); //$NON-NLS-1$
				handleError(e);
			}
		}
	}

	// Documentation copied from the inherited specification
	public EMFCommandTransaction createTransaction(Command command, Map options) throws InterruptedException {
		EMFCommandTransaction result;
		
		if (command instanceof TriggerCommand) {
			result = new TriggerCommandTransaction((TriggerCommand) command,
					getDomain(), options);
		} else {
			result = new EMFCommandTransaction(command, getDomain(), options);
		}
		
		result.start();
		
		return result;
	}

	// Documentation copied from the inherited specification
	public void executeTriggers(Command command, List triggers, Map options) throws InterruptedException, RollbackException {
		if (!triggers.isEmpty()) {
			TriggerCommand trigger = (command == null)
				? new TriggerCommand(triggers)
				: new TriggerCommand(command, triggers);
			
			InternalTransaction tx = createTransaction(trigger, options);
			
			try {
				trigger.execute();
				
				InternalTransaction parent = (InternalTransaction) tx.getParent();
				
				// shouldn't be null if we're executing triggers!
				if (parent != null) {
					parent.addTriggers(trigger);
				}
				
				// commit the transaction now
				tx.commit();
			} catch (RuntimeException e) {
				Tracing.catching(TransactionalCommandStackImpl.class, "executeTriggers", e); //$NON-NLS-1$
				
				IStatus status;
				if (e instanceof OperationCanceledException) {
					status = Status.CANCEL_STATUS;
				} else {
					status = new Status(
							IStatus.ERROR,
							EMFTransactionPlugin.getPluginId(),
							EMFTransactionStatusCodes.PRECOMMIT_FAILED,
							Messages.precommitFailed,
							e);
				}
				
				RollbackException rbe = new RollbackException(status);
				Tracing.throwing(TransactionalCommandStackImpl.class, "executeTriggers", rbe); //$NON-NLS-1$
				throw rbe;
			} finally {
				if ((tx != null) && (tx.isActive())) {
					// roll back because an uncaught exception occurred
					rollback(tx);
				}
			}
		}
	}
	
	// Documentation copied from the inherited specification
	public void dispose() {
		flush();
		setEditingDomain(null);
		exceptionHandler = null;
	}
}
