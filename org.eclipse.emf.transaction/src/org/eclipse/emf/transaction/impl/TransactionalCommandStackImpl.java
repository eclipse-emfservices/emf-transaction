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
 * $Id: TransactionalCommandStackImpl.java,v 1.1 2006/01/30 19:47:54 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.transaction.ExceptionHandler;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;
import org.eclipse.emf.transaction.util.TriggerCommand;

/**
 * The default implementation of the transactional editing domain command stack.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionalCommandStackImpl
	extends BasicCommandStack
	implements InternalTransactionalCommandStack {

	/**
	 * The transaction options that should be used when undoing/redoing changes
	 * on the command stack.  Undo and redo must not perform triggers because
	 * these were implemented as chained commands during the original execution.
	 * Moreover, validation is not required during undo/redo because we can
	 * only return the model from a valid state to another valid state if the
	 * original execution did so.  Finally, it is not necessary to record
	 * undo information when we are undoing or redoing.
	 */
	public static final Map UNDO_REDO_OPTIONS;
	
	private InternalTransactionalEditingDomain domain;
	private TriggerCommand triggerCommand;
	
	private ExceptionHandler exceptionHandler;
	
	static {
		UNDO_REDO_OPTIONS = new java.util.HashMap();
		UNDO_REDO_OPTIONS.put(Transaction.OPTION_NO_TRIGGERS, Boolean.TRUE);
		UNDO_REDO_OPTIONS.put(Transaction.OPTION_NO_UNDO, Boolean.TRUE);
		UNDO_REDO_OPTIONS.put(Transaction.OPTION_NO_VALIDATION, Boolean.TRUE);
	}
	
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
			Transaction tx = createTransaction(command, options);
			
			try {
				super.execute(command);
			} finally {
				if ((tx != null) && (tx.isActive())) {
					// commit the transaction now
					try {
						tx.commit();
						
						if (triggerCommand != null) {
							// replace the executed command by the trigger command
					        mostRecentCommand = triggerCommand;
					        commandList.set(top, triggerCommand);
						}
					} finally {
						triggerCommand = null;
					}
				}
			}
		} else if (command != null) {
			command.dispose();
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
	 * {@link #UNDO_REDO_OPTIONS undo/redo options}).
	 */
	public void undo() {
		if (canUndo()) {
			try {
				Transaction tx = createTransaction(getUndoCommand(), UNDO_REDO_OPTIONS);
			
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
	 * Extends the inherited implementation by invoking it within the context
	 * of a redo transaction (a read/write transaction with the
	 * {@link #UNDO_REDO_OPTIONS undo/redo options}).
	 */
	public void redo() {
		if (canRedo()) {
			try {
				Transaction tx = createTransaction(getRedoCommand(), UNDO_REDO_OPTIONS);
			
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
		EMFCommandTransaction result = new EMFCommandTransaction(
				command, getDomain(), options);
		result.start();
		
		return result;
	}

	// Documentation copied from the inherited specification
	public void executeTriggers(Command command, List triggers, Map options) throws InterruptedException, RollbackException {
		if (!triggers.isEmpty()) {
			TriggerCommand trigger = (command == null)
				? new TriggerCommand(triggers)
				: new TriggerCommand(command, triggers);
			
			Transaction tx = createTransaction(trigger, options);
			
			try {
				trigger.execute();
				
				if ((command != null) &&
						(!(command instanceof TriggerCommand) || (triggerCommand != null))) {
				
					// will replace the original command on the stack with the
					//    trigger
					triggerCommand = trigger;
				}
			} finally {
				if ((tx != null) && (tx.isActive())) {
					// commit the transaction now
					tx.commit();
				}
			}
		}
	}
	
	// Documentation copied from the inherited specification
	public void dispose() {
		setEditingDomain(null);
		triggerCommand = null;
		exceptionHandler = null;
	}
}
