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
 * $Id: TransactionalCommandStackImpl.java,v 1.10 2008/05/06 15:04:03 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.RollbackException;
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
	extends AbstractTransactionalCommandStack {

	/**
	 * Initializes me.
	 */
	public TransactionalCommandStackImpl() {
		super();
	}

    /**
     * {@inheritDoc}
     * 
     *  @since 1.1
     */
	@Override
	protected void doExecute(Command command, Map<?, ?> options) throws InterruptedException, RollbackException {
		InternalTransaction tx = createTransaction(command, options);
		boolean completed = false;
		
		try {
			basicExecute(command);
			
			// new in EMF 2.4:  AbortExecutionException can cause the
			// command not to be added to the undo stack
			completed = mostRecentCommand == command;
			
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
                handleRollback(command, null);
			} else {
				// the transaction has already incorporated the triggers
				//    into its change description, so the recording command
				//    doesn't need them again
				if (!(command instanceof RecordingCommand) && completed) {
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
	}
    
    /**
     * Extends the superclass implementation to first pop the failed command
     * off of the stack, if it was already appended.
     * 
     * @since 1.1
     */
    @Override
	protected void handleRollback(Command command, RollbackException rbe) {
        if ((command != null) && (top >= 0) && (commandList.get(top) == command)) {
            // pop the failed command
            commandList.remove(top--);

            if (top >= 0) {
                mostRecentCommand = commandList.get(top);
            } else {
                mostRecentCommand = null;
            }
        }
        
        super.handleRollback(command, rbe);
    }
	
	/**
     * Extends the inherited implementation by invoking it within the context of
     * an undo transaction (a read/write transaction with the
     * {@link #getUndoRedoOptions() undo/redo options}).
     */
	@Override
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

	/**
	 * Extends the inherited implementation to consider the redoability of
	 * {@link ConditionalRedoCommand}s.
	 */
	@Override
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
	@Override
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
	public EMFCommandTransaction createTransaction(Command command, Map<?, ?> options)
			throws InterruptedException {
		
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
	public void executeTriggers(Command command, List<Command> triggers,
			Map<?, ?> options) throws InterruptedException, RollbackException {
		
		if (!triggers.isEmpty()) {
			TriggerCommand trigger = (command == null)
				? new TriggerCommand(triggers)
				: new TriggerCommand(command, triggers);
			
			InternalTransaction tx = createTransaction(trigger,
                makeTriggerTransactionOptions(options));
			
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
