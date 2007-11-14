/**
 * <copyright>
 *
 * Copyright (c) 2007 IBM Corporation and others.
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
 * $Id: AbstractTransactionalCommandStack.java,v 1.5 2007/11/14 18:14:00 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Collections;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.transaction.ExceptionHandler;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalCommandStack;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;

/**
 * Partial implementation of the {@link TransactionalCommandStack} interface,
 * useful for subclasses to define their specific handling of transactions and
 * other concerns.
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.1
 */
public abstract class AbstractTransactionalCommandStack
    extends BasicCommandStack
    implements InternalTransactionalCommandStack {

    private InternalTransactionalEditingDomain domain;
    protected ExceptionHandler exceptionHandler;

    /**
     * Initializes me.
     */
    public AbstractTransactionalCommandStack() {
        super();
    }

    // Documentation copied from the method specification
    public InternalTransactionalEditingDomain getDomain() {
    	return domain;
    }

    // Documentation copied from the method specification
    public void setEditingDomain(InternalTransactionalEditingDomain domain) {
    	this.domain = domain;
    }

    /**
     * Ensures that the specified transaction is rolled back, first rolling
     * back a nested transaction (if any).
     * 
     * @param tx a transaction to roll back
     */
    protected void rollback(Transaction tx) {
    	while (tx.isActive()) {
    		Transaction active = domain.getActiveTransaction();
    		
    		active.rollback();
    	}
    }

    // Documentation copied from the method specification
    public void setExceptionHandler(ExceptionHandler handler) {
    	this.exceptionHandler = handler;
    }

    // Documentation copied from the method specification
    public ExceptionHandler getExceptionHandler() {
    	return exceptionHandler;
    }

    /**
     * Extends the inherited method by first aborting the active
     * transaction (if any) and passing the exception along to
     * the registered exception handler (if any).
     */
    @Override
	protected void handleError(Exception exception) {
    	InternalTransaction active = getDomain().getActiveTransaction();
    	
    	if ((active != null) && active.isActive()) {
    	    active.abort(new Status(IStatus.ERROR,
    	        EMFTransactionPlugin.getPluginId(),
    	        EMFTransactionStatusCodes.TRANSACTION_ABORTED,
    	        (exception.getMessage() == null)? "" : exception.getMessage(), //$NON-NLS-1$
    	        exception));
    	}
    	
    	if (!isCancelException(exception)) {
    		if (exceptionHandler != null) {
    			try {
    				exceptionHandler.handleException(exception);
    			} catch (Exception e) {
    				Tracing.catching(AbstractTransactionalCommandStack.class, "handleError", e); //$NON-NLS-1$
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
     * Default implementation delegates to the subclass implementation of
     * {@link #doExecute(Command, Map)}, handling the roll-back exception if the
     * command is rolled back.  Note that <code>doExecute()</code> is only
     * called if the command is {@linkplain Command#canExecute() executable}.
     */
    public void execute(Command command, Map<?, ?> options)
            throws InterruptedException, RollbackException {
        
        if ((command != null) && command.canExecute()) {
            try {
                doExecute(command, options);
            } catch (RollbackException e) {
                handleRollback(command, e);
                
                throw e; // re-throw
            }
        } else if (command != null) {
            command.dispose();
        }
    }
    
    /**
     * Implemented by subclasses to perform the execution of the specified
     * <code>command</code>.  Invoked by the {@link #execute(Command, Map)}
     * method.
     * 
     * @param command the command to execute
     * @param options the transaction options to apply to execution of the command
     * 
     * @throws InterruptedException if the current thread is interrupted while
     *     waiting to start the transaction
     * @throws RollbackException if the execution of the command is rolled back
     */
    protected abstract void doExecute(Command command, Map<?, ?> options)
            throws InterruptedException, RollbackException;

    /**
     * Handles the roll-back of the specified <code>command</code> execution.
     * This default implementation disposes the command and notifies
     * command-stack listeners so that they may get the latest information, in
     * case they were already notified of command execution, for example.
     * 
     * @param command the command whose execution was rolled back (may be
     *    <code>null</code> if not known)
     * @param rbe the roll-back exception (may be <code>null</code> if no
     *    exception is to be thrown)
     */
    protected void handleRollback(Command command, RollbackException rbe) {
        if (command != null) {
            command.dispose();
        }
        
        notifyListeners();
    }
    
    /**
     * Redefines the inherited method by forwarding to the
     * {@link TransactionalCommandStack#execute(Command, Map)} method. Any
     * checked exception thrown by that method is handled by
     * {@link #handleError(Exception)} but is not propagated.
     */
    @Override
	public void execute(Command command) {
    	try {
    		execute(command, null);
    	} catch (InterruptedException e) {
    		// just log it.  Note that the transaction is already rolled back,
    		//    so handleError() will not find an active transaction
    		Tracing.catching(AbstractTransactionalCommandStack.class, "execute", e); //$NON-NLS-1$
    		handleError(e);
    	} catch (RollbackException e) {
    		// just log it.  Note that the transaction is already rolled back,
    		//    so handleError() will not find an active transaction
    		Tracing.catching(AbstractTransactionalCommandStack.class, "execute", e); //$NON-NLS-1$
    		handleError(e);
    	}
    }
    
    /**
     * Provides access to the {@link BasicCommandStack} implementation of the
     * {@link #execute(Command)} method, as this class overrides it to delegate
     * to the {@link TransactionalCommandStack#execute(Command, Map)} method.
     * 
     * @param command the command to execute
     */
    protected void basicExecute(Command command) {
        super.execute(command);
    }

    /**
     * Obtains my editing domain's default undo/redo transaction options.
     * 
     * @return my editing domain's transaction options for undo/redo
     */
    protected Map<?, ?> getUndoRedoOptions() {
    	return domain.getUndoRedoOptions();
    }
    
    /**
     * Customizes the specified <code>options</code> for the case of a transaction
     * that executes trigger commands.  The original map is not affected.
     * 
     * @param options a client-supplied options map
     * @return a derived map of options suitable for trigger transactions
     */
    public static final Map<Object, Object> makeTriggerTransactionOptions(Map<?, ?> options) {
        Map<Object, Object> result;
        
        if ((options == null) || options.isEmpty()) {
            result = Collections.<Object, Object>singletonMap(
                TransactionImpl.OPTION_IS_TRIGGER_TRANSACTION, Boolean.TRUE); 
        } else {
            result = new java.util.HashMap<Object, Object>(options);
            result.put(
                TransactionImpl.OPTION_IS_TRIGGER_TRANSACTION, Boolean.TRUE);
        }
        
        return result;
    }

}